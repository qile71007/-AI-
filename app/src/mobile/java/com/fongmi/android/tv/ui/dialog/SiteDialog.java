package com.fongmi.android.tv.ui.dialog;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogSiteBinding;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteBlockSetting;
import com.fongmi.android.tv.ui.adapter.SiteAdapter;
import com.fongmi.android.tv.ui.adapter.SiteSpeedAdapter;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class SiteDialog extends BaseAlertDialog implements SiteAdapter.OnClickListener {

    private DialogSiteBinding binding;
    private SiteListener listener;
    private SiteAdapter adapter;
    private ItemTouchHelper sortTouchHelper;
    private SpaceItemDecoration itemDecoration;
    private List<String> groups;
    private String selectedGroup = "";
    private boolean search;
    private boolean change;
    private boolean block;
    private int columnCount = 1;
    private SiteSpeedAdapter speedAdapter;
    private boolean speedMode;

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
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
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
        attachSortTouchHelper();
        binding.recycler.post(() -> {
            int pos = adapter.getSelectedPosition();
            if (pos > 0) {
                LinearLayoutManager lm = (LinearLayoutManager) binding.recycler.getLayoutManager();
                if (lm != null) lm.scrollToPositionWithOffset(pos, 0);
            } else {
                binding.recycler.scrollToPosition(0);
            }
        });
    }

    private void attachSortTouchHelper() {
        sortTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
                return adapter.drag(source.getBindingAdapterPosition(), target.getBindingAdapterPosition());
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        });
        sortTouchHelper.attachToRecyclerView(binding.recycler);
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
        binding.speed.setOnClickListener(this::onSpeedToggle);
        binding.speedCopy.setOnClickListener(this::onSpeedCopy);
    }

    // ========== 测速核心逻辑 ==========
    private void onSpeedToggle(View view) {
        if (binding == null) return;
        Util.hideKeyboard(binding.keyword);

        speedMode = !speedMode;
        binding.speed.setSelected(speedMode);

        if (speedMode) {
            // 进入测速模式
            if (speedAdapter != null && speedAdapter.isRunning()) {
                speedAdapter.cancelAll();
                speedAdapter = null;
            }
            binding.speedResult.setVisibility(View.VISIBLE);
            binding.recycler.setVisibility(View.GONE);
            binding.groupScroll.setVisibility(View.GONE);
            setSpeedButtonHighlight(true);
            startSpeedTest();
        } else {
            // 退出测速模式
            if (speedAdapter != null) {
                speedAdapter.cancelAll();
                speedAdapter = null;
            }
            binding.speedResult.setVisibility(View.GONE);
            binding.recycler.setVisibility(View.VISIBLE);
            binding.groupScroll.setVisibility(View.VISIBLE);
            binding.speedCopy.setVisibility(View.GONE);
            setSpeedButtonHighlight(false);
            binding.speedProgress.setVisibility(View.GONE);
            binding.speedProgress.setProgress(0);
            binding.speedStatus.setText("");
        }
    }

    private void setSpeedButtonHighlight(boolean highlight) {
        if (binding == null) return;
        if (highlight) {
            // 亮起：图标变为橙色（仅改色，不改变背景，避免图标消失）
            binding.speed.setImageTintList(ContextCompat.getColorStateList(requireContext(), R.color.speed_testing_bg));
        } else {
            // 恢复默认 tint（布局中定义的颜色）
            binding.speed.setImageTintList(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_text));
        }
    }

    private void startSpeedTest() {
        if (binding == null || getActivity() == null) return;

        if (speedAdapter != null) {
            speedAdapter.cancelAll();
            speedAdapter = null;
        }

        speedAdapter = new SiteSpeedAdapter(new SiteSpeedAdapter.ProgressCallback() {
            @Override
            public void onProgress(int done, int total) {
                if (getActivity() == null || binding == null) return;
                binding.speedProgress.setMax(total);
                binding.speedProgress.setProgress(done);
                binding.speedStatus.setText(getString(R.string.site_speed_testing, done, total));
            }

            @Override
            public void onComplete() {
                if (getActivity() == null || binding == null) return;
                binding.speedStatus.setText(R.string.site_speed_done);
                binding.speedProgress.setVisibility(View.GONE);
                binding.speedCopy.setVisibility(View.VISIBLE);
                Toast.makeText(requireContext(), R.string.site_speed_done_hint, Toast.LENGTH_LONG).show();
            }
        });

        binding.speedRecycler.setAdapter(speedAdapter);
        binding.speedRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.speedRecycler.setItemAnimator(null);

        List<Site> searchable = new ArrayList<>();
        for (Site s : VodConfig.get().getSites()) {
            if (s.isSearchable()) searchable.add(s);
        }

        binding.speedProgress.setVisibility(View.VISIBLE);
        binding.speedProgress.setProgress(0);
        binding.speedProgress.setMax(searchable.size());
        binding.speedCopy.setVisibility(View.GONE);
        binding.speedStatus.setText(getString(R.string.site_speed_testing, 0, searchable.size()));

        speedAdapter.startTest(searchable);
    }

    private void onSpeedCopy(View view) {
        if (speedAdapter != null) speedAdapter.copyResults(requireContext());
    }

    // ========== 其他原有方法（保持不变） ==========
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
        button.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_text));
        button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_bg));
        button.setStrokeColor(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_stroke));
        button.setOnClickListener(v -> onGroupClick(group, button));
        return button;
    }

    private void onGroupClick(String group, View view) {
        selectedGroup = group.equals(selectedGroup) ? "" : group;
        updateGroupView();
        filter();
        binding.recycler.scrollToPosition(0);
        if (!TextUtils.isEmpty(selectedGroup)) centerGroup(view);
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
    public boolean onTextLongClick(SiteAdapter.ViewHolder holder) {
        if (sortTouchHelper == null || holder.getBindingAdapterPosition() == RecyclerView.NO_POSITION) return false;
        Util.hideKeyboard(binding.keyword);
        sortTouchHelper.startDrag(holder);
        return true;
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
        else configureWindow();
    }

    private void configureWindow() {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        Window window = getDialog().getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(requireContext());
        int width = Math.min(Math.round(ResUtil.getScreenWidth(requireContext()) * (land ? 0.5f : 0.92f)), ResUtil.dp2px(620));
        params.width = Math.max(width, ResUtil.dp2px(320));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onDestroyView() {
        if (speedAdapter != null) {
            speedAdapter.cancelAll();
            speedAdapter = null;
        }
        super.onDestroyView();
    }
}
