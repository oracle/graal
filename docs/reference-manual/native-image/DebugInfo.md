---
layout: docs
toc_group: native-image
link_title: Debug Info Feature
permalink: /reference-manual/native-image/DebugInfo/
---
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

## Source File Caching

The `GenerateDebugInfo` option also enables caching of sources for any
JDK runtime classes, GraalVM classes, and application classes which can
be located during native image generation. By default, the cache is created
alongside the generated native image in a subdirectory named `sources`. If a
target directory for the image is specified using option `-H:Path=...` then
the cache is also relocated under that same target. A command line option can
be used to provide an alternative path to `sources`. It is used to configure
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
    -H:-SpawnIsolates \
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
created in a directory named `sources`. The `DebugInfoSourceCacheRoot`
option can be used to specify an alternative path, which can be
absolute or relative. In the latter case the path is interpreted
relative to the target directory for the generated native image
specified via option `-H:Path` (which defaults to the current
working directory). As an example, the following variant of the
previous command specifies an absolute temporary directory path
constructed using the current process id:
```shell
SOURCE_CACHE_ROOT=/tmp/$$/sources
native-image -H:GenerateDebugInfo=1 \
    -H:-SpawnIsolates \
    -H:DebugInfoSourceCacheRoot=$SOURCE_CACHE_ROOT \
    -H:DebugInfoSourceSearchPath=apps/hello/target/hello-sources.jar,apps/greeter/target/greeter-sources.jar \
    -cp apps/target/hello.jar:apps/target/greeter.jar \
    org.my.Hello
```
The resulting cache directory will be something like `/tmp/1272696/sources`.

If the source cache path includes a directory that does not yet exist,
it will be created during population of the cache.

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

### Currently Missing Features

  - reference by name to values bound to parameter and local vars

This feature is scheduled for inclusion in a later release.

### Special considerations for debugging Java from GDB

GDB does not currently include support for debugging of Java programs.
In consequence, debug capability has been implemented by generating debug
info that models the Java program as an equivalent C++ program. Java
class, array and interface references are actually pointers to records
that contain the relevant field/array data. In the corresponding C++
model the Java name is used to label the underlying C++ (class/struct)
layout types and Java references appear as pointers.

So, for example in the DWARF debug info model `java.lang.String`
identifies a C++ class. This class layout type declares the expected
fields like `hash` of type `int` and `value` of type `byte[]` and
methods like `String(byte[])`, `charAt(int)`, etc. However, the copy
constructor which appears in Java as `String(String)` appears in gdb
with the signature `String(java.lang.String *)`.

The C++ layout class inherits fields and methods from class (layout)
type java.lang.Object using C++ public inheritance. The latter in turn
inherits standard oop (ordinary object pointer) header fields from a special struct class named
`_objhdr` which includes a single field called `hub` whose type is
`java.lang.Class *` i.e. it is a pointer to the object's class.

The ptype command can be used to print details of a specific type. Note
that the java type name must be specified in quotes because to escape the
embedded `.` characters.

```
(gdb) ptype 'java.lang.String'
type = class java.lang.String : public java.lang.Object {
  private:
    byte [] *value;
    int hash;
    byte coder;

  public:
    void String(byte [] *);
    void String(char [] *);
    void String(byte [] *, java.lang.String *);
    . . .
    char charAt(int);
    . . .
    java.lang.String * concat(java.lang.String *);
    . . .
}
```

The print command can be used to print the contents of a referenced object
field by field. Note how a cast is used to convert a raw memory address to
a reference for a specific Java type.

```
(gdb) print *('java.lang.String' *) 0x7ffff7c01060
$1 = {
  <java.lang.Object> = {
    <_objhdr> = {
      hub = 0x90cb58
    }, <No data fields>},
  members of java.lang.String:
  value = 0x7ffff7c011a0,
  hash = 0,
  coder = 0 '\000'
}
```

The `hub` field in the object header is actually a reference of Java type
`java.lang.Class`. Note that the field is typed by gdb using a pointer
to the underlying C++ class (layout) type.

All classes, from Object downwards inherit from a common, automatically
generated header type `_objhdr`. It is this header type which includes
the `hub` field:

```
(gdb) ptype _objhdr
type = struct _objhdr {
    java.lang.Class *hub;
    int idHash;
}

(gdb) ptype 'java.lang.Object'
type = class java.lang.Object : public _objhdr {
  public:
    void Object(void);
    . . .
```

Given an address that might be an object reference it is possible to
verify that case and identify the object's type by printing the
contents of the String referenced from the hub's name field.  First
the value is cast to an object reference. Then a path expression is
used to dereference through the the hub field and the hub's name field
to the `byte[]` value array located in the name String.

```
(gdb) print/x ((_objhdr *)$rdi)
$2 = 0x7ffff7c01028
(gdb) print *$2->hub->name->value
$3 = {
  <java.lang.Object> = {
    <_objhdr> = {
      hub = 0x942d40,
      idHash = 1806863149
    }, <No data fields>},
  members of byte []:
  len = 19,
  data = 0x923a90 "[Ljava.lang.String;"
}
```

The value in register `rdx` is obviously a reference to a String array.
Casting it to this type shows it has length 1.

```
(gdb) print *('java.lang.String[]' *)$rdi
$4 = {
  <java.lang.Object> = {
    <_objhdr> = {
      hub = 0x925be8,
      idHash = 0
    }, <No data fields>},
  members of java.lang.String[]:
  len = 1,
  data = 0x7ffff7c01038
}
```

A simpler command which allows just the name of the `hub` object to be
printed is as follows:

```
(gdb) x/s $2->hub->name->value->data
798:	"[Ljava.lang.String;"
```

Indeed it is useful to define a gdb command `hubname_raw` to execute this
operation on an arbitrary raw memory address.

```
define hubname_raw
  x/s (('java.lang.Object' *)($arg0))->hub->name->value->data
end

(gdb) hubname_raw $rdi
0x904798:	"[Ljava.lang.String;"
```

Attempting to print the hub name for an invalid reference will fail
safe, printing an error message.

```
(gdb) p/x $rdx
$5 = 0x2
(gdb) hubname $rdx
Cannot access memory at address 0x2
```

Array type layouts are modelled as a C++ class type. It inherits
from class Object so it includes the hub and idHash header fields
defined by `_objhdr`. It adds a length field and an embedded (C++) data
array whose elements are typed from the Java array's element type,
either primitive values or object references.

```
(gdb) ptype 'java.lang.String[]'
type = class java.lang.String[] : public java.lang.Object {
    int len;
    java.lang.String *data[0];
}
```

The embedded array is nominally sized with length 0. However, when a
Java array instance is allocated it includes enough space to ensure
the data array can store the number of items defined in the length
field.

Notice that in this case the type of the values stored in the data
array is `java.lang.String *`. The the C++ array stores Java
object references i.e. addresses as far as the C++ model is
concerned.

If gdb already knows the Java type for a reference it can be printed
without casting using a simpler version of the hubname command. For
example, the String array retrieved above as `$4` has a known type.

```
(gdb) ptype $4
type = class java.lang.String[] : public java.lang.Object {
    int len;
    java.lang.String *data[0];
}

define hubname
  x/s (($arg0))->hub->name->value->data
end

(gdb) hubname $4
0x923b68:	"[Ljava.lang.String;"
```

Interface layouts are modelled as C++ union types. The members of the
union include the C++ layout types for all Java classes which implement
the interface.

```
(gdb) ptype 'java.lang.CharSequence'
type = union java.lang.CharSequence {
    java.nio.CharBuffer _java.nio.CharBuffer;
    java.lang.AbstractStringBuilder _java.lang.AbstractStringBuilder;
    java.lang.String _java.lang.String;
    java.lang.StringBuilder _java.lang.StringBuilder;
    java.lang.StringBuffer _java.lang.StringBuffer;
}
```

Given a reference typed to an interface it can be resolved to the
relevant class type by viewing it through the relevant union element.

If we take the first String in the args array we can ask gdb to cast
it to interface `CharSequence`.

```
(gdb) print (('java.lang.String[]' *)$rdi)->data[0]
$5 = (java.lang.String *) 0x7ffff7c01060
(gdb) print ('java.lang.CharSequence' *)$5
$6 = (java.lang.CharSequence *) 0x7ffff7c01060
```

The `hubname` command will not work with this union type because it is
only objects of the elements of the union that include the hub field:

```
(gdb) hubname $6
There is no member named hub.
```

However, since all elements include the same header any one of them
can be passed to hubname in order to identify the actual type. This
allows the correct union element to be selected:

```
(gdb) hubname $6->'_java.nio.CharBuffer'
0x7d96d8:	"java.lang.String\270", <incomplete sequence \344\220>
(gdb) print $6->'_java.lang.String'
$18 = {
  <java.lang.Object> = {
    <_objhdr> = {
      hub = 0x90cb58
    }, <No data fields>},
  members of java.lang.String:
  value = 0x7ffff7c011a0,
  hash = 0,
  coder = 0 '\000'
}
```

Notice that the printed class name for the hub includes some trailing
characters. That's because a data array storing Java String text
is not guaranteed to be zero-terminated.

The current debug info model does not include the location info needed
to allow symbolic names for local vars and parameter vars to be
resolved to primitive values or object references. However, the
debugger does understand method names and static field names.

The following command places a breakpoint on the main entry point for
class `Hello`. Note that since GDB thinks this is a C++ method it uses
the `::` separator to separate the method name from the class name.

```
(gdb) info func ::main
All functions matching regular expression "::main":

File Hello.java:
	void Hello::main(java.lang.String[] *);
(gdb) x/4i Hello::main
=> 0x4065a0 <Hello::main(java.lang.String[] *)>:	sub    $0x8,%rsp
   0x4065a4 <Hello::main(java.lang.String[] *)+4>:	cmp    0x8(%r15),%rsp
   0x4065a8 <Hello::main(java.lang.String[] *)+8>:	jbe    0x4065fd <Hello::main(java.lang.String[] *)+93>
   0x4065ae <Hello::main(java.lang.String[] *)+14>:	callq  0x406050 <Hello$Greeter::greeter(java.lang.String[] *)>
(gdb) b Hello::main
Breakpoint 1 at 0x4065a0: file Hello.java, line 43.
```

An example of a static field containing Object data is provided by the static field `powerCache` in class `BigInteger`.

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
  public:
    void BigInteger(byte [] *);
    void BigInteger(java.lang.String *, int);
    . . .
}
(gdb) info var powerCache
All variables matching regular expression "powerCache":

File java/math/BigInteger.java:
	java.math.BigInteger[][] *java.math.BigInteger::powerCache;
```

The static variable name can be used to refer to the value stored in
this field. Note also that the address operator can be used identify
the location (address) of the field in the heap.

```
(gdb) p 'java.math.BigInteger'::powerCache
$8 = (java.math.BigInteger[][] *) 0xa6fd98
(gdb) p &'java.math.BigInteger'::powerCache
$9 = (java.math.BigInteger[][] **) 0xa6fbd8
```

The gdb dereferences through symbolic names for static fields to access
the primitive value or object stored in the field.

```
(gdb) p *'java.math.BigInteger'::powerCache
$10 = {
  <java.lang.Object> = {
    <_objhdr> = {
    hub = 0x9ab3d0,
    idHash = 489620191
    }, <No data fields>},
  members of _java.math.BigInteger[][]:
  len = 37,
  data = 0xa6fda8
}
(gdb) p 'java.math.BigInteger'::powerCache->data[0]@4
$11 = {0x0, 0x0, 0xc09378, 0xc09360}
(gdb) p *'java.math.BigInteger'::powerCache->data[2]
$12 = {
  <java.lang.Object> = {
    <_objhdr> = {
    hub = 0x919898,
    idHash = 1796421813
    }, <No data fields>},
  members of java.math.BigInteger[]:
  len = 1,
  data = 0xc09388
}
(gdb) p *'java.math.BigInteger'::powerCache->data[2]->data[0]
$14 = {
  <java.lang.Number> = {
    <java.lang.Object> = {
      <_objhdr> = {
        hub = 0x919bc8
      }, <No data fields>}, <No data fields>},
  members of java.math.BigInteger:
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
`\` rather than `/` as the file separator.

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
The `/path/to/sources/jdk` directory should contain source files for all JDK runtime
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

## Debugging with Isolates

Note that it is currently recommended to disable use of Isolates by
passing flag `-H:-SpawnIsolates` on the command line when debug info
generation is enabled. Enabling of Isolates affects the way ordinary object pointers (oops) are encoded. In turn, that means the debug info
generator has to provide gdb with information about how to translate
an encoded oop to the address in memory, where the object data is
stored. This sometimes requires care when asking gdb to process
encoded oops vs decoded raw addresses.

When isolates are disabled, oops are essentially raw addresses pointing
directly at the object contents. This is generally the same whether
the oop is embedded in a static/instance field or is referenced from a
local or parameter variable located in a register or saved to the stack.
It is not quite that simple because the bottom 3 bits of some oops may
be used to hold "tags" that record certain transient properties of
an object. However, the debuginfo provided to gdb means that it will
remove these tag bits before dereferencing the oop as an address.

By contrast, when isolates are enabled, oops references stored in static
or instance fields are actually relative addresses, offsets from a
dedicated heap base register (r14 on x86_64, r29 on AArch64), rather
than direct addresses (in a few special cases the offset may also have
some low tag bits set). When an 'indirect' oop of this kind gets loaded
during execution, it is almost always immediately converted to a 'raw'
address by adding the offset to the heap base register value. So, oops
which occur as the value of local or parameter vars are actually raw
addresses.

The DWARF info encoded into the image, when isolates are enabled, tells
gdb to rebase indirect oops whenever it tries to dereference them to
access underlying object data. This is normally automatic and
transparent, but it is visible in the underlying type model that gdb
displays when you ask for the type of objects.

For example, consider the static field we encountered above. Printing
its type in an image that uses Isolates shows that this (static) field
has a different type to the expected one:

```
(gdb) ptype 'java.math.BigInteger'::powerCache
type = class _z_.java.math.BigInteger[][] : public java.math.BigInteger[][] {
} *
```
The field is typed as `_z_.java.math.BigInteger[][]` which is an empty
wrapper class that inherits from the expected type
`java.math.BigInteger[][]`. This wrapper type is essentially the same
as the original but the DWARF info record that defines it includes
information that tells gdb how to convert pointers to this type.

When gdb is asked to print the oop stored in this field it is clear that
it is an offset rather than a raw address.

```
(gdb) p/x 'java.math.BigInteger'::powerCache
$1 = 0x286c08
(gdb) x/x 0x286c08
0x286c08:	Cannot access memory at address 0x286c08
```

However, when gdb is asked to dereference through the field, it applies
the necessary address conversion to the oop and fetches the correct
data.

```
(gdb) p/x *'java.math.BigInteger'::powerCache
$2 = {
  <java.math.BigInteger[][]> = {
    <java.lang.Object> = {
      <_objhdr> = {
        hub = 0x1ec0e2,
        idHash = 0x2f462321
      }, <No data fields>},
    members of java.math.BigInteger[][]:
    len = 0x25,
    data = 0x7ffff7a86c18
  }, <No data fields>}
```

Printing the type of the hub field or the data array shows that they
are also modelled using indirect types:

```
(gdb) ptype $1->hub
type = class _z_.java.lang.Class : public java.lang.Class {
} *
(gdb) ptype $2->data
type = class _z_.java.math.BigInteger[] : public java.math.BigInteger[] {
} *[0]
```

The gdb still knows how to dereference these oops:

```
(gdb) p $1->hub
$3 = (_z_.java.lang.Class *) 0x1ec0e2
(gdb) x/x $1->hub
0x1ec0e2:	Cannot access memory at address 0x1ec0e2
(gdb) p *$1->hub
$4 = {
  <java.lang.Class> = {
    <java.lang.Object> = {
      <_objhdr> = {
        hub = 0x1dc860,
        idHash = 1530752816
      }, <No data fields>},
    members of java.lang.Class:
    name = 0x171af8,
    . . .
  }, <No data fields>}

```

Since the indirect types inherit from the corresponding raw type it is
possible to use an expression that identifies an indirect type pointer
in almost all cases where an expression identifying a raw type pointer
would work. The only case case where care might be needed is when
casting a displayed numeric field value or displayed register value.

For example, if the indirect `hub` oop printed above is passed to
`hubname_raw`, the cast to type Object internal to that command fails to
force the required indirect oops translation. The resulting memory
access fails:

```
(gdb) hubname_raw 0x1dc860
Cannot access memory at address 0x1dc860
```

In this case it is necessary to use a slightly different command that
casts its argument to an indirect pointer type:
```
(gdb) define hubname_indirect
 x/s (('_z_.java.lang.Object' *)($arg0))->hub->name->value->data
end
(gdb) hubname_indirect 0x1dc860
0x7ffff78a52f0:	"java.lang.Class"
```
