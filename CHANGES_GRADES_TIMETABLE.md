# Bieżące zmiany w ocenach i planie zajęć — 13.10.2026

## Oceny

- Wpisy są grupowane według nazwy przedmiotu i semestru. Każdy termin egzaminu pozostaje osobnym wpisem.
- Filtry: wszystkie oceny, liczbowe, pozostałe; współpracują z wyborem semestru i wyszukiwaniem.
- Filtry i sortowanie są w rozwijanym panelu. Po jego zamknięciu nadal widać informację o aktywnych ustawieniach, a większa część ekranu jest przeznaczona na oceny.
- Sortowanie grup według nazwy przedmiotu, najnowszej modyfikacji lub najwyższej widocznej oceny liczbowej. Wybór sortowania jest zapamiętywany.
- Wartość oceny ma osobny wyróżniony kafelek. Termin i skrócony komentarz są obok; pełne dane otwierają się po dotknięciu wpisu.
- Szczegóły oceny pozwalają rozpocząć dodawanie zadania z nazwą przedmiotu.
- Kompaktowe podsumowanie pokazuje średnią i liczbę wpisów liczbowych/pozostałych. Przycisk „?” otwiera minimum, maksimum oraz wyjaśnienie obliczeń; średnia pozostaje nieważona i nieoficjalna.
- Brak wyników udostępnia przycisk wyczyszczenia filtrów. Ekran pokazuje błąd ostatniej synchronizacji i przycisk odświeżenia.
- Wyszukiwanie ocen i planu jest niezależne. Filtry i tekst wyszukiwania ocen są zachowywane po odtworzeniu ekranu.
- Krótsza etykieta wyszukiwania i dwie linie liczników poprawiają układ przy powiększonej czcionce; pełny zakres wyszukiwania jest opisany dla czytnika ekranu.

## Plan zajęć

- Nowy widok „Lista tygodnia”, obok osi dnia i agendy całego zapisu.
- Wybór daty w granicach zapisanego planu oraz skrót do kolejnego dnia z zajęciami.
- Wybór dni tygodnia przewija się poziomo na węższych ekranach; przyciski zachowują szerokość co najmniej 48 dp i oznaczenie wybranego dnia.
- Podsumowanie dnia: liczba zajęć, czas zajęć bez podwójnego liczenia nakładających się spotkań, przerwy, liczba nakładających się par i terminów orientacyjnych. Podsumowanie dotyczy widocznych wyników.
- Eksport `.ics` respektuje wybrany dzień/tydzień/agendę i wyszukiwanie.
- Karty listy wyróżniają godziny, pokazują czas trwania i oznaczają spotkania kończące się następnego dnia.
- Oś dnia używa spójnych zielonych kolorów; terminy orientacyjne mają żółte tło i etykietę tekstową. Bloki pokazują salę, a pełne szczegóły pozostają dostępne po dotknięciu.
- Godziny bloków mają format `HH:mm`; etykiety godzin uwzględniają skalę czcionki. Etykiety siatki nie są ponownie formatowane przy każdym rysowaniu.
- Brak pasujących wyników udostępnia przycisk wyczyszczenia wyszukiwania planu.
- Krótszy nagłówek i przycisk „Data” ograniczają liczbę wierszy na węższych ekranach; przycisk ma pełny opis dostępności.

## Sprawdzenie bieżących zmian

- Testy filtrowania, sortowania i grupowania ocen oraz obliczania czasu, przerw i nakładania zajęć.
- Testy Androida obejmują niezależne wyszukiwanie, zachowanie filtrów po odtworzeniu Activity, szczegóły oceny i eksport tylko wybranego tygodnia.
- Sprawdzenie układu na ekranie 900×2000 przy skali czcionki 130%, w osobnej instalacji testowej.
- Wyniki: 58 testów JVM bez błędów; pełny zestaw 25 testów Androida przeszedł. Końcowe zmiany etykiet i podsumowania dodatkowo przeszły test interakcji ocen/planów. APK i lint zbudowano bez błędów.
- Podglądy końcowych widoków: `build/grades-final-clean.png` i `build/timetable-final-clean.png`.
- APK: `dist/usoschecker-grades-plan-debug.apk`.
