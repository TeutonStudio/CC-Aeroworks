# Unterstützte Laufzeit- und Testmatrix

Diese Matrix definiert die minimale Baseline für CC-Aeroworks auf Minecraft 1.21.1. Sie ist absichtlich paarweise aufgebaut: Jeder kritische Pfad wird mindestens einmal geprüft, ohne jede theoretische Kombination zu erzeugen.

## Feste Basis

| Komponente | Baseline |
|---|---|
| Java | 21 |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.228 |
| KotlinForForge | 5.11.0 |
| Create | 6.0.10 |
| Create: Aeronautics | 1.3.0 |
| Create: Aeroworks | 1.3.0 |

Andere Versionen gelten erst dann als unterstützt, wenn ein eigenes Profil hinzugefügt und vollständig ausgeführt wurde.

## Profile

| Profil | Laufzeit | CC:Tweaked | Flywheel | Sable | Drive By Wire | Verbindung | Zweck |
|---|---|---:|---|---|---|---|---|
| `BASE-CLIENT` | Client + integrierter Server | 1.119.0 | aktiv | nein | nein | direkt | Kernfunktionen, UI, Text-/Pixelanzeige |
| `BASE-SERVER` | Dedicated Server | 1.119.0 | n/a | nein | nein | Wired Modem | Serverstart, Capability, Events, Persistenz |
| `FALLBACK-CLIENT` | Client + integrierter Server | 1.119.0 | deaktiviert | nein | nein | direkt | Fallback-Rendering |
| `MULTI-COMPUTER` | Client + integrierter Server | 1.119.0 | aktiv | nein | nein | Wired Modem, zwei Computer | Eventfanout und Attach/Detach |
| `CC-120` | Client + integrierter Server | 1.120.0 | aktiv | nein | nein | direkt | obere CC-Kompatibilität |
| `SABLE-STATIC` | Client + integrierter Server | 1.119.0 | aktiv | statisches Schiff | nein | direkt | Transform und Rendering auf Schiff |
| `SABLE-MOVING` | Client + integrierter Server | 1.119.0 | aktiv | bewegtes Schiff | nein | Wired Modem | Eingabe, Rendering und Lifecycle in Bewegung |
| `DRIVEBYWIRE` | Client + integrierter Server | 1.119.0 | aktiv | nein | 0.2.9 | direkt | optionale Integration installiert |
| `FULL-SERVER` | Dedicated Server | 1.119.0 | n/a | 2.0.1 | 0.2.9 | Wired Modem | Clientklassentrennung mit allen optionalen Mods |

## Abdeckung je Profil

### `BASE-CLIENT`

Pflichtfälle:

- `BUILD-CLIENT-01`
- `PERIPHERAL-DIRECT-01`
- `DISPLAY-TEXT-01` bis `DISPLAY-TEXT-04`
- `DISPLAY-PIXEL-01` bis `DISPLAY-PIXEL-04`
- `RENDER-FLYWHEEL-01`
- `COMBINED-LEVER-01`
- `COMBINED-JOYSTICK-01`
- `COMBINED-THROTTLE-01`
- `GUIDE-01`

### `BASE-SERVER`

Pflichtfälle:

- `BUILD-SERVER-01`
- `PERIPHERAL-MODEM-01`
- `PERIPHERAL-LIFECYCLE-01`
- `EVENT-01`
- `DISPLAY-PERSIST-01`

### `FALLBACK-CLIENT`

Pflichtfälle:

- `RENDER-FALLBACK-01`
- `RENDER-FALLBACK-02`
- `DISPLAY-PIXEL-02`

### `MULTI-COMPUTER`

Pflichtfälle:

- `EVENT-MULTI-01`
- `EVENT-DETACH-01`
- `PERIPHERAL-LIFECYCLE-01`

### `CC-120`

Pflichtfälle:

- `BUILD-CLIENT-01`
- `PERIPHERAL-DIRECT-01`
- `EVENT-01`
- `DISPLAY-TEXT-01`
- `DISPLAY-PIXEL-01`

### `SABLE-STATIC` und `SABLE-MOVING`

Pflichtfälle:

- `SABLE-MOUNT-01`
- `SABLE-RENDER-01`
- `SABLE-COMBINED-01`
- `SABLE-LIFECYCLE-01`

`SABLE-MOVING` muss alle Fälle während realer Schiffsbewegung ausführen. Ein stillstehendes Schiff mit optimistischer Beschriftung zählt nicht als Bewegungstest.

### `DRIVEBYWIRE`

Pflichtfälle:

- `DRIVEBYWIRE-LOAD-01`
- `DRIVEBYWIRE-INPUT-01`

### `FULL-SERVER`

Pflichtfälle:

- `BUILD-SERVER-01`
- `DRIVEBYWIRE-LOAD-01`
- Serverstart ohne Clientklassen- oder Mixinfehler

## Lebenszyklusvarianten

Mindestens die Profile `BASE-SERVER`, `MULTI-COMPUTER` und `SABLE-MOVING` prüfen:

1. Welt speichern und neu laden.
2. Desk-Chunk entladen und erneut laden.
3. Computer trennen und erneut verbinden.
4. Displaymodul demontieren und neu montieren.
5. Desk abbauen.
6. Dimension wechseln, während Combined Input aktiv ist.
7. Clientfokus und Menü öffnen, während Combined Input aktiv ist.

## Ergebniszustände

Jeder Fall erhält genau einen Zustand:

- `PASS`: Erwartung vollständig erfüllt und Nachweis vorhanden.
- `FAIL`: reproduzierbare Abweichung; Issue oder Fix-Commit ist verlinkt.
- `BLOCKED`: Testumgebung oder Abhängigkeit fehlt; Blocker ist konkret benannt.
- `NOT RUN`: noch nicht ausgeführt. Dieser Zustand ist niemals releasefähig.

## Release-Gate

Ein Release ist blockiert, wenn:

- ein Pflichtfall in `BASE-CLIENT` oder `BASE-SERVER` nicht `PASS` ist,
- ein Crash, Weltverlust, Rechtefehler oder dauerhafter Server-/Client-Desync offen ist,
- `CC-120` als unterstützt beworben wird, aber nicht vollständig `PASS` ist,
- eine optionale Integration als unterstützt beworben wird, deren Profil `FAIL`, `BLOCKED` oder `NOT RUN` enthält,
- kein Ergebnisbericht den getesteten Commit und die exakten Artefaktversionen nennt.

## Abhängigkeitsverzeichnisse

Jedes Profil verwendet ein eigenes, sauberes Verzeichnis. Beispiel:

```bash
./gradlew -Pmod_dependency_dir=test-mods/base-cc-1.119 clean test build
./gradlew -Pmod_dependency_dir=test-mods/cc-1.120 clean test build
```

Mehrere CC:Tweaked-Versionen oder doppelte Modartefakte dürfen nicht gemeinsam im Classpath liegen. Die Gradle-Prüfung lehnt doppelte Treffer ab.
