# FS-security-providers: JCA Security Provider Registration and Run-Time Access

This specification defines the Native Image behavior for Java Cryptography Architecture (JCA)
security providers.
In Native Image, the JDK can acquire or select a JCA provider at run time only if the provider
class was *registered for reflection* at build time.
With `--exact-reachability-metadata`, attempted reflective acquisition of an unregistered provider
reports an error that identifies the missing provider type.
Native Image constructs the provider list at run time from the build-time configured security
properties and the registered provider classes.

Reflection registration determines which provider classes and services the native executable
contains; it does not by itself add a provider to the run-time provider list.
Section 7 defines the future-default options that select this behavior during the transition from
service-driven provider inclusion and build-time provider-list initialization.

## 1. Provider Reflection Registration

### 1.1 Registered Providers and Services

A provider class is **registered for reflection** for the purposes of this specification when it
is a concrete `Provider` subtype and reflection metadata registers access to the provider type, a
declared nullary constructor, or a qualifying `provider()` method.

A registered provider class is **JDK-constructible** when Native Image can construct it through
either a declared nullary constructor or a declared public static nullary `provider()` method whose
return type is assignable to `Provider`.
A **registered provider** is an instance of a provider class registered for reflection.
A **registered service** is a valid service whose implementation class and required reflective
construction metadata Native Image can retain in the executable.

JDK-managed acquisition requires a JDK-constructible registered provider.
An application-supplied provider does not need to be JDK-constructible because the application
already possesses its instance.
For such a provider, Native Image does not inspect the provider at build time; each service used at
run time must be retained independently, for example through metadata collected while tracing the
corresponding factory call.

Registration is a build-time property.
Constructing a provider object at run time does not register the provider or add omitted services
to the executable.
Run-time changes to system properties, including properties that report enabled future defaults,
must not change the provider-registration policy selected while building the executable.
Section 2.4 defines the platform-owned conditional registration signal for `SecureRandom`
acquisition.

### 1.2 JDK-Managed Providers and Acquisition

A **JDK-managed provider** is a provider instance that the JDK creates or discovers, as opposed to
an instance that application code constructs and supplies to the JDK.
JDK-managed acquisition paths include loading providers from the configured security properties,
discovering them through a service-provider descriptor, selecting them through a JCA factory or
security-service facade, and creating them through a JDK default or fallback path.

The public JDK API surface covered by this definition includes:

- provider lookup, enumeration, filtering, and algorithm discovery through
  `Security.getProvider(String)`, the `Security.getProviders` overloads, and
  `Security.getAlgorithms(String)`;
- provider-service lookup and instantiation through `Provider.getService(String, String)`,
  `Provider.getServices()`, and `Provider.Service.newInstance(Object)`;
- JCA factory selection, for example through the `Signature.getInstance` overloads, together with
  provider exposure through `Signature.getProvider()`;
- security-service facades, including `GSSManager.getInstance()`, `Sasl.createSaslClient`, and
  `Sasl.createSaslServer`; and
- service discovery through `ServiceLoader.load(Class)` and default construction through
  `new SecureRandom()`.

This list identifies the principal public entry points but does not limit the acquisition rule to
them: other JCA engine factory overloads, provider-exposing engine methods, and internal paths
that implement these APIs are subject to the same rule.

The JDK **acquires** a provider when a JDK API or implementation path does any of the following:

- returns the provider or one of its `Provider.Service` objects to application code;
- selects the provider to back a JCA engine or security-service facade; or
- reports the provider or one of its algorithms as available.

This definition covers public APIs and the internal JDK paths that implement them.
An application constructing its own provider object through a statically resolved constructor is
not JDK acquisition; section 5 specifies the narrower operations allowed for such
application-supplied objects.
Reflective provider loading and construction remain subject to reflection registration and
section 4.3.

### 1.3 Run-Time Provider List

The **run-time provider list** contains registered providers selected from the configured security
properties and reflects subsequent changes made through the standard `Security` API.
Filtering unregistered providers must preserve the relative order of the remaining configured
providers.
Registering a provider for reflection does not insert a provider that is absent from the
configured list.

### 1.4 Service Availability

A service is **available** when it is registered and the corresponding factory call can select its
provider.
A name-based call can select only a provider in the run-time provider list; a provider-object call
can select a supplied registered provider without adding it to that list.
Algorithm aliases and provider selection otherwise follow the standard JCA API behavior.

## 2. Registration Semantics

### 2.1 Qualifying Reflection Metadata

Type access, declared nullary constructor access, and qualifying `provider()` method access are
alternative registration signals.
Registering any one of them is sufficient to register the provider class.
Type access alone does not make a provider JDK-constructible.

### 2.2 Provider Construction

A declared nullary constructor does not have to be public.
When both supported construction paths exist, Native Image uses the declared nullary constructor.

A provider registered through `provider()` must expose the same services through `Security`
lookups and JCA factory calls as it exposed when Native Image inspected it at build time.

### 2.3 Registration Effects

Registering a JDK-constructible provider includes every valid service declared by the provider
whose implementation class Native Image can resolve, and retains the metadata required to
construct those service implementations.
When the configured provider list contains multiple instances of the same registered provider
class, Native Image retains every instance and the valid, resolvable services declared by each
instance.
Registering a provider class that is not JDK-constructible does not include its services; services
used through an application-supplied instance must be retained independently.
Provider registration does not change the configured provider order or make an unconfigured
provider visible by name.
This behavior implements §DF-complete-security-provider-registration.2.

### 2.4 SecureRandom Providers

When a `SecureRandom` acquisition path is reachable, Native Image must register the complete
configured providers that declare `SecureRandom` services.
The acquisition path is a platform-owned conditional provider-registration signal, so the
application does not need to supply reflection metadata for those providers.
This registration has the effects specified in section 2.3, including retention of every valid
service that each registered provider declares and whose implementation class Native Image can
resolve.

This rule applies to the `SecureRandom` constructors, the `SecureRandom.getInstance` overloads,
and JDK paths that perform the same default-provider selection.
It is not the earlier service-driven inclusion behavior described in section 7.3: the platform
supplies a provider-registration signal, and the ordinary complete-provider semantics in section
2.3 apply.
Native Image must not register these providers when no `SecureRandom` acquisition path is
reachable.

Native Image internal runtime randomness must cause this registration only in an executable that
includes the runtime-compilation subsystem that consumes that randomness.
The presence of the optional internal randomness implementation must not register SUN in an
ordinary executable.

This behavior implements §DF-default-secure-random-provider.2.

## 3. Permitted Run-Time Access

### 3.1 JDK-Managed Acquisition

Every JDK-managed provider acquired at run time must be a JDK-constructible registered provider.
This rule applies uniformly to:

- direct provider APIs, including provider enumeration, name lookup, filtering, and algorithm
  discovery through the `Security` methods listed in section 1.2;
- reflective provider loading or construction by class name;
- JCA factories, the `Provider` and `Provider.Service` methods listed in section 1.2, and engine
  objects that expose their selected provider;
- security-service facades that select provider services without using a JCA engine factory,
  including GSS-API and SASL; and
- service loading, default selection, and fallback paths that construct a provider or service
  implementation directly.

A direct JDK fallback must not bypass registration when the configured provider list contains no
matching registered provider.
Default `SecureRandom` construction follows the platform-owned conditional registration signal in
section 2.4; other fallbacks must fail before exposing an unregistered provider.

### 3.2 Provider List Lookups

`Security.getProvider(String)` returns a registered provider when that provider is in the run-time
provider list.
If the provider is not in the list and the lookup does not attempt to load it reflectively, the
call returns `null`.

`Security.getProviders()` contains the same provider and preserves the ordering described in
section 1.3.
`Security.getProviders(String)` and `Security.getProviders(Map)` can return only registered
providers from that list, and `Security.getAlgorithms(String)` can report an algorithm only when
at least one registered provider in that list declares it.

### 3.3 JCA Factories and Security-Service Facades

A name-based JCA factory call can use the registered services of a provider in the run-time
provider list.
A factory overload that accepts a provider object can use the registered services of that provider
without requiring it to be in the list.

A factory call can use only registered service implementations; it does not evaluate a run-time
algorithm argument at build time or make additional providers or services available.
The same registration requirement applies when a facade or a JDK implementation path selects a
provider service without calling a public JCA factory.

### 3.4 Programmatic Access

An application can construct a registered provider and pass it directly to a JCA factory, or add
it to the run-time provider list through `Security.addProvider(Provider)` or
`Security.insertProviderAt(Provider, int)`.
Section 5 specifies these operations in detail.

## 4. Prohibited Run-Time Access and Errors

### 4.1 Unregistered Providers

An unregistered JDK-managed provider must not be returned, selected, or reported as available at
run time.
The JDK must not expose it partially by returning the provider while omitting some services,
advertising its algorithms without allowing their use, returning a `Provider.Service` without a
usable implementation, or producing an engine or facade backed by that provider.

Failure must occur at the acquisition boundary, before application code receives a provider,
service, engine, or facade that represents the unregistered provider as available.
When sections 4.2 and 4.3 do not specify a standard result or missing-registration diagnostic,
this specification requires the operation to fail before exposure but does not prescribe the
exception type.
Reachability of a JCA service factory or JDK security-service facade must not make the provider or
its services available, and neither a service-provider descriptor nor a JDK default or fallback
implementation can register the provider after the executable has been built.

Application-supplied provider objects follow section 5 and do not relax these requirements for
JDK-managed providers.

### 4.2 Standard Unavailable Results

JCA factory calls retain their standard distinction between a missing provider and a missing
algorithm:

- a factory overload given the name of a provider that is not in the run-time provider list
  reports `NoSuchProviderException`;
- a factory call that can select a provider but cannot find a registered implementation for the
  requested algorithm reports `NoSuchAlgorithmException`; and
- a factory overload given a provider object follows section 5.1 instead of requiring that
  provider to be in the run-time provider list.

These results apply when no missing reflection registration is encountered first.

### 4.3 Missing Reflection Registration

When exact reachability metadata checking is enabled, an operation that reflectively loads an
unregistered provider reports `MissingReflectionRegistrationError` for the provider type.
Native Image must not replace that error with `NoSuchProviderException`,
`NoSuchAlgorithmException`, or another security-provider-specific exception.
The diagnostic must identify the provider type and give the user all instructions needed to add
sufficient reflection metadata and rebuild the native image, including the metadata entry and the
location of `reachability-metadata.json`.
For a provider with a supported construction path, the type-only entry suggested by a missing-type
diagnostic is sufficient under section 2.1; Native Image retains the provider's construction and
service metadata during the subsequent build.
For an application-supplied provider without a supported construction path, the type-only entry
registers the provider class but does not retain its service implementations.

This requirement applies both to loading a provider from the configured provider list and to Java
Cryptography Extension (JCE) verification of a programmatically supplied provider.
Without exact reachability metadata checking, this specification does not guarantee a particular
missing-registration diagnostic.

## 5. Programmatically Supplied Providers

### 5.1 Provider-Object Factory Calls

An application can construct a provider and pass it directly to a JCA factory.
Because the application already possesses this object, its construction and ordinary Java method
calls are not JDK-managed provider acquisition as defined in section 1.2.
The provider class must be registered for reflection, but it does not need to be JDK-constructible.
The factory can use its registered services without the provider being in the run-time provider
list.

If the provider is unregistered and the operation requires JCE verification, the operation follows
the missing-registration behavior in section 4.3.

### 5.2 Programmatic Provider-List Changes

An application can call `Security.addProvider(Provider)` or
`Security.insertProviderAt(Provider, int)` with a constructed provider.
Insertion does not register an unregistered provider or any of its services: the insertion can
still make the supplied provider object retrievable from the run-time provider list according to
the standard `Security` API behavior, but JCA factory calls cannot use its unregistered services.
This exception applies only to the same application-supplied object; it does not allow the JDK to
create, discover, or substitute an unregistered provider.
After successful insertion of a registered provider, provider-name lookups and name-based factory
calls can select its registered services.
Removal with `Security.removeProvider(String)` makes the provider unavailable to subsequent
name-based lookups without affecting provider objects already held by the application.

Adding a provider does not itself require JCE verification; the first subsequent operation that
requires JCE verification follows section 5.3.
Provider position, duplicate-name handling, insertion return values, and removal otherwise follow
the standard `Security` API behavior.

### 5.3 JCE Verification

Before a registered provider supplies a service for which the JDK requires Java Cryptography
Extension (JCE) verification, Native Image must have established a verification outcome for that
provider at build time.
Registration is necessary for such an operation, but registration is not successful verification.

For a registered application-supplied provider class that is not one of the build-time configured
providers, Native Image establishes the successful verification outcome from the class
registration without constructing a provider instance.
This permits JCE use of an existing application-supplied instance while avoiding an unsupported
attempt to reconstruct it.

Native Image must preserve the build-time verification outcome and apply it to run-time instances
of that provider class, including an instance whose provider name differs from the name observed
at build time.
A failed verification outcome must prevent every run-time JCE operation that requires verification
from using the provider.
The operation must expose that failure through the standard JCE behavior of the invoked factory
overload; it must not return an engine backed by the provider or continue with a partially usable
provider.

An operation that requires JCE verification of an unregistered provider follows section 4.3.
Provider services that do not require JCE verification remain subject to the registration and
availability rules in sections 1 and 2.

## 6. Tracing Metadata

### 6.1 Provider and Service Coverage

Metadata collected by the Tracing Agent or native metadata tracing from a successful provider
lookup must be sufficient for a subsequently built native executable to perform the same lookup
and use the same provider services without additional provider metadata.
This includes provider enumeration and filtering through the `Security` APIs when the JDK loaded
and cached a returned provider before the traced operation.
Provider-list mutation through `Security.addProvider`, `Security.insertProviderAt`, or
`Security.removeProvider` is not provider enumeration or lookup. Tracing such a mutation must
register a supplied provider that the operation observes, but it must not register unrelated
configured providers loaded or inspected by the JDK while maintaining the provider list.
For a JDK-managed provider, the collected metadata must retain a supported construction path:
declared nullary constructor access or access to the static `provider()` method.
For an application-supplied provider, tracing must register the provider type without inventing a
constructor access and must independently retain each service implementation exercised by the
traced factory calls. This includes a service implementation named only by
`Provider.Service.getClassName()`: the caller-filtered trace must retain the construction access
performed inside `Provider.Service.newInstance` and attribute it to the application operation that
selected the service.

Tracing a missing provider registration must use the ordinary reflection metadata format and
diagnostics; it must not introduce a security-provider-specific metadata category or error.

### 6.2 Observational Transparency

Tracing must observe the application's provider lookup, service lookup, and service instantiation
without invoking any of those operations an additional time.
It must not initialize or cache a provider, service, implementation class, or resource while
recursive tracing is suppressed.
Metadata must include the nested reflection and resource accesses performed by the application's
actual operation.

## 7. Transition to the Future Defaults

Sections 1 through 6 specify the planned default behavior.
The following options select its two independent parts while the earlier behaviors remain
available for compatibility.
Every combination of provider-inclusion policy and provider-list initialization must preserve the
applicable behavior below; selecting one part must not implicitly select or disable the other.

### 7.1 Run-Time Provider-List Initialization

With `--future-defaults=run-time-initialize-security-providers`, Native Image constructs the
run-time provider list from the configured security properties using only registered providers.
An unregistered provider is not added to the list, and its services remain unavailable.
Filtering unregistered providers preserves the ordering and lookup results specified in sections
1.3, 3.2, and 4.

### 7.2 Provider Service Descriptors

A class-path _META-INF/services/java.security.Provider_ descriptor does not register the named
provider for reflection.
If the provider is unregistered, service loading must not return a provider instance.
Iterating to its descriptor can report the standard `ServiceConfigurationError` or
missing-reflection error, and the provider's services remain unavailable.

### 7.3 Earlier Service-Driven Inclusion Behavior

Without `--future-defaults=explicit-security-provider-registration`, reachability of a JCA service
factory or JDK security-service facade can include services of the corresponding service type even
when their provider classes have no reflection metadata.
This compatibility behavior applies to supported facades such as the Generic Security Services API
(GSS-API).
For example, reachability of any `Signature.getInstance` overload can cause signature services and
their providers to be included.
The rule is based on reachability of the service factory and service type, not on build-time
evaluation of the run-time algorithm argument.

With `--future-defaults=explicit-security-provider-registration`, this compatibility behavior is
disabled.
A factory call for an algorithm supplied only by an unregistered provider follows section 4.2, and
a lookup that reflectively loads the provider follows section 4.3.
The platform-owned `SecureRandom` registration signal in section 2.4 remains enabled.
It registers complete providers for this commonly used JDK facility rather than inferring partial
provider support from a general service factory.

### 7.4 Earlier Build-Time Initialization Behavior

`--future-defaults=run-time-initialize-security-providers` replaces the earlier behavior in which
Native Image initializes the configured provider list at build time; during the transition,
omitting this future default retains that earlier initialization behavior.
When explicit provider registration is enabled without run-time provider initialization, Native
Image must filter the build-time provider list before storing it in the executable so that it does
not expose an unregistered JDK-managed provider.
Provider and service availability is still determined at build time according to either the
explicit registration rules in sections 1 and 2 or the compatibility rule in section 7.3.
