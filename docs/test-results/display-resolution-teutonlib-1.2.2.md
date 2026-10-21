# Display-Auflösung und TeutonLib 1.2.2

Datum: 21. Oktober 2026. Minecraft 1.21.1, NeoForge 21.1.234, CC:Tweaked 1.120.2.

## Umsetzung

- TeutonLib 1.2.2 aus dem benachbarten Projekt verwendet. Manifest und Versionsbereich auf `[1.2.2,1.3.0)` aktualisiert; verwendete Computer-API im Quellvergleich unverändert.
- SHA-256 der verwendeten TeutonLib-JAR: `bf8b6dc74a2b2f7a279d052616ef000b2bfb01c34c749eb7fcdd8623dc47ae67`.
- `display.useGlobalResolution=true` erhält die globale Vorgabe. Bei `false` erlaubt `setDisplayResolution(socket, ppb)` individuelle Werte, begrenzt durch `display.maxIndividualPpb` (Standard 256, Bereich 16–1024).
- Kleine und große Displays erlauben Touch mit dem erweiterten Computer des aktiven Pultverbunds. Die Prüfung greift auch vor externen Ereignissen.

## Bestanden

- `verifyModDependencies`, `test`, `build`: erfolgreich, **101 Unit-Tests**, keine Fehler.
- **12 GameTests** erfolgreich, einschließlich des neuen Display-Integrationstests mit echten montierten Modulen und gemischten Block-Entities. Geprüft: zwei unterschiedliche kleine Slots und ein großes Display auf einem Nachbarpult, Randpixel, Größenbeschreibungen, Pixelerhalt bei gleichem PPB, Leeren bei Änderung, ungültige Werte und Slots, NBT-Speicherung/-Wiederherstellung, globaler Modus, abgesenkte Obergrenze, Modulausbau und neue Montage.
- GameTest prüft fehlenden/normalen/erweiterten Computer, widersprüchlichen Verbund, Touchfreigabe für Verbundmitglieder, gespeicherte Skriptzuweisungen und Strahlauflösung auf gedrehten Pulten. Ein angeschlossenes Test-Peripheral bestätigt, dass auch direkt eingespeiste Touch-Eingaben ohne erweiterten Besitzer keine externen Display-/Monitorereignisse erzeugen.
- Gesten-Unit-Test prüft gezielte Invalidierung eines Displays ohne Verlust einer anderen Displaygeste. Auflösungs- und Computerwechsel werden zusätzlich durch serverseitigen Kontextvergleich abgesichert.
- **27 statische Prüfskripte** erfolgreich. Die vier unveränderten Fremdmod-Bytecodeprüfungen wurden für diese Änderung nicht erneut ausgeführt.
- Beide Lua-Tests erfolgreich: `control_handlers_test.lua`, `display_resolution_test.lua`. Der neue Test prüft Weiterleitung von Slot und PPB, Rückgabe, Vererbung durch `touchdisplay` und Weitergabe von Serverfehlern; in CI aufgenommen.
- Dedicated-Server-Smoke-Test erreicht den `Done`-Marker und beendet sich anschließend kontrolliert. Die erzeugte Config enthält die neuen Optionen mit den vorgesehenen Standardwerten.
- Isolierter Client startet mit TeutonLib 1.2.2 und lädt Ressourcen, Renderer, Texturatlanten und Shader. Nach der Startprüfung per Interrupt beendet.
- Release-JAR auf ZIP-Integrität und TeutonLib-Versionsbereich geprüft.

## Artefakt und Nachweise

Mod-JAR: `build/libs/cc-aeroworks-1.4.1.jar`.

SHA-256: `c86ca849a173255216b92d476b48d117eef8afb6a4208ac27fc077eccc3cf914`.

Lokale Logs: `build/test-results/display-resolution/`. Der GameTest-Aufruf verwendet `-I build/test-results/display-resolution/gametest.gradle` zur Isolation in `build/gametest-display`. Dedicated Server: `build/smoke-server-display`; Client: `build/client-display-smoke`.

## Offene manuelle Prüfung

Keine interaktive Sichtprüfung von Zeiger/Draw, Ständern oder bewegten Sable-Unterwelten. Kein Mehrspieler-Reconnect- oder echter Prozessneustart mit individuell konfigurierten Displays; NBT-Wiederherstellung wird automatisiert geprüft. Der Client meldet Ressourcen-/Modellfehler aus Create: Radars sowie Shaderwarnungen; die Startprüfung ist erfolgreich, die Darstellung dieser Drittmod-Ressourcen wurde nicht bewertet. Die ergänzten manuellen Szenarien stehen in `docs/manual-test-plan.md`.
