package com.fongmi.android.tv;

import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import com.fongmi.android.tv.bean.Update;
import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.UpdateDialog;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.AppVersion;
import com.fongmi.android.tv.utils.Github;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Updater implements Download.Callback, UpdateListener {

    private static final String DEFAULT_RELEASE_NOTES = "手动触发 GitHub Actions 构建发布。";
    private static final String SOURCE_CNB = "cnb";
    private static final String SOURCE_GITHUB = "github";
    private static final long UPDATE_CHECK_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);
    private static final long GITHUB_REQUEST_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(4);
    private static final Map<String, String> GITHUB_API_HEADERS = Map.of("Accept", "application/vnd.github+json", "X-GitHub-Api-Version", "2022-11-28");
    private static final Map<String, String> GITHUB_ASSET_HEADERS = Map.of("Accept", "application/octet-stream", "X-GitHub-Api-Version", "2022-11-28");
    private static final Updater INSTANCE = new Updater();

    // 现在只有一个版本通道，统一用 "release" 标识
    private static final String CHANNEL_RELEASE = "release";

    private final LifecycleEventObserver lifecycleObserver = (source, event) -> {
        if (!(source instanceof FragmentActivity)) return;
        FragmentActivity activity = (FragmentActivity) source;
        if (event == Lifecycle.Event.ON_DESTROY) unbind(activity);
    };

    private WeakReference<FragmentActivity> activityRef;
    private UpdateDialog dialog;
    private Download download;
    private Update updateInfo;          // 当前获取到的更新信息
    private boolean force;
    private volatile boolean downloading;
    private volatile boolean canceled;
    private int lastProgress = -1;
    private long lastBytes;
    private long lastTotal;
    private long lastSpeed;
    private long lastElapsed;

    private Updater() {
    }

    public static Updater create() {
        return INSTANCE;
    }

    private File getFile() {
        return Path.cache("update.apk");
    }

    private String getName() {
        return BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi;
    }

    public Updater force() {
        force = true;
        Notify.show(R.string.update_check);
        Setting.putUpdate(true);
        return this;
    }

    public void start(FragmentActivity activity) {
        bind(activity);
        boolean forceCheck = force;
        force = false;
        if (downloading) {
            restoreDialog(activity);
            return;
        }
        if (!Setting.getUpdate()) return;
        Task.execute(() -> doInBackground(activity, forceCheck));
    }

    public void resume(FragmentActivity activity) {
        bind(activity);
        restoreDialog(activity);
    }

    private void doInBackground(FragmentActivity activity, boolean forceCheck) {
        long deadline = SystemClock.elapsedRealtime() + UPDATE_CHECK_TIMEOUT_MS;
        Future<Update> future = Task.executor().submit(this::getUpdate);
        updateInfo = awaitUpdate(future, deadline);
        if (!updateInfo.hasUpdate()) {
            if (forceCheck) {
                // 强制检查：若有 manifest 但无更新，也显示对话框（告知最新）
                if (updateInfo.hasManifest()) {
                    App.post(() -> show(activity));
                } else {
                    App.post(() -> Notify.show(TextUtils.isEmpty(updateInfo.error) ? R.string.update_latest : R.string.update_failed));
                }
            }
            // 非强制且无更新，静默结束
            return;
        }
        App.post(() -> show(activity));
    }

    private Update awaitUpdate(Future<Update> future, long deadline) {
        try {
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0) throw new TimeoutException("Update check timed out");
            return future.get(remaining, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            future.cancel(true);
            e.printStackTrace();
            Update update = Update.empty(CHANNEL_RELEASE);
            update.error = e.getMessage();
            return update;
        }
    }

    private Update getUpdate() {
        // 从 CNB（国内）获取
        Update cnb = readUpdate(CHANNEL_RELEASE, Github.getCnbAsset(getManifestName()), SOURCE_CNB);
        // 从 GitHub 获取
        Update github = getGithubReleaseUpdate();
        // 返回较新的
        return newer(cnb, github);
    }

    private Update getGithubReleaseUpdate() {
        try {
            JSONObject release = new JSONObject(OkHttp.string(Github.getLatestReleaseApi(), GITHUB_API_HEADERS, GITHUB_REQUEST_TIMEOUT_MS));
            return readGithubReleaseUpdate(release);
        } catch (Exception e) {
            e.printStackTrace();
            return Update.empty(CHANNEL_RELEASE);
        }
    }

    private Update readGithubReleaseUpdate(JSONObject release) {
        JSONObject asset = findAsset(release.optJSONArray("assets"), getManifestName());
        long assetId = asset == null ? 0 : asset.optLong("id");
        if (assetId <= 0) return Update.empty(CHANNEL_RELEASE);
        return readUpdate(CHANNEL_RELEASE, Github.getReleaseAssetApi(assetId), SOURCE_GITHUB, GITHUB_ASSET_HEADERS, release.optString("body"));
    }

    private JSONObject findAsset(JSONArray assets, String name) {
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null || !name.equals(asset.optString("name"))) continue;
            return asset;
        }
        return null;
    }

    private Update readUpdate(String channel, String manifestUrl, String source) {
        return readUpdate(channel, manifestUrl, source, null, "");
    }

    private Update readUpdate(String channel, String manifestUrl, String source, Map<String, String> headers, String fallbackNotes) {
        Update update = Update.empty(channel);
        try {
            String text = headers == null ? OkHttp.string(manifestUrl, GITHUB_REQUEST_TIMEOUT_MS) : OkHttp.string(manifestUrl, headers, GITHUB_REQUEST_TIMEOUT_MS);
            if (TextUtils.isEmpty(text)) throw new IllegalStateException("Empty update manifest: " + manifestUrl);
            JSONObject object = new JSONObject(text);
            update.name = object.optString("name");
            update.desc = normalizeText(object.optString("desc"));
            update.notes = normalizeText(object.optString("notes"));
            update.channel = object.optString("channel", channel); // 若 manifest 未指定，则用传入的 channel
            update.code = object.optInt("code");
            update.apk = object.optString("apk");
            update.size = object.optLong("size");
            update.sha256 = object.optString("sha256");
            update.apkUrl = getApkUrl(update, source);
            if (isDefaultReleaseNotes(update.notes)) update.notes = "";
            if (TextUtils.isEmpty(update.notes) && TextUtils.isEmpty(update.desc)) {
                String notes = TextUtils.isEmpty(fallbackNotes) ? getReleaseNotes(update.name) : fallbackNotes;
                if (!TextUtils.isEmpty(notes)) update.notes = normalizeText(notes);
            }
        } catch (Exception e) {
            e.printStackTrace();
            update.error = e.getMessage();
        }
        return update;
    }

    private Update newer(Update first, Update second) {
        if (first == null || !first.hasManifest()) return second == null ? Update.empty(CHANNEL_RELEASE) : second;
        if (second == null || !second.hasManifest()) return first;
        if (second.code != first.code) return second.code > first.code ? second : first;
        return compareName(second.name, first.name) > 0 ? second : first;
    }

    private int compareName(String left, String right) {
        return AppVersion.stripPrefix(left).compareToIgnoreCase(AppVersion.stripPrefix(right));
    }

    private String normalizeText(String text) {
        if (TextUtils.isEmpty(text)) return "";
        return text
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\'", "'");
    }

    private String getManifestName() {
        return getName() + ".json";
    }

    private String getDefaultApkName() {
        return getName() + ".apk";
    }

    private String getApkUrl(Update update, String source) {
        String apk = TextUtils.isEmpty(update.apk) ? getDefaultApkName() : update.apk;
        if (apk.startsWith("http://") || apk.startsWith("https://")) return apk;
        if (SOURCE_GITHUB.equals(source) && !TextUtils.isEmpty(update.name)) {
            return Github.getGithubReleaseAsset(update.name, apk);
        }
        if (SOURCE_CNB.equals(source) && !TextUtils.isEmpty(update.name)) {
            return Github.getCnbAsset(update.name, apk);
        }
        // fallback: 无 tag 的 CNB
        return Github.getCnbAsset(apk);
    }

    private boolean isDefaultReleaseNotes(String notes) {
        return !TextUtils.isEmpty(notes) && DEFAULT_RELEASE_NOTES.equals(notes.trim());
    }

    private String getReleaseNotes(String tag) {
        if (TextUtils.isEmpty(tag)) return "";
        String notes = readReleaseNotes(tag);
        if (!TextUtils.isEmpty(notes) || tag.startsWith("v")) return notes;
        return readReleaseNotes("v" + tag);
    }

    private String readReleaseNotes(String tag) {
        try {
            return new JSONObject(OkHttp.string(Github.getReleaseApi(tag), GITHUB_API_HEADERS, GITHUB_REQUEST_TIMEOUT_MS)).optString("body");
        } catch (Exception ignored) {
            return "";
        }
    }

    private void show(FragmentActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (activity.getSupportFragmentManager().isStateSaved()) return;
        bind(activity);
        dismiss();
        Notify.dismissToast();
        // 只显示单一版本，不提供通道切换
        dialog = UpdateDialog.create().stable(updateInfo).selected(Update.CHANNEL_STABLE).listener(this).show(activity);
    }

    @Override
    public void onConfirm(View view) {
        if (updateInfo == null || !updateInfo.hasUpdate()) {
            Notify.show(R.string.update_latest);
            return;
        }
        view.setEnabled(false);
        downloading = true;
        canceled = false;
        resetProgress();
        Path.clear(getFile());
        setDialogProgress(0, 0, updateInfo.size, 0, 0);
        download = Download.create(updateInfo.apkUrl, getFile()).tag(updateInfo.apkUrl);
        download.start(this);
    }

    @Override
    public void onCancel(View view) {
        if (downloading) {
            canceled = true;
            downloading = false;
            if (download != null) download.cancel();
            download = null;
            resetProgress();
            Notify.show(R.string.update_canceled);
            dismiss();
            return;
        }
        Setting.putUpdate(false);
        if (download != null) download.cancel();
        dismiss();
    }

    @Override
    public void onClose() {
        dialog = null;
    }

    // 通道切换接口不再需要，但必须实现（空实现）
    @Override
    public void onChannel(String channel) {
        // 无操作
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismissAllowingStateLoss();
        } catch (Exception ignored) {
        } finally {
            dialog = null;
        }
    }

    @Override
    public void progress(int progress) {
        setDialogProgress(progress, 0, 0, 0, 0);
    }

    @Override
    public void progress(int progress, long bytes, long total, long speed, long elapsed) {
        setDialogProgress(progress, bytes, total, speed, elapsed);
    }

    private void setDialogProgress(int progress, long bytes, long total, long speed, long elapsed) {
        if (canceled || !downloading) return;
        long manifestSize = updateInfo == null ? 0 : updateInfo.size;
        if (total <= 0 && manifestSize > 0) total = manifestSize;
        if (progress < 0 && total > 0 && bytes > 0) progress = (int) (bytes * 100.0 / total);
        lastProgress = progress;
        lastBytes = bytes;
        lastTotal = total;
        lastSpeed = speed;
        lastElapsed = elapsed;
        if (dialog == null) return;
        if (!dialog.setProgress(progress, bytes, total, speed, elapsed)) dialog = null;
    }

    @Override
    public void error(String msg) {
        if (canceled) return;
        downloading = false;
        download = null;
        resetProgress();
        Notify.show(msg);
        dismiss();
    }

    @Override
    public void success(File file) {
        if (canceled) return;
        download = null;
        Update target = updateInfo;
        Task.execute(() -> {
            String error = validate(file, target);
            App.post(() -> {
                if (canceled) return;
                downloading = false;
                resetProgress();
                if (!TextUtils.isEmpty(error)) {
                    Path.clear(file);
                    Notify.show(error);
                    dismiss();
                    return;
                }
                FileUtil.openFile(file);
                dismiss();
            });
        });
    }

    private void restoreDialog(FragmentActivity activity) {
        if (!downloading || updateInfo == null) return;
        show(activity);
        setDialogProgress(lastProgress, lastBytes, lastTotal, lastSpeed, lastElapsed);
    }

    private String validate(File file, Update update) {
        if (file == null || !file.exists() || file.length() <= 0) return ResUtil.getString(R.string.update_download_invalid);
        if (update != null && update.size > 0 && file.length() != update.size) return ResUtil.getString(R.string.update_download_incomplete);
        if (update != null && !TextUtils.isEmpty(update.sha256) && !update.sha256.equalsIgnoreCase(sha256(file))) return ResUtil.getString(R.string.update_download_checksum);
        if (App.get().getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0) == null) return ResUtil.getString(R.string.update_download_invalid);
        return "";
    }

    private String sha256(File file) {
        try (FileInputStream input = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            StringBuilder builder = new StringBuilder();
            for (byte value : digest.digest()) builder.append(String.format(Locale.ROOT, "%02x", value));
            return builder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void bind(FragmentActivity activity) {
        if (activity == null) return;
        FragmentActivity old = activityRef == null ? null : activityRef.get();
        if (old == activity) return;
        if (old != null) old.getLifecycle().removeObserver(lifecycleObserver);
        activityRef = new WeakReference<>(activity);
        activity.getLifecycle().addObserver(lifecycleObserver);
    }

    private void unbind(FragmentActivity activity) {
        FragmentActivity current = activityRef == null ? null : activityRef.get();
        if (current != activity) return;
        activity.getLifecycle().removeObserver(lifecycleObserver);
        activityRef = null;
        if (downloading && download != null) {
            download.cancel();
            download = null;
            downloading = false;
            canceled = true;
            resetProgress();
        }
        if (!downloading) dialog = null;
    }

    private void resetProgress() {
        lastProgress = -1;
        lastBytes = 0;
        lastTotal = 0;
        lastSpeed = 0;
        lastElapsed = 0;
    }
}
