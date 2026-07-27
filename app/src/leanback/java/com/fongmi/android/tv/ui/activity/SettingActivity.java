package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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
import com.fongmi.android.tv.databinding.ActivitySettingBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.LiveListener;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.AboutDialog;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
import com.fongmi.android.tv.ui.dialog.ConfigListDialog;
import com.fongmi.android.tv.ui.dialog.DohDialog;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.RestoreDialog;
import com.fongmi.android.tv.ui.dialog.BackupProgressDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.dialog.StatsDialog;
import com.fongmi.android.tv.ui.dialog.ThemeDialog;
import com.fongmi.android.tv.ui.dialog.TimerDialog;
import com.fongmi.android.tv.ui.dialog.SyncDialog;
import com.fongmi.android.tv.utils.AesEncryptUtil;
import com.fongmi.android.tv.utils.AppVersion;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.HistorySyncUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SecretConfigManager;
import com.fongmi.android.tv.utils.WebdavUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SettingActivity extends BaseActivity implements ConfigListener, SiteListener, LiveListener, DohDialog.Listener, ThemeDialog.Listener {

    private ActivitySettingBinding mBinding;
    private String[] size;
    private String[] language;
    private String[] uiScale;
    private Dialog webDavDialog;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingActivity.class));
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.vod.requestFocus();
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        setWallText();
        mBinding.versionText.setText(AppVersion.fullName());
        setCacheText();
        setOtherText();
    }

    private void setOtherText() {
        mBinding.themeColorText.setText(getThemeText());
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
        mBinding.incognitoText.setText(getSwitch(Setting.isIncognito()));
        mBinding.languageText.setText((language = ResUtil.getStringArray(R.array.select_language))[Setting.getLanguageIndex()]);
        mBinding.sizeText.setText((size = ResUtil.getStringArray(R.array.select_size))[PlayerSetting.getSize()]);
        mBinding.uiScaleText.setText((uiScale = ResUtil.getStringArray(R.array.select_ui_scale))[Setting.getUiScaleIndex()]);
    }

    private String getThemeText() {
        int color = Setting.getThemeColor();
        if (color == -1) return getString(R.string.setting_off);
        return getString(color == 0 ? R.string.setting_auto : R.string.setting_custom);
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
        mBinding.vod.setOnClickListener(this::onVod);
        mBinding.doh.setOnClickListener(this::setDoh);
        mBinding.live.setOnClickListener(this::onLive);
        mBinding.wall.setOnClickListener(this::onWall);
        mBinding.size.setOnClickListener(this::setSize);
        mBinding.language.setOnClickListener(this::setLanguage);
        mBinding.cache.setOnClickListener(this::onCache);
        mBinding.backup.setOnClickListener(this::onBackup);
        mBinding.enhance.setOnClickListener(this::onEnhance);
        mBinding.player.setOnClickListener(this::onPlayer);
        mBinding.danmaku.setOnClickListener(this::onDanmaku);
        mBinding.restore.setOnClickListener(this::onRestore);
        mBinding.version.setOnClickListener(this::onVersion);
        mBinding.vod.setOnLongClickListener(this::onVodEdit);
        mBinding.vodHome.setOnClickListener(this::onVodHome);
        mBinding.live.setOnLongClickListener(this::onLiveEdit);
        mBinding.liveHome.setOnClickListener(this::onLiveHome);
        mBinding.wall.setOnLongClickListener(this::onWallEdit);
        mBinding.incognito.setOnClickListener(this::setIncognito);
        mBinding.vodHistory.setOnClickListener(this::onVodHistory);
        mBinding.liveHistory.setOnClickListener(this::onLiveHistory);
        mBinding.wallDefault.setOnClickListener(this::setWallDefault);
        mBinding.wallRefresh.setOnClickListener(this::setWallRefresh);
        mBinding.wallRefresh.setOnLongClickListener(this::onWallHistory);
        mBinding.resetApp.setOnClickListener(this::onResetApp);
        mBinding.themeColor.setOnClickListener(this::onThemeColor);
        mBinding.uiScale.setOnClickListener(this::setUiScale);
        mBinding.version.setOnLongClickListener(v -> { showEncryptTool(); return true; });
        mBinding.historySyncUpload.setOnClickListener(this::onSyncUpload);
        mBinding.historySyncDownload.setOnClickListener(this::onSyncDownload);
    }

    @Override
    public void setConfig(Config config) {
        if (config == null) return;
        String url = config.getUrl();
        if (!TextUtils.isEmpty(url) && url.startsWith("file")) {
            PermissionUtil.requestFile(this, allGranted -> load(config));
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
                // ★ 防御性检查：Activity 销毁/结束时不再显示 ProgressDialog
                if (isFinishing() || isDestroyed()) return;
                Notify.progress(getActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                // ★ 防御性检查：避免 onSaveInstanceState 后操作 Fragment
                if (isFinishing() || isDestroyed()) return;
                setCacheText();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                // ★ 防御性检查：避免 onSaveInstanceState 后再提示
                if (isFinishing() || isDestroyed()) return;
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

    private void onVod(View view) {
        ConfigListDialog.create().type(0).listener(this).show(this);
    }

    private void onLive(View view) {
        ConfigListDialog.create().type(1).listener(this).show(this);
    }

    private void onWall(View view) {
        ConfigListDialog.create().type(2).listener(this).show(this);
    }

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

    private void onVodHome(View view) {
        SiteDialog.create().action().show(this);
    }

    private void onLiveHome(View view) {
        LiveDialog.create().action().show(this);
    }

    private void onVodHistory(View view) {
        HistoryDialog.create().vod().show(this);
    }

    private void onLiveHistory(View view) {
        HistoryDialog.create().live().show(this);
    }

    private void onPlayer(View view) {
        SettingPlayerActivity.start(this);
    }

    private void onEnhance(View view) {
        SettingEnhanceActivity.start(this);
    }

    private void onDanmaku(View view) {
        SettingDanmakuActivity.start(this);
    }

    private void onResetApp(View view) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.setting_reset_app)
                .setMessage(R.string.setting_reset_app_confirm)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> resetApp())
                .show();
    }
    private void resetApp() {
        try {
            getSharedPreferences(getPackageName() + "_preferences", 0).edit().clear().apply();
            File prefsDir = new File(getApplicationInfo().dataDir, "shared_prefs");
            deleteRecursive(prefsDir);
            File dbDir = new File(getApplicationInfo().dataDir, "databases");
            deleteRecursive(dbDir);
            File filesDir = getFilesDir();
            deleteRecursive(filesDir);
            File cacheDir = getCacheDir();
            deleteRecursive(cacheDir);
            File externalCacheDir = getExternalCacheDir();
            if (externalCacheDir != null) deleteRecursive(externalCacheDir);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
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
            if (children != null) for (File child : children) deleteRecursive(child);
        }
        file.delete();
    }
    private void onVersion(View view) {
        AboutDialog.show(this, () -> Updater.create().force().start(this));
    }

    @Override
    public void setTheme(int color) {
        Setting.putThemeColor(color);
        RefreshEvent.theme();
    }

    private void onThemeColor(View view) {
        ThemeDialog.show(this);
    }

    private void setUiScale(View view) {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.setting_ui_scale).setNegativeButton(R.string.dialog_negative, null)
                .setSingleChoiceItems(uiScale, Setting.getUiScaleIndex(), (dialog, which) -> {
                    mBinding.uiScaleText.setText(uiScale[which]);
                    Setting.putUiScaleIndex(which);
                    dialog.dismiss();
                    recreate();
                }).show();
    }

    private void onSyncUpload(View view) {
        HistorySyncUtil.upload(this, null);
    }

    private void onSyncDownload(View view) {
        HistorySyncUtil.download(this, null);
    }

    private void showEncryptTool() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_encrypt, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).setCancelable(true).create();
        EditText etName = view.findViewById(R.id.et_name);
        EditText etUrlPlain = view.findViewById(R.id.et_url_plain);
        EditText etKeyPlain = view.findViewById(R.id.et_key_plain);
        TextView tvJsonResult = view.findViewById(R.id.tv_json_result);
        Button btnGenerate = view.findViewById(R.id.btn_generate_json);
        Button btnCopy = view.findViewById(R.id.btn_copy_json);
        final String[] finalJson = {""};
        btnGenerate.setOnClickListener(v -> {
            String namePlain = etName.getText().toString().trim();
            String urlPlain = etUrlPlain.getText().toString().trim();
            String keyPlain = etKeyPlain.getText().toString().trim();
            if (namePlain.isEmpty() || urlPlain.isEmpty() || keyPlain.isEmpty()) {
                Toast.makeText(this, "名称、URL、关键词不能为空", Toast.LENGTH_SHORT).show(); return;
            }
            try {
                String encName = AesEncryptUtil.encrypt(namePlain);
                String encUrl = AesEncryptUtil.encrypt(urlPlain);
                String encKeyword = AesEncryptUtil.encrypt(keyPlain);
                JSONObject jsonItem = new JSONObject();
                jsonItem.put("enc_name", encName);
                jsonItem.put("enc_url", encUrl);
                jsonItem.put("enc_keyword", encKeyword);
                finalJson[0] = jsonItem.toString(4);
                tvJsonResult.setText(finalJson[0]);
            } catch (Exception e) { tvJsonResult.setText("加密失败：" + e.getMessage()); }
        });
        btnCopy.setOnClickListener(v -> {
            if (finalJson[0].isEmpty()) { Toast.makeText(this, "请先生成JSON", Toast.LENGTH_SHORT).show(); return; }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("私密配置", finalJson[0]));
            Toast.makeText(this, "JSON已复制到剪贴板", Toast.LENGTH_SHORT).show();
        });
        dialog.show();
    }

    // ==================== 私密配置生成工具 ====================

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
        new MaterialAlertDialogBuilder(this).setTitle(R.string.setting_size).setNegativeButton(R.string.dialog_negative, null)
                .setSingleChoiceItems(size, PlayerSetting.getSize(), (dialog, which) -> {
                    mBinding.sizeText.setText(size[which]);
                    PlayerSetting.putSize(which);
                    RefreshEvent.size();
                    dialog.dismiss();
                }).show();
    }

    private void setLanguage(View view) {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.setting_language).setNegativeButton(R.string.dialog_negative, null)
                .setSingleChoiceItems(language, Setting.getLanguageIndex(), (dialog, which) -> {
                    if (which != Setting.getLanguageIndex()) {
                        mBinding.languageText.setText(language[which]);
                        Setting.putLanguageIndex(which);
                        RefreshEvent.language();
                    }
                    dialog.dismiss();
                }).show();
    }

    private void setDoh(View view) {
        DohDialog.create().index(getDohIndex()).show(this);
    }

    @Override
    public void setDoh(Doh doh) {
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

    private void onBackup(View view) {
        showWebDavBackupDialog();
    }

    private void showWebDavBackupDialog() {
        webDavDialog = new Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_webdav_backup, null);
        webDavDialog.setContentView(dialogView);
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
        etServer.setText(Setting.getWebdavUrl());
        etUser.setText(Setting.getWebdavUser());
        etPass.setText(Setting.getWebdavPass());
        btnLocalBackup.setOnClickListener(v -> {
            PermissionUtil.requestFile(this, allGranted -> {
                AppDatabase.backup(new Callback() {
                    @Override public void success() { tvStatus.setText("本地备份成功"); Notify.show(R.string.backup_success); }
                    @Override public void error() { tvStatus.setText("本地备份失败"); Notify.show(R.string.backup_fail); }
                });
            });
        });
        btnLink.setOnClickListener(v -> { try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.webdav.com/register"))); } catch (Exception e) { tvStatus.setText("无法打开注册页面"); } });
        btnSave.setOnClickListener(v -> {
            String url = etServer.getText().toString().trim();
            String user = etUser.getText().toString().trim();
            String pass = etPass.getText().toString().trim();
            if (url.isEmpty()) { tvStatus.setText("请填写服务器地址"); return; }
            Setting.putWebdavUrl(url); Setting.putWebdavUser(user); Setting.putWebdavPass(pass);
            tvStatus.setText("配置已保存");
        });
        btnTest.setOnClickListener(v -> {
            String url = Setting.getWebdavUrl(); String user = Setting.getWebdavUser(); String pass = Setting.getWebdavPass();
            if (url.isEmpty()) { tvStatus.setText("请先保存配置"); return; }
            tvStatus.setText("正在测试...");
            new Thread(() -> { boolean ok = WebdavUtil.testConnection(url, user, pass);
                runOnUiThread(() -> tvStatus.setText(ok ? "连接成功" : "连接失败")); }).start();
        });
        btnUpload.setOnClickListener(v -> {
            String url = Setting.getWebdavUrl(); String user = Setting.getWebdavUser(); String pass = Setting.getWebdavPass();
            if (url.isEmpty()) { tvStatus.setText("请先保存配置"); return; }
            File localBackup = new File(Environment.getExternalStorageDirectory(), "TV");
            File[] files = localBackup.listFiles((d, name) -> name.endsWith(".bk.gz") || name.endsWith(".zip"));
            if (files == null || files.length == 0) { tvStatus.setText("请先执行本地备份"); return; }
            File latest = files[0];
            for (File f : files) if (f.lastModified() > latest.lastModified()) latest = f;
            File finalLatest = latest;
            tvStatus.setText("正在上传...");
            new Thread(() -> { boolean ok = WebdavUtil.uploadFile(url, user, pass, finalLatest);
                runOnUiThread(() -> tvStatus.setText(ok ? "上传成功" : "上传失败")); }).start();
        });
        btnDownload.setOnClickListener(v -> {
            String url = Setting.getWebdavUrl(); String user = Setting.getWebdavUser(); String pass = Setting.getWebdavPass();
            if (url.isEmpty()) { tvStatus.setText("请先保存配置"); return; }
            tvStatus.setText("正在获取文件列表...");
            new Thread(() -> {
                List<WebdavUtil.RemoteFile> files = WebdavUtil.listFiles(url, user, pass);
                runOnUiThread(() -> {
                    if (files == null || files.isEmpty()) { tvStatus.setText("远程无备份文件"); return; }
                    WebdavUtil.RemoteFile latest = null;
                    for (WebdavUtil.RemoteFile f : files) {
                        if ((f.path.endsWith(".bk.gz") || f.path.endsWith(".zip")) && (latest == null || f.modified > latest.modified)) latest = f;
                    }
                    if (latest == null) { tvStatus.setText("未找到备份文件"); return; }
                    final WebdavUtil.RemoteFile finalLatest = latest;
                    File tempFile = new File(getExternalCacheDir(), "restore_temp." + (latest.path.endsWith(".zip") ? "zip" : "gz"));
                    tvStatus.setText("正在下载...");
                    new Thread(() -> {
                        boolean downloaded = WebdavUtil.downloadFile(url, user, pass, finalLatest.path, tempFile);
                        runOnUiThread(() -> {
                            if (!downloaded) { tvStatus.setText("下载失败"); return; }
                            tvStatus.setText("正在恢复...");
                            AppDatabase.restore(tempFile, new Callback() {
                                @Override public void success() { tvStatus.setText("恢复成功"); Notify.show(R.string.restore_success); setOtherText(); initConfig(); tempFile.delete(); }
                                @Override public void error() { tvStatus.setText("恢复失败"); Notify.show(R.string.restore_fail); tempFile.delete(); }
                            });
                        });
                    }).start();
                });
            }).start();
        });
        btnList.setOnClickListener(v -> {
            String url = Setting.getWebdavUrl(); String user = Setting.getWebdavUser(); String pass = Setting.getWebdavPass();
            if (url.isEmpty()) { tvStatus.setText("请先保存配置"); return; }
            tvStatus.setText("正在获取列表...");
            new Thread(() -> {
                List<WebdavUtil.RemoteFile> files = WebdavUtil.listFiles(url, user, pass);
                runOnUiThread(() -> {
                    if (files == null || files.isEmpty()) tvStatus.setText("远程无备份文件");
                    else {
                        StringBuilder sb = new StringBuilder("文件列表:\n");
                        for (WebdavUtil.RemoteFile f : files) {
                            sb.append(f.path).append(" (").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(f.modified))).append(")\n");
                        }
                        tvStatus.setText(sb.toString());
                    }
                });
            }).start();
        });
        btnClose.setOnClickListener(v -> { if (webDavDialog != null) webDavDialog.dismiss(); });
        if (webDavDialog.getWindow() != null) {
            WindowManager.LayoutParams params = webDavDialog.getWindow().getAttributes();
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.98);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;
            webDavDialog.getWindow().setAttributes(params);
        }
        webDavDialog.show();
    }

    private void onRestore(View view) {
        PermissionUtil.requestFile(this, allGranted -> RestoreDialog.create().callback(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.restore_success);
                setOtherText();
            }

            @Override
            public void error() {
                Notify.show(R.string.restore_fail);
            }
        }).show(this));
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init().load();
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

}
