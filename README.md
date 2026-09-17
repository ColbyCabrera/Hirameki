<p align="center">
<img alt="Hirameki app select images" src="docs/graphics/logos/readme-banner.webp"/>
</p>

# Hirameki: Material 3 Expressive Fork of AnkiDroid

Hirameki is a fork of **AnkiDroid**, focused on bringing a modern, **Material 3 Expressive** design to the premier spaced-repetition flashcard system for Android.

> [!IMPORTANT]
> **Unofficial Fork**: This project is not affiliated with, endorsed by, or officially associated with the main AnkiDroid maintainers.

### Why This Fork?
The goal of this project is to experiment with and implement the latest Material 3 Expressive design principles, creating a visually unique and fun UI design.

### Documentation Note
Because this fork introduces significant UI and UX changes, the official [AnkiDroid Wiki](https://github.com/ankidroid/Anki-Android/wiki) and [User Manual](https://ankidroid.org/docs/manual.html) may not map 1-1 with the features or layout found here. Please refer to this repository's issues and documentation for fork-specific guidance.

---

### Status & Availability
This project is currently in **active development** and is available on Google Play, with plans to expand to other android app stores!

<div style="display:flex;">

<a href="https://play.google.com/store/apps/details?id=com.hirameki.flashcards">
    <img alt="Coming soon to Google Play" height="80"
        src="docs/graphics/logos/google-badge.png" /></a>

</div>

---

### Key Features (M3 Expressive)
- **Material You**: Dynamic color support and revamped UI components.
- **Expressive Motion**: Smooth, intentional animations for a more fluid experience.
- **Modernized Layouts**: Focused on the latest Android design standards.
- **AnkiWeb Compatibility**: Built on the robust core of AnkiDroid, maintaining sync compatibility with AnkiWeb and the [FSRS algorithm](https://github.com/open-spaced-repetition).
- **Card View Reviewer**: Study with a physical-feeling card: tap to flip it, then drag or flick it into a corner to grade (Again, Hard, Good, Easy). Each part can be switched off in Settings > Reviewing > Card view. A card too long for the screen shrinks its text a little before it scrolls, and its main word stays the largest.
- **Deck Options**: The deck options page follows your Material You colours, number fields have − and + buttons (hold to repeat), and the revert icon beside a changed setting puts its default back with a single tap.
- **Predictive Back**: Back gestures preview the screen you are returning to, and back closes drawers, selections and searches first.
- **Arabic-Script Font**: Arabic, Persian, Urdu and Kurdish text uses [Vazirmatn](https://github.com/rastikerdar/vazirmatn) instead of the system's print font, in the app and on cards. Toggle it in Settings > Appearance > Themes.

---

### Build Your Own APK
The **📦 Build & Release APK** workflow builds signed APKs on GitHub and publishes them as a release you can download — no Android toolchain on your own machine. One-time setup in your fork:

1. Fork this repository, open the **Actions** tab of your fork and enable workflows (GitHub disables them on new forks).
2. Create a signing key. `keytool` ships with every JDK — on Windows: `winget install EclipseAdoptium.Temurin.21.JDK`, then open a new terminal. Run this **outside** the repository folder and choose a password when asked:

   ```
   keytool -genkeypair -v -keystore hirameki-release.jks -storetype PKCS12 -alias hirameki -keyalg RSA -keysize 4096 -validity 10000
   ```

   > [!WARNING]
   > Back up `hirameki-release.jks` and its password, and never commit them. Android only installs updates signed with the same key: lose it and every device has to uninstall the app — along with any collection data that is not synced — before a newer build will install.

3. Turn the key into text. Windows (PowerShell), which copies it to the clipboard:

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path hirameki-release.jks))) | Set-Clipboard
   ```

   macOS or Linux: `base64 < hirameki-release.jks`

4. In your fork, open **Settings → Secrets and variables → Actions** and add four repository secrets:

   | Secret | Value |
   | --- | --- |
   | `KEYSTORE_BASE64` | the text from step 3 |
   | `KEYSTORE_PASSWORD` | the keystore password |
   | `KEY_ALIAS` | `hirameki`, or whatever you passed to `-alias` |
   | `KEY_PASSWORD` | the key password — with the command above, the same as the keystore password |

5. Open **Actions → 📦 Build & Release APK → Run workflow**. Pushing a tag such as `v1.2.0` starts the same build.
6. When the run finishes, download the APK from the **Releases** page of your fork. If unsure, take the `universal` APK; `arm64-v8a` is smaller and fits almost every current phone.

The release tag defaults to `v` + `baseVersionName` from `AnkiDroid/build.gradle.kts`. A pushed tag has to be exactly that — the APKs carry `baseVersionName` whatever the tag says — so to release a new version, bump `baseVersionName` and `baseVersionCode` and tag that commit; any other tag name has to go through the **Run workflow** form. Missing secrets, a wrong keystore password, a mismatched tag and an already-released tag all stop the run within seconds, before the unit tests and the long build start — so if that tag already exists, bump `baseVersionName` or type a different tag in the **Run workflow** form. The unit tests run next to the build, and the release is published only if every test passes; if one fails, the signed APKs stay downloadable from the run page for 7 days. The first build is the slowest; later builds on your default branch reuse the Gradle cache.

These APKs are signed with your key, not Google Play's, so Android will not install them over the Play Store version. Uninstall that first, after syncing or backing up your collection.

---

### Credits & Acknowledgments
This work would not be possible without the incredible foundation laid by the [AnkiDroid](https://github.com/ankidroid/Anki-Android) team and its contributors. Please consider donating to support their work.

### License
This project inherits the licenses of the original AnkiDroid project:
* [GPL-3.0 License](https://github.com/ankidroid/Anki-Android/blob/main/COPYING)
* [AGPL-3.0 License](https://github.com/ankitects/anki/blob/main/LICENSE) for core back-end components.
* [LGPL-3.0 License](https://github.com/ankidroid/Anki-Android/blob/main/api/COPYING.LESSER) for the AnkiDroid API.
* [SIL Open Font License 1.1](https://github.com/rastikerdar/vazirmatn/blob/master/OFL.txt) for the bundled Vazirmatn font (licence text in `AnkiDroid/src/main/assets/fonts/vazirmatn/OFL.txt`).
