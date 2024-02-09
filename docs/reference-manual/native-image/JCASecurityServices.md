---
layout: docs
toc_group: dynamic-features
link_title: JCA Security Services
permalink: /reference-manual/native-image/dynamic-features/JCASecurityServices/
redirect_from: 
- /reference-manual/native-image/features/JCASecurityServices/
- /reference-manual/native-image/JCASecurityServices/
---

# JCA Security Services in Native Image

This page explains Native Image support of the [Java Cryptography Architecture (JCA)](https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html) framework.

The JCA framework uses a provider architecture to access security services such as digital signatures, message digests, certificates and certificate validation, encryption (symmetric/asymmetric block/stream ciphers), key generation and management, and secure random number generation, etc.
To achieve algorithm independence and extensibility it relies on reflection, therefore it requires a custom configuration in Native Image.
By default the `native-image` builder uses static analysis to discover which of these services are used (see next section for details).
The automatic registration of security services can be disabled with `-H:-EnableSecurityServicesFeature`.
Then a custom reflection configuration file or feature can be used to register the security services required by a specific application.
Note that when automatic registration of security providers is disabled, all providers are, by default, filtered from special JDK caches that are necessary for security functionality.
In this case, you must manually mark used providers with `-H:AdditionalSecurityProviders`.

## Security Services Automatic Registration

The mechanism, implemented in the `com.oracle.svm.hosted.SecurityServicesFeature` class, uses reachability of specific API methods in the JCA framework to determine which security services are used.

Each JCA provider registers concrete implementation classes for the algorithms it supports.
Each of the service classes (`Signature`, `Cipher`, `Mac`, `KeyPair`, `KeyGenerator`, `KeyFactory`, `KeyStore`, etc.) declares a series of `getInstance(<algorithm>, <provider>` factory methods which provide a concrete service implementation.
When a specific algorithm is requested, the framework searches the registered providers for the corresponding implementation classes and dynamically allocates objects for concrete service implementations.
The `native-image` builder uses static analysis to discover which of these services are used.
It does so by registering reachability handlers for each of the `getInstance()` factory methods.
When it determines that a `getInstance()` method is reachable at run time, it automatically performs the reflection registration for all the concrete implementations of the corresponding service type.

Tracing of the security services automatic registation can be enabled with `-H:+TraceSecurityServices`.
The report will detail all registered service classes, the API methods that triggered registration, and the parsing context for each reachable API method.

> Note: The `--enable-all-security-services` option is now deprecated and it will be removed in a future release.

## Provider Registration

The `native-image` builder captures the list of providers and their preference order from the underlying JVM.
The provider order is specified in the `java.security` file under `<java-home>/conf/security/java.security`.
New security providers cannot be registered at run time; all providers must be statically configured at executable build time.

## Providers Reordering at Run Time

It is possible to reorder security providers at run time, however only existing provider instances can be used.
For example, if the `BouncyCastle` provider is registered at build time and you want to insert it at position 1 at run time:
```java
Provider bcProvider = Security.getProvider("BC");
Security.removeProvider("BC");
Security.insertProviderAt(bcProvider, 1);
```

## SecureRandom

The `SecureRandom` implementations open the `/dev/random` and `/dev/urandom` files which are used as sources.
These files are usually opened in class initializers.
To avoid capturing state from the machine that runs the `native-image` builder, these classes need to be initialized at run time.

## Custom Service Types

By default, only services specified in the JCA framework are automatically registered. To automatically register custom service types, you can use the `-H:AdditionalSecurityServiceTypes` option.
Note that for automatic registration to work, the service interface must have a `getInstance` method and have the same name as the service type.
If relying on the third-party code that does not comply to the above requirements, a manual configuration will be required. In that case, providers for such services must explicitly be registered using the `-H:AdditionalSecurityProviders` option. Note that these options are only required in very specific cases and should not normally be needed.

### Further Reading

* [URL Protocols in Native Image](URLProtocols.md)
* [Jipher JCE with Native Image](../../security/JipherJCE.md)