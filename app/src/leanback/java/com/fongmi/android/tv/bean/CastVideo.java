package com.fongmi.android.tv.bean;

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
