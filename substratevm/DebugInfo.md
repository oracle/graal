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

## Debugging with Isolates

Note that it is currently recommended to disable use of Isolates by
passing flag `-H:-UseIsolates` on the command line when debug info
generation is enabled. Enabling of Isolates affects the way that oops
(object references) are encoded. In turn that means the debug info
generator has to provide gdb with information about how to translate
an encoded oop to the address in memory where the object data is
stored. This sometimes requires care when asking gdb to process
encoded oops vs decoded raw addresses.

When isolates are disabled oops are essentially raw addresses pointing
directly at the object contents. This is the same whether the oop is
stored in a static/instance field or has been loaded into a register.

When an oop is stored in a static or instance field gdb knows the type
of the value stored in the field and knows how to dereference it to
locate the object contents. For example, assume we have a `Units` type
that details the scale used for a blueprint drawing and that the
`Units` instance has a `String` field called `print_name`. Assume also
we have a static field `DEFAULT_UNIT` that holds the standard `Units`
instance. The following command will print the name for the default
units.

```
(gdb) print *com.acme.Blueprint::DEFAULT_UNIT->print_name
```

gdb knows the type of the oop stored in `Blueprint::DEFAULT_UNIT` and
knows how to dereference it to locate the object field
values. Likewise, it knows that the `print_name` field is a `String`
it will translate the oop stored in that field to an address where the
String contents are stored and it will print the values of the
`String` instance fields one by one.

If, say, an oop referring to the `print_name` String has been loaded
into $rdx it is still possible to print it using a straightforward
cast to the pointer type that gdb associates with oop references.

```
(gdb) print/x *('java.lang.String' *)$rdx
```

The raw address in the register is the same as the oop value stored
in the field.

By contrast, when isolates are enabled oop references stored in static
or instance fields are actually relative addresses, offsets from a
dedicated heap base register (r14 on x86_64, r29 on AArch64), rather
than direct addresses.  However, when an oop gets loaded during
execution it is almost always immediately converted to a direct
address by adding the offset to the heap base register value. The
DWARF info encoded into the image tells gdb to rebase object pointers
whenever it tries to dereference them to access the underlying object
data.

This still means gdb will do the right thing when it accesses an
object via a static field. When processing the field expression above
that prints the default unit name gdb will automatically rebase the
`Units` oop stored in field `DEFAULT_UNIT` by adding it to the heap
base register. It will then fetch and rebase the oop stored in its
`print_name` field to access the contents of the `String`.

However, this transformation won't work correctly in the second case
where gdb is passed an oop that has already been loaded into a
register and converted to a pointer. It is necessary to restore the
original oop by reverting it back to an offset:

```
(gdb) print/x *('java.lang.String' *)($rdx - $r14)
```

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

  - reference by name to values boudn to parameter and local vars

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
inherits standard oop header fields from a special struct class named
_objhdr which includes a single field called `hub` whose type is
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

The hub field in the object header is actually a reference of Java type
`java.lang.Class`. Note that the field is typed by gdb using a pointer
to the underlying C++ class (layout) type.

```
(gdb) ptype _objhdr
type = struct _objhdr {
    java.lang.Class *hub;
}
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
  <_arrhdrB> = {
    hub = 0x90e4b8,
    len = 19,
    idHash = 3759493
  }, 
  members of byte []:
  data = 0x904798 "[Ljava.lang.String;"
}
```

The value in register rdx is obviously a reference to a String array.
Casting it to this type shows it has length 1.

```
(gdb) print *('java.lang.String[]' *)$rdi
$4 = {
  <_arrhdrA> = {
    hub = 0x906a78,
    len = 2,
    idHash = 0
  }, 
  members of java.lang.String[]:
  data = 0x7ffff7c01038
}
```

A simpler command which allows just the name of the hub object to be
printed is as follows:

```
(gdb) x/s $2->hub->name->value->data
798:	"[Ljava.lang.String;"
```

Indeed it is useful to define a gdb command `hubname` to execute this
operation on an arbitrary input argument

```
command hubname
  x/s ((_objhdr *)($arg0))->hub->name->value->data
end

(gdb) hubname $2
0x904798:	"[Ljava.lang.String;"
```

Notice that the `hubname` command also masks out the low 3 flag bits in
the hub field that may sometimes get set by the runtime during program operation.

Attempting to print the hub name for an invalid reference will fail
safe, printing an error message.

```
(gdb) p/x (_objhdr *)$rdx
$5 = 0x2
(gdb) hubname $rdx
Cannot access memory at address 0x2
```

Array type layouts are modelled with a class. The class inherits
fields from an array header struct specific to the array element type,
one of _arrhdrZ _arrhdrB, _arrhdrS, ... _arrhdrA (the last one is for
object arrays). Inherited fields include the hub, array length, idHash
to round up the header size to a boundary suitable for the array
element type. The array class (layout) type includes only one field, a
C++ array of length zero whose element type is a primtiive type or
Java referece type.

```
(gdb) ptype 'java.lang.String[]'
type = struct java.lang.String[] : public _arrhdrA {
    java.lang.String *data[0];
}
```

Notice that the type of the values stored in the data array is
`java.lang.String *` i.e. the C++ array stores Java references
i.e. addresss as far as the C++ model is concerned.

The array header structs are all extensions of the basic _objhdr type
which means that arrays and objects can both be safely cast to oops.

```
(gdb) ptype _arrhdrA
type = struct _arrhdrA {
    java.lang.Class *hub;
    int len;
    int idHash;
}
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
}
```

Given a reference typed to an interface it can be resolved to the
relevant class type by viewing it through the relevant union element.

If we take the first String in the args array we can ask gdb to cast
it to interface CharSequence
```
(gdb) print (('java.lang.String[]' *)$rdi)->data[0]
$6 = (java.lang.String) 0x7ffff7c01060
(gdb) print ('java.lang.CharSequence')$6
$7 = (java.lang.CharSequence) 0x7ffff7c01060
```

The hubname command can be used to identify the actual type of the
object that implements this interface and that type name can be used
to select the union element used to print the object.

```
(gdb) hubname $7
0x7d96d8:	"java.lang.String\270", <incomplete sequence \344\220>
(gdb) print $7->'_java.lang.String'
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
$9 = (java.math.BigInteger[][] *) 0xa6fd98
(gdb) p &'java.math.BigInteger'::powerCache
$10 = (java.math.BigInteger[][] **) 0xa6fbd8
```

gdb dereferences through symbolic names for static fields to access
the primitive value or object stored in the field

```
(gdb) p *'java.math.BigInteger'::powerCache
$11 = {
  <_arrhdrA> = {
    hub = 0x9ab3d0,
    len = 37,
    idHash = 489620191
  },
  members of _java.math.BigInteger[][]:
  data = 0xa6fda8
}
(gdb) p 'java.math.BigInteger'::powerCache->data[0]@4
$12 = {0x0, 0x0, 0xc09378, 0xc09360}
(gdb) p *'java.math.BigInteger'::powerCache->data[2]
$13 = {
  <_arrhdrA> = {
    hub = 0x919898,
    len = 1,
    idHash = 1796421813
  },
  members of java.math.BigInteger[]:
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
