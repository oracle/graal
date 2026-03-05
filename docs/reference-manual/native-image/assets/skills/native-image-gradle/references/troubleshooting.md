## Troubleshooting

### Detect Missing Metadata

Add to config:

```groovy
graalvmNative {
    binaries.all {
        buildArgs.add('--exact-reachability-metadata')
        runtimeArgs.add('-XX:MissingRegistrationReportingMode=Warn')
    }
}
```

**Note:** `--exact-reachability-metadata` requires GraalVM JDK 23+. For older versions use `-H:ThrowMissingRegistrationErrors=`.

Rebuild and check console for warnings. Then either:
1. Add metadata manually to configuration files
2. Use tracing agent to collect automatically

If you want to add additional metadata entries, create a directory within the project named `external-metadata/`. In this directory you can place the following configuration files:
- `reflect-config.json`: Reflection-specific metadata.
- `resource-config.json`: Resource-specific metadata.
- `serialization-config.json`: Serialization-specific metadata.
- `jni-config.json`: JNI-specific metadata.

When ready, pass the following build-time flag via `buildArgs`:

`-H:ConfigurationFileDirectories=<path-to-external-metadata-dir>`