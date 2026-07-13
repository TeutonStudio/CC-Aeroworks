# CC-Aeroworks: JAR Research

## 1. Analysierte Umgebung

Status: VERIFIED

- Minecraft: 1.21.1; NeoForge-MDK: 21.1.228; ModDevGradle: 2.0.141; Java-Ziel: 21.
- Das vorhandene Projekt verwendete Groovy-DSL (`build.gradle`, `settings.gradle`) und die
  ModDevGradle-Metadatenvorlage unter `src/main/templates/`.
- Werkzeuge: `unzip`, `jar`, `sha256sum`, `javap` und lokal vorhandenes Vineflower 1.10.1.
- Vineflower wurde nur nach `.codex-work/` ausgegeben. Aussagen mit Status VERIFIED wurden gegen
  JAR-Metadaten, Klassenlisten oder `javap` geprüft, nicht allein aus dekompiliertem Java abgeleitet.

Nachweis:

```bash
./gradlew --version
javap -version
java -jar ~/.gradle/caches/modules-2/files-2.1/org.vineflower/vineflower/1.10.1/*/vineflower-1.10.1.jar --help
```

## 2. Analysierte JAR-Dateien

Alle Pfade sind absolute lokale Fundorte. Die JARs und Analyseausgaben gehören nicht zum Projekt.

| Status | JAR | Größe | SHA-256 | Mod-ID / Version | Minecraft / Loader |
|---|---|---:|---|---|---|
| VERIFIED | `/home/alex/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/TerrArchitecture v7/minecraft/mods/aeroworks-1.3.0.jar` | 675196 | `f836748d2bbad5b60fffef559418b74688621bf8e77710e6e0d5437c56ed2c78` | `aeroworks` 1.3.0 | 1.21.1 / JavaFML `[4,)` |
| VERIFIED | `/home/alex/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/TerrArchitecture v7/minecraft/mods/create-aeronautics-bundled-1.21.1-1.3.0.jar` | 33120013 | `482c90e0e6fe72f33fe7abb079e5fda581cd660ee53c38c44448a64283e1044c` | `aeronautics_bundled` 1.3.0; nested `aeronautics`, `simulated`, `offroad` 1.3.0 | 1.21.1 / LowCodeFML plus nested JavaFML |
| VERIFIED | `/home/alex/.gradle/caches/modules-2/files-2.1/com.simibubi.create/create-1.21.1/6.0.10-281/79f11cca15c31cf0e6730fa343a443bf686ee39e/create-1.21.1-6.0.10-281.jar` | 19123767 | `2fe00cb77d68019f2af55494dc41601cd7f6735aab8a22ee0b0ebd04c808bc9a` | `create` 6.0.10 | 1.21.1 / JavaFML |
| VERIFIED | `/home/alex/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/TerrArchitecture v7/server/mods/cc-tweaked-1.21.1-forge-1.119.0.jar` | 3125991 | `169e2fe0445e320562c0568baa4c796a69a3464a0a5e902c484be1be3e326a0b` | `computercraft` 1.119.0 | 1.21.1 / JavaFML |
| VERIFIED | `/home/alex/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/TerrArchitecture v7/minecraft/mods/sable-neoforge-1.21.1-2.0.1.jar` | 12888110 | `f0513d490dc099a7271b5e29b5040a1ff556219dee1007df3fe29355fd7ff68d` | `sable` 2.0.1 | 1.21.1 / JavaFML |
| VERIFIED | `/home/alex/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/TerrArchitecture v7/minecraft/mods/drivebywire-0.2.9.jar` | 269068 | `5f8eaa10a6d61aa7330ed997c4f812509c55f02f7213afbebde77c4af17c23c3` | `drivebywire` 0.2.9 | 1.21.1 / JavaFML `[4,)` |
| VERIFIED | `/home/alex/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/21.1.231/bfbe6f443c166fb427ab0b54fa2f8dab9e8a7949/neoforge-21.1.231-universal.jar` | 3531240 | `27ddf2c1cbf085332b800bfbf74250fa5c3148588827770c3b7d86bb7b11a86b` | `neoforge` 21.1.231 | 1.21.1-Linie / JavaFML |

Hinweis: Im Client-Modverzeichnis liegt CC:Tweaked 1.120.0, im Server-Modverzeichnis die geplante
1.119.0. Für diesen Bericht und den deklarierten Zielbereich wurde 1.119.0 verwendet. Die Create-
Display-Link-Integration wurde aus der Create-6.0.10-JAR geprüft; sie ist Create-Code, nicht Code aus
der CC:Tweaked-JAR.

Nachweis:

```bash
./tools/inspect_mod_jars.sh
sha256sum "/pfad/zur/mod.jar"
unzip -p "/pfad/zur/mod.jar" META-INF/neoforge.mods.toml
```

Deklarierte relevante Abhängigkeiten, Status VERIFIED:

- Create 6.0.10: NeoForge `[21.1.219,)`, Minecraft `[1.21.1]`, clientseitig Flywheel
  `[1.0.0,2.0)`, Ponder `[1.0.82,)` auf beiden Seiten.
- Das gebündelte Aeronautics-Artefakt verlangt NeoForge `[21.1.228,)` und enthält die drei
  Jar-in-Jar-Mods `aeronautics`, `simulated` und `offroad` 1.3.0. `aeronautics` verlangt Create
  `[6.0.10,)`, Sable `[2.0.0,3.0.0)` und `simulated` `[1.3.0,)`.
- CC:Tweaked 1.119.0 verlangt NeoForge `[21.1.9,21.2)`.
- Sable 2.0.1 verlangt NeoForge `[21.1.228,)` und Minecraft `[1.21.1]`; Create
  `[6.0.10,6.1.0)` und Flywheel `[1.0.6,)` sind in seiner Metadatei optional/clientseitig.
- Drive By Wire 0.2.9 verlangt NeoForge `[21.1.219,)`, Minecraft `[1.21.1]`, Create `[6.0.9,)`
  und Sable `[1.0.6,)`; `create_tweaked_controllers` ist optional. Es lädt
  `drivebywire.mixins.json`.
- NeoForge 21.1.231 deklariert Mod-ID `neoforge` und JavaFML.

## 3. Aeroworks Mod-Metadaten

Status: VERIFIED

Aeroworks deklariert `aeroworks` 1.3.0 und die Abhängigkeiten NeoForge `[21,)`, Minecraft
`[1.21.1]`, Create `[6.0.0,)`, Sable `[1.1.0,)` sowie optional Drive By Wire `[0.2.8,)`. Geladen
werden `aeroworks.mixins.json` und, nur mit Drive By Wire, `aeroworks-drivebywire.mixins.json`.
Die Kern-Mixinliste ist leer; drei Client-Mixins betreffen Eingabe/GameRenderer. Es gibt keine
Aeroworks-Mixin-Injektion in das Control-Desk-System selbst.

Nachweis:

```bash
unzip -p "/pfad/aeroworks-1.3.0.jar" META-INF/neoforge.mods.toml
unzip -p "/pfad/aeroworks-1.3.0.jar" aeroworks.mixins.json
```

## 4. Control Desk Block und BlockEntity

### Block

Status: VERIFIED

- Registry-ID: `aeroworks:control_desk`.
- Klasse: `com.mred231.aeroworks.content.controls.ConsoleDeskBlock`.
- Signatur: `public class ConsoleDeskBlock extends ConsoleBlock`.
- Basisklasse: `com.mred231.aeroworks.content.controls.ConsoleBlock extends Block implements
  EntityBlock, IBE<ConsoleBlockEntity>, IWrenchable, SpecialBlockItemRequirement`.
- Konstruktor: `ConsoleDeskBlock(BlockBehaviour.Properties, ConsoleType)`.

Die ID folgt direkt aus dem Aufruf
`Aeroworks.getRegistrate().block("control_desk", ...)` in `AeroworksConsoles`.

### BlockEntity

Status: VERIFIED

- Registry-ID des `BlockEntityType`: `aeroworks:console`.
- Klasse: `com.mred231.aeroworks.content.controls.ConsoleBlockEntity`.
- Signatur: `public class ConsoleBlockEntity extends SmartBlockEntity`.
- Konstruktor: `ConsoleBlockEntity(BlockEntityType<?>, BlockPos, BlockState)`.
- Lebenszyklus: `public void initialize()`, `public void tick()`, `public void invalidate()`.
- Speicherung/Synchronisation:
  `public void write(CompoundTag, HolderLookup.Provider, boolean)` und
  `protected void read(CompoundTag, HolderLookup.Provider, boolean)`.
- Item-Komponenten: `protected void collectImplicitComponents(DataComponentMap.Builder)` und
  `protected void applyImplicitComponents(BlockEntity.DataComponentInput)`.

Der Registrate-Aufruf lautet `blockEntity("console", ConsoleBlockEntity::new)` und bindet alle von
`AeroworksConsoles.validBlocks()` gelieferten Konsolenblöcke.

Nachweis:

```bash
javap -classpath "/pfad/aeroworks-1.3.0.jar" -p -s \
  com.mred231.aeroworks.content.controls.ConsoleBlockEntity
javap -classpath "/pfad/aeroworks-1.3.0.jar" -p -c \
  com.mred231.aeroworks.AeroworksBlockEntityTypes
```

## 5. Modular-Controls-System

Status: VERIFIED

Ein Modultyp ist der Record
`ModuleType(List<SocketType> shanks, List<ControlChannel> channels, List<ModulePart> parts,
String summaryKey, boolean composedItemModel)`. `ModuleType.CODEC` ist ein
`com.mojang.serialization.Codec<ModuleType>`. Montierte Instanzen sind
`final class MountedModule`; Items sind `class ModuleItem extends Item`. Die eingebauten Typen sind
keine Enum-Konstanten und keine geschlossene Switch-Liste.

Bestätigte Typ-IDs:

- `aeroworks:lever`
- `aeroworks:button_panel`
- `aeroworks:button_keypad`
- `aeroworks:joystick`
- `aeroworks:button`
- `aeroworks:wheel`
- `aeroworks:throttle_quadrant`
- `aeroworks:yoke`

Die zugehörigen Item-IDs enden jeweils auf `_module`, beispielsweise
`aeroworks:throttle_quadrant_module`.

Nachweis:

```bash
javap -classpath "/pfad/aeroworks-1.3.0.jar" -p -s \
  com.mred231.aeroworks.content.controls.ModuleType
javap -classpath "/pfad/aeroworks-1.3.0.jar" -p -c \
  com.mred231.aeroworks.AeroworksModuleTypes
```

## 6. Modulregistrierung

Status: VERIFIED

Klasse: `com.mred231.aeroworks.content.controls.ModuleTypes`.

```java
public static synchronized ModuleType register(ResourceLocation id, ModuleType type)
public static ModuleType register(ResourceLocation id, ModuleType.Builder builder)
public static synchronized void freeze()
public static ModuleType get(ResourceLocation id)
public static ResourceLocation idOf(ModuleType type)
public static Map<ResourceLocation, ModuleType> all()
```

Intern bestehen `private static final Map<ResourceLocation, ModuleType> BY_ID`, eine Identity-Map
für den Rückweg sowie eine unveränderliche Sicht. Registrierung ist bis `freeze()` ausdrücklich
zulässig; die Fehlermeldung fordert Registrierung aus dem Mod-Konstruktor. Aeroworks registriert
den Freeze als Listener für `FMLLoadCompleteEvent`, nicht direkt am Ende seines Konstruktors.

Antwort: Ja. Drittmods können in Aeroworks 1.3.0 neue Desk-Modultypen über die öffentliche
`ModuleTypes.register(...)`-Funktion mit eigener `ResourceLocation` registrieren. Zusätzlich muss
ein passendes Item registriert werden. `ModuleItem(ModuleType, Item.Properties)` ist öffentlich und
ordnet Typ und Item intern zu. Kein Access Transformer, Invoker, Accessor oder Reflection ist dafür
nötig.

Einschränkung, Status VERIFIED: `ModuleType` verlangt mindestens einen Shank, Summary-Key und
mindestens einen ControlChannel oder Sub-Socket. Ein reines Display ohne Eingabekanal kann daher
nicht als leerer Typ gebaut werden; die konkrete, semantisch saubere Modellierung muss noch getestet
werden.

Nachweis:

```bash
javap -classpath "/pfad/aeroworks-1.3.0.jar" -p -s -c \
  com.mred231.aeroworks.content.controls.ModuleTypes
```

## 7. Modulspeicherung

Status: VERIFIED

`ConsoleBlockEntity` besitzt `private final MountedModule[] modules`; der Arrayindex ist der
Socket-/Slotindex. In `write(...)` wird eine NBT-Liste `Modules` geschrieben. Jeder Eintrag erhält
`Socket` (int). `MountedModule.write(...)` speichert:

- `Type`: ResourceLocation-String aus `ModuleTypes.idOf(type)`,
- `Values`: Compound von Channel-ID zu int,
- `Config`: über `ModuleConfig.CODEC`,
- optional `CustomName`: über `ComponentSerialization.CODEC`,
- optional rekursive `Heads` für Submodule.

Position, Rotation und Ausrichtung werden nicht pro montierter Instanz gespeichert. Sie kommen aus
dem statischen `ConsoleType`/`Socket` und den `ModulePart`-Transformationen. Der Desk hat drei
Sockets: zwei `SMALL` bei Index 0/1 und einen `LARGE` bei Index 2; Index 2 ist jeweils exklusiv zu
0 und 1.

Änderungen verwenden `setChanged()` und `sendData()`. `SmartBlockEntity.sendData()` liefert den
Client-Sync des `clientPacket`-NBT; es existiert kein separates S2C-Desk-Modul-Payload. Beim Kopieren
als Item wird `aeroworks:controller_contents` als persistente und netzwerksynchronisierte
DataComponent mit `ControllerContents.CODEC` und `ControllerContents.STREAM_CODEC` verwendet.

Nachweis:

```bash
javap -classpath "/pfad/aeroworks-1.3.0.jar" -p -c \
  com.mred231.aeroworks.content.controls.ConsoleBlockEntity
javap -classpath "/pfad/aeroworks-1.3.0.jar" -p -c \
  com.mred231.aeroworks.content.controls.MountedModule
```

## 8. Slot- und Hit-Test-System

Status: VERIFIED

Relevante öffentliche Signaturen von `ConsoleBlockEntity`:

```java
public int socketCount()
public MountedModule module(int socket)
public boolean isSocketFree(int socket)
public boolean isSocketBlocked(int socket)
public boolean mount(int socket, ItemStack stack)
public ItemStack dismount(int socket)
public List<MountSpot> freeMounts(ModuleType type)
public MountTarget nearestFreeMount(Vec3 from, Vec3 to, ModuleType type)
public MountTarget nearestOccupiedMount(Vec3 from, Vec3 to)
public boolean attachHead(int socket, String path, ItemStack stack)
public ItemStack detachHead(int socket, String path)
public Matrix4f socketMatrix(int socket)
public Matrix4f mountFrameFor(MountTarget target)
```

`MountTarget` ist `record MountTarget(int socket, @Nullable String subPath)`. Der lokale Hit-Test
projiziert den Blickstrahl auf die Socket-Zentren. Auf Sable-SubLevels werden Start und Ende über
`SubLevel.logicalPose().transformPositionInverse(...)` lokalisiert. Belegte Mounts nutzen Radius
0.5; freie Mounts `SocketType.halfExtent() + 0.15`.

Installation erfolgt in `ConsoleBlock.useItemOn(...)`: `ModuleItem.typeOf(stack)`, Blickstrahl,
`nearestFreeMount`, dann serverseitig `mount` oder `attachHead`, Itemverbrauch und Sound. Entfernung
erfolgt in `onWrenched(...)` über `nearestOccupiedMount`, danach `dismount`/`detachHead`; das Item
wird ins Inventar gelegt. Bei ungültigen rekursiven Heads erzeugt `Block.popResource(...)` Drops.

Nachweis:

```bash
javap -classpath "/pfad/aeroworks-1.3.0.jar" -p -s -c \
  com.mred231.aeroworks.content.controls.ConsoleBlock
javap -classpath "/pfad/aeroworks-1.3.0.jar" -p -s -c \
  com.mred231.aeroworks.content.controls.ConsoleBlockEntity
```

## 9. Rendering

Status: VERIFIED

- BER: `com.mred231.aeroworks.content.controls.ConsoleRenderer extends
  SafeBlockEntityRenderer<ConsoleBlockEntity>`.
- Fallback-Methode:
  `protected void renderSafe(ConsoleBlockEntity, float, PoseStack, MultiBufferSource, int, int)`;
  Deskriptor `(Lcom/mred231/aeroworks/content/controls/ConsoleBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V`.
- Flywheel-Pfad: `com.mred231.aeroworks.content.controls.ConsoleVisual extends
  AbstractBlockEntityVisual<ConsoleBlockEntity> implements SimpleDynamicVisual`.
- Dispatcher/Transformation: `ModulePartRender.flatten(...)`, `displayValues(...)` und
  `apply(...)`. Reihenfolge: Blockzentrum, Blockrotation, Socketoffset, Socketorientation,
  Translation `(-0.5, 0, -0.5)`, danach die Part-Schrittkette. Schritte sind Translate, Rotate oder
  Slide; eine allgemeine Skalierungsstufe existiert nicht.

`ConsoleRenderer.renderSafe` läuft nur, wenn Flywheel-Visualisierung nicht unterstützt wird. Ein
Mixin ausschließlich dort wäre funktional unvollständig. Die kleinste stabile Erweiterungsgrenze
für statische Modulmodelle ist die öffentliche `ModuleType.parts()`-/`ModulePartRender`-Pipeline.
Für dynamische Ziffern existiert jedoch kein öffentlicher per-Modultyp Renderer-Callback.

Status: INFERRED

Die spätere dynamische Anzeige braucht entweder eigene Flywheel-Instanzen plus einen Fallback-Hook
oder einen Sable-fähigen unabhängigen Renderpfad. Zwei gezielte Hooks (Fallback und Flywheel) sind
stabiler als ein vollständiger Ersatz des Renderers. Ein pauschales `render`-HEAD/TAIL-Mixin wurde
in dieser Phase bewusst nicht erstellt.

Nachweis:

```bash
javap -classpath "/pfad/aeroworks-1.3.0.jar" -p -s -c \
  com.mred231.aeroworks.content.controls.ConsoleRenderer
javap -classpath "/pfad/aeroworks-1.3.0.jar" -p -s -c \
  com.mred231.aeroworks.content.controls.ConsoleVisual
```

## 10. Netzwerk und Synchronisation

Status: VERIFIED

`AeroworksPackets.onRegister(RegisterPayloadHandlersEvent)` verwendet
`event.registrar("aeroworks").versioned("1")` und `PayloadRegistrar.playToServer(...)`. Für die
Desk-Steuerung bestehen C2S-Payloads wie:

```java
record C2SConsoleChannel(BlockPos pos, byte socket, String channel, byte value)
record C2SExitConsoleControl(BlockPos pos)
record C2SOpenModuleMenu(BlockPos pos, byte socket, String subPath)
record C2SModuleSetName(BlockPos pos, byte socket, String subPath, String name)
record C2SModuleSetFrequency(BlockPos pos, byte socket, String channel, int slot, ItemStack stack)
```

Jeder Record implementiert `CustomPacketPayload` und besitzt `TYPE` sowie `STREAM_CODEC`. Handler
validieren den ServerPlayer und verwenden je nach Operation `reachableConsole`, `editableConsole`
oder `controlledConsole`. Reichweite, Desk-Lock, Socket, Channel, Flags und Frequenzslot werden
serverseitig geprüft. Montage/Demontage läuft über die normale Blockinteraktion, nicht über ein
eigenes Aeroworks-Payload. Der Desk-Zustand wird durch BlockEntity-Datenpakete synchronisiert.

Nachweis:

```bash
javap -classpath "/pfad/aeroworks-1.3.0.jar" -p -s -c \
  com.mred231.aeroworks.AeroworksPackets
javap -classpath "/pfad/aeroworks-1.3.0.jar" -p -s -c \
  com.mred231.aeroworks.content.controls.C2SConsoleChannel
```

## 11. Create DisplayTarget API

Status: VERIFIED

`com.simibubi.create.api.behaviour.display.DisplayTarget` ist eine abstrakte Klasse, kein Interface.

```java
public abstract void acceptText(int line, List<MutableComponent> text,
                                DisplayLinkContext context)
public abstract DisplayTargetStats provideStats(DisplayLinkContext context)
public static DisplayTarget get(LevelAccessor level, BlockPos pos)
public static final SimpleRegistry<Block, DisplayTarget> BY_BLOCK
public static final SimpleRegistry<BlockEntityType<?>, DisplayTarget> BY_BLOCK_ENTITY
```

Die eigentliche Registry ist `CreateBuiltInRegistries.DISPLAY_TARGET` mit dem Key
`CreateRegistries.DISPLAY_TARGET` (`create:display_target`). CreateRegistrate bietet
`displayTarget(String, Supplier<T>)`; der `SimpleBuilder` kann Targets mit Block oder
BlockEntityType assoziieren. `DisplayTarget.get(level, pos)` prüft zuerst `BY_BLOCK` mit BlockState,
dann `BY_BLOCK_ENTITY` mit dem BE-Type und zuletzt modded SignBlockEntities.

`DisplayTargetStats(int maxRows, int maxColumns, DisplayTarget type)` definiert Zeilen zuerst,
Spalten danach. Reservierungen liegen in `target.getPersistentData().DisplayLink.Line<n>` und
speichern die Position des Display Links; Zeile 0 wird nie reserviert.

Aufrufweg: `DisplayLinkBlockEntity.updateGatheredData()` bestimmt Target und Sources, erstellt den
`DisplayLinkContext` und ruft `activeSource.transferData(context, activeTarget, targetLine)`.
`DisplaySource.transferData(...)` ruft `provideStats`, dann `provideText` und schließlich
`activeTarget.acceptText(line, text, context)`.

Nachweis:

```bash
javap -classpath "/pfad/create-1.21.1-6.0.10-281.jar" -p -s \
  com.simibubi.create.api.behaviour.display.DisplayTarget
javap -classpath "/pfad/create-1.21.1-6.0.10-281.jar" -p -s -c \
  com.simibubi.create.api.behaviour.display.DisplaySource
```

## 12. CC:Tweaked Display-Link-Peripheral

Status: VERIFIED

Create implementiert
`com.simibubi.create.compat.computercraft.implementation.peripherals.DisplayLinkPeripheral`.
Bestätigte Lua-Funktionen: `setCursorPos`, `getCursorPos`, `getSize`, `write`, `writeBytes`,
`clearLine`, `clear`, `update`, außerdem `isColor`/`isColour`. Der Peripheral-Typ ist
`Create_DisplayLink`.

Antworten auf die Leitfragen:

1. Ja. `write` schreibt zunächst Zeilenstrings in die NBT-Liste
   `sourceConfig["ComputerSourceList"]`; Cursorwerte sind zero-based `AtomicInteger`-Felder im
   Peripheral, Lua-Ein-/Ausgabe ist one-based.
2. `acceptText` wird erst durch `update()` -> `DisplayLinkBlockEntity.tickSource()` ->
   `updateGatheredData()` -> `ComputerDisplaySource.provideText()` ->
   `DisplaySource.transferData()` aufgerufen. `write`, `clear` und `clearLine` allein übertragen
   noch nichts. `ComputerDisplaySource.shouldPassiveReset()` ist `false`.
3. Das Target erhält den konfigurierten Startzeilenindex, eine `List<MutableComponent>` mit je einem
   literal Component pro gepufferter Zeile und den `DisplayLinkContext`.
4. Nein. `ComputerDisplaySource.provideText` beschneidet weder Zeilenanzahl noch Stringlänge anhand
   von `DisplayTargetStats`; das Target muss Grenzen behandeln.
5. `DisplayTargetStats` ist `(maxRows, maxColumns, type)`. `getSize()` gibt exakt
   `{stats.maxRows(), stats.maxColumns()}` zurück, also Zeilen/Höhe vor Spalten/Breite. Das weicht
   von der bei Terminal-APIs oft erwarteten Breite-Höhe-Reihenfolge ab.
6. `DisplayTarget.get(level, targetPosition)` prüft Block-Assoziation vor BE-Type-Assoziation. Die
   Zielposition ist `DisplayLinkBlockEntity.worldPosition + targetOffset`.

Die ursprüngliche Display-Link-Planung sah kein eigenes Peripheral vor. CC-Aeroworks ergänzt nun
ein direktes Desk-Peripheral; der optionale Display-Link-Pfad bleibt:

```text
CC:Tweaked -> Create DisplayLinkPeripheral -> ComputerDisplaySource
           -> Create DisplayTarget -> Aeroworks Desk -> CC-Aeroworks-Displaymodul
```

Nachweis:

```bash
javap -classpath "/pfad/create-1.21.1-6.0.10-281.jar" -p -s -c \
  com.simibubi.create.compat.computercraft.implementation.peripherals.DisplayLinkPeripheral
```

## 13. Sable-Mixins und Kollisionsrisiken

Status: VERIFIED

Sable 2.0.1 lädt `sable.mixins.json` und `sable-neoforge.mixins.json`. Es enthält bereits Mixins für
Create Display Links, `SafeBlockEntityRenderer`, Flywheel-Visualisierung, BlockEntity-Rendering,
Raycasts und SubLevel-Rendering. Insbesondere injiziert
`dev.ryanhcode.sable.neoforge.mixin.compatibility.create.display_link.DisplayLinkBlockEntityMixin`
bei `TAIL`, cancellable, in `DisplayLinkBlockEntity.getTargetPosition()` und korrigiert die
Reichweite über SubLevels.

Status: INFERRED

Ein CC-Aeroworks-Mixin in Create `getTargetPosition`, globale Raycastmethoden,
`SafeBlockEntityRenderer.render` oder den globalen BlockEntityRenderDispatcher hätte hohes
Kollisionsrisiko. Aeroworks' eigene `ConsoleBlockEntity.nearestMount(...)` berücksichtigt Sable
bereits. Bevorzugt werden daher Aeroworks-spezifische Hooks mit vollständigem Deskriptor.

Nachweis:

```bash
unzip -p "/pfad/sable-neoforge-1.21.1-2.0.1.jar" sable-neoforge.mixins.json
javap -classpath "/pfad/sable-neoforge-1.21.1-2.0.1.jar" -p -v \
  dev.ryanhcode.sable.neoforge.mixin.compatibility.create.display_link.DisplayLinkBlockEntityMixin
```

## 14. Verifizierte Klassen und Signaturen

Status: VERIFIED

| Klasse | Zentrale Signatur/Bedeutung |
|---|---|
| `com.mred231.aeroworks.content.controls.ModuleTypes` | öffentliche RL-basierte Registrierung mit Freeze |
| `com.mred231.aeroworks.content.controls.ModuleType` | Record plus `CODEC` und öffentlicher Builder |
| `com.mred231.aeroworks.content.controls.ModuleItem` | `ModuleItem(ModuleType, Item.Properties)`; Typ-Item-Zuordnung |
| `com.mred231.aeroworks.content.controls.MountedModule` | NBT-Serialisierung über `write/read`; rekursive Heads |
| `com.mred231.aeroworks.content.controls.ConsoleBlockEntity` | Slotarray, Hit-Test, Montage, NBT und BE-Sync |
| `com.mred231.aeroworks.content.controls.ConsoleRenderer` | Nicht-Flywheel-Fallback-BER |
| `com.mred231.aeroworks.content.controls.ConsoleVisual` | Flywheel-DynamicVisual |
| `com.simibubi.create.api.behaviour.display.DisplayTarget` | abstraktes Display-Target mit `acceptText/provideStats` |
| `com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats` | `(maxRows, maxColumns, type)` |
| `com.simibubi.create.compat.computercraft.implementation.peripherals.DisplayLinkPeripheral` | existierendes CC-Peripheral mit Textpuffer |

Die vollständigen öffentlichen/privaten Signaturen können reproduzierbar mit den in den jeweiligen
Abschnitten angegebenen `javap -p -s`-Befehlen erzeugt werden.

## 15. Offene Fragen

- Status: UNKNOWN — Welcher Aeroworks-ControlChannel ist für ein reines Ausgabe-Modul semantisch
  akzeptabel, da `ModuleType` mindestens Channel oder Sub-Socket verlangt?
- Status: UNKNOWN — Der beste Flywheel-Hook für dynamische eigene Ziffern ist noch nicht durch einen
  Lauf im vollständigen Modpack validiert.
- Status: VERIFIED — Die Implementierung nutzt `MountedModule.customName`; Aeroworks persistiert,
  synchronisiert und übernimmt dieses Feld beim Modul-Drop.
- Status: UNKNOWN — Verhalten des Create-CC-Peripherals bei `activeTarget == null` in `getSize()` ist
  nicht Teil von CC-Aeroworks und wurde nicht im Spiel getestet; der Quellpfad dereferenziert das Feld.
- Status: UNKNOWN — Client und Server des gefundenen Modpacks haben unterschiedliche
  CC:Tweaked-Versionen (1.120.0/1.119.0); die Zielinstallation muss vereinheitlicht werden.

## 16. Empfohlener Implementierungspfad

Empfehlung: Pfad A — öffentliche Aeroworks-Modul-API.

Status: VERIFIED für API-Entscheidung und kompilierte CC-Aeroworks-Erweiterung.

Vorgehen:

1. `ModuleTypes.register(moduleId, ModuleType.builder(...))` im CC-Aeroworks-Konstruktor vor
   Aeroworks `ModuleTypes.freeze()` aufrufen.
2. Eigene `ModuleItem`-Unterklassen/Items für zwei- und dreistellige Displays registrieren; keine
   Aeroworks-Assets übernehmen.
3. Create-`DisplayTarget` in `CreateRegistries.DISPLAY_TARGET` registrieren und über
   `DisplayTarget.BY_BLOCK_ENTITY` mit `aeroworks:console` assoziieren. `provideStats` meldet die
   Desk-Kapazität als rows/columns; `acceptText` verteilt begrenzten Text auf belegte Displays.
4. Displaydaten in `MountedModule.customName` speichern und über `setModuleName` synchronisieren.
5. Kein separates S2C-Textpayload; der bestätigte Aeroworks-SmartBlockEntity-Sync wird genutzt.
6. Dynamische Darstellung zuletzt implementieren und sowohl Fallback- als auch Flywheel-Pfad testen.

Pfad B ist unnötig: Registry und Factory sind öffentlich. Pfad C ist falsch: Es gibt weder ein
geschlossenes Enum noch eine harte Switch-Liste für Modultypen.

## 17. Vorgeschlagene Mixin-Ziele

Status: VERIFIED für Deskriptoren und geladene Mixins; interaktive Renderprüfung bleibt offen.

1. Fallback-Rendering:
   Ziel `ConsoleRenderer.renderSafe` mit dem in Abschnitt 9 angegebenen Deskriptor. Kein pauschales
   `render`; möglicher Hook nach dem letzten Aeroworks-Part-Render oder am `TAIL`, nur um einen
   kleinen CC-Aeroworks-Fallback-Renderer aufzurufen. Fallback bei Nichtübereinstimmung: Required
   Mixin schlägt laut sichtbar fehl; keine stille Deaktivierung.
2. Flywheel:
   Ziel `ConsoleVisual.rebuildIfNeeded()V`, `applyTransforms(F)V` und `_delete()V` für den
   Lebenszyklus eigener Zifferninstanzen. Keine Änderung der Aeroworks-`parts`-Liste und kein
   `@Overwrite`. Vor Umsetzung muss ein stabiler Invoke-/TAIL-Punkt ausgewählt werden.
3. Datenlebenszyklus, nur falls Attachments nicht genügen:
   gezielte RETURN-Hooks in `ConsoleBlockEntity.mount(ILItemStack;)Z` und
   `dismount(I)LItemStack;`, plus `attachHead`/`detachHead` nur falls Displays als Heads zugelassen
   werden. Parameter und Rückgabewert genügen; lokale Variablen sollten nicht gecaptured werden.

Kein Mixin ist für `ModuleTypes.register` oder Create-DisplayTarget-Registrierung vorgesehen.

## 18. Risiken und Versionsbindung

- Status: VERIFIED — Aeroworks' Registry ist eine eigene statische Map, keine NeoForge-Registry;
  Registrierung nach `freeze()` wirft eine Exception.
- Status: VERIFIED — Die DisplayTarget-Statistik und `getSize()` verwenden rows vor columns.
- Status: VERIFIED — Aeroworks hat getrennte Flywheel- und Fallback-Renderpfade.
- Status: INFERRED — Renderer- und Lebenszyklus-Mixins wären binär an Aeroworks 1.3.0 gebunden;
  deshalb ist der Abhängigkeitsbereich `[1.3.0,1.3.1)`.
- Status: INFERRED — Sable-SubLevel-Rendering erhöht das Risiko globaler Renderhooks. CC-Aeroworks
  sollte Sable 2.0.1 eng testen und keine globalen Create-/Minecraft-Renderer mixen.
- Status: VERIFIED — Keine fremden Modelle, Texturen, Sprachdateien oder Java-Quellen wurden ins
  Projekt übernommen. Vineflower-Ausgaben liegen ausschließlich unter `.codex-work/`.

## 19. Reproduktionsbefehle

```bash
./tools/inspect_mod_jars.sh

javap -classpath "/pfad/aeroworks-1.3.0.jar" -p -s \
  com.mred231.aeroworks.content.controls.ModuleTypes
javap -classpath "/pfad/aeroworks-1.3.0.jar" -p -c \
  com.mred231.aeroworks.content.controls.ConsoleBlockEntity
javap -classpath "/pfad/aeroworks-1.3.0.jar" -p -s -c \
  com.mred231.aeroworks.content.controls.ConsoleRenderer

javap -classpath "/pfad/create-1.21.1-6.0.10-281.jar" -p -s -c \
  com.simibubi.create.api.behaviour.display.DisplayTarget
javap -classpath "/pfad/create-1.21.1-6.0.10-281.jar" -p -s -c \
  com.simibubi.create.compat.computercraft.implementation.peripherals.DisplayLinkPeripheral

unzip -p "/pfad/sable-neoforge-1.21.1-2.0.1.jar" sable-neoforge.mixins.json
./gradlew compileJava
./gradlew processResources
./gradlew build
```
