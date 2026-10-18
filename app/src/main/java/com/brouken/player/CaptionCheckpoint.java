package com.brouken.player;

import java.io.*;
import java.util.*;

/** Append-only, bounded recovery journal. A torn last record is truncated before retry. */
public final class CaptionCheckpoint {
    private static final int MAGIC=0x44494E4F;
    public static final class Chunk {
        public final int index;public final List<SubtitleTimeline.Cue> cues;
        Chunk(int index,List<SubtitleTimeline.Cue> cues) { this.index=index;this.cues=cues; }
    }
    public static List<Chunk> read(File file,int chunkCount) throws IOException {
        List<Chunk> chunks=new ArrayList<>();if(!file.isFile())return chunks;
        try(RandomAccessFile input=new RandomAccessFile(file,"rw")) {
            if(input.length()>32L*1024*1024) { input.setLength(0);return chunks; }
            long good=0;Set<Integer> seen=new HashSet<>();
            while(input.getFilePointer()<input.length()) {
                try {
                    if(input.readInt()!=MAGIC)break;
                    int index=input.readInt(),count=input.readInt();
                    if(index<0 || index>=chunkCount || seen.contains(index) || count<0 || count>256)break;
                    List<SubtitleTimeline.Cue> cues=new ArrayList<>();
                    for(int i=0;i<count;i++)cues.add(new SubtitleTimeline.Cue(input.readLong(),input.readLong(),input.readUTF()));
                    if(input.readInt()!=MAGIC)break;
                    chunks.add(new Chunk(index,cues));seen.add(index);good=input.getFilePointer();
                }catch(IOException | IllegalArgumentException e) { break; }
            }
            input.setLength(good);
        }
        return chunks;
    }
    public static void append(File file,int index,List<SubtitleTimeline.Cue> cues) throws IOException {
        if(cues.size()>256 || file.length()>32L*1024*1024)throw new IOException("Subtitle recovery data is too large");
        try(FileOutputStream stream=new FileOutputStream(file,true);DataOutputStream output=new DataOutputStream(stream)) {
            output.writeInt(MAGIC);output.writeInt(index);output.writeInt(cues.size());
            for(SubtitleTimeline.Cue cue:cues) { output.writeLong(cue.startMs);output.writeLong(cue.endMs);output.writeUTF(cue.text); }
            output.writeInt(MAGIC);output.flush();stream.getFD().sync();
        }
    }
}
