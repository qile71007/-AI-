
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
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.ActivityHistoryBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.ResUtil;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

public class HistoryActivity extends BaseActivity {

    private ActivityHistoryBinding mBinding;
    private HistoryAdapter mAdapter;
    private boolean mDeleteMode = false;

    public static void start(Activity activity) { activity.startActivity(new Intent(activity, HistoryActivity.class)); }

    @Override protected ViewBinding getBinding() { return mBinding = ActivityHistoryBinding.inflate(getLayoutInflater()); }

    @Override protected void initView(Bundle savedInstanceState) {
        int column = 4;
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, column));
        mAdapter = new HistoryAdapter();
        mBinding.recycler.setAdapter(mAdapter);
        loadHistory();
    }

    @Override protected void initEvent() {
        mBinding.delete.setOnClickListener(v -> toggleDeleteMode());
    }

    private void loadHistory() {
        List<History> list = History.get();
        mAdapter.setItems(list);
    }

    private void toggleDeleteMode() {
        mDeleteMode = !mDeleteMode;
        mAdapter.notifyDataSetChanged();
        mBinding.delete.setText(mDeleteMode ? R.string.dialog_positive : R.string.home_history);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.HISTORY) loadHistory();
    }

    @Override protected void onBackInvoked() {
        if (mDeleteMode) toggleDeleteMode();
        else super.onBackInvoked();
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private List<History> mItems = java.util.Collections.emptyList();

        void setItems(List<History> items) { mItems = items; notifyDataSetChanged(); }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(HistoryActivity.this);
            tv.setPadding(24, 16, 24, 16);
            tv.setTextSize(18);
            tv.setSingleLine(true);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            tv.setFocusable(true);
            tv.setClickable(true);
            return new ViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            History h = mItems.get(position);
            String text = h.getVodName();
            if (mDeleteMode) text = "[X] " + text;
            holder.textView.setText(text);
            holder.textView.setOnClickListener(v -> {
                if (mDeleteMode) {
                    h.delete();
                    loadHistory();
                } else {
                    VideoActivity.start(HistoryActivity.this, h.getSiteKey(), h.getVodId(), h.getVodName(), h.getVodPic(), null, h.getWallPic());
                }
            });
        }

        @Override public int getItemCount() { return mItems.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            ViewHolder(TextView tv) { super(tv); textView = tv; }
        }
    }
}
