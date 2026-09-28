# Aftercast privacy policy

Aftercast is a podcast player for Android. It does not have an Aftercast account, it does not show ads, and it does not include an analytics or crash-reporting service.

## What stays on the phone

Subscriptions, episode files, playback position, settings, and transcripts are stored on the device.

Transcription runs only when you choose it on a downloaded episode. The audio is read on the phone and is not uploaded to be transcribed. The transcript is saved beside that download.

The speech model is not in the install file. The first transcription downloads the Whisper English model selected in Playback settings and keeps it on the phone. The standard choice is small English, about 375 MB. A faster model is about 160 MB and a larger one is about 900 MB. The files come from Hugging Face. That download is an ordinary file download: Hugging Face can see the requesting IP address. The episode audio is not part of that request.

A crash log, if one exists, stays on the phone until you copy it yourself from the bug-report screen. Aftercast does not send it anywhere.

## What the app contacts when you use it

Refreshing a podcast, downloading an episode, or loading cover art contacts the servers named by that podcast. Those servers see the request and your IP address. Some feeds are served over plain HTTP. The app also trusts certificates you have installed on the phone, so a feed can be inspected with a debugging proxy.

Searching the directory contacts the directory you pick, such as Podcast Index or the iTunes search. That service sees the search and your IP address.

Synchronization is off until you turn it on and enter a server, username, and password for gpodder.net or a Nextcloud server you choose. Aftercast does not run that server. The app sends those credentials, and your subscription and playback state, only to that server. The password is stored on the phone and is excluded from Android backup. An optional proxy password, if you set one, is stored with the rest of the app settings and can be included in your own Android backup.

Sharing a podcast shares its title and feed address. It does not send the episode to Aftercast. There is no Aftercast server.

## Children

Aftercast is not directed at children.

## Source

Aftercast is free software under GPL-3.0. Source: https://github.com/mohuddle/aftercast

## Contact

Questions about this policy: open an issue at https://github.com/mohuddle/aftercast/issues
