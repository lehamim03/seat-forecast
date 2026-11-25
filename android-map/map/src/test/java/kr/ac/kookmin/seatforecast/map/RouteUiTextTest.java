package kr.ac.kookmin.seatforecast.map;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

/** 지도 카드 문구 — 노선 이름 · 입석 가능 · 좌석 정보 없음 */
public class RouteUiTextTest {
    @Test
    public void routeNames() throws Exception {
        assertEquals("8800번", RouteUi.routeName("8800"));
        assertEquals("62-1번", RouteUi.routeName("62-1"));
        assertEquals("M4102번", RouteUi.routeName("M4102"));
        assertEquals("수인분당선", RouteUi.routeName("수인분당선"));
        assertEquals("4호선", RouteUi.routeName("4호선"));
        assertEquals("수인.분당선", RouteUi.routeName(new JSONObject("{\"route\": \"수인.분당선\", \"first_mode\": \"subway\"}")));
    }

    @Test
    public void probability() throws Exception {
        assertEquals("입석 가능", RouteUi.probability(new JSONObject("{\"board_prob\": 1.0, \"seat_limited\": false}")));
        assertEquals("좌석 정보 없음",
                RouteUi.probability(new JSONObject("{\"board_prob\": 0.98, \"seat_limited\": true, \"seat_known\": false}")));
        assertEquals("탑승 73%",
                RouteUi.probability(new JSONObject("{\"board_prob\": 0.73, \"seat_limited\": true, \"seat_known\": true}")));
        assertEquals("탑승 55%", RouteUi.probability(new JSONObject("{\"board_prob\": 0.55}")));   // 예전 응답
    }
}
