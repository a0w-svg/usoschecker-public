# Przygotowanie wydania

Po każdym większym zestawie napraw publikujemy nowy GitHub Release. Dla napraw
zwiększamy zwykle numer PATCH oraz zawsze `versionCode`. Nowe APK podpisujemy
tym samym prywatnym kluczem co poprzednie wydanie, aby umożliwić aktualizację.
Publikacja następuje po testach i wymaganym CI, ze sprawdzonego commita `main`;
zawiera APK, źródła, sumy SHA-256 i opis napraw. Poprzednich wydań nie nadpisujemy.

Publikacja źródeł na GitHubie: [SECURITY.md](SECURITY.md). Skrypt pakujący tworzy
wyłącznie `dist/publication/VistulaChecker-AndroidStudio.zip`; nie dołącza binariów
ani mapowania R8. Nie publikuj starszych APK debug przechowywanych w `dist`.

Projekt wymaga JDK 21 oraz Android SDK Platform 37. Korzystaj z dołączonego wrappera Gradle.
Kod aplikacji pozostaje zgodny z Androidem 12 (API 31) i nowszym; target SDK wynosi 36.
Zmiana target SDK obejmuje obsługę przycisku i gestu Wstecz przez AndroidX.

## Kontrola jakości

Każde nowe wydanie wymaga aktualizacji `app/src/main/res/raw/whats_new.txt` z opisem
zmian dla użytkownika oraz opisu GitHub Release. Aplikacja czyta stabilne, opublikowane
wydanie z `/releases/latest`; powiadamia dopiero, gdy jest dostępny gotowy APK o nazwie
`VistulaChecker-X.Y.Z.apk` w repozytorium publikacji. Zachowaj ten schemat nazwy i tag
`vX.Y.Z`. Wersje robocze i prerelease są pomijane. `ReleaseUpdatesRegressionTest`
sprawdza powiadomienia, brak duplikatów, cache offline, harmonogram i ekran nowości;
`AppReleaseTest` sprawdza numery wersji i odrzucanie obcych linków.
Osobna fixture `ReleaseNotificationPermissionTest` wymaga `-PnotificationPermissionTests`:
zainstaluj QA i test APK, odbierz `POST_NOTIFICATIONS` przez `adb shell pm revoke`
przed startem instrumentacji, następnie uruchom tylko tę klasę. Test wymaga rzeczywiście
odmówionego uprawnienia i sprawdza możliwość ponownego powiadomienia po jego udzieleniu.

Obsługa motywu obejmuje domyślne dopasowanie do telefonu, ręczny wybór jasnego/OLED
oraz zachowanie ustawienia po aktualizacji APK. `AppearanceRegressionTest` sprawdza
zmianę motywu telefonu podczas pracy aplikacji i powrót z wyboru ręcznego do automatycznego.
Osobna fixture w `app/src/upgradeTest` jest dołączana tylko z `-PupgradeTests`;
uruchamiaj ją po aktualizacji odrębnej aplikacji QA przez `adb install -r`, z argumentem
`upgradeTheme=light`, `dark` lub `system`. Nie odinstalowuj starego APK przed tym testem.

W PowerShell, z katalogu projektu:

```powershell
.\tools\production-check.ps1
# Pełny zestaw testów urządzenia, z odrębnymi danymi aplikacji QA:
.\tools\production-check.ps1 -DeviceTests
```

Testy urządzenia wymagają uruchomionego emulatora lub urządzenia podłączonego przez ADB.
Wersja QA ma identyfikator `pl.vistula.checker.qa` i nie nadpisuje danych zwykłej aplikacji.
Nie dodawaj filtrów klas do pełnej kontroli: pominięte klasy nie oznaczają zaliczonego testu.
Skrypt zatrzymuje wydanie przy niepowodzeniu testów, lint lub budowania.
Sprawdza również liczbę wykonanych testów w raportach XML. Sam komunikat `BUILD SUCCESSFUL`
przy braku uruchomionych testów nie jest uznawany za zaliczoną kontrolę.
Przy `-DeviceTests` uruchamia także testy startu, nawigacji i manifestu w zoptymalizowanym
wariancie `optimizedQa` (R8, bez debugowania). Ten wariant nadal ma osobny identyfikator QA
i testowy podpis debug; służy wyłącznie do weryfikacji, nie do publikacji.
Opcjonalny test rzeczywistego publicznego USOS uruchamia się osobno:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -PisolatedTests -PliveApiTests '-Pandroid.testInstrumentationRunnerArguments.class=pl.vistula.checker.LiveUsosTest'
```

Ten test wymaga internetu i dostępności serwera uczelni. Jego źródła są dołączane tylko
po podaniu `-PliveApiTests`, dlatego nie wchodzi do domyślnego zestawu.

Raporty:

- `core/build/reports/tests/test/index.html`
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-release.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/androidTests/connected/optimizedQa/index.html`

## Konfiguracja produkcyjna

Release ma wyłączone debugowanie i włączone R8 oraz redukcję zasobów.
`verifyReleaseConfiguration` blokuje wyłączenie tych zabezpieczeń, wpisanie kluczy USOS
do pól BuildConfig lub skonfigurowanie release z kluczem podpisującym debug.
Reguła R8 zachowuje nazwę i konstruktor pracownika WorkManager, także dla zadań
zaplanowanych przed aktualizacją aplikacji, oraz konstruktor jego bazy Room tworzony przez refleksję.
Wariant QA zachowuje dodatkowo API tracing i Kotlin stdlib współdzielone z osobno optymalizowanym runnerem testów;
ta reguła testowa nie jest dodawana do produkcyjnego release.

Lokalne Consumer Key/Secret z `local.properties` są dostępne wyłącznie w zwykłym debug.
Release oraz debug z `-PisolatedTests` mają te pola puste. W wydaniu produkcyjnym właściciel
telefonu podaje dane przez ekran konta. Tokeny i dane użytkownika trafiają do szyfrowanego
magazynu Android Keystore; kopie zapasowe i transfer magazynu na inne urządzenie są wyłączone.
Nie umieszczaj wspólnego Consumer Secret w APK, zasobach ani konfiguracji CI.
Dla modelu dystrybucji ze wspólnym sekretem potrzebna jest uzgodniona z uczelnią architektura
serwerowa; obfuskacja nie ukrywa sekretu przed osobą posiadającą APK.

Debugowy FileProvider do testowania eksportu jest zdefiniowany tylko w `src/debug`.
Nie powinien występować w manifeście wydania. Aplikacja produkcyjna nie zezwala na HTTP.
Przedmioty bez ocen są zapisane oddzielnie od ocen: nie zwiększają średniej ani historii zmian ocen.

## Artefakty i podpis

Po zaliczeniu kontroli powstają:

- APK: `app/build/outputs/apk/release/app-release-unsigned.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`
- mapowanie R8: `app/build/outputs/mapping/release/mapping.txt`

Domyślne artefakty release są **niepodpisane**. Zachowaj mapowanie dla każdej opublikowanej
wersji, razem z jej `versionCode` i sumą SHA-256, aby móc odtworzyć stos błędu po obfuskacji.
Podpisz finalny AAB w Android Studio przez **Generate Signed Bundle / APK**, używając własnego
klucza przesyłania do Google Play. Aktualizacja istniejącej aplikacji wymaga właściwego klucza
i większego `versionCode`; nie zastępuj go kluczem debug. Klucza ani haseł nie dodawaj do repozytorium.

## Weryfikacja przed publiczną publikacją

Automatyczne testy używają danych testowych i odpowiedzi HTTP z fixture. Nie potwierdzają
działania konta studenta w rzeczywistym USOS ani wszystkich wersji Androida.
Przed publicznym wydaniem sprawdź podpisany build na Androidzie 12 i Androidzie 16,
logowanie z własnym kontem, odświeżenie ocen i planu, pracę po utracie sieci, odmowę zgody
na powiadomienia, eksport dokumentu i powrót do aplikacji po ubiciu procesu.
Test aktualizacji powinien zachować wcześniejszy szyfrowany magazyn i zaplanowane zadania.

W Google Play uzupełnij politykę prywatności, deklarację Data safety oraz informacje
o nieoficjalnym charakterze aplikacji. Opisz eksport i udostępnianie jako działania użytkownika;
nie deklaruj certyfikacji ani audytu bezpieczeństwa na podstawie samych testów.

Podstawa konfiguracji: [optymalizacja R8](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization),
[zmiany Androida 16](https://developer.android.com/about/versions/16/behavior-changes-16),
[wymagania target API Google Play](https://support.google.com/googleplay/android-developer/answer/11926878),
[ryzyko sekretów w APK](https://developer.android.com/privacy-and-security/risks/hardcoded-cryptographic-secrets).

## CodeQL dla Kotlin

Workflow `.github/workflows/codeql.yml` analizuje Java/Kotlin po rzeczywistej
kompilacji Androida i aplikacji desktopowej z JDK 21. Tryb `none` nie analizuje
tego projektu Kotlin i kończył się błędem „could not process any of it”.
Przed uruchomieniem tego workflow należy przełączyć Code scanning z Default setup
na Advanced setup w ustawieniach Security repozytorium; GitHub odrzuca wyniki
zaawansowanego workflow przy jednocześnie aktywnym Default setup.
Python i GitHub Actions pozostają analizowane w osobnych pozycjach macierzy.
Nie uznawaj błędu konfiguracji skanera ani braku wyników za potwierdzenie braku podatności.

## Weryfikacja desktopu

Wersje instalatorów desktopowych i aplikacji Android są zgodne (`1.5.1`, Android
`versionCode` 17). Z pełnym JDK 21 i SDK Android 37:

```sh
bash gradlew :shared:desktopTest :desktopApp:createDistributable :desktopApp:packageDeb
xvfb-run -a python3 scripts/desktop_smoke.py
```

Test okna wymaga Xvfb i Tesseract OCR. Korzysta wyłącznie z konta testowego w `work/desktop-smoke`, wykonuje
wejście i wyjście z demo, odświeżenie oraz skróty nawigacji i sprawdza, czy zaszyfrowany
magazyn konta pozostał bez zmian. Nie testuje rzeczywistego USOS. Na Windows i macOS
nadal wymagane są testy na tych systemach; pakietu Linuksa nie traktujemy jako
weryfikacji instalatorów MSI/EXE/DMG.

### Windows x64

Job `Windows verification` buduje MSI oraz przenośny ZIP z własnym środowiskiem Java.
Lokalnie wymagane są pełny JDK z `jpackage` i narzędzia WiX dostępne w `PATH`:

```powershell
./tools/build-windows.ps1
python scripts/windows_smoke.py
```

Można przekazać `-PackagingJavaHome` do skryptu, jeśli Gradle działa na innym JDK.
Wyniki i SHA-256 znajdują się w `desktopApp/build/windows-publication`.
MSI instaluje aplikację dla bieżącego użytkownika; stały `upgradeUuid` zachowuje
tożsamość instalatora przy kolejnych aktualizacjach. ZIP uruchamia się przez
`VistulaChecker/VistulaChecker.exe`; nie wymaga osobnej instalacji Javy.
Test Windows rozpakowuje MSI administracyjnie bez instalacji na osobistym profilu,
uruchamia wynik z syntetycznym kontem i sprawdza blokadę procesu oraz zachowanie
magazynu. Nie zastępuje ręcznej kontroli całego interfejsu Windows.
