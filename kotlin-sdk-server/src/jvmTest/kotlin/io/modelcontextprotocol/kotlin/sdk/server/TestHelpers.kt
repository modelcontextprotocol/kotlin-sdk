package io.modelcontextprotocol.kotlin.sdk.server

import java.io.ByteArrayOutputStream
import java.io.PrintStream

internal fun captureStderr(block: () -> Unit): String {
    val errStream = ByteArrayOutputStream()
    val originalErr = System.err
    System.setErr(PrintStream(errStream))
    try {
        block()
    } finally {
        System.setErr(originalErr)
    }
    return errStream.toString()
}
