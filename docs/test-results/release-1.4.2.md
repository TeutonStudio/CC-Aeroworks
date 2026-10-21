# Releaseprüfung 1.4.2

Datum: 21. Oktober 2026. Minecraft 1.21.1, NeoForge 21.1.234, CC:Tweaked 1.120.2, TeutonLib 1.2.2.

## Bestanden

- Releaseversion `1.4.2`: `verifyModDependencies test build runGameTestServer --offline` erfolgreich; **101 Unit-Tests und 12 GameTests**, keine Fehler.
- Alle **31** `tools/verify-*.py` erfolgreich, einschließlich der vier Fremdmod-Bytecodeprüfungen mit offiziellen Artefakten.
- Beide Lua-Tests erfolgreich: Control-Handler und Display-Auflösungshelper. Der neue Lua-Test ist in CI aufgenommen.
- GameTest mit echten montierten Displaymodulen prüft unterschiedliche PPB auf zwei kleinen Slots und einem großen Nachbardisplay, Pixelzugriff, Größenbeschreibungen, Leeren bei Änderung, Erhalt bei gleichem Wert, ungültige Werte/Slots, NBT-Wiederherstellung und Ausbau/Neumontage.
- Fehlender, normaler und erweiterter Computer sowie widersprüchliche Verbünde geprüft. Externe Peripherals empfangen ohne erweiterten Besitzer auch bei direkt eingespeisten Touch-Daten keine Displayereignisse. Gedrehte Treffergeometrie und Bindungserhalt beim Computerwechsel geprüft.
- Isolierter Dedicated-Server-Smoke-Test und Clientstart mit TeutonLib 1.2.2 waren vor der Versionsanhebung erfolgreich. Der GameTest-Server wurde zusätzlich mit der fertigen Releaseversion erfolgreich gestartet.
- Lokale TeutonLib-JAR stimmt mit der veröffentlichten GitHub-Prüfsumme überein: `bf8b6dc74a2b2f7a279d052616ef000b2bfb01c34c749eb7fcdd8623dc47ae67`.
- Release-JAR auf ZIP-Integrität, Modversion `1.4.2` und TeutonLib-Versionsbereich `[1.2.2,1.3.0)` geprüft.

## Versionskompatibilität

TeutonLib 1.2.2 begrenzt seine optionale CC-Aeroworks-Integration auf `[1.4.0,1.5.0)`. Deshalb wird diese Erweiterung als **1.4.2** veröffentlicht. Ein Probe-Build mit 1.5.0 wurde vom Loader erwartungsgemäß abgelehnt und wird nicht veröffentlicht.

## Artefakte

- `build/libs/cc-aeroworks-1.4.2.jar`
- `build/libs/cc-aeroworks-1.4.2.jar.sha256`

SHA-256: `2f566940c95fb688f366c25517eb4feb309a3fcc402583157963b7dbe05628ab`.

Lokale Release-Logs: `build/test-results/release-1.4.2/`. Detaillierte Funktionsprüfung: [Display-Auflösung und TeutonLib](display-resolution-teutonlib-1.2.2.md).

## Grenzen

Keine neue interaktive Sichtprüfung von Zeiger/Draw, Ständern oder bewegten Sable-Unterwelten. Mehrspieler-Reconnect und echter Serverneustart mit individuellen Displays bleiben manuell zu prüfen; NBT-Wiederherstellung ist automatisiert abgedeckt. Der Client meldet Ressourcen-/Modellfehler aus Create: Radars und Shaderwarnungen, startet aber erfolgreich. Die Darstellung dieser Drittmod-Ressourcen wurde nicht bewertet.
