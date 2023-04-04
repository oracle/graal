---
layout: ni-docs
toc_group: build-overview
link_title: Native Image Bundles
permalink: /reference-manual/native-image/overview/Bundles/
redirect_from: /$version/reference-manual/native-image/Bundles/
---

# Native Image Bundles

Native Image provides a feature that enables users to build native executables from a self-contained _bundle_. 
In contrast to regular `native-image` building, this mode of operation only takes a single `*.nib`-file as an input that contains everything needed to build an image (a native executable or a native shared library).
This can be useful when large applications consisting of many input files (JAR-files, configuration files, auto-generated files, downloaded files) need to be rebuilt at a later point in time without worrying whether all files are still available.
Often complex builds involve downloading many libraries that are not guaranteed to remain accessible later in time.
Using Native Image bundles is a safe solution to encapsulate all this input required for building into a single file.

### Table of Contents

* [Creating Bundles](#creating-bundles)
* [Building with Bundles](#building-with-bundles)
* [Environment Variables](#capturing-environment-variables)
* [Creating New Bundles from Existing Bundles](#combining---bundle-create-and---bundle-apply)
* [Bundle File Format](#bundle-file-format)

## Creating Bundles

To create a bundle, pass the `--bundle-create` option along with the other arguments for a specific `native-image` command line invocation.
This will cause `native-image` to create a `*.nib`-file in addition to the actual image.

Here is the option description:
```shell
--bundle-create[=new-bundle.nib]
                      in addition to image building, create a Native Image bundle file (*.nib
                      file) that allows rebuilding of that image again at a later point. If a
                      bundle-file gets passed, the bundle will be created with the given
                      name. Otherwise, the bundle-file name is derived from the image name.
                      Note both bundle options can be combined with --dry-run to only perform
                      the bundle operations without any actual image building.
```

For example, assuming a Micronaut application is built with Maven, make sure the `--bundle-create` option is used.
For that, the following needs to be added to the plugins section of `pom.xml`:
```xml
<plugin>
  <groupId>org.graalvm.buildtools</groupId>
  <artifactId>native-maven-plugin</artifactId>
  <configuration>
      <buildArgs combine.children="append">
          <buildArg>--bundle-create</buildArg>
      </buildArgs>
  </configuration>
</plugin>
```

Then, when you run the Maven package command `./mvnw package -Dpackaging=native-image`, you will get the following build artifacts:
```shell
Finished generating 'micronautguide' in 2m 0s.

Native Image Bundles: Bundle build output written to /home/testuser/micronaut-data-jdbc-repository-maven-java/target/micronautguide.output
Native Image Bundles: Bundle written to /home/testuser/micronaut-data-jdbc-repository-maven-java/target/micronautguide.nib

[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  02:08 min
[INFO] Finished at: 2023-03-27T15:09:36+02:00
[INFO] ------------------------------------------------------------------------
```

This output indicates that you have created a native executable, `micronautguide`, and a bundle, _micronautguide.nib_.
The bundle file is created in the _target/_ directory.
It should be copied to some safe place where it can be found if the native executable needs to be rebuilt later.

Obviously, a bundle file can be large because it contains all input files as well as the executable itself (the executable is compressed within the bundle). 
Having the image inside the bundle allows comparing a native executable rebuilt from the bundle against the original one.
In the case of the `micronaut-data-jdbc-repository` example, the bundle is 60.7 MB (the executable is 103.4 MB).
To see what is inside a bundle, run `jar tf *.nib`:
```shell
$ jar tf micronautguide.nib
META-INF/MANIFEST.MF
META-INF/nibundle.properties
output/default/micronautguide
input/classes/cp/micronaut-core-3.8.7.jar
input/classes/cp/netty-buffer-4.1.87.Final.jar
input/classes/cp/jackson-databind-2.14.1.jar
input/classes/cp/micronaut-context-3.8.7.jar
input/classes/cp/reactive-streams-1.0.4.jar
...
input/classes/cp/netty-handler-4.1.87.Final.jar
input/classes/cp/micronaut-jdbc-4.7.2.jar
input/classes/cp/jackson-core-2.14.0.jar
input/classes/cp/micronaut-runtime-3.8.7.jar
input/classes/cp/micronautguide-0.1.jar
input/stage/build.json
input/stage/environment.json
input/stage/path_substitutions.json
input/stage/path_canonicalizations.json
```

As you can see, a bundle is just a JAR file with a specific layout.
This is explained in detail [below](#bundle-file-format).

Next to the bundle, you can also find the output folder: _target/micronautguide.output_.
It contains the native executable and all other files that were created as part of the build. 
Since you did not specify any options that would produce extra output (for example, `-g` to generate debugging information or `--diagnostics-mode`), only the executable can be found there:
```shell
$ tree target/micronautguide.output
target/micronautguide.output
├── default
│   └── micronautguide
└── other
```

### Combining --bundle-create with --dry-run

As mentioned in the `--bundle-create` option description, it is also possible to let `native-image` build a bundle but not actually perform the image building.
This might be useful if a user wants to move the bundle to a more powerful machine and build the image there.
Modify the above `native-maven-plugin` configuration to also contain the argument `<buildArg>--dry-run</buildArg>`. 
Then running `./mvnw package -Dpackaging=native-image` takes only seconds and the created bundle is much smaller: 
```shell
Native Image Bundles: Bundle written to /home/testuser/micronaut-data-jdbc-repository-maven-java/target/micronautguide.nib

[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  2.267 s
[INFO] Finished at: 2023-03-27T16:33:21+02:00
[INFO] ------------------------------------------------------------------------
```

Now `micronautguide.nib` is only 20 MB in file size and the executable is not included:
```shell
$ jar tf micronautguide.nib
META-INF/MANIFEST.MF
META-INF/nibundle.properties
input/classes/cp/micronaut-core-3.8.7.jar
...
```

Note that this time you do not see the following message in the Maven output:
```shell
Native Image Bundles: Bundle build output written to /home/testuser/micronaut-data-jdbc-repository-maven-java/target/micronautguide.output
```
Since no executable is created, no bundle build output is available.

## Building with Bundles

Assuming that the native executable is used in production and once in a while an unexpected exception is thrown at run time.
Since you still have the bundle that was used to create the executable, it is trivial to build a variant of that executable with debugging support.
Use `--bundle-apply=micronautguide.nib` like this:
```shell
$ native-image --bundle-apply=micronautguide.nib -g

Native Image Bundles: Loaded Bundle from /home/testuser/micronautguide.nib
Native Image Bundles: Bundle created at 'Tuesday, March 28, 2023, 11:12:04 AM Central European Summer Time'
Native Image Bundles: Using version: 'GraalVM 23.0.0 Java 20.0.1+8-jvmci-23.0-b09 EE' on platform: 'linux-amd64'
Warning: Native Image Bundles are an experimental feature.
========================================================================================================================
GraalVM Native Image: Generating 'micronautguide' (executable)...
========================================================================================================================
...
Finished generating 'micronautguide' in 2m 16s.

Native Image Bundles: Bundle build output written to /home/testuser/micronautguide.output
```

After running this command, the executable is rebuilt with an extra option `-g` passed after `--bundle-apply`.
The output of this build is in the directory _micronautguide.output_:
```shell
micronautguide.output
micronautguide.output/other
micronautguide.output/default
micronautguide.output/default/micronautguide.debug
micronautguide.output/default/micronautguide
micronautguide.output/default/sources
micronautguide.output/default/sources/javax
micronautguide.output/default/sources/javax/smartcardio
micronautguide.output/default/sources/javax/smartcardio/TerminalFactory.java
...
micronautguide.output/default/sources/java/lang/Object.java
```

You successfully rebuilt the application from the bundle with debug info enabled.

The full option help of `--bundle-apply` shows a more advanced use case that will be discussed [later](#combining---bundle-create-and---bundle-apply) in detail:
```shell
--bundle-apply=some-bundle.nib
                      an image will be built from the given bundle file with the exact same
                      arguments and files that have been passed to native-image originally
                      to create the bundle. Note that if an extra --bundle-create gets passed
                      after --bundle-apply, a new bundle will be written based on the given
                      bundle args plus any additional arguments that haven been passed
                      afterwards. For example:
                      > native-image --bundle-apply=app.nib --bundle-create=app_dbg.nib -g
                      creates a new bundle app_dbg.nib based on the given app.nib bundle.
                      Both bundles are the same except the new one also uses the -g option.
```

## Capturing Environment Variables

Before bundle support was added, all environment variables were visible to the  `native-image` builder.
This approach does not work well with bundles and is problematic for image building without bundles.
Consider having an environment variable that holds sensitive information from your build machine.
Due to Native Image's ability to run code at build time that can create data to be available at run time, it is very easy to build an image were you accidentally leak the contents of such variables.

Passing environment variables to `native-image` now requires explicit arguments.

Suppose a user wants to use environment variable (for example, `KEY_STORAGE_PATH`) from the environment the `native-image` tool is invoked, in the static constructor of a class that is set to be initialized at build time.
To allow accessing the variable in the static constructor (with `java.lang.System.getenv`), pass the option `-EKEY_STORAGE_PATH` to the builder.

To make an environment variable accessible to build time, use:
```shell
-E<env-var-key>[=<env-var-value>]
                      allow native-image to access the given environment variable during
                      image build. If the optional <env-var-value> is not given, the value
                      of the environment variable will be taken from the environment
                      native-image was invoked from.
```

Using `-E` works as expected with bundles.
Any environment variable specified with `-E` will be captured in the bundle.
For variables where the optional `<env-var-value>` is not given, the bundle would capture the value the variable had at the time the bundle was created.
Prefix `-E` was chosen tho make the option look similar to the related `-D<java-system-property-key>=<java-system-property-value>` option (which makes Java system properties available at build time).

## Combining --bundle-create and --bundle-apply

As already mentioned in [Building with Bundles](#building-with-bundles), it is possible to create new bundles based on existing ones.
The `--bundle-apply` help message has a simple example.
A more interesting example arises if an existing bundle is used create a bundle that builds a PGO-optimized version of the original application.

Assuming you have already built the `micronaut-data-jdbc-repository` example into a bundle named `micronautguide.nib`.
To produce a PGO-optimized variant of that bundle, first build a variant of the native executable that generates PGO profiling information at run time (you will use it later):
```shell
$ native-image --bundle-apply=micronautguide.nib --pgo-instrument

Native Image Bundles: Loaded Bundle from /home/testuser/micronautguide.nib
Native Image Bundles: Bundle created at 'Tuesday, March 28, 2023, 11:12:04 AM Central European Summer Time'
Native Image Bundles: Using version: 'GraalVM 23.0.0 Java 20.0.1+8-jvmci-23.0-b09 EE' on platform: 'linux-amd64'
Warning: Native Image Bundles are an experimental feature.
========================================================================================================================
GraalVM Native Image: Generating 'micronautguide' (executable)...
========================================================================================================================
...
[1/8] Initializing...                                                                                    (3.8s @ 0.28GB)
 Version info: 'GraalVM 23.0.0 Java 20.0.1+8-jvmci-23.0-b09 EE'
 Java version info: '20.0.1+8-jvmci-23.0-b09'
 Graal compiler: optimization level: '2', target machine: 'x86-64-v3', PGO: off
 C compiler: gcc (redhat, x86_64, 13.0.1)
 Garbage collector: Serial GC (max heap size: 80% of RAM)
 6 user-specific feature(s)
 - io.micronaut.buffer.netty.NettyFeature
...
Finished generating 'micronautguide' in 2m 47s.

Native Image Bundles: Bundle build output written to /home/testuser/micronautguide.output
```

Now run the generated executable so that profile information is collected:
```shell
$ /home/testuser/micronautguide.output/default/micronautguide
 __  __ _                                  _   
|  \/  (_) ___ _ __ ___  _ __   __ _ _   _| |_ 
| |\/| | |/ __| '__/ _ \| '_ \ / _` | | | | __|
| |  | | | (__| | | (_) | | | | (_| | |_| | |_ 
|_|  |_|_|\___|_|  \___/|_| |_|\__,_|\__,_|\__|
  Micronaut (v3.8.7)

14:51:32.059 [main] INFO  com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Starting...
14:51:32.071 [main] INFO  com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Start completed.
14:51:32.071 [main] INFO  i.m.flyway.AbstractFlywayMigration - Running migrations for database with qualifier [default]
...
```

Based on <a href="https://guides.micronaut.io/latest/micronaut-data-jdbc-repository.html" target="_blank">this walkthrough</a>, you use the running native executable to add new database entries and query the information in the database afterwards so that you get real-world profiling information.
Once completed, shut the Micronaut application down with `CTRL-C` (`SIGTERM`).
Looking into the current working directory, you can find a new file:
```shell
$ ls -lh  *.iprof
-rw------- 1 testuser testuser 19M Mar 28 14:52 default.iprof
```

The file `default.iprof` contains the profiling information that was created because you ran the Micronaut application from the executable built with `--pgo-instrument`.
Now you can create a new optimized bundle out of the existing one:
```shell
native-image --bundle-apply=micronautguide.nib --bundle-create=micronautguide-pgo-optimized.nib --dry-run --pgo

Native Image Bundles: Loaded Bundle from /home/testuser/micronautguide.nib
Native Image Bundles: Bundle created at 'Tuesday, March 28, 2023, 11:12:04 AM Central European Summer Time'
Native Image Bundles: Using version: 'GraalVM 23.0.0 Java 20.0.1+8-jvmci-23.0-b09 EE' on platform: 'linux-amd64'
Warning: Native Image Bundles are an experimental feature.
Native Image Bundles: Bundle written to /home/testuser/micronautguide-pgo-optimized.nib
```

Now take a look how `micronautguide-pgo-optimized.nib` is different from `micronautguide.nib`:
```shell
$ ls -lh *.nib
-rw-r--r-- 1 testuser testuser  20M Mar 28 11:12 micronautguide.nib
-rw-r--r-- 1 testuser testuser  23M Mar 28 15:02 micronautguide-pgo-optimized.nib
```

You can see that the new bundle is 3 MB larger than the existing one.
The reason, as can be guessed, is that now the bundle contains the `default.iprof` file.
Using a tool to compare folders, you can inspect the differences in detail:

![visual-bundle-compare](visual-bundle-compare.png)

As you can see, `micronautguide-pgo-optimized.nib` contains `default.iprof` in the folder _input/auxiliary_, and there
are also changes in other files. The contents of `META-INF/nibundle.properties`, `input/stage/path_substitutions.json`
and `input/stage/path_canonicalizations.json` will be explained [later](#bundle-file-format). 
For now, look at the diff in `build.json`:
```shell
@@ -4,5 +4,6 @@
   "--no-fallback",
   "-H:Name=micronautguide",
   "-H:Class=example.micronaut.Application",
-  "--no-fallback"
+  "--no-fallback",
+  "--pgo"
 ]
```

As expected, the new bundle contains the `--pgo` option that you passed to `native-image` to build an optimized bundle.
Building a native executable from this new bundle generates a pgo-optimized image out of the box (see `PGO: on` in build output):
```shell
$ native-image --bundle-apply=micronautguide-pgo-optimized.nib

Native Image Bundles: Loaded Bundle from /home/testuser/micronautguide-pgo-optimized.nib
Native Image Bundles: Bundle created at 'Tuesday, March 28, 2023, 3:02:43 PM Central European Summer Time'
Native Image Bundles: Using version: 'GraalVM 23.0.0 Java 20.0.1+8-jvmci-23.0-b09 EE' on platform: 'linux-amd64'
...
[1/8] Initializing...                                                                                    (3.9s @ 0.27GB)
 Version info: 'GraalVM 23.0.0-dev Java 20.0.1+8-jvmci-23.0-b09 EE'
 Java version info: '20.0.1+8-jvmci-23.0-b09'
 Graal compiler: optimization level: '2', target machine: 'x86-64-v3', PGO: on
 C compiler: gcc (redhat, x86_64, 13.0.1)
 Garbage collector: Serial GC (max heap size: 80% of RAM)
 6 user-specific feature(s)
...
```

## Bundle File Format

A bundle file is a JAR file with a well-defined internal layout.
Inside a bundle you can find the following inner structure:

```shell
[bundle-file.nib]
├── META-INF
│   ├── MANIFEST.MF
│   └── nibundle.properties <- Contains build bundle version info:
│                              * Bundle format version (BundleFileVersion{Major,Minor})
│                              * Platform and architecture the bundle was created on 
│                              * GraalVM / Native-image version used for bundle creation
├── input <- All information required to rebuild the image
│   ├── auxiliary <- Contains auxiliary files passed to native-image via arguments
│   │                (e.g. external `config-*.json` files or PGO `*.iprof`-files)
│   ├── classes   <- Contains all class-path and module-path entries passed to the builder
│   │   ├── cp
│   │   └── p
│   └── stage
│       ├── build.json          <- Full native-image command line (minus --bundle options)
│       ├── environment.json              <- Environment variables used in the image build
│       ├── path_canonicalizations.json  <- Record of path-canonicalizations that happened
│       │                                       during bundle creation for the input files  
│       └── path_substitutions.json          <- Record of path-substitutions that happened
│                                               during bundle creation for the input files
└── output
    ├── default
    │   ├── myimage         <- Created image and other output created by the image builder 
    │   ├── myimage.debug
    |   └── sources
    └── other      <- Other output created by the builder (not relative to image location)
```
### META-INF

The layout of a bundle file itself is versioned.
There are two properties in `META-INF/nibundle.properties` that declare which version of the layout a given bundle file is based on.
At the time of writing this documentation, bundles use the following layout version:
```shell
BundleFileVersionMajor=0
BundleFileVersionMinor=9
```

Future versions of GraalVM might alter or extend the internal structure of bundle files.
The versioning allows to evolve the bundle format with backwards compatibility in mind.

### input

This directory contains all input data that gets passed to the `native-image` builder. 
The file _input/stage/build.json_ holds the original command line that was passed to `native-image` when the bundle was created.

Parameters that make no sense to get reapplied in a bundle-build are already filtered out.
These include:
* `--bundle-{create,apply}`
* `--verbose`
* `--dry-run`

The state of environment variables that are relevant for the build are captured in `input/stage/environment.json`.
For every `-E` argument that were seen when the bundle was created, a snapshot of its key-value pair is recorded in the file.
The remaining files `path_canonicalizations.json` and `path_substitutions.json` contain a record of the file-path transformations that were performed by the `native-image` tool based on the input file paths as specified by the original command line arguments.

### output

If a native executable is built as part of building the bundle (for example, the `--dry-run` option was not used), you also have an `output` folder in the bundle.
It contains the executable that was built along with any other files that were generated as part of building.
Most output files are located in the directory _output/default_ (the executable, its debug info, and debug sources).
Builder output files, that would have been written to arbitrary absolute paths if the executable had not been built in the bundle mode, can be found in `output/other`.

### Relative Documentation

* [Native Image Build Configuration](BuildConfiguration.md)
* [Native Image Build Output](BuildOutput.md)