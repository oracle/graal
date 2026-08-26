# Driver Setup

## Purpose

The driver functionality is realized via the `native-image` tool executable. This setup allows the
driver to compute the builder module path, JVM flags, system properties, and environment before the
classes of the image-builder are loaded in a freshly configured builder JVM. It turns command-line
arguments, `native-image.properties`, manifests, macro options, API options, environment inputs, and
GraalVM installation metadata into a builder JVM invocation.

This phase is responsible for:

- recognizing tool-level commands such as help, version, bundle handling, and diagnostic modes;
- collecting image classpath/module-path and builder module-path arguments;
- expanding `META-INF/native-image/native-image.properties` embedded in class- and module-path
  entries (passed via `-cp`, `-p`, and similar options), and macro options for each
  `--macro:<macro-name>` option;
- translating public API options into hosted/runtime builder options;
- preparing the arguments for the JVM that will run the hosted image builder;
- if `--bundle-create` or `--bundle-apply` is given, the driver rewires all paths to the
  bundle-root directory;
- starting the builder JVM with a compact argument file and an image-builder argument file.

## Inputs and Entry State

The driver receives:

- command-line arguments passed to `native-image`;
- the GraalVM installation layout, from which [`BuildConfiguration`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/NativeImage.java)
  derives the Java executable, builder module path, upgrade module path, and C-library paths;
- application classpath and module-path entries (as part of the command-line arguments);
- Native Image configuration metadata discovered under `META-INF/native-image` in class- and module-path entries,
  including `native-image.properties`, `reachability-metadata.json`, and legacy `*-config.json` files;
- jar manifest attributes such as `Main-Class`, `Class-Path`, `Add-Opens`, `Add-Exports`, and
  `Enable-Native-Access` as part of `META-INF` processing for given application classpath and module-path entries;
- macro options and public API options;
- builder JVM arguments supplied with `-J`, and arbitrary environment variables explicitly made available
  during image building with `-E<key>[=<value>]`;
- driver configuration supplied through `NATIVE_IMAGE_OPTIONS` or `NATIVE_IMAGE_CONFIG_FILE`;
- optional bundle/container configuration.

At entry, command-line arguments, embedded configuration, macro options, environment variables,
and manifest attributes are still raw inputs.
No hosted builder classes, application classes, or image classes have been loaded by the builder JVM.

## Results and Completion States

The phase produces:

- image-builder arguments, including raw `-H:` and `-R:` arguments and the image classpath/module path,
  entry-point information, target configuration, and metadata paths;
- builder JVM arguments, the builder's module path, add-modules/add-exports/add-opens, memory settings,
  system properties, common-pool setup, and custom system-class-loader setup;
- a keep-alive file used by the builder process to terminate when the driver disappears;
- temporary argument files for the JVM invocation and builder arguments;
- when `--bundle-create` is requested, a `.nib` archive containing the captured build inputs,
  arguments, environment, path mappings, container metadata, and bundle launcher  (a Java class within
  the `.nib`-file that allows it to be run like a jar-file);
- a `ProcessBuilder` command that starts the builder JVM;
- when the build succeeds and is not a dry run, the generated native executable or shared library
  and its auxiliary build artifacts; for bundle builds, these are copied from staging to the
  external bundle output directory;
- a driver exit status derived from the builder process exit status.

Driver-only commands and options are handled locally or translated into builder arguments.
The launched builder JVM performs the subsequent hosted phases; that work is outside driver setup.

## Main Classes

Core anchors:

- [`NativeImage`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/NativeImage.java) owns the external
  `native-image` launcher, option processing, builder JVM command creation, and process execution.
- [`APIOptionHandler`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/APIOptionHandler.java) maps public
  `native-image` options to builder arguments and validates option scope.
- [`DefaultOptionHandler`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/DefaultOptionHandler.java) handles
  built-in command-line options such as classpath, module path, module exports, and Java arguments.
- [`MacroOptionHandler`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/MacroOptionHandler.java) expands macro
  options and their associated property files into builder arguments.
- [`BundleSupport`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/BundleSupport.java) orchestrates
  `--bundle-create` and `--bundle-apply`, staging, argument and environment capture/replay,
  input/output substitution, archive creation, and post-builder finalization.

## Control Flow

The normal driver flow is:

1. [`NativeImage.main(...)`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/NativeImage.java) creates a [`BuildConfiguration`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/NativeImage.java) from raw command-line arguments and calls
   `performBuild(...)`.

2. `build(...)` handles immediate tool commands such as `--help`, `--help-extra`, and `--version`.
   If the invocation is a real image build, it creates a [`NativeImage`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/NativeImage.java) instance.

3. `prepareImageBuildArgs(...)` appends the default builder JVM arguments and initial image classpath entries to the
   driver’s argument lists. These are later used to construct the `java` invocation that starts the image builder:
   - stack size and default memory flags;
   - GraalVM vendor/version properties;
   - build-time [`ImageInfo`](../../../sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/ImageInfo.java) property;
   - custom system class loader and common-pool handler/thread factory;
   - `-Xshare:off`;
   - image classpath entries from the build configuration.
     Each classpath entry processes any manifest `Class-Path` entries when it is added, whether
     during this preparation or later argument processing.

4. `completeImageBuild(...)` completes driver-side preparation and calls `buildImage(...)`:
   1. It processes user and property-file arguments through `processNativeImageArgs(...)` and
      [`NativeImageArgsProcessor`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/NativeImage.java).
      `processNativeImageArgs(...)` creates the argument processor and invokes `apply(false)`.
      Argument processing occurs in two stages:
      - [`CmdLineOptionHandler`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/CmdLineOptionHandler.java)
        consumes command-line options that affect driver state, including bundle options, and
        delegates to
        [`BundleSupport.create(...)`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/BundleSupport.java).
        - `--bundle-apply` expands the archive, restores arguments, environment variables, and path
          mappings, rewrites paths for the current host, and injects the stored arguments back into
          the argument queue.
        - `--bundle-create` initializes staging and records arguments, environment variables,
          canonicalizations, substitutions, inputs, and auxiliary outputs.
      - The registered API, default, and macro option handlers consume the remaining arguments and
        update builder arguments or builder JVM settings.
   2. It validates experimental-option usage and adds builder and upgrade module paths before
      consolidating option arguments, applying target arguments, selecting the default libc, and
      preparing C-library search paths.
   3. It prepares path and default state by adding the current directory when no classpath or module
      path is provided and by adding custom image classpath entries.
   4. It adds deterministic builder JVM flags for AOT, user-supplied JVM arguments, and heuristic
      memory settings.
   5. It assembles final builder and image arguments, validates the entry point and image name,
      resolves image output paths, processes provided JAR metadata, and configures module options.
   6. It invokes `buildImage(...)` with the final builder JVM arguments, module path, image
      arguments, classpath, and module path.

5. `buildImage(...)` constructs the final builder JVM invocation:
   - substitutes image, classpath, module-path, and auxiliary paths for bundle builds;
   - computes implicitly required system modules for the application module path and adds them to
     the builder module configuration;
   - for containerized image builds it rewrites arguments to container paths, creates the default
     Dockerfile when needed, builds or reuses the Dockerfile-derived container image, and constructs
     the container-tool invocation with the required environment variables and bind mounts;
   - writes VM arguments and builder arguments to temporary argument files;
   - adds the generator main class and keep-alive file argument;
   - prepares the environment and launches the process.

6. While the builder runs, the driver transforms bundle-relative output and waits for process
    completion.

7. Afterwards,
    [`BundleSupport.complete()`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/BundleSupport.java)
    copies staged outputs to their external destination and writes or updates the bundle archive,
    including the launcher, metadata, arguments, environment, path maps, and container
    configuration.

8. The driver interprets the builder exit status.
    [`REBUILD_AFTER_ANALYSIS`](../../src/com.oracle.svm.core/src/com/oracle/svm/core/util/ExitStatus.java)
    recursively starts a second build with the rebuild marker set.
    Builder errors, out-of-memory status, and unexpected status values are reported by the driver.

## Key Data Structures

- [`BuildConfiguration`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/NativeImage.java): immutable configuration view for the launcher, including raw build
  arguments, Java home/executable, builder module paths, C-library paths, temporary directories, and
  host flag policy.
- [`NativeImageArgsProcessor`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/NativeImage.java): two-stage argument queue that lets command-line handlers consume
  driver options first, then lets registered option handlers translate remaining arguments into
  builder arguments.
- [`ArgumentQueue`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/NativeImage.java) and [`ArgumentEntry`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/NativeImage.java): preserve argument order and option origin while handlers
  consume, requeue, or transform options.
- [`APIOptionHandler.OptionInfo`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/APIOptionHandler.java): metadata for public API options extracted from hosted option
  descriptors and used to transform user-facing options into builder arguments.
- `imageBuilderJavaArgs`: JVM arguments for the builder process.
- `imageBuilderArgs`: hosted/runtime image-builder arguments passed to
  [`NativeImageGeneratorRunner`](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGeneratorRunner.java).
- `imageClasspath` and `imageModulePath`: application inputs that the builder will load through the
  Native Image class loader.
- [`BundlePathMap`](../../src/com.oracle.svm.driver/src/com/oracle/svm/driver/BundlePathMap.java): optional path
  rewriting and container support used when a build is recorded or replayed as a bundle.
- `ProcessBuilder`: final process launch model for the builder JVM.
