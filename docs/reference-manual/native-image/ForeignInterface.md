---
layout: docs
toc_group: native-code-interoperability
link_title: Foreign Interface
permalink: /reference-manual/native-image/native-code-interoperability/foreign-interface/
redirect_from: /reference-manual/native-image/dynamic-features/foreign-interface/
---

# Foreign Function and Memory API in Native Image

The Foreign Function and Memory (FFM) API is an interface that enables Java code to interact with native code and vice versa.
It is finalized in JDK 22 with [JEP 454](https://openjdk.org/jeps/454){:target="_blank"}.
Support in Native Image is currently experimental and must be explicitly enabled with `-H:+ForeignAPISupport` (in addition to `-H:+UnlockExperimentalVMOptions`).
Modules that are permitted to perform "restricted" native operations (including creating handles for calls to or from native code) must be specified using the `--enable-native-access=` option.
This page gives an overview of the FFM API support in Native Image.

## Foreign Memory

Foreign memory functionality is generally supported. 
Shared arenas are currently not supported.

## Foreign Functions

The FFM API enables Java code to call _down_ to native functions, and conversely allows native code to call _up_ to invoke Java code via method handles.
These two kinds of calls are referred to as "downcalls" and "upcalls" respectively, and are collectively referred to as "foreign calls".

> Note: Currently, foreign calls are supported on the x64 architecture.
> Specifically, downcalls are supported on x64 Linux, Windows and MacOS, while upcalls are supported only on x64 Linux.

### Looking Up Native Functions

The FFM API provides the `SymbolLookup` interface to find functions in native libraries by name.
`SymbolLookup.loaderLookup()` is currently the only supported kind of `SymbolLookup`.

### Registering Foreign Calls

In order to perform calls to native code at run time, supporting code must be generated at image build time.
Therefore, the `native-image` tool must be provided with descriptors that characterize the functions to which downcalls may be performed at run time.

These descriptors can be registered using a custom `Feature`, for example:
```java
import static java.lang.foreign.ValueLayout.*;

class ForeignRegistrationFeature implements Feature { 
  public void duringSetup(DuringSetupAccess access) {
    RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.ofVoid());
    RuntimeForeignAccess.registerForUpcall(FunctionDescriptor.ofVoid());
    RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.ofVoid(), Linker.Option.critical(false));
    RuntimeForeignAccess.registerForUpcall(FunctionDescriptor.of(JAVA_INT, JAVA_INT));
    RuntimeForeignAccess.registerForUpcall(FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT));
    RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT), Linker.Option.firstVariadicArg(1));
    RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.ofVoid(JAVA_INT), Linker.Option.captureCallState("errno"));
  }
}
```
To activate the custom feature, pass the `--features=com.example.ForeignRegistrationFeature` option (the fully-qualified name of the feature class) to `native-image`.
It is recommended to do so [with a _native-image.properties_ file](BuildConfiguration.md#embed-a-configuration-file).

### Related Documentation

- [Interoperability with Native Code](InteropWithNativeCode.md)