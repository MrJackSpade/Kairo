package com.mrjackspade.kairo.frontend

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A downloaded catalog update carries controller mappings (Kairo#55, Kairo#107): the system's from
 * its system definition and each game's from its record, above the shipped catalog and under the
 * player's own mapping; whatever the update leaves out comes from the shipped catalog.
 */
class CatalogUpdateControllerTest {
    private val game = "sha256-test-v1:ab01"
    private val other = "sha256-test-v1:ab02"
    private val key = GameKey("games/Game.dsk")

    private fun bindings(vararg pairs: Pair<String, Int>) = JSONArray().apply {
        for ((input, guestKey) in pairs) put(JSONObject().put("input", input).put("keys", JSONArray().put(guestKey)))
    }

    private fun system(bindings: JSONArray) = JSONObject().put("schemaVersion", 1)
        .put("controller", JSONObject().put("bindings", bindings))

    private fun games(vararg records: Pair<String, JSONObject>) = JSONObject().put("schemaVersion", 1)
        .put("games", JSONObject().apply { for ((id, record) in records) put(id, record) })

    private fun controller(defaults: JSONArray) = JSONObject().put("controller",
        JSONObject().put("defaults", JSONObject().put("withoutSticks", defaults)))

    private val shippedSystem = system(bindings("virtual:up" to 1, "virtual:a" to 2))
    private val shippedGames = games(
        game to controller(bindings("virtual:a" to 10)).put("title", "Shipped title"),
        other to controller(bindings("virtual:b" to 20)).put("title", "Other game"))

    private class Documents(val shipped: Map<String, JSONObject>, val update: Map<String, JSONObject>) : CatalogLayers.Sources {
        override fun shipped(path: String) = shipped[path] ?: JSONObject()
        override fun shippedMain(path: String) = shipped(path)
        override fun shippedAdditional(path: String) = JSONObject()
        override fun updated(path: String) = update[path]
        override fun updatedLanguage(path: String): JSONObject? = null
        override fun installed() = emptyList<InstalledCatalogs.Catalog>()
        override fun additions() = JSONObject().put("schemaVersion", 1).put("games", JSONObject())
        override fun override(game: GameKey): JSONObject? = null
    }

    private fun catalog(update: Map<String, JSONObject>) = CatalogLayers(Schema, Documents(mapOf(
        "manifest-v1.json" to JSONObject().put("schemaVersion", 1).put("shards", JSONArray()),
        "base-v1.json" to shippedGames,
        LayeredGameCatalog.SYSTEM_DOCUMENT to shippedSystem), update), null)

    private fun defaults(layers: CatalogLayers, id: String) =
        layers.resolve(id, null, null, "Game.dsk").controllerDefaultBindings?.let { JSONObject(it).getJSONArray("withoutSticks").toString() }

    @Test
    fun theSystemMappingComesFromTheUpdatesSystemDefinition() {
        val updated = bindings("virtual:up" to 5, "virtual:a" to 6)
        val layers = catalog(mapOf(LayeredGameCatalog.SYSTEM_DOCUMENT to system(updated)))
        assertEquals(updated.toString(), layers.systemControllerBindings().toString())
    }

    @Test
    fun theSystemMappingIsTheShippedOneWithoutAnUpdatedDefinition() {
        val layers = catalog(mapOf("base-v1.json" to games(game to controller(bindings("virtual:a" to 11)))))
        assertEquals(shippedSystem.getJSONObject("controller").getJSONArray("bindings").toString(),
            layers.systemControllerBindings().toString())
        assertEquals(shippedSystem.getJSONObject("controller").getJSONArray("bindings").toString(),
            catalog(emptyMap()).systemControllerBindings().toString())
    }

    @Test
    fun aGamesControllerDefaultsComeFromTheUpdatesRecord() {
        val layers = catalog(mapOf("base-v1.json" to games(game to controller(bindings("virtual:a" to 11)))))
        assertEquals(bindings("virtual:a" to 11).toString(), defaults(layers, game))
        assertEquals("no update: the shipped record's", bindings("virtual:a" to 10).toString(), defaults(catalog(emptyMap()), game))
    }

    @Test
    fun whatTheUpdateLeavesOutComesFromTheShippedCatalog() {
        val layers = catalog(mapOf("base-v1.json" to games(game to controller(bindings("virtual:a" to 11)))))
        val resolved = layers.resolve(game, null, null, "Game.dsk")
        assertEquals("the shipped title, which the update does not carry", "Shipped title", resolved.title)
        assertEquals("a game the update has no record for", bindings("virtual:b" to 20).toString(), defaults(layers, other))
        assertNull("no game record anywhere", catalog(emptyMap()).resolve("sha256-test-v1:ffff", null, null, "None.dsk").controllerDefaultBindings)
    }

    /** What a game plays with, as ControllerProfileCoordinator layers it: the player's layer over the catalog's. */
    @Test
    fun thePlayersMappingStaysAboveBoth() {
        val layers = catalog(mapOf(LayeredGameCatalog.SYSTEM_DOCUMENT to system(bindings("virtual:up" to 5, "virtual:a" to 6)),
            "base-v1.json" to games(game to controller(bindings("virtual:a" to 11)))))
        val codec = ControllerBindingsCodec({ true }, emptyList(), emptyList())
        val system = codec.parse(layers.systemControllerBindings().toString())
        val gameLayer = codec.parse(defaults(layers, game)!!)
        val player = listOf(ControllerBinding("virtual:a", listOf(99)), ControllerBinding("virtual:up"))
        assertEquals(listOf(ControllerBinding("virtual:a", listOf(99))),
            ControllerDefaults.cascade(system, gameLayer, player))
        assertEquals("without the player's layer", listOf(ControllerBinding("virtual:up", listOf(5)), ControllerBinding("virtual:a", listOf(11))),
            ControllerDefaults.cascade(system, gameLayer))
    }

    private object Schema : LayeredGameCatalog.Schema {
        override val platform = "test"
        override val catalogUrl = "https://example.invalid/"
        override val selfHostedArt = "/test/"
        override val contentId = Regex("sha256-test-v1:[0-9a-f]+")
        override val mediaExtensions = Regex("\\.dsk$")
        override val fields = emptyMap<String, Set<String>?>()
        override val settings = emptyList<CatalogSetting>()
        override val escapeKeys = emptySet<Int>()
        override val enterKeys = emptySet<Int>()
        override fun validMedia(role: String, contentId: String) = true
        override fun validField(field: String, value: Any) = true
        override fun validBindings(bindings: JSONArray) = true
    }
}
