# Crema Overview

This document is an architectural overview of Crema for engineers joining the project. It focuses on how the current
implementation under `graal/substratevm` is structured, which subsystems it builds on, and where to start reading the
code.
For using Crema (as opposed to developing it), refer to `runtime-class-loading.md`.

## What Crema is

Crema is Native Image's run-time class loading architecture: it makes `ClassLoader.defineClass` work at run time,
creates new `DynamicHub`s for the new classes, and executes newly loaded bytecode through an interpreter. It is enabled
with `-H:+RuntimeClassLoading`, and that option also configures the main prerequisites:
* Enables open type world with `-H:-ClosedTypeWorld`. This handles subclasses and interfaces loaded after image build time.
* Enables class-loader-aware lookup with `-H:+ClassForNameRespectsClassLoader`. This enables per-class-loader registries
  (see `ClassRegistries.java` and `Target_java_lang_ClassLoader.java`), so the runtime can preserve normal Java class-loader
  delegation and namespace separation. It also makes JDK class-loader state run-time initialized where needed, so app-loader
  resource and class lookup can observe run-time class-path values.
* Disables predefined-class support (see "Support For Predefined Classes" in `ExperimentalAgentsOptions.md`) with `-H:-SupportPredefinedClasses`.

In addition to the above, `CremaFeature.java` builds on the existing Native Image bytecode interpreter and its metadata universe (`BuildTimeInterpreterUniverse.java`).

## Build-time setup

The build-time side is split across hosted features. `RuntimeClassLoadingFeature` turns on runtime
instance-reference-map support, and `ClassRegistryFeature` installs the global `ClassRegistries` singleton and eagerly
initializes the shadowed Espresso classfile package so the parser is available at run
time (`RuntimeClassLoadingFeature.java`: `isInConfiguration`, `getRequiredFeatures`),
`ClassRegistryFeature.java`: `isInConfiguration`, `getRequiredFeatures`, `afterRegistration`).

Crema itself is wired in by `CremaFeature`, which requires `InterpreterFeature`, installs `CremaSupportImpl` as the
runtime bridge, registers the direct and vtable interpreter entry stubs as roots, mirrors hosted vtable indices into
interpreter metadata before compilation, and emits the vtable enter-stub section into the
image (`CremaFeature.java`: `isInConfiguration`, `getRequiredFeatures`, `afterRegistration`, `beforeAnalysis`, `beforeCompilation`, `afterCompilation`, `initializeInterpreterFields`).

The interpreter metadata that Crema needs at run time is built from analysis and hosted data by
`BuildTimeInterpreterUniverse`. It creates interpreter-side types, methods, fields, signatures, constants, and copied
bytecodes, then `CremaSupportImpl.createInterpreterType` populates declared methods and fields for each reachable
analysis type and attaches native entry points for methods that already have compiled
code (`BuildTimeInterpreterUniverse.java`: `createResolvedObjectType`, `createResolvedJavaField`, `initializeJavaFieldFromHosted`, `createResolveJavaMethod`, `createUnresolvedSignature`;
`CremaSupportImpl.java`: `createInterpreterType`, `buildInterpreterMethods`, `addSupportedElements`, `buildInterpreterFields`, `buildInterpreterFieldsFromArray`).

## Class-loader model

There is one class registry per class loader. `ClassRegistries` owns the bootstrap registry plus lazily-created
registries for every non-null loader, and the registry is attached directly to `ClassLoader` through the injected
`classRegistry` field in the substituted JDK
class (`ClassRegistries.java`: `loaderNameAndId`, `getRegistry`, `addAOTClass`, `getBuildTimeRegistry`, `ClassRegistryComputer.transform`;
`Target_java_lang_ClassLoader.java`: `parallelLockMap`, `assertionLock`, `scl`, `classRegistry`).

There are only two runtime registry implementations:

- `BootClassRegistry` for the bootstrap loader
- `UserDefinedClassRegistry` for every other loader

The important point is that Crema does not bypass normal Java delegation. `UserDefinedClassRegistry` calls back into the
Java `ClassLoader` implementation and caches the result in the registry, while the boot registry has its own
bootstrap-specific loading path. Parallel class loading is deliberately disabled for now, both in the registry
implementation and in the substituted `ClassLoader`
state (`AbstractRuntimeClassRegistry.java`: `loadClass`, `loadClassInner`;
`Target_java_lang_ClassLoader.java`: type declaration and substitutions).

## From `defineClass` to a live class

The public entry point is substituted JDK code. `ClassLoader.defineClass*` and hidden-class support are redirected to
`RuntimeClassLoading.defineClass`, so the Crema pipeline starts exactly where a normal Java program expects it to
start (`Target_java_lang_ClassLoader.java`: `defineClass1`, `defineClass2`, `defineClass0`).

From there the runtime path is:

1. `RuntimeClassLoading.defineClass` checks whether the image is in predefined-class mode or Crema mode, and in Crema
   mode forwards to `ClassRegistries.defineClass`.
2. `ClassRegistries.defineClass` resolves the right per-loader registry and hands off to
   `AbstractRuntimeClassRegistry.defineClass`.
3. `AbstractRuntimeClassRegistry.defineClass` parses the bytes with `ClassfileParser`, handles hidden-class patching,
   rejects duplicate definitions, loads the superclass and direct interfaces, performs module and access checks, and
   then asks `CremaSupport` to create the runtime dynamic
   hub.
4. The resulting `Class<?>` is registered in the loader registry unless it is a hidden class, in which case strong
   hidden classes are kept reachable
   separately (`AbstractRuntimeClassRegistry.java`: `registerStrongHiddenClass`).

## Shared Espresso code

Crema uses shadowed Espresso code for parsing and a shared Espresso library for language-level linkage rules.

The parser side is the shadowed `com.oracle.svm.espresso.classfile` package used directly from
`AbstractRuntimeClassRegistry` and `RuntimeInterpreterConstantPool` (`ClassfileParser`, `ParserKlass`, `ParserMethod`,
`ParserField`, constant-pool descriptors, and so
on) (`AbstractRuntimeClassRegistry.java`: parser imports and class context,
`AbstractRuntimeClassRegistry.java`: `parseClass`,
`RuntimeInterpreterConstantPool.java`: constructor, `classFormatError`, `resolve`, `resolveInvokeDynamic`, `resolveMethodHandle`).
The upstream shared sources live under `graal/espresso-shared`, (e.g. `ClassfileParser.java` and `ParserKlass.java`).

The linkage side is shared through the Espresso shared libraries. `CremaLinkResolver` is essentially a thin adapter
around shared `LinkResolver`, and `CremaSupportImpl.createDispatchTable` uses the shared vtable builder to create the
class vtable and itables for a newly loaded
class (`CremaLinkResolver.java`: `resolveFieldSymbolOrThrow`, `resolveFieldSymbolOrNull`, `checkFieldAccessOrThrow`, `checkFieldAccess`, `resolveCallSiteOrThrow`, `resolveCallSiteOrNull`; `CremaSupportImpl.java`: `createDispatchTable`).
The upstream sources are [LinkResolver.java](../../espresso-shared/src/com.oracle.truffle.espresso.shared/src/com/oracle/truffle/espresso/shared/resolver/LinkResolver.java) and [VTable.java](../../espresso-shared/src/com.oracle.truffle.espresso.shared/src/com/oracle/truffle/espresso/shared/vtable/VTable.java).

## Dynamic hubs, metadata, and metaspace

`CremaSupportImpl.createHub` is the central runtime constructor for a Crema class. It derives hub flags and modifiers,
computes type-check data for open-world dispatch, builds the dispatch table, computes field layout, allocates the
`DynamicHub`, creates the runtime `CremaResolvedObjectType`, installs the runtime constant pool, fills the vtable,
creates declared fields, initializes static constant values, and finally attaches runtime hub and reflection
metadata (`CremaSupportImpl.java`: `createHub`).

The actual hub allocation happens in `DynamicHub.allocate`. Runtime-created hubs and the type-check arrays they
reference are allocated in the metaspace, not in the ordinary Java heap. The metaspace exists specifically for
VM-internal objects that should neither move nor be reclaimed like normal Java objects, which is why it is used for
runtime-created hubs and their associated metadata
arrays (`DynamicHub.java`: `allocate`, `finishInitialization`,
`Metaspace.java`: `singleton`, `isSupported`, `copyToMetaspace`).

Crema also follows a clear split between image-time metadata and metadata created for runtime-loaded classes.
`DynamicHubCompanion` stores both hub metadata and reflection metadata, and runtime-loaded classes receive
`RuntimeDynamicHubMetadata` plus `RuntimeReflectionMetadata` rather than the image-time implementations used by AOT
classes (`CremaSupportImpl.java`: `createHub`,
[RuntimeDynamicHubMetadata.java](../../espresso-shared/substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/hub/RuntimeDynamicHubMetadata.java),
[RuntimeReflectionMetadata.java](../../espresso-shared/substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/hub/RuntimeReflectionMetadata.java)).

## Interpreter execution model

Runtime-loaded methods are executed through the existing interpreter, but Crema supplies the runtime-created method
metadata and entry points. `CremaSupportImpl.fillVTable` writes either a native entry point or an interpreter stub into
each vtable slot; for dynamically loaded methods the common case is the stub
path (`CremaSupportImpl.java`: `fillVTable`).

`InterpreterStubSection` contains the two important bridges from AOT code into interpreted code:

- `enterDirectInterpreterStub`, for direct entry when the target method is already
  known (`InterpreterStubSection.java`: `enterDirectInterpreterStub`)
- `enterVTableInterpreterStub`, for virtual dispatch through a vtable slot, where the stub reads the receiver from the
  ABI-spilled arguments, finds its `DynamicHub`, and then selects the interpreted method from that type's
  vtable (`InterpreterStubSection.java`: `enterVTableInterpreterStub`)

The enter helper is also where the calling-convention bridge happens. The low-level stub rewrites raw object references
into thread-local handles so the GC can safely resume, reconstructs the Java argument array, and then either leaves the
interpreter again into compiled code or falls through to
`Interpreter.execute` (`InterpreterStubSection.java`: `enterHelper`,
`InterpreterStubSection.java`: `enterInterpreterStub0`, `enterInterpreterStubCore`,
`InterpreterStubSection.java`: `leaveInterpreter`, `leaveInterpreter0`, `call`).

The frame model is heap-based. `InterpreterFrame` stores parallel primitive and reference slot arrays plus monitor
state, and `InterpreterFrameUtil` overlays the JVM local-variable and operand-stack layout on top of that
storage (`InterpreterFrame.java`: constructor, `create`, slot accessors/mutators, and lock management,
[InterpreterFrameUtil.java](../src/com.oracle.svm.interpreter/src/com/oracle/svm/interpreter/InterpreterFrameUtil.java)).
`Interpreter.execute` initializes the frame from the Java arguments, acquires synchronized locks when needed, and runs
the bytecode
loop (`Interpreter.java`: `initArguments`, `initializeFrame`, `execute`, `execute0`).

Exceptions are handled through `SemanticJavaException`, which lets the interpreter distinguish guest exceptions from
interpreter bugs. The bytecode loop catches `SemanticJavaException`, `OutOfMemoryError`, and `StackOverflowError`, looks
for a matching handler, clears the operand stack, pushes the exception object, and resumes at the handler BCI. Any other
throwable is treated as an internal failure and escalated as a VM
error (`SemanticJavaException.java`: constructor, `fillInStackTrace`, `raise`,
`Interpreter.java`: `Root.executeBodyFromBCI` exception handling loop).

## Reflection and method handles

Crema does not stop at raw execution. Runtime reflection objects are synthesized from Crema metadata by
`RuntimeReflectionMetadata`, which constructs `Field`, `Method`, `Constructor`, and `RecordComponent` objects on
demand (`RuntimeReflectionMetadata.java`: `getClassFlags`, `getDeclaredFields`, `fromResolvedField`, `getDeclaredMethods`, `fromResolvedMethod`, `getDeclaredConstructors`, `fromResolvedConstructor`, `getRecordComponents`, `getUnsafeAllocationMetadata`).

Invocation of those reflective objects goes back through Crema-specific accessors. `CremaMethodAccessor` and
`CremaConstructorAccessor` validate arguments, initialize the declaring class when needed, and then call
`CremaSupport.execute` or `CremaSupport.allocateInstance` plus `execute`, which ultimately dispatches through
`InterpreterToVM.dispatchInvocation` (`CremaMethodAccessor.java`: constructor, `invoke` overloads,
`CremaConstructorAccessor.java`: constructor, `newInstance`,
`CremaSupportImpl.java`: `getStaticStorage`, `execute`).

Method handles also have explicit Crema integration. When runtime class loading is enabled,
`MethodHandleNatives.resolve` delegates to `CremaSupport.resolveMemberName`, and Crema's implementation uses the shared
link resolver to resolve fields and methods against the interpreter
metadata (`Target_java_lang_invoke_MethodHandleNatives.java`: `Util_java_lang_invoke_MethodHandleNatives.resolve`,
`CremaSupportImpl.java`: `resolveMemberName`, `plantResolvedMethod`, `plantResolvedField`, `getSignaturePolymorphicIntrinsicID`).
Some _AOT_-compiled method-handle call sites intentionally route into Crema support, and the compiled-side
`invokeBasic`, `linkToVirtual`, `linkToStatic`, `linkToSpecial`, and `linkToInterface` paths all dispatch through
Crema's bridge back into interpreter-aware
invocation (`CremaSupportImpl.java`: `invokeBasic`, `linkToVirtual`, `linkToStatic`, `linkToSpecial`, `linkToInterface`).

## Current boundaries

The current implementation still has several important boundaries: no parallel class loading, no JNI support for
runtime-loaded classes, no `condy`, and limited reflection for runtime-loaded classes.

The code also shows a few areas that are still under construction:

- `RuntimeClassLoading.ensureLinked` is still not implemented as a separate runtime link
  step (`RuntimeClassLoading.java`: `createInterpreterType`, `ensureLinked`).
- Some reflection metadata is intentionally incomplete, such as enclosing-method support in `RuntimeDynamicHubMetadata`
  and checked exceptions / raw parameters in
  `CremaResolvedJavaMethodImpl` (`RuntimeDynamicHubMetadata.java`: `getEnclosingMethod`, `CremaResolvedJavaMethodImpl.java`: `getRawParameterAnnotations`, `getRawAnnotationDefault`, `getRawParameters`, `getRawTypeAnnotations`, `getAccessor`, `getGenericSignature`).
- The caller-sensitive overload of `CremaMethodAccessor.invoke` is still marked
  unimplemented (`CremaMethodAccessor.java`: `invoke` caller-sensitive overload).

## Summary

Crema is best understood as a composition of existing Native Image subsystems rather than a standalone runtime:

- `RuntimeClassLoading` turns on the right runtime model: open type world plus class-loader-aware lookup.
- `ClassRegistries` preserves normal class-loader structure and drives runtime definition.
- Shadowed Espresso classfile code parses new classes, and shared Espresso resolver/vtable code provides language-level
  linking behavior.
- `DynamicHub.allocate` plus the metaspace make new class metadata physically resident and stable at run time.
- `BuildTimeInterpreterUniverse`, `CremaFeature`, and `CremaSupportImpl` connect that metadata to the interpreter so new
  classes can actually run.
- Reflection and method handles are integrated on top of the same bridge, which is why Crema affects more than just
  `defineClass`.

For a new contributor, the most useful reading order is usually `RuntimeClassLoading` -> `ClassRegistries` /
`AbstractRuntimeClassRegistry` -> `CremaSupportImpl` -> `InterpreterStubSection` -> `Interpreter`.
