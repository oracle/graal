---
layout: docs
toc_group: embedding
link_title: Embedding Languages
permalink: /reference-manual/embed-languages/
---

# Embedding Languages

* [Dependency Setup](#dependency-setup)
* [Compile and Run a Polyglot Application](#compile-and-run-a-polyglot-application)
* [Define Guest Language Functions as Java Values](#define-guest-language-functions-as-java-values)
* [Access Guest Languages Directly from Java](#access-guest-languages-directly-from-java)
* [Access Java from Guest Languages](#access-java-from-guest-languages)
* [Lookup Java Types from Guest Languages](#lookup-java-types-from-guest-languages)
* [Computed Arrays Using Polyglot Proxies](#computed-arrays-using-polyglot-proxies)
* [Host Access](#host-access)
* [Runtime Optimization Support](#runtime-optimization-support)
* [Build Native Executables from Polyglot Applications](#build-native-executables-from-polyglot-applications)
* [Code Caching Across Multiple Contexts](#code-caching-across-multiple-contexts)
* [Polyglot Isolates](#polyglot-isolates)
* [Embed Guest Languages in Java](#embed-guest-languages-in-java)
* [Build a Shell for Many Languages](#build-a-shell-for-many-languages)
* [Step Through with Execution Listeners](#step-through-with-execution-listeners)
* [Setting the Heap Size](#setting-the-heap-size)
* [Compatibility with JSR-223 ScriptEngine](#compatibility-with-jsr-223-scriptengine)

The [GraalVM Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html) lets you embed and run code from guest languages in Java host applications.

Throughout this section, you will learn how to create a host application in Java that runs on GraalVM and directly calls a guest language.
You can use the tabs beneath each code example to choose between JavaScript, R, Ruby, and Python.

> Note: The usage description for polyglot embeddings was revised with GraalVM for JDK 21 and Polyglot API version 23.1.0. If you are still using Polyglot API version older than 23.1.0, ensure the correct version of the documentation is displayed. More information on the change can be found in the [release notes](https://www.graalvm.org/release-notes/JDK_21/#graalvm-for-jdk-21).

## Dependency Setup

Since Polyglot API version 23.1.0, all necessary artifacts can be downloaded directly from Maven Central.
Artifacts relevant to embedders can be found in the Maven dependency group [`org.graalvm.polyglot`](https://central.sonatype.com/namespace/org.graalvm.polyglot).
See the [polyglot embedding demonstration](https://github.com/graalvm/polyglot-embedding-demo) on GitHub for a complete runnable example.

Here is an example Maven dependency setup that you can put into your project:
```xml
<dependency>
	<groupId>org.graalvm.polyglot</groupId>
	<artifactId>polyglot</artifactId>
	<version>${graalvm.polyglot.version}</version>
</dependency>
<dependency>
	<groupId>org.graalvm.polyglot</groupId>
	<!-- Select a language: js, ruby, python, java, llvm, wasm, languages-->
	<artifactId>js</artifactId>
	<version>${graalvm.polyglot.version}</version>
	<type>pom</type>
</dependency>
<!-- Add additional languages if needed -->
<dependency>
	<groupId>org.graalvm.polyglot</groupId>
    <!-- Select a tool: profiler, inspect, coverage, dap, tools -->
	<artifactId>profiler</artifactId>
	<version>${graalvm.polyglot.version}</version>
	<type>pom</type>
</dependency>
```

> The `pom` type is a requirement for language or tool dependencies.

Language and tool dependencies use the [GraalVM Free Terms and Conditions (GFTC)](https://www.oracle.com/downloads/licenses/graal-free-license.html) license.
To use community-licensed versions instead, add the `-community` suffix to each artifact (for example, `js-community`).
To access [polyglot isolate](#polyglot-isolates) artifacts, use the `-isolate` suffix instead (for example, `js-isolate`).

The artifacts `languages` and `tools` include all available languages and tools as dependencies.
This artifact might grow or shrink between major releases.
We recommend selecting only the needed language(s) for a production deployment.

Additionally, your _module-info.java_ file should require `org.graalvm.polyglot` when using Java modules:
```java
module com.mycompany.app {
  requires org.graalvm.polyglot;
}
```

Whether your configuration can run with a Truffle runtime optimization depends on the GraalVM JDK you use.
For further details, refer to the [Runtime Compilation section](#runtime-optimization-support).

We recommend configuring polyglot embeddings using modules and the module path whenever possible.
Be aware that using `org.graalvm.polyglot` from the class path instead will enable access to unsafe APIs for all libraries on the class path.
If the application is not yet modularized, hybrid use of the class path and module path is possible.
For example:
```
$java -classpath=lib --module-path=lib/polyglot --add-modules=org.graalvm.polyglot ...
```
In this example, `lib/polyglot` directory should contain all polyglot and language JAR files.
To access polyglot classes from the class path, you must also specify the `--add-modules=org.graalvm.polyglot` JVM option.
If you are using [GraalVM Native Image](#build-native-executables-from-polyglot-applications), polyglot modules on the class path will be automatically upgraded to the module path.

While we do support [creating single uber JAR files](#uber-jar-file-creation) from polyglot libraries, for example, using the Maven Assembly plugin, we do not recommend it.
Also note that uber JAR files are not supported when creating native binaries with GraalVM Native Image.

## Compile and Run a Polyglot Application

GraalVM can run polyglot applications written in any language implemented with the [Truffle language implementation framework](../../../truffle/docs/README.md).
These languages are henceforth referenced as **guest languages**.

Complete the steps in this section to create a sample polyglot application that runs on GraalVM and demonstrates programming language interoperability.

1. Create a new Java project using Maven.

2. Clone the [polyglot-embedding-demo](https://github.com/graalvm/polyglot-embedding-demo/) repository:
    ```bash
    git clone https://github.com/graalvm/polyglot-embedding-demo.git
    ```

3. Insert the example code into the [Main class](https://github.com/graalvm/polyglot-embedding-demo/blob/main/src/main/java/org/example/embedding/Main.java).

4. Update the Maven [pom.xml](https://github.com/graalvm/polyglot-embedding-demo/blob/main/pom.xml) dependency configuration to include the languages to run as described in the [previous section](#dependency-setup).

5. [Download and install GraalVM](../../getting-started/get-started.md) by setting the value of the `JAVA_HOME` environment variable to the location of a GraalVM JDK.

6. Run `mvn package exec:exec` to build and execute the sample code.

You now have a polyglot application that consists of a Java host application and guest language code, running on GraalVM.
You can use this application with other code examples to demonstrate more advanced capabilities of the GraalVM Polyglot API.

## Define Guest Language Functions as Java Values

Polyglot applications let you take values from one programming language and use them with other languages.

Use the code example in this section with your polyglot application to show how the Polyglot API can return JavaScript or Python functions as Java values.

{%
include snippet-tabs
tab1type="java" tab1id="Function_JS" tab1name="JavaScript" tab1path="embed/function_js.java"
tab2type="java" tab2id="Function_Python" tab2name="Python" tab2path="embed/function_python.java"
%}

In this code:
- `Value function` is a Java value that refers to a function.
- The `eval` call parses the script and returns the guest language function.
- The first assertion checks that the value returned by the code snippet can be executed.
- The `execute` call executes the function with the argument `41`.
- The `asInt` call converts the result to a Java `int`.
- The second assertion verifies that the result was incremented by one as expected.

## Access Guest Languages Directly from Java

Polyglot applications can readily access most language types and are not limited to functions.
Host languages, such as Java, can directly access guest language values embedded in the polyglot application.

Use the code example in this section with your polyglot application to show how the Polyglot API can access objects, numbers, strings, and arrays.

{%
include snippet-tabs
tab1type="java" tab1id="Access_JS" tab1name="JavaScript" tab1path="embed/access_js_from_java.java"
tab2type="java" tab2id="Access_Python" tab2name="Python" tab2path="embed/access_python_from_java.java"
%}

In this code:
- `Value result` is an Object that contains three members: a number named `id`, a string named `text`, and an array named `arr`.
- The first assertion verifies that the return value can contain members, which indicates that the value is an object-like structure.
- The `id` variable is initialized by reading the member with the name `id` from the resulting object. The result is then converted to a Java `int` using `asInt()`.
- The next assert verifies that result has a value of `42`.
- The `text` variable is initialized using the value of the member `text`, which is also converted to a Java `String` using `asString()`.
- The following assertion verifies the result value is equal to the Java `String` `"42"`.
- Next the `arr` member that holds an array is read.
- Arrays return `true` for `hasArrayElements`.
- The next assertion verifies that the size of the array equals three. The Polyglot API supports big arrays, so the array length is of type `long`.
- Finally we verify that the array element at index `1` equals `42`. Array indexing with polyglot values is always zero-based, even for languages where indices start with one.

## Access Java from Guest Languages

Polyglot applications offer bi-directional access between guest languages and host languages.
As a result, you can pass Java objects to guest languages.

Since the Polyglot API is secure by default, access is limited in the default configuration.
To permit guest languages to access any public method or field of a Java object, you have to explicitly specify `allowAllAccess(true)` when the context is built.
In this mode, the guest language code can access any resource that is accessible to host Java code.

Use the code example in this section with your polyglot application to show how guest languages can access primitive Java values, objects, arrays, and functional interfaces.

{%
include snippet-tabs
tab1type="java" tab1id="Access_Java_from_JS" tab1name="JavaScript" tab1path="embed/access_java_from_js.java"
tab2type="java" tab2id="Access_Java_from_Python" tab2name="Python" tab2path="embed/access_java_from_python.java"
%}

In this code:
- The Java class `MyClass` has four public fields `id`, `text`, `arr`, and
`ret42`. The fields are initialized with `42`, `"42"`, `new int[]{1, 42, 3}`, and
lambda `() -> 42` that always returns an `int` value of `42`.
- The Java class `MyClass` is instantiated and exported with the name `javaObj`
into the polyglot scope, which allows the host and guest languages to exchange
symbols.
- A guest language script is evaluated that imports the `javaObj` symbol and
assigns it to the local variable which is also named `javaObj`. To avoid
conflicts with variables, every value in the polyglot scope must be explicitly
imported and exported in the top-most scope of the language.
- The next two lines verify the contents of the Java object by comparing it
to the number `42` and the string `'42'`.
- The third verification reads from the second array position and compares it
to the number `42`. Whether arrays are accessed using 0-based or 1-based indices
depends on the guest language. Independently of the language, the Java array
stored in the `arr` field is always accessed using translated 0-based indices. For
example, in the JavaScript and Ruby languages, the second
array element is at index `1`. In all language examples, the Java array is read
from using the same index `1`.
- The last line invokes the Java lambda that is contained in the field `ret42`
and compares the result to the number value `42`.
- After the guest language script executes, validation takes place to ensure
that the script returns a `boolean` value of `true` as a result.

## Lookup Java Types from Guest Languages

In addition to passing Java objects to the guest language, it is possible to allow the lookup of Java types in the guest language.

Use the code example in this section with your polyglot application to show how guest languages lookup Java types and instantiate them.

{%
include snippet-tabs
tab1type="java" tab1id="Lookup_Java_from_JS" tab1name="JavaScript" tab1path="embed/lookup_java_from_js.java"
tab2type="java" tab2id="Lookup_Java_from_Python" tab2name="Python" tab2path="embed/lookup_java_from_python.java"
%}

In this code:
- A new context is created with all access enabled (`allowAllAccess(true)`).
- A guest language script is evaluated.
- The script looks up the Java type `java.math.BigDecimal` and stores it in a variable named `BigDecimal`.
- The static method `BigDecimal.valueOf(long)` is invoked to create new
`BigDecimal`s with value `10`. In addition to looking up static Java methods, it
is also possible to directly instantiate the returned Java type., for example, in
JavaScript using the `new` keyword.
- The new decimal is used to invoke the `pow` instance method with `20` which calculates  `10^20`.
- The result of the script is converted to a host object by calling `asHostObject()`. The return value is automatically cast to the `BigDecimal` type.
- The result decimal string is asserted to equal to `"100000000000000000000"`.

## Computed Arrays Using Polyglot Proxies

The Polyglot API includes polyglot proxy interfaces that let you customize Java interoperability by mimicking guest language types, such as objects, arrays, native objects, or primitives.

Use the code example in this section with your polyglot application to see how you can implement arrays that compute their values lazily.

> Note: The Polyglot API supports polyglot proxies either on the JVM or in Native Image.

{%
include snippet-tabs
tab1type="java" tab1id="Proxy_JS" tab1name="JavaScript" tab1path="embed/proxy_js.java"
tab2type="java" tab2id="Proxy_Python" tab2name="Python" tab2path="embed/proxy_python.java"
%}

In this code:
- The Java class `ComputedArray` implements the proxy interface `ProxyArray` so
that guest languages treat instances of the Java class-like arrays.
- `ComputedArray` array overrides the method `get` and computes the value
using an arithmetic expression.
- The array proxy does not support write access. For this reason, it throws
an `UnsupportedOperationException` in the implementation of `set`.
- The implementation for `getSize` returns `Long.MAX_VALUE` for its length.
- The main method creates a new polyglot execution context.
- A new instance of the `ComputedArray` class is then exported using the name `arr`.
- The guest language script imports the `arr` symbol, which returns the
exported proxy.
- The second element and the `1000000000`th element is accessed, summed up, and
then returned. Note that array indices from 1-based languages are
converted to 0-based indices for proxy arrays.
- The result of the language script is returned as a long value and verified.

For more information about the polyglot proxy interfaces, see the [Polyglot API JavaDoc](http://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/package-summary.html).

## Host Access

The Polyglot API by default restricts access to certain critical functionality, such as file I/O.
These restrictions can be lifted entirely by setting `allowAllAccess` to `true`.

> Note: The access restrictions are currently only supported with JavaScript.

### Controlling Access to Host Functions

It might be desirable to limit the access of guest applications to the host.
For example, if a Java method is exposed that calls `System.exit`, then the guest application will be able to exit the host process.
In order to avoid accidentally exposed methods, no host access is allowed by default and every public method or field needs to be annotated with `@HostAccess.Export` explicitly.

{%
include snippet-tabs
tab1type="java" tab1id="ExplicitHostAccess_js" tab1name="JavaScript" tab1path="embed/explicit_access_java_from_js.java"
%}

In this code:
- The class `Employee` is declared with a field `name` of type `String`. Access to the `getName` method is explicitly allowed by annotating the method with `@HostAccess.Export`.
- The `Services` class exposes two methods, `createEmployee` and `exitVM`. The `createEmployee` method takes the name of the employee as an argument and creates a new `Employee` instance. The `createEmployee` method is annotated with `@HostAccess.Export` and therefore accessible to the guest application. The `exitVM` method is not explicitly exported and therefore not accessible.
- The `main` method first creates a new polyglot context in the default configuration, disallowing host access except for methods annotated with `@HostAccess.Export`.
- A new `Services` instance is created and put into the context as global variable `services`.
- The first evaluated script creates a new employee using the services object and returns its name.
- The returned name is asserted to equal the expected name `John Doe`.
- A second script is evaluated that calls the `exitVM` method on the services object. This fails with a `PolyglotException` as the exitVM method is not exposed to the guest application.

Host access is fully customizable by creating a custom [`HostAccess`](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/HostAccess.html) policy.

### Controlling Host Callback Parameter Scoping

By default, a `Value` lives as long as the corresponding `Context`.
However, it may be desirable to change this default behavior and bind a value to a scope, such that when execution leaves the scope, the value is invalidated.
An example for such a scope are guest-to-host callbacks, where a `Value` may be passed as a callback parameter.
We have already seen above how passing callback parameters works with the default `HostAccess.EXPLICIT`:

```java
public class Services {
    Value lastResult;

    @HostAccess.Export
    public void callback(Value result) {
        this.lastResult = result;
    }

    String getResult() {
        return this.lastResult.asString();
    }
}

public static void main(String[] args) {
    Services s = new Services()
    try (Context context = Context.newBuilder().allowHostAccess(HostAccess.EXPLICIT).build()) {
        context.getBindings("js").putMember("services", s);
        context.eval("js", "services.callback('Hello from JS');");
        System.out.println(s.getResult());
    }
}
```

In this example, `lastResult` maintains a reference to the value from the guest that is stored on the host and remains accessible also after the scope of `callback()` has ended.

However, this is not always desirable, as keeping the value alive may block resources unnecessarily or not reflect the behavior of ephemeral values correctly.
For these cases, `HostAccess.SCOPED` can be used, which changes the default behavior for all callbacks, such that values that are passed as callback parameters are only valid for the duration of the callback.

To make the above code work with `HostAccess.SCOPED`, individual values passed as a callback parameters can be pinned to extend their validity until after the callback returns:
```java
public class Services {
    Value lastResult;

    @HostAccess.Export
    void callback(Value result, Value notneeded) {
        this.lastResult = result;
        this.lastResult.pin();
    }

    String getResult() {
        return this.lastResult.asString();
    }
}

public static void main(String[] args) {
    Services s = new Services()
    try (Context context = Context.newBuilder().allowHostAccess(HostAccess.SCOPED).build()) {
        context.getBindings("js").putMember("services", s);
        context.eval("js", "services.callback('Hello from JS', 'foobar');");
        System.out.println(services.getResult());
    }
}
```

Alternatively, the entire callback method can opt out from scoping if annotated with `@HostAccess.DisableMethodScope`, maintaining regular semantics for all parameters of the callback:
```java
public class Services {
    Value lastResult;
    Value metaInfo;

    @HostAccess.Export
    @HostAccess.DisableMethodScope
    void callback(Value result, Value metaInfo) {
        this.lastResult = result;
        this.metaInfo = metaInfo;
    }

    String getResult() {
        return this.lastResult.asString() + this.metaInfo.asString();
    }
}

public static void main(String[] args) {
    Services s = new Services()
    try (Context context = Context.newBuilder().allowHostAccess(HostAccess.SCOPED).build()) {
        context.getBindings("js").putMember("services", s);
        context.eval("js", "services.callback('Hello from JS', 'foobar');");
        System.out.println(services.getResult());
    }
}
```

### Access Privilege Configuration

It is possible to configure fine-grained access privileges for guest applications.
The configuration can be provided using the [`Context.Builder`](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.Builder.html) class when constructing a new context.
The following access parameters may be configured:

* Allow access to other languages using [`allowPolyglotAccess`](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.Builder.html#allowPolyglotAccess-org.graalvm.polyglot.PolyglotAccess-).
* Allow and customize access to host objects using [`allowHostAccess`](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.Builder.html#allowHostAccess-org.graalvm.polyglot.HostAccess-).
* Allow and customize host lookup to host types using [`allowHostClassLookup`](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.Builder.html#allowHostClassLookup-java.util.function.Predicate-). Allows the guest application to look up the host application classes permitted by the lookup predicate. For example, a Javascript context can create a Java ArrayList, provided that ArrayList is allowlisted by the `classFilter` and access is permitted by the host access policy: `context.eval("js", "var array = Java.type('java.util.ArrayList')")`
* Allow host class loading using [`allowHostClassLoading`](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.Builder.html#allowHostClassLoading-boolean-). Classes are only accessible if access to them is granted by the host access policy.
* Allow the creation of threads using [`allowCreateThread`](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.Builder.html#allowCreateThread-boolean-).
* Allow access to native APIs using [`allowNativeAccess`](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.Builder.html#allowNativeAccess-boolean-).
* Allow access to IO using [`allowIO`](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.Builder.html#allowIO-boolean-) and proxy file accesses using [`fileSystem`](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.Builder.html#fileSystem-org.graalvm.polyglot.io.FileSystem-).

> Note: Granting access to class loading, native APIs, or host I/O effectively grants all access, as these privileges can be used to bypass other access restrictions.

## Runtime Optimization Support

Polyglot Truffle runtimes can be used on several host virtual machines with varying support for runtime optimization.
Runtime optimization of guest application code is crucial for the efficient execution of embedded guest applications.
This table shows the level of optimizations the Java runtimes currently provide:

| Java Runtime                                  | Runtime Optimization Level                          |
|-----------------------------------------------|-----------------------------------------------------|
| Oracle GraalVM                                | Best (includes additional compiler optimizations)   |
| GraalVM Community Edition                     | Optimized                                           |
| Oracle JDK                                    | Optimized via VM option                             |
| OpenJDK                                       | Optimized via VM option and `--upgrade-module-path` |
| JDK without JVMCI capability                  | No runtime optimizations (interpreter-only)         |

### Explanations

* **Optimized:** Executed guest application code can be compiled and executed as highly efficient machine code at run time.
* **Optimized with additional compiler passes:** Oracle GraalVM implements additional optimizations performed during runtime compilation. For example, it uses a more advanced inlining heuristic. This typically leads to better runtime performance and memory consumption.
* **Optimized via VM option:** Optimization is enabled by specifying `-XX:+EnableJVMCI` to the `java` launcher.
* **Optimized via VM option and `--upgrade-module-path`:** Optimization is enabled by specifying `-XX:+EnableJVMCI` to the `java` launcher. Additionally, the Graal compiler must be downloaded as a JAR file and specified to the `java` launcher with `--upgrade-module-path`. In this mode, the compiler runs as a Java application and may negatively affect the execution performance of the host application.
* **No runtime optimizations:** With no runtime optimizations or if JVMCI is not enabled, the guest application code is executed in interpreter-only mode.
* **JVMCI:** Refers to the [Java-Level JVM Compiler Interface](https://openjdk.org/jeps/243) supported by most Java runtimes.

A project has been created to enable runtime optimization by default for Oracle JDK and OpenJDK.
See [Project Galahad](https://openjdk.org/projects/galahad/) for further details.

### Enable Optimization on OpenJDK and Oracle JDK

When running on a JDK runtime optimization enabled by default, such as OpenJDK, you might see a warning like this:

```
[engine] WARNING: The polyglot engine uses a fallback runtime that does not support runtime compilation to machine code.
Execution without runtime compilation will negatively impact the guest application performance.
```

This indicates that the guest application is executed with no runtime optimizations enabled.
The warning can be suppressed by either suppressing using the `--engine.WarnInterpreterOnly=false` option or the `-Dpolyglot.engine.WarnInterpreterOnly=false` system property.
In addition, the `compiler.jar` file and its dependencies must be downloaded from [Maven Central](https://central.sonatype.com/artifact/org.graalvm.compiler/compiler/) and referred to use the option `--upgrade-module-path`.
Note that `compiler.jar` must *not* be put on the module or class path.
Refer to the [polyglot embedding demonstration](https://github.com/graalvm/polyglot-embedding-demo) for an example configuration using Maven or Gradle.

### Switching to the Fallback Engine

If the need arises, for example, running only trivial scripts or in the resource-constrained systems, you may want to switch to the fallback engine without runtime optimizations.
Since Polyglot version 23.1, the fallback engine can be activated by removing the `truffle-runtime` and `truffle-enterprise` modules from the class or module path.

This can be achieved with Maven like this:

```xml
<dependencies>
  <dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>js</artifactId>
    <version>$graalvm-version</version>
    <exclusions>
      <exclusion>
        <groupId>org.graalvm.truffle</groupId>
        <artifactId>truffle-runtime</artifactId>
      </exclusion>
      <exclusion>
        <groupId>org.graalvm.truffle</groupId>
        <artifactId>truffle-enterprise</artifactId>
      </exclusion>
    </exclusions>
  </dependency>
</dependencies>
```

The exclusion rule for `truffle-enterprise` is unnecessary if you only use `-community` dependencies.
Since `truffle-enterprise` is excluded, the fallback engine does not support advanced extensions such as sandbox limits or polyglot isolates.
It may be useful to double-check with `mvn dependency:tree` that the two dependencies are not included elsewhere.

If the runtime was excluded successfully, you should see the following log message:

```
[engine] WARNING: The polyglot engine uses a fallback runtime that does not support runtime compilation to native code.
Execution without runtime compilation will negatively impact the guest application performance.
The following cause was found: No optimizing Truffle runtime found on the module or class path.
For more information see: https://www.graalvm.org/latest/reference-manual/embed-languages/.
To disable this warning use the '--engine.WarnInterpreterOnly=false' option or the '-Dpolyglot.engine.WarnInterpreterOnly=false' system property.
```

You can disable this message using the indicated options as an additional step.

Removing these dependencies also automatically switches to the fallback engine in Native Image builds.

## Build Native Executables from Polyglot Applications

With Polyglot version 23.1 on GraalVM for JDK 21 and later, no special configuration is required to use [Native Image](../native-image/README.md) to build images with embedded polyglot language runtimes.
Like any other Java dependency, the polyglot language JAR files must be on the class or module path when building a native executable.
We recommend to use the [Maven](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html) or [Gradle](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html) Native Image plugins to configure your `native-image` builds.
A sample Maven and Gradle configuration for Native Image can be found in the [polyglot embedding demonstration repository](https://github.com/graalvm/polyglot-embedding-demo).

Here is a Maven profile configuration example:
```xml
<profiles>
    <profile>
        <id>native</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.graalvm.buildtools</groupId>
                    <artifactId>native-maven-plugin</artifactId>
                    <version>${native.maven.plugin.version}</version>
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
                        <imageName>${project.artifactId}</imageName>
                        <mainClass>org.example.embedding.Main</mainClass>
                        <buildArgs>
                            <buildArg>--no-fallback</buildArg>
                            <buildArg>-J-Xmx20g</buildArg>
                        </buildArgs>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

To build a native executable with the above configuration, run:
```
mvn -Pnative package
```

Building a native executable from a polyglot application, for example, a Java-host application embedding Python, automatically captures all the internal resources required by the included languages and tools.
By default, the resources are included in the native executable itself.
The inclusion of resources in the native executable can be disabled by `-H:-IncludeLanguageResources`.
Another option is a separate _resources_ directory containing all the required files.
To switch to this option, use `-H:+CopyLanguageResources`. This is the default behavior when `-H:+IncludeLanguageResources` is not supported, i.e., with Graal Languages earlier than 24.2.x (see the [versions roadmap](https://www.graalvm.org/release-calendar/)).
When `-H:+CopyLanguageResources` is used, the language runtime will look for the resources directory relative to the native executable or the shared library.
At run time, the lookup location may be customized using the `-Dpolyglot.engine.resourcePath=path/to/resources` option.
To disable the capturing of resources altogether, add both `-H:-IncludeLanguageResources` and `-H:-CopyLanguageResources` to build-time options.
Note that some languages may not support running without their resources.

With Graal Languages version 23.1 and newer the language home options like `-Dorg.graalvm.home` should no longer be used and were replaced with the resource directory option.
The language home options remain functional for compatibility reasons but may be removed in future releases.

### Configuring Native Host Reflection

Accessing host Java code from the guest application requires Java reflection in order to work.
When reflection is used within a native executable, the [reflection configuration file](../native-image/ReachabilityMetadata.md#reflection) is required.

For this example we use JavaScript to show host access with native executables.
Copy the following code in a new file named `AccessJavaFromJS.java`.

```java
import org.graalvm.polyglot.*;
import org.graalvm.polyglot.proxy.*;
import java.util.concurrent.*;

public class AccessJavaFromJS {

    public static class MyClass {
        public int               id    = 42;
        public String            text  = "42";
        public int[]             arr   = new int[]{1, 42, 3};
        public Callable<Integer> ret42 = () -> 42;
    }

    public static void main(String[] args) {
        try (Context context = Context.newBuilder()
                                   .allowAllAccess(true)
                               .build()) {
            context.getBindings("js").putMember("javaObj", new MyClass());
            boolean valid = context.eval("js",
                   "    javaObj.id         == 42"          +
                   " && javaObj.text       == '42'"        +
                   " && javaObj.arr[1]     == 42"          +
                   " && javaObj.ret42()    == 42")
               .asBoolean();
            System.out.println("Valid " + valid);
        }
    }
}
```

Copy the following code into `reachability-metadata.json`:
```json
{
  "reflection": [
     { "type": "AccessJavaFromJS$MyClass", "allPublicFields": true },
     { "type": "java.util.concurrent.Callable", "allPublicMethods": true }
  ]
}
```


Now, you can add `reachability-metadata.json` to `META-INF/native-image/<group-id>/` of your project.

## Code Caching Across Multiple Contexts

The GraalVM Polyglot API allows code caching across multiple contexts.
Code caching allows compiled code to be reused and allows sources to be parsed only once.
Code caching can often reduce memory consumption and warm-up time of the application.

By default, code is cached within a single context instance only.
An explicit engine needs to be specified to enable code caching between multiple contexts.
The engine is specified when creating the context using the [context builder](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html).
The engine instance determines the scope of code sharing.
Code is only shared between contexts associated with one engine instance.

All sources are cached by default.
Caching may be disabled explicitly by setting [cached(boolean cached)](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Source.Builder.html#cached-boolean-) to `false`. Disabling caching may be useful in case the source is known to only be evaluated once.

Consider the following code snippet as an example:

```java
public class Main {
    public static void main(String[] args) {
        try (Engine engine = Engine.create()) {
            Source source = Source.create("js", "21 + 21");
            try (Context context = Context.newBuilder()
                .engine(engine)
                .build()) {
                    int v = context.eval(source).asInt();
                    assert v == 42;
            }
            try (Context context = Context.newBuilder()
                .engine(engine)
                .build()) {
                    int v = context.eval(source).asInt();
                    assert v == 42;
            }
        }
    }
}
```

In this code:
- `import org.graalvm.polyglot.*` imports the base API for the Polyglot API.
- `Engine.create()` creates a new engine instance with the default configuration.
- `Source.create()` creates a source object for the expression “21 + 21”. We use an explicit `Source` object to ensure the code cache does not get garbage collected between contexts.
with "js" language, which is the language identifier for JavaScript.
- `Context.newBuilder().engine(engine).build()` builds a new context with
an explicit engine assigned to it. All contexts associated with an engine share the code.
- `context.eval(source).asInt()` evaluates the source and returns the result as `Value` instance.

***Important:*** To keep the code cache of a cached source alive between executing contexts, the application must ensure that the `Source` object is continually referenced.
The polyglot runtime may collect cached code of sources no longer referenced with the next GC cycle.

### Managing the Code Cache

The data for the code cache is stored as part of the `Engine` instance.
There is never any code sharing happening between two separate engine instances.
Hence, we recommend using a singleton `Engine` instance if a global code cache is needed.
As opposed to contexts, engines can always be shared across multiple threads.
Whether contexts can be shared across multiple threads depends on the language used.

There is no explicit method to purge the code cache.
We rely on the garbage collector to do this automatically with the next collection.
The code cache of an engine is not collected as long as the engine is still strongly referenced and not closed.
Also, the `Source` instance must be kept alive to ensure the associated code is not collected.
If a source instance is no longer referenced, but the engine is still referenced, the code cache associated with a source object may be collected by the GC.
We recommend, therefore, keeping a strong reference to the `Source` object as long as `Source` should remain cached.

To summarize, the code cache can be controlled by keeping and maintaining strong references to the `Engine` and `Source` objects.

## Polyglot Isolates

On Oracle GraalVM, a polyglot engine can be configured to run in a dedicated Native Image isolate.
A polyglot engine in this mode executes within a VM-level fault domain with a dedicated garbage collector and JIT compiler.
Polyglot isolates are useful for [sandboxing](../../security/polyglot-sandbox.md).
Running languages in an isolate works with HotSpot and Native Image host virtual machines.

Languages used as polyglot isolates can be downloaded from Maven Central using the `-isolate` suffix.
For example, a dependency on isolated JavaScript can be configured by adding a Maven dependency like this:

```xml
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>polyglot</artifactId>
    <version>${graalvm.polyglot.version}</version>
    <type>jar</type>
</dependency>
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>js-isolate</artifactId>
    <version>${graalvm.polyglot.version}</version>
    <type>pom</type>
</dependency>
```

Starting from the Polyglot API version 24.1.0, the polyglot engine supports polyglot isolates for individual platforms.
To download a polyglot isolate for a specific platform, append the operating system and CPU architecture classifiers to the polyglot isolate Maven `artifactId`.
For example, to configure a dependency on isolated Python for Linux amd64, add the following Maven dependencies:

```xml
<dependency>
	<groupId>org.graalvm.polyglot</groupId>
	<artifactId>polyglot</artifactId>
	<version>${graalvm.polyglot.version}</version>
	<type>jar</type>
</dependency>
<dependency>
	<groupId>org.graalvm.polyglot</groupId>
	<artifactId>python-isolate-linux-amd64</artifactId>
	<version>${graalvm.polyglot.version}</version>
	<type>pom</type>
</dependency>
```

Supported platform classifiers are:
* `linux-amd64`
* `linux-aarch64`
* `darwin-amd64`
* `darwin-aarch64`
* `windows-amd64`

For a complete Maven POM file that adds the polyglot isolate Native Image dependency for the current platform, refer to the [Polyglot Embedding Demonstration](https://github.com/graalvm/polyglot-embedding-demo) on GitHub.

To enable isolate usage with the Polyglot API, the `--engine.SpawnIsolate=true` option must be passed to `Engine` or `Context` when constructed.
The option `engine.SpawnIsolate` may not be available if used on any JDK other than Oracle GraalVM.

```java
import org.graalvm.polyglot.*;

public class PolyglotIsolate {
	public static void main(String[] args) {
		try (Context context = Context.newBuilder("js")
			  .allowHostAccess(HostAccess.SCOPED)
			  .option("engine.SpawnIsolate", "true").build()) {

			Value function = context.eval("js", "x => x+1");
			assert function.canExecute();
			int x = function.execute(41).asInt();
			assert x == 42;
		}
	}
}
```

Starting from GraalVM 25.0, a polyglot isolate can be launched in a separate external sub-process by setting the `--engine.IsolateMode=external` option.
This allows the isolate to run in a fully separate OS process, providing an additional level of isolation. The default mode remains `internal`, which uses a Native Image isolate embedded in the same process.

```java
Context context = Context.newBuilder("js")
			  .allowHostAccess(HostAccess.SCOPED)
			  .option("engine.SpawnIsolate", "true")
			  .option("engine.IsolateMode", "external")
			  .build()
```

Currently, the following languages are available as polyglot isolates:

| Language                      | Available from |
|-------------------------------|----------------|
| JavaScript (`js-isolate`)     | 23.1           |
| Python (`python-isolate`)     | 24.1           |
| Wasm (`wasm-isolate`)         | 25.0           |

We plan to add support for more languages in future versions.

In the previous example, we enable scoped references using `HostAccess.SCOPED`.
This is necessary because the host GC and the guest GC are unaware of one another, so cyclic references between objects cannot be resolved automatically.
We thus strongly recommend using [scoped parameters for host callbacks](#controlling-host-callback-parameter-scoping) to avoid cyclic references altogether.

Multiple contexts can be spawned in the same isolated engine by [sharing engines](#code-caching-across-multiple-contexts):

```java
public class PolyglotIsolateMultipleContexts {
    public static void main(String[] args) {
        try (Engine engine = Engine.newBuilder("js")
                .option("engine.SpawnIsolate", "true").build()) {
            Source source = Source.create("js", "21 + 21");
            try (Context context = Context.newBuilder()
                .engine(engine)
                .build()) {
                    int v = context.eval(source).asInt();
                    assert v == 42;
            }
            try (Context context = Context.newBuilder()
                .engine(engine)
                .build()) {
                    int v = context.eval(source).asInt();
                    assert v == 42;
            }
        }
    }
}
```

### Passing Native Image Runtime Options

Engines running in an isolate can make use of [Native Image runtime options](../native-image/BuildOptions.md) by passing `--engine.IsolateOption.<option>` to the engine builder.
For example, this can be used to limit the maximum heap memory used by an engine by setting the maximum heap size for the isolate via `--engine.IsolateOption.MaxHeapSize=128m`:

```java
import org.graalvm.polyglot.*;

public class PolyglotIsolateMaxHeap {
  public static void main(String[] args) {
    try {
      Context context = Context.newBuilder("js")
        .allowHostAccess(HostAccess.SCOPED)
        .option("engine.SpawnIsolate", "true")
        .option("engine.IsolateOption.MaxHeapSize", "64m").build()
      context.eval("js", "var a = [];while (true) {a.push('foobar');}");
    } catch (PolyglotException ex) {
      if (ex.isResourceExhausted()) {
        System.out.println("Resource exhausted");
      }
    }
  }
}
```
Exceeding the maximum heap size will automatically close the context and raise a `PolyglotException`.

### Ensuring Host Callback Stack Headroom

With Polyglot Isolates, the `--engine.HostCallStackHeadRoom` ensures a minimum stack space available when performing a host callback.
The host callback fails if the available stack size drops below the specified threshold.

### Memory Protection

In Linux environments that support Memory Protection Keys, the `--engine.MemoryProtection=true` option can be used to isolate the heaps of Polyglot Isolates at the hardware level.
If an engine is created with this option, a dedicated protection key will be allocated for the isolated engine's heap.
GraalVM only enables access to the engine's heap when executing code of the Polyglot Isolate.

## Embed a Guest Language in Java

The GraalVM Polyglot API can be used from within a guest language using Java interoperability.
This can be useful if a script needs to run isolated from the parent context.
In Java as a host language a call to `Context.eval(Source)` returns an instance of `Value`, but since we executing this code as part of a guest language we can use the language-specific interoperability API instead.
It is therefore possible to use values returned by contexts created inside of a language, like regular values of the language.
In the example below we can conveniently write `value.data` instead of `value.getMember("data")`.
Please refer to the individual language documentation for details on how to interoperate with foreign values.
More information on value sharing between multiple contexts can be found [here](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#allowValueSharing-boolean-).

Consider the following code snippet as an example:

```java
import org.graalvm.polyglot.*;

public class Main {
    public static void main(String[] args) {
        try (Context outer = Context.newBuilder()
                                   .allowAllAccess(true)
                               .build()) {
            outer.eval("js", "inner = Java.type('org.graalvm.polyglot.Context').create()");
            outer.eval("js", "value = inner.eval('js', '({data:42})')");
            int result = outer.eval("js", "value.data").asInt();
            outer.eval("js", "inner.close()");

            System.out.println("Valid " + (result == 42));
        }
    }
}
```

In this code:
- `Context.newBuilder().allowAllAccess(true).build()` builds a new outer context with all privileges.
- `outer.eval` evaluates a JavaScript snippet in the outer context.
- `inner = Java.type('org.graalvm.polyglot.Context').create()` the first JS script line looks up the Java host type Context and creates a new inner context instance with no privileges (default).
- `inner.eval('js', '({data:42})');` evaluates the JavaScript code `({data:42})` in the inner context and returns stores the result.
- `"value.data"` this line reads the member `data` from the result of the inner context. Note that this result can only be read as long as the inner context is not yet closed.
- `context.eval("js", "c.close()")` this snippet closes the inner context. Inner contexts need to be closed manually and are not automatically closed with the parent context.
- Finally the example is expected to print `Valid true` to the console.

## Build a Shell for Many Languages

With just a few lines of code, the GraalVM Polyglot API lets you build applications that integrate with any guest language supported by GraalVM.

This shell implementation is agnostic to any particular guest language.

```java
BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
PrintStream output = System.out;
Context context = Context.newBuilder().allowAllAccess(true).build();
Set<String> languages = context.getEngine().getLanguages().keySet();
output.println("Shell for " + languages + ":");
String language = languages.iterator().next();
for (;;) {
    try {
        output.print(language + "> ");
        String line = input.readLine();
        if (line == null) {
            break;
        } else if (languages.contains(line)) {
            language = line;
            continue;
        }
        Source source = Source.newBuilder(language, line, "<shell>")
                        .interactive(true).buildLiteral();
        context.eval(source);
    } catch (PolyglotException t) {
        if(t.isExit()) {
            break;
        }
        t.printStackTrace();
    }
}
```

## Step Through with Execution Listeners

The GraalVM Polyglot API allows users to instrument the execution of guest languages through the [ExecutionListener class](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/management/ExecutionListener.html).
For example, it lets you attach an execution listener that is invoked for every statement of the guest language program.
Execution listeners are designed as simple API for polyglot embedders and may become handy in, for example, single-stepping through the program.

```java
import org.graalvm.polyglot.*;
import org.graalvm.polyglot.management.*;

public class ExecutionListenerTest {
    public static void main(String[] args) {
        try (Context context = Context.create("js")) {
            ExecutionListener listener = ExecutionListener.newBuilder()
                      .onEnter((e) -> System.out.println(
                              e.getLocation().getCharacters()))
                      .statements(true)
                      .attach(context.getEngine());
            context.eval("js", "for (var i = 0; i < 2; i++);");
            listener.close();
        }
    }
}
```

In this code:
- The `Context.create()` call creates a new context for the guest language.
- Create an execution listener builder by invoking `ExecutionListeners.newBuilder()`.
- Set `onEnter` event to notify when element's execution is entered and consumed. At least one event consumer and one filtered source element needs to be enabled.
- To complete the listener attachment, `attach()` needs to be invoked.
- The `statements(true)` filters execution listeners to statements only.
- The `context.eval()` call evaluates a specified snippet of guest language code.
- The `listener.close()` closes a listener earlier, however execution listeners are automatically closed with the engine.

## Uber JAR File Creation

Uber JARs are JAR files that bundle all dependencies into a single archive for easier distribution.
However, creating an Uber JAR is not recommended for Graal languages because it breaks module descriptors, file integrity metadata, and JAR signature information.
Uber JARs are only supported on HotSpot and are not supported for native image generation, as the Native Image tool requires intact Java module descriptors.

If you must use Uber JARs, use the minimal configuration below and verify that it is still up to date whenever you upgrade.

You can find a working example of valid Maven Shade and Assembly plugin configurations in the [polyglot embedding example](https://github.com/graalvm/polyglot-embedding-demo?tab=readme-ov-file#maven-usage).
See the `shade` and `assembly` profiles in [_pom.xml_](https://github.com/graalvm/polyglot-embedding-demo/blob/main/pom.xml#L384).

### Maven Shade Plugin

If you intend to use the Maven Shade plugin, include at least the following transformers and filter configuration:

```xml
<profile>
    <id>shade</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <transformers>
                        <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                        <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                            <mainClass>org.example.embedding.Main</mainClass>
                            <manifestEntries>
                                <Multi-Release>true</Multi-Release>
                            </manifestEntries>
                        </transformer>
                    </transformers>
                    <filters>
                    	  <!-- Filters JAR signature files -->
                        <filter>
                            <artifact>*:*:*:*</artifact>
                            <excludes>
                                <exclude>META-INF/*.SF</exclude>
                                <exclude>META-INF/*.DSA</exclude>
                                <exclude>META-INF/*.RSA</exclude>
                            </excludes>
                        </filter>
                    </filters>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

### Maven Assembly plugin

If you are using the Maven Assembly plugin, you may apply the following configuration:

```xml
<profile>
    <id>assembly</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-assembly-plugin</artifactId>
                <version>3.6.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>single</goal>
                        </goals>
                        <configuration>
                            <archive>
                                <manifest>
                                    <mainClass>org.example.embedding.Main</mainClass>
                                </manifest>
                                <manifestEntries>
                                    <Multi-Release>true</Multi-Release>
                                </manifestEntries>
                            </archive>
                            <descriptors>
                                <descriptor>assembly.xml</descriptor>
                            </descriptors>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</profile>
```
with the corresponding `assembly.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<assembly xmlns="http://maven.apache.org/ASSEMBLY/2.2.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/ASSEMBLY/2.2.0 http://maven.apache.org/xsd/assembly-2.2.0.xsd">
    <id>jar-with-dependencies</id>
    <formats>
        <format>jar</format>
    </formats>
    <includeBaseDirectory>false</includeBaseDirectory>
    <dependencySets>
        <dependencySet>
            <outputDirectory>/</outputDirectory>
            <useProjectArtifact>true</useProjectArtifact>
            <unpack>true</unpack>
            <scope>runtime</scope>
        </dependencySet>
    </dependencySets>
    <containerDescriptorHandlers>
        <containerDescriptorHandler>
            <handlerName>metaInf-services</handlerName>
        </containerDescriptorHandler>
    </containerDescriptorHandlers>
</assembly>
```

## Compatibility with JSR-223 ScriptEngine

<!--

IMPORTANT!!

Whenever you change ANYTHING here, check if you need to reflect the changes
back into our integration tests at:
* tests/python/PythonEngineFactory.java
* https://github.com/oracle/truffleruby/blob/master/src/test-embedding/java/org/truffleruby/test/embedding/TruffleRubyEngineFactory.java

-->

The Truffle language implementation framework does not provide a JSR-223 ScriptEngine implementation.
The Polyglot API provides more fine-grained control over Truffle features and we strongly encourage users to use the `org.graalvm.polyglot.Context` interface in order to control many of the settings directly and benefit from finer-grained security settings in GraalVM.

However, to easily evaluate a Truffle language as a replacement for other scripting languages that are integrated using the ScriptEngine API, we provide a single file script engine below.
This file can be dropped into a source tree and used directly to evaluate a Truffle language via the ScriptEngine APIs.
There are only two lines to adapt to your project:

```java
public final class CHANGE_NAME_EngineFactory implements ScriptEngineFactory {
    private static final String LANGUAGE_ID = "<<INSERT LANGUAGE ID HERE>>";
    }
```

Rename the class as desired and change the `LANGUAGE_ID` to the desired Truffle language (for example, "python" for GraalPy or "js" for GraalJS).
To use it, include a `META-INF/services/javax.script.ScriptEngineFactory` file in your resources with the chosen class name.
This will allow the default `javax.script.ScriptEngineManager` to discover the language automatically.
Alternatively, the factory can be registered via `javax.script.ScriptEngineManager#registerEngineName` or instantiated and used directly.

The best practice is to close the `ScriptEngine` when no longer used rather than relying on finalizers.
To close it, use `((AutoCloseable) scriptEngine).close();` since `ScriptEngine` does not have a `close()` method.

Note that [GraalJS](https://www.graalvm.org/reference-manual/js/) provides [a ScriptEngine implementation](https://www.graalvm.org/reference-manual/js/ScriptEngine/) for users migrating from the Nashorn JavaScript engine that was deprecated in JDK 11, so this method here is not needed.

<details>
<summary>Expand to see the <code>ScriptEngineFactory</code> implementation for Truffle languages in a single file.</summary>

<pre class="language-java"><code>
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

import org.graalvm.home.Version;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

public final class CHANGE_NAME_EngineFactory implements ScriptEngineFactory {
    private static final String LANGUAGE_ID = "<<INSERT LANGUAGE ID HERE>>";

    /***********************************************************/
    /* Everything below is generic and does not need to change */
    /***********************************************************/

    private final Engine polyglotEngine = Engine.newBuilder().build();
    private final Language language = polyglotEngine.getLanguages().get(LANGUAGE_ID);

    @Override
    public String getEngineName() {
        return language.getImplementationName();
    }

    @Override
    public String getEngineVersion() {
        return Version.getCurrent().toString();
    }

    @Override
    public List<String> getExtensions() {
        return List.of(LANGUAGE_ID);
    }

    @Override
    public List<String> getMimeTypes() {
        return List.copyOf(language.getMimeTypes());
    }

    @Override
    public List<String> getNames() {
        return List.of(language.getName(), LANGUAGE_ID, language.getImplementationName());
    }

    @Override
    public String getLanguageName() {
        return language.getName();
    }

    @Override
    public String getLanguageVersion() {
        return language.getVersion();
    }

    @Override
    public Object getParameter(final String key) {
        switch (key) {
            case ScriptEngine.ENGINE:
                return getEngineName();
            case ScriptEngine.ENGINE_VERSION:
                return getEngineVersion();
            case ScriptEngine.LANGUAGE:
                return getLanguageName();
            case ScriptEngine.LANGUAGE_VERSION:
                return getLanguageVersion();
            case ScriptEngine.NAME:
                return LANGUAGE_ID;
        }
        return null;
    }

    @Override
    public String getMethodCallSyntax(final String obj, final String m, final String... args) {
        throw new UnsupportedOperationException("Unimplemented method 'getMethodCallSyntax'");
    }

    @Override
    public String getOutputStatement(final String toDisplay) {
        throw new UnsupportedOperationException("Unimplemented method 'getOutputStatement'");
    }

    @Override
    public String getProgram(final String... statements) {
        throw new UnsupportedOperationException("Unimplemented method 'getProgram'");
    }

    @Override
    public ScriptEngine getScriptEngine() {
        return new PolyglotEngine(this);
    }

    private static final class PolyglotEngine implements ScriptEngine, Compilable, Invocable, AutoCloseable {
        private final ScriptEngineFactory factory;
        private PolyglotContext defaultContext;

        PolyglotEngine(ScriptEngineFactory factory) {
            this.factory = factory;
            this.defaultContext = new PolyglotContext(factory);
        }

        @Override
        public void close() {
            defaultContext.getContext().close();
        }

        @Override
        public CompiledScript compile(String script) throws ScriptException {
            Source src = Source.create(LANGUAGE_ID, script);
            try {
                defaultContext.getContext().parse(src); // only for the side-effect of validating the source
            } catch (PolyglotException e) {
                throw new ScriptException(e);
            }
            return new PolyglotCompiledScript(src, this);
        }

        @Override
        public CompiledScript compile(Reader script) throws ScriptException {
            Source src;
            try {
                src = Source.newBuilder(LANGUAGE_ID, script, "sourcefromreader").build();
                defaultContext.getContext().parse(src); // only for the side-effect of validating the source
            } catch (PolyglotException | IOException e) {
                throw new ScriptException(e);
            }
            return new PolyglotCompiledScript(src, this);
        }

        @Override
        public Object eval(String script, ScriptContext context) throws ScriptException {
            if (context instanceof PolyglotContext) {
                PolyglotContext c = (PolyglotContext) context;
                try {
                    return c.getContext().eval(LANGUAGE_ID, script).as(Object.class);
                } catch (PolyglotException e) {
                    throw new ScriptException(e);
                }
            } else {
                throw new ClassCastException("invalid context");
            }
        }

        @Override
        public Object eval(Reader reader, ScriptContext context) throws ScriptException {
            Source src;
            try {
                src = Source.newBuilder(LANGUAGE_ID, reader, "sourcefromreader").build();
            } catch (IOException e) {
                throw new ScriptException(e);
            }
            if (context instanceof PolyglotContext) {
                PolyglotContext c = (PolyglotContext) context;
                try {
                    return c.getContext().eval(src).as(Object.class);
                } catch (PolyglotException e) {
                    throw new ScriptException(e);
                }
            } else {
                throw new ScriptException("invalid context");
            }
        }

        @Override
        public Object eval(String script) throws ScriptException {
            return eval(script, defaultContext);
        }

        @Override
        public Object eval(Reader reader) throws ScriptException {
            return eval(reader, defaultContext);
        }

        @Override
        public Object eval(String script, Bindings n) throws ScriptException {
            throw new UnsupportedOperationException("Bindings for Polyglot language cannot be created explicitly");
        }

        @Override
        public Object eval(Reader reader, Bindings n) throws ScriptException {
            throw new UnsupportedOperationException("Bindings for Polyglot language cannot be created explicitly");
        }

        @Override
        public void put(String key, Object value) {
            defaultContext.getBindings(ScriptContext.ENGINE_SCOPE).put(key, value);
        }

        @Override
        public Object get(String key) {
            return defaultContext.getBindings(ScriptContext.ENGINE_SCOPE).get(key);
        }

        @Override
        public Bindings getBindings(int scope) {
            return defaultContext.getBindings(scope);
        }

        @Override
        public void setBindings(Bindings bindings, int scope) {
            defaultContext.setBindings(bindings, scope);
        }

        @Override
        public Bindings createBindings() {
            throw new UnsupportedOperationException("Bindings for Polyglot language cannot be created explicitly");
        }

        @Override
        public ScriptContext getContext() {
            return defaultContext;
        }

        @Override
        public void setContext(ScriptContext context) {
            throw new UnsupportedOperationException("The context of a Polyglot ScriptEngine cannot be modified.");
        }

        @Override
        public ScriptEngineFactory getFactory() {
            return factory;
        }

        @Override
        public Object invokeMethod(Object thiz, String name, Object... args)
                throws ScriptException, NoSuchMethodException {
            try {
                Value receiver = defaultContext.getContext().asValue(thiz);
                if (receiver.canInvokeMember(name)) {
                    return receiver.invokeMember(name, args).as(Object.class);
                } else {
                    throw new NoSuchMethodException(name);
                }
            } catch (PolyglotException e) {
                throw new ScriptException(e);
            }
        }

        @Override
        public Object invokeFunction(String name, Object... args) throws ScriptException, NoSuchMethodException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getInterface(Class<T> interfaceClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getInterface(Object thiz, Class<T> interfaceClass) {
            return defaultContext.getContext().asValue(thiz).as(interfaceClass);
        }
    }

    private static final class PolyglotContext implements ScriptContext {
        private Context context;
        private final ScriptEngineFactory factory;
        private final PolyglotReader in;
        private final PolyglotWriter out;
        private final PolyglotWriter err;
        private Bindings globalBindings;

        PolyglotContext(ScriptEngineFactory factory) {
            this.factory = factory;
            this.in = new PolyglotReader(new InputStreamReader(System.in));
            this.out = new PolyglotWriter(new OutputStreamWriter(System.out));
            this.err = new PolyglotWriter(new OutputStreamWriter(System.err));
        }

        Context getContext() {
            if (context == null) {
                Context.Builder builder = Context.newBuilder(LANGUAGE_ID)
                        .in(this.in)
                        .out(this.out)
                        .err(this.err)
                        .allowAllAccess(true);
                Bindings globalBindings = getBindings(ScriptContext.GLOBAL_SCOPE);
                if (globalBindings != null) {
                    for (Entry<String, Object> entry : globalBindings.entrySet()) {
                        Object value = entry.getValue();
                        if (value instanceof String) {
                            builder.option(entry.getKey(), (String) value);
                        }
                    }
                }
                context = builder.build();
            }
            return context;
        }

        @Override
        public void setBindings(Bindings bindings, int scope) {
            if (scope == ScriptContext.GLOBAL_SCOPE) {
                if (context == null) {
                    globalBindings = bindings;
                } else {
                    throw new UnsupportedOperationException(
                            "Global bindings for Polyglot language can only be set before the context is initialized.");
                }
            } else {
                throw new UnsupportedOperationException("Bindings objects for Polyglot language is final.");
            }
        }

        @Override
        public Bindings getBindings(int scope) {
            if (scope == ScriptContext.ENGINE_SCOPE) {
                return new PolyglotBindings(getContext().getBindings(LANGUAGE_ID));
            } else if (scope == ScriptContext.GLOBAL_SCOPE) {
                return globalBindings;
            } else {
                return null;
            }
        }

        @Override
        public void setAttribute(String name, Object value, int scope) {
            if (scope == ScriptContext.ENGINE_SCOPE) {
                getBindings(scope).put(name, value);
            } else if (scope == ScriptContext.GLOBAL_SCOPE) {
                if (context == null) {
                    globalBindings.put(name, value);
                } else {
                    throw new IllegalStateException("Cannot modify global bindings after context creation.");
                }
            }
        }

        @Override
        public Object getAttribute(String name, int scope) {
            if (scope == ScriptContext.ENGINE_SCOPE) {
                return getBindings(scope).get(name);
            } else if (scope == ScriptContext.GLOBAL_SCOPE) {
                return globalBindings.get(name);
            }
            return null;
        }

        @Override
        public Object removeAttribute(String name, int scope) {
            Object prev = getAttribute(name, scope);
            if (prev != null) {
                if (scope == ScriptContext.ENGINE_SCOPE) {
                    getBindings(scope).remove(name);
                } else if (scope == ScriptContext.GLOBAL_SCOPE) {
                    if (context == null) {
                        globalBindings.remove(name);
                    } else {
                        throw new IllegalStateException("Cannot modify global bindings after context creation.");
                    }
                }
            }
            return prev;
        }

        @Override
        public Object getAttribute(String name) {
            return getAttribute(name, ScriptContext.ENGINE_SCOPE);
        }

        @Override
        public int getAttributesScope(String name) {
            if (getAttribute(name, ScriptContext.ENGINE_SCOPE) != null) {
                return ScriptContext.ENGINE_SCOPE;
            } else if (getAttribute(name, ScriptContext.GLOBAL_SCOPE) != null) {
                return ScriptContext.GLOBAL_SCOPE;
            }
            return -1;
        }

        @Override
        public Writer getWriter() {
            return this.out.writer;
        }

        @Override
        public Writer getErrorWriter() {
            return this.err.writer;
        }

        @Override
        public void setWriter(Writer writer) {
            this.out.writer = writer;
        }

        @Override
        public void setErrorWriter(Writer writer) {
            this.err.writer = writer;
        }

        @Override
        public Reader getReader() {
            return this.in.reader;
        }

        @Override
        public void setReader(Reader reader) {
            this.in.reader = reader;
        }

        @Override
        public List<Integer> getScopes() {
            return List.of(ScriptContext.ENGINE_SCOPE, ScriptContext.GLOBAL_SCOPE);
        }

        private static final class PolyglotReader extends InputStream {
            private volatile Reader reader;

            public PolyglotReader(InputStreamReader inputStreamReader) {
                this.reader = inputStreamReader;
            }

            @Override
            public int read() throws IOException {
                return reader.read();
            }
        }

        private static final class PolyglotWriter extends OutputStream {
            private volatile Writer writer;

            public PolyglotWriter(OutputStreamWriter outputStreamWriter) {
                this.writer = outputStreamWriter;
            }

            @Override
            public void write(int b) throws IOException {
                writer.write(b);
            }
        }
    }

    private static final class PolyglotCompiledScript extends CompiledScript {
        private final Source source;
        private final ScriptEngine engine;

        public PolyglotCompiledScript(Source src, ScriptEngine engine) {
            this.source = src;
            this.engine = engine;
        }

        @Override
        public Object eval(ScriptContext context) throws ScriptException {
            if (context instanceof PolyglotContext) {
                return ((PolyglotContext) context).getContext().eval(source).as(Object.class);
            }
            throw new UnsupportedOperationException(
                    "Polyglot CompiledScript instances can only be evaluated in Polyglot.");
        }

        @Override
        public ScriptEngine getEngine() {
            return engine;
        }
    }

    private static final class PolyglotBindings implements Bindings {
        private Value languageBindings;

        PolyglotBindings(Value languageBindings) {
            this.languageBindings = languageBindings;
        }

        @Override
        public int size() {
            return keySet().size();
        }

        @Override
        public boolean isEmpty() {
            return size() == 0;
        }

        @Override
        public boolean containsValue(Object value) {
            for (String s : keySet()) {
                if (get(s) == value) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void clear() {
            for (String s : keySet()) {
                remove(s);
            }
        }

        @Override
        public Set<String> keySet() {
            return languageBindings.getMemberKeys();
        }

        @Override
        public Collection<Object> values() {
            List<Object> values = new ArrayList<>();
            for (String s : keySet()) {
                values.add(get(s));
            }
            return values;
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            Set<Entry<String, Object>> values = new HashSet<>();
            for (String s : keySet()) {
                values.add(new Entry<String, Object>() {
                    @Override
                    public String getKey() {
                        return s;
                    }

                    @Override
                    public Object getValue() {
                        return get(s);
                    }

                    @Override
                    public Object setValue(Object value) {
                        return put(s, value);
                    }
                });
            }
            return values;
        }

        @Override
        public Object put(String name, Object value) {
            Object previous = get(name);
            languageBindings.putMember(name, value);
            return previous;
        }

        @Override
        public void putAll(Map<? extends String, ? extends Object> toMerge) {
            for (Entry<? extends String, ? extends Object> e : toMerge.entrySet()) {
                put(e.getKey(), e.getValue());
            }
        }

        @Override
        public boolean containsKey(Object key) {
            if (key instanceof String) {
                return languageBindings.hasMember((String) key);
            } else {
                return false;
            }
        }

        @Override
        public Object get(Object key) {
            if (key instanceof String) {
                Value value = languageBindings.getMember((String) key);
                if (value != null) {
                    return value.as(Object.class);
                }
            }
            return null;
        }

        @Override
        public Object remove(Object key) {
            Object prev = get(key);
            if (prev != null) {
                languageBindings.removeMember((String) key);
                return prev;
            } else {
                return null;
            }
        }
    }
}
</code></pre>
</details>
