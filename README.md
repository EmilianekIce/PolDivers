# PolDivers

Nieoficjalny companion do Helldivers 2 (dane, kampanie, DSS, archiwum) -- na razie apka na Androida, docelowo też strona internetowa oparta o te same źródła danych.

## Struktura repo

- `Android/` -- natywna apka (Kotlin + Jetpack Compose). Zobacz `Android/app/build.gradle.kts`.
- `Web/` -- (wkrótce) wersja webowa.

## Budowanie i instalacja

- Lokalnie: `cd Android && ./gradlew :app:assembleDebug` → `Android/app/build/outputs/apk/debug/app-debug.apk`.
- Bez Android Studio: każdy push zmieniający `Android/` uruchamia workflow **Android** (GitHub Actions), który odpala testy i buduje APK. Gotowy plik jest w zakładce *Actions* → ostatni run → *Artifacts* → `PolDivers-debug-apk` (zip z `app-debug.apk` do zainstalowania na telefonie).

## Aktualizacje w apce

Każdy push zmieniający `Android/` na `main` lub `claude/*` publikuje podpisane APK jako GitHub Release (`build-<numer>`). Apka przy starcie sprawdza najnowsze wydanie i pokazuje pasek **Aktualizuj** (także: Ustawienia → Aktualizacje). Wymaga sekretów repo `POLDIVERS_KEYSTORE_B64` i `POLDIVERS_KEYSTORE_PASSWORD` (klucz podpisu — bez nich wydania nie powstają). Klucza nigdy nie commitujemy.

## Prognozy (tempo, ETA, Major Ordery)

API zwraca tylko bieżący stan, bez historii. Apka zapisuje kolejne odczyty (co minutę, gdy ekran jest otwarty; historia do 4 h w pamięci telefonu) i z nich liczy tempo %/h, czas do wyzwolenia, wynik obrony przed jej końcem, tempo zbiórki DSS oraz prognozę Major Orderów — tak samo jak helldiverscompanion.com (ekstrapolacja przyrostu z ostatnich minut).

## Grafiki

Ikony (stratagemy, warianty wrogów, nagrody, DSS, typy misji, warunki środowiskowe) i nagłówki kampanii są wbudowane w `Android/app/src/main/assets/` — przekonwertowane skryptem `Android/tools/import_wiki_assets.py` ze zrzutu obrazów helldivers.wiki.gg. Obrazki planet (duże) apka pobiera z wiki dopiero po otwarciu szczegółów planety.

## Źródła danych

- [`api.helldivers2.dev`](https://helldivers-2.github.io/api/) -- community API wrapper wokół danych z gry (planety, kampanie, major ordery, DSS, dispatch'e). Zwraca teksty po polsku przy `Accept-Language: pl-PL`, bez ręcznego tłumaczenia.
- [`helldivers.wiki.gg`](https://helldivers.wiki.gg) -- MediaWiki API (`/api.php`), odpytywane na żądanie (wyszukiwarka + pojedynczy wpis po kliknięciu) jako archiwum broni/wrogów/stratagemów. Treść na licencji CC BY-SA -- wymaga atrybucji.

- [`helldivers-2/json`](https://github.com/helldivers-2/json) -- community tabele znaczeń nieudokumentowanych pól (typy zadań Major Orderów, frakcje); wbudowane w kod, nie pobierane w runtime.

Obie inspiracje wizualne (nie źródła danych): [truthenforcers.com](https://truthenforcers.com), [helldiverscompanion.com](https://helldiverscompanion.com).
