# Kombinierte Eingabe

Der Eingabetyp **Kombiniert** verbindet eine gehaltene Aktivierungstaste mit Mausbewegungen. Er ist für Situationen gedacht, in denen ein Modul erst bewusst ausgewählt und anschließend präzise mit der Maus verstellt werden soll.

Der Modus ist kein zusätzliches Modul und keine globale Minecraft-Tastenbelegung. Die Aktivierungstaste wird pro Kanal direkt in der vorhandenen Aeroworks-Konfiguration des Moduls gespeichert.

## Unterstützte Module und Kanäle

| Modul | Kanal | Mausachse |
|---|---|---|
| Lever | `lever` | Y |
| Joystick | `x` | X |
| Joystick | `y` | Y |
| Throttle Quadrant | `red` | Y |
| Throttle Quadrant | `amber` | Y |
| Throttle Quadrant | `green` | Y |
| Throttle Quadrant | `blue` | Y |

Die Werte bleiben im bestätigten Aeroworks-Bereich von `-15` bis `15`.

## Modus einrichten

1. Öffne den Aeroworks-Modulbildschirm des Levers, Joysticks oder Throttle Quadrants.
2. Klicke mit der linken Maustaste auf das vorhandene Modussymbol der gewünschten Achse.
3. Schalte durch die Folge:

   `Buttons -> Analog -> Kombiniert -> Buttons`

4. Im Modus **Kombiniert** zeigt das mittlere Eingabefeld die Aktivierungstaste statt einer Mausquelle.
5. Linksklicke das Feld und drücke die gewünschte Taste oder Maustaste.
6. Rechtsklick auf das Feld löscht die Belegung.

Beim ersten Wechsel auf **Kombiniert** wird eine leere Belegung mit `K` vorbelegt.

## Steuerung verwenden

1. Schließe den Modulbildschirm.
2. Sieh das gewünschte Modul am Steuerungspult an.
3. Halte die konfigurierte Aktivierungstaste.
4. Bewege die Maus auf der für den Kanal vorgesehenen Achse.
5. Lass die Taste los, um die Steuerung zu beenden.

Während die kombinierte Steuerung aktiv ist, wird die normale Kameradrehung unterdrückt. Die Mausbewegung steuert stattdessen das Modul.

## Achsenverhalten

### Lever

Der Lever verwendet Maus Y. Vertikale Mausbewegungen verändern den Leverwert.

### Joystick

- Kanal `x`: Maus X, also links und rechts
- Kanal `y`: Maus Y, also vor und zurück

Die beiden Kanäle können getrennte Aktivierungstasten besitzen.

Wenn `x` und `y` dieselbe Taste verwenden, werden beide Achsen während derselben gehaltenen Taste gleichzeitig gesteuert. Damit ist echte zweidimensionale Joystickbewegung möglich.

### Throttle Quadrant

Die vier Kanäle `red`, `amber`, `green` und `blue` besitzen jeweils eine eigene Aktivierungstaste. Alle verwenden Maus Y.

Mehrere Kanäle dürfen dieselbe Taste verwenden. In diesem Fall werden sie gemeinsam durch dieselbe Mausbewegung verstellt.

## Zielauswahl

Die Steuerung wird nur aktiviert, wenn alle folgenden Bedingungen erfüllt sind:

- Der Spieler sieht ein Steuerungspult an.
- Das tatsächlich anvisierte Top-Level-Modul unterstützt den Kombiniert-Modus.
- Mindestens ein Kanal dieses Moduls ist auf **Kombiniert** gestellt.
- Die zugehörige Aktivierungstaste wird gehalten.
- Kein anderer Bildschirm ist geöffnet.

Die Auswahl verwendet den vorhandenen Aeroworks-Modulraycast. Dadurch bleiben Desk-Ausrichtung und Transformationen bewegter Konstruktionen erhalten.

## Wann die Steuerung beendet wird

Die aktive Steuerung endet, sobald eine der folgenden Bedingungen eintritt:

- Aktivierungstaste wird losgelassen
- GUI oder Menü wird geöffnet
- Minecraft-Fenster verliert den Fokus
- Spieler stirbt oder wird ersetzt
- Spieler wechselt die Dimension
- Pult oder Chunk ist nicht mehr geladen
- Modul, Socket oder Konfiguration wurde verändert
- Ziel ist nicht mehr gültig
- Spieler verlässt den Server

Wird das Ziel ungültig, während die Taste weiterhin gehalten wird, muss die Taste zunächst losgelassen werden. Dadurch wird verhindert, dass unmittelbar ein anderes Modul mit derselben Taste unbeabsichtigt übernommen wird.

## Tasten und Maustasten

Normale Tastaturtasten und Maustasten können als Aktivierung verwendet werden. Eine leere oder ungültige Belegung bleibt inaktiv.

Aeroworks speichert Tastaturerfassungen normalerweise als Keycodes. Ungewöhnliche reine Scancode-Belegungen werden aus Sicherheitsgründen nicht als gedrückt behandelt.

## Clientkonfiguration

Die kombinierte Steuerung besitzt clientseitige Einstellungen für:

- Empfindlichkeit
- Umkehrung der Y-Achse
- maximale Paketrate zum Server

Die Paketrate ist auf den sinnvollen Bereich von 1 bis 20 Aktualisierungen pro Sekunde begrenzt. Änderungen betreffen nur die lokale Bedienung und ersetzen nicht die serverseitige Prüfung des Ziels und Kanals.

## Tipps

### Joystick mit einer Taste

Lege `x` und `y` auf dieselbe Aktivierungstaste. Beim Halten verarbeitet der Joystick Maus X und Maus Y parallel.

### Throttle gemeinsam bewegen

Lege mehrere Quadrant-Kanäle auf dieselbe Taste, wenn sie gemeinsam bewegt werden sollen. Für unabhängige Schubhebel erhält jeder Kanal eine eigene Taste.

### Unbeabsichtigte Aktivierung vermeiden

Verwende keine Taste, die während normaler Bewegung dauerhaft gedrückt wird. Eine Taste wie `W` wäre technisch möglich, aber ungefähr so klug wie ein Not-Aus-Schalter unter dem Fußpedal.

## Fehlerdiagnose

### Modus erscheint nicht

Der Kombiniert-Modus wird nur für Lever, Joystick und Throttle Quadrant ergänzt. Andere Module bleiben unverändert.

### Taste wird angezeigt, aber nichts bewegt sich

Prüfe:

- Ist das Modul tatsächlich im Modus **Kombiniert**?
- Ist die Taste korrekt gebunden und aktuell gedrückt?
- Wird das richtige Modul angesehen?
- Ist ein Menü geöffnet oder das Fenster nicht aktiv?
- Wurde der Kanal oder das Modul nach der Aktivierung verändert?

### Falsche Richtung

Passe die Y-Achsen-Umkehrung in der Clientkonfiguration an. Maus X des Joysticks wird nicht durch diese Einstellung umgekehrt.

### Zwei Kanäle bewegen sich gleichzeitig

Beide Kanäle verwenden dieselbe Aktivierungstaste. Weise getrennte Tasten zu, wenn sie unabhängig gesteuert werden sollen.
