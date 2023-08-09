---
layout: docs
toc_group: dynamic-features
link_title: Foreign Interface
permalink: /reference-manual/native-image/dynamic-features/foreign-interface/
---

# Foreign Interface in Native Image

The Foreign Interface is a native API that enables Java code to interact with native code and vice versa.
It is currently a preview API of the Java platform and must be enabled with `--enable-preview`.
This page gives an overview of its support in Native Image.

## Foreign memory
Shared arenas are not supported.

## Foreign functions
The Foreign Functions Interface (FFI) allows Java code to call native functions, and conversely allows native code to invoke Java method handles.
These two kind of calls are referred to as "downcalls" and "upcalls" respectively and are collectively referred to as "foreign calls".

This feature is currently only supported on the AMD64 platform.

### Looking up native functions
FFI provides the `SymbolLookup` interface which allows to search native libraries for functions by name.
`loaderLookup` is currently the only supported `SymbolLookup`.

### Registering foreign calls
In order to perform a call to native, some glue code is required and thus must be generated at build time.
Therefore, a list of the types of downcall which will be performed must be provided to the `native-image` builder.

This list can be specified using a custom `Feature`. For example:
```java
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
To activate the custom feature `--features=<fully qualified name of ForeignRegistrationFeature class>` needs to be passed to native-image.
[Native Image Build Configuration](BuildConfiguration.md#embed-a-configuration-file) explains how this can be automated with a `native-image.properties` file in `META-INF/native-image`.

Upcalls are currently not supported.