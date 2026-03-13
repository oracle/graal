# Native Image Testing (Maven)

## Contents
- Required dependencies
- Plugin configuration for testing
- Running native tests
- Collecting test metadata
- Skip options
- Troubleshooting test failures

## Required dependencies

```xml
<dependencies>
  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.0</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.junit.platform</groupId>
    <artifactId>junit-platform-launcher</artifactId>
    <version>1.10.0</version>
    <scope>test</scope>
  </dependency>
</dependencies>
```

**`junit-platform-launcher` is required** — without it, native test discovery fails.

Also ensure `maven-surefire-plugin` 3.0+ is configured:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <version>3.2.5</version>
</plugin>
```

## Plugin configuration for testing

Add the `test` goal execution to the native-maven-plugin:

```xml
<execution>
  <id>test-native</id>
  <goals>
    <goal>test</goal>
  </goals>
  <phase>test</phase>
</execution>
```

## Running native tests

```bash
./mvnw -Pnative test
```

This compiles a native test binary and executes all discovered JUnit tests.

## Collecting test metadata

If `nativeTest` fails due to missing reflection/resource metadata, collect it with the tracing agent:

```bash
./mvnw -Pnative -Dagent=true test
./mvnw -Pnative native:metadata-copy
./mvnw -Pnative test
```

Configure the metadata copy to use the test output:

```xml
<agent>
  <enabled>true</enabled>
  <metadataCopy>
    <outputDirectory>src/test/resources/META-INF/native-image</outputDirectory>
    <merge>true</merge>
    <disabledStages>
      <stage>main</stage>
    </disabledStages>
  </metadataCopy>
</agent>
```

## Skip options

| Flag | Effect |
|------|--------|
| `-DskipTests` | Skip all tests (JVM and native) |
| `-DskipNativeTests` | Run JVM tests only, skip native test compilation and execution |

## Troubleshooting test failures

**"No tests found" in native test**
Ensure `junit-platform-launcher` is in test dependencies and `maven-surefire-plugin` 3.0+ is declared.

**Tests pass on JVM but fail as native image**
Test framework or dependencies use reflection not captured by metadata. Run `./mvnw -Pnative -Dagent=true test`, then `native:metadata-copy`, then retry.
