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
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogStatsBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class SiteSpeedDialog {

    private static final String TEST_KEYWORD = "测试";
    private static final int TIMEOUT_SECONDS = 8;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(5);

    private SiteSpeedDialog() {
    }

    public static void show(FragmentActivity activity) {
        List<Site> sites = VodConfig.get().getSites();
        List<Site> searchable = new ArrayList<>();
        for (Site s : sites) {
            if (s.isSearchable()) searchable.add(s);
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(ResUtil.dp2px(20), ResUtil.dp2px(20), ResUtil.dp2px(20), ResUtil.dp2px(20));

        TextView title = new TextView(activity);
        title.setText(R.string.site_speed_title);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, ResUtil.dp2px(16));
        root.addView(title);

        ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(searchable.size());
        progress.setProgress(0);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pl.bottomMargin = ResUtil.dp2px(8);
        root.addView(progress);

        TextView status = new TextView(activity);
        status.setText(activity.getString(R.string.site_speed_testing, 0, searchable.size()));
        status.setPadding(0, 0, 0, ResUtil.dp2px(12));
        root.addView(status);

        RecyclerView recyclerView = new RecyclerView(activity);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        ResultAdapter adapter = new ResultAdapter();
        recyclerView.setAdapter(adapter);
        root.addView(recyclerView);

        Dialog dialog = LightDialog.create(activity, null, root);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        configureWindow(activity, dialog);

        runSpeedTest(searchable, adapter, progress, status, dialog);
    }

    private static void runSpeedTest(List<Site> sites, ResultAdapter adapter, ProgressBar progress, TextView status, Dialog dialog) {
        AtomicInteger completed = new AtomicInteger(0);
        List<SpeedResult> results = Collections.synchronizedList(new ArrayList<>());

        for (Site site : sites) {
            CompletableFuture.runAsync(() -> {
                long start = System.currentTimeMillis();
                boolean success = false;
                try {
                    var result = SiteApi.searchContent(site, TEST_KEYWORD, false, "1");
                    success = result != null && result.getList() != null;
                } catch (Throwable ignored) {
                }
                long elapsed = System.currentTimeMillis() - start;
                results.add(new SpeedResult(site.getName(), site.getKey(), elapsed, success));

                int done = completed.incrementAndGet();
                adapter.updateResults(new ArrayList<>(results));
                progress.post(() -> {
                    progress.setProgress(done);
                    status.setText(status.getContext().getString(R.string.site_speed_testing, done, sites.size()));
                    if (done >= sites.size()) {
                        status.setText(R.string.site_speed_done);
                        progress.setVisibility(View.GONE);
                    }
                });
            }, EXECUTOR);
        }
    }

    private static void configureWindow(FragmentActivity activity, Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (ResUtil.getScreenWidth(activity) * (ResUtil.isLand(activity) ? 0.62f : 0.92f));
        params.height = (int) (ResUtil.getScreenHeight(activity) * 0.7);
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setAttributes(params);
    }

    static class SpeedResult {
        String name;
        String key;
        long elapsed;
        boolean success;

        SpeedResult(String name, String key, long elapsed, boolean success) {
            this.name = name;
            this.key = key;
            this.elapsed = elapsed;
            this.success = success;
        }
    }

    static class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.VH> {
        private final List<SpeedResult> items = new ArrayList<>();

        void updateResults(List<SpeedResult> newItems) {
            Collections.sort(newItems, (a, b) -> {
                if (a.success != b.success) return a.success ? -1 : 1;
                return Long.compare(a.elapsed, b.elapsed);
            });
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(ResUtil.dp2px(4), ResUtil.dp2px(6), ResUtil.dp2px(4), ResUtil.dp2px(6));
            tv.setTextSize(13);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
            tv.setLayoutParams(lp);
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            SpeedResult r = items.get(position);
            String status = r.success ? r.elapsed + "ms" : "x";
            String text = (position + 1) + ". " + r.name + " — " + status;
            ((TextView) holder.itemView).setText(text);
            if (!r.success) {
                ((TextView) holder.itemView).setTextColor(0xFFFF6B6B);
            } else if (r.elapsed < 2000) {
                ((TextView) holder.itemView).setTextColor(0xFF51CF66);
            } else if (r.elapsed < 5000) {
                ((TextView) holder.itemView).setTextColor(0xFFFFD43B);
            } else {
                ((TextView) holder.itemView).setTextColor(0xFFFF6B6B);
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            VH(View v) { super(v); }
        }
    }
}
