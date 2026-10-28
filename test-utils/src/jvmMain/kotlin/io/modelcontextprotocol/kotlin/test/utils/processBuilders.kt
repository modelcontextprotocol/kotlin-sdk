package io.modelcontextprotocol.kotlin.test.utils

public fun createTeeProcessBuilder(): ProcessBuilder = if (isWindows) {
    ProcessBuilder("cmd", "/c", "findstr", "^")
} else {
    ProcessBuilder("tee")
}

public fun createSleepyProcessBuilder(): ProcessBuilder = if (isWindows) {
    ProcessBuilder("cmd", "/c", "ping -n 2 127.0.0.1 > nul && echo simulated error 1>&2 && exit 1")
} else {
    ProcessBuilder("sh", "-c", "sleep 1 && echo 'simulated error' >&2 && exit 1")
}
