package com.acer.paroyalty;

import org.json.JSONException;
import org.json.JSONObject;

public class AdData {
    public String url;
    public String title;
    public String content;
    public String trackToken;
    public AdData(String url, String title, String content, String trackToken) {
        this.url = url;
        this.title = title;
        this.content = content;
        this.trackToken = trackToken;
    }

    public static AdData parseFromJson(JSONObject object) {
        try {
            return new AdData(object.getString("url"), object.getString("title"), object.getString("content"), object.getString("trackToken"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
}