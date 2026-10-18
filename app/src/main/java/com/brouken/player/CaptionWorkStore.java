package com.brouken.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/** Durable, token-guarded state prevents a cancelled older job overwriting a newer request. */
public final class CaptionWorkStore {
    public enum Status { QUEUED, GENERATING, READY, FAILED, CANCELLED }
    public static final class Entry {
        public Status status;
        public String token, reason;
        public int percent;
        public String label() {
            switch(status) {
                case GENERATING: return "Generating · " + percent + "%";
                case READY: return "Subtitles ready";
                case FAILED: return "Subtitles failed · Retry";
                case CANCELLED: return "Subtitles cancelled · Retry";
                default: return "Subtitles queued";
            }
        }
    }
    private final SharedPreferences prefs;
    private final Context context;
    private static boolean recovered;
    public static synchronized void initialize(Context context) {
        if(recovered)return; recovered=true;
        new CaptionWorkStore(context).recoverInterrupted();
    }
    public CaptionWorkStore(Context context) { this.context=context.getApplicationContext();prefs=context.getSharedPreferences("caption_work",Context.MODE_PRIVATE); }
    static String key(Uri media, CaptionPack pack) { return digest(media+"|"+pack.cacheIdentity()); }
    static String digest(String value) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder();
            final char[] hex="0123456789abcdef".toCharArray();
            for(byte b:bytes) { result.append(hex[(b&255)>>>4]);result.append(hex[b&15]); }
            return result.toString();
        } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public void begin(Uri media,CaptionPack pack,String token) { synchronized(CaptionWorkStore.class) { write(media,pack,token,Status.QUEUED,0,""); } }
    public void update(Uri media,CaptionPack pack,String token,Status state,int percent,String reason) {
        synchronized(CaptionWorkStore.class) {
        Entry current=read(media,pack);
        if(current!=null && token.equals(current.token))write(media,pack,token,state,percent,reason);
        }
    }
    private void write(Uri media,CaptionPack pack,String token,Status state,int percent,String reason) {
        try {
            JSONObject json=new JSONObject();json.put("token",token);json.put("state",state.name());json.put("uri",media.toString());json.put("pack",pack.id);
            json.put("percent",percent);json.put("reason",reason);
            if(state==Status.READY)json.put("path",SubtitleGenerator.resolveRealPath(context,media));
            prefs.edit().putString(key(media,pack),json.toString()).apply();
        } catch(org.json.JSONException e) { throw new IllegalStateException(e); }
    }
    public Entry read(Uri media,CaptionPack pack) { return parse(prefs.getString(key(media,pack),"")); }
    public Map<String,Entry> snapshot(CaptionPack pack,java.util.List<FolderBrowserAdapter.BrowserItem> items) {
        Map<String,Entry> result=new HashMap<>();
        for(FolderBrowserAdapter.BrowserItem item:items)if(item.uri!=null) {
            Entry e=read(item.uri,pack);if(e!=null)result.put(item.uri.toString(),e);
        }
        return result;
    }
    private static Entry parse(String value) {
        try {
            JSONObject json=new JSONObject(value);Entry e=new Entry();e.token=json.getString("token");
            e.status=Status.valueOf(json.getString("state"));e.percent=json.optInt("percent");e.reason=json.optString("reason");return e;
        } catch(Exception e) { return null; }
    }
    public void reconcileReady() {
        synchronized(CaptionWorkStore.class) {
            for(Map.Entry<String,?> entry:prefs.getAll().entrySet())if(entry.getValue() instanceof String) {
                try {
                    JSONObject json=new JSONObject((String)entry.getValue());
                    if(!Status.READY.name().equals(json.optString("state")) || !json.has("uri"))continue;
                    Uri media=Uri.parse(json.getString("uri"));CaptionPack pack=CaptionPack.find(json.getString("pack"));
                    String path=json.optString("path",null);
                    if(!CaptionLibrary.saved(context,media,pack).isFile() && !SubtitleGenerator.getCachedSubtitleFile(context,media,path,pack).isFile()) {
                        update(media,pack,json.getString("token"),Status.CANCELLED,0,"Cached subtitles expired; generate again");
                    }
                }catch(Exception ignored) { }
            }
        }
    }
    public void forget(Uri media) {
        SharedPreferences.Editor edit=prefs.edit();for(CaptionPack pack:CaptionPack.ALL)edit.remove(key(media,pack));edit.apply();
    }
    public void blockDeletion(Uri media,boolean blocked) { prefs.edit().putBoolean("deleting:"+media,blocked).apply(); }
    public boolean isDeleting(Uri media) { return prefs.getBoolean("deleting:"+media,false); }
    public void recoverInterrupted() {
        SharedPreferences.Editor edit=prefs.edit();
        for(Map.Entry<String,?> item:prefs.getAll().entrySet())if(item.getKey().startsWith("deleting:")) { edit.remove(item.getKey()); } else if(item.getValue() instanceof String) {
            Entry e=parse((String)item.getValue());
            if(e!=null && (e.status==Status.GENERATING || e.status==Status.QUEUED)) {
                try { JSONObject j=new JSONObject((String)item.getValue());j.put("state",Status.CANCELLED.name());j.put("reason","App closed before completion");edit.putString(item.getKey(),j.toString()); }
                catch(org.json.JSONException ignored) { }
            }
        }
        edit.apply();
    }
}
