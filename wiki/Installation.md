# Installation

[[Deutsch|Installation]] · [[English|English#installation]]

Diese Anleitung gilt für **CC-Aeroworks 1.5.3**, Minecraft **1.21.1** und **NeoForge**. Installiere bei einem Server dieselben erforderlichen Mods auch auf den Clients.

## Erforderliche Komponenten

Die folgende Kombination entspricht der Projekt-Baseline. Die Versionsbereiche stammen aus den Mod-Metadaten von 1.5.3.

| Komponente | Baseline | Akzeptierter Bereich |
|---|---|---|
| Minecraft | 1.21.1 | genau 1.21.1 |
| Java | 21 | Java 21 für diese Minecraft-Version |
| NeoForge | 21.1.234 | ab 21.1.234, passend zu Minecraft 1.21.1 |
| Create | 6.0.10 | ab 6.0.10, unter 6.0.11 |
| Create: Aeroworks | 1.5.0 | ab 1.5.0, unter 1.6.0 |
| Create: Aeronautics | 1.3.2 | ab 1.3.2, unter 1.4.0 |
| Sable | 2.0.5 | ab 2.0.1 |
| CC:Tweaked | 1.120.2 | ab 1.119.0, unter 1.121.0 |
| TeutonLib | 1.5.0 | ab 1.5.0, unter 1.6.0 |
| Kotlin for Forge | 5.12.0 | ab 5.11.0, unter 6.0.0 |

Die Versionsbereiche weiterer Abhängigkeiten müssen ebenfalls zueinander passen. Verwende NeoForge-/Minecraft-1.21.1-Dateien der jeweiligen Projekte. Die Versionsnummer des Aeroworks-Modpacks ist nicht die Versionsnummer der Aeroworks-Mod.

## Vorgehen

1. Sichere eine bestehende Welt vor einem Versionswechsel.
2. Richte Minecraft 1.21.1 mit NeoForge ein und starte das Profil einmal.
3. Lade `cc-aeroworks-1.5.3.jar` aus dem [Release 1.5.3](https://github.com/TeutonStudio/CC-Aeroworks/releases/tag/v1.5.3) herunter.
4. Lege die JAR und die erforderlichen Abhängigkeiten in den `mods`-Ordner der Instanz. Entferne ältere doppelte Versionen derselben Mod.
5. Starte das Spiel beziehungsweise den Server. Prüfe bei einem Ladefehler zuerst die vom Loader genannten Mod-IDs und Versionsbereiche.
6. Baue ein Aeroworks-Pult und folge [[Erste Schritte|Erste-Schritte]].

Offizielle Bezugsquellen und die genaue Projekt-Baseline stehen im [Abhängigkeitsverzeichnis](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/libs/README.md) und im [Versionsmanifest](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/libs/dependencies.json). [TeutonLib-Releases](https://github.com/TeutonStudio/TeutonLib/releases) werden separat bereitgestellt.

## Optionale Erweiterungen

| Mod | Unterstützte Version für diese Integration | Funktion |
|---|---|---|
| Drive By Wire | 0.2.9 bis unter 0.4; Baseline 0.3.0 | verkabelte Kanäle und Ziele |
| CC: Terminals | ab 0.1.1, unter 0.2 | geschützte Bedienterminals im Slot |
| Create: Radars | ab 0.4.9.4; Baseline 0.4.9.4-1.21.1 | Radarquellen für erweiterte Displays |
| Create Big Cannons | ab 5.11.7, unter 5.12 | Laufzeit für die Radar-Kombination |
| Ritchie's Projectile Library | Baseline 2.1.2 | Abhängigkeit von Create Big Cannons |
| Create: Train Functionality | genau 0.1.47 | Dashboard Controller als Quelle und Peripheral |
| Create: Simulated | ab 1.3.2, unter 1.4.0 | Docking-Telemetrie zwischen Sable-Modulen |

Prüfe bei optionalen Mods auch deren eigene Abhängigkeiten. JEI ist als Rezeptbetrachter hilfreich.

## Umstieg von älteren Versionen

- Seit 1.4.0 sitzt der Computer im gemeinsamen Slot normaler Pulte. Das frühere Kombinationsrezept entfällt; alte Computerpult-Blöcke und -Items werden übernommen.
- Seit 1.5.0 gibt es normale und erweiterte kleine/große Displays. Alte Display-IDs werden über Registry-Aliase übernommen.
- Radar wird über `Display-Steuerung` eines erweiterten Displays gewählt. Separate Radar-Display-Items gehören nicht zum aktuellen Aufbau.
- Das frühere CC-Aeroworks-Zugsteuerungsmodul wurde entfernt; vorhandene `cc_aeroworks:train_controls`-Stacks werden nicht migriert. Verwende Creates native Zugsteuerung.
- Alte X/Y-Auflösungsschlüssel werden durch `display.ppb` ersetzt. Nach einer Rasteränderung müssen Pixelbilder neu gezeichnet werden.


## Lokaler Entwicklungsstand 1.5.4

Die neuen Ingame-Abbildungen wurden mit dem lokalen Entwicklungsstand 1.5.4 und TeutonLib 1.6.0 aufgenommen.
Dieser Stand benötigt TeutonLib 1.6.x und Sable 2.0.6. Er ändert nicht die oben aufgeführten
Versionsanforderungen des veröffentlichten Releases 1.5.3. Die gemeinsame Computeroberfläche
kann sich deshalb von einer älteren installierten Version unterscheiden.

Die [technische Entwicklungsübersicht](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/docs/overview-de.md)
enthält die aktuelle lokale Baseline. Maßgeblich für die Installation bleiben die Metadaten deiner konkreten JAR.
