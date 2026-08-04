# Project Status

## Implementiert

- zwei Computer-Steuerungspultvarianten mit normaler beziehungsweise Advanced-Computerfamilie,
- komponentenerhaltendes Spezialrezept,
- linearer, ausrichtungs- und Deckenstatus-gebundener Multiblock bis 64 Mitglieder,
- Terminalzugriff von jedem gültigen Mitglied,
- Konflikt-, Größen- und Teilladezustand,
- direkte globale `aeroworks`-API des eingebetteten Computers,
- vollständige direkte Socket-, Modul-, Eingabe- und Displayverwaltung,
- multiblockfähiges externes `cc_aeroworks_control_desk`-Peripheral bei nur einer Verbindung,
- rückwärtskompatible Einzelpultmethoden,
- Einzelpult-, Multiblock- und eingebettete Eingabeereignisse einschließlich Entfernungen,
- gemeinsamer Desk-Service,
- externe Display-Target-Unterstützung,
- Redstone, gebündeltes Redstone und fremde Seitenperipherals,
- Modelle, gemischte Pultverbindungen, Skins, Loot Tables, Sprachen und Creative Tab,
- reproduzierbarer Build-Einstieg, Dependency-Validierung, CI und Testharness.

## Statisch geprüft

- Repositorydateien und Ressourcen sind strukturell konsistent.
- Branchhistorien von Buildbasis und Multiblockfunktion sind im Integrationscommit zusammengeführt.
- Einzelpult- und Multiblockzugriff delegieren an denselben Desk-Service.
- Eingabedifferenzen behandeln neue, geänderte und entfernte Kanäle sowie Modulwechsel deterministisch.
- Die direkte API ist ohne Peripheral dokumentiert.

## Offen beziehungsweise blockiert

- vollständige Gradle-Kompilierung mit den rechtmäßig bereitgestellten Ziel-JARs,
- geschützter Vollbuild und Dedicated-Server-Smoke-Test,
- Client- und Ingame-Prüfung,
- Crafting- und Persistenzprüfung,
- Flywheel- und Fallback-Rendering,
- Sable statisch und bewegt,
- CC:Tweaked 1.119.0 und 1.120.0,
- vollständige manuelle Basis- und Multiblockmatrix.

Nicht ausgeführte Laufzeitprüfungen gelten weiterhin als `NOT RUN` oder `BLOCKED`, nicht als implizit bestanden.
