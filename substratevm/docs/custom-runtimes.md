# Building a Custom Runtime With Embedded Java Libraries

You can build a GraalVM JDK that embeds Java libraries in a Native Image-built JVM library and loads application classes at run time.
This guide uses [picocli](https://picocli.info/), a command-line parsing library with no transitive dependencies, to demonstrate the boundary between embedded library code and a run-time-loaded application.
The application does not need to exist when you build the runtime.

The example builds _libjvm.so_ with picocli, then compiles a Java application that uses picocli annotations.
At run time, the embedded library reads the application's annotations, sets an option field reflectively, and invokes the application's `run()` method.
The application's run-time class path contains no picocli JAR.

> Note: This is an experimental developer workflow using [run-time class loading (Crema)](runtime-class-loading.md), not a complete production runtime configuration.
> The example was verified on Linux AMD64 with picocli 4.7.7.
> It builds one JVM shared library; it does not create or consume Native Image layers.

## Prerequisites

- A GraalVM source checkout with `mx` and the native build dependencies configured.
  Follow the [compiler development setup](../../compiler/README.md) to select a compatible build JDK.
- `JAVA_HOME` pointing to that JDK, with its `javac` available.
- Maven (`mvn`) to download picocli.
- Bash for the commands below.

Run the commands in the same shell so that the exported variables remain available.
Set `GRAAL_REPO` to the absolute path of your source checkout:

```shell
export GRAAL_REPO=/absolute/path/to/graal
```

## Build the Runtime

Download picocli and record the location of its JAR:

```shell
mvn dependency:get -Dartifact=info.picocli:picocli:4.7.7
export PICOCLI_JAR="$HOME/.m2/repository/info/picocli/picocli/4.7.7/picocli-4.7.7.jar"
```

If you configured a different Maven local repository, adjust `PICOCLI_JAR` accordingly.

Build from the _vm_ suite:

```shell
cd "$GRAAL_REPO/vm"
export LIBJVM_IMAGE_AS_DEFAULT=true

mx --dy /substratevm \
  --components=svmjava,svmjavad \
  --native-images=lib:jvm \
  --extra-image-builder-argument=jvm:-cp \
  --extra-image-builder-argument="jvm:$PICOCLI_JAR" \
  --extra-image-builder-argument=jvm:-H:+UnlockExperimentalVMOptions \
  --extra-image-builder-argument='jvm:-H:Preserve=package=picocli.*' \
  --extra-image-builder-argument=jvm:-H:-AssertInitializationSpecifiedForAllClasses \
  --extra-image-builder-argument=jvm:-H:-UnlockExperimentalVMOptions \
  build --build-logs=silent
```

The options serve the following purposes:

- `--components=svmjava,svmjavad` selects the Native Image-built JVM library and the component that makes it the default VM.
  `LIBJVM_IMAGE_AS_DEFAULT=true` enables the latter component.
  The resulting `java` launcher uses this library without requiring `-svm`; HotSpot remains installed alongside it.
- `--native-images=lib:jvm` selects the JVM library image build.
- `--extra-image-builder-argument=jvm:...` passes an argument to that image build.
  The prefix here is `jvm:`, not `lib:jvm:`.
  Pass `-cp` and its value as separate arguments.
- `-H:Preserve=package=picocli.*` preserves picocli's classes and members for use by code loaded at run time, including subpackages.
  Putting a library on the image-build class path alone does not preserve all of its methods and fields.
- `-H:-AssertInitializationSpecifiedForAllClasses` relaxes the JVM library build's assertion that every class has an explicit initialization policy.
  It does not force picocli to initialize at build time.
  Review initialization policies before adapting this proof of concept for production.

Do not add the application to this build's class path or preservation selectors.

After the build finishes, retrieve the runtime location from the same suite and with the same component selection:

```shell
export GRAALVM_HOME="$(mx --dy /substratevm \
  --components=svmjava,svmjavad \
  --native-images=lib:jvm \
  graalvm-home)"

"$GRAALVM_HOME/bin/java" -version
```

The version output should identify `Substrate 64-Bit Server VM`.
Keep `LIBJVM_IMAGE_AS_DEFAULT=true` set when querying the home.
The extra image-builder arguments do not need to be repeated for this path query.

## Compile an Application Afterward

Create a separate application directory after the runtime build:

```shell
export PICOCLI_DEMO_DIR="$(mktemp -d)"
cd "$PICOCLI_DEMO_DIR"
```

Save the following source as _Hello.java_ in that directory:

```java
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "hello", mixinStandardHelpOptions = true)
public class Hello implements Runnable {
    @Option(names = "--name", defaultValue = "World")
    private String name;

    @Override
    public void run() {
        System.out.println("Hello " + name);
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Hello()).execute(args));
    }
}
```

Compile with your build JDK, using picocli on the compile-time class path:

```shell
"$JAVA_HOME/bin/javac" -proc:none -cp "$PICOCLI_JAR" Hello.java
```

`-proc:none` explicitly disables annotation processing.
This example needs no picocli annotation processor or application-specific Native Image reflection configuration: the embedded library inspects the run-time-loaded class's annotations at run time.
The option is optional when only the standard picocli JAR is on the compiler class path.

## Run and Verify

Run the application with the custom runtime and only the application directory on the class path:

```shell
"$GRAALVM_HOME/bin/java" -cp . Hello --name World
```

Expected output:

```text
Hello World
```

There is no picocli JAR on this class path and no `-svm` argument.
You do not need to rebuild the runtime after compiling or changing the application.

Check help and invalid-option handling:

```shell
"$GRAALVM_HOME/bin/java" -cp . Hello --help
"$GRAALVM_HOME/bin/java" -cp . Hello --unknown
```

The first command prints usage information and exits with status 0.
The second reports an unknown option and exits with status 2.

As a control, run the application on HotSpot without the picocli JAR:

```shell
"$GRAALVM_HOME/bin/java" -server -cp . Hello --name World
```

This command fails with `NoClassDefFoundError: picocli/CommandLine`.
Adding the picocli JAR makes the HotSpot control work:

```shell
"$GRAALVM_HOME/bin/java" -server -cp ".:$PICOCLI_JAR" Hello --name World
```

## Adapt the Example

For another library, replace the image-build class path and preservation selectors with the library's JARs and packages.
Add any dependencies needed by the preserved code.
Test an application that is absent from the image build, with the embedded library omitted from the run-time class path.

Preservation can make optional code paths reachable and expose dependencies that a smaller application does not otherwise need.
Other libraries can also require resources, service metadata, or explicit class-initialization policies.
Do not assume that the picocli result establishes compatibility with an arbitrary framework.

Classes already included in the image cannot be reloaded to recover methods or fields removed during image building.
Review the [run-time class loading limitations](runtime-class-loading.md#current-limitations) when choosing preservation selectors.
Changing the embedded library or its preserved API requires rebuilding the runtime.
