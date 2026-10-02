package com.mrjackspade.kairo.frontend

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import java.util.concurrent.atomic.AtomicInteger

/** Real ListView regression: moving within the viewport must not resolve/rebind all rows. */
object LibraryScrollFixture {
    private fun onUi(test: Instrumentation, action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (caught: Throwable) { failure = caught } }
        test.waitForIdleSync()
        failure?.let { throw it }
    }
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
        var pinned: String? = null
        fun ui(action: () -> Unit) {
            onUi(instrumentation, action)
        }
        fun findList(view: View): ListView? = if (view is ListView) view else
            (view as? ViewGroup)?.let { group ->
                (0 until group.childCount).firstNotNullOfOrNull { findList(group.getChildAt(it)) }
            }
        try {
            ui {
                screen = LibraryScreen(activity, catalog, LibraryStrings("Fixture"), {}, {}, {}, {},
                    null, {}, emptyList(), { pinned }, {}, {}, {}, { selection = it?.id })
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
            // Repeat events can reverse before ListView applies its pending scroll.
            ui {
                repeat(12) { screen.moveSelection(1) }
                repeat(12) { screen.moveSelection(-1) }
            }
            Thread.sleep(150) // allow the pending traversal to settle before checking it
            ui {
                check(selection == "0")
                val selected = (0 until list.childCount).map(list::getChildAt).singleOrNull { it.isActivated }
                check(selected != null && selected.top >= list.paddingTop &&
                    selected.bottom <= list.height - list.paddingBottom) {
                    "Rapid reversal left selection outside viewport: first=${list.firstVisiblePosition}, selected=$selection, bounds=${selected?.top}..${selected?.bottom}, height=${list.height}"
                }
            }
            ui { pinned = "8"; screen.showEntries(entries) }
            verifyHeldNavigation(instrumentation, screen, list)
            verifyArtworkSpace(instrumentation, activity)
            verifyDescription(instrumentation, activity)
        } finally { ui { activity.finish() } }
    }

    private fun verifyHeldNavigation(test: Instrumentation, screen: LibraryScreen<*>, list: ListView) {
        var badFrame: String? = null
        val draw = android.view.ViewTreeObserver.OnDrawListener {
            if (list.count > 0) {
                val active = (0 until list.childCount).map(list::getChildAt).filter { it.isActivated }
                if (active.size != 1 || active.single().top < list.paddingTop ||
                    active.single().bottom > list.height - list.paddingBottom)
                    badFrame = "Rendered selection outside viewport: first=${list.firstVisiblePosition}, active=${active.map { it.top to it.bottom }}"
            }
        }
        fun send(event: android.view.KeyEvent) {
            val control = if (event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) "down" else "up"
            check(FrontendNavigation.library(screen, control, event, {}))
        }
        fun keys(code: Int) {
            val down = android.os.SystemClock.uptimeMillis()
            repeat(40) { repeat ->
                onUi(test) { send(android.view.KeyEvent(down, android.os.SystemClock.uptimeMillis(),
                    android.view.KeyEvent.ACTION_DOWN, code, repeat, 0, -1, 0, 0,
                    android.view.InputDevice.SOURCE_DPAD)) }
                Thread.sleep(35)
            }
        }
        val hatType = Class.forName("com.mrjackspade.kairo.frontend.DpadMotionNavigation")
        val hat = hatType.constructors.single().newInstance(::send)
        fun axis(y: Float) {
            val now = android.os.SystemClock.uptimeMillis()
            val properties = android.view.MotionEvent.PointerProperties().apply { id = 0 }
            val coords = android.view.MotionEvent.PointerCoords().apply {
                setAxisValue(android.view.MotionEvent.AXIS_HAT_Y, y)
            }
            val event = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_MOVE,
                1, arrayOf(properties), arrayOf(coords), 0, 0, 1f, 1f, 0, 0,
                android.view.InputDevice.SOURCE_JOYSTICK, 0)
            try { onUi(test) { hatType.getMethod("motion", android.view.MotionEvent::class.java).invoke(hat, event) } }
            finally { event.recycle() }
        }
        onUi(test) { list.viewTreeObserver.addOnDrawListener(draw) }
        try {
            keys(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
            keys(android.view.KeyEvent.KEYCODE_DPAD_UP)
            axis(1f)
            Thread.sleep(2000)
            axis(-1f)
            Thread.sleep(2000)
            axis(0f)
            // Filtering must reposition the retained selection in the new adapter.
            fun search(view: View): android.widget.EditText? = if (view is android.widget.EditText) view else
                (view as? ViewGroup)?.let { group ->
                    (0 until group.childCount).firstNotNullOfOrNull { search(group.getChildAt(it)) }
                }
            onUi(test) { search(screen)!!.setText("Game 2") }
            keys(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
            keys(android.view.KeyEvent.KEYCODE_DPAD_UP)
            onUi(test) { search(screen)!!.setText("") }
            Thread.sleep(150)
            check(badFrame == null) { badFrame!! }
        } finally {
            onUi(test) {
                hatType.getMethod("stop").invoke(hat)
                list.viewTreeObserver.removeOnDrawListener(draw)
            }
        }
    }

    private fun verifyArtworkSpace(test: Instrumentation, activity: android.app.Activity) {
        fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name)
            .apply { isAccessible = true }.get(owner)
        lateinit var content: Any
        lateinit var art: View
        lateinit var description: View
        val info = SecondaryDisplayCoordinator.LibraryInfo("Fixture", emptyList(), "Artwork reservation", null, true)
        fun ui(action: () -> Unit) { onUi(test, action) }
        ui {
            val coordinator = activity.javaClass.declaredFields.single {
                it.type == SecondaryDisplayCoordinator::class.java
            }.apply { isAccessible = true }.get(activity)!!
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
        fun ui(action: () -> Unit) { onUi(test, action) }
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
