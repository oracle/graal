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

The JCA framework uses a provider architecture to access security services such as digital signatures, message digests, certificates and certificate validation, encryption, key generation and management, and secure random number generation.
To achieve algorithm independence and extensibility, it uses reflection to construct providers and service implementations.
Native Image therefore requires reachability metadata for dynamically accessed providers and services that it cannot discover automatically.
By default the `native-image` builder uses static analysis to discover which of these services are used (see next section for details).
The automatic registration of security services can be disabled with `-H:-EnableSecurityServicesFeature`.
Then a custom reflection configuration file or feature can be used to register the security services required by a specific application.
Note that when automatic registration of security providers is disabled, all providers are, by default, filtered from special JDK caches that are necessary for security functionality.
To use provider reflection metadata as the complete inclusion signal in this case, also enable `--future-defaults=metadata-security-provider-registration`.
Register either the provider class or one of its supported construction paths for reflection in _reachability-metadata.json_, for example:

```json
{
  "reflection": [
    {
      "type": "com.example.security.CustomProvider"
    }
  ]
}
```

Alternatively, collect the metadata by running your application on the JVM with the [Tracing Agent](AutomaticMetadataCollection.md).
The deprecated `-H:AdditionalSecurityProviders=<provider-class>` option remains accepted for compatibility.
Without explicit security-provider registration, this option preserves its previous provider-inclusion behavior, whereas reflection metadata alone does not.
Neither the option nor reflection metadata installs an otherwise unconfigured provider or changes provider order.

## Security Services Automatic Registration

Each JCA provider registers concrete implementation classes for the algorithms it supports.
Each of the service classes ([`Signature`][signature], [`Cipher`][cipher], [`Mac`][mac],
[`KeyPairGenerator`][key-pair-generator], [`KeyGenerator`][key-generator],
[`KeyFactory`][key-factory], [`KeyStore`][key-store], and others) declares a series of
`getInstance(<algorithm>, <provider>)` factory methods which provide a concrete service
implementation.
When a specific algorithm is requested, the framework searches the registered providers for the corresponding implementation classes and dynamically allocates objects for concrete service implementations.
The `native-image` builder uses static analysis to discover which of these services are used.
It does so by registering reachability handlers for each of the `getInstance()` factory methods.
By default, when it determines that a `getInstance()` method is reachable at run time, it automatically registers the configured providers and concrete implementations of the corresponding service type.
Provider classes discovered as reachable subtypes of [`java.security.Provider`][provider] are
treated only as candidates for provider inclusion.
To apply this reflection requirement to providers selected by reachable service factories, use `--future-defaults=metadata-security-provider-registration`.
With this future default, a factory does not make an unregistered provider or its services
available, except when [`SecureRandom`][secure-random] supplies the registration signal described
below.
For a JDK-constructible provider, registering either the provider implementation type or a supported construction path retains the provider's complete service catalog.
A supported construction path is a public no-argument constructor of a public, concrete provider
class, or a public static no-argument [`provider()`][service-loader] method on a public
service-provider class in a named module.
Provider registration does not install a provider.
For JDK-managed lookup, the provider must also have a matching `security.provider.<n>` entry;
alternatively, application code can insert an existing provider instance with the standard
[`Security`][security] API.

Tracing of the security services automatic registration can be enabled with `-H:+TraceSecurityServices`.
The report will detail all registered service classes, the API methods that triggered registration, and the parsing context for each reachable API method.

> Note: The `--enable-all-security-services` option is now deprecated and it will be removed in a future release.

## Provider Initialization

Currently, security providers are initialized at build time.
To move their initialization to run time, use the option `--future-defaults=run-time-initialize-security-providers`, `--future-defaults=metadata-security-provider-registration`, `--future-defaults=all`, or `--future-defaults=run-time-initialize-jdk`.
Explicit security-provider registration enables run-time provider initialization implicitly.
Native Image still records Java Cryptography Extension (JCE) provider-verification outcomes at build time for retained provider classes.
Provider classes that are not part of the build-time provider configuration are treated as successfully verified when Native Image recognizes their instantiation or includes them through reflection metadata, since run-time codebase verification is not available.
Run-time initialization of security providers helps reduce image heap size.

## Provider Registration

The `native-image` builder captures the configured providers and their preference order from the effective build-time security properties.
The provider order is specified in the `java.security` file under `<java-home>/conf/security/java.security`.
In explicit registration mode, a configured provider is available through JDK-managed lookup only
if its implementation type or supported construction path is registered for reflection, except when
[`SecureRandom`][secure-random] supplies the registration signal.
An application can construct a provider directly and pass the existing instance to a provider-object factory overload without provider-class reflection metadata.
It can also add that instance to the provider list at run time with
[`Security.addProvider(Provider)`][security] or
[`Security.insertProviderAt(Provider, int)`][security].
The provider's service implementations and their required construction metadata must already be retained in the executable, and JCE verification must succeed.
To supply a custom security properties file when building with run-time provider initialization, use `-Djava.security.properties=<path>` on the `native-image` command line.

## Providers Reordering at Run Time

It is possible to reorder installed security-provider instances at run time.
For example, if the `BouncyCastle` provider is available and you want to insert it at position 1 at run time:

```java
Provider bcProvider = Security.getProvider("BC");
Security.removeProvider("BC");
Security.insertProviderAt(bcProvider, 1);
```

If `--future-defaults=run-time-initialize-security-providers`, `--future-defaults=metadata-security-provider-registration`, `--future-defaults=all`, or `--future-defaults=run-time-initialize-jdk` is enabled, the list of configured providers is constructed at run time.
The same approach to manipulating providers can then be used.

## SecureRandom

Native Image initializes `NativePRNG`, its seed generators, and related entropy-holding classes at
run time.
This prevents `/dev/random`, `/dev/urandom`, and machine-specific seed state from being captured
on the image builder.
Class-initialization safety is separate from provider registration: a reachable
[`SecureRandom`][secure-random] acquisition also triggers registration of the complete
configured-provider set that declares
[`SecureRandom`][secure-random] services.

## Custom Service Types

By default, Native Image automatically detects only service types specified in the JCA framework.
The `-H:AdditionalSecurityServiceTypes` option remains accepted for compatibility, but is deprecated.
To replace it, enable `--future-defaults=metadata-security-provider-registration` and register either the provider implementation type or one of its supported construction paths in _reachability-metadata.json_.
For a JDK-constructible provider, Native Image then retains the complete service catalog, including custom service types.
Alternatively, collect this metadata with the Tracing Agent.
For compatibility with automatic service-driven registration, the service interface must have a
`getInstance` method and the same name as the service type.
If you rely on third-party code that does not comply with these requirements, manual configuration is required.

### Further Reading

* [URL Protocols in Native Image](URLProtocols.md)
* [Jipher JCE with Native Image](../../security/JipherJCE.md)

[cipher]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/Cipher.html
[key-factory]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyFactory.html
[key-generator]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KeyGenerator.html
[key-pair-generator]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyPairGenerator.html
[key-store]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/KeyStore.html
[mac]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/Mac.html
[provider]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Provider.html
[secure-random]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureRandom.html
[security]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Security.html
[service-loader]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/ServiceLoader.html
[signature]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Signature.html
