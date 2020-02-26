Using the ptototype debug info feature
--------------------------------------

To add debug info to a generated native image add flag
-H:+GenerateDebugInfo to the native image command line.

    $ javac Hello.java
    $ mx native-image -H:+GenerateDebugInfo Hello

The resulting image should contain code (method) debug records in a
format gdb understands (VS support is still under development).

The flag also enables caching of sources for JDK runtime classes,
GraalVM classes and application classes which can be located during
native image generation. The cache is created under local subdirectory
sources and can be used to configure source file search path roots for
the debugger. Files in the cache are located in a directory hierarchy
that matches the file path information included in the native image
debug records


What is currently implemented
-----------------------------

The currently implemented features include:

  - break points configured by file and line or by method name
  - single stepping by line including both into and over function calls
  - stack backtraces (not including frames detailing inlined code)

Note that single stepping within a compiled method includes file and
line number info for inlined code, including inlined Graal methods.
So, gdb may switch files even though you are still in the same
compiled method.

Identifying the location of source code
---------------------------------------

One goal of the implementation is to make it simple to configure your
debugger so that it can identify the relevant source file when it
stops during program execution. The native image generator tries to
achieve this by accumulating the relevant sources in a suitably
structured file cache.

The native image generator uses different strategies to locate source
files for JDK runtime classes, GraalVM classses and application source
classes for inclusion in the local sources cache. It identifies which
strategy to use based on the package name of the class. So, for
example, packages starting with java.* or jdk.* are JDK classes;
packages starting with org.graal.* or com.oracle.svm.* are GraalVM
classes; any other packages are regarded as application classes.

Sources for JDK runtime classes are retrieved from the src.zip found
in the JDK release used to run the native image generation process.
Retrieved files are cached under subdirectory sources/jdk, using the
module name (for JDK11) and package name of the associated class to
define the directory hierarchy in which the source is located.

So, for example, on Linux the source for class java.util.HashMap will
be cached in file sources/jdk/java.base/java/util/HashMap.java. Debug
info records for this class and its methods will identify this source
file using the relative directory path java.base/java/util and file
name HashMap.java. On Windows things will be the same modulo use of
'\' rather than '/' as the file separator.

Sources for GraalVM classes are retrieved from zip files or source
directories derived from entries in the classpath. Retrieved files are
cached under subdirectory sources/graal, using the package name of the
associated class to define the directory hierarchy in which the source
is located (e.g. class com.oracle.svm.core.VM has its source file
cached at sources/graal/com/oracle/svm/core/VM.java).

The lookup scheme for cached GraalVM sources varies depending upon
what is found in each classpath entry. Given a jar file entry like
/path/to/foo.jar, the corresponding file /path/to/foo.src.zip is
considered as a candidate zip file system from which source files may
be extracted. When the entry specifies a dir like /path/to/bar then
directories /path/to/bar/src and /path/to/bar/src_gen are considered
as candidates. Candidates are skipped when i) the zip file or source
directory does not exist or ii) it does not contain at least one
subdirectory hierarchy that matches one of the the expected GraalVM
package hierarchies.

Sources for application classes are retrieved from source jar files or
source directories derived from entries in the classpath. Retrieved
files are cached under subdirectory sources/src, using the package
name of the associated class to define the directory hierarchy in
which the source is located (e.g. class org.my.foo.Foo has its
source file cached as sources/src/org/my/foo/Foo.java).

The lookup scheme for cached pplication sources varies depending upon
what is found in each classpath entry. Given a jar file entry like
/path/to/foo.jar, the corresponding jar /path/to/foo-sources.jar is
considered as a candidate zip file system from which source files may
be extracted. When the entry specifies a dir like /path/to/bar/classes
or /path/to/bar/target/classes then directory /path/to/bar/src is
considered as a candidate. Finally, the current directory in whcih the
native image program is being run is also considered as a candidate.

These lookup strategies are only provisional and may need extending in
future. Note however that it is possible to make missing sources
available by other means. One option is to unzip extra app source jars
or copying extra app source trees into the cache. Another is to
configure extra source search paths (see below).

Configuring source paths in gdb
-------------------------------

In order for gdb to be able to locate the source files for your app
classes, Graal classes and JDK runtime classes you need to provide gdb
with a list of source root dirs using the 'set directories' command:

    (gdb) set directories /path/to/sources/jdk:/path/to/sources/graal:/path/to/sources/src

Directory .../sources/jdk should contain source files for all JDK runtime
classes referenced from debug records.

Directory .../sources/graal should contain source files for all GraalVM
classes referenced from debug records. Note that the current
implementation does not yet find some sources for the GraalVM JIT
compiler in the org.graalvm.compiler* package subspace.

Directory .../sources/src should contain source files for all
application classes referenced from debug records, assuming they can
be located using the lookup strategy described above.

You can supplement the files cached in sources/src by unzipping
application source jars or copying application source trees into the
cache. You need to ensure that any new subdirectory you add to
sources/src corresponds to the top level package for the classes whose
sources are being included.

You can also add extra directories to the search path. Note that gdb
does not understand zip fomrat file systems so any extra entries you
add must identify a directory tree containing the relevant
sources. Once again. top leel entries in the directory added to the
search path must correspond to the top level package for the classes
whose sources are being included.

Configuring source paths in VS
------------------------------

TO BE ADDED

Checking debug info on Linux
----------------------------

n.b. this is only of interest to those who want to understand how the
debug info implemetation works or want to trouble shoot problems
encountered during debugging that might relate to the debug info
encoding.

The objdump command can be used to display the dbeug info embedded
into a native image. The following commands (which all assume the
target binary is called hello) can be used to display all currentyl
generated content:

    $ objdump --dwarf=info hello > info
    $ objdump --dwarf=abbrev hello > abbrev
    $ objdump --dwarf=ranges hello > ranges
    $ objdump --dwarf=decodedline hello > decodedline
    $ objdump --dwarf=rawline hello > rawline
    $ objdump --dwarf=str hello > str
    $ objdump --dwarf=frames hello > frames

The *info* section includes details of all compiled Java methods.

The *abbrev* sectio defines the layout of records in the info section
that describe Java files (compilation units) and methods.

The *ranges* section details the start and end addresses of method
code segments

The *decodedline* section maps subsegments of method code range
segments to files and line numbers. This mapping includes entries
for files and line numbers for inlined methods.

The *rawline* segment provides deatails of how the line table is
generated using DWARF state machine instuctions that encode file,
line and address transitions.

The *str* section provides a lookup table for strings referenced
from records in the info section

The *frames* section lists transition points in compiled methods
where a (fixed size) stack frame is pushed or popped, allowing
the debugger to identify each frame's current and previous stack
pointers and it's return address.

Note that some of the content embedded in the debug records is
generated by the C compiler and belongs to code that is either in
libraries or the C lib bootstrap code that is bundled in with the
Java method code.

Currently supported targets
---------------------------

The prototype is currently implemented only for gdb on Linux.

  - Linux/x86_64 suppoort has been tested and should work
    correctly.

  - Linux/AArch64 support is present but has not yet been fully
    verified (break points should work ok but stack backtraces
    may be incorrect).

Windows support is still under development.
