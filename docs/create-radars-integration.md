# Create: Radars integration

Die Integration ist optional und wird nur aktiv, wenn Create: Radars mit der Mod-ID `create_radar` geladen ist. Ohne Create: Radars werden die RadarDisplays aus Kreativsuche und Aeroworks-Abschnitt entfernt; ihre Rezepte laden nur unter der NeoForge-Bedingung `neoforge:mod_loaded`.

## Abhängigkeiten

Die unterstützte Entwicklungskette für Minecraft 1.21.1 besteht aus:

- Create: Radars `0.4.9.4-1.21.1`, Mod-ID `create_radar`.
- Create Big Cannons `5.11.7`, Mod-ID `createbigcannons`.
- Ritchie's Projectile Library `2.1.2`, Mod-ID `ritchiesprojectilelib`.

CC-Aeroworks verwendet keine Create:-Radars-Klassen in normalen Signaturen. Ein optionaler Mixin hängt ausschließlich am öffentlichen Tick des Network Controllers; Data-Link-Gegenstand und Data-Link-BlockEntity werden nicht verändert.

## Zweck der RadarDisplays

Die kleine und große Radaranzeige ersetzen den Create:-Radars-Monitor am Steuerungspult. Es gibt keinen zusätzlichen Monitor, keinen am Pult platzierten Data-Link-Block und keinen Data-Link-Klick auf das Pult.

- `cc_aeroworks:small_radar_display` passt in kleine und große Aeroworks-Sockets.
- `cc_aeroworks:large_radar_display` passt ausschließlich in große Aeroworks-Sockets.
- Mehrere RadarDisplays im selben gültigen Pultnetz zeigen dieselbe Radarquelle.
- RadarDisplays verwenden keine konfigurierte Pixelmatrix. Sie rendern eine kontinuierliche Radaroberfläche direkt auf dem Modul.

## Herstellung

Die Radarvarianten entstehen weiterhin mit einem Create-Einsatzgerät:

- `cc_aeroworks:two_digit_display` plus `create_radar:monitor` ergibt `cc_aeroworks:small_radar_display`.
- `cc_aeroworks:three_digit_display` plus `create_radar:monitor` ergibt `cc_aeroworks:large_radar_display`.

Der Monitor wird nur als Herstellungskomponente verbraucht. In der späteren Anlage ist kein Monitorblock erforderlich.

## Aufbau

Create: Radars verwaltet weiterhin sein eigenes Radarnetz:

1. Einen Network Controller mit dem nativen Create:-Radars-Data-Link-Gegenstand auswählen.
2. Danach den gewünschten Radar anklicken.
3. Den Network Controller direkt an eine Seite eines Aeroworks-Steuerungspults setzen.
4. Mindestens ein RadarDisplay in dieses Pult oder ein verbundenes Pult einsetzen.

Damit ist der Aufbau vollständig. CC-Aeroworks erkennt den direkt angrenzenden Network Controller automatisch. Der Data-Link-Gegenstand wird niemals auf dem Pult verwendet.

## Automatische Controller-Erkennung

Der ohnehin alle fünf Ticks laufende Network Controller prüft seine sechs direkten Nachbarpositionen auf Aeroworks-Pulte. Für jedes gefundene Pult wird der vollständige Pultverbund aufgelöst und anschließend geprüft, wie viele Network Controller direkt an irgendein Pult dieses Netzes grenzen.

- **Kein angrenzender Controller:** Alle RadarDisplays zeigen den getrennten Zustand `X`.
- **Genau ein angrenzender Controller:** Dessen verbundener Radar wird dargestellt.
- **Mehrere angrenzende Controller:** Die Quelle ist mehrdeutig; alle RadarDisplays zeigen `X`.

Ein Controller darf an irgendeinem Pult des Netzes liegen. Mehrere Pulte, die an denselben Controller grenzen, erzeugen wegen der Positions-Deduplizierung trotzdem nur eine Quelle.

Für die Radarweiterleitung sind zulässig:

- ein einzelnes Pult ohne eingebetteten Computer,
- ein Pultnetz ohne eingebetteten Computer,
- ein Pultnetz mit genau einem eingebetteten Computer.

Mehrere eingebettete Computer, teilweise geladene Netze und Netze mit mehr als 64 Pulten bleiben gesperrt. Die Position eines vorhandenen Computer-Steuerungspults beeinflusst die Radarquelle nicht.

## Direkter Radar-Snapshot

Der Adapter liest am erkannten Network Controller dessen bereits verbundenen Radar. Verwendet werden:

- Betriebszustand,
- Weltposition des Radars,
- Reichweite,
- ausgewählter Track des Controllers,
- höchstens die **256** nächstgelegenen Radartracks mit Position, Geschwindigkeit und Create:-Radars-Spritekategorie.

Es gibt weder einen Data-Link-Item-Mixin noch einen `DataLinkBlockEntity`-Mixin. Der einzige optionale Create:-Radars-Mixin hängt am öffentlichen statischen `NetworkFiltererBlockEntity.tick(...)`.

Der Snapshot wird nur für Client-Updates übertragen und nicht als fortlaufende Trackhistorie im Weltstand gespeichert. Es wird auch keine Controllerposition am Pult persistiert. Bei einem entfernten Radar sendet der Controller spätestens nach **5 Ticks** einen getrennten Snapshot. Wird der Controller selbst entfernt, bleibt kein Tickgeber zurück; der Renderer behandelt den letzten Snapshot deshalb spätestens nach **20 Ticks** als veraltet.

## Direkte Monitoroberfläche

RadarDisplays werden nicht mehr durch `RadarDisplayRaster` in boolesche Pultpixel umgerechnet. Der klassische BlockEntity-Renderer und der Flywheel-Visual verwenden stattdessen dieselbe Liste flacher Oberflächenmodelle.

Die Oberfläche referenziert direkt die vorhandenen Create:-Radars-Monitorressourcen:

- Hintergrundfüllung,
- Radarkreis,
- rotierender Sweep,
- separate Symbole für Spieler, Projektile, normale Entitäten sowie Contraptions oder Sable-Schiffe,
- Zielmarkierung für den ausgewählten Track.

Die Trackpositionen werden kontinuierlich auf die kleine beziehungsweise große Modulfläche projiziert. Die Pixelauflösung der programmierbaren Zwei- und Dreisteller beeinflusst RadarDisplays nicht mehr.

## Ponder-Erklärungen

Die Radaritems besitzen zwei lokalisierte Storyboards:

1. **Network Controller am Pult** zeigt den physischen Aufbau und die native Verbindung `Controller → Radar`.
2. **Radar direkt darstellen** erklärt die automatische Nachbarschaftssuche, mehrere Displays und den getrennten Zustand.

## Entwicklungsclient

`./gradlew runClient` löst Create: Radars, Create Big Cannons und Ritchie's Projectile Library automatisch über CurseMaven als `localRuntime` auf. Die Zielversionen und CurseForge-Datei-IDs stehen in `gradle.properties`.

Lokal in `libs/` liegende offizielle JARs dieser Mods werden aus dem allgemeinen Datei-Classpath ausgeschlossen, damit nicht zwei Kopien mit derselben Mod-ID geladen werden. Fremdmods werden nicht in das CC-Aeroworks-JAR eingebettet.

## Manuelle Prüfung

- Start ohne Create: Radars: keine Radaritems, Radarrezepte oder optionalen Mixinfehler.
- Network Controller mit dem Data Link auswählen und danach Radar anklicken: natives Create:-Radars-Verhalten bleibt unverändert.
- Network Controller direkt an ein Pult stellen: RadarDisplay zeigt Hintergrund, Kreis und Sweep ohne weiteren Klick.
- Einen Spieler, ein Projektil und eine normale Entität im Radarbereich erzeugen: die passenden Create:-Radars-Symbole erscheinen kontinuierlich auf der Modulfläche.
- Ein Ziel am Network Controller auswählen: die Zielmarkierung erscheint über dem zugehörigen Track.
- Controller an Vorder-, Rück-, Ober-, Unter- und freie Seitennachbarn setzen: jede direkte Nachbarschaft wird erkannt.
- Controller nur diagonal oder mit einem Luftblock Abstand platzieren: Anzeige bleibt `X`.
- Ein Controller grenzt an zwei Pulte desselben Netzes: Quelle wird nur einmal gezählt.
- Zwei verschiedene Controller grenzen an das Pultnetz: Anzeige bleibt wegen Mehrdeutigkeit `X`.
- Ein oder mehrere RadarDisplays im Netz: alle zeigen dieselbe Quelle.
- Radar entfernen: spätestens nach fünf Ticks erscheint `X`; Controller entfernen: spätestens nach 20 Ticks.
- Zwei Computer-Steuerungspulte, teilweise geladenes oder überlanges Netz: Anzeige wird getrennt.
- Rechtsklick mit Data Link auf ein Pult: ausschließlich natives Create:-Radars-Verhalten; CC-Aeroworks greift nicht ein.
- Fallback-Renderer und Flywheel-Renderer getrennt prüfen.
