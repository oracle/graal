---
layout: ni-docs
toc_group: debugging-and-diagnostics
link_title: Debug Info Feature
permalink: /reference-manual/native-image/debugging-and-diagnostics/DebugInfo/
redirect_from: /$version/reference-manual/native-image/DebugInfo/
---

# Debug Info Feature

To add debug information to a generated native image, provide the `-g` option to the `native-image` builder:
```shell
native-image -g Hello
```

The `-g` flag instructs `native-image` to generate debug information.
The resulting image will contain debug records in a format the GNU Debugger (GDB) understands.
Additionally, you can pass `-O0` to the builder which specifies that no compiler optimizations should be performed.
Disabling all optimizations is not required, but in general it makes the debugging experience better.

Debug information is not just useful to the debugger. It can also be used by the Linux performance profiling tools `perf` and `valgrind` to correlate execution statistics such as CPU utilization or cache misses with specific, named Java methods and even link them to individual lines of Java code in the original Java source file.

By default, debug info will only include details of some of the values of parameters and local variables.
This means that the debugger will report many parameters and local variables as being undefined. If you pass `-O0` to the builder then full debug information will be included.
If you
want more parameter and local variable information to be included when employing higher
levels of optimization (`-O1` or, the default, `-O2`) you need to pass an extra command
line flag to the `native-image` command

```shell
native-image -g -H:+SourceLevelDebug Hello
```

Enabling debuginfo with flag `-g` does not make any difference to how a generated
native image is compiled and does not affect how fast it executes nor how much memory it uses at runtime.
However, it can significantly increase the size of the generated image on disk. Enabling full parameter
and local variable information by passing flag `-H:+SourceLevelDebug` can cause a program to be compiled
slightly differently and for some applications this can slow down execution.

The basic `perf report` command, which displays a histogram showing percentage execution time in each Java method, only requires passing flags `-g` and `-H:+SourceLevelDebug` to the `native-image` command.
However, more sophisticated uses of `perf` (i.e. `perf annotate`) and use of
`valgrind` requires debug info to be supplemented with linkage symbols identifying compiled Java methods.
Java method symbols are omitted from the generated native image by default but they can be retained achieved by passing one extra flag to the `native-image` command

```shell
native-image -g -H:+SourceLevelDebug -H:-DeleteLocalSymbols Hello
```

Use of this flag will result in a small increase in the size of the
resulting image file.

> Note: Native Image debugging currently works on Linux with initial support for macOS. The feature is experimental.

> Note: Debug info support for `perf` and `valgrind` on Linux is an experimental feature.

### Table of Contents

- [Source File Caching](#source-file-caching)
- [Special Considerations for Debugging Java from GDB](#special-considerations-for-debugging-java-from-gdb)
- [Identifying Source Code Location](#identifying-source-code-location)
- [Configuring Source Paths in GNU Debugger](#configuring-source-paths-in-gnu-debugger)
- [Checking Debug Info on Linux](#checking-debug-info-on-linux)
- [Debugging with Isolates](#debugging-with-isolates)
- [Debugging Helper Methods](#debugging-helper-methods)
- [Special Considerations for using perf and valgrind](#special-considerations-for-using-perf-and-valgrind)

## Source File Caching

The `-g` option also enables caching of sources for any JDK runtime classes, GraalVM classes, and application classes which can be located when generating a native executable.
By default, the cache is created alongside the generated binary in a subdirectory named `sources`.
If a target directory for the native executable is specified using option `-H:Path=...` then the cache is also relocated under that same target. 
Use a command line option to provide an alternative path to `sources` and to configure source file search path roots for the debugger.
Files in the cache are located in a directory hierarchy that matches the file path information included in the debug records of the native executable.
The source cache should contain all the files needed to debug the generated binary and nothing more.
This local cache provides a convenient way of making just the necessary sources available to the debugger or IDE when debugging a native executable.

The implementation tries to be smart about locating source files.
It uses the current `JAVA_HOME` to locate the JDK src.zip when searching for JDK runtime sources.
It also uses entries in the classpath to suggest locations for GraalVM source files and application source files (see below for precise details of the scheme used to identify source locations).
However, source layouts do vary and it may not be possible to find all sources.
Hence, users can specify the location of source files explicitly on the command line using option `DebugInfoSourceSearchPath`:

```shell
javac --source-path apps/greeter/src \
    -d apps/greeter/classes org/my/greeter/*Greeter.java
javac -cp apps/greeter/classes \
    --source-path apps/hello/src \
    -d apps/hello/classes org/my/hello/Hello.java
native-image -g \
    -H:-SpawnIsolates \
    -H:DebugInfoSourceSearchPath=apps/hello/src \
    -H:DebugInfoSourceSearchPath=apps/greeter/src \
    -cp apps/hello/classes:apps/greeter/classes org.my.hello.Hello
```

The `DebugInfoSourceSearchPath` option can be repeated as many times as required to notify all the target source locations.
The value passed to this option can be either an absolute or relative path.
It can identify either a directory, a source JAR, or a source ZIP file.
It is also possible to specify several source roots at once using a comma separator:

```shell
native-image -g \
    -H:DebugInfoSourceSearchPath=apps/hello/target/hello-sources.jar,apps/greeter/target/greeter-sources.jar \
    -cp apps/target/hello.jar:apps/target/greeter.jar \
    org.my.Hello
```

By default, the cache of application, GraalVM, and JDK sources is created in a directory named `sources`.
The `DebugInfoSourceCacheRoot` option can be used to specify an alternative path, which can be absolute or relative.
In the latter case the path is interpreted relative to the target directory for the generated executable specified via option `-H:Path` (which defaults to the current working directory).
As an example, the following variant of the previous command specifies an absolute temporary directory path constructed using the current process `id`:

```shell
SOURCE_CACHE_ROOT=/tmp/$$/sources
native-image -g \
    -H:-SpawnIsolates \
    -H:DebugInfoSourceCacheRoot=$SOURCE_CACHE_ROOT \
    -H:DebugInfoSourceSearchPath=apps/hello/target/hello-sources.jar,apps/greeter/target/greeter-sources.jar \
    -cp apps/target/hello.jar:apps/target/greeter.jar \
    org.my.Hello
```
The resulting cache directory will be something like `/tmp/1272696/sources`.

If the source cache path includes a directory that does not yet exist, it will be created during population of the cache.

Note that in all the examples above the `DebugInfoSourceSearchPath` options are actually redundant.
In the first case, the classpath entries for _apps/hello/classes_ and _apps/greeter/classes_ will be used to derive the default search roots _apps/hello/src_ and _apps/greeter/src_.
In the second case, the classpath entries for _apps/target/hello.jar_ and _apps/target/greeter.jar_ will be used to derive the default search roots _apps/target/hello-sources.jar_ and _apps/target/greeter-sources.jar_.

## Supported Features

The currently supported features include:

  - break points configured by file and line, or by method name
  - single stepping by line including both into and over function calls
  - stack backtraces (not including frames detailing inlined code)
  - printing of primitive values
  - structured (field by field) printing of Java objects
  - casting/printing objects at different levels of generality
  - access through object networks via path expressions
  - reference by name to methods and static field data
  - reference by name to values bound to parameter and local vars
  - reference by name to class constants

Note that single stepping within a compiled method includes file and line number info for inlined code, including inlined GraalVM methods.
So, GDB may switch files even though you are still in the same compiled method.

### Special considerations for debugging Java from GDB

GDB does not currently include support for Java debugging.
In consequence, debug capability has been implemented by generating debug info that models the Java program as an equivalent C++ program. 
Java class, array and interface references are actually pointers to records that contain the relevant field/array data.
In the corresponding C++ model the Java name is used to label the underlying C++ (class/struct) layout types and Java references appear as pointers.

So, for example in the DWARF debug info model `java.lang.String` identifies a C++ class.
This class layout type declares the expected fields like `hash` of type `int` and `value` of type `byte[]` and methods like `String(byte[])`, `charAt(int)`, etc. However, the copy constructor which appears in Java as `String(String)` appears in `gdb` with the signature `String(java.lang.String *)`.

The C++ layout class inherits fields and methods from class (layout) type `java.lang.Object` using C++ public inheritance.
The latter in turn inherits standard oop (ordinary object pointer) header fields from a special struct class named `_objhdr` which includes two fields. The first field is called
`hub` and its type is `java.lang.Class *` i.e. it is a pointer to the object's
class. The second field is called `idHash` and has type `int`. It stores an
identity hashcode for the object.

The `ptype` command can be used to print details of a specific type.
Note that the Java type name must be specified in quotes because to escape the embedded `.` characters.

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

The ptype command can also be used to identify the static type of a Java
data value. The current example session is for a simple hello world
program. Main method `Hello.main` is passed a single parameter
`args` whose Java type is `String[]`. If the debugger is stopped at
entry to `main` we can use `ptype` to print the type of `args`.
 
 ```
(gdb) ptype args
type = class java.lang.String[] : public java.lang.Object {
  public:
    int len;
    java.lang.String *data[0];
} *
```

There are a few details worth highlighting here. Firstly, the debugger
sees a Java array reference as a pointer type, as it does every Java object
reference.

Secondly, the pointer points to a structure, actually a C++ class,
that models the layout of the Java array using an integer length field
and a data field whose type is a C++ array embedded into the block of
memory that models the array object.

Elements of the array data field are references to the base type, in
this case pointers to `java.lang.String`. The data array has a nominal
length of 0. However, the block of memory allocated for the `String[]`
object actually includes enough space to hold the number of pointers
determined by the value of field `len`.

Finally, notice that the C++ class `java.lang.String[]` inherits from
the C++ class `java.lang.Object`. So, an array is still also an object.
In particular, as we will see when we print the object contents, this
means that every array also includes the object header fields that all
Java objects share.

The print command can be used to display the object reference as a memory
address. 

```
(gdb) print args
$1 = (java.lang.String[] *) 0x7ffff7c01130
```

It can also be used to print the contents of the object field by field. This
is achieved by dereferencing the pointer using the `*` operator.

```
(gdb) print *args
$2 = {
  <java.lang.Object> = {
    <_objhdr> = {
      hub = 0xaa90f0,
      idHash = 0
    }, <No data fields>}, 
  members of java.lang.String[]:
  len = 1,
  data = 0x7ffff7c01140
}
```

The array object contains embedded fields inherited from class
`_objhdr` via parent class `Object`. `_objhdr` is a synthetic type
added to the deubg info to model fields that are present at the start
of all objects. They include `hub` which is a reference to the object's
class and `hashId` a unique numeric hash code.

Clearly, the debugger knows the type (`java.lang.String[]`) and location
in memory (`0x7ffff7c010b8`) of local variable `args`. It also knows about
the layout of the fields embedded in the referenced object. This means
it is possible to use the C++ `.` and `->` operators in debugger commands
to traverse the underlying object data structures.

```
(gdb) print args->data[0]
$3 = (java.lang.String *) 0x7ffff7c01160
(gdb) print *args->data[0]
$4 = {
   <java.lang.Object> = {
     <_objhdr> = {
      hub = 0xaa3350
     }, <No data fields>},
   members of java.lang.String:
   value = 0x7ffff7c01180,
   hash = 0,
   coder = 0 '\000'
 }
(gdb) print *args->data[0]->value
$5 = {
  <java.lang.Object> = {
    <_objhdr> = {
      hub = 0xaa3068,
      idHash = 0
    }, <No data fields>}, 
  members of byte []:
  len = 6,
  data = 0x7ffff7c01190 "Andrew"
}
 ```

Returning to the `hub` field in the object header it was
mentioned before that this is actually a reference to the object's
class. This is actually an instance of Java type `java.lang.Class`.
Note that the field is typed by gdb using a pointer
to the underlying C++ class (layout) type.

```
(gdb) print args->hub
$6 = (java.lang.Class *) 0xaa90f0
```

All classes, from Object downwards inherit from a common, automatically generated header type `_objhdr`.
It is this header type which includes the `hub` field:

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

The fact that all objects have a common header pointing to a class
makes it possible to perform a simple test to decide if an address
is an object reference and, if so,  what the object's class is.
Given a valid object reference it is always possible to print the
contents of the `String` referenced from the `hub`'s name field.

Note that as a consequence, this allows every object observed by the debugger
to be downcast to its dynamic type. i.e. even if the debugger only sees the static
type of e.g. java.nio.file.Path we can easily downcast to the dynamic type, which
might be a subtype such as `jdk.nio.zipfs.ZipPath`, thus making it possible to inspect
fields that we would not be able to observe from the static type alone.
First the value is cast to an object reference.
Then a path expression is used to dereference through the the `hub` field and the `hub`'s name field to the `byte[]` value array located in the name `String`.

```
(gdb) print/x ((_objhdr *)$rdi)
$7 = (_objhdr *) 0x7ffff7c01130
(gdb) print *$7->hub->name->value
$8 = {
  <java.lang.Object> = {
    <_objhdr> = {
      hub = 0xaa3068,
      idHash = 178613527
    }, <No data fields>}, 
   members of byte []:
   len = 19,
  data = 0x8779c8 "[Ljava.lang.String;"
 }
```

The value in register `rdi` is obviously a reference to a String array.
Indeed, this is no coincidence. The example session has stopped at a break
point placed at the entry to `Hello.main` and at that point the value for
the `String[]` parameter `args` will be located in register `rdi`. Looking
back we can see that the value in `rdi` is the same value as was printed by
command `print args`. 

A simpler command which allows just the name of the `hub` object to be printed is as follows:

```
(gdb) x/s $7->hub->name->value->data
798:	"[Ljava.lang.String;"
```

Indeed it is useful to define a `gdb` command `hubname_raw` to execute this operation on an arbitrary raw memory address.

```
define hubname_raw
  x/s (('java.lang.Object' *)($arg0))->hub->name->value->data
end

(gdb) hubname_raw $rdi
0x8779c8:	"[Ljava.lang.String;"
```

Attempting to print the hub name for an invalid reference will fail
safe, printing an error message.

```
(gdb) p/x $rdx
$5 = 0x2
(gdb) hubname $rdx
Cannot access memory at address 0x2
```

If `gdb` already knows the Java type for a reference it can be printed without casting using a simpler version of the hubname command.
For example, the String array retrieved above as `$1` has a known type.

```
(gdb) ptype $1
type = class java.lang.String[] : public java.lang.Object {
    int len;
    java.lang.String *data[0];
} *

define hubname
  x/s (($arg0))->hub->name->value->data
end

(gdb) hubname $1
0x8779c8:	"[Ljava.lang.String;"
```

The native image heap contains a unique hub object (i.e. instance of
`java.lang.Class`) for every Java type that is included in the
image. It is possible to refer to these class constants using the
standard Java class literal syntax:

```
(gdb) print 'Hello.class'
$6 = {
  <java.lang.Object> = {
    <_objhdr> = {
      hub = 0xaabd00,
      idHash = 1589947226
    }, <No data fields>}, 
  members of java.lang.Class:
  typeCheckStart = 13,
  name = 0xbd57f0,
  ...
```

Unfortunately it is necessary to quote the class constant literal to
avoid gdb interpreting the embedded `.` character as a field access.

Note that the type of a class constant literal is `java.lang.Class`
rather than `java.lang.Class *`.

Class constants exist for Java instance classes, interfaces, array
classes and arrays, including primitive arrays:

```
(gdb)  print 'java.util.List.class'.name
$7 = (java.lang.String *) 0xb1f698
(gdb) print 'java.lang.String[].class'.name->value->data
$8 = 0x8e6d78 "[Ljava.lang.String;"
(gdb) print 'long.class'.name->value->data
$9 = 0xc87b78 "long"
(gdb) x/s  'byte[].class'.name->value->data
0x925a00:	"[B"
(gdb) 
```

Interface layouts are modelled as C++ union types.
The members of the union include the C++ layout types for all Java classes which implement the interface.

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

Given a reference typed to an interface it can be resolved to the relevant class type by viewing it through the relevant union element.

If we take the first String in the args array we can ask `gdb` to cast it to interface `CharSequence`.

```
(gdb) print args->data[0]
$10 = (java.lang.String *) 0x7ffff7c01160
(gdb) print ('java.lang.CharSequence' *)$10
$11 = (java.lang.CharSequence *) 0x7ffff7c01160
```

The `hubname` command will not work with this union type because it is only objects of the elements of the union that include the `hub` field:

```
(gdb) hubname $11
There is no member named hub.
```

However, since all elements include the same header any one of them can be passed to `hubname` in order to identify the actual type.
This allows the correct union element to be selected:

```
(gdb) hubname $11->'_java.nio.CharBuffer'
0x95cc58:	"java.lang.String`\302\236"
(gdb) print $11->'_java.lang.String'
$12 = {
  <java.lang.Object> = {
    <_objhdr> = {
      hub = 0xaa3350,
      idHash = 0
    }, <No data fields>},
  members of java.lang.String:
  hash = 0,
  value = 0x7ffff7c01180,
  coder = 0 '\000'
}
```

Notice that the printed class name for `hub` includes some trailing characters.
That is because a data array storing Java String text is not guaranteed to be zero-terminated.

The debugger does not just understand the name and type of local and
parameter variables. It also knows about method names and static field
names.

The following command places a breakpoint on the main entry point for class `Hello`.
Note that since GDB thinks this is a C++ method it uses the `::` separator to separate the method name from the class name.

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

The static variable name can be used to refer to the value stored in this field.
Note also that the address operator can be used identify the location (address) of the field in the heap.

```
(gdb) p 'java.math.BigInteger'::powerCache
$13 = (java.math.BigInteger[][] *) 0xced5f8
(gdb) p &'java.math.BigInteger'::powerCache
$14 = (java.math.BigInteger[][] **) 0xced3f0
```

The debugger dereferences through symbolic names for static fields to access the primitive value or object stored in the field.

```
(gdb) p *'java.math.BigInteger'::powerCache
$15 = {
  <java.lang.Object> = {
    <_objhdr> = {
    hub = 0xb8dc70,
    idHash = 1669655018
    }, <No data fields>},
  members of _java.math.BigInteger[][]:
  len = 37,
  data = 0xced608
}
(gdb) p 'java.math.BigInteger'::powerCache->data[0]@4
$16 = {0x0, 0x0, 0xed5780, 0xed5768}
(gdb) p *'java.math.BigInteger'::powerCache->data[2]
$17 = {
  <java.lang.Object> = {
    <_objhdr> = {
    hub = 0xabea50,
    idHash = 289329064
    }, <No data fields>},
  members of java.math.BigInteger[]:
  len = 1,
  data = 0xed5790
}
(gdb) p *'java.math.BigInteger'::powerCache->data[2]->data[0]
$18 = {
  <java.lang.Number> = {
    <java.lang.Object> = {
      <_objhdr> = {
        hub = 0xabed80
      }, <No data fields>}, <No data fields>},
  members of java.math.BigInteger:
  mag = 0xcbc648,
  signum = 1,
  bitLengthPlusOne = 0,
  lowestSetBitPlusTwo = 0,
  firstNonzeroIntNumPlusTwo = 0
}
```

## Identifying Source Code Location

One goal of the implementation is to make it simple to configure the debugger so that it can identify the relevant source file when it stops during program execution. The `native-image` tool tries to achieve this by accumulating the relevant sources in a suitably structured file cache.

The `native-image` tool uses different strategies to locate source files for JDK runtime classes, GraalVM classes, and application source classes for inclusion in the local sources cache.
It identifies which strategy to use based on the package name of the class.
So, for example, packages starting with `java.*` or `jdk.*` are JDK classes; packages starting with `org.graal.*` or `com.oracle.svm.*` are GraalVM classes; any other packages are regarded as application classes.

Sources for JDK runtime classes are retrieved from the _src.zip_ found in the JDK release used to run the native image generation process.
Retrieved files are cached under subdirectory _sources_, using the module name (for JDK11) and package name of the associated class to define the directory hierarchy in which the source is located.

For example, on Linux the source for `class java.util.HashMap` will be cached in file _sources/java.base/java/util/HashMap.java_.
Debug info records for this class and its methods will identify this source file using the relative directory path _java.base/java/util_ and file name _HashMap.java_. On Windows things will be the same modulo use of `\` rather than `/` as the file separator.

Sources for GraalVM classes are retrieved from ZIP files or source directories derived from entries in the classpath.
Retrieved files are cached under subdirectory _sources_, using the package name of the associated class to define the directory hierarchy in which the source is located (e.g., class `com.oracle.svm.core.VM` has its source file cached at `sources/com/oracle/svm/core/VM.java`).

The lookup scheme for cached GraalVM sources varies depending upon what is found in each classpath entry.
Given a JAR file entry like _/path/to/foo.jar_, the corresponding file _/path/to/foo.src.zip_ is considered as a candidate ZIP file system from which source files may be extracted.
When the entry specifies a directory like _/path/to/bar_, then directories _/path/to/bar/src_ and _/path/to/bar/src_gen_ are considered as candidates.
Candidates are skipped when the ZIP file or source directory does not exist, or it does not contain at least one subdirectory hierarchy that matches one of the the expected GraalVM package hierarchies.

Sources for application classes are retrieved from source JAR files or source directories derived from entries in the classpath.
Retrieved files are cached under subdirectory _sources_, using the package name of the associated class to define the directory hierarchy in which the source is located (e.g., class `org.my.foo.Foo` has its source file cached as `sources/org/my/foo/Foo.java`).

The lookup scheme for cached application sources varies depending upon what is found in each classpath entry.
Given a JAR file entry like _/path/to/foo.jar_, the corresponding JAR _/path/to/foo-sources.jar_ is considered as a candidate ZIP file system from which source files may be extracted.
When the entry specifies a dir like _/path/to/bar/classes_ or _/path/to/bar/target/classes_ then one of the directories
_/path/to/bar/src/main/java_, _/path/to/bar/src/java_ or _/path/to/bar/src_ is selected as a candidate (in that order of preference).
Finally, the current directory in which the native executable is being run is also considered as a candidate.

These lookup strategies are only provisional and may need extending in the future.
However, it is possible to make missing sources available by other means.
One option is to unzip extra app source JAR files, or copy extra app source trees into the cache.
Another is to configure extra source search paths.

## Configuring Source Paths in GNU Debugger

By default, GDB will employ the local directory root `sources` to locate the source files for your application classes, GraalVM classes, and JDK runtime classes.
If the sources cache is not located in the directory in which you run GDB, you can configure the required paths using the following command:

```
(gdb) set directories /path/to/sources/
```

The argument to the set directories command should identify the location of the sources cache as an absolute path or a relative path from the working directory of the `gdb` session.

Note that the current implementation does not yet find some sources for the GraalVM JIT compiler in the _org.graalvm.compiler*_ package subspace.

You can supplement the files cached in `sources` by unzipping application source JAR files or copying application source trees into the cache.
You will need to ensure that any new subdirectory you add to `sources` corresponds to the top level package for the classes whose sources are being included.

You can also add extra directories to the search path using the `set directories` command:
```shell
(gdb) set directories /path/to/my/sources/:/path/to/my/other/sources
```
Note that the GNU Debugger does not understand ZIP format file systems so any extra entries you add must identify a directory tree containing the relevant sources.
Once again, top level entries in the directory added to the search path must correspond to the top level package for the classes whose sources are being included.

## Checking Debug Info on Linux

Note that this is only of interest to those who want to understand how the debug info implementation works or want to troubleshoot problems encountered during debugging that might relate to the debug info encoding.

The `objdump` command can be used to display the debug info embedded into a native executable.
The following commands (which all assume the target binary is called `hello`) can be used to display all generated content:
```
objdump --dwarf=info hello > info
objdump --dwarf=abbrev hello > abbrev
objdump --dwarf=ranges hello > ranges
objdump --dwarf=decodedline hello > decodedline
objdump --dwarf=rawline hello > rawline
objdump --dwarf=str hello > str
objdump --dwarf=loc hello > loc
objdump --dwarf=frames hello > frames
```

The *info* section includes details of all compiled Java methods.

The *abbrev* section defines the layout of records in the info section that describe Java files (compilation units) and methods.

The *ranges* section details the start and end addresses of method code segments.

The *decodedline* section maps subsegments of method code range segments to files and line numbers.
This mapping includes entries for files and line numbers for inlined methods.

The *rawline* segment provides details of how the line table is generated using DWARF state machine instructions that encode file, line, and address transitions.

The *loc* section provides details of address ranges within
which parameter and local variables declared in the info section
are known to have a determinate value. The details identify where
the value is located, either in a machine register, on the stack or
at a specific address in memory.

The *str* section provides a lookup table for strings referenced from records in the info section.

The *frames* section lists transition points in compiled methods where a (fixed size) stack frame is pushed or popped, allowing the debugger to identify each frame's current and previous stack pointers and its return address.

Note that some of the content embedded in the debug records is generated by the C compiler and belongs to code that is either in libraries or the C lib bootstrap code that is bundled in with the Java method code.

### Currently Supported Targets

The prototype is currently implemented only for the GNU Debugger on Linux:

  - Linux/x86_64 support has been tested and should work correctly

  - Linux/AArch64 support is present but has not yet been fully verified (break points should work ok but stack backtraces may be incorrect)

Windows support is still under development.

## Debugging with Isolates

Enabling the use of [isolates](https://medium.com/graalvm/isolates-and-compressed-references-more-flexible-and-efficient-memory-management-for-graalvm-a044cc50b67e), by passing command line option `-H:-SpawnIsolates` to the `native-image` builder, affects the way ordinary object pointers (oops) are encoded.
In turn, that means the debug info generator has to provide `gdb` with information about how to translate an encoded oop to the address in memory, where the object data is stored.
This sometimes requires care when asking `gdb` to process encoded oops vs decoded raw addresses.

When isolates are disabled, oops are essentially raw addresses pointing directly at the object contents.
This is generally the same whether the oop is embedded in a static/instance field or is referenced from a local or parameter variable located in a register or saved to the stack.
It is not quite that simple because the bottom 3 bits of some oops may be used to hold "tags" that record certain transient properties of an object.
However, the debug info provided to `gdb` means that it will remove these tag bits before dereferencing the oop as an address.

By contrast, when isolates are enabled, oops references stored in static or instance fields are actually relative addresses, offsets from a dedicated heap base register (r14 on x86_64, r29 on AArch64), rather than direct addresses (in a few special cases the offset may also have some low tag bits set).
When an "indirect" oop of this kind gets loaded during execution, it is almost always immediately converted to a "raw" address by adding the offset to the heap base register value.
So, oops which occur as the value of local or parameter vars are actually raw addresses.

> Note that on some operating systems enabling isolates causes problems with printing of objects when using a `gdb` release version 10 or earlier. It is currently recommended to disable use of isolates, by passing command line option `-H:-SpawnIsolates`, when generating debug info if your operating system includes one of these earlier releases. Alternatively, you may be able to upgrade your debugger to a later version.

The DWARF info encoded into the image, when isolates are enabled, tells `gdb` to rebase indirect oops whenever it tries to dereference them to access underlying object data.
This is normally automatic and transparent, but it is visible in the underlying type model that `gdb` displays when you ask for the type of objects.

For example, consider the static field we encountered above.
Printing its type in an image that uses isolates shows that this static field has a different type to the expected one:

```
(gdb) ptype 'java.math.BigInteger'::powerCache
type = class _z_.java.math.BigInteger[][] : public java.math.BigInteger[][] {
} *
```
The field is typed as `_z_.java.math.BigInteger[][]` which is an empty wrapper class that inherits from the expected type `java.math.BigInteger[][]`.
This wrapper type is essentially the same as the original but the DWARF info record that defines it includes information that tells gdb how to convert pointers to this type.

When `gdb` is asked to print the oop stored in this field it is clear that it is an offset rather than a raw address.

```
(gdb) p/x 'java.math.BigInteger'::powerCache
$1 = 0x286c08
(gdb) x/x 0x286c08
0x286c08:	Cannot access memory at address 0x286c08
```

However, when `gdb` is asked to dereference through the field, it applies the necessary address conversion to the oop and fetches the correct data.

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

Printing the type of the `hub` field or the data array shows that they are also modelled using indirect types:

```
(gdb) ptype $1->hub
type = class _z_.java.lang.Class : public java.lang.Class {
} *
(gdb) ptype $2->data
type = class _z_.java.math.BigInteger[] : public java.math.BigInteger[] {
} *[0]
```

The debugger still knows how to dereference these oops:

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

Since the indirect types inherit from the corresponding raw type it is possible to use an expression that identifies an indirect type pointer in almost all cases where an expression identifying a raw type pointer would work.
The only case case where care might be needed is when casting a displayed numeric field value or displayed register value.

For example, if the indirect `hub` oop printed above is passed to `hubname_raw`, the cast to type Object internal to that command fails to force the required indirect oops translation.
The resulting memory access fails:

```
(gdb) hubname_raw 0x1dc860
Cannot access memory at address 0x1dc860
```

In this case it is necessary to use a slightly different command that casts its argument to an indirect pointer type:
```
(gdb) define hubname_indirect
 x/s (('_z_.java.lang.Object' *)($arg0))->hub->name->value->data
end
(gdb) hubname_indirect 0x1dc860
0x7ffff78a52f0:	"java.lang.Class"
```

## Debugging Helper Methods

On platforms where the debugging information is not fully supported, or when debugging complex issues, it can be helpful to print or query high-level information about the Native Image execution state.
For those scenarios, Native Image provides debug helper methods that can be embedded into a native executable by specifying the build-time option `-H:+IncludeDebugHelperMethods`.
While debugging, it is then possible to invoke those debug helper methods like any normal C method.
This functionality is compatible with pretty much any debugger.

While debugging with gdb, the following command can be used to list all debug helper methods that are embedded into the native image:
```
(gdb) info functions svm_dbg_
```

Before invoking a method, it is best to directly look at the source code of the Java class `DebugHelper` to determine which arguments each method expects.
For example, calling the method below prints high-level information about the Native Image execution state similar to what is printed for a fatal error:
```
(gdb) call svm_dbg_print_fatalErrorDiagnostics($r15, $rsp, $rip)
```

### Further Reading

- [Debugging Native Image in VS Code](Debugging.md)

## Special Considerations for using perf and valgrind

Debug info includes details of address ranges for top level and
inlined compiled method code as well as mappings from code addresses
to the corresponding source files and lines.
`perf` and `valgrind` are able to use this information for some of
their recording and reporting operations.
For example, `perf report` is able to associate code adresses sampled
during a `perf record` session with Java methods and print the
DWARF-derived method name for the method in its output histogram.

```
    . . .
    68.18%     0.00%  dirtest          dirtest               [.] _start
            |
            ---_start
               __libc_start_main_alias_2 (inlined)
               |          
               |--65.21%--__libc_start_call_main
               |          com.oracle.svm.core.code.IsolateEnterStub::JavaMainWrapper_run_5087f5482cc9a6abc971913ece43acb471d2631b (inlined)
               |          com.oracle.svm.core.JavaMainWrapper::run (inlined)
               |          |          
               |          |--55.84%--com.oracle.svm.core.JavaMainWrapper::runCore (inlined)
               |          |          com.oracle.svm.core.JavaMainWrapper::runCore0 (inlined)
               |          |          |          
               |          |          |--55.25%--DirTest::main (inlined)
               |          |          |          |          
               |          |          |           --54.91%--DirTest::listAll (inlined)
               . . .
```              

Unfortunately, other operations require Java methods to be identified
by an ELF (local) function symbol table entry locating the start of
the compiled method code.
In particular, assembly code dumps provided by both tools identify
branch and call targets using an offset from the nearest symbol.
Omitting Java method symbols means that offsets are generally
displayed relative to some unrelated global symbol, usually the entry
point for a method exported for invocation by C code.

As an illustration of the problem, the following excerpted output from
`perf annotate` displays the first few annotated instructions of the
compiled code for method `java.lang.String::String()`.

```
    . . .
         : 501    java.lang.String::String():
         : 521    public String(byte[] bytes, int offset, int length, Charset charset) {
    0.00 :   519d50: sub    $0x68,%rsp
    0.00 :   519d54: mov    %rdi,0x38(%rsp)
    0.00 :   519d59: mov    %rsi,0x30(%rsp)
    0.00 :   519d5e: mov    %edx,0x64(%rsp)
    0.00 :   519d62: mov    %ecx,0x60(%rsp)
    0.00 :   519d66: mov    %r8,0x28(%rsp)
    0.00 :   519d6b: cmp    0x8(%r15),%rsp
    0.00 :   519d6f: jbe    51ae1a <graal_vm_locator_symbol+0xe26ba>
    0.00 :   519d75: nop
    0.00 :   519d76: nop
         : 522    Objects.requireNonNull(charset);
    0.00 :   519d77: nop
         : 524    java.util.Objects::requireNonNull():
         : 207    if (obj == null)
    0.00 :   519d78: nop
    0.00 :   519d79: nop
         : 209    return obj;
    . . .
```

The leftmost column shows percentages for the amount of time recorded
at each instruction in samples obtained during the `perf record` run.
Each instruction is prefaced with it's address in the program's code
section.
The disassembly interleaves the source lines from which the code is
derived, 521-524 for the top level code and 207-209 for the code
inlined from from `Objects.requireNonNull()`.
Also, the start of the method is labelled with the name defined in the
DWARF debug info, `java.lang.String::String()`.
However, the branch instruction `jbe` at address `0x519d6f` uses a
very large offset from `graal_vm_locator_symbol`.
The printed offset does identify the correct address relative to the
location of the symbol.
However, this fails to make clear that the target address actually
lies within the compiled code range for method `String::String()` i.e. that thsi is a method-local branch.

Readability of the tool output is significantly improved if
option `-H-DeleteLocalSymbols` is passed to the `native-image`
command.
The equivalent `perf annotate` output with this option enabled is as
follows:

```
    . . .
         : 5      000000000051aac0 <String_constructor_f60263d569497f1facccd5467ef60532e990f75d>:
         : 6      java.lang.String::String():
         : 521    *          {@code offset} is greater than {@code bytes.length - length}
         : 522    *
         : 523    * @since  1.6
         : 524    */
         : 525    @SuppressWarnings("removal")
         : 526    public String(byte[] bytes, int offset, int length, Charset charset) {
    0.00 :   51aac0: sub    $0x68,%rsp
    0.00 :   51aac4: mov    %rdi,0x38(%rsp)
    0.00 :   51aac9: mov    %rsi,0x30(%rsp)
    0.00 :   51aace: mov    %edx,0x64(%rsp)
    0.00 :   51aad2: mov    %ecx,0x60(%rsp)
    0.00 :   51aad6: mov    %r8,0x28(%rsp)
    0.00 :   51aadb: cmp    0x8(%r15),%rsp
    0.00 :   51aadf: jbe    51bbc1 <String_constructor_f60263d569497f1facccd5467ef60532e990f75d+0x1101>
    0.00 :   51aae5: nop
    0.00 :   51aae6: nop
         : 522    Objects.requireNonNull(charset);
    0.00 :   51aae7: nop
         : 524    java.util.Objects::requireNonNull():
         : 207    * @param <T> the type of the reference
         : 208    * @return {@code obj} if not {@code null}
         : 209    * @throws NullPointerException if {@code obj} is {@code null}
         : 210    */
         : 211    public static <T> T requireNonNull(T obj) {
         : 212    if (obj == null)
    0.00 :   51aae8: nop
    0.00 :   51aae9: nop
         : 209    throw new NullPointerException();
         : 210    return obj;
    . . .
```

In this version the start address of the method is now labelled with
the mangled symbol name `String_constructor_f60263d569497f1facccd5467ef60532e990f75d`
as well as the DWARF name.
The branch target is now printed using an offset from that start
symbol.

Unfortunately, `perf` and `valgrind` do not correctly understand the
mangling algorithm employed by GraalVM, nor are they currently able to
replace the mangled name with the DWARF name in the disassembly even
though both symbol and DWARF function data are known to identify code
starting at the same address.
So, the branch instruction still prints its target using a symbol plus
offset but it is at least using the method symbol this time.

Also, because address `51aac0` is now recognized as a method start,
`perf` has preceded the first line of the method with 5 context lines,
which list the tail end of the method's javadoc comment.
Unfortunately, perf has numbered these lines incorrectly, labelling
the first comment with 521 rather than 516.

Executing command `perf annotate` will provide a disassembly listing
for all methods and C functions in the image.
It is possible to annotate a specific method by passing it's name as
an argument to the perf annotate command.
Note, however, that `perf` requries the mangled symbol name as
argument rather than the DWARF name.
So, in order to annotate method `java.lang.String::String()` it is
necessary to run command `perf annotate
String_constructor_f60263d569497f1facccd5467ef60532e990f75d`.

The `valgrind` tool `callgrind` also requires local symbols to be
retained in order to provide high quality output.
When `callgrind` is used in combination with a viewer like
`kcachegrind` it is possible to identify a great deal of valuable
information about native image execution aand relate it back to
specific source code lines.
