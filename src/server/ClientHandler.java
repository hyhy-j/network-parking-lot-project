package server;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import utils.Protocol;

public class ClientHandler extends Thread {
    private String role = null;    // 역할: "LPR" 또는 "USER"
    private String carNum = null;  // 유저일 경우 차량 번호

    private DataInputStream is = null;
    private PrintStream os = null;
    private Socket clientSocket = null;
    private final ClientHandler[] threads; // 전체 접속자 관리용 배열 참조
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
            // 입출력 스트림 생성
            is = new DataInputStream(clientSocket.getInputStream());
            os = new PrintStream(clientSocket.getOutputStream());

            // 1. 로그인 (Handshake) 처리
            String loginMsg = is.readLine();
            if (loginMsg == null) return; // 바로 끊긴 경우
            loginMsg = loginMsg.trim();

            if (loginMsg.startsWith(Protocol.LOGIN_LPR)) {
                this.role = "LPR";
                os.println("[Server] LPR Camera registered successfully.");
                System.out.println("[Log] LPR Camera connected from " + clientSocket.getInetAddress());

            } else if (loginMsg.startsWith(Protocol.LOGIN_USER)) {
                this.role = "USER";
                // "LOGIN:USER:1234" 형식에서 "1234" 파싱
                if (loginMsg.split(":").length > 2) {
                    this.carNum = loginMsg.split(":")[2];
                    os.println("[Server] User App registered. Car Number: " + this.carNum);
                    System.out.println("[Log] User connected. Car: " + this.carNum);
                } else {
                    os.println("[Server] Invalid login format.");
                    return; // 로그인 실패 시 종료
                }
            } else {
                os.println("[Server] Unknown client type. Bye.");
                return;
            }

            // 2. 메시지 수신 및 처리 루프
            while (true) {
                String line = is.readLine();

                // 연결이 끊어지거나 종료 명령 수신 시 루프 탈출
                if (line == null || line.startsWith(Protocol.CMD_EXIT)) {
                    break;
                }

                line = line.trim();

                // [LPR 로직] 차량 인식 메시지가 온 경우 ("DETECT:1234")
                if ("LPR".equals(this.role) && line.startsWith(Protocol.DETECT_CAR)) {
                    String targetCarNum = line.split(":")[1];
                    System.out.println("[Event] LPR detected car: " + targetCarNum);

                    boolean userFound = false;

                    // 현재 접속 중인 모든 클라이언트 스캔 (동기화 블록 사용)
                    synchronized (this) {
                        for (int i = 0; i < maxClientsCount; i++) {
                            ClientHandler t = threads[i];
                            // 접속 중이고 + 유저 역할이며 + 차량 번호가 일치하는지 확인
                            if (t != null && "USER".equals(t.role) && targetCarNum.equals(t.carNum)) {
                                // 해당 유저에게 알림 전송 (Unicast)
                                t.os.println(Protocol.MSG_PAYMENT);
                                userFound = true;
                                this.os.println("[Server] Notification sent to user (" + targetCarNum + ").");
                                System.out.println("[Log] Alert sent to User " + targetCarNum);
                                break;
                            }
                        }
                    }

                    if (!userFound) {
                        this.os.println("[Server] User with car number " + targetCarNum + " is not connected.");
                    }
                }
                // [User 로직] 유저가 서버에 뭔가 보낸 경우 (필요 시 구현)
                else if ("USER".equals(this.role)) {
                    // 예: 하트비트나 상태 확인 등
                    System.out.println("[Msg from User " + this.carNum + "] " + line);
                }
            }

            // 루프를 빠져나오면 연결 종료 메시지 출력
            os.println("*** Bye " + (role.equals("USER") ? carNum : role) + " ***");

        } catch (IOException e) {
            // 통신 중 에러 발생 시 (클라이언트 강제 종료 등)
            System.out.println("[Error] Connection lost with " + role);
        } finally {

            // 3. 연결 종료 및 리소스 정리 (Clean-up)
            // 현재 스레드를 배열에서 제거하여 빈 자리를 만듦
            synchronized (this) {
                for (int i = 0; i < maxClientsCount; i++) {
                    if (threads[i] == this) {
                        threads[i] = null;
                        System.out.println("[Log] Client disconnected: " + role + (carNum != null ? " (" + carNum + ")" : ""));
                    }
                }
            }

            // 소켓 및 스트림 닫기
            try {
                if (is != null) is.close();
                if (os != null) os.close();
                if (clientSocket != null) clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}