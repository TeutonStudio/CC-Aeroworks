# Releaseprüfung 1.3.4

Datum: 11. Oktober 2026. Finaler Arbeitsstand mit Diagnose-Schaltern, Radar-Quellenkorrekturen und Statusanzeigen; Minecraft 1.21.1, NeoForge 21.1.228 und den Abhängigkeiten aus `libs/dependencies.json`.

## Ergebnis

- `./gradlew clean build --offline --stacktrace`: erfolgreich.
- 88 Unit-Tests, keine Fehler und keine übersprungenen Tests. Die neue Diagnoseprüfung deckt ungeladene Config, Standardwerte, alle vier Schalterkombinationen, Abschalten, INFO-Level, Display-Sample-Filter und verzögerte beziehungsweise gedrosselte Radar-Nachrichten ab.
- Alle 31 `tools/verify-*.py`-Prüfungen erfolgreich. Die vier Bytecode-Prüfungen wurden mit Netzwerkzugriff gegen die festgelegten öffentlichen Artefakte ausgeführt.
- Isolierter Dedicated-Server-Start mit Create: Radars: `Done` erreicht, kein bekannter fataler Marker.
- Isolierter Dedicated-Server-Start ohne Create: Radars: Integration als deaktiviert gemeldet, `Done` erreicht, kein bekannter fataler Marker.
- Beide Serverläufe erzeugten `config/cc_aeroworks-server.toml` mit `debug.radar = false` und `debug.display = false`. Keine Radar- oder Touch-Traces in den Serverlogs.
- Isolierter Clientstart: Ressourcen und Shader geladen, keine fatalen CC-Aeroworks-/Mixin-Fehler beobachtet; anschließend gezielt beendet. Der Start verwendete denselben IDE-`util_rt.jar`-Classpath wie die gezielte Cache-Prüfung.
- JAR-ZIP-Integrität und Metadaten geprüft: beide Mod-Einträge tragen Version 1.3.4; keine eingebetteten Minecraft-, Aeroworks- oder Create:-Radars-Klassen.

## Zusätzliche ästhetische Korrektur

Die Kategorie-Balken im Aeroworks-Kreativtab überdeckten bisher die unterste Pixelzeile der 18 Pixel hohen Slots. Die Balken enden jetzt einen Pixel früher, sodass die originale untere Slotkante sichtbar bleibt. Die Slottextur wurde für alle neun Spalten und fünf sichtbaren Reihen geprüft. Der finale Build einschließlich aller 88 Tests und beide Dedicated-Server-Starts enthalten diese Änderung. Eine interaktive Sichtprüfung der Kategoriezeilen im Spiel steht aus.

## Radar-Laufzeitprüfung in einer Weltkopie

Ein temporäres, nicht mitgeliefertes Testmodul öffnete eine isolierte Kopie des vorhandenen Aufbaus. Die Originalwelt wurde nur gelesen.

- Reproduktion: Die native Controller-Gruppe hatte Monitorendpoints, aber `radarPos = null`; beide ausgewählten Anzeigen blieben deshalb inaktiv. Ein direkt angrenzender Controller ersetzt die native Radarzuordnung nicht.
- Nur in der Kopie wurde die fehlende Zuordnung über `NetworkData.attachRadar` ergänzt. Der Server lieferte anschließend `ACTIVE`, Reichweite 20 und zwei gefilterte Tracks.
- Ein echtes Clientpaket wählte für das kleine Display den Ingress des großen Displays. Die Serverbestätigung wurde zurücksynchronisiert. Für beide Größen wurden erfolgreiche Aufrufe von `MonitorRenderer.renderRadarDisplay` protokolliert; keine native Renderexception.
- Quellenwahl am Sable-Pult mit Plotposition `(20481032, 129, 20481032)` wurde aus projizierter Spielernähe akzeptiert und synchronisiert. Nach Teleport außerhalb der Reichweite wurde ein Änderungsversuch abgewiesen; das Binding blieb erhalten.
- Testlauf endete mit `PASS` und kontrolliertem Speichern/Herunterfahren. Diagnoseausgaben waren ausschließlich in der Testkopie aktiviert. Das Testmodul ist nicht in der Release-JAR enthalten.
- Neue Unit-Tests decken lokale, fremde, eigene explizite und nicht mehr verfügbare Quellen, alle inaktiven Statuswerte sowie Snapshot-Transport und Client-Frische ab.

## Gradle-Reparatur

Der gemeldete Transform `532c3322c0bd0ce0b963291c3482debc` wurde nach Stoppen der Gradle-Daemons aus dem Cache gesichert. Ein Init-Skript mit dem identischen IDE-`util_rt.jar` erzeugte genau diesen Transform neu. `runClient --dry-run --offline --stacktrace` und der anschließende echte Clientstart passierten die zuvor fehlschlagende Konfiguration erfolgreich. Die Reparatur betrifft die lokale Entwicklungsumgebung; die Mod-JAR verändert keine Gradle-Caches.

## Artefakt

Datei: `build/libs/cc-aeroworks-1.3.4.jar`

SHA-256:

```text
18867cdeaf66171e34d4a9b7ea615267c52fa2cefc965ba9c9f48f3d38768d7a
```

Eine passende `.sha256`-Datei liegt neben der JAR.

## Grenzen

Keine interaktive Sichtprüfung von Radar-Grafik oder Kategoriezeilen und keine manuelle Touch-/Draw-Prüfung. Der beschriebene Radar-Laufzeittest lief mit integriertem Server; ein dedizierter Server mit verbundenem Grafikclient sowie dessen Config-Synchronisierung wurden nicht zusätzlich manuell geprüft. Der Clientlog enthält Ressourcenfehler zu leeren beziehungsweise fehlenden Create:-Radars-Modellen und Shader-Warnungen anderer Mods; diese verhinderten den beobachteten Clientstart nicht. Vorhandene Compiler- und Gradle-Deprecation-Warnungen sind nicht Gegenstand dieser Korrektur.

Laufzeitbelege liegen unter `build/test-results/` sowie in `build/smoke-client-1.3.4/logs/`, `build/smoke-server-1.3.4/logs/` und `build/smoke-server-no-radar-1.3.4/logs/`. Der Radar-Laufzeittest verwendete ausschließlich eine Kopie; bestehende Spielstände wurden nicht verändert. Die Probe liegt unter `build/radar-release-client/`, ihr Ergebnis unter `build/test-results/radar-release-probe.txt`.
