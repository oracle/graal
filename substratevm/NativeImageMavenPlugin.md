# Native Image Maven Plugin

To simplify the generation of native images, Native Image now works out
of [Maven](https://maven.apache.org/what-is-maven.html) with the [Native Image Maven Plugin](https://search.maven.org/artifact/org.graalvm.nativeimage/native-image-maven-plugin).

One can build a native image directly with Maven
using the `mvn package` command without running the `native-image` command as a
separate step. It is sufficient to add `native-image-maven-plugin` into the
`<plugins>` section of the `pom.xml` file:

```xml
<plugin>
    <groupId>org.graalvm.nativeimage</groupId>
    <artifactId>native-image-maven-plugin</artifactId>
    <version>${graalvm.version}</version>
    <executions>
        <execution>
            <goals>
                <goal>native-image</goal>
            </goals>
            <phase>package</phase>
        </execution>
    </executions>
    <configuration>
        <skip>false</skip>
        <imageName>example</imageName>
        <buildArgs>
            --no-fallback
        </buildArgs>
    </configuration>
</plugin>
```

Then add the `org.graalvm.sdk` library dependency in the `<dependencies>` list:

```xml
<dependency>
    <groupId>org.graalvm.sdk</groupId>
    <artifactId>graal-sdk</artifactId>
    <version>${graalvm.version}</version>
    <scope>provided</scope>
</dependency>
```

The plugin figures out which JAR files it needs to pass to the native image and
what the executable main class should be. If the heuristics fails with the `no main manifest attribute, in target/<name>.jar` error, the main class should be
specified in the `<configuration>` node of the plugin. When `mvn package` completes, an executable is ready for use, generated in the _target_ directory of the project.

## Maven Plugin Customization

If you use Native Image Maven plugin, it will pick up all the configuration for your application stored below the  _META-INF/native-image/_ resource location, as described in [Native Image Build Configuration](BuildConfiguration.md).
It is also possible to customize the plugin within a
`<configuration>` node. The following configurations are available.

1. Configuration parameter `<mainClass>`: if the execution fails with the `no main manifest attribute, in target/<name>.jar` error, the main class should be specified. By default the plugin consults several locations in the  `pom.xml` file in the following order to determine what the main class of the image should be:
* `<maven-shade-plugin> <transformers> <transformer> <mainClass>`
* `<maven-assembly-plugin> <archive> <manifest> <mainClass>`
* `<maven-jar-plugin> <archive> <manifest> <mainClass>`
2. Configuration parameter `<imageName>`: if an image filename is not set explicitly, use parameter `<imageName>` to provide a custom filename for the image.
3. Configuration parameter `<buildArgs>`: if you want to pass additional options for to the native image builder, use the `<buildArgs>` parameter in the definition of the plugin. For example, to build a native image with assertions enabled that uses _com.test.classname_ as a main class, add:

```xml
<configuration>
    <imageName>executable-name</imageName>
    <mainClass>com.test.classname</mainClass>
    <buildArgs>
        --no-fallback
    </buildArgs>
    <skip>false</skip>
</configuration>
```

If you use GraalVM Enterprise as the `JAVA_HOME` environment, the plugin builds a native image with Enterprise features enabled, e.g., an executable will automatically be built with [compressed references](https://medium.com/graalvm/isolates-and-compressed-references-more-flexible-and-efficient-memory-management-for-graalvm-a044cc50b67e) and other optimizations enabled.

### Reusing configuration from a parent POM

The `<buildArgs>` element can be combined between parent and children POM. Suppose the following parent POM definition:

```xml
<plugin>
    <groupId>org.graalvm.nativeimage</groupId>
    <artifactId>native-image-maven-plugin</artifactId>
    <version>${graalvm.version}</version>
    <configuration>
        <imageName>${project.artifactId}</imageName>
        <mainClass>${exec.mainClass}</mainClass>
        <buildArgs>
            <buildArg>--no-fallback<buildArg>
        </buildArgs>
    </configuration>
</plugin>
```

Children projects have the ability to append `<buildArg>`s in the following way:

```xml
<plugin>
    <groupId>org.graalvm.nativeimage</groupId>
    <artifactId>native-image-maven-plugin</artifactId>
    <configuration>
        <buildArgs combine.children="append">
            <buildArg>--verbose</buildArg>
        </buildArgs>
    </configuration>
</plugin>
```

In this case, the arguments that will be passed to the `native-image` executable will be:

```
--no-fallback --verbose
```