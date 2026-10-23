# Releaseprüfung 1.5.0

Datum: 24. Oktober 2026. Minecraft 1.21.1, NeoForge 21.1.234, CC:Tweaked 1.120.2,
Sable 2.0.5 und TeutonLib 1.5.0.

## Bestanden

- Sauberer Release-Build: `./gradlew --offline clean test build`.
- TeutonLib-Gegenrichtungstest: CC-Aeroworks greift ausschließlich auf öffentliche Pakete unter
  `de.TeutonStudio.TeutonLib.api` zu. Der TeutonLib-Binärcode enthält keine Rückreferenz auf
  `de.teutonstudio.ccaeroworks`.
- Alle in `.github/workflows/verify.yml` aufgeführten statischen Python-Verträge erfolgreich,
  einschließlich Peripheral/Workspace, Gruppen und Wire-Signale, Display, Telemetrie, Radar und
  GPS. Die vier Fremdmod-Bytecodeverträge wurden gegen die exakt vorgesehenen Artefakte geprüft.
- Reales Dedicated-Server-Console-Profil mit normalen und erweiterten Computern: Workspace,
  Split/Merge, Reboot, genau ein Peripheral-Attachment, alte Handle-Invalidierung sowie
  Serverneustart und Persistenz erfolgreich.
- Reale Sable-Funkprüfung: Welt/Welt, Welt/Sublevel, zwei Sublevels, Bewegung, Rotation,
  Reichweitenverlust/-rückkehr, Welt-Y und Dimensionsisolation erfolgreich.
- Echter CraftOS-GPS-Lauf mit vier globalen Hosts: sichtbare Position vor Bewegung
  `108 188 40`, danach `138 198 20`; kein eigener Aeroworks-Trilaterationspfad.
- Profile ohne optionale Mods sowie mit CC:Tweaked/Create ohne Sable und ohne CC-Aeroworks
  starteten erfolgreich und bestanden Neustart/Persistenz.
- Alle 26 NeoForge-GameTests bestanden, einschließlich Displayauflösung und Touch auf einer
  persistent verbundenen Pultreihe.
- Der echte TeutonLib-Client-Smoke mit CC-Aeroworks, Sable und DbW bestand zwölf
  Workspace-Szenarien für normale/erweiterte Computer, GUI-Skalierung 2/3, Resize, Fokus,
  Escape und Quellen-API-Overlay.

## Artefakte

- `build/libs/cc-aeroworks-1.5.0.jar`
- TeutonLib: `../TeutonLib/build/libs/teutonlib-1.5.0.jar`

SHA-256 CC-Aeroworks: `6f6bc4c0b9dd9a15d784617843cbcb0a5153295b6bc5770cc12258c303a1ebdd`.

SHA-256 TeutonLib: `de3aa4755af06502d3d2489d5c8bf5d16a3d600ca35db757937210df3fb110c0`.

## Grenzen

Die automatisierte Clientmatrix für GUI-Skalierung 2/3, Resize und Fokus wurde ausgeführt. Die
manuelle Zwei-Spieler-Sichtprüfung wurde in diesem Lauf nicht erneut durchgeführt und wird nicht
als bestanden behauptet.
