# CC:Tweaked-Beispiele

Die Skripte in diesem Verzeichnis sind nicht nur Minimalbeispiele, sondern interaktive Diagnose- und Regressionstests für die öffentliche CC-Aeroworks-Lua-API. Sie sollen deshalb weder eine bestimmte Pultposition noch einen festen Socket oder genau ein vorhandenes Gerät voraussetzen.

Gemeinsamer Grundsatz für auswählbare Ressourcen:

- kein passender Treffer: Abbruch mit konkreter Ursache und, soweit möglich, einer Abhilfe;
- genau ein Treffer: automatische Auswahl;
- mehrere Treffer: explizite, nummerierte Benutzerauswahl;
- Mehrkanal-Eingaben werden als einzelne numerische Kanäle behandelt;
- ausgewählte Endpunkte werden nach Möglichkeit über stabile Desk-, Modul-, Display- und Kanalidentität verfolgt und nicht nach Discovery-Reihenfolge;
- laufende Skripte reagieren auf relevante Attach/Detach-, Input- und Topologieereignisse und validieren kritische Endpunkte zusätzlich periodisch.

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

## `input-monitor.lua`

Zeigt alle numerischen Pulteingänge live im Terminal. Das Skript unterstützt sowohl das eingebettete Pultnetz als auch normale beziehungsweise über Wired Modems verbundene `ControlDesk`-Peripherals.

- Eingabeereignisse aktualisieren die Anzeige sofort.
- Attach/Detach- und Multiblockänderungen lösen eine neue Topologieprüfung aus.
- Eine periodische Prüfung korrigiert auch Änderungen, für die gerade kein passendes Eingabeereignis anliegt.
- Konflikte, teilweise geladene Netze, Netze über 64 Pulte und fehlende Eingabemodule werden mit konkreter Ursache beziehungsweise Abhilfe angezeigt.
- Große Eingabelisten sind mit Pfeiltasten, Bild hoch/runter sowie Pos1/Ende scrollbar.
- `r` erzwingt eine neue Erkennung; `q` oder `Ctrl+T` beendet den Monitor.

## `embedded-console.lua`

Ist die bewusst eingebettete Variante des Input-zu-Display-Beispiels. Das Skript verweigert den Start auf einem normalen Computer und verlangt die globale `peripherals`-API eines Computer Control Desk.

Es prüft zuerst den Netzwerkzustand und behandelt Konflikte, teilweise geladene Netze, mehr als 64 Pulte und falsche Eigentümerschaft mit erklärenden Fehlern. Danach werden alle numerischen Eingabekanäle und alle Displays des gesamten Pultnetzes ermittelt und nach dem 0/1/mehrere-Schema ausgewählt.

Im Betrieb wird ausschließlich der aktuelle eingebettete Ereignisvertrag verwendet: `cc_aeroworks_console_input` für Werte und `cc_aeroworks_console_changed` für Topologieänderungen. Die ausgewählten Endpunkte werden periodisch anhand stabiler Identitäten neu gebunden. Entfernte, ersetzte oder in ihrer Form geänderte Module und Displays führen zu einem konkreten Fehler. Der vorherige Displayzustand wird beim Beenden nach Möglichkeit wiederhergestellt.

## `multiblock-dashboard.lua`

Demonstriert ausdrücklich den **normalen CC:Tweaked-/Wired-Modem-Pfad** für mehrere Pulte. Die frühere Annahme, ein einziges lokales Peripheral stelle automatisch den gesamten Aeroworks-Multiblock als Fassade bereit, gilt nicht mehr.

Das Skript sucht deshalb über `peripheral.getNames()` jedes erreichbare `ControlDesk`-Peripheral einzeln, liest daraus alle numerischen Eingabekanäle und alle Displays und lässt Quelle und Ziel nach dem 0/1/mehrere-Schema wählen. Es verwendet `cc_aeroworks_desk_input` und normale Peripheral-Attach/Detach-Ereignisse. Bei einem Reconnect wird anhand der stabilen Desk-ID versucht, denselben gewählten Endpunkt auch dann wiederzufinden, wenn sich sein CC:Tweaked-Anschlussname geändert hat.

Auch dieses Beispiel restauriert den vorherigen Displayzustand, sofern das Ziel beim Beenden noch beziehungsweise wieder erreichbar ist.

## Erwartete Betriebsarten

Ein **eingebetteter Computer Control Desk** besitzt die globale `peripherals`-API und kann das gesamte verbundene Pultnetz adressieren. Ein **normaler CC:Tweaked-Computer** besitzt diese API nicht; er sieht nur die über die normale CC:Tweaked-Peripheral-Infrastruktur erreichbaren `ControlDesk`-Adapter.

`dashboard.lua`, `input-monitor.lua` und `pixel-test.lua` erkennen beide Fälle selbstständig. `embedded-console.lua` ist absichtlich auf den eingebetteten Pfad beschränkt. `multiblock-dashboard.lua` zeigt absichtlich den normalen/wired Peripheral-Pfad, damit beide Verträge getrennt und nachvollziehbar testbar bleiben.
