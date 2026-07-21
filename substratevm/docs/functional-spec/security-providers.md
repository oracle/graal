# FS-security-providers: JCA Security Provider Inclusion

The set of security providers and services available in a native executable is determined when the executable is built.
At run time, `Security` lookups and Java Cryptography Architecture (JCA) factory calls expose only the providers and services included by the rules below.

## 1. Provider Metadata and Lookup Results

Registering a provider class for reflection through any of the following metadata includes the provider and all of its services:

- access to the provider type;
- access to its declared nullary constructor; or
- access to its public static `provider()` method.

If an included provider is present in the configured security-provider list, `Security.getProvider(String)` returns it and `Security.getProviders()` contains it.
JCA factory calls can use the services declared by that provider, subject to their normal algorithm and provider arguments.

If exact reachability metadata checking is enabled, an operation that attempts to load an omitted provider reflectively reports `MissingReflectionRegistrationError` for the provider type.
Native Image must not replace that error with a security-provider-specific exception.

If no reflective provider access occurs, an omitted provider remains unavailable through the normal JCA API results.
`Security.getProvider(String)` returns `null` when the provider is absent from the provider list, and a factory call for an unavailable provider or algorithm reports `NoSuchProviderException` or `NoSuchAlgorithmException`, as appropriate.

## 2. Service Factory Calls

By default, a reachable JCA service factory or JDK security-service facade, including GSS, can include the providers and services needed by that call even when the provider has no reflection metadata.
For example, a reachable `Signature.getInstance(String)` call can make a matching signature implementation available without separately registering its provider.

A provider registered through a static `provider()` method has the same observable services as a provider registered through its constructor.
Metadata collected by tracing must preserve a loading path that makes subsequent `Security.getProvider(String)` and JCA factory calls behave the same way.

### 2.1 Explicit Provider Registration Future Default

With `--future-defaults=explicit-security-provider-registration`, service-factory reachability alone does not include a provider or its services.
The provider must have one of the reflection registrations listed in section 1.
Without that metadata, a factory call for an algorithm supplied only by that provider reports that the algorithm is unavailable.
A direct lookup that attempts to load the omitted provider follows the missing-registration behavior in section 1.

## 3. Programmatically Supplied Providers

An application can construct a provider and pass it directly to a JCA factory or add it with `Security.addProvider(Provider)`.
The provider's class still requires the metadata listed in section 1 before JCE can verify and use its services.

If the metadata is present, provider-name lookups and factory calls using either the provider object or its name can use the provider's services.
If it is absent and exact reachability metadata checking is enabled, the first operation that requires JCE verification reports `MissingReflectionRegistrationError` for the provider type.
The error and tracing behavior must be the same as for other missing reflective type access.

## 4. Run-Time Provider Initialization

When security providers are initialized at run time, Native Image reconstructs the configured provider list using only providers included in the executable.
An omitted provider's services remain unavailable.
A lookup either returns `null` when the provider is absent from the reconstructed list or reports the missing-registration error from section 1 when it attempts reflective loading.

Exhausting the configured provider list must not fail merely because no class-path `META-INF/services/java.security.Provider` descriptor is present.
An absent descriptor does not make an omitted provider or its services available.
