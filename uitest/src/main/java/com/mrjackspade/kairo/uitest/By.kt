package com.mrjackspade.kairo.uitest

import android.view.View
import com.mrjackspade.kairo.frontend.TestAttributes

/**
 * Selects views, as Selenium's By does: by the test id and key the app gives its elements
 * (TestAttributes), by the text they show, or by both. A selector is matched afresh every time an
 * element is used, so it never refers to a view that has gone.
 */
class By private constructor(val description: String, private val test: (View) -> Boolean) {
    fun matches(view: View) = test(view)

    infix fun and(other: By) = By("$description, ${other.description}") { test(it) && other.test(it) }

    /** Also requires the key, such as a game's entry id or a modal button's label. */
    fun withKey(key: String) = and(key(key))

    /** Also requires a descendant (or the view itself) to show exactly [text]. */
    fun withText(text: String) = and(By("containing text \"$text\"") { view -> Views.texts(view).any { it == text } })

    /** Also requires a descendant (or the view itself) to show text containing [part], ignoring case. */
    fun withTextContaining(part: String) = and(By("containing text with \"$part\"") { view ->
        Views.texts(view).any { it.contains(part, ignoreCase = true) }
    })

    override fun toString() = description

    companion object {
        fun id(id: String) = By("id=$id") { TestAttributes.id(it) == id }
        fun key(key: String) = By("key=$key") { TestAttributes.key(it) == key }

        /** An id matching [pattern], for ids that carry an index, such as "firmware0-n88.rom". */
        fun idMatching(pattern: Regex) = By("id~/$pattern/") { TestAttributes.id(it)?.let(pattern::matches) == true }

        /** A page by the name the app gives it: its title, or its kind, such as "flyout" or "modal". */
        fun page(name: String) = By("page \"$name\"") {
            TestAttributes.id(it) == TestAttributes.PAGE && TestAttributes.key(it) == name
        }

        /** The view itself shows exactly [text]. */
        fun text(text: String) = By("text \"$text\"") { Views.ownText(it) == text }

        /** The view itself shows text matching [pattern]. */
        fun textMatching(pattern: Regex) = By("text matching /$pattern/") { view ->
            Views.ownText(view)?.let(pattern::containsMatchIn) == true
        }
    }
}
