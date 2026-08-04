# CC-Aeroworks

CC-Aeroworks verbindet Create: Aeroworks Control Desks mit CC:Tweaked. Die Mod ergänzt programmierbare Pultanzeigen, kombinierte Maussteuerung und Computer-Steuerungspulte für zusammenhängende Cockpits.

## Computer-Steuerungspulte

Ein Aeroworks-Steuerungspult kann mit einem normalen oder erweiterten CC:Tweaked-Computer kombiniert werden. Das Ergebnis behält die Daten beider Zutaten.

Gleich ausgerichtete Pulte verbinden sich direkt links und rechts zu einer Reihe. Genau ein Computer-Steuerungspult genügt an beliebiger Stelle. Mit **Schleichen + Rechtsklick bei leerer Haupthand** lässt sich dasselbe Terminal von jedem Pult der Reihe öffnen.

Die normale und die Advanced-Variante unterscheiden sich ausschließlich durch die CC:Tweaked-Programmieroberfläche.

Im eingebetteten Computer steht die API `aeroworks` zur Verfügung:

```lua
for _, desk in ipairs(aeroworks.getDesks()) do
  print(desk.index, desk.id, desk.variant)
end

local modules = aeroworks.getModules(1)
```

## Einzelpult-Peripheral

Jeder geladene Control Desk bleibt als `cc_aeroworks_control_desk` über direkte Nachbarschaft oder ein kabelgebundenes Modem erreichbar. Sockets heißen `left`, `right` und `big` und akzeptieren kompatibel auch `0`, `1`, `2`.

## Entwicklungsumgebung

- Minecraft 1.21.1
- NeoForge 21.1.228
- Java 21
- Kotlin 2.2.20 / KotlinForForge 5.11.0
- Create 6.0.10
- Aeroworks 1.3.0
- CC:Tweaked 1.119.x bis vor 1.121

Die nicht redistribuierbaren Ziel-JARs werden lokal unter `libs/` bereitgestellt. Danach:

```bash
./gradlew clean build
./gradlew runClient
```

Details stehen in `BranchChanges.md` sowie unter `docs/`.
