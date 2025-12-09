package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Scanner;
import utils.Protocol;

public class LPRCamera {

    static class ServerListener extends Thread {
        BufferedReader reader;
        public ServerListener(Socket s) throws IOException {
            reader = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
        }
        public void run() {
            try {
                String line;
                while((line = reader.readLine()) != null) {
                    System.out.println("[Server Response] " + line);
                }
            } catch(IOException e) {}
        }
    }

    public static void main(String[] args) {
        String host = "192.168.35.247"; // 서버 IP 확인
        int port = 8888;
        Scanner sc = new Scanner(System.in);

        System.out.println("=== 📷 LPR Camera Simulator (In/Out) ===");
        System.out.println("사용법:");
        System.out.println(" - 입차: in [차량번호]  (예: in 1234)");
        System.out.println(" - 출차: out [차량번호] (예: out 1234)");
        System.out.println(" - 종료: /quit");

        try {
            Socket socket = new Socket(host, port);
            PrintStream os = new PrintStream(socket.getOutputStream(), true, "UTF-8");

            new ServerListener(socket).start();

            // LPR 로그인
            os.println(Protocol.LOGIN_LPR);

            while (true) {
                System.out.print("Command > ");
                String input = sc.nextLine().trim();

                if (input.equalsIgnoreCase("/quit")) {
                    os.println(Protocol.CMD_EXIT);
                    break;
                }

                if (!input.isEmpty()) {
                    // 입력값 파싱 (in 1234 -> type=in, car=1234)
                    String[] parts = input.split(" ");
                    if (parts.length < 2) {
                        System.out.println("형식이 잘못되었습니다. (예: in 1234)");
                        continue;
                    }
                    String type = parts[0];
                    String carNum = parts[1];

                    if (type.equalsIgnoreCase("in")) {
                        // 입차 신호 (프로토콜: LPR_IN:차번호)
                        os.println("LPR_IN:" + carNum);
                        System.out.println("[전송] 입차 -> " + carNum);
                    } else if (type.equalsIgnoreCase("out")) {
                        // 출차 신호 (프로토콜: LPR_OUT:차번호)
                        os.println("LPR_OUT:" + carNum);
                        System.out.println("[전송] 출차 -> " + carNum);
                    } else {
                        System.out.println("알 수 없는 명령어입니다.");
                    }
                }
            }
            socket.close();
            sc.close();
        } catch (IOException e) {
            System.out.println("Connection Error: " + e.getMessage());
        }
    }
}