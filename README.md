# HoloVis (Fabric 1.21.4, client-side)

Prawy Shift otwiera menu z kolumnami modułów (przeciągaj panele za nagłówek, PPM / strzałka = ustawienia, pole wyszukiwania).
**L** w menu otwiera edytor HUD (przeciągasz FPS, koordynaty, Spotify i TargetHUD).

## Moduły
- **PVP:** Hitbox, **TargetHUD** (serca jako pasek + liczba na górze, pancerz), **Kill Effect**
  (duch lecący w górę – czas i kolor; piorun + wyrzut gracza w górę – wysokość i kolor pioruna), **TestBot**.
- **Dźwięki:** Hit Sound, Kill Sound, Click Sound (menu + machnięcie ręką). Własne .wav: przeciągnij na okno gry
  przy otwartym menu albo wrzuć do `config/holovis/sounds`.
- **Świat:** Custom Sky (tylko Overworld).
- **HUD:** Watermark, Koordynaty, Spotify, Edytor HUD.
- **Klient:** Akcent GUI, Gradient, Tło GUI, Kolor napisu, **Discord**, **Config**.

## Komendy
- `/holovis` – otwiera menu
- `/holovis ghost` – tworzy ducha gracza „TestBot” widocznego tylko dla Ciebie (możesz go bić; nic nie idzie na serwer)
- `/holovis ghost remove` – usuwa go
- `/holovis killtest` – podgląd Kill Effectu

## Spotify
Windows 10/11: odczyt i sterowanie przez systemową sesję multimediów (bez logowania).
Otwórz czat i najedź na kartę Spotify: tekst piosenki (lrclib.net), pasek przewijania (przeciąganie) i przyciski.
Skróty (Sterowanie → HoloVis): `[` poprzedni, `]` następny, `\` pauza/play.

## Discord Rich Presence
1. Wejdź na https://discord.com/developers/applications i utwórz aplikację o nazwie **HoloVisuals**.
2. Skopiuj *Application ID*.
3. W menu: Klient → Discord → „Wklej ID ze schowka” (albo wpisz go w `DiscordRpc.DEFAULT_APP_ID`).
Na profilu: „HoloVisuals”, „Gra na <ip serwera>”, „Minecraft 1.21.4”.

## Configi
Klient → Config: **Kopiuj kod** (kod `HV1:...` trafia do schowka) i **Load config** (wczytuje kod ze schowka).
Ustawienia Discorda zostają lokalne.
