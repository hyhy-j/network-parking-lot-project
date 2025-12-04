package server;

import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

public class ParkingServer {
    // 서버 소켓 및 클라이언트 소켓
    private static ServerSocket serverSocket = null;
    private static Socket clientSocket = null;

    // 최대 접속 가능 클라이언트 수
    private static final int maxClientsCount = 10;
    // 접속한 클라이언트 핸들러들을 관리하는 배열
    private static final ClientHandler[] threads = new ClientHandler[maxClientsCount];

    public static void main(String args[]) {
        // 포트 번호 설정
        int portNumber = 8888;

        try {
            serverSocket = new ServerSocket(portNumber);
            System.out.println("[Server] Parking Control System Started on port " + portNumber);
            System.out.println("[Server] Waiting for clients (LPR Camera / User App)...");
        } catch (IOException e) {
            System.out.println("Server Socket Error: " + e);
        }

        // 클라이언트 접속 대기 무한 루프
        while (true) {
            try {
                clientSocket = serverSocket.accept();
                int i = 0;
                for (i = 0; i < maxClientsCount; i++) {
                    if (threads[i] == null) {
                        // 빈 슬롯을 찾으면 핸들러 스레드 생성 및 시작
                        (threads[i] = new ClientHandler(clientSocket, threads)).start();
                        break;
                    }
                }

                // 정원이 꽉 찼을 경우
                if (i == maxClientsCount) {
                    PrintStream os = new PrintStream(clientSocket.getOutputStream());
                    os.println("Server too busy. Try later.");
                    os.close();
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.out.println(e);
            }
        }
    }
}