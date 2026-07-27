
package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.FragmentActivity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.server.Server;

public class ReceiveDialog extends DialogFragment {
    public static void show(FragmentActivity activity) { new ReceiveDialog().show(activity.getSupportFragmentManager(), "receive"); }
    @NonNull @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        String address = Server.get().getAddress();
        TextView textView = new TextView(requireActivity());
        textView.setText("正在等待投屏...\n\n服务器地址: " + address + "\n\n请使用手机浏览器访问此地址");
        textView.setPadding(24, 16, 24, 16); textView.setTextSize(16);
        return new AlertDialog.Builder(requireActivity()).setTitle("接收投屏").setView(textView).setPositiveButton(R.string.dialog_positive, null).create();
    }
}
