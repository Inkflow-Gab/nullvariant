package com.nullvariant.official

import java.util.concurrent.TimeUnit

class CommandRunner {

    fun hasRootAccess(): Boolean {
        var process: Process? = null
        return try {
            val started = ProcessBuilder("su", "-c", "echo root")
                .redirectErrorStream(true)
                .start()
            process = started
            if (!started.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                started.destroy()
                return false
            }
            started.inputStream.bufferedReader().use { it.readLine() }?.contains("root") == true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { process?.destroy() }
        }
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
        const val ROOT_TIMEOUT_SECONDS = 2L
        const val COMMAND_TIMEOUT_SECONDS = 30L
    }
}
