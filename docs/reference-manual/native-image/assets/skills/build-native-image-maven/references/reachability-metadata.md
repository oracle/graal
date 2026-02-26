# Missing Reachability Metadata (Maven)

Use this guide when native-image fails because reflection, resources, serialization, or JNI entries are missing.

## Detect missing metadata

### If using GraalVM JDK 23+

```xml
<buildArgs>
  <buildArg>--exact-reachability-metadata</buildArg>
</buildArgs>
```

Then run the native binary with:
```bash
./target/myapp -XX:MissingRegistrationReportingMode=Warn
```

### If using older GraalVM

```xml
<buildArgs>
  <buildArg>-H:ThrowMissingRegistrationErrors=</buildArg>
</buildArgs>
```

## Resolution workflow

### Enable metadata repository

The repository provides pre-built metadata for popular libraries and is enabled by default:

```xml
<configuration>
  <metadataRepository>
    <enabled>true</enabled>
  </metadataRepository>
</configuration>
```

Rebuild:

```bash
./mvnw -Pnative package
```

### If the repository doesn't cover it, run the tracing agent

```bash
./mvnw -Pnative -Dagent=true test
./mvnw -Pnative native:metadata-copy
./mvnw -Pnative package
```


### If agent-collected metadata is still incomplete, add manual config

Create `src/main/resources/META-INF/native-image/` with only the files you need:

- `reflect-config.json`
- `resource-config.json`
- `serialization-config.json`
- `jni-config.json`
- `proxy-config.json`

Or use an external directory:

```xml
<buildArg>-H:ConfigurationFileDirectories=external-metadata/</buildArg>
```

Minimal `reflect-config.json` example:

```json
[
  {
    "name": "com.example.MyClass",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true,
    "allDeclaredFields": true
  }
]
```

## Rebuild and verify

```bash
./mvnw -Pnative package
./mvnw -Pnative test
```

If a library still fails after repository + agent + manual entries, capture the exact missing symbol from the error output and add only that entry.
