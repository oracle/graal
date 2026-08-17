---
layout: docs
toc_group: dynamic-features
link_title: Dynamic Features
permalink: /reference-manual/native-image/dynamic-features/
---

# Dynamic Features of Java

When you build a native image, it only includes elements that static analysis finds reachable from your application entry point, its dependent libraries, and the JDK.
Some Java features, such as reflection and resource access, determine the elements they use only at run time.
If Native Image cannot determine that an element is needed, the generated binary does not include it and the application can fail at run time.

For dynamic features that require configuration, use [Reachability Metadata](ReachabilityMetadata.md).
That page explains how to provide metadata, documents each supported metadata type, and provides the [JSON schema reference](ReachabilityMetadata.md#json-schema-reference).

For feature-specific information, see the following documentation:

- [Accessing Resources](ReachabilityMetadata.md#resources)
- [Certificate Management](CertificateManagement.md)
- [Foreign Function and Memory API in Native Image](FFM-API.md)
- [Java Native Interface (JNI)](ReachabilityMetadata.md#java-native-interface)
- [JCA Security Services](JCASecurityServices.md)
- [Reflection](ReachabilityMetadata.md#reflection)
- [URL Protocols](URLProtocols.md)
