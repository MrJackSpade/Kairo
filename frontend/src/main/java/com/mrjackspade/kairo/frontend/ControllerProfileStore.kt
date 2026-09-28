package com.mrjackspade.kairo.frontend

import android.content.SharedPreferences

/** Persistence shared by every Kairo app's global and physical controller profiles. */
class ControllerProfileStore(
    private val preferences: SharedPreferences,
    private val parseGuest: (String?) -> List<ControllerBinding>,
    private val encodeGuest: (List<ControllerBinding>) -> String
) {
    fun global() = parseGuest(preferences.getString("controller_global_v1", null))

    fun saveGlobal(bindings: List<ControllerBinding>) {
        preferences.edit().putString("controller_global_v1", encodeGuest(bindings)).apply()
    }

    fun resetGlobal() { preferences.edit().remove("controller_global_v1").apply() }

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
