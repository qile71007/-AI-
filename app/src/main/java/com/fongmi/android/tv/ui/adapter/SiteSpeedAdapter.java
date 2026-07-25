package com.fongmi.android.tv.ui.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.AdapterSiteSpeedBinding;
import com.fongmi.android.tv.setting.SiteHealthStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;
import android.os.Handler;
import android.os.Looper;

public class SiteSpeedAdapter extends RecyclerView.Adapter<SiteSpeedAdapter.ViewHolder> {

    private static final String TEST_KEYWORD = "\u6d4b\u8bd5";
    private static final int TIMEOUT_SECONDS = 8;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(5);

    private final List<SpeedResult> items = new CopyOnWriteArrayList<>();
    private final List<Site> sites = new ArrayList<>();
    private final AtomicInteger completed = new AtomicInteger(0);
    private final ProgressCallback callback;
    private volatile boolean running = false;
    private final List<CompletableFuture<Void>> futures = new ArrayList<>();

    public interface ProgressCallback {
        void onProgress(int done, int total);
        void onComplete();
    }

    public SiteSpeedAdapter(ProgressCallback callback) {
        this.callback = callback;
    }

    public synchronized void startTest(List<Site> siteList) {
        cancelAll();
        items.clear();
        sites.clear();
        completed.set(0);
        running = true;
        sites.addAll(siteList);
        notifyDataSetChanged();

        for (Site site : siteList) {
            if (!site.isSearchable()) {
                items.add(new SpeedResult(site.getName(), site.getKey(), 0, false));
                int done = completed.incrementAndGet();
                new Handler(Looper.getMainLooper()).post(() -> notifyDataSetChanged());
                if (callback != null) new Handler(Looper.getMainLooper()).post(() -> callback.onProgress(done, siteList.size()));
                if (done >= siteList.size()) { running = false; if (callback != null) new Handler(Looper.getMainLooper()).post(() -> callback.onComplete()); }
                continue;
            }
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                if (!Thread.currentThread().isInterrupted()) {
                    long start = System.currentTimeMillis();
                    boolean success = false;
                    try {
                        var result = SiteApi.searchContent(site, TEST_KEYWORD, false, "1");
                        success = result != null && result.getList() != null;
                        SiteHealthStore.recordSearch(site, success, success && result.getList() != null ? result.getList().size() : 0, System.currentTimeMillis() - start, "");
                    } catch (Throwable e) {
                        SiteHealthStore.recordSearch(site, false, 0, System.currentTimeMillis() - start, e.getMessage());
                    }
                    long elapsed = System.currentTimeMillis() - start;
                    if (!Thread.currentThread().isInterrupted()) {
                        items.add(new SpeedResult(site.getName(), site.getKey(), elapsed, success));
                    }
                }
                int done = completed.incrementAndGet();
                sortItems();
                new Handler(Looper.getMainLooper()).post(() -> notifyDataSetChanged());
                if (callback != null) new Handler(Looper.getMainLooper()).post(() -> callback.onProgress(done, siteList.size()));
                if (done >= siteList.size()) { running = false; if (callback != null) new Handler(Looper.getMainLooper()).post(() -> callback.onComplete()); }
            }, EXECUTOR);
            futures.add(future);
        }
    }

    public synchronized void cancelAll() {
        running = false;
        for (CompletableFuture<?> future : futures) {
            if (!future.isDone() && !future.isCancelled()) {
                future.cancel(true);
            }
        }
        futures.clear();
    }

    private void sortItems() {
        List<SpeedResult> sorted = new ArrayList<>(items);
        Collections.sort(sorted, (a, b) -> {
            if (a.success != b.success) return a.success ? -1 : 1;
            return Long.compare(a.elapsed, b.elapsed);
        });
        items.clear();
        items.addAll(sorted);
    }

    public boolean isRunning() {
        return running;
    }

    public void copyResults(Context context) {
        if (items.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        sb.append("\u7ad9\u6e90\u6d4b\u901f\u7ed3\u679c\n");
        for (int i = 0; i < items.size(); i++) {
            SpeedResult r = items.get(i);
            String speed = r.success ? r.elapsed + "ms" : "\u5931\u8d25";
            sb.append(i + 1).append(". ").append(r.name).append(" \u2014 ").append(speed).append("\n");
        }
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("site_speed_results", sb.toString()));
        Toast.makeText(context, R.string.site_speed_copied, Toast.LENGTH_SHORT).show();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSiteSpeedBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SpeedResult r = items.get(position);
        holder.binding.speedName.setText(r.name);
        String time = r.success ? r.elapsed + "ms" : "x";
        holder.binding.speedTime.setText(time);
        int color;
        if (!r.success) {
            color = 0xFFFF6B6B;
        } else if (r.elapsed < 2000) {
            color = 0xFF51CF66;
        } else if (r.elapsed < 5000) {
            color = 0xFFFFD43B;
        } else {
            color = 0xFFFF6B6B;
        }
        holder.binding.speedDot.setBackgroundTintList(ColorStateList.valueOf(color));
        holder.binding.speedTime.setTextColor(color);
    }

    public static class SpeedResult {
        public final String name;
        public final String key;
        public final long elapsed;
        public final boolean success;

        SpeedResult(String name, String key, long elapsed, boolean success) {
            this.name = name;
            this.key = key;
            this.elapsed = elapsed;
            this.success = success;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final AdapterSiteSpeedBinding binding;
        ViewHolder(@NonNull AdapterSiteSpeedBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}