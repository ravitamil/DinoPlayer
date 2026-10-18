# Offline subtitle packs

DinoPlayer 0.39 bundles the player/runtime and a small English decoder transform, not full speech models. The model reference files are kept outside Android assets in `tools/model-source/`. Fresh installations download the user-selected pack in Settings. Existing verified English models from 0.38 are migrated locally without a download.

## Packages

- English: Whisper tiny.en int8, 104,271,045 download bytes including VAD/tokens. Its decoder is transformed to the same attention-output graph previously verified against the eight-second English sample. The final SHA-256 is `09e547cbcfe69b9f8ca7e4df4ac9d5b20e08af923537b77efd5ce895764c25d5`.
- Hindi → English: multilingual Whisper base int8, 161,253,144 download bytes including VAD/tokens, `language=hi`, `task=translate`. No ML Kit layer is required. This is experimental until Hindi/Hinglish quality, heat, and throughput are checked on the user's device.

Whisper translation words do not align one-to-one with the source Hindi words. The Java binding in the bundled Sherpa AAR does not expose native segment-text timestamps. Hindi therefore uses VAD-anchored recognition spans capped at four seconds and readable phrase pages within those spans. English retains cross-attention token alignment. Do not claim precise translated-word alignment or guaranteed real-time recognition.

`CaptionPack.java` pins model repository revisions, exact sizes and SHA-256 digests. Android Download Manager handles background transfer/retry; opening Settings reconciles completed jobs after process death. The installer validates all files in a staging directory, applies the English byte transform with streaming I/O, validates the output hash and activates the entire directory atomically. Cancellation removes only that attempt's downloads. An active recognition lease prevents package removal. An interrupted verification can be retried on the next reconciliation. Recognition runs offline after installation.

The downloaded base decoder is the multilingual model, not `base.en`. The catalog identity, task, language and hashes are included in caption-cache keys. Never silently reuse a different-language cache.

## Reproduce the English transform

The complete model files are reference inputs, never APK assets. Install `onnx` in an isolated Python environment only when re-exporting the graph:

```powershell
python tools/prepare_whisper_alignment.py tools/model-source/tiny.en-decoder.int8.onnx tools/model-source/aligned-english-assets/tiny.en-decoder.attention.int8.onnx
python tools/prepare_caption_download_patch.py tools/model-source/tiny.en-decoder.int8.onnx tools/model-source/aligned-english-assets/tiny.en-decoder.attention.int8.onnx app/src/main/assets/caption_patches
```

The transform consists of a small graph prefix/suffix and a bounded copy range from the verified vendor decoder. `VerifiedModelFiles` never loads a whole model into Java memory. Changing the catalog or transform requires updating its hashes/version and checking byte-for-byte reconstruction before shipping.

Sources:

- [Sherpa model catalog](https://k2-fsa.github.io/sherpa/onnx/spoken-language-identification/pretrained_models.html)
- [Sherpa v1.13.8 attention exporter](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/scripts/whisper/export-onnx-with-attention.py)
- [Sherpa decoder and translation contract](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/offline-whisper-greedy-search-decoder.cc)
- [OpenAI alignment heads](https://github.com/openai/whisper/blob/main/whisper/__init__.py)

## Validation

Run `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` with a compatible JDK and one worker. Packaging verification must assert that the APK contains no `.onnx` files and no `assets/sherpa/` entries. Test package download, interruption, cancellation, migration, model removal and translation quality manually on the device before release. No device/emulator testing was requested for 0.39.
