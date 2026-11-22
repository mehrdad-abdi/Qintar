package io.github.mehrdad_abdi.qintar.diagnostics

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The canary's record: off by default, offline, bounded, and saved as one report. */
class DiagnosticsTest {
    private val dir: File = Files.createTempDirectory("diagnostics").toFile()
    private var time = Instant.parse("2026-11-22T10:00:00Z")
    private fun open() = Diagnostics(dir, { time }, { "Qintar 2.1.0 (201) · Android 15 · Samsung SM-A346B" }, { it.run() })

    private fun Diagnostics.entries(): Map<String, String> {
        val out = ByteArrayOutputStream()
        export(out)
        val zip = ZipInputStream(out.toByteArray().inputStream())
        return generateSequence { zip.nextEntry }.associate { it.name to zip.readBytes().decodeToString() }
    }

    @Test
    fun `nothing is recorded while it is off, which is the default`() {
        val d = open()
        assertFalse(d.state.value.enabled)
        d.log("screen", "Today")
        d.recordCrash(Thread.currentThread(), IllegalStateException("boom"))
        assertTrue(d.state.value.isEmpty)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `while on, events are kept with their time, and the switch survives a restart`() {
        val d = open()
        d.setEnabled(true)
        d.log("screen", "Bookmarks")
        val lines = File(dir, "events.log").readLines()
        assertTrue(lines.last(), lines.last().startsWith("2026-11-22T10:00:00Z [screen] Bookmarks"))
        assertTrue("a new process knows it is on at once", open().state.value.enabled)
        assertTrue("turning it on is itself recorded", lines.first().endsWith("[diagnostics] recording started"))
        assertEquals("qintar-diagnostics-2026-11-22-1000.zip", d.reportFileName())
    }

    @Test
    fun `only the last events are kept`() {
        val d = open()
        d.setEnabled(true)
        repeat(Diagnostics.MAX_EVENTS + 150) { d.log("n", "$it") }
        val lines = File(dir, "events.log").readLines()
        assertTrue(lines.size <= Diagnostics.MAX_EVENTS + 100)
        assertTrue("the newest is kept", lines.last().endsWith("[n] ${Diagnostics.MAX_EVENTS + 149}"))
    }

    @Test
    fun `a crash is written with the device and the stack trace, and the previous handler still runs`() {
        val d = open()
        d.setEnabled(true)
        var previousRan = false
        val before = Thread.getDefaultUncaughtExceptionHandler()
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> previousRan = true }
            d.installCrashHandler()
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), IllegalStateException("Field value_ not found"))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }
        assertTrue(previousRan)
        assertEquals(1, d.state.value.crashes)
        val crash = dir.listFiles { f -> f.name.startsWith("crash-") }!!.single().readText()
        assertTrue(crash.contains("SM-A346B"))
        assertTrue(crash.contains("IllegalStateException: Field value_ not found"))
        assertTrue(crash.contains("DiagnosticsTest"))
    }

    @Test
    fun `screenshots need their own permission and only the last ones are kept`() {
        val d = open()
        d.setEnabled(true)
        d.saveScreenshot(byteArrayOf(1))
        assertEquals("not allowed yet", 0, d.state.value.screenshots)

        d.setScreenshotsAllowed(true)
        repeat(Diagnostics.MAX_SCREENSHOTS + 3) {
            time = time.plusSeconds(1)
            d.saveScreenshot(byteArrayOf(it.toByte()))
        }
        assertEquals(Diagnostics.MAX_SCREENSHOTS, d.state.value.screenshots)
    }

    @Test
    fun `the report holds the device, the events, the crashes and the screenshots`() {
        val d = open()
        d.setEnabled(true)
        d.setScreenshotsAllowed(true)
        d.log("permission", "notifications reported=false allowed=true")
        d.recordCrash(Thread.currentThread(), RuntimeException("x"))
        d.saveScreenshot(byteArrayOf(9))

        val entries = d.entries()
        assertTrue(entries.getValue("device.txt").contains("Android 15"))
        assertTrue(entries.getValue("events.log").contains("[permission] notifications reported=false allowed=true"))
        assertEquals(1, entries.keys.count { it.startsWith("crash-") })
        assertEquals(1, entries.keys.count { it.startsWith("screenshot-") })
    }

    @Test
    fun `clear deletes what was recorded but keeps the switches`() {
        val d = open()
        d.setEnabled(true)
        d.log("a", "b")
        d.recordCrash(Thread.currentThread(), RuntimeException())
        d.clear()
        assertTrue(d.state.value.isEmpty)
        assertTrue(d.state.value.enabled)
        assertEquals(setOf("device.txt"), d.entries().keys)
    }
}
