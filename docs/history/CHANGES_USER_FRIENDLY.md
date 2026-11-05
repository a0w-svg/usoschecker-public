# Bieżące usprawnienia obsługi — 14.10.2026

- Oceny pojawiają się przed podsumowaniem, a przycisk resetowania aktywnych filtrów jest dostępny również przy znalezionych wynikach.
- Wyszukiwanie ocen i planu działa z polskimi znakami lub ich odpowiednikami, np. `finansow`, `cwiczenia`, `Lodz`.
- Pola wyszukiwania mają przycisk czyszczenia. Przycisk wyszukiwania na klawiaturze zamyka ją, pozostawiając wyniki na ekranie.
- Domyślny widok planu to „Lista dnia”. Można wybrać oś czasu, tydzień lub agendę; preferowany widok jest zapamiętywany.
- Karty listy dnia pokazują aktualne zajęcia i pozostały czas bez powielania osobnej karty tych samych zajęć.
- Podsumowanie dnia jest pod zajęciami. Pusta oś dnia nie tworzy wielogodzinnej pustej siatki.
- Pusty dzień proponuje przejście do konkretnej kolejnej daty. Nawigacja respektuje wyszukiwanie i zapisany zakres planu.
- Przy braku pasujących kolejnych zajęć przycisk nawigacji jest wyłączony. Eksport jest wyłączony przy pustym wyniku.
- Po ręcznym odświeżeniu komunikat rozróżnia pierwsze pobranie, brak nowych zmian i liczbę wykrytych zmian. Po udanym odświeżeniu zachowywana jest pozycja przewinięcia.

## Weryfikacja

- 63 testy JVM przeszły, w tym wyszukiwanie bez znaków diakrytycznych, zakres tygodnia, zajęcia przez północ i kolejna pasująca data.
- Pełny zestaw 26 testów Androida przeszedł w osobnej instalacji QA. Nowy test sprawdza kolejność ocen/podsumowania, przycisk czyszczenia i nawigację przy aktywnym wyszukiwaniu.
- Podglądy: `build/friendly-grades.png` i `build/friendly-plan.png`.
- Kompilacja APK i lint: bez błędów.
- APK: `dist/usoschecker-friendly-debug.apk`.
