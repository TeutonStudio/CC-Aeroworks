# Project Status

## Completed

- Projektidentität, Kotlin-Build, Registries, Ressourcen und Packages sind auf `CC-Aeroworks`, `cc_aeroworks` und `de.teutonstudio.ccaeroworks` migriert.
- Der gesamte eigene Produktions- und Testcode einschließlich der Mixins liegt in Kotlin; unter `src/` verbleiben keine Java-Dateien.
- Control Desks erhalten über die CC:Tweaked-BlockEntity-Capability ein gemeinsames Peripheral.
- Die dokumentierte Lua-API, Eingabeänderungsereignisse, beide Aeroworks-Module, Create-DisplayTarget und die kombinierte Lever-Payload sind implementiert.
- Eigene Modulmodelle, Texturen, Rezepte, Fallback- und Flywheel-Ziffernrenderer sind vorhanden.
- Die kombinierte Leversteuerung ist als sichtbares Keybinding `Kombiniert (Lever, Maus Y)` mit Standardtaste `K` registriert.
- Zweistellige Displays akzeptieren kleine und große Aeroworks-Sockets; dreistellige Displays akzeptieren ausschließlich große Sockets.
- Der Aeroworks-Creative-Tab wird clientseitig in die beschrifteten Abschnitte `Aeroworks` und `CC-Aeroworks` gegliedert.
- Drive By Wire 0.2.9 ist als optionale Mod-Abhängigkeit deklariert; eine deutschsprachige Peripheral-Programmierhilfe ist vorhanden.

## Verified

- Aeroworks 1.3.0: öffentliche `ModuleTypes.register`-API, `ConsoleBlockEntity`, `MountedModule[]`, Socket-Hit-Test, Zustandsänderung und Synchronisation.
- CC:Tweaked 1.119.0: `PeripheralCapability`, `IPeripheral`, `AttachedComputerSet` und `@LuaFunction(mainThread = true)`.
- Create 6.0.10: `DisplayTarget` und Registry `create:display_target`.
- Der Client erreicht unter NeoForge 21.1.228 mit allen lokalen Zielmods das Hauptmenü.
- Die neuen Creative-Tab-Mixins werden beim Clientstart ohne Mixin-Apply-Fehler auf `CreativeModeTab` und `CreativeModeInventoryScreen` angewendet.
- Drive By Wire 0.2.9: Mod-ID `drivebywire` und Version wurden direkt aus der lokal vorhandenen JAR gelesen.

## Inferred

- Das wiederverwendete Feld `MountedModule.customName` ist semantisch als Displayzustand geeignet, weil Aeroworks es persistent speichert, synchronisiert und beim Modul-Drop übernimmt.

## Unknown

- Verhalten im produktiven Mehrspielerbetrieb mit CC:Tweaked 1.120.0 wurde nicht interaktiv geprüft.
- Montage, sichtbare Segmentausrichtung, Sable-Luftschifftransformation und Flywheel-Neuaufbau müssen noch im Spiel manuell geprüft werden.
- Das vollständige Combined-Lever-Gefühl und die Servervalidierung wurden kompiliert und geladen, aber noch nicht gegen einen montierten Lever bedient.
- Die Creative-Tab-Schilder wurden geladen, aber ihre optische Position und ihr Scrollverhalten noch nicht manuell im geöffneten Tab geprüft.
- Die Aeroworks-/Drive-By-Wire-Funktionen wurden gemeinsam geladen, aber Kanalwahl und Wire-Veröffentlichung noch nicht interaktiv bedient.

## Build Result

Erfolgreich am 13. Juli 2026: `compileKotlin`, `compileJava` (`NO-SOURCE`), `processResources`, `test`, `build`, `clean build` und `runData`. Nach der Socket-, Keybinding- und Creative-Tab-Änderung erreichte `runClient` die vorhandene Testwelt und wendete alle neuen Mixins an. Ein weiterer Clientlauf mit der lokalen Drive-By-Wire-0.2.9-JAR lud auch `aeroworks-drivebywire.mixins.json` ohne Apply-Fehler. Der erneute Dedicated-Server-Start meldete `Done (0.815s)`.

## Missing Dependencies

Keine im lokalen Entwicklungsverzeichnis. Die ignorierten Pflichtdateien sowie die optionale Drive-By-Wire-Test-JAR unter `libs/` sind auf einem anderen Rechner gemäß `libs/README.md` bereitzustellen.

## Recommended Next Implementation Step

Den Ablauf in `docs/manual-test-plan.md` in einer Testwelt ausführen, insbesondere Montage/Demontage, Neustartpersistenz, Modemzugriff, Flywheel an/aus und Combined Lever auf einem Sable-Schiff.
