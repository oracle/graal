# Debug Info Feature

To add debug info to a generated native image, add the flag
`-H:GenerateDebugInfo=<N>` to the native image command line (where `N` is
a positive integer value -- the default value `0` means generate no
debug info). For example,
```shell
javac Hello.java
native-image -H:GenerateDebugInfo=1 Hello
```
The resulting image should contain code (method) debug records in a
format the GNU Debugger (GDB) understands (Windows support is still under development).
At present it makes no difference which positive value is supplied to the `GenerateDebugInfo` option.

Note that it is currently recommended to pass flag `-H:-UseIsolates` on the command
line whenever  debug info generation is enabled. When isolates are enabled field
values of Object (reference) type are stored as offsets from the heap base rather
than direct addresses and this confuses the debugger when it tries to read through
object fields. This problem should be corrected in a later release.

The `GenerateDebugInfo` option also enables caching of sources for any
JDK runtime classes, GraalVM classes, and application classes which can
be located during native image generation. By default, the cache is
created under local subdirectory sources (a command line option can be
used to specify an alternative location). It is used to configure
source file search path roots for the debugger. Files in the cache are
located in a directory hierarchy that matches the file path
information included in the native image debug records. The source
cache should contain all the files needed to debug the generated image
and nothing more. This local cache provides a convenient way of making
just the necessary sources available to the debugger or IDE when
debugging a native image.

The implementation tries to be smart about locating source files. It
uses the current `JAVA_HOME` to locate the JDK src.zip when searching
for JDK runtime sources. It also uses entries in the classpath to
suggest locations for GraalVM source files and application source
files (see below for precise details of the scheme used to identify
source locations). However, source layouts do vary and it may not be
possible to find all sources. Hence, users can specify the location of
source files explicitly on the command line using option
`DebugInfoSourceSearchPath`:
```shell
javac --source-path apps/greeter/src \
    -d apps/greeter/classes org/my/greeter/*Greeter.java
javac -cp apps/greeter/classes \
    --source-path apps/hello/src \
    -d apps/hello/classes org/my/hello/Hello.java
native-image -H:GenerateDebugInfo=1 \
    -H:-UseIsolates \
    -H:DebugInfoSourceSearchPath=apps/hello/src \
    -H:DebugInfoSourceSearchPath=apps/greeter/src \
    -cp apps/hello/classes:apps/greeter/classes org.my.hello.Hello
```
The `DebugInfoSourceSearchPath` option can be repeated as many times as
required to notify all the target source locations. The value passed
to this option can be either an absolute or relative path. It can
identify either a directory, a source JAR, or a source zip file. It is
also possible to specify several source roots at once using a comma
separator:
```shell
native-image -H:GenerateDebugInfo=1 \
    -H:DebugInfoSourceSearchPath=apps/hello/target/hello-sources.jar,apps/greeter/target/greeter-sources.jar \
    -cp apps/target/hello.jar:apps/target/greeter.jar \
    org.my.Hello
```
By default, the cache of application, GraalVM, and JDK sources is
located under local directory sources. The `DebugInfoSourceCacheRoot`
option can be used to specify an alternative location for the top level
directory. As an example, the following variant of the previous
command specifies the same target but employs an absolute path:
```shell
SOURCE_CACHE_ROOT=$PWD/sources
native-image -H:GenerateDebugInfo=1 \
    -H:-UseIsolates \
    -H:DebugInfoSourceCacheRoot=$SOURCE_CACHE_ROOT \
    -H:DebugInfoSourceSearchPath=apps/hello/target/hello-sources.jar,apps/greeter/target/greeter-sources.jar \
    -cp apps/target/hello.jar:apps/target/greeter.jar \
    org.my.Hello
```
If you specify a root directory that does not yet exist, it will be
created during population of the cache.

Note that in all the examples above the `DebugInfoSourceSearchPath`
options are actually redundant. In the first case, the classpath
entries for _apps/hello/classes_ and _apps/greeter/classes_ will be used
to derive the default search roots _apps/hello/src_ and
_apps/greeter/src_. In the second case, the classpath entries for
_apps/target/hello.jar_ and _apps/target/greeter.jar_ will be used to
derive the default search roots _apps/target/hello-sources.jar_ and
_apps/target/greeter-sources.jar_.


## Currently Implemented Features

The currently implemented features include:

  - break points configured by file and line, or by method name
  - single stepping by line including both into and over function calls
  - stack backtraces (not including frames detailing inlined code)
  - printing of primitive values
  - structured (field by field) printing of Java objects
  - casting/printing objects at different levels of generality
  - access through object networks via path expressions
  - reference by name to methods and static field data

Note that single stepping within a compiled method includes file and
line number info for inlined code, including inlined GraalVM methods.
So, GDB may switch files even though you are still in the same
compiled method.

### Special considerations for debugging Java from GDB

GDB does not currently include support for debugging of Java programs.
In consequence, debug capability has been implemented by generating debug
info that models the Java program as an equivalent C++ program. Java
class, array and interface references are modelled as pointers to
underlying C++ (class/struct) layout types.

So, for example in the DWARF debug info model `java.lang.String` names
a pointer type to a class (layout) type named `_java.lang.String`. The
layout type declares fields like `hash` of type `int` and `value` of
type `byte[]` and methods like `String(byte[])`, `charAt(int)`,
etc. It also inherits fields and methods from class (layout) type
_java.lang.Object using C++ public inheritance. The latter in turn
inherits standard oop header fields from a special struct class named
_objhdr which includes a pointer to the object's class.

The ptype command can be used to print details of a specific type. Note
that the java type name must be specified in quotes because to escape the
embedded `.` characters.

```
(gdb) ptype 
ptype 'java.lang.String'
type = class _java.lang.String : public _java.lang.Object {
  private:
    byte [] value;
    int hash;
    byte coder;
  public:
    void String(byte []);
    . . .
    char charAt(int);
    . . .
} *
```

The print command can be used to print the contents of a referenced object
field by field. Note how a cast is used to convert a raw memory address to
a reference of a specific Java type.

```
(gdb) print/x *('java.lang.String')0x7ffff7c01058
$1 = {
  <_java.lang.Object> = {
    <_objhdr> = {
      hub = 0x90c670
    }, <No data fields>}, 
  members of _java.lang.String:
  value = 0x7ffff7c01198,
  hash = 0x0,
  coder = 0x0
}
```

The hub field in the object header is actually a reference of type
`java.lang/Class`. Note that the field is typed using the pointer type
rather than the underlying layout type `_java.lang.Class`.

```
(gdb) ptype _objhdr
type = struct _objhdr {
    java.lang.Class hub;
}
```

Given a putative object reference it is possible to identify it's type by
printing the contents of the String referenced from the hub's name field.
First the value is cast to an object reference. Then a path expression is
used to dereference through the the hub field and the hub's name field to
the `byte[]` value array located in the name String.

```
(gdb) print/x ((_objhdr *)$rdi)
$19 = 0x7ffff7c01028
(gdb) print *$19->hub->name->value
$21 = {
  <_arrhdrB> = {
    hub = 0x90e0a8,
    len = 19,
    idHash = 797697923
  },
  members of _byte []:
  data = 0x9046d8 "[Ljava.lang.String;"
}
```

A simpler equivalent is as follows

```
(gdb) x/s $19->hub->name->value->data
0x9046d8:	"[Ljava.lang.String;"
```

The value in register rdx is obviously a reference to a String array.
Casting it to this type shows it has length 1.

```
(gdb) print *('java.lang.String[]')$rdi
$40 = {
  <_arrhdrA> = {
    hub = 0x906b30,
    len = 1,
    idHash = 0
  },
  members of _java.lang.String[]:
  data = 0x7ffff7c01038
}

```

Note that attempting to print the hub name for an invalid reference
will fail.

```
(gdb) p/x (_objhdr *)$rdx
$37 = 0x2
(gdb) x/s  $37->hub->name->value->data
Cannot access memory at address 0x2
```

Array type layouts are modelled with an array header whose fields
include the hub, array length, idhash plus type-specific padding
followed by a (zero length) array inlined into the object. The common
prefix for the array header and object header structs means that they
are all able to be viewed as oops.

```
(gdb) ptype 'java.lang.String[]'
type = struct _java.lang.String[] : public _arrhdrA {
    java.lang.String data[0];
} *
```

Interfaces layouts are modelled as union types whose members are the
layouts for all the classes which implement the interfacse.

```
(gdb) ptype 'java.lang.CharSequence'
type = union _java.lang.CharSequence {
    _java.lang.AbstractStringBuilder _java.lang.AbstractStringBuilder;
    _java.lang.StringBuffer _java.lang.StringBuffer;
    _java.lang.StringBuilder _java.lang.StringBuilder;
    _java.lang.String _java.lang.String;
    _java.nio.CharBuffer _java.nio.CharBuffer;
} *
```

Given a reference typed to an interface it can be resolved to the
relevant class type by viewing it through the relevant union element
```
(gdb) print (('java.lang.String[]')$rdi)->data[0]
$41 = (java.lang.String) 0x7ffff7c01058
(gdb) print ('java.lang.CharSequence')$41
$42 = (java.lang.CharSequence) 0x7ffff7c01058
(gdb) x/s ((_objhdr*)$42)->hub->name->value->data
0x7d97e0:	"java.lang.String\250", <incomplete sequence \340\220>
(gdb) print $42->'_java.lang.String'
$45 = {
  <_java.lang.Object> = {
    <_objhdr> = {
      hub = 0x90c670
    }, <No data fields>},
  members of _java.lang.String:
  value = 0x7ffff7c01198,
  hash = 0,
  coder = 0 '\000'
}
```

The current debug info model does not include the location info needed
to allow symbolic names for local vars and parameter vars to be resolved
to primitive values or object references. However, the debugger does
understand method names and static field names.

The following command places a breakpoint on the main entry point for
class `Hello`. Note that the GDB considers the method to belong to the
class (layout) type `_Hello` rather than the pointer type `Hello`. Also,
since GDB thinks this is a C++ program it uses the `::` separator to
separate the method name from the class name.

```
(gdb) info func ::main
All functions matching regular expression "::main":

File Hello.java:
	void _Hello::main(java.lang.String[]);
(gdb)  b _Hello::main
Breakpoint 1 at 0x4065a0: file Hello.java, line 43.
(gdb) x/4i _Hello::main
   0x4065a0 <_Hello::main(java.lang.String[])>:	sub    $0x8,%rsp
   0x4065a4 <_Hello::main(java.lang.String[])+4>:	cmp    0x8(%r15),%rsp
   0x4065a8 <_Hello::main(java.lang.String[])+8>:	jbe    0x4065fd <_Hello::main(java.lang.String[])+93>
   0x4065ae <_Hello::main(java.lang.String[])+14>:	callq  0x406050 <_Hello$Greeter::greeter(java.lang.String[])>
```

 An example of a static field containing Object data is provided by
 the static field `powerCache` in class `BigInteger`

```
(gdb) ptype 'java.math.BigInteger'
type = class _java.math.BigInteger : public _java.lang.Number {
  public:
    int [] mag;
    int signum;
  private:
    int bitLengthPlusOne;
    int lowestSetBitPlusTwo;
    int firstNonzeroIntNumPlusTwo;
    static java.math.BigInteger[][] powerCache;
    . . .
} *
(gdb) info var powerCache
All variables matching regular expression "powerCache":

File java/math/BigInteger.java:
	java.math.BigInteger[][] _java.math.BigInteger::powerCache;
```

The static variable name can be used to refer to the value stored in
this field. Note also that the address operator can be used identify
the location (address) of the field in the heap.

```
(gdb) p '_java.math.BigInteger'::powerCache
$56 = (java.math.BigInteger[][]) 0xa6fd98
(gdb) p &'_java.math.BigInteger'::powerCache
$55 = (java.math.BigInteger[][] *) 0xa6fbc8
```

gdb dereferences through symbolic names for static fields to access
the primitive value or object stored in the field

```
(gdb) p *'_java.math.BigInteger'::powerCache
$33 = {
  <_arrhdrA> = {
    hub = 0x9ab3d0,
    len = 37,
    idHash = 489620191
  },
  members of _java.math.BigInteger[][]:
  data = 0xa6fda8
}
(gdb) p '_java.math.BigInteger'::powerCache->data[0]@4
$51 = {0x0, 0x0, 0xc09378, 0xc09360}
(gdb) p *'_java.math.BigInteger'::powerCache->data[2]
$52 = {
  <_arrhdrA> = {
    hub = 0x919898,
    len = 1,
    idHash = 1796421813
  },
  members of _java.math.BigInteger[]:
  data = 0xc09388
}
(gdb) p *'_java.math.BigInteger'::powerCache->data[2]->data[0]
$53 = {
  <_java.lang.Number> = {
    <_java.lang.Object> = {
      <_objhdr> = {
        hub = 0x919c70
      }, <No data fields>}, <No data fields>},
  members of _java.math.BigInteger:
  mag = 0xa5b030,
  signum = 1,
  bitLengthPlusOne = 0,
  lowestSetBitPlusTwo = 0,
  firstNonzeroIntNumPlusTwo = 0
}
```

### Identifying the Location of Source Code

One goal of the implementation is to make it simple to configure your
debugger so that it can identify the relevant source file when it
stops during program execution. The native image builder tries to
achieve this by accumulating the relevant sources in a suitably
structured file cache.

The native image builder uses different strategies to locate source
files for JDK runtime classes, GraalVM classes, and application source
classes for inclusion in the local sources cache. It identifies which
strategy to use based on the package name of the class. So, for
example, packages starting with `java.*` or `jdk.*` are JDK classes;
packages starting with `org.graal.*` or `com.oracle.svm.*` are GraalVM
classes; any other packages are regarded as application classes.

Sources for JDK runtime classes are retrieved from the _src.zip_ found
in the JDK release used to run the native image generation process.
Retrieved files are cached under subdirectory _sources/jdk_, using the
module name (for JDK11) and package name of the associated class to
define the directory hierarchy in which the source is located.

For example, on Linux the source for `class java.util.HashMap` will
be cached in file _sources/jdk/java.base/java/util/HashMap.java_. Debug
info records for this class and its methods will identify this source
file using the relative directory path _java.base/java/util_ and file
name _HashMap.java_. On Windows things will be the same modulo use of
'\' rather than '/' as the file separator.

Sources for GraalVM classes are retrieved from zip files or source
directories derived from entries in the classpath. Retrieved files are
cached under subdirectory _sources/graal_, using the package name of the
associated class to define the directory hierarchy in which the source
is located (e.g., class `com.oracle.svm.core.VM` has its source file
cached at `sources/graal/com/oracle/svm/core/VM.java`).

The lookup scheme for cached GraalVM sources varies depending upon
what is found in each classpath entry. Given a JAR file entry like
_/path/to/foo.jar_, the corresponding file _/path/to/foo.src.zip_ is
considered as a candidate zip file system from which source files may
be extracted. When the entry specifies a dir like _/path/to/bar_ then
directories _/path/to/bar/src_ and _/path/to/bar/src_gen_ are considered
as candidates. Candidates are skipped when the zip file or source
directory does not exist, or it does not contain at least one
subdirectory hierarchy that matches one of the the expected GraalVM
package hierarchies.

Sources for application classes are retrieved from source JAR files or
source directories derived from entries in the classpath. Retrieved
files are cached under subdirectory _sources/src_, using the package
name of the associated class to define the directory hierarchy in
which the source is located (e.g., class `org.my.foo.Foo` has its
source file cached as `sources/src/org/my/foo/Foo.java`).

The lookup scheme for cached application sources varies depending upon
what is found in each classpath entry. Given a JAR file entry like
_/path/to/foo.jar_, the corresponding JAR _/path/to/foo-sources.jar_ is
considered as a candidate zip file system from which source files may
be extracted. When the entry specifies a dir like _/path/to/bar/classes_
or _/path/to/bar/target/classes_ then one of the directories
_/path/to/bar/src/main/java_, _/path/to/bar/src/java_ or _/path/to/bar/src_
is selected as a candidate (in that order of preference). Finally, the
current directory in which the Native Image program is being run is
also considered as a candidate.

These lookup strategies are only provisional and may need extending in the
future. However, it is possible to make missing sources
available by other means. One option is to unzip extra app source JAR files,
or copy extra app source trees into the cache. Another is to
configure extra source search paths.

### Configuring Source Paths in GNU Debugger

By default, GDB will employ the three local directory roots
`sources/{jdk,graal,src}` to locate the source files for your app classes, GraalVM
classes, and JDK runtime classes. If the sources cache is not located in the
directory in which you run GDB, you can configure the required paths using the
following command:
```
(gdb) set directories /path/to/sources/jdk:/path/to/sources/graal:/path/to/sources/src
```
THe `/path/to/sources/jdk` directory should contain source files for all JDK runtime
classes referenced from debug records.

The `/path/to/sources/graal` directory should contain source files for all GraalVM
classes referenced from debug records. Note that the current
implementation does not yet find some sources for the GraalVM JIT
compiler in the _org.graalvm.compiler*_ package subspace.

The `/path/to/sources/src` directory should contain source files for all
application classes referenced from debug records, assuming they can
be located using the lookup strategy described above.

You can supplement the files cached in `sources/src` by unzipping
application source JAR files or copying application source trees into the
cache. You will need to ensure that any new subdirectory you add to
`sources/src` corresponds to the top level package for the classes whose
sources are being included.

You can also add extra directories to the search path using the `set directories` command:
```shell
(gdb) set directories /path/to/my/sources/:/path/to/my/other/sources
```
Note that the GNU Debugger does not understand zip format file systems so any extra entries you
add must identify a directory tree containing the relevant
sources. Once again, top level entries in the directory added to the
search path must correspond to the top level package for the classes
whose sources are being included.

<!-- ### Configuring Source Paths in VS
TO BE ADDED -->

## Checking Debug Info on Linux

Note that this is only of interest to those who want to understand how the
debug info implementation works or want to troubleshoot problems
encountered during debugging that might relate to the debug info
encoding.

The `objdump` command can be used to display the debug info embedded
into a native image. The following commands (which all assume the
target binary is called `hello`) can be used to display all generated content:
```
objdump --dwarf=info hello > info
objdump --dwarf=abbrev hello > abbrev
objdump --dwarf=ranges hello > ranges
objdump --dwarf=decodedline hello > decodedline
objdump --dwarf=rawline hello > rawline
objdump --dwarf=str hello > str
objdump --dwarf=frames hello > frames
```

The *info* section includes details of all compiled Java methods.

The *abbrev* section defines the layout of records in the info section
that describe Java files (compilation units) and methods.

The *ranges* section details the start and end addresses of method
code segments.

The *decodedline* section maps subsegments of method code range
segments to files and line numbers. This mapping includes entries
for files and line numbers for inlined methods.

The *rawline* segment provides details of how the line table is
generated using DWARF state machine instructions that encode file,
line, and address transitions.

The *str* section provides a lookup table for strings referenced
from records in the info section.

The *frames* section lists transition points in compiled methods
where a (fixed size) stack frame is pushed or popped, allowing
the debugger to identify each frame's current and previous stack
pointers and its return address.

Note that some of the content embedded in the debug records is
generated by the C compiler and belongs to code that is either in
libraries or the C lib bootstrap code that is bundled in with the
Java method code.

### Currently Supported Targets

The prototype is currently implemented only for the GNU Debugger on Linux:

  - Linux/x86_64 support has been tested and should work
    correctly

  - Linux/AArch64 support is present but has not yet been fully
    verified (break points should work ok but stack backtraces
    may be incorrect)

Windows support is still under development.
