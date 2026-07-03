# FullScreendy

Ein Vollbild-Kiosk-Browser (Chromium/WebView) für Android-Wandtablets, der ein
FHEM-Dashboard anzeigt und das Tablet über **MQTT** als FHEM-Gerät anbindet.

- 🔋 **Akku & Laden** – Ladestand, Ladezustand, Stecker-Typ, Temperatur
- 🔊 **Text-to-Speech** – FHEM schickt Text, das Tablet spricht ihn
- 🔈 **Töne** – gespeicherte Sounddateien im Hintergrund abspielen
- 🖥️ **Bildschirm-Steuerung** – an/aus (Overlay) und Helligkeit
- 🚶 **Präsenz / Bewegung** – Kamera-Bewegungserkennung, weckt das Display (kein Bild verlässt das Gerät)
- 🌐 **Fernsteuerung** – URL wechseln, neu laden, Cache leeren – alles per MQTT
- 🗂️ **Menü** – von links einwischen: Status, Aktionen, Einstellungen (PIN), Hilfe, Über
- 🌍 **Zweisprachig** – Englisch (Standard) / Deutsch

Alles läuft in einem dauerhaften Foreground-Service mit Auto-Reconnect zum MQTT-Broker.

---

## Bauen

Voraussetzung: **Android Studio** (Ladybug o. neuer) mit Android SDK 35.

1. In Android Studio: *File → Open* → diesen Ordner (`fullscreendy`) wählen.
2. Gradle-Sync abwarten (lädt AGP 8.7, Kotlin 2.1, Abhängigkeiten).
3. *Run* auf ein Gerät/Emulator (Android 10+), oder APK bauen:
   *Build → Build Bundle(s)/APK(s) → Build APK(s)*.

```bash
gradle wrapper            # einmalig, legt ./gradlew an
./gradlew assembleDebug    # APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## Erste Einrichtung (auf dem Tablet)

1. App starten. Da noch keine URL konfiguriert ist, öffnen sich direkt die
   **Einstellungen**.
2. Eintragen:
   - *Verbindung*: **Dashboard-URL** (z. B. `http://192.168.1.10:8083/fhem/floorplan/Wohnung`),
     **MQTT-Host/Port** (+ ggf. Benutzer/Passwort/TLS), **Basis-Topic** (Standard
     `fhem/tablet`) und **Geräte-ID** (Standard `tablet1`)
   - *System*: Sprache und Admin-PIN (Standard `0000`)
3. Oben **Speichern**, dann zurück → das Dashboard wird angezeigt.

Kamera- und Benachrichtigungs-Berechtigung beim ersten Start erlauben.

**Menü / zurück in die Einstellungen:** vom **linken Bildschirmrand nach rechts
wischen** → Menü mit MQTT-Status, *Neu laden*, *Cache leeren*, *Bildschirm aus*,
*Einstellungen* (PIN), *Hilfe*, *Über*.

### Als echter Kiosk (optional)
- App als **Standard-Home-App** setzen (Einstellungen → Apps → Standard-Apps →
  Start-App). Dann startet sie nach dem Booten automatisch (sofern *Autostart*
  aktiv ist) und der Home-Button bleibt in der App.
- Für Vollverriegelung ggf. Android **Screen Pinning** aktivieren.

---

## MQTT-Topics

Basis: `<Basis-Topic>/<Geräte-ID>`, im Beispiel `fhem/tablet/tablet1`.

**Das Tablet meldet (retained):**

| Topic | Werte |
|---|---|
| `…/status` | `online` / `offline` (Last-Will) |
| `…/battery` | `0`–`100` |
| `…/charging` | `on` / `off` |
| `…/plug` | `ac` / `usb` / `wireless` / `none` |
| `…/batteryTemp` | z. B. `24.5` |
| `…/motion` | `on` / `off` |
| `…/presence` | `present` / `absent` |
| `…/screen` | `on` / `off` |
| `…/brightness` | `0`–`100` oder `auto` |
| `…/url` | aktuell geladene URL |
| `…/ip` | IPv4-Adresse |
| `…/appVersion` | App-Version, z. B. `0.2.0` |
| `…/androidVersion` | z. B. `13 (SDK 33)` |

**Das Tablet empfängt Befehle (`…/cmd/…`):**

| Topic | Payload | Wirkung |
|---|---|---|
| `cmd/tts` | beliebiger Text | spricht den Text (Aliase: `say`, `speak`) |
| `cmd/mediaplay` | `ordner/sound.mp3` | spielt gespeicherte Tondatei ab (Aliase: `media`, `play`) |
| `cmd/mediastop` | (egal) | stoppt die Wiedergabe |
| `cmd/url` | URL | lädt eine andere Seite und meldet sie als `url`-Reading |
| `cmd/reload` | (egal) | lädt die Seite neu |
| `cmd/clearcache` | (egal) | leert den Browser-Cache |
| `cmd/screen` | `on` / `off` | weckt das Display physisch (an) bzw. schwarzes Overlay (aus) |
| `cmd/screensaver` | `on` / `off` | wie `screen`, invertiert |
| `cmd/brightness` | `0`–`100` oder `auto` | Helligkeit (voller Bereich mit „Helligkeitssteuerung erlauben") |
| `cmd/lock` | (egal) | sperrt den Bildschirm (benötigt Geräteadmin) |
| `cmd/unlock` | (egal) | weckt & löst den (unsicheren) Sperrbildschirm |

**Töne:** Dateien in den Sound-Ordner des Geräts kopieren (Pfad steht in der App
unter *Einstellungen → Töne*, typ. `/sdcard/Android/data/de.kewl.fullscreendy/files/sounds/`),
dann per `cmd/mediaplay` mit relativem Pfad abspielen. `http…`-URLs werden gestreamt,
Pfade mit `/` als absolute Datei behandelt.

---

## FHEM-Konfiguration

Voraussetzung: ein MQTT-Server in FHEM (`MQTT2_SERVER`) **oder** ein
`MQTT2_CLIENT`, der mit deinem Broker (z. B. mosquitto) verbunden ist.

### Gerät anlegen
Sobald das Tablet zum ersten Mal publisht, schlägt FHEM per `autocreate` ein
`MQTT2_DEVICE` vor. Alternativ manuell:

```
define tablet1 MQTT2_DEVICE
attr tablet1 readingList \
  fhem/tablet/tablet1/status:.* status \
  fhem/tablet/tablet1/battery:.* battery \
  fhem/tablet/tablet1/charging:.* charging \
  fhem/tablet/tablet1/plug:.* plug \
  fhem/tablet/tablet1/batteryTemp:.* batteryTemp \
  fhem/tablet/tablet1/motion:.* motion \
  fhem/tablet/tablet1/presence:.* presence \
  fhem/tablet/tablet1/screen:.* screen \
  fhem/tablet/tablet1/brightness:.* brightness \
  fhem/tablet/tablet1/url:.* url \
  fhem/tablet/tablet1/ip:.* ip \
  fhem/tablet/tablet1/appVersion:.* appVersion \
  fhem/tablet/tablet1/androidVersion:.* androidVersion
attr tablet1 setList \
  screenOn:noArg   fhem/tablet/tablet1/cmd/screen on \
  screenOff:noArg  fhem/tablet/tablet1/cmd/screen off \
  reload:noArg     fhem/tablet/tablet1/cmd/reload 1 \
  clearCache:noArg fhem/tablet/tablet1/cmd/clearcache 1 \
  lock:noArg       fhem/tablet/tablet1/cmd/lock 1 \
  unlock:noArg     fhem/tablet/tablet1/cmd/unlock 1 \
  mediaStop:noArg  fhem/tablet/tablet1/cmd/mediastop 1 \
  brightness:slider,0,1,100 fhem/tablet/tablet1/cmd/brightness $EVTPART1 \
  url:textField       fhem/tablet/tablet1/cmd/url $EVTPART1 \
  mediaPlay:textField fhem/tablet/tablet1/cmd/mediaplay $EVTPART1
attr tablet1 stateFormat online battery %
```

`$EVTPART1` reicht für Werte ohne Leerzeichen (Zahl, URL, Dateipfad).

### Text-to-Speech (ganzer Satz mit Leerzeichen)
`setList` zerlegt Argumente wortweise, daher für freien Text ein Perl-Ausdruck,
der `$EVENT` ab dem ersten Leerzeichen abschneidet:

```
attr tablet1 setList {...bisherige Zeilen...} \
  say:textField {my @a=split(" ",$EVENT);; shift(@a);; return "fhem/tablet/tablet1/cmd/tts ".join(" ",@a)}
```

Alternativ direkt publizieren (`<IODev>` = dein MQTT2_SERVER/CLIENT):

```
set MQTT2_Broker publish fhem/tablet/tablet1/cmd/tts Guten Morgen, es sind 8 Grad
```

### Beispiele
```
# Bei Bewegung Licht an
define n_flur_licht notify tablet1:motion:.on set Flurlicht on

# Klingel-Sound abspielen (Datei im Sound-Ordner)
set MQTT2_Broker publish fhem/tablet/tablet1/cmd/mediaplay tuerklingel.mp3
```

---

## Geräte-Einstellungen & Berechtigungen (v0.3)

Unter *Einstellungen → Verhalten*:
- **Bewegungs-Empfindlichkeit** (Schieberegler) für die Kamera-Erkennung.
- **Wecken bei Ton (Mikrofon)** mit **Ton-Empfindlichkeit** – lauter Umgebungsschall
  weckt das Display (nur Lautstärke, keine Aufnahme).

Unter *Einstellungen → Anzeige*:
- **Bildschirm immer an** ist jetzt **standardmäßig aus**.

Unter *Einstellungen → System → Berechtigungen* (einmalig erteilen):
- **Geräteadmin aktivieren** → nötig für `cmd/lock`.
- **Helligkeitssteuerung erlauben** (WRITE_SETTINGS) → echte Hardware-Helligkeit
  über den vollen Bereich (behebt „nur bis ~60 %").
- **Als Home-App festlegen** → zuverlässiger Autostart nach dem Booten.

## Architektur (Kurzüberblick)

```
MainActivity ──── WebView (Vollbild-Dashboard) + Menü/Overlay/Helligkeit
      ▲  KioskBus (Befehle)
      │
KioskService (Foreground) ── MqttManager (Paho, Auto-Reconnect, Last-Will)
      ├── BatteryMonitor   → …/battery, …/charging, …
      ├── TtsManager       ← …/cmd/tts
      ├── MediaManager     ← …/cmd/mediaplay
      └── MotionDetector   → …/motion, …/presence   (CameraX, Luminanz-Diff)
```

Einstellungen liegen in Jetpack DataStore; alle Topics werden daraus abgeleitet.

---

## Bekannte Grenzen / To-do
- `cmd/unlock` weckt und löst nur einen **unsicheren** Sperrbildschirm; eine
  gesicherte PIN/Muster kann aus Sicherheitsgründen nicht umgangen werden.
- **Bewegungs-/Ton-Weckung** brauchen Kamera/Mikrofon im Vordergrund (Kiosk-Fall).
  Startet das Display per Zeitüberschreitung komplett durch, wecke lieber per
  `cmd/screen on` (Wakelock) – das funktioniert immer.
- Kamera/Mikrofon starten nur, wenn die App im Vordergrund war; nach reinem
  Boot ohne Öffnen bleiben sie aus (Android-Hintergrund-Restriktion).
- Autostart nach Boot ist ohne **Home-App/OEM-Autostart** unzuverlässig
  (Android blockiert Hintergrund-Activity-Starts).
- TLS nutzt den System-Truststore; selbstsignierte Broker-Zertifikate müssten
  ergänzt werden.
- `cmd/volume` (Lautstärke für TTS/Media) ist noch nicht umgesetzt.
