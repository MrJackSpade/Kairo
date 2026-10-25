package com.mrjackspade.kairo.frontend

import android.net.Uri
import android.provider.DocumentsContract

/**
 * Where a folder picker opens. Android's picker starts wherever it was last unless told, which
 * after choosing the games folder is inside it: in a games folder of thousands of folders (Kairo88's
 * PC88) the picker then showed empty or stale listings (Kairo#51).
 */
object FolderStart {
    /** The document a picker opens at to show [tree] itself, such as a folder chosen before. */
    fun at(tree: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    /**
     * The folder that holds [tree], where a library keeps its BIOS folder beside its games
     * (AtariST/Games and AtariST/BIOS); the storage's root for a tree at the top of it. Null for a
     * provider whose document IDs are not paths, whose folders have no known parent.
     */
    fun beside(tree: Uri): Uri? = parentId(DocumentsContract.getTreeDocumentId(tree))
        ?.let { DocumentsContract.buildDocumentUri(tree.authority, it) }

    /** "FAF7-34B4:AtariST/Games" to "FAF7-34B4:AtariST"; a root such as "FAF7-34B4:" stays itself. */
    fun parentId(documentId: String): String? {
        if (':' !in documentId) return null
        val volume = documentId.substringBefore(':')
        val path = documentId.substringAfter(':')
        return if ('/' in path) "$volume:${path.substringBeforeLast('/')}" else "$volume:"
    }
}
