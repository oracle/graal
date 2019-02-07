# Substrate VM Options

Substrate VM has two distinct kinds of options:

* Hosted options: configure the boot image generation, i.e., influence what is put into the image and how the image is built.
They are set using the prefix `-H:` on the command line.

* Runtime options: get their initial value during boot image generation, using the prefix `-R:` on the command line of the boot image generator.
At run time, the default prefix is `-XX:` (but this is application specific and not mandated by Substrate VM).

For developer documentation on how to define and use options, read the package documentation of the package `com.oracle.svm.core.option`.


## List of Useful Options

### Graal Graph Dumping

Substrate VM re-used the Graal options for graph dumping, logging, counters, and everything else of the Graal debug environment.
These Graal options can be used both as hosted options (if you want to dump graphs of the boot image generator) and runtime options (if you want to dump graphs during dynamic compilation at run time).

Graal options that work as expected include `Dump`, `DumpOnError`, `Log`, `MethodFilter`, and the options to specify file names and ports for the dump handlers.

Example that dumps Graal graphs of the boot image generator: `-H:Dump= -H:MethodFilter=ClassName.MethodName`.

Example that dumps Graal graphs at run time: specify the dump flags at run time with `-XX:Dump= -XX:MethodFilter=ClassName.MethodName`.

### Debug Options

These options enable additional checks in the generated executable.
This helps with debugging.

* `-H:[+|-]HostedAssertions`
  Enable or disable Java assert statements in the boot image generator.
This flag is translated to either `-ea -esa` or `-da -dsa` for the HotSpot VM.
* `-H:[+|-]RuntimeAssertions`
  Enable or disable Java assert statements at run time.
* `-H:TempDirectory=FileSystemPath`
  Directory for temporary files generated during boot image generation.
If this option is specified, the temporary files are not deleted so that you can inspect them after boot image generation.


### Garbage Collection Options

* `-Xmn=`
  Set the size of the young generation (the amount of memory that can be allocated without triggering a GC).
Value is specified in bytes, suffix `k`, `m`, or `g` can be used for scaling.
* `-Xmx=`
  Set the maximum heap size in bytes.
Value is specified in bytes, suffix `k`, `m`, or `g` can be used for scaling.
Note that this is not the maximum amount of consumed memory, because during GC the system can request more temporary memory.
* `-Xms=`
  Set the minimum heap size in bytes.
Value is specified in bytes, suffix `k`, `m`, or `g` can be used for scaling.
Heap space that is unused will be retained for future heap usage, rather than being returned to the operating system.
* `-R:[+|-]PrintGC`
  Print summary GC information after each collection.
* `-R:[+|-]VerboseGC`
  Print more information about the heap before and after each
  collection.


### Control the main entry points

* `-H:Kind=[EXECUTABLE | SHARED_LIBRARY]`
  Generate a executable with a main entry point, or a shared library with all entry points that are marked via `@CEntryPoint`.
* `-H:Class=ClassName`
  Class containing the default entry point method.
Ignored if `Kind == SHARED_LIBRARY`.
* `-H:Projects=Project1,Project2`
  The project that contains the application (and transitively all projects that it depends on).
* `-H:Name=FileName`
  Name of the executable file that is generated.
* `-H:Path=FileSystemPath`
  Directory where the generated executable is placed.
