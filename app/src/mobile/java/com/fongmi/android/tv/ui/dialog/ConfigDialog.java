package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.databinding.DialogConfigBinding;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;

public class ConfigDialog extends BaseAlertDialog {

    private DialogConfigBinding binding;
    private boolean append = true;
    private boolean edit;
    private String ori;
    private int type;
    private CheckBox cbModify; // 修改当前配置复选框

    public static ConfigDialog create() {
        return new ConfigDialog();
    }

    public ConfigDialog vod() {
        type = 0;
        return this;
    }

    public ConfigDialog live() {
        type = 1;
        return this;
    }

    public ConfigDialog wall() {
        type = 2;
        return this;
    }

    public ConfigDialog edit() {
        edit = true;
        return this;
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogConfigBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        Config config = getConfig();
        binding.title.setText(getDialogTitle());
        binding.positive.setText(edit ? R.string.dialog_edit : R.string.dialog_positive);
        binding.name.setText(config.getName());
        binding.url.setText(ori = config.getUrl());
        binding.url.setSelection(TextUtils.isEmpty(ori) ? 0 : ori.length());

        // 仅点播类型显示“修改当前配置”复选框
        if (type == 0) {
            cbModify = new CheckBox(requireContext());
            cbModify.setText("修改当前配置");
            cbModify.setChecked(false);
            ViewGroup root = (ViewGroup) binding.getRoot();
            root.addView(cbModify, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    @Override
    protected void initEvent() {
        binding.negative.setOnClickListener(v -> dismiss());
        binding.positive.setOnClickListener(v -> onPositive());
        binding.choose.setEndIconOnClickListener(this::onChoose);
        binding.url.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());
            }
        });
        binding.url.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive();
            return true;
        });
        binding.name.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive();
            return true;
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        configureWindow();
        binding.url.requestFocus();
    }

    private Config getConfig() {
        return switch (type) {
            case 0 -> VodConfig.get().getConfig();
            case 1 -> LiveConfig.get().getConfig();
            case 2 -> WallConfig.get().getConfig();
            default -> null;
        };
    }

    private Config getStoredConfig() {
        return switch (type) {
            case 0 -> Config.vod();
            case 1 -> Config.live();
            case 2 -> Config.wall();
            default -> Config.create(type);
        };
    }

    private int getTypeName() {
        return switch (type) {
            case 0 -> R.string.setting_vod;
            case 1 -> R.string.setting_live;
            case 2 -> R.string.setting_wall;
            default -> R.string.remote_trust_config_type;
        };
    }

    private String getDialogTitle() {
        int action = edit ? R.string.remote_trust_config_edit : R.string.remote_trust_config_add;
        return getString(R.string.setting_config_dialog_title, getString(action), getString(getTypeName()));
    }

    private void onChoose(View view) {
        FileChooser.from(launcher).show();
    }

    private void detect(String s) {
        if (append && "h".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ttp://");
        } else if (append && "f".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ile://");
        } else if (append && "a".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ssets://");
        } else if (s.length() > 1) {
            append = false;
        } else if (s.isEmpty()) {
            append = true;
        }
    }

    // ★★★ 核心修改：onPositive 方法 ★★★
    private void onPositive() {
        String url = binding.url.getText().toString().trim();
        String name = binding.name.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            Notify.show("请输入配置链接");
            binding.url.requestFocus();
            return;
        }

        // 判断是否要修改当前配置（仅点播有效）
        boolean modifyCurrent = type == 0 && cbModify != null && cbModify.isChecked();

        Config config;
        if (modifyCurrent) {
            // 修改当前配置：使用当前配置的 id 进行更新
            Config current = getConfig();
            if (current == null || current.isEmpty()) {
                Notify.show("没有当前配置可修改，将作为新配置添加");
                // 降级为添加
                config = Config.create(type).url(url).name(name).update();
                EventBus.getDefault().post(new ConfigEvent(ConfigEvent.Type.COMMON));
                Notify.show("新配置已添加");
                dismiss();
                return;
            }
            // 更新当前配置的 URL 和名称
            current.setUrl(url);
            current.setName(name);
            current.update(); // 保存到数据库
            config = current;
            // 加载新配置（重新解析）
            ((ConfigListener) requireParentFragment()).setConfig(config);
            Notify.show("当前配置已更新");
        } else {
            // ========== 添加新配置（自动加载） ==========
            // 检查是否已存在相同 URL 的配置
            Config exists = AppDatabase.get().getConfigDao().find(url, type);
            if (exists != null) {
                // 如果已存在，直接切换到该配置（加载它）
                ((ConfigListener) requireParentFragment()).setConfig(exists);
                Notify.show("配置已存在，已切换");
                dismiss();
                return;
            }
            // 不存在则新建
            config = Config.create(type).url(url).name(name).update();
            // ★ 新增配置后自动加载使用 ★
            ((ConfigListener) requireParentFragment()).setConfig(config);
            Notify.show("新配置已添加并加载");
        }
        dismiss();
    }

    private void configureWindow() {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        Window window = getDialog().getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(requireContext());
        int width = Math.min(Math.round(ResUtil.getScreenWidth(requireContext()) * (land ? 0.58f : 0.92f)), ResUtil.dp2px(560));
        params.width = Math.max(width, ResUtil.dp2px(320));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String name = binding.name.getText().toString().trim();
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (TextUtils.isEmpty(path)) return;
        String url = "file:/" + path.replace(Path.rootPath(), "");
        // 文件选择器默认当作添加（也自动加载）
        Config exists = AppDatabase.get().getConfigDao().find(url, type);
        if (exists != null) {
            ((ConfigListener) requireParentFragment()).setConfig(exists);
            Notify.show("配置已存在，已切换");
            dismiss();
            return;
        }
        Config config = Config.create(type).url(url).name(name).update();
        ((ConfigListener) requireParentFragment()).setConfig(config);
        Notify.show("新配置已添加并加载");
        dismiss();
    });
}
