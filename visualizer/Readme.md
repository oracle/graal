# GraalVM Ideal Graph Visualizer (IGV)

## Prerequisites

- JDK 21 is the recommended Java runtime platform for IGV, but any release between 17 and 24 is
  supported by the NetBeans 26 platform.

## Building and testing IGV

### MX

IGV is an MX project and for convenience should be built and tested as such.  It's also a NetBeans
26 project based on Maven so it can be developed using any tool chain which supports Maven.
Certains kind of edits, like editing the NetBeans module exlusions or editing the special UI
components, will require using NetBeans.

#### Building

To build IGV run the command:
`mx build`

#### Testing

To run the unittests included in IGV run command:
`mx unittest`

#### Creating a release

To create a release build, use the command `mx release` which will run the maven commands to create
a build without the `-SNAPSHOT` part of the version string.  It will then update the version string
to the sequential number and commit those changes.  It will also create a tag for the released
version though this will have to be propagated manually.  You can respin the build by deleting the
changeset pushed by the release command and deleting the tag it created.

### Important files

 - `IdealGraphVisualizer/pom.xml` contains a `properties` section for values which affect the build
  - the base NetBeans platform version is `netbeans.version`

## Running IGV

`mx igv` will launch the newly built IGV.

### Command Line Options
- `--jdkhome <path>` sets path to jdk to run IGV with (IGV runtime Java platform).
- `--open <file>` or `<file>` (*.bgv) opens specified file immediately after IGV is started.
