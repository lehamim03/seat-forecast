package kr.ac.kookmin.seatforecast.home.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import kr.ac.kookmin.seatforecast.home.model.Option;
import kr.ac.kookmin.seatforecast.home.model.RouteChoice;

/**
 * 추천 목록 정렬 메뉴 (안드로이드 없이 단위 테스트 가능).
 * 기준 값이 같으면 서버 ranking 순서(도착 빠른 순)를 그대로 따르고,
 * 우회로 탈 수 없는 정류장({@code skipped})은 어떤 정렬이든 맨 뒤에 둡니다.
 */
enum OptionSort {

    /** 서버 ranking 그대로 — 도착 빠른 순 (같은 버스 · 비슷한 확률이면 가까운 정류장 먼저) */
    ARRIVAL,
    /** 갈아타는 횟수가 적은 순 ({@code transfers}, 없으면 route_choices 중 가장 적은 값) */
    FEWEST_TRANSFERS,
    /** 출발지에서 탈 곳까지 걷는 시간이 짧은 순 */
    NEAREST_STOP,
    /** 탈 수 있는 첫 차의 탑승 확률이 높은 순 */
    BOARD_PROB;

    /** 경로 정보가 없을 때 — 환승 적은 순에서 맨 뒤로 */
    static final int UNKNOWN_TRANSFERS = Integer.MAX_VALUE;

    /** ranking 을 이 기준으로 정렬한 새 목록 (원본은 그대로) */
    List<Option> apply(List<Option> ranking) {
        List<Option> out = new ArrayList<>();
        if (ranking == null) return out;
        for (Option o : ranking) if (o != null) out.add(o);
        Map<Option, Integer> serverOrder = new IdentityHashMap<>();
        for (int i = 0; i < out.size(); i++) serverOrder.put(out.get(i), i);

        Comparator<Option> c = Comparator.comparing((Option o) -> o.skipped);   // false(탈 수 있음) 먼저
        switch (this) {
            case FEWEST_TRANSFERS:
                c = c.thenComparingInt(OptionSort::fewestTransfers);
                break;
            case NEAREST_STOP:
                c = c.thenComparingInt(o -> o.walkMin);
                break;
            case BOARD_PROB:
                c = c.thenComparing(Comparator.comparingDouble((Option o) -> o.boardProb).reversed());
                break;
            case ARRIVAL:
            default:
                break;
        }
        Collections.sort(out, c.thenComparingInt(serverOrder::get));
        return out;
    }

    /** {@code transfers} (출발지 · 도착지 모드), 없으면 route_choices 중 가장 적은 값, 둘 다 없으면 {@link #UNKNOWN_TRANSFERS} */
    static int fewestTransfers(Option o) {
        if (o.transfers != null) return o.transfers;
        int min = UNKNOWN_TRANSFERS;
        if (o.routeChoices == null) return min;
        for (RouteChoice ch : o.routeChoices) {
            if (ch != null) min = Math.min(min, ch.transfers);
        }
        return min;
    }
}
