package kr.ac.kookmin.seatforecast.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import kr.ac.kookmin.seatforecast.alternatives.ui.AlternativesFragment;
import kr.ac.kookmin.seatforecast.home.ui.HomeFragment;
import kr.ac.kookmin.seatforecast.map.MapActivity;

/**
 * 하단 탭: 홈(A) · 실시간 대안(B) · 지도(C) · 마이(D).
 * 홈 · 대안은 탭 안의 화면(Fragment), 지도 · 마이는 각 모듈의 화면(Activity)을 엽니다.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG_HOME = "home";
    private static final String TAG_ALT = "alternatives";

    private BottomNavigationView nav;
    /** 대안 탭을 만들 때의 LastTrip.version */
    private int altTripVersion = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_activity_main);
        nav = findViewById(R.id.app_nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.app_tab_home) {
                show(TAG_HOME);
                return true;
            }
            if (id == R.id.app_tab_alternatives) {
                show(TAG_ALT);
                return true;
            }
            if (id == R.id.app_tab_map) {
                startActivity(new Intent(this, MapActivity.class));
            } else if (id == R.id.app_tab_my) {
                AccountBridge.openMyPage(this);
            }
            return false;   // 지도 · 마이는 새 화면이므로 선택 표시는 그대로
        });
        if (savedInstanceState == null) show(TAG_HOME);
        else altTripVersion = savedInstanceState.getInt("alt_trip_version", -1);
    }

    /** 홈 · 대안 탭 — 한 번 만든 화면은 숨겼다 다시 보여 줌 */
    private void show(String tag) {
        var fm = getSupportFragmentManager();
        var tx = fm.beginTransaction();
        for (Fragment f : fm.getFragments()) tx.hide(f);
        Fragment f = fm.findFragmentByTag(tag);
        // 대안 탭: 홈에서 다른 출발지 · 도착지를 검색했으면 그 경로로 새로 만듦
        if (f != null && TAG_ALT.equals(tag) && altTripVersion != LastTrip.version()) {
            tx.remove(f);
            f = null;
        }
        if (f == null) {
            if (TAG_HOME.equals(tag)) {
                f = new HomeFragment();
            } else {
                altTripVersion = LastTrip.version();
                f = AlternativesFragment.newInstance(LastTrip.altFrom(), LastTrip.altTo());
            }
            tx.add(R.id.app_container, f, tag);
        } else {
            tx.show(f);
        }
        tx.commit();
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("alt_trip_version", altTripVersion);
    }

    /** 로그인 · 마이페이지(로그아웃 · 설정 변경)에서 돌아오면 보이는 탭을 다시 불러옴 */
    @Override
    protected void onRestart() {
        super.onRestart();
        Fragment home = getSupportFragmentManager().findFragmentByTag(TAG_HOME);
        Fragment alt = getSupportFragmentManager().findFragmentByTag(TAG_ALT);
        if (home instanceof HomeFragment && home.isVisible()) ((HomeFragment) home).reload();
        if (alt instanceof AlternativesFragment && alt.isVisible()) ((AlternativesFragment) alt).reload();
    }
}
