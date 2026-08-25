# FS-002-security-providers: JCA Security Provider Registration and Run-Time Access

The JDK defines its security-provider list through the `security.provider.<n>` security properties,
and HotSpot can load every configured provider on demand.
Native Image cannot simply treat every configured entry as an inclusion signal: a closed-world
executable would then retain every provider and its complete service catalog, even when the
application never uses them.
To avoid that executable-size cost, Native Image uses two independent inputs for a JDK-managed
provider: a `security.provider.<n>` property determines its position in the
[run-time provider list](#13-run-time-provider-list), and the provider implementation class or a
supported construction path must be [registered for reflection](#11-registered-providers-and-services)
at build time to include the provider and its services.
Only providers that satisfy both conditions are constructed and exposed through JDK-managed lookup
at run time; once included, they behave as they do on HotSpot.
`Security.addProvider(Provider)` and `Security.insertProviderAt(Provider, int)` do not require
reflection metadata for the supplied provider class because both APIs accept an existing provider
instance.
Native Image still establishes the provider's Java Cryptography Extension (JCE) verification
outcome at build time and retains metadata for service implementations that JCA constructs
reflectively.

For example, assume Bouncy Castle is on the class path and the effective _java.security_
configuration contains:

```properties
security.provider.13=org.bouncycastle.jce.provider.BouncyCastleProvider
```

Register the provider for reflection in _reachability-metadata.json_:

```json
{
  "reflection": [
    {
      "type": "org.bouncycastle.jce.provider.BouncyCastleProvider"
    }
  ]
}
```

Only with both entries, `Security.getProvider("BC")` returns the Bouncy Castle provider.
Without the reflection entry, the same lookup throws an actionable `java.lang.SecurityException`
that identifies `org.bouncycastle.jce.provider.BouncyCastleProvider` as the unregistered provider
class and directs the user to reflection metadata.
Without the `security.provider.13` property, the lookup returns `null` because reflection metadata
does not install the provider.
The application can install it explicitly with the standard JDK API:

```java
Security.addProvider(new BouncyCastleProvider());
```

The Tracing Agent collects provider construction metadata when it observes JDK-managed acquisition
and service metadata when it observes a JCA factory use ([§6](#6-tracing-metadata)).
As on HotSpot, a `security.provider.<n>` property installs the provider during provider-list
initialization, and an application can instead install an existing instance with
`Security.addProvider` or `Security.insertProviderAt`.

[§7](#7-transition-to-the-future-defaults) defines the future-default options that select this
behavior during the transition from service-driven provider inclusion and build-time provider-list
initialization.
The implementation is described by [§AR-002-security-providers](../architecture/security-providers.md).

## 1. Notation

This specification uses the [common notation](README.md#notation) and the default package
`java.security`.

### 1.1 Registered Providers and Services

A *provider implementation class* is a concrete `Provider` subtype.
A *service provider class* is the type through which `ServiceLoader` constructs a provider; it can
differ from the provider implementation class it produces.
A **supported construction path** is either a public no-argument constructor of a public, concrete
`Provider` subtype or, on a public service provider class in an explicit module, a public static
no-argument `provider()` method returning a `Provider` subtype.
A provider implementation class is **JDK-constructible** if such a path produces it, and
**registered** if ordinary reflection metadata covers its type or such a path.
Registration in this specification governs JDK-managed acquisition, not an existing
application-supplied instance.

### 1.2 JDK-Managed Providers and Acquisition

A **JDK-managed provider** is created or discovered by the JDK; an **application-supplied provider**
is an existing instance passed by application code.
**JDK-managed acquisition** is any JDK path that returns or reports a provider or service, or selects
one to back an engine or security facade, including configuration, `ServiceLoader`, factories, and
default or fallback selection.
Application reflection used to create a supplied instance remains ordinary reflective access.

> For example, a successful `Security.getProvider("BC")` returns a provider from the configured
> provider list, and `Cipher.getInstance("AES")` selects a provider to back an engine.
> Both are JDK-managed acquisition.
> In contrast, if application code calls
> `Class.forName(providerClass).getConstructor().newInstance()` and passes the resulting provider to
> `Security.addProvider(Provider)`, the instance is application-supplied.
> Its reflective construction requires ordinary reflection metadata, but it is not JDK-managed
> acquisition.

### 1.3 Run-Time Provider List

The **run-time provider list** is the ordered list consulted by `Security` lookups and name-based JCA
factories.
It contains registered configured providers, in configured relative order, and existing instances
subsequently inserted through the `Security` API.
The value of a `security.provider.<n>` property consists of a provider class or provider name and an
optional configuration argument separated from it by whitespace.
For an entry with a non-empty argument, the JDK constructs the provider through its selected path and
then calls `Provider.configure(argument)`; the provider returned by `configure` is the instance for
that entry, even when it has a different provider name or implementation class from the
pre-configuration instance.
When a configured token is a provider name rather than a class name, Native Image tries eligible
_META-INF/services/java.security.Provider_ declarations in descriptor order and uses the first
successfully constructed provider whose pre-configuration name matches the token.
An ambiguous provider name never falls back to treating that token as a binary class name.
Each configured entry is independent, so two entries for the same provider class can produce
distinct provider instances, names, and service catalogs while retaining their configured relative
order.
Provider registration does not install a provider, and insertion does not register one.
Provider-list initialization is distinct from class initialization (JLS §12.4).

### 1.4 Service Availability

A **retained service** has its implementation and required construction metadata in the executable.
It is **available** to a call when that call can also select its provider.
Name-based calls select from the run-time provider list; provider-object calls select the supplied
instance without installing it.

## 2. Registration Semantics

### 2.1 Qualifying Reflection Metadata

Ordinary reflection metadata registers a provider implementation class when it covers either that
class's type or one of its supported construction paths.
For a configured, JDK-constructible provider, either form makes `Security.getProvider(String)` return
the provider and makes all its retained algorithms usable through calls such as
`Signature.getInstance(String, String)`.
Metadata for only a service implementation class does neither.
Conditional metadata has the behavior in [§2.5](#25-conditional-registration-signals).
Using ordinary reflection metadata instead of a provider-specific flag follows
§FD-001-hotspot-compatible-semantics-by-default.2.
Deriving registration from explicit metadata instead of incidental JCA reachability follows
§FD-002-reachability-independent-runtime-semantics.2.

### 2.2 Provider Construction

Native Image constructs a JDK-managed provider through the same supported path as `ServiceLoader`:
a public no-argument constructor, or the public static no-argument `provider()` method of a service
provider class in a named module.
When both apply, `provider()` takes precedence (JLS §7.7.4).
For a configured entry with an argument, construction includes the subsequent
`Provider.configure(argument)` call and uses its return value.
A private or parameterized constructor and a class-path `provider()` method do not qualify.
A service descriptor is passive inventory: discovering it does not initialize or instantiate its
declaration class.
Native Image invokes a descriptor construction path during image generation only after qualifying
metadata activates that path or its returned provider implementation.
A registered provider without a qualifying path is not returned by `Security.getProvider(String)`;
application code can still supply an existing instance under [§5](#5-programmatically-supplied-providers).
If invoking a selected path during the image build fails or returns the wrong provider class, the
build fails with a diagnostic naming the provider and construction failure.

### 2.3 Registration Effects

Registering a JDK-constructible provider retains its selected construction path, every valid and
resolvable service in its catalog, the service construction metadata, and its JCE verification
outcome.
Thus, registering a provider that declares algorithms `A` and `B` makes both appear through
`Provider.getServices()` and makes both usable even if application reachability mentions only `A`.
If configured instances of the same provider class declare different services, Native Image retains
the services of every instance.
A registered class without a supported construction path retains no provider catalog.
Registration alone does not install the provider: without a matching `security.provider.<n>` entry,
`Security.getProvider(provider.getName())` returns `null` until the application inserts an instance.

### 2.4 SecureRandom Providers

Reachability of a `SecureRandom` constructor or `SecureRandom.getInstance` call registers the
configured providers that supply `SecureRandom`, without application reflection metadata for those
providers.
The provider is then visible through `Security` if its `security.provider.<n>` entry installs it, and
its complete catalog has the effects of [§2.3](#23-registration-effects).
Native Image's internal random source triggers this rule only in an image that includes runtime
compilation, which consumes that source.

### 2.5 Conditional Registration Signals

A condition on provider metadata gates the provider, its construction path, services, and
verification outcome as one unit.
Before the condition becomes active, `Security.getProviders()` omits the configured provider and a
name-based factory cannot select it.
After activation, `Security.getProvider(String)` returns it at its configured position and its
retained services become usable.
The result is the same whether `Security.getProviders()` initialized the list before or after
activation, and an active condition never becomes inactive.

## 3. Permitted Run-Time Access

### 3.1 JDK-Managed Acquisition

A configured, registered, JDK-constructible provider can be returned by
`Security.getProvider(String)` and `ServiceLoader.load(Provider.class)`, selected by calls such as
`Signature.getInstance(String)` or `Signature.getInstance(String, String)`, and used by JDK default
or fallback selection.
The engine's `getProvider()` returns the selected provider.
`SecureRandom` additionally follows [§2.4](#24-securerandom-providers).

#### 3.1.1 GSS Mechanism Discovery

`GSSManager.getInstance().getMechs()` reports a mechanism when an available provider supplies its
retained GSS mechanism service.
It does not report a mechanism supplied only by an unavailable provider.

#### 3.1.2 SASL Client Creation

`Sasl.createSaslClient(mechanisms, authorizationId, protocol, serverName, properties, callbackHandler)`
can select only a retained SASL client-factory service from an available provider.
It returns `null` when none of the requested mechanisms has such a service.

#### 3.1.3 SASL Server Creation

`Sasl.createSaslServer(mechanism, protocol, serverName, properties, callbackHandler)` can select only
a retained SASL server-factory service from an available provider.
It returns `null` when the requested mechanism has no such service.

### 3.2 Provider List Lookups

`Security.getProvider(String)` and the `Security.getProviders` overloads operate on the run-time
provider list from [§1.3](#13-run-time-provider-list).
After `Security.addProvider(provider)` succeeds, `Security.getProvider(provider.getName())` returns
that same object even when its class has no reflection metadata.
`Security.getProviders(String)`, `Security.getProviders(Map)`, and
`Security.getAlgorithms(String)` report only providers and retained services present in that list.

### 3.3 JCA Factories and Security-Service Facades

`Signature.getInstance(algorithm)` selects a retained service from the run-time provider list, and
`Signature.getInstance(algorithm, providerName)` restricts selection to the named list entry.
`Signature.getInstance(algorithm, provider)` instead uses the supplied object without installing it
or requiring provider-class reflection metadata.
Equivalent overloads on other JCA factories follow the same rules.
Changing an algorithm string at run time can select only services already retained in the
executable; it cannot add another provider or service.

### 3.4 Programmatic Access

An application can use an existing provider immediately with a provider-object factory.
It can make the same object available to name-based lookup by calling
`Security.addProvider(Provider)` or `Security.insertProviderAt(Provider, int)`, and remove it with
`Security.removeProvider(String)`.
[§5](#5-programmatically-supplied-providers) gives the metadata and verification rules.

## 4. Prohibited Run-Time Access and Errors

### 4.1 Unregistered Providers

A configured but unregistered provider is omitted from `Security.getProviders()`, provider filters,
and `Security.getAlgorithms(String)`.
`Security.getProvider(String)`, JCA factories, `ServiceLoader`, and JDK default or fallback paths
must not return that provider, one of its services, or an engine backed by it.
Making a factory call reachable, adding a _META-INF/services/java.security.Provider_ descriptor, or
passing a different algorithm string at run time cannot change that result after the executable is
built.

### 4.2 Standard Unavailable Results

`Signature.getInstance(algorithm, missingProviderName)` throws `NoSuchProviderException` when the
name is absent from the run-time provider list.
`Signature.getInstance(missingAlgorithm)` throws `NoSuchAlgorithmException` when no list entry has a
retained implementation, and `Signature.getInstance(missingAlgorithm, provider)` throws the same
exception when the supplied object lacks one.
A provider-object overload does not throw merely because the provider is absent from the list.

### 4.3 Missing Reflection Registration

When `Security.getProvider(String)` or a JCA factory causes the JDK to reflectively load an
unregistered configured provider, acquisition fails before returning the provider.
The ordinary reflection diagnostic names the provider implementation class in binary form and tells
the user to register its type or supported construction path in _reachability-metadata.json_.
There is no provider-specific metadata format or provider-specific missing-metadata error type.
The diagnostic lookup is side-effect-free: it does not construct or cache the omitted provider and
does not change subsequent enumeration, duplicate detection, insertion, or factory behavior.
An implementation retained only for provider-object use under [§5](#5-programmatically-supplied-providers)
is not diagnosed by a name lookup: until the application inserts an instance,
`Security.getProvider(String)` returns `null`.
This exception does not suppress the diagnostic for a configured built-in provider merely because
application code also makes provider-object use of its implementation class reachable.
The diagnostic resolver uses only recorded configured entries and built-in provider aliases; it
does not interpret an arbitrary dotted provider name as an implementation class name.
The same diagnostic applies in exact-metadata and compatibility reporting modes.
If reflective construction is attempted, `MissingReflectionRegistrationError` remains the top-level
failure and is not wrapped as a provider-configuration error.

## 5. Programmatically Supplied Providers

### 5.1 Provider-Object Factory Calls

After application code constructs a provider, a call such as
`Signature.getInstance(algorithm, provider)` does not require reflection metadata for the provider
class and does not add the provider to the run-time list.
The call succeeds when the service implementation and its required construction metadata are
retained and [§5.3](#53-jce-verification) permits use.
A missing implementation produces `NoSuchAlgorithmException`; a JCE verification failure follows
[§5.3](#53-jce-verification).

### 5.2 Programmatic Provider-List Changes

`Security.addProvider(Provider)` and `Security.insertProviderAt(Provider, int)` do not require
reflection metadata for the supplied provider class.
On success they insert that exact object, after which `Security.getProvider(provider.getName())`
returns it and a name-based factory can select its retained services.
They return `-1` when the list already contains a provider with the same name, and otherwise use the
positions and ordering defined by the standard `Security` API.
Duplicate detection and insertion positions count only providers visible in the run-time list.
Inactive conditional or unregistered configurations retain their relative order but neither block an
application-supplied provider with the same name nor shift its visible insertion position.
`Security.removeProvider(provider.getName())` removes the list entry.
Insertion retains no additional service implementation, so a missing service still produces
`NoSuchAlgorithmException`.

### 5.3 JCE Verification

`Cipher`, `Mac`, and other factories that require JCE provider verification may construct a service
only when Native Image recorded successful build-time verification for that provider class.
A failed outcome makes the factory throw `SecurityException`; a missing outcome produces the
missing-registration failure from [§4.3](#43-missing-reflection-registration).
In either case, the factory fails before it constructs the service implementation.
This rule applies equally to a JDK-managed provider and an application-supplied provider; the latter
still requires no provider-class reflection metadata.
The implementation-specific recognition rule is §AR-002-security-providers.3.

## 6. Tracing Metadata

### 6.1 Provider and Service Coverage

Tracing a successful `Security.getProvider(String)`, provider enumeration, or name-based factory
selection records ordinary reflection metadata for each JDK-managed provider returned or selected.
Tracing a factory call or `Provider.Service.newInstance(Object)` also records the implementation and
constructor metadata needed to repeat the observed service creation.
The factory selection itself records both kinds of metadata; this does not depend on a preceding
provider lookup, enumeration, or filter operation having exposed the same provider.
Rebuilding with only the generated metadata must reproduce the observed factory selection when
explicit provider registration is enabled.
Tracing `Security.addProvider`, `Security.insertProviderAt`, or `Security.removeProvider` does not
record provider-class metadata merely because the call receives an existing object.
Only a successfully inserted provider object retains application-supplied provenance for later
name-based selection; a failed insertion does not.
A provider-object factory call has transient application-supplied provenance for that call only and
does not change how a later name-based selection of the same object is traced.
Provider provenance is per object, does not keep the object alive, and has bounded lookup cost.
For a cached service implementation, tracing records the constructor the JDK actually cached,
including a no-argument constructor selected after a parameterized-constructor probe failed.
Security operations reached through JDK runtime modules are attributed to the first non-JDK caller,
including calls mediated by non-security JDK classes or platform provider modules.
An application `Class.forName`, reflective constructor call, or reflective service construction is
traced by the ordinary reflection rules.

### 6.2 Observational Transparency

With tracing enabled, a provider lookup, factory call, or provider-list mutation has the same return
value, exception, provider order, and application-visible side effects as the same call without
tracing.
The tracer does not repeat the operation or initialize an unselected provider or service.
Reflection and resource access performed inside the application's actual call is still recorded.

## 7. Transition to the Future Defaults

[§2](#2-registration-semantics) through [§6](#6-tracing-metadata) specify the planned
default behavior.
The following build-time options select it while earlier behaviors remain available.
Explicit provider registration implies run-time provider-list initialization.

### 7.1 Run-Time Provider-List Initialization

With `--future-defaults=run-time-initialize-security-providers`, Native Image initializes the list
when `Security` first needs it at run time.
`Security.getProviders()` contains only registered entries from the build-time
`security.provider.<n>` configuration and preserves their relative order.
`Security.getProvider(String)` constructs an entry through its retained path and fails with an
actionable diagnostic if that path fails or returns the wrong implementation.
Every configured construction path uses the JDK recursion guard and retry counter, including a path
resolved from retained descriptor inventory.
Recursive loading returns no provider for that attempt, and repeated failures stop at the JDK retry
limit rather than retrying indefinitely.
`--future-defaults=explicit-security-provider-registration` enables this behavior implicitly.

### 7.2 Provider Service Descriptors

A class-path _META-INF/services/java.security.Provider_ descriptor does not register its provider
for reflection.
Without provider reflection metadata, `ServiceLoader.load(Provider.class)` does not return the
described provider.
With that metadata, it can return the provider only when `ServiceLoader` access to `Provider` is
reachable and therefore retains the descriptor.
Descriptor discovery preserves declaration order without instantiating declarations that have no
active provider-registration signal.
Only explicit-registration mode registers a negative resource query for an absent provider
descriptor; both legacy modes preserve the ordinary resource-query behavior. The negative query
permits an absent descriptor to return no resource, but does not suppress a descriptor retained by
an independent reachable use. In particular, run-time provider initialization can itself retain
the descriptor in a legacy mode, so an opaque query may find it without explicit resource metadata.

### 7.3 Earlier Service-Driven Inclusion Behavior

Without `--future-defaults=explicit-security-provider-registration`, reachability of a supported JCA
factory or security facade retains configured services of the corresponding type even when their
provider classes lack reflection metadata.
For example, a reachable `Signature.getInstance(runtimeAlgorithm)` can make configured `Signature`
services available, but the run-time value cannot retain another service type.
Likewise, reachability of `GSSManager.getInstance().getMechs()`, `Sasl.createSaslClient(...)`, or
`Sasl.createSaslServer(...)` can retain the configured GSS mechanism, SASL client-factory, or SASL
server-factory services, respectively.
With `--future-defaults=explicit-security-provider-registration`, the same call can select only
providers registered under [§2.1](#21-qualifying-reflection-metadata), except for the `SecureRandom`
rule in [§2.4](#24-securerandom-providers).
Neither mode changes the programmatic rules in [§5](#5-programmatically-supplied-providers).

### 7.4 Earlier Build-Time Initialization Behavior

Without `--future-defaults=run-time-initialize-security-providers`, Native Image retains the earlier
behavior that initializes the configured provider list during the image build.
Provider-constructor side effects therefore occur during the build, and the executable starts with
the resulting provider-list state.

### 7.5 Deprecated Additional-Provider Option

`-H:AdditionalSecurityProviders=<provider-class>` remains accepted with a deprecation warning during
the transition.
In explicit registration mode, each named provider class is an unconditional, complete
provider-registration signal with the same provider and catalog effects as unconditional qualifying
reflection metadata.
Without explicit registration, the option preserves the earlier option-driven inclusion behavior
without requiring another registration signal or a future-default option.
In legacy run-time provider-list initialization mode, naming a provider does not by itself construct
the provider during the image build.
The JDK constructs a configured provider through its retained path at run time, and only services
retained through legacy service-driven inclusion or independent metadata are available.
In legacy build-time provider-list initialization mode, [§7.4](#74-earlier-build-time-initialization-behavior)
still permits configured-provider construction during the image build.
The option does not install an otherwise unconfigured provider and does not change provider order.
Replacing it with ordinary reflection metadata requires explicit provider registration until that
behavior becomes the default.

## 8. Requirements

The requirements in this chapter define the acceptance criteria for this specification.
Each states a required property and its domain.
[§8.9](#89-architecture-obligations) contains universal implementation obligations.

### 8.1 Construction Parity

**Requirement.** For every provider the JDK can construct on HotSpot through a supported path from
[§1.1](#11-registered-providers-and-services), ordinary reflection metadata must let a native
executable acquire the same provider through the same JDK API and use its services.
Native Image must require neither a provider-specific option nor an application source change.
This is the provider-specific form of §FD-001-hotspot-compatible-semantics-by-default.2.
The transition options in [§7](#7-transition-to-the-future-defaults) are the only temporary build
options permitted (§REQ-001-spec-compatibility.2).

**Domain.** A public provider constructor on the class path; a provider method in an explicit module;
a provider method whose implementation differs from its service provider class; a provider method
returning a non-public implementation; a provider configured more than once; a configured provider
whose `configure` result changes its name or implementation class; ordered same-name descriptors;
recursive construction; and repeated failing construction.

### 8.2 Metadata Closure

**Requirement.** The only signals that register a JDK-managed provider are the ordinary reflection
signals in [§2.1](#21-qualifying-reflection-metadata) and the platform signal in
[§2.4](#24-securerandom-providers).
Native Image must introduce no provider-specific metadata category, configuration file, permanent
option, or missing-metadata exception.
Instantiation reachability used to verify an application-supplied provider under
[§5.3](#53-jce-verification) is not a provider-registration signal and must not grant JDK-managed
acquisition or retain services.

**Domain.** The reachability-metadata schema, Native Image option list, provider-path exception types,
and the architecture chokepoints in [§8.9](#89-architecture-obligations).

### 8.3 Diagnostic Sufficiency

**Requirement.** A failure caused by missing provider registration must identify the provider
implementation class by binary name and supply ordinary reflection metadata that makes the same
operation succeed after one rebuild.
For a provider with a supported construction path, the same entry must enable the provider and its
services.

**Witness.** The metadata entry printed in the diagnostic.

**Domain.** Exact-metadata and compatibility reporting, before and after provider enumeration and
provider-list mutation.

### 8.4 No Exposure of an Unregistered Provider

**Requirement.** An unregistered JDK-managed provider must not become observable through any path in
[§1.2](#12-jdk-managed-providers-and-acquisition).
A provider must not supply a JCE-verified service unless Native Image recorded a successful
build-time verification outcome for its class.
Failure must precede application receipt of any provider, service, engine, or facade representing
the unavailable provider.

**Domain.** The enumerated acquisition paths in [§1.2](#12-jdk-managed-providers-and-acquisition),
with unenumerated paths covered by [§8.9.2](#892-the-acquisition-boundary).

### 8.5 Standard Semantics Modulo Registration

**Requirement.** When every JDK-managed provider used by a program is registered and every service
used through an application-supplied provider is retained, the covered APIs must behave as on
HotSpot: the same values, exception types, provider order, and programmatic provider-list behavior.
The only permitted difference is that the native provider list filters out unregistered configured
providers while preserving the relative order of the remainder.

**Oracle.** The same program on HotSpot.

### 8.6 Reachability Independence

**Requirement.** With explicit provider registration enabled, JDK-managed provider availability must
be a function only of configured security properties and the registration signals.
Adding reachable but unexecuted code that introduces no registration signal must not change the
configured provider list, lookup results, or service availability.
Programmatic provider-list mutations are outside this domain because they intentionally change the
list at run time.
This is the provider-specific form of
§FD-002-reachability-independent-runtime-semantics.2.

**Domain.** Pairs of builds differing by one reachable but unexecuted JCA factory or facade call,
excluding the `SecureRandom` acquisition paths in [§2.4](#24-securerandom-providers).

### 8.7 Transition Compatibility

**Requirement.** Every supported combination in [§7](#7-transition-to-the-future-defaults) must be
specified and selected at build time, and the earlier behavior must remain selectable without an
application source change (§REQ-001-spec-compatibility.2).
In legacy run-time provider-list initialization mode, the deprecated additional-provider option must
not require successful provider construction during the image build solely because the option names
the provider.

**Domain.** Legacy inclusion with build-time initialization, legacy inclusion with run-time
initialization, and explicit registration with run-time initialization, including the deprecated
additional-provider option in each mode.

### 8.8 Tracing Round-Trip

**Requirement.** Agent and native tracing metadata must be sufficient to reproduce traced provider
operations and minimal enough not to register an unused provider or repeat an observed operation.
Tracing a provider-list mutation must not add provider-class metadata merely because it observes the
supplied object.
Tracing must not retain application provider objects, must distinguish transient provider-object
calls from successful insertion, and must record the constructor actually used on service-cache
hits.
Caller attribution must cross every JDK runtime-module intermediary before selecting an application
condition.

**Domain.** Provider lookup, enumeration, filtering, list mutation, factory service selection, and
`Provider.Service.newInstance(Object)`.

### 8.9 Architecture Obligations

The following requirements apply universally to the implementation.
[§AR-002-security-providers](../architecture/security-providers.md) names the structures that enforce
them.

#### 8.9.1 Closure of Registration Signals

Every signal that grants JDK-managed provider eligibility must pass through one registration
chokepoint.
A new path must update the architecture record.

#### 8.9.2 The Acquisition Boundary

Every configured-list construction, provider lookup, and direct JDK construction path must pass
through one acquisition filter.
With explicit registration, the filter must reject any provider for which the registration
chokepoint did not record a complete, JDK-constructible plan.
In a legacy mode, the filter may also admit a provider named by the deprecated additional-provider
option under [§7.5](#75-deprecated-additional-provider-option); that provider exposes only services
retained by the applicable legacy rules.

### 8.10 Condition Fidelity

**Requirement.** A conditional registration signal must register a provider exactly as far as its
condition permits.
An unreachable build-time condition contributes nothing; an unsatisfied run-time condition exposes
neither the provider nor derived construction, service, or verification effects.
After activation, the provider and all effects in [§2.3](#23-registration-effects) must become
observable independent of when the provider list was initialized.

**Domain.** Each signal shape in [§2.1](#21-qualifying-reflection-metadata), combined with an
unconditional entry, a build-time condition, and an unsatisfied and satisfied run-time condition,
observed across the acquisition surface in [§1.2](#12-jdk-managed-providers-and-acquisition).
