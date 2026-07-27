package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.Setting;

public class ThemeDialog extends DialogFragment {
    public interface Listener { void setTheme(int color); }
    private Listener listener;
    public static void show(Listener listener) {
        ThemeDialog dialog = new ThemeDialog();
        dialog.listener = listener;
        dialog.show(((FragmentActivity)listener).getSupportFragmentManager(), "theme");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String[] themes = {"系统默认", "自定义颜色", "关闭"};
        int checked = Setting.getThemeColor() == -1 ? 0 : Setting.getThemeColor() == 0 ? 1 : 2;
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.setting_theme_color)
                .setSingleChoiceItems(themes, Math.min(checked, 2), (dialog, which) -> {
                    int color = which == 0 ? -1 : which == 1 ? 0 : Setting.getThemeColor();
                    Setting.putThemeColor(color);
                    if (listener != null) listener.setTheme(color);
                    RefreshEvent.theme();
                    dialog.dismiss();
                }).create();
    }
}
