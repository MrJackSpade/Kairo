package com.mrjackspade.kairo.uitest

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.mrjackspade.kairo.frontend.UiTestDocuments
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

/**
 * The base of every UI test. Each test starts the app fresh in the UI test build (its own
 * package, beside any installed copy), with the test resources in place and a log of its own; it
 * drives the app through [driver] and [screens] and checks it through [expect]. The runner clears
 * the app's data between tests (tools/uitest.ps1, Android Test Orchestrator), so every test begins
 * at first run.
 */
abstract class KairoUiTest {
    abstract val app: AppUnderTest

    @get:Rule
    val session = AppSession { app }

    val driver: Driver get() = session.driver
    val screens: Screens get() = session.screens
    val expect: Expect get() = session.driver.expect
}

/** Starts the app for one test and ends it after, keeping the record (TestLog). */
class AppSession(private val app: () -> AppUnderTest) : TestRule {
    lateinit var driver: Driver
        private set
    lateinit var screens: Screens
        private set

    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val log = TestLog(description.testClass.simpleName, description.methodName)
            driver = Driver(instrumentation, log)
            val documents = TestDocuments(instrumentation, log)
            screens = Screens(driver, app(), documents)
            log.step("start", description.displayName, mapOf("package" to instrumentation.targetContext.packageName))
            try {
                requireFreshData()
                documents.install()
                launch()
                base.evaluate()
                log.step("result", "passed")
            } catch (failure: Throwable) {
                log.step("result", "failed", mapOf("error" to failure.toString()))
                try { driver.pictures.screenshot("failure") } catch (screenshot: Throwable) { failure.addSuppressed(screenshot) }
                try { log.text("failure-views", driver.onMain { Views.dump() }) } catch (dump: Throwable) { failure.addSuppressed(dump) }
                throw failure
            } finally {
                finishActivities()
                log.close()
            }
        }
    }

    /**
     * The orchestrator clears the app's data before each test; a test on old data proves nothing.
     * The test documents folder, which only a test creates, shows whether an earlier test ran here.
     */
    private fun requireFreshData() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val earlier = File(target.filesDir, UiTestDocuments.DIRECTORY)
        check(!earlier.exists()) {
            "The app's data was not cleared before this test ($earlier exists); run it with clearPackageData, " +
                "as tools/uitest.ps1 does"
        }
    }

    /** Closes the app and starts it again from its launcher, keeping its data, as a player reopening it. */
    fun relaunch() {
        driver.log.step("relaunch", "closing and reopening the app")
        finishActivities()
        launch()
    }

    private fun launch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val intent = checkNotNull(target.packageManager.getLaunchIntentForPackage(target.packageName)) {
            "${target.packageName} has no launcher activity"
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        driver.log.step("launch", intent.component.toString())
        instrumentation.startActivitySync(intent)
        driver.idle()
    }

    private fun finishActivities() {
        val open = driver.onMain {
            val registry = ActivityLifecycleMonitorRegistry.getInstance()
            listOf(Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.STARTED, Stage.CREATED)
                .flatMap { registry.getActivitiesInStage(it) }.distinct().onEach { it.finish() }
        }
        driver.log.step("finish", "${open.size} activities")
        driver.waitUntil("every activity to close") {
            driver.onMain {
                val registry = ActivityLifecycleMonitorRegistry.getInstance()
                listOf(Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.STARTED, Stage.CREATED)
                    .all { registry.getActivitiesInStage(it).isEmpty() }
            }
        }
    }
}
