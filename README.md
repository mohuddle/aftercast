# Aftercast

Download once. Read it later.

Aftercast is a podcast player for Android. It plays shows you have already downloaded, and it can transcribe a downloaded episode on the phone when you ask it to.

This project is derived from [AntennaPod](https://github.com/AntennaPod/AntennaPod) 3.12.2 and is licensed under GPL-3.0. AntennaPod is a separate project. The name, application id (`app.aftercast`), and launcher icon are Aftercast's. Do not present this app as AntennaPod.

## Transcription

Transcription runs only when you choose Transcribe on a downloaded episode. A publisher-supplied transcript is preferred, and that action stays hidden when one is already available. Audio stays on the phone.

The install file does not contain the speech model. The first transcription downloads Whisper small English (int8), about 375 MB, from [csukuangfj/sherpa-onnx-whisper-small.en](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small.en) and keeps it on the phone. Later episodes reuse that download. A 50-minute episode can take longer than the episode itself, especially on a phone several years old. Phones from about the last five years are the intended fit.

The recognizer is [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 1.13.8, licensed under Apache-2.0. The Android library is vendored at `app/libs/sherpa-onnx-1.13.8.aar` (arm64-v8a only). Source for that version is tag [v1.13.8](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8). Upstream's gitignore skips `libs/`, so that file is added on purpose. The Whisper weights are not in this repository. Those weights are OpenAI's Whisper small.en model, converted for sherpa-onnx, and the original model is under the MIT license ([openai/whisper](https://github.com/openai/whisper)).

## Building

```
./gradlew :app:assembleFreeRelease
```

The APK is `app/build/outputs/apk/free/release/app-free-release.apk`. Release signing stays on the machine that builds the APK. `local.properties` and the keystore are not in this repository.

Play Store copy lives in `store-metadata/listings/en-US/`. Aftercast has not been submitted to the Play Store. Do not publish it with AntennaPod's Play Console credentials. The privacy policy is [PRIVACY.md](PRIVACY.md).

## Based on AntennaPod

AntennaPod upstream is https://github.com/AntennaPod/AntennaPod. The Java package namespace in this tree is still `de.danoeh.antennapod`. The installable application id is `app.aftercast`.

The GPL-3.0 license text is in [LICENSE](LICENSE).

Bug reports and pull requests for Aftercast belong in this repository. AntennaPod's forum, issue tracker, Play listing, F-Droid listing, and Weblate project are for AntennaPod, not for Aftercast.
