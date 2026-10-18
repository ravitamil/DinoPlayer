package com.brouken.player;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.util.*;

/** Edited captions are user data, kept outside disposable caches until explicitly deleted. */
public final class CaptionLibrary {
    private CaptionLibrary() { }
    public static File saved(Context context,Uri media,CaptionPack pack) {
        return new File(context.getNoBackupFilesDir(),"edited-captions/"+CaptionWorkStore.key(media,pack)+".srt");
    }
    public static boolean isSaved(Context context,Uri subtitle) {
        return subtitle!=null && "file".equals(subtitle.getScheme()) && subtitle.getPath()!=null
            && new File(context.getNoBackupFilesDir(),"edited-captions").equals(new File(subtitle.getPath()).getParentFile());
    }
    public static File find(Context context,Uri media,String path) {
        CaptionPack pack=CaptionPackages.get(context).selected();
        File edited=saved(context,media,pack);if(edited.isFile())return edited;
        File cached=SubtitleGenerator.getCachedSubtitleFile(context,media,path,pack);if(cached.isFile() && cached.length()>0) { cached.setLastModified(System.currentTimeMillis());return cached; }
        return SubtitleGenerator.findLocalSubtitle(path);
    }
    public static void save(Context context,Uri media,CaptionPack pack,List<SubtitleTimeline.Cue> cues) throws IOException {
        File target=saved(context,media,pack);
        if(!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs())throw new IOException("Cannot save edited captions");
        File temporary=new File(target.getPath()+".tmp");
        try(FileOutputStream stream=new FileOutputStream(temporary);Writer writer=new OutputStreamWriter(stream,java.nio.charset.StandardCharsets.UTF_8)) {
            SubtitleDocument.write(writer,cues,false);writer.flush();stream.getFD().sync();
        }
        if(!temporary.renameTo(target))throw new IOException("Could not save edited captions");
    }
    public static void removeSaved(Context context,Uri media,CaptionPack pack) { saved(context,media,pack).delete(); }
    public static List<File> associated(Context context,Uri media,String path) {
        List<File> result=new ArrayList<>();
        for(CaptionPack pack:CaptionPack.ALL) {
            result.add(saved(context,media,pack));
            File cached=SubtitleGenerator.getCachedSubtitleFile(context,media,path,pack);
            result.add(cached);result.add(new File(cached.getPath()+".partial"));
        }
        return result;
    }
}
