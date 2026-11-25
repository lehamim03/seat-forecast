package kr.ac.kookmin.seatforecast.home.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import kr.ac.kookmin.seatforecast.home.Place;

/** 예시 JSON 장소 검색 (서버가 꺼졌을 때) — 이름 · 주소에 검색어, 띄어쓰기 무시 */
public class AssetPlaceFilterTest {

    private static final List<Place> PLACES = Arrays.asList(
            new Place("국민대학교", "서울 성북구 정릉동", 37.610977, 126.995185),
            new Place("국민대학교 정문", "서울 성북구 정릉동", 37.611088, 126.994713),
            new Place("서현역", "경기 성남시 분당구", 37.385, 127.1233));

    private static List<String> names(String q) {
        List<Place> out = AssetRecommendationSource.filter(PLACES, q);
        String[] n = new String[out.size()];
        for (int i = 0; i < n.length; i++) n[i] = out.get(i).name;
        return Arrays.asList(n);
    }

    @Test
    public void byNameIgnoringSpaces() {
        assertEquals(Arrays.asList("국민대학교", "국민대학교 정문"), names("국민대"));
        assertEquals(Arrays.asList("국민대학교 정문"), names("국민대학교정문"));
    }

    @Test
    public void byAddress() {
        assertEquals(Arrays.asList("서현역"), names("분당"));
    }

    @Test
    public void emptyQueryReturnsAll() {
        assertEquals(3, names("  ").size());
    }
}
