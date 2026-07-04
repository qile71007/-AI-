package com.fongmi.android.tv.playback;

public class PlaybackRemoteSyncResult {

    public boolean success;
    public int fetched;
    public int applied;
    public int skipped;
    public int failed;
    public String message;

    public static PlaybackRemoteSyncResult success(PlaybackProgressBatchResult batch) {
        PlaybackRemoteSyncResult result = new PlaybackRemoteSyncResult();
        result.success = true;
        result.fetched = batch == null ? 0 : batch.total;
        result.applied = batch == null ? 0 : batch.applied;
        result.skipped = batch == null ? 0 : batch.skipped;
        result.failed = batch == null ? 0 : batch.failed;
        result.message = "";
        return result;
    }

    public static PlaybackRemoteSyncResult failure(String message) {
        PlaybackRemoteSyncResult result = new PlaybackRemoteSyncResult();
        result.success = false;
        result.message = message == null ? "" : message;
        return result;
    }
}
