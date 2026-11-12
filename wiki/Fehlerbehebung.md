# Fehlerbehebung

[[Deutsch|Fehlerbehebung]] · [[English|English#troubleshooting]]

![Ponder-Diagnose erklärt Steuerautorität und ungültige Pultzustände.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-desk_diagnostics.png)


Prüfe zuerst, ob deine Version zur [[Installation]] passt und ob alle Segmente der gewünschten Pultreihe geladen sind. Für einen reproduzierbaren Test beginne mit einem Pult, einem Computer und dem betroffenen Modul.

## Spiel oder Server startet nicht

Lies im Loaderfehler die Mod-ID und den geforderten Versionsbereich. Besonders eng sind Create 6.0.10, TeutonLib 1.5.x und die optionale CTF-Integration mit genau 0.1.47. Entferne doppelte JAR-Versionen und verwende die passende Minecraft-/Loader-Variante.

## Kein Terminal oder kein Computerknopf

Schleichen + Rechtsklick mit leerer Haupthand öffnet die Modulübersicht, auch bei leerem Pult. Setze dort einen Computer ein und verwende den Computerknopf. Ein gewöhnlicher Rechtsklick bedient montierte Module. Ein eingesetztes CC:-Terminals-Gerät öffnet eine Bedienstation und führt selbst keine Programme aus.

## `peripherals` fehlt

Die globale API und das Modul `cc_aeroworks.peripherals` gehören zum **eingebetteten Computer**. Ein externer Computer verwendet die normale CC:Tweaked-API `peripheral` und lokale `ControlDesk`-Adapter. Achte auf das abschließende `s`.

## Benachbarte Pulte fehlen im Netzwerk

Seit 1.5.1 zählt die explizite Assembly-Zugehörigkeit. Gleich ausgerichtete benachbarte Pulte können unabhängige Reihen sein. Erweitere eine Reihe über ihre Platzierungspfeile oder passende seitliche Platzierung. Schleichen beginnt eine neue Reihe. Desk und Stand sind unterschiedliche Geometriefamilien.

```lua
print(textutils.serialize(peripherals.getNetwork()))
for address, desk in pairs(peripherals.find("ControlDesk")) do
  print(address, desk.getInfo().id)
end
```

Der globale Zugriff wird bei teilweise geladenen Reihen, mehr als 64 physischen Segmenten oder ungültiger Eigentümerschaft abgelehnt. Es werden keine Chunks automatisch nachgeladen. Integrierte Create-Steuerungen zählen zur Grenze, aber nicht als `ControlDesk` im `deskCount`.

## `find` liefert einen unerwarteten Wert

`peripherals.find(type)` liefert bei null Treffern `nil`, bei einem Treffer ein direktes Handle und bei mehreren eine Adresstabelle. `ControlDesk` liefert immer eine Tabelle. Für eine Schleife über beliebig viele Geräte verwende `findAll(type)`.

Ein Positionsschlüssel kann sich bei Bewegung ändern. Speichere für dauerhafte Zuordnungen die Desk-ID oder verwende logische Kanalgruppen.

## Display bleibt leer

- Prüfe Socket und Modul mit `getDisplays()`.
- Große Displays passen nur in `big`.
- Pixelkoordinaten beginnen bei `(1,1)`; Rasterzeilen müssen genau zur aktuellen Breite und Höhe passen.
- Nach einer Auflösungsänderung muss das Programm das Raster neu zeichnen.
- Eine gewählte Radarquelle besitzt die Bildausgabe exklusiv. Entferne die Bindung beziehungsweise wähle die gewünschte Lua-Steuerung.

## Touch reagiert nicht

Touch erfordert ein **erweitertes Display** und einen **erweiterten Computer oder ein erweitertes CC:-Terminals-Terminal** im aktiven Verbund. Normale oder fehlende Slot-Geräte deaktivieren Touch auch für externe Computer.

Öffne die Moduleinstellungen, prüfe Display-Steuerung und Eingabebelegung und betrete die Pultbedienung. Linksklick erzeugt `tap`, gehaltene rechte Maustaste eine `draw`-Geste. Externe Computer starten gespeicherte owner-lokale Handler nicht automatisch.

## Radar meldet einen Statusfehler

| Meldung / Verhalten | Prüfen |
|---|---|
| Kein Radar zugeordnet | Network Filterer separat mit dem Radar per Data Link verbinden |
| Mehrere Network Controller | eindeutige Quelle herstellen oder nativen lokalen Monitorendpoint verwenden |
| Keine Quelle / fehlende Verbindung | Data-Link-Block, sichtbaren Filterer und gespeicherte Displayquelle prüfen |
| Keine Kontakte | Betriebszustand, Reichweite und Detection-Filter der nativen Radargruppe prüfen |
| Veraltete Daten | Radar, Filterer und Pultnetz laden; Quelle erneut prüfen |

Die Auswahl im Display-Dropdown erzeugt keine Filterer-Radar-Verbindung. Für Details siehe [[Radar-Routing]].

## Hebel bewegt sich nicht oder kehrt zurück

Prüfe Eingabetyp, Aktivierungstaste und Kamerafokus. Bei aktiver HARD-Übernahme durch Lua werden normale Eingaben für genau diesen Kanal unterdrückt. Gib den Override aus dem zuständigen Programm frei. `release` gibt Autorität zurück und setzt den letzten Wert nicht automatisch auf null.

Wertebereiche unterscheiden sich: logische `channels` und `wires` verwenden 0–15; native `controls` verwenden -15–15. Display-Zeigerachsen sind keine Fahrzeugausgänge.

## Messwert fehlt oder ist veraltet

Prüfe `available`, `supported`, `stale` und `ageTicks`. Nach dem Serverstart müssen Create-Display-Links Werte neu liefern. Ein fehlender oder alter Wert ist kein gültiger Nullwert. Dock-Telemetrie benötigt eine verriegelte Verbindung; Connector-Puffer sind nicht der komplette Tank- oder Frachtrauminhalt.

## Fehler melden

Erstelle ein [Issue](https://github.com/TeutonStudio/CC-Aeroworks/issues) mit:

- CC-Aeroworks-, Minecraft-, NeoForge- und relevanten Abhängigkeitsversionen;
- Einzelspieler/Server und stationär, Sable oder Create-Contraption;
- kurzem Aufbau und genauen Schritten zum Reproduzieren;
- erwartetem und tatsächlichem Verhalten;
- relevantem Logauszug, Netzwerkstatus und bei Bedarf einem kleinen Lua-Beispiel.

Für Radar oder Display lassen sich zeitweise die Diagnosen aus [[Konfiguration]] aktivieren. Entferne vor dem Veröffentlichen Zugangsdaten und andere private Angaben aus Logs.
