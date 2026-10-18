package com.brouken.player;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Streaming verification and a pinned byte transform; no full ONNX model is loaded into Java memory. */
public final class VerifiedModelFiles {
    private VerifiedModelFiles() { }

    public static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[65536];
                int n;
                while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n);
            }
            StringBuilder result = new StringBuilder();
            for (byte b : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static void verify(File file, long length, String hash) throws IOException {
        if (!file.isFile() || file.length() != length || !sha256(file).equals(hash)) {
            throw new IOException("Subtitle package failed its integrity check. Please download it again.");
        }
    }

    public static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[65536];
        int n;
        while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
    }

    public static void patch(File original, File output, long start, long end,
                             InputStream head, InputStream tail) throws IOException {
        if (start < 0 || end < start || end > original.length()) throw new IOException("Invalid model patch");
        try (RandomAccessFile source = new RandomAccessFile(original, "r");
             FileOutputStream target = new FileOutputStream(output)) {
            copy(head, target);
            source.seek(start);
            byte[] buffer = new byte[65536];
            long left = end - start;
            while (left > 0) {
                int n = source.read(buffer, 0, (int) Math.min(left, buffer.length));
                if (n < 0) throw new EOFException("Incomplete speech model");
                target.write(buffer, 0, n);
                left -= n;
            }
            copy(tail, target);
            target.getFD().sync();
        }
    }
}
