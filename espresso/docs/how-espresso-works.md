# How Espresso works

## Introduction

_(All package paths are relative to `com.oracle.truffle.espresso`)_

Espresso is an implementation of a Java Virtual Machine that itself runs on a host JVM. This can make what's going on a
little confusing. To begin understanding the Espresso internals you should first learn about the Graal compiler and the
Truffle language implementation framework. Espresso does not contain components you might expect from a JVM like a
garbage collector, JIT compiler or operating system abstraction because they are already provided by lower levels of the
stack. Instead Espresso provides runtime services like:

* Bytecode parsing, validation and interpretation.
* Debugging.
* HotSwap (changing code whilst it's running).
* JNI.
* A development/debug java launcher command based on the polyglot API.
* The "native" components of the standard library like `libjava` (in quotes because what native means can vary).
* Sandboxing.

## How to attach a debugger

A good way to start Espresso when working fully in JVM mode (Espresso isn't a native image), is to run:

```
mx --env jvm-ce espresso --log.file=/tmp/truffle-log '--vm.agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8000' -cp .... hello.World
```

Espresso will pause at startup until you attach your IDE's debugger to it.

## Modes of operation

Espresso can be used in different configurations with differing tradeoffs.

The **host VM** can be OpenJDK, GraalVM HotSpot or GraalVM Native Image. On OpenJDK Espresso will only execute in the
interpreter and is thus slow. With a regular GraalVM it executes faster, because the Graal compiler will be used which
knows how to accelerate Truffle languages, and with a Native Image host it is the fastest due to not needing the 
Espresso VM itself to warm up.

The **native components** that implement the JNI side of the standard library can be genuine operating system 
specific machine code, in which case the host OS must be Linux and Java code cannot be sandboxed, or LLVM bitcode 
executed via Sulong in which case it can in theory run on any OS and Java code can be sandboxed.

## Startup sequence

There are three ways that Espresso can be started up:

1. Embedded in another program that uses the Truffle Polyglot API to create a context, e.g. for sandboxing.
2. By the user running `java -truffle -cp ...`. This requires that Espresso be compiled with native image and is the 
   normal way for users to interact with Espresso if they just want to treat it as a regular JVM.
3. By an Espresso developer (you) running `mx espresso`. This is the way to start Espresso as a JVM library and passes
   control to `launcher.EspressoLauncher`, a simple Java program that somewhat unconvincingly pretends to be the `java` 
   launcher. It parses the flags and then uses the Polyglot API to create an Espresso context. 

No matter what path is used the first time Espresso gets control is in `EspressoLanguage`, which is instantiated
by Truffle when it's asked for a Java context. You can't actually evaluate Java sources with this language, only a few
commands in a control language that point Espresso at a classpath. `EspressoLanguage` in turn creates `EspressoContext`s
which contain the VM state for a Java world.

When a user runs `java -truffle` execution starts at the regular native C `java` launcher from OpenJDK, which passes
control to `libjli` (the Java Launch Infrastructure). That looks up and instantiates the Espresso JVM when the
`-truffle` flag is passed. The launcher/`libjli` isn't patched to do this, instead a generic OpenJDK mechanism is used
in which the `-truffle` flag is mapped via the `jvm.cfg` file in the JVM `lib` directory to the file
`lib/truffle/libjvm.dylib`. This is a small native code library that in the source is called Mokapot.

Mokapot exports both the JNI interface for creating a JVM and also the internal `JVM_*` methods that the OpenJDK
standard library modules sometimes call into from their own native code. It makes Espresso look like a regular JVM.
Mokapot doesn't contain Espresso itself. It's just a binding layer. Espresso will be found in
`lib/languages/libjavavm.{so,dylib,dll}`. This is a native image that exports some C APIs via the native image C
interface. In `libjavavm.LibEspresso` a C entry point is exported which creates a Polyglot context for the Espresso
engine, 'casts' it to the JNI interface and then returns it.

So adding it all together the startup sequence for end users goes like this:

`java -truffle ...` ➜ `libjli` ➜ `lib/truffle/libjvm` (mokapot) ➜ `libjavavm` (`LibEspresso`) ➜ Polyglot API ➜ `EspressoLanguage` ➜ `EspressoContext`

The launcher uses this code path to obtain a JNI reference to the class `sun.launcher.LauncherHelper` which
loads the user's main class from inside Java, and then invokes it. There are other launcher helper classes for
alternative launch modes like when using `java` with a source file.

## Execution loop

The core of the interpreter is in `nodes.BytecodeNode`, which is a Truffle node representing all the bytecodes in a
single regular guest method. It's a big `while (true)` loop. When a guest method becomes hot enough to be compiled by
Truffle the loop will be unrolled and partially evaluated, which is how the method gets translated to native code.

### Quickening

Most bytecodes are handled in the obvious way. Some are _quickened_.

Truffle started as an AST interpreter and thus is very oriented around the concept of tree nodes. It has 
a lot of features that require nodes, and in particular nodes can be _specialized_ based on how they are actually used
whereas a bytecode generally cannot. Quickening bridges the two worlds. A bytecode that is quickened is replaced with 
a special internal bytecode, `QUICK` or `SLIM_QUICK`, and a Truffle node is created for it then stored in the
`BytecodeNode`'s child node array. The quick opcode passes control to the right child node.  

Some examples of quickened opcodes include `instanceof`, and all the method invocation opcodes. Quickening trades off
extra memory for faster runtime, so some bytecodes are always quickened but others only sometimes.

### Special methods

Some methods are special and have to be implemented by the JVM itself. How these work depend on how they are reached. A
method in the standard library may appear to be a normal Java method but is actually replaced by a different
implementation (an intrinsic), usually one that's doing more optimal things that can't be expressed in regular Java.
Espresso implements these using _substitutions_, which are explained below.

When the interpreter reaches a JNI method, control will be pass to either native code or into Sulong. The two
possibilities are abstracted by the (not Espresso specific) Truffle NFI "language", which acts as a generic FFI, so
native calls look like polyglot/interop calls.

The OpenJDK version of the Java standard library has native methods, some of which call into HotSpot via
private APIs. Espresso implements these APIs in a library called `mokapot`, which is compiled to a `libjvm.so`/`jvm.dll`
file. The operating system links the stdlib native components to this library, and when code execution reaches 
mokapot control is transferred back into the Java world via a thread local variable storing a struct of function 
pointers, where it emerges in the static methods in `vm.VM`.

### Liveness analysis

The garbage collector looks at local variables on the stack to find objects that are still live. An edge case is
where a method puts a reference into a local variable that roots a large object graph, uses it briefly and then 
enters a hot loop in which that local is never used again. The object graph could be collected but because it's still
referenced from the stack, it won't be.

The solution is to do an analysis on the method to figure out when it's safe to automatically null out locals that
aren't being used anymore. The analysis has a cost, so it's only done for JIT compiled code and by default only when
there are a lot of locals.

## Things in the guest world

### The heap

Objects allocated by guest code are all represented in the host heap as instances of
`runtime.staticobject.StaticObject`, including arrays and even `null`! The JVM executing the _host_ (Espresso)
provides garbage collection services. 

You may notice that this class has only a lock word and a pointer to the class of the object, and wonder where the
actual data is stored. The answer depends on what host JVM is being used. On HotSpot the Truffle framework synthesizes
bytecode to create and load subclasses of `StaticObject` with the right number of fields to store the guest data. This
lets guest code use the host garbage collector. On SubstrateVM (native image) you can't dynamically load classes, so an
alternative is required. There are two available: array based and "pod" based. The goal is to migrate to "pods", in 
which Truffle communicates object layouts directly to the SubstrateVM runtime and GC, but as of January 2024 
the array based method is faster.

Behind the scenes field accesses may be carefully checked, or they may have their checks compiled out. That's useful for
Espresso where the bytecode is statically verified ahead of time, so it's known to be safe at the time it executes. To
learn more about how object storage works, read about the [Truffle Static Object Model](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/StaticObjectModel/).

### Reflecting guest heap objects 

The `meta.Meta` and `descriptors.Symbol` classes are boilerplate that makes reflecting guest classes more type safe and
convenient. It's especially convenient for navigating in the IDE, as it lets you do "Find Usages" to locate where a
guest symbol is being manipulated by the VM, without being distracted by all the places the host code is also using the
same symbol.

Accessing guest objects is relatively straightforward. Add new names, signatures and fields to the `Meta` and `Symbol`
classes using the existing entries as a guide. This will get you an `impl.ObjectKlass`, which is similar to (but not the same as) a `Class` in
the guest world. Once you've obtained one you can use it to read fields, call methods etc. If you want to access
_static_ fields, then you'll need to look up the field using `impl.Klass#lookupField()`, then use `getObject` on the
field passing in the result of `Klass#tryInitializeAndGetStatics()`. Static fields are stored in a separate
`StaticObject` and this will ensure the guest code `<clinit>` runs to set up the static fields of the class you're
trying to access. The guest world `java.lang.reflect.Class` that matches a host `impl.ObjectKlass` can be obtained by
calling `ObjectKlass#mirror()`. 

Guest-world reflection objects are tied back to host-world objects using _hidden fields_. These are special kinds of
fields added to objects. Their names start with `0` making them illegal identifiers, ensuring there can never be
conflicts, and they are hidden from guest-world reflection.

### The stack

Guest threads are run on host threads 1:1. That means guest stacks use host stacks, and for virtual threads to work the
hosting JVM must support the combination of virtual threads and Truffle, which as of December 2023 HotSpot does not.
Likewise guest exceptions are wrapped in `EspressoException` and then thrown, so the JVM running Espresso provides
stack unwinding services and similar.

Truffle provides stack frame management. A `nodes.BytecodeNode` receives a Truffle `VirtualFrame` object, which manages
a series of slots stored on the host stack, or when _materialized_, the frame is stored on the heap. In dynamic Truffle
languages these slots are strongly typed and correspond to the base Java type system with ints, booleans, longs and
object references etc. Espresso however uses Truffle's _static slots_, which are (somewhat confusingly) both typed and
untyped at the same time. The Truffle `Frame` interface has an API for reading and writing to static slots which
distinguishes betwen primitive types (`get/setObjectStatic`, `get/setLongStatic` etc), but the actual primitive type of
a static slot is tracked only when assertions are enabled i.e. in debug mode. You can see some of this code in Truffle's
`FrameWithoutBoxing` class. In normal execution the slot types are marked only as being "static". This is OK because
Java bytecode implicitly types stack slots. The types aren't recorded in the bytecode itself, but can be recovered using
an analysis algorithm. During bytecode verification the types of slots are computed to ensure that (even when they
change) they are never being used inconsistently. Behind the scenes then we only need to store object references
separately from everything else so the GC can find them, and thus other types of stack slot are just stored in
uninterpreted longs.

## Substitutions and extension modules

Espresso specific features may need guest-exposed APIs to control them. For example Espresso exposes a HotSwap control
API that lets apps register for callbacks that run when the program code is mutated.

To add one of these:

1. Add a new module in the `com.oracle.truffle.espresso` namespace by creating the directories and then adding it to
   `suite.py`. Use the way `HOTSWAP` is defined as a template.
2. In `substitutions.ModuleExtension` add a new entry to the `ESPRESSO_EXTENSION_MODULES` array.
3. In `meta.Meta` and `descriptors.Symbol`, add entries for any parts of the new module that you need to access from 
   the VM. Watch out! In `meta.Meta` your new fields should be `@CompilationFinal public static ....` and not `final`.
   You'll need to look them up in `postSystemInit` as otherwise you'll load classes into the wrong module.

Extension modules can't directly call into Espresso code - they are effectively just normal Java libraries, and should
be able to gracefully degrade or report failure on non-Espresso JVMs. To hook the extension into Espresso requires
the substitution mechanism.

How to register substitutions is explained clearly in the JavaDoc for `substitutions.Substitutions`. An annotation
processor generates boilerplate to help with invoking the substitution-containing classes. Generally, each method has
a Truffle node object created for it, but you can also define a node directly in a substitutions class which is helpful
if you need to optimize it with Truffle specializations.

## Guest code metadata

Espresso divides metadata about the guest program into three layers: parser (data), linked (structural) and runtime.

### Parser (data)

The parser layer is whatever comes out from the .class parser.
In the parser (data) layer, Espresso could already do some pre-processing of the (method) bytecodes:

- Pre-quicken bytecodes.
- Pre-allocate/count how many nodes are needed.
- Answer questions like: does a method use monitors, does it call AccessController.doPrivileged, is it a getter/setter ...

### Linked (structural)

The linked layer (LinkedKlass, LinkedMethod, LinkedField) defines an immutable, structural representation of the class hierarchy.
At structural layer, the super class and super interfaces are also linked, the class hierarchy is known.
In the linked (structural) layer, Espresso can compute useful information:

- vtables
- itables
- Class layout: field offsets, object size...
- Mark the class as finalizable (or not)

### Runtime

Finally the runtime layer associates a linked class with the runtime data: a class loader instance, static fields,
initialization and verification status, guest Class<?> instance. The runtime layer can squeeze even more information,
via assumptions, based on classes loaded at runtime:

- (Effectively) leaf methods
- (Effectively) leaf classes
- Single interface/abstract class implementor
- All of the above AKA class hierarchy analysis (CHA).

These layers were designed to make metadata as shareable as possible via a strict separation between metadata and
runtime data. The idea is to share up to the linked (structural) layer between contexts e.g. a second context doesn't
need to reparse and recompute vtable, itables etc.
