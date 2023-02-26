---
layout: ni-docs
toc_group: how-to-guides
link_title: Use Shared Reachability Metadata with Native Image Gradle Plugin
permalink: /reference-manual/native-image/guides/use-reachability-metadata-repository-gradle/
---

# Use Shared Reachability Metadata with Native Image Gradle Plugin

With the Gradle plugin for GraalVM Native Image you can easily build a native executable from a Java application. The plugin is provided as part of the [Native Build Tools project](https://graalvm.github.io/native-build-tools/latest/index.html) and uses the [Gradle build tool](https://gradle.org/).
If the application does not load dynamically any classes at run time, then your workflow is just one command: `./gradlew nativeRun`. 

In the real-world scenario, your application will, most likely, call either Java Reflection, Dynamic Proxy objects, or call some native code, or access classpath resources - the dynamic features which the `native-image` tool must be aware of at build time, and provided in the form of [metadata](../ReachabilityMetadata.md). 
Native Image loads classes dynamically at build time, and not at run time.

Depending on your application dependencies, there could be three ways to provide the metadata with the Native Image Gradle Plugin:

1. [Using the Tracing Agent](#build-a-native-executable-with-the-agent)
2. [Using the shared GraalVM Reachability Metadata Repository](#build-a-native-executable-using-the-graalvm-reachability-metadata-repository)
3. [Autodetecting](use-native-image-gradle-plugin.md#build-a-native-executable-with-resources-autodetection) (if the required resources are directly available on the classpath, in the `src/main/resources` directory)

For the Java application used in this guide the first two approaches are applicable. 
This guide demonstrates how to build a native executable with the [Tracing agent](#build-a-native-executable-with-the-agent) and using the [GraalVM Reachability Metadata Repository](https://github.com/oracle/graalvm-reachability-metadata).
The goal is to show users the difference, and prove how using shared metadata can simplify the work.

We recommend that you follow the instructions and create the application step-by-step. Alternatively, you can go right to the [completed example](https://github.com/graalvm/native-build-tools/tree/master/samples/metadata-repo-integration).

> You must have [GraalVM installed with Native Image support](../README.md#install-native-image). 

## Prepare a Demo Application

1. Create a new Java project with **Gradle** in your favorite IDE, called "H2Example", in the `org.graalvm.example` package.

2. Rename the default `app` directory to `H2Example`, then rename the default filename `App.java` to `H2Example.java` and replace its contents with the following: 

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

3. Delete the `H2Example/src/test/java` directory.

4. Open the Gradle configuration file _build.gradle_, and update the main class in the `application` section:

    ```xml
    application {
        mainClass.set('org.graalvm.example.H2Example')
    }
    ```
5. Add explicit dependency on [H2 Database](https://www.h2database.com/html/main.html), an open source SQL database for Java. The application interacts with this database through the JDBC driver. Insert the following line in the `dependencies` section of _build.gradle_:

    ```xml
    dependencies {
        implementation("com.h2database:h2:2.1.210")
    }
    ```
    Also, in the dependencies section, remove the dependency on `guava` that will not be used.

    The next steps will be focused what you should do to enable the Native Image Gradle plugin.

6. Register the Native Image Gradle plugin. Add the following to `plugins` section of your project’s _build.gradle_ file:

    ```xml
    plugins {
    // ...
    id 'org.graalvm.buildtools.native' version '0.9.13'
    }
    ```
    The plugin discovers which JAR files it needs to pass to the `native-image` builder and what the executable main class should be.

7. The plugin is not yet available on the Gradle Plugin Portal, so declare an additional plugin repository. Open the _settings.gradle_ file and replace the default content with this:

    ```xml
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

## Build a Native Executable with the Agent

The Native Image Gradle plugin simplifies generation of the required metadata by injecting the [Tracing agent](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#agent-support) (later *the agent*) automatically for you at compile time. 
To enable the agent, just pass the `-Pagent` option to any Gradle tasks that extends `JavaForkOptions` (for example, `test` or `run`).

The agent can run in multiple modes:
- **Standard**: Collects metadata without conditions. This is recommended if you are building an executable.
- **Conditional**: Collects metadata with conditions. This is recommended if you are creating conditional metadata for a library intended for further use.
- **Direct**: For advanced users only. This mode allows directly controlling the command line passed to the agent.

You can configure the agent either passing the options on the command line, or in the _build.gradle_ file. See below how to configure the Native Image Gradle plugin, collect metadata with the tracing agent, and build a native executable applying the provided configuration.

1. (Optional) Instruct the agent to run in the standard mode. Insert this configuration block at the bottom of the _build.gradle_ file:

    ```xml
    graalvmNative {
        agent {
            defaultMode = "standard"
        }
        binaries {
            main {
                imageName.set('h2demo') 
            }
        }
        toolchainDetection = false
    }
    ```
    If you prefer the command-lime option, that will be `-Pagent=standard`.
    The second part of the configuration shows how to specify a custom name for a final native executable. 

    Another thing to note here, the plugin may not be able to properly detect the GraalVM installation, because of limitations in Gradle. The workaround is to disable toolchain detection with this command: `toolchainDetection = false`. Learn more about selecting the GraalVM toolchain [here](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#configuration-toolchains).

2. Now run your application with the agent enabled, on the JVM:

    ```shell
    ./gradlew -Pagent run
    ```
    The agent captures and writes down calls to the H2 Database and all the dynamic features encountered during a test run into multiple _*-config.json_ files.

3. Once the metadata is collected, copy it into the project's `/META-INF/native-image` directory using the `metadataCopy` task:

    ```shell
    ./gradlew metadataCopy --task run --dir src/main/resources/META-INF/native-image
    ```

    The JSON files are stored in the `META-INF/native-image/<group.id>/<artifact.id>` project directory. It is not required but recommended that the output directory is `/resources/META-INF/native-image/`. The `native-image` tool will pick up metadata from that location automatically. For more information about how to collect metadata for your application automatically, see [Collecting Metadata Automatically](../AutomaticMetadataCollection.md).
    Here is the expected files tree after this step:
    
    ![Configuration Files Generated by the Agent](img/H2Example-json-configs.png)

4. Build a native executable using metadata acquired by the agent: 

    ```shell
    ./gradlew nativeCompile
    ```
    The native executable, named _h2demo_, is created in the _build/native/nativeCompile_ directory.

5. Run the application from the native executable:

    ```shell
    ./H2Example/build/native/nativeCompile/h2demo
    ```

Learn more about using the agent with the Native Image Gradle plugin [here](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#agent-support).

> Important: To proceed to the next section, clean up the project: `./gradlew clean`. Make sure to delete `META-INF` and its contents.

## Build a Native Executable Using the GraalVM Reachability Metadata Repository

Since release 0.9.11, the Native Image Gradle plugin adds experimental support for the [GraalVM Reachability Metadata repository](https://github.com/oracle/graalvm-reachability-metadata). 
This repository provides GraalVM configuration for libraries which do not support GraalVM Native Image by default. The support needs to be enabled explicitly.

1. Open the _build.gradle_ file, and enable the GraalVM Reachability Metadata Repository in the `graalvmNative` plugin configuration: 

    ```xml
    metadataRepository {
        enabled = true
    }
    ```
    The whole configuration block should look like: 
    ```xml
    graalvmNative {
        agent {
            defaultMode = "standard"
        }
        binaries {
            main {
                imageName.set('h2demo') 
            }
        }
        metadataRepository {
            enabled = true
        }
        toolchainDetection = false
    }
    ```
    The plugin will automatically download the metadata from the repository.

2. Now build a native executable re-using metadata from the shared repository:
    ```shell
    ./gradlew nativeRun
    ```
3. Run the application from the native executable:

    ```shell
    ./H2Example/build/native/nativeCompile/h2demo
    ```

You are reaching the same results in less steps. Using the shared GraalVM Reachability Metadata Repository enhances the usability of Native Image for Java applications depending on 3rd party libraries.

### Summary

The [GraalVM Reachability Metadata Repository](https://github.com/oracle/graalvm-reachability-metadata) enables Native Image users to share and reuse metadata for libraries and frameworks in the Java ecosystem, and, thus share the burden of maintaining third-party dependencies.

Note that if your application does not call any dynamic features at run time, running the agent or enabling the GraalVM Reachability Metadata Repository is needless. 
Your workflow in that case would just be:
```shell
./gradlew nativeRun
```

### Related Documentation

- [Reachability Metadata](../ReachabilityMetadata.md)
- [Collect Metadata with the Tracing Agent](../AutomaticMetadataCollection.md#tracing-agent)
- [Gradle plugin for GraalVM Native Image building](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html)
- [GraalVM Reachability Metadata Repository](https://github.com/oracle/graalvm-reachability-metadata)