# Computer-Steuerungspult-, Handbuch- und Ponder-Testplan

Dieser Plan ergänzt `manual-test-plan.md` um die Interaktions-, Platzierungs- und Dokumentationsfälle der Computer-Steuerungspulte.

## `DESK-INTERACTION-01` Terminal öffnen

**Profil:** `BASE-CLIENT`

1. Einen Multiblock aus drei gleich ausgerichteten Pulten bauen; genau eines ist ein Computer-Steuerungspult.
2. Mit leerer Haupthand schleichen und jedes der drei Pulte rechtsklicken.
3. Mit einem Item in der Haupthand wiederholen.
4. Ohne Schleichen wiederholen.

**Erwartung:**

- Jedes geladene Mitglied öffnet dasselbe eingebettete Terminal.
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

## `DESK-DUPLICATE-01` Survival-Doppelplatzierung

**Profil:** `BASE-CLIENT`, `BASE-SERVER`

1. Ein Computer-Steuerungspult mit Computer-ID, Label und Dateien vorbereiten.
2. Ein zweites vorbereitetes Computer-Steuerungspult an denselben vollständig geladenen Multiblock setzen.
3. Das neu platzierte Pult und den ausgeworfenen Computer prüfen.
4. Welt speichern und neu laden.

**Erwartung:**

- Das zuerst vorhandene Computer-Steuerungspult bleibt Besitzer.
- Das neu platzierte Pult wird zu `aeroworks:control_desk`.
- Module, Modulkonfigurationen, Displaytext und Pixelzustand des neuen Pults bleiben erhalten.
- Genau ein normaler oder erweiterter CC:Tweaked-Computer wird ausgeworfen.
- Computer-ID, Label, Terminalgröße, Speicherkapazität und Dateisystem bleiben erhalten.
- Nach Reload ist der Multiblock `active`, nicht `conflict`.

## `DESK-DUPLICATE-02` Creative-Doppelplatzierung

**Profil:** `BASE-CLIENT`

1. Im Creative-Modus einen Multiblock mit eingebettetem Computer bauen.
2. Ein zweites Computer-Steuerungspult daneben platzieren.

**Erwartung:**

- Die konfliktverursachende Platzierung wird entfernt.
- Das kombinierte Item bleibt in der Creative-Hand.
- Es entsteht kein zusätzlicher Computer-Drop und keine duplizierte Computer-ID.
- Eine Actionbar-Meldung erklärt den Grund.

## `DESK-DUPLICATE-03` Nicht-Spieler-Platzierung und Altwelt

**Profil:** `BASE-SERVER`

1. Zwei Computerpulte per `/setblock` oder Strukturwerkzeug verbinden.
2. Einen bestehenden Konflikt aus einer gespeicherten Welt laden.
3. Einen Multiblock nur teilweise laden.

**Erwartung:**

- Kein Computer wird willkürlich ausgeworfen.
- Der Zustand bleibt `conflict` beziehungsweise `partially_loaded`.
- Die direkte API verweigert mehrdeutigen Zugriff.
- Externe Peripheral-Methoden bleiben im Konfliktfall nutzbar.

## `PONDER-01` Beide Varianten

**Profil:** `BASE-CLIENT`

1. Im Inventar W über `computer_control_desk` halten.
2. Szene vollständig ansehen.
3. Mit `advanced_computer_control_desk` wiederholen.
4. Deutsch und Englisch getrennt prüfen.

**Erwartung:**

- Beide Items öffnen dieselbe Szene.
- Struktur zeigt ein Computerpult und zwei normale Pulte.
- Acht lokalisierte Erklärungsschritte erscheinen in richtiger Reihenfolge.
- Terminal, normale Steuerung und Schraubenschlüssel-Einstellungen werden getrennt gezeigt.
- Externer Computer wird als Alternative erklärt.
- Doppelplatzierung endet mit normalem Pult und Computerdrop.
- Keine fehlende Struktur, fehlende Übersetzung oder BlockEntity-Ausnahme.

## `GUIDE-02` Überarbeitetes Ingame-Handbuch

**Profil:** `BASE-CLIENT`

1. API-Handbuch öffnen.
2. Alle sieben Bereiche auswählen.
3. Bei kleiner und großer GUI-Skalierung scrollen und Vor/Zurück verwenden.
4. Deutsch und Englisch prüfen.

**Erwartung:**

- Bereiche: Einstieg, Computerpulte, Netzwerk & API, Module, Displays, Steuerung, Fehlerhilfe.
- Hinweise, Warnungen, Eingabehinweise und Codeblöcke sind optisch unterscheidbar.
- Direkte API und Peripheral-API werden nicht vermischt.
- Bedienung und Doppelplatzierung entsprechen dem tatsächlichen Laufzeitverhalten.
- Kein Text läuft aus dem Panel oder unter den Footer.

## `GUIDE-03` Vanilla-Buchfallback

**Profil:** `BASE-CLIENT`

1. Acht `book.cc_aeroworks.page_*`-Seiten über Datenkomponente beziehungsweise Fallback prüfen.
2. Beide Sprachen laden.

**Erwartung:**

- Alle acht Seiten existieren.
- Zugriffsweg, Bedienung, Multiblock, externe API, direkte API, Displays, Combined Input und Hilfe werden abgedeckt.
- Kein roher Translation-Key wird angezeigt.
