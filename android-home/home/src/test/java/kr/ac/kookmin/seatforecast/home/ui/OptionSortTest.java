package kr.ac.kookmin.seatforecast.home.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import kr.ac.kookmin.seatforecast.home.data.RecommendationJson;
import kr.ac.kookmin.seatforecast.home.model.Option;
import kr.ac.kookmin.seatforecast.home.model.RouteChoice;
import kr.ac.kookmin.seatforecast.home.model.Stop;

public class OptionSortTest {

    private static Option option(String name, int walk, double prob, int... transfers) {
        Option o = new Option();
        o.route = "3007";
        o.stop = new Stop();
        o.stop.name = name;
        o.walkMin = walk;
        o.boardProb = prob;
        for (int t : transfers) {
            RouteChoice ch = new RouteChoice();
            ch.transfers = t;
            o.routeChoices.add(ch);
        }
        return o;
    }

    private static List<String> names(List<Option> options) {
        List<String> out = new ArrayList<>();
        for (Option o : options) out.add(o.stop.name);
        return out;
    }

    // 서버 ranking 순서: A(도착 가장 빠름) → B → C → D
    private final Option a = option("A", 7, 0.50, 3, 2);
    private final Option b = option("B", 0, 0.80, 1);
    private final Option c = option("C", 3, 0.95, 2, 4);
    private final Option d = option("D", 0, 0.30);   // 경로 정보 없음
    private final List<Option> ranking = Arrays.asList(a, b, c, d);

    @Test
    public void arrivalKeepsServerOrder() {
        assertEquals(Arrays.asList("A", "B", "C", "D"), names(OptionSort.ARRIVAL.apply(ranking)));
    }

    @Test
    public void fewestTransfersUsesBestRouteChoiceAndPutsUnknownLast() {
        assertEquals(2, OptionSort.fewestTransfers(a));
        assertEquals(OptionSort.UNKNOWN_TRANSFERS, OptionSort.fewestTransfers(d));
        // B 1회 → A 2회 · C 2회 (같으면 도착 빠른 순) → D 정보 없음
        assertEquals(Arrays.asList("B", "A", "C", "D"), names(OptionSort.FEWEST_TRANSFERS.apply(ranking)));
    }

    @Test
    public void nearestStopTiesKeepArrivalOrder() {
        // B · D 둘 다 0분 → 도착 빠른 B 먼저
        assertEquals(Arrays.asList("B", "D", "C", "A"), names(OptionSort.NEAREST_STOP.apply(ranking)));
    }

    @Test
    public void boardProbHighestFirst() {
        assertEquals(Arrays.asList("C", "B", "A", "D"), names(OptionSort.BOARD_PROB.apply(ranking)));
    }

    @Test
    public void skippedStopsAlwaysLast() {
        Option skipped = option("S", 0, 0.99, 0);
        skipped.skipped = true;
        List<Option> withSkipped = Arrays.asList(skipped, a, b, c, d);
        for (OptionSort s : OptionSort.values()) {
            List<Option> sorted = s.apply(withSkipped);
            assertEquals(s.name(), "S", sorted.get(sorted.size() - 1).stop.name);
        }
    }

    @Test
    public void doesNotChangeInputAndHandlesNull() {
        OptionSort.BOARD_PROB.apply(ranking);
        assertEquals(Arrays.asList("A", "B", "C", "D"), names(ranking));
        assertTrue(OptionSort.NEAREST_STOP.apply(null).isEmpty());
        assertEquals(1, OptionSort.NEAREST_STOP.apply(Arrays.asList(null, a)).size());
    }

    @Test
    public void demoRecommendationsSortWithoutLosingCards() throws Exception {
        String json = new String(Files.readAllBytes(
                new File("../demo/src/main/assets/recommendations.json").toPath()), StandardCharsets.UTF_8);
        List<Option> r = RecommendationJson.parse(new StringReader(json)).ranking;
        for (OptionSort s : OptionSort.values()) {
            List<Option> sorted = s.apply(r);
            assertEquals(r.size(), sorted.size());
            for (int i = 1; i < sorted.size(); i++) {
                Option prev = sorted.get(i - 1), cur = sorted.get(i);
                if (s == OptionSort.NEAREST_STOP) assertTrue(prev.walkMin <= cur.walkMin);
                if (s == OptionSort.BOARD_PROB) assertTrue(prev.boardProb >= cur.boardProb);
                if (s == OptionSort.FEWEST_TRANSFERS) {
                    assertTrue(OptionSort.fewestTransfers(prev) <= OptionSort.fewestTransfers(cur));
                }
            }
        }
    }
}
