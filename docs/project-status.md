# Project Status

## Completed

- Projektidentität, Kotlin-Build, Registries, Ressourcen und Packages sind auf `CC-Aeroworks`, `cc_aeroworks` und `de.teutonstudio.ccaeroworks` migriert.
- Der gesamte eigene Produktions- und Testcode einschließlich der Mixins liegt in Kotlin; unter `src/` verbleiben keine Java-Dateien.
- Control Desks erhalten über die CC:Tweaked-BlockEntity-Capability ein gemeinsames Peripheral.
- Die dokumentierte Lua-API, Eingabeänderungsereignisse, beide Aeroworks-Module, Create-DisplayTarget und die kombinierte Lever-Payload sind implementiert.
- Eigene Modulmodelle, Texturen, Rezepte, Fallback- und Flywheel-Ziffernrenderer sind vorhanden.
- Im Aeroworks-Modulbildschirm existiert der persistente Input Type `Kombiniert` für Lever, beide Joystick-Achsen und alle vier Throttle-Quadrant-Achsen. Die Aktivierungstaste wird pro Achse direkt im mittleren Eingabefeld erfasst; Joystick `x` verwendet Maus X, alle anderen unterstützten Achsen Maus Y.
- Zweistellige Displays akzeptieren kleine und große Aeroworks-Sockets; dreistellige Displays akzeptieren ausschließlich große Sockets.
- Der Aeroworks-Creative-Tab wird clientseitig in die beschrifteten Abschnitte `Aeroworks` und `CC-Aeroworks` gegliedert.
- Drive By Wire 0.2.9 ist als optionale Mod-Abhängigkeit deklariert; eine deutschsprachige Peripheral-Programmierhilfe ist vorhanden.
- Beide Displays besitzen zusätzlich einen persistenten Pixelmodus (`7x5` beziehungsweise `11x5`) mit Einzelpixel- und Raster-Lua-API sowie Fallback-/Flywheel-Darstellung.
- Das registrierte Item `cc_aeroworks:guide_book` öffnet eine eigene lokalisierte API-Dokumentationsoberfläche mit sieben Kapiteln, Codeblöcken, Hinweisen und Navigation; acht Vanilla-Buchseiten bleiben als Datenfallback erhalten.
- Das Guide-Book besitzt einen clientseitigen Öffnungshook, weil Vanilla registrierte `WrittenBookItem`-Unterklassen serverseitig nicht über `openItemGui` öffnet.
- Die Creative-Tab-Kategorieflächen sind vollständig deckend, damit in der Überschriftenzeile keine Vanilla-Slotgrafik sichtbar bleibt.

## Verified

- Aeroworks 1.3.0: öffentliche `ModuleTypes.register`-API, `ConsoleBlockEntity`, `MountedModule[]`, Socket-Hit-Test, Zustandsänderung und Synchronisation.
- CC:Tweaked 1.119.0: `PeripheralCapability`, `IPeripheral`, `AttachedComputerSet` und `@LuaFunction(mainThread = true)`.
- Create 6.0.10: `DisplayTarget` und Registry `create:display_target`.
- Der Client erreicht unter NeoForge 21.1.228 mit allen lokalen Zielmods das Hauptmenü.
- Die neuen Creative-Tab-Mixins werden beim Clientstart ohne Mixin-Apply-Fehler auf `CreativeModeTab` und `CreativeModeInventoryScreen` angewendet.
- Drive By Wire 0.2.9: Mod-ID `drivebywire` und Version wurden direkt aus der lokal vorhandenen JAR gelesen.
- Der Clientstart wendete den erweiterten ModuleScreen-Hook, alle benötigten Invoker/Accessors und die Übersetzung der Input Source `cc_aeroworks.combined` ohne Mixin-Apply-Fehler an. Das Guide-Book-Modell wurde ohne Missing-Model-Fehler geladen.

## Inferred

- Das wiederverwendete Feld `MountedModule.customName` ist semantisch als Displayzustand geeignet, weil Aeroworks es persistent speichert, synchronisiert und beim Modul-Drop übernimmt.

## Unknown

- Verhalten im produktiven Mehrspielerbetrieb mit CC:Tweaked 1.120.0 wurde nicht interaktiv geprüft.
- Montage, sichtbare Segmentausrichtung, Sable-Luftschifftransformation und Flywheel-Neuaufbau müssen noch im Spiel manuell geprüft werden.
- Der erweiterte Input Type und die Steuerung samt kanalspezifischer Servervalidierung wurden kompiliert und geladen, aber noch nicht interaktiv gegen einen montierten Lever beziehungsweise Throttle Quadrant bedient.
- Die Creative-Tab-Schilder wurden geladen, aber ihre optische Position und ihr Scrollverhalten noch nicht manuell im geöffneten Tab geprüft.
- Die Aeroworks-/Drive-By-Wire-Funktionen wurden gemeinsam geladen, aber Kanalwahl und Wire-Veröffentlichung noch nicht interaktiv bedient.
- Pixelzustand und beide Renderer sind kompiliert und geladen; die sichtbare Pixelgeometrie sowie Lua-Aufrufe an einem real montierten Display wurden noch nicht interaktiv geprüft.

## Build Result

Erfolgreich am 13. Juli 2026: `compileKotlin`, `compileJava` (`NO-SOURCE`), `processResources`, `test`, `build`, `clean build` und `runData`. Nach Erweiterung auf vier Throttle-Kanäle und einer pro Achse erfassbaren Aktivierungstaste lud `runClient` bis ins Hauptmenü und wendete alle ModuleScreen-/InputSource-Mixins ohne Apply-Fehler an. Das Guide-Book-Modell erzeugte keinen Ressourcenfehler. Der anschließende Dedicated-Server-Start meldete `Done (0.816s)`.

## Missing Dependencies

Keine im lokalen Entwicklungsverzeichnis. Die ignorierten Pflichtdateien sowie die optionale Drive-By-Wire-Test-JAR unter `libs/` sind auf einem anderen Rechner gemäß `libs/README.md` bereitzustellen.

## Recommended Next Implementation Step

Den Ablauf in `docs/manual-test-plan.md` in einer Testwelt ausführen, insbesondere Montage/Demontage, Neustartpersistenz, Modemzugriff, Flywheel an/aus und Combined Lever auf einem Sable-Schiff.
