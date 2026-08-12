# FS-002-security-providers: JCA Security Provider Registration and Run-Time Access

This specification defines Native Image behavior for Java Cryptography Architecture (JCA) security
providers.
The JDK can acquire or select a provider at run time only if the provider class was [*registered for
reflection*](#11-registered-providers-and-services) at build time.
Registration alone does not install a provider: it does not add one to the run-time provider list
([§1.3](#13-run-time-provider-list)) that `Security.getProviders()` returns, that
`Security.getProvider(String)` searches, and that name-based JCA factory overloads such as
`Signature.getInstance(String, String)` select from.
Native Image builds that list at run time from the `security.provider.<n>` security properties
configured at build time, keeping only the providers that are registered.
With `--exact-reachability-metadata`, reflective acquisition of an unregistered provider throws an
error that identifies the missing provider class.

[§7](#7-transition-to-the-future-defaults) defines the future-default options that select this
behavior during the transition from service-driven provider inclusion and build-time provider-list
initialization.
The implementation is described by [§AR-002-security-providers](../architecture/security-providers.md).

## Notation

This specification uses the following [notation](README.md#notation).
Its default package is `java.security`, so a single identifier refers to a class or interface of
that package, for example `Provider` and `Security.getProviders`.

It uses these further terms:

- Initialization of the run-time provider list, defined in [§1.3](#13-run-time-provider-list), is
  not class initialization (JLS §12.4), although [§7.1](#71-run-time-provider-list-initialization)
  changes when the class initialization that populates that list runs.
- A *service provider class* is a public class or interface that `java.util.ServiceLoader` locates
  as a provider of a service: the class named in the `with` clause of a `provides` directive
  (JLS §7.7.4) of a module declaration, or a class named in a class-path provider-configuration
  file, for example _META-INF/services/java.security.Provider_.
  It is the class that `java.util.ServiceLoader` examines for a provider constructor or provider
  method.
- A *provider implementation class* is the concrete `Provider` subtype of an acquired provider
  instance.
  A provider method can return an implementation whose class differs from the service provider
  class.
- A *provider constructor* and a *provider method* are the two construction paths
  `java.util.ServiceLoader` uses to instantiate a service provider.
  [§1.1](#11-registered-providers-and-services) defines how those paths resolve a provider
  implementation class.

## 1. Provider Reflection Registration

### 1.1 Registered Providers and Services

A provider implementation class is **registered for reflection** if and only if reflection metadata
registers access to its type or to a supported construction path that resolves to an instance of
that class, whether that metadata comes from the application or from a platform-owned registration
signal ([§2.4](#24-securerandom-providers)).

A **supported construction path** that resolves a provider implementation class is either:

1. the implementation class's **provider constructor**, when the implementation class is public
   and concrete: a public constructor with no formal parameters; or
2. a **provider method** declared by a public service provider class in an explicit module: a public
   static method named `provider` with no formal parameters whose return type is assignable to
   `Provider` and whose invocation returns an instance of the implementation class.

For the provider-method path, the service provider class can be an interface, an abstract class, or
a class that is not assignable to `Provider`.
The returned implementation class need not be public.
A provider implementation class is **JDK-constructible** if and only if a supported construction
path resolves an instance of it.
A **registered provider** is an instance of a registered provider implementation class.
A **registered service** is a valid service whose implementation class and required reflective
construction metadata Native Image retains in the executable.
Registration is a build-time property: constructing a provider at run time registers no provider.

> These are the paths the JDK uses to instantiate a configured provider, because it loads service
> provider classes through `java.util.ServiceLoader`.
> A provider that the JDK can construct on HotSpot therefore remains constructible in a native
> executable; [§REQ-002-security-providers.1](requirements/security-providers.md#1-construction-parity)
> requires it.
> *Provider constructor* and *provider method* are the terms `java.util.ServiceLoader` uses for
> them.

JDK-managed acquisition requires a JDK-constructible registered provider.
An application-supplied provider need not be JDK-constructible, because the application already
possesses its instance.
Native Image does not inspect such a provider at build time, so each service used at run time must
be retained independently, for example through metadata collected while tracing the corresponding
factory call.

### 1.2 JDK-Managed Providers and Acquisition

A **JDK-managed provider** is a provider instance that the JDK creates or discovers, as opposed to
one that application code constructs and supplies to the JDK.
JDK-managed acquisition paths load providers from the configured security properties, discover them
through a service-provider descriptor, select them through a JCA factory or security-service facade,
and create them through a JDK default or fallback path.

The public JDK API surface covered by this definition includes:

- provider lookup, enumeration, filtering, and algorithm discovery through
  `Security.getProvider(String)`, the `Security.getProviders` overloads, and
  `Security.getAlgorithms(String)`;
- provider-service lookup and instantiation through `Provider.getService(String, String)`,
  `Provider.getServices()`, and `Provider.Service.newInstance(Object)`;
- JCA factory selection, for example through the `Signature.getInstance` overloads, together with
  provider exposure through `Signature.getProvider()`;
- security-service facades, including `org.ietf.jgss.GSSManager.getInstance()`,
  `javax.security.sasl.Sasl.createSaslClient`, and `javax.security.sasl.Sasl.createSaslServer`; and
- service discovery through `java.util.ServiceLoader.load(Class)` and default construction through
  `new SecureRandom()`.

This list is not exhaustive: other JCA engine factory overloads, provider-exposing engine methods,
and the internal paths that implement these APIs follow the same rule.

The JDK **acquires** a provider when a JDK API or implementation path returns the provider or one of
its `Provider.Service` objects to application code, selects the provider to back a JCA engine or
security-service facade, or reports the provider or one of its algorithms as available.

An application constructing its own provider through a statically resolved constructor is not JDK
acquisition; [§5](#5-programmatically-supplied-providers) specifies the narrower operations allowed
for such objects.
Reflective provider loading and construction remain subject to reflection registration and
[§4.3](#43-missing-reflection-registration).

### 1.3 Run-Time Provider List

The **run-time provider list** contains the registered providers selected from the configured
security properties and reflects subsequent changes made through the standard `Security` API.
Filtering unregistered providers must preserve the relative order of the remaining configured
providers.
Registering a provider for reflection does not insert a provider that the configured list omits.

### 1.4 Service Availability

A service is **available** if and only if it is registered and the corresponding factory call can
select its provider.
A name-based call can select only a provider in the run-time provider list; a provider-object call
can select a supplied registered provider without adding it to that list.
Algorithm aliases and provider selection otherwise follow the standard JCA API behavior.

## 2. Registration Semantics

### 2.1 Qualifying Reflection Metadata

Type access is the provider registration signal: registering access to the provider implementation
type registers the provider implementation class.
Access to a supported construction path also registers the provider implementation class, because
requesting a construction path states a stronger intent than naming the type.
Any one of these signals therefore registers the provider implementation class.
Type access alone does not make a provider JDK-constructible.
A provider implementation class is not registered under any circumstance other than these signals,
the platform-owned signal in [§2.4](#24-securerandom-providers), and the compatibility behavior in
[§7.3](#73-earlier-service-driven-inclusion-behavior).
[§REQ-002-security-providers.9.1](requirements/security-providers.md#91-closure-of-the-registration-signals)
states how this closure is discharged.

> Using ordinary reflection metadata as this registration signal implements
> [§AR-007-standard-jca-semantics.2](decisions/standard-jca-semantics.md#2-decision), and
> [§REQ-002-security-providers.2](requirements/security-providers.md#2-metadata-closure) is the
> acceptance criterion for it.
> Separating the registration signal from the construction path implements
> [§AR-005-provider-registration-signals.2](decisions/provider-registration-signals.md#2-decision).

### 2.2 Provider Construction

Native Image constructs a provider through the path the JDK would use for its service provider
class.
When that class is in an explicit module and declares a qualifying provider method, Native Image
calls that method; otherwise Native Image calls the provider constructor.
The provider constructor belongs to the provider implementation class, but a provider method can
belong to a different service provider class.

The following members are not construction paths, because `java.util.ServiceLoader` does not
instantiate a provider through them:

- a constructor that is not public;
- a constructor with formal parameters;
- a `provider()` method on a class-path provider; and
- a `provider()` method that is not public, not static, has formal parameters, or has a return type
  that is not assignable to `Provider`.

Reflection metadata for such a member does not make a provider implementation class
JDK-constructible.

A provider registered through `provider()` must expose the same services through `Security` lookups
and JCA factory calls as it exposed when Native Image inspected it at build time.

### 2.3 Registration Effects

Registering a JDK-constructible provider includes every valid service the provider declares whose
implementation class Native Image can resolve, and retains the metadata required to construct those
implementations.
When the configured provider list contains multiple instances of the same registered provider
implementation class, Native Image retains every instance and the valid, resolvable services each
instance declares.
Registering a provider class that is not JDK-constructible includes no services; services used
through an application-supplied instance must be retained independently.
Provider registration does not change the configured provider order or make an unconfigured provider
visible by name.

> This behavior implements
> [§AR-003-complete-security-provider-registration.2](decisions/complete-security-provider-registration.md#2-decision).

### 2.4 SecureRandom Providers

When a `SecureRandom` acquisition path is reachable, Native Image must register the complete
configured providers that declare `SecureRandom` services.
The acquisition path is a platform-owned conditional provider-registration signal, so the
application need not supply reflection metadata for those providers.
This registration has the effects of [§2.3](#23-registration-effects), including retention of every
valid, resolvable service each registered provider declares.

This rule applies to the `SecureRandom` constructors, the `SecureRandom.getInstance` overloads, and
JDK paths that perform the same default-provider selection.
It is not the earlier service-driven inclusion behavior of
[§7.3](#73-earlier-service-driven-inclusion-behavior): the platform supplies a provider-registration
signal, and the ordinary complete-provider semantics of [§2.3](#23-registration-effects) apply.
Native Image must not register these providers when no `SecureRandom` acquisition path is reachable.

Native Image internal runtime randomness must cause this registration only in an executable that
includes the runtime-compilation subsystem that consumes that randomness.
The presence of the optional internal randomness implementation must not register SUN in an ordinary
executable.

> This behavior implements
> [§AR-004-default-secure-random-provider.2](decisions/default-secure-random-provider.md#2-decision).

## 3. Permitted Run-Time Access

### 3.1 JDK-Managed Acquisition

Every JDK-managed provider acquired at run time must be a JDK-constructible registered provider.
This rule applies uniformly to:

- direct provider APIs, including provider enumeration, name lookup, filtering, and algorithm
  discovery through the `Security` methods listed in
  [§1.2](#12-jdk-managed-providers-and-acquisition);
- reflective provider loading or construction by class name;
- JCA factories, the `Provider` and `Provider.Service` methods listed in
  [§1.2](#12-jdk-managed-providers-and-acquisition), and engine objects that expose their selected
  provider;
- security-service facades that select provider services without using a JCA engine factory,
  including GSS-API and SASL; and
- service loading, default selection, and fallback paths that construct a provider or service
  implementation directly.

A direct JDK fallback must not bypass registration when the configured provider list contains no
matching registered provider.
Default `SecureRandom` construction follows the platform-owned conditional registration signal in
[§2.4](#24-securerandom-providers); other fallbacks must fail before exposing an unregistered
provider.

### 3.2 Provider List Lookups

`Security.getProvider(String)` returns a registered provider that is in the run-time provider list.
If the provider is not in the list and the lookup does not attempt to load it reflectively, the call
returns `null`.

`Security.getProviders()` contains the same provider and preserves the ordering described in
[§1.3](#13-run-time-provider-list).
`Security.getProviders(String)` and `Security.getProviders(java.util.Map)` return only registered
providers from that list, and `Security.getAlgorithms(String)` reports an algorithm only when a
registered provider in that list declares it.

### 3.3 JCA Factories and Security-Service Facades

A name-based JCA factory call can use the registered services of a provider in the run-time provider
list.
A factory overload that accepts a provider object can use that provider's registered services
without requiring it to be in the list.

A factory call can use only registered service implementations; it does not evaluate a run-time
algorithm argument at build time or make additional providers or services available.
The same registration requirement applies when a facade or a JDK implementation path selects a
provider service without calling a public JCA factory.

### 3.4 Programmatic Access

An application can construct a registered provider and pass it directly to a JCA factory, or add it
to the run-time provider list through `Security.addProvider(Provider)` or
`Security.insertProviderAt(Provider, int)`.
[§5](#5-programmatically-supplied-providers) specifies these operations in detail.

## 4. Prohibited Run-Time Access and Errors

### 4.1 Unregistered Providers

An unregistered JDK-managed provider must not be returned, selected, or reported as available at run
time.
The JDK must not expose it partially by returning the provider while omitting some services,
advertising its algorithms without allowing their use, returning a `Provider.Service` without a
usable implementation, or producing an engine or facade backed by that provider.

Failure must occur at the acquisition boundary, before application code receives a provider,
service, engine, or facade that represents the unregistered provider as available.
Where [§4.2](#42-standard-unavailable-results) and [§4.3](#43-missing-reflection-registration)
specify no standard result or missing-registration diagnostic, the operation must fail before
exposure, but this specification does not prescribe the exception type.
Reachability of a JCA service factory or JDK security-service facade must not make the provider or
its services available, and neither a service-provider descriptor nor a JDK default or fallback
implementation can register the provider after the executable has been built.

Application-supplied provider objects follow [§5](#5-programmatically-supplied-providers) and do not
relax these requirements for JDK-managed providers.
[§REQ-002-security-providers.4](requirements/security-providers.md#4-no-exposure-of-an-unregistered-provider)
states the evidence for this rule, and
[§REQ-002-security-providers.9.2](requirements/security-providers.md#92-the-acquisition-boundary) states
how it holds for the acquisition paths that [§1.2](#12-jdk-managed-providers-and-acquisition) does
not enumerate.

### 4.2 Standard Unavailable Results

JCA factory calls retain their standard distinction between a missing provider and a missing
algorithm:

- a factory overload given the name of a provider that is not in the run-time provider list throws
  `NoSuchProviderException`;
- a factory call that can select a provider but cannot find a registered implementation for the
  requested algorithm throws `NoSuchAlgorithmException`; and
- a factory overload given a provider object follows [§5.1](#51-provider-object-factory-calls)
  instead of requiring that provider to be in the run-time provider list.

These results apply when no missing reflection registration is encountered first.

### 4.3 Missing Reflection Registration

When exact reachability metadata checking is enabled, an operation that reflectively loads an
unregistered provider throws `org.graalvm.nativeimage.MissingReflectionRegistrationError` for the
provider class.
Native Image must not replace that error with `NoSuchProviderException`, `NoSuchAlgorithmException`,
or another security-provider-specific exception.
The diagnostic must identify the provider class by its binary name and give every instruction needed
to add sufficient reflection metadata and rebuild, including the metadata entry and the location of
`reachability-metadata.json`.
For a provider with a supported construction path, the type-only entry suggested by a missing-type
diagnostic is sufficient under [§2.1](#21-qualifying-reflection-metadata); Native Image retains the
provider's construction and service metadata during the subsequent build.
For an application-supplied provider without a supported construction path, that entry registers the
provider class but retains no service implementations.
[§REQ-002-security-providers.3](requirements/security-providers.md#3-diagnostic-sufficiency) requires
the suggested entry to repair the reported failure.

This requirement applies both to loading a provider from the configured provider list and to Java
Cryptography Extension (JCE) verification of a programmatically supplied provider.
Without exact reachability metadata checking, the operation throws an actionable
`java.lang.SecurityException` that identifies the unregistered provider class instead of an internal
error.

## 5. Programmatically Supplied Providers

### 5.1 Provider-Object Factory Calls

An application can construct a provider and pass it directly to a JCA factory.
Because the application already possesses this object, constructing it and calling its ordinary Java
methods is not JDK-managed provider acquisition as defined in
[§1.2](#12-jdk-managed-providers-and-acquisition).
The provider class must be registered for reflection but need not be JDK-constructible.
The factory can use its registered services without the provider being in the run-time provider
list.

If the provider is unregistered and the operation requires JCE verification, the operation follows
the missing-registration behavior in [§4.3](#43-missing-reflection-registration).

### 5.2 Programmatic Provider-List Changes

An application can call `Security.addProvider(Provider)` or
`Security.insertProviderAt(Provider, int)` with a constructed provider.
Insertion registers neither the provider nor any of its services: it can still make the supplied
object retrievable from the run-time provider list according to the standard `Security` API
behavior, but JCA factory calls cannot use its unregistered services.
This exception applies only to the same application-supplied object; it does not allow the JDK to
create, discover, or substitute an unregistered provider.
After successful insertion of a registered provider, provider-name lookups and name-based factory
calls can select its registered services.
Removal with `Security.removeProvider(String)` makes the provider unavailable to subsequent
name-based lookups without affecting provider objects the application already holds.

Adding a provider does not itself require JCE verification; the first subsequent operation that
requires JCE verification follows [§5.3](#53-jce-verification).
Provider position, duplicate-name handling, insertion return values, and removal otherwise follow
the standard `Security` API behavior.

### 5.3 JCE Verification

Before a registered provider supplies a service for which the JDK requires Java Cryptography
Extension (JCE) verification, Native Image must have established a verification outcome for that
provider at build time.
Registration is necessary for such an operation but is not successful verification.

For a registered application-supplied provider class that is not one of the build-time configured
providers, Native Image establishes the successful verification outcome from the class registration
without constructing a provider instance.

> This permits JCE use of an existing application-supplied instance while avoiding an unsupported
> attempt to reconstruct it.

Native Image must preserve the build-time verification outcome and apply it to run-time instances of
that provider class, including an instance whose provider name differs from the name observed at
build time.
A failed verification outcome must prevent every run-time JCE operation that requires verification
from using the provider.
The operation must expose that failure through the standard JCE behavior of the invoked factory
overload; it must not return an engine backed by the provider or continue with a partially usable
provider.

An operation that requires JCE verification of an unregistered provider follows
[§4.3](#43-missing-reflection-registration).
Provider services that do not require JCE verification remain subject to the registration and
availability rules in [§1](#1-provider-reflection-registration) and [§2](#2-registration-semantics).

## 6. Tracing Metadata

### 6.1 Provider and Service Coverage

Metadata collected by the Tracing Agent or native metadata tracing from a successful provider lookup
must let a subsequently built executable perform the same lookup and use the same provider services
without additional provider metadata.
This includes provider enumeration and filtering through the `Security` APIs when the JDK loaded and
cached a returned provider before the traced operation.
Provider-list mutation through `Security.addProvider`, `Security.insertProviderAt`, or
`Security.removeProvider` is neither provider enumeration nor lookup.
Tracing such a mutation must register a supplied provider that the operation observes, but it must
not register unrelated configured providers that the JDK loads or inspects while maintaining the
provider list.

For a JDK-managed provider, the collected metadata must retain the supported construction path
defined in [§1.1](#11-registered-providers-and-services): access to the provider constructor or to
the provider method.
For an application-supplied provider, tracing must register the provider class without inventing a
constructor access, and must independently retain each service implementation exercised by the
traced factory calls.
This includes a service implementation named only by `Provider.Service.getClassName()`: the
caller-filtered trace must retain the construction access performed inside
`Provider.Service.newInstance` and attribute it to the application operation that selected the
service, including when `Provider.Service` reuses its cached implementation class and therefore
performs no subsequent reflective class lookup.
The trace may locate `Provider.Service.newInstance` through contiguous helper frames declared by
`Provider.Service`, but it must not cross a frame declared by another class.

Tracing a missing provider registration must use the ordinary reflection metadata format and
diagnostics; it must not introduce a security-provider-specific metadata category or error.
[§REQ-002-security-providers.8](requirements/security-providers.md#8-tracing-round-trip) requires
collected metadata to be both sufficient and minimal.

### 6.2 Observational Transparency

Tracing must observe the application's provider lookup, service lookup, and service instantiation
without performing any of those operations an additional time.
It must not initialize or cache a provider, service, implementation class, or resource while
recursive tracing is suppressed.
Metadata must include the nested reflection and resource accesses performed by the application's
actual operation.

## 7. Transition to the Future Defaults

[§1](#1-provider-reflection-registration) through [§6](#6-tracing-metadata) specify the planned
default behavior.
The following options select the transition behavior while the earlier behaviors remain available
for compatibility.
Option selection is a build-time property: run-time changes to system properties, including
properties that report enabled future defaults, must not change the behavior selected while building
the executable.
Run-time provider-list initialization can be selected independently; explicit provider registration
depends on it and enables it implicitly.
The supported combinations are legacy inclusion with build-time initialization, legacy inclusion
with run-time initialization, and explicit registration with run-time initialization.
[§REQ-002-security-providers.7](requirements/security-providers.md#7-transition-compatibility) requires
each combination to be specified and selected at build time.

### 7.1 Run-Time Provider-List Initialization

With `--future-defaults=run-time-initialize-security-providers`, Native Image constructs the
run-time provider list from the configured security properties using only registered providers.
When a configured entry names a provider that the JDK resolves through `java.util.ServiceLoader`,
Native Image applies the registration decision to the resolved provider implementation class rather
than treating the provider name as a binary name.
Native Image also retains the service provider class and construction path that produced that
implementation.
At run time, Native Image invokes that retained path directly, without loading or constructing
unrelated provider descriptors.
When that construction does not yield the configured provider, because it fails or because the
constructed instance reports a different provider name, Native Image throws an actionable error that
identifies the provider implementation class and service provider class instead of silently omitting
the provider from the list.
Provider names do not globally identify provider implementation classes: if multiple registered
implementation classes report the same name, Native Image retains their class-based registration
but does not treat that name as an unambiguous configured-provider-to-class mapping.
An unregistered provider is not added to the list, and its services remain unavailable.
Filtering unregistered providers preserves the ordering and lookup results specified in
[§1.3](#13-run-time-provider-list), [§3.2](#32-provider-list-lookups), and
[§4](#4-prohibited-run-time-access-and-errors).
`--future-defaults=explicit-security-provider-registration` enables this behavior implicitly.

### 7.2 Provider Service Descriptors

A class-path _META-INF/services/java.security.Provider_ descriptor does not register the named
provider for reflection.
Native Image preserves the descriptor only when `java.util.ServiceLoader` access to `Provider` is
reachable.
If the provider is unregistered, service loading must not return a provider instance.
Iterating to its descriptor can throw the standard `java.util.ServiceConfigurationError` or a
missing-reflection error, and the provider's services remain unavailable.

### 7.3 Earlier Service-Driven Inclusion Behavior

Without `--future-defaults=explicit-security-provider-registration`, reachability of a JCA service
factory or JDK security-service facade can include services of the corresponding service type even
when their provider classes have no reflection metadata.
In this compatibility mode, pre-existing reflection metadata for a provider remains inert unless
another compatibility registration signal includes that provider; Native Image must not construct
the provider or expand its complete service catalog merely because its type is registered.
Type-only metadata collected for an application-supplied provider still establishes the class-based
JCE verification outcome specified in [§5.3](#53-jce-verification), without causing Native Image to
construct the provider, register a construction path, or expand its service catalog.
This compatibility behavior applies to supported facades such as the Generic Security Services API
(GSS-API); for example, reachability of any `Signature.getInstance` overload can include signature
services and their providers.
When it includes a provider, it preserves the earlier compatibility registration of every public
constructor the provider class declares, not only of its provider constructor.
The rule is based on reachability of the service factory and service type, not on build-time
evaluation of the run-time algorithm argument.

With `--future-defaults=explicit-security-provider-registration`, this compatibility behavior is
disabled.
A factory call for an algorithm supplied only by an unregistered provider follows
[§4.2](#42-standard-unavailable-results), and a lookup that reflectively loads the provider follows
[§4.3](#43-missing-reflection-registration).
The platform-owned `SecureRandom` registration signal in [§2.4](#24-securerandom-providers) remains
enabled.

> That signal registers complete providers for this commonly used JDK facility rather than inferring
> partial provider support from a general service factory.
> This planned behavior implements
> [§AR-006-reachability-independent-runtime-semantics.2](decisions/reachability-independent-runtime-semantics.md#2-decision).

### 7.4 Earlier Build-Time Initialization Behavior

`--future-defaults=run-time-initialize-security-providers` replaces the earlier behavior in which
Native Image initializes the configured provider list at build time.
Omitting both this future default and explicit provider registration retains that earlier
initialization behavior.

> The initialization these options select is the construction of the run-time provider list defined
> in [§1.3](#13-run-time-provider-list).
> Native Image realizes it through the class initialization (JLS §12.4) of the JDK classes that hold
> that list, which the option moves from build time to run time.
