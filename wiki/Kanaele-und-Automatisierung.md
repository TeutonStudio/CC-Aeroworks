# Kanäle und Automatisierung

[[Deutsch|Kanaele-und-Automatisierung]] · [[English|English#channels-and-automation]]

![Ponder erklärt die Übernahme und Freigabe der Steuerautorität.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-desk_diagnostics.png)


![Kanalseite mit den tatsächlich angelegten Wire-Kanälen landing_gear und engine_start; beide stehen auf null.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-channels.png)


Der eingebettete Computer besitzt den Tab **Channels** für physische Steuermodule, selbst definierte Wire-Kanäle und benannte Gruppen. Neue Automatisierungsprogramme sollten die Lua-API `channels` verwenden: Sie arbeitet mit logischen Pfaden und Richtungswerten von **0 bis 15**.

## Drei Arten von Kanälen

| Bereich | Verwendung | Bearbeitung |
|---|---|---|
| Physische Controls | Hebel, Yoke, Joystick und andere Module | automatisch entdeckt |
| Wire-Kanäle | softwaregesteuerte Ausgänge, etwa `landing_gear` | im Channels-Tab oder mit dem Shellbefehl `wires` anlegen |
| Benutzergruppen | Namen wie `flight/roll_right` für vorhandene Ziele | im Channels-Tab oder mit `channels` verwalten |

Die physischen Module selbst können hier nicht umbenannt oder gelöscht werden. Gruppen verweisen auf stabile Ziel-IDs. Ein zeitweise fehlendes Modul bleibt als `MISSING` sichtbar; seine Zuordnung wird nicht gelöscht.

Wire-Namen entsprechen `[a-z][a-z0-9_-]{0,31}`. Pro Computer sind höchstens 32 Wire-Kanäle möglich. Umbenennen behält die UUID und bestehende Drive-By-Wire-Verbindungen; Löschen setzt den Ausgang auf null und entfernt dessen Verbindungen.

## Gruppe einrichten

Lege im Channels-Tab zum Beispiel die Gruppe `flight` an. Ordne `roll_right` einer physischen Richtung und `gear` einem zuvor angelegten Wire-Kanal zu. Danach kann Software diese Namen verwenden, ohne die Position des Pults fest einzubauen.

Zum Untersuchen stehen im CraftOS-Terminal unter anderem bereit:

```text
channels ls /
channels ls /modules
channels ls /wires
channels ls /groups
channels group add flight
```

Die Lua-Runtime legt keine Hardwarekanäle an. Anlegen, Löschen und Umbenennen bleiben Aufgaben der Konfiguration.

## Werte lesen und Ausgänge schreiben

Die folgenden Pfade setzen entsprechende Gruppenbindungen voraus:

```lua
local channels = require("cc_aeroworks.channels")
print(textutils.serialize(channels.ls("/")))
print(channels.read("/groups/flight/roll_right"))

channels.setWire("/groups/flight/gear", 15)
channels.pulseWire("/wires/engine_start", 10, 15)
channels.resetWire("/groups/flight/gear")
```

`pulseWire` erhält die Dauer in Ticks und optional die Stärke. `stat(path)` beschreibt Ziel und Verfügbarkeit. Prüfe `available`, bevor ein Programm von dauerhaft vorhandener Hardware ausgeht.

## Kontrolle übernehmen

```lua
channels.override("/groups/flight/roll_right", 7)
channels.release("/groups/flight/roll_right")
```

Ein Override übernimmt den physischen Kanal mit **HARD-Autorität**: Normale Eingaben auf diesem Kanal werden bis zur Freigabe unterdrückt. Die Modulstellung folgt dem effektiven Wert. `overrideBatch` validiert alle Ziele vor dem Schreiben; beide Richtungen derselben Achse dürfen nicht gemeinsam in einer Batch angesprochen werden.

`release` gibt die Steuerautorität zurück. Es setzt den letzten Wert nicht automatisch auf null. Baue deshalb bewusst ein, ob vor der Freigabe eine Neutralstellung erforderlich ist. Programme sollten eigene Overrides bei Ende oder Fehlern freigeben.

Die ältere API `controls` arbeitet direkt mit nativen signierten Aeroworks-Werten **-15 bis 15** und Desk-UUID, Socket und Kanal. `wires` steuert direkte Wire-Werte **0 bis 15**. Beide bleiben verfügbar; Details stehen in [[API-Schnellreferenz]]. Externe `ControlDesk`-Adapter erhalten keine globale Override-Autorität.

## Drive By Wire verbinden

Mit Drive By Wire kannst du per Cable Kanäle der gesamten aktiven Reihe durchschalten. Die Quelle bleibt das echte Pult des jeweiligen Moduls; selbst definierte Wires verwenden den Computerträger.

Alternativ im Channels-Tab:

1. Einen physischen Richtungskanal, Wire-Kanal oder eine entsprechende Gruppenbindung wählen.
2. **Ziel…** öffnen.
3. Ganzzahlige X/Y/Z-Blockkoordinaten und die Eingangsseite des Zielblocks wählen.
4. **Verbinden** oder **Trennen** verwenden. **Ziel laden** blättert durch bestehende Ziele.

Das Ziel muss geladen, bearbeitbar und in derselben Dimension sein. Auf Sable gelten gespeicherte Sublevel-Blockkoordinaten und deren Blockseiten. Wenn Drive By Wire Kabel verbraucht, kostet die Verbindung ein Cable und das Trennen gibt eines zurück; Creative ist davon ausgenommen.

Display-Zeigerachsen dienen der lokalen Displaybedienung und sind keine Fahrzeugausgänge.

## Neustart und ungültige Netzwerke

Kanaldefinitionen und Gruppen werden gespeichert. Laufzeitwerte, Pulse und Overrides sind vorübergehend. Computer-Aus, ungültiger Verbund oder Entladen setzen Wire-Ausgänge auf null und geben Overrides frei. Nach dem Boot muss dein Programm gewünschte Ausgänge erneut setzen.

Mehr: [Wire-Channels und Gruppen](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/docs/wire-channels.md), [Control-Overrides](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/docs/control-overrides.md).

![Kanalseite mit der angelegten Benutzergruppe flight und den Wire-Kanälen.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-channel-groups.png)

![Nativer Drive-By-Wire-Zieleditor mit Blockkoordinaten und Blockseite.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-dbw-target-editor.png)
