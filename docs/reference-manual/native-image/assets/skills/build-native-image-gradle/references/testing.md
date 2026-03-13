# Native Image Testing

## Contents
- Required dependencies
- Running native tests
- Custom test suites

## Required dependencies

```groovy
dependencies {
    testImplementation platform('org.junit:junit-bom:5.10.0')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
}
```

**`junit-platform-launcher` is required** — without it, `nativeTest` cannot discover tests in native mode.

## Running native tests

```bash
./gradlew nativeTest
```

Output binary: `build/native/nativeTestCompile/<imageName>`


## Custom test suites

Register additional test binaries for integration tests or other test source sets:

```groovy
graalvmNative {
    registerTestBinary("integTest") {
        usingSourceSet(sourceSets.integTest)
        forTestTask(tasks.named('integTest'))
    }
}
```

This creates two tasks:
- `nativeIntegTestCompile` — builds the native test binary
- `nativeIntegTest` — runs it

