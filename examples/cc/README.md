# CC:Tweaked-Beispiele

Die Skripte in diesem Verzeichnis sind nicht nur Minimalbeispiele, sondern interaktive Diagnose- und Regressionstests für die öffentliche CC-Aeroworks-Lua-API. Sie sollen deshalb weder eine bestimmte Pultposition noch einen festen Socket oder genau ein vorhandenes Gerät voraussetzen.

## `pixel-test.lua`

Sucht zuerst alle erreichbaren CC-Aeroworks-Displays. Auf dem eingebetteten Computer wird das vollständige Pultnetz durchsucht, auf normalen CC:Tweaked-Computern alle erreichbaren `ControlDesk`-Peripherals.

- Kein Display: Abbruch mit erklärender Fehlermeldung.
- Genau ein Display: automatische Auswahl.
- Mehrere Displays: nummerierte Auswahl mit Pultadresse, Socket, Displaygröße und Pixelauflösung.
- Danach wird ein zur tatsächlich konfigurierten Auflösung passendes Rahmenmuster geschrieben und wieder gelöscht.

## `dashboard.lua`

Spiegelt einen frei gewählten numerischen Pulteingang auf ein frei gewähltes CC-Aeroworks-Display.

Das Skript entdeckt alle numerischen Einzel- und Mehrkanal-Eingänge sowie alle Displays. Bei genau einem Kandidaten wird automatisch ausgewählt; bei mehreren Kandidaten erscheint eine paginierte Auswahl. Die Auswahl wird über stabile Desk-Identität, Socket, Modul-ID und Kanal beziehungsweise Display-ID verfolgt.

Während des Betriebs werden die passenden CC-Aeroworks-Ereignisse verwendet:

- eingebetteter Computer: `cc_aeroworks_console_input` und `cc_aeroworks_console_changed`,
- normaler Computer: `cc_aeroworks_desk_input` und `peripheral_detach`.

Zusätzlich validiert das Skript die ausgewählten Endpunkte regelmäßig. Wird ein Pult, Eingabemodul, Kanal oder Display entfernt beziehungsweise ersetzt, beendet sich das Dashboard mit einer konkreten Erklärung statt still auf ein anderes Gerät umzuschalten. Beim normalen Beenden und nach abgefangenen Laufzeitfehlern versucht es, den vorherigen Text- oder Pixelzustand des Displays wiederherzustellen.

## `input-monitor.lua`

Zeigt alle numerischen Pulteingänge live im Terminal. Das Skript unterstützt sowohl das eingebettete Pultnetz als auch normale beziehungsweise über Wired Modems verbundene `ControlDesk`-Peripherals.

- Eingabeereignisse aktualisieren die Anzeige sofort.
- Attach/Detach- und Multiblockänderungen lösen eine neue Topologieprüfung aus.
- Eine periodische Prüfung korrigiert auch Änderungen, für die gerade kein passendes Eingabeereignis anliegt.
- Konflikte, teilweise geladene Netze, Netze über 64 Pulte und fehlende Eingabemodule werden mit konkreter Ursache beziehungsweise Abhilfe angezeigt.
- Große Eingabelisten sind mit Pfeiltasten, Bild hoch/runter sowie Pos1/Ende scrollbar.
- `r` erzwingt eine neue Erkennung; `q` oder `Ctrl+T` beendet den Monitor.

## Erwartete Betriebsarten

Ein **eingebetteter Computer Control Desk** besitzt die globale `peripherals`-API und kann das gesamte verbundene Pultnetz adressieren. Ein **normaler CC:Tweaked-Computer** besitzt diese API nicht; er sieht nur die über die normale CC:Tweaked-Peripheral-Infrastruktur erreichbaren `ControlDesk`-Adapter. Die Beispiele erkennen diese beiden Fälle selbstständig und verwenden jeweils den passenden API- und Ereignispfad.
