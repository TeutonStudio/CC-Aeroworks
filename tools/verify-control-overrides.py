#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


manager = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/control/ControlOverrideManager.kt")
context = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/control/ControlWriteContext.kt")
api = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerControlLuaApi.kt")
api_registry = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerConsoleAccess.kt")
block_entity = read("src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerControlDeskBlockEntity.kt")
mixin = read("src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/ConsoleBlockEntityControlOverrideMixin.kt")
mixins_json = read("src/main/resources/cc_aeroworks.mixins.json")
constants = read("src/main/kotlin/de/teutonstudio/ccaeroworks/CCAeroworks.kt")
local_peripheral = read("src/main/kotlin/de/teutonstudio/ccaeroworks/compat/computercraft/ControlDeskPeripheral.kt")
docs = read("docs/control-overrides.md")
api_docs = read("docs/cc-peripheral-api.md")
quick_ref = read("wiki/API-Schnellreferenz.md")
example = read("examples/cc/control-override-demo.lua")
workflow = read(".github/workflows/verify.yml")

# Authority must be embedded-computer-only, not another write surface on public ControlDesk peripherals.
require("ComputerControlLuaApi" in api_registry,
        "embedded ComputerControlDesk API factory must register ComputerControlLuaApi")
require('getNames(): Array<String> = arrayOf("controls")' in api,
        "control API must expose the controls global")
require('getModuleName(): String = "cc_aeroworks.controls"' in api,
        "control API must expose the cc_aeroworks.controls module")
require("ControlOverrideManager" not in local_peripheral,
        "public ControlDesk peripheral must not receive computer control authority")

# HARD authority must guard the canonical Aeroworks controller setter, while computer writes reuse it.
require('method = ["setChannelFromController"]' in mixin and "cancellable = true" in mixin,
        "override mixin must guard the canonical Aeroworks controller setter")
require("isHardOverridden" in mixin and "callback.cancel()" in mixin,
        "manual writes must be cancelled while HARD authority owns the channel")
require("desk.notifyUpdate()" in mixin,
        "blocked manual writes must resync the authoritative module state for visual feedback")
require("ControlWriteContext.isComputerOverrideWrite()" in mixin,
        "manager-originated writes must bypass the manual-write guard")
require("setChannelFromController" in manager and "ControlWriteContext.computerOverride" in manager,
        "computer commands must still write through Aeroworks' canonical controller setter")
require("ConsoleBlockEntityControlOverrideMixin" in mixins_json,
        "server mixin configuration must register the control override guard")
require("ThreadLocal" in context and "finally" in context,
        "control write source context must be nested-safe and always restored")

# Only supported continuous vehicle channels are writable in v1.
require("command.value !in -15..15" in manager,
        "control values must be rejected outside the Aeroworks -15..15 range")
require("CombinedInputSource.channels" in manager,
        "supported continuous channels must use the shared control-channel catalogue")
require("CombinedInputSource.isDisplayPointerModule" in manager,
        "display pointer pseudo channels must be excluded from vehicle overrides")
require("Display pointer channels cannot be overridden" in manager,
        "display pointer exclusion must produce an explicit Lua error")

# Ownership, batches and change-only writes are core autopilot behaviour.
require("ownerDeskId" in manager and "already controlled by another ComputerControlDesk" in manager,
        "control state must track and enforce one ComputerControlDesk owner")
require("overrideBatch" in api and "overrideBatch" in manager,
        "Lua API and manager must support grouped control commands")
require("duplicate channel" in manager,
        "batch commands must reject duplicate targets before applying writes")
require("module.value(command.channel) != command.value" in manager,
        "identical effective values must not cause redundant Aeroworks writes")

# Runtime authority must fail safe instead of persisting or surviving invalid topology.
require("ControlOverrideManager.tick(this, newPowered)" in block_entity,
        "ComputerControlDesk tick must validate/release active overrides")
require('ControlOverrideManager.releaseAll(this, "invalidated")' in block_entity,
        "ComputerControlDesk invalidation must release every owned override")
require('releaseAll(owner, "computer_off")' in manager,
        "computer shutdown must release control authority")
require('releaseAll(owner, "network_invalid")' in manager,
        "invalid multiblock ownership must release control authority")
require('"target_invalid"' in manager,
        "removed/replaced target modules must release their overrides")
require("CompoundTag" not in manager,
        "active override authority must remain runtime state, not NBT persistence")

# New events must be additive; existing input event contracts remain untouched.
require("CONTROL_OVERRIDE_EVENT" in constants and "CONTROL_RELEASE_EVENT" in constants,
        "override engage/update and release events must have stable constants")
require("CCAeroworks.CONTROL_OVERRIDE_EVENT" in manager and "CCAeroworks.CONTROL_RELEASE_EVENT" in manager,
        "control manager must emit the dedicated ComputerControlDesk events")

# Keep the public contract, example and CI verifier together with the implementation.
for required in (
    "getChannels()",
    "getState(deskId, socket, channel)",
    "override(deskId, socket, channel, value)",
    "overrideBatch(commands)",
    "release(deskId, socket, channel)",
    "releaseAll()",
):
    require(required in docs, f"control override docs must describe {required}")
require("cc_aeroworks.controls" in api_docs and "cc_aeroworks.controls" in quick_ref,
        "main API docs and wiki quick reference must expose the controls module")
require("controls.overrideBatch" in example and "controls.releaseAll" in example,
        "example must demonstrate grouped authority and guaranteed release")
require("python3 tools/verify-control-overrides.py" in workflow,
        "repository workflow must run the control override verifier")

print(
    "Validated ComputerControlDesk control authority: embedded-only HARD ownership, "
    "Aeroworks canonical writes, visual/effective-state coupling, fail-safe lifecycle, "
    "continuous-channel filtering, batching, events and documentation."
)
