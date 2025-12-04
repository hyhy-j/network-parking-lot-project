package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Scanner;
import utils.Protocol;

public class UserApp {

    // 수신 전용 스레드 클래스 (내부 클래스)
    static class ReceiveThread extends Thread {
        private BufferedReader reader;
        private Socket socket;

        public ReceiveThread(Socket socket) {
            this.socket = socket;
            try {
                this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    // 서버로부터 온 메시지 출력
                    System.out.println("\n[App Alert] " + line);

                    // 결제 완료 메시지 수신 시 처리
                    if (line.equals(Protocol.MSG_PAYMENT)) {
                        System.out.println(">>> -------------------------------- <<<");
                        System.out.println(">>>  [알림] 자동 결제가 완료되었습니다.  <<<");
                        System.out.println(">>>      안녕히 가십시오 (출차 가능)     <<<");
                        System.out.println(">>> -------------------------------- <<<");
                        System.out.print("Input Command (/quit to exit): "); // 프롬프트 다시 출력
                    }
                }
            } catch (IOException e) {
                System.out.println("[System] Server disconnected.");
                System.exit(0);
            }
        }
    }

    public static void main(String[] args) {
        String host = "localhost";
        int port = 8888;
        Scanner sc = new Scanner(System.in);

        System.out.println("=== Smart Parking User App ===");
        System.out.print("Enter your Car Number to login: ");
        String myCarNum = sc.nextLine();

        try {
            Socket socket = new Socket(host, port);
            PrintStream os = new PrintStream(socket.getOutputStream());

            // 1. 로그인 패킷 전송
            os.println(Protocol.LOGIN_USER + myCarNum);

            // 2. 수신 스레드 시작 (서버 알림 대기)
            new ReceiveThread(socket).start();

            // 3. 메인 스레드는 사용자 입력 대기 (종료 명령용)
            while (true) {
                String input = sc.nextLine();
                if (input.equalsIgnoreCase("/quit")) {
                    os.println(Protocol.CMD_EXIT);
                    socket.close();
                    break;
                }
            }
            sc.close();
        } catch (IOException e) {
            System.out.println("Cannot connect to server: " + e.getMessage());
        }
    }
}