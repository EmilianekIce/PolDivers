# PolDivers

Nieoficjalny companion do Helldivers 2 (dane, kampanie, DSS, archiwum) -- na razie apka na Androida, docelowo też strona internetowa oparta o te same źródła danych.

## Struktura repo

- `Android/` -- natywna apka (Kotlin + Jetpack Compose). Zobacz `Android/app/build.gradle.kts`.
- `Web/` -- (wkrótce) wersja webowa.

## Budowanie i instalacja

- Lokalnie: `cd Android && ./gradlew :app:assembleDebug` → `Android/app/build/outputs/apk/debug/app-debug.apk`.
- Bez Android Studio: każdy push zmieniający `Android/` uruchamia workflow **Android** (GitHub Actions), który odpala testy i buduje APK. Gotowy plik jest w zakładce *Actions* → ostatni run → *Artifacts* → `PolDivers-debug-apk` (zip z `app-debug.apk` do zainstalowania na telefonie).

## Źródła danych

- [`api.helldivers2.dev`](https://helldivers-2.github.io/api/) -- community API wrapper wokół danych z gry (planety, kampanie, major ordery, DSS, dispatch'e). Zwraca teksty po polsku przy `Accept-Language: pl-PL`, bez ręcznego tłumaczenia.
- [`helldivers.wiki.gg`](https://helldivers.wiki.gg) -- MediaWiki API (`/api.php`), odpytywane na żądanie (wyszukiwarka + pojedynczy wpis po kliknięciu) jako archiwum broni/wrogów/stratagemów. Treść na licencji CC BY-SA -- wymaga atrybucji.

- [`helldivers-2/json`](https://github.com/helldivers-2/json) -- community tabele znaczeń nieudokumentowanych pól (typy zadań Major Orderów, frakcje); wbudowane w kod, nie pobierane w runtime.

Obie inspiracje wizualne (nie źródła danych): [truthenforcers.com](https://truthenforcers.com), [helldiverscompanion.com](https://helldiverscompanion.com).
