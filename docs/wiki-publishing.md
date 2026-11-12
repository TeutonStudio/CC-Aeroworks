# GitHub-Wiki veröffentlichen

Die veröffentlichungsfertigen Wiki-Seiten liegen im Verzeichnis [`wiki/`](../wiki/). Die Dateinamen und internen Links entsprechen direkt dem GitHub-Wiki-Format.

## Enthaltene Seiten

Stand: CC-Aeroworks 1.5.3, 1. November 2026. Die Wiki umfasst 22 Inhaltsseiten sowie Sidebar und Footer. Die Startseite führt zu Display- und API-Seiten auf Deutsch, britischem Englisch und amerikanischem Englisch.

- `API-Schnellreferenz.md`
- `API-en-GB.md`
- `API-en-US.md`
- `Bedienterminals.md`
- `Bedienung.md`
- `Bewegte-Konstruktionen.md`
- `Computer-Steuerungspulte.md`
- `Displays-en-GB.md`
- `Displays-en-US.md`
- `Erste-Schritte.md`
- `Fehlerbehebung.md`
- `Home.md`
- `Installation.md`
- `Kanaele-und-Automatisierung.md`
- `Kombinierte-Eingabe.md`
- `Konfiguration.md`
- `Peripheral-Netzwerk.md`
- `Programmierbare-Displays.md`
- `Radar-Routing.md`
- `Steuerungsskripte.md`
- `Telemetrie.md`
- `Zugsteuerung.md`
- `_Footer.md`
- `_Sidebar.md`

### Rezeptgrafiken

Die zwölf PNG-Dateien liegen unter `wiki/assets/recipes/{de,en-gb,en-us}/`. `python3 tools/render-wiki-recipes.py` erzeugt sie aus den vier Rezeptdateien und den Modellen/Texturen des Projekts sowie der lokal bereitgestellten CC:Tweaked- und Create-JARs. Das Werkzeug benötigt Pillow, NumPy und eine der im Skript angegebenen Sans-Serif-Schriften. Es veröffentlicht keine Quelltexturen der Abhängigkeiten.

Die Wiki bindet Bilder über `https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/recipes/...` ein. Beim Synchronisieren müssen Seiten und Bilddateien gemeinsam übertragen werden.

## Erstmalige Veröffentlichung

GitHub stellt für Wikis keinen normalen Contents-API-Endpunkt bereit. Das Wiki wird stattdessen als separates Git-Repository verwaltet.

Falls das Wiki noch keine Startseite besitzt, zuerst unter
`https://github.com/TeutonStudio/CC-Aeroworks/wiki` eine leere Seite `Home` anlegen. Dadurch wird das Wiki-Git-Repository initialisiert.

Danach:

```bash
git clone https://github.com/TeutonStudio/CC-Aeroworks.wiki.git
cd CC-Aeroworks.wiki

cp ../CC-Aeroworks/wiki/*.md .
cp -R ../CC-Aeroworks/wiki/assets .

git add -- *.md assets

git commit -m "Create CC-Aeroworks wiki"
git push
```

Die relativen Pfade im `cp`-Befehl müssen an die lokale Verzeichnisstruktur angepasst werden.

## Aktualisierung

Nach Änderungen an den Quelldateien:

```bash
cd CC-Aeroworks.wiki
cp ../CC-Aeroworks/wiki/*.md .
cp -R ../CC-Aeroworks/wiki/assets .
git diff --check
git diff
git add .
git commit -m "Update CC-Aeroworks wiki"
git push
```

## Pflegekonventionen

- Nutzerorientierte Erklärungen gehören in die Wiki-Seiten.
- Vollständige und maschinennahe API-Verträge bleiben zusätzlich unter `docs/` im Hauptrepository.
- Codebeispiele sollen mit aktuellen Methodennamen und Ereignisargumenten übereinstimmen.
- Noch nicht ausgeführte Laufzeittests dürfen nicht als bestanden beschrieben werden.
- Änderungen an Bedienung, Sockets, Displayformaten, Ereignissen oder Multiblockregeln müssen gleichzeitig in Wiki, Ingame-Handbuch und Repository-Dokumentation aktualisiert werden.


## Überarbeitung vom 12. November 2026

`README.md` ist der kurze englische Einstieg. Ausführliche technische Übersichten liegen unter
`docs/overview-de.md` und `docs/overview-en.md`; alte README-Sprachadressen verweisen auf den neuen Einstieg.

Die Wiki startet auf Deutsch. `English.md` erklärt Einrichtung und sämtliche Funktionsthemen auf Englisch;
`Displays-en.md` und `API-en.md` ergänzen die Detailreferenzen. Jede deutsche Anleitung besitzt einen
Link zum passenden englischen Abschnitt. Alte UK-/US-Seiten bleiben als Weiterverweise erhalten.

### Bildnachweise und Grenzen

Echte Aufnahmen aus dem getrennten Entwicklungsclient liegen unter `wiki/assets/screenshots/`.
Die Rezeptgrafiken bleiben unter `wiki/assets/recipes/`. Ingame-Aufnahmen: lokaler Entwicklungsstand
1.5.4 mit TeutonLib 1.6.0; Installationsbaseline der veröffentlichten Wiki: Release 1.5.3.
Neue Screenshots ändern keine Mod-Version und sind keine Freigabe eines unveröffentlichten Builds.

`docs/wiki-media-coverage.json` ordnet Funktionen ihren tatsächlichen Abbildungen zu.
Ein Screenshot einer Konfigurationsseite ist kein Nachweis einer ausgeführten Mausgeste oder
funktionierenden Zugfahrt. Offene Motive bleiben ausdrücklich offen; fehlende Bilder werden
weder durch Rezeptgrafiken noch durch sachfremde Screenshots ersetzt.

Vor jeder Veröffentlichung:

```bash
python3 tools/verify-wiki.py
python3 tools/verify-wiki.py --require-all-media
```

Die erste Prüfung validiert Seiten, Abschnittslinks, Sprachverweise und Bilddateien. Die zweite
verlangt zusätzlich vollständige Feature-Abbildungen und scheitert, solange im Medieninventar
noch offene oder nur teilweise gezeigte Funktionen stehen. Ein erfolgreicher normaler CI-Lauf
bedeutet deshalb nicht automatisch vollständige Bebilderung.

Nur README-Dateien, Wiki-Quellen, zugehörige Medien, Dokumentationsprüfungen und deren CI-Schritt
werden veröffentlicht. Lokale Änderungen am Mod-Code und lokale Release-Commits werden nicht
mit dieser Dokumentationsänderung übertragen. Wiki-Seiten und Bilder gemeinsam in das separate
Wiki-Repository kopieren und pushen; das Hauptrepository allein aktualisiert die Wiki nicht.
