# Bewegte Konstruktionen

[[Deutsch|Bewegte-Konstruktionen]] · [[English|English#moving-constructions]]

CC-Aeroworks unterstützt gewöhnliche Create-Contraptions und bewegte Sable-Sublevels. Die beiden Umgebungen haben unterschiedliche Geräte- und Koordinatenmodelle.

## Gewöhnliche Create-Contraptions

Die mobile Laufzeit ist standardmäßig aktiviert. Bei der Montage startet der eingebettete Computer mit derselben Computer-ID, Dateien, Computerfamilie, Terminalgröße und Einschaltlage neu. Plane Programme so, dass sie nach einem Neustart ihre Geräte erneut entdecken und Ausgänge neu einrichten.

```lua
local peripherals = require("cc_aeroworks.peripherals")
local network = peripherals.getNetwork()
print(network.space) -- contraption auf einer mobilen Create-Contraption
```

Mobile Metadaten enthalten unter anderem die Entity-UUID, `localPosition` und die projizierte `worldPosition`. Montage und Demontage verwenden reguläre Attach-/Detach-Ereignisse. Positionsschlüssel sollten nicht als dauerhafte Identität gespeichert werden; verwende Desk-IDs und stabile Kanalbindungen.

Die Laufzeit unterstützt ausdrücklich adaptierte Geräte, darunter Adapter für CC:Tweaked-Basisperipherals. Ein beliebiges stationäres Drittmod-Peripheral ist dadurch nicht automatisch mobil verwendbar. Weltziele und Drittmod-Geräte ohne passenden `MobilePeripheralAdapter` werden als nicht verfügbar behandelt.

## Mobile Telemetrie

```lua
local telemetry = require("cc_aeroworks.telemetry")
local source = telemetry.get("contraption")
if source then
  print(textutils.serialize(source))
end
```

Diese Quelle enthält Position, Rotation, Geschwindigkeit und Mounted-Storage-Belegung. Stationäre Weltziele werden mobil als `unavailable` gemeldet. Behandle einen fehlenden Messwert nicht wie einen gültigen Nullwert.

## Sable und GPS

Sable verwendet weiterhin echte Sublevel-BlockEntities. Geräteidentität und Telemetriequellen können auf Sublevel-UUID und lokaler Blockposition beruhen; dadurch bleibt ihre Identität bei Bewegung erhalten.

TeutonLib misst CC:Tweaked-Funkentfernungen und normale Modemreichweiten in sichtbaren Weltkoordinaten. Nach einem erfolgreichen echten GPS-Fix liefern `gps.locate()` und die GPS-Informationsquelle die globale Weltposition. Ein direkt erkennbares Funkmodem bleibt erforderlich; GPS-Hosts in echten anderen Dimensionen werden ignoriert.

Koordinaten im Drive-By-Wire-Zieleditor sind dagegen die gespeicherten Sublevel-Blockkoordinaten samt lokalen Blockseiten. Diese Angaben dürfen nicht mit globalen GPS-Werten vertauscht werden.

## Serveroption

Mit `mobileContraptions.enabled = false` wird die mobile Create-Laufzeit deaktiviert. Bewegte Computer, Adapter, Audio- und Akteurausgänge werden dabei geschlossen. Dies betrifft die mobile Laufzeit gewöhnlicher Create-Contraptions; Sable behält sein Sublevel-Modell.

Siehe [[Konfiguration]], [[Peripheral-Netzwerk]] und [Peripheral-Programmierung](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/docs/peripheral-programming.md).

![Tatsächlich montiertes Pult auf einer laufenden Create-Lager-Contraption.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-create-mobile-desk.png)

![Laufendes Programm auf einem echten Sable-Pult: gps.locate liefert globale Koordinaten 12, 80, 0; das Pultnetz meldet sable.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-sable-gps.png)
