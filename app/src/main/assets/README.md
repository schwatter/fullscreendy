# FullScreendy

Ein Vollbild-Kiosk-Browser (Chromium/WebView) für Android-Wandtablets, der ein
FHEM-Dashboard anzeigt und das Tablet über **MQTT** als FHEM-Gerät anbindet:

- 🔋 **Akku & Laden** – Ladestand, Ladezustand, Stecker-Typ, Temperatur
- 🔊 **Text-to-Speech** – FHEM schickt Text, das Tablet spricht ihn (Deutsch)
- 🖥️ **Bildschirm-Steuerung** – an/aus (schwarzes Overlay) und Helligkeit
- 🚶 **Präsenz / Bewegung** – Kamera-basierte Bewegungserkennung (kein Bild verlässt das Gerät)

Alles läuft in einem dauerhaften Foreground-Service mit Auto-Reconnect zum MQTT-Broker.

---

## Bauen

Voraussetzung: **Android Studio** (Ladybug o. neuer) mit Android SDK 35.

1. In Android Studio: *File → Open* → diesen Ordner (`fullscreendy`) wählen.
2. Gradle-Sync abwarten (lädt AGP 8.7, Kotlin 2.1, Abhängigkeiten).
3. *Run* auf ein Gerät/Emulator (Android 10+), oder APK bauen:
   *Build → Build Bundle(s)/APK(s) → Build APK(s)*.

Per Kommandozeile (falls Gradle installiert ist, erzeugt zuerst den Wrapper):

```bash
gradle wrapper           # einmalig, legt ./gradlew an
./gradlew assembleDebug   # APK: app/build/outputs/apk/debug/app-debug.apk
```

> Hinweis: Diese Umgebung hat kein Android SDK – der Build läuft auf deinem Rechner.

---

## Erste Einrichtung (auf dem Tablet)

1. App starten. Da noch keine URL konfiguriert ist, öffnet sich direkt der
   **Einstellungen**-Dialog.
2. Eintragen:
   - **Dashboard-URL** (z. B. `http://192.168.1.10:8083/fhem/floorplan/Wohnung`)
   - **MQTT-Host/Port**, ggf. Benutzer/Passwort und TLS
   - **Basis-Topic** (Standard `fhem/tablet`) und **Geräte-ID** (Standard `tablet1`)
   - Kamera-Bewegungserkennung, „Bildschirm immer an“, Admin-PIN
3. *Speichern & starten* → das Dashboard wird angezeigt.

Kamera- und Benachrichtigungs-Berechtigung beim ersten Start erlauben.

**Zurück in die Einstellungen:** oben links lange in die Ecke (ca. 2 Sek.)
drücken → Admin-PIN eingeben (Standard `0000`).

### Als echter Kiosk (optional)
- App als **Standard-Home-App** setzen (Einstellungen → Apps → Standard-Apps →
  Start-App). Dann startet sie nach dem Booten automatisch und der Home-Button
  bleibt in der App.
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

**Das Tablet empfängt Befehle:**

| Topic | Payload | Wirkung |
|---|---|---|
| `…/cmd/tts` | beliebiger Text | spricht den Text |
| `…/cmd/url` | URL | lädt eine andere Seite |
| `…/cmd/reload` | (egal) | lädt die Seite neu |
| `…/cmd/screen` | `on` / `off` | Bildschirm an / schwarzes Overlay |
| `…/cmd/brightness` | `0`–`100` oder `auto` | Helligkeit |
| `…/cmd/screensaver` | `on` / `off` | wie screen, invertiert |

---

## FHEM-Konfiguration

Voraussetzung: ein MQTT-Server in FHEM (`MQTT2_SERVER`) **oder** ein
`MQTT2_CLIENT`, der mit deinem Broker (z. B. mosquitto) verbunden ist.

### Gerät automatisch anlegen
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
  fhem/tablet/tablet1/screen:.* screen
attr tablet1 setList \
  screenOn:noArg   fhem/tablet/tablet1/cmd/screen on \
  screenOff:noArg  fhem/tablet/tablet1/cmd/screen off \
  reload:noArg     fhem/tablet/tablet1/cmd/reload 1 \
  brightness:slider,0,1,100 fhem/tablet/tablet1/cmd/brightness $EVTPART1 \
  url:textField    fhem/tablet/tablet1/cmd/url $EVTPART1
attr tablet1 stateFormat online battery %
```

### Text-to-Speech (freier Text mit Leerzeichen)
Da `setList` Argumente wortweise zerlegt, ist für ganze Sätze das direkte
Publish am zuverlässigsten (`<IODev>` = dein MQTT2_SERVER/CLIENT):

```
set MQTT2_Broker publish fhem/tablet/tablet1/cmd/tts Guten Morgen, es sind 8 Grad
```

Als bequemer Befehl am Gerät, z. B. in `99_myUtils.pm` oder per notify:

```
set MQTT2_Broker publish fhem/tablet/tablet1/cmd/tts [wetter:temperatur] Grad draussen
```

### Beispiel: bei Bewegung Licht an
```
define n_flur_licht notify tablet1:motion:.on set Flurlicht on
```

---

## Architektur (Kurzüberblick)

```
MainActivity ──── WebView (Vollbild-Dashboard) + Overlay/Helligkeit
      ▲  KioskBus (Befehle)
      │
KioskService (Foreground) ── MqttManager (Paho, Auto-Reconnect, Last-Will)
      ├── BatteryMonitor   → …/battery, …/charging, …
      ├── TtsManager       ← …/cmd/tts
      └── MotionDetector   → …/motion, …/presence   (CameraX, Luminanz-Diff)
```

Einstellungen liegen in Jetpack DataStore; alle Topics werden daraus abgeleitet.

---

## Bekannte Grenzen / To-do (v1)
- „Bildschirm aus“ ist ein schwarzes Overlay + Helligkeit 0. Echtes Abschalten
  des Panels bräuchte einen **Device-Admin/DeviceOwner** (`lockNow`) – bewusst
  noch nicht drin.
- Der Foreground-Service nutzt Typ `dataSync`; unter Android 15 gibt es dafür
  Laufzeit-Limits. Für 24/7 ggf. auf `specialUse`/DeviceOwner umstellen.
- Bewegungserkennung ist eine einfache Helligkeitsdifferenz (Schwellwert in
  `MotionDetector.THRESHOLD`), robust genug für Anwesenheit, kein Personen-Tracking.
- TLS nutzt den System-Truststore; selbstsignierte Broker-Zertifikate müssten
  ergänzt werden.
```

---

## Neu in v0.2

**Menü:** Vom linken Rand nach rechts wischen öffnet ein modernes Drawer-Menü mit
MQTT-Status (grün/grau), Dashboard, Neu laden, Cache leeren, Bildschirm aus,
Einstellungen (PIN), Hilfe und Über. Einstellungen sind in Untermenüs gegliedert
(Verbindung, Anzeige, Verhalten, Töne, System).

**Sprache:** Englisch (Standard) und Deutsch, umschaltbar unter *Einstellungen → System*.

**Zusätzliche MQTT-Readings** (unter `<basis>/<id>/`):
`appVersion`, `androidVersion`, `ip`, `url`, `brightness`.

**Zusätzliche Befehle** (`<basis>/<id>/cmd/`):

| Topic | Payload | Wirkung |
|---|---|---|
| `cmd/mediaplay` | `ordner/sound.mp3` | spielt gespeicherte Tondatei ab |
| `cmd/mediastop` | (egal) | stoppt die Wiedergabe |
| `cmd/clearcache` | (egal) | leert den Browser-Cache |
| `cmd/url` | URL | lädt Seite und meldet sie als `url`-Reading |

**Weitere Einstellungen:** Zoomen im Dashboard an/aus, Pull-to-Refresh (nach unten
ziehen), Autostart beim Booten, Schrift unabhängig vom System-Zoom.

**FHEM-setList-Ergänzungen:**
```
mediaPlay:textField  <basis>/<id>/cmd/mediaplay  $EVTPART1
mediaStop:noArg      <basis>/<id>/cmd/mediastop  1
clearCache:noArg     <basis>/<id>/cmd/clearcache 1
```
