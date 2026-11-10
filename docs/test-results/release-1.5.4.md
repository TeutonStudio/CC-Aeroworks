# Releaseprüfung 1.5.4

Datum: 10. November 2026. Minecraft 1.21.1, NeoForge 21.1.234, CC:Tweaked 1.120.2,
Create: Aeroworks 1.5.0, TeutonLib 1.6.0 und Sable 2.0.6.

## Änderungen

- TeutonLib-Buildbasis und erforderlicher Laufzeitbereich auf `[1.6.0,1.7.0)` aktualisiert.
- Lokale Release-JAR aus dem benachbarten TeutonLib-Projekt übernommen und SHA-256 im Manifest gepinnt.
- Sable auf 2.0.6 angehoben, da TeutonLib 1.6.0 ältere Sable-Versionen ablehnt.
- TeutonLib-Prüfquellen in der CI auf Commit `15f1a2a17b5a2dd09e6874aad8d001682ec3e07f` gepinnt.
- Test-Heap auf 2 GiB erhöht: Der vollständige NeoForge-Modstack überschreitet beim Start das bisherige Standardlimit von 512 MiB.

## Bestanden

- `./gradlew test build --offline`: alle 167 Unit-Tests erfolgreich.
- `./gradlew runGameTestServer --offline -I /tmp/cc-aeroworks-1.5.4-gametest.gradle`:
  alle 31 erforderlichen GameTests erfolgreich. Das Init-Skript setzt ausschließlich
  `neoForge.runs.gameTestServer.gameDirectory` auf `build/gametest-teutonlib-1.6.0`.
- Alle 30 Python-Verträge aus `.github/workflows/verify.yml` erfolgreich.
- Release-Metadaten in der JAR enthalten Version 1.5.4, TeutonLib `[1.6.0,1.7.0)` und Sable `[2.0.6,)`.
- `git diff --check` ohne Fehler.

## Artefakt

`build/libs/cc-aeroworks-1.5.4.jar`

SHA-256: `1e023c394180a18c9d3c5a3805c4c7abc8ff74d5a700d140c6f51f71cd4d57e5`.

## Grenzen

Eine manuelle Client-Sichtprüfung im Spiel wurde nicht durchgeführt.
