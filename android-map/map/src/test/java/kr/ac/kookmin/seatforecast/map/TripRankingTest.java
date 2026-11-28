package kr.ac.kookmin.seatforecast.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * 지도 1 · 2 · 3순위는 /recommendations ranking 순서 (A 홈 · B 대안과 같음).
 * 서버가 실측 추천을 합치면 /map 선은 ODsay 후보(92-1 …)로 남아 있어 순위와 짝이 안 맞을 수 있음 → 짝 없는 선은 순위로 쓰지 않음.
 */
public class TripRankingTest {

    private static JSONObject line(String route, String summary, boolean main) throws Exception {
        return new JSONObject().put("type", "Feature")
                .put("geometry", new JSONObject().put("type", "LineString")
                        .put("coordinates", new JSONArray("[[127.04,37.26],[127.0,37.4]]")))
                .put("properties", new JSONObject().put("kind", "transfer").put("route", route)
                        .put("summary", summary).put("main", main));
    }

    private static JSONObject option(String route, String stop, String summary) throws Exception {
        return new JSONObject().put("route", route).put("summary", summary)
                .put("stop", new JSONObject().put("id", "1").put("name", stop).put("lat", 37.266).put("lng", 127.041));
    }

    private static RouteData data() throws Exception {
        JSONObject geo = new JSONObject().put("type", "FeatureCollection").put("features", new JSONArray()
                .put(new JSONObject().put("type", "Feature")
                        .put("geometry", new JSONObject().put("type", "Point").put("coordinates", new JSONArray("[127.041,37.266]")))
                        .put("properties", new JSONObject().put("kind", "me").put("name", "매탄동")))
                .put(line("92-1", "버스 92-1 → 1호선", true))
                .put(line("13-1", "버스 13-1 → 수인.분당선", false))
                .put(line("3008", "버스 3008(삼성1차아파트→신논현역) → 버스 144", false)));
        JSONObject rec = new JSONObject().put("my_route", JSONObject.NULL)
                .put("recommendation", new JSONObject().put("route", "8800").put("stop_name", "삼성1차아파트"))
                .put("ranking", new JSONArray()
                        .put(option("8800", "삼성1차아파트", "버스 8800 → 버스 1711"))
                        .put(option("8800", "매탄위브하늘채", "버스 8800(매탄위브하늘채) → 버스 1711"))
                        .put(option("3008", "삼성1차아파트", "버스 3008(삼성1차아파트→신논현역) → 버스 144"))
                        .put(option("92-1", "매탄삼성1차아파트", "버스 92-1 → 1호선")));
        return new RouteData(geo, new JSONObject().put("routes", new JSONArray()), rec);
    }

    @Test
    public void topThreeFollowServerRanking() throws Exception {
        List<JSONObject> top = data().top();
        assertEquals(3, top.size());
        assertEquals("8800", top.get(0).getString("route"));
        assertEquals("매탄위브하늘채", top.get(1).getJSONObject("stop").getString("name"));
        assertEquals("3008", top.get(2).getString("route"));
    }

    @Test
    public void linesArePairedWithRankingNotMapOrder() throws Exception {
        RouteData d = data();
        List<JSONObject> top = d.top();
        assertNull(d.lineFor(top.get(0)));                 // 8800 은 /map 선이 없음
        assertEquals("3008", d.lineFor(top.get(2)).value("route"));
        List<RouteData.Feature> lines = d.topLines();
        assertEquals(1, lines.size());                     // 92-1 · 13-1 선은 1~3순위가 아니라 그리지 않음
        assertSame(d.lineFor(top.get(2)), lines.get(0));
        assertEquals(127.041, d.point("me").lon(), 1e-9);
    }
}
