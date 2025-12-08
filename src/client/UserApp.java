package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import utils.Protocol;

public class UserApp extends JFrame {

    // 화면 전환을 위한 CardLayout
    private CardLayout cardLayout;
    private JPanel mainContainer;

    // 네트워크 변수
    private Socket socket;
    private PrintStream os;
    private String myCarNum;

    // UI 컴포넌트
    private JTextArea chatArea;
    private JTextField inputField;

    // ★ [핵심] 결제 상태 플래그 (채팅 입력 시 결제 응답인지 확인용)
    private boolean isWaitingForPayment = false;

    public UserApp() {
        setTitle("Smart Parking System - Client");
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // 1. 로그인
        myCarNum = JOptionPane.showInputDialog(this,
                "차량 번호를 입력하세요:\n(1000~1999: 교수 / 2000~2999: 학생 / 그외: 방문객)",
                "주차 시스템 로그인", JOptionPane.QUESTION_MESSAGE);

        if (myCarNum == null || myCarNum.trim().isEmpty()) {
            System.exit(0);
        }

        // 2. 서버 연결
        connectToServer();

        // 3. UI 구성
        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);

        mainContainer.add(createMenuPanel(), "MENU");
        mainContainer.add(createMainPanel(), "MAIN");

        add(mainContainer);
        cardLayout.show(mainContainer, "MENU");

        setVisible(true);
    }

    // [화면 1] 메인 메뉴
    private JPanel createMenuPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(50, 50, 50, 50));

        JLabel titleLabel = new JLabel("Smart Parking Service", SwingConstants.CENTER);
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 24));

        JButton btnStart = new JButton("주차 시스템 접속");
        btnStart.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        btnStart.setBackground(new Color(230, 240, 255));

        btnStart.addActionListener(e -> {
            cardLayout.show(mainContainer, "MAIN");
            // 접속 시 안내 멘트 출력 (처음 한 번만)
            if(chatArea.getText().isEmpty()) {
                chatArea.append("[System] 주차 관제 시스템에 접속했습니다.\n");
                chatArea.append("[System] '길 안내 요청'을 누르거나 채팅을 입력하세요.\n");
            }
        });

        panel.add(titleLabel);
        panel.add(btnStart);
        return panel;
    }

    // [화면 2] 통합 메인 패널 (길안내 + 채팅 + 결제)
    private JPanel createMainPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // 1. 상단 버튼
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnNav = new JButton("🗺️ 길 안내 요청");
        JButton btnExit = new JButton("🚪 메뉴로");
        btnNav.setBackground(new Color(200, 255, 200));

        btnNav.addActionListener(e -> {
            if (os != null) {
                chatArea.setText(""); // 화면 정리
                chatArea.append("[Me] 길 안내를 요청합니다.\n");
                os.println(Protocol.REQ_NAV); // 서버로 요청 전송
            }
        });

        btnExit.addActionListener(e -> cardLayout.show(mainContainer, "MENU"));

        topPanel.add(btnExit);
        topPanel.add(btnNav);
        panel.add(topPanel, BorderLayout.NORTH);

        // 2. 중앙 로그창
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        chatArea.setLineWrap(true);
        panel.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        // 3. 하단 입력창
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
            os.println("/help");
            chatArea.append("[Me] (🆘긴급) 도움 요청 전송\n");
        });

        btnReport.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(this, "신고 내용을 입력하세요:");
            if(input != null && !input.trim().isEmpty()) {
                os.println("/report " + input);
                chatArea.append("[Me] (🚨신고) " + input + "\n");
            }
        });

        return panel;
    }

    private void connectToServer() {
        // ★ 본인의 서버 IP로 꼭 변경하세요!
        String host = "10.101.48.65";
        int port = 8888;

        try {
            socket = new Socket(host, port);
            // 한글 깨짐 방지 & AutoFlush 설정
            os = new PrintStream(socket.getOutputStream(), true, "UTF-8");

            os.println(Protocol.LOGIN_USER + myCarNum);
            new ReceiveThread(socket).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "서버 연결 실패: " + e.getMessage());
            System.exit(0);
        }
    }

    // ★ [핵심] 메시지 전송 로직 (결제 인터셉트 기능 포함)
    private void sendMessage() {
        String input = inputField.getText();
        if (input.isEmpty()) return;

        // 1. [결제 대기 상태]일 때 -> 로컬에서 처리 (서버로 안 보냄)
        if (isWaitingForPayment) {
            chatArea.append("[Me] " + input + "\n");

            if (input.equalsIgnoreCase("y") || input.equals("예")) {
                processPaymentPopup(); // 팝업 띄우기
            } else {
                // 결제 취소 시 -> 안내 메시지 후 메뉴로 이동
                chatArea.append("--------------------------------\n");
                chatArea.append("[System] 결제를 보류했습니다.\n");
                chatArea.append("         메뉴 화면으로 이동합니다.\n");
                chatArea.append("--------------------------------\n");

                isWaitingForPayment = false;
                inputField.setText("");

                // ★ 홈 화면으로 강제 이동
                cardLayout.show(mainContainer, "MENU");
                return;
            }

            isWaitingForPayment = false;
            inputField.setText("");
            return;
        }

        // 2. [일반 상태] -> 서버로 전송 (길 안내 답변 포함)
        chatArea.append("[Me] " + input + "\n");
        os.println(input);
        inputField.setText("");

        // 자동 스크롤
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // 결제 팝업창
    private void processPaymentPopup() {
        int choice = JOptionPane.showOptionDialog(
                UserApp.this,
                "결제 방식을 선택해주세요.\n(총 금액: 12,000원)",
                "결제 수단 선택",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new Object[]{"💳 카드 결제", "💵 현장 결제"},
                "💳 카드 결제"
        );

        if (choice == JOptionPane.YES_OPTION) {
            chatArea.append("[System] 카드 결제가 완료되었습니다. 안녕히 가세요!\n");
            JOptionPane.showMessageDialog(UserApp.this, "결제 완료! 차단기가 열렸습니다.");
        } else {
            chatArea.append("[System] 현장 결제/기타 수단을 선택하셨습니다.\n");
            JOptionPane.showMessageDialog(UserApp.this, "출구 정산기를 이용해주세요.");
        }
        isWaitingForPayment = false;
    }

    // 수신 스레드
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

                        // 1. 좌표 데이터 숨김 (원할 경우 주석 해제하여 확인 가능)
                        if (msg.startsWith(Protocol.NAV_COORD)) {
                            // System.out.println("좌표 수신: " + msg);
                            return;
                        }

                        // 2. 길 안내 종료
                        if (msg.equals(Protocol.NAV_END)) {
                            chatArea.append("🏁 목적지에 도착했습니다.\n");
                            JOptionPane.showMessageDialog(UserApp.this, "안내가 종료되었습니다.");
                            return;
                        }

                        // 3. ★ [결제 요청 수신] -> 채팅창에 상세 내역 출력
                        if (msg.equals(Protocol.MSG_PAYMENT)) {
                            chatArea.append("\n================================\n");
                            chatArea.append("📢 [출차 알림] 차량이 인식되었습니다.\n");
                            chatArea.append(" - 차량 번호: " + myCarNum + "\n");
                            chatArea.append(" - 총 이용 시간: 3시간 15분\n");
                            chatArea.append(" - 결제 예정 금액: 12,000원\n");
                            chatArea.append("--------------------------------\n");
                            chatArea.append("결제하시겠습니까? (y/n)\n");
                            chatArea.append("================================\n");

                            // 스크롤 맨 아래로
                            chatArea.setCaretPosition(chatArea.getDocument().getLength());

                            // ★ 상태 변경: 다음 입력은 결제 응답으로 처리
                            isWaitingForPayment = true;
                            return;
                        }

                        // 4. 그 외 모든 서버 메시지 (길안내 멘트, 채팅 등) 출력
                        if (!msg.startsWith(Protocol.LOGIN_USER)) {
                            chatArea.append(msg + "\n");
                            chatArea.setCaretPosition(chatArea.getDocument().getLength());
                        }
                    });
                }
            } catch (IOException e) {
                chatArea.append("[System] 서버와의 연결이 끊어졌습니다.\n");
            }
        }
    }

    public static void main(String[] args) {
        new UserApp();
    }
}