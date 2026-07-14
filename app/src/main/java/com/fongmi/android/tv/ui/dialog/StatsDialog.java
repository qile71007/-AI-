package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.DialogStatsBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class StatsDialog {

    private StatsDialog() {
    }

    public static void show(FragmentActivity activity) {
        DialogStatsBinding binding = DialogStatsBinding.inflate(LayoutInflater.from(activity));
        List<History> all = History.getAll();
        fillStats(binding, all);
        configureContentHeight(activity, binding);

        Dialog dialog = LightDialog.create(activity, null, binding.getRoot());
        binding.statsConfirm.setOnClickListener(v -> dialog.dismiss());
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        configureWindow(activity, dialog);
        binding.statsConfirm.requestFocus();
    }

    private static void fillStats(DialogStatsBinding binding, List<History> all) {
        if (all == null || all.isEmpty()) {
            binding.statsTotalCount.setText("0");
            binding.statsTotalTime.setText("0");
            binding.statsMonthCount.setText("0");
            binding.statsWeekCount.setText("0");
            TextView empty = new TextView(binding.getRoot().getContext());
            empty.setText(R.string.stats_no_data);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, ResUtil.dp2px(24), 0, 0);
            binding.statsRecentList.addView(empty);
            return;
        }

        long now = System.currentTimeMillis();
        long monthStart = getMonthStart(now);
        long weekStart = getWeekStart(now);
        long totalWatchMs = 0;
        int monthCount = 0;
        int weekCount = 0;

        for (History h : all) {
            if (h.getCreateTime() >= monthStart) monthCount++;
            if (h.getCreateTime() >= weekStart) weekCount++;
            long pos = h.getPosition();
            long dur = h.getDuration();
            if (pos > 0 && dur > 0) {
                totalWatchMs += Math.min(pos, dur);
            } else if (pos > 0) {
                totalWatchMs += pos;
            }
        }

        binding.statsTotalCount.setText(String.valueOf(all.size()));
        binding.statsTotalTime.setText(formatDuration(totalWatchMs));
        binding.statsMonthCount.setText(String.valueOf(monthCount));
        binding.statsWeekCount.setText(String.valueOf(weekCount));

        fillRecent(binding, all);
    }

    private static void fillRecent(DialogStatsBinding binding, List<History> all) {
        List<History> recent = new ArrayList<>();
        Map<String, History> seen = new HashMap<>();
        for (History h : all) {
            String name = h.getVodName();
            if (name == null || name.isEmpty()) continue;
            if (seen.containsKey(name)) continue;
            seen.put(name, h);
            recent.add(h);
            if (recent.size() >= 5) break;
        }

        for (History h : recent) {
            TextView tv = new TextView(binding.getRoot().getContext());
            String name = h.getVodName();
            String remarks = h.getVodRemarks();
            String time = formatRelativeTime(h.getCreateTime());
            String text = "• " + name;
            if (remarks != null && !remarks.isEmpty()) text += " [" + remarks + "]";
            text += "  " + time;
            tv.setText(text);
            tv.setTextSize(13);
            tv.setPadding(ResUtil.dp2px(4), ResUtil.dp2px(6), ResUtil.dp2px(4), ResUtil.dp2px(6));
            binding.statsRecentList.addView(tv);
        }
    }

    private static String formatDuration(long ms) {
        if (ms <= 0) return "0";
        long hours = ms / TimeUnit.HOURS.toMillis(1);
        long minutes = (ms % TimeUnit.HOURS.toMillis(1)) / TimeUnit.MINUTES.toMillis(1);
        if (hours > 0) return hours + "h" + (minutes > 0 ? minutes + "m" : "");
        return minutes + "m";
    }

    private static String formatRelativeTime(long time) {
        long diff = System.currentTimeMillis() - time;
        if (diff < TimeUnit.MINUTES.toMillis(1)) return "just now";
        if (diff < TimeUnit.HOURS.toMillis(1)) return diff / TimeUnit.MINUTES.toMillis(1) + "min ago";
        if (diff < TimeUnit.DAYS.toMillis(1)) return diff / TimeUnit.HOURS.toMillis(1) + "h ago";
        if (diff < TimeUnit.DAYS.toMillis(30)) return diff / TimeUnit.DAYS.toMillis(1) + "d ago";
        return diff / TimeUnit.DAYS.toMillis(30) + "mo ago";
    }

    private static long getMonthStart(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long getWeekStart(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static void configureContentHeight(FragmentActivity activity, DialogStatsBinding binding) {
        int screenHeight = ResUtil.getScreenHeight(activity);
        int maxHeight = (int) (screenHeight * (ResUtil.isLand(activity) ? 0.6f : 0.55f));
        ViewGroup.LayoutParams params = binding.getRoot().getLayoutParams();
        if (params == null) params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.height = maxHeight;
    }

    private static void configureWindow(FragmentActivity activity, Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (ResUtil.getScreenWidth(activity) * (ResUtil.isLand(activity) ? 0.62f : 0.92f));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
    }
}
