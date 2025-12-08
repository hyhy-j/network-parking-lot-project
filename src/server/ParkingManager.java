package server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 주차면 정보 클래스
class ParkingSpot {
    String id;
    int x, y;
    boolean isOccupied;

    public ParkingSpot(String id, int x, int y) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.isOccupied = false;
    }
}

// 주차장 매니저 (자리 배정 로직)
public class ParkingManager {
    static List<ParkingSpot> spots = new ArrayList<>();
    static Map<String, int[]> destinations = new HashMap<>();

    static {
        // 1. 주차면 데이터 (가상 좌표)
        spots.add(new ParkingSpot("A-1 (교수 전용)", 50, 100));
        spots.add(new ParkingSpot("B-1 (일반)", 50, 80));
        spots.add(new ParkingSpot("C-1 (학생/명신관)", -30, 40));
        spots.add(new ParkingSpot("D-1 (전기차)", 90, 90));
    }

    // 사용자 역할에 따라 가장 가까운 빈 자리 추천
    public static ParkingSpot recommendSpot(String role) {
        // 간단하게 역할별 선호 위치 지정
        int targetX = 0, targetY = 0;
        if("LPR".equals(role)) return null;

        if (role.equals("USER")) { // 기본값
            targetX = 10; targetY = 10;
        }

        // 실제로는 차 번호나 역할로 구분하지만, 여기선 단순화
        // 가까운 빈 자리 찾기 로직 (팀원 코드 기반)
        for (ParkingSpot spot : spots) {
            if (!spot.isOccupied) {
                // 테스트를 위해 무조건 첫 번째 빈 자리를 줌 (원하면 거리 계산 로직 추가 가능)
                spot.isOccupied = true;
                return spot;
            }
        }
        return null;
    }
}