package com.mrjackspade.kairo.frontend

import android.view.KeyEvent

/**
 * A key of the IBM PC keyboard: the 101/102-key AT enhanced layout, and the keys the AT's 84-key,
 * the Japanese 106-key, the Korean 103-key and the Brazilian ABNT2 keyboards add ([PcKeyboardModel]).
 * Each has its legend, its legend with Shift on a US keyboard, its make code in scan code set 1
 * ([scanCode], with 0x100 added for the keys sent after an E0 prefix, and Pause, sent as E1 1D 45,
 * as 0x100), and the Android keys that type it. Every Kairo app with a PC keyboard takes its keys
 * from here and gives each its own code ([PcKeyboard.layout]); the scan code is the PC's own.
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
    // The JIS keyboard's 半角/全角 key is here too.
    GRAVE("`", 0x29, "~", KeyEvent.KEYCODE_GRAVE, KeyEvent.KEYCODE_ZENKAKU_HANKAKU),
    LEFT_SHIFT("Shift", 0x2A, null, KeyEvent.KEYCODE_SHIFT_LEFT),
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
    // The 84-key AT keyboard's own SysRq key (the 101-key keyboard sends it for Alt+Print Screen).
    SYS_REQ("SysRq", 0x54),
    // The 102-key (ISO) keyboard's key beside Left Shift, which a US BIOS types as \ and |.
    ISO_BACKSLASH("\\", 0x56, "|"),
    F11("F11", 0x57, null, KeyEvent.KEYCODE_F11), F12("F12", 0x58, null, KeyEvent.KEYCODE_F12),
    // The Japanese keyboard's keys, and the Brazilian keyboard's / and keypad . keys.
    KANA("カナ", 0x70, null, KeyEvent.KEYCODE_KATAKANA_HIRAGANA, KeyEvent.KEYCODE_KANA),
    RO("\\", 0x73, "_", KeyEvent.KEYCODE_RO),
    HENKAN("変換", 0x79, null, KeyEvent.KEYCODE_HENKAN), MUHENKAN("無変換", 0x7B, null, KeyEvent.KEYCODE_MUHENKAN),
    YEN("¥", 0x7D, "|", KeyEvent.KEYCODE_YEN), PAD_SEPARATOR(".", 0x7E, null, KeyEvent.KEYCODE_NUMPAD_COMMA),
    // The Korean keyboard's Hanja and Han/Yeong keys.
    HANJA("한자", 0xF1), HANGUL("한/영", 0xF2),
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

/**
 * The PC keyboards whose keys and legends differ, by the number of keys a machine's keyboard has
 * ([id], as 86Box names them): the PC/XT's 83 keys, the AT's 84, the enhanced 101 (ANSI, US) and
 * 102 (ISO, international), the Japanese 106 (JIS), the Korean 103 (KSC) and the Brazilian 104 (ABNT2).
 */
enum class PcKeyboardModel(val id: String) {
    XT("83"), AT("84"), ANSI("101"), ISO("102"), JIS("106"), KSC("103"), ABNT2("104");

    companion object {
        fun of(id: String): PcKeyboardModel = entries.firstOrNull { it.id == id } ?: error("No PC keyboard has $id keys")
    }
}

/** The on-screen PC keyboard every Kairo app with a PC keyboard shows, in its own key codes. */
object PcKeyboard {
    /** A main-block key's legends on a model: its own and with Shift. */
    private class Legend(val key: PcKey, val label: String, val shifted: String?)

    private fun us(key: PcKey) = Legend(key, key.label, key.shifted)

    /** The US punctuation by row: the digit row's, the Q row's, the A row's and the Z row's. */
    private val US_PUNCTUATION = listOf(
        listOf(PcKey.MINUS, PcKey.EQUALS).map(::us),
        listOf(PcKey.LEFT_BRACKET, PcKey.RIGHT_BRACKET, PcKey.BACKSLASH).map(::us),
        listOf(PcKey.SEMICOLON, PcKey.APOSTROPHE).map(::us),
        listOf(PcKey.COMMA, PcKey.PERIOD, PcKey.SLASH).map(::us))

    private val US_DIGITS = listOf(PcKey.D1, PcKey.D2, PcKey.D3, PcKey.D4, PcKey.D5, PcKey.D6, PcKey.D7, PcKey.D8,
        PcKey.D9, PcKey.D0).map(::us)

    /** The Brazilian ABNT2 legends, as KEYB BR types them. */
    private val ABNT2_DIGITS = US_DIGITS.map { if (it.key == PcKey.D6) Legend(PcKey.D6, "6", "¨") else it }
    private val ABNT2_PUNCTUATION = listOf(
        listOf(us(PcKey.MINUS), us(PcKey.EQUALS)),
        listOf(Legend(PcKey.LEFT_BRACKET, "´", "`"), Legend(PcKey.RIGHT_BRACKET, "[", "{")),
        listOf(Legend(PcKey.SEMICOLON, "ç", "Ç"), Legend(PcKey.APOSTROPHE, "~", "^"), Legend(PcKey.BACKSLASH, "]", "}")),
        listOf(us(PcKey.COMMA), us(PcKey.PERIOD), Legend(PcKey.SLASH, ";", ":"), Legend(PcKey.RO, "/", "?")))

    /** The PC's own numbering of the JIS main block (JisMainBlock), in set 1 scan codes. */
    private val PC_JIS = PcKey::scanCode.let { scan ->
        JisMainBlock.Codes(scan(PcKey.ESCAPE), scan(PcKey.D1), scan(PcKey.MINUS), scan(PcKey.EQUALS), scan(PcKey.YEN),
            scan(PcKey.BACKSPACE), scan(PcKey.TAB), listOf(scan(PcKey.Q), scan(PcKey.A), scan(PcKey.Z)),
            scan(PcKey.LEFT_BRACKET), scan(PcKey.RIGHT_BRACKET), scan(PcKey.ENTER), scan(PcKey.SEMICOLON),
            scan(PcKey.APOSTROPHE), scan(PcKey.BACKSLASH), scan(PcKey.COMMA), scan(PcKey.PERIOD), scan(PcKey.SLASH),
            scan(PcKey.RO), scan(PcKey.SPACE), underscoreLabel = "\\", underscoreShifted = "_")
    }

    /**
     * The layout of [model]'s keyboard (the US 101-key one by default): its main block, a page of
     * symbols (each typed with Shift where the PC needs it), its function and navigation keys, and
     * its keypad. [code] gives each key the app's own code.
     */
    fun layout(brand: String, model: PcKeyboardModel = PcKeyboardModel.ANSI, code: (PcKey) -> Int): GuestKeyboardLayout {
        fun key(key: PcKey, width: Float = 1f, label: String = key.label) = KeyboardKey(label, code(key), width, shifted = key.shifted)
        fun legend(legend: Legend) = KeyboardKey(legend.label, code(legend.key), shifted = legend.shifted)
        fun pad(key: PcKey, numLockOff: String, width: Float = 1f) = KeyboardKey(key.label, code(key), width, numLockOff = numLockOff)
        fun letters(text: String) = text.map { letter -> key(PcKey.valueOf(letter.uppercase())) }
        val enhanced = model != PcKeyboardModel.XT && model != PcKeyboardModel.AT
        val digits = if (model == PcKeyboardModel.ABNT2) ABNT2_DIGITS else US_DIGITS
        val punctuation = if (model == PcKeyboardModel.ABNT2) ABNT2_PUNCTUATION else US_PUNCTUATION
        val grave = if (model == PcKeyboardModel.ABNT2) Legend(PcKey.GRAVE, "'", "\"") else us(PcKey.GRAVE)
        val iso = if (model == PcKeyboardModel.ISO || model == PcKeyboardModel.ABNT2) listOf(us(PcKey.ISO_BACKSLASH)) else emptyList()

        val alphabet = if (model == PcKeyboardModel.JIS) {
            JisMainBlock.alphabetRows(PC_JIS.remap(code),
                listOf(key(PcKey.ESCAPE), KeyboardKey("半/全", code(PcKey.GRAVE))), key(PcKey.CAPS_LOCK, 1.4f, "英数"),
                code(PcKey.LEFT_SHIFT),
                listOf(key(PcKey.LEFT_CTRL), key(PcKey.LEFT_ALT), key(PcKey.MUHENKAN), key(PcKey.SPACE, 3f),
                    key(PcKey.HENKAN), key(PcKey.KANA), key(PcKey.RIGHT_ALT), key(PcKey.RIGHT_CTRL)))
        } else {
            // The rows of the model's legends, so a locked Shift shows what every key types.
            val bottom = when (model) {
                PcKeyboardModel.XT, PcKeyboardModel.AT -> listOf(key(PcKey.LEFT_CTRL), key(PcKey.LEFT_ALT), key(PcKey.SPACE, 5f))
                PcKeyboardModel.KSC -> listOf(key(PcKey.LEFT_CTRL), key(PcKey.LEFT_ALT), key(PcKey.HANJA), key(PcKey.SPACE, 4f),
                    key(PcKey.HANGUL), key(PcKey.RIGHT_ALT), key(PcKey.RIGHT_CTRL))
                PcKeyboardModel.ABNT2 -> listOf(key(PcKey.LEFT_CTRL), key(PcKey.LEFT_ALT), key(PcKey.SPACE, 5f),
                    key(PcKey.RIGHT_ALT, label = "AltGr"), key(PcKey.RIGHT_CTRL))
                else -> listOf(key(PcKey.LEFT_CTRL), key(PcKey.LEFT_ALT), key(PcKey.SPACE, 5f), key(PcKey.RIGHT_ALT),
                    key(PcKey.RIGHT_CTRL))
            }
            listOf(
                listOf(key(PcKey.ESCAPE, 1.3f), legend(grave)) + digits.map(::legend) +
                    punctuation[0].map(::legend) + key(PcKey.BACKSPACE, 1.5f),
                listOf(key(PcKey.TAB, 1.3f)) + letters("qwertyuiop") + punctuation[1].map(::legend),
                listOf(key(PcKey.CAPS_LOCK, 1.4f)) + letters("asdfghjkl") + punctuation[2].map(::legend) +
                    key(PcKey.ENTER, 1.5f),
                listOf(key(PcKey.LEFT_SHIFT, if (iso.isEmpty()) 1.5f else 1.2f)) + iso.map(::legend) + letters("zxcvbnm") +
                    punctuation[3].map(::legend) + key(PcKey.RIGHT_SHIFT, 1.5f),
                bottom)
        }

        // The digits, their Shift symbols chorded, and the model's punctuation keys.
        val symbols = if (model == PcKeyboardModel.JIS) JisMainBlock.symbolRows(PC_JIS.remap(code)) else listOf(
            listOf(key(PcKey.ESCAPE)) + digits.map(::legend) + key(PcKey.BACKSPACE),
            digits.map { KeyboardKey(it.shifted!!, code(it.key), chordShift = true) },
            (punctuation.flatten() + iso).map(::legend),
            listOf(key(PcKey.TAB), key(PcKey.SPACE, 5f), key(PcKey.ENTER)))

        // The keys above and beside the main block, with Shift for Shift+F-key commands. The XT
        // and the AT have ten function keys and move with the keypad (its page).
        val function = if (enhanced) listOf(
            listOf(PcKey.F1, PcKey.F2, PcKey.F3, PcKey.F4, PcKey.F5, PcKey.F6).map { key(it) },
            listOf(PcKey.F7, PcKey.F8, PcKey.F9, PcKey.F10, PcKey.F11, PcKey.F12).map { key(it) },
            listOf(PcKey.ESCAPE, PcKey.HOME, PcKey.UP, PcKey.END, PcKey.PAGE_UP, PcKey.INSERT, PcKey.PRINT_SCREEN).map { key(it) },
            listOf(PcKey.TAB, PcKey.LEFT, PcKey.DOWN, PcKey.RIGHT, PcKey.PAGE_DOWN, PcKey.DELETE, PcKey.PAUSE).map { key(it) },
            listOf(key(PcKey.LEFT_SHIFT, 1.5f), key(PcKey.LEFT_CTRL), key(PcKey.LEFT_ALT), key(PcKey.SPACE, 3f),
                key(PcKey.ENTER, 1.5f), key(PcKey.SCROLL_LOCK))
        ) else listOf(
            listOf(PcKey.F1, PcKey.F2, PcKey.F3, PcKey.F4, PcKey.F5).map { key(it) },
            listOf(PcKey.F6, PcKey.F7, PcKey.F8, PcKey.F9, PcKey.F10).map { key(it) },
            listOf(PcKey.ESCAPE, PcKey.TAB).map { key(it) } +
                (if (model == PcKeyboardModel.AT) listOf(key(PcKey.SYS_REQ)) else emptyList()) + key(PcKey.SCROLL_LOCK),
            listOf(key(PcKey.LEFT_SHIFT, 1.5f), key(PcKey.LEFT_CTRL), key(PcKey.LEFT_ALT), key(PcKey.SPACE, 3f),
                key(PcKey.ENTER, 1.5f)))

        // The keypad, with its tall + and Enter; with Num Lock off the digits act as the
        // navigation keys their legends then show. The XT's and the AT's have no / or Enter of
        // their own (Enter is the main one); the ABNT2's has a . key under its +.
        val keypad = when {
            !enhanced -> listOf(
                listOf(key(PcKey.NUM_LOCK), key(PcKey.SCROLL_LOCK), key(PcKey.PAD_MULTIPLY), key(PcKey.PAD_MINUS)),
                listOf(pad(PcKey.PAD_7, "Home"), pad(PcKey.PAD_8, "↑"), pad(PcKey.PAD_9, "PgUp"),
                    KeyboardKey(PcKey.PAD_PLUS.label, code(PcKey.PAD_PLUS), rows = 2)),
                listOf(pad(PcKey.PAD_4, "←"), pad(PcKey.PAD_5, "5"), pad(PcKey.PAD_6, "→")),
                listOf(pad(PcKey.PAD_1, "End"), pad(PcKey.PAD_2, "↓"), pad(PcKey.PAD_3, "PgDn"),
                    KeyboardKey(PcKey.ENTER.label, code(PcKey.ENTER), rows = 2)),
                listOf(pad(PcKey.PAD_0, "Ins", 2f), pad(PcKey.PAD_PERIOD, "Del")))
            model == PcKeyboardModel.ABNT2 -> listOf(
                listOf(key(PcKey.NUM_LOCK), key(PcKey.PAD_DIVIDE), key(PcKey.PAD_MULTIPLY), key(PcKey.PAD_MINUS)),
                listOf(pad(PcKey.PAD_7, "Home"), pad(PcKey.PAD_8, "↑"), pad(PcKey.PAD_9, "PgUp"), key(PcKey.PAD_PLUS)),
                listOf(pad(PcKey.PAD_4, "←"), pad(PcKey.PAD_5, "5"), pad(PcKey.PAD_6, "→"), key(PcKey.PAD_SEPARATOR)),
                listOf(pad(PcKey.PAD_1, "End"), pad(PcKey.PAD_2, "↓"), pad(PcKey.PAD_3, "PgDn"),
                    KeyboardKey(PcKey.PAD_ENTER.label, code(PcKey.PAD_ENTER), rows = 2)),
                listOf(pad(PcKey.PAD_0, "Ins", 2f), KeyboardKey(",", code(PcKey.PAD_PERIOD), numLockOff = "Del")))
            else -> listOf(
                listOf(key(PcKey.NUM_LOCK), key(PcKey.PAD_DIVIDE), key(PcKey.PAD_MULTIPLY), key(PcKey.PAD_MINUS)),
                listOf(pad(PcKey.PAD_7, "Home"), pad(PcKey.PAD_8, "↑"), pad(PcKey.PAD_9, "PgUp"),
                    KeyboardKey(PcKey.PAD_PLUS.label, code(PcKey.PAD_PLUS), rows = 2)),
                listOf(pad(PcKey.PAD_4, "←"), pad(PcKey.PAD_5, "5"), pad(PcKey.PAD_6, "→")),
                listOf(pad(PcKey.PAD_1, "End"), pad(PcKey.PAD_2, "↓"), pad(PcKey.PAD_3, "PgDn"),
                    KeyboardKey(PcKey.PAD_ENTER.label, code(PcKey.PAD_ENTER), rows = 2)),
                listOf(pad(PcKey.PAD_0, "Ins", 2f), pad(PcKey.PAD_PERIOD, "Del")))
        }

        val kana = if (model == PcKeyboardModel.JIS) code(PcKey.KANA) else null
        return GuestKeyboardLayout(
            brand = brand,
            pages = listOf(
                GuestKeyboardPage("ABC", alphabet),
                GuestKeyboardPage("?123", symbols, pickerLabel = UiText.res(R.string.kairo_keyboard_symbols)),
                GuestKeyboardPage("FN", function, pickerLabel = UiText.res(R.string.kairo_keyboard_function)),
                GuestKeyboardPage("NUM", keypad, pickerLabel = UiText.res(R.string.kairo_keyboard_keypad))
            ),
            modifiers = (listOf(PcKey.CAPS_LOCK, PcKey.LEFT_SHIFT, PcKey.RIGHT_SHIFT, PcKey.LEFT_CTRL, PcKey.RIGHT_CTRL,
                PcKey.LEFT_ALT, PcKey.RIGHT_ALT, PcKey.NUM_LOCK, PcKey.SCROLL_LOCK).map(code) + listOfNotNull(kana)).toSet(),
            shiftCodes = setOf(code(PcKey.LEFT_SHIFT), code(PcKey.RIGHT_SHIFT)),
            capsCode = code(PcKey.CAPS_LOCK),
            chordShiftCode = code(PcKey.LEFT_SHIFT),
            kanaCode = kana,
            numLockCode = code(PcKey.NUM_LOCK),
            otherLockCodes = setOf(code(PcKey.SCROLL_LOCK))
        )
    }

    /** [PC_JIS] in the app's codes: each scan code through [code]. */
    private fun JisMainBlock.Codes.remap(code: (PcKey) -> Int): JisMainBlock.Codes {
        fun c(scan: Int) = code(checkNotNull(PcKey.fromScanCode(scan)) { "No PC key has scan code $scan" })
        val digitCodes = (0 until 10).map { c(digits + it) }
        require(digitCodes.zipWithNext().all { (a, b) -> b == a + 1 }) { "The app's codes must keep the digit row in order" }
        val rows = letterRows.mapIndexed { row, first ->
            val length = listOf(10, 9, 7)[row]
            val rowCodes = (0 until length).map { c(first + it) }
            require(rowCodes.zipWithNext().all { (a, b) -> b == a + 1 }) { "The app's codes must keep each letter row in order" }
            rowCodes.first()
        }
        return JisMainBlock.Codes(c(esc), digitCodes.first(), c(minus), c(hat), c(yen), c(backspace), c(tab), rows, c(at),
            c(leftBracket), c(returnKey), c(semicolon), c(colon), c(rightBracket), c(comma), c(dot), c(slash), c(underscore),
            c(space), underscoreLabel, underscoreShifted)
    }
}
