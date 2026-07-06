# Dependency Update Policy

This SDK follows a conservative dependency policy because MCP SDK users depend
on protocol compatibility across multiple Kotlin targets.

## Goals

- Keep security fixes moving quickly.
- Keep Kotlin, Ktor, kotlinx, and build-tool versions close to supported stable
  releases.
- Avoid dependency churn that changes public API, platform support, or transport
  behavior without tests.

## Update Cadence

- Security updates: review immediately and prioritize based on severity.
- Kotlin, Ktor, kotlinx libraries, Gradle plugins, and Netty: review at least
  monthly.
- Sample-only dependencies: update with the main SDK dependencies when possible,
  but do not block SDK security fixes on sample cleanup.

## Validation

Dependency updates that affect SDK runtime behavior should run:

- `./gradlew :kotlin-sdk-core:jvmTest`
- `./gradlew :kotlin-sdk-client:jvmTest`
- `./gradlew :kotlin-sdk-server:jvmTest`
- `./gradlew apiCheck`

Dependency updates that affect Kotlin, Ktor, kotlinx libraries, serialization,
or multiplatform compilation should also run `./gradlew assemble`, or a narrower
target-specific compile/test set when the affected platform is known.

Transport, serialization, or auth-related updates should also run the relevant
integration or conformance suite.

## Compatibility

- Public API changes require an intentional API dump update and release notes.
- Updates that remove a supported Kotlin target, JVM baseline, or MCP protocol
  version require an explicit compatibility decision.
- Pre-release dependencies are allowed only when needed for MCP spec support or
  toolchain compatibility, and should be tracked until a stable replacement is
  available.
