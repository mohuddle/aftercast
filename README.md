# Aftercast

![Aftercast icon](ui/common/src/main/res/mipmap-xxxhdpi/ic_launcher_teal.png)

**Download once. Read later.**

> A [Mobitecture](https://github.com/mohuddle) app · *apps, architected.*

[![License](https://img.shields.io/badge/License-GPL--3.0-22c55e?style=for-the-badge)](LICENSE)
[![Android](https://img.shields.io/badge/Android-6.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/about/versions/marshmallow)
[![API](https://img.shields.io/badge/Target%20API-36-3DDC84?style=for-the-badge)](https://developer.android.com/about/versions)
[![ABI](https://img.shields.io/badge/ABI-arm64--v8a-555555?style=for-the-badge)](app/build.gradle)
[![Privacy](https://img.shields.io/badge/Privacy-on--device%20transcription-black?style=for-the-badge)](PRIVACY.md)

---

## 📌 Overview

**Aftercast** is a podcast player for Android. Subscribe to a show, download an episode, and play it. When you want that episode as text, choose Transcribe. The words are written on the phone while you listen, and you can read what is already finished.

This project is derived from [AntennaPod](https://github.com/AntennaPod/AntennaPod) 3.12.2. AntennaPod is a separate project. The name, application id (`app.aftercast`), and launcher icon are Aftercast's.

Aftercast has not been submitted to the Play Store. Publish it with an Aftercast signing key and an Aftercast Play Console account.

---

## Studio

Aftercast is made by [Mobitecture](https://github.com/mohuddle) — mobile apps, architected. Studio palette accents: Blueprint `#4E5CF0`, Copper `#EE9A5B`.

---

## 🔥 Key Features

### 🎧 1. Subscribe, download, and play

- Add a podcast from its feed address, or search a directory such as Podcast Index or iTunes.
- Download episodes and play them from the queue, including playback speed, a sleep timer, shownotes, and chapters.
- Sharing a podcast sends its title and feed address.

### 📝 2. Transcribe a downloaded episode

- Choose **Transcribe** on an episode you have already downloaded.
- A transcript the publisher already supplies is used as-is, and Transcribe stays hidden in that case.
- The audio is read on the phone. The episode file is not sent out to be transcribed.
- A notification shows download progress for the speech model, then progress through the episode.
- Cancel keeps the text already written. **Resume transcription** continues from there.

### 📖 3. Read along

- The transcript opens as one document. Long-press a word and drag to copy a range with the system selection menu.
- **Follow audio** underlines the sentence that is playing and scrolls to keep it in view. Scrolling the page turns follow off.
- Tap a sentence to seek to its start and play.

### ⏳ 4. Transcript progress on the player

- The player row is **Shownotes**, **Transcript**, and chapters when the episode has them.
- Transcript stays faded until a publisher transcript exists, the generated transcript is finished, or transcription has covered about half of the episode.
- The bar under the button matches the transcription notification.

### ⚙️ 5. Choose the speech model

- **Settings → Playback → Transcription model.** The choice applies the next time you transcribe.
- **Faster**, about 160 MB, for an older phone.
- **Standard**, about 375 MB. This is the default: Whisper small English.
- **Larger**, about 900 MB, for a closer read. It can be too heavy on an older phone while audio is playing.
- A model already downloaded stays on the phone. The install file does not contain the weights.

### 💾 6. Transcripts stay with the episode

- Each generated transcript is a JSON file beside the downloaded episode, named with `.aftercast.json`.
- Times are stored per sentence, in seconds, so follow and tap-to-seek use the times that were written.
- A finished job adds an empty `.aftercast.complete` marker next to that file.

### 🏠 7. Home screen and launcher

- Home keeps the name Aftercast and the tagline **Download once. Read later.**
- Up Next is a horizontal row of what to play next.
- **Settings → User interface → App icon** offers five launcher icons: Teal, Ember, Navy, Letterpress, and Dusk. Teal is the default.

---

## 📥 How to build and install

Aftercast is installed from a build of this repository. There is no Play listing yet.

1. Install JDK 21 and the Android SDK. This project compiles against API 36.
2. Clone the repository. Normal development does not need a git submodule.
3. Open the folder in Android Studio and run the `freeDebug` variant, or build a release APK:

```
./gradlew :app:assembleFreeRelease
```

4. The APK is `app/build/outputs/apk/free/release/app-free-release.apk`.
5. Install that APK on a 64-bit ARM phone. The application id is `app.aftercast`. Debug builds use `app.aftercast.debug`.

Release signing stays on the machine that builds the APK. `local.properties` and the keystore are not in this repository. Placeholder signing values in Gradle are not a release key.

Bug reports and pull requests belong in this repository. See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## 🛠️ Technical Specifications

- **Minimum Android version**: Android 6.0 (API 23). Phones from about the last five years are the intended fit for transcription.
- **Target Android version**: Android 16 (API 36)
- **Version**: 0.1.12 (versionCode 3120303)
- **Application id**: `app.aftercast`
- **ABI**: arm64-v8a only
- **Language**: Java
- **UI**: Android views, Material components, and view binding
- **Playback**: Media3
- **Database**: on-device SQLite for subscriptions, queue, and playback position
- **Speech library**: [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 1.13.8, Apache-2.0, vendored at `app/libs/sherpa-onnx-1.13.8.aar`. Source tag [v1.13.8](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8)
- **Speech models**: Whisper English int8, downloaded on first use from Hugging Face and checked with SHA-256. Standard model: [csukuangfj/sherpa-onnx-whisper-small.en](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small.en). Original Whisper is MIT ([openai/whisper](https://github.com/openai/whisper))
- **Recognizer settings**: English, greedy search, two CPU threads, segment timestamps
- **Transcript format**: `{"segments":[{"startTime","endTime","body","speaker"}]}` beside the episode file

A 50-minute episode can take longer than the episode itself on an older phone. Later episodes reuse the model download for the size you picked.

---

## 🔒 Privacy

The full policy is [PRIVACY.md](PRIVACY.md).

- Aftercast has no account, shows no ads, and includes no analytics or crash-reporting service.
- Subscriptions, episode files, playback position, settings, and transcripts stay on the phone.
- Transcription reads the downloaded audio on the phone. That audio is not part of any request.
- The speech-model download is a file request to Hugging Face. Hugging Face can see the requesting IP address.
- Refreshing a podcast, downloading an episode, loading cover art, or searching a directory contacts the server that show or directory names. Those servers see the request and your IP address.
- Synchronization is off until you turn it on and enter a gpodder.net or Nextcloud server you choose. Aftercast does not run that server.
- A crash log stays on the phone until you copy it from the bug-report screen.

---

## 🛡️ Security

- The release keystore is kept on the build machine. It is not committed, and this repository does not contain a Play service account.
- An optional sync password is stored on the phone and is excluded from Android backup.
- Crash logs are exported only when you copy them. The app does not send them.
- The free release is arm64-v8a, with no advertising ID.
- Some podcast feeds are published over plain HTTP. Aftercast can load those feeds, and it trusts certificates you have installed, so a feed can be inspected with a debugging proxy you configured.
- Source for this app is public under GPL-3.0: https://github.com/mohuddle/aftercast

---

## 📄 License

This project is licensed under **GPL-3.0**. The license text is in [LICENSE](LICENSE).

Aftercast is a modified version of AntennaPod 3.12.2, which is also GPL-3.0. AntennaPod's forum, issue tracker, Play listing, F-Droid listing, and Weblate project are for AntennaPod.

Other notices:

- sherpa-onnx 1.13.8 is Apache-2.0. The Android library is vendored in this tree because upstream gitignore skips `libs/`.
- The Whisper weights are not in this repository. They download at runtime. The original OpenAI Whisper model is MIT.
- Geist, Geist Mono, and Inter are SIL Open Font License 1.1. The credit files are `ui/preferences/src/main/assets/LICENSE_GEIST.txt` and `LICENSE_INTER.txt`.

The Java package namespace in this tree is still `de.danoeh.antennapod`. The installable application id is `app.aftercast`.

---

Download once. Read later.

---
Made by [Mobitecture](https://github.com/mohuddle) · apps, architected.
