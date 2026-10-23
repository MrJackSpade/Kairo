package com.mrjackspade.kairo.frontend

import android.view.View

/**
 * Names views for the UI tests (shared/uitest, docs/ui-tests.md), as a web page's ids and data
 * attributes name its elements for Selenium. An element has an id, such as "library.search" or a
 * row's id, and, when it stands for data such as a game, the key of that data. Ids are unique
 * within their page or row, not across the app: tests select an element inside its page.
 * The player never sees them.
 */
object TestAttributes {
    fun mark(view: View, id: String, key: String? = null) {
        view.setTag(R.id.kairo_test_id, id)
        view.setTag(R.id.kairo_test_key, key)
    }

    fun id(view: View): String? = view.getTag(R.id.kairo_test_id) as? String
    fun key(view: View): String? = view.getTag(R.id.kairo_test_key) as? String

    /** A page's root: its kind, and the title the player sees, when it has one. */
    const val PAGE = "page"
}

/**
 * The document provider of a UI test build (shared/uitest/provider): it serves the files the tests
 * place in [DIRECTORY] of the app's own storage, as a folder the system picker would return.
 */
object UiTestDocuments {
    const val DIRECTORY = "uitest-documents"
    fun authority(packageName: String) = "$packageName.uitest.documents"
}

/**
 * A running session as the UI tests read it (KairoActivity.gameProbe): the game's title, the state
 * the menu shows, and details by name, such as "frames" and "media.Drive 1".
 */
class GameProbe(val title: String?, val state: String, val details: Map<String, String>) {
    override fun toString() = "GameProbe(title=$title, state=$state, details=$details)"
}

/** [TestAttributes.mark] as an extension, for the views a builder returns. */
fun <V : View> V.testId(id: String, key: String? = null): V = apply { TestAttributes.mark(this, id, key) }
