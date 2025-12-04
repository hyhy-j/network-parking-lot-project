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

    // 출차(기존) 기능용 컴포넌트
    private JTextArea chatArea;
    private JTextField inputField;

    public UserApp() {
        // 1. 윈도우 기본 설정
        setTitle("Smart Parking System - Client");
        setSize(500, 400);
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

        // 각 화면(패널) 생성
        JPanel menuPanel = createMenuPanel();      // 기능 선택 화면
        JPanel exitPanel = createExitPanel();      // [본인 기능] 출차/결제 화면
        JPanel navPanel = createNavigationPanel(); // [팀원 기능] 길 안내 화면 (아직 빈 화면)

        // 메인 컨테이너에 패널들을 카드처럼 추가 (이름표 붙이기)
        mainContainer.add(menuPanel, "MENU");
        mainContainer.add(exitPanel, "EXIT");
        mainContainer.add(navPanel, "NAV");

        add(mainContainer); // 프레임에 장착

        // 처음에는 메뉴 화면을 보여줌
        cardLayout.show(mainContainer, "MENU");

        setVisible(true);
    }

    // --- [화면 1] 메인 메뉴 패널 생성 ---
    private JPanel createMenuPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 10, 10)); // 3행 1열
        panel.setBorder(BorderFactory.createEmptyBorder(50, 50, 50, 50)); // 여백

        JLabel titleLabel = new JLabel("원하시는 서비스를 선택하세요", SwingConstants.CENTER);
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 20));

        JButton btnExit = new JButton("출차 / 자동 결제");
        JButton btnNav = new JButton("주차장 길 안내");

        // 버튼 스타일 (옵션)
        btnExit.setFont(new Font("맑은 고딕", Font.PLAIN, 18));
        btnNav.setFont(new Font("맑은 고딕", Font.PLAIN, 18));

        // [이벤트] 출차 버튼 클릭 시 -> EXIT 화면으로 전환
        btnExit.addActionListener(e -> {
            cardLayout.show(mainContainer, "EXIT");
            setTitle("Smart Parking - 출차 모드");
        });

        // [이벤트] 길 안내 버튼 클릭 시 -> NAV 화면으로 전환
        btnNav.addActionListener(e -> {
            cardLayout.show(mainContainer, "NAV");
            setTitle("Smart Parking - 길 안내 모드");
        });

        panel.add(titleLabel);
        panel.add(btnExit);
        panel.add(btnNav);

        return panel;
    }

    // --- [화면 2] 출차/결제 패널 (기존 작성하신 코드) ---
    private JPanel createExitPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // 중앙: 채팅/로그창
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        // 로그인 성공 로그를 여기서 미리 찍어줄 수도 있음
        chatArea.append("[System] " + myCarNum + "님 환영합니다. 출차 대기 모드입니다.\n");
        panel.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        // 하단: 입력창과 전송 버튼
        JPanel bottomPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        JButton sendButton = new JButton("Send");

        // 뒤로가기(메뉴로) 버튼 추가 (선택사항)
        JButton backButton = new JButton("메뉴");
        backButton.addActionListener(e -> cardLayout.show(mainContainer, "MENU"));

        bottomPanel.add(backButton, BorderLayout.WEST);
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        panel.add(bottomPanel, BorderLayout.SOUTH);

        // 이벤트 리스너 (메시지 전송)
        ActionListener sendAction = e -> sendMessage();
        inputField.addActionListener(sendAction);
        sendButton.addActionListener(sendAction);

        return panel;
    }

    // --- [화면 3] 길 안내 패널 (팀원이 작업할 공간) ---
    private JPanel createNavigationPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JLabel tempLabel = new JLabel("길 안내 기능 준비 중입니다...", SwingConstants.CENTER);
        tempLabel.setFont(new Font("돋움", Font.BOLD, 15));

        // 테스트용: 메뉴로 돌아가는 버튼
        JButton btnBack = new JButton("메뉴로 돌아가기");
        btnBack.addActionListener(e -> cardLayout.show(mainContainer, "MENU"));

        panel.add(tempLabel, BorderLayout.CENTER);
        panel.add(btnBack, BorderLayout.SOUTH);

        // TODO: 나중에 팀원이 코드를 주면 이 panel 내부를 팀원 코드로 채워넣으면 됩니다.

        return panel;
    }

    // --- 네트워크 및 기능 로직 (기존과 동일) ---

    private void connectToServer() {
        String host = "10.101.17.50";
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

        if (input.equalsIgnoreCase("/quit")) {
            os.println(Protocol.CMD_EXIT);
            try { socket.close(); } catch (IOException e) {}
            System.exit(0);
        } else {
            chatArea.append("[Me] " + input + "\n");
            // os.println(input); // 필요 시 주석 해제
        }
        inputField.setText("");
    }

    // 내부 클래스: 수신 스레드
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
                    String msg = line;

                    SwingUtilities.invokeLater(() -> {
                        // 1. 로그창에 출력 (출차 패널에 있을 때만 보임)
                        if (chatArea != null) {
                            chatArea.append("[Server] " + msg + "\n");
                            chatArea.setCaretPosition(chatArea.getDocument().getLength());
                        }

                        // 2. 결제 완료 처리 (화면이 어디에 있든 팝업은 떠야 함)
                        if (msg.equals(Protocol.MSG_PAYMENT)) {
                            // 혹시 길 안내 중이어도 결제 알림이 오면 출차 화면으로 강제 이동시킬지 선택 가능
                            // cardLayout.show(mainContainer, "EXIT");

                            JOptionPane.showMessageDialog(UserApp.this,
                                    "자동 결제가 완료되었습니다.\n안녕히 가십시오 (출차 가능)",
                                    "결제 알림",
                                    JOptionPane.INFORMATION_MESSAGE);
                        }
                    });
                }
            } catch (IOException e) {
                // 연결 종료 시 처리
            }
        }
    }

    public static void main(String[] args) {
        new UserApp();
    }
}