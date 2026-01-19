package io.modelcontextprotocol.kotlin.test.utils

public val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("windows")

public val NPX: String = if (isWindows) "npx.cmd" else "npx"
