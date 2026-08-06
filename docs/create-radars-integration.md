# Create: Radars integration

Die Integration ist optional und wird nur aktiv, wenn Create: Radars mit der Mod-ID `create_radar` geladen ist. Ohne Create: Radars werden die RadarDisplays aus Kreativsuche und Aeroworks-Abschnitt entfernt; ihre Rezepte laden nur unter der NeoForge-Bedingung `neoforge:mod_loaded`.

## Abhängigkeiten

Die unterstützte Entwicklungskette für Minecraft 1.21.1 besteht aus:

- Create: Radars `0.4.9.4-1.21.1`, Mod-ID `create_radar`.
- Create Big Cannons `5.11.7`, Mod-ID `createbigcannons`.
- Ritchie's Projectile Library `2.1.2`, Mod-ID `ritchiesprojectilelib`.

CC-Aeroworks verwendet keine Create:-Radars-Klassen in normalen Signaturen. Die optionale Integration arbeitet über einen `@Pseudo`-Mixin am Data-Link-Gegenstand und einen Reflexionsadapter.

## Zweck der RadarDisplays

Die kleine und große Radaranzeige ersetzen den Create:-Radars-Monitor am Steuerungspult. Es gibt keinen zusätzlichen Monitor, keinen am Pult platzierten Data-Link-Block und keinen Monitor-zuerst-Ablauf.

- `cc_aeroworks:small_radar_display` passt in kleine und große Aeroworks-Sockets.
- `cc_aeroworks:large_radar_display` passt ausschließlich in große Aeroworks-Sockets.
- Mehrere RadarDisplays im selben gültigen Pultnetz zeigen dieselbe Controller-Quelle.
- Die konfigurierte kleine beziehungsweise große Pixelauflösung bleibt maßgeblich.

## Herstellung

Die Radarvarianten entstehen weiterhin mit einem Create-Einsatzgerät:

- `cc_aeroworks:two_digit_display` plus `create_radar:monitor` ergibt `cc_aeroworks:small_radar_display`.
- `cc_aeroworks:three_digit_display` plus `create_radar:monitor` ergibt `cc_aeroworks:large_radar_display`.

Der Monitor wird dabei als Herstellungskomponente verbraucht. In der späteren Anlage ist kein Monitorblock erforderlich.

## Verbindung

Create: Radars verwaltet weiterhin sein eigenes Radarnetz. Der Network Controller muss dort bereits über den nativen Data-Link-Ablauf mit einem Radar verbunden sein.

Danach wird der Controller direkt mit dem Aeroworks-Pult verknüpft:

1. Mindestens ein RadarDisplay in ein Pult des Zielnetzes einsetzen.
2. Mit dem Create:-Radars-Data-Link-Gegenstand den Network Controller rechtsklicken.
3. Mit demselben Gegenstand ein beliebiges Steuerungspult im Zielnetz rechtsklicken.
4. CC-Aeroworks speichert die Position des Network Controllers auf diesem Pult und löscht nur die abgeschlossene `SelectedFiltererPos`-Auswahl vom Gegenstand.
5. Es wird **kein Data-Link-Block** am Pult platziert und kein Gegenstand verbraucht.

Schleichen und Rechtsklick bleibt vollständig beim originalen Data-Link-Gegenstand und bricht dessen Auswahl wie gewohnt ab. Klicks auf alle anderen nativen Ziele werden nicht von CC-Aeroworks übernommen.

## Pultnetz und Routing

Die Verknüpfung gehört zu genau einem Quellpult. Dieses liest den verbundenen Radar des Controllers alle fünf Ticks und verteilt den Snapshot automatisch an alle RadarDisplays im vollständig geladenen Pultnetz.

Ein erneutes Verbinden eines Network Controllers ersetzt die bisherige Controller-Quelle des Netzes. Dadurch gibt es keinen Wettlauf mehrerer Quellpulte.

Für die Radarweiterleitung sind zulässig:

- ein einzelnes Pult ohne eingebetteten Computer,
- ein Pultnetz ohne eingebetteten Computer,
- ein Pultnetz mit genau einem eingebetteten Computer.

Mehrere eingebettete Computer, teilweise geladene Netze und Netze mit mehr als 64 Pulten bleiben gesperrt. Die Position eines vorhandenen Computer-Steuerungspults beeinflusst die Radarroute nicht.

## Direkter Radar-Snapshot

Der Adapter liest am Network Controller dessen verbundenen Radar. Verwendet werden:

- Betriebszustand,
- Weltposition des Radars,
- Reichweite,
- ausgewählter Track des Controllers,
- höchstens die **256** nächstgelegenen Radartracks mit Position und Geschwindigkeit.

Dafür wird kein Create:-Radars-Monitor simuliert. Der Monitor-spezifische Data-Link-BlockEntity-Mixin und die frühere `DataLinkBlockEntity.updateGatheredData`-Erfassung existieren nicht mehr.

Der Snapshot wird nur für Client-Updates übertragen und nicht als fortlaufende Trackhistorie im Weltstand gespeichert. Die Controller-Verknüpfung selbst wird am Quellpult gespeichert. Nach **20 Ticks** ohne frischen Snapshot oder bei einem entfernten beziehungsweise ungültigen Controller zeigt das RadarDisplay den getrennten Zustand `X`.

## Ponder-Erklärungen

Die Radaritems besitzen zwei lokalisierte Storyboards:

1. **Network Controller verbinden** zeigt die Auswahl des Controllers und den anschließenden Klick auf das Pult ohne Blockplatzierung.
2. **Radar direkt darstellen** erklärt die direkte Controller-Abfrage, mehrere Displays im Pultnetz und den getrennten Zustand.

## Entwicklungsclient

`./gradlew runClient` löst Create: Radars, Create Big Cannons und Ritchie's Projectile Library automatisch über CurseMaven als `localRuntime` auf. Die Zielversionen und CurseForge-Datei-IDs stehen in `gradle.properties`.

Lokal in `libs/` liegende offizielle JARs dieser Mods werden aus dem allgemeinen Datei-Classpath ausgeschlossen, damit nicht zwei Kopien mit derselben Mod-ID geladen werden. Fremdmods werden nicht in das CC-Aeroworks-JAR eingebettet.

## Manuelle Prüfung

- Start ohne Create: Radars: keine Radaritems, Radarrezepte oder optionalen Mixinfehler.
- Network Controller mit Radar verbinden: natives Create:-Radars-Verhalten bleibt unverändert.
- Network Controller mit dem Data-Link-Gegenstand auswählen und danach ein Pult anklicken: Erfolgsmeldung, kein Data-Link-Block, kein Itemverbrauch.
- Pult ohne RadarDisplay: verständliche Fehlermeldung und keine gespeicherte Verbindung.
- Ein oder mehrere RadarDisplays im Netz: alle zeigen denselben verbundenen Radar.
- Quellpult und Anzeigen an verschiedenen Pulten: automatische Weiterleitung funktioniert.
- Controller erneut an ein anderes Pult desselben Netzes binden: vorherige Quelle wird ersetzt.
- Network Controller, Radar oder Chunk entfernen: spätestens nach 20 Ticks erscheint `X`.
- Zwei Computer-Steuerungspulte, teilweise geladenes oder überlanges Netz: Verbindung wird abgewiesen.
- Native Waffen-, Radar- und Monitorziele des Data-Link-Gegenstands bleiben unverändert.
- Fallback-Renderer und Flywheel-Renderer getrennt prüfen.
