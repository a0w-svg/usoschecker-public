# Validation — 2026-10-13

## Passed

- Gradle 8.11.1 / JDK 17 build: `:core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`.
- 14 JVM tests, 0 failures, 0 skipped: OAuth signing/encoding, response parsing,
  initial baseline, grade and timetable differences, rolling schedule windows, serialization,
  and rejection of incomplete or duplicated responses.
- Android Lint: **0 errors**, 6 advisory warnings about newer dependency/Gradle versions.
  Versions are deliberately pinned to the documented AGP 8.10.1 toolchain.
- Debug application APK and instrumentation-test APK both built.
- `apksigner verify`: APK signature scheme v2 verified.
- Offline catalog: all **571/571** indexed methods have matching JSON descriptors,
  argument lists and authorization requirements.
- Live anonymous request to `services/apisrv/installation` confirmed that the configured
  server belongs to Akademia Finansów i Biznesu Vistula z siedzibą w Warszawie.

## Not verified

- Device/emulator tests were compiled but not executed. No KVM device is available on this
  host. Android Emulator 37.2.12 with the Android 35 x86_64 system image crashed before
  Android booted, both with SwiftShader and with Vulkan disabled/GPU off. This is an emulator
  host failure, not a successful application runtime test.
- The Android Studio GUI itself was not launched here. The project has standard Android
  Gradle modules, the complete wrapper and a shared Android App run configuration.
- OAuth login and private grade/timetable requests require a registered key and user consent.
  No real university account or credentials were used, and no key was registered.
- Actual background timing, notification delivery, file upload/download and administrative
  permissions need device/account validation. No write operation was sent to the university.

Run the supplied `AppSmokeTest` on a phone or working emulator from Android Studio, or use:

```sh
./gradlew :app:connectedDebugAndroidTest
```

The six instrumentation tests cover demo navigation, searching and opening API documentation,
all bundled descriptors, encrypted storage, preservation of data after failed sync, and an
anonymous live installation request. Compilation of those tests does not establish their runtime result.

Debug APK SHA-256:

```text
a9c303a0a73616d7a2772c7a5e6f22ff6aca59c3e3e43f28b1720453d355f423
```
