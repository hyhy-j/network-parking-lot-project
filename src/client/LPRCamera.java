package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Scanner;
import utils.Protocol;

public class LPRCamera {

    // LPR은 주로 보내기만 하므로 수신 로직은 간단히 처리
    static class ServerListener extends Thread {
        BufferedReader reader;
        public ServerListener(Socket s) throws IOException {
            reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
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
        String host = "10.101.17.72";
        int port = 8888;
        Scanner sc = new Scanner(System.in);

        System.out.println("=== LPR Camera Simulator (IoT) ===");

        try {
            Socket socket = new Socket(host, port);
            PrintStream os = new PrintStream(socket.getOutputStream());

            // 서버 응답 듣는 리스너 시작
            new ServerListener(socket).start();

            // 1. LPR 로그인 전송
            System.out.println("Connecting to server as LPR Camera...");
            os.println(Protocol.LOGIN_LPR);

            System.out.println("Ready. Type car number to simulate detection (e.g., 1234)");
            System.out.println("Type '/quit' to exit.");

            // 2. 사용자 입력으로 차량 인식 시뮬레이션
            while (true) {
                System.out.print("Detected Car Number > ");
                String input = sc.nextLine();

                if (input.equalsIgnoreCase("/quit")) {
                    os.println(Protocol.CMD_EXIT);
                    break;
                }

                if (!input.isEmpty()) {
                    // 서버로 "DETECT:차번호" 전송
                    os.println(Protocol.DETECT_CAR + input);
                }
            }

            socket.close();
            sc.close();
        } catch (IOException e) {
            System.out.println("Connection Error: " + e.getMessage());
        }
    }
}