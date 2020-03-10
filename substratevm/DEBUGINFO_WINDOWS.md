Using the prototype debug info feature on windows.
--------------------------------------

To add debug info to a generated native image add flag
-H:GenerateDebugInfo=<N> to the native image command line (where N is
a positive integer value -- the default value 0 means generate no
debug info). For example,

    $ javac Hello.java
    $ mx native-image -H:GenerateDebugInfo=1 Hello

The resulting image should contain CodeView4 debug records in a
format Visual Studio understands. 

Please read the standard DEBUGINFO file; this file only documents differences.

Because of limitations in the linker, function names are mangled into the equivalent of
 _package.class.Function_999_, where '999' is a hash of the function arguments.  The exception is the first main() function encountered,
 which is mangled to _package.class.main_, with no argument hash.

As an experimental feature, the Windows debug info ignores inlined Graal code.
This is currently a Graal compile-time flag in CVConstants.java: _skipGraalIntrinsics_.

To enable Visual Studio to access the cached sources, right click on the solution
in the "Solution Explorer", and select "Properties", then "Debug Source Files".
in the Debug Source Files plane, add the cache directories to the source directory list.

___Unimplemented functionality___

- Currently stack frames are not properly expressed in the debug info.
The stack frame display may have extraneous information in it, and "Step Out", may work incorrectly.

- Currently there is no type information in the debug info.

- Currently variables and members do not appear in the type information.
