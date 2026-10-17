# Bezpieczeństwo i publikacja

Nie publikuj kluczy USOS, tokenów OAuth, plików podpisujących, danych studenta ani
eksportów API w kodzie, zgłoszeniach, logach czy załącznikach. Release nie zawiera
wspólnego Consumer Secret. Właściciel telefonu wpisuje własne dane na ekranie konta.
Każdy sekret umieszczony w APK można odczytać z pliku aplikacji.

## Kontrole lokalne

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
Nie publikuje artefaktów ani nie uruchamia testów na prawdziwym koncie.
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
