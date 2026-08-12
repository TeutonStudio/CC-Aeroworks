# CC:Tweaked-Beispiele

Die Skripte in diesem Verzeichnis sind nicht nur Minimalbeispiele, sondern interaktive Diagnose- und Regressionstests für die öffentliche CC-Aeroworks-Lua-API. Sie sollen deshalb weder eine bestimmte Pultposition noch einen festen Socket oder genau ein vorhandenes Gerät voraussetzen.

Gemeinsamer Grundsatz für auswählbare Ressourcen:

- kein passender Treffer: Abbruch mit konkreter Ursache und, soweit möglich, einer Abhilfe;
- genau ein Treffer: automatische Auswahl;
- mehrere Treffer: explizite, nummerierte Benutzerauswahl;
- Mehrkanal-Eingaben werden als einzelne numerische Kanäle behandelt;
- ausgewählte Endpunkte werden nach Möglichkeit über stabile Desk-, Modul-, Display- und Kanalidentität verfolgt und nicht nach Discovery-Reihenfolge;
- laufende Skripte reagieren auf relevante Attach/Detach-, Input- und Topologieereignisse und validieren kritische Endpunkte zusätzlich periodisch.

Die Beispiele besitzen bewusst unterschiedliche Aufgaben. `dashboard.lua` ist der einzige allgemeine Input-zu-Display-Mapper. Diagnoseprogramme verändern keine Displays, damit rohe Aeroworks-Werte einschließlich Vorzeichen und Achsenrichtung unverfälscht sichtbar bleiben.

## `pixel-test.lua`

Sucht zuerst alle erreichbaren CC-Aeroworks-Displays. Auf dem eingebetteten Computer wird das vollständige Pultnetz durchsucht, auf normalen CC:Tweaked-Computern alle erreichbaren `ControlDesk`-Peripherals.

- Kein Display: Abbruch mit erklärender Fehlermeldung.
- Genau ein Display: automatische Auswahl.
- Mehrere Displays: nummerierte Auswahl mit Pultadresse, Socket, Displaygröße und Pixelauflösung.
- Danach wird ein zur tatsächlich konfigurierten Auflösung passendes Rahmenmuster geschrieben und wieder gelöscht.

## `dashboard.lua`

Spiegelt einen frei gewählten numerischen Pulteingang auf ein frei gewähltes CC-Aeroworks-Display und unterstützt beide Betriebsarten automatisch.

Das Skript entdeckt alle numerischen Einzel- und Mehrkanal-Eingänge sowie alle Displays. Bei genau einem Kandidaten wird automatisch ausgewählt; bei mehreren Kandidaten erscheint eine paginierte Auswahl. Die Auswahl wird über stabile Desk-Identität, Socket, Modul-ID und Kanal beziehungsweise Display-ID verfolgt.

Während des Betriebs werden die passenden CC-Aeroworks-Ereignisse verwendet:

- eingebetteter Computer: `cc_aeroworks_console_input` und `cc_aeroworks_console_changed`,
- normaler oder Wired-Computer: `cc_aeroworks_desk_input` sowie normale `peripheral`-/`peripheral_detach`-Ereignisse.

Zusätzlich validiert das Skript die ausgewählten Endpunkte regelmäßig. Wird ein Pult, Eingabemodul, Kanal oder Display entfernt beziehungsweise ersetzt, beendet sich das Dashboard mit einer konkreten Erklärung statt still auf ein anderes Gerät umzuschalten. Beim normalen Beenden und nach abgefangenen Laufzeitfehlern versucht es, den vorherigen Text- oder Pixelzustand des Displays wiederherzustellen.

`dashboard.lua` gibt bewusst den gelesenen Rohwert weiter. Für bipolare Aeroworks-Achsen können Werte wie `-15..15` auftreten. Ein kleines zweistelliges Display kann dreistellige Darstellungen wie `-15` naturgemäß nicht vollständig anzeigen. Das Beispiel erfindet deshalb keine versteckte Skalierung oder Achseninvertierung; wer eine normierte Anzeige benötigt, sollte die gewünschte Transformation im eigenen Programm explizit festlegen.

## `input-monitor.lua`

Zeigt alle numerischen Pulteingänge live im Terminal. Das Skript unterstützt sowohl das eingebettete Pultnetz als auch normale beziehungsweise über Wired Modems verbundene `ControlDesk`-Peripherals.

- Eingabeereignisse aktualisieren die Anzeige sofort.
- Attach/Detach- und Multiblockänderungen lösen eine neue Topologieprüfung aus.
- Eine periodische Prüfung korrigiert auch Änderungen, für die gerade kein passendes Eingabeereignis anliegt.
- Konflikte, teilweise geladene Netze, Netze über 64 Pulte und fehlende Eingabemodule werden mit konkreter Ursache beziehungsweise Abhilfe angezeigt.
- Große Eingabelisten sind mit Pfeiltasten, Bild hoch/runter sowie Pos1/Ende scrollbar.
- `r` erzwingt eine neue Erkennung; `q` oder `Ctrl+T` beendet den Monitor.

## `embedded-console.lua`

Ist jetzt ein **read-only Inspector für den eingebetteten Computer Control Desk** und absichtlich kein zweites Dashboard mehr.

Das Skript verlangt die globale `peripherals`-API, prüft den Netzwerkzustand und listet alle Pulte nach Position. Bei genau einem Pult wird dessen Detailansicht automatisch geöffnet; bei mehreren Pulten kann gezielt per Nummer gewählt werden. Für jedes Pult zeigt der Inspector:

- stabile Desk-ID, Variante, Ausrichtung, Dimension und Computerstatus,
- installierte Module,
- alle Eingabekanäle mit ihren **rohen** Werten,
- Displays mit Textbreite, Pixelauflösung und aktuellem Modus,
- angrenzende CC:Tweaked-Peripherals.

Die Detailansicht ist seitenweise navigierbar und kann jederzeit verlassen werden. `r` führt die Netzwerkerkennung erneut aus, `q` beendet das Programm. Konflikte, teilweise geladene Netze, mehr als 64 Pulte und falsche Eigentümerschaft werden als Diagnosezustand ausgegeben.

Der Inspector ruft keine `setDisplay...`-Methode auf. Damit bleiben Vorzeichen und Achsenrichtung als Diagnoseinformation erhalten und kein kleines Display muss Werte wie `-10..-15` irgendwie verstümmeln.

## `multiblock-dashboard.lua`

Ist eine **read-only Live-Übersicht aller erreichbaren Pulte** und funktioniert sowohl vom eingebetteten Computer Control Desk als auch von einem normalen beziehungsweise Wired-CC:Tweaked-Computer aus.

Im eingebetteten Modus verwendet es die globale `peripherals`-API und zeigt zusätzlich Netzwerkzustand sowie globale Desk-/Peripheral-Zahlen. Im normalen/Wired-Modus durchsucht es `peripheral.getNames()` und behandelt jedes erreichbare `ControlDesk` als eigenes physisches Pult.

Pro Desk zeigt die Übersicht Position, Variante/Computerstatus sowie die Anzahl von Modulen, numerischen Eingabekanälen, Displays und, soweit über den eingebetteten Desk-Handle verfügbar, angrenzenden Peripherals. Die Tabelle aktualisiert sich periodisch und reagiert zusätzlich auf Netzwerk- beziehungsweise Peripheral-Änderungen.

Große Pultlisten sind mit Pfeiltasten, Bild hoch/runter, Pos1 und Ende scrollbar. `r` aktualisiert sofort, `q` oder `Ctrl+T` beendet das Dashboard. Fehlende Pulte sind kein Lua-Crash: das Programm zeigt stattdessen, ob im eingebetteten Netz ein ungültiger Zustand vorliegt oder beim normalen Computer schlicht kein `ControlDesk` erreichbar ist.

## Erwartete Betriebsarten

Ein **eingebetteter Computer Control Desk** besitzt die globale `peripherals`-API und kann das gesamte verbundene Pultnetz adressieren. Ein **normaler CC:Tweaked-Computer** besitzt diese API nicht; er sieht nur die über die normale CC:Tweaked-Peripheral-Infrastruktur erreichbaren `ControlDesk`-Adapter.

`dashboard.lua`, `input-monitor.lua`, `pixel-test.lua` und `multiblock-dashboard.lua` erkennen beide Fälle selbstständig. `embedded-console.lua` ist absichtlich auf den eingebetteten Pfad beschränkt und dient dort der detaillierten Inspektion statt der Displaysteuerung.
