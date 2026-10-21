package de.teutonstudio.ccaeroworks.gametest;

import com.mred231.aeroworks.content.controls.console.ConsoleBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.shared.ModRegistry;
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskAccess;
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksDeskService;
import de.teutonstudio.ccaeroworks.compat.aeroworks.AeroworksTypes;
import de.teutonstudio.ccaeroworks.computer.ConsoleComputers;
import de.teutonstudio.ccaeroworks.config.CCServerConfig;
import de.teutonstudio.ccaeroworks.display.*;
import de.teutonstudio.ccaeroworks.registry.CCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3f;

/** Exercises real console NBT, mixed-in state, mounted modules and server config together. */
@GameTestHolder("cc_aeroworks")
@PrefixGameTestTemplate(false)
public final class DisplayGameTests {
    private static void assertExternalTouch(GameTestHelper h, ConsoleBlockEntity desk, boolean expected) {
        var received = new java.util.ArrayList<String>();
        var computer = (dan200.computercraft.api.peripheral.IComputerAccess) java.lang.reflect.Proxy.newProxyInstance(
            DisplayGameTests.class.getClassLoader(),
            new Class<?>[]{dan200.computercraft.api.peripheral.IComputerAccess.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getID" -> 123;
                case "getAttachmentName" -> "test";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "queueEvent" -> { received.add((String) args[0]); yield null; }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        var peripheral = new de.teutonstudio.ccaeroworks.compat.computercraft.ControlDeskPeripheral(desk);
        peripheral.attach(computer);
        try {
            // Deliberately bypass client geometry: the dispatcher must still enforce the tier.
            var forged = new DeskDisplayTouch(0, "front_left", "cc_aeroworks:two_digit_display", 1, 1, 14, 14, 0.0, 0.0);
            de.teutonstudio.ccaeroworks.computer.DeskDisplayInputDispatcher.dispatch(desk, forged,
                de.teutonstudio.ccaeroworks.network.DisplayPointerAction.TAP);
            h.assertTrue(received.contains("monitor_touch") == expected, "External touch bypassed owner capability");
            h.assertTrue(received.contains("cc_aeroworks_desk_display_input") == expected, "External display event capability mismatch");
        } finally {
            peripheral.detach(computer);
        }
    }

    @GameTest(template = "empty_console", batch = "display_settings")
    public static void resolutionPersistenceAndComputerDependentTouch(GameTestHelper h) throws Exception {
        boolean global = CCServerConfig.displayUseGlobalResolution.get();
        int configured = CCServerConfig.displayPartsPerBlock.get();
        int maximum = CCServerConfig.displayMaxIndividualPpb.get();
        try {
            CCServerConfig.displayPartsPerBlock.set(256);
            CCServerConfig.displayMaxIndividualPpb.set(256);
            CCServerConfig.displayUseGlobalResolution.set(false);
            BlockPos origin = new BlockPos(2, 2, 2);
            h.setBlock(origin, AeroworksTypes.INSTANCE.controlDeskBlock("control_desk").defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
            ConsoleBlockEntity desk = (ConsoleBlockEntity) h.getBlockEntity(origin);
            h.assertTrue(desk.mount(0, new ItemStack(CCItems.TWO_DIGIT_DISPLAY.get())), "Small display mount failed");
            h.assertTrue(desk.mount(1, new ItemStack(CCItems.TWO_DIGIT_DISPLAY.get())), "Second small display mount failed");
            h.setBlock(origin.north(), AeroworksTypes.INSTANCE.controlDeskBlock("control_desk").defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
            ConsoleBlockEntity largeDesk = (ConsoleBlockEntity) h.getBlockEntity(origin.north());
            h.assertTrue(largeDesk.mount(2, new ItemStack(CCItems.THREE_DIGIT_DISPLAY.get())), "Large display mount failed");
            var api = AeroworksDeskService.INSTANCE;
            api.setDisplayResolution(desk, 1, 16.0);
            api.setDisplayResolution(desk, 0, 32.0);
            api.setDisplayResolution(largeDesk, 2, 64.0);
            h.assertTrue(api.getDisplaySize(desk, 0).get("width") == 14, "Small individual size is wrong");
            h.assertTrue(api.getDisplaySize(largeDesk, 2).get("width") == 40, "Large individual size is wrong");
            api.setDisplayPixel(desk, 0, 14, 14, true);
            api.setDisplayResolution(desk, 0, 32.0);
            h.assertTrue(api.getDisplayPixel(desk, 0, 14, 14), "Unchanged PPB cleared pixels");
            api.setDisplayResolution(desk, 0, 64.0);
            h.assertTrue(!api.getDisplayPixel(desk, 0, 14, 14), "Changed PPB did not clear pixels");
            api.setDisplayPixel(desk, 0, 28, 28, true);
            var saved = desk.saveWithFullMetadata(h.getLevel().registryAccess());
            desk.loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(api.getDisplaySize(desk, 0).get("ppb") == 64 && api.getDisplayPixel(desk, 0, 28, 28), "Saved resolution/pixels were lost");
            h.assertTrue(api.getDisplaySize(desk, 1).get("ppb") == 16, "Other slot setting was lost");
            h.assertTrue(api.getModule(desk, 0).get("pixelWidth").equals(28), "Module size ignores individual PPB");
            h.assertTrue(api.getDisplay(desk, 0).get("pixelHeight").equals(28), "Display description size mismatch");
            try {
                api.setDisplayResolution(desk, 0, 64.5);
                h.fail("Fractional density was accepted");
            } catch (LuaException expected) { }
            h.assertTrue(api.getDisplayPixel(desk, 0, 28, 28), "Invalid request changed the raster");
            CCServerConfig.displayUseGlobalResolution.set(true);
            h.assertTrue(api.getDisplaySize(desk, 0).get("width") == 112, "Global mode did not override slot");
            try {
                api.setDisplayResolution(desk, 0, 32.0);
                h.fail("Global mode accepted an individual resolution");
            } catch (LuaException expected) { }
            CCServerConfig.displayUseGlobalResolution.set(false);
            h.assertTrue(api.getDisplaySize(desk, 0).get("ppb") == 64, "Global mode destroyed individual setting");
            CCServerConfig.displayMaxIndividualPpb.set(32);
            h.assertTrue(api.getDisplaySize(desk, 0).get("ppb") == 32, "Lowered server limit did not apply");
            CCServerConfig.displayMaxIndividualPpb.set(256);
            h.assertTrue(!DisplayTouchAccess.INSTANCE.enabled(desk), "Touch enabled without computer");
            assertExternalTouch(h, desk, false);
            ConsoleComputers.INSTANCE.install(desk, new ItemStack(ModRegistry.Items.COMPUTER_NORMAL.get()));
            h.assertTrue(!DisplayTouchAccess.INSTANCE.enabled(desk), "Normal computer enabled touch");
            assertExternalTouch(h, desk, false);
            api.setDisplayResolution(desk, 0, 32.0);
            h.assertTrue(DisplayBindings.INSTANCE.set(desk, 0, new DisplayBinding.LuaHandler("/touch.lua")), "Small display rejected handler");
            ConsoleComputers.INSTANCE.remove(desk);
            ConsoleComputers.INSTANCE.install(desk, new ItemStack(ModRegistry.Items.COMPUTER_ADVANCED.get()));
            h.assertTrue(DisplayTouchAccess.INSTANCE.enabled(desk), "Advanced computer did not enable touch");
            assertExternalTouch(h, desk, true);
            h.assertTrue(DeskDisplayGeometry.touch(desk, -1, .5, .5) == null, "Negative socket accepted");
            h.assertTrue(DeskDisplayGeometry.touch(desk, Integer.MAX_VALUE, .5, .5) == null, "Out of range socket accepted");
            for (int slot : new int[]{0, 1, 2}) {
                ConsoleBlockEntity surface = slot == 2 ? largeDesk : desk;
                var size = api.getDisplaySize(surface, slot);
                var touch = DeskDisplayGeometry.touch(surface, slot, 1.0, 1.0);
                h.assertTrue(touch != null && touch.getX() == size.get("width") && touch.getY() == size.get("height"), "Touch does not match slot raster");
                var transform = DeskDisplayGeometry.moduleTransform(surface, slot);
                Vector3f from = transform.transformPosition(new Vector3f(.5f, 1f, .5f));
                Vector3f to = transform.transformPosition(new Vector3f(.5f, -1f, .5f));
                Vec3 offset = Vec3.atLowerCornerOf(surface.getBlockPos());
                var pointer = DeskDisplayGeometry.resolveRay(surface,
                    new Vec3(from.x, from.y, from.z).add(offset), new Vec3(to.x, to.y, to.z).add(offset));
                h.assertTrue(pointer != null && pointer.getSocket() == slot, "Rotated display ray hit wrong slot");
            }
            // A member without its own computer inherits the active owner's capability.
            BlockPos neighbor = origin.north(2);
            h.setBlock(neighbor, AeroworksTypes.INSTANCE.controlDeskBlock("control_desk").defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
            ConsoleBlockEntity member = (ConsoleBlockEntity) h.getBlockEntity(neighbor);
            h.assertTrue(DisplayTouchAccess.INSTANCE.enabled(member), "Advanced owner did not enable member touch");
            ConsoleComputers.INSTANCE.install(member, new ItemStack(ModRegistry.Items.COMPUTER_NORMAL.get()));
            h.assertTrue(!DisplayTouchAccess.INSTANCE.enabled(desk), "Conflicting owners enabled touch");
            ConsoleComputers.INSTANCE.remove(member);
            ConsoleComputers.INSTANCE.remove(desk);
            ConsoleComputers.INSTANCE.install(desk, new ItemStack(ModRegistry.Items.COMPUTER_NORMAL.get()));
            h.assertTrue(DeskDisplayGeometry.touch(desk, 0, .5, .5) == null, "Normal computer still accepts small touch");
            h.assertTrue(DisplayBindings.INSTANCE.get(desk, 0) instanceof DisplayBinding.LuaHandler, "Computer replacement lost binding");
            desk.dismount(0);
            h.assertTrue(((DisplayResolutionStateAccess)desk).ccaeroworks_getDisplayPpb(0) == null, "Dismount retained slot PPB");
            desk.mount(0, new ItemStack(CCItems.TWO_DIGIT_DISPLAY.get()));
            h.assertTrue(api.getDisplaySize(desk, 0).get("ppb") == 256, "Replacement display inherited old PPB");
            h.assertTrue(api.getDisplaySize(desk, 1).get("ppb") == 16, "Dismount changed another slot");
            h.assertTrue(AeroworksDeskAccess.display(largeDesk, 2).getPartsPerBlock() == 64, "Other slot changed");
            h.succeed();
        } finally {
            CCServerConfig.displayUseGlobalResolution.set(global);
            CCServerConfig.displayPartsPerBlock.set(configured);
            CCServerConfig.displayMaxIndividualPpb.set(maximum);
        }
    }
}
