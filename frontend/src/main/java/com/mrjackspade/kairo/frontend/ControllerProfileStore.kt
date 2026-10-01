package com.mrjackspade.kairo.frontend

import android.content.SharedPreferences

/** Persistence shared by every Kairo app's global and physical controller profiles. */
class ControllerProfileStore(
    private val preferences: SharedPreferences,
    private val parseGuest: (String?) -> List<ControllerBinding>,
    private val encodeGuest: (List<ControllerBinding>) -> String,
    private val defaults: ((ControllerLayout) -> List<ControllerBinding>)? = null,
    val configuration: ControllerConfiguration = ControllerConfiguration(preferences)
) {
    private val globalKey get() = "controller_global_${configuration.layout.key}_v2"

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

    fun global(): List<ControllerBinding> = preferences.getString(globalKey, null)?.let(parseGuest)
        ?: defaults?.invoke(configuration.layout) ?: parseGuest(null)

    fun saveGlobal(bindings: List<ControllerBinding>) {
        preferences.edit().putString(globalKey, encodeGuest(bindings)).apply()
    }

    fun resetGlobal() { preferences.edit().remove(globalKey).apply() }

    fun physical() = PhysicalControllerBindings.parse(
        preferences.getString("controller_physical_v1", null))

    fun savePhysical(bindings: List<PhysicalControllerBinding>) {
        preferences.edit().putString("controller_physical_v1",
            PhysicalControllerBindings.toJson(bindings).toString()).apply()
    }

    fun resetPhysical() { preferences.edit().remove("controller_physical_v1").apply() }

    var deadZone: Float
        get() = preferences.getFloat("controller_dead_zone", 0.35f)
        set(value) { preferences.edit().putFloat("controller_dead_zone", value).apply() }
}
