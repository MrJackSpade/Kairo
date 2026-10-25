package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderStartTest {
    @Test
    fun aGamesFolderOpensBesideItself() {
        assertEquals("FAF7-34B4:AtariST", FolderStart.parentId("FAF7-34B4:AtariST/Games"))
        assertEquals("primary:Roms/PC88", FolderStart.parentId("primary:Roms/PC88/Games"))
    }

    @Test
    fun aFolderAtTheTopOpensAtTheStoragesRoot() {
        assertEquals("FAF7-34B4:", FolderStart.parentId("FAF7-34B4:PC88"))
        assertEquals("FAF7-34B4:", FolderStart.parentId("FAF7-34B4:"))
    }

    @Test
    fun anOpaqueDocumentHasNoKnownParent() {
        assertNull(FolderStart.parentId("acc=1;doc=encoded=AbCd"))
    }
}
