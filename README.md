# ASCII Studio

Moderní Android aplikace, která z fotek a obrázků vytváří ASCII art. Vše běží přímo
v telefonu, bez internetu a bez odesílání dat.

```
                  $@@@$$$$$$@@@@$
              $$$$$###*****###$$$$$$$
           #$$###**!!!!!!!*!!!**##$$$$$
         *####**!*!!=;;;;;;==!!!**###$$$#
        ######**!==::~-----~::=!!!*###$$$#*
       *#####**!=;:-,.     .,~:;=!**#######!
      *######**!;:-.         .~:=!**#######*
      *#######*!=:,           ~;!**########*=
     ;*###$$$$##*!:          ~=!*#########**=
     ;!*##$$$$$$$#*!        =**##########**!=
     :=**##$$$$$$$$$$$#######$$$$$$#####**!=:
      ;=!*##$$$$@@@@@@@@$$$$$$$$$$####**!!=;
      ~=!!**###$$$@@@@@@@$$$$$$$####***!!=;,
       -;=!!**#####$$$$$$$$$####*****!!!=;
         ~;=!!*****########****!!!*!!=;:~
          .~;;===*!*****!!!!*!!!!==;;:-
             ,-::;;======!===;=;::~-
                  ,--~~~~~~~~-,,
```

*Animovaný „donut“ z úvodní obrazovky – klasika ASCII artu, vykreslená stejným rendererem jako vaše fotky.*

## Funkce

- **Fotky z galerie** přes systémový výběr fotek (nepotřebuje žádné oprávnění) a **sdílení
  obrázků** do aplikace z galerie, prohlížeče nebo chatu.
- **Živá ASCII kamera** – obraz z kamery se převádí na ASCII art v reálném čase, přepínání
  přední/zadní kamery, fotka v plném rozlišení rovnou do editoru.
- **Editor s okamžitým náhledem** – přibližování dvěma prsty, posun, dvojité klepnutí,
  porovnání s originálem.
- **Sady znaků:** standardní, detailní (28 úrovní), bloky `░▒▓█`, **Braille** (2×4 body na
  znak = dvojnásobné rozlišení), binární `0/1` a vlastní znaky.
- **Kalibrované tóny:** hustota „inkoustu“ každého znaku je změřená ve vestavěném písmu
  JetBrains Mono, takže přechody odpovídají fotce. Vlastní znaky se proměří a seřadí
  automaticky.
- **Úpravy tónu:** automatické úrovně, jas, kontrast, ostrost, invertování.
- **Dithering:** Floyd–Steinberg, Atkinson a Bayer.
- **Obrysy:** detekce hran (Sobel + tenzor struktury) kreslí linky znaky `| / - \`,
  v kombinaci s tóny nebo samostatně.
- **Barvy:** 7 palet (Terminál, Jantar, Noc, Synthwave, Modrotisk, Papír, Inkoust) nebo
  barvy z fotky pro každý znak zvlášť.
- **Export:** kopírování textu, sdílení textu nebo PNG, uložení do galerie
  (`Obrázky/ASCII Studio`), TXT, barevné HTML a ANSI pro terminál (`cat obrazek.ans`).
- Material 3 s barvami podle tapety (Android 12+), tmavý režim, edge-to-edge, prediktivní
  gesto zpět, rozložení pro tablety a na šířku, čeština a angličtina (jazyk lze nastavit
  pro aplikaci zvlášť v nastavení Androidu 13+).
- Nastavení se pamatuje a rozpracovaný obrázek přežije i ukončení aplikace systémem.

## Instalace hotového APK

Každý push spustí GitHub Actions, které aplikaci otestují a sestaví:

1. Na GitHubu otevřete záložku **Actions → Android CI** a poslední úspěšný běh.
2. Dole v sekci **Artifacts** stáhněte `ascii-studio-apk` a rozbalte ho.
3. Do telefonu nahrajte `app-release.apk` (optimalizovaná verze, doporučeno) a nainstalujte
   ho (Android se zeptá na povolení instalace z neznámých zdrojů).

Z počítače to jde i přes USB (v telefonu zapněte *Možnosti pro vývojáře → Ladění přes USB*):

```bash
sudo apt install adb
adb install -r app-release.apk
```

Debug i release buildy podepisuje stejný sdílený ladicí klíč (`app/debug.keystore`), takže
novější verze jde vždy nainstalovat přes starší. Pro vydání na Google Play použijte vlastní
klíč (viz níže).

## Sestavení ze zdrojových kódů

Požadavky: JDK 17 nebo novější a Android SDK (platforma 37). Nejjednodušší je
[Android Studio](https://developer.android.com/studio) – obsahuje JDK i SDK. Stačí otevřít
složku projektu a spustit konfiguraci `app`.

Z příkazové řádky (Pop!_OS / Ubuntu):

```bash
sudo apt install openjdk-21-jdk
export ANDROID_HOME="$HOME/Android/Sdk"   # cesta k SDK z Android Studia

./gradlew :engine:test                 # testy převodního enginu (běží i bez Android SDK)
./gradlew :app:testDebugUnitTest       # testy aplikace
./gradlew :app:assembleRelease         # APK v app/build/outputs/apk/release/
./gradlew :app:installDebug            # sestaví a nainstaluje do připojeného telefonu
```

Tipy pro notebook s 8 GB RAM:

- `gradle.properties` omezuje paměť Gradlu na 2 GB, aby vedle běželo i Android Studio.
- Místo emulátoru testujte na skutečném telefonu přes USB – emulátor si vezme další
  2–4 GB paměti a na dvoujádrovém procesoru je pomalý.
- Při sestavování zavřete prohlížeč s mnoha panely; první build stahuje závislosti
  a trvá několik minut, další jsou díky cache rychlé.

### Podpis vlastním klíčem

Vytvořte klíč a soubor `keystore.properties` v kořeni projektu (je v `.gitignore`):

```bash
keytool -genkeypair -v -keystore release.jks -alias ascii-studio -keyalg RSA -keysize 4096 -validity 10000
```

```properties
storeFile=release.jks
storePassword=…
keyAlias=ascii-studio
keyPassword=…
```

`./gradlew :app:assembleRelease` pak podepíše APK tímto klíčem.

## Architektura

```
ASCII-convert/
├── engine/   Čistý Kotlin (JVM), žádná závislost na Androidu
│   ├── AsciiConverter   vzorkování → tóny → dithering → znaky (+ obrysy, Braille)
│   ├── CharRamp(s)      sady znaků s naměřenou hustotou inkoustu
│   ├── AsciiExport      HTML a ANSI export
│   └── Donut            animace z úvodní obrazovky
└── app/      Android aplikace (Jetpack Compose)
    ├── data/     nastavení (DataStore), načítání obrázků, export a sdílení
    ├── render/   kreslení ASCII artu na Canvas, měření znaků vlastního písma
    └── ui/       domovská obrazovka, editor, živá kamera, téma
```

- **Engine** pracuje s polem pixelů (`IntArray` ve formátu ARGB), takže je rychlý,
  deterministický a testovatelný na běžné JVM. Převod fotky o šířce 110 znaků trvá jednotky
  milisekund, 300 znaků v režimu Braille s ditheringem kolem 40 ms.
- **UI** je jednosměrné (UDF): `ViewModel` skládá `StateFlow` obrázku a nastavení, převod
  běží na pozadí přes `mapLatest`, takže posuvníky reagují okamžitě a zastaralé výsledky se
  zahodí.
- **Renderer** kreslí každý řádek jedním voláním `drawText`. Barvy z fotky zajišťuje
  `BitmapShader` s jedním pixelem na znak (nearest-neighbour), takže i barevný náhled se
  vykresluje stejně rychle jako jednobarevný. Braille se kreslí jako skutečné body, proto je
  ostrý a zarovnaný na všech zařízeních.
- **Kamera** (CameraX): analýza snímků v nízkém rozlišení pro živý náhled a samostatné
  `ImageCapture` pro ostrou fotku.
- Závislosti se předávají ručně přes `AppContainer` – pro aplikaci této velikosti je to
  jednodušší a rychlejší na sestavení než DI framework.

Technologie: Kotlin 2.4, Jetpack Compose (Material 3), Navigation Compose, Lifecycle,
DataStore, CameraX, Coroutines/Flow, Android Gradle Plugin 9, Gradle 9, minSdk 26
(Android 8.0), targetSdk 37.

## Testy a CI

- `./gradlew :engine:test` – testy převodu (tóny, dithering, obrysy, Braille, export).
- `./gradlew :app:testDebugUnitTest` – ukládání nastavení přes skutečný DataStore.
- **Android CI** (GitHub Actions) při každém pushi spustí testy a lint a sestaví debug
  i release APK.
- **Emulator smoke test** projde aplikaci v Android emulátoru: výběr fotky, sdílení, editor,
  export, otočení na šířku, živá kamera, čeština a tmavý režim. Při pádu aplikace selže.
  Spouští se ručně v záložce *Actions* nebo pushem commitu, který má v popisu `[emulator]`.

## Licence třetích stran

- Písmo [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) – SIL Open Font
  License 1.1 (text licence je v `app/src/main/assets/licenses/`).
- Ikony [Material Symbols](https://github.com/google/material-design-icons) – Apache
  License 2.0.
