# Combined Control Focus

## Ziel

Der Eingabetyp `Kombiniert` ist für schnelles Umschalten zwischen Kamera und Cockpit-Steuerung
gedacht. Während eine Combined-Taste gehalten wird, muss der ausgewählte Regler exklusiv die Maus
besitzen. Rendering, permanentes Targeting und Multiblock-Suche dürfen nicht im heißen Mauspfad
liegen.

## Session-Lebenszyklus

```text
PRESS
  -> Multiblock-Kontext auflösen
  -> Binding auf ein Modul auflösen
  -> Maus-Baseline speichern
  -> exklusiven CONTROL- oder DISPLAY-Owner claimen
  -> Focus Session

MOUSE
  -> nur DX/DY + lokaler Akkumulator
  -> höchstens configured packet rate
  -> atomisches Multi-Channel-Sample

alle 5 Client-Ticks
  -> Watchdog: Desk/Socket/Modul/Binding/Reichweite prüfen

RELEASE / Shift / Fokusverlust
  -> finales Sample ohne Rate-Limit senden
  -> Owner freigeben
  -> Kamera wieder normal
```

Target-Akquise geschieht ausschließlich an physischen PRESS-Kanten. `ClientTickEvent` und
`CalculatePlayerTurnEvent` erwerben niemals ein neues Ziel.

## Multiblock-Kontext

`CombinedInputContext` verwendet den kanonischen `ConsoleMultiblockManager`.

Beim ersten Zugriff gilt:

1. trifft Minecraft bereits irgendeinen ControlDesk, wird dessen vollständiger Desk-Multiblock
   als Kontext gespeichert;
2. besitzt ein visuelles Modul wie das große Display keinen geeigneten Vanilla-Hit, wird nur ein
   schmaler 3x3x3-Korridor entlang des Sichtstrahls in Halbblock-Schritten geprüft;
3. danach bleibt der Multiblock-Kontext erhalten, solange der Spieler in Reichweite eines seiner
   Mitglieder steht.

Damit muss nicht mehr bei jedem Wechsel exakt das einzelne Steuerobjekt angesehen werden. Ein
eindeutiges Binding im bekannten Pultnetz kann direkt aufgelöst werden.

Bei mehrfach belegten Tasten ist die Auswahl absichtlich konservativ:

1. das aktuell direkt anvisierte passende Modul gewinnt;
2. sonst wird die letzte Auswahl für diese Taste wiederverwendet;
3. ohne eindeutige oder erinnerte Auswahl startet keine Session.

## Exklusiver Mausbesitz

`CombinedInputCoordinator` kennt zwei Owner:

- `CONTROL` für Lever, Joystick, Wheel, Yoke und Throttle;
- `DISPLAY` für den großen Display-/Radar-Pointer.

Ownership ist nicht preemptiv. Eine zweite Combined-Taste kann eine aktive Session nicht mitten im
Tastendruck übernehmen. Shift beendet die Session und gibt die Maus unmittelbar an die Kamera
zurück.

Aeroworks' eigener `feedMouseDelta`-Pfad bleibt während einer Combined-Session unterdrückt. Somit
wird jedes physische Mausdelta nur einmal verarbeitet.

## Maus-Baseline statt verlorenem ersten Frame

Beim PRESS werden die aktuellen `MouseHandler`-Deltas gespeichert. Beim ersten Turn-Event wird nur
die Differenz zu dieser Baseline verarbeitet.

Dadurch gelangt die Mausbewegung vor dem Aktivierungsrand nicht in den Regler, ohne pauschal das
komplette erste Maus-Sample zu verwerfen.

## Netzwerk

Normale Steuerobjekte verwenden `CombinedControlSamplePayload`.

Ein Sample enthält:

- Desk-Position;
- Socket;
- Sequenznummer;
- Kennzeichnung des finalen Samples;
- alle zusammengehörigen Channel-Werte.

Joystick-/Yoke-Achsen werden damit gemeinsam übertragen statt als voneinander unabhängige Pakete.

Die normale Paketfrequenz bleibt über `combinedLeverPacketRate` auf maximal 20/s begrenzt. Beim
Session-Ende wird dieses Limit absichtlich übergangen und der aktuelle Zustand immer noch einmal
gesendet. Ein kurzer `drücken -> bewegen -> loslassen`-Vorgang kann deshalb nicht mehr seinen
letzten `pendingValue` verlieren.

Der Server besitzt für Combined-Samples keine `first packet per tick wins`-Sperre mehr. Später
eintreffende Samples dürfen den Zustand desselben Server-Ticks aktualisieren. Der alte
`SetCombinedLeverValuePayload` bleibt für Protokoll-Kompatibilität registriert und verwendet
dieselbe Latest-Wins-Semantik.

Serverseitige Reichweitenprüfung erfolgt gegen irgendein Mitglied desselben ControlDesk-Multiblocks,
nicht ausschließlich gegen den Block, auf dem das Zielmodul montiert ist.

## CC:Tweaked-Ereignisse

Direkt angeschlossene `ControlDesk`-Peripherals erhalten Combined-Eingabeänderungen unmittelbar im
Server-Payload-Handler. Der bestehende Snapshot-Diff in `ServerTickEvent.Post` bleibt als Fallback
für native Aeroworks-Änderungen und andere Eingabequellen erhalten.

Existiert bereits ein Snapshot, wird er nach dem Immediate-Event gepatcht, damit das Fallback
dasselbe Ereignis im nächsten Tick nicht noch einmal ausgibt.

## Watchdog

Während einer aktiven Session werden keine Raycasts und keine Mount-Suchen pro Mausframe
durchgeführt. Die vollständige Weltvalidierung läuft nur alle fünf Client-Ticks.

Sofort geprüft werden weiterhin die billigen Zustände:

- Fensterfokus;
- geöffnete GUI;
- Spieler lebt;
- Dimension;
- physischer Release der Aktivierungstaste;
- Shift-Override.

Der 5-Tick-Watchdog prüft zusätzlich:

- Desk noch geladen;
- Socket und Modul noch vorhanden;
- Channel weiterhin Combined und mit demselben Binding konfiguriert;
- Spieler noch in Reichweite des Desk-Multiblocks.

## Manuelle Regressionstests

### Schneller Impuls

1. Combined-Taste drücken.
2. Regler kurz bewegen.
3. Vor Ablauf von 50 ms wieder loslassen.

Erwartung: Die Endposition kommt serverseitig an.

### Blick weg

1. Eine Combined-Session im Desk-Netz erfolgreich benutzen.
2. Kamera auf einen anderen Bereich des Cockpits drehen.
3. Taste eines eindeutig belegten anderen Moduls drücken.

Erwartung: Das Modul wird aus dem gecachten Multiblock-Kontext gewählt, ohne dass sein einzelnes
Modell exakt getroffen werden muss.

### Display ohne Vanilla-Hit

1. Großes Display sichtbar anvisieren, sodass der Vanilla-Hit nicht zwingend den Desk trifft.
2. Display-Combined-Taste drücken.

Erwartung: Der schmale View-Ray-Korridor findet den zugehörigen Desk; kein großer Weltwürfel wird
gescannt.

### Mehrfachbelegung

1. Zwei Module im selben Netz auf dieselbe Taste legen.
2. Eines davon direkt ansehen und Taste drücken.
3. Später erneut drücken, ohne beide eindeutig anzusehen.

Erwartung: Zuerst entscheidet das direkt anvisierte Modul, danach darf die letzte eindeutige Auswahl
wiederverwendet werden. Ohne Tiebreaker wird kein zufälliges Modul übernommen.

### Shift

1. Combined-Taste halten und steuern.
2. Shift drücken.

Erwartung: finales Sample wird übertragen, Focus endet und die Maus steuert sofort wieder die Kamera.
