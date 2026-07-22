# FS-security-providers: JCA Security Provider Registration and Run-Time Access

This specification defines the Native Image behavior for Java Cryptography Architecture
(JCA) security providers.
Under this behavior, the JDK can acquire or select a provider for JCA at run time only if the
provider class was *registered for reflection* at build time.
With `--exact-reachability-metadata`, attempted reflective acquisition of an unregistered provider
reports an error that identifies the missing provider type.
Native Image constructs the provider list at run time from the configured security properties and
the registered provider classes.

Reflection registration determines which provider classes and services the native executable
contains.
It does not by itself add a provider to the run-time provider list.
Section 7 defines the future-default options that select this behavior during the transition from
service-driven provider inclusion and build-time provider-list initialization.

## 1. Provider Reflection Registration

### 1.1 Registered Providers and Services

A provider class is **registered for reflection** for the purposes of this specification when all
the following conditions hold:

- the class is a concrete `Provider` subtype;
- Native Image can construct it through either a declared nullary constructor or a declared public
  static nullary `provider()` method whose return type is assignable to `Provider`; and
- reflection metadata registers access to at least one of the provider type, its declared nullary
  constructor, or its qualifying `provider()` method.

A **registered provider** is a provider whose class satisfies this definition.
A **registered service** is a valid service declared by a registered provider whose implementation
class and required reflective construction metadata Native Image can retain in the executable.

Registration is a build-time property.
Constructing a provider object at run time does not register the provider or add omitted services
to the executable.

### 1.2 JDK-Managed Providers and Acquisition

A **JDK-managed provider** is a provider instance that the JDK creates or discovers instead of an
instance that application code constructs and supplies to the JDK.
JDK-managed acquisition paths include loading providers from the configured security properties,
discovering them through a service-provider descriptor, selecting them through a JCA factory or
security-service facade, and creating them through a JDK default or fallback path.

The public JDK API surface covered by this definition includes:

- provider lookup, enumeration, filtering, and algorithm discovery through
  `Provider Security.getProvider(String)`, `Provider[] Security.getProviders()`,
  `Provider[] Security.getProviders(String)`,
  `Provider[] Security.getProviders(Map<String, String>)`, and
  `Set<String> Security.getAlgorithms(String)`;
- provider-service lookup and instantiation through
  `Provider.Service Provider.getService(String, String)`,
  `Set<Provider.Service> Provider.getServices()`, and
  `Object Provider.Service.newInstance(Object)`;
- JCA factory selection, for example through `Signature Signature.getInstance(String)`,
  `Signature Signature.getInstance(String, String)`, and
  `Signature Signature.getInstance(String, Provider)`, together with provider exposure through
  `Provider Signature.getProvider()`;
- security-service facades, including `GSSManager GSSManager.getInstance()`,
  `SaslClient Sasl.createSaslClient(String[], String, String, String, Map<String, ?>, CallbackHandler)`,
  and
  `SaslServer Sasl.createSaslServer(String, String, String, Map<String, ?>, CallbackHandler)`; and
- service discovery through `ServiceLoader<S> ServiceLoader.load(Class<S>)` and default
  construction through `SecureRandom()`.

This list identifies the principal public entry points but does not limit the acquisition rule to
those methods.
Other JCA engine factory overloads, provider-exposing engine methods, and internal paths that
implement these APIs are subject to the same rule.

The JDK **acquires** a provider when a JDK API or implementation path does any of the following:

- returns the provider or one of its `Provider.Service` objects to application code;
- selects the provider to back a JCA engine or security-service facade; or
- reports the provider or one of its algorithms as available.

This definition covers public APIs and the internal JDK paths that implement them.
It does not treat an application constructing its own provider object through a statically resolved
constructor as JDK acquisition.
Reflective provider loading and construction remain subject to reflection registration and section
4.3.
Section 5 specifies the narrower operations allowed for such application-supplied objects.

### 1.3 Run-Time Provider List

The **run-time provider list** contains registered providers selected from the configured security
properties and reflects subsequent changes made through the standard `Security` API.
Filtering unregistered providers must preserve the relative order of the remaining configured
providers.
Registering a provider for reflection does not insert a provider that is absent from the configured
list.

### 1.4 Service Availability

A service is **available** when it is registered and the corresponding factory call can select its
provider.
A name-based call can select only a provider in the run-time provider list.
A provider-object call can select a supplied registered provider without adding it to that list.
Algorithm aliases and provider selection otherwise follow the standard JCA API behavior.

## 2. Registration Semantics

### 2.1 Qualifying Reflection Metadata

Type access, declared nullary constructor access, and qualifying `provider()` method access are
alternative registration signals.
Registering any one of them is sufficient if the provider class meets all construction requirements
in section 1.1.
Registering a signal does not relax those construction requirements.

### 2.2 Provider Construction

A declared nullary constructor does not have to be public.
When both supported construction paths exist, Native Image uses the declared nullary constructor.

A provider registered through `provider()` must expose the same services through `Security`
lookups and JCA factory calls as it exposed when Native Image inspected it at build time.

### 2.3 Registration Effects

Registering a provider includes every valid service declared by the provider whose implementation
class Native Image can resolve.
It must also retain the metadata required to construct those service implementations.
Provider registration does not change the configured provider order or make an unconfigured
provider visible by name.

## 3. Permitted Run-Time Access

### 3.1 JDK-Managed Acquisition

Every JDK-managed provider acquired at run time must be a registered provider.
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
For example, a default `SecureRandom` construction must not expose a fallback SUN provider unless
the SUN provider class is registered.

### 3.2 Provider List Lookups

`Provider Security.getProvider(String)` returns a registered provider when that provider is in the
run-time provider list.
`Provider[] Security.getProviders()` contains the same provider and preserves the ordering
described in section 1.3.
`Provider[] Security.getProviders(String)` and
`Provider[] Security.getProviders(Map<String, String>)` can return only registered providers from
that list.
`Set<String> Security.getAlgorithms(String)` can report an algorithm only when at least one
registered provider in that list declares it.

If a provider is not in the run-time provider list and the lookup does not attempt to load it
reflectively, `Provider Security.getProvider(String)` returns `null`.

### 3.3 JCA Factories and Security-Service Facades

A name-based JCA factory call can use the registered services of a provider in the run-time provider
list.
A factory overload that accepts a provider object can use the registered services of that provider
without requiring it to be in the list.

A factory call can use only registered service implementations.
It does not evaluate a run-time algorithm argument at build time or make additional providers or
services available.

The same registration requirement applies when a facade or a JDK implementation path selects a
provider service without calling a public JCA factory.

### 3.4 Programmatic Access

An application can construct a registered provider and pass it directly to a JCA factory.
It can also add the provider to the run-time provider list through
`int Security.addProvider(Provider)` or `int Security.insertProviderAt(Provider, int)`.
Section 5 specifies these operations in detail.

## 4. Prohibited Run-Time Access and Errors

### 4.1 Unregistered Providers

An unregistered JDK-managed provider must not be returned, selected, or reported as available at
run time.
The JDK must not expose it partially by returning the provider while omitting some services,
advertising its algorithms without allowing their use, returning a `Provider.Service` without a
usable implementation, or producing an engine or facade backed by that provider.

Failure must occur at the acquisition boundary before application code receives a provider,
service, engine, or facade that represents the unregistered provider as available.
When sections 4.2 and 4.3 do not specify a standard result or missing-registration diagnostic, this
specification requires the operation to fail before exposure but does not prescribe the exception
type.
Reachability of a JCA service factory or JDK security-service facade must not make that provider or
its services available.
Neither a service-provider descriptor nor a JDK default or fallback implementation can register the
provider after the executable has been built.

Application-supplied provider objects follow section 5 and do not relax these requirements for
JDK-managed providers.

### 4.2 Standard Unavailable Results

JCA factory calls retain their standard distinction between a missing provider and a missing
algorithm:

- a factory overload given the name of a provider that is not in the run-time provider list reports
  `NoSuchProviderException`;
- a factory call that can select a provider but cannot find a registered implementation for the
  requested algorithm reports `NoSuchAlgorithmException`; and
- a factory overload given a provider object follows section 5.1 instead of requiring that provider
  to be in the run-time provider list.

These results apply when no missing reflection registration is encountered first.

### 4.3 Missing Reflection Registration

When exact reachability metadata checking is enabled, an operation that reflectively loads an
unregistered provider reports `MissingReflectionRegistrationError` for the provider type.
Native Image must not replace that error with `NoSuchProviderException`,
`NoSuchAlgorithmException`, or another security-provider-specific exception.

This requirement applies both to loading a provider from the configured provider list and to Java
Cryptography Extension (JCE) verification of a programmatically supplied provider.
Without exact reachability metadata checking, this specification does not guarantee a particular
missing-registration diagnostic.

## 5. Programmatically Supplied Providers

### 5.1 Provider-Object Factory Calls

An application can construct a provider and pass it directly to a JCA factory.
Because the application already possesses this object, its construction and ordinary Java method
calls are not JDK-managed provider acquisition as defined in section 1.2.
Direct construction does not waive the registration requirements in section 1.1.
If the provider is registered, the factory can use its registered services without the provider
being in the run-time provider list.

If the provider is unregistered and the operation requires JCE verification, the operation follows
the missing-registration behavior in section 4.3.

### 5.2 Programmatic Provider-List Changes

An application can call `int Security.addProvider(Provider)` or
`int Security.insertProviderAt(Provider, int)` with a constructed provider.
Insertion does not register an unregistered provider or any of its services.
The insertion can still make the supplied provider object retrievable from the run-time provider
list according to the standard `Security` API behavior, but JCA factory calls cannot use its
unregistered services.
This exception applies only to the same application-supplied object; it does not allow the JDK to
create, discover, or substitute an unregistered provider.
After successful insertion of a registered provider, provider-name lookups and name-based factory
calls can select its registered services.
Removal with `void Security.removeProvider(String)` makes the provider unavailable to subsequent
name-based lookups without affecting provider objects already held by the application.

Adding a provider does not itself require JCE verification.
The first subsequent operation that requires JCE verification follows section 5.3.
Provider position, duplicate-name handling, insertion return values, and removal otherwise follow
the standard `Security` API behavior.

### 5.3 JCE Verification

Before a registered provider supplies a service for which the JDK requires Java Cryptography
Extension (JCE) verification, Native Image must have established a verification outcome for that
provider at build time.
Registration is necessary for such an operation, but registration is not successful verification.

Native Image must preserve the build-time verification outcome and apply it to run-time instances
of that provider class.
A run-time instance must receive the same outcome when its provider name differs from the name
observed at build time.
A failed verification outcome must prevent every run-time JCE operation that requires verification
from using the provider.
The operation must expose that failure through the standard JCE behavior of the invoked factory
overload; it must not return an engine backed by the provider or continue with a partially usable
provider.

An operation that requires JCE verification of an unregistered provider follows section 4.3.
Provider services that do not require JCE verification remain subject to the registration and
availability rules in sections 1 and 2.

## 6. Tracing Metadata

Metadata collected by the Tracing Agent or native metadata tracing from a successful provider
lookup must be sufficient for a subsequently built native executable to perform the same lookup
and use the same provider services without additional provider metadata.
The collected metadata must retain a supported construction path: declared nullary constructor
access or access to the static `provider()` method.

Tracing a missing provider registration must use the ordinary reflection metadata format and
diagnostics.
It must not introduce a security-provider-specific metadata category or error.

## 7. Transition to the Future Defaults

Sections 1 through 6 specify the planned default behavior.
The following options select its two independent parts while the earlier behaviors remain available
for compatibility.

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

For example, reachability of `Signature Signature.getInstance(String)`,
`Signature Signature.getInstance(String, String)`, or
`Signature Signature.getInstance(String, Provider)` can cause signature services and their
providers to be included.
This rule is based on reachability of the service factory and service type, not on build-time
evaluation of the run-time algorithm argument.

With `--future-defaults=explicit-security-provider-registration`, this compatibility behavior is
disabled.
A factory call for an algorithm supplied only by an unregistered provider follows section 4.2, and
a lookup that reflectively loads the provider follows section 4.3.

### 7.4 Earlier Build-Time Initialization Behavior

`--future-defaults=run-time-initialize-security-providers` replaces the earlier behavior in which
Native Image initializes the configured provider list at build time.
During the transition, omitting this future default retains that earlier initialization behavior.
When explicit provider registration is enabled without run-time provider initialization, Native
Image must filter the build-time provider list before storing it in the executable so that it does
not expose an unregistered JDK-managed provider.
Provider and service availability is still determined at build time according to either the
explicit registration rules in sections 1 and 2 or the compatibility rule in section 7.3.
