---
layout: ni-docs
toc_group: how-to-guides
link_title: Debug Native Executables with GDB
permalink: /reference-manual/native-image/guides/debug-native-image-process/
---

# Debug Native Executables with GDB

### Which GDB to Use?

* Please use GDB 10.2 or later. The debug info is tested via `mx debuginfotest` against 10.2.
* Note that later versions might have slightly different formatting of debugger output (which, for example, may cause CI/CD gate checks to fail)
* GDB bundled in recent Linux releases works just fine for debugging sessions 

### Build a Native Executable with Debug Information

To build a native executable with debug information, provide the `-g` command-line option for `javac` when compiling the application, and then to the `native-image` builder. 
This enables source-level debugging, and the debugger (GDB) then correlates machine instructions with specific source lines in Java files.

Adding `-g` to the `native-image` arguments causes debuginfo to be generated. 
Next to the native executable, there will be an _&lt;executable_name&gt;.debug_ file that contains debuginfo and a _sources/_ directory that contains Java source files, which the debugger uses to show sources for lineinfo. For example:
```
hello_image
hello_image.debug
sources
```

GDB automatically loads the _&lt;executable_name&gt;.debug_ file for a given native executable `<executable_name>`. (There is a link between the native executable and its _*.debug_  file)

> For a better debugging experience, we recommend combining `-g` with `-O0`.
The latter option disables inlining and other optimizations of the Graal compiler, which otherwise would be observable in the debugger (for example, the debugger may jump back and forth between lines instead of allowing you to step from one line to the next one).
At the same time, `-O0` also enables additional metadata to be collected in the compiler, which then helps the debugger to resolve, for example, local variables.

### Use GDB with New Debug Information

#### Build Information

The _*.debug_ file contains additional information about the build, which can be accessed as follows:
```
readelf -p .debug.svm.imagebuild.classpath hello_image.debug
```

It gives a list of all classpath entries that were used to build the native executable:
```
String dump of section '.debug.svm.imagebuild.classpath':
  [     0]  /home/user/.mx/cache/HAMCREST_e237ae735aac4fa5a7253ec693191f42ef7ddce384c11d29fbf605981c0be077d086757409acad53cb5b9e53d86a07cc428d459ff0f5b00d32a8cbbca390be49/hamcrest.jar
  [    b0]  /home/user/.mx/cache/JUNIT_5974670c3d178a12da5929ba5dd9b4f5ff461bdc1b92618c2c36d53e88650df7adbf3c1684017bb082b477cb8f40f15dcf7526f06f06183f93118ba9ebeaccce/junit.jar
  [   15a]  /home/user/mx/mxbuild/jdk20/dists/jdk9/junit-tool.jar
  [   1a9]  /home/user/graal/substratevm/mxbuild/jdk20/com.oracle.svm.test/bin
```

The following sections are available
* .debug.svm.imagebuild.classpath
* .debug.svm.imagebuild.modulepath
* .debug.svm.imagebuild.arguments
* .debug.svm.imagebuild.java.properties

#### Where is the `main()` Method?

Use
```
info functions ::main
```
to find all methods named `main` and then use `b <main method name>`, for example:
```
(gdb) info functions ::main
All functions matching regular expression "::main":

File hello/Hello.java:
76:	void hello.Hello::main(java.lang.String[]*);

File java/util/Timer.java:
534:	void java.util.TimerThread::mainLoop();
(gdb) b 'hello.Hello::main'

Breakpoint 1 at 0x83c030: file hello/Hello.java, line 76.
```

#### Set a Breakpoint

First, find the type of the method you want to set a breakpoint in, for example:
```
(gdb) info types ArrayList
All types matching regular expression "ArrayList":

...
File java/util/ArrayList.java:
	java.util.ArrayList;
	java.util.ArrayList$ArrayListSpliterator;
	java.util.ArrayList$Itr;
	java.util.ArrayList$ListItr;
...
```

Now use the following GDB autocompletion:
```
(gdb) b 'java.util.ArrayList::
```

Pressing tab twice now shows all `ArrayList` methods to choose from:
```
java.util.ArrayList::ArrayList(int)                                                java.util.ArrayList::iterator()
java.util.ArrayList::ArrayList(java.util.Collection*)                              java.util.ArrayList::lastIndexOf(java.lang.Object*)
java.util.ArrayList::add(int, java.lang.Object*)                                   java.util.ArrayList::lastIndexOfRange(java.lang.Object*, int, int)
java.util.ArrayList::add(java.lang.Object*)                                        java.util.ArrayList::listIterator()
java.util.ArrayList::add(java.lang.Object*, java.lang.Object[]*, int)              java.util.ArrayList::listIterator(int)
java.util.ArrayList::addAll(int, java.util.Collection*)                            java.util.ArrayList::nBits(int)
java.util.ArrayList::addAll(java.util.Collection*)                                 java.util.ArrayList::outOfBoundsMsg(int)
...
```
If to complete with
```
(gdb) b 'java.util.ArrayList::add`
```
breakpoints in all variants of `add` are installed.

#### Arrays

Arrays have a **`data` field** that can be accessed via an index to get the individual array elements, for example:
```
Thread 1 "hello_image" hit Breakpoint 1, hello.Hello::main(java.lang.String[]*) (args=0x7ff33f800898) at hello/Hello.java:76
76	        Greeter greeter = Greeter.greeter(args);
(gdb) p args
$1 = (java.lang.String[] *) 0x7ff33f800898
(gdb) p *args
$2 = {
  <java.lang.Object> = {
    <_objhdr> = {
      hub = 0x1e37be0
    }, <No data fields>}, 
  members of java.lang.String[]:
  len = 4,
  data = 0x7ff33f8008a0
}
(gdb) p args.data
$3 = 0x7ff33f8008a0
(gdb) ptype args.data
type = class _z_.java.lang.String : public java.lang.String {
} *[0]
```

Here `args.data` can be accessed via an index.

In this case, the first of the four array elements is a pointer to a String:
```
(gdb) p args.data[0]
$4 = (_z_.java.lang.String *) 0x27011a
```

#### Strings

To see the actual contents of a Java String object, look at its **`value` field**, for example:
```
(gdb) p args.data[0]
$4 = (_z_.java.lang.String *) 0x27011a
```

`args.data[0]` points to a String object. Let's deref:
```
(gdb) p *args.data[0]
$5 = {
  <java.lang.String> = {
    <java.lang.Object> = {
      <_objhdr> = {
        hub = 0x1bb4780
      }, <No data fields>}, 
    members of java.lang.String:
    value = 0x270118,
    hash = 0,
    coder = 0 '\000',
    hashIsZero = false,
    static CASE_INSENSITIVE_ORDER = 0x19d752,
    ...
    static COMPACT_STRINGS = true
  }, <No data fields>}
```

The `value` field holds the String data.
Let's check the type of `value`:
```
(gdb) p args.data[0].value
$3 = (_z_.byte[] *) 0x250119
```

`value` is of type `byte[]`.

As you already learned before, the elements of an array can be accessed via its `data` field.
```
(gdb) p args.data[0].value.data
$10 = 0x7ff33f8008c8 "this\376\376\376\376\200G\273\001\030\001'"
```

GDB is smart enough to interpret the byte-pointer as a C string out of the box.
But in essence, it is an array.
The following gives us the `t` from `this`.
```
(gdb) p args.data[0].value.data[0]
$13 = 116 't'
```

The reason for the garbage after the last char is that Java String values are not 0-terminated (unlike C strings).
To know where the garbage starts you can inspect the `len` field.
```
(gdb) p args.data[0].value.len
$14 = 4
```

#### Downcasting

Suppose your source uses a variable of static type `Greeter` and you want to inspect its data.
```
75	    public static void main(String[] args) {
76	        Greeter greeter = Greeter.greeter(args);
77	        greeter.greet(); // Here you might have a NamedGreeter
```
As you can see, currently GDB only knows about the static type of greeter in line 77:
```
Thread 1 "hello_image" hit Breakpoint 2, hello.Hello::main(java.lang.String[]*) (args=<optimized out>) at hello/Hello.java:77
77	        greeter.greet();
(gdb) p greeter
$17 = (hello.Hello$Greeter *) 0x7ff7f9101208
```

Also, you are not able to see fields that only exist for the `NamedGreeter` subclass.
```
(gdb) p *greeter
$18 = {
  <java.lang.Object> = {
    <_objhdr> = {
      hub = 0x1d1cae0
    }, <No data fields>}, <No data fields>}
```

But you do have the `hub` field, which points to the class-object of an object.
Therefore, it allows you to determine the runtime-type of the Greeter object at address `0x7ff7f9101208`:
```
(gdb) p greeter.hub
$19 = (_z_.java.lang.Class *) 0x1d1cae0
(gdb) p *greeter.hub
$20 = {
  <java.lang.Class> = {
    <java.lang.Object> = {
      <_objhdr> = {
        hub = 0x1bec910
      }, <No data fields>}, 
    members of java.lang.Class:
    typeCheckStart = 1188,
    name = 0xb94a2, <<<< WE ARE INTERESTED IN THIS FIELD
    superHub = 0x90202,
    ...
    monitorOffset = 8,
    optionalIdentityHashOffset = 12,
    flags = 0,
    instantiationFlags = 3 '\003'
  }, <No data fields>}
(gdb) p greeter.hub.name
$21 = (_z_.java.lang.String *) 0xb94a2
(gdb) p greeter.hub.name.value.data
$22 = 0x7ff7f80705b8 "hello.Hello$NamedGreeter\351\001~*"
```

So you learned that the actual type of that object is `hello.Hello$NamedGreeter`.

Now cast to that type:
```
(gdb) set $rt_greeter = ('hello.Hello$NamedGreeter' *) greeter
```

Now you can inspect the downcasted convenience variable `rt_greeter`:
```
(gdb) p $rt_greeter
$23 = (hello.Hello$NamedGreeter *) 0x7ff7f9101208
(gdb) p *$rt_greeter
$24 = {
  <hello.Hello$Greeter> = {
    <java.lang.Object> = {
      <_objhdr> = {
        hub = 0x1d1cae0
      }, <No data fields>}, <No data fields>}, 
  members of hello.Hello$NamedGreeter:
  name = 0x270119
}
```

Now you can see the `name` field that only exists in the `NamedGreeter` subtype.
```
(gdb) p $rt_greeter.name
$25 = (_z_.java.lang.String *) 0x270119
```

So the `name` field is of type String. You already know how to see the contents of a String:
```
(gdb) p $rt_greeter.name.value.data
$26 = 0x7ff7f91008c0 "FooBar\376\376\200G\273\001\027\001'"
```

> Note: If the static type that you want to downcast from is a compressed reference then the type used in the downcast also needs to be that of a compressed reference.

For example, if you have:
```
(gdb) p elementData.data[0]

$38 = (_z_.java.lang.Object *) 0x290fcc
```

In the internal array of an `ArrayList`, the first entry points to a `java.lang.Object` with a `_z_.` prefix, which denotes that this is a **compressed ref**.

To check what the runtime-type of that object is, use:
```
(gdb) p elementData.data[0].hub.name.value.data

$40 = 0x7ff7f8665600 "java.lang.String=\256\271`"
```

Now you know that the compressed ref actually points to a `java.lang.String`.

**Then, when you cast, do not forget to use the `_z_.` prefix.**
```
(gdb) p ('_z_.java.lang.String' *) elementData.data[0]

$41 = (_z_.java.lang.String *) 0x290fcc
(gdb) p *$41

$43 = {
  <java.lang.String> = {
    <java.lang.Object> = {
      <_objhdr> = {
        hub = 0x1bb4780
      }, <No data fields>}, 
    members of java.lang.String:
    value = 0x290fce,
    ...
```

To see the contents of that String, again use:
```
(gdb) p $41.value.data

$44 = 0x7ff7f9207e78 "#subsys_name\thierarchy\tnum_cgroups\tenabled"
```

#### Using the `this` variable in instance methods

```
(gdb) bt
#0  hello.Hello$NamedGreeter::greet() (this=0x7ff7f9101208) at hello/Hello.java:71
#1  0x000000000083c060 in hello.Hello::main(java.lang.String[]*) (args=<optimized out>) at hello/Hello.java:77
#2  0x0000000000413355 in com.oracle.svm.core.JavaMainWrapper::runCore0() () at com/oracle/svm/core/JavaMainWrapper.java:178
#3  0x00000000004432e5 in com.oracle.svm.core.JavaMainWrapper::runCore() () at com/oracle/svm/core/JavaMainWrapper.java:136
#4  com.oracle.svm.core.JavaMainWrapper::doRun(int, org.graalvm.nativeimage.c.type.CCharPointerPointer*) (argc=<optimized out>, argv=<optimized out>) at com/oracle/svm/core/JavaMainWrapper.java:233
#5  com.oracle.svm.core.JavaMainWrapper::run(int, org.graalvm.nativeimage.c.type.CCharPointerPointer*) (argc=<optimized out>, argv=<optimized out>) at com/oracle/svm/core/JavaMainWrapper.java:219
#6  com.oracle.svm.core.code.IsolateEnterStub::JavaMainWrapper_run_e6899342f5939c89e6e2f78e2c71f5f4926b786d(int, org.graalvm.nativeimage.c.type.CCharPointerPointer*) (__0=<optimized out>, __1=<optimized out>)
at com/oracle/svm/core/code/IsolateEnterStub.java:1
(gdb) p this
$1 = (hello.Hello$NamedGreeter *) 0x7ff7f9001218
(gdb) p *this
$2 = {
  <hello.Hello$Greeter> = {
    <java.lang.Object> = {
      <_objhdr> = {
        hub = 0x1de2260
      }, <No data fields>}, <No data fields>}, 
  members of hello.Hello$NamedGreeter:
  name = 0x25011b
}
(gdb) p this.name
$3 = (_z_.java.lang.String *) 0x270119
```

Just like in Java or C++ code, in instance-methods, prefixing with `this.` is not needed. 
```
(gdb) p name
$7 = (_z_.java.lang.String *) 0x270119
(gdb) p name.value.data
$8 = 0x7ff7f91008c0 "FooBar\376\376\200G\273\001\027\001'"
```

#### Accessing static fields

While static fields are shown whenever an instance of an object is printed, you just want to see the value of a specific static field.
```
(gdb) p 'java.math.BigDecimal::BIG_TEN_POWERS_TABLE'
$23 = (_z_.java.math.BigInteger[] *) 0x132b95
```

To get a list of all static fields, use:
```
(gdb) info variables ::
```

#### Inspecting `.class` Objects

For every Java type in the image, there exists an easy way to access its class object (aka the hub).
```
(gdb) info types PrintStream
All types matching regular expression "PrintStream":

...
File java/io/PrintStream.java:
	java.io.PrintStream;
	java.io.PrintStream$1;
...
```

To access the hub of `java.io.PrintStream`, you can use the `.class` suffix:
```
(gdb) p 'java.io.PrintStream.class'
$4 = {
  <java.lang.Object> = {
    <_objhdr> = {
      hub = 0x1bec910
    }, <No data fields>}, 
  members of java.lang.Class:
  typeCheckStart = 1340,
  name = 0xbab58,
  superHub = 0x901ba,
  ...
  sourceFileName = 0xbab55,
  classInitializationInfo = 0x14d189,
  module = 0x14cd8d,
  nestHost = 0xde78d,
  simpleBinaryName = 0x0,
  companion = 0x149856,
  signature = 0x0,
  ...
}
```

This allows you, for example, to check which module `java.io.PrintStream` belongs to:
```
(gdb) p 'java.io.PrintStream.class'.module.name.value.data
$12 = 0x7ff7f866b000 "java.base"
```

#### Inlined methods

Setting a breakpoint in `PrintStream.writeln`
```
(gdb) b java.io.PrintStream::writeln
Breakpoint 2 at 0x4080cb: java.io.PrintStream::writeln. (35 locations)
```

Now you navigate to:
```
(gdb) bt
#0  java.io.BufferedWriter::min(int, int) (this=<optimized out>, a=8192, b=14) at java/io/BufferedWriter.java:216
#1  java.io.BufferedWriter::implWrite(java.lang.String*, int, int) (this=0x7ff7f884e828, s=0x7ff7f9101230, off=<optimized out>, len=<optimized out>) at java/io/BufferedWriter.java:329
#2  0x000000000084c50d in java.io.BufferedWriter::write(java.lang.String*, int, int) (this=<optimized out>, s=<optimized out>, off=<optimized out>, len=<optimized out>) at java/io/BufferedWriter.java:313
#3  0x0000000000901369 in java.io.Writer::write(java.lang.String*) (this=<optimized out>, str=<optimized out>) at java/io/Writer.java:278
#4  0x00000000008df465 in java.io.PrintStream::implWriteln(java.lang.String*) (this=0x7ff7f87e67b8, s=<optimized out>) at java/io/PrintStream.java:846
#5  0x00000000008e10a5 in java.io.PrintStream::writeln(java.lang.String*) (this=0x7ff7f87e67b8, s=<optimized out>) at java/io/PrintStream.java:826
#6  0x000000000083c00c in java.io.PrintStream::println(java.lang.String*) (this=<optimized out>, x=<optimized out>) at java/io/PrintStream.java:1168
#7  hello.Hello$NamedGreeter::greet() (this=<optimized out>) at hello/Hello.java:71
#8  0x000000000083c060 in hello.Hello::main(java.lang.String[]*) (args=<optimized out>) at hello/Hello.java:77
#9  0x0000000000413355 in com.oracle.svm.core.JavaMainWrapper::runCore0() () at com/oracle/svm/core/JavaMainWrapper.java:178
#10 0x00000000004432e5 in com.oracle.svm.core.JavaMainWrapper::runCore() () at com/oracle/svm/core/JavaMainWrapper.java:136
#11 com.oracle.svm.core.JavaMainWrapper::doRun(int, org.graalvm.nativeimage.c.type.CCharPointerPointer*) (argc=<optimized out>, argv=<optimized out>) at com/oracle/svm/core/JavaMainWrapper.java:233
#12 com.oracle.svm.core.JavaMainWrapper::run(int, org.graalvm.nativeimage.c.type.CCharPointerPointer*) (argc=<optimized out>, argv=<optimized out>) at com/oracle/svm/core/JavaMainWrapper.java:219
#13 com.oracle.svm.core.code.IsolateEnterStub::JavaMainWrapper_run_e6899342f5939c89e6e2f78e2c71f5f4926b786d(int, org.graalvm.nativeimage.c.type.CCharPointerPointer*) (__0=<optimized out>, __1=<optimized out>)
    at com/oracle/svm/core/code/IsolateEnterStub.java:1
```

If you query extra info about the top frame, you see that `min` got inlined into `implWrite`:
```
(gdb) info frame
Stack level 0, frame at 0x7fffffffdb20:
 rip = 0x84af8a in java.io.BufferedWriter::min(int, int) (java/io/BufferedWriter.java:216); saved rip = 0x84c50d
 inlined into frame 1
 source language unknown.
 Arglist at unknown address.
 Locals at unknown address, Previous frame's sp in rsp
```

Now stepping into the use-site of `min`, you see that value `14` was returned by `min` (as expected):
```
(gdb) bt
#0  java.lang.String::getChars(int, int, char[]*, int) (this=0x7ff7f9101230, srcBegin=0, srcEnd=14, dst=0x7ff7f858ac58, dstBegin=0) at java/lang/String.java:1688
#1  java.io.BufferedWriter::implWrite(java.lang.String*, int, int) (this=0x7ff7f884e828, s=0x7ff7f9101230, off=<optimized out>, len=<optimized out>) at java/io/BufferedWriter.java:330
...
```

#### Calling `svm_dbg_` helper functions during debugging

When the image gets built with `-H:+IncludeDebugHelperMethods`, additional `@CEntryPoint` functions are defined that can be called from GDB during debugging, for example:
```
(gdb) p greeter 
$3 = (hello.Hello$Greeter *) 0x7ffff6881900
```

Here again, you have a local named `greeter` with the static-type `hello.Hello$Greeter`.
To see its runtime-type, you can use the methods already described above.

Alternatively, you can make use of the `svm_dbg_` helper functions.
For example, from within the running debug session, you can call:
```java
void svm_dbg_print_hub(graal_isolatethread_t* thread, size_t hubPtr)
```

You have to pass a value for `graal_isolatethread_t` and the absolute address of the hub you want to get printed.
In most situations, the value for `graal_isolatethread_t` is just the value of the current `IsolateThread` that can be found in a platform-specific register:

| Platform  | Register |
| --------- | -------- |
| `amd64`   | `$r15`   |
| `aarch64` | `$r28`   |

Finally, before you can call `svm_dbg_print_hub`, make sure you have the **absolute address** of the hub you want to print. Using
```
(gdb) p greeter.hub
$4 = (_z_.java.lang.Class *) 0x837820 <java.io.ObjectOutputStream::ObjectOutputStream(java.io.OutputStream*)+1120>
```
reveals that in the current situation, the `hub` field in `greeter` holds a compressed reference to the hub (the `hub-type` is prefixed with `_z_.`). 
Thus, you first need to get the absolute address of the hub field by using another `svm_dbg_` helper method.
```
(gdb) call svm_dbg_obj_uncompress($r15, greeter.hub)
$5 = 140737339160608
(gdb) p/x $5
$6 = 0x7ffff71b7820
```

With the help of calling `svm_dbg_obj_uncompress`, you now know that the hub is located at address `0x7ffff71b7820` and you can finally call `svm_dbg_print_hub`:
```
(gdb) call (void) svm_dbg_print_hub($r15, 0x7ffff71b7820)
hello.Hello$NamedGreeter
```

Both calls to `svm_dbg_` helper can be combined into a single command line:
```
(gdb) call (void) svm_dbg_print_hub($r15, svm_dbg_obj_uncompress($r15, greeter.hub))
hello.Hello$NamedGreeter
```

The following `svm_dbg_` helper methods are currently defined:
```
int svm_dbg_ptr_isInImageHeap(graal_isolatethread_t* thread, size_t ptr);
int svm_dbg_ptr_isObject(graal_isolatethread_t* thread, size_t ptr);
int svm_dbg_hub_getLayoutEncoding(graal_isolatethread_t* thread, size_t hubPtr);
int svm_dbg_hub_getArrayElementSize(graal_isolatethread_t* thread, size_t hubPtr);
int svm_dbg_hub_getArrayBaseOffset(graal_isolatethread_t* thread, size_t hubPtr);
int svm_dbg_hub_isArray(graal_isolatethread_t* thread, size_t hubPtr);
int svm_dbg_hub_isPrimitiveArray(graal_isolatethread_t* thread, size_t hubPtr);
int svm_dbg_hub_isObjectArray(graal_isolatethread_t* thread, size_t hubPtr);
int svm_dbg_hub_isInstance(graal_isolatethread_t* thread, size_t hubPtr);
int svm_dbg_hub_isReference(graal_isolatethread_t* thread, size_t hubPtr);
long long int svm_dbg_obj_getHub(graal_isolatethread_t* thread, size_t objPtr);
long long int svm_dbg_obj_getObjectSize(graal_isolatethread_t* thread, size_t objPtr);
int svm_dbg_obj_getArrayElementSize(graal_isolatethread_t* thread, size_t objPtr);
long long int svm_dbg_obj_getArrayBaseOffset(graal_isolatethread_t* thread, size_t objPtr);
int svm_dbg_obj_isArray(graal_isolatethread_t* thread, size_t objPtr);
int svm_dbg_obj_isPrimitiveArray(graal_isolatethread_t* thread, size_t objPtr);
int svm_dbg_obj_isObjectArray(graal_isolatethread_t* thread, size_t objPtr);
int svm_dbg_obj_isInstance(graal_isolatethread_t* thread, size_t objPtr);
int svm_dbg_obj_isReference(graal_isolatethread_t* thread, size_t objPtr);
long long int svm_dbg_obj_uncompress(graal_isolatethread_t* thread, size_t compressedPtr);
long long int svm_dbg_obj_compress(graal_isolatethread_t* thread, size_t objPtr);
int svm_dbg_string_length(graal_isolatethread_t* thread, size_t strPtr);
void svm_dbg_print_hub(graal_isolatethread_t* thread, size_t hubPtr);
void svm_dbg_print_obj(graal_isolatethread_t* thread, size_t objPtr);
void svm_dbg_print_string(graal_isolatethread_t* thread, size_t strPtr);
void svm_dbg_print_fatalErrorDiagnostics(graal_isolatethread_t* thread, size_t sp, void * ip);
void svm_dbg_print_locationInfo(graal_isolatethread_t* thread, size_t mem);
```

### Related Documentation

* [Debug Info Feature](../DebugInfo.md)