package com.fongmi.android.tv.ui.activity;

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
