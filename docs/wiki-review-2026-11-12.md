# Wiki- und README-Prüfung — 12. November 2026

## Ergebnis

- Kurze englische Haupt-README mit Links auf Downloads, beide Wiki-Sprachen, API, Beispiele und Issues.
- Vollständiger englischer Nutzerleitfaden; Deutsch bleibt der Einstieg und die Hauptnavigation.
- Englische Display- und API-Referenzen vereinheitlicht; bestehende UK-/US-URLs bleiben erhalten.
- Ausführliche frühere README-Inhalte nach `docs/overview-de.md` und `docs/overview-en.md` verschoben.
- Alle 25 erfassten Nutzerfunktionen besitzen eine passende Ingame- oder Ponder-Abbildung.
- 27 unveränderte Ingame-PNGs plus die vorhandenen Rezeptgrafiken werden tatsächlich eingebunden.

## Aufnahmeumgebung

Minecraft 1.21.1, NeoForge 21.1.234, lokaler CC-Aeroworks-Entwicklungsstand 1.5.4,
TeutonLib 1.6.0, Sable 2.0.6, Create 6.0.10, Aeroworks 1.5.0 und die vorhandenen optionalen
Integrationen einschließlich Create: Radars und CTF 0.1.47. GUI-Skalierung 2, 1280 × 800 Pixel.
Die Bilder wurden mit Minecrafts Screenshot-API im getrennten Entwicklungsprofil
`build/wiki-capture-run` aufgenommen. Ein eigener neu erzeugter Spielstand wurde benutzt;
vorhandene persönliche Welten wurden nicht geöffnet. Temporäre Aufnahmefixture-Quellen lagen
außerhalb des Produktquellbaums und werden nicht als Mod ausgeliefert.

Die Wiki unterscheidet ausdrücklich die veröffentlichte Installationsbaseline 1.5.3 und die
Aufnahmen des lokalen Entwicklungsstands 1.5.4. Die Dokumentationsveröffentlichung ist kein Mod-Release.

## Konkrete Beobachtungen

- Alle vier Displayvarianten, gemeinsamer Geräteslot, normale Computer- und Terminalansicht wurden geöffnet.
- Wire-Kanäle und die Gruppe `flight` wurden tatsächlich angelegt. Der DbW-Zieleditor zeigt die Konfiguration;
  das Bild allein behauptet keine erfolgreiche Verbindung oder tatsächliche Kabelrückgabe.
- Ein vorhandenes `/gear.lua` wurde einem Hebel zugewiesen; die Aufnahme zeigt die gespeicherte Auswahl.
- Der Dashboard Controller wurde nativ als `AVAILABLE` aufgelöst; der Hebel erhielt die explizite THROTTLE-Zuordnung.
- Copycat-Rahmen und -Panel wurden über die vorhandene Materiallogik verändert.
- Das Draw-Bild entstand durch den echten Lua-Helfer `touchdisplay.drawStroke` mit vorgegebenen Testpunkten.
  Es belegt die Darstellung des Beispiels, nicht eine physisch aufgezeichnete Mausgeste.
- Die kombinierte Eingabe wurde über die tatsächliche Kanal-Konfiguration aktiviert; der Tooltip benennt
  `Kombiniert (konfigurierte Taste + Maus Y)`. Das ist eine Einrichtungsabbildung.
- Create-Lagertelemetrie las eine echte Truhe mit 64 Diamanten über einen Threshold Switch und Display Link.
  Der Zustand war `supported=true`, `available=true`, `stale=false`, Inhalt 64 von 1728 Items.
- Zwei originale Simulated-Docking-Connector auf Welt/Sable meldeten `locked=true/true`.
  Der eingebettete Computer las die entfernte Lagertelemetrie einschließlich `current=64`.
- Ein tatsächlich montiertes Create-Lager meldete `running=true` und trug das Pult als Contraption.
- Vier echte GPS-Hosts lieferten dem Computer eines echten Sable-Sublevels `12, 80, 0`; dessen Netzwerkraum war `sable`.
- Ein originales Radar-Lager mit Empfänger und Creative Radar Plate meldete `running=true`, Reichweite 1000,
  eine Dish und reale Kontakte. Ein tatsächlicher Kuh-Kontakt wurde über die native NetworkData-API ausgewählt
  und anschließend als `selectedTrackId` synchronisiert. Das Bild zeigt die native Radaransicht;
  es ist kein Nachweis einer mit der Maus ausgelösten Auswahl oder einer Waffensteuerung.

## Prüfungen

Die strenge Medienprüfung `python3 tools/verify-wiki.py --require-all-media` prüft die Zuordnung
25/25, sämtliche Wiki-Seiten-/Abschnittslinks, beidseitige Sprachverweise und die eingebundenen PNG/GIF-Dateien.
Die Verifier für Release-Layout, Beispiele, Guide/Ponder, API-Referenz, Peripheral-Netzwerk,
Control-Overrides, Radar-Link-Vertrag und Repository wurden ausgeführt. Die Aufnahmeclients wurden
gegen den vorhandenen lokalen Modbestand kompiliert und gestartet.

Die Prüfungen ersetzen keine vollständige Mehrspieler-, Rechte-, Persistenz- oder Gameplay-Abnahme.
Veröffentlicht werden ausschließlich Dokumentation, Medien und die dafür angepassten Prüfungen.
