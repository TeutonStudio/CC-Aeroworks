# Create: Radars integration

Die Integration ist optional und wird nur aktiv, wenn Create: Radars mit der Mod-ID `create_radar` geladen ist. CC-Aeroworks registriert die Item-IDs immer stabil. Ohne Create: Radars werden die Radar-Displays sowohl aus dem Aeroworks-Creative-Tab als auch aus der globalen Kreativsuche entfernt; ihre Rezepte laden nur unter der NeoForge-Bedingung `neoforge:mod_loaded`. Mit geladener Abhängigkeit erscheinen beide Displays im Abschnitt `Aeroworks` des Aeroworks-Tabs, behalten aber ihre IDs im Namespace `cc_aeroworks`.

## Abhängigkeiten

Die unterstützte Entwicklungskette für Minecraft 1.21.1 besteht aus:

- Create: Radars `0.4.4-1.21.1`, Mod-ID `create_radar`.
- Create Big Cannons `5.11.7`, Mod-ID `createbigcannons`, als optionale CC-Aeroworks-Abhängigkeit und Laufzeitvoraussetzung von Create: Radars.
- Ritchie's Projectile Library `2.1.2`, Mod-ID `ritchiesprojectilelib`, als erforderliche Laufzeitbibliothek von Create Big Cannons.

CC-Aeroworks verwendet keine CBC- oder RPL-Klassen direkt. Ohne Create: Radars bleibt die Radar-Integration deaktiviert; die zusätzlichen Mods sind für die übrigen CC-Aeroworks-Funktionen nicht erforderlich.

## Displays

- `cc_aeroworks:small_radar_display` passt in kleine und große Aeroworks-Sockets.
- `cc_aeroworks:large_radar_display` passt ausschließlich in große Aeroworks-Sockets.
- Beide verwenden die konfigurierte kleine beziehungsweise große Pixelauflösung.

## Herstellung

Keines der vier Displayitems besitzt ein Werkbankrezept. Die normalen programmierbaren Displays werden weiterhin über ihre vorhandenen mechanischen Pressrezepte aus einem normalen beziehungsweise erweiterten CC:Tweaked-Monitor hergestellt.

Die Radarvarianten entstehen ausschließlich mit einem Create-Einsatzgerät:

- `cc_aeroworks:two_digit_display` auf dem Depot oder Förderband und `create_radar:monitor` im Einsatzgerät ergeben `cc_aeroworks:small_radar_display`.
- `cc_aeroworks:three_digit_display` auf dem Depot oder Förderband und `create_radar:monitor` im Einsatzgerät ergeben `cc_aeroworks:large_radar_display`.

Der Create:-Radars-Monitor wird dabei verbraucht. Beide Rezepte sind `create:deploying`-Rezepte und werden nur geladen, wenn `create_radar` vorhanden ist. JEI zeigt entsprechend keine Werkbankrezepte für die Displays, sondern die mechanische Herstellung und die beiden Einsatzgerät-Umwandlungen.

## Itemdarstellung

Die vier Inventarmodelle verwenden eigenständige Flachbildschirmgehäuse statt der dünnen Einbaugeometrie der Pultmodule. Kleine und große Varianten unterscheiden sich durch die Gehäusebreite. Normale Displays besitzen eine dunkle Bildschirmfläche, Radarvarianten eine grüne Bildschirmfläche. Die im Pult montierten Modulmodelle bleiben unverändert.

## Data Link verbinden

Die normale Zwei-Klick-Auswahl von Create: Radars akzeptiert nur die von der Fremdmod vorgesehenen Kanonen- und Netzwerkendpunkte. Ein Aeroworks-Steuerungspult ist dort weder als `DataPeripheral` noch als zulässiges Ziel registriert. Deshalb stellt CC-Aeroworks für Pulte mit eingebauter Radaranzeige einen eigenen, eng begrenzten Verbindungsablauf bereit:

1. Eine kleine oder große Radaranzeige in das Steuerungspult einsetzen.
2. Mit dem Create:-Radars-Data-Link-Gegenstand den gewünschten Radar-Monitor rechtsklicken. Bei einem Mehrblockmonitor wird dessen Controller gespeichert.
3. Mit demselben Gegenstand eine freie Seite des Steuerungspults rechtsklicken.
4. CC-Aeroworks platziert den Data Link an dieser Seite, richtet seine Quellseite auf das Pult und setzt den gewählten Monitorcontroller als Ziel.

Schleichen und Rechtsklick mit dem Data-Link-Gegenstand löscht eine begonnene Monitorauswahl. Die übrigen Create:-Radars-Verbindungsarten werden nicht verändert und weiterhin vollständig von Create: Radars verarbeitet.

Der Data Link bleibt eine echte Laufzeitbedingung. CC-Aeroworks sucht keine Radarblöcke in der Welt und übernimmt keine Tracks ohne einen passend platzierten und konfigurierten Data Link. Ein `X` auf dem Display bedeutet, dass keine aktuelle Verbindung verfügbar ist.

## Synchronisierung

Ein optionaler Item-Mixin fängt ausschließlich die Kombination aus Create:-Radars-Data-Link, ausgewähltem Monitor und Aeroworks-Pult mit Radaranzeige ab. Die Platzierung verwendet weiterhin den originalen Data-Link-Block; anschließend wird dessen öffentliches `target(BlockPos)` auf den Monitorcontroller gesetzt.

Der bestehende BlockEntity-Mixin beobachtet danach die Rückkehrpunkte von `DataLinkBlockEntity.updateGatheredData`. Alle fünf Spielticks wird ein flüchtiger Snapshot an das direkt an der Quellseite liegende Pult übertragen. Er enthält Radarzentrum, Reichweite, ausgewählte Track-ID und höchstens die 256 nächstgelegenen Tracks. Die Daten werden nur in Client-Updatepakete geschrieben und nicht im Weltstand gespeichert. Nach 20 Ticks ohne Aktualisierung gilt der Snapshot als veraltet.

Die Integration referenziert keine Create:-Radars-Klasse in normalen Methodensignaturen. Ohne die optionale Mod werden beide `@Pseudo`-Mixins übersprungen und der Reflexionsadapter nie aufgerufen.

## Entwicklungsclient

`./gradlew runClient` löst Create: Radars, Create Big Cannons und Ritchie's Projectile Library automatisch über CurseMaven als `localRuntime` auf. Die Zielversionen und zugehörigen CurseForge-Datei-IDs stehen in `gradle.properties`.

Lokal in `libs/` liegende offizielle JARs dieser drei Mods werden aus dem allgemeinen Datei-Classpath ausgeschlossen, damit nicht zwei Kopien mit derselben Mod-ID geladen werden.

Die veröffentlichte CC-Aeroworks-Mod bleibt davon unberührt: Die Fremdmods werden nicht in das eigene JAR eingebettet. Create: Radars und Create Big Cannons bleiben in den NeoForge-Metadaten optional; RPL ist die Laufzeitbibliothek von CBC und keine direkte CC-Aeroworks-Abhängigkeit.

## Manuelle Prüfung

- JEI: keine Werkbankrezepte für die vier Displayitems.
- JEI: normales und erweitertes Display zeigen nur ihre mechanische Pressherstellung.
- JEI: kleines Radar-Display zeigt `two_digit_display` plus `create_radar:monitor` als Einsatzgerät-Rezept.
- JEI: großes Radar-Display zeigt `three_digit_display` plus `create_radar:monitor` als Einsatzgerät-Rezept.
- Inventar und JEI: alle vier Items sind als flache Bildschirme erkennbar; große Varianten sind sichtbar breiter.
- Start ohne Create: Radars: keine Radar-Items im Aeroworks-Tab oder in der Kreativsuche, keine Radarrezepte, kein Mixinfehler.
- Data Link auf einen verbundenen Radar-Monitor rechtsklicken: Auswahlmeldung erscheint, kein Data Link wird am Monitor platziert.
- Danach eine freie Pultseite rechtsklicken: Data Link wird an der Seite platziert und auf den Monitorcontroller gesetzt.
- Pult ohne Radaranzeige rechtsklicken: CC-Aeroworks greift nicht ein; Create: Radars behält sein ursprüngliches Verhalten.
- Schleichen und Rechtsklick nach einer Monitorauswahl: Auswahl wird gelöscht.
- Monitor aus einer anderen Dimension oder ungültiger Monitor: Auswahl wird verworfen und kein Link erzeugt.
- Display ohne Data Link: `X`.
- Data Link auf unverbundenen Monitor: `X`.
- Aktiver Monitor: Zentrum und Kontakte sichtbar; ausgewählter Kontakt als Kreuz markiert.
- Data Link entfernen: spätestens nach 20 Ticks wieder `X`.
- Fallback-Renderer und Flywheel-Renderer getrennt prüfen.
