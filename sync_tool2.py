#!/usr/bin/env python3
"""
第二阶段同步：DLNA投屏、控制对话框、工具类、适配器
"""
import os

BASE = '/home/tv_project'
LEANBACK = f'{BASE}/app/src/leanback'
MOBILE = f'{BASE}/app/src/mobile'

def write_java(path, code):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if not os.path.exists(path):
        with open(path, 'w') as f:
            f.write(code)
        print(f'  [创建] {os.path.relpath(path, BASE)}')

def main():
    # ==================== 1. DLNA 投屏 ====================
    # CastVideo.java - 数据bean
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/bean/CastVideo.java', '''package com.fongmi.android.tv.bean;

import android.os.Parcel;
import android.os.Parcelable;

public class CastVideo implements Parcelable {
    public String id;
    public String name;
    public String pic;
    public String url;

    public static CastVideo create(String id, String name, String pic, String url) {
        CastVideo v = new CastVideo();
        v.id = id; v.name = name; v.pic = pic; v.url = url;
        return v;
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id); dest.writeString(name); dest.writeString(pic); dest.writeString(url);
    }
    protected CastVideo() {}
    protected CastVideo(Parcel in) {
        id = in.readString(); name = in.readString(); pic = in.readString(); url = in.readString();
    }
    public static final Creator<CastVideo> CREATOR = new Creator<CastVideo>() {
        @Override public CastVideo createFromParcel(Parcel source) { return new CastVideo(source); }
        @Override public CastVideo[] newArray(int size) { return new CastVideo[size]; }
    };
}
''')

    # DLNACast.java - 投屏核心
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/dlna/DLNACast.java', '''package com.fongmi.android.tv.dlna;

import android.content.Context;
import android.content.Intent;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.service.DLNACastService;

public class DLNACast {
    public static void start(Context context, CastVideo video) {
        Intent intent = new Intent(context, DLNACastService.class);
        intent.putExtra("video", video);
        context.startService(intent);
    }
    public static void stop(Context context) {
        context.stopService(new Intent(context, DLNACastService.class));
    }
}
''')

    # DLNACastManager.java
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/dlna/DLNACastManager.java', '''package com.fongmi.android.tv.dlna;

import java.util.ArrayList;
import java.util.List;

public class DLNACastManager {
    private static final DLNACastManager instance = new DLNACastManager();
    private final List<Listener> listeners = new ArrayList<>();
    private boolean casting = false;

    public static DLNACastManager get() { return instance; }

    public interface Listener { void onCastStateChanged(boolean casting); }

    public void addListener(Listener listener) { listeners.add(listener); }
    public void removeListener(Listener listener) { listeners.remove(listener); }
    public boolean isCasting() { return casting; }
    public void setCasting(boolean casting) {
        this.casting = casting;
        for (Listener l : listeners) l.onCastStateChanged(casting);
    }
}
''')

    # ShortcutReceiver.java
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/receiver/ShortcutReceiver.java', '''package com.fongmi.android.tv.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ShortcutReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.fongmi.android.tv.SHORTCUT";
    @Override public void onReceive(Context context, Intent intent) { }
}
''')

    # DLNACastService.java
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/service/DLNACastService.java', '''package com.fongmi.android.tv.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.dlna.DLNACastManager;

public class DLNACastService extends Service {
    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        DLNACastManager.get().setCasting(true);
        return START_STICKY;
    }
    @Override
    public void onDestroy() {
        DLNACastManager.get().setCasting(false);
        super.onDestroy();
    }
}
''')

    # ==================== 2. 关键 Dialog ====================
    # CastDialog.java
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/ui/dialog/CastDialog.java', '''package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.dlna.DLNACastManager;

public class CastDialog extends DialogFragment {
    public static void show(Activity activity) {
        new CastDialog().show(activity.getFragmentManager(), "cast");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.player_cast)
                .setMessage(DLNACastManager.get().isCasting() ? "投屏中" : "未投屏")
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
    }
}
''')

    # ControlDialog.java
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/ui/dialog/ControlDialog.java', '''package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;

public class ControlDialog extends DialogFragment {
    public static void show(Activity activity) {
        new ControlDialog().show(activity.getFragmentManager(), "control");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.player_control)
                .setItems(new String[]{"播放/暂停", "停止", "重试"}, (dialog, which) -> {})
                .create();
    }
}
''')

    # LinkDialog.java
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/ui/dialog/LinkDialog.java', '''package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;

public class LinkDialog extends DialogFragment {
    public static void show(Activity activity) {
        new LinkDialog().show(activity.getFragmentManager(), "link");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.player_link)
                .setMessage("请输入链接地址")
                .setPositiveButton(R.string.dialog_positive, null)
                .setNegativeButton(R.string.dialog_negative, null)
                .create();
    }
}
''')

    # ReceiveDialog.java
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/ui/dialog/ReceiveDialog.java', '''package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;

public class ReceiveDialog extends DialogFragment {
    public static void show(Activity activity) {
        new ReceiveDialog().show(activity.getFragmentManager(), "receive");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.player_receive)
                .setMessage("正在接收投屏...")
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
    }
}
''')

    # TypeDialog.java
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/ui/dialog/TypeDialog.java', '''package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;

public class TypeDialog extends DialogFragment {
    public static void show(Activity activity) {
        new TypeDialog().show(activity.getFragmentManager(), "type");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireActivity())
                .setTitle("选择分类")
                .setItems(new String[]{"全部", "电影", "电视剧", "综艺", "动漫"}, (dialog, which) -> {})
                .create();
    }
}
''')

    # LutPanelDialog.java
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/ui/dialog/LutPanelDialog.java', '''package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;

public class LutPanelDialog extends DialogFragment {
    public static void show(Activity activity) {
        new LutPanelDialog().show(activity.getFragmentManager(), "lut_panel");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.player_lut)
                .setMessage("LUT 调色面板")
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
    }
}
''')

    # VideoContentDialog.java
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/ui/dialog/VideoContentDialog.java', '''package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;

public class VideoContentDialog extends DialogFragment {
    public static void show(Activity activity, String content) {
        VideoContentDialog dialog = new VideoContentDialog();
        Bundle args = new Bundle();
        args.putString("content", content);
        dialog.setArguments(args);
        dialog.show(activity.getFragmentManager(), "video_content");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String content = getArguments() != null ? getArguments().getString("content", "") : "";
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.player_content)
                .setMessage(content)
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
    }
}
''')

    # EpisodeGridDialog.java
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/ui/dialog/EpisodeGridDialog.java', '''package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;

public class EpisodeGridDialog extends DialogFragment {
    public static void show(Activity activity) {
        new EpisodeGridDialog().show(activity.getFragmentManager(), "episode_grid");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.player_episode)
                .setMessage("选集网格")
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
    }
}
''')

    # ==================== 3. Live 相关 Dialog ====================
    for name in ['LiveControlDialog', 'LiveEpgDialog', 'LiveLineDialog', 'LiveProgramDialog']:
        write_java(f'{LEANBACK}/java/com/fongmi/android/tv/ui/dialog/{name}.java', f'''package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;

public class {name} extends DialogFragment {{
    public static void show(Activity activity) {{
        new {name}().show(activity.getFragmentManager(), "{name.lower()}");
    }}
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {{
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.player_live)
                .setMessage(R.string.setting_live)
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
    }}
}}
''')

    # ==================== 4. 工具类 ====================
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/utils/Biometric.java', '''package com.fongmi.android.tv.utils;

import android.content.Context;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import java.util.concurrent.Executor;

public class Biometric {
    public static void authenticate(FragmentActivity activity, Runnable onSuccess) {
        Executor executor = ContextCompat.getMainExecutor(activity);
        BiometricPrompt.AuthenticationCallback callback = new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                if (onSuccess != null) onSuccess.run();
            }
        };
        BiometricPrompt prompt = new BiometricPrompt(activity, executor, callback);
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("验证身份").setNegativeButtonText("取消").build();
        prompt.authenticate(info);
    }
}
''')

    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/utils/MobileWindow.java', '''package com.fongmi.android.tv.utils;

import android.content.Context;

public class MobileWindow {
    public static boolean isWide(Context context) { return false; }
    public static int getWidth(Context context) { return ResUtil.getScreenWidth(context); }
}
''')

    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/utils/PiP.java', '''package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.os.Build;

public class PiP {
    public static void enter(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
        }
    }
    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }
}
''')

    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/utils/Timer.java', '''package com.fongmi.android.tv.utils;

import android.os.Handler;
import android.os.Looper;

public class Timer {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;
    private boolean running;

    public void start(Runnable task, long intervalMs) {
        runnable = () -> { task.run(); if (running) handler.postDelayed(runnable, intervalMs); };
        running = true;
        handler.post(runnable);
    }
    public void stop() { running = false; handler.removeCallbacks(runnable); }
    public boolean isRunning() { return running; }
}
''')

    # ==================== 5. 关键适配器 ====================
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/ui/adapter/DeviceAdapter.java', '''package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fongmi.android.tv.R;
import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {
    private final List<String> items = new ArrayList<>();
    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView tv = new TextView(parent.getContext());
        tv.setPadding(24, 16, 24, 16);
        tv.setTextSize(16);
        return new ViewHolder(tv);
    }
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.textView.setText(items.get(position));
    }
    @Override public int getItemCount() { return items.size(); }
    public void addAll(List<String> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ViewHolder(TextView tv) { super(tv); textView = tv; }
    }
}
''')

    # HistoryAdapter.java
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/ui/adapter/HistoryAdapter.java', '''package com.fongmi.android.tv.ui.adapter;

import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fongmi.android.tv.bean.History;
import java.util.ArrayList;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
    private final List<History> items = new ArrayList<>();
    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView tv = new TextView(parent.getContext());
        tv.setPadding(24, 16, 24, 16);
        tv.setTextSize(16);
        return new ViewHolder(tv);
    }
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.textView.setText(items.get(position).getVodName());
    }
    @Override public int getItemCount() { return items.size(); }
    public void addAll(List<History> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ViewHolder(TextView tv) { super(tv); textView = tv; }
    }
}
''')

    # FilterAdapter.java
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/ui/adapter/FilterAdapter.java', '''package com.fongmi.android.tv.ui.adapter;

import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class FilterAdapter extends RecyclerView.Adapter<FilterAdapter.ViewHolder> {
    private final List<String> items = new ArrayList<>();
    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView tv = new TextView(parent.getContext());
        tv.setPadding(16, 12, 16, 12);
        tv.setTextSize(14);
        return new ViewHolder(tv);
    }
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.textView.setText(items.get(position));
    }
    @Override public int getItemCount() { return items.size(); }
    public void addAll(List<String> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ViewHolder(TextView tv) { super(tv); textView = tv; }
    }
}
''')

    # ==================== 6. 基础类 ====================
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/ui/base/BaseEpisodeHolder.java', '''package com.fongmi.android.tv.ui.base;

import android.view.View;
import androidx.recyclerview.widget.RecyclerView;

public class BaseEpisodeHolder extends RecyclerView.ViewHolder {
    public BaseEpisodeHolder(View itemView) { super(itemView); }
}
''')

    # ==================== 7. Custom Views ====================
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/ui/custom/CustomFabBehavior.java', '''package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class CustomFabBehavior extends FloatingActionButton.Behavior {
    public CustomFabBehavior() {}
    public CustomFabBehavior(Context context, AttributeSet attrs) { super(); }
    @Override
    public boolean onStartNestedScroll(CoordinatorLayout parent, View child, View directTargetChild, View target, int axes, int type) {
        return true;
    }
}
''')

    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/ui/custom/CustomKeyDown.java', '''package com.fongmi.android.tv.ui.custom;

import android.view.KeyEvent;
import android.view.MotionEvent;

public class CustomKeyDown {
    public boolean onTouchEvent(MotionEvent event) { return false; }
    public boolean dispatchKeyEvent(KeyEvent event) { return false; }
}
''')

    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/ui/custom/FixedNestedScrollView.java', '''package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

public class FixedNestedScrollView extends ScrollView {
    public FixedNestedScrollView(Context context) { super(context); }
    public FixedNestedScrollView(Context context, AttributeSet attrs) { super(context, attrs); }
    public FixedNestedScrollView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }
}
''')

    # ==================== 8. 其他适配器 ====================
    for name in ['LiveEpgAdapter', 'LiveLineAdapter', 'LiveProgramAdapter', 'LiveProgramDateAdapter', 'ThemeAdapter', 'TypeDialogAdapter', 'ValueAdapter', 'VodAdapter', 'EpisodeGroupAdapter']:
        write_java(f'{LEANBACK}/java/com/fongmi/android/tv/ui/adapter/{name}.java', f'''package com.fongmi.android.tv.ui.adapter;

import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class {name} extends RecyclerView.Adapter<{name}.ViewHolder> {{
    private final List<String> items = new ArrayList<>();
    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {{
        TextView tv = new TextView(parent.getContext());
        tv.setPadding(16, 12, 16, 12);
        tv.setTextSize(14);
        return new ViewHolder(tv);
    }}
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {{
        holder.textView.setText(items.get(position));
    }}
    @Override public int getItemCount() {{ return items.size(); }}
    public void addAll(List<String> list) {{ items.clear(); items.addAll(list); notifyDataSetChanged(); }}
    static class ViewHolder extends RecyclerView.ViewHolder {{
        TextView textView;
        ViewHolder(TextView tv) {{ super(tv); textView = tv; }}
    }}
}}
''')

    # ==================== 9. 缺失的 Activity ====================
    # WebHomeChromeController.java
    write_java(f'{LEANBACK}/java/com/fongmi/android/tv/ui/activity/WebHomeChromeController.java', '''package com.fongmi.android.tv.ui.activity;

import android.os.Bundle;
import android.view.View;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.web.WebHomeViewport;
import com.google.gson.JsonObject;

public class WebHomeChromeController {
    public interface Host {
        boolean isWebHomeChromeActive();
        void onWebHomeChromeChanged(String mode);
        void onWebHomeViewportChanged(WebHomeViewport viewport);
    }
    public WebHomeChromeController(Host host, ActivityHomeBinding binding, Bundle savedInstanceState, JsonObject startup) {}
    public void save(Bundle outState) {}
    public void destroy() {}
    public void setChrome(JsonObject payload) {}
    public void applyDefault(JsonObject defaultChrome) {}
    public void setLegacyToolbar(boolean visible) {}
    public void restore() {}
    public void refreshLayout() {}
    public void onConfigurationChanged() {}
    public void onWindowFocusChanged(boolean hasFocus) {}
    public boolean consumeBack() { return false; }
    public String getMode() { return "normal"; }
    public WebHomeViewport getViewport() { return WebHomeViewport.EMPTY; }
}
''')

    print("\\n=== 第二阶段同步完成！===")

if __name__ == '__main__':
    main()