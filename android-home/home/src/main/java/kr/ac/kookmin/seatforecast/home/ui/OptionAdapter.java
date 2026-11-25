package kr.ac.kookmin.seatforecast.home.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import kr.ac.kookmin.seatforecast.home.R;
import kr.ac.kookmin.seatforecast.home.model.Bus;
import kr.ac.kookmin.seatforecast.home.model.Option;

/** 추천 목록 (정렬은 {@link OptionSort}) — Figma '02 홈' 추천 카드. 보여 줄 내용의 규칙은 {@link OptionFormat}. */
class OptionAdapter extends RecyclerView.Adapter<OptionAdapter.Holder> {

    interface OnOptionClick {
        void onClick(Option option);
    }

    /** 우회로 건너뛰는 정류장 카드는 흐리게 */
    private static final float SKIPPED_ALPHA = 0.5f;

    private final List<Option> items = new ArrayList<>();
    private final OnOptionClick onClick;
    /** 서버 ranking 1순위 ('1순위' 태그) */
    @Nullable
    private Option recommended;
    /** 응답을 받은 뒤 지난 분 — 다가오는 차의 남은 시간을 줄여 보여 줌 (12주차) */
    private int elapsedMin;

    OptionAdapter(OnOptionClick onClick) {
        this.onClick = onClick;
    }

    /** 새 응답 · 정렬 변경 — 지난 시간은 0 으로 */
    void submit(List<Option> options, @Nullable Option recommended) {
        submit(options, recommended, 0);
    }

    void submit(List<Option> options, @Nullable Option recommended, int elapsedMin) {
        this.recommended = recommended;
        this.elapsedMin = elapsedMin;
        items.clear();
        items.addAll(options);
        notifyDataSetChanged();
    }

    /** 시간이 지나면 셋째 줄(남은 분)만 다시 그림. 분이 바뀌지 않았으면 아무것도 하지 않음 */
    void setElapsedMin(int elapsedMin) {
        if (this.elapsedMin == elapsedMin) return;
        this.elapsedMin = elapsedMin;
        notifyItemRangeChanged(0, items.size(), PAYLOAD_TIME);
    }

    /** {@link #setElapsedMin} 때 카드 전체가 아니라 셋째 줄만 다시 그리도록 */
    private static final Object PAYLOAD_TIME = new Object();

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.home_item_option, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Option o = items.get(position);
        Context c = h.itemView.getContext();
        boolean first = OptionFormat.isFirst(o, recommended);

        h.itemView.setBackgroundResource(first ? R.drawable.home_bg_option_first : R.drawable.home_bg_option);
        h.itemView.setAlpha(o.skipped ? SKIPPED_ALPHA : 1f);
        h.firstTag.setVisibility(first ? View.VISIBLE : View.GONE);

        h.route.setText(o.route);
        h.mode.setImageResource(o.isSubway() ? R.drawable.home_ic_subway : R.drawable.home_ic_bus);
        h.mode.setContentDescription(c.getString(o.isSubway() ? R.string.home_mode_subway : R.string.home_mode_bus));
        bindArrive(h, o);
        // 정류장 이름은 API 이름 그대로
        h.stop.setText(c.getString(R.string.home_stop_walk, o.stop == null ? "" : o.stop.name, o.walkMin));
        String kind = OptionFormat.kindLabel(o);
        h.kind.setText(kind);
        h.kind.setVisibility(kind == null ? View.GONE : View.VISIBLE);
        String summary = OptionFormat.summary(o);
        h.summary.setText(summary);
        h.summary.setVisibility(summary == null ? View.GONE : View.VISIBLE);

        bindBadge(h, o);
        bindBusLine(h, o, elapsedMin);
        bindNote(h, o);
        bindActual(h, o);

        h.itemView.setOnClickListener(v -> onClick.onClick(o));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.stream().allMatch(p -> p == PAYLOAD_TIME)) {
            bindBusLine(h, items.get(position), elapsedMin);
            return;
        }
        onBindViewHolder(h, position);
    }

    /** 1줄: "도착 10:08" + 걸리는 시간 "2시간 28분" (total_min) */
    private static void bindArrive(Holder h, Option o) {
        Context c = h.itemView.getContext();
        String total = o.arriveAt == null ? null : LiveTime.duration(o.totalMin);
        if (o.arriveAt == null) {
            h.arrive.setText(R.string.home_arrive_unknown);
            h.arrive.setContentDescription(null);
        } else {
            h.arrive.setText(c.getString(R.string.home_arrive, o.arriveAt));
            h.arrive.setContentDescription(total == null
                    ? c.getString(R.string.home_arrive_desc, o.arriveAt)
                    : c.getString(R.string.home_arrive_total_desc, o.arriveAt, total));
        }
        h.total.setText(total);
    }

    private static void bindBadge(Holder h, Option o) {
        OptionFormat.Badge kind = OptionFormat.badge(o);
        if (kind == OptionFormat.Badge.NONE) {
            h.badge.setVisibility(View.GONE);
            return;
        }
        Context c = h.itemView.getContext();
        if (kind == OptionFormat.Badge.STANDING || kind == OptionFormat.Badge.SEAT_UNKNOWN) {
            // seat_limited=false: 서서 탈 수 있음 · 지하철 / seat_known=false: 좌석 수를 모름 — 확률 대신 글자
            boolean standing = kind == OptionFormat.Badge.STANDING;
            h.badge.setVisibility(View.VISIBLE);
            h.badge.setText(standing ? R.string.home_standing : R.string.home_seat_unknown);
            h.badge.setContentDescription(c.getString(standing ? R.string.home_standing_desc
                    : R.string.home_seat_unknown_desc));
            h.badge.setBackgroundResource(R.drawable.home_bg_standing);
            h.badge.setTextColor(ContextCompat.getColor(c, standing ? R.color.home_text_body : R.color.home_muted));
            return;
        }
        OptionFormat.Level level = OptionFormat.level(o.boardProb);
        String percent = OptionFormat.percent(o.boardProb);
        h.badge.setVisibility(View.VISIBLE);
        h.badge.setText(percent);
        h.badge.setContentDescription(c.getString(R.string.home_badge_desc, percent));
        h.badge.setBackgroundResource(badgeBackground(level));
        h.badge.setTextColor(ContextCompat.getColor(c, levelColor(level)));
    }

    private static void bindBusLine(Holder h, Option o, int elapsedMin) {
        if (o.skipped) {
            h.busRow.setVisibility(View.GONE);
            return;
        }
        Context c = h.itemView.getContext();
        OptionFormat.BusLine line = OptionFormat.busLine(o, elapsedMin);
        String text;
        @ColorRes int color;
        switch (line.kind) {
            case BUS:
                Bus b = line.bus;
                // 남은 분은 받은 뒤 지난 시간만큼 줄인 값 (line.etaMin)
                String eta = line.missedEarlier
                        ? (line.etaMin <= 0 ? c.getString(R.string.home_bus_eta_now)
                        : c.getString(R.string.home_bus_next_eta, line.etaMin))
                        : line.etaMin <= 0 ? c.getString(R.string.home_bus_eta_now)
                        : c.getString(R.string.home_bus_eta, line.etaMin);
                String seats = b.seatsAtStop == null ? c.getString(R.string.home_bus_seats_unknown)
                        : b.seatsAtStop <= 0 ? c.getString(R.string.home_bus_full)
                        : c.getString(R.string.home_bus_seats, b.seatsAtStop);
                // 서서 탈 수 있는 버스(초록) · 좌석 수를 모르는 버스(회색)면 좌석 수 대신 도착 시간만
                text = o.showsProbability() ? c.getString(R.string.home_bus_line, eta, seats) : eta;
                color = o.showsProbability() ? levelColor(OptionFormat.level(b.boardProb))
                        : o.isStandingOk() ? R.color.home_badge_high : R.color.home_muted;
                break;
            case NONE_REACHABLE:
                text = c.getString(R.string.home_bus_none_reachable);
                color = R.color.home_badge_low;
                break;
            case PASSED:
                text = c.getString(R.string.home_bus_passed);
                color = R.color.home_muted;
                break;
            case SUBWAY:
                text = c.getString(R.string.home_subway_line);
                color = R.color.home_badge_high;
                break;
            case NO_INFO:
            default:
                text = c.getString(R.string.home_bus_no_info);
                color = R.color.home_muted;
                break;
        }
        int tint = ContextCompat.getColor(c, color);
        h.busRow.setVisibility(View.VISIBLE);
        h.bus.setText(text);
        h.bus.setTextColor(tint);
        h.busIcon.setImageTintList(ColorStateList.valueOf(tint));
    }

    /** 지난 출근길 재생: "그날 실제로 · (만석 N대 보내고) 07:51 탑승 → 09:44 도착 (2시간 4분)" */
    private static void bindActual(Holder h, Option o) {
        Context c = h.itemView.getContext();
        switch (OptionFormat.actualKind(o)) {
            case BOARDED: {
                String boarded = (o.actual.fullSkipped > 0 ? c.getString(R.string.home_actual_skipped, o.actual.fullSkipped) : "")
                        + o.actual.boardedAt;
                String total = LiveTime.duration(o.actual.totalMin);
                String text = o.actual.arriveAt == null ? c.getString(R.string.home_actual_boarded_only, boarded)
                        : total == null ? c.getString(R.string.home_actual_boarded, boarded, o.actual.arriveAt)
                        : c.getString(R.string.home_actual_boarded_total, boarded, o.actual.arriveAt, total);
                h.actual.setText(text);
                h.actual.setTextColor(ContextCompat.getColor(c, R.color.home_primary));
                h.actual.setVisibility(View.VISIBLE);
                break;
            }
            case NOT_BOARDED:
                h.actual.setText(R.string.home_actual_none);
                h.actual.setTextColor(ContextCompat.getColor(c, R.color.home_badge_low));
                h.actual.setVisibility(View.VISIBLE);
                break;
            default:
                h.actual.setVisibility(View.GONE);
        }
    }

    /** 넷째 줄: 건너뜀(빨강)이 지연(노랑)보다 우선 */
    private static void bindNote(Holder h, Option o) {
        Context c = h.itemView.getContext();
        if (o.skipped) {
            h.note.setVisibility(View.VISIBLE);
            h.note.setText(R.string.home_skipped);
            h.note.setTextColor(ContextCompat.getColor(c, R.color.home_badge_low));
        } else if (o.delayMin > 0) {
            h.note.setVisibility(View.VISIBLE);
            h.note.setText(c.getString(R.string.home_delay, o.delayMin));
            h.note.setTextColor(ContextCompat.getColor(c, R.color.home_badge_mid));
        } else {
            h.note.setVisibility(View.GONE);
        }
    }

    @ColorRes
    static int levelColor(OptionFormat.Level level) {
        switch (level) {
            case HIGH:
                return R.color.home_badge_high;
            case MID:
                return R.color.home_badge_mid;
            default:
                return R.color.home_badge_low;
        }
    }

    @DrawableRes
    static int badgeBackground(OptionFormat.Level level) {
        switch (level) {
            case HIGH:
                return R.drawable.home_bg_badge_high;
            case MID:
                return R.drawable.home_bg_badge_mid;
            default:
                return R.drawable.home_bg_badge_low;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView mode;
        final TextView route;
        final TextView kind;
        final TextView summary;
        final TextView arrive;
        final TextView total;
        final TextView firstTag;
        final TextView badge;
        final TextView stop;
        final View busRow;
        final ImageView busIcon;
        final TextView bus;
        final TextView note;
        final TextView actual;

        Holder(View v) {
            super(v);
            mode = v.findViewById(R.id.home_option_mode);
            route = v.findViewById(R.id.home_option_route);
            kind = v.findViewById(R.id.home_option_kind);
            summary = v.findViewById(R.id.home_option_summary);
            arrive = v.findViewById(R.id.home_option_arrive);
            total = v.findViewById(R.id.home_option_total);
            firstTag = v.findViewById(R.id.home_option_first_tag);
            badge = v.findViewById(R.id.home_option_badge);
            stop = v.findViewById(R.id.home_option_stop);
            busRow = v.findViewById(R.id.home_option_bus_row);
            busIcon = v.findViewById(R.id.home_option_bus_icon);
            bus = v.findViewById(R.id.home_option_bus);
            note = v.findViewById(R.id.home_option_note);
            actual = v.findViewById(R.id.home_option_actual);
        }
    }
}
