# Maven Plugin Configuration Options

## Contents
- Plugin goals
- Configuration options
- Build arguments
- Common buildArg flags
- Parent POM inheritance
- Shaded JAR support
- Full example

## Plugin goals

| Goal | Purpose |
|------|---------|
| `compile` | Build native image (forks Maven process) |
| `compile-no-fork` | Build native image without forking (recommended) |
| `test` | Compile and run tests as native image |
| `metadata-copy` | Copy agent-generated metadata to source directories |
| `add-reachability-metadata` | Bundle reachability metadata into JAR |
| `write-args-file` | Generate native-image argument files |

## Configuration options

| Option | Type | Default | Purpose |
|--------|------|---------|---------|
| `<imageName>` | String | artifactId | Name of the output executable |
| `<mainClass>` | String | — | Entry point class (required) |
| `<debug>` | boolean | `false` | Generate debug info |
| `<verbose>` | boolean | `false` | Enable verbose build output |
| `<fallback>` | boolean | `false` | Allow fallback to JVM |
| `<sharedLibrary>` | boolean | `false` | Build shared library instead of executable |
| `<quickBuild>` | boolean | `false` | Faster build, lower runtime performance |
| `<useArgFile>` | boolean | `true` | Use argument file for long classpaths |
| `<skipNativeBuild>` | boolean | `false` | Skip native compilation |
| `<skipNativeTests>` | boolean | `false` | Skip native test execution |
| `<buildArgs>` | List | empty | Arguments passed directly to `native-image` |
| `<jvmArgs>` | List | empty | JVM arguments for the native-image builder |
| `<runtimeArgs>` | List | empty | Arguments passed to the app at runtime |
| `<environment>` | Map | empty | Environment variables during build |
| `<systemPropertyVariables>` | Map | empty | System properties during build |
| `<classpath>` | List | auto | Override classpath entries |
| `<classesDirectory>` | String | auto | Override classes directory |

## Build arguments

Pass any `native-image` flag via `<buildArgs>`:

```xml
<buildArgs>
  <buildArg>--initialize-at-run-time=com.example.LazyClass</buildArg>
  <buildArg>-H:IncludeResources=.*\.xml$</buildArg>
  <buildArg>-O2</buildArg>
</buildArgs>
```

### Common buildArg flags

**If a class must not initialize at build time:**
```xml
<buildArg>--initialize-at-run-time=&lt;class-or-package&gt;</buildArg>
```

**If a class must initialize at build time:**
```xml
<buildArg>--initialize-at-build-time=&lt;class-or-package&gt;</buildArg>
```

**If the build runs out of memory:**
```xml
<jvmArgs>
  <arg>-Xmx8g</arg>
</jvmArgs>
```

**If you need to include resource files:**
```xml
<buildArg>-H:IncludeResources=.*\.(properties|xml)$</buildArg>
```

**If you need external metadata config files:**
```xml
<buildArg>-H:ConfigurationFileDirectories=external-metadata/</buildArg>
```

**If you want to inspect build details:**
```xml
<buildArg>--diagnostics-mode</buildArg>
```

**If you want maximum performance (requires PGO, Oracle GraalVM only):**
```xml
<buildArg>-O3</buildArg>
```

**If you want fastest build time (dev iteration):**
```xml
<buildArg>-Ob</buildArg>
```

**If you want a build report (Oracle GraalVM):**
```xml
<buildArg>--emit build-report</buildArg>
```

## Parent POM inheritance

Child projects can append build arguments to a parent POM config using `combine.children`:

```xml
<buildArgs combine.children="append">
  <buildArg>--verbose</buildArg>
</buildArgs>
```

## Shaded JAR support

If using `maven-shade-plugin`, point the native plugin to the shaded JAR:

```xml
<configuration>
  <useArgFile>false</useArgFile>
  <classpath>
    <param>${project.build.directory}/${project.artifactId}-${project.version}-shaded.jar</param>
  </classpath>
</configuration>
```

## Full example

```xml
<plugin>
  <groupId>org.graalvm.buildtools</groupId>
  <artifactId>native-maven-plugin</artifactId>
  <version>0.11.1</version>
  <extensions>true</extensions>
  <executions>
    <execution>
      <id>build-native</id>
      <goals>
        <goal>compile-no-fork</goal>
      </goals>
      <phase>package</phase>
    </execution>
  </executions>
  <configuration>
    <imageName>myapp</imageName>
    <mainClass>com.example.Main</mainClass>
    <verbose>true</verbose>
    <buildArgs>
      <buildArg>--initialize-at-run-time=com.example.Lazy</buildArg>
      <buildArg>-H:IncludeResources=.*\.properties$</buildArg>
      <buildArg>-O2</buildArg>
    </buildArgs>
    <jvmArgs>
      <arg>-Xmx8g</arg>
    </jvmArgs>
  </configuration>
</plugin>
```
