# Gemeinsame Computeroberfläche mit TeutonLib

CC-Aeroworks benötigt TeutonLib 1.2.2 auf Client und Server sowie NeoForge 21.1.234. TeutonLib muss vor CC-Aeroworks geladen werden; die optionale Gegenintegration darf keine AFTER-Abhängigkeit zurück zu Aeroworks deklarieren.

## Bedienung

Unter den nativen CC-Strom-/Neustartaktionen folgen **Steuerung**, **Quellen**, **Kanäle**. Steuerung erscheint bei vorhandenem Rückkehrkontext und öffnet die zuvor besuchte Pultübersicht oder Modulansicht. Aktiven Reiter erneut anklicken oder Escape drücken führt zum Terminal.

Quellen und Kanalgruppen sind aufklappbar. Die Quellen enthalten Peripherals aus TeutonLib, die vorhandenen Display-Link-/GPS-/Radar-Quellen und die vollständigen Display-Skriptdetails. Lange Detailzeilen stehen auch im Tooltip.

Im Kanalreiter rechts einen Namen/Alias eingeben und **+Kanal** oder **+Gruppe** wählen. Ein Klick auf einen vorhandenen Kanal, eine Gruppe oder Bindung bietet die passenden Aktionen. Zum Binden zuerst die Benutzergruppe und dann den Zielkanal wählen, Alias eingeben und **Binden** drücken. Löschen/Lösen benötigt einen zweiten Klick. **Ziele…** öffnet den bestehenden DbW-Zieleditor, sofern das Backend verfügbar ist.

## Zuständigkeit

`DeskComputerWorkspace` ist der serverseitige Adapter für TeutonLibs `ComputerWorkspace`. Er projiziert die bestehenden Snapshots in einen gemeinsamen Baum und führt Aktionen über `WireChannelBank`, `ChannelGroupBank` und `ChannelRegistry` aus. UUIDs und kanonische Kanal-IDs bleiben unverändert. Pultteilung/-vereinigung, Itemdaten, Lua `channels` und Override-Regeln verbleiben bei Aeroworks.

TeutonLib verwaltet Darstellung, Fokus, Polling und Antworten für das aktive Menü. Jede Panelinstanz und jeder Seitenwechsel verwendet eine neue Sitzung. Antworten werden nur bei passender Menü-ID, Sitzung, Backend und Seite übernommen. Serveraktionen prüfen das aktuelle Menü, die tatsächliche Computerinstanz, Entfernung/Nutzbarkeit und Pultbelegung.

Die bisherigen eigenständigen Quellen-/Kanalwidgets sind durch die gemeinsamen Panels ersetzt. Die älteren Aeroworks-Payloads bleiben für bestehende Aufrufer erhalten.

## Abnahme

- Beide Projekte: Build und Tests; Aeroworks-Vertragsprüfungen.
- Normale/erweiterte Computer, GUI-Skalierung 2/3: genau eine Reiterleiste, Fokus, Inventartaste, Escape, Resize und Steuerungsrückkehr.
- Kanal-/Gruppenaktionen, fehlende Bindungsziele, DbW-Zieleditor und Betrieb ohne DbW.
- Quellen, Display-Skripte, Touch-Ereignisse und optionale Radar-/Peripheral-Link-Daten ohne doppelte Quellen.
- Neustart, Chunkwechsel, Pultteilung/-vereinigung und unveränderte gespeicherte Daten.
- Zwei Spieler, veraltete Antworten, ausgeschalteter Computer und unberechtigte Aktionen.

Tatsächliche Prüfergebnisse stehen in `docs/test-results/teutonlib-computer-ui.md`; diese Liste allein ist kein Testnachweis.
