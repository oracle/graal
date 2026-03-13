# Missing Reachability Metadata (Gradle)

Use this guide when native-image fails because reflection, resources, serialization, or JNI entries are missing.

## Detect missing metadata

### If using GraalVM JDK 23+

```groovy
graalvmNative {
    binaries.all {
        buildArgs.add('--exact-reachability-metadata')
        runtimeArgs.add('-XX:MissingRegistrationReportingMode=Warn')
    }
}
```

### If using older GraalVM

```groovy
graalvmNative {
    binaries.all {
        buildArgs.add('-H:ThrowMissingRegistrationErrors=')
    }
}
```

## Resolution workflow

### Enable metadata repository

The metadata repository provides pre-built metadata for popular libraries and is enabled by default:

```groovy
graalvmNative {
    metadataRepository {
        enabled = true
    }
}
```

### If the repository doesn't cover it, run the tracing agent

```bash
./gradlew generateMetadata -Pcoordinates=<library-coordinates> -PagentAllowedPackages=<condition-packages>
```

### If agent-collected metadata is still incomplete, add manual config

Create `external-metadata/` with only the files needed, then register it:

```groovy
graalvmNative {
    binaries.all {
        buildArgs.add('-H:ConfigurationFileDirectories=external-metadata/')
    }
}
```

Minimal `reflect-config.json` example:

```json
[
  {
    "condition": {
      "typeReachable": "com.example.Condition"
    },
    "name": "com.example.Type",
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": []
      }
    ]
  }
]
```

## Rebuild and verify

```bash
./gradlew nativeCompile
./gradlew nativeTest
```
