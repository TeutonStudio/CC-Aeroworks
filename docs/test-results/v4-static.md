# v4 Integrationsprüfung

## Ziel

Integrationsstand aus `post-v3-development` und `agent/computer-control-desk-multiblock`.

## Ausgeführt

- Branch- und Commitvergleich über die GitHub-API.
- Prüfung, dass der Multiblock-Featurebranch den Planungsbranch enthält.
- Zusammenführung beider Historien in einem Integrationscommit.
- Übernahme des reproduzierbaren Build-, CI- und Testharnessstands.
- statische Prüfung der geänderten Kotlin-Dateien auf ausgeglichene Klammern und konsistente API-Namen.
- Abgleich von README, API-Dokumentation, Programmierleitfaden und Lua-Beispielen.
- Prüfung des finalen GitHub-Diffs vor dem Mastermerge.

## Nicht ausgeführt

- Gradle-Konfiguration und Kotlin-Kompilierung,
- Unit-Tests,
- Client- oder Dedicated-Server-Start,
- geschützter Vollbuild,
- Ingame-, Flywheel-, Sable- und Persistenztests.

## Grund

Die Ausführungsumgebung besitzt weder einen lokalen authentifizierten GitHub-Checkout noch die rechtmäßig bereitzustellenden Create-, Aeronautics-, Aeroworks-, Sable- und CC:Tweaked-Ziel-JARs. Der öffentliche Repositoryvertrag wird durch die integrierte GitHub-Action bereitgestellt; der Vollbuild benötigt die geschützten Dependency-Secrets.

## Ergebnis

Der Integrationsbaum ist für den Mastercommit vorbereitet. Der Laufzeitstatus bleibt für nicht ausgeführte Profile `BLOCKED` beziehungsweise `NOT RUN`. Dieser Bericht ist kein Ersatz für die Release-Gates in `runtime-test-matrix.md`.
