package com.mrjackspade.kairo.frontend

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import java.util.concurrent.atomic.AtomicInteger

/** Real ListView regression: moving within the viewport must not resolve/rebind all rows. */
object LibraryScrollFixture {
    fun verify(instrumentation: Instrumentation) {
        val context = instrumentation.targetContext
        val launch = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        val activity = instrumentation.startActivitySync(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val resolves = AtomicInteger()
        val catalog = object : LibraryCatalog {
            override fun resolve(contentId: String, fileName: String) = object : LibraryGame {
                init { resolves.incrementAndGet() }
                override val title = fileName
                override val description: String? = null
                override val boxArt: String? = null
                override val preview: String? = null
                override val tags = emptyList<String>()
            }
            override fun hiddenFromLibrary(contentId: String) = false
            override fun openArtwork(path: String): java.io.InputStream = error("No artwork")
        }
        val entries = (0..29).map { index -> object : LibraryItem {
            override val id = index.toString()
            override val contentId = id
            override val path = "Game $index.zip"
            override val displayName = "Game $index"
            override val zipEntry: String? = null
            override val error: String? = null
            override val playable = true
            override val mediaLabel = "fixture"
        } }
        lateinit var screen: LibraryScreen<LibraryItem>
        lateinit var list: ListView
        var selection: String? = null
        fun ui(action: () -> Unit) {
            instrumentation.runOnMainSync(action)
            instrumentation.waitForIdleSync()
        }
        fun findList(view: View): ListView? = if (view is ListView) view else
            (view as? ViewGroup)?.let { group ->
                (0 until group.childCount).firstNotNullOfOrNull { findList(group.getChildAt(it)) }
            }
        try {
            ui {
                screen = LibraryScreen(activity, catalog, LibraryStrings("Fixture"), {}, {}, {}, {},
                    null, {}, emptyList(), { null }, {}, {}, {}, { selection = it?.id })
                activity.setContentView(screen)
                screen.showEntries(entries)
                list = findList(screen)!!
            }
            check(resolves.get() > 0 && list.childCount >= 2)
            val before = resolves.get()
            repeat(8) {
                ui { screen.moveSelection(1) }
                check(selection == "1")
                ui { screen.moveSelection(-1) }
                check(selection == "0")
            }
            check(resolves.get() == before) { "Selection rebound rows: $before -> ${resolves.get()}" }
            // Recycled/new rows must still acquire the correct activation state.
            repeat(12) { ui { screen.moveSelection(1) } }
            ui {
                check(selection == "12")
                check((0 until list.childCount).count { list.getChildAt(it).isActivated } == 1)
                val active = (0 until list.childCount).map(list::getChildAt).single { it.isActivated }
                check(list.getPositionForView(active) == 12)
            }
            val afterScroll = resolves.get()
            repeat(12) { ui { screen.moveSelection(-1) } }
            check(resolves.get() == afterScroll) { "Revisiting rows reread the catalog" }
            ui { screen.showEntries(entries) }
            check(resolves.get() > afterScroll) { "New catalog/library snapshot retained stale row metadata" }
            verifyArtworkSpace(instrumentation, activity)
            verifyDescription(instrumentation, activity)
        } finally { ui { activity.finish() } }
    }

    private fun verifyArtworkSpace(test: Instrumentation, activity: android.app.Activity) {
        fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name)
            .apply { isAccessible = true }.get(owner)
        lateinit var content: Any
        lateinit var art: View
        lateinit var description: View
        val info = SecondaryDisplayCoordinator.LibraryInfo("Fixture", emptyList(), "Artwork reservation", null, true)
        fun ui(action: () -> Unit) { test.runOnMainSync(action); test.waitForIdleSync() }
        ui {
            val coordinator = activity.javaClass.declaredFields.single {
                it.type == SecondaryDisplayCoordinator::class.java
            }.apply { isAccessible = true }.get(activity)
            val companion = checkNotNull(field(coordinator, "companion")) { "RGDS companion display missing" }
            content = field(companion, "content")!!
            art = field(content, "libraryArt") as View
            description = field(content, "libraryDescription") as View
            content.javaClass.getMethod("setAppearance", Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                SecondaryDisplayCoordinator.LibraryInfo::class.java).invoke(content, false, 0, false, info)
        }
        val width = description.width
        check(width > 0 && art.visibility == View.INVISIBLE)
        val bitmap = android.graphics.Bitmap.createBitmap(32, 16, android.graphics.Bitmap.Config.ARGB_8888)
        ui {
            content.javaClass.getMethod("setAppearance", Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                SecondaryDisplayCoordinator.LibraryInfo::class.java).invoke(content, false, 0, false, info.copy(art = bitmap))
        }
        check(description.width == width && art.visibility == View.VISIBLE) { "Artwork arrival reflowed the description" }
    }

    private fun verifyDescription(test: Instrumentation, activity: android.app.Activity) {
        // The view is internal to frontend; reflect only in the test fixture so no
        // testing hooks become public application APIs.
        val type = Class.forName("com.mrjackspade.kairo.frontend.LibraryDescriptionView")
        lateinit var view: View
        val rendered = type.getDeclaredField("rendered").apply { isAccessible = true }
        fun ui(action: () -> Unit) { test.runOnMainSync(action); test.waitForIdleSync() }
        val finalText = "Latest selected game. ".repeat(100)
        ui {
            view = type.getConstructor(android.content.Context::class.java).newInstance(activity) as View
            activity.setContentView(view)
        }
        ui {
            type.getMethod("setText", String::class.java).invoke(view, "Old description. ".repeat(1000))
            type.getMethod("setText", String::class.java).invoke(view, finalText)
            type.getMethod("setMaxLines", Int::class.javaPrimitiveType).invoke(view, 2)
        }
        val deadline = android.os.SystemClock.uptimeMillis() + 5000
        var ready = false
        while (!ready && android.os.SystemClock.uptimeMillis() < deadline) {
            ui {
                val layout = rendered.get(view) as? android.text.StaticLayout
                ready = layout != null && layout.text.toString().startsWith("Latest selected game.") && layout.lineCount <= 2
            }
            if (!ready) Thread.sleep(20)
        }
        check(ready) { "Description did not publish the latest bounded paragraph" }
        ui {
            val layout = rendered.get(view) as android.text.StaticLayout
            check(layout.width == view.width)
            check(view.contentDescription.toString() == finalText)
            val root = view.parent as ViewGroup
            root.removeView(view)
            root.addView(view)
        }
        ui { check(rendered.get(view) != null) { "Description lost its layout after reattachment" } }
    }
}
