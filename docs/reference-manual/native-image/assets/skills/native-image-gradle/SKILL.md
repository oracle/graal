---
name: native-image-gradle
description: Build GraalVM native images using Gradle Native Build Tools. Use this skill when the user wants to build Java application within the Gradle project using Native Image and Native Build tools, configure native image build.gradle settings, troubleshoot native build tools, use the tracing agent for metadata collection.
---

# Gradle Native Image Build

Build native executables from Java applications using the Gradle Native Build Tools plugin. Native images are ahead-of-time compiled applications that start faster, use less memory, and produce standalone executables.

## Environment setup
- `JAVA_HOME` must point to a standard JDK 17 or 21 installation.
- `GRAALVM_HOME` must point to a GraalVM distribution. If it is not set, ask the user for the path to their GraalVM installation and set `GRAALVM_HOME` accordingly.
- Use a Gradle version compatible with Java 17–21.
- The project must apply one of the following plugins: `application`, `java-library`, or `java`.

## Mandatory build.gradle setup

Add to `build.gradle`:

```groovy
plugins {
    id 'application'
    id 'org.graalvm.buildtools.native' version '0.11.1'
}
```

Or `build.gradle.kts`:

```kotlin
plugins {
    application
    id("org.graalvm.buildtools.native") version "0.11.1"
}
```

## Build and run:

```bash
./gradlew nativeCompile  # Builds to build/native/nativeCompile/
./gradlew nativeRun      # Runs the native executable
./gradlew nativeTest     # Runs the Junit tests with native-image
```

## Additional documentation

If the native image build or run still fails check out:

**Native Build tools gradle options**: [native-image-build-gradle-options.md](references/native-image-build-gradle-options.md)
**JUnit Native Build Tools Testing**: [testing.md](references/testing.md)

If there is Native Image run metadata related issues:
**Metadata Repository**: [metadata-repository.md](references/metadata-repository.md)
**Troubleshooting**: [troubleshooting.md](references/troubleshooting.md)
**Tracing Agent**: [agent.md](references/agent.md)
