# Implementation Log

## Computer-Steuerungspult

Die beiden Computer-Steuerungspulte sind Aeroworks-`ConsoleDeskBlock`-Unterklassen. Ihre BlockEntity erweitert `ConsoleBlockEntity` und hostet per Komposition einen CC:Tweaked-`ServerComputer`.

Die CC-Abhängigkeit auf Klassen unter `dan200.computercraft.shared` ist bewusst in den Paketen `computer`, `recipe` und den entsprechenden Registrierungen konzentriert. Diese Klassen sind keine stabile öffentliche API; unterstützt wird der deklarierte Versionsbereich 1.119.x bis vor 1.121.

## Multiblock

`ConsoleMultiblockResolver` leitet eine gleich ausgerichtete Links-rechts-Reihe aus geladenen Blöcken ab. `ConsoleMultiblockManager` cached Snapshots und invalidiert sie bei Block- und Chunkänderungen.

Mehrere Computer erzeugen einen Konflikt. Ein zufälliger primärer Computer wird nicht ausgewählt.

## Lua

`CCComputerComponents.CONSOLE` wird nur den eingebetteten `ServerComputer.Properties` hinzugefügt. Die über `ComputerCraftAPI.registerAPIFactory` registrierte API wird deshalb auf gewöhnlichen Computern nicht erzeugt.

## Gemeinsamer Desk-Service

`AeroworksDeskService` enthält Socketvalidierung, Modulbeschreibung, Eingabeabfrage und sämtliche Displayoperationen. `ControlDeskPeripheral` und `ComputerConsoleLuaApi` delegieren dorthin.

## Persistenz

Aeroworks behält die Verantwortung für `controller_contents`. Eigene Data Components speichern Desk-ID und Einschaltzustand; CC:Tweakeds bestehende Komponenten speichern Computer-ID, Terminalgröße und Kapazität.

Das Spezialrezept kopiert alle Komponenten beider Eingaben.

## Rendering

Der eigene BlockEntityRenderer delegiert Aeroworks' `ConsoleRenderer` über eine kleine reflektive Konstruktorgrenze. Damit ist der klassische Renderpfad abgedeckt. Eine native Flywheel-Visual-Registrierung ist noch nicht als verifiziert markiert.

## Laufzeitstatus

Der Quellstand wurde ohne die lokal erforderlichen Fremdmod-JARs erstellt. JSON-Ressourcen und Struktur wurden statisch validiert. Gradle-, Client-, Server- und Ingame-Prüfung sind offen.
