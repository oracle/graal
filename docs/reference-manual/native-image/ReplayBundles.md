# Draft: Native Image Replay Bundles

## Motivation

1. The deployment of a native image is just one step in the lifecycle of an application or service. Real world 
applications run for years and sometimes need to be updated or patched long after deployment (security fixes). It would 
be great if there would be an easy way to redo an image build at some point in the future as accurately as possible.

2. Another angle is provided from the development phase. If image building fails or a malfunctioning image is
created (i.e. the same application runs fine when executed via JVM) we would like to get bug reports that allow us to
reproduce the problem locally without hours of replicating their setup. We would want some way to bundle up what the
user built (or tried to build) into a nice package that allows us to instantly reproduce the problem on our side.

3. Debugging an image created long time ago is also sometimes needed. It would be great if there is a single bundle that
contains everything needed to perform this task.

## Replay Bundles 

A set of options should be added to the `native-image` command that allows to create so-called "replay bundles" that can
be used to help with problems described above. There shall be

```shell
native-image --replay-create ...other native-image arguments...
```

This will instruct native-image to create a replay bundle alongside the image.

For example, after the running:

```shell
native-image --replay-create -Dlaunchermode=2 -EBUILD_ENVVAR=env42 \
 -p somewhere/on/my/drive/app.launcher.jar:/mnt/nfs/server0/mycorp.base.jar \
 -cp $HOME/ourclasses:somewhere/logging.jar:/tmp/other.jar:aux.jar \
 -m app.launcher/paw.AppLauncher alaunch
```
the user sees the following build results:
```shell
~/foo$ ls
alaunch alaunch.nirb.jar somewhere aux.jar
```
As we can see, in addition to the image a `<imagename>.nirb.jar`-file was created. This is the native image replay
bundle for the image that got built. At any time later, if the same version of GraalVM is used, the image can be rebuilt
with:

```shell
native-image --replay .../path/to/alaunch.nirb.jar
```

this will rebuild the `alaunch` image with the same image arguments, environment variables, system properties
settings, classpath and module-path options as in the initial build.

## Replay Bundles File Format

A `<imagename>.nirb.jar` file is a regular jar-file that contains all information needed to replay a previous build.
For example, the `alaunch.nirb.jar` replay bundle has the following inner structure:

```
alaunch.nirb.jar
├── build
│   └── report <- Contains information about the build proccess.
│       │         In case of replay these will be compared against. 
│       ├── analysis_results.json
│       ├── build_artifacts.json
│       ├── build.log
│       ├── build_output.json
│       ├── jni_access_details.json
│       └── reflection_details.json
├── input
│   ├── classes <- Contains all class-path and module-path entries passed to the builder
│   │   ├── cp
│   │   │   ├── aux.jar
│   │   │   ├── logging.jar
│   │   │   ├── other.jar
│   │   │   └── ourclasses
│   │   └── p
│   │       ├── app.launcher.jar
│   │       └── mycorp.base.jar
│   └── stage
│       ├── all.env <- All environment variables used in the image build
│       ├── all.properties  <- All system properties passed to the builder
│       ├── build.cmd <- Full native-image command line (minus --replay-create option)
│       └── run.cmd <- Arguments to run application on java (for laucher, see below) 
├── META-INF
│   ├── MANIFEST.MF <- Specifes rbundle/Launcher as mainclass
│   └── RBUNDLE.MF <- Contains replay bundle version info:
│                     * Replay-bundle format version
│                     * GraalVM / Native-image version used for build
├── out
│   ├── alaunch.debug <- Native debuginfo for the built image
│   └── sources <- Reachable sources needed for native debugging
└── rbundle
    └── Launcher.class <- Launcher for running of application with `java`
                          (uses files from input directory)
```

As we can see, there are several components in a replay bundle that we need to describe in more detail.

### `META-INF`:

Since the bundle is also a regular jar-file we have a `META-INF` subdirectory with the familiar `MANIFEST.MF`. The
bundle can be used like a regular jar-launcher (by running command `java -jar <imagename>.nirb.jar`) so that the
application we build an image from is instead executed on the JVM. For that purpose the `MANIFEST.MF` specifies the
`rbundle/Launcher` as main class.

Here we also find `RBUNDLE.MF`. This file is specific to replay bundles. Its existence makes clear that this is no
ordinary jar-file but a native image replay bundle. The file contains version information of the native image replay
bundle format itself and also which GraalVM version was used to create the bundle. This can later be used to report a
warning message if a bundle gets replayed with a GraalVM version different from the one used to create the bundle.

### `input`:

This directory contains the entire amount of information needed to redo the previous image build. The original
class-path and module-path entries are placed into corresponding files (for jar-files) and subdirectories (for
directory-based class/module-path entries) into the `input/classes/cp` (original -cp/--class-path entries) and the
`input/classes/p` (original -p/--module-path entries) folders. The `input/stage` folder contains all information
needed to replicate the previous build context.

#### `input/stage`:

Here we have `build.cmd` that contains all native-image command line options used in the previous build. Note that
**even the initial build that created the bundle already uses a class- and/or module-path that refers to the contents
of the `input/classes` folder**. This way we can guarantee that a replay build sees exactly the same relocated
class/module-path entries as the initial build. The use of `run.cmd` is explained later.

File `all.env` contains the environment variables that we allowed the builder to see during the initial build and
`all.properties` the respective system-properties.

### `build`:

This folder is used to document the build process that lead to the image that was created alongside the bundle.
The `report` sub-folder holds `build.log`. It is equivalent to what would have been created if the user had appended
`|& tee build.log` to the original native-image command line. Additionally, we have several json-files:
* `analysis_results.json`: Contains the results of the static analysis. A rerun should compare the new
`analysis_results.json` file with this one and report deviations in a user-friendly way.
* `build_artifacts.json`: Contains a list of the artifacts that got created during the initial build. As before,
changes should be reported to the user. 
* `build_output.json`: Similar information as `build.log`.
* `jni_access_details.json`: Overview which methods/classes/fields have been made jni-accessible for image-runtime.
* `reflection_details.json`: Same kind of information for reflection access at image runtime.

As already mentioned a rebuild should compare its newly generated set of json-files against the one in the bundle and
report deviations from the original ones in a user-friendly way.

### `out`:

This folder contains all the debuginfo needed in case we need to debug the image at some point in the future.

### `rbundle`:

Contains the `Launcher.class` that is used when the bundle is run as a regular java launcher. The class-file is not
specific to a particular bundle. Instead, the Launcher class extracts the contents of the `input` into a temporary
subdirectory in `$TEMP` and uses the files from `input/stage/all.*` and `input/stage/run.cmd` to invoke
`$JAVA_HOME/bin/java` with the environment-variables and with the arguments (e.g. system-properties) needed to run the
application on the JVM.

## Enforced sanitized image building

### Containerized image building on supported platforms

If available, docker/podman should be used to run the image builder inside a well-defined container image. **This allows
us to prevent the builder from using the network during image build**, thus guaranteeing that the image build result did
not depend on some unknown (and therefore unreproducible) network state. Another advantage is that we can mount
`input/classes` and `$GRAALVM_HOME` read-only into the container and only allow read-write access to the mounted `out`
and `build` directories. This will prevent the application code that runs at image build time to mess with anything
other than those directories. 

### Fallback for systems without container support

If containerized builder execution is not possible we can still at least **have the builder run in a sanitized
environment variable state** and make sure that **only environment variables are visible that were explicitly
specified with `-E<env_var_name>=<env_var_value>` or `-E<env_var_name>`** (to allow passing through from the
surrounding environment).

## Handling of Image build errors

To ensure replay bundles are feasible for the [second usecase decribed above](#motivation) we have to make sure a
bundle gets successfully created even if the image build fails. Most likely in this case the `out` folder will be
missing in the bundle. But as usual `build/report/build.log` will contain all the command line output that was shown
during the image build. This also includes any error messages that resulted in the build failure.