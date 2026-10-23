# TeutonLib-Erweiterung: weltbezogenes CC:Tweaked-GPS auf Sable-Sublevels

## Ziel

Ein CC:Tweaked-Computer mit Funkmodem auf einem bewegten Sable-Sublevel soll dieselbe globale
Weltposition ermitteln wie ein stationärer Computer an der sichtbaren Position des Modems.
`gps.locate()` und die GPS-Informationsquelle von CC-Aeroworks dürfen weder an den versteckten
Sable-Plotkoordinaten scheitern noch diese als Ergebnis anzeigen.

Die Lösung gehört nach TeutonLib, weil nicht die Aeroworks-Fachlogik fehlerhaft ist, sondern die
allgemeine Distanzgeometrie zwischen CC:Tweaked-Funkendpunkten und Sable-Sublevels. Damit werden
gleichzeitig normale Modemnachrichten und Rednet-Reichweiten auf beweglichen Sublevels räumlich
konsistent.

## Ausgangslage

- Sable speichert BlockEntities an Plotkoordinaten und projiziert sie über die logische Pose in die
  sichtbare Welt.
- CC:Tweaked `WirelessNetwork.tryTransmit(...)` vergleicht bei identischem `Level` direkt die von
  `PacketSender` und `PacketReceiver` gelieferten `Vec3`-Positionen. Auf einem Sable-Sublevel sind
  das Plotkoordinaten.
- CC:Tweaked-GPS trilateriert die vom Funknetz gelieferten Distanzen gegen die globalen Koordinaten
  der GPS-Hosts. Plotdistanzen und globale Hostkoordinaten bilden deshalb kein gemeinsames
  Koordinatensystem.
- `WirelessModemPeripheral.getRange()` verwendet außerdem die rohe Y-Position für die
  Höhenreichweite normaler Modems.
- CC-Aeroworks' `GpsSourceTracker` verwendet absichtlich das echte CC:Tweaked-Paketnetz. Eine
  Sondertrilateration oder eine zweite Funkimplementierung in Aeroworks wäre daher die falsche
  Schicht.
- TeutonLib besitzt mit `SableLinkPositions` und den Sable-Integrationstests bereits erste
  Weltpositionslogik. Diese darf nicht parallel dupliziert werden.

## Architekturentscheidung

TeutonLib führt eine kleine, wiederverwendbare und optional ladefähige Sable-Geometrie ein. Die
öffentliche Fassade verwendet ausschließlich Minecraft-Typen und bietet mindestens:

- Projektion einer lokalen beziehungsweise Plotposition in die Welt;
- Distanz beziehungsweise Distanzquadrat zwischen zwei Positionen unter Berücksichtigung von
  Sublevels;
- unverändertes Vanilla-Verhalten, wenn Sable fehlt oder keine Position in einem Sublevel liegt.

Die endgültigen Namen sollen den TeutonLib-Konventionen folgen. Eine mögliche Aufteilung ist eine
öffentliche Fassade wie `api.sable.SubLevelGeometry` und eine intern, nur bei vorhandenem Sable
geladene Implementierung unter `integration.sable`. Die öffentliche Klasse darf in Signaturen und
statischer Initialisierung keine Sable-Klassen voraussetzen.

TeutonLib korrigiert anschließend an einer einzigen Stelle die CC:Tweaked-Funkgeometrie:

1. In `WirelessNetwork.tryTransmit(...)` wird nur die Berechnung von `distanceToSqr` durch die
   Sable-fähige Distanz ersetzt. Levelvergleich, Reichweitenprüfung, Interdimensionalität,
   Empfängerfilter und Zustellung bleiben Eigentum von CC:Tweaked.
2. In `WirelessModemPeripheral.getRange()` wird für die Höhenreichweite die projizierte Welt-Y-
   Position verwendet. Advanced-/Ender-Modems behalten ihre bestehende Reichweitensemantik.
3. Die Eingriffe müssen schmale Mixins sein. Die vollständige CC:Tweaked-Sendemethode darf nicht
   kopiert oder neu implementiert werden.
4. Die Mixins werden nur aktiviert, wenn sowohl `computercraft` als auch `sable` geladen sind. Ein
   Lauf nur mit einem der beiden Mods darf keine optionale Klasse auflösen.
5. `SableLinkPositions` wird auf dieselbe zentrale Geometrie umgestellt oder darin aufgeführt,
   sodass TeutonLib genau einen serverseitigen Sable-Weltpositionshelfer besitzt.

Die Korrektur wirkt dadurch auch auf den privaten `PacketReceiver` des Aeroworks-
`GpsSourceTracker`: Der Probe behält seine echte Plotposition, aber CC:Tweaked liefert ihm die
physische Weltdistanz zu den globalen GPS-Hosts. Die bestehende Trilateration ergibt damit die
sichtbare Weltposition, ohne Aeroworks-spezifische Paket- oder Sable-Sonderpfade.

## Semantik und Grenzen

- Welt und Sublevel im selben Minecraft-`Level`: Funkdistanz wird in Weltkoordinaten berechnet.
- Zwei verschiedene Sable-Sublevels desselben `Level`: Beide Endpunkte werden durch Sable
  berücksichtigt; die räumliche Distanz ihrer sichtbaren Posen entscheidet.
- Bewegung und Rotation: Die aktuelle logische Pose gilt bei jeder Übertragung, ohne Cache mit
  veralteter Position.
- Normale Modems: Reichweite und gemeldete `modem_message`-Distanz folgen der sichtbaren Weltlage.
- Advanced-/Ender-Modems: Zustellung bleibt unbegrenzt; innerhalb desselben `Level` wird weiterhin
  eine Distanz geliefert, jetzt jedoch als Weltdistanz.
- Echte verschiedene Dimensionen: Verhalten bleibt unverändert. CC:Tweaked liefert dort keine
  Distanz, und GPS darf daraus weiterhin keinen Fix berechnen.
- GPS-Hostkoordinaten bleiben globale Weltkoordinaten. TeutonLib verändert keine GPS-Payloads.
- Keine Persistenz, kein neues Netzwerkprotokoll und keine neue Lua-API.
- Die GPS-Erkennung und Darstellung im Aeroworks-Workspace bleibt in CC-Aeroworks.

## Arbeitspakete

### 1. Baseline und Binärvertrag

- In TeutonLib und CC-Aeroworks zuerst Arbeitsbaum, lokale Anweisungen und vorhandene Änderungen
  prüfen; fremde Änderungen nicht überschreiben.
- Die installierten Zielversionen von CC:Tweaked 1.120.x und Sable 2.0.5 untersuchen.
- TeutonLibs `DependencyCompatibilityTest` um die tatsächlich verwendeten Methoden und
  Deskriptoren von `WirelessNetwork`, `WirelessModemPeripheral` und Sable Companion ergänzen.
- Baseline für beide Builds und die bestehenden Sable-/Peripheral-Tests dokumentieren.

### 2. Zentrale Sable-Geometrie in TeutonLib

- Die dependency-sichere Fassade und den optionalen Sable-Backendpfad implementieren.
- Für Distanzberechnungen bevorzugt Sables dafür vorgesehene Companion-Funktion
  `distanceSquaredWithSubLevels(...)` verwenden; nicht selbst Poseketten nachbauen.
- Für einzelne Weltpositionen `projectOutOfSubLevel(...)` verwenden.
- `SableLinkPositions` und `LinkRegistry.worldPosition(...)` auf die gemeinsame Implementierung
  umstellen.
- Vanilla-, Welt-zu-Sublevel- und Sublevel-zu-Sublevel-Fälle testen.

### 3. CC:Tweaked-Funkintegration

- Einen serverseitigen Mixin für die Distanzberechnung in `WirelessNetwork.tryTransmit(...)`
  ergänzen.
- Einen zweiten schmalen Eingriff für die Y-Position in
  `WirelessModemPeripheral.getRange()` ergänzen.
- Die Mixin-Konfiguration und `CsoMixinPlugin.shouldApplyMixin(...)` so erweitern, dass beide
  Zielmods explizit erforderlich sind.
- Keine GPS-Kanäle, Nachrichten, Events oder Lua-Funktionen in TeutonLib nachbauen.

### 4. Reale Integrationsprüfung

Den vorhandenen TeutonLib-`peripheralTest` mit echten CC:Tweaked- und Sable-Objekten erweitern.
Mindestens folgende Fälle müssen automatisiert geprüft werden:

1. Welt zu Welt bleibt byte-/bedeutungsgleich.
2. Welt zu bewegtem Sublevel innerhalb normaler Modemreichweite wird zugestellt und meldet die
   erwartete Weltdistanz.
3. Nach Bewegung außerhalb der Reichweite wird nicht zugestellt; nach Rückkehr wieder sofort.
4. Zwei getrennte Sublevels verwenden ihre sichtbare gegenseitige Entfernung.
5. Rotation verändert nicht fälschlich die Punktdistanz.
6. Die Höhenreichweite eines normalen Modems richtet sich nach Welt-Y statt Plot-Y.
7. Ein echter CraftOS-Aufruf `gps.locate()` auf einem Sable-Sublevel liefert mit mindestens vier
   geladenen, nicht kollinearen globalen Hosts die sichtbare Weltposition innerhalb einer kleinen
   numerischen Toleranz.
8. Nach Bewegung des Sublevels liefert ein neuer GPS-Aufruf die neue Position und keinen alten
   Cachewert.
9. Eine echte andere Dimension bleibt distanzlos und erzeugt keinen GPS-Fix.
10. TeutonLib startet ohne Sable sowie ohne CC:Tweaked ohne Mixin- oder Klassenladefehler.

Der GPS-Fall erhält einen eigenen klaren Teststatus, beispielsweise
`PASS sable_gps: world fix, movement, range and dimension isolation`, und wird in
`tools/verify_peripheral_links.py console` ausgeführt. Ein reiner Quelltexttest reicht nicht als
Abnahme.

### 5. CC-Aeroworks-Integration

- Mindestversion beziehungsweise lokales TeutonLib-Testartefakt auf die neue Version anheben.
- `GpsSourceTracker` nicht nach TeutonLib verschieben und keine zweite Sable-Projektion dort
  ergänzen.
- Einen Regressionstest hinzufügen, der für den Aeroworks-Probe auf einem Sable-Sublevel einen
  globalen Fix und in einer echten anderen Dimension weiterhin keinen Fix bestätigt.
- Deutsche und englische Dokumentation sowie Changelogs beider Projekte aktualisieren.
- Festhalten, dass die GPS-Quelle erst nach erfolgreichem Fix erscheint und weiterhin ein direkt
  erkennbares Funkmodem benötigt.

## Abnahmebedingungen

- `gps.locate()` und Aeroworks' GPS-Informationsquelle liefern auf einem bewegten Sable-Sublevel
  die sichtbare globale Weltposition.
- Normale Funkreichweiten und `modem_message`-Distanzen werden zwischen Welt und Sublevels sowie
  zwischen zwei Sublevels in Weltblöcken ausgewertet.
- Vanilla-/Nicht-Sable-Verhalten und echte Dimensionsgrenzen bleiben unverändert.
- TeutonLib besitzt nur einen allgemeinen Sable-Weltgeometriepfad; CC-Aeroworks besitzt keinen
  neuen Funk- oder Projektionssonderfall.
- Optionale Modkombinationen laden ohne `NoClassDefFoundError`, ungültige Mixins oder
  clientseitige Klassen auf dem Dedicated Server.
- TeutonLib- und CC-Aeroworks-Build, Unit-Tests, Binärvertragstests und reale Sable-GPS-
  Integrationstests sind erfolgreich.
- Nicht ausgeführte Ingame- oder Servertests werden ausdrücklich als nicht ausgeführt
  dokumentiert und nicht als bestanden bezeichnet.

## Empfohlene Commit-Reihenfolge

1. TeutonLib: zentrale Sable-Geometrie und Unit-/Binärvertragstests.
2. TeutonLib: CC:Tweaked-Funkmixins und reale Sable-GPS-Integrationstests.
3. CC-Aeroworks: Versionsanhebung, Regressionstest und Dokumentation.
4. Getrennter Cleanup nur für danach wirklich unbenutzte Hilfsklassen.

## Direkt verwendbarer Codex-Auftrag

```text
Implementiere weltbezogenes CC:Tweaked-GPS und weltbezogene Funkdistanzen auf bewegten
Sable-Sublevels als TeutonLib-Erweiterung.

Arbeite in beiden Quellrepositorys:
- TeutonLib: /home/alex/Dokumente/VoxelTeuton/Modding/TeutonLib
- CC-Aeroworks: /home/alex/Dokumente/VoxelTeuton/Modding/CC-Aeroworks

Lies zuerst alle anwendbaren AGENTS.md-Dateien, prüfe in beiden Repositorys git status und die
Build-/Testdokumentation. Beide Arbeitsbäume enthalten möglicherweise fremde, noch nicht
committete Änderungen. Bewahre sie vollständig, überschreibe oder bereinige nichts Unbeteiligtes
und erstelle keine Commits, sofern ich das nicht ausdrücklich verlange.

Nutze den detaillierten Fach- und Abnahmeplan in
CC-Aeroworks/docs/teutonlib-sable-global-gps-codex-auftrag.md als verbindliche Spezifikation.

Architekturvorgaben:
- Behebe die allgemeine CC:Tweaked-Funkgeometrie in TeutonLib, nicht nur den Aeroworks-
  GpsSourceTracker.
- Führe eine dependency-sichere zentrale Sable-Geometrie mit Weltprojektion und
  Sublevel-fähiger Distanz ein. Verwende Sable Companions projectOutOfSubLevel und
  distanceSquaredWithSubLevels statt eigener Posemathematik.
- Korrigiere in CC:Tweaked mit schmalen, optional gated Mixins ausschließlich die
  Distanzberechnung in WirelessNetwork.tryTransmit und die Welt-Y-Auswertung in
  WirelessModemPeripheral.getRange. Kopiere nicht die vollständige CC:Tweaked-Sendemethode.
- Aktiviere die Mixins nur, wenn computercraft und sable vorhanden sind. TeutonLib muss ohne
  jeden der optionalen Mods sauber laden.
- Erhalte das Verhalten für Vanilla-Weltfunk, Advanced-/Ender-Modems und echte verschiedene
  Dimensionen. Verschiedene Dimensionen bleiben distanzlos und liefern keinen GPS-Fix.
- Ändere keine GPS-Payloads, Lua-Namen, Kanäle oder gespeicherten Daten.
- Lass GPS-Probe, Trilateration und Workspace-Projektion in CC-Aeroworks; ergänze dort keine
  zweite Sable-Sonderlösung.
- Konsolidiere TeutonLibs vorhandene SableLinkPositions-Nutzung auf die neue Geometrie.

Verifikation:
- Ergänze Binärvertragstests für alle verwendeten CC:Tweaked- und Sable-Signaturen.
- Ergänze echte TeutonLib-peripheralTest-Fälle für Welt/Sublevel, zwei Sublevels, Bewegung,
  Reichweite, Welt-Y, echte Dimensionsisolation und einen wirklichen CraftOS-gps.locate-Aufruf
  mit globalen GPS-Hosts. Der GPS-Test muss vor und nach Bewegung die sichtbare Weltposition
  prüfen und einen eigenen PASS-Status besitzen.
- Binde den Test in tools/verify_peripheral_links.py console ein.
- Ergänze in CC-Aeroworks einen Regressionstest für den globalen GPS-Fix des bestehenden
  GpsSourceTracker auf Sable.
- Führe mindestens TeutonLib `./gradlew --offline test build compilePeripheralTestKotlin`, die
  relevanten optionalen Startprofile einschließlich ohne Sable/ohne CC:Tweaked,
  `python3 tools/verify_peripheral_links.py console` sowie CC-Aeroworks
  `./gradlew --offline test build` und `python3 tools/verify-gps-information-source.py` aus.
  Falls eine Laufzeitprüfung durch Umgebung oder fehlende Binärdateien unmöglich ist, dokumentiere
  sie ehrlich als nicht ausgeführt.
- Aktualisiere deutsche und englische Dokumentation, Changelogs und den kompatiblen
  TeutonLib-Versionsbereich in CC-Aeroworks.

Arbeite bis zur vollständigen Implementierung und Verifikation. Berichte abschließend getrennt:
geänderte Architektur, sichtbares Verhalten, Tests mit Ergebnis, nicht ausgeführte Prüfungen und
verbleibende Risiken. Nenne keine Prüfung PASS, die du nicht tatsächlich ausgeführt hast.
```
