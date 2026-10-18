# DinoPlayer

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" alt="DinoPlayer green app icon">

An Android video player with offline, on-device subtitle creation, a searchable local library, and a focused set of playback controls.

[Download the APK](https://github.com/ravitamil/DinoPlayer/releases/tag/v0.43) · [Portfolio](https://ravitamil.github.io/#dinoplayer) · [Changelog](CHANGELOG.md) · [Third-party notices](THIRD_PARTY_NOTICES.md)

**DinoPlayer is an enhanced derivative of [Just Player](https://github.com/moneytoo/Player), based on the [whistlingwoods/JustPlayer](https://github.com/whistlingwoods/JustPlayer) checkout.** Upstream history, authorship and the Unlicense are preserved. DinoPlayer changes are maintained by [Ravikumar Tamilmani](https://github.com/ravitamil), with AI-assisted implementation and verification.

## Features

- Folder library, video search, watch-progress bars and last-watched highlighting.
- Portrait/landscape playback, seeking, speed and track selection, screen lock, fit/zoom and picture-in-picture.
- Optional offline speech packs: English captions and experimental Hindi audio → English translation. Recognition stays on the device after the pack is installed; the app downloads packs only on demand.
- Incremental captions synchronized with the video clock, saved completed captions, resumable chunk checkpoints, Cancel and Retry.
- Optional background generation for newly discovered videos, with Queued / Generating / Ready / Failed / Cancelled status. Android controls background scheduling.
- Subtitle text/timing editing, global timing shift, SRT/VTT export and explicit regeneration.
- Shared subtitle appearance: text size/colour, backing opacity, outline, bottom spacing, Android accessibility style, live preview and reset.
- Video-file deletion with Android/provider approval where required and a separate choice for deleting DinoPlayer subtitle data.
- Bounded thumbnail/subtitle caches and background recognition that yields to playback.

## Install

Android 5.0+ (API 21). Download **DinoPlayer-0.43-debug.apk** from [Releases](https://github.com/ravitamil/DinoPlayer/releases/tag/v0.43), allow installation from your browser/file manager, and open the app. Package: `app.dino.player`.

The initial release is a **debug-signed preview**, not a Play Store release. Keep the same signing identity for in-place upgrades; no signing key or credentials are committed. The current build retains an older Android toolchain/target SDK and broad optional file-access functionality inherited/extended for local media browsing.

To create captions, open Settings and install the desired offline pack, then use the player’s subtitle menu. Appearance changes do not regenerate captions. Generated captions are disposable cache data; saved edits and exported files are separate. Completed captions can need regeneration after cache expiry/cleanup or a media/language change.

## Build from source

Prerequisites: **JDK 17**, Python 3.9+, Android SDK Platform 30 and Build Tools 30.0.2, internet access for the first dependency download. The checked-in wrapper is Gradle 7.2 and Android Gradle Plugin 4.1.3.

```sh
python tools/bootstrap_runtime.py
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=1
```

On Windows use `gradlew.bat`. Set `ANDROID_HOME`/`ANDROID_SDK_ROOT` or an untracked `local.properties` (`sdk.dir=...`). Output: `app/build/outputs/apk/debug/DinoPlayer.v0.43-debug.apk`.

`bootstrap_runtime.py` downloads only the pinned Sherpa-ONNX Android runtime AAR, checks the official release size and SHA-256, and activates it atomically. Full speech models are not stored in Git or bundled in the APK. The small verified English decoder transform is in Android assets; reproduction details are in [tools/README.md](tools/README.md).

The native ExoPlayer FFmpeg/AV1 extensions inherited from the upstream project remain in `app/libs/`. The runtime and model sources, licences and build references are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Validation and limitations

- Version 0.43: **49 host unit tests passed**, Android lint reported **zero errors** (warnings remain), APK identity/signature/ZIP integrity checked, and installation/version verified on one connected Android phone.
- These checks do not prove every Android UI flow, storage provider, codec/device, download failure, or background scheduling path. See [manual checks](docs/MANUAL_CHECKS.md).
- Hindi/Hinglish translation quality, heat, latency and throughput need device validation. Recognition is not guaranteed to keep up with playback. Translated phrase timing is approximate, not precise translated-word alignment.
- Bitmap subtitles retain their own artwork. The appearance controls apply to text subtitles.
- No crash telemetry or performance benchmark results are claimed.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Bug reports should include app version, Android/device version, reproducible steps, the selected subtitle pack, and sanitized logs. Do not upload private video, credentials, or personal media without permission.

## Licence and credits

DinoPlayer source follows the inherited [Unlicense](LICENSE). This does **not** relicense third-party libraries, speech models or fonts; their licences and notices remain applicable. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [NOTICE](NOTICE).
