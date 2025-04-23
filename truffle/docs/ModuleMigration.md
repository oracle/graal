---
layout: docs
toc_group: truffle
link_title: Truffle Languages and Instruments Migration to Java Modules
permalink: /graalvm-as-a-platform/language-implementation-framework/migration-to-java-modules/
---
# Truffle Languages and Instruments Migration to Java Modules

Since version 23.1 Truffle uses languages and instruments primarily as Java modules loaded from the Java VM module path.
Loading languages and tools from language or tool homes is still supported for compatibility reasons. However, it is deprecated and will be removed in future versions. The motivations for this change are:
1. Truffle should be used the same way on all JDKs.
2. Truffle is not a part of the Java VM jimage and is used as a regular JVM VM module path library.
3. Loading languages and tools from the module path is much easier for the embedder and works right out of the box with
tools like Apache Maven.
4. The Java module system guarantees strong encapsulation and reliable configuration.

## Module Migration
For general information on migrating to Java modules, please refer to [development-with-jdk-9](https://blogs.oracle.com/java/post/modular-development-with-jdk-9).
The Truffle module is distributed in two versions: open Truffle and closed Truffle. The open Truffle exports all API
packages in its module descriptor. On the other hand, the closed Truffle does not export API packages in the module
descriptor. Instead, it dynamically exports API packages at runtime to modules that provide the language or instrument,
as well as to modules that the language or instrument module reads. While the open Truffle is intended for testing and
compile-time purposes, the closed Truffle must be used in production. To correctly load a language or instrument as a
module, the following migration steps need to be applied:
1. Define a named Java module that requires the `org.graalvm.truffle` module.
2. For each Truffle language provided by this module, register a language provider using the    
   `provides TruffleLanguageProvider with <LanguageClass>Provider` directive.
3. For each Truffle instrument provided by this module, register an instrument provider using the    
   `provides TruffleInstrumentProvider with <InstrumentClass>Provider` directive.
4. If a library is exported with a default export lookup enabled using `@GenerateLibrary(defaultExportLookupEnabled = true)`,
   the generated implementation of the `DefaultExportProvider` must be registered in the module descriptor using the provides
   directive for the `com.oracle.truffle.api.library.provider.DefaultExportProvider` service. If you build your language or instrument using `mx`
   the provides directive is generated automatically.  
5. If an AOT library is exported using `@ExportLibrary(useForAOT = true)`, the generated implementation of the `EagerExportProvider`
   must be registered in the module descriptor using the provides directive for the `com.oracle.truffle.api.library.provider.EagerExportProvider`
   service. If you build your language or instrument using `mx` the provides directive is generated automatically.
6. If your language or instrument already has a module descriptor, make sure that it does not provide any implementation
   of a deprecated `com.oracle.truffle.api.library.EagerExportProvider` or `com.oracle.truffle.api.library.DefaultExportProvider`
   interface in the module descriptor. They must be replaced by the `com.oracle.truffle.api.library.provider.EagerExportProvider` and
   `com.oracle.truffle.api.library.provider.DefaultExportProvider`. Providing these deprecated interfaces in the module descriptor
   will cause an error during  creation of a module layer on the closed Truffle.
7. Languages or instruments must not provide JDK services or services from third party libraries in the module descriptor.
   This is needed to avoid languages getting loaded by the JDK or third parties without the necessary dynamic exports.  
8. Language dependencies that might be commonly used by Java applications, like ICU4J, should be shadowed to avoid
   conflicts with modules used by the embedding.
9. If the language or instrument does not expose any API, it is recommended to keep the module as encapsulated as possible
   and avoid exporting any packages. Otherwise, export the API packages. In the case of internal APIs that are exclusively
   utilized by known modules, it is advised to use qualified exports with the syntax `export <package> to <module>`.

Here is a sample module descriptor for the simple language.
```java
module org.graalvm.sl {
  requires java.base;
  requires java.logging;
  requires jdk.unsupported;
  requires org.antlr.antlr4.runtime;
  requires org.graalvm.truffle;
  provides  com.oracle.truffle.api.provider.TruffleLanguageProvider with
    com.oracle.truffle.sl.SLLanguageProvider;
}
```
