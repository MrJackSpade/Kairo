package com.mrjackspade.kairo.frontend

import org.junit.Assert.*
import org.junit.Test

class ControllerConfigurationTest {
    private fun device(left: Boolean, right: Boolean, external: Boolean = false, id: String = "pad") =
        ControllerCapabilities.Device(id, id, external, left, right)

    /** Kairo#173: a game's layer may add to a D-pad direction, never take the arrow keys under it away. */
    @Test fun aGamesLayerKeepsTheArrowKeysUnderItsDpad() {
        val below = listOf(ControllerBinding("virtual:up", listOf(72), joystick = "up"),
            ControllerBinding("virtual:down", listOf(80)), ControllerBinding("virtual:left", listOf(75)),
            ControllerBinding("virtual:right", listOf(77)), ControllerBinding("virtual:a", listOf(28)))
        val layer = listOf(ControllerBinding("virtual:up", mouse = "moveUp"),
            ControllerBinding("virtual:down", joystick = "down"),
            ControllerBinding("virtual:left", cycleKeys = listOf(2, 3)),
            ControllerBinding("virtual:right", listOf(77, 4)),
            ControllerBinding("virtual:a", listOf(57)))
        val merged = ControllerDefaults.cascade(below, ControllerDefaults.keepArrowKeys(below, layer)).associateBy { it.input }
        assertEquals(ControllerBinding("virtual:up", listOf(72), mouse = "moveUp"), merged["virtual:up"])
        assertEquals(ControllerBinding("virtual:down", listOf(80), joystick = "down"), merged["virtual:down"])
        assertEquals(ControllerBinding("virtual:left", listOf(75), cycleKeys = listOf(2, 3)), merged["virtual:left"])
        assertEquals("an arrow key the game also sends is sent once", listOf(77, 4), merged["virtual:right"]!!.keys)
        assertEquals("a button outside the D-pad is the game's", listOf(57), merged["virtual:a"]!!.keys)
    }

    @Test fun aDpadThePlayerLeftWithoutKeysGetsNoneFromAGame() {
        val below = listOf(ControllerBinding("virtual:up", joystick = "up"))
        val layer = listOf(ControllerBinding("virtual:up", mouse = "moveUp"))
        assertEquals(layer, ControllerDefaults.keepArrowKeys(below, layer))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aShippedActionOnTheDpadFails() {
        ControllerDefaults.keepArrowKeys(listOf(ControllerBinding("virtual:up", listOf(72))),
            listOf(ControllerBinding("virtual:up", action = "menu")))
    }

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
        val system = listOf(ControllerBinding("virtual:b", keys = listOf(27)))
        fun store() = ControllerProfileStore(preferences, { if (it == "legacy") legacy else error("no layer saved") },
            { "edited" }, { system }, configuration)
        val store = store()
        // A whole mapping saved before layers still plays as it did: A, the menu, nothing else.
        val asSaved = listOf(ControllerBinding("virtual:a", keys = listOf(13)), ControllerBinding("virtual:menu", action = "menu"))
        configuration.select(ControllerConfiguration.Choice.WITH_STICKS)
        assertEquals(asSaved, store.global())
        store.resetGlobal()
        assertEquals(system, store.global())
        assertEquals(system, store().global()) // Creating the store again must not repeat migration.
        configuration.select(ControllerConfiguration.Choice.WITHOUT_STICKS)
        assertEquals(asSaved, store.global())
        assertEquals("physical", preferences.getString("controller_physical_v1", null))
        assertEquals("legacy", preferences.getString("controller_global_v1", null))
    }

    private val arrows = listOf(ControllerBinding("virtual:up", listOf(273), joystick = "joy1up"),
        ControllerBinding("virtual:down", listOf(274), joystick = "joy1down"),
        ControllerBinding("virtual:left", listOf(276), joystick = "joy1left"),
        ControllerBinding("virtual:right", listOf(275), joystick = "joy1right"),
        ControllerBinding("virtual:lsup", joystick = "joy1up"), ControllerBinding("virtual:a", listOf(13)))

    @Test
    fun layersReplaceInputByInputAndWhatTheyLeaveCascades() {
        // A game that binds its D-pad plays with its own D-pad; its left stick and A come from below.
        val game = listOf(ControllerBinding("virtual:up", joystick = "joy1up"), ControllerBinding("virtual:b", listOf(32)))
        assertEquals(listOf(ControllerBinding("virtual:up", joystick = "joy1up")) + arrows.drop(1) +
            ControllerBinding("virtual:b", listOf(32)), ControllerDefaults.cascade(arrows, game))
        // A game that leaves its D-pad alone keeps the system's arrow keys.
        assertEquals(arrows, ControllerDefaults.cascade(arrows, emptyList()))
    }

    @Test
    fun anExplicitNullRemovesTheInputFromEveryLayerBelow() {
        val player = listOf(ControllerBinding("virtual:up"), ControllerBinding("virtual:a", listOf(32)))
        assertEquals(arrows.drop(1).dropLast(1) + ControllerBinding("virtual:a", listOf(32)),
            ControllerDefaults.cascade(arrows, emptyList(), player))
    }

    @Test
    fun aSavedLayerHoldsWhatChangedAndNullsWhatWasUnbound() {
        val edited = arrows.filterNot { it.input == "virtual:lsup" }.map {
            if (it.input == "virtual:a") ControllerBinding("virtual:a", listOf(32)) else it
        } + ControllerBinding("virtual:x", listOf(120))
        val layer = ControllerDefaults.layerOver(arrows, edited)
        assertEquals(listOf(ControllerBinding("virtual:a", listOf(32)), ControllerBinding("virtual:x", listOf(120)),
            ControllerBinding("virtual:lsup")), layer)
        assertEquals(edited.toSet(), ControllerDefaults.cascade(arrows, layer).toSet())
        // Unchanged: nothing saved, so later changes below still cascade through.
        assertEquals(emptyList<ControllerBinding>(), ControllerDefaults.layerOver(arrows, arrows))
    }

    @Test
    fun aMappingSavedBeforeLayersPlaysAsItDid() {
        val saved = listOf(ControllerBinding("virtual:up", listOf(119)), ControllerBinding("virtual:a", listOf(13)))
        val played = ControllerDefaults.cascade(arrows + ControllerBinding("virtual:rsup", joystick = "joy2up"),
            ControllerDefaults.legacyLayer(saved))
        assertEquals(setOf(ControllerBinding("virtual:up", listOf(119)), ControllerBinding("virtual:a", listOf(13)),
            ControllerBinding("virtual:lsup", listOf(119)), ControllerBinding("virtual:menu", action = "menu")), played.toSet())
    }
}
