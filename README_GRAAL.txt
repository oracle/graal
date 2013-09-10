Building Graal
--------------
There is a Python script in mxtool/mx.py that simplifies working with the code
base. It requires Python 2.7. While you can run this script by using an absolute path,
it's more convenient to add graal/mxtool to your PATH environment variable so that the
'mx' helper script can be used. The following instructions in this file assume this
setup.

Building both the Java and C++ source code comprising the Graal VM
can be done with the following simple command.

% mx build

This builds the 'product' version of HotSpot with the Graal modifications.
To build the debug or fastdebug versions:

  mx --vmbuild debug build
  mx --vmbuild fastdebug build

Running Graal
-------------

To run the VM, use 'mx vm' in place of the standard 'java' command:

% mx vm ...

To select the fastdebug or debug versions of the VM:

% mx --vmbuild fastdebug vm ...
% mx --vmbuild debug vm ...

Graal has an optional bootstrap step where it compiles itself before
compiling any application code. This bootstrap step currently takes about 20 seconds
on a fast x64 machine. It's useful to disable this bootstrap step when running small
programs with the -XX:-BootstrapGraal options. For example:

% mx vm -XX:-BootstrapGraal ...


Other Build Configurations
--------------------------

By default the build commands above create a HotSpot binary where Graal
is the only compiler. This binary is the Graal VM binary and identifies as
such with the -version option:

% mx vm -XX:-BootstrapGraal -version
java version "1.7.0_25"
Java(TM) SE Runtime Environment (build 1.7.0_25-b15)
OpenJDK 64-Bit Graal VM (build 25.0-b43-internal, mixed mode)

It's also possible to build and execute the standard HotSpot binaries
using the --vm option:

% mx --vm server build
% mx --vm server vm -version
java version "1.7.0_25"
Java(TM) SE Runtime Environment (build 1.7.0_25-b15)
OpenJDK 64-Bit Server VM (build 25.0-b43-internal, mixed mode)

These standard binaries still include the code necessary to support use of the
Graal compiler for explicit compilation requests. However, in this configuration
the Graal compiler will not service VM issued compilation requests (e.g., upon
counter overflow in the interpreter).

To build and run a HotSpot binary that completely omits all VM support for Graal,
use the following as a guide:

% mx --vm server-nograal build
% mx --vm server-nograal vm -version
java version "1.7.0_25"
Java(TM) SE Runtime Environment (build 1.7.0_25-b15)
OpenJDK 64-Bit Server VM (build 25.0-b43-internal, mixed mode)

% mx --vm client-nograal build
% mx --vm client-nograal vm -version
java version "1.7.0_25"
Java(TM) SE Runtime Environment (build 1.7.0_25-b15)
OpenJDK 64-Bit Cleint VM (build 25.0-b43-internal, mixed mode)

