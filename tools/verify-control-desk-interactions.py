#!/usr/bin/env python3
"""Validate that CC-Aeroworks does not hijack Aeroworks ControlDesk interactions."""

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
    german = read("src/main/resources/assets/aeroworks/lang/de_de.json")

    require(
        "AeroworksTypes.isControlDesk" in handler,
        "Interaction session hook is no longer scoped to ControlDesks",
    )
    require(
        "event.hand != InteractionHand.MAIN_HAND" in handler,
        "Interaction session hook must ignore the off hand",
    )
    require(
        "!event.entity.isCrouching || !event.itemStack.isEmpty" in handler,
        "Only the native sneak + empty-hand configuration click should be remembered",
    )
    require(
        "ControlDeskUiSwitchState.remember(event)" in handler,
        "Native configuration click no longer establishes the fallback server UI-switch session",
    )

    forbidden = (
        "WrenchItem",
        "openComputerTerminal",
        "openControlDefinition",
        "useItemOn(",
        "useWithoutItem(",
        "event.isCanceled",
        "event.setUseBlock",
        "event.setUseItem",
        "cancellationResult",
    )
    for token in forbidden:
        require(
            token not in handler,
            f"ControlDesk interaction handler still hijacks native interaction through {token}",
        )

    require(
        "ControlDeskUiSwitchState.rememberClientControls(menu.contentHolder)" in switch,
        "Aeroworks ModuleScreen no longer captures its exact return socket",
    )
    require(
        "SwitchControlDeskUiPayload(current.be().blockPos)" in switch,
        "Aeroworks ModuleScreen must switch to the embedded computer using its current desk anchor",
    )
    require(
        '"aeroworks.ponder.console_configure.text_1": "Schleichen und rechtsklicken, um die Konfiguration zu öffnen"'
        in german,
        "Bundled Aeroworks guidance no longer documents native sneak + right-click configuration",
    )

    print(
        "Validated native ControlDesk ownership: sneak + empty-hand right-click remains Aeroworks, "
        "wrench input is not intercepted, and ModuleScreen sends its exact desk anchor to the computer switch."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, UnicodeDecodeError) as exception:
        print(f"ERROR: {exception}")
        raise SystemExit(1)
