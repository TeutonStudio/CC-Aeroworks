# Computer-Steuerungspult-, Handbuch- und Ponder-Testplan

Dieser Plan ergänzt `manual-test-plan.md` um die Interaktions-, Netzwerk-, Display-, Radar- und Dokumentationsfälle der neuen Desk-Adapter-Architektur.

## `DESK-INTERACTION-01` Terminal öffnen

**Profil:** `BASE-CLIENT`

1. Eine Reihe aus drei gleich ausgerichteten Pulten bauen; genau eines ist ein Computer-Steuerungspult.
2. Das Computerpult nacheinander links, mittig und rechts einbauen.
3. Mit leerer Haupthand schleichen und jedes der drei Pulte rechtsklicken.
4. Mit einem Item in der Haupthand und ohne Schleichen wiederholen.

**Erwartung:**

- Jedes geladene Mitglied öffnet dasselbe eingebettete Terminal.
- Die Position des Computerpults verändert weder Terminal noch Peripheral-Graph.
- Item in der Haupthand oder fehlendes Schleichen reserviert die Terminalinteraktion nicht.
- Der Computer wird eingeschaltet und behält seine ID.

## `DESK-INTERACTION-02` Steuerung und Einstellungen

**Profil:** `BASE-CLIENT`

1. Lever, Joystick und Throttle Quadrant montieren.
2. Jedes Modul ohne Schraubenschlüssel normal bedienen.
3. Mit Create-Schraubenschlüssel eine horizontale Pultseite rechtsklicken.
4. Ober- und Unterseite mit Schraubenschlüssel rechtsklicken.

**Erwartung:**

- Normale Modulinteraktion bleibt Aeroworks-Verhalten.
- Horizontale Seite öffnet die Steuerungsübersicht.
- Ober- und Unterseite behalten die Create-Rotation.
- Keine Aktion öffnet gleichzeitig Terminal und Steuerungsübersicht.

## `DESK-ADAPTER-01` Lokale ControlDesk-Peripherals

**Profile:** `BASE-CLIENT`, `BASE-SERVER`

1. Drei Pulte über ein Wired-Modem-Netz an einen externen Computer anschließen.
2. `peripheral.getNames()` und `peripheral.getType(name)` ausgeben.
3. Jedes `ControlDesk` einzeln wrappen.
4. Module, Eingaben und Displays jedes Pults vergleichen.

**Erwartung:**

- Jedes Pult erscheint als eigener Adapter.
- Primärtyp ist `ControlDesk`.
- Zusätzliche Typen enthalten `control_desk`, `cc_aeroworks:control_desk` und `cc_aeroworks_control_desk`.
- Methoden arbeiten ausschließlich auf dem jeweils gewrappten Pult.
- Kein Adapter besitzt alte netzwerkweite `getDesk...`-Methoden.

## `DESK-GRAPH-01` Netzwerkweite Peripheral-Suche

**Profil:** `BASE-CLIENT`

1. Ein EnderModem an ein Pult, einen Speaker an ein zweites Pult und ein anderes Peripheral an das dritte Pult setzen.
2. `peripherals.find("ControlDesk")` ausführen.
3. `peripherals.find("endermodem")` ausführen.
4. Ein zweites EnderModem hinzufügen und erneut `find` sowie `findAll` ausführen.
5. Geräte über `peripherals.wrap` und `desk.wrap(side)` abrufen.

**Erwartung:**

- `ControlDesk` liefert immer eine nach `x,y,z` adressierte Tabelle aller Pulte.
- Ein einzelnes EnderModem wird direkt als Methoden-Handle zurückgegeben.
- Zwei EnderModems ergeben bei `find` eine Tabelle und bei `findAll` ebenfalls eine Tabelle.
- Adressen enthalten Desk-Position und Anschlussseite.
- `EnderModem`, `ender_modem`, `endermodem` und der vollständige namespaced Typ finden dasselbe Gerät.
- Das Methoden-Handle kann echte Gerätemethoden, Events und Mounts verwenden.

## `DESK-GRAPH-02` Attach, Detach und Aktualisierung

**Profil:** `BASE-SERVER`

1. Eventmonitor für `peripheral`, `peripheral_detach`, `cc_aeroworks_peripheral_attached` und `cc_aeroworks_peripheral_detached` starten.
2. Geräte an mehreren Pulten platzieren und entfernen.
3. Einen Geräte-Chunk entladen und erneut laden.
4. Eine Capability-Änderung ohne Blockwechsel erzeugen und `peripherals.refresh()` aufrufen.

**Erwartung:**

- Der Graph aktualisiert sich automatisch spätestens nach fünf Ticks.
- Standard- und CC-Aeroworks-Ereignisse verwenden dieselbe Netzwerkadresse.
- Entfernte Geräte werden detached und verlieren Mounts und Computerzugriff.
- Keine veraltete Methodenreferenz bleibt funktionsfähig.
- `refresh()` erzwingt eine sofortige Neuauswertung.

## `DESK-DUPLICATE-01` Survival-Doppelplatzierung

**Profil:** `BASE-CLIENT`, `BASE-SERVER`

1. Ein Computer-Steuerungspult mit Computer-ID, Label und Dateien vorbereiten.
2. Ein zweites vorbereitetes Computer-Steuerungspult an dasselbe vollständig geladene Netzwerk setzen.
3. Das neu platzierte Pult und den ausgeworfenen Computer prüfen.
4. Welt speichern und neu laden.

**Erwartung:**

- Das zuerst vorhandene Computer-Steuerungspult bleibt Besitzer.
- Das neu platzierte Pult wird zu `aeroworks:control_desk`.
- Module, Modulkonfigurationen, Displaytext und Pixelzustand bleiben erhalten.
- Genau ein normaler oder erweiterter CC:Tweaked-Computer wird ausgeworfen.
- Computer-ID, Label, Terminalgröße, Speicherkapazität und Dateisystem bleiben erhalten.
- Nach Reload ist das Netzwerk `active`, nicht `conflict`.

## `DESK-DUPLICATE-02` Nicht-Spieler-Platzierung und Altwelt

**Profil:** `BASE-SERVER`

1. Zwei Computerpulte per `/setblock` oder Strukturwerkzeug verbinden.
2. Einen bestehenden Konflikt aus einer gespeicherten Welt laden.
3. Einen Verbund nur teilweise laden.
4. Eine Reihe mit mehr als 64 Mitgliedern erzeugen.

**Erwartung:**

- Kein Computer wird willkürlich ausgeworfen.
- Zustand bleibt `conflict`, `partially_loaded` beziehungsweise `too_large`.
- Die globale `peripherals`-API verweigert den Zugriff mit passender Diagnose.
- Lokale `ControlDesk`-Adapter bleiben einzeln nutzbar, soweit ihr Pult geladen ist.

## `DISPLAY-RECIPE-01` Pressrezepte

**Profil:** `BASE-CLIENT`, `BASE-SERVER`

1. Einen normalen CC:Tweaked-Monitor unter einer mechanischen Presse verarbeiten.
2. Den Ablauf mit einem erweiterten Monitor wiederholen.
3. Beide Rezepte im Rezeptbetrachter prüfen.

**Erwartung:**

- `computercraft:monitor_normal` wird zu genau einer `cc_aeroworks:two_digit_display`.
- `computercraft:monitor_advanced` wird zu genau einer `cc_aeroworks:three_digit_display`.
- Die Monitorvarianten werden nicht vertauscht.

## `DISPLAY-ADDRESS-01` Displays über Desk-Handles

**Profil:** `BASE-CLIENT`

1. Displays an unterschiedlichen Pulten und Sockets montieren.
2. Alle Pulte über `peripherals.find("ControlDesk")` auflisten.
3. Text, Zahlen und Pixel über das jeweilige Desk-Handle schreiben.
4. Computerpult an eine andere Position der Reihe verschieben und wiederholen.

**Erwartung:**

- Nur das adressierte Pult und der adressierte Socket ändern sich.
- Desk-Adresse und Desk-ID werden korrekt gemeldet.
- Computerposition beeinflusst die Displayadressierung nicht.
- Pixelcode liest `getDisplaySize` und setzt keine feste Auflösung voraus.

## `PONDER-COMPUTER-01` Drei Computerpult-Szenen

**Profil:** `BASE-CLIENT`

1. W über normalem und erweitertem Computer-Steuerungspult halten.
2. Alle drei Storyboards vollständig ansehen.
3. Deutsch und Englisch getrennt prüfen.

**Erwartung:**

- Beide Items registrieren dieselben drei Szenen.
- Netzwerk-Szene erklärt einzelne Desk-Adapter und beliebige Computerposition.
- Such-Szene zeigt Geräte an verschiedenen Pulten, direkte eindeutige Rückgabe und `findAll`.
- Diagnose-Szene erklärt Konflikt, Teilbeladung, Übergröße und `refresh()`.
- Insgesamt erscheinen 18 lokalisierte Erklärungsschritte ohne rohe Translation-Keys.

## `PONDER-DISPLAY-01` Drei Display-Szenen

**Profil:** `BASE-CLIENT`

1. W über `two_digit_display` und `three_digit_display` halten.
2. Herstellung, Montage und Programmierung vollständig ansehen.
3. Deutsch und Englisch getrennt prüfen.

**Erwartung:**

- Herstellung zeigt normalen und erweiterten Monitor unter der Presse.
- Montage erklärt kleine Displays in allen kompatiblen Sockets und das große Display nur in `big`.
- Programmierung verwendet Desk-Handles, Text, Zahlen, Pixel und `getDisplaySize`.
- Keine Szene behauptet feste Gesamtpixelzahlen.
- Insgesamt erscheinen 13 lokalisierte Erklärungsschritte.

## `PONDER-RADAR-01` Zwei Radar-Szenen

**Profile:** `RADAR-CLIENT`, `BASE-CLIENT`

1. Mit Create: Radars W über beiden Radaritems halten.
2. Automatisches Routing und Data-Link-Kompatibilität vollständig ansehen.
3. Ohne Create: Radars prüfen, dass Radar-Szenen nicht registriert werden.
4. Deutsch und Englisch getrennt prüfen.

**Erwartung:**

- Quelle, Computer und Anzeige dürfen an verschiedenen Pulten liegen.
- Genau ein Radarziel wird automatisch verwendet.
- Mehrere Radarziele werden als mehrdeutig erklärt.
- Data Link wird als optionaler Quellenadapter, nicht als lokales Pflichtkabel dargestellt.
- Insgesamt erscheinen 10 lokalisierte Erklärungsschritte.

## `GUIDE-01` Dynamisches Ingame-Handbuch

**Profile:** `BASE-CLIENT`, `RADAR-CLIENT`

1. Handbuch öffnen.
2. Die Sidebar vollständig hoch und herunter scrollen und jede verfügbare Seite öffnen.
3. Bei kleiner und großer GUI-Skalierung Inhalts- und Sidebar-Scrolling sowie Vor/Zurück verwenden.
4. Im Basisprofil prüfen, dass optionale Integrationsseiten nicht erscheinen.
5. Im Radarprofil prüfen, dass die Create:-Radars-Seite erscheint.
6. Deutsch und Englisch für alle weiterhin lokalisierten Kerntexte prüfen.

**Erwartung:**

- Kernbereiche umfassen Einstieg, Computerpulte, Netzwerk/API, Module, Steuerung, `telemetry`, Displays, `touch / draw`, Events, Pixel-Editor und Fehlerhilfe.
- Lokaler `ControlDesk` und eingebettetes Desk-Handle werden als unterschiedliche Verträge dargestellt.
- `peripherals`, `channels`, `controls`, `wires`, `telemetry`, `display` und `touchdisplay` besitzen sichtbare API-Karten mit Scope und Methoden.
- `channels` ist als bevorzugte High-Level-Steuerungs-API markiert; `controls` und `wires` bleiben als Low-Level-Verträge sichtbar.
- Kein Beispiel verwendet die entfernte globale `aeroworks.*`-API.
- Tap/Draw und die Draw-Felder `gestureId`, `sequence`, Start, Delta und `isEnd` werden dokumentiert.
- Create: Radars und Create: Simulated erscheinen nur bei vorhandener Integration.
- Kein Text läuft aus dem Panel oder unter den Footer; die Sidebar bleibt bei jeder Seitenzahl vollständig erreichbar.

## `GUIDE-02` Vanilla-Buchfallback

**Profile:** `BASE-CLIENT`, `RADAR-CLIENT`

1. Im Basisprofil den Vanilla-Fallback prüfen.
2. Mit Create: Radars denselben Fallback erneut prüfen.
3. Beide Sprachen laden.

**Erwartung:**

- Die allgemeinen Fallbackseiten bleiben vorhanden und verwenden aktuelle `ControlDesk`-/`peripherals`-Beispiele.
- Ohne Create: Radars wird die Radar-Fallbackseite nicht in das Buch aufgenommen.
- Mit Create: Radars wird die Radar-Fallbackseite zusätzlich aufgenommen.
- Kein roher Translation-Key wird angezeigt.

## `GUIDE-03` API-Drift-Vertrag

**Profil:** Repository-Verifikation

1. `python3 tools/verify-api-reference.py` ausführen.
2. Testweise eine öffentliche `@LuaFunction` ergänzen, ohne den Katalog zu ändern, und den erwarteten Fehler prüfen.
3. Testweise einen `display.lua`- oder `touchdisplay.lua`-Export aus dem Katalog entfernen und den erwarteten Fehler prüfen.

**Erwartung:**

- Öffentliche Kotlin-Lua-Methoden und ROM-Modulmethoden müssen im `ApiReferenceCatalog` vorhanden sein.
- `debugDisplayTouchLog` bleibt als interne Diagnosebrücke aus der öffentlichen Referenz ausgeschlossen.
- Optionale Integrationen müssen feature-gated bleiben.
- Die entfernte globale `aeroworks.*`-API darf nicht wieder in das Handbuch gelangen.
