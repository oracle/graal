---
layout: docs
toc_group: examples
link_title: Native Image Examples
permalink: /examples/native-image-examples/
---

# Ahead-of-Time Compilation of Java and Polyglot Applications

Below are sample applications illustrating GraalVM's unique capabilities to create self-contained executable images which can run incredibly fast.
Here you will also find a more sophisticated example displaying GraalVM's ability to create polyglot native executables.

## Preparation

1&#46; Download or clone the repository and navigate into the `native-list-dir` directory:
  ```shell
  git clone https://github.com/graalvm/graalvm-demos
  cd graalvm-demos/native-list-dir
  ```
  There are two Java classes, but you will start by building `ListDir.java` for the purposes of this demo.
  You can manually execute `javac ListDir.java`, and there is also a `build.sh` script included for your convenience.

  Note that you can use any JDK for compiling the Java classes.
  However, we refer to `javac` from GraalVM in the build script to simplify the prerequisites so another JDK does not need to be installed.

2&#46; [Download GraalVM](https://www.graalvm.org/downloads/), unzip the archive, export the GraalVM home directory as the `$JAVA_HOME` and add `$JAVA_HOME/bin` to the `PATH` environment variable:
  On Linux:
  ```bash
  export JAVA_HOME=/home/${current_user}/path/to/graalvm
  export PATH=$JAVA_HOME/bin:$PATH
  ```
  On macOS:
  ```bash
  export JAVA_HOME=/Users/${current_user}/path/to/graalvm/Contents/Home
  export PATH=$JAVA_HOME/bin:$PATH
  ```
  On Windows:
  ```bash
  setx /M JAVA_HOME "C:\Progra~1\Java\<graalvm>"
  setx /M PATH "C:\Progra~1\Java\<graalvm>\bin;%PATH%"
  ```
  Note that your paths are likely to be different depending on the download location.

3&#46; [Install Native Image](../reference-manual/native-image/README.md/#install-native-image) by running.
  ```bash
  gu install native-image
  ```

4&#46; Then execute:
  ```shell
  ./build.sh
  ```

The `build.sh` script creates a native executable from the Java class.

Look at it in more detail:
```shell
$JAVA_HOME/bin/native-image ListDir
```
The `native-image` utility ahead-of-time compiles the `ListDir` class into a standalone binary in the current working directory.
After running the command, the executable file `listdir` should have been produced.

## Running the Application

To run the application, you need to either execute the `ListDir` class as a normal Java application using `java`, or since we have a native executable prepared, run that directly.

The `run.sh` file executes both, and times them with the `time` utility:
```shell
time java ListDir $1
time ./listdir $1
```

To make it more interesting, pass it to a parent directory: `./run.sh ..`, where `..` is the parent of the current directory (the one containing all the demos).

Depending on the directory content you pass this script for, the output will be different than this:
```shell
java ListDir ..
Walking path: ..
Total: 141 files, total size = 14448801 bytes

real	0m0.320s
user	0m0.379s
sys	0m0.070s
./listDir ..
Walking path: ..
Total: 141 files, total size = 14448801 bytes

real	0m0.030s
user	0m0.005s
sys	0m0.011s
```
The performance gain of the native version is largely due to the faster startup.

## Polyglot Capabilities

You can also experiment with a more sophisticated `ExtListDir` example, which takes advantage of GraalVM's Java and JavaScript polyglot capabilities.

```shell
$JAVA_HOME/bin/javac ExtListDir.java
```

Building the native executable command is similar to the one above, but since the example uses JavaScript, you need to inform the `native-image` utility about that by passing the `--language:js` option.
Note that it takes a bit more time because it needs to include the JavaScript support.
```shell
$JAVA_HOME/bin/native-image --language:js ExtListDir
```

The execution is the same as in the previous example:
```shell
time java ExtListDir $1
time ./extlistdir $1
```

## Profile-Guided Optimizations for High Throughput

Oracle GraalVM Enterprise Edition offers extra benefits for building native executables.
These are [profile-guided optimisations (PGO)](../reference-manual/native-image/PGOEnterprise.md).
As an example, a [program demonstrating Java streams](https://github.com/graalvm/graalvm-demos/blob/master/scala-examples/streams/Streams.java) will be used.

1&#46; Run the application with `java` to see the output:
```shell
javac Streams.java
$JAVA_HOME/bin/native-image Streams
./streams 1000000 200
...
Iteration 20 finished in 1955 milliseconds with checksum 6e36c560485cdc01
```

2&#46; Build an instrumented image and run it to collect profiles:
```shell
$JAVA_HOME/bin/native-image --pgo-instrument Streams
./streams 1000 200
```
Profiles collected from this run are now stored in the `default.iprof` file. Note that the profiling now runs with a much smaller data size.

3&#46; Use the profiles gathered at the previous step to build an optimized native executable:
```shell
$JAVA_HOME/bin/native-image --pgo Streams
```

4&#46; Run that optimized native executable:
```shell
./streams 1000000 200
...
Iteration 20 finished in 827 milliseconds with checksum 6e36c560485cdc01
```
You should see more than 2x improvements in performance.
