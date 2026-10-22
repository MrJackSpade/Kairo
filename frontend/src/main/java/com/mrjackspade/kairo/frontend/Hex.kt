package com.mrjackspade.kairo.frontend

private val HEX_DIGITS = "0123456789abcdef".toCharArray()

/**
 * The bytes as lower-case hex, two digits each: the same text as formatting each with "%02x",
 * without String.format, which costs a fraction of a millisecond per byte on Android and runs for
 * every game in a library walk (GameKey.storageName).
 */
fun ByteArray.toHex(): String {
    val text = CharArray(size * 2)
    for (index in indices) {
        val value = this[index].toInt() and 0xff
        text[index * 2] = HEX_DIGITS[value ushr 4]
        text[index * 2 + 1] = HEX_DIGITS[value and 0x0f]
    }
    return String(text)
}
