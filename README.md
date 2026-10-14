# Vistula · zmiany — aplikacja Android

Natywna aplikacja w języku polskim dla Akademii Finansów i Biznesu Vistula.
Android 8.0 (API 26) lub nowszy. Nie jest oficjalną aplikacją uczelni.

Projekt można otworzyć bezpośrednio w **Android Studio**: wybierz katalog zawierający
`settings.gradle`, wykonaj Gradle Sync i uruchom konfigurację `app`.
Dokładne kroki: [ANDROID_STUDIO.md](ANDROID_STUDIO.md).

## Nawigacja i wygląd

Material 3: ikony i etykiety w dolnym pasku **Start / Oceny / Plan / Zmiany / Więcej**,
menu boczne otwierane przyciskiem w górnym pasku, obrysowane pola formularzy,
przełącznik Material i spójne przyciski. Menu inspirowane dziennikiem ucznia;
konto USOS i katalog API dostępne w **Więcej** oraz w menu bocznym.

Start pokazuje liczbę ocen, zajęć dzisiaj, otwartych zadań i najbliższe zajęcia
według zapisanego planu. **Zadania** pozwalają dodawać własne wpisy z terminem,
oznaczać je jako ukończone, przywracać i usuwać. To osobista lista bez powiadomień
o terminie i bez synchronizacji z uczelnią. Zadania są szyfrowane razem z lokalnymi
danymi; odłączenie konta usuwa je wraz z magazynem. Zadania demonstracyjne są
oddzielne i nie zapisują się w prawdziwym magazynie.

Oceny, plan i historia mają wyszukiwanie. Plan można zawęzić do dzisiaj lub
najbliższych siedmiu dni. Katalog API udostępnia wszystkie wyniki przyciskiem
„Pokaż kolejne metody”.

## Nowe narzędzia studenckie

- Zadania mają priorytet niski / normalny / wysoki, notatkę do 2000 znaków i sortowanie
  według terminu, priorytetu lub nazwy. Ukończone wpisy pozostają na końcu, a wybór
  sortowania jest zapamiętywany. Wyszukiwanie obejmuje również notatki.
- Pasek postępu pokazuje ukończone zadania. Po usunięciu dostępne jest „Cofnij” przez
  10 sekund; przywracanie zachowuje treść, priorytet, termin i stan ukończenia.
- Szczegóły zajęć umożliwiają utworzenie zadania z nazwą przedmiotu i otwarcie
  systemowego wyboru aplikacji do udostępnienia informacji o zajęciach.
- Start otwiera plan w dniu najbliższych zajęć. Filtry przewijają się poziomo,
  mają przyciski o wysokości co najmniej 48 dp i widoczny stan wyboru.
- Błędy synchronizacji wskazują etap operacji i metodę API. Opis można skopiować
  z okna błędu; tokeny i surowe odpowiedzi serwera nie są dołączane.
- Puste kolekcje ocen oraz opcjonalne daty i komentarze zwracane jako JSON `null`
  nie przerywają synchronizacji. Brak wymaganych pól lub nieprawidłowy typ danych
  nadal zatrzymuje zapis, zachowując poprzedni zestaw danych.

- Oceny: filtr semestru, podsumowanie aktualnie widocznych wpisów, liczba ocen liczbowych
  i pozostałych, prosta średnia oraz minimum i maksimum. Średnia nie uwzględnia wag ani ECTS;
  obejmuje wpisy z różnych terminów egzaminów i nie jest oficjalną średnią uczelni.
- Plan: eksport widocznych zajęć do pliku .ics przez systemowy wybór pliku. Wyszukiwanie
  i zakres dat ograniczają eksport. Godziny są przeliczane z Europe/Warsaw do UTC.
  Zapisany plik jest kopią danych, nie subskrypcją ani automatyczną synchronizacją kalendarza.
- Zadania: edycja treści i terminu, wyszukiwanie, filtry otwarte / ukończone / dzisiaj /
  po terminie. Ukończenie zadania pozostaje zachowane po edycji.
- Start: trzy najbliższe otwarte zadania z oznaczeniem dzisiejszych i zaległych terminów.
- **Komunikaty uczelni**: oficjalne ogłoszenia i zarządzenia Rektora pobierane bezpośrednio z USOS API (`services/news/search`), z podglądem na ekranie Start, wyszukiwarką, autorem, datą publikacji i możliwością kopiowania lub otwierania w przeglądarce.
- **Licznik nieobecności na zajęciach**: osobiste narzędzie studenta do kontroli limitu opuszczeń zajęć (domyślnie 2 w semestrze zgodnie z regulaminem Vistuli). Przedmioty są automatycznie wykrywane z planu i ocen; aplikacja ostrzega przed wyczerpaniem lub przekroczeniem limitu.
- **Detektor kolizji w planie zajęć**: automatyczne wykrywanie nakładających się czasowo zajęć z alertem na ekranie głównym i bezpośrednim oznaczeniem w kalendarzu.
- **Konfiguracja kluczy API**: obsługa `apiKey` i `apiSecret` w pliku `local.properties` (oraz `usos.key` / `usos.consumer.key`).

## Kalendarz i aktualne zajęcia

Plan ma pasek wyboru dni tygodnia i strzałki zmiany tygodnia. Widok „Oś dnia”
pokazuje zajęcia na siatce godzinowej, rozdziela nakładające się spotkania na kolumny
i zaznacza aktualną godzinę. Dotknięcie zajęć otwiera pełne szczegóły. Przycisk
„Przejdź do teraz” przewija siatkę do bieżącej godziny. Widok „Agenda · cały plan”
pokazuje wszystkie pobrane tygodnie, grupując zajęcia według dnia.

Na ekranie Start i w planie dzisiejszego dnia widać „TRWAJĄ TERAZ”, prowadzącego,
salę i pozostały czas. Początek zajęć jest włączony do przedziału, koniec wyłączony;
terminy orientacyjne nie są przedstawiane jako potwierdzone aktualne zajęcia.
Wskazanie korzysta z ostatniego zapisu USOS, nie z informacji o faktycznej obecności.
Prawdziwy plan przechowuje dokładne czasy początku i końca; starsze zapisy pozostają czytelne.

Domyślnie aplikacja pobiera osiem tygodni od poniedziałku bieżącego tygodnia.
„Wczytaj kolejne 4 tygodnie” rozszerza zakres do maksymalnie szesnastu tygodni.
Maksymalnie trzy zapytania do USOS działają równocześnie, a pasek operacji pokazuje
postęp tygodni i pobieranie prowadzących. Błąd dowolnej strony pozostawia stary
plan w całości. Data w nagłówku oznacza rzeczywiście zapisany zakres; brak zajęć
w danym tygodniu może oznaczać pustą odpowiedź USOS. Odczyty zapisu w tle nie
blokują formularzy ani nie pokazują paska ładowania; zegar aktualizuje się co minutę.

## Funkcje

- Katalog API: ulubione metody zapisywane na telefonie oraz filtr „Ulubione”, działające offline.
  Zmiana ulubionych nie kasuje wpisanych parametrów. Lista zawiera tylko nazwy metod;
  nie przechowuje odpowiedzi ani danych uwierzytelniających.
- Obsługa błędów API: rozpoznawanie JSON i formularzy OAuth, rozróżnienie wygasłej sesji
  od błędnego podpisu, podpowiedzi przy wymaganych polach i zachowanie parametrów po błędzie.
  Nowe żądanie unieważnia poprzednią odpowiedź do eksportu.
- Synchronizacja respektuje nagłówek `Retry-After` (sekundy lub data HTTP). Start pokazuje
  ostatnią próbę i czas kolejnego dozwolonego sprawdzenia. Wygasła sesja wstrzymuje
  monitorowanie i pokazuje skrót do ustawień konta.

- Monitor ocen: nowe, zmienione i usunięte oceny; wartość, komentarz, termin egzaminu i czas modyfikacji z API.
- Monitor planu na 8 tygodni (rozszerzalny do 16 tygodni): dodanie/usunięcie zajęć, godziny, sala, budynek i imiona i nazwiska prowadzących (lub ID, jeśli USOS nie udostępnia danych osoby).
- Historia ostatnich 200 zmian z wartościami przed/po i czasem wykrycia w strefie Europe/Warsaw.
- Ręczne odświeżanie i WorkManager w tle co 15, 30, 60 lub 120 minut.
- Powiadomienia bez ujawniania ocen na ekranie blokady; zgoda na Androidzie 13+.
- OAuth 1.0a w zewnętrznej przeglądarce i potwierdzenie kodem PIN (oob). Hasło do uczelni nie trafia do aplikacji.
- Tokeny, dane i historia szyfrowane AES-GCM z kluczem Android Keystore; zapis atomowy, kopie zapasowe wyłączone.
- Tryb demonstracyjny z symulacją zmiany oceny i sali, oddzielony od prawdziwego konta.
- Pełny katalog **571 metod API** z dokumentacją offline: opisy, parametry, domyślne wartości, zakresy OAuth, wymagania administracyjne, pola odpowiedzi, błędy i oznaczenia beta/deprecated.
- Uniwersalny klient metod: GET, formularz POST, multipart dla `blobbox/put` i `blobbox/put_unsigned`, podgląd JSON/tekstu, eksport odpowiedzi i pobieranie plików przez systemowy wybór dokumentu.

Katalog odwzorowuje wszystkie metody obecne w publicznym indeksie Vistuli podczas pobrania.
Każda metoda ma formularz generowany z własnej dokumentacji. To nie oznacza 571 odrębnych,
zaprojektowanych ekranów użytkowych ani przyznania uprawnień administracyjnych. Własne ekrany
studenckie obejmują oceny, plan i monitorowanie zmian. Serwer uczelni sprawdza uprawnienia
każdego żądania. Niektóre metody wymagają dodatkowych zakresów zgody, specjalnego klucza
lub interakcji w przeglądarce. Odpowiedzi OAuth zawierające tokeny są ukryte w uniwersalnym podglądzie.
Transfery i pliki ograniczono do 8 MB, podgląd tekstowy do 100 000 znaków; eksport zachowuje pełną odpowiedź.

## Uruchomienie na telefonie

1. Zainstaluj APK z `dist/vistula-checker-debug.apk` (jeśli dołączono gotowy build).
2. Bez klucza wybierz **Zobacz wersję demonstracyjną**. Katalog i dokumentacja API działają offline.
3. Aby pobierać prywatne dane, w **Konto** podaj własny `Consumer Key` i `Consumer Secret`
   z [USOSapps Vistuli](https://usosapps.vistula.edu.pl/developers/).
4. Wybierz **Zaloguj w przeglądarce**, zaakceptuj zakresy dostępu na stronie USOS,
   wróć do aplikacji i wpisz otrzymany PIN.
5. Zezwól na powiadomienia. Pierwsze pobranie tworzy punkt odniesienia, kolejne zapisują różnice.

Rejestracja klucza nie jest wykonywana przez aplikację. Klucz nie jest wpisany do kodu ani do APK.
Domyślne zakresy to `grades|studies|payments|offline_access`. Zakres `payments` jest wymagany do pobierania subkont i harmonogramu płatności. Dla innych metod można dodać zakresy
podane w katalogu przed logowaniem. Zmiana zakresów wymaga odłączenia konta i nowej autoryzacji.
Nie należy publicznie rozpowszechniać wspólnego Consumer Secret w aplikacji mobilnej;
wersja używa klucza podanego przez właściciela telefonu. Dla publikacji dla wielu osób
oficjalna dokumentacja zaleca serwerowy pośrednik OAuth.

## Dokładność monitorowania

- Czas **wykrycia** to czas porównania na telefonie. Czas **modyfikacji w USOS** pochodzi
  z `date_modified` oceny. API planu nie podaje czasu edycji; aplikacja nie udaje, że zna czas publikacji.
- Android może opóźniać WorkManager, szczególnie w Doze, bez internetu lub po wymuszonym zatrzymaniu.
  Interwał to częstotliwość zamówiona, nie gwarantowana. Monitorowanie nie jest usługą push uczelni.
- Oceny pochodzą z semestrów zwróconych przez `courses/user` z `active_terms_only=false`
  oraz `grades/terms2`. Nie obejmują wyników pojedynczych sprawdzianów z modułu `crstests`.
- Plan pobierany jest w ośmiu żądaniach po siedem dni (do szesnastu po rozszerzeniu zakresu) do `tt/student`. Porównujemy tylko
  wspólny zakres kolejnych pobrań: wejście nowego dnia w zakres i zakończenie starego dnia
  nie tworzą fałszywych powiadomień.
- Identyfikator `sm_id` pozwala rozpoznać przeniesienie potwierdzonego spotkania.
  Dla planów generowanych (`classgroup`) identyfikator obejmuje datę; przeniesienie na inny
  dzień może być pokazane jako usunięcie i dodanie. Nieznane typy używają tożsamości zastępczej.
- Dla częstotliwości `other`/`once` API może zwracać możliwe, niepotwierdzone terminy.
  Aplikacja oznacza je jako orientacyjne.
- Pobranie jest zatwierdzane dopiero po powodzeniu wszystkich żądań i parsowania.
  Błąd sieci/API nie zastępuje poprzedniego zapisu pustymi danymi. Nie wykrywamy zmian,
  które pojawiły się i zniknęły całkowicie między sprawdzeniami.

## Budowanie

Wymagane JDK 17, Android SDK Platform 35 i Build Tools 35.0.0.
Gradle 8.11.1 i Android Gradle Plugin 8.10.1 są przypięte; wrapper sprawdza SHA-256 Gradle.

```sh
export JAVA_HOME=/sciezka/do/jdk-17
export ANDROID_HOME=/sciezka/do/android-sdk
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Na podłączonym telefonie/emulatorze:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Aby uruchomić testy w osobnej instalacji i zachować konto aplikacji na emulatorze:

```sh
./gradlew -PisolatedTests :app:connectedDebugAndroidTest
```

Opcja dodaje sufiks `.qa` do identyfikatora debugowej aplikacji. Zwykłe budowanie
nie używa tego sufiksu. Testy demo wymagają instalacji bez połączonego konta.

Testy JVM sprawdzają podpis OAuth z opublikowanym wektorem, kodowanie, parsowanie odpowiedzi,
punkt odniesienia, zmiany ocen/sali/godzin, czas źródłowy, przesuwanie okna planu,
odrzucanie niepełnych danych i serializację. Testy Android sprawdzają przepływ demo,
wyszukiwanie w katalogu, zgodność wszystkich plików dokumentacji i szyfrowanie magazynu.
Wyniki wykonania dla dostarczonego builda znajdują się w `VALIDATION.md`.

Odświeżenie publicznej dokumentacji (Python 3, internet; bez klucza):

```sh
python3 scripts/refresh_api_catalog.py
```

## Prowadzący zajęcia

Plan pobiera identyfikatory prowadzących z tt/student, a następnie zbiorczo pobiera
first_name i last_name przez users/users. Powtarzające się identyfikatory z czterech
tygodni planu są łączone; zapytania obejmują do 50 osób. Nazwiska są widoczne w planie,
podglądzie najbliższych zajęć, wyszukiwaniu i opisie eksportu .ics.
Jeżeli USOS zwraca null dla danej osoby, widoczne jest jej ID. Błąd sieci lub niepełna
odpowiedź nie zatwierdza nowego zapisu danych. Po aktualizacji wybierz „Sprawdź teraz”,
aby uzupełnić prowadzących w istniejącym planie.

## Źródła API

Serwer: https://usosapps.vistula.edu.pl/. Publiczny endpoint `services/apisrv/installation`
potwierdził nazwę „Akademia Finansów i Biznesu Vistula z siedzibą w Warszawie”.
Dokumentacja w `app/src/main/assets/api` pochodzi z oficjalnych metod `apiref/method_index`
i `apiref/method`; nazwy, opisy i wymagania są treścią dostarczoną przez USOS API.

- [Autoryzacja OAuth](https://usosapps.vistula.edu.pl/developers/api/authorization/)
- [Oceny](https://usosapps.vistula.edu.pl/developers/api/services/grades/)
- [Plan zajęć](https://usosapps.vistula.edu.pl/developers/api/services/tt/)
- [Dokumentacja API jako dane](https://usosapps.vistula.edu.pl/developers/api/services/apiref/)
- [Przesyłanie plików blobbox](https://usosapps.vistula.edu.pl/developers/api/services/blobbox/)
