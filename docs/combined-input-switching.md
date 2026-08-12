# Optimierung des kombinierten Eingabemodus

## Ziel

Der Eingabetyp `Kombiniert` ist für einen schnellen Wechsel zwischen normaler Maussteuerung und der Bedienung eines Lever-, Joystick- oder Throttle-Kanals gedacht. Die Umschaltgrenzen müssen deshalb auf dem Maus-/Turn-Pfad liegen und dürfen nicht ausschließlich vom 20-Hz-Client-Tick abhängen.

## Behobene Fehlerquellen

### Doppelte Mausverarbeitung

Vor dieser Änderung konnten dieselben physischen Mausdeltas über zwei Wege in `CombinedLeverController.consumeMouseDelta` gelangen:

- direkt über `CalculatePlayerTurnEvent` und die akkumulierten MouseHandler-Deltas;
- zusätzlich über Aeroworks `ConsoleControlClient.feedMouseDelta` beziehungsweise `JoystickControlClient.feedMouseDelta`.

`CalculatePlayerTurnEvent` ist nun der einzige autoritative Combined-Input-Pfad. Der Aeroworks-Hook verhindert während einer aktiven Combined-Session lediglich, dass Aeroworks dieselbe Mausbewegung selbst verarbeitet.

### Nachlauf beim Loslassen

Der Aktivierungszustand wurde bisher hauptsächlich in `ClientTickEvent.Post` geprüft. Zwischen dem Loslassen einer Taste und dem nächsten Client-Tick konnte deshalb noch ein Turn-Event auftreten und ein Mausdelta auf das Steuerobjekt anwenden.

Der Turn-Event prüft die Aktivierungstaste nun unmittelbar vor jeder möglichen Verarbeitung. Ist die Taste bereits losgelassen, endet die Session sofort und das aktuelle Mausdelta bleibt bei der normalen Kamerasteuerung.

### Übergangsdelta beim Aktivieren

Eine Mausbewegung, mit der ein Spieler unmittelbar vor dem Tastendruck auf ein Modul zielt, kann zeitlich im selben akkumulierten Maus-Sample wie die Aktivierung liegen. Dieses Sample darf nicht zum neuen Steuerwert werden.

Jedes neu erworbene Combined-Ziel verwirft deshalb genau das erste Turn-Sample. Die Kamera ist für dieses Grenzsample bereits eingefroren. Ab dem folgenden Sample wird ohne zusätzliche Zeitverzögerung normal gesteuert.

## Zustandsverhalten

```text
normal
  |
  | gültiges Ziel + Aktivierungstaste
  v
aktiviert
  |
  | erstes Turn-Sample verwerfen
  v
steuern
  |
  | Taste loslassen
  v
normal
```

Wird das Ziel ungültig, während die Aktivierungstaste weiterhin gehalten wird, bleibt die bestehende Suppression erhalten. Ein anderes Modul mit derselben Taste kann dadurch nicht mitten im Tastendruck übernommen werden.

## Manueller Regressionstest

### Schnelles Aktivieren

1. Maus sichtbar in Richtung eines Combined-Moduls bewegen.
2. Während der Bewegung die Aktivierungstaste drücken.
3. Bewegung unmittelbar fortsetzen.

Erwartung:

- Die Zielbewegung vor beziehungsweise am Aktivierungsrand verändert den Steuerwert nicht.
- Ab dem folgenden Maus-Sample reagiert das Modul ohne merkliche zusätzliche Zeitverzögerung.
- Die Kamera bleibt während der aktiven Session stehen.

### Schnelles Loslassen

1. Combined-Modul aktiv bewegen.
2. Während einer fortlaufenden Mausbewegung die Aktivierungstaste loslassen.
3. Maus ohne Pause weiterbewegen.

Erwartung:

- Nach dem Loslassen wird kein weiteres Delta auf das Modul angewendet.
- Die Mausbewegung des Release-Turns steht wieder der Kamera zur Verfügung.
- Es gibt keine sichtbare Nachlaufphase bis zum nächsten Client-Tick.

### Wiederholtes Umschalten

1. Mindestens zwanzigmal schnell `drücken -> bewegen -> loslassen` wiederholen.
2. Dazwischen die Kamera jeweils weiterbewegen.

Erwartung:

- Keine schleichende Drift durch Übergangsdeltas.
- Keine doppelte Wertänderung pro physischer Mausbewegung.
- Keine hängenbleibende Kamerasperre.

### Joystick mit gemeinsamer Taste

1. Joystick `x` und `y` auf dieselbe Aktivierungstaste legen.
2. Diagonal bewegen.

Erwartung:

- X und Y werden parallel aus demselben autoritativen Turn-Sample aktualisiert.
- Das Sample wird nicht zusätzlich über Aeroworks `feedMouseDelta` ein zweites Mal eingerechnet.

### Throttle mit gemeinsamer Taste

1. Zwei Throttle-Kanäle auf dieselbe Aktivierungstaste legen.
2. Vertikal bewegen.

Erwartung:

- Beide Kanäle folgen derselben Bewegung.
- Jeder Kanal wird pro physischem Maus-Sample nur einmal akkumuliert.
