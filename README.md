# Little Light

*Small steps in faith make a big difference.*

Little Light is a Christian children's faith and screen-time companion for ages 4–10. It has short Bible stories, memory verses, kindness challenges and a garden that grows. On Android, its **faith-first helper** keeps chosen fun apps (games, video) closed until the child has had faith time, then opens them for the parent's set number of minutes.

This first version is **Android only**.

## Getting a test version onto an Android phone

1. Every time code is pushed to `main`, GitHub builds the app automatically (see the **Actions** tab).
2. When the build finishes, open the **Releases** section of this repo on the Android phone and choose **Little Light test build**.
3. Tap **LittleLight-test.apk**, then **Install**. Android may first ask you to allow installs from your browser.

## Turning on the faith-first helper (on the phone)

Open Little Light, tap **Grown-ups**, enter your PIN and go to **App guard**:

1. Allow **Usage access** and **Display over other apps**. Little Light explains each one first.
2. Choose the fun apps.
3. Switch the helper on.

To use the phone yourself, pause the helper for 30 minutes, 1 hour or until tomorrow.

## What's in each folder

| Folder or file | What it is |
|---|---|
| `www/index.html` | All of Little Light's screens, stories and garden (one file) |
| `www/fonts/` | Fonts saved inside the app, so it works offline |
| `android/app/src/main/java/.../guard/` | The faith-first helper: `GuardService` (watches which app is open and shows the "Faith time first" screen), `GuardPlugin` (lets the screens talk to the helper), `GuardPrefs` (its saved settings), `BootReceiver` (restarts the helper after the phone restarts) |
| `.github/workflows/android.yml` | Builds the installable test app on GitHub |
| `PRIVACY.md` | Privacy policy (also needed for Google Play) |

## Privacy in one line

No accounts, no ads, no tracking, and no internet permission. Everything stays on the phone.

## Current limits

- Fun time is shared by everyone using the same phone each day.
- An older child could switch the helper off in Android Settings. On a child's own phone, use Google Family Link as well.
- Three of the four stories are still to be written. David and Goliath is complete.
- The "Testing tools" in Grown-ups › Routine will be removed before release.

© 2026 Anderson Jones. All rights reserved.
