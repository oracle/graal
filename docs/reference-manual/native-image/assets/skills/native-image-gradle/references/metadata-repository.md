## Metadata Repository

Plugin automatically downloads metadata for libraries from the GraalVM Reachability Metadata Repository. The plugin adds support for the GraalVM Reachability Metadata Repository. This repository provides the configuration for libraries that do not support GraalVM Native Image.

Configure:

```groovy
graalvmNative {
    metadataRepository {
        enabled = true  // Default
        version = "0.11.1"
    }
}
```

Use local repository:

```groovy
graalvmNative {
    metadataRepository {
        uri(file("path/to/metadata-repo"))
    }
}
```

Exclude libraries:

```groovy
graalvmNative {
    metadataRepository {
        excludes.add("com.company:library")
    }
}
```

Override metadata version:

```groovy
graalvmNative {
    metadataRepository {
        moduleToConfigVersion.put("com.company:library", "3")
    }
}
```

Include metadata in JAR:

```groovy
tasks.named("jar") {
    from tasks.named("collectReachabilityMetadata")
}
```