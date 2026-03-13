---
name: build-native-image-gradle
description: Build GraalVM native images using Gradle Native Build Tools. Use this skill when the user wants to build Java application within the Gradle project using Native Image and Native Build tools, configure native image build.gradle settings, or fix missing reachability metadata.
---

# Gradle Native Image Build

## Prerequisites

- `JAVA_HOME` must point to a standard JDK 17+ installation.
- `GRAALVM_HOME` must point to a GraalVM distribution. If not set, ask the user for their GraalVM path.
- The project must apply `application`, `java-library`, or `java` plugin alongside `org.graalvm.buildtools.native`.

## Plugin setup

```groovy
plugins {
    id 'application'
    id 'org.graalvm.buildtools.native' version '0.11.1'
}
```

Kotlin DSL:

```kotlin
plugins {
    application
    id("org.graalvm.buildtools.native") version "0.11.1"
}
```

## Build and run

```bash
./gradlew nativeCompile  # Builds to build/native/nativeCompile/
./gradlew nativeRun      # Builds and runs the native executable
./gradlew nativeTest     # Builds and runs JUnit tests as native image
```

## Build or runtime failures

If the build fails with class initialization, linking errors, memory issues, or the binary behaves incorrectly at runtime, see [references/native-image-build-gradle-options.md](references/native-image-build-gradle-options.md).

## Native testing

If `nativeTest` fails or you need to configure native JUnit tests or custom test suites, see [references/testing.md](references/testing.md).

## Missing reachability metadata

If a build or runtime error reports missing reflection, resource, serialization, or JNI registrations, see [references/reachability-metadata.md](references/reachability-metadata.md).

## Reference files

| Topic | File |
|-------|------|
| DSL options and build arguments | [references/native-image-build-gradle-options.md](references/native-image-build-gradle-options.md) |
| Missing reachability metadata | [references/reachability-metadata.md](references/reachability-metadata.md) |
| Native testing | [references/testing.md](references/testing.md) |
| Metadata repository | [references/metadata-repository.md](references/metadata-repository.md) |
