package io.github.mehrdad_abdi.qintar.diagnostics

import java.io.File
import java.io.OutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.util.concurrent.Executor
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the Diagnostics screen shows. */
data class DiagnosticsState(
    val enabled: Boolean = false,
    val screenshotsAllowed: Boolean = false,
    val events: Int = 0,
    val crashes: Int = 0,
    val screenshots: Int = 0,
) {
    val isEmpty: Boolean get() = events == 0 && crashes == 0 && screenshots == 0
}

/**
 * The canary (owner, 2026-11-22): an opt-in, offline record that helps reproduce a bug. While it is on
 * it keeps the last [MAX_EVENTS] events (screens, setting changes, permission answers, playback,
 * reminders), every crash with its stack trace, and — only if allowed too — screenshots taken when
 * the user marks a problem. Nothing leaves the phone: the user saves a report and attaches it to a
 * GitHub issue. Its two switches live in [dir] as files, so the crash handler knows them at once,
 * before anything else is loaded, and they are not part of backups.
 *
 * Events hold actions and ids only, never Quran text, bookmark notes or names.
 */
class Diagnostics(
    private val dir: File,
    private val now: () -> Instant,
    /** App version, Android version, device, language: the head of every report and crash. */
    private val device: () -> String,
    /** Where file writes happen; one background thread in the app, the caller's thread in tests. */
    private val executor: Executor,
) {
    private val events = File(dir, EVENTS)
    private val enabledFlag = File(dir, ENABLED)
    private val screenshotsFlag = File(dir, SCREENSHOTS)
    private val lock = Any()

    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    init {
        refresh()
    }

    val enabled: Boolean get() = _state.value.enabled

    fun setEnabled(on: Boolean) {
        synchronized(lock) {
            dir.mkdirs()
            if (on) enabledFlag.writeText("1") else enabledFlag.delete()
        }
        refresh()
        // After refresh(): log() writes only once the switch is seen as on.
        if (on) log("diagnostics", "recording started")
    }

    fun setScreenshotsAllowed(on: Boolean) {
        synchronized(lock) {
            dir.mkdirs()
            if (on) screenshotsFlag.writeText("1") else screenshotsFlag.delete()
        }
        refresh()
    }

    /** One line in the event log, if recording. [message] must not hold personal text. */
    fun log(tag: String, message: String) {
        if (!enabled) return
        val line = "${now()} [$tag] ${message.replace('\n', ' ')}"
        executor.execute {
            synchronized(lock) {
                dir.mkdirs()
                events.appendText(line + "\n")
                val lines = events.readLines()
                if (lines.size > MAX_EVENTS + TRIM_SLACK) events.writeText(lines.takeLast(MAX_EVENTS).joinToString("\n", postfix = "\n"))
            }
            refresh()
        }
    }

    /** Written at once, on the crashing thread: the process is about to end. */
    fun recordCrash(thread: Thread, error: Throwable) {
        if (!enabled) return
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
        val time = now()
        synchronized(lock) {
            dir.mkdirs()
            File(dir, "$CRASH_PREFIX${time.toEpochMilli()}.txt").writeText(
                "Crash at $time on thread ${thread.name}\n\n${device()}\n\n$trace",
            )
            events.appendText("$time [crash] ${error.javaClass.name}: ${error.message.orEmpty().replace('\n', ' ')}\n")
        }
        refresh()
    }

    /** Records crashes from now on (when recording), then lets the previous handler end the process as usual. */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { recordCrash(thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** A screenshot (PNG) taken when the user marked a problem; only when both switches are on. */
    fun saveScreenshot(png: ByteArray) {
        if (!enabled || !_state.value.screenshotsAllowed) return
        val time = now().toEpochMilli()
        executor.execute {
            synchronized(lock) {
                dir.mkdirs()
                File(dir, "$SCREENSHOT_PREFIX$time.png").writeBytes(png)
                files(SCREENSHOT_PREFIX).sortedBy { it.name }.dropLast(MAX_SCREENSHOTS).forEach { it.delete() }
            }
            refresh()
        }
    }

    /** The report: device details, the event log, crashes and screenshots, as one zip. */
    fun export(out: OutputStream) {
        synchronized(lock) {
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("device.txt"))
                zip.write("Qintar diagnostics, saved ${now()}\n\n${device()}\n".toByteArray())
                zip.closeEntry()
                val included = listOfNotNull(events.takeIf { it.exists() }) + files(CRASH_PREFIX) + files(SCREENSHOT_PREFIX)
                for (file in included.sortedBy { it.name }) {
                    zip.putNextEntry(ZipEntry(file.name))
                    zip.write(file.readBytes())
                    zip.closeEntry()
                }
            }
        }
        log("diagnostics", "report saved")
    }

    /** The name offered when the report is saved, e.g. `qintar-diagnostics-2026-11-22-1405.zip` (UTC). */
    fun reportFileName(): String {
        val t = now().toString() // 2026-11-22T14:05:31Z
        return "qintar-diagnostics-${t.take(10)}-${t.substring(11, 13)}${t.substring(14, 16)}.zip"
    }

    /** Deletes what was recorded; the switches stay as they are. */
    fun clear() {
        synchronized(lock) {
            events.delete()
            (files(CRASH_PREFIX) + files(SCREENSHOT_PREFIX)).forEach { it.delete() }
        }
        refresh()
    }

    private fun files(prefix: String): List<File> = dir.listFiles { f -> f.name.startsWith(prefix) }?.toList().orEmpty()

    private fun refresh() {
        val next = synchronized(lock) {
            DiagnosticsState(
                enabled = enabledFlag.exists(),
                screenshotsAllowed = screenshotsFlag.exists(),
                events = if (events.exists()) events.readLines().size else 0,
                crashes = files(CRASH_PREFIX).size,
                screenshots = files(SCREENSHOT_PREFIX).size,
            )
        }
        _state.value = next
    }

    companion object {
        const val MAX_EVENTS = 500
        const val MAX_SCREENSHOTS = 10
        private const val TRIM_SLACK = 100
        private const val EVENTS = "events.log"
        private const val ENABLED = "enabled"
        private const val SCREENSHOTS = "screenshots-allowed"
        private const val CRASH_PREFIX = "crash-"
        private const val SCREENSHOT_PREFIX = "screenshot-"

        /** Where the report is attached. */
        const val NEW_ISSUE_URL = "https://github.com/mehrdad-abdi/Qintar/issues/new?template=bug_report.md"
    }
}
