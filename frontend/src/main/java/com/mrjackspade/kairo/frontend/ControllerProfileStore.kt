package com.mrjackspade.kairo.frontend

import android.content.SharedPreferences

/** Persistence shared by every Kairo app's global and physical controller profiles. */
class ControllerProfileStore(
    private val preferences: SharedPreferences,
    private val parseGuest: (String) -> List<ControllerBinding>,
    private val encodeGuest: (List<ControllerBinding>) -> String,
    /** The system's mapping, from its catalog. */
    private val system: () -> List<ControllerBinding>,
    val configuration: ControllerConfiguration = ControllerConfiguration(preferences)
) {
    // The player's global layer over the system's defaults; before layers, the whole mapping (v2).
    private val globalKey get() = "controller_global_${configuration.layout.key}_layer_v3"
    private val legacyGlobalKey get() = "controller_global_${configuration.layout.key}_v2"

    init {
        // Copy legacy overrides to both configurations once. Never infer intent from their contents.
        if (!preferences.getBoolean("controller_variants_migrated_v2", false)) {
            val edit = preferences.edit()
            preferences.getString("controller_global_v1", null)?.let { legacy ->
                ControllerLayout.entries.forEach { layout ->
                    val key = "controller_global_${layout.key}_v2"
                    if (!preferences.contains(key)) edit.putString(key, legacy)
                }
            }
            edit.putBoolean("controller_variants_migrated_v2", true).apply()
        }
    }

    /** The system's mapping, as its catalog defines it: the lowest layer of every mapping. */
    fun system(): List<ControllerBinding> = system.invoke()

    /** The player's own global layer: what they changed, with an explicit null for what they unbound. */
    fun globalLayer(): List<ControllerBinding> =
        preferences.getString(globalKey, null)?.let(parseGuest)
            ?: preferences.getString(legacyGlobalKey, null)?.let { ControllerDefaults.legacyLayer(parseGuest(it)) }
            ?: emptyList()

    fun global(): List<ControllerBinding> = ControllerDefaults.cascade(system(), globalLayer())

    fun saveGlobal(bindings: List<ControllerBinding>) {
        preferences.edit().putString(globalKey, encodeGuest(ControllerDefaults.layerOver(system(), bindings)))
            .remove(legacyGlobalKey).apply()
    }

    fun resetGlobal() { preferences.edit().remove(globalKey).remove(legacyGlobalKey).apply() }

    /** Whether the player changed the global mapping for the current layout. */
    val globalCustom: Boolean get() = preferences.contains(globalKey) || preferences.contains(legacyGlobalKey)

    val physicalCustom: Boolean get() = preferences.contains("controller_physical_v1")

    fun physical() = PhysicalControllerBindings.parse(
        preferences.getString("controller_physical_v1", null))

    fun savePhysical(bindings: List<PhysicalControllerBinding>) {
        preferences.edit().putString("controller_physical_v1",
            PhysicalControllerBindings.toJson(bindings).toString()).apply()
    }

    fun resetPhysical() { preferences.edit().remove("controller_physical_v1").apply() }

    var deadZone: Float
        get() = preferences.getFloat("controller_dead_zone", GamepadMapper.DEFAULT_DEAD_ZONE)
        set(value) { preferences.edit().putFloat("controller_dead_zone", value).apply() }
}
