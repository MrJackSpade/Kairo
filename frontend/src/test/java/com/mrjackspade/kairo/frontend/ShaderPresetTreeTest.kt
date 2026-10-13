package com.mrjackspade.kairo.frontend

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ShaderPresetTreeTest {
    private fun root(vararg files: String): File {
        val root = Files.createTempDirectory("presets").toFile()
        files.forEach { File(root, it).apply { parentFile!!.mkdirs(); writeText("") } }
        return root
    }

    @Test fun missingRootIsEmpty() {
        val tree = ShaderPresetTree.read(File(root(), "slang-shaders"))
        assertTrue(tree.isEmpty)
    }

    @Test fun foldsThePackWrapperAndPresetlessChains() {
        val root = root(
            "pack/slang-shaders-master/crt/zfast.slangp",
            "pack/slang-shaders-master/crt/crt-easymode.slangp",
            "pack/slang-shaders-master/crt/shaders/crt-easymode.slang",
            "pack/slang-shaders-master/bezel/Mega_Bezel/Presets/Base/a.slangp",
            "pack/slang-shaders-master/bezel/Mega_Bezel/Presets/Base/Root/b.slangp",
            "pack/slang-shaders-master/bezel/Mega_Bezel/Presets/c.slangp")
        val tree = ShaderPresetTree.read(root)
        assertTrue(tree.presets.isEmpty())
        assertEquals(listOf("bezel/Mega_Bezel/Presets", "crt"), tree.folders.map { it.label })
        val crt = tree.folders[1]
        assertEquals(listOf("crt-easymode.slangp", "zfast.slangp"), crt.presets.map { it.name })
        assertTrue(crt.folders.isEmpty())
        val presets = tree.folders[0]
        assertEquals(listOf("c.slangp"), presets.presets.map { it.name })
        assertEquals(listOf("Base"), presets.folders.map { it.label })
        assertEquals("bezel/Mega_Bezel/Presets/Base", presets.folders[0].path)
        assertEquals(listOf("Root"), presets.folders[0].folders.map { it.label })
        assertEquals(5, tree.paths.size)
    }

    @Test fun mergesImportsAndKeepsTopLevelPresets() {
        val root = root("one/a.slangp", "two/B.slangp", "two/crt/c.slangp", "three/readme.txt")
        val tree = ShaderPresetTree.read(root)
        assertEquals(listOf("a.slangp", "B.slangp"), tree.presets.map { it.name })
        assertEquals(listOf("crt"), tree.folders.map { it.label })
        assertTrue(File(root, "two/crt/c.slangp").absolutePath in tree.paths)
    }

    @Test fun choiceFlattensAndFindsAcrossFolders() {
        val inner = ChoiceFolder("crt/inner", "inner") {
            listOf(ChoiceGroup(null, listOf(ChoiceOption("deep", "Deep"))))
        }
        val crt = ChoiceFolder("crt", "crt") {
            listOf(ChoiceGroup(null, listOf(ChoiceOption("easy", "Easy")), folders = listOf(inner)))
        }
        val groups = listOf(ChoiceGroup("BUILT-IN", listOf(ChoiceOption("off", "Off"))),
            ChoiceGroup("IMPORTED", listOf(ChoiceOption("top", "Top")), folders = listOf(crt)))
        val spec = ChoiceSpec("Shader", groups = { groups }, selected = { "deep" }, select = {})
        assertEquals(listOf("off", "deep", "easy", "top"), spec.options.map { it.id })
        assertEquals("Deep", ChoiceSpec.find(groups, "deep")?.label)
        assertEquals("Deep", crt.find("deep")?.label)
        assertNull(inner.find("easy"))
        assertNull(crt.find(null))
    }
}
