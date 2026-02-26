---
name: build-native-image-maven
description: Build GraalVM native images using the native-maven-plugin (org.graalvm.buildtools). Use when the user wants to build a Java application within a Maven project using Native Image, configure pom.xml native image settings, run native tests, use the tracing agent for metadata collection, or troubleshoot Maven native image build and runtime failures.
---

# Maven Native Image Build

## Prerequisites

- `JAVA_HOME` must point to a JDK 17+ installation.
- `GRAALVM_HOME` must point to a GraalVM distribution. If not set, ask the user for their GraalVM path.
- Maven 3.6+ required.

## Plugin setup

Add to `pom.xml` inside a `native` profile:

```xml
<profiles>
  <profile>
    <id>native</id>
    <build>
      <plugins>
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
            <execution>
              <id>test-native</id>
              <goals>
                <goal>test</goal>
              </goals>
              <phase>test</phase>
            </execution>
          </executions>
          <configuration>
            <mainClass>org.example.Main</mainClass>
          </configuration>
        </plugin>
      </plugins>
    </build>
  </profile>
</profiles>
```

## Build and run

```bash
./mvnw -Pnative package                   # Build native image → target/<imageName>
./target/myapp                             # Run the native executable
./mvnw -Pnative test                      # Build and run JUnit tests as native image
./mvnw -Pnative -DskipTests package       # Skip all tests
./mvnw -Pnative -DskipNativeTests package # Run JVM tests only, skip native
```

## Plugin not resolving or activating

- **"Could not resolve artifact"** — ensure `mavenCentral()` is in repositories and the version is correct.
- **"Could not find goal 'compile-no-fork'"** — verify `<extensions>true</extensions>` is set on the plugin.
- **Build runs without native compilation** — check you're activating the profile: `./mvnw -Pnative package`.

## Build or runtime failures

For class initialization errors, linking issues, memory problems, or unexpected runtime behavior, see [references/maven-plugin-options.md](references/maven-plugin-options.md).

## Missing reachability metadata

When native-image reports missing reflection, resources, serialization, or JNI entries, see [references/reachability-metadata.md](references/reachability-metadata.md).

## Native testing

For `nativeTest` failures or setting up native JUnit tests, see [references/testing.md](references/testing.md).

## Reference files

| Topic | File |
|-------|------|
| Plugin configuration options | [references/maven-plugin-options.md](references/maven-plugin-options.md) |
| Missing reachability metadata | [references/reachability-metadata.md](references/reachability-metadata.md) |
| Native testing | [references/testing.md](references/testing.md) |
