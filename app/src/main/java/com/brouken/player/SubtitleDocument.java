package com.brouken.player;

import java.io.*;
import java.util.*;

/** Bounded SRT/VTT editing model shared by preview, saving and document export. */
public final class SubtitleDocument {
    public static final int MAX_CUES=20000;
    private SubtitleDocument() { }
    public static List<SubtitleTimeline.Cue> read(Reader input) throws IOException {
        BufferedReader reader=new BufferedReader(new LimitedReader(input));
        List<SubtitleTimeline.Cue> cues=new ArrayList<>();
        String line;int characters=0;
        while((line=reader.readLine())!=null) {
            characters+=line.length();if(characters>8*1024*1024)throw new IOException("Subtitle file is too large to edit");
            line=line.replace("\uFEFF","").trim();
            if(line.startsWith("NOTE") || line.equals("STYLE") || line.equals("REGION")) {
                while((line=reader.readLine())!=null && !line.trim().isEmpty()) {
                    characters+=line.length();if(characters>8*1024*1024)throw new IOException("Subtitle file is too large");
                }
                continue;
            }
            if(!line.contains("-->"))continue;
            String[] times=line.split("-->");
            if(times.length!=2)throw new IOException("Invalid subtitle timing");
            long start=parseTime(times[0].trim()),end=parseTime(times[1].trim().split("\\s+")[0]);
            StringBuilder text=new StringBuilder();
            while((line=reader.readLine())!=null && !line.trim().isEmpty()) {
                characters+=line.length();if(characters>8*1024*1024 || text.length()+line.length()>16384)throw new IOException("Caption text is too large");
                if(text.length()>0)text.append('\n');text.append(line);
            }
            if(text.length()>0) {
                if(cues.size()>=MAX_CUES)throw new IOException("Too many captions to edit");
                try { cues.add(new SubtitleTimeline.Cue(start,end,text.toString())); }
                catch(IllegalArgumentException e) { throw new IOException("Caption end must follow its start",e); }
            }
        }
        if(cues.isEmpty())throw new IOException("No SRT or VTT captions found");
        Collections.sort(cues,(a,b)->Long.compare(a.startMs,b.startMs));
        validate(cues);
        return cues;
    }
    private static final class LimitedReader extends FilterReader {
        private int remaining=8*1024*1024;
        LimitedReader(Reader input) { super(input); }
        @Override public int read(char[] buffer,int offset,int count) throws IOException {
            if(count==0)return 0;
            if(remaining==0) { if(super.read()==-1)return -1;throw new IOException("Subtitle file is too large to edit"); }
            int read=super.read(buffer,offset,Math.min(count,remaining));if(read>0)remaining-=read;return read;
        }
        @Override public int read() throws IOException {
            char[] one=new char[1];int count=read(one,0,1);return count<0?-1:one[0];
        }
    }
    public static long parseTime(String value) throws IOException {
        String[] parts=value.trim().replace(',','.').split(":");
        if(parts.length<2 || parts.length>3)throw new IOException("Use hh:mm:ss.mmm or mm:ss.mmm");
        try {
            long hours=parts.length==3?Long.parseLong(parts[0]):0;
            long minutes=Long.parseLong(parts[parts.length-2]);
            String[] seconds=parts[parts.length-1].split("\\.");
            if(seconds.length!=2 || !seconds[1].matches("[0-9]{1,3}"))throw new NumberFormatException();
            long sec=Long.parseLong(seconds[0]);
            long milli=Long.parseLong((seconds[1]+"00").substring(0,3));
            if(hours<0 || hours>100000 || minutes<0 || minutes>=60 || sec<0 || sec>=60)throw new NumberFormatException();
            return ((hours*60+minutes)*60+sec)*1000+milli;
        } catch(NumberFormatException e) { throw new IOException("Invalid caption time",e); }
    }
    public static void validate(List<SubtitleTimeline.Cue> cues) throws IOException {
        long previousStart = -1;
        for (SubtitleTimeline.Cue cue : cues) {
            if (cue.startMs < 0 || cue.endMs <= cue.startMs) {
                throw new IOException("Caption end must follow its start");
            }
            if (cue.startMs < previousStart) {
                throw new IOException("Captions must be sorted by start time");
            }
            previousStart = cue.startMs;
        }
    }
    public static List<SubtitleTimeline.Cue> shifted(List<SubtitleTimeline.Cue> cues,long offset) throws IOException {
        if(offset < -86400000L || offset>86400000L)throw new IOException("Timing offset must be within 24 hours");
        List<SubtitleTimeline.Cue> result=new ArrayList<>();
        for(SubtitleTimeline.Cue cue:cues)if(cue.endMs+offset>0)result.add(new SubtitleTimeline.Cue(Math.max(0,cue.startMs+offset),cue.endMs+offset,cue.text));
        if(result.isEmpty())throw new IOException("This offset moves every caption before the video starts");
        validate(result);return result;
    }
    public static void write(Writer output,List<SubtitleTimeline.Cue> cues,boolean vtt) throws IOException {
        validate(cues);
        if(vtt)output.write("WEBVTT\n\n");
        for(int i=0;i<cues.size();i++) {
            SubtitleTimeline.Cue cue=cues.get(i);
            if(!vtt)output.write((i+1)+"\n");
            String start=SubtitleTimeline.timestamp(cue.startMs),end=SubtitleTimeline.timestamp(cue.endMs);
            output.write((vtt?start.replace(',','.'):start)+" --> "+(vtt?end.replace(',','.'):end)+"\n"+cue.text+"\n\n");
        }
        output.flush();
    }
}
