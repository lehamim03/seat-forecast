package kr.ac.kookmin.seatforecast.alternatives.model;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/** {@code alternatives} — 키가 한국어 그대로 ("기다리기" · "걸어가기" · "다른 버스"), 해당 없으면 null. */
public class AlternativeSet {

    @Nullable
    @SerializedName(Option.KIND_WAIT)
    public Option wait;

    @Nullable
    @SerializedName(Option.KIND_WALK)
    public Option walk;

    @Nullable
    @SerializedName(Option.KIND_OTHER_BUS)
    public Option otherBus;

    /** 화면 순서(① 기다리기 → ② 걸어가기 → ③ 다른 버스)로, null 은 빼고 */
    public List<Option> inOrder() {
        List<Option> list = new ArrayList<>(3);
        if (wait != null) list.add(wait);
        if (walk != null) list.add(walk);
        if (otherBus != null) list.add(otherBus);
        return list;
    }
}
