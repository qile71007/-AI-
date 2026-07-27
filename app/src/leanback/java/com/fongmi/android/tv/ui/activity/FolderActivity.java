
package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityFolderBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;

import java.util.ArrayList;
import java.util.List;

public class FolderActivity extends BaseActivity {

    private ActivityFolderBinding mBinding;
    private final List<Vod> mItems = new ArrayList<>();

    public static void start(Activity activity, String key, Class type, List<Vod> items) {
        Intent intent = new Intent(activity, FolderActivity.class);
        intent.putExtra("key", key);
        intent.putExtra("type", type);
        activity.startActivity(intent);
    }

    @Override protected ViewBinding getBinding() { return mBinding = ActivityFolderBinding.inflate(getLayoutInflater()); }

    @Override protected void initView(Bundle savedInstanceState) {
        String key = getIntent().getStringExtra("key");
        Class type = getIntent().getParcelableExtra("type");
        if (type != null) setTitle(type.getTypeName());
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, 4));
        mBinding.recycler.setAdapter(new FolderAdapter(key));
    }

    private class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.ViewHolder> {
        private final String mKey;
        FolderAdapter(String key) { mKey = key; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(FolderActivity.this);
            tv.setPadding(24, 16, 24, 16);
            tv.setTextSize(16);
            tv.setSingleLine(true);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            tv.setFocusable(true);
            tv.setClickable(true);
            return new ViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Vod vod = mItems.get(position);
            holder.textView.setText(vod.getVodName());
            holder.textView.setOnClickListener(v -> {
                VideoActivity.start(FolderActivity.this, mKey, vod.getVodId(), vod.getVodName(), vod.getVodPic());
            });
        }

        @Override public int getItemCount() { return mItems.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            ViewHolder(TextView tv) { super(tv); textView = tv; }
        }
    }
}
