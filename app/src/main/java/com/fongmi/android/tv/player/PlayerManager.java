package com.fongmi.android.tv.player;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MediaTitle;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.effect.ColorLut;
import androidx.media3.ui.danmaku.DanmakuConfig;
import androidx.media3.ui.danmaku.DanmakuController;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.player.engine.ExoPlayerEngine;
import com.fongmi.android.tv.player.engine.IjkPlayerEngine;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.lut.DynamicLutEffect;
import com.fongmi.android.tv.player.lut.LutEffectFactory;
import com.fongmi.android.tv.player.lut.LutEligibility;
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.player.lut.LutStore;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.LocalProxyDebug;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.google.common.net.HttpHeaders;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerManager implements ParseCallback {

    public static final String RELOAD_LUT_WARMUP = "__webhtv_lut_warmup_reload__";

    private static final long LOCAL_PROXY_READY_TIMEOUT_MS = 5000;
    private static final long LOCAL_PROXY_RETRY_DELAY_MS = 1000;
    private static final int LOCAL_PROXY_MAX_RETRY = 2;
    private static final int LUT_WARMUP_RECOVERED_ERROR_REFRESH_THRESHOLD = 3;
    private static final float[] SPEED_PRESETS = new float[]{0.5f, 0.75f, 1f, 1.2f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 5f};
    private static final DecimalFormat SPEED_FORMAT = new DecimalFormat("0.##x");

    private final Runnable runnable;
    private final Callback callback;
    private final DynamicLutEffect dynamicLutEffect;
    private DanmakuController danmakuController;
    private PlayerEngine engine;
    private VideoSize videoSize;
    private ParseJob parseJob;
    private PlaySpec spec;
    private Player player;
    private String currentDanmakuUrl;

    private boolean initTrack;
    private boolean exoFallbackTried;
    private boolean realtimeFallbackTried;
    private boolean videoEffectsActive;
    private boolean videoEffectsDirty;
    private boolean lutAppliedForItem;
    private boolean lutApplyInProgress;
    private boolean lutPipelineReadyForItem;
    private boolean lutPipelinePrepareInProgress;
    private boolean pendingLutPreview;
    private boolean waitingLutBeforePlay;
    private boolean lutWarmupRecoveryActive;
    private boolean lutWarmupRefreshRequested;
    private boolean lutWarmupReloadPreviewPending;
    private boolean lutAllowed = true;
    private int playerType;
    private int retry;
    private int localProxyRetry;
    private int prepareSeq;
    private int lutApplySeq;
    private int lutWarmupRecoveredErrors;

    public PlayerManager(Callback callback) {
        this.runnable = this::onPlaybackTimeout;
        this.dynamicLutEffect = new DynamicLutEffect();
        this.playerType = PlayerSetting.getPlayer();
        this.engine = buildEngine(playerType, PlayerEngine.HARD);
        this.player = engine.getPlayer();
        this.callback = callback;
    }

    public void release() {
        prepareSeq++;
        lutApplySeq++;
        player.removeListener(listener);
        App.removeCallbacks(runnable);
        if (engine == null) return;
        engine.release();
        engine = null;
        player = null;
        videoEffectsActive = false;
        videoEffectsDirty = false;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        pendingLutPreview = false;
        waitingLutBeforePlay = false;
        lutWarmupReloadPreviewPending = false;
        clearLutWarmupRecovery();
        currentDanmakuUrl = null;
    }

    private void onPlaybackTimeout() {
        if (retryLutWarmupByRefresh("timeout")) return;
        if (retryRealtimeFallback("timeout")) return;
        if (retryExoFallback("timeout")) return;
        callback.onError(ResUtil.getString(R.string.error_play_timeout));
    }

    private void resetLutRuntimeState(String reason, boolean clearEngineEffects) {
        lutApplySeq++;
        if (clearEngineEffects && engine != null && videoEffectsActive) {
            try {
                engine.setVideoEffects(Collections.emptyList());
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "clear effects before reset reason=%s", reason);
            } catch (Throwable e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "clear effects before reset failed reason=%s error=%s", reason, causeChain(e));
            }
        }
        dynamicLutEffect.clear();
        videoEffectsActive = false;
        videoEffectsDirty = false;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        pendingLutPreview = false;
        waitingLutBeforePlay = false;
        lutWarmupReloadPreviewPending = false;
        clearLutWarmupRecovery();
    }

    public Player getPlayer() {
        return player;
    }

    public Tracks getCurrentTracks() {
        return engine.getCurrentTracks();
    }

    public List<MediaTitle> getCurrentMediaTitles() {
        return engine.getCurrentMediaTitles();
    }

    public MediaItem getCurrentMediaItem() {
        return player.getCurrentMediaItem();
    }

    public int getPlaybackState() {
        return player.getPlaybackState();
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public boolean isReleased() {
        return player == null;
    }

    public String getUrl() {
        return spec != null ? spec.getUrl() : null;
    }

    public String getKey() {
        return spec != null ? spec.getKey() : null;
    }

    public List<Danmaku> getDanmakus() {
        return spec != null ? spec.getDanmakus() : null;
    }

    public MediaMetadata getMetadata() {
        return spec != null ? spec.getMetadata() : null;
    }

    public Map<String, String> getHeaders() {
        return spec == null || spec.getHeaders() == null ? new HashMap<>() : spec.getHeaders();
    }

    public float getSpeed() {
        return player.getPlaybackParameters().speed;
    }

    public boolean isEmpty() {
        return spec == null || TextUtils.isEmpty(spec.getUrl());
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public boolean isLandscape() {
        return getVideoWidth() > getVideoHeight();
    }

    public boolean isLive() {
        return engine.isLive();
    }

    public boolean isVod() {
        return engine.isVod();
    }

    public boolean haveTrack(int type) {
        return engine.haveTrack(type);
    }

    public boolean haveTitle() {
        return engine.haveTitle();
    }

    public boolean haveDanmaku() {
        return getDanmakus() != null && getDanmakus().stream().anyMatch(Danmaku::isSelected);
    }

    public boolean canSetOpening(long position, long duration) {
        return position > 0 && duration > 0 && position <= Constant.getOpEdLimit(duration);
    }

    public boolean canSetEnding(long position, long duration) {
        return position > 0 && duration > 0 && duration - position <= Constant.getOpEdLimit(duration);
    }

    public int getVideoWidth() {
        return videoSize == null ? 0 : videoSize.width;
    }

    public int getVideoHeight() {
        return videoSize == null ? 0 : videoSize.height;
    }

    public long getPosition() {
        return player.getCurrentPosition();
    }

    public long getBufferedDuration() {
        return Math.max(0, player.getBufferedPosition() - getPosition());
    }

    public int getBufferedPercentage() {
        return player.getBufferedPercentage();
    }

    public boolean isLoading() {
        return player.isLoading();
    }

    public String getSizeText() {
        int width = getVideoWidth();
        int height = getVideoHeight();
        if (width <= 0 || height <= 0) {
            Format format = getVideoFormat();
            if (format != null) {
                if (width <= 0) width = format.width;
                if (height <= 0) height = format.height;
            }
        }
        return width <= 0 || height <= 0 ? "" : width + " x " + height;
    }

    public Format getVideoFormat() {
        return engine.getVideoFormat();
    }

    public String getSpeedText() {
        return SPEED_FORMAT.format(getSpeed());
    }

    public String getDecodeText() {
        return engine.getDecodeText();
    }

    public String getPlayerText() {
        return ResUtil.getStringArray(R.array.select_player_kernel)[playerType];
    }

    public String getLutText() {
        return LutSetting.getButtonText();
    }

    public void setLutAllowed(boolean allowed) {
        if (lutAllowed == allowed) return;
        lutAllowed = allowed;
        if (!allowed) resetLutRuntimeState("lut_disallowed", true);
    }

    public String getLutUnavailableReason() {
        return LutEligibility.getUnavailableReason(engine, spec);
    }

    public boolean isIjk() {
        return playerType == PlayerSetting.IJK;
    }

    public String getPositionTime(long delta) {
        long time = Math.max(0, Math.min(getPosition() + delta, Math.max(0, getDuration())));
        return Util.timeMs(time);
    }

    public long getDuration() {
        return player.getDuration();
    }

    public String getDurationTime() {
        return Util.timeMs(Math.max(0, getDuration()));
    }

    public void setSub(Sub sub) {
        if (spec != null) spec.setSub(sub);
        setMediaItem();
    }

    public void setFormat(String format) {
        if (spec != null) spec.setFormat(format);
        setMediaItem();
    }

    public void setTitle(MediaTitle title) {
        if (spec != null) spec.setUrl(spec.getUri().buildUpon().fragment("title=" + title.index).build().toString());
        setMediaItem();
        seekTo(0);
    }

    public static MediaMetadata buildMetadata(String title, String artist, String artUri) {
        Uri artwork = TextUtils.isEmpty(artUri) ? null : Uri.parse(artUri);
        return new MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(artwork).build();
    }

    public void setMetadata(MediaMetadata data) {
        if (spec != null) spec.setMetadata(data);
        engine.setMetadata(data);
    }

    public void setDanmakuController(DanmakuController controller) {
        danmakuController = controller;
        danmakuController.setOkHttpClient(OkHttp.player());
        danmakuController.setConfig(DanmakuSetting.getConfig());
    }

    public void setDanmakuConfig(DanmakuConfig config) {
        danmakuController.setConfig(config);
    }

    public void setDanmakuEnabled(boolean enabled) {
        danmakuController.setEnabled(enabled);
    }

    public void sendDanmaku(String text) {
        danmakuController.sendNow(text);
    }

    public String setSpeed(float speed) {
        if (!player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) return getSpeedText();
        player.setPlaybackParameters(player.getPlaybackParameters().withSpeed(speed));
        return getSpeedText();
    }

    public String addSpeed() {
        return setSpeed(nextPresetSpeed());
    }

    public String addSpeed(float value) {
        return setSpeed(Math.min(getSpeed() + value, 5));
    }

    public String subSpeed(float value) {
        return setSpeed(Math.max(getSpeed() - value, 0.25f));
    }

    public String toggleSpeed() {
        return setSpeed(getSpeed() == 1 ? PlayerSetting.getSpeed() : 1);
    }

    private float nextPresetSpeed() {
        float speed = getSpeed();
        for (float preset : SPEED_PRESETS) if (speed < preset - 0.01f) return preset;
        return SPEED_PRESETS[0];
    }

    public void setTrack(List<Track> tracks) {
        if (!tracks.isEmpty()) engine.setTrack(tracks);
    }

    public void play() {
        player.play();
    }

    public void pause() {
        player.pause();
    }

    public void stop() {
        engine.stop();
        stopParse();
    }

    public void clearMediaItems() {
        player.clearMediaItems();
    }

    public boolean isRepeatOne() {
        return engine.isRepeatOne();
    }

    public void setRepeatOne(boolean repeat) {
        engine.setRepeatOne(repeat);
    }

    public void seekTo(long time) {
        player.seekTo(time);
    }

    public long getTextOffsetMs() {
        if (player.isCommandAvailable(Player.COMMAND_GET_TEXT_OFFSET)) return player.getTextOffsetMs();
        return 0;
    }

    public void setTextOffsetMs(long offsetMs) {
        if (player.isCommandAvailable(Player.COMMAND_SET_TEXT_OFFSET)) player.setTextOffsetMs(offsetMs);
    }

    public long getAudioOffsetMs() {
        if (player.isCommandAvailable(Player.COMMAND_GET_AUDIO_OFFSET)) return player.getAudioOffsetMs();
        return 0;
    }

    public void setAudioOffsetMs(long offsetMs) {
        if (player.isCommandAvailable(Player.COMMAND_SET_AUDIO_OFFSET)) player.setAudioOffsetMs(offsetMs);
    }

    public void reset() {
        App.removeCallbacks(runnable);
        retry = 0;
        localProxyRetry = 0;
    }

    public void clear() {
        prepareSeq++;
        lutApplySeq++;
        spec = null;
        currentDanmakuUrl = null;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        pendingLutPreview = false;
        waitingLutBeforePlay = false;
        clearLutWarmupRecovery();
    }

    public void resetTrack() {
        engine.resetTrack();
    }

    public void toggleDecode() {
        engine.setDecode(engine.isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD);
        rebuildPlayer();
        setMediaItem();
    }

    public void togglePlayer() {
        switchPlayer(playerType == PlayerSetting.EXO ? PlayerSetting.IJK : PlayerSetting.EXO);
    }

    public void switchPlayer(int type) {
        switchPlayer(type, true);
    }

    private void switchPlayer(int type, boolean persist) {
        if (engine == null || player == null) return;
        type = PlayerSetting.sanitizePlayer(type);
        if (type == playerType) return;
        long position = getPosition();
        float speed = getSpeed();
        boolean repeat = isRepeatOne();
        int decode = engine.getDecode();
        prepareSeq++;
        resetLutRuntimeState("switch_player", true);
        engine.release();
        playerType = type;
        if (persist) {
            exoFallbackTried = false;
            PlayerSetting.putPlayer(type);
        }
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "switch player type=%d persist=%s position=%d spec=%s", type, persist, position, debugSpec());
        engine = buildEngine(playerType, decode);
        player = engine.getPlayer();
        callback.onPlayerRebuild(player);
        if (spec == null || spec.getUrl() == null) return;
        setMediaItem();
        if (position > 0) seekTo(position);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
    }

    private void rebuildPlayer() {
        player = engine.rebuild(listener);
        videoEffectsActive = false;
        videoEffectsDirty = false;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        pendingLutPreview = false;
        waitingLutBeforePlay = false;
        callback.onPlayerRebuild(player);
    }

    private PlayerEngine buildEngine(int type, int decode) {
        return type == PlayerSetting.IJK ? new IjkPlayerEngine(decode, listener) : new ExoPlayerEngine(decode, listener);
    }

    public void browse(PlaySpec spec) {
        reset();
        clear();
        stopParse();
        start(spec, Constant.TIMEOUT_PLAY);
    }

    public void start(PlaySpec spec, long timeout) {
        this.spec = spec;
        retry = 0;
        exoFallbackTried = false;
        realtimeFallbackTried = false;
        localProxyRetry = 0;
        currentDanmakuUrl = null;
        setMediaItem(timeout);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata) {
        stopParse();
        spec = PlaySpec.fromParse(result, key, metadata);
        retry = 0;
        exoFallbackTried = false;
        realtimeFallbackTried = false;
        localProxyRetry = 0;
        currentDanmakuUrl = null;
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
    }

    public void setMediaItem() {
        setMediaItem(Constant.TIMEOUT_PLAY);
    }

    private void setMediaItem(long timeout) {
        if (spec == null || spec.getUrl() == null) return;
        int seq = ++prepareSeq;
        if (LocalProxyDebug.shouldAwaitReady(spec.getUrl())) {
            awaitLocalProxyAndSetMediaItem(seq, timeout);
            return;
        }
        setMediaItemNow(timeout, true);
    }

    private void awaitLocalProxyAndSetMediaItem(int seq, long timeout) {
        PlaySpec target = spec;
        String url = target.getUrl();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy await start seq=%d timeout=%d spec=%s", seq, timeout, debugSpec());
        Task.execute(() -> {
            boolean ready = LocalProxyDebug.awaitReady(url, LOCAL_PROXY_READY_TIMEOUT_MS);
            App.post(() -> {
                if (seq != prepareSeq || spec != target || engine == null) {
                    if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy await skip seq=%d current=%d ready=%s", seq, prepareSeq, ready);
                    return;
                }
                if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy await done seq=%d ready=%s spec=%s", seq, ready, debugSpec());
                setMediaItemNow(timeout, true);
            });
        });
    }

    private void setMediaItemNow(long timeout, boolean notifyPrepare) {
        if (spec == null || spec.getUrl() == null || engine == null) return;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "setMediaItem timeout=%d notify=%s spec=%s", timeout, notifyPrepare, debugSpec());
        App.removeCallbacks(runnable);
        setDanmakus(spec.getDanmakus());
        prepareLutPipeline();
        initTrack = false;
        waitingLutBeforePlay = false;
        engine.start(spec.checkUa());
        App.post(runnable, timeout);
        if (notifyPrepare) callback.onPrepare();
    }

    private void prepareLutPipeline() {
        lutApplySeq++;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        lutPipelineReadyForItem = false;
        lutPipelinePrepareInProgress = false;
        pendingLutPreview = false;
        dynamicLutEffect.clear();
        clearLutWarmupRecovery();
        if (videoEffectsDirty) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "rebuild clean player before media item reason=prepare spec=%s", debugSpec());
            rebuildPlayer();
        }
        clearVideoEffects("prepare");
    }

    private boolean shouldPrepareLutBeforePlay() {
        return false;
    }

    public void applyLut(boolean notify) {
        applyLut(notify, false);
    }

    public void applyLutPreview(boolean notify) {
        applyLut(notify, true);
    }

    private void applyLut(boolean notify, boolean preview) {
        if (engine == null) return;
        int seq = ++lutApplySeq;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "request seq=%d notify=%s preview=%s enabled=%s preset=%s state=%s videoFormat=%s tracksEmpty=%s active=%s dirty=%s applied=%s applying=%s pendingPreview=%s", seq, notify, preview, LutSetting.isEnabled(), LutSetting.getPresetId(), stateName(player.getPlaybackState()), engine.getVideoFormat(), engine.getCurrentTracks() == null || engine.getCurrentTracks().isEmpty(), videoEffectsActive, videoEffectsDirty, lutAppliedForItem, lutApplyInProgress, pendingLutPreview);
        if (!lutAllowed) {
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("disallowed");
            completeLutBeforePlay("disallowed");
            return;
        }
        if (!LutSetting.isEnabled()) {
            if (waitingLutBeforePlay && shouldWaitForVideoFormat()) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before neutral start reason=off state=%s spec=%s", stateName(player.getPlaybackState()), debugSpec());
                return;
            }
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("off");
            completeLutBeforePlay("off");
            return;
        }
        LutPreset preset = LutStore.find(LutSetting.getPresetId());
        if (preset == null) {
            if (waitingLutBeforePlay && shouldWaitForVideoFormat()) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before neutral start reason=missing state=%s spec=%s", stateName(player.getPlaybackState()), debugSpec());
                return;
            }
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("missing");
            completeLutBeforePlay("missing");
            if (notify) Notify.show(R.string.lut_missing);
            return;
        }
        String reason = getLutUnavailableReason();
        if (!TextUtils.isEmpty(reason)) {
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects(reason);
            completeLutBeforePlay(reason);
            if (notify) Notify.show(reason);
            return;
        }
        if (shouldWaitForLutFormat()) {
            lutAppliedForItem = false;
            lutApplyInProgress = false;
            if (notify || preview) pendingLutPreview = preview;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format notify=%s preview=%s state=%s spec=%s", notify, preview, stateName(player.getPlaybackState()), debugSpec());
            return;
        }
        if (notify || preview) pendingLutPreview = preview;
        if (!ensureLutPipelineReadyForCurrentItem("request")) {
            return;
        }
        lutAppliedForItem = false;
        lutApplyInProgress = true;
        pendingLutPreview = false;
        int strength = LutSetting.getStrength();
        int previewSeconds = LutSetting.getPreviewSeconds();
        Task.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                ColorLut colorLut = LutEffectFactory.createColorLut(preset, strength);
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "create dynamic preset=%s format=%s strength=%d preview=%s seconds=%d cost=%dms", preset.getId(), preset.getFormat(), strength, preview, previewSeconds, System.currentTimeMillis() - start);
                App.post(() -> applyLutColor(seq, colorLut, notify, preview, previewSeconds));
            } catch (Throwable e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "create failed preset=%s strength=%d error=%s", preset.getId(), strength, causeChain(e));
                App.post(() -> {
                    if (seq != lutApplySeq || engine == null) return;
                    lutApplyInProgress = false;
                    setNeutralVideoEffects("error");
                    completeLutBeforePlay("error");
                    if (notify) Notify.show(R.string.lut_apply_failed);
                });
            }
        });
    }

    private void applyLutColor(int seq, ColorLut colorLut, boolean notify, boolean preview, int previewSeconds) {
        if (seq != lutApplySeq || engine == null) return;
        String reason = getLutUnavailableReason();
        if (!TextUtils.isEmpty(reason)) {
            dynamicLutEffect.clear();
            lutApplyInProgress = false;
            setNeutralVideoEffects(reason);
            completeLutBeforePlay(reason);
            if (notify) Notify.show(reason);
            return;
        }
        if (shouldWaitForLutFormat()) {
            lutAppliedForItem = false;
            lutApplyInProgress = false;
            if (notify || preview) pendingLutPreview = preview;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before set effects preview=%s spec=%s", preview, debugSpec());
            return;
        }
        dynamicLutEffect.set(colorLut, preview, previewSeconds);
        if (safeSetVideoEffects(dynamicLutEffect.effects(), preview ? "preview_dynamic" : "apply_dynamic")) {
            lutAppliedForItem = true;
            pendingLutPreview = false;
        } else {
            lutAppliedForItem = false;
        }
        lutApplyInProgress = false;
        completeLutBeforePlay(preview ? "preview" : "apply");
    }

    private void applyLutForCurrentItem() {
        if (engine == null) return;
        if (!lutAllowed) {
            if (lutAppliedForItem && !videoEffectsActive && !waitingLutBeforePlay) return;
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("auto_disallowed");
            completeLutBeforePlay("auto_disallowed");
            return;
        }
        if (!LutSetting.isEnabled()) {
            if (lutAppliedForItem && !videoEffectsActive && !waitingLutBeforePlay) return;
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects("auto_off");
            completeLutBeforePlay("auto_off");
            return;
        }
        String reason = getLutUnavailableReason();
        if (!TextUtils.isEmpty(reason)) {
            lutAppliedForItem = true;
            lutApplyInProgress = false;
            pendingLutPreview = false;
            lutWarmupReloadPreviewPending = false;
            setNeutralVideoEffects(reason);
            completeLutBeforePlay(reason);
            return;
        }
        if (shouldWaitForLutFormat()) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before auto apply state=%s spec=%s", stateName(player.getPlaybackState()), debugSpec());
            return;
        }
        if (lutApplyInProgress) return;
        if (lutAppliedForItem) return;
        if (!ensureLutPipelineReadyForCurrentItem("auto")) return;
        boolean preview = pendingLutPreview || lutWarmupReloadPreviewPending;
        lutWarmupReloadPreviewPending = false;
        applyLut(false, preview);
    }

    private void completeLutBeforePlay(String reason) {
        if (!waitingLutBeforePlay || player == null) return;
        waitingLutBeforePlay = false;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "start playback after lut decision reason=%s active=%s dirty=%s", reason, videoEffectsActive, videoEffectsDirty);
        player.play();
    }

    private boolean ensureLutPipelineReadyForCurrentItem(String reason) {
        if (lutPipelineReadyForItem) return true;
        if (lutPipelinePrepareInProgress) return false;
        if (!canWarmLutPipeline()) return true;
        if (shouldWaitForVideoFormat()) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "wait video format before pipeline warmup reason=%s state=%s spec=%s", reason, stateName(player.getPlaybackState()), debugSpec());
            return false;
        }
        String unavailable = getLutUnavailableReason();
        if (!TextUtils.isEmpty(unavailable)) return true;
        return prepareLutPipelineForCurrentItem(reason);
    }

    private boolean prepareLutPipelineForCurrentItem(String reason) {
        if (spec == null || engine == null || player == null) return true;
        lutPipelinePrepareInProgress = true;
        lutAppliedForItem = false;
        lutApplyInProgress = false;
        dynamicLutEffect.clear();
        long position = Math.max(0, getPosition());
        boolean playWhenReady = player.getPlayWhenReady();
        float speed = getSpeed();
        if (!safeSetVideoEffects(dynamicLutEffect.effects(), reason + "_prepare_dynamic_passthrough")) {
            lutPipelinePrepareInProgress = false;
            return true;
        }
        lutPipelineReadyForItem = true;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "prepare current item with effects reason=%s position=%d play=%s spec=%s", reason, position, playWhenReady, debugSpec());
        startLutWarmupRecovery();
        engine.restart(spec.checkUa(), position, playWhenReady);
        if (speed != 1f) setSpeed(speed);
        lutPipelinePrepareInProgress = false;
        return false;
    }

    private void startLutWarmupRecovery() {
        lutWarmupRecoveryActive = true;
        lutWarmupRefreshRequested = false;
        lutWarmupRecoveredErrors = 0;
    }

    private void clearLutWarmupRecovery() {
        lutWarmupRecoveryActive = false;
        lutWarmupRefreshRequested = false;
        lutWarmupRecoveredErrors = 0;
    }

    private boolean retryLutWarmupByRefresh(String reason) {
        if (!lutWarmupRecoveryActive || lutWarmupRefreshRequested || !LutSetting.isEnabled()) return false;
        lutWarmupRefreshRequested = true;
        lutWarmupRecoveryActive = false;
        lutWarmupReloadPreviewPending = true;
        App.removeCallbacks(runnable);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "request playback refresh after warmup failure reason=%s errors=%d spec=%s", reason, lutWarmupRecoveredErrors, debugSpec());
        callback.onReload(RELOAD_LUT_WARMUP);
        return true;
    }

    private boolean retryLutWarmupByRefresh(PlayerEngine.ErrorAction action, PlaybackException e) {
        if (!lutWarmupRecoveryActive || lutWarmupRefreshRequested || !LutSetting.isEnabled()) return false;
        if (action == PlayerEngine.ErrorAction.RECOVERED && ++lutWarmupRecoveredErrors < LUT_WARMUP_RECOVERED_ERROR_REFRESH_THRESHOLD) return false;
        if (action != PlayerEngine.ErrorAction.RECOVERED && action != PlayerEngine.ErrorAction.FATAL && action != PlayerEngine.ErrorAction.RELOAD) return false;
        return retryLutWarmupByRefresh("error_" + e.errorCode + "_" + action);
    }

    private boolean shouldWaitForLutFormat() {
        if (engine == null || !LutSetting.isEnabled()) return false;
        return shouldWaitForVideoFormat();
    }

    private boolean shouldWaitForVideoFormat() {
        if (engine == null) return false;
        Format currentFormat = engine.getVideoFormat();
        if (isUsableVideoFormat(currentFormat)) return false;
        Tracks tracks = engine.getCurrentTracks();
        if (tracks == null || tracks.isEmpty()) return true;
        boolean hasVideo = false;
        boolean hasUsableFormat = false;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            hasVideo = true;
            for (int i = 0; i < group.length; i++) {
                if (isUsableVideoFormat(group.getTrackFormat(i))) {
                    hasUsableFormat = true;
                    break;
                }
            }
            if (hasUsableFormat) break;
        }
        return hasVideo && !hasUsableFormat;
    }

    private boolean isUsableVideoFormat(Format format) {
        if (format == null) return false;
        return !TextUtils.isEmpty(format.sampleMimeType) || !TextUtils.isEmpty(format.codecs) || format.colorInfo != null || format.width > 0 || format.height > 0;
    }

    private void clearVideoEffects(String reason) {
        dynamicLutEffect.clear();
        safeSetVideoEffects(Collections.emptyList(), reason);
    }

    private void setNeutralVideoEffects(String reason) {
        dynamicLutEffect.clear();
        if (canKeepWarmNeutralEffects()) safeSetVideoEffects(dynamicLutEffect.effects(), reason + "_dynamic_passthrough");
        else clearVideoEffects(reason);
    }

    private boolean canKeepWarmNeutralEffects() {
        if (!LutSetting.isEnabled()) return false;
        if (!canWarmLutPipeline()) return false;
        if (shouldWaitForVideoFormat()) return false;
        return TextUtils.isEmpty(getLutUnavailableReason());
    }

    private boolean canWarmLutPipeline() {
        if (!lutAllowed) return false;
        if (engine == null || !engine.supportsVideoEffects()) return false;
        if (spec != null && spec.getDrm() != null) return false;
        if (PlayerSetting.isTunnel()) return false;
        if (engine.getDecode() == PlayerEngine.SOFT) return false;
        if (PlayerSetting.isVideoPrefer()) return false;
        return true;
    }

    private boolean safeSetVideoEffects(List<Effect> effects, String reason) {
        if (engine == null) return false;
        boolean empty = effects == null || effects.isEmpty();
        if (empty && !videoEffectsActive) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "skip clear effects reason=%s", reason);
            return false;
        }
        try {
            engine.setVideoEffects(empty ? Collections.emptyList() : effects);
            if (empty) {
                videoEffectsActive = false;
            } else {
                videoEffectsActive = true;
                videoEffectsDirty = true;
            }
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "set effects=%d reason=%s active=%s dirty=%s", empty ? 0 : effects.size(), reason, videoEffectsActive, videoEffectsDirty);
            return true;
        } catch (Throwable e) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "set effects failed reason=%s error=%s", reason, causeChain(e));
            return false;
        }
    }

    private void setDanmakus(List<Danmaku> items) {
        setDanmaku(items == null || items.isEmpty() ? Danmaku.empty() : items.get(0));
    }

    public void setDanmaku(Danmaku item) {
        setDanmaku(item, false);
    }

    public void reloadDanmaku(Danmaku item) {
        setDanmaku(item, true);
    }

    private void setDanmaku(Danmaku item, boolean force) {
        if (danmakuController == null) return;
        if (spec != null) spec.setDanmaku(item);
        if (item.isEmpty()) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "clear current=%s", summarizeUrl(currentDanmakuUrl));
            if (currentDanmakuUrl != null) danmakuController.clearItems();
            currentDanmakuUrl = null;
            return;
        }
        String url = item.getRealUrl();
        if (!force && TextUtils.equals(currentDanmakuUrl, url)) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "skip same url=%s", summarizeUrl(url));
            return;
        }
        if (force && currentDanmakuUrl != null) danmakuController.clearItems();
        currentDanmakuUrl = url;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("danmaku", "%s name=%s url=%s", force ? "reload" : "load", item.getName(), summarizeUrl(url));
        danmakuController.setDataSource(Uri.parse(url));
    }

    public void addDanmaku(Danmaku item) {
        if (danmakuController == null || item.isEmpty()) return;
        if (spec != null) spec.addDanmaku(item);
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (!TextUtils.isEmpty(from)) Notify.show(ResUtil.getString(R.string.parse_from, from));
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "parseSuccess from=%s url=%s headers=%s", from, summarizeUrl(url), headers == null ? 0 : headers.size());
        if (headers != null) headers.remove(HttpHeaders.RANGE);
        if (spec != null) spec.setHeaders(headers);
        if (spec != null) spec.setUrl(url);
        setMediaItem();
    }

    @Override
    public void onParseError() {
        callback.onError(ResUtil.getString(R.string.error_play_parse));
    }

    private String debugSpec() {
        if (spec == null) return "null";
        return "key=" + spec.getKey() +
                ", url=" + summarizeUrl(spec.getUrl()) +
                ", format=" + spec.getFormat() +
                ", headers=" + (spec.getHeaders() == null ? 0 : spec.getHeaders().size()) +
                ", subs=" + (spec.getSubs() == null ? 0 : spec.getSubs().size()) +
                ", danmakus=" + (spec.getDanmakus() == null ? 0 : spec.getDanmakus().size());
    }

    private static String summarizeUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        int port = uri.getPort();
        String path = uri.getPath();
        StringBuilder builder = new StringBuilder();
        builder.append(uri.getScheme()).append("://");
        builder.append(TextUtils.isEmpty(host) ? "unknown" : host);
        if (port > 0) builder.append(':').append(port);
        if (!TextUtils.isEmpty(path)) builder.append(path.length() > 48 ? path.substring(0, 48) + "..." : path);
        builder.append(" len=").append(url.length());
        return builder.toString();
    }

    private static String stateName(int state) {
        return switch (state) {
            case Player.STATE_IDLE -> "IDLE";
            case Player.STATE_BUFFERING -> "BUFFERING";
            case Player.STATE_READY -> "READY";
            case Player.STATE_ENDED -> "ENDED";
            default -> String.valueOf(state);
        };
    }

    private static String causeChain(Throwable error) {
        if (error == null) return "null";
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 8) {
            if (builder.length() > 0) builder.append(" <- ");
            builder.append(current.getClass().getName());
            if (!TextUtils.isEmpty(current.getMessage())) builder.append(": ").append(current.getMessage());
            current = current.getCause();
        }
        return builder.toString();
    }

    public interface Callback {

        void onPrepare();

        void onTracksChanged();

        void onTitlesChanged();

        void onError(String msg);

        void onReload(String msg);

        void onPlayerRebuild(Player newPlayer);
    }

    private final Player.Listener listener = new Player.Listener() {

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state != Player.STATE_IDLE) App.removeCallbacks(runnable);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "state=%s spec=%s", stateName(state), debugSpec());
            if (state == Player.STATE_READY) {
                clearLutWarmupRecovery();
                applyLutForCurrentItem();
            }
        }

        @Override
        public void onVideoSizeChanged(@NonNull VideoSize size) {
            videoSize = size;
            applyLutForCurrentItem();
        }

        @Override
        public void onTracksChanged(@NonNull Tracks tracks) {
            if (!tracks.isEmpty() && !initTrack) {
                setTrack(Track.find(getKey()));
                callback.onTracksChanged();
                initTrack = true;
            }
            applyLutForCurrentItem();
        }

        @Override
        public void onMediaTitlesChanged(@NonNull List<MediaTitle> titles) {
            callback.onTitlesChanged();
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException e) {
            App.removeCallbacks(runnable);
            PlayerEngine.ErrorAction action = engine.handleError(e);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "error code=%d message=%s action=%s retry=%d spec=%s cause=%s", e.errorCode, e.getMessage(), action, retry, debugSpec(), causeChain(e));
            LocalProxyDebug.dumpIfLocalFailure(spec == null ? null : spec.getUrl(), e);
            if (retryLutFailure(e)) return;
            if (retryLutWarmupByRefresh(action, e)) return;
            if (action == PlayerEngine.ErrorAction.FATAL && retryLocalProxy(e)) return;
            if (action == PlayerEngine.ErrorAction.FATAL && retryRealtimeFallback(e)) return;
            if (action == PlayerEngine.ErrorAction.FATAL && retryExoFallback(e)) return;
            if (action == PlayerEngine.ErrorAction.RELOAD) {
                callback.onReload(engine.getErrorMessage(e));
                return;
            }
            if (action == PlayerEngine.ErrorAction.RECOVERED) {
                if (spec != null) setDanmakus(spec.getDanmakus());
                return;
            }
            if (action == PlayerEngine.ErrorAction.FATAL) {
                callback.onError(engine.getErrorMessage(e));
            } else if (++retry > 1) {
                callback.onError(engine.getErrorMessage(e));
            } else {
                toggleDecode();
            }
        }
    };

    private boolean retryLutFailure(PlaybackException e) {
        if (!LutSetting.isEnabled()) return false;
        if (e.errorCode != PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED && e.errorCode != PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED) return false;
        App.removeCallbacks(runnable);
        LutSetting.select(null);
        lutAppliedForItem = true;
        lutApplyInProgress = false;
        pendingLutPreview = false;
        lutWarmupReloadPreviewPending = false;
        clearVideoEffects("lut_error_retry");
        Notify.show(R.string.lut_apply_failed);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "disable and retry after frame processing error code=%d spec=%s cause=%s", e.errorCode, debugSpec(), causeChain(e));
        if (spec != null) setMediaItem();
        return true;
    }

    private boolean retryLocalProxy(PlaybackException e) {
        if (spec == null || !LocalProxyDebug.isLocalProxyUrl(spec.getUrl())) return false;
        if (!LocalProxyDebug.isConnectionRefused(e)) return false;
        if (++localProxyRetry > LOCAL_PROXY_MAX_RETRY) return false;
        int attempt = localProxyRetry;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy retry schedule attempt=%d delay=%d spec=%s", attempt, LOCAL_PROXY_RETRY_DELAY_MS, debugSpec());
        App.removeCallbacks(runnable);
        App.post(() -> {
            if (spec == null || attempt != localProxyRetry) return;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy retry start attempt=%d spec=%s", attempt, debugSpec());
            setMediaItem();
        }, LOCAL_PROXY_RETRY_DELAY_MS);
        return true;
    }

    private boolean retryRealtimeFallback(PlaybackException e) {
        if (!canFallbackRealtimeToIjk()) return false;
        realtimeFallbackTried = true;
        exoFallbackTried = true;
        App.removeCallbacks(runnable);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "exo realtime fallback to ijk code=%d message=%s spec=%s cause=%s", e.errorCode, e.getMessage(), debugSpec(), causeChain(e));
        switchPlayer(PlayerSetting.IJK, false);
        return true;
    }

    private boolean retryRealtimeFallback(String reason) {
        if (!canFallbackRealtimeToIjk()) return false;
        realtimeFallbackTried = true;
        exoFallbackTried = true;
        App.removeCallbacks(runnable);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "exo realtime fallback to ijk reason=%s spec=%s", reason, debugSpec());
        switchPlayer(PlayerSetting.IJK, false);
        return true;
    }

    private boolean canFallbackRealtimeToIjk() {
        if (playerType != PlayerSetting.EXO) return false;
        if (realtimeFallbackTried || spec == null || TextUtils.isEmpty(spec.getUrl())) return false;
        String scheme = spec.getUri().getScheme();
        return "rtp".equalsIgnoreCase(scheme) || "udp".equalsIgnoreCase(scheme);
    }

    private boolean retryExoFallback(PlaybackException e) {
        if (playerType != PlayerSetting.IJK) return false;
        if (exoFallbackTried || spec == null || TextUtils.isEmpty(spec.getUrl())) return false;
        exoFallbackTried = true;
        App.removeCallbacks(runnable);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "ijk fatal fallback to exo code=%d message=%s spec=%s cause=%s", e.errorCode, e.getMessage(), debugSpec(), causeChain(e));
        switchPlayer(PlayerSetting.EXO, false);
        return true;
    }

    private boolean retryExoFallback(String reason) {
        if (playerType != PlayerSetting.IJK) return false;
        if (exoFallbackTried || spec == null || TextUtils.isEmpty(spec.getUrl())) return false;
        exoFallbackTried = true;
        App.removeCallbacks(runnable);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "ijk fallback to exo reason=%s spec=%s", reason, debugSpec());
        switchPlayer(PlayerSetting.EXO, false);
        return true;
    }
}
