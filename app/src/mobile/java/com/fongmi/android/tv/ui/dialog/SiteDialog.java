package com.fongmi.android.tv.ui.dialog;

import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogSiteBinding;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteBlockSetting;
import com.fongmi.android.tv.ui.adapter.SiteAdapter;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class SiteDialog extends BaseAlertDialog implements SiteAdapter.OnClickListener {

    private static String selectedGroup = "";

    private DialogSiteBinding binding;
    private SiteListener listener;
    private SiteAdapter adapter;
    private SpaceItemDecoration itemDecoration;
    private List<String> groups;
    private boolean search;
    private boolean change;
    private boolean block;
    private int columnCount = 1;
    // 新增：缓存当前选中的站点
    private Site mCurrentSite;

    public static SiteDialog create() {
        return new SiteDialog();
    }

    public SiteDialog search() {
        search = true;
        return this;
    }

    public SiteDialog change() {
        change = true;
        return this;
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
        if (fragment instanceof SiteListener) listener = (SiteListener) fragment;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSiteBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new SiteAdapter(this);
        groups = getGroups();
        binding.recycler.setAdapter(adapter);
        adapter.search(search).change(change);
        setColumnCount(Setting.getSiteColumn());
        setGroupView();
        filter();
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);

        // 【修改】获取当前选中的站点并缓存
        mCurrentSite = VodConfig.get().getHome();
        // 滚动到当前选中站点位置（延迟执行以确保 Adapter 已填充数据）
        binding.recycler.post(() -> scrollToCurrentSite());
    }

    @Override
    protected void initEvent() {
        binding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) Util.hideKeyboard(binding.keyword);
            return false;
        });
        binding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable s) {
                filter();
                binding.recycler.scrollToPosition(0);
            }
        });
        binding.block.setOnClickListener(this::onBlockToggle);
        binding.search.setOnClickListener(this::onColumnToggle);
    }

    private void onBlockToggle(View view) {
        Util.hideKeyboard(binding.keyword);
        block = !block;
        binding.block.setSelected(block);
        binding.block.setImageResource(block ? R.drawable.ic_site_visible : R.drawable.ic_site_hidden);
        adapter.block(block);
        groups = getGroups();
        setGroupView();
        filter();
        binding.recycler.scrollToPosition(0);
        // 过滤后重新定位当前站点
        binding.recycler.post(() -> scrollToCurrentSite());
    }

    private void onColumnToggle(View view) {
        Util.hideKeyboard(binding.keyword);
        int nextColumn = columnCount == 1 ? 2 : 1;
        Setting.putSiteColumn(nextColumn);
        setColumnCount(nextColumn);
    }

    private void setColumnCount(int count) {
        columnCount = count == 2 ? 2 : 1;
        if (itemDecoration != null) binding.recycler.removeItemDecoration(itemDecoration);
        itemDecoration = new SpaceItemDecoration(columnCount, 8);
        binding.recycler.addItemDecoration(itemDecoration);
        binding.recycler.setLayoutManager(columnCount == 1 ? new LinearLayoutManager(requireContext()) : new GridLayoutManager(requireContext(), columnCount));
        binding.search.setImageResource(columnCount == 1 ? R.drawable.ic_site_double_column : R.drawable.ic_site_single_column);
        if (adapter != null) adapter.column(columnCount);
    }

    private List<String> getGroups() {
        return new ArrayList<>(Site.getGroups(SiteBlockSetting.filter(VodConfig.get().getSites(), block)));
    }

    private void setGroupView() {
        if (groups.isEmpty()) {
            selectedGroup = "";
            binding.groupScroll.setVisibility(View.GONE);
            return;
        }
        if (!TextUtils.isEmpty(selectedGroup) && !groups.contains(selectedGroup)) selectedGroup = "";
        binding.groupScroll.setVisibility(View.VISIBLE);
        binding.groupList.removeAllViews();
        for (String group : groups) binding.groupList.addView(getGroupView(group));
        updateGroupView();
    }

    private MaterialButton getGroupView(String group) {
        MaterialButton button = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(ResUtil.dp2px(8));
        button.setLayoutParams(params);
        button.setText(group);
        button.setSingleLine(true);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(ResUtil.dp2px(14), ResUtil.dp2px(6), ResUtil.dp2px(14), ResUtil.dp2px(6));
        button.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.selector_control));
        button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.selector_button));
        button.setStrokeColor(ContextCompat.getColorStateList(requireContext(), R.color.selector_stroke));
        button.setOnClickListener(v -> onGroupClick(group, button));
        return button;
    }

    private void onGroupClick(String group, View view) {
        selectedGroup = group.equals(selectedGroup) ? "" : group;
        updateGroupView();
        filter();
        binding.recycler.scrollToPosition(0);
        if (!TextUtils.isEmpty(selectedGroup)) centerGroup(view);
        // 分组过滤后重新定位当前站点
        binding.recycler.post(() -> scrollToCurrentSite());
    }

    private void updateGroupView() {
        for (int i = 0; i < binding.groupList.getChildCount(); i++) {
            View view = binding.groupList.getChildAt(i);
            boolean selected = ((MaterialButton) view).getText().toString().equals(selectedGroup);
            view.setSelected(selected);
            view.setAlpha(TextUtils.isEmpty(selectedGroup) || selected ? 1.0f : 0.5f);
        }
    }

    private void centerGroup(View view) {
        binding.groupScroll.post(() -> binding.groupScroll.smoothScrollTo(Math.max(0, view.getLeft() + view.getWidth() / 2 - binding.groupScroll.getWidth() / 2), 0));
    }

    private void filter() {
        adapter.filter(selectedGroup, binding.keyword.getText().toString());
    }

    // ==================== 新增：滚动到当前选中站点 ====================
    private void scrollToCurrentSite() {
        if (mCurrentSite == null || adapter == null || adapter.getItemCount() == 0) return;
        // 获取适配器当前的站点列表
        List<Site> sites = adapter.getItems();
        if (sites == null || sites.isEmpty()) return;
        
        int position = -1;
        String currentKey = mCurrentSite.getKey();
        for (int i = 0; i < sites.size(); i++) {
            Site site = sites.get(i);
            if (site != null && currentKey.equals(site.getKey())) {
                position = i;
                break;
            }
        }
        if (position != -1) {
            // 平滑滚动到目标位置
            binding.recycler.smoothScrollToPosition(position);
        }
    }

    @Override
    public void onTextClick(Site item) {
        if (block) {
            SiteBlockSetting.toggle(item);
            filter();
            return;
        }
        if (listener != null) listener.setSite(item);
        dismiss();
    }

    @Override
    public void onSearchClick(int position, Site item) {
        item.setSearchable(!item.isSearchable()).save();
        adapter.notifyItemChanged(position);
    }

    @Override
    public void onChangeClick(int position, Site item) {
        item.setChangeable(!item.isChangeable()).save();
        adapter.notifyItemChanged(position);
    }

    @Override
    public boolean onSearchLongClick(Site item) {
        boolean result = !item.isSearchable();
        adapter.getItems().forEach(site -> site.setSearchable(result).save());
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        return true;
    }

    @Override
    public boolean onChangeLongClick(Site item) {
        boolean result = !item.isChangeable();
        adapter.getItems().forEach(site -> site.setChangeable(result).save());
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0 && SiteBlockSetting.filter(VodConfig.get().getSites(), true).isEmpty()) dismiss();
        else if (ResUtil.isLand(requireContext())) setWidth(0.5f);
    }
}