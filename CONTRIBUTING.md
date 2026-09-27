How to report a bug
-------------------
- Before anything else, please make sure you are on the latest Aftercast build. The behavior you are seeing may already have changed.
- Search [open issues](https://github.com/mohuddle/aftercast/issues) and [closed issues](https://github.com/mohuddle/aftercast/issues?q=is%3Aissue+is%3Aclosed) before opening a new one.
- Describe the problem with as much detail as you can.
- Include the device, the Android version (`Settings → About Phone`), and the Aftercast version from the in-app settings screen.
- If the bug only happens with one podcast, include that podcast's URL.
- Include steps to reproduce the bug when you can.
- If the app crashes, attach a log. Aftercast can export logs from the bug-report screen.
- Please use the [bug report template](https://github.com/mohuddle/aftercast/issues/new?template=bug_report.yml).

This repository is Aftercast. AntennaPod's forum and issue tracker are for AntennaPod.


How to submit a feature request
-------------------------------
- Search existing issues first. If the same request is already open, comment there instead of opening a duplicate.
- One feature per issue.
- Explain the problem and how the feature solves it, including how the screen would behave if the request changes the UI.
- Please use the [feature request template](https://github.com/mohuddle/aftercast/issues/new?template=feature_request.yml).


Translating
-----------
There is no separate translation project for Aftercast. Change English strings only, in `ui/i18n/src/main/res/values/strings.xml`. Files under `values-*/` still contain AntennaPod translations and are not edited here.


Submit a pull request
---------------------
- Open the pull request against this repository (`mohuddle/aftercast`), based on `main`. Do not open it against AntennaPod.
- Comment on an existing issue when you start work, so two people do not write the same change.
- Keep the change focused. Do not upgrade dependencies or build tools unless the issue calls for that.
- Add unit tests when the change has a clear case to pin down.
- Please only change English string resources.
- Check style locally with `./gradlew checkstyle lint`.
- The checkstyle rules live in [config/checkstyle/checkstyle.xml](config/checkstyle/checkstyle.xml).
- Mention the related issue in the pull request text. [Special keywords](https://docs.github.com/en/issues/tracking-your-work-with-issues/linking-a-pull-request-to-an-issue) such as `Closes: #123` will close it when the pull request merges.


Building from source
--------------------
1. Fork this repository.
2. Clone your fork. You do not need any git submodule for normal development.
3. Open the folder in Android Studio and wait until Gradle finishes.
4. Run the `freeDebug` or `playDebug` variant. The application id is `app.aftercast` (`app.aftercast.debug` for debug builds).

From the command line, a release APK is:

```
./gradlew :app:assembleFreeRelease
```

### Unit tests

* `./gradlew testPlayDebugUnitTest`       # all projects
* `./gradlew :app:testPlayDebugUnitTest`  # the app module
