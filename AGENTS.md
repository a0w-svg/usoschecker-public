# Wydania po większych poprawkach

Użytkownik wymaga nowego publicznego GitHub Release po każdym większym zestawie
napraw błędów. Traktuj publikację jako część zadania, bez ponownego pytania o zgodę.

- Zwiększ `versionName` (dla napraw zwykle PATCH) i zawsze zwiększ `versionCode`.
- Uruchom właściwe testy regresji, lint, kontrolę sekretów i build release.
- Scal poprawki przez PR po przejściu wymaganych kontroli CI; nie omijaj ochrony gałęzi.
- Publikuj tag przypisany do sprawdzonego commita i nowy release, zachowując stare wydania.
- Dołącz instalowalny APK podpisany tym samym prywatnym kluczem release,
  archiwum źródeł, sumy SHA-256 i opis napraw oraz wykonanych kontroli.
- Sprawdź podpis, numer wersji i brak sekretów w artefaktach przed wysłaniem.
- Nigdy nie publikuj klucza podpisującego, jego hasła, lokalnych credentials,
  osobistego debug APK ani prywatnego mapowania R8.
- Jeśli brakuje istniejącego klucza podpisującego, nie zastępuj go nowym:
  zgłoś blokadę i zachowaj ciągłość podpisu aktualizacji.

Repozytorium publikacji: https://github.com/a0w-svg/usoschecker-public.
Zasady bezpieczeństwa i przygotowania wydania: `SECURITY.md` oraz `RELEASE.md`.
