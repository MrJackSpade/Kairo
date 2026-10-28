package com.mrjackspade.kairo.frontend

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Text the player reads, put into the device's language when it is shown. Code without a
 * [Context], such as a catalog schema, an option's choices or a key table, holds its text as
 * this and the screen resolves it: a string resource with its format arguments, a plural for a
 * count, or [Fixed] text that no language changes, such as a file name, a key legend or a
 * product name. An argument that is UiText itself is resolved first.
 */
sealed class UiText {
    abstract fun resolve(context: Context): String

    /**
     * UiText has no words without a Context: a string template or `+` that reaches here would show
     * the player a class name, so it fails instead. Resolve it with [Context.text].
     */
    final override fun toString(): String = error("UiText is resolved with a Context (Context.text), not toString()")

    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText() {
        override fun resolve(context: Context): String =
            if (args.isEmpty()) context.getString(id) else context.getString(id, *resolved(context, args))
    }

    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText() {
        override fun resolve(context: Context): String =
            context.resources.getQuantityString(id, count, *resolved(context, args))
    }

    data class Fixed(val text: String) : UiText() {
        override fun resolve(context: Context) = text
    }

    companion object {
        fun res(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText =
            Plural(id, count, if (args.isEmpty()) listOf(count) else args.toList())
        fun fixed(text: String): UiText = Fixed(text)

        private fun resolved(context: Context, args: List<Any>): Array<Any> =
            args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray()
    }
}

/** [text] in the device's language. */
fun Context.text(text: UiText): String = text.resolve(this)
