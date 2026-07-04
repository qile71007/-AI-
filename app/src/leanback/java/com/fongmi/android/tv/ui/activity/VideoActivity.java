package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.VerticalGridView;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.CustomTarget;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.model.SearchProgress;
import com.fongmi.android.tv.playback.PlaybackEventCollector;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.player.lut.LutStore;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PlayerButtonSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.ui.adapter.ArrayAdapter;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.adapter.PartAdapter;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.adapter.QuickAdapter;
import com.fongmi.android.tv.ui.custom.CustomKeyDownVod;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.ui.custom.PlayerOsdController;
import com.fongmi.android.tv.ui.dialog.ContentDialog;
import com.fongmi.android.tv.ui.dialog.DanmakuDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeListDialog;
import com.fongmi.android.tv.ui.dialog.QuickSearchDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.TitleDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.crawler.SpiderDebug;
import com.github.bassaer.library.MDColor;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class VideoActivity extends PlaybackActivity implements CustomKeyDownVod.Listener, TrackDialog.Listener, ArrayAdapter.OnClickListener, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, ParseAdapter.OnClickListener, Clock.Callback {

    private ActivityVideoBinding mBinding;
    private ViewGroup.LayoutParams mFrameParams;
    private Observer<Result> mObserveDetail;
    private Observer<Result> mObservePlayer;
    private Observer<Result> mObserveSearch;
    private Observer<SearchProgress> mObserveSearchProgress;
    private EpisodeAdapter mEpisodeAdapter;
    private QualityAdapter mQualityAdapter;
    private ArrayAdapter mArrayAdapter;
    private ParseAdapter mParseAdapter;
    private QuickAdapter mQuickAdapter;
    private FlagAdapter mFlagAdapter;
    private PartAdapter mPartAdapter;
    private Map<String, View> mActionButtons;
    private QuickSearchDialog mQuickSearchDialog;
    private PlayerOsdController mOsd;
    private CustomKeyDownVod mKeyDown;
    private SiteViewModel mViewModel;
    private List<String> mBroken;
    private History mHistory;
    private boolean fullscreen;
    private boolean initAuto;
    private boolean autoMode;
    private boolean revealManualSearch;
    private boolean quickSearchDialogClosed;
    private boolean useParse;
    private boolean detailRequested;
    private boolean detailHealthRecorded;
    private boolean playHealthRecorded;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Clock mClock;
    private View mFocus1;
    private View mFocus2;
    private Result mPendingDetail;
    private Result mPendingPlayer;
    private String mContextWallUrl;
    private String mContextWallLockedUrl;
    private String playHealthKey;
    private long detailStartTime;
    private long playerStartTime;
    private boolean pendingLutImport;

    private final ActivityResultLauncher<Intent> mLutDir = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        LutStore.setUserDir(result.getData().getData(), result.getData().getFlags());
        Notify.show(R.string.lut_directory_selected);
        mBinding.lutQuick.refreshList();
        if (pendingLutImport) {
            pendingLutImport = false;
            chooseLutFile();
        }
    });

    private final ActivityResultLauncher<Intent> mLutFile = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (TextUtils.isEmpty(path)) {
            Notify.show(R.string.lut_import_failed);
            return;
        }
        Task.execute(() -> {
            try {
                LutPreset preset = LutStore.importFile(path);
                App.post(() -> {
                    Notify.show(R.string.lut_imported);
                    mBinding.lutQuick.selectImported(preset, player(), mBinding.exo, this::onLutChanged);
                });
            } catch (Exception e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "import failed path=%s error=%s", path, e.getMessage());
                App.post(() -> Notify.show(Notify.getError(R.string.lut_import_failed, e)));
            }
        });
    });

    public static void push(FragmentActivity activity, String text) {
        if (FileChooser.isValid(activity, Uri.parse(text))) file(activity, FileChooser.getPathFromUri(Uri.parse(text)));
        else start(activity, Sniffer.getUrl(text));
    }

    public static void file(FragmentActivity activity, String path) {
        if (TextUtils.isEmpty(path)) return;
        String name = new File(path).getName();
        start(activity, SiteApi.PUSH, "file://" + path, name);
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic(), null, false, true, history.getWallPic());
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true, false);
    }

    public static void collect(Activity activity, String key, String id, String name, String pic, String wallPic) {
        start(activity, key, id, name, pic, null, true, false, wallPic);
    }

    public static void start(Activity activity, String url) {
        start(activity, SiteApi.PUSH, url, url);
    }

    public static void start(Activity activity, String key, String id, String name) {
        start(activity, key, id, name, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, false, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, String wallPic) {
        start(activity, key, id, name, pic, mark, false, false, wallPic);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, String wallPic, String content) {
        start(activity, key, id, name, pic, mark, false, false, wallPic, content);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast) {
        start(activity, key, id, name, pic, mark, collect, cast, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, String wallPic) {
        start(activity, key, id, name, pic, mark, collect, cast, wallPic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, String wallPic, String content) {
        long launch = System.currentTimeMillis();
        SpiderDebug.log("video-flow", "launch request key=%s id=%s name=%s collect=%s cast=%s", key, id, name, collect, cast);
        ImgUtil.preload(activity, pic);
        if (Setting.isPlaybackArtworkWall() && !TextUtils.isEmpty(wallPic) && !TextUtils.equals(wallPic, pic)) ImgUtil.preload(activity, wallPic);
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("launchTime", launch);
        intent.putExtra("collect", collect);
        intent.putExtra("cast", cast);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("wallPic", wallPic);
        intent.putExtra("content", content);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
        SpiderDebug.log("video-flow", "launch dispatched cost=%dms key=%s id=%s", System.currentTimeMillis() - launch, key, id);
    }

    private boolean isCast() {
        return getIntent().getBooleanExtra("cast", false);
    }

    private String getName() {
        return Objects.toString(getIntent().getStringExtra("name"), "");
    }

    private String getPic() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
    }

    private String getWallPic() {
        return Objects.toString(getIntent().getStringExtra("wallPic"), "");
    }

    private String getContent() {
        return Objects.toString(getIntent().getStringExtra("content"), "");
    }

    private String getMark() {
        return Objects.toString(getIntent().getStringExtra("mark"), "");
    }

    private String getKey() {
        return Objects.toString(getIntent().getStringExtra("key"), "");
    }

    private String getId() {
        return Objects.toString(getIntent().getStringExtra("id"), "");
    }

    private String getHistoryKey() {
        return getKey().concat(AppDatabase.SYMBOL).concat(getId()).concat(AppDatabase.SYMBOL) + VodConfig.getCid();
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private Flag getFlag() {
        return mFlagAdapter.getActivated();
    }

    private Episode getEpisode() {
        return mEpisodeAdapter.getActivated();
    }

    private String getOsdTitle() {
        String name = getName();
        if (mEpisodeAdapter == null || mEpisodeAdapter.getItemCount() == 0) return name;
        String episode = Objects.toString(getEpisode().getName(), "");
        if (TextUtils.isEmpty(episode) || TextUtils.equals(name, episode)) return name;
        return TextUtils.isEmpty(name) ? episode : name + " " + episode;
    }

    private int getScale() {
        return mHistory != null && mHistory.getScale() != -1 ? mHistory.getScale() : PlayerSetting.getScale();
    }

    private boolean isReplay() {
        return Setting.getReset() == 1;
    }

    private boolean isFromCollect() {
        return getIntent().getBooleanExtra("collect", false);
    }

    private long getLaunchTime() {
        return getIntent().getLongExtra("launchTime", 0);
    }

    private long getLaunchCost(long now) {
        long launchTime = getLaunchTime();
        return launchTime <= 0 ? 0 : now - launchTime;
    }

    @Override
    protected ViewBinding getBinding() {
        long start = System.currentTimeMillis();
        mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
        SpiderDebug.log("video-flow", "inflate cost=%dms sinceLaunch=%dms", System.currentTimeMillis() - start, getLaunchCost(start));
        return mBinding;
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
    }

    @Override
    protected PlayerView getExoView() {
        return mBinding.exo;
    }

    @Override
    protected CustomSeekView getSeekView() {
        return mBinding.control.seek;
    }

    @Override
    protected void onServiceConnected() {
        SpiderDebug.log("video-flow", "service ready sinceLaunch=%dms key=%s id=%s", getLaunchCost(System.currentTimeMillis()), getKey(), getId());
        player().setDanmakuController(mBinding.exo.getDanmakuController());
        setPlayerKernel();
        setDecode();
        setLut();
        if (!detailRequested) checkId();
        if (mPendingDetail != null) {
            Result result = mPendingDetail;
            mPendingDetail = null;
            setDetail(result);
        }
        if (mPendingPlayer != null) {
            Result result = mPendingPlayer;
            mPendingPlayer = null;
            setPlayer(result);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        String oldId = getId();
        super.onNewIntent(intent);
        String id = Objects.toString(intent.getStringExtra("id"), "");
        if (TextUtils.isEmpty(id) || id.equals(oldId)) return;
        saveHistory();
        getIntent().putExtras(intent);
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        long start = System.currentTimeMillis();
        SpiderDebug.log("video-flow", "initView start sinceLaunch=%dms key=%s id=%s", getLaunchCost(start), getKey(), getId());
        if (!isCast() && hasInitialPreview()) showInitialPreview();
        super.initView(savedInstanceState);
        SpiderDebug.log("video-flow", "initView after playback cost=%dms", System.currentTimeMillis() - start);
        mFrameParams = mBinding.video.getLayoutParams();
        mClock = Clock.create(mBinding.widget.clock);
        mKeyDown = CustomKeyDownVod.create(this);
        mObserveDetail = this::setDetail;
        mObservePlayer = this::setPlayer;
        mObserveSearch = this::setSearch;
        mObserveSearchProgress = this::setSearchProgress;
        mBroken = new ArrayList<>();
        mR1 = this::hideControl;
        mR2 = this::updateFocus;
        mR3 = this::setTraffic;
        mR4 = this::showEmpty;
        SpiderDebug.log("video-flow", "initView state ready cost=%dms", System.currentTimeMillis() - start);
        checkCast();
        SpiderDebug.log("video-flow", "initView preview ready cost=%dms", System.currentTimeMillis() - start);
        setRecyclerView();
        setShortDisplay();
        mOsd = new PlayerOsdController(mBinding.osd.getRoot(), mBinding.osd.osdTopLeft, mBinding.osd.osdTopRight, mBinding.osd.osdBottomLeft, mBinding.osd.osdBottomRight, mBinding.osd.osdDiagnostics, mBinding.osd.osdMiniProgress, new PlayerOsdController.Source() {
            @Override
            public PlayerManager getPlayer() {
                return service() == null ? null : player();
            }

            @Override
            public String getTitle() {
                return getOsdTitle();
            }
        }, 14f);
        SpiderDebug.log("video-flow", "initView recycler ready cost=%dms", System.currentTimeMillis() - start);
        setVideoView();
        SpiderDebug.log("video-flow", "initView video view ready cost=%dms", System.currentTimeMillis() - start);
        setViewModel();
        checkId();
        SpiderDebug.log("video-flow", "initView end cost=%dms sinceLaunch=%dms", System.currentTimeMillis() - start, getLaunchCost(System.currentTimeMillis()));
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.keep.setOnClickListener(view -> onKeep());
        mBinding.search.setOnClickListener(view -> onSearch());
        mBinding.video.setOnClickListener(view -> onVideo());
        mBinding.change1.setOnClickListener(view -> onChange());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.speed.setUpListener(this::onSpeedAdd);
        mBinding.control.action.speed.setDownListener(this::onSpeedSub);
        mBinding.control.action.ending.setUpListener(this::onEndingAdd);
        mBinding.control.action.ending.setDownListener(this::onEndingSub);
        mBinding.control.action.opening.setUpListener(this::onOpeningAdd);
        mBinding.control.action.opening.setDownListener(this::onOpeningSub);
        mBinding.control.action.text.setUpListener(this::onSubtitleClick);
        mBinding.control.action.text.setDownListener(this::onSubtitleClick);
        mBinding.control.action.next.setOnClickListener(view -> checkNext());
        mBinding.control.action.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.action.episodes.setOnClickListener(view -> onEpisodes());
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.lut.setOnClickListener(view -> onLut());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.reset.setOnClickListener(view -> onReset());
        mBinding.control.action.title.setOnClickListener(view -> onTitle());
        mBinding.control.action.player.setOnClickListener(view -> onPlayerKernel());
        mBinding.control.action.player.setOnLongClickListener(view -> onChooseLong());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.ending.setOnClickListener(view -> onEnding());
        mBinding.control.action.repeat.setOnClickListener(view -> onRepeat());
        mBinding.control.action.change2.setOnClickListener(view -> onChange());
        mBinding.control.action.fullscreen.setOnClickListener(view -> onFullscreen());
        mBinding.control.action.danmaku.setOnClickListener(view -> onDanmaku());
        mBinding.control.action.opening.setOnClickListener(view -> onOpening());
        mBinding.shortDisplay.setOnClickListener(view -> onShortDisplay());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.reset.setOnLongClickListener(view -> onResetToggle());
        mBinding.control.action.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.opening.setOnLongClickListener(view -> onOpeningReset());
        setActionFocusScroll();
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.flag.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mFlagAdapter.getItemCount() > 0) onItemClick(mFlagAdapter.get(position));
            }
        });
        mBinding.episode.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (child != null && mBinding.video != mFocus1) mFocus1 = child.itemView;
            }
        });
        mBinding.episode.setOnKeyListener((view, keyCode, event) -> onEpisodeKey(event));
        mBinding.array.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                int count = mEpisodeAdapter.getItemCount();
                if (count > getEpisodeSegmentSize(count) && position > 1) scrollToEpisode(mArrayAdapter.getStart(position));
            }
        });
    }

    private void setActionFocusScroll() {
        HorizontalScrollView scroll = mBinding.control.action.getRoot();
        if (scroll.getChildCount() == 0 || !(scroll.getChildAt(0) instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus) scroll.post(() -> scroll.smoothScrollTo(Math.max(0, view.getLeft() - ResUtil.dp2px(24)), 0));
            });
        }
    }

    private void setRecyclerView() {
        mBinding.flag.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.flag.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        int episodeColumn = getEpisodeColumn();
        mBinding.episode.setNumColumns(episodeColumn);
        mBinding.episode.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.episode.setVerticalSpacing(ResUtil.dp2px(8));
        mBinding.episode.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_LOW_EDGE);
        mBinding.episode.setWindowAlignmentPreferKeyLineOverLowEdge(false);
        mBinding.episode.setWindowAlignmentPreferKeyLineOverHighEdge(false);
        mBinding.episode.setWindowAlignmentOffset(0);
        mBinding.episode.setWindowAlignmentOffsetPercent(0);
        mBinding.episode.setItemAlignmentOffset(0);
        mBinding.episode.setItemAlignmentOffsetPercent(0);
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this));
        mEpisodeAdapter.setColumn(episodeColumn);
        mBinding.quality.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quality.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this));
        mBinding.array.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.array.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.array.setAdapter(mArrayAdapter = new ArrayAdapter(this));
        mBinding.part.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.part.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.part.setAdapter(mPartAdapter = new PartAdapter(item -> initSearch(item, false)));
        mBinding.quick.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quick.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(this));
        mBinding.control.parse.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.control.parse.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.control.parse.setAdapter(mParseAdapter = new ParseAdapter(this));
        mParseAdapter.addAll(VodConfig.get().getParses());
    }

    private void setVideoView() {
        mBinding.control.action.danmaku.setVisibility(DanmakuSetting.isLoad() ? View.VISIBLE : View.GONE);
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        setupActionButtons();
    }

    private void setupActionButtons() {
        mActionButtons = new HashMap<>();
        addActionButton(PlayerButtonSetting.NEXT, mBinding.control.action.next);
        addActionButton(PlayerButtonSetting.PREV, mBinding.control.action.prev);
        addActionButton(PlayerButtonSetting.EPISODES, mBinding.control.action.episodes);
        addActionButton(PlayerButtonSetting.RESET, mBinding.control.action.reset);
        addActionButton(PlayerButtonSetting.CHANGE, mBinding.control.action.change2);
        addActionButton(PlayerButtonSetting.FULLSCREEN, mBinding.control.action.fullscreen);
        addActionButton(PlayerButtonSetting.PLAYER, mBinding.control.action.player);
        addActionButton(PlayerButtonSetting.DECODE, mBinding.control.action.decode);
        addActionButton(PlayerButtonSetting.SPEED, mBinding.control.action.speed);
        addActionButton(PlayerButtonSetting.SCALE, mBinding.control.action.scale);
        addActionButton(PlayerButtonSetting.LUT, mBinding.control.action.lut);
        addActionButton(PlayerButtonSetting.TEXT, mBinding.control.action.text);
        addActionButton(PlayerButtonSetting.AUDIO, mBinding.control.action.audio);
        addActionButton(PlayerButtonSetting.VIDEO, mBinding.control.action.video);
        addActionButton(PlayerButtonSetting.OPENING, mBinding.control.action.opening);
        addActionButton(PlayerButtonSetting.ENDING, mBinding.control.action.ending);
        addActionButton(PlayerButtonSetting.DANMAKU, mBinding.control.action.danmaku);
        addActionButton(PlayerButtonSetting.TITLE, mBinding.control.action.title);
        addActionButton(PlayerButtonSetting.REPEAT, mBinding.control.action.repeat);
        PlayerButtonSetting.applyOrder(mBinding.control.action.container, mActionButtons);
    }

    private void addActionButton(String id, View view) {
        mActionButtons.put(id, view);
    }

    private void applyActionButtonVisibility() {
        if (mActionButtons != null) PlayerButtonSetting.applyVisibility(mActionButtons);
    }

    private int getEpisodeColumn() {
        return mEpisodeAdapter == null ? 8 : mEpisodeAdapter.getColumn();
    }

    private void setDecode() {
        mBinding.control.action.decode.setText(player().getDecodeText());
    }

    private void setPlayerKernel() {
        mBinding.control.action.player.setText(player().getPlayerText());
    }

    private void setScale(int scale) {
        if (mHistory != null) mHistory.setScale(scale);
        if (SiteApi.PUSH.equals(getKey())) PlayerSetting.putScale(scale);
        applyResizeMode(scale);
        mBinding.exo.post(() -> applyResizeMode(scale));
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void setLut() {
        mBinding.control.action.lut.setText(player().getLutText());
    }

    private void onLutChanged() {
        setLut();
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observeForever(mObserveDetail);
        mViewModel.getPlayer().observeForever(mObservePlayer);
        mViewModel.getSearch().observeForever(mObserveSearch);
        mViewModel.getSearchProgress().observeForever(mObserveSearchProgress);
    }

    private void checkCast() {
        if (isCast() && !isFullscreen()) enterFullscreen();
        else if (hasInitialPreview()) showInitialPreview();
        else mBinding.progressLayout.showProgress();
    }

    private void checkId() {
        if (detailRequested) return;
        detailRequested = true;
        if (getId().startsWith("push://")) getIntent().putExtra("key", SiteApi.PUSH).putExtra("id", getId().substring(7));
        if (getId().isEmpty() || getId().startsWith("msearch:")) setEmpty(false);
        else getDetail();
    }

    private void getDetail() {
        detailStartTime = System.currentTimeMillis();
        detailHealthRecorded = false;
        SpiderDebug.log("video-flow", "detail start key=%s id=%s name=%s", getKey(), getId(), getName());
        mViewModel.detailContent(getKey(), getId());
    }

    private void getDetail(Vod item) {
        revealManualSearch = false;
        if (!isAutoMode()) mViewModel.stopSearch();
        saveHistory();
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("pic", item.getPic());
        getIntent().putExtra("id", item.getId());
        mBinding.scroll.scrollTo(0, 0);
        mClock.setCallback(null);
        updateNavigationKey();
        if (service() != null) {
            player().reset();
            player().stop();
        }
        getDetail();
    }

    private void setDetail(Result result) {
        long cost = System.currentTimeMillis() - detailStartTime;
        SpiderDebug.log("video-flow", "detail finish cost=%dms empty=%s msg=%s", cost, result.getList().isEmpty(), result.getMsg());
        recordDetailHealth(result, cost);
        if (service() == null) {
            mPendingDetail = result;
            SpiderDebug.log("video-flow", "detail pending service key=%s id=%s", getKey(), getId());
            return;
        }
        if (result.getList().isEmpty()) setEmpty(result.hasMsg());
        else setDetail(result.getVod());
        Notify.show(result.getMsg());
    }

    private void setEmpty(boolean finish) {
        if (isFromCollect() || finish) {
            finish();
        } else if (getName().isEmpty()) {
            showEmpty();
        } else {
            mBinding.name.setText(getName());
            App.post(mR4, 10000);
            checkSearch(false);
        }
    }

    private void showEmpty() {
        mBinding.progressLayout.showEmpty();
    }

    private void setDetail(Vod item) {
        item.checkPic(getPic());
        item.checkName(getName());
        item.checkContent(getContent());
        mBinding.progressLayout.showContent();
        mBinding.name.setText(item.getName());
        mFlagAdapter.addAll(item.getFlags());
        mBinding.video.requestFocus();
        App.removeCallbacks(mR4);
        checkHistory(item);
        checkFlag(item);
        checkKeepImg();
        setText(item);
        updateKeep();
    }

    private void setText(Vod item) {
        mBinding.content.setTag(item.getContent());
        setText(mBinding.year, R.string.detail_year, item.getYear());
        setText(mBinding.area, R.string.detail_area, item.getArea());
        setText(mBinding.type, R.string.detail_type, item.getTypeName());
        setText(mBinding.site, R.string.detail_site, getSite().getName());
        setText(mBinding.director, R.string.detail_director, item.getDirector());
        setText(mBinding.actor, R.string.detail_actor, item.getActor());
        setText(mBinding.remark, 0, item.getRemarks());
    }

    private void setText(TextView view, int resId, String text) {
        if (TextUtils.isEmpty(text) && !TextUtils.isEmpty(view.getText())) return;
        view.setText(Sniffer.buildClickable(resId > 0 ? getString(resId, text) : text, this::clickableSpan), TextView.BufferType.SPANNABLE);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        view.setLinkTextColor(MDColor.YELLOW_500);
        CustomMovement.bind(view);
    }

    private ClickableSpan clickableSpan(Result result) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                VodActivity.start(getActivity(), getKey(), result);
                setRedirect(true);
            }
        };
    }

    private void getPlayer(Flag flag, Episode episode) {
        mBinding.widget.title.setText(getString(R.string.detail_title, mBinding.name.getText(), episode.getName()));
        playerStartTime = System.currentTimeMillis();
        beginPlayHealth();
        SpiderDebug.log("video-flow", "player start key=%s flag=%s episode=%s url=%s", getKey(), flag.getFlag(), episode.getName(), episode.getUrl());
        mViewModel.playerContent(getKey(), flag.getFlag(), episode.getUrl());
        mBinding.widget.title.setSelected(true);
        updateHistory(episode);
        showProgress();
    }

    private void setPlayer(Result result) {
        if (isFinishing() || isDestroyed()) return;
        SpiderDebug.log("video-flow", "player finish cost=%dms useParse=%s multi=%s msg=%s", System.currentTimeMillis() - playerStartTime, result.shouldUseParse(), result.getUrl().isMulti(), result.getMsg());
        if (service() == null) {
            mPendingPlayer = result;
            SpiderDebug.log("video-flow", "player pending service key=%s id=%s", getKey(), getId());
            return;
        }
        mQualityAdapter.addAll(result);
        setUseParse(result.shouldUseParse());
        setQualityVisible(result.getUrl().isMulti());
        result.getUrl().set(mQualityAdapter.getPosition());
        if (result.hasArtwork() && !shouldKeepPushArtwork()) setArtwork(result.getArtwork());
        if (result.hasDesc()) mBinding.content.setTag(result.getDesc());
        if (result.hasPosition()) mHistory.setPosition(result.getPosition());
        mBinding.control.parse.setVisibility(isUseParse() ? View.VISIBLE : View.GONE);
        List<Danmaku> siteDanmakus = result.getDanmaku();
        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata());
        if (DanmakuApi.canAutoSearch(siteDanmakus)) DanmakuApi.search(mHistory.getVodName(), getEpisode().getName(), player()::setDanmaku);
    }

    private void recordDetailHealth(Result result, long cost) {
        if (detailHealthRecorded) return;
        detailHealthRecorded = true;
        boolean success = result != null && !result.getList().isEmpty();
        String error = result == null ? "" : result.hasMsg() ? result.getMsg() : success ? "" : "empty";
        SiteHealthStore.recordDetail(getKey(), success, cost, error);
    }

    private void beginPlayHealth() {
        playHealthKey = getKey();
        playHealthRecorded = false;
    }

    private void recordPlayHealth(boolean success, String error) {
        if (playHealthRecorded) return;
        playHealthRecorded = true;
        SiteHealthStore.recordPlay(TextUtils.isEmpty(playHealthKey) ? getKey() : playHealthKey, success, error);
    }

    @Override
    public void onItemClick(Flag item) {
        if (mFlagAdapter.getItemCount() == 0 || item.isSelected()) return;
        mFlagAdapter.setSelected(item);
        mBinding.flag.setSelectedPosition(mFlagAdapter.indexOf(item));
        notifyItemChanged(mBinding.flag, mFlagAdapter);
        setEpisodeAdapter(item.getEpisodes());
        setQualityVisible(false);
        seamless(item);
    }

    private void setEpisodeAdapter(List<Episode> items) {
        setEpisodeAdapter(items, true);
    }

    private void setEpisodeAdapter(List<Episode> items, boolean scrollToCurrent) {
        mBinding.control.action.episodes.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        applyActionButtonVisibility();
        mBinding.episode.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        int column = EpisodeAdapter.getColumn(items);
        mBinding.episode.setNumColumns(column);
        mEpisodeAdapter.setColumn(column);
        mEpisodeAdapter.addAll(items);
        setArrayAdapter(items.size());
        updateFocus();
        updateEpisodeWindow();
        if (scrollToCurrent) scrollToCurrentEpisode();
        setR2Callback();
    }

    private void refreshEpisodeTitles() {
        if (mEpisodeAdapter == null || mFlagAdapter == null || mFlagAdapter.getItemCount() == 0) return;
        int position = mEpisodeAdapter.getSelectedPosition();
        setEpisodeAdapter(getFlag().getEpisodes(), false);
        if (position != RecyclerView.NO_POSITION) scrollToEpisode(position);
    }

    private void seamless(Flag flag) {
        Episode episode = flag.find(mHistory.getEpisode(), getMark().isEmpty());
        setQualityVisible(episode != null && episode.isSelected() && mQualityAdapter.getItemCount() > 1);
        if (episode == null || episode.isSelected()) return;
        selectEpisode(episode, false);
    }

    @Override
    public void onItemClick(Episode item) {
        if (shouldEnterFullscreen(item)) return;
        selectEpisode(item, true);
    }

    private void selectEpisode(Episode item, boolean scrollToEpisode) {
        int oldPosition = mEpisodeAdapter.getSelectedPosition();
        mFlagAdapter.toggle(item);
        int newPosition = mEpisodeAdapter.indexOf(item);
        if (newPosition == RecyclerView.NO_POSITION) newPosition = mEpisodeAdapter.getSelectedPosition();
        mEpisodeAdapter.notifySelectionChanged(oldPosition, newPosition);
        SpiderDebug.log("video-episode", "select old=%s new=%s focus=%s scroll=%s name=%s", oldPosition, newPosition, mBinding.episode.hasFocus(), scrollToEpisode, item.getName());
        if (scrollToEpisode && !mBinding.episode.hasFocus()) scrollToEpisode(newPosition);
        if (isFullscreen()) Notify.show(getString(R.string.play_ready, item.getName()));
        onRefresh();
    }

    private void setQualityVisible(boolean visible) {
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
        updateFocus();
        updateEpisodeWindow();
        setR2Callback();
    }

    @Override
    public void onItemClick(Result result) {
        beginPlayHealth();
        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata());
    }

    private void reverseEpisode(boolean scroll) {
        mFlagAdapter.reverse();
        setEpisodeAdapter(getFlag().getEpisodes(), scroll);
        if (scroll) scrollToCurrentEpisode();
        else scrollToFirstEpisode();
    }

    private void scrollToCurrentEpisode() {
        scrollToEpisode(mEpisodeAdapter.getPosition());
    }

    private void scrollToFirstEpisode() {
        scrollToEpisode(0, true);
    }

    private void scrollToEpisode(int position) {
        scrollToEpisode(position, false);
    }

    private void scrollToEpisode(int position, boolean requestFocus) {
        if (position < 0 || position >= mEpisodeAdapter.getItemCount()) return;
        mBinding.episode.post(() -> {
            updateEpisodeWindowNow();
            mBinding.episode.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                mBinding.episode.setSelectedPosition(position);
                if (requestFocus) mBinding.episode.requestFocus();
            });
        });
    }

    private void updateEpisodeWindow() {
        if (mEpisodeAdapter == null || mEpisodeAdapter.getItemCount() == 0) return;
        mBinding.episode.post(this::updateEpisodeWindowNow);
    }

    private void updateEpisodeWindowNow() {
        int height = getEpisodeWindowHeight();
        if (height <= 0) return;
        ViewGroup.LayoutParams params = mBinding.episode.getLayoutParams();
        if (params instanceof LinearLayoutCompat.LayoutParams layoutParams) {
            if (layoutParams.height == height && layoutParams.weight == 0) return;
            layoutParams.height = height;
            layoutParams.weight = 0;
            mBinding.episode.setLayoutParams(layoutParams);
        } else if (params.height != height) {
            params.height = height;
            mBinding.episode.setLayoutParams(params);
        }
    }

    private int getEpisodeWindowHeight() {
        int column = Math.max(1, mEpisodeAdapter.getColumn());
        int totalRows = Math.max(1, (mEpisodeAdapter.getItemCount() + column - 1) / column);
        int rowHeight = ResUtil.dp2px(40);
        int spacing = mBinding.episode.getVerticalSpacing();
        int maxRows = getEpisodeMaxRows(rowHeight, spacing);
        int rows = Math.min(totalRows, maxRows);
        return rowHeight * rows + spacing * Math.max(0, rows - 1) + mBinding.episode.getPaddingTop() + mBinding.episode.getPaddingBottom();
    }

    private int getEpisodeMaxRows(int rowHeight, int spacing) {
        int legacyRows = ResUtil.getScreenHeight() < ResUtil.dp2px(560) ? 2 : 3;
        int available = getEpisodeAvailableHeight();
        if (available <= 0) return legacyRows;
        int content = Math.max(0, available - mBinding.episode.getPaddingTop() - mBinding.episode.getPaddingBottom());
        int rows = (content + spacing) / (rowHeight + spacing);
        return Math.max(legacyRows, rows);
    }

    private int getEpisodeAvailableHeight() {
        int height = mBinding.scroll.getHeight();
        if (height <= 0) return 0;
        int available = height - mBinding.scroll.getPaddingTop() - mBinding.scroll.getPaddingBottom();
        ViewGroup.LayoutParams episodeParams = mBinding.episode.getLayoutParams();
        if (episodeParams instanceof ViewGroup.MarginLayoutParams margins) available -= margins.topMargin + margins.bottomMargin;
        for (int i = 0; i < mBinding.scroll.getChildCount(); i++) {
            View child = mBinding.scroll.getChildAt(i);
            if (child == mBinding.episode || child.getVisibility() == View.GONE) continue;
            available -= child.getMeasuredHeight();
            ViewGroup.LayoutParams params = child.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams margins) available -= margins.topMargin + margins.bottomMargin;
        }
        return available;
    }

    @Override
    public void onItemClick(Parse item) {
        setParse(item);
        onRefresh();
    }

    private void setParse(Parse item) {
        VodConfig.get().setParse(item);
        notifyItemChanged(mBinding.control.parse, mParseAdapter);
    }

    private void setArrayAdapter(int size) {
        int segment = getEpisodeSegmentSize(size);
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.play_reverse));
        items.add(getString(mHistory.getRevPlayText()));
        mBinding.array.setVisibility(size > 1 ? View.VISIBLE : View.GONE);
        if (mHistory.isRevSort()) for (int i = size; i > 0; i -= segment) items.add(i + "-" + Math.max(i - segment + 1, 1));
        else for (int i = 0; i < size; i += segment) items.add((i + 1) + "-" + Math.min(i + segment, size));
        mArrayAdapter.setSegmentSize(segment);
        mArrayAdapter.addAll(items);
        updateFocus();
    }

    private int getEpisodeSegmentSize(int size) {
        return size <= 60 ? 20 : 40;
    }

    private int findFocusDown(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.array, R.id.episode, R.id.part, R.id.quick);
        for (int i = 0; i < orders.size(); i++) if (i > index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private int findFocusUp(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.array, R.id.episode, R.id.part, R.id.quick);
        for (int i = orders.size() - 1; i >= 0; i--) if (i < index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private void updateFocus() {
        mArrayAdapter.setNextFocus(findFocusUp(2), findFocusDown(2));
        mEpisodeAdapter.setNextFocusUp(findFocusUp(3));
        mFlagAdapter.setNextFocusDown(findFocusDown(0));
        mEpisodeAdapter.setNextFocusDown(findFocusDown(3));
        mPartAdapter.setNextFocus(findFocusUp(4), findFocusDown(4));
        mQuickAdapter.setNextFocus(findFocusUp(5), findFocusDown(5));
        int searchDown = isVisible(mBinding.quick) ? R.id.quick : findFocusDown(-1);
        mBinding.search.setNextFocusDownId(searchDown == 0 ? View.NO_ID : searchDown);
    }

    private boolean onEpisodeKey(KeyEvent event) {
        if (!KeyUtil.isActionDown(event) || !KeyUtil.isUpKey(event)) return false;
        RecyclerView.ViewHolder holder = mBinding.episode.findContainingViewHolder(getCurrentFocus());
        if (holder == null) return false;
        int position = holder.getBindingAdapterPosition();
        int column = Math.max(1, mEpisodeAdapter.getColumn());
        if (position == RecyclerView.NO_POSITION || position >= column) return false;
        int target = findFocusUp(3);
        if (target == 0) return false;
        View view = findViewById(target);
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        view.requestFocus();
        return true;
    }

    @Override
    public void onRevSort() {
        mHistory.setRevSort(!mHistory.isRevSort());
        reverseEpisode(false);
    }

    @Override
    public void onRevPlay(TextView view) {
        mHistory.setRevPlay(!mHistory.isRevPlay());
        view.setText(mHistory.getRevPlayText());
        Notify.show(mHistory.getRevPlayHint());
    }

    private boolean shouldEnterFullscreen(Episode item) {
        boolean enter = !isFullscreen() && item.isSelected();
        if (enter) enterFullscreen();
        return enter;
    }

    private void enterFullscreen() {
        mFocus1 = getCurrentFocus();
        mBinding.video.requestFocus();
        mBinding.video.setForeground(null);
        mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        mBinding.flag.setSelectedPosition(mFlagAdapter.getPosition());
        mKeyDown.setFull(true);
        setFullscreen(true);
        mFocus2 = null;
    }

    private void exitFullscreen() {
        mBinding.video.setForeground(ResUtil.getDrawable(R.drawable.selector_video));
        mBinding.video.setLayoutParams(mFrameParams);
        getFocus1().requestFocus();
        mKeyDown.setFull(false);
        setFullscreen(false);
        mFocus2 = null;
        hideInfo();
    }

    private void onContent() {
        if (mBinding.content.getTag() == null) return;
        ContentDialog.create().content(mBinding.content.getTag().toString()).show(this);
    }

    private void onSearch() {
        String keyword = mBinding.name.getText().toString();
        if (TextUtils.isEmpty(keyword)) return;
        initSearch(keyword, false);
    }

    private void onShortDisplay() {
        Setting.putCompactEpisodeTitle(!Setting.isCompactEpisodeTitle());
        setShortDisplay();
        refreshEpisodeTitles();
    }

    private void setShortDisplay() {
        mBinding.shortDisplay.setSelected(Setting.isCompactEpisodeTitle());
    }

    private void onKeep() {
        Keep keep = Keep.find(getHistoryKey());
        Notify.show(keep != null ? R.string.keep_del : R.string.keep_add);
        if (keep != null) keep.delete();
        else createKeep();
        checkKeepImg();
    }

    private void onVideo() {
        if (!isFullscreen()) enterFullscreen();
    }

    private void onChange() {
        checkSearch(true);
    }

    private void onFullscreen() {
        boolean exit = isFullscreen();
        if (exit) exitFullscreen();
        else enterFullscreen();
        showControl(exit ? mBinding.control.action.fullscreen : mBinding.control.action.player);
    }

    private void onEpisodes() {
        if (mFlagAdapter.getItemCount() == 0 || mEpisodeAdapter.getItemCount() < 2) return;
        hideControl();
        EpisodeListDialog.create().flags(mFlagAdapter.getItems()).show(this);
    }

    private void onRepeat() {
        player().setRepeatOne(!player().isRepeatOne());
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
    }

    private void checkNext() {
        checkNext(true);
    }

    private void checkNext(boolean notify) {
        if (mHistory.isRevPlay()) onPrev(notify);
        else onNext(notify);
    }

    private void checkPrev() {
        if (mHistory.isRevPlay()) onNext(true);
        else onPrev(true);
    }

    private void onNext(boolean notify) {
        Episode item = mEpisodeAdapter.getNext();
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(mHistory.isRevPlay() ? R.string.error_play_prev : R.string.error_play_next);
    }

    private void onPrev(boolean notify) {
        Episode item = mEpisodeAdapter.getPrev();
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(mHistory.isRevPlay() ? R.string.error_play_next : R.string.error_play_prev);
    }

    private void onScale() {
        int index = getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        setScale(index == array.length - 1 ? 0 : ++index);
    }

    private void onLut() {
        mBinding.lutQuick.toggle(player(), mBinding.exo, this::onLutChanged, new com.fongmi.android.tv.ui.custom.LutQuickPanel.ImportCallback() {
            @Override
            public void onImportLut() {
                onLutImport();
            }

            @Override
            public void onSelectLutDir() {
                onLutDir();
            }
        });
        focusLutQuickIfVisible();
    }

    private void focusLutQuickIfVisible() {
        mBinding.lutQuick.post(this::focusLutQuickContent);
        mBinding.lutQuick.postDelayed(this::focusLutQuickContent, 220);
        mBinding.lutQuick.postDelayed(this::focusLutQuickContent, 420);
    }

    private boolean focusLutQuickContent() {
        if (!isVisible(mBinding.lutQuick)) return false;
        View focus = getCurrentFocus();
        RecyclerView recycler = findRecyclerView(mBinding.lutQuick);
        if (focus != null && isChildOf(mBinding.lutQuick, focus) && focus != recycler) return true;
        if (mBinding.lutQuick.focusSelectedEntry()) return true;
        if (focusRecyclerItem(recycler)) return true;
        return focusFirstChild(mBinding.lutQuick);
    }

    private RecyclerView findRecyclerView(View view) {
        if (view instanceof RecyclerView recycler) return recycler;
        if (!(view instanceof ViewGroup group)) return null;
        for (int i = 0; i < group.getChildCount(); i++) {
            RecyclerView recycler = findRecyclerView(group.getChildAt(i));
            if (recycler != null) return recycler;
        }
        return null;
    }

    private boolean focusRecyclerItem(RecyclerView recycler) {
        return focusRecyclerPosition(recycler, 0);
    }

    private boolean focusRecyclerPosition(RecyclerView recycler, int position) {
        if (recycler == null || recycler.getVisibility() != View.VISIBLE || !recycler.isEnabled()) return false;
        RecyclerView.Adapter<?> adapter = recycler.getAdapter();
        if (adapter == null || adapter.getItemCount() <= 0) return false;
        if (position < 0 || position >= adapter.getItemCount()) return false;
        recycler.scrollToPosition(position);
        RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
        if (holder != null && focusFirstChild(holder.itemView)) return true;
        for (int i = 0; i < recycler.getChildCount(); i++) {
            View child = recycler.getChildAt(i);
            if (recycler.getChildAdapterPosition(child) == position && focusFirstChild(child)) return true;
        }
        recycler.post(() -> {
            RecyclerView.ViewHolder next = recycler.findViewHolderForAdapterPosition(position);
            if (next != null) {
                focusFirstChild(next.itemView);
                return;
            }
            for (int i = 0; i < recycler.getChildCount(); i++) {
                View child = recycler.getChildAt(i);
                if (recycler.getChildAdapterPosition(child) == position) {
                    focusFirstChild(child);
                    return;
                }
            }
        });
        return true;
    }

    private boolean focusFirstChild(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isEnabled()) return false;
        if (view instanceof RecyclerView recycler) return focusRecyclerItem(recycler);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                if (focusFirstChild(group.getChildAt(i))) return true;
            }
        }
        if (view.isFocusable() && view.requestFocus()) return true;
        return false;
    }

    private boolean isChildOf(ViewGroup parent, View child) {
        for (View view = child; view != null; ) {
            if (view == parent) return true;
            if (!(view.getParent() instanceof View next)) return false;
            view = next;
        }
        return false;
    }

    private void onLutImport() {
        if (!LutStore.hasUserDir()) {
            pendingLutImport = true;
            chooseLutDir();
            return;
        }
        chooseLutFile();
    }

    private void onLutDir() {
        pendingLutImport = false;
        chooseLutDir();
    }

    private void chooseLutFile() {
        FileChooser.from(mLutFile).show("*/*", new String[]{"application/octet-stream", "text/*", "image/*", "*/*"});
    }

    private void chooseLutDir() {
        FileChooser.from(mLutDir).showDirectory();
    }

    private void onSpeed() {
        mBinding.control.action.speed.setText(player().addSpeed());
        saveDefaultSpeed();
        setR1Callback();
    }

    private void onSpeedAdd() {
        mBinding.control.action.speed.setText(player().addSpeed(0.25f));
        saveDefaultSpeed();
        setR1Callback();
    }

    private void onSpeedSub() {
        mBinding.control.action.speed.setText(player().subSpeed(0.25f));
        saveDefaultSpeed();
        setR1Callback();
    }

    private boolean onSpeedLong() {
        mBinding.control.action.speed.setText(player().toggleSpeed());
        saveDefaultSpeed();
        setR1Callback();
        return true;
    }

    private void saveDefaultSpeed() {
        PlayerSetting.putDefaultSpeed(player().getSpeed());
        mHistory.setSpeed(player().getSpeed());
    }

    private void onReset() {
        if (isReplay()) onReplay();
        else onRefresh();
    }

    private void onReplay() {
        mHistory.setPosition(C.TIME_UNSET);
        if (player().isEmpty()) onRefresh();
        else player().setMediaItem();
    }

    private void onRefresh() {
        saveHistory();
        player().stop();
        player().clear();
        mClock.setCallback(null);
        if (mFlagAdapter.getItemCount() == 0) return;
        if (mEpisodeAdapter.getItemCount() == 0) return;
        getPlayer(getFlag(), getEpisode());
    }

    private boolean onResetToggle() {
        Setting.putReset(Math.abs(Setting.getReset() - 1));
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        return true;
    }

    private void onOpening() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) setOpening(position);
    }

    private void onOpeningAdd() {
        setOpening(Math.max(0, Math.max(0, mHistory.getOpening()) + 1000));
    }

    private void onOpeningSub() {
        setOpening(Math.max(0, Math.max(0, mHistory.getOpening()) - 1000));
    }

    private boolean onOpeningReset() {
        setOpening(0);
        return true;
    }

    private void setOpening(long opening) {
        mHistory.setOpening(opening);
        mBinding.control.action.opening.setText(opening <= 0 ? getString(R.string.play_op) : Util.timeMs(mHistory.getOpening()));
    }

    private void onEnding() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetEnding(position, duration)) setEnding(duration - position);
    }

    private void onEndingAdd() {
        setEnding(Math.max(0, Math.max(0, mHistory.getEnding()) + 1000));
    }

    private void onEndingSub() {
        setEnding(Math.max(0, Math.max(0, mHistory.getEnding()) - 1000));
    }

    private boolean onEndingReset() {
        setEnding(0);
        return true;
    }

    private void setEnding(long ending) {
        mHistory.setEnding(ending);
        mBinding.control.action.ending.setText(ending <= 0 ? getString(R.string.play_ed) : Util.timeMs(mHistory.getEnding()));
    }

    private void onChoose() {
        PlayerHelper.choose(this, player().getUrl(), player().getHeaders(), player().isVod(), player().getPosition(), mBinding.widget.title.getText());
        setRedirect(true);
    }

    private boolean onChooseLong() {
        onChoose();
        return true;
    }

    private void onPlayerKernel() {
        mClock.setCallback(null);
        player().togglePlayer();
        setPlayerKernel();
        setDecode();
    }

    private void onDecode() {
        mClock.setCallback(null);
        player().toggleDecode();
        setDecode();
    }

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).show(this);
        hideControl();
    }

    private void onTitle() {
        TitleDialog.create().player(player()).show(this);
        hideControl();
    }

    private void onDanmaku() {
        DanmakuDialog.create().player(player()).show(this);
        hideControl();
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl(getFocus2());
    }

    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        App.post(mR3, 0);
        hideCenter();
        hideError();
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR3);
        Traffic.reset();
    }

    private void showError(String text) {
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void showInfo() {
        showTopInfo();
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.widget.duration.setText(player().getDurationTime());
        mBinding.widget.position.setText(player().getPositionTime(0));
    }

    private void showTopInfo() {
        mBinding.widget.top.setVisibility(View.VISIBLE);
        mBinding.widget.size.setText(player().getSizeText());
    }

    private void hideInfo() {
        mBinding.widget.top.setVisibility(View.GONE);
        mBinding.widget.center.setVisibility(View.GONE);
    }

    private void showControl(View view) {
        showTopInfo();
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        if (mOsd != null) mOsd.setControlsVisible(true);
        view.requestFocus();
        setR1Callback();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        if (mOsd != null) mOsd.setControlsVisible(false);
        if (player().isPlaying()) mBinding.widget.top.setVisibility(View.GONE);
        App.removeCallbacks(mR1);
    }

    private void hideCenter() {
        mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
        mBinding.widget.center.setVisibility(View.GONE);
        if (isGone(mBinding.control.getRoot())) mBinding.widget.top.setVisibility(View.GONE);
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.progress.traffic);
        App.post(mR3, 1000);
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR2Callback() {
        App.post(mR2, 500);
    }

    private void setArtwork(String url) {
        if (mHistory != null) mHistory.setVodPic(url);
        loadArtwork(url);
        setContextWall(getContextWall());
    }

    private void setArtwork() {
        if (mHistory == null) return;
        setArtwork(mHistory.getVodPic());
    }

    private void loadArtwork(String url) {
        ImgUtil.load(this, url, new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                mBinding.exo.setDefaultArtwork(resource);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                mBinding.exo.setDefaultArtwork(errorDrawable);
            }
        });
    }

    private String getContextWall() {
        if (!TextUtils.isEmpty(getWallPic())) return getWallPic();
        return mHistory == null ? "" : mHistory.getWallPic();
    }

    private String lockContextWall(String url) {
        String wall = Objects.toString(url, "");
        if (mContextWallLockedUrl == null && !TextUtils.isEmpty(wall)) mContextWallLockedUrl = wall;
        return mContextWallLockedUrl == null ? wall : mContextWallLockedUrl;
    }

    private void setContextWall(String url) {
        if (!Setting.isPlaybackArtworkWall()) {
            mContextWallUrl = "";
            hideContextWall();
            return;
        }
        String wall = lockContextWall(url);
        if (TextUtils.isEmpty(wall)) {
            mContextWallUrl = "";
            hideContextWall();
            return;
        }
        if (Objects.equals(mContextWallUrl, wall)) return;
        mContextWallUrl = wall;
        resetContextWallAlpha();
        if (isGone(mBinding.contextWall)) {
            mBinding.contextWall.setBackgroundColor(0xFF000000);
            mBinding.contextWall.setVisibility(View.VISIBLE);
        }
        ImgUtil.load(this, wall, new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                if (!Objects.equals(mContextWallUrl, wall)) return;
                resetContextWallAlpha();
                mBinding.contextWall.setBackgroundColor(0x00000000);
                mBinding.contextWall.setImageDrawable(resource);
                mBinding.contextWall.setVisibility(View.VISIBLE);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                if (!Objects.equals(mContextWallUrl, wall)) return;
                mContextWallUrl = "";
                hideContextWall();
            }
        });
    }

    private void resetContextWallAlpha() {
        mBinding.contextWall.animate().cancel();
        mBinding.contextWall.setAlpha(1f);
    }

    private void hideContextWall() {
        resetContextWallAlpha();
        mBinding.contextWall.setImageDrawable(null);
        mBinding.contextWall.setBackgroundColor(0x00000000);
        mBinding.contextWall.setVisibility(View.GONE);
    }

    private void setPartAdapter() {
        mPartAdapter.clear();
        mBinding.part.setVisibility(View.GONE);
        updateFocus();
    }

    private void checkFlag(Vod item) {
        boolean empty = item.getFlags().isEmpty();
        mBinding.flag.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            startFlow();
        } else {
            onItemClick(mHistory.getFlag());
            if (mHistory.isRevSort()) reverseEpisode(true);
        }
    }

    private void checkHistory(Vod item) {
        mHistory = History.find(getHistoryKey());
        mHistory = mHistory == null ? createHistory(item) : mHistory;
        if (!TextUtils.isEmpty(getWallPic())) mHistory.setWallPic(getWallPic());
        if (!TextUtils.isEmpty(getMark())) mHistory.setVodRemarks(getMark());
        if (Setting.isIncognito() && mHistory.getKey().equals(getHistoryKey())) mHistory.delete();
        mBinding.control.action.opening.setText(mHistory.getOpening() <= 0 ? getString(R.string.play_op) : Util.timeMs(mHistory.getOpening()));
        mBinding.control.action.ending.setText(mHistory.getEnding() <= 0 ? getString(R.string.play_ed) : Util.timeMs(mHistory.getEnding()));
        mBinding.control.action.speed.setText(player().setSpeed(PlayerSetting.getDefaultSpeed()));
        mHistory.setSpeed(player().getSpeed());
        mHistory.setVodName(item.getName());
        PlaybackEventCollector.get().updateHistory(mHistory);
        setArtwork(getInitialArtwork(item));
        setScale(getScale());
        setPartAdapter();
    }

    private boolean shouldKeepPushArtwork() {
        return SiteApi.PUSH.equals(getKey()) && !TextUtils.isEmpty(getPic());
    }

    private String getInitialArtwork(Vod item) {
        return shouldKeepPushArtwork() ? getPic() : item.getPic();
    }

    private void applySearchArtwork(Vod item) {
        String pic = getSearchArtworkPic();
        if (!TextUtils.isEmpty(pic)) item.setPic(pic);
    }

    private String getSearchArtworkPic() {
        if (!TextUtils.isEmpty(getPic())) return getPic();
        if (mHistory != null && !TextUtils.isEmpty(mHistory.getVodPic())) return mHistory.getVodPic();
        return "";
    }

    private boolean hasInitialPreview() {
        return !getName().isEmpty() || !getPic().isEmpty() || !getWallPic().isEmpty();
    }

    private void showInitialPreview() {
        mBinding.progressLayout.showContent();
        mBinding.name.setText(getName());
        if (!getContent().isEmpty()) mBinding.content.setTag(getContent());
        if (!getPic().isEmpty()) setArtwork(getPic());
        else if (!getWallPic().isEmpty()) setContextWall(getWallPic());
        mBinding.video.requestFocus();
    }

    private History createHistory(Vod item) {
        History history = new History();
        history.setKey(getHistoryKey());
        history.setCid(VodConfig.getCid());
        history.setVodName(item.getName());
        history.setVodPic(getInitialArtwork(item));
        history.setWallPic(getWallPic());
        history.findEpisode(item.getFlags());
        return history;
    }

    private void saveHistory() {
        saveHistory(false);
    }

    private void saveHistory(boolean exit) {
        if (mHistory == null || Setting.isIncognito()) return;
        if (service() != null && isOwner()) {
            updatePlaybackHistoryPosition();
            mHistory.setCreateTime(System.currentTimeMillis());
        }
        if (exit && service() != null) PlaybackEventCollector.get().onStop(player());
        if (!mHistory.canSave()) return;
        History history = mHistory.copy();
        Task.execute(() -> {
            if (history.getDuration() > 0) history.merge().save();
            else history.save();
            if (exit) RefreshEvent.history();
        });
    }

    private void syncHistory() {
        if (mHistory == null || Setting.isIncognito()) return;
        History history = mHistory.copy();
        Task.execute(history::save);
    }

    private void updateHistory(Episode item) {
        boolean sameEpisode = item.matchesName(mHistory.getEpisode());
        boolean sameFlag = TextUtils.equals(mHistory.getVodFlag(), getFlag().getFlag());
        if ((!sameEpisode || !sameFlag) && service() != null) {
            updatePlaybackHistoryPosition();
            PlaybackEventCollector.get().onStop(player());
        }
        mHistory.setPosition(sameEpisode ? mHistory.getPosition() : C.TIME_UNSET);
        if (!sameEpisode) mHistory.setDuration(C.TIME_UNSET);
        mHistory.setVodFlag(getFlag().getFlag());
        mHistory.setVodRemarks(item.getName());
        mHistory.setEpisodeUrl(item.getUrl());
        PlaybackEventCollector.get().updateHistory(mHistory);
    }

    private void checkKeepImg() {
        mBinding.keep.setCompoundDrawablesWithIntrinsicBounds(Keep.find(getHistoryKey()) == null ? R.drawable.ic_detail_keep_off : R.drawable.ic_detail_keep_on, 0, 0, 0);
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(VodConfig.getCid());
        keep.setVodPic(mHistory.getVodPic());
        keep.setVodName(mHistory.getVodName());
        keep.setSiteName(getSite().getName());
        keep.setCreateTime(System.currentTimeMillis());
        keep.save();
    }

    private void updateKeep() {
        Keep keep = Keep.find(getHistoryKey());
        if (keep != null) {
            keep.setVodName(mHistory.getVodName());
            keep.setVodPic(mHistory.getVodPic());
            keep.save();
        }
    }

    private void updateVod(Vod item) {
        boolean id = !item.getId().isEmpty();
        boolean pic = !item.getPic().isEmpty();
        boolean name = !item.getName().isEmpty();
        if (id) getIntent().putExtra("id", item.getId());
        if (id) mHistory.replace(getHistoryKey());
        if (name) mHistory.setVodName(item.getName());
        if (name) mBinding.name.setText(item.getName());
        if (name) mBinding.widget.title.setText(item.getName());
        updateFlag(getFlag(), item.getFlags());
        if (pic) setArtwork(item.getPic());
        if (pic || name) setMetadata();
        if (pic || name) syncHistory();
        if (pic || name) updateKeep();
        if (id) updateNavigationKey();
        if (name) setPartAdapter();
        PlaybackEventCollector.get().updateHistory(mHistory);
        setText(item);
    }

    private void updateFlag(Flag activated, List<Flag> items) {
        items.forEach(item -> mFlagAdapter.getItems().stream()
                .filter(item::equals).findFirst().ifPresentOrElse(target -> {
                    target.mergeEpisodes(item.getEpisodes(), mHistory.isRevSort());
                    if (target.equals(activated)) setEpisodeAdapter(target.getEpisodes());
                }, () -> mFlagAdapter.add(item)));
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {
        @Override
        public void onNext() {
            checkNext();
        }

        @Override
        public void onPrev() {
            checkPrev();
        }

        @Override
        public void onStop() {
            finishVideoPlayback();
        }

        @Override
        public void onReplay() {
            VideoActivity.this.onReplay();
        }
    };

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
    }

    @Override
    protected void onPrepare() {
        setDecode();
        setLut();
        setPosition();
    }

    @Override
    protected void onTracksChanged() {
        setTrackVisible();
        mClock.setCallback(this);
    }

    @Override
    protected void onTitlesChanged() {
        setTitleVisible();
    }

    @Override
    protected void onError(String msg) {
        recordPlayHealth(false, msg);
        Track.delete(player().getKey());
        mClock.setCallback(null);
        player().resetTrack();
        player().reset();
        player().stop();
        showError(msg);
        startFlow();
    }

    @Override
    protected void onReclaim() {
        Result result = mViewModel.getPlayer().getValue();
        if (result != null) setPlayer(result);
    }

    @Override
    protected void onStateChanged(int state) {
        switch (state) {
            case Player.STATE_BUFFERING:
                showProgress();
                break;
            case Player.STATE_READY:
                recordPlayHealth(true, "");
                hideProgress();
                player().reset();
                break;
            case Player.STATE_ENDED:
                checkEnded(true);
                break;
        }
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        if (isPlaying) {
            hideCenter();
        } else if (isPaused()) {
            if (isFullscreen()) showInfo();
            else hideInfo();
        }
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        applyResizeMode(getScale());
        mBinding.widget.size.setText(player().getSizeText());
    }

    @Override
    protected void onSurfaceAttached() {
        applyResizeMode(getScale());
    }

    @Override
    public void onSubtitleClick() {
        SubtitleDialog.create().view(mBinding.exo.getSubtitleView()).show(this);
        App.post(this::hideControl, 100);
    }

    @Override
    public void onTimeChanged(long time) {
        if (!isOwner()) return;
        long position, duration;
        mHistory.setCreateTime(time);
        updatePlaybackHistoryPosition();
        position = mHistory.getPosition();
        duration = mHistory.getDuration();
        PlaybackEventCollector.get().onProgress(mHistory, player());
        if (mHistory.canSave() && mHistory.canSync()) syncHistory();
        if (mHistory.getEnding() > 0 && duration > 0 && mHistory.getEnding() + position >= duration) {
            checkEnded(false);
        }
    }

    private void updatePlaybackHistoryPosition() {
        if (mHistory == null) return;
        long position = player().getPosition();
        long duration = player().getDuration();
        if (position > 0) mHistory.setPosition(position);
        if (duration > 0) mHistory.setDuration(duration);
        else if (mHistory.getDuration() < 0) mHistory.setDuration(0);
        PlaybackEventCollector.get().updateHistory(mHistory);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (isRedirect()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) getDetail();
        else if (event.getType() == RefreshEvent.Type.PLAYER) onRefresh();
        else if (event.getType() == RefreshEvent.Type.VOD) updateVod(event.getVod());
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) player().setSub(Sub.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.DANMAKU) player().reloadDanmaku(Danmaku.from(event.getPath()));
    }

    private void setPosition() {
        if (mHistory == null) return;
        if (mHistory.isNearEnding()) {
            SpiderDebug.log("video-flow", "reset near-end history position=%d duration=%d key=%s", mHistory.getPosition(), mHistory.getDuration(), getHistoryKey());
            mHistory.resetPlaybackPosition();
            syncHistory();
        }
        long position = Math.max(mHistory.getOpening(), mHistory.getPosition());
        if (position > 0) player().seekTo(position);
    }

    private void checkEnded(boolean notify) {
        checkNext(notify);
    }

    private void setTrackVisible() {
        mBinding.control.action.text.setVisibility(player().haveTrack(C.TRACK_TYPE_TEXT) || player().isVod() ? View.VISIBLE : View.GONE);
        mBinding.control.action.audio.setVisibility(player().haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        mBinding.control.action.video.setVisibility(player().haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
        applyActionButtonVisibility();
    }

    private void setTitleVisible() {
        mBinding.control.action.title.setVisibility(player().haveTitle() ? View.VISIBLE : View.GONE);
        applyActionButtonVisibility();
    }

    private MediaMetadata buildMetadata() {
        String title = mHistory.getVodName();
        String episode = getEpisode().getName();
        boolean empty = episode.isEmpty() || title.equals(episode);
        String artist = empty ? "" : episode;
        return PlayerManager.buildMetadata(title, artist, mHistory.getVodPic());
    }

    private void setMetadata() {
        player().setMetadata(buildMetadata());
    }

    private void startFlow() {
        if (!PlayerSetting.isAutoChange()) return;
        if (!getSite().isChangeable()) return;
        if (isUseParse()) checkParse();
        else checkFlag();
    }

    private void checkParse() {
        int position = mParseAdapter.getPosition();
        boolean last = position == mParseAdapter.getItemCount() - 1;
        boolean pass = position == 0 || last;
        if (last) initParse();
        if (pass) checkFlag();
        else nextParse(position);
    }

    private void initParse() {
        if (mParseAdapter.getItemCount() == 0) return;
        setParse(mParseAdapter.first());
    }

    private void checkFlag() {
        int position = isGone(mBinding.flag) ? -1 : mFlagAdapter.getPosition();
        if (position == mFlagAdapter.getItemCount() - 1) checkSearch(false);
        else nextFlag(position);
    }

    private void checkSearch(boolean force) {
        if (!force && !PlayerSetting.isAutoChange()) return;
        if (mQuickAdapter.getItemCount() == 0) initSearch(mBinding.name.getText().toString(), true);
        else if (isAutoMode() || force) nextSite();
    }

    private void initSearch(String keyword, boolean auto) {
        setAutoMode(auto);
        setInitAuto(auto);
        revealManualSearch = !auto;
        startSearch(keyword);
        mBinding.part.setTag(keyword);
    }

    private boolean isPass(Site item) {
        if (isAutoMode() && !item.isChangeable()) return false;
        return item.isSearchable();
    }

    private void startSearch(String keyword) {
        mQuickAdapter.clear();
        mBinding.quick.setVisibility(View.GONE);
        dismissQuickSearchDialog();
        quickSearchDialogClosed = false;
        if (!isInitAuto()) {
            revealManualSearch = false;
            showQuickSearchDialog(new ArrayList<>());
        }
        updateFocus();
        List<Site> sites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) if (isPass(site)) sites.add(site);
        SiteHealthStore.sortSites(sites);
        mViewModel.searchContent(sites, keyword, true);
    }

    private void setSearch(Result result) {
        List<Vod> items = result.getList();
        items.removeIf(this::mismatch);
        mQuickAdapter.addAll(items);
        mBinding.quick.setVisibility(View.GONE);
        updateFocus();
        if (!isInitAuto() && !items.isEmpty()) {
            showQuickSearchDialog(items);
        }
        if (isInitAuto() && PlayerSetting.isAutoChange()) nextSite();
        if (items.isEmpty()) return;
        App.removeCallbacks(mR4);
    }

    private void setSearchProgress(SearchProgress progress) {
        if (progress == null || isInitAuto()) return;
        showQuickSearchDialog(new ArrayList<>());
        if (mQuickSearchDialog != null) mQuickSearchDialog.setProgress(progress.current(), progress.total(), progress.finished());
    }

    private void showQuickSearchDialog(List<Vod> items) {
        if (quickSearchDialogClosed) return;
        if (mQuickSearchDialog != null) {
            mQuickSearchDialog.addAll(items);
            return;
        }
        QuickSearchDialog dialog = QuickSearchDialog.create().listener(this).items(items);
        dialog.dismissListener(d -> {
            if (mQuickSearchDialog != dialog) return;
            mQuickSearchDialog = null;
            quickSearchDialogClosed = true;
        });
        mQuickSearchDialog = dialog;
        dialog.show(this);
    }

    private void dismissQuickSearchDialog() {
        QuickSearchDialog dialog = mQuickSearchDialog;
        mQuickSearchDialog = null;
        if (dialog != null) dialog.dismissAllowingStateLoss();
    }

    @Override
    public void onItemClick(Vod item) {
        setAutoMode(false);
        applySearchArtwork(item);
        getDetail(item);
    }

    private boolean mismatch(Vod item) {
        if (getId().equals(item.getId())) return true;
        if (mBroken.contains(item.getId())) return true;
        String keyword = Objects.toString(mBinding.part.getTag(), "");
        if (isAutoMode()) return !item.getName().equals(keyword);
        else return !item.getName().contains(keyword);
    }

    private void nextParse(int position) {
        Parse parse = mParseAdapter.get(position + 1);
        Notify.show(getString(R.string.play_switch_parse, parse.getName()));
        onItemClick(parse);
    }

    private void nextFlag(int position) {
        Flag flag = mFlagAdapter.get(position + 1);
        Notify.show(getString(R.string.play_switch_flag, flag.getFlag()));
        onItemClick(flag);
    }

    private void nextSite() {
        if (mQuickAdapter.getItemCount() == 0) return;
        int position = mQuickAdapter.getBestPosition();
        Vod item = mQuickAdapter.get(position);
        Notify.show(getString(R.string.play_switch_site, item.getSiteName()));
        mQuickAdapter.remove(position);
        mBroken.add(getId());
        setInitAuto(false);
        applySearchArtwork(item);
        getDetail(item);
    }

    private void onPaused() {
        controller().pause();
    }

    private void onPlay() {
        if (mHistory != null && isEnded()) controller().seekTo(mHistory.getOpening());
        if (!player().isEmpty() && isIdle()) controller().prepare();
        controller().play();
    }

    private boolean onSeekBack() {
        controller().seekBack();
        return true;
    }

    private boolean onSeekForward() {
        controller().seekForward();
        return true;
    }

    private boolean isFullscreen() {
        return fullscreen;
    }

    private void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
        mBinding.control.action.fullscreen.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        mBinding.control.action.fullscreen.setText(R.string.play_fullscreen);
        applyActionButtonVisibility();
    }

    private boolean isInitAuto() {
        return initAuto;
    }

    private void setInitAuto(boolean initAuto) {
        this.initAuto = initAuto;
    }

    private boolean isAutoMode() {
        return autoMode;
    }

    private void setAutoMode(boolean autoMode) {
        this.autoMode = autoMode;
    }

    public boolean isUseParse() {
        return useParse;
    }

    public void setUseParse(boolean useParse) {
        this.useParse = useParse;
    }

    private View getFocus1() {
        return mFocus1 == null || mFocus1.getVisibility() != View.VISIBLE ? mBinding.video : mFocus1;
    }

    private View getFocus2() {
        return mFocus2 == null || mFocus2.getVisibility() != View.VISIBLE || mFocus2 == mBinding.control.action.opening || mFocus2 == mBinding.control.action.ending ? mBinding.control.action.next : mFocus2;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isActionUp(event) && KeyUtil.isBackKey(event) && mBinding.lutQuick.hideIfVisible()) return true;
        if (isVisible(mBinding.lutQuick)) return dispatchLutQuickKey(event);
        if (isFullscreen() && KeyUtil.isMenuKey(event)) onToggle();
        if (isVisible(mBinding.control.getRoot())) setR1Callback();
        if (isVisible(mBinding.control.getRoot())) mFocus2 = getCurrentFocus();
        if (onEpisodeKey(event)) return true;
        if (isFullscreen() && isGone(mBinding.control.getRoot()) && mKeyDown.hasEvent(event) && service() != null) return mKeyDown.onKeyDown(event);
        if (KeyUtil.isMediaFastForward(event)) return onSeekForward();
        if (KeyUtil.isMediaRewind(event)) return onSeekBack();
        return super.dispatchKeyEvent(event);
    }

    private boolean dispatchLutQuickKey(KeyEvent event) {
        if (KeyUtil.isEnterKey(event)) return dispatchLutQuickEnter(event);
        if (isLutQuickDirectionKey(event)) return dispatchLutQuickDirection(event);
        if (KeyUtil.isActionDown(event)) focusLutQuickContent();
        boolean handled = super.dispatchKeyEvent(event);
        if (KeyUtil.isActionDown(event)) {
            View focus = getCurrentFocus();
            if (focus == null || !isChildOf(mBinding.lutQuick, focus)) focusLutQuickContent();
        }
        return true;
    }

    private boolean isLutQuickDirectionKey(KeyEvent event) {
        return KeyUtil.isUpKey(event) || KeyUtil.isDownKey(event) || KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event);
    }

    private boolean dispatchLutQuickDirection(KeyEvent event) {
        if (!KeyUtil.isActionDown(event)) return true;
        RecyclerView recycler = findRecyclerView(mBinding.lutQuick);
        View focus = getCurrentFocus();
        if (recycler != null && (focus == recycler || isChildOf(recycler, focus)) && moveLutQuickRecycler(recycler, event)) return true;
        if (focus == null || !isChildOf(mBinding.lutQuick, focus) || focus == recycler) {
            focusLutQuickContent();
            focus = getCurrentFocus();
        }
        if (focus != null && isChildOf(mBinding.lutQuick, focus) && moveLutQuickFocus(focus, event)) return true;
        if (recycler != null && KeyUtil.isDownKey(event) && focusRecyclerItem(recycler)) return true;
        focusLutQuickContent();
        return true;
    }

    private boolean moveLutQuickRecycler(RecyclerView recycler, KeyEvent event) {
        if (!KeyUtil.isUpKey(event) && !KeyUtil.isDownKey(event)) return false;
        RecyclerView.Adapter<?> adapter = recycler.getAdapter();
        if (adapter == null || adapter.getItemCount() <= 0) return false;
        int current = getRecyclerFocusPosition(recycler);
        if (current == RecyclerView.NO_POSITION) return mBinding.lutQuick.focusSelectedEntry();
        int next = current + (KeyUtil.isDownKey(event) ? 1 : -1);
        if (next < 0 || next >= adapter.getItemCount()) return false;
        return focusRecyclerPosition(recycler, next);
    }

    private int getRecyclerFocusPosition(RecyclerView recycler) {
        View child = getRecyclerDirectChild(recycler, getCurrentFocus());
        return child == null ? RecyclerView.NO_POSITION : recycler.getChildAdapterPosition(child);
    }

    private View getRecyclerDirectChild(RecyclerView recycler, View focus) {
        for (View view = focus; view != null && view != recycler; ) {
            if (view.getParent() == recycler) return view;
            if (!(view.getParent() instanceof View next)) return null;
            view = next;
        }
        return null;
    }

    private boolean moveLutQuickFocus(View focus, KeyEvent event) {
        List<View> focusables = new ArrayList<>();
        collectLutQuickFocusables(mBinding.lutQuick, focusables);
        View target = findLutQuickFocusTarget(focus, focusables, event);
        return target != null && target.requestFocus();
    }

    private void collectLutQuickFocusables(View view, List<View> focusables) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isEnabled()) return;
        if (view instanceof RecyclerView recycler) {
            for (int i = 0; i < recycler.getChildCount(); i++) collectLutQuickFocusables(recycler.getChildAt(i), focusables);
            return;
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) collectLutQuickFocusables(group.getChildAt(i), focusables);
            return;
        }
        if (view.isFocusable()) focusables.add(view);
    }

    private View findLutQuickFocusTarget(View focus, List<View> focusables, KeyEvent event) {
        Rect current = new Rect();
        if (focus == null || !focus.getGlobalVisibleRect(current)) return null;
        View target = null;
        long bestScore = Long.MAX_VALUE;
        for (View item : focusables) {
            if (item == focus) continue;
            Rect candidate = new Rect();
            if (!item.getGlobalVisibleRect(candidate) || !isLutQuickFocusCandidate(current, candidate, event)) continue;
            long score = scoreLutQuickFocusCandidate(current, candidate, event);
            if (score < bestScore) {
                bestScore = score;
                target = item;
            }
        }
        return target;
    }

    private boolean isLutQuickFocusCandidate(Rect current, Rect candidate, KeyEvent event) {
        int dx = candidate.centerX() - current.centerX();
        int dy = candidate.centerY() - current.centerY();
        if (KeyUtil.isLeftKey(event)) return dx < 0 && isSameFocusRow(current, candidate);
        if (KeyUtil.isRightKey(event)) return dx > 0 && isSameFocusRow(current, candidate);
        if (KeyUtil.isUpKey(event)) return dy < 0;
        if (KeyUtil.isDownKey(event)) return dy > 0;
        return false;
    }

    private boolean isSameFocusRow(Rect current, Rect candidate) {
        return Math.abs(candidate.centerY() - current.centerY()) <= Math.max(current.height(), candidate.height());
    }

    private long scoreLutQuickFocusCandidate(Rect current, Rect candidate, KeyEvent event) {
        long dx = Math.abs(candidate.centerX() - current.centerX());
        long dy = Math.abs(candidate.centerY() - current.centerY());
        long primary = KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event) ? dx : dy;
        long secondary = KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event) ? dy : dx;
        return primary * 1000 + secondary;
    }

    private boolean dispatchLutQuickEnter(KeyEvent event) {
        if (KeyUtil.isActionDown(event)) {
            focusLutQuickContent();
            return true;
        }
        if (!KeyUtil.isActionUp(event)) return true;
        View focus = getCurrentFocus();
        if (focus == null || !isChildOf(mBinding.lutQuick, focus) || focus instanceof RecyclerView) {
            if (!focusLutQuickContent()) return true;
            focus = getCurrentFocus();
        }
        if (focus != null && isChildOf(mBinding.lutQuick, focus) && focus.isEnabled()) focus.performClick();
        return true;
    }

    @Override
    public void onSeeking(long time) {
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.widget.duration.setText(player().getDurationTime());
        mBinding.widget.position.setText(player().getPositionTime(time));
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        hideProgress();
    }

    @Override
    public void onSeekEnd(long time) {
        mKeyDown.reset();
        seekTo(time);
    }

    @Override
    public void onSpeedUp() {
        if (!player().isPlaying()) return;
        mBinding.widget.speed.setVisibility(View.VISIBLE);
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        mBinding.control.action.speed.setText(player().setSpeed(PlayerSetting.getSpeed()));
        saveDefaultSpeed();
    }

    @Override
    public void onSpeedEnd() {
        mBinding.widget.speed.clearAnimation();
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.control.action.speed.setText(player().getSpeedText());
        mHistory.setSpeed(player().getSpeed());
    }

    @Override
    public void onKeyUp() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) {
            showControl(mBinding.control.action.opening);
        } else if (player().canSetEnding(position, duration)) {
            showControl(mBinding.control.action.ending);
        } else {
            showControl(getFocus2());
        }
    }

    @Override
    public void onKeyDown() {
        showControl(getFocus2());
    }

    @Override
    public void onKeyCenter() {
        if (player().isPlaying()) onPaused();
        else if (player().isEmpty()) onRefresh();
        else onPlay();
        hideControl();
    }

    @Override
    public void onSingleTap() {
        if (isFullscreen()) onToggle();
    }

    @Override
    public void onDoubleTap() {
        if (isFullscreen()) onKeyCenter();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1001) PlayerHelper.onExternalResult(data, service()::dispatchNext, controller()::seekTo);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
        if (mOsd != null) {
            mOsd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics());
            mOsd.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mOsd != null) mOsd.stop();
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
    }

    @Override
    protected void onBackInvoked() {
        if (mBinding.lutQuick.hideIfVisible()) {
            return;
        } else if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.center)) {
            hideCenter();
        } else if (isFullscreen()) {
            exitFullscreen();
        } else {
            finishVideoPlayback();
        }
    }

    private void finishVideoPlayback() {
        if (isPlaybackExiting()) return;
        mViewModel.stopSearch();
        saveHistory(true);
        markPlaybackExiting();
        stopPlayback();
        if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        super.onBackInvoked();
    }

    @Override
    protected void onDestroy() {
        mClock.release();
        saveHistory(true);
        DanmakuApi.cancel();
        dismissQuickSearchDialog();
        RefreshEvent.keep();
        App.removeCallbacks(mR1, mR2, mR3, mR4);
        if (mOsd != null) mOsd.release();
        mViewModel.getResult().removeObserver(mObserveDetail);
        mViewModel.getPlayer().removeObserver(mObservePlayer);
        mViewModel.getSearch().removeObserver(mObserveSearch);
        mViewModel.getSearchProgress().removeObserver(mObserveSearchProgress);
        SiteHealthStore.flush();
        super.onDestroy();
    }
}
