# Create-Display-Link-Telemetrie

CC-Aeroworks kann Create-Display-Links direkt als Telemetrieeingänge des eingebetteten `ComputerControlDesk` verwenden. Der Computer ist dabei ein Create-`DisplayTarget`: Der Display Link bleibt für Quelle, Filterung und Aktualisierung zuständig; CC-Aeroworks speichert die empfangenen Messwerte strukturiert und stellt sie über die globale Lua-API `telemetry` bereit.

Normale Aeroworks-ControlDesks behalten ihr bisheriges Display-Link-Verhalten und zeigen Create-Text auf den montierten Desk-Displays an. Ein Display Link auf einen `ComputerControlDesk` wird dagegen als Telemetriequelle behandelt.

## Datenfluss

```text
Tank / Inventar
      |
Threshold Switch / Smart Observer
      |
Display Link
      |
ComputerControlDesk
      |
telemetry API
      |
Lua-Programm -> Displays / Warnungen / Automatisierung
```

CC-Aeroworks parst keine bereits formatierten Texte wie `50%` oder `8 B` zurück in Zahlen. Für bekannte Create-Quellen werden die zugrunde liegenden Create-Behaviours und Messwerte gelesen. Der vom Display Link gelieferte Text bleibt nur als `displayText`-Fallback und Diagnoseinformation erhalten.

## Unterstützte Create-Quellen

| Display-Source | `kind` | Strukturierte Daten |
|---|---|---|
| `create:fill_level` | `fill_level` | `current`, `minimum`, `maximum`, `fraction`, `percent`, `contentType` |
| `create:count_items` | `item_count` | `count` |
| `create:list_items` | `item_list` | `totalCount`, `entryCount`, `entries`, `truncated` |
| `create:count_fluids` | `fluid_amount` | `amount`, `buckets` |
| `create:list_fluids` | `fluid_list` | `totalAmount`, `entryCount`, `entries`, `truncated` |

Unbekannte Display-Sources bleiben sichtbar:

```lua
{
  supported = false,
  kind = "unsupported",
  sourceType = "create:kinetic_speed",
  displayText = { "128 RPM" }
}
```

Damit wird nichts erfunden. Sobald eine weitere Source strukturiert unterstützt wird, kann ein Decoder ergänzt werden, ohne die Telemetriearchitektur zu ändern.

## Einrichtung

Für einen Tankfüllstand:

1. Tank mit einem Create Threshold Switch beobachten.
2. Einen Display Link am Threshold Switch konfigurieren.
3. Als Ziel den `ComputerControlDesk` wählen.
4. Im Display Link die gewünschte Create-Source auswählen.
5. Im Computer `telemetry.list()` oder `telemetry.get(...)` verwenden.

Für Item- oder Fluidlisten wird ein Smart Observer verwendet. Sein Create-Filter wird von der strukturierten CC-Aeroworks-Auswertung respektiert.

Mehrere Display Links dürfen gleichzeitig auf denselben Computer zeigen. Das Telemetrie-Target besitzt genau eine gemeinsame Create-Zeile; Create reserviert Zeile 0 nicht exklusiv. Eine künstliche Portnummer pro Sensor ist daher nicht nötig.

Die normale Create-Reichweite des Display Links bleibt unverändert. CC-Aeroworks baut keine zweite Reichweitenlogik darum herum.

## Globale Lua-API

Nur der eingebettete Computer besitzt `telemetry`. Dasselbe Objekt kann als Modul geladen werden:

```lua
local telemetry = require("cc_aeroworks.telemetry")
```

Methoden für lokale Quellen:

- `telemetry.list() -> table`
- `telemetry.get(nameOrId) -> table|nil`
- `telemetry.find(type) -> table`
- `telemetry.rename(nameOrId, alias) -> table`
- `telemetry.clearName(nameOrId) -> table`
- `telemetry.getStatus() -> table`

`get` akzeptiert die stabile Source-ID, einen eigenen Alias und, soweit die Create-Source ein Label besitzt, dieses Create-Label.

### Beispiel

```lua
local sources = telemetry.list()

for id, source in pairs(sources) do
  print(id, source.kind, source.supported, source.stale)
end
```

Ein Füllstand sieht beispielsweise so aus:

```lua
{
  id = "stabile-uuid",
  alias = "fuel",
  sourceType = "create:fill_level",
  kind = "fill_level",
  supported = true,
  available = true,
  stale = false,
  revision = 12,
  value = {
    contentType = "fluid",
    current = 48000,
    minimum = 0,
    maximum = 64000,
    fraction = 0.75,
    percent = 75.0
  }
}
```

## Stabile IDs und bewegliche Sable-Level

Eine Source-ID wird deterministisch aus dem physischen Display Link erzeugt.

- Normale Welt: Dimension + Display-Link-Blockposition.
- Sable-Sublevel: persistente Sublevel-UUID + lokale Display-Link-Position.

Damit bleibt eine Messstelle auf einem fahrenden oder rotierenden Sable-Fahrzeug dieselbe Source. Weltkoordinaten eines beweglichen Fahrzeugs als Identität zu benutzen wäre schließlich eine recht kreative Definition von "stabil".

## Aliase

IDs sind für Programme gut, Menschen bevorzugen meist Wörter. Ein Alias wird deshalb am Telemetrie-Endpunkt gespeichert:

```lua
local source = telemetry.rename(sourceId, "fuel")
local fuel = telemetry.get("fuel")
```

Entfernen:

```lua
telemetry.clearName("fuel")
```

Aliase werden persistent gespeichert. Aktuelle Messwerte dagegen bleiben ausschließlich im Runtime-Cache und verursachen keine NBT-Schreibvorgänge bei jeder Tankänderung.

## Frische und Lifecycle

Jede Source besitzt:

- `lastSeenTick`
- `ageTicks`
- `stale`
- `revision`

Nach `telemetry.staleAfterTicks` ohne neue Übertragung wird die Source nur als `stale=true` markiert. Sie wird nicht allein wegen eines Timeouts entfernt, weil Create-Display-Links beispielsweise über Redstone angehalten werden können.

Entfernt wird eine Source erst, wenn ihr Chunk geladen ist und CC-Aeroworks bestätigen kann, dass der Display Link fehlt, auf ein anderes Ziel zeigt oder eine andere Source verwendet.

## Events

Der eingebettete Computer erhält bei tatsächlichen Zustandsänderungen:

```text
cc_aeroworks_telemetry_added
cc_aeroworks_telemetry_changed
cc_aeroworks_telemetry_removed
```

Argumente:

```text
added:   sourceId, revision
changed: sourceId, revision
removed: sourceId
```

Ein identischer periodischer Display-Link-Refresh erhöht die Revision nicht und erzeugt kein `changed`-Event.

## Listenbegrenzung

Sehr große Item- und Fluidlisten werden durch `telemetry.maxListEntries` begrenzt. Die Tabelle enthält trotzdem die vollständige `entryCount` und setzt `truncated=true`.

Beispiel:

```lua
local cargo = telemetry.get("cargo")
if cargo.value.truncated then
  print("Weitere Einträge vorhanden:", cargo.value.entryCount)
end
```

## Serverkonfiguration

In `cc_aeroworks-server.toml` stehen unter `telemetry`:

- `maxSourcesPerEndpoint = 128`
- `maxListEntries = 128`
- `staleAfterTicks = 220`
- `validationIntervalTicks = 20`
- `dockScanIntervalTicks = 40`

Die Werte begrenzen Speicher-/Lua-Tabellengröße und steuern Lifecycle-Prüfungen. Sie verändern Creates eigene Display-Link-Reichweite und Source-Refreshregeln nicht.

## Docking

Für Telemetrie über Simulated-Docking-Connectoren und zwischen getrennten Sable-Sublevels siehe [`docking-telemetry.md`](docking-telemetry.md).
