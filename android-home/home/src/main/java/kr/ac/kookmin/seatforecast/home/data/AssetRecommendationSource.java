package kr.ac.kookmin.seatforecast.home.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;

import kr.ac.kookmin.seatforecast.home.model.Recommendations;

/**
 * 앱 assets 에 넣은 예시 응답(docs/examples/recommendations.json 복사본)을 읽습니다.
 * 서버가 생기기 전 화면 개발 · 시연용이라 요청 값(정류장 · 노선 · 토큰)은 쓰지 않습니다.
 */
public class AssetRecommendationSource implements RecommendationSource {

    public static final String DEFAULT_FILE = "recommendations.json";

    private final Context context;
    private final String fileName;

    public AssetRecommendationSource(Context context) {
        this(context, DEFAULT_FILE);
    }

    public AssetRecommendationSource(Context context, String fileName) {
        this.context = context.getApplicationContext();
        this.fileName = fileName;
    }

    @NonNull
    @Override
    public Recommendations load(@NonNull String homeStopId, @NonNull String route, @Nullable String idToken)
            throws SourceException {
        try (InputStream in = context.getAssets().open(fileName)) {
            return RecommendationJson.parse(in);
        } catch (IOException e) {
            throw new SourceException("예시 파일 " + fileName + " 을(를) 찾지 못했습니다.", e);
        }
    }
}
