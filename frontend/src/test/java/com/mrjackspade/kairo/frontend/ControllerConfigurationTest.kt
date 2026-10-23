package com.mrjackspade.kairo.frontend

import org.junit.Assert.*
import org.junit.Test

class ControllerConfigurationTest {
    private fun device(left: Boolean, right: Boolean, external: Boolean = false, id: String = "pad") =
        ControllerCapabilities.Device(id, id, external, left, right)

    @Test fun autoRequiresTwoSticksOnOneController() {
        assertEquals(ControllerLayout.WITHOUT_STICKS, ControllerCapabilities.detect(emptyList()).layout)
        assertEquals(ControllerLayout.WITHOUT_STICKS, ControllerCapabilities.detect(listOf(device(false, false))).layout)
        assertEquals(ControllerLayout.WITHOUT_STICKS, ControllerCapabilities.detect(listOf(device(true, false))).layout)
        assertEquals(ControllerLayout.WITHOUT_STICKS, ControllerCapabilities.detect(listOf(device(false, true))).layout)
        assertEquals(ControllerLayout.WITHOUT_STICKS, ControllerCapabilities.detect(listOf(
            device(true, false, id = "a"), device(false, true, id = "b"))).layout)
        assertEquals(ControllerLayout.WITH_STICKS, ControllerCapabilities.detect(listOf(device(true, true))).layout)
    }

    @Test fun builtInControllerTakesPrecedenceOverExternalTwoStickController() {
        val builtin = device(false, false)
        val external = device(true, true, external = true)
        assertEquals(builtin, ControllerCapabilities.detect(listOf(external, builtin)).device)
        assertEquals(builtin, ControllerCapabilities.detect(listOf(builtin, external)).device)
    }

    @Test fun rememberedExternalDescriptorKeepsHandheldSelected() {
        val handheld = device(false, false, external = true, id = "z-handheld")
        val added = device(true, true, external = true, id = "a-usb-pad")
        assertEquals(handheld, ControllerCapabilities.detect(listOf(added, handheld), handheld.descriptor).device)
    }

    @Test fun controllerlessAutoRequiresAnExplicitBootChoice() {
        val preferences = memoryPreferences()
        val configuration = ControllerConfiguration(preferences) { ControllerCapabilities.detect(emptyList()) }
        assertTrue(configuration.needsSetup)
        configuration.select(ControllerConfiguration.Choice.AUTO)
        assertTrue(configuration.needsSetup)
        configuration.select(ControllerConfiguration.Choice.WITHOUT_STICKS)
        assertFalse(configuration.needsSetup)
    }

    @Test fun absentVariantFallsBackButEmptyVariantDisablesBindings() {
        val legacy = listOf("a")
        assertEquals(ControllerDefaults.Resolution(legacy, true),
            ControllerDefaults.resolve(ControllerLayout.WITH_STICKS, legacy, null))
        assertEquals(ControllerDefaults.Resolution(emptyList<String>(), false),
            ControllerDefaults.resolve(ControllerLayout.WITH_STICKS, legacy, emptyList()))
        assertEquals(ControllerDefaults.Resolution(legacy, false),
            ControllerDefaults.resolve(ControllerLayout.WITHOUT_STICKS, legacy, listOf("b")))
    }

    @Test fun automaticChangesWaitUntilSessionEndsAndManualChoicePersists() {
        val preferences = memoryPreferences()
        var detection = ControllerCapabilities.detect(listOf(device(true, true)))
        val configuration = ControllerConfiguration(preferences) { detection }
        assertFalse(configuration.configured)
        configuration.select(ControllerConfiguration.Choice.AUTO)
        configuration.beginSession()
        detection = ControllerCapabilities.detect(emptyList())
        configuration.devicesChanged()
        assertEquals(ControllerLayout.WITH_STICKS, configuration.layout)
        configuration.endSession()
        assertEquals(ControllerLayout.WITHOUT_STICKS, configuration.layout)
        configuration.select(ControllerConfiguration.Choice.WITH_STICKS)
        assertEquals(ControllerLayout.WITH_STICKS, ControllerConfiguration(preferences) { detection }.layout)
    }

    @Test fun migrationCopiesLegacyOverridesOnceAndResetIsConfigurationSpecific() {
        val preferences = memoryPreferences(mapOf("controller_global_v1" to "legacy", "controller_physical_v1" to "physical"))
        val configuration = ControllerConfiguration(preferences) { ControllerCapabilities.detect(emptyList()) }
        val legacy = listOf(ControllerBinding("virtual:a", keys = listOf(13)))
        val default = listOf(ControllerBinding("virtual:b", keys = listOf(27)))
        fun store() = ControllerProfileStore(preferences, { if (it == "legacy") legacy else default },
            { "edited" }, { default }, configuration)
        val store = store()
        configuration.select(ControllerConfiguration.Choice.WITH_STICKS)
        assertEquals(legacy, store.global())
        store.resetGlobal()
        assertEquals(default, store.global())
        assertEquals(default, store().global()) // Creating the store again must not repeat migration.
        configuration.select(ControllerConfiguration.Choice.WITHOUT_STICKS)
        assertEquals(legacy, store.global())
        assertEquals("physical", preferences.getString("controller_physical_v1", null))
        assertEquals("legacy", preferences.getString("controller_global_v1", null))
    }

    @Test
    fun aProfileKeepsItsDirectionsAndLeavesNoButtonUnbound() {
        val steering = listOf(ControllerBinding("virtual:up", listOf(273)), ControllerBinding("virtual:a", listOf(32)))
        val standard = listOf(ControllerBinding("virtual:up", listOf(119)), ControllerBinding("virtual:lsup", joystick = "joy1up"),
            ControllerBinding("virtual:a", listOf(13)), ControllerBinding("virtual:b", listOf(27)),
            ControllerBinding("virtual:start", listOf(27)), ControllerBinding("virtual:menu", action = "menu"))
        assertEquals(steering + listOf(ControllerBinding("virtual:b", listOf(27)), ControllerBinding("virtual:start", listOf(27))),
            ControllerDefaults.layered(steering, standard))
    }
}
