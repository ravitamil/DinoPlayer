package com.brouken.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Media-time cues shared by incremental display and the completed SRT. */
public final class SubtitleTimeline {
    public static final class Cue {
        public final long startMs;
        public final long endMs;
        public final String text;

        public Cue(long startMs, long endMs, String text) {
            if (startMs < 0 || endMs <= startMs || text == null || text.trim().isEmpty()) {
                throw new IllegalArgumentException("Invalid subtitle cue");
            }
            this.startMs = startMs;
            this.endMs = endMs;
            this.text = text;
        }
    }

    private final List<Cue> cues = new ArrayList<>();

    public void addAll(List<Cue> batch) {
        if(batch.isEmpty())return;
        boolean ordered=true;
        long last=cues.isEmpty()?-1:cues.get(cues.size()-1).startMs;
        for(Cue cue:batch) { if(cue.startMs<last) { ordered=false;break; }last=cue.startMs; }
        cues.addAll(batch);
        if(!ordered)Collections.sort(cues,(first,second)->Long.compare(first.startMs,second.startMs));
    }

    public Cue at(long positionMs) {
        int low = 0;
        int high = cues.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            Cue cue = cues.get(mid);
            if (positionMs < cue.startMs) high = mid - 1;
            else if (positionMs >= cue.endMs) low = mid + 1;
            else return cue;
        }
        return null;
    }

    public List<Cue> snapshot() {
        return new ArrayList<>(cues);
    }

    public void clear() {
        cues.clear();
    }

    public boolean isEmpty() {
        return cues.isEmpty();
    }

    public static List<Cue> fromRecognition(String text, String[] tokens, float[] times,
                                             float[] durations, long offsetMs, long lengthMs) {
        if (text == null || text.trim().isEmpty() || lengthMs <= 0) return Collections.emptyList();
        List<Cue> result = new ArrayList<>();
        if (!validTimes(tokens, times, lengthMs)) {
            // Recognition inputs are bounded to four seconds; never spread a whole video over one cue.
            addWrapped(result, text.trim(), offsetMs, offsetMs + lengthMs);
            return result;
        }
        StringBuilder phrase = new StringBuilder();
        long start = 0;
        long end = 0;
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token == null || token.startsWith("<|")) continue;
            long time = Math.min(lengthMs - 1, Math.max(0, Math.round(times[i] * 1000)));
            boolean wordBoundary = !token.isEmpty() && Character.isWhitespace(token.charAt(0));
            if (phrase.length() > 0 && wordBoundary
                    && (phrase.length() + token.length() > 60 || time - start >= 3000)) {
                addWrapped(result, phrase.toString().trim(), offsetMs + start, offsetMs + Math.max(start + 1, time));
                phrase.setLength(0);
            }
            if (phrase.length() == 0) start = time;
            phrase.append(token);
            long tokenEnd = i + 1 < times.length ? Math.round(times[i + 1] * 1000) : lengthMs;
            if (durations != null && i < durations.length && finite(durations[i]) && durations[i] > 0) {
                tokenEnd = time + Math.round(durations[i] * 1000);
            }
            end = Math.min(lengthMs, Math.max(time + 1, tokenEnd));
        }
        if (phrase.length() > 0) addWrapped(result, phrase.toString().trim(), offsetMs + start,
                offsetMs + Math.max(start + 1, end));
        return result;
    }

    private static boolean validTimes(String[] tokens, float[] times, long lengthMs) {
        if (tokens == null || times == null || tokens.length == 0 || tokens.length != times.length) return false;
        float previous = -1;
        for (float time : times) {
            if (!finite(time) || time < 0 || time < previous || time * 1000 > lengthMs + 200) return false;
            previous = time;
        }
        return times.length == 1 || times[times.length - 1] > times[0];
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static void addWrapped(List<Cue> result, String text, long start, long end) {
        if (text.isEmpty() || end <= start) return;
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (line.length() > 0 && line.length() + word.length() + 1 > 32) {
                lines.add(line.toString());
                line.setLength(0);
            }
            while (word.length() > 32) {
                lines.add(word.substring(0, 32));
                word = word.substring(32);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        int pages = (lines.size() + 1) / 2;
        for (int i = 0; i < pages; i++) {
            String page = lines.get(i * 2);
            if (i * 2 + 1 < lines.size()) page += "\n" + lines.get(i * 2 + 1);
            long pageStart = start + (end - start) * i / pages;
            long pageEnd = start + (end - start) * (i + 1) / pages;
            if (pageEnd > pageStart) result.add(new Cue(pageStart, pageEnd, page));
        }
    }

    public static String timestamp(long millis) {
        millis = Math.max(0, millis);
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", millis / 3600000,
                millis / 60000 % 60, millis / 1000 % 60, millis % 1000);
    }
}
