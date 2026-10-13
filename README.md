# Vistula · zmiany — aplikacja Android

Natywna aplikacja w języku polskim dla Akademii Finansów i Biznesu Vistula.
Android 8.0 (API 26) lub nowszy. Nie jest oficjalną aplikacją uczelni.

## Funkcje

- Monitor ocen: nowe, zmienione i usunięte oceny; wartość, komentarz, termin egzaminu i czas modyfikacji z API.
- Monitor planu na następne 28 dni: dodanie/usunięcie zajęć, godziny, sala, budynek i identyfikatory prowadzących.
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
Domyślne zakresy to `grades|studies|offline_access`. Dla innych metod można dodać zakresy
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
- Plan pobierany jest w czterech żądaniach po siedem dni do `tt/student`. Porównujemy tylko
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

Testy JVM sprawdzają podpis OAuth z opublikowanym wektorem, kodowanie, parsowanie odpowiedzi,
punkt odniesienia, zmiany ocen/sali/godzin, czas źródłowy, przesuwanie okna planu,
odrzucanie niepełnych danych i serializację. Testy Android sprawdzają przepływ demo,
wyszukiwanie w katalogu, zgodność wszystkich plików dokumentacji i szyfrowanie magazynu.
Wyniki wykonania dla dostarczonego builda znajdują się w `VALIDATION.md`.

Odświeżenie publicznej dokumentacji (Python 3, internet; bez klucza):

```sh
python3 scripts/refresh_api_catalog.py
```

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
