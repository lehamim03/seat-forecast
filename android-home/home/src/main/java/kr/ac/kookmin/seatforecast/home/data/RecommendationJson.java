package kr.ac.kookmin.seatforecast.home.data;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import kr.ac.kookmin.seatforecast.home.model.Recommendations;

/** recommendations.json (UTF-8) 읽기. 예시 파일과 서버 응답이 같은 형식이므로 둘 다 이 Gson 을 씁니다. */
public final class RecommendationJson {

    static final Gson GSON = new Gson();

    private RecommendationJson() {
    }

    public static Recommendations parse(InputStream in) throws SourceException {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (IOException e) {
            throw new SourceException("추천 데이터를 읽지 못했습니다.", e);
        }
    }

    public static Recommendations parse(Reader reader) throws SourceException {
        Recommendations r;
        try {
            r = GSON.fromJson(reader, Recommendations.class);
        } catch (JsonParseException e) {
            throw new SourceException("추천 데이터 형식이 올바르지 않습니다.", e);
        }
        if (r == null) throw new SourceException("추천 데이터가 비어 있습니다.", null);
        return r;
    }
}
