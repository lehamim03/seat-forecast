package kr.ac.kookmin.seatforecast.account.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 마이페이지에서 고를 수 있는 정류장 · 노선 목록. 저장소 루트 config.py 의 STOPS · ROUTES 와 같게 유지합니다.
 * 정류장 이름은 공공데이터 API 이름 그대로 씁니다 (docs/api.md 공통 규칙).
 */
public final class Stops {

    public static final class Stop {
        public final String id;
        public final String name;
        public final String mobileNo;
        public final int walkMin;
        /** 이 정류장에 서는 노선 */
        public final List<String> routes;

        Stop(String id, String name, String mobileNo, int walkMin, String... routes) {
            this.id = id;
            this.name = name;
            this.mobileNo = mobileNo;
            this.walkMin = walkMin;
            this.routes = Collections.unmodifiableList(Arrays.asList(routes));
        }

        /** 화면 표시용: "삼성1차아파트 (04007)" */
        public String label() {
            return name + " (" + mobileNo + ")";
        }

        @Override
        public String toString() {
            return label();
        }
    }

    public static final List<Stop> ALL = Collections.unmodifiableList(Arrays.asList(
            new Stop("203000063", "삼성1차아파트", "04007", 0, "8800", "3008", "3007", "7001"),
            new Stop("203000161", "자유총연맹", "04236", 3, "3008", "3007", "7001"),
            new Stop("203000162", "매탄위브하늘채.경기아트센터", "04235", 7, "8800", "3008", "3007", "7001")
    ));

    public static final List<String> ROUTES = Collections.unmodifiableList(
            Arrays.asList("8800", "3007", "7001", "3008"));

    public static final String DESTINATION_NAME = "국민대학교";
    public static final double DESTINATION_X = 126.9971;
    public static final double DESTINATION_Y = 37.6108;

    private Stops() {
    }

    public static Stop find(String id) {
        for (Stop s : ALL) {
            if (s.id.equals(id)) return s;
        }
        return null;
    }

    public static String nameOf(String id) {
        Stop s = find(id);
        return s == null ? id : s.name;
    }

    /** 정류장에 서는 노선만 (노선 순서는 ROUTES 순서) */
    public static List<String> routesAt(String stopId) {
        Stop s = find(stopId);
        List<String> out = new ArrayList<>();
        for (String r : ROUTES) {
            if (s == null || s.routes.contains(r)) out.add(r);
        }
        return out;
    }
}
