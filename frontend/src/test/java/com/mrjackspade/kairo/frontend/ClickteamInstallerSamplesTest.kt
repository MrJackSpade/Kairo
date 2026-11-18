package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.channels.FileChannel
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Reads every file of real installers (The Collection Chamber's downloads), each to its end, so
 * its stream's own check (zlib's Adler-32, bzip2's CRC) and its size are checked. Runs only when
 * KAIRO_CLICKTEAM_SAMPLES names a folder of them, one installer per folder, optionally narrowed by
 * the KAIRO_CLICKTEAM_FILTER regular expression on the folder name. Results go to
 * build/clickteam-samples.txt.
 */
class ClickteamInstallerSamplesTest {
    @Test
    fun everyFileOfEverySampleInstallerReadsWhole() {
        val root = System.getenv("KAIRO_CLICKTEAM_SAMPLES")
        assumeTrue("KAIRO_CLICKTEAM_SAMPLES is not set", !root.isNullOrEmpty())
        val filter = System.getenv("KAIRO_CLICKTEAM_FILTER")?.takeIf { it.isNotEmpty() }?.let(::Regex)
        val programs = File(root!!).listFiles().orEmpty().filter { it.isDirectory && (filter == null || filter.matches(it.name)) }
            .sortedBy { it.name }.mapNotNull { folder ->
                folder.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".exe", true) }.singleOrNull()
            }
        assertTrue("no installers under $root", programs.isNotEmpty())
        val pool = Executors.newFixedThreadPool(System.getenv("KAIRO_CLICKTEAM_THREADS")?.toInt() ?: 2)
        val results = try {
            programs.map { program -> pool.submit(Callable { check(program) }) }.map { it.get() }
        } finally { pool.shutdown() }
        val report = File("build/clickteam-samples.txt")
        report.writeText(results.joinToString("\n") + "\n")
        val failed = results.filter { it.startsWith("FAIL") }
        assertTrue("${failed.size} of ${results.size} failed:\n" + failed.joinToString("\n"), failed.isEmpty())
    }

    private fun check(program: File): String = try {
        val parts = generateSequence(1) { it + 1 }.map { File(program.parentFile, File(ClickteamInstaller.partName(program.name, it)).name) }
            .takeWhile { it.isFile }.toList()
        val channels = (listOf(program) + parts).map { file ->
            FileChannel.open(file.toPath()).let { ClickteamInstaller.Part(it, it) }
        }
        ClickteamInstaller.open(channels).use { installer ->
            val buffer = ByteArray(1 shl 16)
            var bytes = 0L
            for (member in installer.members) try {
                installer.open(member).use { input ->
                    while (true) { val read = input.read(buffer); if (read < 0) break; bytes += read }
                }
            } catch (failure: Exception) {
                throw java.io.IOException("${member.name} (${member.size} bytes): ${failure.message}", failure)
            }
            "OK   ${program.parentFile.name} ${parts.size + 1} parts ${installer.members.size} files $bytes bytes"
        }
    } catch (failure: Throwable) {
        "FAIL ${program.parentFile.name}: ${failure::class.simpleName}: ${failure.message}" +
            failure.suppressed.joinToString("") { " | ${it.message}: ${it.cause?.message}" }
    }
}
