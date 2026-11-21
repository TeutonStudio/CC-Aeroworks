# Handbuch, Quellen und Pixel-Editor

[[Deutsch|Handbuch-und-Quellen]] · [[English|English#using-the-desks]]

## Quellenübersicht

Die Computeroberfläche bündelt Peripherals, Display-Link-Daten, GPS und optionale Radarquellen unter **Quellen**. Aufklappbare Gruppen und Detailansichten zeigen die verfügbaren Geräte und Display-Skripte. Eine sichtbare, aber nicht verbundene Quelle ist noch kein gültiger Messwert. Prüfe ihren Status vor der Verwendung.

![Quellenreiter des eingebetteten Computers: Gruppen für Display Links, Storage, GPS und Radar. Der gezeigte Radar-Ingress ist noch nicht verbunden.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-sources.png)

Kanalgruppen und Wire-Ausgänge stehen unter [[Kanäle und Automatisierung|Kanaele-und-Automatisierung]]. GPS und die Grenzen bewegter Geräte erklärt [[Bewegte Konstruktionen|Bewegte-Konstruktionen]].

## Ingame-Handbuch und API

Das Ingame-Handbuch erklärt Computer, Module, Netzwerk-APIs, Steuerung, Telemetrie, Displays, Touch und Ereignisse. Je nach installierten Erweiterungen kommen Docking und Radar hinzu. Die Methodenreferenz unterscheidet lokale Peripherals, eingebettete Desk-Handles und globale APIs.

![Startseite des tatsächlichen Ingame-Handbuchs mit Themenauswahl und API-Geltungsbereichen.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-manual.png)

Halte für die animierten Ponder-Anleitungen **W** über einem passenden Pult-, Computer- oder Display-Item. Pultnetz, Gerätesuche, Diagnose, Displayherstellung, Montage und Programmierung besitzen eigene Szenen; mit Create: Radars sind weitere Szenen vorhanden.

## Pixel-Editor

Im Handbuch lässt sich ein Pixelentwurf für ein kleines oder großes Display und den gewünschten Socket vorbereiten. Linksklick zeichnet den gewählten Zustand, Rechtsklick löscht. Die Werkzeuge können das Raster leeren, füllen oder invertieren und Lua-Ausgabe für Pixel beziehungsweise Raster kopieren. Bei hoher Pixeldichte wird der Entwurf verkleinert dargestellt.

![Pixel-Editor im Ingame-Handbuch mit Displaygröße, Socket und Zeichenmodus. Der abgebildete Entwurf ist leer.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-pixel-editor.png)

Der Editor verändert selbst kein Display in der Welt. Übernimm den erzeugten Lua-Code in ein Programm und verwende das aktuelle Raster des Ziel-Displays. Die Ausgabe- und Pixelfunktionen stehen in der [[Lua-API|API-Schnellreferenz]].
