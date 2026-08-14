#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


module_types = read("src/main/kotlin/de/teutonstudio/ccaeroworks/registry/CCModuleTypes.kt")
source = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/CombinedInputSource.kt")
controller = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/DisplayCombinedInputController.kt")
combined_ui = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenCombinedInputMixin.kt")
client = read("src/main/kotlin/de/teutonstudio/ccaeroworks/client/CCAeroworksClient.kt")
coordinator = read("src/main/kotlin/de/teutonstudio/ccaeroworks/input/CombinedInputCoordinator.kt")
dbw_guard = read("src/main/java/de/teutonstudio/ccaeroworks/mixin/client/DriveByWireDisplaySourceMixin.java")
mixins = read("src/main/resources/cc_aeroworks.mixins.json")
workflow = read(".github/workflows/verify.yml")

# Large interactive display modules must expose actual Aeroworks ControlChannels. The two Yoke
# channels are cloned so their range/options stay native while the public ids become x/y.
require("interactive = true" in module_types, "large display module registration must be interactive")
require('addClonedChannel(builder, YOKE_X_TEMPLATE, "x")' in module_types,
        "interactive displays must expose real Aeroworks x channel")
require('addClonedChannel(builder, YOKE_Y_TEMPLATE, "y")' in module_types,
        "interactive displays must expose real Aeroworks y channel")
require("cloneRecordWithId" in module_types and "appendChannel" in module_types,
        "display channels must derive from Aeroworks ControlChannel records")

# X/Y are normal ModuleScreen columns but display pointer modules are Combined-only.
require('"cc_aeroworks:three_digit_display" to listOf(X_CHANNEL, Y_CHANNEL)' in source,
        "three-digit display must advertise x/y combined channels")
require('"cc_aeroworks:large_radar_display" to listOf(X_CHANNEL, Y_CHANNEL)' in source,
        "large radar must advertise x/y combined channels")
require("fun isCombinedOnly(module: MountedModule): Boolean = isDisplayPointerModule(module)" in source,
        "display pointer channels must be Combined-only")
require("forceCombined(invoker, module, column, index)" in combined_ui,
        "ModuleScreen must force display x/y channels into Combined mode")
require("ccaeroworks_sendBind(index, \"key.keyboard.k\")" in combined_ui,
        "blank display channel activation binding must receive the existing default")

# Pointer activation must come from the channel binding, not a separate global key/button.
require("CombinedInputSource.activationBinding" in controller,
        "display pointer controller must read per-channel activation bindings")
require("activeAxes" in controller and "bindingActivates" in controller,
        "display pointer controller must derive active axes from x/y channel state")
require("DisplayInteractionKey" not in controller,
        "display pointer controller must not depend on the removed global display key")
require("DisplayInteractionKey" not in client,
        "client bootstrap must not register the removed global display key")
require(not (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/input/DisplayInteractionKey.kt").exists(),
        "global DisplayInteractionKey source must be removed")
require(not (ROOT / "src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenDisplayInteractionMixin.kt").exists(),
        "floating ModuleScreen Display-Bedienung button mixin must be removed")
require('"client.ModuleScreenDisplayInteractionMixin"' not in mixins,
        "floating display condition button mixin must not be registered")

# Combined input ownership keeps display and vehicle controls from consuming the same mouse delta;
# Shift remains the explicit camera-only override.
require("enum class Owner" in coordinator and "CONTROL" in coordinator and "DISPLAY" in coordinator,
        "combined input coordinator must distinguish control and display mouse ownership")
require("isShiftCameraOnly" in coordinator and "claimDisplay" in coordinator,
        "combined input coordinator must retain Shift override and display claim")

# Real display ControlChannels are configuration-only and must not become Drive By Wire outputs.
require("CombinedInputSource.INSTANCE.isDisplayPointerModule(module)" in dbw_guard,
        "Drive By Wire source selection must reject display pointer channels")
require('"client.DriveByWireDisplaySourceMixin"' in mixins,
        "Drive By Wire display-source guard must be registered")

require((ROOT / "src/main/resources/assets/cc_aeroworks/textures/gui/combined_input_placeholder.png").is_file(),
        "Combined mode icon for ModuleScreen is missing")
require("python3 tools/verify-display-combined-input.py" in workflow,
        "workflow must enforce display combined-input contract")

print("Validated display Combined input: real Aeroworks x/y channels, per-channel bindings, no floating global Display-Bedienung button, coordinated mouse ownership, and DBW source isolation.")
