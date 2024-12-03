---
layout: ni-docs
toc_group: how-to-guides
link_title: Optimize a Native Executable for File Size
permalink: /reference-manual/native-image/guides/optimize-for-file-size/
---

# Optimize a Native Executable for File Size

You can optimize your native executable by taking advantage of different optimization levels. 
This guide will demonstrate how to create small native executables for a given application, using the optimization for size, `-Os`.

> `-Os` enables `-O2` optimizations except those that can increase code or executable size significantly. Typically, it creates the smallest possible executables at the cost of reduced performance. Learn more in [Optimization Levels](../OptimizationsAndPerformance.md#optimization-levels).

### Prerequisite 

Make sure you have installed Oracle GraalVM for JDK 23 or later.
The easiest way to get started is with [SDKMAN!](https://sdkman.io/jdks#graal){:target="_blank"}.
For other installation options, visit the [Downloads](https://www.graalvm.org/downloads/){:target="_blank"} section.

For the demo, run a "fortune teller" application that simulates the traditional `fortune` Unix program (for more information, see [fortune](https://en.wikipedia.org/wiki/Fortune_(Unix)){:target="_blank"}).

1. Clone the GraalVM Demos repository:
    ```bash
    git clone https://github.com/graalvm/graalvm-demos.git
    ```
    
2. Change directory to _fortune-demo/fortune-maven_:
    ```bash
    cd native-image/native-build-tools/maven-plugin
    ```

## Build a Native Executable with Default Configuration

1. Create a native executable using the [Maven plugin for Native Image building](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html){:target="_blank"}:
    ```bash
    ./mvnw -Pnative package
    ```
    The command compiles the project, creates a JAR file with all dependencies, and then generates a native executable, `fortune`, in the _target_ directory.

2. (Optional) Run the application:
    ```bash
    ./target/fortune
    ```
    The application will return a random saying. 

3. Check the file size which should be around 13M:
    ```bash
    du -h target/fortune
    ```

## Build a Native Executable Optimized for Size

Next create a native executable with the size optimization on, giving a different name for the output file to differentiate it from the previous build.

1. Open the _pom.xml_ file. Find the `native-maven-plugin` declaration, and notice the following build arguments within the `<configuration>` element:
    ```xml
    <configuration>
        <imageName>fortune-optimized</imageName>
        <buildArgs>
            <buildArg>-Os</buildArg>
            <buildArg>--emit build-report</buildArg>
        </buildArgs>
    </configuration>
    ```
    The `-Os` option enables size optimization. 
    The option `--emit build-report` generates a [Build Report](../BuildReport.md) along with other artifacts in the _target_ directory.

2. Create the second native executable:
    ```bash
    ./mvnw -Pnative package
    ```
    The command generates an executable file, `fortune-optimized`, in the _target_ directory.

3. Compare the sizes of all relevant output files:
    ```bash
    du -h target/fortune*
    ```
    You should see the output similar to this:
    ```
    13M    target/fortune
    16K    target/fortune-1.0-SNAPSHOT.jar
    9.8M   target/fortune-optimized
    1.9M   target/fortune-optimized-build-report.html
    ```
    The file size decreased from 13M to 9.8M! 

How much the file size can be reduced by the `-Os` option varies between applications, and depends on how much Native Image applies inlining and other optimizations that increase size in the default `-O2` mode.

The build report generated in the previous step, _fortune-optimized-build-report.html_, tells exactly what was included in your native executable.
It is an HTML file that you can open in a regular web browser. 

In case your native executable is quite large in file size, you may want to review the list of embedded resources, the list of modules and packages included in the code area, or the list of object types in the image heap, and check whether these elements are essential for your application.
Sometimes, large files are accidentally embedded as a resource due to erroneous regular expression patterns in the resource configuration of reachability metadata.
Registering wrong or too many Java types for reflection can also increase the size of a native executable significantly, by making unnecessary parts of the application, libraries, or the JDK reachable by accident.
Moreover, build-time initialization, if not used towards a specific goal, can cause large Java objects such as empty caches to be accidentally included in the image heap and, thus, cause bloat in a native executable.

Generally, it is a good idea to check file size, number of embedded resources, or other metrics from time to time, for example, when adding or updating dependencies, or even monitor build metrics frequently.
For this, you can use the [machine-readable version of the build output](../BuildOutput.md#machine-readable-build-output) or the [build reports for GitHub Actions](https://medium.com/graalvm/native-image-build-reports-and-update-notifications-351aca964a55){:target="_blank"}.

There are other Native Image techniques that can positively affect the executable size, besides improving other metrics, for example, [Profile-Guided Optimization (PGO)](optimize-native-executable-with-pgo.md).

### Related Documentation

- [Optimizations and Performance](../OptimizationsAndPerformance.md)