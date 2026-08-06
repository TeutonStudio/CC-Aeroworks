# Create: Radars integration

Die Integration ist optional und wird nur aktiv, wenn Create: Radars mit der Mod-ID `create_radar` geladen ist. CC-Aeroworks registriert die Item-IDs stabil. Ohne Create: Radars werden die Radar-Displays aus dem Aeroworks-Creative-Tab und aus der globalen Kreativsuche entfernt; ihre Rezepte laden nur unter der NeoForge-Bedingung `neoforge:mod_loaded`.

## Abhängigkeiten

Die unterstützte Entwicklungskette für Minecraft 1.21.1 besteht aus:

- Create: Radars `0.4.4-1.21.1`, Mod-ID `create_radar`.
- Create Big Cannons `5.11.7`, Mod-ID `createbigcannons`.
- Ritchie's Projectile Library `2.1.2`, Mod-ID `ritchiesprojectilelib`.

CC-Aeroworks verwendet keine CBC- oder RPL-Klassen direkt. Ohne Create: Radars bleibt die Radar-Integration deaktiviert; die übrigen Funktionen benötigen diese Mods nicht.

## Displays

- `cc_aeroworks:small_radar_display` passt in kleine und große Aeroworks-Sockets.
- `cc_aeroworks:large_radar_display` passt ausschließlich in große Aeroworks-Sockets.
- Beide verwenden die konfigurierte kleine beziehungsweise große Pixelauflösung.

## Herstellung

Die normalen programmierbaren Displays entstehen unter einer mechanischen Presse aus einem normalen beziehungsweise erweiterten CC:Tweaked-Monitor.

Die Radarvarianten entstehen mit einem Create-Einsatzgerät:

- `cc_aeroworks:two_digit_display` plus `create_radar:monitor` ergibt `cc_aeroworks:small_radar_display`.
- `cc_aeroworks:three_digit_display` plus `create_radar:monitor` ergibt `cc_aeroworks:large_radar_display`.

Der Create:-Radars-Monitor wird verbraucht. Beide Rezepte sind `create:deploying`-Rezepte und werden nur geladen, wenn `create_radar` vorhanden ist.

## Automatisches Routing im Pultnetz

Radarquelle und Radaranzeige dürfen an **verschiedenen Pulten** desselben linearen Pultnetzes liegen. Der Data Link liefert den Radar-Snapshot an das Pult, an dessen Seite er montiert ist. CC-Aeroworks löst anschließend den vollständigen Pultverbund auf und routet die Daten automatisch zu dessen Radaranzeige.

Für reine Radarweiterleitung ist **kein eingebetteter Computer** erforderlich. Ein Netzwerk ohne Computer und ein Netzwerk mit genau einem Computer sind beide zulässig. Mehrere eingebettete Computer bilden weiterhin einen Konflikt und werden abgelehnt. Teilweise geladene Netzwerke und Netzwerke mit mehr als 64 Pulten bleiben ebenfalls gesperrt.

Für die automatische Route muss im vollständig geladenen Netzwerk **genau eine** kleine oder große Radaranzeige vorhanden sein:

- Keine Radaranzeige: Die Platzierung wird mit einer verständlichen Meldung abgewiesen.
- Genau eine Radaranzeige: Quelle und Ziel werden automatisch verbunden.
- Mehrere Radaranzeigen: Die Route ist mehrdeutig und wird nicht zufällig gewählt.

Dafür ist kein Lua-Programm erforderlich. Falls ein Computer-Steuerungspult vorhanden ist, beeinflusst dessen Position innerhalb der Pultreihe die Route nicht.

## Zwei getrennte Data-Link-Modi

CC-Aeroworks ergänzt Create: Radars, ersetzt dessen vorhandene Verbindungsabläufe aber nicht.

### Native Create:-Radars-Verbindungen

Hat der Data-Link-Gegenstand bereits eine native Auswahl von Create: Radars, greift CC-Aeroworks nicht ein. Dazu gehören insbesondere:

- Network Controller → Monitor,
- Network Controller → Radar,
- Waffenhalterung → Yaw-, Pitch- oder Feuercontroller.

Die nativen Auswahl-Tags wie `SelectedFiltererPos` und `SelectedMountPos` werden vor jeder Monitorbehandlung geprüft. Ein Monitor-Klick mit einer solchen Auswahl wird vollständig an den originalen `DataLinkBlockItem` von Create: Radars weitergereicht.

### CC-Aeroworks-Radarpult

Der zusätzliche Monitor-zuerst-Ablauf beginnt ausschließlich mit einem Data-Link-Gegenstand ohne native Auswahl:

1. Im Ziel-Pultnetz genau eine Radaranzeige montieren.
2. Mit einem ansonsten unbenutzten Data-Link-Gegenstand einen verbundenen Create:-Radars-Monitor rechtsklicken. Bei einem Mehrblockmonitor wird dessen Controller gespeichert.
3. Mit demselben Gegenstand eine freie Seite eines beliebigen Pults im Zielnetz rechtsklicken.
4. CC-Aeroworks platziert dort den originalen Data-Link-Block und setzt den gewählten Monitorcontroller als Ziel.
5. Die erfassten Radardaten werden automatisch zur einzigen Radaranzeige des Pultnetzes weitergeleitet.

Die gewählte Monitorposition wird auf dem konkreten Data-Link-**Itemstack** gespeichert, nicht im dauerhaften Spielerdatensatz. Verschiedene Data-Link-Gegenstände teilen daher keinen unsichtbaren Auswahlzustand. Schleichen und Rechtsklick mit dem ausgewählten Gegenstand löscht die begonnene Monitorauswahl.

Nach der Platzierung prüft CC-Aeroworks sowohl die Quellposition als auch die Zielposition des erzeugten Data Links. Scheitert die Konfiguration, wird der Block entfernt und das verbrauchte Item im Überlebensmodus erstattet.

Der Data Link bleibt eine echte Quellenkomponente. CC-Aeroworks durchsucht nicht eigenmächtig die Welt nach Radarblöcken und erzeugt keine Kontakte ohne eine gültige Quelle.

## Erkennung der Radarmodule

Die schnelle Erkennung verwendet weiterhin die von CC-Aeroworks registrierten `ModuleType`-Objekte. Zusätzlich werden stabile Modulmerkmale ausgewertet:

- Registry-ID `cc_aeroworks:small_radar_display` beziehungsweise `cc_aeroworks:large_radar_display`,
- Übersetzungsschlüssel `item.cc_aeroworks.small_radar_display` beziehungsweise `item.cc_aeroworks.large_radar_display`.

Damit bleibt die Erkennung funktionsfähig, wenn Aeroworks einen logisch identischen Modultyp nach Laden oder Synchronisierung nicht als dieselbe JVM-Objektinstanz liefert.

## Synchronisierung

Ein optionaler Item-Mixin fängt nur die Kombination aus einem unbenutzten Create:-Radars-Data-Link, ausgewähltem Monitor und einem Pultnetz mit eindeutigem Radarziel ab. Die Platzierung verwendet weiterhin den originalen Data-Link-Block; anschließend wird dessen öffentliches `target(BlockPos)` auf den Monitorcontroller gesetzt.

Der BlockEntity-Mixin beobachtet die Rückkehrpunkte von `DataLinkBlockEntity.updateGatheredData`. Alle fünf Spielticks entsteht ein flüchtiger Snapshot mit:

- Radarzentrum,
- Reichweite,
- ausgewählter Track-ID,
- höchstens den **256** nächstgelegenen Tracks.

Der Snapshot wird über den Pultverbund an die einzige Radaranzeige übertragen. Die Daten werden nur in Client-Updatepakete geschrieben und nicht dauerhaft im Weltstand gespeichert. Nach **20 Ticks** ohne Aktualisierung gilt der Snapshot als veraltet und das Display zeigt den getrennten Zustand `X`.

Die Integration referenziert keine Create:-Radars-Klasse in normalen Methodensignaturen. Ohne die optionale Mod werden beide `@Pseudo`-Mixins übersprungen und der Reflexionsadapter nicht aufgerufen.

## Ponder-Erklärungen

Die beiden Radaritems besitzen zwei vollständig lokalisierte Storyboards:

1. **Automatisches Routing** erklärt Quelle und Ziel an verschiedenen Pulten und die Regel für genau eine Radaranzeige.
2. **Data-Link-Kompatibilität** zeigt den Monitor-zuerst-Ablauf, die Platzierung an einem beliebigen Pult und den getrennten Zustand bei veralteten Daten.

## Entwicklungsclient

`./gradlew runClient` löst Create: Radars, Create Big Cannons und Ritchie's Projectile Library automatisch über CurseMaven als `localRuntime` auf. Die Zielversionen und zugehörigen CurseForge-Datei-IDs stehen in `gradle.properties`.

Lokal in `libs/` liegende offizielle JARs dieser Mods werden aus dem allgemeinen Datei-Classpath ausgeschlossen, damit nicht zwei Kopien mit derselben Mod-ID geladen werden. Die veröffentlichten Fremdmods werden nicht in das CC-Aeroworks-JAR eingebettet.

## Manuelle Prüfung

- Start ohne Create: Radars: keine Radaritems in Kreativsuche oder Aeroworks-Abschnitt, keine Radarrezepte und kein Mixinfehler.
- Network Controller mit dem Data Link auswählen, danach einen Monitor anklicken: Der native Create:-Radars-Link wird erzeugt; keine CC-Aeroworks-Monitorauswahl erscheint.
- Network Controller mit Radar oder weiteren nativen Endpunkten verbinden: ursprüngliches Create:-Radars-Verhalten bleibt erhalten.
- Unbenutzten Data Link auf einen verbundenen Radar-Monitor rechtsklicken: CC-Aeroworks-Auswahlmeldung erscheint; am Monitor wird kein Block platziert.
- Danach eine freie Seite eines beliebigen Pults im Zielnetz rechtsklicken: Data Link wird platziert und auf den Monitorcontroller gesetzt.
- Pultnetz ohne eingebetteten Computer und mit genau einer Radaranzeige: Routing funktioniert.
- Pultnetz mit genau einem eingebetteten Computer und genau einer Radaranzeige: Routing funktioniert unabhängig von der Computerposition.
- Quelle und einzige Radaranzeige an verschiedenen Pulten: Kontakte erscheinen automatisch auf der Anzeige.
- Keine Radaranzeige: verständliche Fehlermeldung und keine von CC-Aeroworks übernommene Platzierung.
- Zwei Radaranzeigen: Mehrdeutigkeitsmeldung und keine zufällige Route.
- Zwei Computer-Steuerungspulte: Konfliktmeldung und keine Radarroute.
- Monitor in anderer Dimension oder ungültiger Monitor: Auswahl wird verworfen.
- Zwei verschiedene Data-Link-Itemstacks: Monitorauswahl wird nicht zwischen den Gegenständen geteilt.
- Schleichen und Rechtsklick nach einer Monitorauswahl: Auswahl wird gelöscht.
- Erzwungener Konfigurationsfehler: platzierter Link wird entfernt und das Item erstattet.
- Data Link auf unverbundenen Monitor: Anzeige bleibt `X`.
- Aktiver Monitor: Zentrum und Kontakte sichtbar; ausgewählter Kontakt ist markiert.
- Data Link entfernen: spätestens nach 20 Ticks wieder `X`.
- Fallback-Renderer und Flywheel-Renderer getrennt prüfen.
