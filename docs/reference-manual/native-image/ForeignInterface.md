---
layout: docs
toc_group: dynamic-features
link_title: Foreign Interface
permalink: /reference-manual/native-image/dynamic-features/foreign-interface/
---

# Foreign Function & Memory API in Native Image

The Foreign Function & Memory (FFM) API is a native interface that enables Java code to interact with native code and vice versa.
As of [JEP 442](https://openjdk.org/jeps/442){:target="_blank"}, it is a preview API of the Java platform and must be enabled with `--enable-preview`.
Modules that are permitted to perform "restricted" native operations (including creating handles for calls to or from native code) must be specified using `--enable-native-access=`.
This page gives an overview of support for the FFM API in Native Image.

## Foreign memory
Foreign memory functionality is generally supported. Shared arenas are currently not supported.

## Foreign functions
The FFM API enables Java code to call _down_ to native functions, and conversely allows native code to call _up_ to invoke Java code via method handles.
These two kinds of calls are referred to as "downcalls" and "upcalls" respectively and are collectively referred to as "foreign calls".

Currently, only downcalls are supported, and only on the AMD64 architecture.

### Looking up native functions
The FFM API provides the `SymbolLookup` interface to find functions in native libraries by name.
`SymbolLookup.loaderLookup()` is currently the only supported kind of `SymbolLookup`.

### Registering foreign calls
In order to perform calls to native code at runtime, supporting code must be generated at image build time.
Therefore, the `native-image` tool must be provided with descriptors that characterize functions to which downcalls may be performed at runtime.

These descriptors can be registered using a custom `Feature`, for example:
```java
import static java.lang.foreign.ValueLayout.*;

class ForeignRegistrationFeature implements Feature { 
  public void duringSetup(DuringSetupAccess access) {
    RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.ofVoid());
    RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.ofVoid(), Linker.Option.isTrivial());
    RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT));
    RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT), Linker.Option.firstVariadicArg(1));
    RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.ofVoid(JAVA_INT), Linker.Option.captureCallState("errno"));
  }
}
```
To activate the custom feature, `--features=com.example.ForeignRegistrationFeature` (the fully-qualified name of the feature class) needs to be passed to `native-image`.
It is recommended to do so [with a _native-image.properties_ file](BuildConfiguration.md#embed-a-configuration-file).

### Upcalls

Upcalls are not yet supported.
