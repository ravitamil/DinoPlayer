# Contributing

1. Follow the source-build steps in README.md, including the pinned runtime bootstrap.
2. Keep changes focused and retain upstream attribution and third-party notices.
3. Run `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` with JDK 17 and one worker.
4. Check real-device behaviour for playback, cancellation, storage approval and caption timing when those paths change. Distinguish host tests from device evidence.
5. Keep full model files, SDK paths, signing keys, private videos, generated builds and personal logs out of commits.
6. Report experimental translation limitations accurately. Do not claim real-time performance without measuring it on the target phone.

Open a pull request describing the problem, resulting behaviour, tests run and remaining manual checks. Native library/model changes must update hashes, source references and applicable notices.
