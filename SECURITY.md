# Bezpieczeństwo i publikacja

Nie publikuj kluczy USOS, tokenów OAuth, plików podpisujących, danych studenta ani
eksportów API w kodzie, zgłoszeniach, logach czy załącznikach. Release nie zawiera
wspólnego Consumer Secret. Właściciel telefonu wpisuje własne dane na ekranie konta.
Każdy sekret umieszczony w APK można odczytać z pliku aplikacji.

## Kontrole lokalne

Sprawdzanie aktualizacji korzysta wyłącznie z publicznego API GitHub Releases przez
HTTPS, bez tokena i bez danych konta USOS. Wysyłany jest numer wersji aplikacji w
User-Agent; GitHub widzi również adres IP połączenia. Odpowiedź ma limit rozmiaru i
czasu pobrania, a przekierowania API są odrzucane. Link wydania i gotowego APK musi
dokładnie wskazywać repozytorium publikacji. Opis zmian jest wyświetlany jako tekst,
bez wykonywania HTML. APK jest pobierany w przeglądarce na żądanie użytkownika;
system Android weryfikuje podpis przy instalacji aktualizacji.

Wymagane: Python 3 i Git. Z katalogu projektu:

```powershell
git config --local core.hooksPath .githooks
python scripts/security_check.py
python scripts/security_check.py --history
python -m unittest discover -s scripts -p "test_*.py"
.\tools\production-check.ps1
python scripts/package_android_studio.py
```

Hook `pre-commit` skanuje zawartość indeksu, a `pre-push` całą dostępną historię
wszystkich referencji. Hooki wymagają lokalnej instalacji w każdym klonie i mogą być
ominięte; CI jest dodatkową kontrolą. Skan historii odrzuca płytkie klony.
Skrypt nie wypisuje dopasowanych sekretów. Sprawdza również dokładne wartości
kluczy z lokalnego `local.properties`, bez wysyłania ich do jakiejkolwiek usługi.
Detekcja wzorcami nie gwarantuje wykrycia każdego sekretu ani danych osobowych.
Przed pierwszym pushem przejrzyj też `git diff --cached` oraz pliki i obrazy.

Nie dodawaj `local.properties`, `.env`, kluczy podpisujących ani katalogów
`build`, `bin`, `private`, `captures` i `dist`. Szablon konfiguracji znajduje się
w `local.properties.example`. `.gitignore` nie usuwa plików z wcześniejszych commitów.

Skrypt pakujący tworzy wyłącznie archiwum źródeł
`dist/publication/VistulaChecker-AndroidStudio.zip` po kontroli kodu i historii.
Nie kopiuje APK debug, AAB ani mapowania R8. Starsze pliki w `dist` mogą nadal
zawierać klucze z poprzednich buildów — nie publikuj całego katalogu `dist`.
Publiczne binaria buduj ponownie wariantem release po zaliczeniu kontroli;
mapowanie R8 i materiał podpisujący przechowuj prywatnie.

## Ustawienia GitHuba

Po utworzeniu repozytorium administrator powinien:

1. Włączyć Secret scanning i Push protection, jeżeli są dostępne dla repozytorium.
2. Włączyć Dependabot alerts oraz aktualizacje bezpieczeństwa.
3. Ustawić ochronę głównej gałęzi: wymagany PR, przegląd oraz kontrole
   `Source security` i `Android verification`; zablokować force push i usuwanie gałęzi.
4. Ustawić domyślne uprawnienia Actions na odczyt. Nie przekazywać danych USOS do CI.

Workflow używa `pull_request`, uprawnień tylko do odczytu i akcji przypiętych do SHA.
Testy nie korzystają z prawdziwego konta. Zadanie desktopowe udostępnia instalator
Linuksa z sumą SHA-256 do przeglądu PR; przy błędzie testu okna dołącza wyłącznie
zrzuty i log aplikacji ze sztucznego konta testowego. Sejf, klucze i dane profilu
testowego nie są dołączane do tych artefaktów.
Dependabot proponuje aktualizacje Gradle i GitHub Actions do przeglądu.

Dokumentacja: [GitHub Push protection](https://docs.github.com/en/code-security/concepts/secret-security/push-protection)
oraz [Secret scanning](https://docs.github.com/en/code-security/concepts/secret-security/secret-scanning).

## Zgłaszanie i obsługa wycieku

Zgłoś podatność przez prywatne zgłoszenie w zakładce Security GitHuba, jeśli
właściciel je udostępnił. W innym przypadku poproś opiekuna o prywatny kanał
kontaktu bez publikowania szczegółów ataku ani danych użytkowników w Issues.
Nie zamieszczaj rzeczywistych tokenów jako przykładu.

Po wycieku natychmiast unieważnij token/klucz u wystawcy i wygeneruj nowy.
Usunięcie pliku lub commita nie unieważnia klucza. Zatrzymaj push,
oczyść wszystkie dotknięte referencje historii, ponów skan i usuń publiczne
artefakty/logi zawierające sekret. Czyszczenie współdzielonej historii wymaga
koordynacji z innymi autorami; skaner nie zmienia historii automatycznie.

## Publiczne aktualności uczelni

Ekran aktualności pobiera publiczne wpisy z `https://vistula.edu.pl/wp-json/wp/v2/posts`.
Ten osobny transport nie przyjmuje kluczy aplikacji, tokenów ani danych konta USOS.
Pobiera po 20 wpisów w języku polskim i angielskim; dla ukraińskiego interfejsu
pokazuje oznaczoną wersję angielską. Serwer widzi adres IP i ogólny User-Agent.
Przekierowania są odrzucane, rozmiar odpowiedzi i czas odczytu są ograniczone,
a linki artykułów muszą wskazywać dokładnie domenę `vistula.edu.pl` przez HTTPS.
HTML jest wyświetlany jako tekst. Źródło USOS można wybrać oddzielnie; jego pusta
lista nie jest przedstawiana jako brak wiadomości na publicznej stronie uczelni.

Odbiór decyzji i wybór planu rat są operacjami wykonywanymi wyłącznie na żądanie
użytkownika po potwierdzeniu. Przed wysłaniem aplikacja sprawdza konto, zgody
i aktualną dostępność działania. Nie ponawia automatycznie operacji zapisu.
Kody odbioru nie trafiają do cache ani zapisanego stanu formularza.

Wybór filii opiera się na identyfikatorze jednostki i jej hierarchii z aktywnego programu
studiów USOS. Nadpisanie ręczne jest zapisywane w szyfrowanym stanie konta, a komunikaty
są oznaczane identyfikatorem kampusu. Zmiana filii usuwa cache wiadomości; stary cache
innej filii nie jest wyświetlany. Publiczny kanał strony głównej jest dostępny dla Warszawy;
pozostałe filie korzystają z filtrowanych komunikatów USOS oraz zweryfikowanych linków.
Nie wysyłamy identyfikatora studenta ani danych konta do publicznej strony uczelni.

Dane aktywacji mLegitymacji (`token`, `distribution_pass`) pobieramy wyłącznie
na żądanie z `services/cards/mcard_order2`. Nie trafiają do cache, logów ani
zapisanego stanu ekranu. Sprawdzamy identyfikator zamówienia i status gotowości
(`ready_to_receive` lub `ready`). Android zasłania okno aktywacji przed zrzutami
ekranu i zamyka je po przejściu aplikacji w tło. Wydanie i unieważnienie wymagają
potwierdzenia; status nie jest ponawiany jako zapis po niepewnej odpowiedzi.

Desktop przechowuje demo w odrębnym magazynie pamięciowym, bez zapisu do sejfu
użytkownika ani jego frekwencji. Operacje sieciowe na tokenie demo są odrzucane.
Sejf desktopowy ogranicza dostęp do właściciela (POSIX lub ACL Windows), odrzuca
dowiązania i zbyt duże pliki oraz blokuje zapis pomiędzy instancjami i procesami.
Odłączenie konta usuwa również klucz szyfrujący lokalny sejf. Na systemie Windows klucz `vault.key` jest dodatkowo opakowany przez DPAPI (`CryptProtectData` z nagłówkiem `VKW1` w kontekście bieżącego użytkownika), a istniejące surowe klucze 32-bajtowe są migrowane automatycznie przy odczycie. Interfejs otwiera
wyłącznie adresy HTTPS (`WebLinks.isWebUrl`); eksport wymaga wybrania pliku i potwierdzenia
zastąpienia istniejącego pliku. Sekret OAuth i PIN są domyślnie maskowane.
Drugie uruchomienie desktopu nie otwiera tego samego magazynu: blokada instancji
jest utrzymywana przez cały czas życia procesu, również podczas jego zamykania.
Chroni to operacje konta przed wzajemnym nadpisaniem przez dwie aplikacje.

## Tryb wykładowcy i eksperymentalny zapis ocen

Tryb konta (`STUDENT`, `LECTURER`, `BOTH`) jest wykrywany automatycznie na podstawie
profilu USOS (`staff_status`, `student_status`, aktywne prowadzone przedmioty i grupy).
W trybie studenta wszystkie nowe widoki są wyłącznie do odczytu, a metody zapisu
wykładowcy są ukryte w katalogu API i blokowane w kliencie (`UsosClient.explore`).
W szyfrowanym stanie konta zapisywane są wyłącznie zminimalizowane pola potrzebne do
widoku offline (np. `lecturerGroups`, `lecturerTests`, `lecturerMeetings`, `staffDirectory`,
`registrations`, `exams`, `academicCalendar`) z limitem liczby elementów i bez prywatnych
komentarzy, adresów e-mail czy zdjęć uczestników grup.

Zapis ocen, punktów i właściwości węzłów sprawdzianów (`services/crstests/update_grades`,
`services/crstests/update_points`, `services/crstests/change_grade_node_properties`,
`services/crstests/change_task_node_properties`) jest oznaczony jako **Eksperymentalny**
(zaimplementowany wyłącznie na podstawie dokumentacji USOS API):

- Wymaga aktywnej roli wykładowcy, zgód `staff_perspective` oraz `crstests`,
  włączenia przełącznika w ustawieniach konta i jednorazowego potwierdzenia komunikatu.
- Każdy zapis poprzedza weryfikacja uprawnień węzła (`services/crstests/node2`), okno
  potwierdzenia wskazujące węzeł, studenta i nową wartość oraz pojedyncze żądanie POST
  bez automatycznych ponowień po błędzie sieci lub przekroczeniu czasu (`Uncertain`).
- Po zapisie aplikacja ponownie odczytuje węzeł z USOS w celu autorytatywnego
  potwierdzenia wyniku i zapisuje lokalny wpis audytowy zawierający wyłącznie
  znacznik czasu, nazwę metody, identyfikator węzła i status (bez ocen ani danych osobowych).
