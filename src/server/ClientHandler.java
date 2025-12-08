package server;

import java.io.*;
import java.net.Socket;
import utils.Protocol;

public class ClientHandler extends Thread {
    private String role = null;
    private String carNum = null;

    private BufferedReader reader = null;
    private PrintStream os = null;
    private Socket clientSocket = null;
    private final ClientHandler[] threads;
    private int maxClientsCount;

    public ClientHandler(Socket clientSocket, ClientHandler[] threads) {
        this.clientSocket = clientSocket;
        this.threads = threads;
        this.maxClientsCount = threads.length;
    }

    public void run() {
        int maxClientsCount = this.maxClientsCount;
        ClientHandler[] threads = this.threads;

        try {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
            os = new PrintStream(clientSocket.getOutputStream(), true, "UTF-8");

            String loginMsg = reader.readLine();
            if (loginMsg == null) return;
            loginMsg = loginMsg.trim();

            if (loginMsg.startsWith(Protocol.LOGIN_LPR)) {
                this.role = "LPR";
                os.println("[System] LPR Camera connected.");
            } else if (loginMsg.startsWith(Protocol.LOGIN_USER)) {
                this.role = "USER";
                if (loginMsg.split(":").length > 2) {
                    this.carNum = loginMsg.split(":")[2];
                    os.println("[System] " + this.carNum + "님 환영합니다. 주차 대기 모드입니다.");
                    System.out.println("[Log] User connected: " + this.carNum);
                }
            }

            while (true) {
                String line = reader.readLine();
                if (line == null || line.startsWith(Protocol.CMD_EXIT)) break;
                line = line.trim();

                // [신고 기능]
                if (line.startsWith("/report")) {
                    String content = line.replace("/report", "").trim();
                    os.println("[System] 신고가 접수되었습니다. (내용: " + content + ")");
                    // (옵션) 관리자에게 알림 방송 코드 추가 가능
                }

                // [LPR 로직] 차량 인식 시 -> 접속된 유저에게 알림
                else if ("LPR".equals(this.role) && line.startsWith(Protocol.DETECT_CAR)) {
                    String targetCarNum = line.split(":")[1];
                    System.out.println("[Event] Detected: " + targetCarNum);

                    synchronized (this) {
                        for (int i = 0; i < maxClientsCount; i++) {
                            ClientHandler t = threads[i];
                            if (t != null && "USER".equals(t.role) && targetCarNum.equals(t.carNum)) {
                                // 1. 결제 프로토콜 전송 (팝업용)
                                t.os.println(Protocol.MSG_PAYMENT);
                                // 2. [팀원 기능 반영] 채팅창에 인식 알림 텍스트 전송
                                t.os.println("🔔 " + targetCarNum + "님 차량이 인식되었습니다. (출차 절차 진행)");
                                this.os.println("[System] User " + targetCarNum + " notified.");
                            }
                        }
                    }
                }

                // [길 안내 요청]
                else if ("USER".equals(this.role) && line.equals(Protocol.REQ_NAV)) {
                    // 별도 스레드로 안내 시작
                    new Thread(this::simulateNavigation).start();
                }
            }
        } catch (IOException e) {
            System.out.println("[Error] Connection lost: " + role);
        } finally {
            // 리소스 정리 (생략 - 기존 코드와 동일)
            closeResources();
        }
    }

    // [팀원 기능 통합] 상세 텍스트 내비게이션
    private void simulateNavigation() {
        try {
            // 1. 사용자 타입 구분 (가정: 차번호 끝자리가 짝수=교수, 홀수=학생)
            char lastChar = (carNum != null) ? carNum.charAt(carNum.length() - 1) : '1';
            boolean isProfessor = (lastChar - '0') % 2 == 0;

            String targetName = isProfessor ? "본관(교수 연구동)" : "명신관(강의동)";
            String msgStart = isProfessor ? "교수님 환영합니다! " : "학생이시군요! ";

            // 좌표 설정
            int destX = isProfessor ? 50 : -30;
            int destY = isProfessor ? 100 : 40;

            // [안내 시작]
            os.println("=========================================");
            os.println(msgStart + targetName + " 쪽으로 안내해 드릴까요? (자동 시작)");
            Thread.sleep(1000);
            os.println("[System] " + targetName + "으로 안내를 시작합니다.");
            os.println("📡 [IoT 모드] 스마트 내비게이션 시작");
            Thread.sleep(1000);

            os.println("🚗 주차장 입구에서 출발합니다.");
            os.println("⏱️ 예상 소요 시간: 10초");
            Thread.sleep(1500);

            // [주행 시뮬레이션]
            for (int i = 1; i <= 5; i++) {
                // 좌표 전송 (UserApp에서는 숨김 처리됨, 지도용)
                int curX = (destX / 5) * i;
                int curY = (destY / 5) * i;
                os.println(Protocol.NAV_COORD + curX + "," + curY);

                // [상세 텍스트 안내] - 팀원 스타일 적용
                if (i == 2) {
                    if (isProfessor) os.println("➡️ 20m 앞 본관 방향으로 우회전하세요.");
                    else os.println("⬅️ 15m 앞 명신관 방향으로 좌회전하세요.");
                } else if (i == 3) {
                    os.println("🚗 " + (isProfessor ? "연구동" : "강의동") + " 방면으로 직진 중...");
                } else if (i == 4) {
                    os.println("⚠️ 곧 주차 구역입니다. 속도를 줄이세요.");
                } else if (i == 5) {
                    if (isProfessor) os.println("🔄 좌측 교수 전용 주차구역으로 진입하세요.");
                    else os.println("🔄 우측 일반 주차구역으로 진입하세요.");
                }

                Thread.sleep(1500); // 1.5초 간격
            }

            // [도착]
            Thread.sleep(1000);
            os.println("🎉 목적지 도착! 안전하게 주차되었습니다.");
            os.println(Protocol.NAV_END);

        } catch (InterruptedException e) {
        }
    }

    private void closeResources() {
        try {
            if (reader != null) reader.close();
            if (os != null) os.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {}
    }
}