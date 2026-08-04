# Branch-Änderungen: Computer-Steuerungspult und Steuerungspult-Multiblock

## Status

Dieser Branch ist der Arbeitsbranch für das Computer-Steuerungspult und die Multiblock-Unterstützung.

Aktueller Stand dieses ersten Commits:

- Der technische Implementierungsplan ist vollständig dokumentiert.
- Es wurden noch keine Produktionsklassen, Ressourcen oder Rezepte implementiert.
- Alle nachfolgend beschriebenen Änderungen sind als Arbeitsumfang dieses Branches vorgesehen.
- Ausgangspunkt ist `master` auf Commit `464994c68c13d0dc031ddd2f07ff3bf349b4451c`.

## Ziel

CC-Aeroworks erhält zwei neue Steuerungspultvarianten:

- `cc_aeroworks:computer_control_desk`
- `cc_aeroworks:advanced_computer_control_desk`

Beide Varianten verhalten sich gegenüber Aeroworks wie ein reguläres Steuerungspult und können dieselben Module aufnehmen. Zusätzlich enthalten sie einen vollständigen CC:Tweaked-Computer.

Die normale und die Advanced-Variante unterscheiden sich ausschließlich durch die CC:Tweaked-Programmieroberfläche beziehungsweise Computerfamilie:

- normales Computer-Steuerungspult: `ComputerFamily.NORMAL`
- erweitertes Computer-Steuerungspult: `ComputerFamily.ADVANCED`

Multiblock-, Aeroworks-, Lua- und Modulverhalten bleiben für beide Varianten identisch.

## Nutzerverhalten

### Herstellung

Es werden zwei Rezepte ergänzt:

1. Aeroworks-Steuerungspult und normaler CC:Tweaked-Computer ergeben ein Computer-Steuerungspult.
2. Aeroworks-Steuerungspult und Advanced-Computer ergeben ein erweitertes Computer-Steuerungspult.

Das Rezept muss Daten beider Eingaben erhalten:

- vom Steuerungspult vorhandene Aeroworks-Modulinhalte und zugehörige Komponenten,
- vom Computer Computer-ID, Beschriftung, Speicherzustand und weitere relevante CC:Tweaked-Komponenten.

Ein bereits verwendeter Computer darf beim Crafting nicht stillschweigend seine Computer-ID und damit sein Dateisystem verlieren.

### Multiblock

Ein Steuerungspult-Multiblock besteht aus direkt kompatibel verbundenen Steuerungspultblöcken. Als Mitglieder gelten:

- `aeroworks:control_desk`,
- `cc_aeroworks:computer_control_desk`,
- `cc_aeroworks:advanced_computer_control_desk`.

Für die erste Implementierung wird eine klar vorhersehbare lineare Verbindung verwendet:

- gleiche Dimension beziehungsweise dasselbe Sable-Sublevel,
- gleiche Blockhöhe,
- gleiche horizontale Ausrichtung,
- direkte Nachbarschaft an der lokalen linken oder rechten Seite des Steuerungspults.

Dadurch kann die Computervariante links, rechts oder in der Mitte einer Pultreihe stehen. Rückseitig, diagonal oder unterschiedlich ausgerichtet aneinander gesetzte Pulte verbinden sich nicht versehentlich.

Die Netzgröße erhält eine feste Schutzgrenze, zunächst 64 Steuerungspulte. Der Resolver darf keine Chunks laden und keine unbegrenzte Suche auslösen.

### Terminal öffnen

Enthält ein gültiger Multiblock genau ein Computer-Steuerungspult, kann das Terminal von jedem Mitglied dieses Multiblocks geöffnet werden.

Das bedeutet:

- Ein Rechtsklick auf das Computer-Steuerungspult öffnet dessen Terminal.
- Ein Rechtsklick auf jedes reguläre Aeroworks-Steuerungspult desselben Multiblocks öffnet dasselbe Terminal.
- Der Abstand zum eigentlichen Computerblock ist nicht maßgeblich, solange der Spieler ein erreichbares Mitglied des Multiblocks anklickt.
- Das Menü bleibt nur geöffnet, solange der Spieler ein Mitglied desselben gültigen Multiblocks erreichen kann.

Aeroworks-Interaktionen müssen erhalten bleiben. Die endgültige Klickreihenfolge lautet:

1. Modulmontage, Werkzeug- und Wrench-Interaktionen behalten Vorrang.
2. Ein vorhandenes Aeroworks-Modulmenü darf nicht durch die Terminalweiterleitung unzugänglich werden.
3. Ein Klick auf den freien Pultkörper öffnet das Terminal.
4. Falls Aeroworks für den gesamten Block bereits eine leere-Hand-Interaktion beansprucht, wird eine eindeutig dokumentierte Ausweichinteraktion ergänzt, vorzugsweise Schleichen und Rechtsklick mit leerer Hand.

Vor der Implementierung wird der exakte `ConsoleBlock`-Interaktionspfad in der Aeroworks-1.3.0-JAR erneut mit `javap` geprüft. Es wird kein pauschales `@Overwrite` verwendet.

### Kein Computer im Multiblock

Enthält ein Multiblock kein Computer-Steuerungspult, bleibt sein Verhalten unverändert. Er ist weiterhin über angrenzende Computer oder kabelgebundene Modems als normales `cc_aeroworks_control_desk`-Peripheral verwendbar.

### Mehrere Computer im Multiblock

Ein Multiblock mit mehreren Computer-Steuerungspulten ist mehrdeutig. Dateisysteme oder Computer-IDs dürfen nicht automatisch zusammengeführt werden.

Deshalb wird ein expliziter Konfliktzustand verwendet:

- Jedes Computer-Steuerungspult behält seine eigenen persistenten Computerdaten.
- Reguläre Pultblöcke öffnen in diesem Zustand kein zufällig gewähltes Terminal.
- Der Spieler erhält eine übersetzte Meldung, dass mehrere Computer-Steuerungspulte verbunden sind.
- Das direkte Anklicken eines Computer-Steuerungspults darf dessen eigenes Terminal weiterhin öffnen, damit Daten erreichbar bleiben und der Konflikt behoben werden kann.
- Nach dem Trennen des Multiblocks wird die gültige Einzelcomputerstruktur automatisch neu erkannt.

Ein positionsabhängig ausgewählter „primärer“ Computer wird bewusst nicht verwendet, da sich die Auswahl beim Umbau oder bei Chunk-Ladevorgängen ändern könnte.

## Technische Architektur

## 1. Neue Registrierungen

Folgende Registrierungsgrenzen werden ergänzt:

- `CCBlocks`
  - normales Computer-Steuerungspult,
  - Advanced-Computer-Steuerungspult.
- `CCBlockEntities`
  - gemeinsamer BlockEntityType für beide Computer-Steuerungspulte.
- `CCDataComponents`
  - nur soweit zusätzliche, nicht bereits von CC:Tweaked bereitgestellte Itemdaten benötigt werden.
- `CCRecipeSerializers`
  - Serializer für das komponentenerhaltende Kombinationsrezept.
- `CCComputerComponents`
  - CC:Tweaked-Computerkomponente, über die nur eingebettete Pultcomputer die Aeroworks-Lua-API erhalten.
- `CCLuaApis`
  - Registrierung der eingebetteten Lua-API über `ComputerCraftAPI.registerAPIFactory`.

Die Hauptklasse `CCAeroworks` registriert diese Bestandteile in einer festen Reihenfolge vor den davon abhängigen Capability- und API-Registrierungen.

## 2. Blockklasse

Eine gemeinsame Blockklasse `ComputerControlDeskBlock` wird vorgesehen.

Bevorzugte Vererbung:

```text
ComputerControlDeskBlock
  -> Aeroworks ConsoleDeskBlock
  -> Aeroworks ConsoleBlock
```

Damit bleiben Form, Ausrichtung, Montagepunkte und das normale Aeroworks-Pultverhalten erhalten.

Vor Umsetzung werden folgende Punkte gegen die Ziel-JAR verifiziert:

- `ConsoleDeskBlock` ist nicht final.
- Der öffentliche Konstruktor mit `ConsoleType` kann mit dem bestehenden Desk-Typ verwendet werden.
- Die von `ConsoleBlock` erwarteten BlockEntity-Hooks können für den eigenen BlockEntityType sauber überschrieben werden.
- Aeroworks führt keine harte Registry-ID-Prüfung ausschließlich auf `aeroworks:control_desk` durch.

Falls die BlockEntity-Auswahl in `ConsoleBlock` fest auf `aeroworks:console` gebunden ist, wird ein gezielter, signaturgebundener Hook ergänzt. Es wird weder die gesamte Klasse überschrieben noch die globale Minecraft-BlockEntity-Prüfung verändert.

## 3. BlockEntity und Mehrfachvererbung

`ConsoleBlockEntity` und CC:Tweaked-`ComputerBlockEntity` sind Klassen. Eine direkte Mehrfachvererbung ist daher nicht möglich.

Die geplante BlockEntity lautet:

```text
ComputerControlDeskBlockEntity
  -> Aeroworks ConsoleBlockEntity
  + eingebetteter CC:Tweaked-Computerhost als Komposition
```

Damit bleiben erhalten:

- Aeroworks-Modularray,
- Modul-NBT,
- Socket-Hit-Tests,
- Montage und Demontage,
- Aeroworks-Datensynchronisation,
- CC-Aeroworks-Displayzustände.

Zusätzlich verwaltet die BlockEntity den CC:Tweaked-Lebenszyklus:

- Computer-ID,
- Instanz-UUID des laufenden `ServerComputer`,
- Label,
- Ein-/Aus-Zustand,
- Startzustand nach Laden,
- Speichergröße,
- normale oder Advanced-Computerfamilie,
- Terminalzustand,
- Dateisystem-Mount,
- Redstone- und Peripheral-Eingänge, soweit mit der Zielversion stabil integrierbar.

Der Lebenszyklus orientiert sich eng an CC:Tweakeds `AbstractComputerBlockEntity`, wird jedoch nicht blind kopiert. Gemeinsame Schritte:

- Computer bei Bedarf erzeugen und registrieren.
- Computer pro Servertick am Leben halten.
- Status- und Labeländerungen synchronisieren.
- Beim Chunk-Unload die laufende Instanz schließen, aber Computer-ID und Dateisystem erhalten.
- Beim endgültigen Blockabbau denselben persistenten Zustand auf das Item übertragen.
- Nach Bewegung auf einem Sable-Schiff die Position des `ServerComputer` aktualisieren, statt einen neuen Computer zu erzeugen.

## 4. Rendering

Der neue Block soll optisch einem Aeroworks-Steuerungspult entsprechen. Normale und Advanced-Variante erhalten kein unterschiedliches Gehäuse; der Unterschied bleibt auf die Programmieroberfläche beschränkt.

Für den eigenen BlockEntityType werden beide Aeroworks-Renderwege angebunden:

- Fallback-BlockEntityRenderer auf Basis von `ConsoleRenderer`,
- Flywheel-Visual auf Basis von `ConsoleVisual`.

Da `ComputerControlDeskBlockEntity` eine Unterklasse von `ConsoleBlockEntity` ist, sollen die vorhandenen Renderer wiederverwendet werden.

Vor Umsetzung wird geprüft:

- Konstruktor und Sichtbarkeit von `ConsoleRenderer`,
- Visual-Factory und Konstruktor von `ConsoleVisual`,
- Registrierung des Visuals für einen zusätzlichen BlockEntityType,
- Verhalten auf Sable-Sublevels.

Falls die Flywheel-Registrierung nicht öffentlich erweiterbar ist, wird nur die kleinste notwendige Registrierungsbrücke ergänzt. Globale Renderer-Hooks bleiben ausgeschlossen.

## 5. Multiblock-Resolver

Der Multiblock wird abgeleitet und nicht dauerhaft als fremdanfällige Blockpositionsliste gespeichert.

Geplante Klassen:

- `ConsoleMultiblockResolver`
- `ConsoleMultiblockSnapshot`
- `ConsoleMultiblockManager`
- `ConsoleMemberKind`
- `ConsoleNetworkState`

Ein Snapshot enthält mindestens:

- kanonischen Anker,
- sortierte Mitglieder,
- gefundene Computer-Steuerungspulte,
- gültigen Besitzer oder Konfliktzustand,
- Revisionsnummer,
- Größen- und Ladefehler.

Die Mitgliedschaft wird per begrenzter Breitensuche ermittelt. Die Sortierung erfolgt in lokaler Links-nach-rechts-Richtung der gemeinsamen Pultausrichtung.

Neuberechnung wird ausgelöst durch:

- Platzieren eines kompatiblen Pults,
- Abbauen eines kompatiblen Pults,
- Änderung eines direkten Nachbarn,
- Chunk-Laden und Chunk-Unload,
- Laden oder Entfernen einer BlockEntity,
- Sable-Montage beziehungsweise Rückkehr in eine Welt, soweit entsprechende Ereignisse verfügbar sind.

Die Neuberechnung wird auf den nächsten Servertick verschoben, damit Block und BlockEntity nach Nachbarschaftsänderungen konsistent sind.

Es findet keine vollständige Multiblocksuche pro Tick statt.

## 6. Persistenz und Itemdaten

Das Computer-Steuerungspult muss beim Abbau beide Datengruppen erhalten:

### Aeroworks-Daten

- montierte Module,
- Modulwerte und Modulkonfiguration,
- Displaytexte und Pixelzustände,
- rekursive Modulköpfe,
- weitere von Aeroworks über `controller_contents` gespeicherte Daten.

### CC:Tweaked-Daten

- Computer-ID,
- Label,
- Speicherkapazität,
- erforderliche Terminaldaten,
- normale oder Advanced-Familie über den Itemtyp beziehungsweise Blocktyp.

Die BlockEntity ergänzt `collectImplicitComponents` und `applyImplicitComponents`, ohne Aeroworks' Implementierung zu überspringen.

Für Rezepte wird kein einfaches Vanilla-Shapeless-Rezept verwendet, wenn dadurch Komponenten verloren gehen. Stattdessen wird ein eigenes Rezept implementiert, das:

1. genau ein kompatibles Aeroworks-Steuerungspult erkennt,
2. genau einen passenden CC:Tweaked-Computer erkennt,
3. den Variantentyp aus dem Computer bestimmt,
4. Aeroworks-Komponenten vom Pult übernimmt,
5. Computerkomponenten vom Computer übernimmt,
6. alle anderen Eingabekombinationen ablehnt.

Ein normales Computer-Steuerungspult darf nicht durch ein Advanced-Rezept erzeugt werden und umgekehrt.

## 7. Terminalmenü

Das Menü verwendet CC:Tweakeds vorhandene Computer-Menü- und Terminalnetzwerkklassen. Es wird keine eigene Lua-Konsole nachgebaut.

Beim Öffnen:

- wird der Computer bei Bedarf erzeugt,
- eingeschaltet,
- der passende normale oder Advanced-Terminalzustand verwendet,
- ein ItemStack der korrekten Pultvariante mit sicheren Computerkomponenten an das Menü übergeben.

Die Benutzbarkeitsprüfung darf nicht nur die Entfernung zum Besitzerblock prüfen. Sie verwendet den aktuellen Multiblock:

- Spieler in derselben Welt beziehungsweise demselben Sublevel,
- gültiger Einzelcomputer-Multiblock,
- Spieler befindet sich in Reichweite mindestens eines geladenen Mitglieds,
- der Besitzerblock ist noch derselbe Computer,
- kein Konflikt- oder Übergrößenzustand.

Dadurch bleibt das Terminal auch dann offen, wenn der Computer am anderen Ende einer größeren Pultreihe steht.

## 8. Zugriff von regulären Aeroworks-Pulten

Reguläre Aeroworks-Blöcke gehören nicht CC-Aeroworks und können deshalb nicht direkt überschrieben werden.

Geplant ist ein kleiner Mixin- oder Invoker-Pfad auf die konkrete `ConsoleBlock`-Interaktionsmethode:

- nur für bestätigte Control-Desk-Blöcke,
- nur bei passender Hand- und Spielerinteraktion,
- nur wenn ein gültiger Multiblockcomputer existiert,
- serverseitige Reichweitenprüfung,
- keine Änderung anderer Aeroworks-Konsolenarten,
- keine Änderung von Modulmontage, Wrench oder Combined-Steuerung.

Der Hook wird mit vollständigem Methodendeskriptor dokumentiert und in `docs/implementation-log.md` aufgenommen.

## 9. Lua-API des eingebetteten Computers

Der eingebettete Computer erhält ein Lua-Modul `aeroworks`. Gewöhnliche CC:Tweaked-Computer, Turtles und Pocket Computer erhalten dieses Modul nicht automatisch.

Die Einschränkung erfolgt über eine eigene `ComputerComponent`, die nur beim Erzeugen des Pultcomputers an dessen `ServerComputer.Properties` übergeben wird.

Vorgesehene API:

```lua
local desks = aeroworks.getDesks()
local desk = aeroworks.getDesk(idOrIndex)
local modules = aeroworks.getModules(desk)
local module = aeroworks.getModule(desk, socket)
local input = aeroworks.getInput(desk, socket)
local inputs = aeroworks.getInputs(desk)
local displays = aeroworks.getDisplays(desk)
local display = aeroworks.getDisplay(desk, socket)

aeroworks.setDisplayText(desk, socket, text)
aeroworks.setDisplayNumber(desk, socket, value, zeroPad)
aeroworks.clearDisplay(desk, socket)
aeroworks.clearDisplays(desk)
aeroworks.getDisplaySize(desk, socket)
aeroworks.getDisplayPixel(desk, socket, x, y)
aeroworks.setDisplayPixel(desk, socket, x, y, enabled)
aeroworks.setDisplayPixels(desk, socket, rows)
aeroworks.clearDisplayPixels(desk, socket)
```

Desk-Beschreibungen enthalten mindestens:

- stabile Desk-ID,
- Netzwerkindex,
- lokale Blockposition,
- Angabe, ob es der Computerbesitzer ist,
- Blockvariante,
- Ausrichtung,
- geladener Zustand.

Socketangaben verwenden weiterhin die bestehende API:

- `left` beziehungsweise `0`,
- `right` beziehungsweise `1`,
- `big` beziehungsweise `2`.

Die bestehende externe Peripheral-API bleibt kompatibel.

## 10. Gemeinsamer Desk-Service

`ControlDeskPeripheral` enthält derzeit sowohl CC-Methoden als auch die eigentliche Desk-Aufbereitung. Diese Logik wird vor der Multiblock-API getrennt.

Geplante Struktur:

```text
AeroworksDeskService
  - Socketvalidierung
  - Modulbeschreibung
  - Eingaben lesen
  - Displays lesen und schreiben
  - Pixelvalidierung

ControlDeskPeripheral
  - delegiert Einzelpultaufrufe an AeroworksDeskService

ComputerConsoleLuaApi
  - delegiert Multiblockaufrufe an AeroworksDeskService
```

Damit verwenden Einzelperipheral und eingebetteter Computer dieselben Formatierungs-, Fehler- und Validierungsregeln.

## 11. Desk-Identität

Jedes geladene Pult erhält eine stabile UUID, die über Chunk-Neuladen und Sable-Bewegungen erhalten bleibt.

Bevorzugt wird eine serialisierte NeoForge-AttachmentType-Komponente an `ConsoleBlockEntity`. Falls Attachments auf der Zielversion nicht zuverlässig mit dem Aeroworks-Itemzustand interagieren, wird die ID über einen kleinen Mixin-NBT-Zusatz gespeichert.

Die UUID wird nicht als alleinige Lua-Oberfläche verwendet. `getDesks()` liefert zusätzlich einen gut lesbaren, links-nach-rechts sortierten Netzwerkindex.

## 12. Eingabeereignisse

Die derzeitige Eingabeüberwachung ist an aktive Einzelperipherals gekoppelt. Für Multiblockcomputer wird sie zu einem gemeinsamen Monitor refaktoriert.

Ziele:

- jedes abonnierte Pult höchstens einmal pro Tick abfragen,
- Änderungen an externe Peripheral-Computer und eingebettete Multiblockcomputer verteilen,
- den ersten Snapshot weiterhin nicht als Änderung melden,
- bei Modulabbau oder Kanalentfernung einen definierten Änderungszustand erzeugen,
- beim Verlassen des Multiblocks keine veralteten Abonnements behalten.

Die bestehende Veranstaltung bleibt für externe Peripherals unverändert:

```text
cc_aeroworks_desk_input
```

Für den Multiblockcomputer wird eine eindeutigere Veranstaltung ergänzt:

```text
cc_aeroworks_console_input
```

Vorgesehene Argumente:

1. Desk-ID,
2. Netzwerkindex,
3. Socketindex,
4. Socketname,
5. Modul-ID,
6. Wert,
7. Kanal.

Zusätzliche Strukturereignisse:

- `cc_aeroworks_console_changed`
- `cc_aeroworks_desk_added`
- `cc_aeroworks_desk_removed`
- `cc_aeroworks_console_conflict`

## 13. Redstone und fremde Peripherals

Der eingebettete Block soll sich möglichst wie ein vollständiger CC:Tweaked-Computer verhalten.

Geplant:

- Redstone-Ein- und Ausgänge relativ zur Ausrichtung des Computer-Steuerungspults,
- Erkennung angrenzender fremder Peripherals an freien Seiten,
- keine zusätzliche seitliche Einbindung der eigenen Multiblock-Pulte als sechs normale CC-Seitenperipherals,
- Aeroworks-Multiblockzugriff ausschließlich über das `aeroworks`-Lua-Modul.

Die linke und rechte Seite können durch Multiblockmitglieder belegt sein. Fremde Peripherals auf anderen Seiten bleiben nutzbar.

Sollten CC:Tweakeds interne Plattformzugriffe in 1.119 und 1.120 inkompatibel voneinander abweichen, wird die Funktion in eine kleine versionsgebundene Compat-Schicht gekapselt. Der Computer darf nicht wegen optionaler Seitenerkennung seinen Kernlebenszyklus verlieren.

## 14. Capabilities und Create Display Targets

Das Computer-Steuerungspult bleibt selbst ein Aeroworks-Pult und soll daher auch von einem externen Computer als `cc_aeroworks_control_desk` erkannt werden können.

Dafür werden die bisherigen hart auf `aeroworks:console` gerichteten Registrierungen erweitert:

- `ControlDeskPeripheralRegistry` registriert das Peripheral sowohl für Aeroworks' BlockEntityType als auch für den neuen Computer-Pult-BlockEntityType.
- `CCDisplayTargets` registriert das bestehende Desk-DisplayTarget ebenfalls für den neuen Typ.

Die BlockEntityType-Auflösung wird zentralisiert und mit verständlichen Fehlermeldungen versehen. Der derzeitige ungesicherte Cast in `CCDisplayTargets` wird dabei an die robustere Prüfung aus `ControlDeskPeripheralRegistry` angeglichen.

## 15. Modelle, Loot, Sprache und Creative Tab

Geplante Ressourcen:

- Blockstates für beide Varianten,
- Blockmodelle mit Verweis auf das vorhandene Aeroworks-Control-Desk-Modell,
- Itemmodelle,
- Loot Tables mit Komponentenübernahme,
- normale und Advanced-Rezepte,
- deutsche und englische Übersetzungen,
- Einträge im Abschnitt `CC-Aeroworks` des Aeroworks-Creative-Tabs,
- Guide-Book-Erweiterung.

Deutsche Namen:

- `Computer-Steuerungspult`
- `Erweitertes Computer-Steuerungspult`

Englische Namen:

- `Computer Control Desk`
- `Advanced Computer Control Desk`

Beide Varianten verwenden dasselbe Pultmodell. Eine optische Unterscheidung wird nicht eingeführt, solange keine solche Anforderung besteht.

## 16. Dokumentation

Folgende Dokumente werden aktualisiert oder ergänzt:

- `README.md`
- `docs/cc-peripheral-api.md`
- `docs/peripheral-programming.md`
- `docs/configuration.md`
- `docs/implementation-log.md`
- `docs/manual-test-plan.md`
- `docs/project-status.md`
- Ingame-Guide-Book auf Deutsch und Englisch

Die Dokumentation erklärt ausdrücklich:

- Multiblock-Verbindungsregeln,
- genau einen Computer pro gültigem Multiblock,
- normales gegenüber Advanced-Terminal,
- Crafting und Datenübernahme,
- Lua-Desk-Auswahl,
- Konfliktmeldungen,
- Chunk- und Sable-Verhalten.

## Geplante Korrekturen am bestehenden Projekt

Während dieser Umsetzung dürfen gefundene Probleme korrigiert werden. Bereits im Plan berücksichtigt sind:

### Zentralisierung der BlockEntityType-Auflösung

`ControlDeskPeripheralRegistry` und `CCDisplayTargets` lösen `aeroworks:console` getrennt auf. Das wird durch eine gemeinsame Compat-Funktion ersetzt.

### Sicherer DisplayTarget-Cast

`CCDisplayTargets` castet den Registryeintrag derzeit ohne Null- und Typprüfung. Die Registrierung erhält dieselbe verständliche Fehlerbehandlung wie das Peripheral-Register.

### Gemeinsame Desk-Methoden

Die Desk- und Displaylogik wird aus `ControlDeskPeripheral` extrahiert, um Unterschiede zwischen Einzel- und Multiblock-API zu verhindern.

### Gemeinsamer Eingabemonitor

Mehrere angeschlossene Computer können derzeit dasselbe Pult über getrennte Peripheralinstanzen beziehungsweise spätere Multiblockabonnements wiederholt abfragen. Der neue Monitor dedupliziert Desk-Snapshots und verteilt Ereignisse an mehrere Empfänger.

### Entfernte Kanäle und Module

Die bestehende Schleife meldet geänderte vorhandene Kanäle, aber nicht ausdrücklich das Entfernen eines Moduls oder Kanals. Der neue Snapshotvergleich definiert dieses Verhalten und verhindert veraltete Zustände in Multiblockprogrammen.

### Dokumentierte Versionsbindung

Die eingebettete Computerimplementierung verwendet teilweise Klassen aus `dan200.computercraft.shared`. Diese sind keine stabile öffentliche API. Die Abhängigkeit wird in `implementation-log.md` mit den konkret geprüften CC:Tweaked-Versionen dokumentiert und durch eine kleine Compat-Grenze isoliert.

## Implementierungsphasen

### Phase 0: Signaturen erneut verifizieren

- Aeroworks `ConsoleDeskBlock` und `ConsoleBlock` prüfen.
- Eigener BlockEntityType mit `ConsoleBlockEntity`-Unterklasse prüfen.
- Renderer- und Flywheel-Registrierung prüfen.
- CC:Tweaked-Menü-, Computer-, Komponenten- und Rezeptklassen für 1.119.0 und 1.120.0 vergleichen.
- Exakten Interaktionshook für reguläre Pulte festlegen.

### Phase 1: Gemeinsame Dienste refaktorieren

- `AeroworksDeskService` extrahieren.
- BlockEntityType-Auflösung zentralisieren.
- Eingabemonitor vorbereiten.
- Bestehende Unit-Tests an den Service verschieben.

### Phase 2: Multiblockmodell

- Mitgliedserkennung und Sortierung implementieren.
- Zustände `NONE`, `ACTIVE`, `CONFLICT`, `TOO_LARGE`, `PARTIALLY_LOADED` definieren.
- Platzierungs-, Abbau- und Chunk-Invalidierung implementieren.
- Unit- und GameTests ergänzen.

### Phase 3: Blöcke und eingebetteter Computer

- Blöcke, Items und BlockEntity registrieren.
- CC-Computerlebenszyklus implementieren.
- normale und Advanced-Familie anbinden.
- Terminalmenü vom Besitzerblock öffnen.
- Datenpersistenz und Drops implementieren.

### Phase 4: Terminalrouting im Multiblock

- reguläre Aeroworks-Pulte an den Resolver anbinden.
- Menü von jedem Mitglied öffnen.
- Interaktionsprioritäten und Konfliktmeldungen implementieren.
- Reichweiten- und Menüvalidierung testen.

### Phase 5: Lua-API und Ereignisse

- ComputerComponent registrieren.
- `aeroworks`-Modul implementieren.
- Multiblock-Desk-Methoden ergänzen.
- gemeinsamen Eingabemonitor aktivieren.
- Struktur- und Eingabeereignisse dokumentieren.

### Phase 6: Crafting und Ressourcen

- komponentenerhaltendes Rezept implementieren.
- Blockstates, Modelle, Loot Tables und Übersetzungen ergänzen.
- Creative Tab und Guide Book aktualisieren.

### Phase 7: Laufzeit- und Kompatibilitätstests

- normaler Client und Dedicated Server,
- CC:Tweaked 1.119.0 und 1.120.0 getrennt,
- Flywheel aktiv und deaktiviert,
- mit und ohne Drive By Wire,
- normale Welt und Sable-Schiff,
- Einzelspieler und Mehrspieler.

## Testmatrix

### Crafting und Persistenz

- frischer normaler Computer plus leeres Pult,
- frischer Advanced-Computer plus leeres Pult,
- beschrifteter Computer,
- Computer mit bestehender ID und Dateien,
- Pultitem mit montierten Modulen,
- Abbau und erneutes Platzieren,
- Chunk-Unload und Serverneustart,
- falsche oder doppelte Zutaten werden abgelehnt.

### Multiblock

- Computer links, mittig und rechts,
- ein einzelnes Computer-Steuerungspult,
- lange Pultreihe,
- unterschiedliche Ausrichtung trennt Netze,
- Höhenunterschied trennt Netze,
- Bruch in der Mitte teilt den Multiblock,
- späteres Verbinden führt neu zusammen,
- zwei Computer erzeugen Konflikt,
- Entfernen eines Computers löst Konflikt,
- Größenlimit wird verständlich gemeldet,
- Chunkgrenze lädt keine fremden Chunks zwangsweise.

### Terminal

- Öffnen vom Besitzerblock,
- Öffnen von jedem normalen Mitglied,
- normales Terminal ist normal,
- Advanced-Terminal ist farbfähig,
- Modulmontage und Wrench bleiben erreichbar,
- Menü schließt bei ungültigem Multiblock,
- Menü bleibt beim Wechsel der erreichbaren Mitgliedsposition stabil,
- zwei Spieler können denselben Computer entsprechend CC:Tweaked-Verhalten öffnen.

### Lua

- Desk-Liste und stabile IDs,
- Socketnamen und numerische Indizes,
- Eingaben aller Mitglieder,
- Mehrkanalmodule,
- Text- und Pixelanzeigen,
- ungültige Desk- und Socketargumente,
- Ereignisse bei Änderung,
- Ereignisse bei Modulabbau,
- Ereignisse bei Multiblockänderung,
- keine API auf gewöhnlichen Computern.

### Rendering und Sable

- Fallback-Renderer,
- Flywheel-Visual,
- Rotation und Beleuchtung,
- bewegtes Sable-Schiff,
- Montage und Demontage von Modulen,
- Computer-ID bleibt bei Bewegung erhalten,
- keine doppelten Renderer oder Z-Fighting.

## Definition of Done

Der Branch gilt als fertig, wenn:

- beide Rezepte die korrekte Variante erzeugen,
- Computer- und Aeroworks-Daten vollständig erhalten bleiben,
- ein Computer an beliebiger Position einer Pultreihe genügt,
- jedes Mitglied denselben Computer öffnen kann,
- mehrere Computer keinen zufälligen Besitzer erzeugen,
- alle Pulte des Multiblocks über die eingebettete Lua-API erreichbar sind,
- das bestehende Einzelpult-Peripheral kompatibel bleibt,
- normale und Advanced-Variante sich nur durch ihre CC-Terminalfamilie unterscheiden,
- Client und Dedicated Server erfolgreich starten,
- Build, Unit-Tests und relevante GameTests erfolgreich sind,
- die manuelle Testmatrix dokumentiert ausgeführt wurde,
- `BranchChanges.md`, Projektdokumentation und Ingame-Handbuch den tatsächlichen Endstand wiedergeben.
