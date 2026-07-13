# Implementation Log

## Architektur

Pfad A wurde umgesetzt: Die Displaymodule werden über Aeroworks `ModuleTypes.register(ResourceLocation, ModuleType.Builder)` registriert. Es gibt keine Enum-Erweiterung, Reflection, Access Transformer oder `@Overwrite`.

Die Displaywerte liegen im von Aeroworks bereits persistent gespeicherten und synchronisierten `MountedModule.customName`. `ConsoleBlockEntity.setModuleName` markiert die BlockEntity und sendet deren Daten. Socket und Displayvariante werden nicht doppelt gespeichert.

Aeroworks nimmt registrierte `ModuleItem`s selbst in seinen Creative Tab auf. Ein zusätzlicher `BuildCreativeModeTabContentsEvent`-Eintrag wurde nach einem Dedicated-Server-Test entfernt, weil er die Items doppelt einfügte. Clientseitig wird der vorhandene Aeroworks-Tab nun nach dem Vorbild der Simulated-Kategorieschilder in die Abschnitte `Aeroworks` und `CC-Aeroworks` gegliedert. Ein `@WrapMethod` auf `CreativeModeTab.buildContents` ordnet nur diesen bestätigten Tab um; Accessors ersetzen dessen Anzeigeliste und lesen den Scrollwert. Fremde Tabs und die eigentliche Aeroworks-Itemregistrierung bleiben unverändert.

Die Sockettypen werden bereits bei `ModuleType.builder(SocketType...)` eingeschränkt: `TWO_DIGIT` nennt explizit `AeroworksSocketTypes.SMALL` und `LARGE`, `THREE_DIGIT` ausschließlich `LARGE`. Damit passt die zweistellige Anzeige in kleine und große, die dreistellige nur in große Desk-Sockets.

Das Desk-Peripheral wird ausschließlich für den bestätigten BlockEntityType `aeroworks:console` über `RegisterCapabilitiesEvent.registerBlockEntity` und `PeripheralCapability.get()` bereitgestellt. Eine `WeakHashMap` erhält Identität ohne Weltreferenzleck; das Peripheral selbst hält die BlockEntity schwach. Alle Lua-Weltzugriffe sind mit `mainThread = true` annotiert.

## Rendering

Aeroworks rendert den statischen, eigenen Modulgrundkörper regulär als `ModulePart`. Dynamische Siebensegmentteile werden im Fallback am Ende von `ConsoleRenderer.renderSafe` ergänzt. Der Flywheel-Pfad ergänzt ausschließlich CC-Aeroworks-Displays am Ende von `ConsoleVisual` und verwaltet eigene `TransformedInstance`s. Beide Hooks ersetzen keinen fremden Renderer.

Auch die Mixins wurden nach Kotlin migriert. Die Deskriptoren der vier Render-/Eingabemixins wurden mit
`javap -p -s -v` geprüft und diese Mixins anschließend durch einen Clientstart angewendet. Der
statische Analog-Handler verwendet ein `private companion object` mit `@JvmStatic`; dadurch bleibt
die erzeugte Companion-Referenz privat und erfüllt Mixins Feldvalidierung. Zielmethoden:

- `ConsoleRenderer.renderSafe(ConsoleBlockEntity,float,PoseStack,MultiBufferSource,int,int)` bei `TAIL`.
- Konstruktor und Lebenszyklusmethoden von `ConsoleVisual`; keine pauschale Render-Injection.
- `ConsoleControlClient.feedMouseDelta(DD)V` und `JoystickControlClient.feedMouseDelta(DD)V` bei `HEAD`, nur während des gültigen Combined-Lever-Modus.
- Accessor auf `MouseHandler.accumulatedDY` für das bestätigte `CalculatePlayerTurnEvent`.
- `CreativeModeTab.buildContents(ItemDisplayParameters)` über MixinExtras `@WrapMethod`, beschränkt durch die bestätigte Aeroworks-Tab-ID; zwei Vanilla-Accessors setzen `displayItems` beziehungsweise lesen `scrollOffs`.

Alle Fremdmodziele verwenden `remap = false`; Vanilla-Zugriffe werden remappt. Fehlschlagende Kernmixins sind absichtlich nicht still optional.

## Versionsentscheidung

Kotlin 2.2.20 und das NeoForge-Artefakt `kotlinforforge-neoforge:5.11.0` wurden aus einer lokalen funktionierenden 1.21.1-Konfiguration übernommen. NeoForge ist auf 21.1.228 gepinnt: Aeronautics/Sable verlangen mindestens 21.1.228; Create 6.0.10 startet damit. Unter 21.1.231 scheiterte Create 6.0.10 in Registrate an ungenutzten Callback-Prüfungen.

Drive By Wire ist eine optionale Laufzeitintegration. Die lokal untersuchte `drivebywire-0.2.9.jar` deklariert die Mod-ID `drivebywire`, Minecraft 1.21.1 und NeoForge; deshalb ist `[0.2.9,0.3)` als optionale Abhängigkeit eingetragen. Aeroworks aktiviert bei Anwesenheit seine eigene konditionale Konfiguration `aeroworks-drivebywire.mixins.json`. CC-Aeroworks importiert keine Drive-By-Wire-Klassen und bleibt ohne die Mod startfähig.

## Laufzeitstatus

Client-Modloading, Registry-Phase, Mixins und Ressourcen-Reload liefen bis ins Hauptmenü. Interaktive Weltprüfungen sind in `manual-test-plan.md` offen und werden nicht als abgeschlossen bezeichnet.
