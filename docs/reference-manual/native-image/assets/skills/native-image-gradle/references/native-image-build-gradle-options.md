## build.gradle Options
`NativeImageOptions` lets you fine-tune how the native image is built. The plugin allows configuring the main (application) binary, the test binary, and options common to both.
```
graalvmNative {
    binaries.main {
        // options for the main binary
    }
    binaries.test {
        // options for the test binary
    }
    binaries.all {
        // common options for both main and test binaries
    }
}
```
**Native Image options**
- imageName - The name of the native executable, defaults to the project name
- mainClass - The main class to use, defaults to the application.mainClass
- debug - Determines if debug info should be generated; defaults to false (alternatively, add --debug-native to the CLI)
- verbose - Adds verbose output (false by default)
- fallback - Sets the fallback mode of native-image (false by default)
- sharedLibrary - Determines if the image is a shared library
- quickBuild - Determines if the image is being built in quick build mode
- richOutput - Determines whether native-image building should produce a rich output
- jvmArgs: Passes the specified arguments directly to the JVM running the native-image builder


## Build-time and run-time arguments
You can also pass build-time and run-time arguments:

- `buildArgs.add('<buildArg>')`: Configures the build by passing options directly to native-image. You can pass any Native Image build option listed here. 
- `runtimeArgs.add('<runtimeArg>')`: Specifies runtime arguments consumed by your application.

Use these build-time arguments when a `native-image` build fails with errors such as: "You should initialize class at build/run time":
- `--initialize-at-build-time`: A comma-separated list of packages and classes (and implicitly all of their superclasses) that are initialized during generation of a native executable. An empty string designates all packages.
- `--initialize-at-run-time`: A comma-separated list of packages and classes (and implicitly all of their subclasses) that must be initialized at run time and not during generation. An empty string is currently not supported.

## Example build.gradle

Example configurations in `build.gradle`:

```groovy
graalvmNative {
    binaries {
        main {
            imageName = 'myapp'
            mainClass = 'com.example.Main'
            verbose = true
            debug = true
            
            // Build arguments (passed to native-image)
            buildArgs.add('--link-at-build-time')
            buildArgs.add('-O3')  // Max optimization
            
            // JVM args for the native-image builder
            jvmArgs.add('-Xmx8g')
            
            // Runtime args for your application
            runtimeArgs.add('--help')
        }
    }
}
```

Kotlin DSL:

```kotlin
graalvmNative {
    binaries {
        named("main") {
            imageName.set("myapp")
            mainClass.set("com.example.Main")
            verbose.set(true)
            debug.set(true)
            buildArgs.add("-O3")
        }
    }
}
```