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

    // [화면 1] 메인 메뉴 패널 생성
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

    // [화면 2] 출차/결제 패널
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

    // [화면 3] 길 안내 패널
    private JTextArea navLogArea; // 로그 출력용

    private JPanel createNavigationPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // 상단: 안내 문구
        JLabel titleLabel = new JLabel("실시간 주차 길 안내 서비스", SwingConstants.CENTER);
        titleLabel.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        // 중앙: 주행 로그/지도 화면 (팀원의 ChatFrame ui 참고)
        navLogArea = new JTextArea();
        navLogArea.setEditable(false);
        navLogArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        navLogArea.setText("안내 시작 버튼을 누르면\n서버로부터 경로를 수신합니다.\n\n");
        panel.add(new JScrollPane(navLogArea), BorderLayout.CENTER);

        // 하단: 조작 버튼
        JPanel bottomPanel = new JPanel(new FlowLayout());

        JButton btnStart = new JButton("안내 시작");
        JButton btnBack = new JButton("메인 메뉴");

        // [이벤트] 안내 시작 버튼 -> 서버에 "REQ:NAV" 전송
        btnStart.addActionListener(e -> {
            if (os != null) {
                navLogArea.setText("[System] 경로 탐색 요청 중...\n");
                os.println(Protocol.REQ_NAV); // 서버로 요청 전송
            }
        });

        // [이벤트] 메인 메뉴 복귀
        btnBack.addActionListener(e -> cardLayout.show(mainContainer, "MENU"));

        bottomPanel.add(btnStart);
        bottomPanel.add(btnBack);

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(bottomPanel, BorderLayout.SOUTH);

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

                        // 길 안내 로그 (길 안내 화면용)
                        // NAV:COORD:10,20 형태의 메시지가 오면 파싱해서 출력
                        if (msg.startsWith(Protocol.NAV_COORD)) {
                            String coords = msg.split(":")[2]; // "10,20" 추출
                            if (navLogArea != null) {
                                navLogArea.append("🚗 자율 주행 중... 현재 좌표: (" + coords + ")\n");
                                navLogArea.setCaretPosition(navLogArea.getDocument().getLength());
                            }
                        }
                        // 도착 메시지 처리
                        else if (msg.equals(Protocol.NAV_END)) {
                            if (navLogArea != null) {
                                navLogArea.append("🏁 목적지에 도착하여 주차를 완료했습니다.\n");
                                JOptionPane.showMessageDialog(UserApp.this, "주차가 완료되었습니다!");
                            }
                        }
                        // 그 외 일반 메시지는 로그창에 띄움
                        else if (!msg.startsWith(Protocol.LOGIN_USER) && navLogArea != null) {
                            // (서버가 보내는 일반 텍스트 메시지도 길안내 로그에 표시)
                            // 단, LPR 관련 메시지 등은 제외하고 싶으면 조건 추가 가능
                            if(!msg.startsWith("NOTI:")) {
                                navLogArea.append(msg + "\n");
                            }
                        }

                        // 2. 결제 완료 처리 (화면이 어디에 있든 팝업은 떠야 함)
                        // [수정된 부분] 결제 신호 수신 시 바로 완료 처리하지 않고, 선택창을 띄움
                        if (msg.equals(Protocol.MSG_PAYMENT)) {
                            // 1. 사용자에게 결제 방식 물어보기 (팝업창)
                            int choice = JOptionPane.showOptionDialog(
                                    UserApp.this,
                                    "출차 차량이 감지되었습니다.\n등록된 '자동 결제' 수단으로 결제하시겠습니까?", // 내용
                                    "결제 방식 선택", // 제목
                                    JOptionPane.YES_NO_OPTION, // 버튼 종류 (예/아니오)
                                    JOptionPane.QUESTION_MESSAGE, // 아이콘 모양
                                    null,
                                    new Object[]{"예 (자동 결제)", "아니오 (다른 수단)"}, // 버튼 글자 커스텀
                                    "예 (자동 결제)" // 기본 선택값
                            );

                            // 2. 선택에 따른 처리
                            if (choice == JOptionPane.YES_OPTION) {
                                // [자동 결제 선택 시]
                                // 실제로는 여기서 서버에 "자동결제 진행해줘"라는 패킷을 보내야 하지만,
                                // 지금은 완료되었다고 가정하고 메시지를 띄웁니다.
                                JOptionPane.showMessageDialog(UserApp.this,
                                        "등록된 카드로 결제가 완료되었습니다.\n안녕히 가십시오 (출차 가능)",
                                        "결제 성공",
                                        JOptionPane.INFORMATION_MESSAGE);

                            } else {
                                // [다른 수단 선택 시]
                                // 현장 결제나 다른 앱 결제를 유도하는 메시지
                                JOptionPane.showMessageDialog(UserApp.this,
                                        "자동 결제가 취소되었습니다.\n출구 정산기에서 직접 결제해주세요.",
                                        "일반 결제 전환",
                                        JOptionPane.WARNING_MESSAGE);
                            }
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