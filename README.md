# Compass (Statusbar Compass)

A compass that lives in the Android **status bar** and on the **lock screen**.

- Package name: `statuscompass.blackfalcon.jan`
- App name: **Compass** — APK file: **Statusbar Compass.apk**

**By: Black Falcon**

## What you see
- **All phones (Android 8+):** a status-bar icon with the degrees on top and the direction below (e.g. `245` / `SW`), plus an ongoing notification (`245° SW`, full direction name, **Stop** button) that is readable on the lock screen.
- **Android 16+:** the app also requests a *Live Update*, which shows a chip such as `245°SW` in the status bar (ahead of the normal notification icons) and at the top of the lock screen. If you do not see it, open the app and tap **Live updates settings**, then allow it.

## Notes
- Android does not let normal apps add icons to the right-hand *system* icon area (VPN key, airplane mode, etc.). Notification icons / Live Update chips are what third-party apps can use.
- Runs as a foreground service, so it keeps working in the background.
- Phone flat → direction of the top of the phone. Phone upright → direction the back of the phone faces.
- No internet, no dependencies.

## Permissions
- `POST_NOTIFICATIONS` (Android 13+), `FOREGROUND_SERVICE`, `POST_PROMOTED_NOTIFICATIONS` (Android 16+ Live Updates)

## Build
Push to GitHub — the **Build APK** workflow runs automatically.
Open **Actions → latest run → Artifacts → Statusbar Compass** to download the APK.
