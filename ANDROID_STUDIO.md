# Opening the project in Android Studio

1. In Android Studio choose **Open** and select the `usoschecker` directory containing
   `settings.gradle`. If using the ZIP, extract it and open its `VistulaChecker` folder.
2. Allow Gradle sync. The repository includes `gradlew`, `gradlew.bat` and the wrapper JAR.
3. Under **Settings → Build, Execution, Deployment → Build Tools → Gradle**, select
   **JDK 17** as Gradle JDK. The IDE can download it if it is not installed.
4. Under **Tools → SDK Manager**, install **Android SDK Platform 35** and
   **Android SDK Build-Tools 35.0.0**. Android Studio manages `local.properties` for your machine.
5. Select the **app** run configuration and your phone or an emulator running API 26 or newer.
6. Click **Run ▶**. On the start screen choose **Zobacz wersję demonstracyjną** to use the
   application without registering an API key.

Use Android Studio Meerkat Feature Drop (2024.3.2) or a later IDE version that supports
Android Gradle Plugin 8.10.1. The Gradle wrapper version is 8.11.1.

To generate an APK from the IDE, choose **Build → Generate App Bundles or APKs → Generate APKs**
(the wording may differ slightly between IDE versions). The debug APK appears at
`app/build/outputs/apk/debug/app-debug.apk`.

## Modules

- `app`: native Android UI in Java, OAuth, API transport, encrypted storage and WorkManager.
- `core`: platform-independent Java code for OAuth signing, response parsing and change detection.
- `app/src/main/assets/api`: complete bundled documentation for the 571 published Vistula API methods.
- `core/src/test`: JVM tests; run `CoreTest` directly from the IDE.
- `app/src/androidTest`: device/emulator tests; run `AppSmokeTest` from the IDE.

Private grades and timetable data require a Consumer Key/Secret and authorization through
Vistula's website. The API catalog and demo are usable without those credentials.
Administrative methods require permission issued by the university; ordinary OAuth login
does not grant administrative access. See `README.md` for scope and monitoring limits.
