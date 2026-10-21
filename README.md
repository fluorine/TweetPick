# TweetPick (alpha)

Android image picker that captures tweets from x.com as images, with the tweet link below.

## How it works

- A WebView shows x.com. You sign in on X's own login page (username/password; Google sign-in is blocked inside WebViews).
- A blue **+** shows on each tweet. Tap it to select (it turns red). Select several tweets for a thread.
- **Capture** draws the selected tweets from the page, hides sticky bars, adds the link footer and saves PNG (JPEG if above ~4 MB).
- Opened from another app (`GET_CONTENT` / `PICK`, `image/*`): **Use** returns the image to that app.
- Opened from the launcher: **Share** sends it with the share sheet.
- The app is also a Documents provider: "TweetPick" shows in the system file picker sidebar with every saved capture.

Toolbar: Bookmarks, Likes, Mine (tweets and replies), Search (my tweets / all of X / find on this page), Capture, ⋯ (set handle, clear selection, home, reload).

## Build

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Needs JDK 17 and Android SDK 35 (`local.properties` → `sdk.dir`).

## Test on the emulator

```bash
emulator -avd tweetpick -dns-server 8.8.8.8,1.1.1.1
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Use `-dns-server` when the emulator cannot resolve hosts on a corporate DNS.

## Known gaps

- Likes have no text search on X. Use "On this page" after you scroll.
- Bookmark search on X needs Premium.
- Tweets taller than 8000 px are cut off.
- The capture code depends on X's `data-testid` markup and can break when X changes it.
