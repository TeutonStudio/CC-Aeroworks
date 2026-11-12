# Konfiguration

[[Deutsch|Konfiguration]] · [[English|English#configuration]]

CC-Aeroworks trennt Client- und Servereinstellungen. Die Serverkonfiguration gilt auch im Einzelspieler und wird an verbundene Clients synchronisiert.

## Dateien

| Datei | Geltungsbereich |
|---|---|
| `config/cc_aeroworks-client.toml` | lokale Eingabe und Mausverhalten |
| `config/cc_aeroworks-server.toml` | Standard-Servereinstellungen |
| `<welt>/serverconfig/cc_aeroworks-server.toml` | vorhandener weltbezogener Override mit Vorrang |

Für weltbezogene Einstellungen kopiere die Serverdatei nach `<welt>/serverconfig/` und starte Welt beziehungsweise Server neu.

## Kombinierte Eingabe

| Clientschlüssel | Standard | Bedeutung |
|---|---:|---|
| `combinedLeverSensitivity` | 0.15 | Maus-Empfindlichkeit; Bereich 0.001–10 |
| `combinedLeverInvertY` | false | vertikale Richtung umkehren |
| `combinedLeverPacketRate` | 20 | maximal 1–20 Aktualisierungen pro Sekunde |
| `displayPointerSensitivity` | 0.0025 | Display-Zeigerempfindlichkeit; Bereich 0.0001–0.05 |
| `freezeCameraOnlyWithValidTarget` | true | gültiges Steuerziel voraussetzen |

Auch bei deaktivierter letzter Option friert die Kamera ohne gültigen Lever nicht ein. Die Aktivierungstaste wird pro Achse im Modulbildschirm gespeichert, siehe [[Kombinierte-Eingabe]].

## Displayauflösung

```toml
[display]
ppb = 256
useGlobalResolution = true
maxIndividualPpb = 256
```

- `ppb`: Parts per Block, mindestens 16; Standard 256.
- `useGlobalResolution`: Bei `true` gilt `ppb` für alle Displays. Bei `false` dürfen Lua-Programme individuelle Slot-Auflösungen setzen.
- `maxIndividualPpb`: Obergrenze individueller Werte; Standard 256, Bereich 16–1024.

| PPB | Kleines Display | Großes Display |
|---:|---:|---:|
| 16 | 7 × 7 Pixel | 10 × 7 Pixel |
| 32 | 14 × 14 Pixel | 20 × 14 Pixel |
| 256 | 112 × 112 Pixel | 160 × 112 Pixel |

`getDisplaySize(socket)` liefert immer das effektive Raster. Eine Änderung der Rastergröße leert ein inkompatibles Pixelbild. Texte bleiben erhalten; Skripte müssen Pixel neu zeichnen. Alte Schlüssel wie `display.small.width` und `display.large.height` werden nicht ausgewertet.

## Telemetrie

| Serverschlüssel | Standard | Bereich |
|---|---:|---:|
| `telemetry.maxSourcesPerEndpoint` | 128 | 1–4096 |
| `telemetry.maxListEntries` | 128 | 1–4096 |
| `telemetry.staleAfterTicks` | 220 | 1–72000 |
| `telemetry.validationIntervalTicks` | 20 | 1–1200 |
| `telemetry.dockScanIntervalTicks` | 40 | 1–1200 |

`staleAfterTicks` markiert lange nicht erneuerte Messwerte. Listen können mit `truncated=true` gekürzt sein; Gesamtzahlen bleiben erhalten. Diese Einstellungen ändern weder Creates Display-Link-Reichweite noch dessen Aktualisierungsrate. Gespeichert werden Aliase; aktuelle Messwerte kommen nach einem Neustart erneut aus den Quellen.

## Bewegte Create-Contraptions

```toml
[mobileContraptions]
enabled = true
```

Deaktivieren schließt bewegte Computer, Adapter und Ausgänge. Details unter [[Bewegte Konstruktionen|Bewegte-Konstruktionen]].

## Diagnose

```toml
[debug]
radar = false
display = false
```

Bei `true` erscheinen zusätzliche Radar- beziehungsweise Displaydiagnosen auf INFO im normalen Log. Ein globaler DEBUG-Logger ist nicht erforderlich. Nach der Fehlersuche wieder deaktivieren. Lua-Skriptfehler und echte Warnungen bleiben unabhängig davon sichtbar.

Weitere Erläuterungen: [Konfigurationsreferenz](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/docs/configuration.md).
