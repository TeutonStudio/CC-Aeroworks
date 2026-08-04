# v4: Buildbasis, Computer-Steuerungspulte und Multiblock-API

## Enthaltener Stand

`v4` führt die beiden nach `v3` getrennt entwickelten Arbeitslinien zusammen:

- `post-v3-development`: reproduzierbarer Gradle-Einstieg, Dependency-Validierung, Repository-CI, Unit-Testbasis, Integrationsrunner und Dedicated-Server-Smoke-Test.
- `agent/computer-control-desk-multiblock`: normale und erweiterte Computer-Steuerungspulte, komponentenerhaltendes Rezept, eingebetteter CC:Tweaked-Computer, Terminalweiterleitung, Multiblockauflösung, gemeinsame Desk-Services sowie Modell- und Skin-Kompatibilität.

Die Historien beider Branches sind Eltern des Integrationscommits. Der veröffentlichte Masterstand wird als Squash-Commit `v4` erzeugt.

## Korrektur von Issue #1

Der vorherige Featurestand kannte den Multiblock nur innerhalb des eingebetteten Computer-Steuerungspults. Ein gewöhnlicher Computer sah über `cc_aeroworks_control_desk` weiterhin lediglich die physisch verbundene `ConsoleBlockEntity`.

`v4` ergänzt deshalb am bestehenden Peripheral einen vollständigen Multiblockzugriff:

- `getNetwork()`
- `getDesks()` und `getDesk(desk)`
- deskbezogene Socket-, Modul-, Eingabe- und Displaymethoden
- Auswahl über 1-basierten Index oder stabile Desk-ID
- Multiblock-Eingabe- und Strukturereignisse
- Erkennung neuer, geänderter und entfernter Kanäle sowie ersetzter Module

Die bisherigen Einzelpultmethoden bleiben kompatibel und adressieren weiterhin das angeschlossene Pult.

## Direkte API

Der eingebettete Computer verwendet die globale API `aeroworks` direkt und benötigt kein Peripheral. Ergänzt wurden:

- `getNetwork()`
- `getSocketCount(desk)`
- `getSockets(desk)`

Damit ist die Verwaltung jedes Pults einschließlich seiner Sockets vollständig in der direkten API dokumentiert und abrufbar.

## Computer-Steuerungspulte

Enthalten sind:

- `cc_aeroworks:computer_control_desk`
- `cc_aeroworks:advanced_computer_control_desk`
- Erhalt von Aeroworks- und CC:Tweaked-Komponenten beim Crafting und Abbau
- normale beziehungsweise Advanced-Computerfamilie
- Terminalöffnung von jedem geladenen Mitglied eines gültigen Multiblocks
- expliziter Konflikt bei mehreren eingebetteten Computern
- lineare, gleich ausgerichtete Multiblocks bis 64 Mitglieder
- keine Chunk-Nachladung
- kompatible Block-, Item-, Verbindungs- und Multiblockmodelle

## Build und Prüfung

Versioniert und integriert wurden:

- Gradle-Bootstrap für Java 21
- Manifest und Validierung der lokalen Fremdmod-JARs
- Repositoryvertrag
- öffentlicher CI-Vertrag und geschützter Vollbuild
- Unit-Testbasis für Sockets, Modulbeschreibungen, Eingabedifferenzen und Combined Input
- Integrationsprofilrunner
- Dedicated-Server-Smoke-Test
- manuelle Laufzeitmatrix und gesonderter Multiblock-Testplan

Die GitHub-Ausführungsumgebung besitzt die proprietären beziehungsweise nicht redistribuierbaren Ziel-JARs nicht automatisch. Deshalb sind vollständiger Modbuild, Client-, Dedicated-Server-, Sable- und Ingame-Prüfungen weiterhin nur über den geschützten Build oder eine lokal vollständig bestückte Umgebung beweisbar. Nicht ausgeführte Tests werden nicht in `PASS` umbenannt, nur weil der Commit jetzt eine angenehm kurze Bezeichnung besitzt.

## Release-Gate

Vor einer binären Veröffentlichung müssen mindestens bestehen:

- Repositoryvertrag
- `verifyDependencyManifest`
- `verifyModDependencies`
- Unit-Tests und Build
- Dedicated-Server-Smoke-Test
- `BASE-CLIENT` und `BASE-SERVER`
- Multiblockzugriff über genau eine externe Verbindung
- direkte API ohne Peripheral
- Crafting- und Abbaupersistenz
- Konflikt-, Chunk-, Flywheel-, Fallback- und Sable-Fälle
