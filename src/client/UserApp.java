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
import utils.Protocol;

public class UserApp extends JFrame {

    // 화면 전환을 위한 CardLayout 관리 변수
    private CardLayout cardLayout;
    private JPanel mainContainer; // 모든 화면을 담을 그릇

    // 네트워크 관련 변수
    private Socket socket;
    private PrintStream os;
    private String myCarNum;

    // 로그 표시용 컴포넌트
    private JTextArea chatArea;
    private JTextField inputField;

    public UserApp() {
        // 1. 윈도우 기본 설정
        setTitle("Smart Parking System - Client");
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // 화면 가운데 띄우기

        // 2. 로그인 (차량 번호 입력)
        // 프로그램 시작하자마자 입력을 받습니다.
        myCarNum = JOptionPane.showInputDialog(this, "차량 번호를 입력하세요:", "로그인", JOptionPane.QUESTION_MESSAGE);

        if (myCarNum == null || myCarNum.trim().isEmpty()) {
            System.exit(0); // 취소하거나 빈 값이면 종료
        }

        // 3. 서버 연결 시도
        connectToServer();

        // 4. 화면 구성 (CardLayout 적용)
        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);

        // [변경] 메뉴 패널과 메인(채팅/기능) 패널 2개로 단순화
        mainContainer.add(createMenuPanel(), "MENU");
        mainContainer.add(createMainPanel(), "MAIN"); // EXIT + NAV 통합

        add(mainContainer); // 프레임에 장착

        // 처음에는 메뉴 화면을 보여줌
        cardLayout.show(mainContainer, "MENU");

        setVisible(true);
    }

    // [화면 1] 메인 메뉴 패널 생성
    private JPanel createMenuPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(50, 50, 50, 50));

        JLabel titleLabel = new JLabel("Smart Parking Service", SwingConstants.CENTER);
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 20));

        JButton btnStart = new JButton("주차 시스템 접속");
        btnStart.setFont(new Font("맑은 고딕", Font.BOLD, 16));

        btnStart.addActionListener(e -> {
            cardLayout.show(mainContainer, "MAIN"); // 메인 화면으로 이동
        });

        panel.add(titleLabel);
        panel.add(btnStart);
        return panel;
    }

    // [화면 2] 길안내,출차,결제 패널
    private JPanel createMainPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // 1. 상단: 기능 버튼들
        JPanel topPanel = new JPanel(new FlowLayout());
        JButton btnNav = new JButton("🗺️ 길 안내 요청");
        JButton btnExit = new JButton("🚪 메뉴로");

        btnNav.setBackground(new Color(200, 230, 255)); // 연한 파랑

        // 길 안내 요청 버튼: 화면 전환 없이 바로 요청 전송
        btnNav.addActionListener(e -> {
            if (os != null) {
                chatArea.append("[Me] 길 안내를 요청합니다.\n");
                os.println(Protocol.REQ_NAV);
            }
        });

        btnExit.addActionListener(e -> cardLayout.show(mainContainer, "MENU"));

        topPanel.add(btnNav);
        topPanel.add(btnExit);
        panel.add(topPanel, BorderLayout.NORTH);

        // 2. 중앙: 통합 로그창 (팀원 화면 스타일)
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 14)); // 고정폭 글꼴 추천
        panel.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        // 3. 하단: 입력창 (신고/채팅/도움)
        JPanel bottomPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));

        JButton btnSend = new JButton("전송");
        JButton btnHelp = new JButton("🆘도움");
        btnHelp.setBackground(Color.ORANGE);
        JButton btnReport = new JButton("🚨신고");
        btnReport.setBackground(Color.PINK);

        btnPanel.add(btnSend);
        btnPanel.add(btnHelp);
        btnPanel.add(btnReport);

        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(btnPanel, BorderLayout.EAST);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        // 이벤트 리스너
        ActionListener sendAction = e -> sendMessage();
        inputField.addActionListener(sendAction);
        btnSend.addActionListener(sendAction);

        btnHelp.addActionListener(e -> {
            os.println("/help"); // 서버로 명령어 전송
            chatArea.append("[Me] (🆘긴급) 관리자에게 도움을 요청했습니다.\n");
        });

        btnReport.addActionListener(e -> {
            String input = inputField.getText();
            if(input.isEmpty()) {
                JOptionPane.showMessageDialog(this, "신고 내용을 입력하세요.");
                return;
            }
            os.println("/report " + input);
            chatArea.append("[Me] (🚨신고) " + input + "\n");
            inputField.setText("");
        });

        return panel;
    }

    // 네트워크 및 기능 로직
    private void connectToServer() {
        String host = "10.101.17.72";
        int port = 8888;

        try {
            socket = new Socket(host, port);
            os = new PrintStream(socket.getOutputStream());

            // 로그인 패킷 전송
            os.println(Protocol.LOGIN_USER + myCarNum);

            // 수신 스레드 시작
            new ReceiveThread(socket).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "서버 연결 실패: " + e.getMessage());
            System.exit(0);
        }
    }

    private void sendMessage() {
        String input = inputField.getText();
        if (input.isEmpty()) return;

        chatArea.append("[Me] " + input + "\n");
        os.println(input);
        inputField.setText("");
        // 자동 스크롤
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // 내부 클래스: 수신 스레드
    class ReceiveThread extends Thread {
        private BufferedReader reader;

        public ReceiveThread(Socket socket) {
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            } catch (IOException e) {}
        }

        @Override
        public void run() {
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    String msg = line;
                    SwingUtilities.invokeLater(() -> {
                        // 1. [좌표 숨김] 팀원 화면처럼 좌표는 안 보이게 처리
                        if (msg.startsWith(Protocol.NAV_COORD)) {
                            // (나중에 지도 기능을 쓴다면 여기서 coords 파싱해서 사용)
                            return; // ★ 화면에 출력하지 않고 종료
                        }

                        // 2. [종료 신호]
                        if (msg.equals(Protocol.NAV_END)) {
                            // 안내 종료 메시지는 띄워줌 (선택 사항)
                            // chatArea.append("--- 안내가 종료되었습니다 ---\n");
                            return;
                        }

                        // 3. [결제 알림]
                        if (msg.equals(Protocol.MSG_PAYMENT)) {
                            // 팝업은 띄우되, 로그에는 따로 안 남겨도 됨 (팀원 화면 참고)
                            int choice = JOptionPane.showOptionDialog(UserApp.this,
                                    "차량이 인식되었습니다. 자동 결제 하시겠습니까?",
                                    "결제", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                                    null, new Object[]{"예", "아니오"}, "예");
                            // (결제 로직 생략...)
                            return;
                        }

                        // 4. [모든 메시지 출력] 위에서 걸러지지 않은 텍스트(안내 멘트 등)는 채팅창에 표시
                        if (chatArea != null && !msg.startsWith(Protocol.LOGIN_USER)) {
                            chatArea.append(msg + "\n");
                            chatArea.setCaretPosition(chatArea.getDocument().getLength());
                        }
                    });
                }
            } catch (IOException e) {}
        }
    }

    public static void main(String[] args) {
        new UserApp();
    }
}