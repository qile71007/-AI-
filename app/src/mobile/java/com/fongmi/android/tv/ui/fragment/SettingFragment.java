package com.fongmi.android.tv.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.FragmentSettingBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.LiveListener;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.AboutDialog;
import com.fongmi.android.tv.ui.dialog.AiAssistantDialog;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
import com.fongmi.android.tv.ui.dialog.ConfigListDialog;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.RestoreDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.dialog.ThemeDialog;
import com.fongmi.android.tv.utils.AppVersion;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.WebdavUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SettingFragment extends BaseFragment implements ConfigListener, SiteListener, LiveListener, ThemeDialog.Listener {

    private FragmentSettingBinding mBinding;
    private String[] size;
    private String[] uiScale;
    private AlertDialog webDavDialog;

    public static SettingFragment newInstance() {
        return new SettingFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getChildFragmentManager().setFragmentResultListener("ai_config_result", this, (requestKey, result) -> {
            String url = result.getString("config_url");
            String name = result.getString("config_name");
            if (url != null && !url.isEmpty()) {
                Config config = Config.find(url, 0);
                if (config != null) {
                    if (name != null && !name.isEmpty()) config.setName(name);
                    setConfig(config);
                } else {
                    Notify.show("配置无效，请检查链接");
                }
            } else {
                Notify.show("未收到有效的配置链接");
            }
        });
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    private String getThemeText() {
        int color = Setting.getThemeColor();
        if (color == -1) return getString(R.string.setting_off);
        return getString(color == 0 ? R.string.setting_auto : R.string.setting_custom);
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    private HomeActivity getRoot() {
        return (HomeActivity) requireActivity();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        setWallText();
        mBinding.versionText.setText(AppVersion.fullName());
        setOtherText();
        setCacheText();
    }

    private void setOtherText() {
        mBinding.themeColorText.setText(getThemeText());
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
        mBinding.incognitoText.setText(getSwitch(Setting.isIncognito()));
        mBinding.sizeText.setText((size = ResUtil.getStringArray(R.array.select_size))[PlayerSetting.getSize()]);
        mBinding.uiScaleText.setText((uiScale = ResUtil.getStringArray(R.array.select_ui_scale))[Setting.getUiScaleIndex()]);
    }

    private void setCacheText() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                mBinding.cacheText.setText(result);
            }
        });
    }

    @Override
    protected void initEvent() {
        // 短按：打开配置列表
        mBinding.vod.setOnClickListener(this::onVod);
        mBinding.live.setOnClickListener(this::onLive);
        mBinding.wall.setOnClickListener(this::onWall);
        // 长按：打开编辑对话框
        mBinding.vod.setOnLongClickListener(this::onVodEdit);
        mBinding.live.setOnLongClickListener(this::onLiveEdit);
        mBinding.wall.setOnLongClickListener(this::onWallEdit);

        mBinding.doh.setOnClickListener(this::setDoh);
        mBinding.size.setOnClickListener(this::setSize);
        mBinding.uiScale.setOnClickListener(this::setUiScale);
        mBinding.cache.setOnClickListener(this::onCache);
        mBinding.backup.setOnClickListener(this::onBackup);
        mBinding.enhance.setOnClickListener(this::onEnhance);
        mBinding.player.setOnClickListener(this::onPlayer);
        mBinding.danmaku.setOnClickListener(this::onDanmaku);
        mBinding.restore.setOnClickListener(this::onRestore);
        mBinding.version.setOnClickListener(this::onVersion);
        mBinding.vodHome.setOnClickListener(this::onVodHome);
        mBinding.liveHome.setOnClickListener(this::onLiveHome);
        mBinding.incognito.setOnClickListener(this::setIncognito);
        mBinding.vodHistory.setOnClickListener(this::onVodHistory);
        mBinding.themeColor.setOnClickListener(this::onThemeColor);
        mBinding.liveHistory.setOnClickListener(this::onLiveHistory);
        mBinding.wallDefault.setOnClickListener(this::setWallDefault);
        mBinding.wallRefresh.setOnClickListener(this::setWallRefresh);
        mBinding.wallRefresh.setOnLongClickListener(this::onWallHistory);
        
        mBinding.resetApp.setOnClickListener(this::onResetApp);

        // AI 助手入口
        mBinding.aiAssistant.setOnClickListener(v -> {
            AiAssistantDialog dialog = new AiAssistantDialog();
            Bundle args = new Bundle();
            args.putInt("config_type", 0);
            dialog.setArguments(args);
            dialog.show(getChildFragmentManager(), "ai_assistant");
        });
    }

    // ==================== 短按：打开配置列表 ====================
    private void onVod(View view) {
        ConfigListDialog.create().type(0).listener(this).show(this);
    }

    private void onLive(View view) {
        ConfigListDialog.create().type(1).listener(this).show(this);
    }

    private void onWall(View view) {
        ConfigListDialog.create().type(2).listener(this).show(this);
    }

    // ==================== 长按：打开编辑对话框 ====================
    private boolean onVodEdit(View view) {
        ConfigDialog.create().vod().edit().show(this);
        return true;
    }

    private boolean onLiveEdit(View view) {
        ConfigDialog.create().live().edit().show(this);
        return true;
    }

    private boolean onWallEdit(View view) {
        ConfigDialog.create().wall().edit().show(this);
        return true;
    }

    // ==================== 其余方法 ====================
    @Override
    public void setConfig(Config config) {
        if (config.getUrl().startsWith("file")) {
            requireView().post(() -> PermissionUtil.requestFile(this, allGranted -> load(config)));
        } else {
            load(config);
        }
    }

    private void load(Config config) {
        switch (config.getType()) {
            case 0:
                VodConfig.load(config, getCallback());
                break;
            case 1:
                LiveConfig.load(config, getCallback());
                break;
            case 2:
                Setting.putWall(0);
                WallConfig.load(config, getCallback());
                break;
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(requireActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                setCacheText();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    @Override
    public void setTheme(int color) {
        Setting.putThemeColor(color);
        RefreshEvent.theme();
    }

    private void onVodHome(View view) {
        SiteDialog.create().search().change().show(this);
    }

    private void onLiveHome(View view) {
        LiveDialog.show(this);
    }

    private void onVodHistory(View view) {
        HistoryDialog.create().vod().show(this);
    }

    private void onLiveHistory(View view) {
        HistoryDialog.create().live().show(this);
    }

    private void onPlayer(View view) {
        getRoot().change(2);
    }

    private void onDanmaku(View view) {
        getRoot().change(4);
    }

    private void onEnhance(View view) {
        getRoot().change(3);
    }

    private void onThemeColor(View view) {
        ThemeDialog.show(this);
    }

    private void onVersion(View view) {
        AboutDialog.show(requireActivity(), () -> Updater.create().force().start(requireActivity()));
    }

    private void setWallDefault(View view) {
        Setting.putWall(Setting.nextDefaultWall());
        Setting.putWallType(0);
        setWallText();
        ConfigEvent.wall();
    }

    private void setWallRefresh(View view) {
        Setting.putWall(0);
        WallConfig.get().load(getCallback());
    }

    private boolean onWallHistory(View view) {
        HistoryDialog.create().wall().show(this);
        return true;
    }

    private void setIncognito(View view) {
        Setting.putIncognito(!Setting.isIncognito());
        mBinding.incognitoText.setText(getSwitch(Setting.isIncognito()));
    }

    private void setSize(View view) {
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_size).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(size, PlayerSetting.getSize(), (dialog, which) -> {
            mBinding.sizeText.setText(size[which]);
            PlayerSetting.putSize(which);
            RefreshEvent.size();
            dialog.dismiss();
        }).show();
    }

    private void setUiScale(View view) {
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_ui_scale).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(uiScale, Setting.getUiScaleIndex(), (dialog, which) -> {
            mBinding.uiScaleText.setText(uiScale[which]);
            Setting.putUiScaleIndex(which);
            dialog.dismiss();
            requireActivity().recreate();
        }).show();
    }

    private void setDoh(View view) {
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_doh).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(getDohList(), getDohIndex(), (dialog, which) -> {
            setDoh(VodConfig.get().getDoh().get(which));
            dialog.dismiss();
        }).show();
    }

    private void setDoh(Doh doh) {
        OkHttp.dns().setDoh(doh);
        Setting.putDoh(doh.toString());
        mBinding.dohText.setText(doh.getName());
    }

    private void onCache(View view) {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                setCacheText();
            }
        });
    }

    // ==================== WebDAV 备份 ====================
    private void onBackup(View view) {
        showWebDavBackupDialog();
    }

    private void showWebDavBackupDialog() {
        if (getContext() == null) return;
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_webdav_backup, null);

        // 获取控件（ID 与布局完全匹配）
        EditText etServer = dialogView.findViewById(R.id.dialogWebdavUrl);
        EditText etUser = dialogView.findViewById(R.id.dialogWebdavUser);
        EditText etPass = dialogView.findViewById(R.id.dialogWebdavPass);
        Button btnLocalBackup = dialogView.findViewById(R.id.btnLocalBackup);
        Button btnLink = dialogView.findViewById(R.id.dialogWebdavLink);
        Button btnSave = dialogView.findViewById(R.id.dialogWebdavSave);
        Button btnTest = dialogView.findViewById(R.id.dialogWebdavTest);
        Button btnUpload = dialogView.findViewById(R.id.dialogWebdavUpload);
        Button btnList = dialogView.findViewById(R.id.dialogWebdavList);
        Button btnDownload = dialogView.findViewById(R.id.dialogWebdavDownload);
        TextView tvStatus = dialogView.findViewById(R.id.dialogWebdavStatus);
        Button btnClose = dialogView.findViewById(R.id.dialogClose);

        // 加载已保存的配置
        etServer.setText(Setting.getWebdavUrl());
        etUser.setText(Setting.getWebdavUser());
        etPass.setText(Setting.getWebdavPass());

        // 使用 MaterialAlertDialogBuilder 无参构造
        webDavDialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

        // ---- 本地备份 ----
        btnLocalBackup.setOnClickListener(v -> {
            PermissionUtil.requestFile(this, allGranted -> {
                AppDatabase.backup(new Callback() {
                    @Override
                    public void success() {
                        tvStatus.setText("本地备份成功");
                        Notify.show(R.string.backup_success);
                    }
                    @Override
                    public void error() {
                        tvStatus.setText("本地备份失败");
                        Notify.show(R.string.backup_fail);
                    }
                });
            });
        });

        // ---- 注册开通（打开默认浏览器） ----
        btnLink.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.webdav.com/register"));
                startActivity(intent);
            } catch (Exception e) {
                tvStatus.setText("无法打开注册页面");
            }
        });

        // ---- 保存配置 ----
        btnSave.setOnClickListener(v -> {
            String url = etServer.getText().toString().trim();
            String user = etUser.getText().toString().trim();
            String pass = etPass.getText().toString().trim();
            if (url.isEmpty()) {
                tvStatus.setText("请填写服务器地址");
                return;
            }
            Setting.putWebdavUrl(url);
            Setting.putWebdavUser(user);
            Setting.putWebdavPass(pass);
            tvStatus.setText("配置已保存");
        });

        // ---- 测试连接 ----
        btnTest.setOnClickListener(v -> {
            String url = Setting.getWebdavUrl();
            String user = Setting.getWebdavUser();
            String pass = Setting.getWebdavPass();
            if (url.isEmpty()) {
                tvStatus.setText("请先保存配置");
                return;
            }
            tvStatus.setText("正在测试...");
            new Thread(() -> {
                boolean ok = WebdavUtil.testConnection(url, user, pass);
                requireActivity().runOnUiThread(() -> {
                    tvStatus.setText(ok ? "连接成功" : "连接失败，请检查配置");
                });
            }).start();
        });

        // ---- 上传备份 ----
        btnUpload.setOnClickListener(v -> {
            String url = Setting.getWebdavUrl();
            String user = Setting.getWebdavUser();
            String pass = Setting.getWebdavPass();
            if (url.isEmpty()) {
                tvStatus.setText("请先保存配置");
                return;
            }
            File localBackup = getLatestBackupFile();
            if (localBackup == null || !localBackup.exists()) {
                tvStatus.setText("请先执行本地备份（点击“本地备份”按钮）");
                return;
            }
            tvStatus.setText("正在上传 " + localBackup.getName() + " ...");
            new Thread(() -> {
                boolean ok = WebdavUtil.uploadFile(url, user, pass, localBackup);
                requireActivity().runOnUiThread(() -> {
                    tvStatus.setText(ok ? "上传成功" : "上传失败");
                });
            }).start();
        });

        // ---- 下载备份 ----
        btnDownload.setOnClickListener(v -> {
            String url = Setting.getWebdavUrl();
            String user = Setting.getWebdavUser();
            String pass = Setting.getWebdavPass();
            if (url.isEmpty()) {
                tvStatus.setText("请先保存配置");
                return;
            }
            tvStatus.setText("正在获取文件列表...");
            new Thread(() -> {
                List<WebdavUtil.RemoteFile> files = WebdavUtil.listFiles(url, user, pass);
                if (files == null || files.isEmpty()) {
                    requireActivity().runOnUiThread(() -> tvStatus.setText("远程无备份文件"));
                    return;
                }
                WebdavUtil.RemoteFile latest = null;
                for (WebdavUtil.RemoteFile f : files) {
                    if (f.path.endsWith(".bk.gz") || f.path.endsWith(".zip")) {
                        if (latest == null || f.modified > latest.modified) {
                            latest = f;
                        }
                    }
                }
                if (latest == null) {
                    requireActivity().runOnUiThread(() -> tvStatus.setText("未找到备份文件"));
                    return;
                }
                File tempFile = new File(requireContext().getExternalCacheDir(), "restore_temp." + (latest.path.endsWith(".zip") ? "zip" : "gz"));
                requireActivity().runOnUiThread(() -> tvStatus.setText("正在下载..."));
                boolean downloaded = WebdavUtil.downloadFile(url, user, pass, latest.path, tempFile);
                if (!downloaded) {
                    requireActivity().runOnUiThread(() -> tvStatus.setText("下载失败"));
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    tvStatus.setText("正在恢复...");
                    AppDatabase.restore(tempFile, new Callback() {
                        @Override
                        public void success() {
                            tvStatus.setText("恢复成功");
                            Notify.show(R.string.restore_success);
                            setOtherText();
                            initConfig();
                            tempFile.delete();
                        }
                        @Override
                        public void error() {
                            tvStatus.setText("恢复失败");
                            Notify.show(R.string.restore_fail);
                            tempFile.delete();
                        }
                    });
                });
            }).start();
        });

        // ---- 查看文件列表 ----
        btnList.setOnClickListener(v -> {
            String url = Setting.getWebdavUrl();
            String user = Setting.getWebdavUser();
            String pass = Setting.getWebdavPass();
            if (url.isEmpty()) {
                tvStatus.setText("请先保存配置");
                return;
            }
            tvStatus.setText("正在获取列表...");
            new Thread(() -> {
                List<WebdavUtil.RemoteFile> files = WebdavUtil.listFiles(url, user, pass);
                requireActivity().runOnUiThread(() -> {
                    if (files == null || files.isEmpty()) {
                        tvStatus.setText("远程无备份文件");
                    } else {
                        StringBuilder sb = new StringBuilder("文件列表:\n");
                        for (WebdavUtil.RemoteFile f : files) {
                            sb.append(f.path).append(" (")
                                    .append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                            .format(new java.util.Date(f.modified)))
                                    .append(")\n");
                        }
                        tvStatus.setText(sb.toString());
                    }
                });
            }).start();
        });

        // ---- 关闭 ----
        btnClose.setOnClickListener(v -> {
            if (webDavDialog != null) webDavDialog.dismiss();
        });

        webDavDialog.show();

        // ★★★★★ 扩大宽度和高度：设置窗口为 MATCH_PARENT 和 WRAP_CONTENT ★★★★★
        if (webDavDialog.getWindow() != null) {
            WindowManager.LayoutParams params = webDavDialog.getWindow().getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;
            webDavDialog.getWindow().setAttributes(params);
            // 清除系统默认的对话框边距，避免内容被挤压
            webDavDialog.getWindow().getDecorView().setPadding(0, 0, 0, 0);
        }
    }

    // 获取外部存储目录中最新的备份文件（.bk.gz 或 .zip）
    private File getLatestBackupFile() {
        File dir = requireContext().getExternalFilesDir(null);
        if (dir == null) return null;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".bk.gz") || name.endsWith(".zip"));
        if (files == null || files.length == 0) return null;
        File latest = files[0];
        for (File f : files) {
            if (f.lastModified() > latest.lastModified()) latest = f;
        }
        return latest;
    }

    private void onRestore(View view) {
        PermissionUtil.requestFile(this, allGranted -> RestoreDialog.create().show(requireActivity(), new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.restore_success);
                setOtherText();
                initConfig();
            }

            @Override
            public void error() {
                Notify.show(R.string.restore_fail);
            }
        }));
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init().load();
    }

    private void onResetApp(View view) {
        new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.setting_reset_app)
                .setMessage(R.string.setting_reset_app_confirm)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> resetApp())
                .show();
    }

    private void resetApp() {
        try {
            requireActivity().getSharedPreferences(requireActivity().getPackageName() + "_preferences", 0).edit().clear().apply();
            File prefsDir = new File(requireActivity().getApplicationInfo().dataDir, "shared_prefs");
            deleteRecursive(prefsDir);
            File dbDir = new File(requireActivity().getApplicationInfo().dataDir, "databases");
            deleteRecursive(dbDir);
            File filesDir = requireActivity().getFilesDir();
            deleteRecursive(filesDir);
            File cacheDir = requireActivity().getCacheDir();
            deleteRecursive(cacheDir);
            File externalCacheDir = requireActivity().getExternalCacheDir();
            if (externalCacheDir != null) deleteRecursive(externalCacheDir);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Intent intent = requireActivity().getPackageManager().getLaunchIntentForPackage(requireActivity().getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
        System.exit(0);
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        file.delete();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() == ConfigEvent.Type.WALL) {
            setWallText();
            return;
        }
        if (event.type() != ConfigEvent.Type.COMMON) return;
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        setWallText();
    }

    private void setWallText() {
        mBinding.wallUrl.setText(Setting.getWallDesc(WallConfig.getDesc()));
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) return;
        setCacheText();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getChildFragmentManager().clearFragmentResultListener("ai_config_result");
        if (webDavDialog != null && webDavDialog.isShowing()) {
            webDavDialog.dismiss();
        }
    }
}
