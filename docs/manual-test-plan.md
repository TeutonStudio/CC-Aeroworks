# Manueller Testplan

Dieser Plan enthält interaktive Fälle, die Unit-Tests, Build und Server-Smoke-Test nicht abdecken. Die auszuführenden Profile stehen in [`runtime-test-matrix.md`](runtime-test-matrix.md).

## Ergebnisformat

Für jeden ausgeführten Fall im Baselinebericht erfassen:

```text
Status: PASS | FAIL | BLOCKED | NOT RUN
Commit: <vollständiger SHA>
Profil: <Profil-ID>
Tester: <Name oder GitHub-Login>
Datum: <ISO-8601>
Nachweis: <Logstelle, Screenshotbeschreibung oder Welt-/Koordinatenangabe>
Abweichung/Issue: <Link oder ->
```

Ein Fall ist nur `PASS`, wenn alle Schritte und Erwartungen erfüllt sind. Ein Hauptmenü ist kein Ersatz für eine Welt, und ein Screenshot eines Displays ist kein Persistenztest. Die Menschheit hat genügend kreative Abkürzungen entwickelt; hier brauchen wir keine weiteren.

---

## Build und Start

### `BUILD-CLIENT-01` Clientstart

**Profile:** `BASE-CLIENT`, `CC-120`

**Voraussetzungen:** Profilabhängigkeiten validiert; frisches Run-Verzeichnis.

**Schritte:**

1. `./gradlew -Pmod_dependency_dir=<profil> runClient` ausführen.
2. Hauptmenü erreichen.
3. Eine neue Testwelt laden.
4. Welt verlassen und Client schließen.

**Erwartung:**

- Keine Registry-, Mixin-, Classloading- oder Ressourcenfehler.
- Welt lädt vollständig.
- Log enthält keine `ERROR`-Einträge aus `cc_aeroworks`, die Initialisierung oder Mixins betreffen.

### `BUILD-SERVER-01` Dedicated-Server-Start

**Profile:** `BASE-SERVER`, `FULL-SERVER`

**Schritte:**

1. `python3 tools/dedicated-server-smoke.py --dependency-dir <profil>` ausführen.
2. Ergebnis und `build/test-results/server-smoke/gradle-output.log` prüfen.

**Erwartung:**

- Server erreicht den `Done`-Marker innerhalb des Timeouts.
- Keine Clientklasse und kein Clientmixin wird geladen.
- Skript beendet den Server kontrolliert mit Exitcode `0`.

---

## Peripheral und Lifecycle

### `PERIPHERAL-DIRECT-01` Direkte Verbindung

**Profile:** `BASE-CLIENT`, `CC-120`

**Schritte:**

1. Computer direkt neben ein Control Desk setzen.
2. `peripheral.find("cc_aeroworks_control_desk")` ausführen.
3. `getSockets()`, `getModules()`, `getInputs()` und `getDisplays()` aufrufen.
4. Jeden socketbezogenen Aufruf einmal mit Namen und einmal mit Index ausführen.

**Erwartung:**

- Peripheral wird genau einmal gefunden.
- `getSockets()` liefert `left=0`, `right=1`, `big=2` in dieser Reihenfolge.
- Name und Index adressieren dasselbe Modul.
- Modul- und Displaytabellen enthalten `socketName`.

### `PERIPHERAL-MODEM-01` Wired Modem

**Profile:** `BASE-SERVER`

**Schritte:**

1. Desk und Computer über Wired Modems verbinden.
2. Modem am Desk aktivieren.
3. Peripheral über Netzwerk finden und Methoden aufrufen.
4. Modem deaktivieren und erneut aktivieren.

**Erwartung:**

- Peripheral erscheint nur bei aktiver Verbindung.
- Methoden liefern dieselben Werte wie bei direkter Verbindung.
- Reconnect erzeugt keine doppelte Peripheralinstanz.

### `PERIPHERAL-LIFECYCLE-01` Chunk und BlockEntity

**Profile:** `BASE-SERVER`, `MULTI-COMPUTER`

**Schritte:**

1. Peripheral verbinden und Namen notieren.
2. Desk-Chunk entladen und erneut laden.
3. Welt speichern und neu öffnen.
4. Desk abbauen.
5. Computer beziehungsweise Modem trennen.

**Erwartung:**

- Nach Reload ist genau ein gültiges Peripheral verfügbar.
- Nach Desk-Abbau verschwinden Methoden und Events ohne Serverfehler.
- Keine stale Referenz erzeugt Events für das entfernte Desk.

---

## Textdisplays

### `DISPLAY-TEXT-01` Modulmontage

**Profile:** `BASE-CLIENT`

**Schritte:**

1. Zweistelliges Display in `left`, `right` und `big` montieren.
2. Dreistelliges Display in `left`, `right` und `big` montieren versuchen.
3. Beide Typen demontieren.

**Erwartung:**

- Zweisteller akzeptiert kleine und große Sockets.
- Dreisteller wird in kleinen Sockets abgelehnt und in `big` akzeptiert.
- Demontage erzeugt genau den korrekten Drop.

### `DISPLAY-TEXT-02` Normalisierung

**Profile:** `BASE-CLIENT`

**Schritte:**

1. Leerstring, Ziffern, Minus, Leerzeichen und ungültige Zeichen schreiben.
2. Strings unterhalb und oberhalb der Displaybreite schreiben.
3. Ergebnis über `getDisplay()` lesen und visuell vergleichen.

**Erwartung:**

- Text wird links beginnend auf die Displaybreite begrenzt.
- Erlaubt sind Ziffern, Minus und Leerzeichen.
- Andere Zeichen werden konsistent als Leerzeichen dargestellt.
- Lua-Rückgabe und sichtbare Anzeige stimmen überein.

### `DISPLAY-TEXT-03` Zahlenbereich

**Profile:** `BASE-CLIENT`

**Schritte:**

1. Minimum, Maximum, negative und positive Werte schreiben.
2. Werte außerhalb des Bereichs schreiben.
3. `zeroPad=false` und `zeroPad=true` prüfen.
4. NaN und Unendlich über Lua erzeugen und übergeben.

**Erwartung:**

- Zweisteller begrenzt auf `-9..99`, Dreisteller auf `-99..999`.
- Überlauf wird begrenzt, nicht umgebrochen.
- Zero-Padding respektiert das Vorzeichen.
- NaN und Unendlich erzeugen einen Lua-Fehler ohne Welt- oder Serverfehler.

### `DISPLAY-TEXT-04` Löschen und Wechsel

**Profile:** `BASE-CLIENT`

**Schritte:**

1. Text auf mehrere Displays schreiben.
2. Ein Display mit `clearDisplay` löschen.
3. Alle Displays mit `clearDisplays` löschen.
4. Nach Pixelmodus erneut Text schreiben.

**Erwartung:**

- Nur das adressierte Display wird einzeln gelöscht.
- `clearDisplays` liefert die korrekte Anzahl.
- Textschreiben wechselt zuverlässig zurück in den Textmodus.

### `DISPLAY-PERSIST-01` Persistenz und Synchronisation

**Profile:** `BASE-SERVER`, `SABLE-MOVING`

**Schritte:**

1. Unterschiedliche Text- und Pixelzustände auf mehreren Displays setzen.
2. Chunk entladen und laden.
3. Welt speichern, Server stoppen und neu starten.
4. Zweiten Client verbinden und Zustände vergleichen.

**Erwartung:**

- Zustand bleibt nach Chunk- und Weltneustart erhalten.
- Alle Clients sehen denselben Zustand.
- Kein Display übernimmt den Zustand eines anderen Sockets.

---

## Pixeldisplays

### `DISPLAY-PIXEL-01` Größe und Koordinaten

**Profile:** `BASE-CLIENT`, `CC-120`

**Schritte:**

1. `getDisplaySize()` für Zwei- und Dreisteller aufrufen.
2. Pixel `(1,1)` und den jeweils rechten unteren Rand setzen und lesen.
3. Koordinaten `0`, negative Werte und Werte außerhalb der Größe testen.

**Erwartung:**

- Größen sind `7x5` beziehungsweise `11x5`.
- Koordinaten beginnen bei `1`.
- Randpixel sind schreib- und lesbar.
- Ungültige Koordinaten erzeugen einen Lua-Fehler ohne Teiländerung.

### `DISPLAY-PIXEL-02` Vollständiges Raster

**Profile:** `BASE-CLIENT`, `FALLBACK-CLIENT`

**Schritte:**

1. `examples/cc/pixel-test.lua` ausführen.
2. Zusätzlich ein vollständig gefülltes und ein leeres Raster schreiben.
3. Rückgabewert und `getDisplay()` prüfen.

**Erwartung:**

- Genau fünf Zeilen mit korrekter Breite werden übernommen.
- Sichtbare Pixel, Rückgabe und gelesener Zustand stimmen überein.

### `DISPLAY-PIXEL-03` Ungültige Raster

**Profile:** `BASE-CLIENT`

**Schritte:**

1. Zu wenige und zu viele Zeilen übergeben.
2. Falsche Zeilenbreite übergeben.
3. Zeichen außerhalb `0` und `1` übergeben.
4. Zustand vor und nach jedem Fehler vergleichen.

**Erwartung:**

- Jeder ungültige Aufruf erzeugt einen Lua-Fehler.
- Kein teilweise geschriebenes Raster bleibt zurück.

### `DISPLAY-PIXEL-04` Moduswechsel

**Profile:** `BASE-CLIENT`

**Schritte:**

1. Text schreiben.
2. Einzelpixel setzen.
3. Vollständiges Raster schreiben.
4. Pixel löschen.
5. Zahl schreiben.

**Erwartung:**

- Pixeloperationen setzen `mode=pixels`.
- Text- und Zahloperationen setzen `mode=text`.
- Alte Zustände scheinen nach Moduswechsel nicht durch.

---

## Eingaben und Events

### `EVENT-01` Einzelcomputer

**Profile:** `BASE-SERVER`, `CC-120`

**Schritte:**

1. `examples/cc/input-monitor.lua` starten.
2. Jedes installierte Aeroworks-Eingabemodul verändern.
3. Mehrkanalmodule Kanal für Kanal bewegen.

**Erwartung:**

- Pro tatsächlicher Wertänderung erscheint genau ein Event.
- Argumentreihenfolge ist Peripheralname, Socketindex, Modul-ID, Wert, Kanal, Socketname.
- Unveränderte Werte erzeugen keine Events.

### `EVENT-MULTI-01` Zwei Computer

**Profile:** `MULTI-COMPUTER`

**Schritte:**

1. Zwei Computer über dasselbe Wired Network verbinden.
2. Auf beiden den Eventmonitor starten.
3. Einen Eingabewert einmal verändern.

**Erwartung:**

- Jeder angehängte Computer erhält genau ein gleichwertiges Event.
- Kein Computer erhält doppelte Events.

### `EVENT-DETACH-01` Attach und Detach

**Profile:** `MULTI-COMPUTER`

**Schritte:**

1. Zwei Computer anhängen.
2. Einen Computer beziehungsweise dessen Modem trennen.
3. Eingabe verändern.
4. Zweiten Computer ebenfalls trennen und Eingabe erneut verändern.

**Erwartung:**

- Getrennter Computer erhält keine weiteren Events.
- Verbleibender Computer erhält weiterhin genau ein Event.
- Ohne angehängte Computer entstehen keine Fehler oder dauerhaft aktive Poller.

---

## Rendering

### `RENDER-FLYWHEEL-01` Flywheel-Pfad

**Profile:** `BASE-CLIENT`

**Schritte:**

1. Displays in allen Desk-Sockets montieren.
2. Desk in allen horizontalen Rotationen und an Boden/Decke prüfen.
3. Tag/Nacht, unterschiedliche Lichtwerte und Rückseite prüfen.
4. Text- und Pixelzustände animiert ändern.

**Erwartung:**

- Segmente und Pixel liegen auf dem richtigen Modul.
- Kein Z-Fighting, Flackern oder verbleibendes altes Instance-Modell.
- Licht und Rotation folgen dem Desk.

### `RENDER-FALLBACK-01` Fallback-Pfad

**Profile:** `FALLBACK-CLIENT`

**Schritte:** Wie `RENDER-FLYWHEEL-01`, Flywheel-Backend deaktiviert.

**Erwartung:** Ausgabe stimmt geometrisch und inhaltlich mit dem Flywheel-Pfad überein.

### `RENDER-FALLBACK-02` Backendwechsel

**Profile:** `FALLBACK-CLIENT`

**Schritte:**

1. Welt einmal mit aktivem Flywheel laden und speichern.
2. Backend deaktivieren und dieselbe Welt laden.
3. Backend wieder aktivieren.

**Erwartung:**

- Persistente Zustände bleiben identisch.
- Keine doppelten Renderinstanzen oder fehlenden Pixel nach Backendwechsel.

---

## Combined Input

### `COMBINED-LEVER-01` Lever-Konfiguration

**Profile:** `BASE-CLIENT`

**Schritte:**

1. Lever-Modulbildschirm öffnen.
2. Modusfolge `Buttons -> Analog -> Kombiniert -> Buttons` prüfen.
3. Taste erfassen, per Rechtsklick löschen und neu erfassen.
4. Bildschirm schließen und erneut öffnen.
5. Taste halten und Maus Y bewegen.

**Erwartung:**

- Zusätzlicher Modus erscheint nur für unterstützte Module/Kanäle.
- Binding bleibt persistent.
- Maus Y verändert den Lever innerhalb `-15..15`.
- Kamera wird nur bei gültigem Ziel eingefroren.

### `COMBINED-JOYSTICK-01` Joystickachsen

**Profile:** `BASE-CLIENT`

**Schritte:**

1. `x` und `y` mit unterschiedlichen Tasten konfigurieren.
2. X-Taste halten und Maus horizontal/vertikal bewegen.
3. Y-Taste halten und beide Richtungen bewegen.
4. Beide Achsen auf dieselbe Taste legen und diagonal bewegen.

**Erwartung:**

- X reagiert ausschließlich auf Maus X, Y ausschließlich auf Maus Y.
- Bei identischem Binding werden beide Achsen im selben Steuerungszyklus aktualisiert.
- Serverseitiges Rate-Limit verwirft keine der beiden gültigen Achsen.

### `COMBINED-THROTTLE-01` Throttle Quadrant

**Profile:** `BASE-CLIENT`

**Schritte:**

1. `red`, `amber`, `green`, `blue` mit unterschiedlichen Tasten konfigurieren.
2. Jede Taste separat bei Maus-Y-Bewegung prüfen.
3. Zwei Kanäle auf dieselbe Taste legen.

**Erwartung:**

- Jede Taste verändert ausschließlich den zugeordneten Kanal.
- Identische Bindings können mehrere gültige Kanäle im selben Zyklus steuern.

### `COMBINED-TARGET-01` Zielauswahl

**Profile:** `BASE-CLIENT`

**Schritte:**

1. Lever in `left` und `right` montieren.
2. Unterschiedliche, danach identische Aktivierungstasten konfigurieren.
3. Beide Lever aus mehreren Blickwinkeln einzeln anvisieren.
4. Zwischen den Modulen und am Desk vorbeizielen.

**Erwartung:**

- Nur das tatsächlich getroffene gültige Modul reagiert.
- Ein näheres, aber nicht gültiges Modul verdrängt das anvisierte Ziel nicht.
- Ohne gültigen Treffer friert die Kamera nicht ein.

### `COMBINED-LIFECYCLE-01` Abbruchbedingungen

**Profile:** `BASE-CLIENT`, `SABLE-MOVING`

**Schritte:** Combined Input aktivieren und jeweils Menü öffnen, Fokus verlieren, sterben, Dimension wechseln, Desk abbauen, Modul demontieren und Verbindung trennen.

**Erwartung:** Kamera und Maussteuerung werden sofort freigegeben; keine weiteren Pakete werden gesendet.

---

## Sable

### `SABLE-MOUNT-01` Montage auf Schiff

**Profile:** `SABLE-STATIC`, `SABLE-MOVING`

**Schritte:** Displays und Eingabemodule auf einem Sable-Schiff montieren und demontieren.

**Erwartung:** Socketvalidierung, Drops und Peripheralzugriff entsprechen der normalen Welt.

### `SABLE-RENDER-01` Schiffstransform

**Profile:** `SABLE-STATIC`, `SABLE-MOVING`

**Schritte:** Text und Pixel aus mehreren Blickwinkeln bei Translation und Rotation des Schiffs prüfen.

**Erwartung:** Rendering bleibt am Modul ausgerichtet und zeigt kein Z-Fighting oder Weltkoordinaten-Offset.

### `SABLE-COMBINED-01` Steuerung in Bewegung

**Profile:** `SABLE-MOVING`

**Schritte:** Combined Input während realer Translation und Rotation verwenden.

**Erwartung:** Hit-Test folgt dem transformierten Desk; nur das anvisierte Modul reagiert.

### `SABLE-LIFECYCLE-01` Schiffslifecycle

**Profile:** `SABLE-MOVING`

**Schritte:** Chunk entladen, Welt neu laden, Schiff stoppen/starten und Desk abbauen.

**Erwartung:** Keine stale Peripherals, Renderinstanzen oder eingefrorene Kamera.

---

## Optionale Integration und Guide

### `DRIVEBYWIRE-LOAD-01` Optionales Laden

**Profile:** `DRIVEBYWIRE`, `FULL-SERVER`

**Schritte:** Client und Server einmal ohne und einmal mit Drive By Wire 0.2.9 starten.

**Erwartung:** Ohne Mod keine Abhängigkeitswarnung; mit Mod keine Classloading- oder Integrationsfehler.

### `DRIVEBYWIRE-INPUT-01` Kanalinteraktion

**Profile:** `DRIVEBYWIRE`

**Schritte:** Bestätigte Aeroworks-Kanäle über Drive By Wire auswählen, veröffentlichen und über CC lesen.

**Erwartung:** Werte und Kanalnamen bleiben konsistent; CC-Aeroworks überschreibt keine fremden Quellen.

### `GUIDE-01` Creative Tab und Handbuch

**Profile:** `BASE-CLIENT`

**Schritte:**

1. Aeroworks-Creative-Tab bei kleiner und großer GUI-Skalierung öffnen.
2. Kategorien, Scrollposition, Reihenfolge und doppelte Items prüfen.
3. Handbuch öffnen und alle sieben Kapitel, Codeblöcke, Hinweise, Sidebar, Scrollen, Vor/Zurück, Fertig und Escape prüfen.

**Erwartung:**

- Kategoriezeilen verdecken Slotgrafiken vollständig.
- Displayitems und Handbuch erscheinen genau einmal im richtigen Abschnitt.
- Text und Oberfläche bleiben scharf; kein Blur-Shader verwischt die bereits gezeichnete Oberfläche.
- Navigation funktioniert bei beiden GUI-Skalierungen.
