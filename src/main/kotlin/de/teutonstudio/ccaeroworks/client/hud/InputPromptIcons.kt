package de.teutonstudio.ccaeroworks.client.hud

/** Kenney Input Prompts 1.5A names, mapped from Aeroworks' persisted input sources. */
object InputPromptIcons {
    data class Glyph(val asset: String, val caption: String = "")

    /** The inverse keycap denotes a held input without an extra word or icon. */
    fun negative(glyph: Glyph): Glyph {
        val asset = glyph.asset
        if (asset.endsWith("_outline")) return glyph
        val supported = asset.startsWith("keyboard_") || asset.startsWith("xbox_button_") ||
            asset.startsWith("xbox_dpad_") || asset in setOf(
                "mouse", "mouse_left", "mouse_right", "mouse_scroll", "mouse_scroll_up", "mouse_scroll_down",
                "mouse_side_back", "mouse_side_forward", "generic_button_circle", "generic_button_square",
                "xbox_guide", "xbox_lb", "xbox_lt", "xbox_rb", "xbox_rt"
            )
        return if (supported) glyph.copy(asset = "${asset}_outline") else glyph
    }

    private val keyboardNames = mapOf(
        "space" to "space", "escape" to "escape", "enter" to "enter", "tab" to "tab",
        "backspace" to "backspace", "insert" to "insert", "delete" to "delete",
        "right" to "arrow_right", "left" to "arrow_left", "down" to "arrow_down", "up" to "arrow_up",
        "page.up" to "page_up", "page.down" to "page_down", "home" to "home", "end" to "end",
        "caps.lock" to "capslock", "scroll.lock" to "scroll_lock", "num.lock" to "numlock",
        "print.screen" to "printscreen", "pause" to "pause", "menu" to "function",
        "left.shift" to "shift", "right.shift" to "shift",
        "left.control" to "ctrl", "right.control" to "ctrl",
        "left.alt" to "alt", "right.alt" to "alt",
        "left.win" to "win", "right.win" to "win",
        "keypad.enter" to "numpad_enter", "keypad.add" to "numpad_plus",
        "keypad.subtract" to "minus", "keypad.multiply" to "asterisk",
        "keypad.divide" to "slash_forward", "keypad.decimal" to "period",
        "keypad.equal" to "equals"
    )
    private val characters = mapOf(
        "'" to "apostrophe", "," to "comma", "-" to "minus", "." to "period",
        "/" to "slash_forward", ";" to "semicolon", "=" to "equals", "[" to "bracket_open",
        "]" to "bracket_close", "\\" to "slash_back", "`" to "tilde", "+" to "plus",
        "<" to "bracket_less", ">" to "bracket_greater", "*" to "asterisk"
    )
    private val mouse = mapOf(
        "mouse.x" to "mouse_horizontal", "mouse.y" to "mouse_vertical", "mouse.xy" to "mouse_move",
        "mouse.wheel.up" to "mouse_scroll_up", "mouse.wheel.down" to "mouse_scroll_down",
        "key.mouse.left" to "mouse_left", "key.mouse.right" to "mouse_right",
        "key.mouse.middle" to "mouse_scroll", "key.mouse.4" to "mouse_side_back",
        "key.mouse.5" to "mouse_side_forward"
    )
    private val gamepad = mapOf(
        "left_x" to "xbox_stick_l_horizontal", "left_y" to "xbox_stick_l_vertical",
        "right_x" to "xbox_stick_r_horizontal", "right_y" to "xbox_stick_r_vertical",
        "left_trigger" to "xbox_lt", "right_trigger" to "xbox_rt",
        "btn.a" to "xbox_button_a", "btn.b" to "xbox_button_b",
        "btn.x" to "xbox_button_x", "btn.y" to "xbox_button_y",
        "btn.left_bumper" to "xbox_lb", "btn.right_bumper" to "xbox_rb",
        "btn.back" to "xbox_button_back", "btn.start" to "xbox_button_start",
        "btn.guide" to "xbox_guide", "btn.left_thumb" to "xbox_stick_l_press",
        "btn.right_thumb" to "xbox_stick_r_press", "btn.dpad_up" to "xbox_dpad_up",
        "btn.dpad_right" to "xbox_dpad_right", "btn.dpad_down" to "xbox_dpad_down",
        "btn.dpad_left" to "xbox_dpad_left"
    )

    /** Printable keyboard labels follow the player's layout (for example QWERTZ Y/Z). */
    fun glyph(source: String, displayLabel: String = ""): Glyph {
        mouse[source]?.let { return Glyph(it) }
        gamepad[source.removePrefix("gamepad.")]?.takeIf { source.startsWith("gamepad.") }
            ?.let { return Glyph(it) }
        if (source.startsWith("joystick.")) {
            val kind = source.substringAfterLast(".axis.", "").toIntOrNull()
            if (kind != null && kind >= 0) return Glyph("generic_stick", "A$kind")
            val button = source.substringAfterLast(".btn.", "").toIntOrNull()
            if (button != null && button >= 0) return Glyph("generic_button_circle", "B$button")
            val hat = source.substringAfterLast(".hat.", "").split('.')
            if (hat.size == 2 && hat[0].toIntOrNull()?.let { it >= 0 } == true &&
                hat[1] in listOf("up", "right", "down", "left")) {
                return Glyph("generic_stick_${hat[1]}", "H${hat[0]}")
            }
        }
        if (source.startsWith("key.keyboard.")) {
            val key = source.removePrefix("key.keyboard.")
            val printable = displayLabel.lowercase().takeIf { it.length == 1 }
                ?: key.takeIf { it.length == 1 }
            if (printable != null) {
                if (printable[0] in 'a'..'z' || printable[0] in '0'..'9') return Glyph("keyboard_$printable")
                characters[printable]?.let { return Glyph("keyboard_$it") }
            }
            val number = key.removePrefix("keypad.")
            if (key.startsWith("keypad.") && number.length == 1 && number[0] in '0'..'9') {
                return Glyph("keyboard_$number", "N")
            }
            keyboardNames[key]?.let {
                val side = when {
                    key.startsWith("left.") -> "L"
                    key.startsWith("right.") -> "R"
                    key.startsWith("keypad.") -> "N"
                    else -> ""
                }
                return Glyph("keyboard_$it", side)
            }
            if (key.startsWith('f') && key.drop(1).toIntOrNull() in 1..12) return Glyph("keyboard_$key")
        }
        if (source.startsWith("key.mouse.")) return Glyph("mouse", source.removePrefix("key.mouse."))
        return Glyph("generic_button_square", displayLabel.ifBlank { source }.ifBlank { "—" })
    }
}
