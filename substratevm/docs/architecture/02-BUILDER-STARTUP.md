# Builder Startup

## Purpose

The builder startup phase runs inside the builder JVM started by the driver. It installs the Native
Image class-loader environment, parses hosted options, validates early platform assumptions, loads
application classes, chooses the image kind, resolves Java and C entry points, and creates the
[`NativeImageGenerator`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java).

This phase bridges the external driver world and the hosted image-builder lifecycle. It ends when
[`NativeImageGenerator.run(...)`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java) is called.

## Inputs and Entry State

The builder receives:

- builder JVM system properties and module settings from the driver;
- an image-builder argument file containing hosted/runtime options and image classpath/module-path;
- the image classpath and image module path extracted by [`NativeImageGeneratorRunner`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGeneratorRunner.java);
- a keep-alive file that lets the builder detect driver death;
- hosted option descriptors visible to the builder class loader;
- optional service provider override for [`NativeImageGeneratorRunnerProvider`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGeneratorRunnerProvider.java).

The builder JVM has started, but the Native Image class-loader environment is not yet installed and
application classes have not yet been loaded through `ImageClassLoader`.

## Results and Completion States

The phase produces:

- an installed [`NativeImageSystemClassLoader`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageSystemClassLoader.java) delegating to the Native Image class loader;
- a [`NativeImageClassLoaderSupport`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageClassLoaderSupport.java) instance with parsed hosted options, remaining arguments, and
  class-loader support services;
- an [`ImageClassLoader`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/ImageClassLoader.java) for application, platform, module, and builder class discovery;
- validated image kind: executable, static executable, shared library, or image layer;
- resolved Java main support and native entry-point metadata;
- a configured [`NativeImageGenerator`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java) ready to run hosted setup.

Hosted options, image kind, and entry points are resolved and stable for later phases.

## Main Classes

Core anchors:

- CE [`NativeImageGeneratorRunner`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGeneratorRunner.java) owns the
  builder JVM entry point, class-loader installation, image-kind selection, and generator creation.
- CE [`NativeImageGenerator`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java) is created at
  the end of this phase and receives the resolved build inputs.

## Control Flow

The normal builder startup flow is:

1. `NativeImageGeneratorRunner.main(...)` checks for a
   [`NativeImageGeneratorRunnerProvider`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGeneratorRunnerProvider.java). If none is present, it creates the default runner and calls
   `start(...)`.

2. `start(...)` expands the image-builder argument file, extracts image classpath and module-path
   entries, and installs a keep-alive timer when the driver provided a keep-alive file.

3. `installNativeImageClassLoader(...)` creates [`NativeImageClassLoaderSupport`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageClassLoaderSupport.java) over the image
   classpath and module path. It sets up the hosted option parser and, for isolated guest mode,
   configures guest VM access.

4. The builder plants the [`GuestAccess`](../../src/com.oracle.svm.util/src/com/oracle/svm/util/GuestAccess.java) configuration, sets up the libgraal class loader, runs
   [`NativeImageClassLoaderPostProcessing`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageClassLoaderPostProcessing.java) services, and sets the current thread context class
   loader to the Native Image class loader.

5. [`NativeImageSystemClassLoader`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageSystemClassLoader.java) is configured to delegate to the Native Image class loader. This
   lets system-class-loader lookups during image building resolve classes and resources from the
   image classpath/module-path.

6. Early build-time system properties are installed before class iteration can trigger annotation or
   enum class initialization. Common-pool parallelism is configured and checked so worker threads
   use the expected context class loader.

7. `start(...)` verifies that no unrecognized builder arguments remain after hosted option parsing.
   It optionally checks boot-module dependencies.

8. `buildImage(...)` validates the Java version, operating system, and architecture, then loads all
   classes through `ImageClassLoader.loadAllClasses()`.

9. `buildImage(...)` reads hosted options such as image name, main class/module/method, shared
   library, static executable, and layer creation. It rejects incompatible image-kind combinations.

10. If a Java main entry point is present, the builder resolves it through [`GuestAccess`](../../src/com.oracle.svm.util/src/com/oracle/svm/util/GuestAccess.java), creates
    [`JavaMainSupport`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/JavaMainWrapper.java) when needed, verifies the main entry point, and creates [`MainEntryPoint`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/MainEntryPoint.java).

11. The runner creates a [`NativeImageGenerator`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java) and calls `generator.run(...)` with entry points,
    Java main support, image name, image kind, runtime option names, and timers.

## Key Data Structures

- [`NativeImageClassLoaderSupport`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageClassLoaderSupport.java): owns the Native Image class loader, hosted option parser,
  annotation extractor, class-loader list, parsed hosted options, and remaining arguments.
- [`NativeImageSystemClassLoader`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageSystemClassLoader.java): custom system class loader installed by the driver and pointed at
  the current Native Image class loader by the builder.
- [`ImageClassLoader`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/ImageClassLoader.java): phase-wide class discovery facade, including target platform, guest types,
  class-loader support, and class/module metadata.
- [`HostedOptionParser`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/option/HostedOptionParser.java): parses hosted options from builder arguments and option descriptors.
- [`OptionValues`](../../../compiler/src/jdk.graal.compiler.options/src/jdk/graal/compiler/options/OptionValues.java): immutable parsed hosted option values used to select image kind and configure
  later phases.
- [`NativeImageKind`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/image/AbstractImage.java): classifies the requested output as executable, static executable, shared
  library, or image layer.
- [`MainEntryPoint`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/MainEntryPoint.java), [`JavaMainSupport`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/JavaMainWrapper.java), and [`CEntryPointData`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/code/CEntryPointData.java): entry-point models passed into hosted
  setup.
- [`TimerCollection`](../../src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/util/TimerCollection.java) and [`ProgressReporter`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/ProgressReporter.java): build timing and user-visible progress state carried
  into the generator.
