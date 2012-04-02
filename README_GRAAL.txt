There is a Python script in graal/mxtool/mx.py that simplifies working with the code
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

To run one of the correspong VM instances:

  mx --product vm ...
  mx --fastdebug vm ...
  mx --debug vm ...

The default is --product.