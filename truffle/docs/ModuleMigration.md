---
layout: docs
toc_group: truffle
link_title: Truffle Language Safepoint Tutorial
---

# Truffle languages and instruments migration to Java modules
Since version 23.1 Truffle uses languages and instruments primarily as Java modules loaded from the Java VM module-path.
Loading languages and instruments from language (instrument) homes is still supported, but languages and instruments are
loaded as Java modules. The motivations for this change are:
1. Truffle is not a part of the Java VM jimage and is used as a regular JVM VM module-path library.
2. Loading languages and tools from the module path is much easier for the embedder and works right out of the box with
tools like Apache Maven.
3. The Java module system guarantees strong encapsulation and reliable configuration.

## Module Migration
For general information on migrating to Java modules, see [development-with-jdk-9](https://blogs.oracle.com/java/post/modular-development-with-jdk-9).
The Truffle library is distributed in two versions, open Truffle and closed Truffle. The open Truffle exports all API
packages in its `module-info`. The closed Truffle does not export API packages in the `module-info`, but opens API
packages dynamically in the runtime to modules that provide the language or instrument and to modules that the language
(instrument) module reads. While the open Truffle is intended for development, the closed Truffle is for deployment.
For a language (instrument) to work correctly with both Truffle deployments it must meet the following requirements:
1. Each named Java package can be part of only a single Java module.
2. All `TruffleLanguageProvider` or `TruffleInstrumentProvider` implementations generated for `TruffleLanguage.Registration`
and `TruffleInstrument.Registration` must be registered in the module-info using the `provides` directive.
3. Implementations of `TruffleLanguageProvider` or `TruffleInstrumentProvider` must not use Truffle types in a static
initializer or constructor. Truffle uses provider instances to export API packages. Provider instances are created  by  
the `ServiceLoader` before Truffle packages are exported to the language (instrument) module.
4. If the language (instrument) uses or provides services loaded by `ServiceLoader`, these services must not use
Truffle types. The exception to this rule are services that are loaded after the language (instrument) is created.

### Migration steps
1. Define a named Java module that requires the `org.graalvm.truffle` module.
2. For each Truffle language provided by this module, register a language provider using the    
`provides TruffleLanguageProvider with <LanguageClass>Provider` directive.
3. For each Truffle instrument provided by this module, register an instrument provider using the    
`provides TruffleInstrumentProvider with <InstrumentClass>Provider` directive.
4. If the language (instrument) has default library exports, register them using
`TruffleLanguage.Registration#defaultLibraryExports` (`TruffleInstrument.Registration#defaultLibraryExports`). Do not
register `DefaultExportProvider` implementations in the `module-info`. Registering `DefaultExportProvider` in the
`module-info` will cause an error during the creation of the module layer on the closed Truffle.
5. If the language (instrument) has exported libraries with AOT compilation, register them using      
`TruffleLanguage.Registration#aotLibraryExports` (`TruffleInstrument.Registration#aotLibraryExports`). Do not register
`EagerExportProvider` implementations in the `module-info`. Registering `EagerExportProvider` in the `module-info` will
cause an error during the creation of the module layer on the closed Truffle.
6. If the language (instrument) does not expose any API, it is recommended to keep the module as encapsulated as
possible and not to export any package. Otherwise, export the API packages. For the internal API, which is used only
by known modules, it is recommended to use qualified export `export <package> to <module>`.

### Sample module-info
Here is a sample module info for the simple language.
```java
module com.oracle.truffle.sl {
  requires java.base;
  requires java.logging;
  requires jdk.unsupported;
  requires org.antlr.antlr4.runtime;
  requires transitive org.graalvm.truffle;
  provides  com.oracle.truffle.api.provider.TruffleLanguageProvider with
    com.oracle.truffle.sl.SLLanguageProvider;
}
```
