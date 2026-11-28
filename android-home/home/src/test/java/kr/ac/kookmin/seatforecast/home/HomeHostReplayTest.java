package kr.ac.kookmin.seatforecast.home;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import kr.ac.kookmin.seatforecast.home.model.Option;

/** 재생 중 카드 → 기본 구현은 예전 콜백으로 (팀 앱이 onReplayOptionClicked 를 구현하지 않아도 동작) */
public class HomeHostReplayTest {

    private static class Recorder implements HomeHost {
        final List<String> calls = new ArrayList<>();

        @Override public boolean isSignedIn() { return true; }
        @Override public void requestSignIn(Context context) { }
        @Override public void getIdToken(Callback<String> callback) { callback.onSuccess(null); }
        @Override public void onDepartureAlarmClicked(Context context, Option first) { }

        @Override
        public void onOptionClicked(Context context, Option option, Place from, Place to) {
            calls.add("trip");
        }

        @Override
        public void onOptionClicked(Context context, Option option) {
            calls.add("old");
        }
    }

    @Test
    public void replayWithoutTripFallsBackToOldCallback() {
        Recorder r = new Recorder();
        r.onReplayOptionClicked(null, new Option(), null, null, "2026-11-11T08:20");
        assertEquals("old", r.calls.get(0));
    }

    @Test
    public void replayWithTripFallsBackToTripCallback() {
        Recorder r = new Recorder();
        r.onReplayOptionClicked(null, new Option(), new Place("A", 37.1, 127.1), new Place("B", 37.6, 127.0),
                "2026-11-11T08:20");
        assertEquals("trip", r.calls.get(0));
    }

    @Test
    public void replayExtraName() {
        assertEquals("replay", TripExtras.REPLAY);
    }
}
