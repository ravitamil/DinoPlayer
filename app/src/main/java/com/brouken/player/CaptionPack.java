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

    private static final String BASE_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/bb53ee204431c90d314c1cc08d28d23e5b7927cc/";
    public static final Asset VAD = new Asset("vad.onnx", "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
            643854, "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6");

    public static final CaptionPack MULTILINGUAL = new CaptionPack("whisper-base-multilingual-v1", "Multilingual Speech Engine (99 Languages)", "auto", "transcribe", false,
            new Asset("encoder.onnx", BASE_URL + "base-encoder.int8.onnx", 29120534, "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11"),
            new Asset("decoder.onnx", BASE_URL + "base-decoder.int8.onnx", 130672026, "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d"),
            new Asset("tokens.txt", BASE_URL + "base-tokens.txt", 816730, "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"), VAD);

    // Compatibility references
    public static final CaptionPack ENGLISH = MULTILINGUAL;
    public static final CaptionPack HINDI = MULTILINGUAL;

    public static final CaptionPack[] ALL = {MULTILINGUAL};
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
        if (id == null) return MULTILINGUAL;
        for (CaptionPack pack : ALL) if (pack.id.equals(id)) return pack;
        if ("hindi-base-translate-v1".equals(id) || "english-tiny-aligned-v1".equals(id)) return MULTILINGUAL;
        return MULTILINGUAL;
    }
}
