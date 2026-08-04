# GitHub-Wiki veröffentlichen

Die veröffentlichungsfertigen Wiki-Seiten liegen im Verzeichnis [`wiki/`](../wiki/). Die Dateinamen und internen Links entsprechen direkt dem GitHub-Wiki-Format.

## Enthaltene Seiten

- `Home.md`
- `Computer-Steuerungspulte.md`
- `Programmierbare-Displays.md`
- `Kombinierte-Eingabe.md`
- `API-Schnellreferenz.md`
- `_Sidebar.md`

## Erstmalige Veröffentlichung

GitHub stellt für Wikis keinen normalen Contents-API-Endpunkt bereit. Das Wiki wird stattdessen als separates Git-Repository verwaltet.

Falls das Wiki noch keine Startseite besitzt, zuerst unter
`https://github.com/TeutonStudio/CC-Aeroworks/wiki` eine leere Seite `Home` anlegen. Dadurch wird das Wiki-Git-Repository initialisiert.

Danach:

```bash
git clone https://github.com/TeutonStudio/CC-Aeroworks.wiki.git
cd CC-Aeroworks.wiki

cp ../CC-Aeroworks/wiki/*.md .

git add Home.md \
  Computer-Steuerungspulte.md \
  Programmierbare-Displays.md \
  Kombinierte-Eingabe.md \
  API-Schnellreferenz.md \
  _Sidebar.md

git commit -m "Create CC-Aeroworks wiki"
git push
```

Die relativen Pfade im `cp`-Befehl müssen an die lokale Verzeichnisstruktur angepasst werden.

## Aktualisierung

Nach Änderungen an den Quelldateien:

```bash
cd CC-Aeroworks.wiki
cp ../CC-Aeroworks/wiki/*.md .
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
- Änderungen an Sockets, Displayformaten, Ereignissen oder Multiblockregeln müssen gleichzeitig in Wiki und Repository-Dokumentation aktualisiert werden.
