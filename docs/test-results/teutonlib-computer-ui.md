# TeutonLib-Computeroberfläche: Prüfergebnis

Datum: 18. Oktober 2026. CC-Aeroworks 1.4.0 mit TeutonLib 1.1.0, Minecraft 1.21.1 und NeoForge 21.1.234.

## Bestanden

| Prüfung | Ergebnis |
|---|---|
| CC-Aeroworks `./gradlew --offline test build` | 95 Tests, keine Fehler |
| TeutonLib `./gradlew --offline test build compilePeripheralTestJava` | 30 Tests, keine Fehler; native Vorlage und Integrationsfixtures kompiliert |
| Repository-Vertragsprüfungen | Alle 31 `tools/verify-*.py`; vier Artefakt-/Bytecodeprüfungen mit offiziellen Downloads |
| Dedicated Server | `tools/dedicated-server-smoke.py --dependency-dir libs`: Done-Marker erreicht, kein bekannter fataler Marker |
| Bestehende Computer-GameTests | Alle 11 erforderlichen Tests erfolgreich |
| TeutonLib `verify_peripheral_links.py console --dbw` | Native Computer, Pulte, gemeinsame Workspaces, Links, Sable, Teilung/Vereinigung und Neustart erfolgreich |
| Derselbe Serverdurchlauf ohne `--dbw` | Erfolgreich; Pultbearbeitung und Quellen funktionieren ohne DbW |
| Automatisierter Clienttest | Native Computer, Addon-Vorlage und Aeroworks-Workspace; normal/erweitert; GUI-Skalierung 2/3; erfolgreich |

Die Workspace-Serverprüfung bedient den tatsächlichen Menütransport für Kanalcreation und verworfene Menü-IDs. Sie prüft außerdem Gruppen, Bindungen, Aliasänderungen, Itempersistenz, Quellenprojektion, verweigerten Zugriff außerhalb der Reichweite und den Schutz vor einer zweiten beschreibbaren Kanalbank. Die bestehenden nativen Kanaltests prüfen zusätzlich zwei getrennte Spielermenüs und Neustart/Chunk-Lebenszyklus.

Die Clientprüfung erzeugt synthetische Computer-Menüs ohne Spielwelt, klickt die tatsächlichen Reiter und prüft genau eine gemeinsame Reiterleiste, Terminal-Sichtbarkeit/Aktivität, Editorfokus, Inventartaste, Escape, Terminalfokus und eine neue Sitzung nach Resize. Beispiel-Snapshots prüfen die Darstellung und Auswahl. Die Screenshots wurden visuell kontrolliert.

## Lokale Nachweise

- `build/test-results/test/` und `build/reports/tests/test/`
- `build/test-results/server-smoke/`
- `build/test-results/teutonlib/console-dbw.log`
- `build/test-results/teutonlib/console-without-dbw.log` und `.json`
- `build/test-results/teutonlib/computer-ui.txt`
- `build/test-results/teutonlib/teutonlib-computer-ui-{18,20,22,24}.png`
- TeutonLib-Projekt: `build/test-results/test/`

## Grenzen der Prüfung

Die neue Oberfläche wurde nicht manuell in einer vorhandenen Spielerwelt bedient. Die exakte Steuerungsrückkehr wird weiterhin durch die Repository-Vertragsprüfung abgedeckt; der synthetische Clienttest hat keinen echten Modul-Rückkehrkontext. Die tatsächliche Radar-/GPS-/Display-Skript-Darstellung wird durch Projektion und bestehende Fachtests abgesichert, nicht durch einen vollständigen visuellen In-World-Test jeder Quellenkombination.
