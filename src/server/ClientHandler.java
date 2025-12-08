package server;

import java.io.*;
import java.net.Socket;
import utils.Protocol;

public class ClientHandler extends Thread {
    private String role = null;
    private String userType = "VISITOR";
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

    private String determineUserType(String carNum) {
        try {
            int num = Integer.parseInt(carNum);
            if (num >= 1000 && num <= 1999) return "PROFESSOR";
            if (num >= 2000 && num <= 2999) return "STUDENT";
        } catch (NumberFormatException e) {}
        return "VISITOR";
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

            // 1. 로그인 처리
            if (loginMsg.startsWith(Protocol.LOGIN_LPR)) {
                this.role = "LPR";
                os.println("[System] LPR Camera connected.");
            } else if (loginMsg.startsWith(Protocol.LOGIN_USER)) {
                this.role = "USER";
                if (loginMsg.split(":").length > 2) {
                    this.carNum = loginMsg.split(":")[2];
                    this.userType = determineUserType(this.carNum);

                    String welcomeMsg = "방문객";
                    if(userType.equals("PROFESSOR")) welcomeMsg = "교수님";
                    else if(userType.equals("STUDENT")) welcomeMsg = "학생";

                    os.println("[System] " + welcomeMsg + "(" + this.carNum + ")님 접속 환영합니다.");
                    System.out.println("[Log] User connected: " + this.carNum + " (" + this.userType + ")");
                }
            }

            // 2. 메시지 수신 루프
            while (true) {
                String line = reader.readLine();
                if (line == null || line.startsWith(Protocol.CMD_EXIT)) break;
                line = line.trim();

                // ----------------------------------------------------
                // [기능 A] LPR 카메라 처리 (입차 vs 출차)
                // ----------------------------------------------------
                if ("LPR".equals(this.role)) {

                    // 1) 입차 인식 (LPR_IN:1234)
                    if (line.startsWith("LPR_IN:")) {
                        String targetCar = line.split(":")[1];
                        System.out.println("[LPR 입차] " + targetCar);

                        // 해당 유저에게 "ENTRY" 신호 전송
                        sendToUser(targetCar, "ENTRY");
                        this.os.println("[System] Entry alert sent to " + targetCar);
                    }

                    // 2) 출차 인식 (LPR_OUT:1234)
                    else if (line.startsWith("LPR_OUT:")) {
                        String targetCar = line.split(":")[1];
                        System.out.println("[LPR 출차] " + targetCar);

                        // 해당 유저에게 "PAYMENT" 신호 전송
                        sendToUser(targetCar, Protocol.MSG_PAYMENT);
                        this.os.println("[System] Payment alert sent to " + targetCar);
                    }

                    // (구버전 호환용) DETECT_CAR -> 기본 출차로 처리
                    else if (line.startsWith(Protocol.DETECT_CAR)) {
                        String targetCar = line.split(":")[1];
                        sendToUser(targetCar, Protocol.MSG_PAYMENT);
                    }
                }

                // ----------------------------------------------------
                // [기능 B] 유저 명령 처리
                // ----------------------------------------------------
                else if ("USER".equals(this.role)) {
                    if (line.equals(Protocol.REQ_NAV)) {
                        System.out.println("[Nav] Navigation requested by " + this.carNum);
                        new Thread(this::simulateNavigation).start();
                    }
                    else if (line.startsWith("/report")) {
                        String content = line.replace("/report", "").trim();
                        os.println("[System] 신고가 접수되었습니다.");
                        System.out.println("[Report] " + this.carNum + ": " + content);
                    }
                    else if (line.startsWith("/help")) {
                        os.println("[System] 보안팀 호출 완료.");
                        System.out.println("[Emergency] " + this.carNum + " help requested.");
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("[Error] Connection lost: " + role);
        } finally {
            closeResources();
        }
    }

    // 특정 유저에게 메시지 보내기 헬퍼 메서드
    private void sendToUser(String targetCarNum, String message) {
        synchronized (this) {
            for (int i = 0; i < maxClientsCount; i++) {
                ClientHandler t = threads[i];
                if (t != null && "USER".equals(t.role) && targetCarNum.equals(t.carNum)) {
                    t.os.println(message);
                    return;
                }
            }
        }
    }

    // [길 안내] 상세 텍스트 내비게이션
    private void simulateNavigation() {
        try {
            String targetName = "";
            String msgStart = "";
            int destX = 0, destY = 0;

            if ("PROFESSOR".equals(this.userType)) {
                targetName = "A-1 [연구실 전용]";
                msgStart = "교수님 환영합니다! 본관 연구동 ";
                destX = 50; destY = 100;
            } else if ("STUDENT".equals(this.userType)) {
                targetName = "C-1 [명신관]";
                msgStart = "학생이시군요! 명신관 강의동 ";
                destX = -30; destY = 40;
            } else {
                targetName = "B-1 [주차타워]";
                msgStart = "일반 방문객 추천 구역, ";
                destX = 10; destY = 10;
            }

            os.println("=========================================");
            os.println(msgStart + "쪽으로 안내를 시작합니다.");
            Thread.sleep(1000);
            os.println("📡 [IoT 모드] 스마트 내비게이션 활성화");
            Thread.sleep(1000);

            int totalDist = (int)Math.sqrt(destX * destX + destY * destY);
            os.println("📍 추천 주차면: " + targetName);
            os.println("📍 총 거리: " + totalDist + "m (예상 " + (totalDist / 5) + "초)");

            Thread.sleep(1000);
            os.println("🚗 주차장 입구 통과. 서행하세요.");
            Thread.sleep(1500);

            for (int i = 1; i <= 5; i++) {
                int curX = (destX / 5) * i;
                int curY = (destY / 5) * i;
                os.println(Protocol.NAV_COORD + curX + "," + curY);

                if (i == 2) {
                    if ("PROFESSOR".equals(userType)) os.println("➡️ 20m 앞 본관 방향으로 우회전하세요.");
                    else if ("STUDENT".equals(userType)) os.println("⬅️ 15m 앞 명신관 방향으로 좌회전하세요.");
                    else os.println("⬆️ 주차타워 방향으로 직진하세요.");
                }
                else if (i == 3) {
                    if ("VISITOR".equals(userType)) os.println("➡️ 12m 앞 주차타워 진입로입니다.");
                    else os.println("🚗 목적지 방면으로 안전 운행 중...");
                }
                else if (i == 4) {
                    os.println("⚠️ 보행자 주의! 속도를 줄이세요.");
                }
                else if (i == 5) {
                    if ("PROFESSOR".equals(userType)) os.println("🔄 좌측 교수 전용 구역에 주차하세요.");
                    else if ("STUDENT".equals(userType)) os.println("🔄 우측 학생 주차 구역에 주차하세요.");
                    else os.println("🔄 전방 주차타워 입구로 진입하세요.");
                }
                Thread.sleep(1500);
            }

            Thread.sleep(1000);
            os.println("🎉 목적지 도착 완료. 안전하게 주차되었습니다.");
            os.println(Protocol.NAV_END);

        } catch (InterruptedException e) {}
    }

    private void closeResources() {
        try {
            if (reader != null) reader.close();
            if (os != null) os.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {}
    }
}