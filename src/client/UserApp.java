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

    private CardLayout cardLayout;
    private JPanel mainContainer;
    private Socket socket;
    private PrintStream os;
    private String myCarNum;

    private JTextArea chatArea;
    private JTextField inputField;

    private boolean isWaitingForPayment = false;

    public UserApp() {
        setTitle("Smart Parking System - Client");
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        myCarNum = JOptionPane.showInputDialog(this,
                "차량 번호를 입력하세요.",
                "주차 시스템 로그인", JOptionPane.QUESTION_MESSAGE);

        if (myCarNum == null || myCarNum.trim().isEmpty()) {
            System.exit(0);
        }

        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);

        mainContainer.add(createMenuPanel(), "MENU");
        mainContainer.add(createMainPanel(), "MAIN");

        add(mainContainer);
        cardLayout.show(mainContainer, "MENU");

        setVisible(true);

        connectToServer();
    }

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
        });

        panel.add(titleLabel);
        panel.add(btnStart);
        return panel;
    }

    // [화면 2] 통합 메인 패널 (버튼 삭제됨)
    private JPanel createMainPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // 1. 상단: 메뉴 복귀 버튼만 남김
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnExit = new JButton("🚪 메뉴로 나가기");

        // 버튼 디자인 (선택사항)
        btnExit.setBackground(new Color(255, 230, 230));
        btnExit.setFocusPainted(false);

        btnExit.addActionListener(e -> {
            // 메뉴로 나갈 때 화면 클리어 (선택사항)
            chatArea.setText("");
            cardLayout.show(mainContainer, "MENU");
        });

        topPanel.add(btnExit);
        // btnNav 관련 코드(버튼 생성, 리스너, add) 모두 삭제함

        panel.add(topPanel, BorderLayout.NORTH);

        // 2. 중앙: 통합 로그창
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        chatArea.setLineWrap(true);
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
            os.println("/help");
            chatArea.append("[Me] (🆘긴급) 도움 요청 전송\n");
        });

        btnReport.addActionListener(e -> {
            String input = inputField.getText(); // 1. 채팅창 내용을 가져옴

            // 내용이 비어있으면 안내창 띄우기
            if (input.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "신고할 내용을 입력창에 먼저 적어주세요.");
                return;
            }

            // 2. 서버로 신고 접수 (내용과 함께)
            os.println("/report " + input);
            chatArea.append("[Me] (🚨신고) " + input + "\n");

            // 3. 입력창 비우기
            inputField.setText("");
        });

        return panel;
    }

    private void connectToServer() {
        String host = "192.168.35.247";
        int port = 8888;

        try {
            socket = new Socket(host, port);
            os = new PrintStream(socket.getOutputStream(), true, "UTF-8");
            os.println(Protocol.LOGIN_USER + myCarNum);
            new ReceiveThread(socket).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "서버 연결 실패: " + e.getMessage());
            System.exit(0);
        }
    }

    private void sendMessage() {
        String input = inputField.getText();
        if (input.isEmpty()) return;

        // 1. 결제 대기 중 (y/n 입력)
        if (isWaitingForPayment) {
            chatArea.append("[Me] " + input + "\n");

            if (input.equalsIgnoreCase("y") || input.equals("예")) {
                processPaymentPopup();
            } else {
                chatArea.append("--------------------------------\n");
                chatArea.append("[System] 결제를 보류했습니다.\n");
                chatArea.append("         메뉴 화면으로 이동합니다.\n");
                chatArea.append("--------------------------------\n");

                isWaitingForPayment = false;
                inputField.setText("");
                cardLayout.show(mainContainer, "MENU");
                return;
            }

            isWaitingForPayment = false;
            inputField.setText("");
            return;
        }

        // 2. 일반 채팅
        chatArea.append("[Me] " + input + "\n");
        os.println(input);
        inputField.setText("");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

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

        //결제 후 초기 화면으로 이동
        //다음 이용을 위해 채팅창 내용 초기화
        chatArea.setText("");

        // 화면을 'MENU' (초기 접속 화면) 카드로 전환
        cardLayout.show(mainContainer, "MENU");
    }

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

                        // ★ [1] 입차 알림 (ENTRY) -> 길안내 시작 권유
                        if (msg.equals("ENTRY")) {
                            chatArea.append("\n=== 📢 [알림] 주차장 입차 확인 ===\n");
                            chatArea.append("환영합니다! 빈 자리로 안내해 드릴까요?\n");
                            chatArea.setCaretPosition(chatArea.getDocument().getLength());

                            int choice = JOptionPane.showConfirmDialog(UserApp.this,
                                    "주차장에 입차하셨습니다.\n추천 구역으로 길 안내를 시작할까요?",
                                    "입차 알림", JOptionPane.YES_NO_OPTION);

                            if (choice == JOptionPane.YES_OPTION) {
                                chatArea.append("[Me] 네, 길 안내를 시작해주세요.\n");
                                os.println(Protocol.REQ_NAV); // 길안내 요청 전송
                            } else {
                                chatArea.append("[Me] 아니오, 괜찮습니다.\n");
                            }
                            return;
                        }

                        // ★ [2] 출차/결제 알림
                        if (msg.equals(Protocol.MSG_PAYMENT)) {
                            chatArea.append("\n================================\n");
                            chatArea.append("📢 [출차 알림] 차량이 인식되었습니다.\n");
                            chatArea.append(" - 차량 번호: " + myCarNum + "\n");
                            chatArea.append(" - 총 이용 시간: 3시간 15분\n");
                            chatArea.append(" - 결제 예정 금액: 12,000원\n");
                            chatArea.append("--------------------------------\n");
                            chatArea.append("결제하시겠습니까? (y/n)\n");
                            chatArea.append("================================\n");

                            chatArea.setCaretPosition(chatArea.getDocument().getLength());
                            isWaitingForPayment = true;
                            return;
                        }

                        // [3] 좌표 정보 (숨김)
                        if (msg.startsWith(Protocol.NAV_COORD)) {
                            return;
                        }

                        // [4] 길 안내 종료
                        if (msg.equals(Protocol.NAV_END)) {
                            chatArea.append("🏁 목적지에 도착했습니다.\n");
                            JOptionPane.showMessageDialog(UserApp.this, "안내가 종료되었습니다.");
                            return;
                        }

                        // [5] 일반 메시지 출력
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