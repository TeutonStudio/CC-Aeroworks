# Radar-Routing

Die Create:-Radars-Integration verbindet einen vorhandenen Data Link mit einer CC-Aeroworks-Radaranzeige. Quelle, eingebetteter Computer und Anzeige dürfen an verschiedenen Pulten desselben Netzwerks liegen.

## Voraussetzungen

- Create: Radars ist geladen.
- Das Pultnetz ist vollständig geladen und gültig.
- Genau ein eingebetteter Computer besitzt das Netzwerk.
- Genau eine kleine oder große Radaranzeige ist im Netzwerk montiert.
- Der ausgewählte Create:-Radars-Monitor gehört zu einem funktionierenden Radarnetz.

## Automatische Route einrichten

1. Eine kleine oder große Radaranzeige in einem beliebigen Pult des Zielnetzes montieren.
2. Mit dem Data-Link-Gegenstand einen verbundenen Create:-Radars-Monitor rechtsklicken.
3. Mit demselben Gegenstand eine freie Seite eines beliebigen Pults im Zielnetz rechtsklicken.
4. CC-Aeroworks platziert den originalen Data-Link-Block und trägt den Monitorcontroller als Ziel ein.
5. Die Quell-Snapshots werden automatisch zur einzigen Radaranzeige des Netzwerks weitergeleitet.

Ein Lua-Programm ist für diesen eindeutigen Standardfall nicht erforderlich.

## Warum genau eine Anzeige?

Die automatische Route darf nicht still ein zufälliges Ziel auswählen:

| Anzeigen im Netz | Ergebnis |
|---:|---|
| 0 | Meldung, dass kein Radarziel vorhanden ist |
| 1 | automatische Route |
| 2 oder mehr | Mehrdeutigkeitsmeldung, keine zufällige Zuweisung |

Mehrere Quellen können durch die tatsächlich platzierten Data-Link-Blöcke kontrolliert werden. Jede Quelle behält ihr eigenes Create:-Radars-Monitorziel.

## Datenübertragung

Alle fünf Ticks liest CC-Aeroworks vom Data Link:

- Radarzentrum,
- Reichweite,
- ausgewählte Track-ID,
- höchstens die 256 nächstgelegenen Tracks.

Die Daten werden nicht dauerhaft in der Welt gespeichert. Nach 20 Ticks ohne frischen Snapshot gilt die Verbindung als veraltet und das Display zeigt `X`.

## Monitorauswahl löschen

Schleichen und Rechtsklick mit dem Data-Link-Gegenstand löscht eine begonnene Monitorauswahl.

Eine Auswahl wird ebenfalls verworfen, wenn:

- der Monitor entfernt wurde,
- der Monitor-Chunk nicht geladen ist,
- der Monitor in einer anderen Dimension liegt,
- der gespeicherte Block kein Create:-Radars-Monitor mehr ist.

## Netzwerkfehler

Routing findet nicht statt bei:

- mehreren eingebetteten Computern,
- teilweise geladenem Pultnetz,
- mehr als 64 Pulten,
- fehlender Radaranzeige,
- mehreren Radaranzeigen,
- ungültigem oder unverbundenem Monitor.

## Ponder

Die Radaritems besitzen zwei lokalisierte Storyboards:

1. automatisches Routing über verschiedene Pulte,
2. Monitor-zuerst-Einrichtung des Data Links und Fehlerzustand `X`.

Die Data-Link-Kompatibilität ist optional. Ohne Create: Radars werden Radaritems, Rezepte und Ponder-Szenen nicht registriert.
