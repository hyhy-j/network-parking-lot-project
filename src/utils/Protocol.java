package utils;

public class Protocol {
    // 명령어 프로토콜 정의
    public static final String LOGIN_LPR = "LOGIN:LPR";       // LPR 카메라 로그인 헤더
    public static final String LOGIN_USER = "LOGIN:USER:";    // 유저 앱 로그인 헤더 (뒤에 차번호 붙음)
    public static final String DETECT_CAR = "DETECT:";        // LPR이 차량 인식 시 보내는 헤더
    public static final String MSG_PAYMENT = "NOTI:PAYMENT_SUCCESS"; // 결제 완료 알림 메시지

    // 종료 메시지
    public static final String CMD_EXIT = "/quit";
}
