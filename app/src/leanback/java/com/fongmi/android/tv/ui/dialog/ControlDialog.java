
package com.fongmi.android.tv.ui.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.PlayerManager;

public class ControlDialog extends DialogFragment {
    private PlayerManager mPlayer;
    private static final float[] SPEEDS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    public static ControlDialog create(PlayerManager player) { ControlDialog d = new ControlDialog(); d.mPlayer = player; return d; }
    @NonNull @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        LinearLayout layout = new LinearLayout(requireActivity()); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(32, 24, 32, 24);
        addBtn(layout, "播放/暂停", v -> { if (mPlayer != null) { if (mPlayer.isPlaying()) mPlayer.pause(); else mPlayer.play(); } });
        addBtn(layout, "停止", v -> { if (mPlayer != null) mPlayer.stop(); });
        TextView speedTitle = new TextView(requireActivity()); speedTitle.setText(getString(R.string.player_speed)); speedTitle.setTextSize(16); speedTitle.setPadding(0, 16, 0, 8); layout.addView(speedTitle);
        LinearLayout speedRow = new LinearLayout(requireActivity()); speedRow.setOrientation(LinearLayout.HORIZONTAL);
        for (float s : SPEEDS) { Button btn = new Button(requireActivity()); btn.setText(s + "x"); btn.setPadding(12, 8, 12, 8); btn.setOnClickListener(v -> { if (mPlayer != null) mPlayer.setSpeed(s); }); speedRow.addView(btn); }
        layout.addView(speedRow);
        addBtn(layout, "切换解码", v -> { if (mPlayer != null) mPlayer.toggleDecode(); });
        return new AlertDialog.Builder(requireActivity()).setTitle(R.string.player_control).setView(layout).setNegativeButton(R.string.dialog_negative, null).create();
    }
    private void addBtn(LinearLayout parent, String text, View.OnClickListener listener) { Button btn = new Button(requireActivity()); btn.setText(text); btn.setPadding(16, 12, 16, 12); btn.setAllCaps(false); btn.setOnClickListener(listener); parent.addView(btn); }
}
