import os
LEANBACK = '/home/tv_project/app/src/leanback'
def write_java(path, code):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f: f.write(code)
    print(f'  [OK] {os.path.relpath(path, "/home/tv_project")}')

# CastDialog
write_java(f'{LEANBACK}/java/com/fongmi/android/tv/ui/dialog/CastDialog.java', '''
package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.dlna.DLNACastManager;
import java.util.ArrayList;
import java.util.List;

public class CastDialog extends DialogFragment implements DLNACastManager.DeviceListener {
    private final List<Device> mDevices = new ArrayList<>();
    private DeviceAdapter mAdapter;
    public static void show(Activity activity) { new CastDialog().show(activity.getFragmentManager(), "cast"); }
    @Override public void onStart() { super.onStart(); DLNACastManager.get().init(requireActivity()); DLNACastManager.get().setDeviceListener(this); DLNACastManager.get().search(); }
    @NonNull @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        RecyclerView recycler = new RecyclerView(requireActivity());
        recycler.setLayoutManager(new LinearLayoutManager(requireActivity()));
        recycler.setPadding(24, 16, 24, 16);
        mAdapter = new DeviceAdapter(); recycler.setAdapter(mAdapter);
        loadDevices();
        return new AlertDialog.Builder(requireActivity()).setTitle(R.string.player_cast).setView(recycler).setNegativeButton(R.string.dialog_negative, null).create();
    }
    private void loadDevices() { mDevices.clear(); mDevices.addAll(Device.getAll()); mAdapter.notifyDataSetChanged(); if (mDevices.isEmpty()) DLNACastManager.get().search(); }
    @Override public void onDeviceAdded(Device device) { if (!mDevices.contains(device)) { mDevices.add(device); mAdapter.notifyDataSetChanged(); } }
    @Override public void onDeviceRemoved(Device device) { mDevices.remove(device); mAdapter.notifyDataSetChanged(); }
    @Override public void onFind(Device device) { if (!mDevices.contains(device)) { mDevices.add(device); mAdapter.notifyDataSetChanged(); } }
    @Override public void onStop() { super.onStop(); DLNACastManager.get().setDeviceListener(null); DLNACastManager.get().release(requireActivity()); }
    private class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) { TextView tv = new TextView(requireActivity()); tv.setPadding(24, 16, 24, 16); tv.setTextSize(18); tv.setSingleLine(true); return new ViewHolder(tv); }
        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) { Device device = mDevices.get(position); holder.textView.setText(device.getName() + " (" + device.getIp() + ")"); }
        @Override public int getItemCount() { return mDevices.size(); }
        class ViewHolder extends RecyclerView.ViewHolder { TextView textView; ViewHolder(TextView tv) { super(tv); textView = tv; } }
    }
}
''')

print('Done')
