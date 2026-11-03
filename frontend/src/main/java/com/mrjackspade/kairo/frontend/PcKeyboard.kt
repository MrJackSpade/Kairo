package com.mrjackspade.kairo.frontend

import android.view.KeyEvent

/**
 * A key of the IBM PC keyboard (the 101/102-key AT enhanced layout): its legend, its legend with
 * Shift on a US keyboard, its make code in scan code set 1 ([scanCode], with 0x100 added for the
 * keys sent after an E0 prefix, and Pause, sent as E1 1D 45, as 0x100), and the Android keys that
 * type it. Every Kairo app with a PC keyboard takes its keys from here and gives each its own code
 * ([PcKeyboard.layout]); the scan code is the PC's own.
 */
enum class PcKey(val label: String, val scanCode: Int, val shifted: String? = null, vararg val android: Int) {
    ESCAPE("Esc", 0x01, null, KeyEvent.KEYCODE_ESCAPE),
    D1("1", 0x02, "!", KeyEvent.KEYCODE_1), D2("2", 0x03, "@", KeyEvent.KEYCODE_2), D3("3", 0x04, "#", KeyEvent.KEYCODE_3),
    D4("4", 0x05, "$", KeyEvent.KEYCODE_4), D5("5", 0x06, "%", KeyEvent.KEYCODE_5), D6("6", 0x07, "^", KeyEvent.KEYCODE_6),
    D7("7", 0x08, "&", KeyEvent.KEYCODE_7), D8("8", 0x09, "*", KeyEvent.KEYCODE_8), D9("9", 0x0A, "(", KeyEvent.KEYCODE_9),
    D0("0", 0x0B, ")", KeyEvent.KEYCODE_0),
    MINUS("-", 0x0C, "_", KeyEvent.KEYCODE_MINUS), EQUALS("=", 0x0D, "+", KeyEvent.KEYCODE_EQUALS),
    BACKSPACE("Back", 0x0E, null, KeyEvent.KEYCODE_DEL), TAB("Tab", 0x0F, null, KeyEvent.KEYCODE_TAB),
    Q("q", 0x10, null, KeyEvent.KEYCODE_Q), W("w", 0x11, null, KeyEvent.KEYCODE_W), E("e", 0x12, null, KeyEvent.KEYCODE_E),
    R("r", 0x13, null, KeyEvent.KEYCODE_R), T("t", 0x14, null, KeyEvent.KEYCODE_T), Y("y", 0x15, null, KeyEvent.KEYCODE_Y),
    U("u", 0x16, null, KeyEvent.KEYCODE_U), I("i", 0x17, null, KeyEvent.KEYCODE_I), O("o", 0x18, null, KeyEvent.KEYCODE_O),
    P("p", 0x19, null, KeyEvent.KEYCODE_P),
    LEFT_BRACKET("[", 0x1A, "{", KeyEvent.KEYCODE_LEFT_BRACKET), RIGHT_BRACKET("]", 0x1B, "}", KeyEvent.KEYCODE_RIGHT_BRACKET),
    ENTER("Enter", 0x1C, null, KeyEvent.KEYCODE_ENTER), LEFT_CTRL("Ctrl", 0x1D, null, KeyEvent.KEYCODE_CTRL_LEFT),
    A("a", 0x1E, null, KeyEvent.KEYCODE_A), S("s", 0x1F, null, KeyEvent.KEYCODE_S), D("d", 0x20, null, KeyEvent.KEYCODE_D),
    F("f", 0x21, null, KeyEvent.KEYCODE_F), G("g", 0x22, null, KeyEvent.KEYCODE_G), H("h", 0x23, null, KeyEvent.KEYCODE_H),
    J("j", 0x24, null, KeyEvent.KEYCODE_J), K("k", 0x25, null, KeyEvent.KEYCODE_K), L("l", 0x26, null, KeyEvent.KEYCODE_L),
    SEMICOLON(";", 0x27, ":", KeyEvent.KEYCODE_SEMICOLON), APOSTROPHE("'", 0x28, "\"", KeyEvent.KEYCODE_APOSTROPHE),
    GRAVE("`", 0x29, "~", KeyEvent.KEYCODE_GRAVE), LEFT_SHIFT("Shift", 0x2A, null, KeyEvent.KEYCODE_SHIFT_LEFT),
    BACKSLASH("\\", 0x2B, "|", KeyEvent.KEYCODE_BACKSLASH),
    Z("z", 0x2C, null, KeyEvent.KEYCODE_Z), X("x", 0x2D, null, KeyEvent.KEYCODE_X), C("c", 0x2E, null, KeyEvent.KEYCODE_C),
    V("v", 0x2F, null, KeyEvent.KEYCODE_V), B("b", 0x30, null, KeyEvent.KEYCODE_B), N("n", 0x31, null, KeyEvent.KEYCODE_N),
    M("m", 0x32, null, KeyEvent.KEYCODE_M),
    COMMA(",", 0x33, "<", KeyEvent.KEYCODE_COMMA), PERIOD(".", 0x34, ">", KeyEvent.KEYCODE_PERIOD),
    SLASH("/", 0x35, "?", KeyEvent.KEYCODE_SLASH), RIGHT_SHIFT("Shift", 0x36, null, KeyEvent.KEYCODE_SHIFT_RIGHT),
    PAD_MULTIPLY("*", 0x37, null, KeyEvent.KEYCODE_NUMPAD_MULTIPLY), LEFT_ALT("Alt", 0x38, null, KeyEvent.KEYCODE_ALT_LEFT),
    SPACE("Space", 0x39, null, KeyEvent.KEYCODE_SPACE), CAPS_LOCK("Caps", 0x3A, null, KeyEvent.KEYCODE_CAPS_LOCK),
    F1("F1", 0x3B, null, KeyEvent.KEYCODE_F1), F2("F2", 0x3C, null, KeyEvent.KEYCODE_F2), F3("F3", 0x3D, null, KeyEvent.KEYCODE_F3),
    F4("F4", 0x3E, null, KeyEvent.KEYCODE_F4), F5("F5", 0x3F, null, KeyEvent.KEYCODE_F5), F6("F6", 0x40, null, KeyEvent.KEYCODE_F6),
    F7("F7", 0x41, null, KeyEvent.KEYCODE_F7), F8("F8", 0x42, null, KeyEvent.KEYCODE_F8), F9("F9", 0x43, null, KeyEvent.KEYCODE_F9),
    F10("F10", 0x44, null, KeyEvent.KEYCODE_F10),
    NUM_LOCK("Num", 0x45, null, KeyEvent.KEYCODE_NUM_LOCK), SCROLL_LOCK("ScrLk", 0x46, null, KeyEvent.KEYCODE_SCROLL_LOCK),
    PAD_7("7", 0x47, null, KeyEvent.KEYCODE_NUMPAD_7), PAD_8("8", 0x48, null, KeyEvent.KEYCODE_NUMPAD_8),
    PAD_9("9", 0x49, null, KeyEvent.KEYCODE_NUMPAD_9), PAD_MINUS("-", 0x4A, null, KeyEvent.KEYCODE_NUMPAD_SUBTRACT),
    PAD_4("4", 0x4B, null, KeyEvent.KEYCODE_NUMPAD_4), PAD_5("5", 0x4C, null, KeyEvent.KEYCODE_NUMPAD_5),
    PAD_6("6", 0x4D, null, KeyEvent.KEYCODE_NUMPAD_6), PAD_PLUS("+", 0x4E, null, KeyEvent.KEYCODE_NUMPAD_ADD),
    PAD_1("1", 0x4F, null, KeyEvent.KEYCODE_NUMPAD_1), PAD_2("2", 0x50, null, KeyEvent.KEYCODE_NUMPAD_2),
    PAD_3("3", 0x51, null, KeyEvent.KEYCODE_NUMPAD_3), PAD_0("0", 0x52, null, KeyEvent.KEYCODE_NUMPAD_0),
    PAD_PERIOD(".", 0x53, null, KeyEvent.KEYCODE_NUMPAD_DOT),
    F11("F11", 0x57, null, KeyEvent.KEYCODE_F11), F12("F12", 0x58, null, KeyEvent.KEYCODE_F12),
    PAD_ENTER("Enter", 0x11C, null, KeyEvent.KEYCODE_NUMPAD_ENTER), RIGHT_CTRL("Ctrl", 0x11D, null, KeyEvent.KEYCODE_CTRL_RIGHT),
    PAD_DIVIDE("/", 0x135, null, KeyEvent.KEYCODE_NUMPAD_DIVIDE), PRINT_SCREEN("PrtSc", 0x137, null, KeyEvent.KEYCODE_SYSRQ),
    RIGHT_ALT("Alt", 0x138, null, KeyEvent.KEYCODE_ALT_RIGHT), PAUSE("Pause", 0x100, null, KeyEvent.KEYCODE_BREAK),
    HOME("Home", 0x147, null, KeyEvent.KEYCODE_MOVE_HOME), UP("↑", 0x148, null, KeyEvent.KEYCODE_DPAD_UP),
    PAGE_UP("PgUp", 0x149, null, KeyEvent.KEYCODE_PAGE_UP), LEFT("←", 0x14B, null, KeyEvent.KEYCODE_DPAD_LEFT),
    RIGHT("→", 0x14D, null, KeyEvent.KEYCODE_DPAD_RIGHT), END("End", 0x14F, null, KeyEvent.KEYCODE_MOVE_END),
    DOWN("↓", 0x150, null, KeyEvent.KEYCODE_DPAD_DOWN), PAGE_DOWN("PgDn", 0x151, null, KeyEvent.KEYCODE_PAGE_DOWN),
    INSERT("Ins", 0x152, null, KeyEvent.KEYCODE_INSERT), DELETE("Del", 0x153, null, KeyEvent.KEYCODE_FORWARD_DEL);

    companion object {
        private val byAndroid = entries.flatMap { key -> key.android.map { it to key } }.toMap()
        private val byScanCode = entries.associateBy { it.scanCode }

        /** The key an Android key event types, or null for one the PC has no key for. */
        fun fromAndroid(androidKey: Int): PcKey? = byAndroid[androidKey]

        /** The key with [scanCode] ([PcKey.scanCode]), or null. */
        fun fromScanCode(scanCode: Int): PcKey? = byScanCode[scanCode]
    }
}

/** The on-screen PC keyboard every Kairo app with a PC keyboard shows, in its own key codes. */
object PcKeyboard {
    /**
     * The layout: the US main block, a page of symbols (each typed with Shift where the PC needs it),
     * the function and navigation keys, and the keypad. [code] gives each key the app's own code.
     */
    fun layout(brand: String, code: (PcKey) -> Int): GuestKeyboardLayout {
        fun key(key: PcKey, width: Float = 1f, label: String = key.label) = KeyboardKey(label, code(key), width, shifted = key.shifted)
        fun chord(key: PcKey) = KeyboardKey(key.shifted!!, code(key), chordShift = true)
        fun pad(key: PcKey, numLockOff: String, width: Float = 1f) = KeyboardKey(key.label, code(key), width, numLockOff = numLockOff)
        val digits = listOf(PcKey.D1, PcKey.D2, PcKey.D3, PcKey.D4, PcKey.D5, PcKey.D6, PcKey.D7, PcKey.D8, PcKey.D9, PcKey.D0)
        fun letters(text: String) = text.map { letter -> key(PcKey.valueOf(letter.uppercase())) }
        return GuestKeyboardLayout(
            brand = brand,
            pages = listOf(
                // The US rows, so a locked Shift shows what every key types.
                GuestKeyboardPage("ABC", listOf(
                    listOf(key(PcKey.ESCAPE, 1.3f), key(PcKey.GRAVE)) + digits.map { key(it) } +
                        listOf(key(PcKey.MINUS), key(PcKey.EQUALS), key(PcKey.BACKSPACE, 1.5f)),
                    listOf(key(PcKey.TAB, 1.3f)) + letters("qwertyuiop") +
                        listOf(key(PcKey.LEFT_BRACKET), key(PcKey.RIGHT_BRACKET), key(PcKey.BACKSLASH)),
                    listOf(key(PcKey.CAPS_LOCK, 1.4f)) + letters("asdfghjkl") +
                        listOf(key(PcKey.SEMICOLON), key(PcKey.APOSTROPHE), key(PcKey.ENTER, 1.5f)),
                    listOf(key(PcKey.LEFT_SHIFT, 1.5f)) + letters("zxcvbnm") +
                        listOf(key(PcKey.COMMA), key(PcKey.PERIOD), key(PcKey.SLASH), key(PcKey.RIGHT_SHIFT, 1.5f)),
                    listOf(key(PcKey.LEFT_CTRL), key(PcKey.LEFT_ALT), key(PcKey.SPACE, 5f), key(PcKey.RIGHT_ALT),
                        key(PcKey.RIGHT_CTRL))
                )),
                GuestKeyboardPage("?123", listOf(
                    listOf(key(PcKey.ESCAPE)) + digits.map { key(it) } + key(PcKey.BACKSPACE),
                    digits.map(::chord),
                    listOf(PcKey.MINUS, PcKey.EQUALS, PcKey.LEFT_BRACKET, PcKey.RIGHT_BRACKET, PcKey.BACKSLASH,
                        PcKey.SEMICOLON, PcKey.APOSTROPHE, PcKey.COMMA, PcKey.PERIOD, PcKey.SLASH).map { key(it) },
                    listOf(key(PcKey.TAB), key(PcKey.SPACE, 5f), key(PcKey.ENTER))
                ), pickerLabel = UiText.res(R.string.kairo_keyboard_symbols)),
                // The keys above and beside the main block, with Shift for Shift+F-key commands.
                GuestKeyboardPage("FN", listOf(
                    listOf(PcKey.F1, PcKey.F2, PcKey.F3, PcKey.F4, PcKey.F5, PcKey.F6).map { key(it) },
                    listOf(PcKey.F7, PcKey.F8, PcKey.F9, PcKey.F10, PcKey.F11, PcKey.F12).map { key(it) },
                    listOf(PcKey.ESCAPE, PcKey.HOME, PcKey.UP, PcKey.END, PcKey.PAGE_UP, PcKey.INSERT, PcKey.PRINT_SCREEN).map { key(it) },
                    listOf(PcKey.TAB, PcKey.LEFT, PcKey.DOWN, PcKey.RIGHT, PcKey.PAGE_DOWN, PcKey.DELETE, PcKey.PAUSE).map { key(it) },
                    listOf(key(PcKey.LEFT_SHIFT, 1.5f), key(PcKey.LEFT_CTRL), key(PcKey.LEFT_ALT), key(PcKey.SPACE, 3f),
                        key(PcKey.ENTER, 1.5f), key(PcKey.SCROLL_LOCK))
                ), pickerLabel = UiText.res(R.string.kairo_keyboard_function)),
                // The keypad, with its tall + and Enter; with Num Lock off the digits act as the
                // navigation keys their legends then show.
                GuestKeyboardPage("NUM", listOf(
                    listOf(key(PcKey.NUM_LOCK), key(PcKey.PAD_DIVIDE), key(PcKey.PAD_MULTIPLY), key(PcKey.PAD_MINUS)),
                    listOf(pad(PcKey.PAD_7, "Home"), pad(PcKey.PAD_8, "↑"), pad(PcKey.PAD_9, "PgUp"),
                        KeyboardKey(PcKey.PAD_PLUS.label, code(PcKey.PAD_PLUS), rows = 2)),
                    listOf(pad(PcKey.PAD_4, "←"), pad(PcKey.PAD_5, "5"), pad(PcKey.PAD_6, "→")),
                    listOf(pad(PcKey.PAD_1, "End"), pad(PcKey.PAD_2, "↓"), pad(PcKey.PAD_3, "PgDn"),
                        KeyboardKey(PcKey.PAD_ENTER.label, code(PcKey.PAD_ENTER), rows = 2)),
                    listOf(pad(PcKey.PAD_0, "Ins", 2f), pad(PcKey.PAD_PERIOD, "Del"))
                ), pickerLabel = UiText.res(R.string.kairo_keyboard_keypad))
            ),
            modifiers = listOf(PcKey.CAPS_LOCK, PcKey.LEFT_SHIFT, PcKey.RIGHT_SHIFT, PcKey.LEFT_CTRL, PcKey.RIGHT_CTRL,
                PcKey.LEFT_ALT, PcKey.RIGHT_ALT, PcKey.NUM_LOCK, PcKey.SCROLL_LOCK).map(code).toSet(),
            shiftCodes = setOf(code(PcKey.LEFT_SHIFT), code(PcKey.RIGHT_SHIFT)),
            capsCode = code(PcKey.CAPS_LOCK),
            chordShiftCode = code(PcKey.LEFT_SHIFT),
            numLockCode = code(PcKey.NUM_LOCK),
            otherLockCodes = setOf(code(PcKey.SCROLL_LOCK))
        )
    }
}
