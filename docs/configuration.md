# Configuration

Die Clientdatei ist `config/cc_aeroworks-client.toml`.

- `combinedLeverSensitivity`: Änderung des Gleitkommaakkumulators pro vertikalem Mausdelta; Standard `0.15`.
- `combinedLeverInvertY`: kehrt die vertikale Richtung um; Standard `false`.
- `combinedLeverPacketRate`: maximale Pakete pro Sekunde; Standard `20`, Bereich 1 bis 20.
- `freezeCameraOnlyWithValidTarget`: Sicherheitsoption, Standard `true`. Unabhängig vom Wert friert die Implementierung ohne gültigen Lever nie die Kamera ein; `false` erweitert aus Sicherheitsgründen nicht den Zielbereich.

Die Aktivierungstaste wird pro Achse direkt im Aeroworks-Modulbildschirm gespeichert. Nach Auswahl des Input Types `Kombiniert` zeigt das mittlere Eingabefeld nicht mehr die Mausachse, sondern die Aktivierungstaste. Linksklick startet die Tastenerfassung, Rechtsklick löscht sie. Beim erstmaligen Umschalten auf `Kombiniert` wird eine leere Belegung mit `K` vorbelegt.

Ein Linksklick auf das vorhandene Modussymbol schaltet bei Lever, Joystick und jeder Achse des Throttle Quadrant zyklisch `Buttons -> Analog -> Kombiniert -> Buttons`. Beim Throttle Quadrant besitzen `red`, `amber`, `green` und `blue` jeweils eine eigene Aktivierungstaste. Der Joystick besitzt getrennte Tasten für `x` und `y`: links/rechts verwendet Maus X, vor/zurück Maus Y. Lever und Throttle verwenden Maus Y. Beim Blick auf das Modul wählt die gerade gehaltene konfigurierte Taste die zu steuernde Achse. Intern wird die freie Aeroworks-Input-Source `cc_aeroworks.combined` gespeichert; es wird kein fremder Enumwert ergänzt.

Maus Y ist für `Kombiniert` fest vorgegeben. Die Aktivierungstaste stammt aus der vorhandenen, von Aeroworks persistent gespeicherten negativen Tastenbindung der jeweiligen Achse. Eine globale Minecraft-Keybinding-Zuordnung ist dafür nicht mehr erforderlich.
