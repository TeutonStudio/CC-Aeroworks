# Implementation Log

## Architektur

Pfad A wurde umgesetzt: Die Displaymodule werden über Aeroworks `ModuleTypes.register(ResourceLocation, ModuleType.Builder)` registriert. Es gibt keine Enum-Erweiterung, Reflection, Access Transformer oder `@Overwrite`.

Die Displaywerte liegen im von Aeroworks bereits persistent gespeicherten und synchronisierten `MountedModule.customName`. `ConsoleBlockEntity.setModuleName` markiert die BlockEntity und sendet deren Daten. Socket und Displayvariante werden nicht doppelt gespeichert.

Textzustände bleiben direkt lesbarer Text. Pixelzustände verwenden im selben Feld das versionierte Präfix `@cca_pixels_1:` und ein validiertes zeilenweises Bitraster. Damit gibt es weiterhin nur eine persistente Quelle. Zweisteller nutzen `7x5`, Dreisteller `11x5` Pixel. Fallback- und Flywheel-Pfad teilen Positionierung und eigenes Pixelmodell.

Aeroworks nimmt registrierte `ModuleItem`s selbst in seinen Creative Tab auf. Ein zusätzlicher `BuildCreativeModeTabContentsEvent`-Eintrag wurde nach einem Dedicated-Server-Test entfernt, weil er die Items doppelt einfügte. Clientseitig wird der vorhandene Aeroworks-Tab nun nach dem Vorbild der Simulated-Kategorieschilder in die Abschnitte `Aeroworks` und `CC-Aeroworks` gegliedert. Ein `@WrapMethod` auf `CreativeModeTab.buildContents` ordnet nur diesen bestätigten Tab um; Accessors ersetzen dessen Anzeigeliste und lesen den Scrollwert. Die Kategorieflächen werden vollständig deckend nach dem Slotrendering gezeichnet, sodass darunter keine Slotgrafik sichtbar bleibt. Fremde Tabs und die eigentliche Aeroworks-Itemregistrierung bleiben unverändert.

Die Sockettypen werden bereits bei `ModuleType.builder(SocketType...)` eingeschränkt: `TWO_DIGIT` nennt explizit `AeroworksSocketTypes.SMALL` und `LARGE`, `THREE_DIGIT` ausschließlich `LARGE`. Damit passt die zweistellige Anzeige in kleine und große, die dreistellige nur in große Desk-Sockets.

Das Desk-Peripheral wird ausschließlich für den bestätigten BlockEntityType `aeroworks:console` über `RegisterCapabilitiesEvent.registerBlockEntity` und `PeripheralCapability.get()` bereitgestellt. Eine `WeakHashMap` erhält Identität ohne Weltreferenzleck; das Peripheral selbst hält die BlockEntity schwach. Alle Lua-Weltzugriffe sind mit `mainThread = true` annotiert.

Aeroworks 1.3.0 verwendet für Analogquellen freie `String`-Werte und keine geschlossene Registry oder ein Enum. Deshalb ergänzt `cc_aeroworks.combined` den Modulbildschirm als dritten Zustand für `aeroworks:lever`, `aeroworks:joystick` (`x`, `y`) sowie die vier bestätigten Throttle-Quadrant-Kanäle `red`, `amber`, `green`, `blue`. Gezielte Invoker auf `modeToggleAt`, `bindAreaAt`, `analogDriven`, `sendAnalogSource`, `sendBind`, `sendChannelFlag`, `bindFor` und `module` erweitern den Zyklus zu `Buttons -> Analog -> Kombiniert -> Buttons`. In diesem Zustand dient die vorhandene negative Tastenbindung der Achse als persistent gespeicherte Aktivierungstaste; das mittlere Feld erfasst und zeigt diese Taste. Joystick `x` verarbeitet Maus X, alle übrigen unterstützten Kanäle Maus Y. `InputSource.displayName(String)` erhält nur für die eigene Quellen-ID einen übersetzten Namen. Client-Zielerfassung und Server-Payload akzeptieren nur explizit unterstützte Module/Kanäle mit `ANALOG_ACTIVE` und exakt dieser Quelle.

Der Throttle Quadrant wird über Modulraycast plus Aktivierungstaste aufgelöst. Weil jeder seiner vier Kanäle eine eigene Taste besitzt, ist kein fragiler geometrischer Raycast gegen die vier beweglichen Teilmodelle nötig. Die Payload überträgt den Kanalnamen; der Server prüft ihn gegen die feste, aus der Aeroworks-JAR bestätigte Kanalliste, bevor er `setChannelFromController` aufruft.

Das Guide-Book ist ein registriertes Vanilla-`WrittenBookItem` mit `WrittenBookContent` aus acht übersetzten Component-Seiten als Datenfallback. Es benötigt keine optionale Dokumentationsmod und referenziert für das Item lediglich das Vanilla-Modell `minecraft:item/written_book`. Der Creative-Tab-Ordner fügt dessen Default-Stack genau einmal in den Abschnitt `CC-Aeroworks` ein. Weil `ServerPlayer.openItemGui` in Minecraft 1.21.1 hart auf exakt `Items.WRITTEN_BOOK` prüft, öffnet ein ausschließlich clientseitig registrierter `RightClickItem`-Handler `GuideBookScreen`; dadurch bleiben Clientklassen vom Dedicated Server getrennt. Die eigene Oberfläche bietet sieben lokalisierte Kapitel, Codeblöcke, Hinweise, Sidebar-, Vor/Zurück- und Scrollnavigation in einem skalierenden Cockpit-Layout.

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
- `ModuleScreen.mouseClicked(DDI)Z` und `renderModeTooltip(GuiGraphics,III)V` jeweils bei `HEAD`, nur für `aeroworks:lever`; private Aeroworks-Helfer werden über Invoker aufgerufen.
- `InputSource.displayName(String)` bei `HEAD`, nur für die eigene Quellen-ID `cc_aeroworks.combined`.

Alle Fremdmodziele verwenden `remap = false`; Vanilla-Zugriffe werden remappt. Fehlschlagende Kernmixins sind absichtlich nicht still optional.

## Versionsentscheidung

Kotlin 2.2.20 und das NeoForge-Artefakt `kotlinforforge-neoforge:5.11.0` wurden aus einer lokalen funktionierenden 1.21.1-Konfiguration übernommen. NeoForge ist auf 21.1.228 gepinnt: Aeronautics/Sable verlangen mindestens 21.1.228; Create 6.0.10 startet damit. Unter 21.1.231 scheiterte Create 6.0.10 in Registrate an ungenutzten Callback-Prüfungen.

Drive By Wire ist eine optionale Laufzeitintegration. Die lokal untersuchte `drivebywire-0.2.9.jar` deklariert die Mod-ID `drivebywire`, Minecraft 1.21.1 und NeoForge; deshalb ist `[0.2.9,0.3)` als optionale Abhängigkeit eingetragen. Aeroworks aktiviert bei Anwesenheit seine eigene konditionale Konfiguration `aeroworks-drivebywire.mixins.json`. CC-Aeroworks importiert keine Drive-By-Wire-Klassen und bleibt ohne die Mod startfähig.

## Laufzeitstatus

Client-Modloading, Registry-Phase, Mixins und Ressourcen-Reload liefen bis ins Hauptmenü. Dabei wurden auch `ModuleScreenCombinedInputMixin`, dessen Invoker/Accessor und `InputSourceMixin` nachweislich angewendet. Interaktive Weltprüfungen sind in `manual-test-plan.md` offen und werden nicht als abgeschlossen bezeichnet.
