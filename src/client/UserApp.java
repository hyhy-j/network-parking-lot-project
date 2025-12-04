package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import utils.Protocol; // 기존 프로토콜 패키지 사용

public class UserApp extends JFrame {

    // GUI 컴포넌트 선언
    private JTextArea chatArea;  // 채팅/로그가 출력될 화면
    private JTextField inputField; // 명령어 입력창
    private JButton sendButton;    // 전송 버튼

    // 네트워크 관련 변수
    private Socket socket;
    private PrintStream os;
    private String myCarNum;

    public UserApp() {
        // 1. 윈도우(프레임) 기본 설정
        setTitle("Smart Parking System - Client");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // 창 닫으면 프로그램 종료
        setLayout(new BorderLayout());

        // 2. 화면 구성 (가운데: 채팅창, 아래: 입력창)
        chatArea = new JTextArea();
        chatArea.setEditable(false); // 사용자가 수정 못하게 막음
        add(new JScrollPane(chatArea), BorderLayout.CENTER); // 스크롤 기능 추가

        JPanel bottomPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        sendButton = new JButton("Send");
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        // 3. 이벤트 리스너 등록 (엔터키 치거나 버튼 누르면 전송)
        ActionListener sendAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        };
        inputField.addActionListener(sendAction);
        sendButton.addActionListener(sendAction);

        // 4. 창 보이기 및 로그인/연결 시도
        setVisible(true);
        connectToServer();
    }

    // 서버 연결 및 초기화
    private void connectToServer() {
        String host = "10.101.17.50";
        int port = 8888;

        // 간단하게 입력창(Dialog)을 띄워 차량번호를 받습니다.
        myCarNum = JOptionPane.showInputDialog(this, "차량 번호를 입력하세요:", "로그인", JOptionPane.QUESTION_MESSAGE);

        if (myCarNum == null || myCarNum.trim().isEmpty()) {
            System.exit(0); // 취소하면 종료
        }

        try {
            socket = new Socket(host, port);
            os = new PrintStream(socket.getOutputStream());

            // 로그인 패킷 전송
            os.println(Protocol.LOGIN_USER + myCarNum);
            chatArea.append("[System] 서버에 연결되었습니다. 차량번호: " + myCarNum + "\n");

            // 수신 스레드 시작
            new ReceiveThread(socket).start();

        } catch (IOException e) {
            chatArea.append("[Error] 서버 연결 실패: " + e.getMessage() + "\n");
        }
    }

    // 메시지 전송 로직
    private void sendMessage() {
        String input = inputField.getText();
        if (input.isEmpty()) return;

        if (input.equalsIgnoreCase("/quit")) {
            os.println(Protocol.CMD_EXIT);
            try { socket.close(); } catch (IOException e) {}
            System.exit(0);
        } else {
            // 일반 메시지라면 필요에 따라 전송 (현재는 프로토콜이 없으면 그냥 전송하거나 막을 수 있음)
            // os.println(input); // 서버가 채팅을 지원한다면 주석 해제
            chatArea.append("[Me] " + input + "\n"); // 내 화면에 표시
        }
        inputField.setText(""); // 입력창 비우기
    }

    // 내부 클래스: 수신 스레드 (GUI 버전)
    class ReceiveThread extends Thread {
        private BufferedReader reader;

        public ReceiveThread(Socket socket) {
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
                    String msg = line; // 람다식 등에서 쓰기 위해

                    // GUI 업데이트는 원래 Event Dispatch Thread에서 해야 안전함
                    SwingUtilities.invokeLater(() -> {
                        chatArea.append("[Server] " + msg + "\n");
                        chatArea.setCaretPosition(chatArea.getDocument().getLength()); // 자동 스크롤

                        // 결제 완료 처리
                        if (msg.equals(Protocol.MSG_PAYMENT)) {
                            JOptionPane.showMessageDialog(UserApp.this,
                                    "자동 결제가 완료되었습니다.\n안녕히 가십시오 (출차 가능)",
                                    "결제 알림",
                                    JOptionPane.INFORMATION_MESSAGE);
                        }
                    });
                }
            } catch (IOException e) {
                chatArea.append("[System] 서버와의 연결이 끊어졌습니다.\n");
            }
        }
    }

    public static void main(String[] args) {
        // Swing 프로그램 시작
        new UserApp();
    }
}