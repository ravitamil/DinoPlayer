package com.brouken.player;

/** Immutable, pinned catalog: the app never accepts executable code or arbitrary model URLs. */
public final class CaptionPack {
    public static final long ALIGNED_DECODER_BYTES = 89854537;
    public static final String ALIGNED_DECODER_SHA256 = "09e547cbcfe69b9f8ca7e4df4ac9d5b20e08af923537b77efd5ce895764c25d5";
    public static final class Asset {
        public final String name, url, sha256;
        public final long bytes;
        Asset(String name, String url, long bytes, String sha256) {
            this.name = name; this.url = url; this.bytes = bytes; this.sha256 = sha256;
        }
    }

    private static final String EN = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/d026532c022fa99fd789d6b32446a1df7b6bfc43/";
    private static final String HI = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/bb53ee204431c90d314c1cc08d28d23e5b7927cc/";
    public static final Asset VAD = new Asset("vad.onnx", "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
            643854, "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6");
    public static final CaptionPack ENGLISH = new CaptionPack("english-tiny-aligned-v1", "English captions", "en", "transcribe", true,
            new Asset("encoder.onnx", EN + "tiny.en-encoder.int8.onnx", 12937772, "0ce578b827c94a961aacb8fa14b02f096504b337e5c94be37c36238cbe3e8bc6"),
            new Asset("decoder.onnx", EN + "tiny.en-decoder.int8.onnx", 89853865, "06c0e6ff6348d427e51839219d1c886c18cfdf411e629e33f5e1679bff9c1527"),
            new Asset("tokens.txt", EN + "tiny.en-tokens.txt", 835554, "306cd27f03c1a714eca7108e03d66b7dc042abe8c258b44c199a7ed9838dd930"), VAD);
    public static final CaptionPack HINDI = new CaptionPack("hindi-base-translate-v1", "Hindi → English", "hi", "translate", false,
            new Asset("encoder.onnx", HI + "base-encoder.int8.onnx", 29120534, "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11"),
            new Asset("decoder.onnx", HI + "base-decoder.int8.onnx", 130672026, "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d"),
            new Asset("tokens.txt", HI + "base-tokens.txt", 816730, "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"), VAD);
    public static final CaptionPack[] ALL = {ENGLISH, HINDI};
    public final String id, title, language, task;
    public final boolean aligned;
    public final Asset[] assets;

    private CaptionPack(String id, String title, String language, String task, boolean aligned, Asset... assets) {
        this.id = id; this.title = title; this.language = language; this.task = task;
        this.aligned = aligned; this.assets = assets;
    }

    public long downloadBytes() {
        long total = 0;
        for (Asset asset : assets) total += asset.bytes;
        return total;
    }

    public String cacheIdentity() {
        StringBuilder identity = new StringBuilder(id).append(':').append(language).append(':').append(task);
        if (aligned) identity.append(':').append(ALIGNED_DECODER_SHA256);
        for (Asset asset : assets) identity.append(':').append(asset.sha256);
        return identity.toString();
    }

    public static CaptionPack find(String id) {
        for (CaptionPack pack : ALL) if (pack.id.equals(id)) return pack;
        return ENGLISH;
    }
}
