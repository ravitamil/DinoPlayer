# Third-party notices and source references

The root Unlicense applies to DinoPlayer application source derived from Just Player. It does not replace any component licence below. Copies are in `LICENSES/` and in APK assets under `licenses/`.

| Component | Use / version | Licence and source |
| --- | --- | --- |
| Just (Video) Player | Base application; inherited whistlingwoods checkout history | Unlicense; [moneytoo/Player](https://github.com/moneytoo/Player), [whistlingwoods/JustPlayer](https://github.com/whistlingwoods/JustPlayer) |
| ExoPlayer / AndroidX / Material / Kotlin | Playback 2.14.1 and Android framework libraries, pinned in Gradle | Apache-2.0; [ExoPlayer r2.14.1](https://github.com/google/ExoPlayer/tree/r2.14.1), [AndroidX](https://android.googlesource.com/platform/frameworks/support/), [Material](https://github.com/material-components/material-components-android), [Kotlin](https://github.com/JetBrains/kotlin) |
| ExoPlayer FFmpeg and AV1 extensions | Upstream prebuilt AARs in app/libs; decoder list/build reference in app/libs/README.md | ExoPlayer code Apache-2.0; FFmpeg keeps its LGPL licence, and native codecs keep theirs. [FFmpeg extension build source](https://github.com/google/ExoPlayer/tree/r2.14.1/extensions/ffmpeg), [AV1 extension](https://github.com/google/ExoPlayer/tree/r2.14.1/extensions/av1) |
| FFmpegKit community rebuild | io.github.maitrungduc1410:ffmpeg-kit-min:6.0.0 | Native FFmpeg/FFmpegKit is LGPL-3.0; [source and build scripts](https://github.com/maitrungduc1410/ffmpeg-kit), [original source](https://github.com/arthenica/ffmpeg-kit), [FFmpeg n6.0 source](https://github.com/FFmpeg/FFmpeg/tree/n6.0). The Maven POM labels its wrapper MIT; that label does not relicense the bundled LGPL code. The non-GPL `min` package is used. |
| Sherpa-ONNX | Official v1.13.8 Android runtime; downloaded by bootstrap, SHA-256 pinned | Apache-2.0; [source, build scripts and notices](https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8), [official runtime asset](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8) |
| ONNX Runtime | Transitive native inference runtime inside Sherpa | MIT; [source and third-party notices](https://github.com/microsoft/onnxruntime) |
| Whisper models and English graph transform | Optional tiny.en/base model packs and small derived decoder graph assets | MIT; [OpenAI Whisper source, weights and licence](https://github.com/openai/whisper); pinned ONNX model provenance is recorded in CaptionPack.java and tools/README.md. |
| Silero VAD | Optional speech-pack VAD model | MIT; [Silero VAD](https://github.com/snakers4/silero-vad) |
| DoubleTapPlayerView | Adapted gesture/view code and resources in dtpv | MIT, Viktor Krez (vkay94); [source](https://github.com/vkay94/DoubleTapPlayerView) |
| Nunito | Font files retained from earlier DinoPlayer UI; default UI now uses Android sans-serif | SIL Open Font License 1.1; [source/licence](https://github.com/google/fonts/tree/main/ofl/nunito) |
| ICU4J | com.ibm.icu:icu4j:69.1 | Unicode/ICU licence; [source and licence](https://github.com/unicode-org/icu/tree/release-69-1) |
| TapTargetView | com.getkeepsafe.taptargetview:1.13.2 | Apache-2.0; [source](https://github.com/KeepSafe/TapTargetView) |
| android-file-chooser | com.github.hedzr:android-file-chooser:v1.2.0-final | Apache-2.0; [source](https://github.com/hedzr/android-file-chooser) |

Native dependency distributions can include additional transitive notices. Their source trees and embedded notices remain authoritative. No speech-model training or exact real-time Hindi translation capability is claimed.

## Rebuilding and replacing dependencies

DinoPlayer is distributed as source with Gradle build files; there is no licence restriction against modifying it or rebuilding with replacement libraries. Install the pinned runtime with `python tools/bootstrap_runtime.py`, or rebuild the matching upstream runtime from its linked source. The official bootstrap AAR SHA-256 is `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96` (50,129,134 bytes).

The source/build references above provide upstream FFmpeg, FFmpegKit, ExoPlayer codec extensions and Sherpa implementations. If you redistribute changed native binaries, retain the corresponding licences/notices and provide the matching source/build changes. Full model inputs are intentionally excluded from Git; the pinned vendor revisions and byte-transform reproduction scripts are available under tools/.
