## Testing

JUnit dependency must be present:

```groovy
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.8.1'
    testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.8.1'
}
```

Additional test suites:

```groovy
graalvmNative {
    registerTestBinary("integTest") {
        usingSourceSet(sourceSets.integTest)
        forTestTask(tasks.named('integTest'))
    }
}
```

This creates `nativeIntegTestCompile` and `nativeIntegTest` tasks.
