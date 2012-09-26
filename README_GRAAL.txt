Building Graal
--------------
There is a Python script in mxtool/mx.py that simplifies working with the code
base. It requires Python 2.7. While you can run this script by using an absolute path,
it's more convenient to add graal/mxtool to your PATH environment variable so that the
'mx' helper script can be used. The following instructions in this file assume this
setup.

Building both the Java and C++ source code comprising the Graal VM
can be done with the following simple command.

  mx build

This builds the 'product' version of HotSpot with the Graal modifications.
To build the debug or fastdebug versions:

  mx build debug
  mx build fastdebug

Running Graal
-------------

To run the VM, use 'mx vm' in place of the standard 'java' command:

  mx vm ...

To select the fastdebug or debug versions of the VM:

  mx --fastdebug vm ...
  mx --debug vm ...

Graal has an optional bootstrap step where it compiles itself before
compiling any application code. This bootstrap step currently takes about 7 seconds
on a fast x64 machine. It's useful to disable this bootstrap step when running small
programs with the -XX:-BootstrapGraal options. For example:

  mx vm -XX:-BootstrapGraal ...
