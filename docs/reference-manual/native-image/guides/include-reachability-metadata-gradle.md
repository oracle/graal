---
layout: ni-docs
toc_group: how-to-guides
link_title: Include Reachability Metadata Using the Native Image Gradle Plugin
permalink: /reference-manual/native-image/guides/use-reachability-metadata-repository-gradle/
---

# Include Reachability Metadata Using the Native Image Gradle Plugin

You can build a native executable from a Java application with **Gradle**. 
For that, use the GraalVM Native Image Gradle plugin provided as part of the [Native Build Tools project](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html).

A "real-world" Java application likely requires some Java reflection objects, or it calls some native code, or accesses resources on the class path - dynamic features that the `native-image` tool must be aware of at build time, and provided in the form of [metadata](../ReachabilityMetadata.md). 
(Native Image loads classes dynamically at build time, and not at run time.)

Depending on your application dependencies, there are three ways to provide the metadata with the Native Image Gradle Plugin:

1. [Using the GraalVM Reachability Metadata Repository](#build-a-native-executable-using-the-graalvm-reachability-metadata-repository)
2. [Using the Tracing Agent](#build-a-native-executable-with-the-tracing-agent)
3. [Autodetecting](https://graalvm.github.io/native-build-tools/latest/gradle-plugin-quickstart.html#build-a-native-executable-with-resources-autodetection) (if the required resources are directly available on the classpath, in the _src/main/resources_ directory)

This guide demonstrates how to build a native executable using the [GraalVM Reachability Metadata Repository](https://github.com/oracle/graalvm-reachability-metadata), and with the [Tracing agent](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#agent-support).
The goal of this guide is to illustrate the difference between the two approaches, and demonstrate how the use of reachability metadata can simplify your development tasks.

We recommend that you follow the instructions and create the application step-by-step. 
Alternatively, you can go right to the [completed example](https://github.com/graalvm/native-build-tools/tree/master/samples/metadata-repo-integration).

## Prepare a Demo Application

> Note: A Java version between 17 and 20 is required to execute Gradle (see the [Gradle Compatibility Matrix](https://docs.gradle.org/current/userguide/compatibility.html)). However, if you want to run your application with Java 21 (or higher), there is a workaround: set `JAVA_HOME` to a Java version between 17 and 20, and `GRAALVM_HOME` to GraalVM for JDK 21. See the [Native Image Gradle Plugin documentation](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#_installing_graalvm_native_image_tool) for more details.

### Prerequisite 
Make sure you have installed a GraalVM JDK.
The easiest way to get started is with [SDKMAN!](https://sdkman.io/jdks#graal).
For other installation options, visit the [Downloads section](https://www.graalvm.org/downloads/).

1. Create a new Java project with **Gradle** in your favorite IDE, called "H2Example", in the `org.graalvm.example` package.

2. Rename the default _app/_ directory to _H2Example/_, then rename the default filename _App.java_ to _H2Example.java_ and replace its contents with the following:
    ```java
    package org.graalvm.example;

    import java.sql.Connection;
    import java.sql.DriverManager;
    import java.sql.PreparedStatement;
    import java.sql.ResultSet;
    import java.sql.SQLException;
    import java.util.ArrayList;
    import java.util.Comparator;
    import java.util.HashSet;
    import java.util.List;
    import java.util.Set;

    public class H2Example {

        public static final String JDBC_CONNECTION_URL = "jdbc:h2:./data/test";

        public static void main(String[] args) throws Exception {
            // Cleanup
            withConnection(JDBC_CONNECTION_URL, connection -> {
                connection.prepareStatement("DROP TABLE IF EXISTS customers").execute();
                connection.commit();
            });

            Set<String> customers = Set.of("Lord Archimonde", "Arthur", "Gilbert", "Grug");

            System.out.println("=== Inserting the following customers in the database: ");
            printCustomers(customers);

            // Insert data
            withConnection(JDBC_CONNECTION_URL, connection -> {
                connection.prepareStatement("CREATE TABLE customers(id INTEGER AUTO_INCREMENT, name VARCHAR)").execute();
                PreparedStatement statement = connection.prepareStatement("INSERT INTO customers(name) VALUES (?)");
                for (String customer : customers) {
                    statement.setString(1, customer);
                    statement.executeUpdate();
                }
                connection.commit();
            });

            System.out.println("");
            System.out.println("=== Reading customers from the database.");
            System.out.println("");

            Set<String> savedCustomers = new HashSet<>();
            // Read data
            withConnection(JDBC_CONNECTION_URL, connection -> {
                try (ResultSet resultSet = connection.prepareStatement("SELECT * FROM customers").executeQuery()) {
                    while (resultSet.next()) {
                        savedCustomers.add(resultSet.getObject(2, String.class));
                    }
                }
            });

            System.out.println("=== Customers in the database: ");
            printCustomers(savedCustomers);
        }

        private static void printCustomers(Set<String> customers) {
            List<String> customerList = new ArrayList<>(customers);
            customerList.sort(Comparator.naturalOrder());
            int i = 0;
            for (String customer : customerList) {
                System.out.println((i + 1) + ". " + customer);
                i++;
            }
        }

        private static void withConnection(String url, ConnectionCallback callback) throws SQLException {
            try (Connection connection = DriverManager.getConnection(url)) {
                connection.setAutoCommit(false);
                callback.run(connection);
            }
        }

        private interface ConnectionCallback {
            void run(Connection connection) throws SQLException;
        }
    }
    ```

3. Delete the _H2Example/src/test/java/_ directory (if it exists).

4. Open the Gradle configuration file _build.gradle_, and replace its contents with the following:
    ```
    plugins {
        id 'application'
        // 1. Native Image Gradle plugin
        id 'org.graalvm.buildtools.native' version '0.10.1'
    }

    repositories {
        mavenCentral()
    }
    
    // 2. Application main class
    application {
        mainClass.set('org.graalvm.example.H2Example')
    }

    dependencies {
        // 3. H2 Database dependency
        implementation("com.h2database:h2:2.2.220")
    }

    // 4. Native Image build configuration
    graalvmNative {
        agent {
            defaultMode = "standard"
        }
        binaries {
            main {
                imageName.set('h2example')
                buildArgs.add("-Ob")
            }
        }
    }
    ```

    **1** Enable the [Native Image Gradle plugin](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html).
    The plugin discovers which JAR files it needs to pass to `native-image` and what the executable main class should be.
    
    **2** Specify the application main class explicitly.

    **3** Add a dependency on the [H2 Database](https://www.h2database.com/html/main.html), an open source SQL database for Java. The application interacts with this database through the JDBC driver.
    
    **4** You can pass parameters to the `native-image` tool in the `graalvmNative` plugin configuration. In individual `buildArgs` you can pass parameters exactly the same way as you do from a command line. The `-Ob` option to enable quick build mode (recommended during development only) is used as an example. `imageName.set()` is used to specify the name for the resulting binary. Learn about other configuration options from the [plugin's documentation](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#configuration).

5. The plugin is not yet available on the Gradle Plugin Portal, so declare an additional plugin repository. Open the _settings.gradle_ file and replace the default content with this:
    ```
    pluginManagement {
        repositories {
            mavenCentral()
            gradlePluginPortal()
        }
    }

    rootProject.name = 'H2Example'
    include('H2Example')
    ```
    Note that the `pluginManagement {}` block must appear before any other statements in the file.

6.  (Optional) Build the application. From the root directory of the repository, run the following command:
    ```shell
    ./gradlew run
    ```
    This generates an "executable" JAR file, one that contains all of the application's dependencies and also a correctly configured _MANIFEST_ file.

## Build a Native Executable Using the GraalVM Reachability Metadata Repository

The Native Image Gradle plugin provides support for the [GraalVM Reachability Metadata repository](https://github.com/oracle/graalvm-reachability-metadata). 
This repository provides GraalVM configuration for libraries which do not support GraalVM Native Image by default. 
One of these is the [H2 Database](https://www.h2database.com/html/main.html) this application depends on. 
The support needs to be enabled explicitly.

1. Open the _build.gradle_ file, and enable the GraalVM Reachability Metadata Repository in the `graalvmNative` plugin configuration: 
    ```
    metadataRepository {
        enabled = true
    }
    ```
    The whole configuration block should look like: 
    ```
    graalvmNative {
        agent {
            defaultMode = "standard"
        }
        binaries {
            main {
                imageName.set('h2example')
                buildArgs.add("-Ob")
            }
        }
        metadataRepository {
            enabled = true
        }
    }
    ```
    The plugin automatically downloads the metadata from the repository.

2. Now build a native executable using the metadata:
    ```shell
    ./gradlew nativeRun
    ```
    This generates a native executable for the platform in the _build/native/nativeCompile/_ directory, called `h2example`.
    The command also runs the application from that native executable.

Using the GraalVM Reachability Metadata Repository enhances the usability of Native Image for Java applications depending on 3rd party libraries.

## Build a Native Executable with the Tracing Agent

The second way to provide the medatata configuration for `native-image` is by injecting the [Tracing agent](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#agent-support) (later *the agent*) at compile time.

The agent can run in three modes:
- **Standard**: Collects metadata without conditions. This is recommended if you are building a native executable.
- **Conditional**: Collects metadata with conditions. This is recommended if you are creating conditional metadata for a native shared library intended for further use.
- **Direct**: For advanced users only. This mode allows directly controlling the command line passed to the agent.

You can configure the agent by either passing the options on the command line, or in the _build.gradle_ file.
See below how to collect metadata with the tracing agent, and build a native executable applying the provided configuration.

1. Open the _build.gradle_ file and see the agent mode specified in the `graalvmNative` plugin configuration:
    ```
    graalvmNative {
        agent {
            defaultMode = "standard"
        }
        ...
    }    
    ```
    If you prefer the command-lime option, it is `-Pagent=standard`.

2.  Now run your application with the agent, on the JVM. To enable the agent with the Native Image Gradle plugin, pass the `-Pagent` option to any Gradle tasks that extends `JavaForkOptions` (for example, `test` or `run`):
    ```shell
    ./gradlew -Pagent run
    ```
    The agent captures and records calls to the H2 Database and all the dynamic features encountered during a test run into multiple _*-config.json_ files.

3. Once the metadata is collected, copy it into the project's _/META-INF/native-image/_ directory using the `metadataCopy` task:
    ```shell
    ./gradlew metadataCopy --task run --dir src/main/resources/META-INF/native-image
    ```

    It is not required but recommended that the output directory is _/resources/META-INF/native-image/_. The `native-image` tool picks up metadata from that location automatically. For more information about how to collect metadata for your application automatically, see [Collecting Metadata Automatically](../AutomaticMetadataCollection.md).

4. Build a native executable using configuration collected by the agent:
    ```shell
    ./gradlew nativeCompile
    ```
    The native executable, named _h2example_, is created in the _build/native/nativeCompile_ directory.

5. Run the application from the native executable:
    ```shell
    ./build/native/nativeCompile/h2example
    ```
    
6. (Optional) To clean up the project, run `./gradlew clean`, and delete the directory _META-INF_ with its contents.

### Summary

This guide demonstrated how to build a native executable using the [GraalVM Reachability Metadata Repository](https://github.com/oracle/graalvm-reachability-metadata) and with the Tracing agent. The goal was to show the difference, and prove how using the reachability metadata can simplify the work.

Note that if your application does not call any dynamic features at run time, enabling the GraalVM Reachability Metadata Repository is needless. 
Your workflow in that case would just be:
```shell
./gradlew nativeRun
```

### Related Documentation

- [Reachability Metadata](../ReachabilityMetadata.md)
- [Native Image Build Tools](https://graalvm.github.io/native-build-tools/latest/index.html)
- [Collect Metadata with the Tracing Agent](../AutomaticMetadataCollection.md#tracing-agent)