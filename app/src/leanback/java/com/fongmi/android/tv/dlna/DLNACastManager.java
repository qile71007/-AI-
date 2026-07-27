package com.fongmi.android.tv.dlna;

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
