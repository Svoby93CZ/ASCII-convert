# Podklady pro Google Play

| Soubor | Obsah |
| --- | --- |
| `listing.md` | Texty záznamu v obchodu (čeština a angličtina) a přehled grafiky |
| `privacy-policy.md` | Zásady ochrany soukromí k vystavení na veřejné adrese |
| `icon-512.png` | Ikona aplikace 512×512 px |
| `feature-graphic-cs.png`, `feature-graphic-en.png` | Hlavní obrázek 1024×500 px |
| `make_graphics.py` | Znovu vykreslí ikonu a hlavní obrázky (potřebuje Chromium) |

## 1. Zásady ochrany soukromí

Google Play je vyžaduje u každé aplikace, a to na veřejné adrese i přímo v aplikaci. V aplikaci
jsou v dialogu *O aplikaci → Zásady ochrany soukromí*. Repozitář je soukromý, proto je na webu
vystavte jako veřejný gist:

1. Otevřete <https://gist.github.com>.
2. Soubor pojmenujte `ascii-studio-privacy-policy.md` a vložte do něj obsah `privacy-policy.md`.
   Doplňte jméno vývojáře a kontaktní e-mail místo textů v hranatých závorkách.
3. Klikněte na **Create public gist** a zkopírujte adresu stránky.
4. V Play Console otevřete **Obsah aplikace → Zásady ochrany soukromí**, vložte adresu a uložte.

Při změně zásad upravte gist, `privacy-policy.md` i text `privacy_policy_text` v aplikaci.
Android CI hlídá, že aplikace nemá oprávnění k internetu, protože to zásady slibují.

## 2. Obsah aplikace

Odpovědi pro dotazníky v Play Console (**Obsah aplikace**):

| Dotazník | Odpověď |
| --- | --- |
| Přístup k aplikaci | Všechny funkce jsou dostupné bez omezení, bez přihlášení |
| Reklamy | Aplikace neobsahuje reklamy |
| Hodnocení obsahu | Kategorie „nástroje, produktivita, komunikace nebo jiné“, na všechny otázky **Ne** |
| Cílové publikum | Věk 13 a více let (13–15, 16–17, 18+), aplikace není určená dětem |
| Zabezpečení dat | Aplikace neshromažďuje ani nesdílí žádné údaje: na první otázku **Ne** |
| Reklamní ID | Aplikace reklamní ID nepoužívá |
| Vládní aplikace | Ne |
| Finanční funkce | Aplikace nemá finanční funkce |
| Zdraví | Aplikace nemá zdravotní funkce |

Zabezpečení dat: Google za shromažďování považuje jen údaje, které opustí zařízení. ASCII Studio
zpracovává vše v telefonu a k internetu nemá přístup.

## 3. Záznam v obchodu

Texty jsou v `listing.md`, grafika v této složce. Snímky obrazovky pořiďte v telefonu s vlastními
fotkami (2 až 8 snímků, ideálně aspoň 4): úvodní obrazovka, editor s fotkou, Braille nebo barvy
z fotky, živá kamera a nabídka exportu.

## 4. Vydání

1. Na GitHubu spusťte **Actions → Android CI → Run workflow** na větvi `main`.
2. Z běhu stáhněte artefakt `ascii-studio-play-bundle` a rozbalte ho.
3. V Play Console vytvořte vydání v testovací skladbě, nahrajte `app-release.aab` a zaveďte ho.

Postup od interního testování ke zveřejnění:

- **Interní testování:** až 100 testerů podle e-mailu, verze je k dispozici obvykle během pár
  minut. Hodí se pro vlastní telefon.
- **Uzavřené testování:** osobní vývojářské účty založené po 13. 11. 2023 musí mít před
  zveřejněním uzavřený test s alespoň 12 testery, kteří jsou přihlášení 14 dní bez přerušení.
- **Produkce:** po uzavřeném testu požádejte na nástěnce Play Console o přístup do produkce
  a pak vydání z testu povyšte do produkce.

Aplikaci z Google Play podepisuje Google. Verzi nainstalovanou z APK proto před instalací
z Google Play odinstalujte.
