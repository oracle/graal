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

This page explains Native Image support of the [Java Cryptography Architecture (JCA)](https://docs.oracle.com/en/java/javase/25/security/java-cryptography-architecture-jca-reference-guide.html) framework.

The JCA framework uses a provider architecture to access security services such as digital signatures, message digests, certificates and certificate validation, encryption (symmetric/asymmetric block/stream ciphers), key generation and management, and secure random number generation, etc.
To achieve algorithm independence and extensibility it relies on reflection, therefore it requires a custom configuration in Native Image.
By default the `native-image` builder uses static analysis to discover which of these services are used (see next section for details).
The automatic registration of security services can be disabled with `-H:-EnableSecurityServicesFeature`.
Then a custom reflection configuration file or feature can be used to register the security services required by a specific application.
Note that when automatic registration of security providers is disabled, all providers are, by default, filtered from special JDK caches that are necessary for security functionality.
In this case, register the provider class and its nullary constructor for reflection in _reachability-metadata.json_, for example:

```json
{
  "reflection": [
    {
      "type": "com.example.security.CustomProvider",
      "methods": [
        {
          "name": "<init>",
          "parameterTypes": []
        }
      ]
    }
  ]
}
```

Alternatively, collect the metadata by running your application on the JVM with the [Tracing Agent](AutomaticMetadataCollection.md).

## Security Services Automatic Registration

The mechanism, implemented in the `com.oracle.svm.hosted.SecurityServicesFeature` class, uses reachability of specific API methods in the JCA framework to determine which security services are used.

Each JCA provider registers concrete implementation classes for the algorithms it supports.
Each of the service classes (`Signature`, `Cipher`, `Mac`, `KeyPair`, `KeyGenerator`, `KeyFactory`, `KeyStore`, etc.) declares a series of `getInstance(<algorithm>, <provider>` factory methods which provide a concrete service implementation.
When a specific algorithm is requested, the framework searches the registered providers for the corresponding implementation classes and dynamically allocates objects for concrete service implementations.
The `native-image` builder uses static analysis to discover which of these services are used.
It does so by registering reachability handlers for each of the `getInstance()` factory methods.
When it determines that a `getInstance()` method is reachable at run time, it automatically performs the reflection registration for all the concrete implementations of the corresponding service type.
Provider classes discovered as reachable subtypes of `java.security.Provider` are treated only as candidates for provider inclusion.
The builder includes such a provider and all of its services only when the provider class is registered for reflection, either by type access, its declared nullary constructor, or its static `provider()` method.
To apply this reflection requirement to providers selected by reachable service factories, use `--future-defaults=explicit-security-provider-registration`.
With this future default, a factory does not make an unregistered provider or its services available.

Tracing of the security services automatic registration can be enabled with `-H:+TraceSecurityServices`.
The report will detail all registered service classes, the API methods that triggered registration, and the parsing context for each reachable API method.

> Note: The `--enable-all-security-services` option is now deprecated and it will be removed in a future release.

## Provider Initialization

Currently, security providers are initialized at build time.
To move their initialization to run time, use the option `--future-defaults=run-time-initialize-security-providers`, `--future-defaults=all`, or `--future-defaults=run-time-initialize-jdk`.
Providers listed in the build-time `java.security` configuration are still verified at build time.
Providers included only through reflection metadata are treated as explicitly configured, since run-time codebase verification is not available in Native Image.
Run-time initialization of security providers helps reduce image heap size.

## Provider Registration

The `native-image` builder captures the list of providers and their preference order from the underlying JVM.
The provider order is specified in the `java.security` file under `<java-home>/conf/security/java.security`.
New security providers cannot be registered at run time by default (see the section above); all providers must be statically configured at executable build time.
If the user specifies `--future-defaults=run-time-initialize-security-providers`, `--future-defaults=all`, or `--future-defaults=run-time-initialize-jdk` to move providers initialization to run time, then a specific properties file can be used via the command line option `-Djava.security.properties=<path>`.

## Providers Reordering at Run Time

It is possible to reorder security providers at run time, however only existing provider instances can be used.
For example, if the `BouncyCastle` provider is registered at build time and you want to insert it at position 1 at run time:
```java
Provider bcProvider = Security.getProvider("BC");
Security.removeProvider("BC");
Security.insertProviderAt(bcProvider, 1);
```

If `--future-defaults=all` or `--future-defaults=run-time-initialize-jdk` is enabled, the list of providers is constructed at run time.
The same approach to manipulating providers can then be used.

## SecureRandom

Native Image initializes `NativePRNG`, its seed generators, and related entropy-holding classes at
run time.
This prevents `/dev/random`, `/dev/urandom`, and machine-specific seed state from being captured
on the image builder.
Class-initialization safety is separate from provider registration: a reachable `SecureRandom`
acquisition also triggers registration of the complete configured-provider set that declares
`SecureRandom` services.

## Custom Service Types

By default, Native Image automatically detects only service types specified in the JCA framework.
The `-H:AdditionalSecurityServiceTypes` option is deprecated.
Register the provider class and its supported construction path in _reachability-metadata.json_ so
Native Image retains its complete service catalog, including custom service types.
Alternatively, collect this metadata with the Tracing Agent.
For compatibility with automatic service-driven registration, the service interface must have a
`getInstance` method and the same name as the service type.
If you rely on third-party code that does not comply with these requirements, manual configuration is required.

### Further Reading

* [URL Protocols in Native Image](URLProtocols.md)
* [Jipher JCE with Native Image](../../security/JipherJCE.md)
