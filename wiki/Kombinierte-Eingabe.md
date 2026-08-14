# Kombinierte Eingabe

Der Eingabetyp **Kombiniert** verbindet eine gehaltene Aktivierungstaste mit Mausbewegungen. Er ist ein dritter Eingabemodus neben **Tasten** und **Maus** und ist für schnellen, bewussten Wechsel zwischen Kamera und Steuerobjekt gedacht.

Die Aktivierungstaste wird pro Kanal direkt in der vorhandenen Aeroworks-Konfiguration des Moduls gespeichert. Sie ist keine globale Minecraft-Tastenbelegung.

## Unterstützte Module und Kanäle

`Kombiniert` steht für alle kontinuierlichen Aeroworks-Steuerkanäle zur Verfügung. Binäre Tastermodule bleiben unverändert, weil ihre Press-Kanäle keine kontinuierliche Mausachse besitzen.

| Modul | Kanal | Mausachse |
|---|---|---|
| Lever | `lever` | Y |
| Joystick | `x` | X |
| Joystick | `y` | Y |
| Steering Wheel | `wheel` | X |
| Yoke | `turn` | X |
| Yoke | `pitch` | Y |
| Throttle Quadrant | `red` | Y |
| Throttle Quadrant | `amber` | Y |
| Throttle Quadrant | `green` | Y |
| Throttle Quadrant | `blue` | Y |
| Große Pultanzeige | `x` | X |
| Große Pultanzeige | `y` | Y |
| Große Radaranzeige | `x` | X |
| Große Radaranzeige | `y` | Y |

Die normalen Aeroworks-Steuerwerte bleiben im Bereich von `-15` bis `15`. Die Displaykanäle steuern stattdessen die normalisierte Position des Pseudo-Fingers.

## Modus einrichten

1. Öffne den Aeroworks-Modulbildschirm des gewünschten Steuerobjekts.
2. Klicke mit der linken Maustaste auf das vorhandene Modussymbol der gewünschten Achse.
3. Bei normalen kontinuierlichen Steuerungen läuft die Folge `Tasten -> Maus -> Kombiniert -> Tasten`.
4. Im Modus **Kombiniert** zeigt das Eingabefeld die Aktivierungstaste statt einer Mausquelle.
5. Linksklicke das Feld und drücke die gewünschte Taste oder Maustaste.
6. Rechtsklick auf das Feld löscht die Belegung.

Beim ersten Wechsel auf **Kombiniert** wird eine leere Belegung mit `K` vorbelegt.

Die große Pultanzeige und die große Radaranzeige besitzen je zwei echte Aeroworks-Kanäle `x` und `y`. Diese Zeilen sind absichtlich auf **Kombiniert** festgelegt und können nicht auf Tasten oder normalen Mausmodus umgeschaltet werden. X und Y dürfen unterschiedliche Aktivierungstasten verwenden.

## Steuerung verwenden

1. Schließe den Modulbildschirm.
2. Sieh das gewünschte Modul am Steuerungspult an.
3. Halte die konfigurierte Aktivierungstaste.
4. Bewege die Maus auf der für den Kanal vorgesehenen Achse.
5. Lass die Taste los, um die Steuerung zu beenden.

Während einer aktiven Combined-Session gehört die Maus exklusiv dem ausgewählten Steuerobjekt. Die Kamera und konkurrierende Control-Desk-Mauseingaben werden für diese Session blockiert.

**Shift** ist der harte Kamera-Override. Wird Shift gedrückt, endet die Combined-Session sofort und die Maus steuert wieder die Kamera. Solange die vorherige Aktivierungstaste weiter gehalten wird, bleibt sie bis zum Loslassen gesperrt; erst ein neuer Tastendruck darf erneut Combined aktivieren.

## Achsenverhalten

### Lever und Throttle Quadrant

Lever sowie die vier Throttle-Kanäle verwenden Maus Y.

### Joystick

- Kanal `x`: Maus X
- Kanal `y`: Maus Y

Die beiden Kanäle können getrennte Aktivierungstasten besitzen. Verwenden beide dieselbe Taste, werden X und Y aus demselben Maus-Sample parallel gesteuert.

### Steering Wheel

Der Kanal `wheel` verwendet Maus X.

### Yoke

- Kanal `turn`: Maus X
- Kanal `pitch`: Maus Y

Auch hier dürfen beide Achsen dieselbe oder unterschiedliche Aktivierungstasten verwenden.

### Große Displays

- Kanal `x`: horizontale Bewegung des Pseudo-Fingers
- Kanal `y`: vertikale Bewegung des Pseudo-Fingers

Gleiche Bindings erzeugen normale zweidimensionale Pointerbewegung. Unterschiedliche Bindings erlauben die unabhängige Freigabe beider Achsen.

## Zielauswahl

Die Steuerung wird nur aktiviert, wenn alle folgenden Bedingungen erfüllt sind:

- Der Spieler sieht ein Steuerungspult an.
- Das tatsächlich anvisierte Top-Level-Modul unterstützt den Kombiniert-Modus.
- Mindestens ein Kanal dieses Moduls ist auf **Kombiniert** gestellt.
- Die zugehörige Aktivierungstaste wird gehalten.
- Kein anderer Bildschirm ist geöffnet.
- Shift ist nicht gedrückt.

Die Auswahl verwendet den vorhandenen Aeroworks-Modulraycast. Dadurch bleiben Desk-Ausrichtung und Transformationen bewegter Konstruktionen erhalten.

## Wann die Steuerung beendet wird

Die aktive Steuerung endet sofort, sobald eine der folgenden Bedingungen eintritt:

- Aktivierungstaste wird losgelassen
- Shift wird gedrückt
- GUI oder Menü wird geöffnet
- Minecraft-Fenster verliert den Fokus
- Spieler stirbt oder wird ersetzt
- Spieler wechselt die Dimension
- Pult oder Chunk ist nicht mehr geladen
- Modul, Socket oder Konfiguration wurde verändert
- Ziel ist nicht mehr gültig
- Spieler verlässt den Server

Wird das Ziel ungültig, während die Taste weiterhin gehalten wird, muss die Taste zunächst losgelassen werden. Dadurch kann dieselbe Taste nicht mitten im Tastendruck unbeabsichtigt ein anderes Modul übernehmen.

## Tasten und Maustasten

Normale Tastaturtasten und Maustasten können als Aktivierung verwendet werden. Eine leere oder ungültige Belegung bleibt inaktiv.

Aeroworks speichert Tastaturerfassungen normalerweise als Keycodes. Ungewöhnliche reine Scancode-Belegungen werden aus Sicherheitsgründen nicht als gedrückt behandelt.

## Clientkonfiguration

Die kombinierte Steuerung besitzt clientseitige Einstellungen für:

- Empfindlichkeit
- Umkehrung der Y-Achse
- maximale Paketrate zum Server

Die Paketrate ist auf den sinnvollen Bereich von 1 bis 20 Aktualisierungen pro Sekunde begrenzt. Änderungen betreffen nur die lokale Bedienung und ersetzen nicht die serverseitige Prüfung des Ziels und Kanals.

## Fehlerdiagnose

### Modus erscheint nicht

`Kombiniert` wird für kontinuierliche Steuerkanäle ergänzt. Button, Button Panel und Button Keypad besitzen binäre Press-Kanäle und behalten ihre normalen Eingabemodi.

### Taste wird angezeigt, aber nichts bewegt sich

Prüfe:

- Ist der Kanal tatsächlich im Modus **Kombiniert**?
- Ist die Taste korrekt gebunden und aktuell gedrückt?
- Wird das richtige Modul angesehen?
- Ist Shift gedrückt?
- Ist ein Menü geöffnet oder das Fenster nicht aktiv?
- Wurde der Kanal oder das Modul nach der Aktivierung verändert?

### Falsche Richtung

Passe die Y-Achsen-Umkehrung in der Clientkonfiguration an. X-Achsen werden dadurch nicht umgekehrt.

### Zwei Kanäle bewegen sich gleichzeitig

Beide Kanäle verwenden dieselbe Aktivierungstaste. Weise getrennte Tasten zu, wenn sie unabhängig gesteuert werden sollen.
