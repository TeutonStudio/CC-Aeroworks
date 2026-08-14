#!/usr/bin/env python3
"""Validate ControlDesk interaction ownership and the unified I/O configuration entry."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    handler = read(
        "src/main/kotlin/de/teutonstudio/ccaeroworks/computer/ComputerConsoleInteractionHandler.kt"
    )
    switch = read(
        "src/main/kotlin/de/teutonstudio/ccaeroworks/mixin/client/ModuleScreenSwitchMixin.kt"
    )
    request = read(
        "src/main/kotlin/de/teutonstudio/ccaeroworks/network/RequestDeskIoOverviewPayload.kt"
    )
    german = read("src/main/resources/assets/aeroworks/lang/de_de.json")

    require(
        "AeroworksTypes.isControlDesk" in handler,
        "Interaction hook is no longer scoped to ControlDesks",
    )
    require(
        "event.hand != InteractionHand.MAIN_HAND" in handler,
        "Interaction hook must ignore the off hand",
    )
    require(
        "!event.entity.isCrouching || !event.itemStack.isEmpty" in handler,
        "Only sneak + empty-hand configuration may enter the CC-Aeroworks overview",
    )
    require(
        "ControlDeskUiSwitchState.remember(event)" in handler,
        "Configuration click no longer establishes the UI-switch context",
    )

    # Normal controls, mounting and wrench interaction remain Aeroworks-owned. The one intentional
    # exception is the configuration click when an embedded computer is available: that client-side
    # click is cancelled and replaced with the server-authoritative unified I/O overview request.
    for token in (
        "WrenchItem",
        "openComputerTerminal",
        "openControlDefinition",
        "useItemOn(",
        "useWithoutItem(",
        "event.setUseBlock",
        "event.setUseItem",
        "cancellationResult",
    ):
        require(token not in handler, f"ControlDesk handler unexpectedly owns native path through {token}")

    require(
        "if (!event.level.isClientSide) return" in handler,
        "Unified overview interception must occur only on the logical client",
    )
    require(
        "if (!ControlDeskUiSwitchState.clientCanSwitchToComputer()) return" in handler,
        "Networks without an embedded computer must keep Aeroworks' native configuration UI",
    )
    require(
        "RequestDeskIoOverviewPayload" in handler and "event.isCanceled = true" in handler,
        "Embedded networks must replace the native configuration click with the unified I/O request",
    )
    require(
        "player.distanceToSqr" in request and "level.mayInteract" in request,
        "Replacement overview request must revalidate reach and interaction permission server-side",
    )

    require(
        "ControlDeskUiSwitchState.rememberClientControls(menu.contentHolder)" in switch,
        "Aeroworks ModuleScreen no longer captures its exact return socket",
    )
    require(
        "SwitchControlDeskUiPayload()" in switch,
        "Aeroworks ModuleScreen no longer provides the embedded-computer switch",
    )
    require(
        '"aeroworks.ponder.console_configure.text_1": "Schleichen und rechtsklicken, um die Konfiguration zu öffnen"'
        in german,
        "Bundled Aeroworks guidance no longer documents sneak + right-click configuration",
    )

    print(
        "Validated ControlDesk ownership: normal operation/mounting/wrench paths remain Aeroworks; "
        "only sneak + empty-hand configuration on embedded networks enters the server-authoritative I/O overview."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, UnicodeDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
