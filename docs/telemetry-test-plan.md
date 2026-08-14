# Telemetrie-Testplan

Dieser Plan deckt die Create-Display-Link-Telemetrie des ComputerControlDesk und den optionalen Relay über Create: Simulated Docking Connectoren ab.

## Automatische Prüfungen

### Repositoryvertrag

```bash
python3 tools/verify-telemetry.py
```

Die Prüfung stellt sicher, dass:

- normale ControlDesks ihr bisheriges DisplayTarget behalten;
- ComputerControlDesks das Telemetrie-Target verwenden;
- der Simulated-Docking-Connector nur optional/dynamisch registriert wird;
- die fünf strukturierten Create-Decoder vorhanden sind;
- Item-/Fluidfilter die Create-Behaviours verwenden;
- strukturierte Werte nicht aus formatiertem Displaytext geparst werden;
- stabile IDs, Alias-Persistenz, stale/revision und Linkvalidierung vorhanden sind;
- Lua-API und Events registriert sind;
- Docking-Discovery nur den Sable-Plot verwendet;
- Transferpuffer ausdrücklich getrennt von Remote-Telemetrie behandelt werden;
- Dokumentation und Beispielprogramm vorhanden sind.

### Unit-Tests

`TelemetryPayloadTest` prüft reine Payload-Semantik:

- Prozent-/Fraction-Berechnung für `fill_level`;
- Verhalten bei Nullbereich;
- Listentrunkierung bleibt explizit;
- Fluid-Bucket-Umrechnung.

Die vollständige Laufzeit benötigt die modifizierte Minecraft-/Create-/CC:Tweaked-Testumgebung und läuft deshalb im normalen Gradle-Testlauf mit bereitgestellten Mod-Abhängigkeiten.

## Lokale Telemetrie

### Fill Level

Aufbau:

```text
Create Fluid Tank -> Threshold Switch -> Display Link -> ComputerControlDesk
```

Testwerte:

| Inhalt | Kapazität | Erwartung |
|---:|---:|---:|
| 0 | 16000 | 0 % |
| 4000 | 16000 | 25 % |
| 8000 | 16000 | 50 % |
| 16000 | 16000 | 100 % |

Prüfen:

- `sourceType == "create:fill_level"`
- `kind == "fill_level"`
- `contentType == "fluid"`
- `current/minimum/maximum` korrekt
- `fraction` nicht gerundet
- `percent` nicht vorzeitig gerundet

### Item Count mit Filter

Lager:

```text
100 Iron Ingot
200 Copper Ingot
300 Gold Ingot
```

Smart Observer auf Iron filtern und `count_items` verwenden.

Erwartung:

```lua
source.value.count == 100
```

Nicht 600.

### Item List

Ohne Filter alle drei Itemtypen listen. Erwartung:

- `totalCount == 600`
- `entryCount == 3`
- Sortierung nach Menge absteigend
- Registry-ID und Anzeigename pro Eintrag

Zusätzlich zwei ItemStacks gleicher Item-ID aber unterschiedlicher Components testen. Sie dürfen nicht zusammengelegt werden.

### Fluid Count/List

Mehrere Fluidtanks mit Filter testen. Prüfen:

- Rohmenge in Minecraft-Fluid-Einheiten;
- Convenience-Wert `buckets`;
- Filterwirkung;
- Gruppierung gleicher Fluids;
- Sortierung nach Menge.

## Mehrere Links auf ein Ziel

Mindestens zehn Display Links auf denselben ComputerControlDesk richten.

Erwartung:

- alle Quellen erscheinen gleichzeitig in `telemetry.list()`;
- keine Source verdrängt eine andere wegen Create-Zeilenreservierung;
- jede Source besitzt eine andere stabile ID.

## Aliase

```lua
local source = next(telemetry.list())
telemetry.rename(source.id, "fuel")
assert(telemetry.get("fuel").id == source.id)
```

Welt speichern und neu laden. Alias muss erhalten bleiben.

Doppelten Alias auf demselben Endpoint versuchen. Erwartung: Lua-Fehler mit klarer Ursache.

## Lifecycle

### Stale

Display Link per Redstone pausieren.

Nach `staleAfterTicks`:

```lua
telemetry.get(id).stale == true
```

Source darf nicht gelöscht werden.

### Entfernen

Display Link abbauen, während der Chunk geladen ist.

Nach der Validation:

- Source verschwindet;
- `cc_aeroworks_telemetry_removed` wird einmal erzeugt.

### Umkonfigurieren

Bestehenden Link auf anderes Ziel oder andere Source stellen.

Alter Registry-Eintrag muss verschwinden beziehungsweise als neue Source aktualisiert werden. Keine Phantomquelle darf bestehen bleiben.

### Identischer Refresh

Source-Wert konstant lassen und mehrere passive Create-Refreshs abwarten.

`revision` darf nicht steigen und `cc_aeroworks_telemetry_changed` darf nicht erneut ausgelöst werden.

## Computer Lifecycle

Computer ausschalten, Telemetriewert ändern und später einschalten.

Erwartung:

- Runtime-Endpoint wird weiter aktualisiert;
- `telemetry.get(...)` enthält nach dem Einschalten sofort den aktuellen Wert;
- es wird keine künstliche Historie aller während der Abschaltung verpassten Events abgespielt.

## Sable-Bewegung

Computer, Sensor und Display Link auf dasselbe bewegliche Sable-Sublevel setzen.

- Translation testen;
- Rotation testen;
- speichern/neuladen;
- weiterbewegen.

Source-ID muss konstant bleiben und der Datenfluss darf nicht von der Weltpose abhängen.

## Docking

### Discovery

Auf dem Computer-Sublevel 0, 1, 2 und 4 Docking Connectoren testen.

`telemetry.getDocks()` muss ausschließlich Connectoren desselben Sable-Sublevels liefern.

### Zustände

Simulated Connector nacheinander in folgende Zustände bringen:

- unpowered/retracted
- extended
- locking
- locked

`dock.getInfo().state` und die booleschen Felder prüfen.

### Remote-Tank

Remote Sublevel:

```text
Tank -> Threshold Switch -> Display Link -> Remote Dock
```

Nach `LOCKED`:

```lua
local dock = telemetry.getDock(localAlias)
local fuel = dock.getTelemetry("fuel")
```

Tankinhalt ändern. Erwartung:

- Remote-Payload ändert sich;
- Revision steigt;
- `cc_aeroworks_remote_telemetry_changed` wird erzeugt.

### Abkoppeln

Remote-Modul entfernen.

Erwartung:

- `dock.getInfo().locked == false` beziehungsweise Dockzustand aktualisiert;
- `listTelemetry()` liefert keine Daten des alten Moduls;
- ein später angedocktes anderes Modul sieht nur seinen eigenen Endpoint.

### Mehrere Docks

Zwei Module gleichzeitig koppeln:

```text
left  -> Fuel Pod
right -> Cargo Pod
```

Beide dürfen eine Source `status` besitzen. Die Namen dürfen sich nicht gegenseitig überschreiben, weil jede Remote-Source im Namespace ihres Dock-Handles lebt.

### Persistente Remote-Aliase

Auf Modul A:

```lua
dock.renameTelemetry(sourceId, "fuel")
```

Modul A abkoppeln, an ein anderes Fahrzeug koppeln und `getTelemetry("fuel")` erneut prüfen. Alias muss dem Remote-Dock folgen.

## Transferpuffer

`dock.getTransferBuffers()` während Item-/Fluid-/Energietransfer beobachten.

Prüfen:

- Werte entsprechen nur den Connector-Capabilities;
- API nennt sie nicht Tank-/Cargo-Füllstand;
- Remote-Tankinhalt wird weiterhin ausschließlich über `getTelemetry(...)` ermittelt.

## Optionale Mod-Abwesenheit

Testprofil ohne Simulated starten.

Erwartung:

- CC-Aeroworks startet;
- lokale Display-Link-Telemetrie funktioniert;
- `telemetry.getDocks()` liefert `{}`;
- `telemetry.getStatus().simulatedDockingAvailable == false`;
- kein `ClassNotFoundException`/`NoClassDefFoundError` aus Simulated-Compat.

## Dedicated Server

Mit dem geschützten Baseline-Dependency-Bundle:

```bash
python3 tools/run-integration-profile.py BASE-SERVER --dependency-dir <mods> --server-smoke
```

Zusätzlich ist ein vollständiges:

```bash
./gradlew clean test build
```

erforderlich, bevor der Featurebranch in `master` gemergt wird.
