package com.fongmi.android.tv.ui.holder;

import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.fongmi.android.tv.R;

public class EpisodeHoriHolder extends RecyclerView.ViewHolder {
    public TextView textView;
    public EpisodeHoriHolder(View itemView) { super(itemView); textView = itemView.findViewById(android.R.id.text1); }
}
