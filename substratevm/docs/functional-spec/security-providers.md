# FS-security-providers: JCA Security Provider Inclusion

Native Image determines at build time which security provider classes and service implementations a native executable can use.
Including a provider does not by itself place the provider in the run-time provider list.
A provider is exposed by name only when it is included and is either present in the configured provider list or successfully added with `Security.addProvider(Provider)`.
An included provider can also be used through a Java Cryptography Architecture (JCA) factory overload that accepts a provider object, subject to the verification requirements below.

## 1. Provider States and Common Rules

### 1.1 Included Providers and Services

An **included provider** is a provider whose class and supported services are retained in the native executable.
An **included service** is a service whose implementation and required reflective construction metadata are retained in the native executable.
Native Image includes providers and services according to sections 2 and 3.

Inclusion is a build-time property.
Constructing a provider object at run time does not add omitted provider services to the executable.

### 1.2 Run-Time Provider List

The **run-time provider list** contains included providers selected from the configured security-provider list and reflects subsequent changes made through the standard `Security` API.
Filtering omitted providers must preserve the relative order of the remaining configured providers.
Including a provider through metadata does not insert a provider that is absent from the configured list.

### 1.3 Service Availability

A service is **available** when its implementation is included and the corresponding factory call can select its provider.
A name-based call can select only a provider in the run-time provider list.
A provider-object call can select the supplied included provider without adding it to that list.
Algorithm aliases and provider selection otherwise follow the standard JCA API behavior.

## 2. Explicit Provider Inclusion

### 2.1 Qualifying Reflection Metadata

Registering any of the following reflection access for an eligible provider class includes that provider:

- access to the provider type;
- access to its declared nullary constructor; or
- access to its declared public static nullary `provider()` method whose return type is assignable to `Provider`.

These alternatives are inclusion signals.
The provider class must still satisfy the construction requirements in section 2.2 regardless of which signal is registered.

### 2.2 Eligible Provider Classes

An eligible provider class is a concrete `Provider` subtype that Native Image can construct using either its declared nullary constructor or the `provider()` method described in section 2.1.
A declared nullary constructor does not have to be public.

When both construction paths exist, Native Image uses the declared nullary constructor.

### 2.3 Inclusion Effects

Explicitly including an eligible provider includes each valid service declared by the provider whose implementation class Native Image can resolve.
JCA factory calls can use those services subject to the service-availability rules in section 1.3.
Provider metadata does not change the configured provider order or make an unconfigured provider visible by name.

A provider registered through `provider()` must expose the same services through `Security` lookups and JCA factory calls as it exposed when inspected at build time.

## 3. Service-Driven Inclusion

### 3.1 Default Compatibility Behavior

By default, reachability of a JCA service factory or JDK security-service facade can include services of the corresponding service type without reflection metadata for their providers.
This compatibility behavior applies to supported facades such as the Generic Security Services API (GSS-API).

For example, reachability of a `Signature.getInstance` overload can cause signature services and their providers to be included.
This rule is based on reachability of the service factory and service type; it does not imply build-time evaluation of the run-time algorithm argument.

### 3.2 Explicit Provider Registration Future Default

With `--future-defaults=explicit-security-provider-registration`, service-factory or facade reachability alone does not include a provider or its services.
The provider must have one of the reflection registrations in section 2.1.
Without that metadata, a factory call for an algorithm supplied only by the omitted provider reports that the algorithm is unavailable as specified in section 4.2.
A lookup that reflectively loads the omitted provider follows section 4.3.

## 4. Lookups and Errors

### 4.1 Provider List Lookups

`Security.getProvider(String)` returns an included provider when that provider is in the run-time provider list.
`Security.getProviders()` contains the same provider and preserves the list ordering described in section 1.2.

If a provider is not in the run-time provider list and the lookup does not attempt to load it reflectively, `Security.getProvider(String)` returns `null`.

### 4.2 Factory Call Results

JCA factory calls retain their standard distinction between a missing provider and a missing algorithm:

- a factory overload given the name of a provider that is not in the run-time provider list reports `NoSuchProviderException`;
- a factory call that can select a provider but cannot find an included implementation for the requested algorithm reports `NoSuchAlgorithmException`; and
- a factory overload given a provider object follows section 5.1 instead of requiring that provider to be in the run-time provider list.

These results apply when no missing reflection registration is encountered first.

### 4.3 Missing Reflection Registration

When exact reachability metadata checking is enabled, an operation that reflectively loads an omitted provider reports `MissingReflectionRegistrationError` for the provider type.
Native Image must not replace that error with `NoSuchProviderException`, `NoSuchAlgorithmException`, or another security-provider-specific exception.

This requirement applies both to loading a provider from the configured provider list and to JCE verification of a programmatically supplied provider.
Without exact reachability metadata checking, this specification does not guarantee a particular missing-registration diagnostic.

## 5. Programmatically Supplied Providers

### 5.1 Provider-Object Factory Calls

An application can construct a provider and pass it directly to a JCA factory.
Direct construction does not waive the inclusion requirements in section 2.
If the provider is included, the factory can use its included services without the provider being in the run-time provider list.

If the provider is omitted and the operation requires Java Cryptography Extension (JCE) verification, the operation follows the missing-registration behavior in section 4.3.

### 5.2 Programmatic Provider-List Changes

An application can call `Security.addProvider(Provider)` or `Security.insertProviderAt(Provider, int)` with a constructed provider.
Insertion does not include an omitted provider or any of its services.
After successful insertion of an included provider, provider-name lookups and name-based factory calls can select its included services.
Removal with `Security.removeProvider(String)` makes the provider unavailable to subsequent name-based lookups without affecting provider objects already held by the application.

Adding a provider does not itself require JCE verification.
The first subsequent operation that requires JCE verification follows section 5.3.
Provider position, duplicate-name handling, insertion return values, and removal otherwise follow the standard `Security` API behavior.

### 5.3 JCE Verification

Native Image must preserve the JCE verification outcome established for every included provider and apply it to run-time instances of that provider class.
A run-time instance must receive the same outcome when its provider name differs from the name observed at build time.

An operation that requires JCE verification of an omitted provider follows section 4.3.
Provider services that do not require JCE verification remain subject to the inclusion and availability rules in sections 1 and 2.

## 6. Tracing Metadata

Metadata collected by the Tracing Agent or native metadata tracing from a successful provider lookup must be sufficient for a subsequently built native executable to perform the same lookup and use the same provider services without additional provider metadata.
The collected metadata must retain a supported construction path: declared nullary constructor access or access to the static `provider()` method.

Tracing a missing provider registration must use the ordinary reflection metadata format and diagnostics.
It must not introduce a security-provider-specific metadata category or error.

## 7. Run-Time Provider Initialization

### 7.1 Provider List Construction

With `--future-defaults=run-time-initialize-security-providers`, Native Image initializes the run-time provider list from the configured security properties using only included providers.
An omitted provider is not added to the list, and its services remain unavailable.
Filtering omitted providers preserves the ordering and standard lookup results specified in sections 1.2 and 4.

### 7.2 Provider Service Descriptors

A class-path _META-INF/services/java.security.Provider_ descriptor does not by itself include the named provider.
If the provider is omitted, iterating to its descriptor can report the standard `ServiceConfigurationError` or missing-reflection error, and the provider's services remain unavailable.
