package com.fongmi.android.tv.ui.dialog;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogAboutBinding;
import com.fongmi.android.tv.utils.AppVersion;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class AboutDialog {

    private AboutDialog() {
    }

    public static void show(FragmentActivity activity, Runnable updateAction) {
        DialogAboutBinding binding = DialogAboutBinding.inflate(LayoutInflater.from(activity));
        binding.version.setText(activity.getString(R.string.about_version, AppVersion.fullName(), BuildConfig.FLAVOR_mode, BuildConfig.FLAVOR_abi));
        configureContentHeight(activity, binding);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setView(binding.getRoot())
                .create();
        binding.confirm.setOnClickListener(v -> dialog.dismiss());
        binding.checkUpdate.setOnClickListener(v -> {
            dialog.dismiss();
            if (updateAction != null) updateAction.run();
        });
        dialog.setCanceledOnTouchOutside(false);
        boolean configured = configureWindow(activity, dialog);
        dialog.setOnShowListener(d -> {
            if (!configured) configureWindow(activity, dialog);
            binding.confirm.requestFocus();
        });
        dialog.show();
    }

    private static void configureContentHeight(FragmentActivity activity, DialogAboutBinding binding) {
        int screenHeight = ResUtil.getScreenHeight(activity);
        int maxHeight = (int) (screenHeight * (ResUtil.isLand(activity) ? 0.42f : 0.38f));
        int minHeight = ResUtil.dp2px(220);
        ViewGroup.LayoutParams params = binding.contentScroll.getLayoutParams();
        params.height = Math.max(minHeight, Math.min(maxHeight, ResUtil.dp2px(420)));
        binding.contentScroll.setLayoutParams(params);
    }

    private static boolean configureWindow(FragmentActivity activity, AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return false;
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (ResUtil.getScreenWidth(activity) * (ResUtil.isLand(activity) ? 0.62f : 0.92f));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
        return true;
    }
}
