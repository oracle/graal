## Building Graal

There is a Python script in mxtool/mx.py that simplifies working with the code
base. It requires Python 2.7. While you can run this script by using an absolute path,
it's more convenient to add graal/mxtool to your PATH environment variable so that the
'mx' helper script can be used. The following instructions in this file assume this
setup.

Building both the Java and C++ source code comprising the Graal VM
can be done with the following simple command.

```
% mx build
```

There are a number of VM configurations supported by mx which can
be explicitly specified using the --vm option. However, you'll typically
want one of these VM configurations:

1. The 'server' configuration is a standard HotSpot VM that includes the
   runtime support for Graal but uses the existing compilers for normal
   compilation (e.g., when the interpreter threshold is hit for a method).
   Compilation with Graal is only done by explicit requests to the
   Graal API. This is how Truffle uses Graal.
   
2. The 'graal' configuration is a VM where all compilation is performed
   by Graal and no other compilers are built into the VM binary. This
   VM will bootstrap Graal itself at startup unless the -XX:-BootstrapGraal
   VM option is given.   

Unless you use the --vm option with the build command, you will be presented
with a dialogue to choose one of the above VM configurations for the build
as well as have the option to make it your default for subsequent commands
that need a VM specified.

To build the debug or fastdebug builds:

```
% mx --vmbuild debug build
% mx --vmbuild fastdebug build
```

## Running Graal

To run the VM, use 'mx vm' in place of the standard 'java' command:

```
% mx vm ...
```

To select the fastdebug or debug builds of the VM:

```
% mx --vmbuild fastdebug vm ...
% mx --vmbuild debug vm ...
```

## Other VM Configurations

In addition to the VM configurations described above, there are
VM configurations that omit all VM support for Graal:

```
% mx --vm server-nograal build
% mx --vm server-nograal vm -version
java version "1.7.0_25"
Java(TM) SE Runtime Environment (build 1.7.0_25-b15)
OpenJDK 64-Bit Server VM (build 25.0-b43-internal, mixed mode)
```

```
% mx --vm client-nograal build
% mx --vm client-nograal vm -version
java version "1.7.0_25"
Java(TM) SE Runtime Environment (build 1.7.0_25-b15)
OpenJDK 64-Bit Client VM (build 25.0-b43-internal, mixed mode)
```

These configurations aim to match as closely as possible the
VM(s) included in the OpenJDK binaries one can download.
 No newline at end of file

