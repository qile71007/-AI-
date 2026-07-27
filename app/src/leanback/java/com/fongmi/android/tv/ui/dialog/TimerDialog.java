package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Notify;

public class TimerDialog extends DialogFragment {
    private CountDownTimer mTimer;
    public static void show(Activity activity) {
        new TimerDialog().show(activity.getFragmentManager(), "timer");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String[] items = {"5分钟", "10分钟", "15分钟", "30分钟", "60分钟", "关闭"};
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.setting_timer)
                .setItems(items, (dialog, which) -> {
                    if (which == 5) {
                        if (mTimer != null) mTimer.cancel();
                        return;
                    }
                    long[] times = {5, 10, 15, 30, 60};
                    final long target = times[which] * 60 * 1000;
                    if (mTimer != null) mTimer.cancel();
                    mTimer = new CountDownTimer(target, 1000) {
                        @Override public void onTick(long millisUntilFinished) {}
                        @Override public void onFinish() {
                            App.post(() -> {
                                Notify.show("定时器已到，正在退出");
                                requireActivity().finish();
                            });
                        }
                    }.start();
                }).create();
    }
    @Override public void onDestroy() { super.onDestroy(); if (mTimer != null) mTimer.cancel(); }
}
