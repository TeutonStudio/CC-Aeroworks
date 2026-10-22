# Releaseprüfung 1.4.5

Datum: 22. Oktober 2026. Minecraft 1.21.1, NeoForge 21.1.234, CC:Tweaked 1.120.2, TeutonLib 1.3.0.

## Bestanden

- `verifyModDependencies test build --offline`: erfolgreich; **101 Unit-Tests**, keine Fehler.
- GameTest-Server: **13 GameTests** erfolgreich. Der neue Regressionstest verwendet ein echtes Aeroworks-Yoke, dessen `ControlTransmitter` und registrierte Create-Redstone-Link-Empfänger; er prüft positive und negative Computerwerte, mehrtaktige Spring-Rückstellung, HARD-Sperre, `release`, Computer-Aus und ein vom Eigentümernetz getrenntes Zielpult.
- Alle **31** `tools/verify-*.py` erfolgreich, einschließlich der vier Fremdmod-Bytecodeprüfungen mit den exakten veröffentlichten Artefakten.
- Beide Lua-Tests erfolgreich: Control-Handler sowie Display-Auflösungshelper.
- Isolierter Dedicated Server mit dem Abhängigkeitsprofil aus `libs/` erreichte den `Done`-Marker ohne bekannten fatalen Marker.
- Isolierter Client lud beide CC-Aeroworks-Modteile, TeutonLib 1.3.0, Ponder-Szenen, Modelle, Texturatlanten und Shader. Der Smoke-Test wurde danach kontrolliert beendet.
- Lokale TeutonLib-JAR stimmt mit der vorgesehenen Prüfsumme überein: `a3e655dc804868bc7e73d6d6aaa58eef70a86817394203d7e7d58f04459bd32c`.
- Release-JAR auf ZIP-Integrität, beide Modversionen `1.4.5` und TeutonLib-Versionsbereich `[1.3.0,1.4.0)` geprüft.

## Artefakte

- `build/libs/cc-aeroworks-1.4.5.jar`
- `build/libs/cc-aeroworks-1.4.5.jar.sha256`

SHA-256: `889c519c7588defa27cc7fccc5d9518663e1392fc7e03125ddf7940218a9499f`.

## Grenzen

Die vier aktiven Itemtexturen wurden als Rastergrafik kontrolliert und beim Clientstart erfolgreich geladen. Eine interaktive In-Game-Sichtprüfung in Inventar, Hand, gedroppt und im Item Frame wurde in diesem automatisierten Lauf nicht durchgeführt. Der Client meldet weiterhin Modellfehler aus Create: Radars sowie Shaderwarnungen anderer Abhängigkeiten; CC Aeroworks lud dennoch bis zur vollständigen Ressourceninitialisierung.
