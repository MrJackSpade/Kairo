package com.mrjackspade.kairo.frontend

import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.InputStream

/**
 * A CD kept as a CHD, read as the disc image it was made from (shared native/chd/kairo_chd.h):
 * a CHD made from a CloneCD .img or a one-file BIN reads back as that file, so it hashes to the
 * same content ID. Needs libkairo_chd.so, which an app that lists CHDs builds (cmake/KairoChd.cmake).
 */
class ChdImage private constructor(private var handle: Long) : Closeable {
    /** The disc image's size in bytes. */
    val size: Long = nativeSize(handle)

    /** The image from its start, read in order. */
    fun inputStream(): InputStream = object : InputStream() {
        private var position = 0L

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (position >= size) return -1
            val read = nativeRead(checkOpen(), position, buffer, offset, length)
            position += read
            return read
        }
    }

    /** The image's cue sheet, naming [binName] as its one BINARY file (kairo_chd_cue). */
    fun cue(binName: String): String = nativeCue(checkOpen(), binName)

    private fun checkOpen(): Long {
        check(handle != 0L) { "The CHD is closed" }
        return handle
    }

    override fun close() {
        if (handle != 0L) nativeClose(handle)
        handle = 0
    }

    companion object {
        init { System.loadLibrary("kairo_chd") }

        /** Opens the CHD read through [document], which stays open and the caller's. */
        fun open(document: ParcelFileDescriptor) = ChdImage(nativeOpenFd(document.fd))

        @JvmStatic private external fun nativeOpenFd(fd: Int): Long
        @JvmStatic private external fun nativeSize(handle: Long): Long
        @JvmStatic private external fun nativeRead(handle: Long, offset: Long, buffer: ByteArray, at: Int, length: Int): Int
        @JvmStatic private external fun nativeCue(handle: Long, binName: String): String
        @JvmStatic private external fun nativeClose(handle: Long)
    }
}
