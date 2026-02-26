# Gradle Native Image Build Options

## DSL structure

```groovy
graalvmNative {
    binaries {
        main { /* options for the application binary */ }
        test { /* options for the test binary */ }
        all  { /* options shared by main and test */ }
    }
}
```

## Binary properties

| Property | Type | Default | Purpose |
|----------|------|---------|---------|
| `imageName` | String | project name | Name of the output executable |
| `mainClass` | String | `application.mainClass` | Entry point class |
| `debug` | boolean | `false` | Generate debug info (or use `--debug-native` CLI flag) |
| `verbose` | boolean | `false` | Enable verbose build output |
| `fallback` | boolean | `false` | Allow fallback to JVM if native build fails |
| `sharedLibrary` | boolean | `false` | Build a shared library instead of executable |
| `quickBuild` | boolean | `false` | Faster build at cost of runtime performance |
| `richOutput` | boolean | `false` | Rich console output during build |
| `jvmArgs` | ListProperty | empty | JVM arguments for the native-image builder process |
| `buildArgs` | ListProperty | empty | Arguments passed directly to `native-image` |
| `runtimeArgs` | ListProperty | empty | Arguments passed to the application at runtime |
| `javaLauncher` | Property | auto-detected | GraalVM toolchain launcher |

## Binary configuration

**If you want to rename the output binary:**
```groovy
imageName = 'myapp'
```

**If you need to set the entry point explicitly:**
```groovy
mainClass = 'com.example.Main'
```

**If you want debug info in the binary:**
```groovy
debug = true
// or pass --debug-native on the CLI
```

**If you need verbose build output:**
```groovy
verbose = true
```

**If you want faster builds during development:**
```groovy
quickBuild = true
// or use -Ob buildArg for maximum speed
buildArgs.add('-Ob')
```

**If you need to build a shared library instead of an executable:**
```groovy
sharedLibrary = true
```

## Build failures and errors

**If the build runs out of memory:**
```groovy
jvmArgs.add('-Xmx8g')
```

**If a class fails because it initializes at build time but must not:**
```groovy
buildArgs.add('--initialize-at-run-time=com.example.LazyClass')
```

**If a class must be initialized at build time:**
```groovy
buildArgs.add('--initialize-at-build-time=com.example.EagerClass')
```

**If you need to inspect build diagnostics:**
```groovy
buildArgs.add('--diagnostics-mode')
```

## Resources and metadata

**If resource files are missing at runtime:**
```groovy
buildArgs.add('-H:IncludeResources=.*\\.(properties|xml)$')
```

**If you need to point to external metadata config files:**
```groovy
buildArgs.add('-H:ConfigurationFileDirectories=external-metadata/')
```

## Runtime arguments

**If you need to pass arguments to the application at startup:**
```groovy
runtimeArgs.add('--server.port=8080')
```

## Full example

### Groovy DSL

```groovy
graalvmNative {
    binaries {
        main {
            imageName = 'myapp'
            mainClass = 'com.example.Main'
            verbose = true
            buildArgs.addAll(
                '--initialize-at-run-time=com.example.Lazy',
                '-H:IncludeResources=.*\\.properties$',
                '-O3'
            )
            jvmArgs.add('-Xmx8g')
        }
        test {
            imageName = 'myapp-tests'
        }
        all {
            javaLauncher = javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }
    }
}
```

### Kotlin DSL

```kotlin
graalvmNative {
    binaries {
        named("main") {
            imageName.set("myapp")
            mainClass.set("com.example.Main")
            verbose.set(true)
            buildArgs.addAll(
                "--initialize-at-run-time=com.example.Lazy",
                "-H:IncludeResources=.*\\.properties$",
                "-O3"
            )
            jvmArgs.add("-Xmx8g")
        }
        named("test") {
            imageName.set("myapp-tests")
        }
    }
}
```
