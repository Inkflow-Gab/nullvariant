package com.nullvariant.official

import java.util.concurrent.TimeUnit

class CommandRunner {

    fun hasRootAccess(): Boolean {
        for (probe in ROOT_PROBES) {
            var process: Process? = null
            try {
                val started = ProcessBuilder(*probe)
                    .redirectErrorStream(true)
                    .start()
                process = started
                if (!started.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    started.destroy()
                    continue
                }
                val output = started.inputStream.bufferedReader().use { it.readText() }
                if (started.exitValue() == 0 && output.contains("uid=0")) return true
            } catch (_: Exception) {
                // try the next su variant
            } finally {
                runCatching { process?.destroy() }
            }
        }
        return false
    }

    fun run(command: String, useRoot: Boolean): Boolean {
        var process: Process? = null
        return try {
            val started = ProcessBuilder(if (useRoot) "su" else "sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            process = started

            val drain = Thread {
                runCatching {
                    started.inputStream.bufferedReader().use { it.readText() }
                }
            }
            drain.isDaemon = true
            drain.start()

            if (!started.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                started.destroy()
                false
            } else {
                started.exitValue() == 0
            }
        } catch (_: Exception) {
            false
        } finally {
            runCatching { process?.destroy() }
        }
    }

    private companion object {
        const val ROOT_TIMEOUT_SECONDS = 8L
        const val COMMAND_TIMEOUT_SECONDS = 60L
        val ROOT_PROBES = listOf(
            arrayOf("su", "-c", "id"),
            arrayOf("su", "0", "id"),
            arrayOf("/system/bin/su", "-c", "id"),
            arrayOf("/system/xbin/su", "-c", "id"),
            arrayOf("/sbin/su", "-c", "id"),
            arrayOf("/debug_ramdisk/su", "-c", "id")
        )
    }
}
