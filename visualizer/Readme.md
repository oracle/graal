# GraalVM Ideal Graph Visualizer (IGV)

## Prerequisites

- JDK 11 is the recommended Java runtime platform for IGV.

## Settings

- During IGV first run, you will be prompted to download and install JavaScript dependencies for IGV's Scripting shell.
 - Without JavaScript modules the Scripting shell will have less fetures (highlighting will be missing among other features).
- It is recomended to run IGV on GraalVM.
 - To run with GraalVM use flag `--jdkhome <path>` or setup your `PATH` environment variable to point to GraalVM installation directory.
  - This will enable the use of Scripting shell with GraalVM scripting languages.

## Building and testing IGV

### MX

IGV is an MX project and should be builded and tested as such.
Please download MX to be able to build and test IGV.

#### Building

To build run the command:
`mx build`

This will:
- clone other needed missing repositories:
 - graal
 - graaljs
- download NB platform the proper internal or external server
- build relevant parts of graal for IGV.
- build IGV.

The resulting build is a development build with a version number of `dev`.  This means that
preferences are stored in a different location which is helpful so that development doesn't mess
with your regular preferences.  Use `mx build-release` to build a release version.

#### Testing

To check for possible checkstyle or gate issues and run spotbugs run command:
`mx gate`

To do the unittests included in IGV run command:
`mx unittest`

### Important files

 - `IdealGraphVisualizer/nbproject/project.properties`
  - version number specification
   - `app.version`
  - run and debug arguments
   - `run.args.extra`
   - `debug.args.extra`
 - `IdealGraphVisualizer/nbproject/platform.properties`
  - platform disabled modules
   - `disabled.modules`
  - platform download url and expected name
   - `ide.dist.url`
   - `platform.dist.number.regexp`

## Running IGV

### Linux

Run: `>$ idealgraphvisualizer/bin/idealgraphvisualizer`

### MacOS

Run: `>$ idealgraphvisualizer/bin/idealgraphvisualizer`

### Windows

Execute: `idealgraphvisualizer/bin/idealgraphvisualizer64.exe` or `idealgraphvisualizer/bin/idealgraphvisualizer.exe`

### Command Line Options
- `--jdkhome <path>` sets path to jdk to run IGV with (IGV runtime Java platform).
- `--open <file>` or `<file>` (*.bgv) opens specified file immediately after IGV is started.
