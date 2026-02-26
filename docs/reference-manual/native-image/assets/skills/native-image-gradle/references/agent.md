## Tracing Agent (Metadata Collection)

The agent captures reflection, resource, and JNI usage during JVM execution.

Enable in build config:

```groovy
graalvmNative {
    agent {
        enabled = true
        defaultMode = "standard"  // or "conditional" or "direct"
        
        metadataCopy {
            inputTaskNames.add("run")
            outputDirectories.add("src/main/resources/META-INF/native-image")
            mergeWithExisting = true
        }
    }
}
```

Run with agent:

```bash
./gradlew -Pagent run      # Runs app with agent
./gradlew -Pagent test     # Runs tests with agent

# Specify mode
./gradlew -Pagent=standard run
./gradlew -Pagent=conditional test
```

Generated metadata location: `build/native/agent-output/<taskName>/`

Copy metadata to resources:

```bash
./gradlew metadataCopy
```

### Agent Modes

**Conditional mode** - generates metadata with conditions (recommended):
```groovy
modes {
    conditional {
        userCodeFilterPath = "src/main/resources/user-code-filter.json"
    }
}
```

**Direct mode** - pass options directly to agent:
```groovy
modes {
    direct {
        options.add("config-output-dir={output_dir}")
        options.add("experimental-configuration-with-origins")
    }
}
```

### Filter Files

Reduce generated metadata with filter files (`user-code-filter.json`):

```json
{
  "rules": [
    {"includeClasses": "com.myapp.**"},
    {"excludeClasses": "org.junit.**"},
    {"excludeClasses": "org.gradle.**"},
    {"excludeClasses": "java.**"}
  ]
}
```

**Warning:** Filtering too aggressively can break the native image.