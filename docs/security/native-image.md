---
layout: docs
toc_group: security-guide
link_title: Security Considerations in Native Image
permalink: /security-guide/native-image/
---

# Security Considerations in Native Image

The `native-image` builder generates a snapshot of an application after startup and bundles it in a binary executable.
The security report section of the native image [build output](../reference-manual/native-image/BuildOutput.md#security-report) provides security relevant information about the native image build.

## Class Initialization

The `native-image` builder may execute the static initializers of certain classes at build time (see [class initialization](../reference-manual/native-image/ClassInitialization.md) for more details).
Executing static initializers at build time persists the state after initialization in the image heap.
This means that any information that is obtained or computed in static initializers becomes part of a native executable.
This can either result in sensitive data ending up in the snapshot or fixing initialization data that is supposed to be obtained at startup, such as random number seeds.

Developers can request static initializers that process sensitive information to be executed at run time by specifying the `--initialize-at-run-time` CLI parameter when building a native executable, followed by a comma-separated list of packages and classes (and implicitly all of their subclasses) that must be initialized at runtime and not during image building.
Alternatively developers can make use of the [`RuntimeClassInitialization` API](https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/RuntimeClassInitialization.html).

Developers should run the `native-image` builder in a dedicated environment, such as a container, that does not contain any sensitive information in the first place.

## Software Bill of Materials

Native Image can embed a Software Bill of Materials (SBOM) which is an inventory of all the components, libraries, and modules that make up an application.
Read more in [Software Bill of Materials (SBOM) in Native Image](SBOM.md).

## Obfuscation 

Native Image provides strong obfuscation capabilities by default through native compilation and aggressive optimizations. 
For an additional layer of protection, use the experimental [Advanced Obfuscation feature](Obfuscation.md) to obfuscate symbol names (module, package, class, method, field, and source file names).
Obfuscation makes reverse engineering more difficult and helps protect intellectual property. 

## Java serialization

Native Image supports Serialization to help users deserialize the constructors for classes, contained in a native executable.
Unless picked up by native image analysis automatically, [these classes have to be prespecified](../reference-manual/native-image/ReachabilityMetadata.md#reflection), as classes not contained in a native executable cannot be deserialized.
Native Image cannot prevent exploitation of deserialization vulnerabilities in isolation.
The [serialization and deserialization Secure Coding Guidelines for Java SE](https://www.oracle.com/java/technologies/javase/seccodeguide.html#8) should be followed.

The security report section of the native image [build output](../reference-manual/native-image/BuildOutput.md#security-report) provides information on whether deserialization code is part of a native image's attack surface or not.

## Miscellaneous

Setting the security manager is not allowed. For more information see the [compatibility documentation](../reference-manual/native-image/Compatibility.md#security-manager).

Native Image provides multiple ways to specify a certificate file used to define the default TrustStore.
While the default behavior for `native-image` is to capture and use the default TrustStore from the build-time host environment, this can be changed at run time by setting the "javax.net.ssl.trustStore\*" system properties.
See the [documentation](../reference-manual/native-image/CertificateManagement.md) for more details.

The directory containing the native executable is part of the search path when loading native libraries using `System.loadLibrary()` at run time.

Native Image will not allow a Java Security Manager to be enabled because this functionality has now deprecated since Java 17.
Attempting to set a security manager will trigger a runtime error.

## Related Documentation

- [Security Guide](security-guide.md)
- [Sandboxing](polyglot-sandbox.md)
- [Jipher JCE with Native Image](JipherJCE.md)
