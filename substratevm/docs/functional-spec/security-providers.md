# FS-002-security-providers: JCA Security Provider Registration and Run-Time Access

The JDK defines its security-provider list through the `security.provider.<n>` security properties,
and HotSpot can load every configured provider on demand.
Native Image cannot simply treat every configured entry as an inclusion signal: a closed-world
executable would then retain every provider and its complete service catalog, even when the
application never uses them.
To avoid that executable-size cost, Native Image uses two independent inputs for a JDK-managed
provider: a `security.provider.<n>` property determines its position in the
[run-time provider list](#terminology3-run-time-provider-list), and invocation metadata for the JDK-selected
construction path must be [registered for reflection](#terminology1-registered-providers-and-services)
at build time to include the provider and its services.
Only providers that satisfy both conditions are constructed and exposed through JDK-managed lookup
at run time; once included, they behave as they do on HotSpot.
[`Security.addProvider(Provider)`][security] and
[`Security.insertProviderAt(Provider, int)`][security] do not require reflection metadata for the
supplied provider class because both APIs accept an existing provider instance.
Native Image still establishes the provider's Java Cryptography Extension (JCE) verification
outcome at build time and retains metadata for service implementations that JCA constructs
reflectively.

For example, assume Bouncy Castle is on the class path and the effective _java.security_
configuration contains:

```properties
security.provider.13=org.bouncycastle.jce.provider.BouncyCastleProvider
```

Register invocation of the provider constructor in _reachability-metadata.json_:

```json
{
  "reflection": [
    {
      "type": "org.bouncycastle.jce.provider.BouncyCastleProvider",
      "methods": [{ "name": "<init>", "parameterTypes": [] }]
    }
  ]
}
```

Only with both entries, [`Security.getProvider("BC")`][security] returns the Bouncy Castle provider.
Without the reflection entry, the same lookup throws an actionable
[`java.lang.SecurityException`][security-exception]
that identifies `org.bouncycastle.jce.provider.BouncyCastleProvider` as the unregistered provider
class and directs the user to reflection metadata.
Without the `security.provider.13` property, the lookup returns `null` because reflection metadata
does not install the provider.
The application can install it explicitly with the standard JDK API:

```java
Security.addProvider(new BouncyCastleProvider());
```

The Tracing Agent collects provider construction metadata when it observes JDK-managed acquisition
and service metadata when it observes a JCA factory use ([§FS-002-security-providers.5](security-providers.md#5-tracing-metadata)).
As on HotSpot, a `security.provider.<n>` property installs the provider during provider-list
initialization, and an application can instead install an existing instance with
[`Security.addProvider`][security] or [`Security.insertProviderAt`][security].

[§FS-002-security-providers.6](security-providers.md#6-transition-to-the-future-defaults) defines the future-default options that select this
behavior during the transition from service-driven provider inclusion and build-time provider-list
initialization.
The implementation is described by [§AR-004-security-providers](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/jca/SecurityServicesFeature.java).

## terminology: Terminology

This specification uses the [common notation](README.md#notation) and the default package
[`java.security`][java-security].

### terminology.1: Registered Providers and Services

- **Provider implementation class**: a concrete [`Provider`][provider] subtype.
- **Service provider class**: the type through which [`ServiceLoader`][service-loader] constructs a
  provider; it can differ from the provider implementation class.
- **Supported construction path**: a public no-argument constructor of a public, concrete
  [`Provider`][provider] subtype, or a public static no-argument [`provider()`][service-loader]
  method of a public service provider class in an explicit module.
- **JDK-constructible**: produced by a supported construction path.
- **Registered**: invocation of the JDK-selected construction path is covered by ordinary
  reflection metadata, and that path is discoverable through the effective security-provider
  configuration or a `java.security.Provider` service declaration.

### terminology.2: JDK-Managed Providers and Acquisition

- **JDK-managed provider**: a provider the JDK creates or discovers.
- **Application-supplied provider**: an existing instance passed by application code.
- **JDK-managed acquisition**: any JDK path that returns, reports, or selects a provider or service,
  including configuration, [`ServiceLoader`][service-loader], factories, and default selection.

### terminology.3: Run-Time Provider List

- **Run-time provider list**: the ordered list consulted by [`Security`][security] lookups and
  name-based JCA factories.
- **Configured entry**: a `security.provider.<n>` value, a provider class or provider name with an
  optional configuration argument.

### terminology.4: Service Availability

- **Retained service**: a service whose implementation and construction metadata are in the
  executable.
- **Available service**: a retained service whose provider the call can select.

## 1. Registration Semantics

### 1.1 Qualifying Reflection Metadata

Ordinary reflection metadata registers a provider only when it permits invocation of the
construction path selected by the JDK: its public no-argument constructor, or the public static
`provider()` factory in an explicit module when that factory takes precedence.
The path must be discoverable through the effective `security.provider.<n>` configuration or a
`java.security.Provider` service declaration.
Type-only metadata, query-only metadata, metadata for a constructor displaced by a selected factory,
and invocation metadata for an unconfigured, undeclared application provider retain their ordinary
reflection meaning; they do not cause provider construction, full-catalog retention, or JDK-managed
eligibility.
For a configured provider, qualifying invocation metadata makes
[`Security.getProvider(String)`][security] return the provider and makes its retained algorithms
usable through calls such as [`Signature.getInstance(String, String)`][signature].
Metadata for only a service implementation class does neither.
Conditional metadata has the behavior in [§FS-002-security-providers.1.5](security-providers.md#15-conditional-registration-signals).
Using ordinary reflection metadata instead of a provider-specific flag follows
[§FD-001-hotspot-compatible-semantics-by-default.2](decisions/hotspot-compatible-semantics-by-default.md#2-decision).
Deriving registration from explicit metadata instead of incidental JCA reachability follows
[§FD-002-reachability-independent-runtime-semantics.2](decisions/reachability-independent-runtime-semantics.md#2-decision).

### 1.2 Provider Construction

Native Image constructs a JDK-managed provider through the same supported path as
[`ServiceLoader`][service-loader]:
a public no-argument constructor, or the public static no-argument
[`provider()`][service-loader] method of a service provider class in a named module.
When both apply, [`provider()`][service-loader] takes precedence (JLS 7.7.4).
For a configured entry with an argument, construction includes the subsequent
[`Provider.configure(argument)`][provider] call and uses its return value, even when that provider
has a different name or implementation class.
When a configured token is a provider name rather than a class name, Native Image tries eligible
_META-INF/services/java.security.Provider_ declarations in descriptor order and uses the first
successfully constructed provider whose pre-configuration name matches the token.
An ambiguous provider name never falls back to treating that token as a binary class name.
Each configured entry is independent, so two entries for the same provider class can produce
distinct provider instances, names, and service catalogs while retaining their configured relative
order.
The run-time provider list contains registered configured providers in configured relative order.
Existing instances are inserted at the positions specified by the [`Security`][security] API, as
defined in [§FS-002-security-providers.4.2](security-providers.md#42-programmatic-provider-list-changes).
Provider registration does not install a provider, and insertion does not register one.
Provider-list initialization is distinct from class initialization (JLS 12.4).
A private or parameterized constructor and a class-path `provider()` method do not qualify.
A service descriptor is passive inventory: discovering it does not initialize or instantiate its
declaration class.
Native Image invokes a descriptor construction path during image generation only after qualifying
invocation metadata activates that selected path.
A reflectively accessible provider without a qualifying invocation path is not returned by
[`Security.getProvider(String)`][security]; application code can still supply an existing instance
under [§FS-002-security-providers.4](security-providers.md#4-programmatically-supplied-providers).
If invoking a selected path during the image build fails or returns the wrong provider class, the
build fails with a diagnostic naming the provider and construction failure.

### 1.3 Registration Effects

Registering a JDK-constructible provider retains its selected construction path, every valid and
resolvable service in its catalog, the service construction metadata, and its JCE verification
outcome.
Thus, registering a provider that declares algorithms `A` and `B` makes both appear through
[`Provider.getServices()`][provider] and makes both usable even if application reachability mentions
only `A`.
If configured instances of the same provider class declare different services, Native Image retains
the services of every instance.
A registered class without a supported construction path retains no provider catalog.
Registration alone does not install the provider: without a matching `security.provider.<n>` entry,
[`Security.getProvider(provider.getName())`][security] returns `null` until the application inserts
an instance.

### 1.4 SecureRandom Providers

Reachability of a [`SecureRandom`][secure-random] constructor or
[`SecureRandom.getInstance`][secure-random] call registers the configured providers that supply
[`SecureRandom`][secure-random], without application reflection metadata for those providers.
The provider is then visible through [`Security`][security] if its `security.provider.<n>` entry
installs it, and its complete catalog has the effects of [§FS-002-security-providers.1.3](security-providers.md#13-registration-effects).
Native Image's internal random source triggers this rule only in an image that includes runtime
compilation, which consumes that source.

### 1.5 Conditional Registration Signals

A condition on provider metadata gates the provider, its construction path, services, and
verification outcome as one unit.
Before the condition becomes active, [`Security.getProviders()`][security] omits the configured
provider, and a name-based factory cannot select it.
After activation, [`Security.getProvider(String)`][security] returns it at its configured position
and its retained services become usable.
The result is the same whether [`Security.getProviders()`][security] initialized the list before or
after activation, and an active condition never becomes inactive.

## 2. Permitted Run-Time Access

### 2.1 JDK-Managed Acquisition

A configured, registered, JDK-constructible provider can be returned by
[`Security.getProvider(String)`][security] and
[`ServiceLoader.load(Provider.class)`][service-loader], selected by calls such as
[`Signature.getInstance(String)`][signature] or
[`Signature.getInstance(String, String)`][signature], and used by JDK default
or fallback selection.
The engine's [`getProvider()`][signature] returns the selected provider.
[`SecureRandom`][secure-random] additionally follows [§FS-002-security-providers.1.4](security-providers.md#14-securerandom-providers).

#### 2.1.1 GSS Mechanism Discovery

[`GSSManager.getInstance().getMechs()`][gss-manager] reports a mechanism when an available provider
supplies its retained GSS mechanism service.
It does not report a mechanism supplied only by an unavailable provider.

#### 2.1.2 SASL Client Creation

[`Sasl.createSaslClient(mechanisms, authorizationId, protocol, serverName, properties, callbackHandler)`][sasl]
can select only a retained SASL client-factory service from an available provider.
It returns `null` when none of the requested mechanisms has such a service.

#### 2.1.3 SASL Server Creation

[`Sasl.createSaslServer(mechanism, protocol, serverName, properties, callbackHandler)`][sasl] can
select only a retained SASL server-factory service from an available provider.
It returns `null` when the requested mechanism has no such service.

### 2.2 Provider List Lookups

[`Security.getProvider(String)`][security] and the [`Security.getProviders`][security] overloads
operate on the run-time provider list from [§FS-002-security-providers.terminology.3](security-providers.md#terminology3-run-time-provider-list).
After [`Security.addProvider(provider)`][security] succeeds,
[`Security.getProvider(provider.getName())`][security] returns
that same object even when its class has no reflection metadata.
[`Security.getProviders(String)`][security], [`Security.getProviders(Map)`][security], and
[`Security.getAlgorithms(String)`][security] report only providers and retained services present in
that list.

### 2.3 JCA Factories and Security-Service Facades

[`Signature.getInstance(algorithm)`][signature] selects a retained service from the run-time provider
list, and [`Signature.getInstance(algorithm, providerName)`][signature] restricts selection to the
named list entry.
[`Signature.getInstance(algorithm, provider)`][signature] instead uses the supplied object without
installing it or requiring provider-class reflection metadata.
Equivalent overloads on other JCA factories follow the same rules.
Changing an algorithm string at run time can select only services already retained in the
executable; it cannot add another provider or service.

### 2.4 Programmatic Access

An application can use an existing provider immediately with a provider-object factory.
It can make the same object available to name-based lookup by calling
[`Security.addProvider(Provider)`][security] or
[`Security.insertProviderAt(Provider, int)`][security], and remove it with
[`Security.removeProvider(String)`][security].
[§FS-002-security-providers.4](security-providers.md#4-programmatically-supplied-providers) gives the metadata and verification rules.

## 3. Prohibited Run-Time Access and Errors

### 3.1 Unregistered Providers

A configured but unregistered provider is omitted from [`Security.getProviders()`][security],
provider filters, and [`Security.getAlgorithms(String)`][security].
These APIs enumerate usable providers and services.
They neither expose a partially functional provider nor fail solely because another configured
provider is unregistered, as required by
[§FD-003-provider-enumeration-reports-usable-providers](decisions/provider-enumeration-reports-usable-providers.md#fd-003-provider-enumeration-reports-usable-providers-enumerate-only-usable-security-providers).
[`Security.getProvider(String)`][security], JCA factories, [`ServiceLoader`][service-loader], and JDK
default or fallback paths must not return that provider, one of its services, or an engine backed by
it.
Making a factory call reachable, adding a _META-INF/services/java.security.Provider_ descriptor, or
passing a different algorithm string at run time cannot change that result after the executable is
built.

### 3.2 Standard Unavailable Results

[`Signature.getInstance(algorithm, missingProviderName)`][signature] throws
[`NoSuchProviderException`][no-such-provider] when the name is absent from the run-time provider
list.
[`Signature.getInstance(missingAlgorithm)`][signature] throws
[`NoSuchAlgorithmException`][no-such-algorithm] when no list entry has a retained implementation,
and [`Signature.getInstance(missingAlgorithm, provider)`][signature] throws the same
exception when the supplied object lacks one.
A provider-object overload does not throw merely because the provider is absent from the list.

### 3.3 Missing Reflection Registration

When [`Security.getProvider(String)`][security] or a JCA factory causes the JDK to reflectively load
an unregistered configured provider, acquisition fails before returning the provider.
This diagnostic applies only with explicit provider registration enabled. Legacy modes preserve
their earlier lookup results and exceptions for providers omitted from the Native Image provider
list.
The ordinary reflection diagnostic names the provider implementation class in binary form and tells
the user to register invocation of the JDK-selected constructor or factory in _reachability-metadata.json_.
There is no provider-specific metadata format or provider-specific missing-metadata error type.
The diagnostic lookup is side-effect-free: it does not construct or cache the omitted provider and
does not change subsequent enumeration, duplicate detection, insertion, or factory behavior.
An implementation retained only for provider-object use under [§FS-002-security-providers.4](security-providers.md#4-programmatically-supplied-providers)
is not diagnosed by a name lookup: until the application inserts an instance,
[`Security.getProvider(String)`][security] returns `null`.
This exception does not suppress the diagnostic for a configured built-in provider merely because
application code also makes provider-object use of its implementation class reachable.
The diagnostic resolver uses only recorded configured entries and built-in provider aliases; it
does not interpret an arbitrary dotted provider name as an implementation class name.
The same diagnostic applies in exact-metadata and compatibility reporting modes.
If reflective construction is attempted,
[`MissingReflectionRegistrationError`][missing-reflection-registration-error] remains the top-level
failure and is not wrapped as a provider-configuration error.

## 4. Programmatically Supplied Providers

### 4.1 Provider-Object Factory Calls

After application code constructs a provider, a call such as
[`Signature.getInstance(algorithm, provider)`][signature] does not require reflection metadata for
the provider class and does not add the provider to the run-time list.
The call succeeds when the service implementation and its required construction metadata are
retained and [§FS-002-security-providers.4.3](security-providers.md#43-jce-verification) permits use.
A missing implementation produces [`NoSuchAlgorithmException`][no-such-algorithm]; a JCE
verification failure follows [§FS-002-security-providers.4.3](security-providers.md#43-jce-verification).

### 4.2 Programmatic Provider-List Changes

[`Security.addProvider(Provider)`][security] and
[`Security.insertProviderAt(Provider, int)`][security] do not require
reflection metadata for the supplied provider class.
On success they insert that exact object, after which
[`Security.getProvider(provider.getName())`][security]
returns it and a name-based factory can select its retained services.
They return `-1` when the list already contains a provider with the same name, and otherwise use the
positions and ordering defined by the standard [`Security`][security] API.
Duplicate detection and insertion positions count only providers visible in the run-time list.
Inactive conditional or unregistered configurations retain their relative order but neither block an
application-supplied provider with the same name nor shift its visible insertion position.
[`Security.removeProvider(provider.getName())`][security] removes the list entry.
Insertion retains no additional service implementation, so a missing service still produces
[`NoSuchAlgorithmException`][no-such-algorithm].

### 4.3 JCE Verification

[`Cipher`][cipher], [`Mac`][mac], and other factories that require JCE provider verification may
construct a service only when Native Image recorded successful build-time verification for that
provider class.
A failed outcome makes the factory throw [`SecurityException`][security-exception]; a missing
outcome produces the missing-registration failure from
[§FS-002-security-providers.3.3](security-providers.md#33-missing-reflection-registration).
In either case, the factory fails before it constructs the service implementation.
This rule applies equally to a JDK-managed provider and an application-supplied provider; the latter
still requires no provider-class reflection metadata.
Instantiation reachability records successful verification for an application-supplied-only
provider class independently of any configured-list instance of the same class. In particular, a
verification failure for an omitted configured instance does not prevent provider-object use of an
application-created instance.
The implementation-specific recognition rule is [§AR-004-security-providers.requirements.3](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/jca/SecurityServicesFeature.java).

## 5. Tracing Metadata

### 5.1 Provider and Service Coverage

Provider construction under tracing records the actual reflection operations.
Successful JDK-managed acquisition also retains the selected construction invocation for a cached
provider when the acquisition hook can identify that path, including built-in providers cached
before tracing starts.
Acquisition hooks must not grant a provider visited only while searching for a different result.
Ordinary reflection tracing can still record construction that the JDK actually attempts while
searching or filtering.
These hooks remain until cached and selective acquisition round trips have equivalent coverage.
Tracing a factory call or [`Provider.Service.newInstance(Object)`][provider-service] records the
actual implementation and constructor operations needed to repeat each service construction that
the JDK attempts. This includes a candidate that is constructed before a later initialization or
selection step rejects it.
Rebuilding with only the generated metadata must reproduce the observed factory selection when
explicit provider registration is enabled and its construction or supported cached acquisition occurred under tracing.
Tracing [`Security.addProvider`][security], [`Security.insertProviderAt`][security], or
[`Security.removeProvider`][security] does not record provider-class metadata merely because the
call receives an existing object.
Selecting an application-supplied object later does not record its provider class: the object was
not reflectively constructed by the provider-list operation.
For a cached service implementation, tracing records the constructor the JDK actually cached,
including a no-argument constructor selected after a parameterized-constructor probe failed.
Security operations reached through JDK runtime modules are attributed to the first non-JDK caller,
including calls mediated by non-security JDK classes or platform provider modules.
An application [`Class.forName`][class], reflective constructor call, or reflective service
construction is traced by the ordinary reflection rules.

### 5.2 Observational Transparency

With tracing enabled, a provider lookup, factory call, or provider-list mutation has the same return
value, exception, provider order, and application-visible side effects as the same call without
tracing.
The tracer does not repeat the operation or initialize an unselected provider or service.
Reflection and resource access performed inside the application's actual call is still recorded.

## 6. Transition to the Future Defaults

[§FS-002-security-providers.1](security-providers.md#1-registration-semantics) through [§FS-002-security-providers.5](security-providers.md#5-tracing-metadata) specify the planned
default behavior.
The following build-time options select it while earlier behaviors remain available.
Explicit provider registration implies run-time provider-list initialization.

With configuration, metadata, and mode fixed, adding reachable but unexecuted application code
does not change provider availability or ordering, except for the documented `SecureRandom` rule.
Switching to metadata-driven registration can change those results with otherwise unchanged metadata:
constructor or factory invocation metadata collected for application reflection can enable a
configured provider, while type-only and query-only entries no longer enable its complete catalog.
When migrating, register the selected constructor or factory for every intended configured provider,
and remove unwanted providers from the effective security configuration.
Switching back to a legacy mode restores service-reachability inclusion and can expose providers
omitted by metadata-driven registration.

### 6.1 Run-Time Provider-List Initialization

With `--future-defaults=run-time-initialize-security-providers`, Native Image initializes the list
when [`Security`][security] first needs it at run time.
[`Security.getProviders()`][security] contains only registered entries from the build-time
`security.provider.<n>` configuration and preserves their relative order.
[`Security.getProvider(String)`][security] constructs an entry through its retained path and fails
with an actionable diagnostic if that path fails or returns the wrong implementation.
Every configured construction path uses the JDK recursion guard and retry counter, including a path
resolved from retained descriptor inventory.
Recursive loading returns no provider for that attempt, and repeated failures stop at the JDK retry
limit rather than retrying indefinitely.
`--future-defaults=metadata-security-provider-registration` enables this behavior implicitly.

### 6.2 Provider Service Descriptors

A class-path _META-INF/services/java.security.Provider_ descriptor does not register its provider
for reflection.
Without provider reflection metadata, [`ServiceLoader.load(Provider.class)`][service-loader] does not
return the described provider.
With that metadata, it can return the provider only when [`ServiceLoader`][service-loader] access to
[`Provider`][provider] is reachable and therefore retains the descriptor.
Descriptor discovery preserves declaration order without instantiating declarations that have no
active provider-registration signal.
Only explicit-registration mode registers a negative resource query for an absent provider
descriptor; both legacy modes preserve the ordinary resource-query behavior. The negative query
permits an absent descriptor to return no resource, but does not suppress a descriptor retained by
an independent reachable use. In particular, run-time provider initialization can itself retain
the descriptor in a legacy mode, so an opaque query may find it without explicit resource metadata.

### 6.3 Earlier Service-Driven Inclusion Behavior

Without `--future-defaults=metadata-security-provider-registration`, reachability of a supported JCA
factory or security facade retains configured services of the corresponding type even when their
provider classes lack reflection metadata.
For example, a reachable [`Signature.getInstance(runtimeAlgorithm)`][signature] can make configured
[`Signature`][signature] services available, but the run-time value cannot retain another service
type.
Likewise, reachability of [`GSSManager.getInstance().getMechs()`][gss-manager],
[`Sasl.createSaslClient(...)`][sasl], or [`Sasl.createSaslServer(...)`][sasl] can retain the configured
GSS mechanism, SASL client-factory, or SASL server-factory services, respectively.
With `--future-defaults=metadata-security-provider-registration`, the same call can select only
providers registered under [§FS-002-security-providers.1.1](security-providers.md#11-qualifying-reflection-metadata), except for the
[`SecureRandom`][secure-random] rule in [§FS-002-security-providers.1.4](security-providers.md#14-securerandom-providers).
Neither mode changes the programmatic rules in [§FS-002-security-providers.4](security-providers.md#4-programmatically-supplied-providers).

### 6.4 Earlier Build-Time Initialization Behavior

Without `--future-defaults=run-time-initialize-security-providers`, Native Image retains the earlier
behavior that initializes the configured provider list during the image build.
Provider-constructor side effects therefore occur during the build, and the executable starts with
the resulting provider-list state.

### 6.5 Deprecated Additional-Provider Option

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
In legacy build-time provider-list initialization mode, [§FS-002-security-providers.6.4](security-providers.md#64-earlier-build-time-initialization-behavior)
still permits configured-provider construction during the image build.
The option does not install an otherwise unconfigured provider and does not change provider order.
Replacing it with ordinary reflection metadata requires explicit provider registration until that
behavior becomes the default.

## requirements: Requirements

The requirements in this chapter define the acceptance criteria for this specification.
Each states a required property and its domain.
Universal implementation obligations are stated by [§AR-004-security-providers.requirements](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/jca/SecurityServicesFeature.java).

### requirements.1: Construction Parity

**Requirement.** For every provider the JDK can construct on HotSpot through a supported path from
[§FS-002-security-providers.terminology.1](security-providers.md#terminology1-registered-providers-and-services), ordinary reflection metadata must let a native
executable acquire the same provider through the same JDK API and use its services.
Native Image must require neither a provider-specific option nor an application source change.
This is the provider-specific form of [§FD-001-hotspot-compatible-semantics-by-default.2](decisions/hotspot-compatible-semantics-by-default.md#2-decision).
The transition options in [§FS-002-security-providers.6](security-providers.md#6-transition-to-the-future-defaults) are the only temporary build
options permitted.

**Domain.** A public provider constructor on the class path; a provider method in an explicit module;
a provider method whose implementation differs from its service provider class; a provider method
returning a non-public implementation; a provider configured more than once; a configured provider
whose `configure` result changes its name or implementation class; ordered same-name descriptors;
recursive construction; and repeated failing construction.

### requirements.2: Metadata Closure

**Requirement.** The only signals that register a JDK-managed provider are the ordinary reflection
signals in [§FS-002-security-providers.1.1](security-providers.md#11-qualifying-reflection-metadata) and the platform signal in
[§FS-002-security-providers.1.4](security-providers.md#14-securerandom-providers).
Native Image must introduce no provider-specific metadata category, configuration file, permanent
option, or missing-metadata exception.
Instantiation reachability used to verify an application-supplied provider under
[§FS-002-security-providers.4.3](security-providers.md#43-jce-verification) is not a provider-registration signal and must not grant JDK-managed
acquisition or retain services.

**Domain.** The reachability-metadata schema, Native Image option list, provider-path exception types,
and the architecture chokepoints in [§AR-004-security-providers.requirements](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/jca/SecurityServicesFeature.java).

### requirements.3: Diagnostic Sufficiency

**Requirement.** A failure caused by missing provider registration must identify the provider
implementation class by binary name and supply ordinary reflection metadata that makes the same
operation succeed after one rebuild.
For a provider with a supported construction path, the same entry must enable the provider and its
services.

**Witness.** The metadata entry printed in the diagnostic.

**Domain.** Exact-metadata and compatibility reporting, before and after provider enumeration and
provider-list mutation.

### requirements.4: No Exposure of an Unregistered Provider

**Requirement.** An unregistered JDK-managed provider must not become observable through any path in
[§FS-002-security-providers.terminology.2](security-providers.md#terminology2-jdk-managed-providers-and-acquisition).
A provider must not supply a JCE-verified service unless Native Image recorded a successful
build-time verification outcome for its class.
Failure must precede application receipt of any provider, service, engine, or facade representing
the unavailable provider.

**Domain.** The enumerated acquisition paths in [§FS-002-security-providers.terminology.2](security-providers.md#terminology2-jdk-managed-providers-and-acquisition),
with unenumerated paths covered by [§AR-004-security-providers.requirements.2](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/jca/SecurityServicesFeature.java).

### requirements.5: Standard Semantics Modulo Registration

**Requirement.** When every JDK-managed provider used by a program is registered and every service
used through an application-supplied provider is retained, the covered APIs must behave as on
HotSpot: the same values, exception types, provider order, and programmatic provider-list behavior.
The only permitted difference is that the native provider list filters out unregistered configured
providers while preserving the relative order of the remainder.

**Oracle.** The same program on HotSpot.

### requirements.6: Reachability Independence

**Requirement.** With explicit provider registration enabled, JDK-managed provider availability must
be a function only of configured security properties and the registration signals.
Adding reachable but unexecuted code that introduces no registration signal must not change the
configured provider list, lookup results, or service availability.
Programmatic provider-list mutations are outside this domain because they intentionally change the
list at run time.
This is the provider-specific form of
[§FD-002-reachability-independent-runtime-semantics.2](decisions/reachability-independent-runtime-semantics.md#2-decision).

**Domain.** Pairs of builds differing by one reachable but unexecuted JCA factory or facade call,
excluding the [`SecureRandom`][secure-random] acquisition paths in
[§FS-002-security-providers.1.4](security-providers.md#14-securerandom-providers).

### requirements.7: Transition Compatibility

**Requirement.** Every supported combination in [§FS-002-security-providers.6](security-providers.md#6-transition-to-the-future-defaults) must be
specified and selected at build time, and the earlier behavior must remain selectable without an
application source change.
In legacy run-time provider-list initialization mode, the deprecated additional-provider option must
not require successful provider construction during the image build solely because the option names
the provider.

**Domain.** Legacy inclusion with build-time initialization, legacy inclusion with run-time
initialization, and explicit registration with run-time initialization, including the deprecated
additional-provider option in each mode.

### requirements.8: Tracing Round-Trip

**Requirement.** With explicit provider registration enabled, agent and native tracing metadata
must reproduce observed factory selection when construction or a supported cached acquisition
occurred under tracing.
Tracing records the provider and service construction operations that the JDK actually attempts,
including candidates constructed during traversal or rejected by later initialization or selection.
It must not construct additional providers or services or repeat an observed operation to generate
metadata.
A cached acquisition may record its selected construction invocation without executing it again.
A search-only visit must not add an acquisition grant for an unselected provider.
Tracing a provider-list mutation or later selection of an application-supplied object must not add
provider-class metadata merely because it observes the supplied object.
Tracing must not retain application provider objects and must record the constructor actually used
on service-cache hits.
Caller attribution must cross every JDK runtime-module intermediary before selecting an application
condition.

**Domain.** Provider lookup, enumeration, filtering, list mutation, factory service selection, and
[`Provider.Service.newInstance(Object)`][provider-service].

### requirements.9: Condition Fidelity

**Requirement.** A conditional registration signal must register a provider exactly as far as its
condition permits.
An unreachable build-time condition contributes nothing; an unsatisfied run-time condition exposes
neither the provider nor derived construction, service, or verification effects.
After activation, the provider and all effects in [§FS-002-security-providers.1.3](security-providers.md#13-registration-effects) must become
observable independent of when the provider list was initialized.

**Domain.** Each signal shape in [§FS-002-security-providers.1.1](security-providers.md#11-qualifying-reflection-metadata), combined with an
unconditional entry, a build-time condition, and an unsatisfied and satisfied run-time condition,
observed across the acquisition surface in [§FS-002-security-providers.terminology.2](security-providers.md#terminology2-jdk-managed-providers-and-acquisition).

[cipher]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/Cipher.html
[class]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Class.html
[gss-manager]: https://docs.oracle.com/en/java/javase/25/docs/api/java.security.jgss/org/ietf/jgss/GSSManager.html
[java-security]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/package-summary.html
[mac]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/Mac.html
[missing-reflection-registration-error]: https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/MissingReflectionRegistrationError.html
[no-such-algorithm]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/NoSuchAlgorithmException.html
[no-such-provider]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/NoSuchProviderException.html
[provider-service]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Provider.Service.html
[provider]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Provider.html
[sasl]: https://docs.oracle.com/en/java/javase/25/docs/api/java.security.sasl/javax/security/sasl/Sasl.html
[secure-random]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureRandom.html
[security-exception]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/SecurityException.html
[security]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Security.html
[service-loader]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/ServiceLoader.html
[signature]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Signature.html
