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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import kr.ac.kookmin.seatforecast.home.R;
import kr.ac.kookmin.seatforecast.home.model.Bus;
import kr.ac.kookmin.seatforecast.home.model.Option;

/** 추천 목록 (ranking 순서 그대로) — Figma '02 홈' 추천 카드. 보여 줄 내용의 규칙은 {@link OptionFormat}. */
class OptionAdapter extends RecyclerView.Adapter<OptionAdapter.Holder> {

    interface OnOptionClick {
        void onClick(Option option);
    }

    /** 우회로 건너뛰는 정류장 카드는 흐리게 */
    private static final float SKIPPED_ALPHA = 0.5f;

    private final List<Option> items = new ArrayList<>();
    private final OnOptionClick onClick;

    OptionAdapter(OnOptionClick onClick) {
        this.onClick = onClick;
    }

    void submit(List<Option> options) {
        items.clear();
        items.addAll(options);
        notifyDataSetChanged();
    }

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
        boolean first = OptionFormat.isFirst(o, position);

        h.itemView.setBackgroundResource(first ? R.drawable.home_bg_option_first : R.drawable.home_bg_option);
        h.itemView.setAlpha(o.skipped ? SKIPPED_ALPHA : 1f);
        h.firstTag.setVisibility(first ? View.VISIBLE : View.GONE);

        h.route.setText(o.route);
        h.arrive.setText(o.arriveAt == null
                ? c.getString(R.string.home_arrive_unknown)
                : c.getString(R.string.home_arrive, o.arriveAt));
        // 정류장 이름은 API 이름 그대로
        h.stop.setText(c.getString(R.string.home_stop_walk, o.stop == null ? "" : o.stop.name, o.walkMin));

        bindBadge(h, o);
        bindBusLine(h, o);
        bindNote(h, o);

        h.itemView.setOnClickListener(v -> onClick.onClick(o));
    }

    private static void bindBadge(Holder h, Option o) {
        if (!OptionFormat.showBadge(o)) {
            h.badge.setVisibility(View.GONE);
            return;
        }
        Context c = h.itemView.getContext();
        OptionFormat.Level level = OptionFormat.level(o.boardProb);
        String percent = OptionFormat.percent(o.boardProb);
        h.badge.setVisibility(View.VISIBLE);
        h.badge.setText(percent);
        h.badge.setContentDescription(c.getString(R.string.home_badge_desc, percent));
        h.badge.setBackgroundResource(badgeBackground(level));
        h.badge.setTextColor(ContextCompat.getColor(c, levelColor(level)));
    }

    private static void bindBusLine(Holder h, Option o) {
        if (o.skipped) {
            h.busRow.setVisibility(View.GONE);
            return;
        }
        Context c = h.itemView.getContext();
        OptionFormat.BusLine line = OptionFormat.busLine(o);
        String text;
        @ColorRes int color;
        switch (line.kind) {
            case BUS:
                Bus b = line.bus;
                String eta = line.missedEarlier
                        ? c.getString(R.string.home_bus_next_eta, b.etaMin)
                        : b.etaMin <= 0 ? c.getString(R.string.home_bus_eta_now)
                        : c.getString(R.string.home_bus_eta, b.etaMin);
                String seats = b.seatsAtStop == null ? c.getString(R.string.home_bus_seats_unknown)
                        : b.seatsAtStop <= 0 ? c.getString(R.string.home_bus_full)
                        : c.getString(R.string.home_bus_seats, b.seatsAtStop);
                text = c.getString(R.string.home_bus_line, eta, seats);
                color = levelColor(OptionFormat.level(b.boardProb));
                break;
            case NONE_REACHABLE:
                text = c.getString(R.string.home_bus_none_reachable);
                color = R.color.home_badge_low;
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
        final TextView route;
        final TextView arrive;
        final TextView firstTag;
        final TextView badge;
        final TextView stop;
        final View busRow;
        final ImageView busIcon;
        final TextView bus;
        final TextView note;

        Holder(View v) {
            super(v);
            route = v.findViewById(R.id.home_option_route);
            arrive = v.findViewById(R.id.home_option_arrive);
            firstTag = v.findViewById(R.id.home_option_first_tag);
            badge = v.findViewById(R.id.home_option_badge);
            stop = v.findViewById(R.id.home_option_stop);
            busRow = v.findViewById(R.id.home_option_bus_row);
            busIcon = v.findViewById(R.id.home_option_bus_icon);
            bus = v.findViewById(R.id.home_option_bus);
            note = v.findViewById(R.id.home_option_note);
        }
    }
}
