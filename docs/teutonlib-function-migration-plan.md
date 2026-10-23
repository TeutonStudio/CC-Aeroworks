# Codex-Plan: Wiederverwendbare Funktionen nach TeutonLib verschieben

## Ziel

Die wiederverwendbare ComputerCraft-, UI-, Peripheral-, Kanal- und Sable-Infrastruktur aus
CC-Aeroworks wird in TeutonLib überführt. CC-Aeroworks behält ausschließlich seine fachliche
Aeroworks-Integration und registriert diese über öffentliche TeutonLib-Schnittstellen.

Am Ende gilt eine gerichtete Abhängigkeit:

```text
CC-Aeroworks -> TeutonLib -> optionale Fremdmods
```

TeutonLib darf weder zur Compilezeit noch im Bytecode auf Klassen unter
`de.teutonstudio.ccaeroworks` zugreifen. Insbesondere werden die heutigen TeutonLib-Mixins auf
interne Klassen wie `PeripheralNetworkRuntime`, `PeripheralBinding`, `ConsoleComputer` und
`InformationSourceSnapshot` durch registrierbare APIs ersetzt.

## Nichtziele

Folgende Fachlogik bleibt in CC-Aeroworks:

- Auflösung, Validierung, Teilung und Vereinigung des Aeroworks-Pultmultiblocks;
- Auswahl und Transfer des eingebetteten Computers innerhalb einer Pultreihe;
- Aeroworks-Module, Sockets, Eingabekanäle und Control Overrides;
- programmierbare Displays, Pixelzustände, Touch- und Draw-Semantik;
- Create-Display-Link-Telemetrie und deren Lua-Vertrag;
- Radar-, Zug- und sonstige mod-spezifische Integrationen;
- `DeskComputerWorkspace` als Aeroworks-Adapter auf die TeutonLib-Workspace-API.

## Arbeitsregeln für Codex

1. Arbeite in beiden Quellrepositorys. Im CC-Aeroworks-Repository liegt derzeit nur das gebaute
   `teutonlib-1.3.4.jar`; ohne TeutonLib-Quellcheckout darf keine Implementierung durch
   Dekompilieren oder Patchen des JARs begonnen werden.
2. Prüfe in beiden Repositorys zuerst `AGENTS.md`, den Arbeitsbaum und die vorhandenen Build- und
   Testanweisungen. Fremde Änderungen bleiben unangetastet.
3. Halte beide Projekte nach jedem Arbeitspaket kompilierbar. Verwende kurzlebige
   Kompatibilitätsadapter, wenn ein atomarer Cross-Repository-Wechsel nicht möglich ist.
4. Erhalte gespeicherte Computer-IDs, Labels, Dateien, Kanal-UUIDs, kanonische Kanal-IDs,
   Gruppennamen, Bindungsziele und vorhandene Item-Komponenten byte- beziehungsweise
   bedeutungsgleich.
5. Ändere öffentliche Lua-Namen und Ereignisse nicht: `peripherals`, `channels`, `controls`,
   `wires`, `telemetry`, Attach-/Detach-Ereignisse und die dokumentierten Rückgabestrukturen bleiben
   kompatibel.
6. Verschiebe nicht lediglich Klassen zwischen Paketen. Trenne generischen Mechanismus und
   Aeroworks-Adapter durch kleine öffentliche Interfaces.
7. Vermeide neue TeutonLib-Sonderfälle, die anhand von Mod-ID, Datenkomponente oder konkretem
   CC-Aeroworks-Klassennamen erkennen, ob ein Desk-Computer vorliegt.
8. Veröffentliche keine Zwischenversion, in der TeutonLib und CC-Aeroworks nur in einer zufälligen
   lokalen Kombination funktionieren. Die Release-Anhebung beider Projekte wird koordiniert.

## Phase 0: Ausgangslage und Architekturvertrag sichern

### Aufgaben

- Beide Repositorys und ihre erwarteten Zielbranches eindeutig bestimmen.
- In beiden Repositorys Build und Unit-Tests ausführen und die Ergebnisse als Baseline notieren.
- Im CC-Aeroworks-Repository zusätzlich die vorhandenen `tools/verify-*.py`-Prüfungen und den
  vorgesehenen Dedicated-Server-Smoke-Test erfassen.
- Mit `jdeps` alle TeutonLib-Referenzen auf `de.teutonstudio.ccaeroworks` als Maschinenbericht
  speichern.
- Die gegenwärtigen öffentlichen Lua-Methoden, Payload-IDs, Ereignisnamen, Datenkomponenten und
  gespeicherten Formate als Kompatibilitätsvertrag erfassen.
- Einen Architekturtest in TeutonLib hinzufügen, der Build-Artefakte auf verbotene Referenzen zu
  `de.teutonstudio.ccaeroworks` prüft.
- Einen Gegenrichtungstest in CC-Aeroworks ergänzen, der nur freigegebene TeutonLib-API-Pakete als
  stabile Integrationspunkte akzeptiert.

### Abnahme

- Baseline-Status beider Projekte ist dokumentiert.
- Die aktuelle Rückabhängigkeit wird zuverlässig von einem automatisierten Test erkannt.
- Es wurde noch kein persistiertes Format geändert.

## Phase 1: Peripheral-Lifecycle nach TeutonLib verschieben

Dies ist das erste Implementierungspaket, weil TeutonLib heute direkt in die internen
CC-Aeroworks-Klassen des Peripheral-Netzwerks eingreift.

### Neue TeutonLib-Abstraktionen

Unter einem öffentlichen Paket wie `api.computer.peripheral` werden mindestens folgende Konzepte
eingeführt; die endgültigen Namen müssen den bestehenden TeutonLib-Konventionen folgen:

- `PeripheralTopologyProvider`: liefert für einen `ComputerContext` einen unveränderlichen Snapshot;
- `PeripheralTopology`: Revision, Zustand, logische Hosts und erreichbare Endpunkte;
- `PeripheralEndpoint`: stabile Adresse, Typen, Peripheral, Verbindungsmetadaten und Owner-Schlüssel;
- `PeripheralAttachmentRuntime`: Graphvergleich, Attach/Detach, Refresh und CC-Ereignisse;
- `PeripheralDeviceBinding`: Methodenauflösung, `IComputerAccess`, `GuardedLuaContext`, Mounts und
  idempotentes Schließen;
- optionale Descriptor-/Handle-Factorys, damit ein Addon eigene Host-Handles bereitstellen kann.

Die API darf keine Aeroworks-Typen enthalten. Endpunkt- und Owner-IDs sind opake Werte des
Providers. Das Runtime-Modul besitzt ausschließlich den generischen Lifecycle.

### Migration in CC-Aeroworks

- `PeripheralBinding` und `PeripheralMountRegistry` durch die TeutonLib-Implementierung ersetzen.
- Attach-/Detach-, Mount-, Fehler- und Main-Thread-Verhalten unverändert übernehmen.
- Den generischen Teil von `PeripheralNetworkRuntime` durch `PeripheralAttachmentRuntime` ersetzen.
- `PeripheralNetworkBuilder` auf einen Aeroworks-`PeripheralTopologyProvider` reduzieren.
- `DeskLuaHandle` und die Methoden aus `AeroworksDeskService` im Mod belassen.
- Typalias-Normalisierung nur dann in TeutonLib aufnehmen, wenn sie über Provider oder Strategie
  konfigurierbar ist; die besondere Erkennung von `ControlDesk` bleibt im Aeroworks-Adapter.
- Peripheral-Link-Geräte über die öffentliche Provider-API einspeisen. TeutonLib darf dafür keine
  Mixins mehr auf CC-Aeroworks-Runtimeklassen anwenden.

### Tests

- Attach genau einmal, Detach genau einmal;
- sofortige Mount-Aufrufe während `attach()`;
- vollständiges Unmount bei Attach-Fehler und bei normalem Schließen;
- gleiche beziehungsweise ausgetauschte Peripheral-Instanz bei unveränderter Adresse;
- Graphrevision ohne Topologieänderung;
- Attach-/Detach-Ereignisse und stabile Attachment-Namen;
- Wired-Modem-Peripherals und TeutonLib-Peripheral-Links;
- Pultteilung, Pultvereinigung, Chunk-Unload und ausgeschalteter Computer.

### Abnahme

- TeutonLib enthält keine Imports, Deskriptoren oder Mixins auf die bisherigen
  CC-Aeroworks-Peripheralklassen.
- `peripherals.find`, `findAll`, `wrap`, `getTree`, `getTypes`, `getNetwork` und `refresh` liefern
  weiterhin dieselben fachlichen Ergebnisse.
- Der `jdeps`-Bericht enthält für dieses Subsystem keine Rückreferenz mehr.

## Phase 2: Clientseitige Workspace-Integration zentralisieren

### TeutonLib-API

- Einen generischen clientseitigen Workspace-Descriptor registrierbar machen.
- Die Workspace-Auswahl darf nicht über einen hart codierten Check auf
  `cc_aeroworks:desk_id` erfolgen.
- Bevorzugt transportiert der geöffnete Computer einen von TeutonLib definierten Workspace-Schlüssel
  oder eine äquivalente registrierbare Kennung im Display-Stack beziehungsweise Menüvertrag.
- Der Descriptor stellt bereit:
  - Workspace-ID;
  - sichtbare Seiten und Tabs;
  - optionale zusätzliche Tabs;
  - Client-Action-Handler;
  - optionalen Rücksprung in einen Hostscreen.
- TeutonLib übernimmt einheitlich Initialisierung, Resize, Fokus, Polling, Terminal-Sichtbarkeit,
  Escape, Inventartaste, Seitenwechsel und Sitzungswechsel.

### Migration in CC-Aeroworks

- Die Funktion von `AbstractComputerScreenSwitchMixin` über den Descriptor registrieren.
- `AbstractComputerScreenAccessor` und den globalen CC-Screen-Mixin aus CC-Aeroworks entfernen,
  sobald TeutonLib die Funktion vollständig übernimmt.
- Den Reiter „Steuerung“ als zusätzliche registrierte Aktion anbinden.
- Den Clienthandler für „Ziele…“ als Workspace-Action registrieren, ohne dass TeutonLib den
  konkreten Aeroworks-Editor kennen muss.
- `DeskComputerWorkspace` als serverseitigen Adapter behalten.

### Tests

- normale und erweiterte Computer;
- GUI-Skalierung 2 und 3 sowie Resize;
- genau eine Reiterleiste;
- Terminal, Quellen, Kanäle und Steuerungsrückkehr;
- Escape, Inventartaste und Textfokus;
- veraltete Antwort, falsche Menü-ID, falsche Sitzung und Seitenwechsel;
- zwei Spieler an getrennten Menüs.

### Abnahme

- CC-Aeroworks mischt nicht mehr direkt in `AbstractComputerScreen` ein.
- TeutonLib enthält keinen CC-Aeroworks-spezifischen Datenkomponenten- oder Mod-ID-Check.
- Das Verhalten aus `docs/teutonlib-computer-ui.md` bleibt erhalten.

## Phase 3: Kanal-, Gruppen- und DbW-Grundlage vereinheitlichen

### 3A: Generische persistente Kanalgruppen

- In TeutonLib eine generische Bank für Gruppen, Aliase und stabile Ziel-IDs einführen.
- Grenzen, Namensvalidierung und Änderungs-Callback werden konfigurierbar.
- Fehlende Ziele bleiben als Bindung erhalten und werden nur als nicht verfügbar projiziert.
- Serialisierung und Deserialisierung erhalten UUIDs und Ziel-IDs unverändert.
- `ChannelGroupBank` in CC-Aeroworks durch diese Implementierung ersetzen.

### 3B: Benannte Signalausgänge

- Die bestehende TeutonLib-`ChannelBank` nicht unkontrolliert brechen. Entweder kompatibel
  erweitern oder eine klar benannte zusätzliche `SignalBank` einführen.
- Erforderliche Fähigkeiten:
  - stabile ID plus Anzeigename;
  - Wertebereich;
  - Erstellen, Umbenennen und Löschen;
  - Pulse auf Server-Ticks;
  - Aktivierungs-/Authority-Prädikat;
  - Fail-safe-Reset;
  - Backend-Lifecycle;
  - Snapshot als `ChannelView`.
- Vorhandene `WIRE_CHANNELS`- und `CHANNEL_GROUPS`-Itemkomponenten zunächst beibehalten. Ein
  CC-Aeroworks-Codec lädt das Altformat in die neue TeutonLib-Struktur und schreibt während der
  Kompatibilitätsphase dasselbe Format zurück.

### 3C: Drive By Wire

- `WireBackend` und den generischen DbW-Zugriff in TeutonLib zusammenführen.
- Verbindungsliste, Erstellen, Entfernen, Umbenennen und Signalbereinigung in genau einer
  TeutonLib-Integration implementieren.
- Den generischen Ziel-Editor und seine Ergebnisübertragung nach TeutonLib verschieben.
- Die Autorisierung bleibt delegiert: CC-Aeroworks validiert aktives Pultnetz, Reichweite,
  Pultbelegung und die Abbildung von kanonischer Kanal-ID auf physische Quelle.
- `NativeDriveByWireChannels`, `ControlDirectionalSignals`, `ControlChannelSemantics` und
  `ControlOverrideManager` bleiben in CC-Aeroworks.

### Tests

- Roundtrip aller bestehenden Kanal- und Gruppenfixtures;
- unveränderte UUIDs und kanonische IDs nach Neustart, Itemtransfer und Pultteilung;
- doppelte Namen, Limits, ungültige Namen und fehlende Ziele;
- Pulsende, Computer-Aus, ungültiges Netzwerk und Chunk-Unload setzen Signale auf null;
- Umbenennen migriert physische DbW-Verbindungen verlustfrei;
- Betrieb ohne Drive By Wire lädt keine optionalen Klassen;
- normale und Sable-Sublevel-Ziele mit vollständiger Serverautorisierung.

### Abnahme

- Nur TeutonLib besitzt die allgemeine Kanalbank, Gruppenbank und DbW-Verbindungsmechanik.
- CC-Aeroworks besitzt weiterhin die Aeroworks-Kanalentdeckung und Override-Regeln.
- Lua- und gespeicherte Daten bleiben kompatibel.

## Phase 4: Generischen Kernel für eingebettete Computer extrahieren

Diese Phase folgt erst nach Peripheral- und Workspace-Migration, damit der neue Host nicht wieder
beide Mechanismen dupliziert.

### TeutonLib-Komponente

Eine Komponente wie `HostedComputer` kapselt:

- Computer-ID, Familie, Label, Terminalgröße, Storage-Kapazität und Powerzustand;
- Erzeugung, Registrierung, `keepAlive`, Start, Shutdown und Schließen des `ServerComputer`;
- sicheren Item-Komponenten-Roundtrip;
- Öffnen eines Standard-CC-Computermenüs mit konfigurierbarer Nutzbarkeitsprüfung;
- gepufferte Ereignisse während des asynchronen Starts;
- konfigurierbare Redstone-/Bundled-Redstone- und Peripheral-Seitenabbildung;
- Addon-Komponenten und Lifecycle-Hooks.

Die API wird als CC:Tweaked-Integration und nicht als allgemeine Minecraft-Hilfsklasse geführt, da
sie bewusst CC-interne Kompatibilitätsdetails zentralisiert.

### CC-Aeroworks-Adapter

- `ConsoleComputer` auf Komposition mit `HostedComputer` umstellen.
- Im Adapter bleiben Desk-ID, Pultnetzbesitz, Events, Radarziel, Wire-/Gruppenbesitz und
  Control-Override-Ticks.
- `ConsoleComputerLifecycle`, `ConsoleComputers` und `ComputerTransferSelection` bleiben im Mod.
- Alte Computerpulte und Itemkomponenten weiterhin migrieren.

### Tests

- neue und bestehende Computer-ID;
- normales und Advanced-Item;
- Label, Dateien, Terminalgröße und Kapazität nach Item- und Block-Roundtrip;
- Startzustand, Eventpuffer und vollständiges Schließen;
- Menü-Nutzbarkeit und Entfernung;
- Redstone-Seitenabbildung;
- letzter Pultabbau, Teilung, Vereinigung und Konfliktauflösung.

### Abnahme

- `ConsoleComputer` enthält nur noch Aeroworks-Orchestrierung.
- Der Hosted-Computer-Kernel kann in einem TeutonLib-Testhost ohne Aeroworks erzeugt werden.

## Phase 5: Sable-, Simulated- und Netzwerkhilfen übernehmen

### Sable

- Die weltbezogene CC:Tweaked-Funk- und GPS-Geometrie nach dem separaten Plan
  [`teutonlib-sable-global-gps-codex-auftrag.md`](teutonlib-sable-global-gps-codex-auftrag.md)
  in TeutonLib zentralisieren. Globale GPS-Payloads und Trilateration bleiben bei CC:Tweaked;
  TeutonLib korrigiert Weltprojektion, Funkdistanz und höhenabhängige Modemreichweite.
- `SableInteractionGeometry` als TeutonLib-Helfer für Sublevel-sichere Reichweiten- und
  `mayInteract`-Prüfungen übernehmen.
- Die allgemeine Renderpose-Projektion aus `SableClientRenderPose` übernehmen.
- Den Aeroworks-Backport-Mixin für `SubLevelPoseClient` nicht zu einer dauerhaften TeutonLib-API
  machen. Die Korrektur gehört in die unterstützte Aeroworks-Version oder bleibt bis dahin als eng
  begrenzter Kompatibilitätspatch im Mod.

### Simulated

- Reflektiven Zugriff auf Docking-Connector-Zustand in der bestehenden TeutonLib-Simulated-
  Integration zusammenführen.
- CC-Aeroworks behält Dock-Suche, Alias, Telemetrie-Endpunkte und Lua-Projektion.

### Netzwerkprimitiven

- `PlayerSessionState`, `PlayerTickBudget` und `SingleGestureSessionState` als interne,
  domänenfreie TeutonLib-Helfer übernehmen.
- Namen neutralisieren, Eingaben validieren und die bestehenden Unit-Tests mitverschieben.
- Display-, Pointer- und Draw-Payloads bleiben in CC-Aeroworks.

### Abnahme

- CC-Aeroworks besitzt keinen zweiten allgemeinen Sable-Koordinatenhelfer oder reflektiven
  Simulated-Accessor.
- Touch-/Draw-Schutz, TTL und Rate Limits verhalten sich unverändert.

## Phase 6: Optionale gemeinsame UI- und Lua-Werkzeuge

Diese Phase wird nur umgesetzt, wenn TeutonLib mindestens einen zweiten realen Nutzer dafür hat
oder das Werkzeug Bestandteil des stabilen Computer-UI-Vertrags werden soll.

- `SourceSelectorWidget` als generisches, scrollbares Auswahlwidget mit injizierbaren Sprites
  und ohne CC-Aeroworks-Ressourcen-ID anbieten.
- Popup-/Tooltip-Overlay über eine TeutonLib-Screen-Erweiterung behandeln, nicht über einen
  zusätzlichen globalen CC-Aeroworks-Mixin.
- Aus `DisplayScriptCatalog` nur Mount-Walk, Größen-/Tiefenlimits, sichere Pfadnormalisierung und
  den kleinen Lua-`require`-Lexer abstrahieren.
- Displayrollen, `touchdisplay`, Touch-Callback-Namen und Bindungsprüfung bleiben im Mod und werden
  als Analyseprofil an den generischen Scanner übergeben.

## Phase 7: Kompatibilitätscode und Rückabhängigkeit entfernen

### TeutonLib

- Alle `peripheral.console.*`- und `computer.console.*`-Mixins entfernen, die konkrete
  CC-Aeroworks-Klassen adressieren.
- `ConsoleLinkDevices` und `ConsoleLinkSupport` durch providerbasierte Implementierungen ersetzen.
- Die optionale TeutonLib-Abhängigkeit auf `cc_aeroworks` aus den Metadaten entfernen, sofern keine
  weitere echte Integration übrig ist.
- Keine Reflection auf CC-Aeroworks-Klassennamen als Ersatz einführen.

### CC-Aeroworks

- Ersetzte Klassen, Mixins und Accessors erst löschen, wenn beide Projekte mit der neuen API bauen.
- Die alten `WireChannelPayloads` und `InformationSourcePayloads` nicht nach TeutonLib verschieben.
  Sie bleiben für die ausdrücklich dokumentierte Kompatibilitätsfrist bestehen und werden danach in
  einem eigenen, klar benannten Breaking-Cleanup entfernt.
- Vor dem Entfernen prüfen, ob externe Addons die Payload-IDs tatsächlich verwenden. Fehlende
  interne Leser allein sind kein ausreichender Nachweis für externe Unbenutztheit.
- Dokumentation, Abhängigkeitsmanifest, Versionsbereich und Changelog beider Projekte aktualisieren.

## Durchgängige Verifikation

Nach jedem Arbeitspaket:

1. TeutonLib Unit-Tests und vollständiger Build;
2. CC-Aeroworks Unit-Tests und vollständiger Build;
3. Repository- und API-Referenzprüfungen;
4. Dedicated-Server-Smoke-Test ohne clientseitige Klassenladefehler;
5. Lauf mit und ohne Drive By Wire, Create: Radars und Simulated;
6. statischer `jdeps`-Check beider erzeugter JARs;
7. Prüfung auf verbotene Strings und Imports:
   - TeutonLib: kein `de.teutonstudio.ccaeroworks` und kein `cc_aeroworks:desk_id`;
   - CC-Aeroworks: keine Zugriffe auf nicht freigegebene TeutonLib-Implementierungsdetails;
8. gezielte Ingame-Matrix aus `docs/teutonlib-computer-ui.md`;
9. Neustart-, Chunk-, Item-, Pultteilungs- und Zwei-Spieler-Prüfungen.

## Commit- und Release-Reihenfolge

Die Migration wird in reviewbaren Paketen durchgeführt:

1. TeutonLib: neue Provider-APIs und Tests, alte Integration vorübergehend weiter vorhanden;
2. CC-Aeroworks: Peripheral-Adapter auf die neue API umstellen;
3. TeutonLib: alte Console-Mixins und Rückreferenzen entfernen;
4. TeutonLib und CC-Aeroworks: Workspace-Descriptor und Clientmigration;
5. TeutonLib und CC-Aeroworks: Gruppen-/Kanalbank und DbW;
6. TeutonLib und CC-Aeroworks: Hosted Computer;
7. TeutonLib und CC-Aeroworks: Sable/Simulated/Netzwerkhilfen;
8. getrennte Cleanup-Commits für veraltete Klassen, Ressourcen, Tests und Dokumentation.

Jeder Commit benennt das betroffene API-Verhalten und enthält die dazugehörigen Tests. Ein
TeutonLib-Commit, der eine alte Brücke entfernt, darf erst landen, wenn der korrespondierende
CC-Aeroworks-Commit verfügbar ist. Die veröffentlichten Versionen erhalten gegenseitig passende
Versionsbereiche.

## Definition of Done

Die Gesamtmigration ist abgeschlossen, wenn:

- TeutonLib keine Compile-, Bytecode-, Mixin-, Reflection- oder Metadatenabhängigkeit auf
  CC-Aeroworks besitzt;
- CC-Aeroworks ausschließlich öffentliche TeutonLib-APIs verwendet;
- Peripheral-Lifecycle, Workspace-Client, persistente Kanalgruppen, allgemeine Signalausgänge,
  DbW-Zugriff und Hosted-Computer-Kernel jeweils genau einen Eigentümer haben;
- bestehende Welten und Computeritems ohne Verlust von IDs, Dateien, Kanälen oder Gruppen laden;
- die dokumentierten Lua-APIs und Ereignisse kompatibel bleiben;
- beide Builds, statischen Architekturprüfungen, Unit-Tests, Server-Smoke-Tests und die relevante
  manuelle Clientmatrix erfolgreich sind;
- die neue Zuständigkeitsgrenze in beiden Projekten dokumentiert ist.
