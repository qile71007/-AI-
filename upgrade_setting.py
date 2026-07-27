#!/usr/bin/env python3
"""升级 SettingActivity 加入全部移动版功能"""
import os

LEANBACK = '/home/tv_project/app/src/leanback'

# 读取当前 SettingActivity
with open(f'{LEANBACK}/java/com/fongmi/android/tv/ui/activity/SettingActivity.java') as f:
    code = f.read()

# 添加缺失的导入
new_imports = '''import android.app.Dialog;
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
'''

# 替换旧导入
old_imports_end = '''import com.fongmi.android.tv.utils.AesEncryptUtil;
import com.fongmi.android.tv.utils.SecretConfigManager;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.RestoreDialog;
import com.fongmi.android.tv.ui.dialog.BackupProgressDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.utils.AppVersion;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

import java.util.ArrayList;
import java.io.File;
import java.util.List;'''

code = code.replace(old_imports_end, new_imports.strip())

# 添加接口实现
code = code.replace('implements ConfigListener, SiteListener, LiveListener, DohDialog.Listener',
                     'implements ConfigListener, SiteListener, LiveListener, DohDialog.Listener, ThemeDialog.Listener')

# 添加字段
code = code.replace('private String[] size;\n    private String[] language;',
                     'private String[] size;\n    private String[] language;\n    private String[] uiScale;\n    private Dialog webDavDialog;')

# 更新 setOtherText
old_other = '''    private void setOtherText() {
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
        mBinding.incognitoText.setText(getSwitch(Setting.isIncognito()));
        mBinding.languageText.setText((language = ResUtil.getStringArray(R.array.select_language))[Setting.getLanguageIndex()]);
        mBinding.sizeText.setText((size = ResUtil.getStringArray(R.array.select_size))[PlayerSetting.getSize()]);
    }'''

new_other = '''    private void setOtherText() {
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
    }'''

code = code.replace(old_other, new_other)

# 在 initEvent 中添加缺失的点击事件
old_events = '''        mBinding.resetApp.setOnClickListener(this::onResetApp);'''

new_events = '''        mBinding.resetApp.setOnClickListener(this::onResetApp);
        mBinding.themeColor.setOnClickListener(this::onThemeColor);
        mBinding.uiScale.setOnClickListener(this::setUiScale);
        mBinding.version.setOnLongClickListener(v -> { showEncryptTool(); return true; });
        mBinding.historySyncUpload.setOnClickListener(this::onSyncUpload);
        mBinding.historySyncDownload.setOnClickListener(this::onSyncDownload);'''

code = code.replace(old_events, new_events)

# 添加缺失的方法
# 在 onVersion 方法之前添加
old_on_version = '''    private void onVersion(View view) {
        AboutDialog.show(this, () -> Updater.create().force().start(this));
    }

    // ==================== 私密配置生成工具 ===================='''

new_methods = '''    private void onVersion(View view) {
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

    // ==================== 私密配置生成工具 ===================='''

code = code.replace(old_on_version, new_methods)

# 替换 setLanguage 使用单选框
old_lang = '''    private void setLanguage(View view) {
        int index = (Setting.getLanguageIndex() + 1) % language.length;
        Setting.putLanguageIndex(index);
        RefreshEvent.language();
    }'''

new_lang = '''    private void setLanguage(View view) {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.setting_language).setNegativeButton(R.string.dialog_negative, null)
                .setSingleChoiceItems(language, Setting.getLanguageIndex(), (dialog, which) -> {
                    if (which != Setting.getLanguageIndex()) {
                        mBinding.languageText.setText(language[which]);
                        Setting.putLanguageIndex(which);
                        RefreshEvent.language();
                    }
                    dialog.dismiss();
                }).show();
    }'''

code = code.replace(old_lang, new_lang)

# 替换 setSize 使用单选框
old_size = '''    private void setSize(View view) {
        int index = (PlayerSetting.getSize() + 1) % size.length;
        mBinding.sizeText.setText(size[index]);
        PlayerSetting.putSize(index);
        RefreshEvent.size();
    }'''

new_size = '''    private void setSize(View view) {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.setting_size).setNegativeButton(R.string.dialog_negative, null)
                .setSingleChoiceItems(size, PlayerSetting.getSize(), (dialog, which) -> {
                    mBinding.sizeText.setText(size[which]);
                    PlayerSetting.putSize(which);
                    RefreshEvent.size();
                    dialog.dismiss();
                }).show();
    }'''

code = code.replace(old_size, new_size)

# 替换 onBackup 使用 WebDAV 对话框
old_backup = '''    private void onBackup(View view) {
        PermissionUtil.requestFile(this, allGranted -> {
            BackupProgressDialog progress = BackupProgressDialog.open(getSupportFragmentManager(), "备份应用数据");
            AppDatabase.backup(new Callback() {
            @Override
            public void success() {
                progress.finish();
                Notify.show(R.string.backup_success);
            }

            @Override
            public void error() {
                progress.finish();
                Notify.show(R.string.backup_fail);
            }
            }, progress::update);
        });
    }'''

new_backup = '''    private void onBackup(View view) {
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
                    File tempFile = new File(getExternalCacheDir(), "restore_temp." + (latest.path.endsWith(".zip") ? "zip" : "gz"));
                    tvStatus.setText("正在下载...");
                    new Thread(() -> {
                        boolean downloaded = WebdavUtil.downloadFile(url, user, pass, latest.path, tempFile);
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
                        StringBuilder sb = new StringBuilder("文件列表:\\n");
                        for (WebdavUtil.RemoteFile f : files) {
                            sb.append(f.path).append(" (").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(f.modified))).append(")\\n");
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
    }'''

code = code.replace(old_backup, new_backup)

# 添加 initConfig 方法
old_restore = '''    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init().load();
    }'''

if old_restore in code:
    pass  # 已存在
else:
    # 在 onRestore 后添加
    code = code.replace('    private void onRestore', '    private void initConfig() {\n        VodConfig.get().init().load(getCallback());\n        LiveConfig.get().init().load();\n        WallConfig.get().init().load();\n    }\n\n    private void onRestore')

# 写入文件
with open(f'{LEANBACK}/java/com/fongmi/android/tv/ui/activity/SettingActivity.java', 'w') as f:
    f.write(code)

print('SettingActivity upgraded!')