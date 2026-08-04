# Integrations-Testharness

`tools/run-integration-profile.py` führt ein Profil aus [`runtime-test-matrix.md`](runtime-test-matrix.md) gegen ein isoliertes Verzeichnis lokaler Mod-JARs aus.

## Zweck

Der Runner verbindet drei Prüfungen:

1. lokale Artefakte gegen `libs/dependencies.json` validieren,
2. Unit-Tests und vollständigen Modbuild ausführen,
3. ein maschinenlesbares JSON-Ergebnis mit Commit-SHA, Branch, Plattform, Befehlen, Exitcodes und Laufzeiten erzeugen.

Damit ist nachvollziehbar, welcher Code gegen welches Dependency-Verzeichnis geprüft wurde. Das Ergebnis behauptet keine interaktive Weltprüfung; Menschen schaffen es erstaunlich zuverlässig, einen erfolgreichen Compilerlauf mit funktionierendem Gameplay zu verwechseln.

## Aufruf

```bash
python3 tools/run-integration-profile.py BASE-CLIENT \
  --dependency-dir test-mods/base-cc-1.119
```

Mit Dedicated-Server-Smoke-Test:

```bash
python3 tools/run-integration-profile.py BASE-SERVER \
  --dependency-dir test-mods/base-cc-1.119 \
  --server-smoke
```

Windows:

```powershell
py tools/run-integration-profile.py BASE-SERVER `
  --dependency-dir test-mods/base-cc-1.119 `
  --server-smoke
```

## Ergebnis

Standardpfad:

```text
build/test-results/integration/<profil>.json
```

`PASS` bedeutet ausschließlich:

- das Manifest war gültig,
- die verlangten lokalen Abhängigkeiten wurden eindeutig gefunden,
- `test` und `build` endeten erfolgreich,
- der optionale Server-Smoke-Test erreichte erfolgreich seinen Marker.

Interaktive Fälle aus `docs/manual-test-plan.md` werden separat in einem versionierten Baselinebericht dokumentiert.

## Saubere Dependency-Sets

Jedes Profil erhält ein eigenes Verzeichnis. Insbesondere dürfen CC:Tweaked 1.119.0 und 1.120.0 nie gemeinsam geladen werden.

Empfohlene Struktur:

```text
test-mods/
  base-cc-1.119/
  cc-1.120/
  sable/
  full-server/
```

Diese Verzeichnisse gehören nicht ins Repository. Fremdmod-JARs werden weder kopiert noch in Ergebnisdateien eingebettet.

## GameTest-Erweiterung

NeoForge stellt einen `gameTestServer`-Run bereit. CC-Aeroworks behält diese Konfiguration im Build. Automatische Weltfälle werden erst ergänzt, wenn folgende Voraussetzungen erfüllt sind:

- eine minimale Aeroworks-Desk-Struktur kann deterministisch erzeugt werden,
- Montage und Kanalkonfiguration sind ohne clientseitige UI steuerbar,
- Fremdmod-Registries stehen im Testserver reproduzierbar bereit,
- Testwelten enthalten keine redistributierten Fremdmoddaten.

Bis dahin ersetzt der Runner keine GameTests. Er bildet die reproduzierbare äußere Schicht, auf die spätere GameTests aufgesetzt werden.
