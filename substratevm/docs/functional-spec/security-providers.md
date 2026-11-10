# FS-002-security-providers: JCA Security Provider Registration and Run-Time Access

## reason: Reason for Existence

A JCA factory can select its algorithm at run time:

```java
java.security.Signature.getInstance(args[0]);
```

Retaining all 12 configured JDK security providers and their service catalogs grows Hello World
from 6.07 MiB to 26.94 MiB on Linux x86-64 with Oracle GraalVM 25.6.0-dev and `-O2`.
Provider reflection metadata limits inclusion to the intended providers
([§FS-002-security-providers.design.1.1](security-providers.md#1-qualifying-reflection-metadata)).

## terminology: Terminology

This specification uses the [common notation](README.md#notation) and the default package
[`java.security`][java-security].

**Registered Providers and Services**

- **Provider implementation class**: a concrete [`Provider`][provider] subtype.
- **Service provider class**: the type through which [`ServiceLoader`][service-loader] constructs a
  provider; it can differ from the provider implementation class.
- **Supported construction path**: a public no-argument constructor of a public, concrete
  [`Provider`][provider] subtype, or a public static no-argument [`provider()`][service-loader]
  method of a public service provider class in an explicit module.
- **JDK-selected constructor**: the public no-argument constructor of a public, concrete
  provider class that the JDK uses when no qualifying `provider()` factory takes precedence.
- **JDK-constructible**: produced by a supported construction path.
- **Registered**: invocation of the JDK-selected construction path is covered by ordinary
  reflection metadata, and that path is discoverable through the effective security-provider
  configuration or a `java.security.Provider` service declaration.

**JDK-Managed Providers and Acquisition**

- **JDK-managed provider**: a provider the JDK creates or discovers.
- **Application-supplied provider**: an existing instance passed by application code.
- **JDK-managed acquisition**: any JDK path that returns, reports, or selects a provider or service,
  including configuration, [`ServiceLoader`][service-loader], factories, and default selection.

**Run-Time Provider List**

- **Run-time provider list**: the ordered list consulted by [`Security`][security] lookups and
  name-based JCA factories.
- **Configured entry**: a `security.provider.<n>` value, a provider class or provider name with an
  optional configuration argument.

**Service Availability**

- **Retained service**: a service whose implementation and construction metadata are in the
  executable.
- **Available service**: a retained service whose provider the call can select.

## design: Design

Native Image separates including a provider in the executable from installing it in the run-time
provider list.
Reflection metadata for the JDK-selected constructor or `provider()` factory includes a configured
or service-declared provide r and its complete service catalog ([§FS-002-security-providers.design.1.3](security-providers.md#3-registration-effects)).
The `security.provider.<n>` properties determine which providers the JDK installs and their order
([§FS-002-security-providers.design.1.2](security-providers.md#2-provider-construction)).

For example, suppose Bouncy Castle is on the class path and its provider name is `BC`.
Its configuration property is:

```properties
security.provider.13=org.bouncycastle.jce.provider.BouncyCastleProvider
```

The reflection entry permits invocation of `BouncyCastleProvider`'s public no-argument constructor.
The two inputs have independent effects:

| Security property | Constructor metadata | `Security.getProvider("BC")` |
| --- | --- | --- |
| Present | Present | Returns the provider. |
| Present | Missing | Reports missing reflection metadata ([§FS-002-security-providers.design.3.3](security-providers.md#3-missing-reflection-registration)). |
| Missing | Present | Returns `null`: metadata does not install the provider. |

You can obtain or use the provider in three ways:

- `Security.getProvider("BC")` retrieves the installed provider.
- `Signature.getInstance("SHA256withRSA", "BC")` uses a service from the installed provider.
- `Signature.getInstance("SHA256withRSA", new BouncyCastleProvider())` uses the supplied instance
  without installing it or requiring provider-class reflection metadata.

These factory overloads follow [§FS-002-security-providers.design.2.3](security-providers.md#3-algorithm-selection).
To make an existing instance available to name-based calls,
`Security.addProvider(new BouncyCastleProvider())` installs it
without provider-class reflection metadata ([§FS-002-security-providers.design.4.2](security-providers.md#2-programmatic-provider-list-changes)).
Its services still need reflection metadata ([§FS-002-security-providers.design.4.1](security-providers.md#1-provider-object-factory-calls)), and Native Image
records the provider's JCE verification result at build time ([§FS-002-security-providers.design.4.3](security-providers.md#3-jce-verification)).

The implementation is described by [§AR-004-security-providers](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/jca/SecurityServicesFeature.java).

### .1: Registration Semantics

#### .1: Qualifying Reflection Metadata

To include a provider and its services, Native Image needs two things:

1. **A way to find the provider:** a `security.provider.<n>` property or a
   `java.security.Provider` service declaration.
2. **Permission to create it:** reflection metadata that allows calling the constructor or factory
   method the JDK uses.

Usually, you register the provider's public no-argument constructor.
For Bouncy Castle, this lets the JDK create a `BouncyCastleProvider`.
If the JDK uses a public static no-argument `provider()` method from an explicit module, register
that method instead.
Registering only the constructor does not help when the JDK calls the factory.

If the metadata has a condition, that condition also controls when the provider becomes available
([§FS-002-security-providers.design.1.4](security-providers.md#4-conditional-registration-signals)).
This follows the decisions to use standard metadata without a provider-specific flag
([§FD-001-hotspot-compatible-semantics-by-default.2](decisions/hotspot-compatible-semantics-by-default.md#2-decision))
and make provider inclusion an explicit choice
([§FD-002-reachability-independent-runtime-semantics.2](decisions/reachability-independent-runtime-semantics.md#2-decision)).

#### .2: Provider Construction

Native Image creates a JDK-managed provider through a public no-argument constructor or,
in a named module, a public static no-argument `provider()` method.
The method takes precedence when both exist, following the rules of [`ServiceLoader`][service-loader]
([JLS 7.7.4](https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.7.4)).
Private or parameterized constructors and `provider()` methods on the class path do not qualify.

Native Image calls the selected constructor or method at build time only when reflection metadata
allows the call.
Listing the class in a service descriptor alone does not initialize it or create a provider.
If the call fails or returns the wrong provider class, Native Image stops the build and reports
which provider failed and why.

A factory may return a subclass of its declared return type.
For a configuration entry with an argument, Native Image then calls [`Provider.configure(argument)`][provider].
It uses the object returned by that call, even if its name or class differs from the original.
Native Image retains the final object's name, actual class, and services.

For a provider configured by name, such as `BC`, Native Image tries eligible entries in
_META-INF/services/java.security.Provider_ in their listed order.
It selects the first provider it can create with that name, before calling `configure`.
It never treats an ambiguous provider name as a class name.

Configuration entries are independent: entries for the same class can produce separate objects
with different names and services.
Registered providers stay in configuration order at run time.
Instances added by the application follow the [`Security`][security] API's positioning rules
([§FS-002-security-providers.design.4.2](security-providers.md#2-programmatic-provider-list-changes)).
Initializing the provider list and initializing provider classes are separate operations
([JLS 12.4](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4)).

#### .3: Registration Effects

Registering a JDK-constructible provider retains its selected construction path, every valid and
resolvable service in its catalog, the service construction metadata, and its JCE verification
outcome.
Reusing an already-loaded build-time instance preserves the same service declaration and factory
path that fresh construction would record; the implementation class does not replace that path.
Thus, registering a provider that declares algorithms `A` and `B` makes both appear through
[`Provider.getServices()`][provider] and makes both usable even if application reachability mentions
only `A`.
If configured instances of the same provider class declare different services, Native Image retains
the services of every instance.
A registered class without a supported construction path retains no provider catalog.
Registration alone does not install the provider: without a matching `security.provider.<n>` entry,
[`Security.getProvider(provider.getName())`][security] returns `null` until the application inserts
an instance.

#### .4: Conditional Registration Signals

A configured provider becomes available when the condition on its reflection metadata is met.
The same condition controls its constructor or factory, services, and recorded verification result.
Until then, JDK lookups do not construct or select that provider.

For example, suppose Bouncy Castle is configured as `BC`, and its constructor metadata contains
`"condition": {"typeReached": "com.example.Signing"}`.
Assume `Signing` is initialized at run time and no other registration enables `BC`.
The condition is met when `Signing` or one of its subtypes begins initialization.

| Method | Condition not met | Condition met |
| --- | --- | --- |
| [`Security.getProvider("BC")`][security] | Throws [`MissingReflectionRegistrationError`][missing-reflection-registration-error]. | Returns `BC`. |
| [`Signature.getInstance("SHA256withRSA", "BC")`][signature] | Throws [`MissingReflectionRegistrationError`][missing-reflection-registration-error]. | Uses `BC`'s signing service. |
| [`Security.getProviders()`][security] | Omits `BC`. | Includes `BC` in configured order. |
| [`Security.getProviders("Signature.SHA256withRSA")`][security] | Omits `BC`; returns `null` if no other provider matches. | Includes `BC` among the matching providers. |
| [`Security.getAlgorithms("Signature")`][security] | Reports only algorithms from other active providers. | Also reports `BC`'s signature algorithms. |
| [`Signature.getInstance("SHA256withRSA")`][signature] | Uses another active provider, or throws [`NoSuchAlgorithmException`][no-such-algorithm] if none supplies the algorithm. | May use `BC`, following provider order. |

Activation works even if an earlier call initialized the provider list while the condition was unmet.
Subsequent lookups see the provider; arrays returned by earlier calls do not change.
Once met, the condition stays met.
If the condition type is unreachable at build time, this metadata contributes no provider or services.
If activation makes an earlier service descriptor eligible, Native Image tries it even if a later
candidate was already cached or had exhausted its construction retries.
The newly selected descriptor starts with a fresh retry budget.

#### .5: SecureRandom Providers

[`SecureRandom`][secure-random] is used throughout the JDK and libraries, often indirectly.
Its providers are therefore registered automatically when the application uses it.

Reachability of a [`SecureRandom`][secure-random] constructor or
[`SecureRandom.getInstance`][secure-random] call registers the configured providers that supply
[`SecureRandom`][secure-random], without application reflection metadata for those providers.
The provider is then visible through [`Security`][security] if its `security.provider.<n>` entry
installs it, and its complete catalog has the effects of [§FS-002-security-providers.design.1.3](security-providers.md#3-registration-effects).
If no configured provider supplies this service, the rule registers nothing; a provider-object
`getInstance` call can still use an application-supplied provider with service metadata.
Native Image's internal random source triggers this rule only in an image that includes runtime
compilation, which consumes that source.

### .2: Provider Lookup and Use

At run time, JCA selects providers from the provider list, unless an application passes a provider
object directly.
In either case, the required service implementation and reflection metadata must be included in
the executable.

#### .1: Provider Discovery

The JDK can use a configured provider once it is registered, its construction path is supported,
and its registration conditions are met.
This includes default and fallback provider selection.
Providers with service declarations can also be discovered through
[`ServiceLoader.load(Provider.class)`][service-loader].
[`SecureRandom`][secure-random] additionally follows [§FS-002-security-providers.design.1.5](security-providers.md#5-securerandom-providers).

##### .1: GSS Mechanism Discovery

[`GSSManager.getInstance().getMechs()`][gss-manager] reports mechanisms supplied by available
providers whose GSS service implementations are included in the executable.
Mechanisms supplied only by unavailable providers do not appear in the result.

##### .2: SASL Client Creation

[`Sasl.createSaslClient(...)`][sasl] uses included SASL client factories from available providers.
It returns `null` when none supplies a service for any of the requested mechanisms.

##### .3: SASL Server Creation

[`Sasl.createSaslServer(...)`][sasl] uses included SASL server factories from available providers.
It returns `null` when none supplies a service for the requested mechanism.

#### .2: Provider Lookup

[`Security.getProvider(name)`][security] looks up a provider by name in the run-time provider list.
[`Security.getProviders()`][security] returns the active providers in order.
The filtering overloads, [`Security.getProviders(String)`][security] and
[`Security.getProviders(Map)`][security], return matching providers from that list.
[`Security.getAlgorithms(service)`][security] reports their available algorithms.
These lookups use the same provider list as JCA factories.

#### .3: Algorithm Selection

The factory overload determines how a provider is selected:

| Call | Provider selection |
| --- | --- |
| [`Signature.getInstance(algorithm)`][signature] | Searches the provider list in order. |
| [`Signature.getInstance(algorithm, providerName)`][signature] | Uses the named provider from the list. |
| [`Signature.getInstance(algorithm, provider)`][signature] | Uses the supplied object directly, without adding it to the list. |

Equivalent overloads on other JCA factories follow the same rules.
The resulting engine's [`getProvider()`][signature] returns the selected provider.
Changing the algorithm name at run time can select another included implementation; it cannot add
providers or algorithms to the executable.

#### .4: Adding and Removing Providers

[`Security.addProvider(provider)`][security] and
[`Security.insertProviderAt(provider, position)`][security] add an existing instance to the provider list.
After successful insertion, name-based lookups return that same object.
[`Security.removeProvider(name)`][security] removes it from the list
([§FS-002-security-providers.design.4.2](security-providers.md#2-programmatic-provider-list-changes)).

Supplying or inserting an existing instance requires no reflection metadata for the provider class.
Its services still require their implementation metadata
([§FS-002-security-providers.design.4.1](security-providers.md#1-provider-object-factory-calls)) and, where applicable,
successful JCE verification ([§FS-002-security-providers.design.4.3](security-providers.md#3-jce-verification)).

### .3: Prohibited Run-Time Access and Errors

#### .1: Unregistered Providers

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

#### .2: Standard Unavailable Results

When an explicit lookup names a configured provider whose registration is missing or whose condition
is unmet, [`Security.getProvider(name)`][security] and
[`Signature.getInstance(algorithm, providerName)`][signature] throw
[`MissingReflectionRegistrationError`][missing-reflection-registration-error]
([§FS-002-security-providers.design.3.3](security-providers.md#3-missing-reflection-registration)).
A provider omitted for this reason is not treated as an unknown provider.

For a name that does not identify an available provider or an omitted configured provider,
[`Security.getProvider(name)`][security] returns `null`, and
[`Signature.getInstance(algorithm, providerName)`][signature] throws
[`NoSuchProviderException`][no-such-provider].
Removing a registered provider from the run-time list also gives these standard unavailable results.

[`Signature.getInstance(missingAlgorithm)`][signature] throws
[`NoSuchAlgorithmException`][no-such-algorithm] when no active provider supplies the algorithm.
The overloads that take a provider name or object throw the same exception when that provider
is available but lacks the requested algorithm.
A provider-object overload does not throw merely because the provider is absent from the list.

#### .3: Missing Reflection Registration

An explicit lookup of a configured provider fails with
[`MissingReflectionRegistrationError`][missing-reflection-registration-error] when the provider lacks
qualifying reflection metadata or its registration condition is unmet.
This applies to [`Security.getProvider(String)`][security] and JCA factories given a provider name,
before they return a provider or service.
This diagnostic applies only with explicit provider registration enabled. Legacy modes preserve
their earlier lookup results and exceptions for providers omitted from the Native Image provider
list.
The ordinary reflection diagnostic names the provider implementation class in binary form and tells
the user to register invocation of the JDK-selected constructor or factory in _reachability-metadata.json_.
There is no provider-specific metadata format or provider-specific missing-metadata error type.
The diagnostic lookup is side-effect-free: it does not construct or cache the omitted provider and
does not change subsequent enumeration, duplicate detection, insertion, or factory behavior.
Direct use of an existing provider object does not satisfy a configured entry's registration
requirement or suppress its missing-registration diagnostic.
An unconfigured application-supplied provider remains absent from name-based lookup until the
application inserts it ([§FS-002-security-providers.design.4.2](security-providers.md#2-programmatic-provider-list-changes)).
The diagnostic resolver uses only recorded configured entries; a built-in alias alone does not
establish that a provider was configured or is available on the current platform.
An unconfigured built-in name and an arbitrary unknown name return `null`.
The resolver does not interpret an arbitrary dotted provider name as an implementation class name.
The same diagnostic applies in exact-metadata and compatibility reporting modes.
If reflective construction is attempted,
[`MissingReflectionRegistrationError`][missing-reflection-registration-error] remains the top-level
failure and is not wrapped as a provider-configuration error.

### .4: Programmatically Supplied Providers

#### .1: Provider-Object Factory Calls

After application code constructs a provider, a call such as
[`Signature.getInstance(algorithm, provider)`][signature] does not require reflection metadata for
the provider class and does not add the provider to the run-time list.
The call succeeds when the service implementation and its required construction metadata are
retained and [§FS-002-security-providers.design.4.3](security-providers.md#3-jce-verification) permits use.
A missing implementation produces [`NoSuchAlgorithmException`][no-such-algorithm]; a JCE
verification failure follows [§FS-002-security-providers.design.4.3](security-providers.md#3-jce-verification).

A `Provider.Service` object retained from build time follows the same run-time metadata requirements
as a service looked up at run time.
Populating its implementation or constructor cache during image building does not register the
implementation or its constructor and must not prevent image generation.

#### .2: Programmatic Provider-List Changes

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

#### .3: JCE Verification

Native Image records JCE verification outcomes at **build time** for each included provider class.
For configured providers available in the build-time provider list, it invokes the build JDK's
`JceSecurity.getVerificationResult` and retains success or the verification exception by provider
class.
The build JDK determines the authentication policy, including whether it requires a trusted JCE
signature.
Application-supplied-only provider classes follow the successful-recognition rule below.
At run time, JCE factories consult the recorded outcome instead of verifying the provider JAR again.
Direct construction of a provider, including through reflection, does not itself enforce this JCE
verification outcome.

[`Cipher`][cipher], [`Mac`][mac], and other factories that require JCE provider verification may
construct a service only when Native Image recorded successful build-time verification for that
provider class.
A failed outcome makes the factory throw [`SecurityException`][security-exception]; a missing
outcome produces the missing-registration failure from
[§FS-002-security-providers.design.3.3](security-providers.md#3-missing-reflection-registration).
In either case, the factory fails before it constructs the service implementation.
This rule applies equally to a JDK-managed provider and an application-supplied provider; the latter
still requires no provider-class reflection metadata.
Instantiation reachability records successful verification for an application-supplied-only
provider class independently of any configured-list instance of the same class. In particular, a
verification failure for an omitted configured instance does not prevent provider-object use of an
application-created instance.
Each failed verification outcome retains its original registration condition when registrations for
the same class are merged; unconditional application verification does not activate a conditional
configured-instance failure.
The implementation-specific recognition rule is [§AR-004-security-providers.requirements.3](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/jca/SecurityServicesFeature.java).

### .5: Tracing Metadata

#### .1: Provider and Service Coverage

The Tracing Agent records the reflection operations needed to repeat provider and service
construction, including attempts that the JDK later rejects.
For a cached service, it records the cached constructor, including a no-argument constructor
selected after a parameterized-constructor probe failed.

Successful JDK-managed acquisition also records the selected construction path when the agent can
identify it, even for a built-in provider cached before tracing starts.
It does not register other providers merely visited during the search, though actual reflective
construction attempts are still traced.
With explicit provider registration enabled, rebuilding with only this metadata must reproduce
the observed factory selection after traced construction or supported cached acquisition.

Passing an existing provider to [`Security.addProvider`][security],
[`Security.insertProvideterAt`][security], or [`Security.removeProvider`][security] does not record
its class for reflection.
Neither does later selecting that application-supplied object, even if a cached lookup or
enumeration originally returned it as a built-in provider.

Ordinary reflection rules cover application [`Class.forName`][class] calls, reflective constructor
calls, and service construction through factories or
[`Provider.Service.newInstance(Object)`][provider-service].
Security operations through JDK modules are attributed to the first non-JDK caller, including calls
through non-security JDK classes and platform provider modules.

#### .2: Observational Transparency

Tracing preserves return values, exceptions, provider order, and application-visible side effects
of provider lookups, factory calls, and provider-list changes.
The agent records reflection and resource access inside the actual call without repeating it or
initializing an unselected provider or service.

### .6: Transition to the Future Defaults

[§FS-002-security-providers.design.1](security-providers.md#1-registration-semantics) through [§FS-002-security-providers.design.5](security-providers.md#5-tracing-metadata) describe the planned defaults.
Until then, you can enable them with:

- `--future-defaults=metadata-security-provider-registration`: requires provider registration through
  metadata and also enables run-time provider-list initialization.
- `--future-defaults=run-time-initialize-security-providers`: initializes the provider list at run time
  while keeping service-driven inclusion unless explicit registration is also enabled.

With configuration, metadata, and mode fixed, reachable but unexecuted application code does not
change provider availability or order, except for the documented `SecureRandom` rule.
Changing modes can change availability: constructor or factory invocation metadata can register a
configured provider, while type-only and query-only metadata cannot include its full service catalog.
To migrate, register the JDK-selected constructor or factory for each intended provider and remove
unwanted providers from the security configuration.
Returning to a legacy mode restores service-driven inclusion and may expose additional providers.

#### .1: Run-Time Provider-List Initialization

With `--future-defaults=run-time-initialize-security-providers`, Native Image initializes the provider
list when [`Security`][security] first needs it at run time.
The list contains only registered entries from the build-time `security.provider.<n>` configuration,
in their original order.
[`Security.getProvider(String)`][security] uses the registered construction path and reports an
actionable error if construction fails or returns the wrong implementation.

All configured paths, including those found through service descriptors, use the JDK recursion guard
and retry limit.
Recursive loading returns no provider for that attempt; repeated failures stop at the retry limit.
For layered images, every layer must use the same initial security properties.
The builder rejects changes and names the affected keys.

#### .2: Provider Service Descriptors

A class-path _META-INF/services/java.security.Provider_ descriptor does not itself register providers.
[`ServiceLoader.load(Provider.class)`][service-loader] returns a described provider only when its
reflection metadata is present and reachable `ServiceLoader` access retains the descriptor.
Discovery preserves declaration order and does not instantiate unregistered providers.

`-H:ServiceLoaderFeatureExcludeServices=java.security.Provider` disables automatic descriptor retention
and removes provider entries from the services catalog in every mode.
Reflection metadata does not override this exclusion.

Explicit-registration mode allows queries for an absent descriptor without resource metadata.
Legacy modes use ordinary resource-query rules; run-time provider initialization can itself retain
the descriptor and make it visible without explicit resource metadata.
In every mode, a descriptor retained by an independent reachable use remains visible.

An extension layer cannot add providers to a descriptor retained by an earlier layer.
The builder rejects such additions because service files cannot yet be extended across image layers.

#### .3: Earlier Service-Driven Inclusion Behavior

Without `--future-defaults=metadata-security-provider-registration`, reachability of a supported JCA
factory or security facade retains configured services of the corresponding type even when their
provider classes lack reflection metadata.
This includes services of JDK-internal providers used by the reached service, such as the TLS
hybrid KEM provider when the selected JDK supplies it.
For example, a reachable [`Signature.getInstance(runtimeAlgorithm)`][signature] can make configured
[`Signature`][signature] services available, but the run-time value cannot retain another service
type.
Likewise, reachability of [`GSSManager.getInstance().getMechs()`][gss-manager],
[`Sasl.createSaslClient(...)`][sasl], or [`Sasl.createSaslServer(...)`][sasl] can retain the configured
GSS mechanism, SASL client-factory, or SASL server-factory services, respectively.
With `--future-defaults=metadata-security-provider-registration`, the same call can select only
providers registered under [§FS-002-security-providers.design.1.1](security-providers.md#1-qualifying-reflection-metadata), except for the
[`SecureRandom`][secure-random] rule in [§FS-002-security-providers.design.1.5](security-providers.md#5-securerandom-providers).
Neither mode changes the programmatic rules in [§FS-002-security-providers.design.4](security-providers.md#4-programmatically-supplied-providers).

#### .4: Earlier Build-Time Initialization Behavior

Without `--future-defaults=run-time-initialize-security-providers`, Native Image retains the earlier
behavior that initializes the configured provider list during the image build.
Provider-constructor side effects therefore occur during the build, and the executable starts with
the resulting provider-list state.

#### .5: Deprecated Additional-Provider Option

`-H:AdditionalSecurityProviders=<provider-class>` remains accepted with a deprecation warning during
the transition.
In explicit registration mode, each named provider class is an unconditional, complete
provider-registration signal with the same provider and catalog effects as unconditional qualifying
reflection metadata.
Without explicit registration, the option preserves the earlier option-driven inclusion behavior
without requiring another registration signal or a future-default option.
In legacy modes, the option retains provider eligibility and its construction path without retaining
the complete service catalog.
In legacy run-time provider-list initialization mode, naming a provider does not by itself construct
the provider during the image build.
The JDK constructs a configured provider through its retained path at run time, and only services
retained through legacy service-driven inclusion or independent metadata are available.
In legacy build-time provider-list initialization mode, [§FS-002-security-providers.design.6.4](security-providers.md#4-earlier-build-time-initialization-behavior)
still permits configured-provider construction during the image build.
The option does not install an otherwise unconfigured provider and does not change provider order.
Replacing it with ordinary reflection metadata requires explicit provider registration until that
behavior becomes the default.

## requirements: Requirements

The requirements in this chapter define the acceptance criteria for this specification.
Each states a required property and its domain.
Universal implementation obligations are stated by [§AR-004-security-providers.requirements](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/jca/SecurityServicesFeature.java).

### requirements.1: Construction Parity

**Requirement.** For every provider the JDK can construct on HotSpot through a public no-argument
constructor of a public, concrete [`Provider`][provider] subtype, or a public static no-argument
[`provider()`][service-loader] method of a public service provider class in an explicit module,
ordinary reflection metadata must let a native executable acquire the same provider through the
same JDK API and use its services.
Native Image must require neither a provider-specific option nor an application source change.
This is the provider-specific form of [§FD-001-hotspot-compatible-semantics-by-default.2](decisions/hotspot-compatible-semantics-by-default.md#2-decision).
The transition options in [§FS-002-security-providers.design.6](security-providers.md#6-transition-to-the-future-defaults) are the only temporary build
options permitted.

**Domain.** A public provider constructor on the class path; a provider method in an explicit module;
a provider method whose implementation differs from its service provider class; a provider method
returning a non-public implementation; a provider configured more than once; a configured provider
whose `configure` result changes its name or implementation class; ordered same-name descriptors;
recursive construction; and repeated failing construction.

### requirements.2: Metadata Closure

**Requirement.** The only signals that register a JDK-managed provider are the ordinary reflection
signals in [§FS-002-security-providers.design.1.1](security-providers.md#1-qualifying-reflection-metadata) and the platform signal in
[§FS-002-security-providers.design.1.5](security-providers.md#5-securerandom-providers).
Native Image must introduce no provider-specific metadata category, configuration file, permanent
option, or missing-metadata exception.
Instantiation reachability used to verify an application-supplied provider under
[§FS-002-security-providers.design.4.3](security-providers.md#3-jce-verification) is not a provider-registration signal and must not grant JDK-managed
acquisition or retain services.

**Domain.** The reachability-metadata schema, Native Image option list, provider-path exception types,
and the architecture chokepoints in [§AR-004-security-providers.requirements](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/jca/SecurityServicesFeature.java).

### requirements.3: Diagnostic Sufficiency

**Requirement.** A failure caused by missing provider registration must identify the provider
implementation class by name and supply ordinary reflection metadata that makes the same
operation succeed after one rebuild.
For a provider with a supported construction path, the same entry must enable the provider and its
services.

**Witness.** The metadata entry printed in the diagnostic.

**Domain.** Exact-metadata and compatibility reporting, before and after provider enumeration and
provider-list mutation.

### requirements.4: No Exposure of an Unregistered Provider

**Requirement.** An unregistered provider that the JDK creates or discovers must not be returned,
reported, or selected through configuration, [`ServiceLoader`][service-loader], factories, or
default selection.
A provider must not supply a JCE-verified service unless Native Image recorded a successful
build-time verification outcome for its class.
Failure must precede application receipt of any provider, service, engine, or facade representing
the unavailable provider.

**Domain.** JDK paths that return, report, or select a provider or service, including configuration,
[`ServiceLoader`][service-loader], factories, and default selection, with unenumerated paths covered
by [§AR-004-security-providers.requirements.2](../../src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/jca/SecurityServicesFeature.java).

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
[§FS-002-security-providers.design.1.5](security-providers.md#5-securerandom-providers).

### requirements.7: Transition Compatibility

**Requirement.** Every supported combination in [§FS-002-security-providers.design.6](security-providers.md#6-transition-to-the-future-defaults) must be
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
After activation, the provider and all effects in [§FS-002-security-providers.design.1.3](security-providers.md#3-registration-effects) must become
observable independent of when the provider list was initialized.

**Domain.** Each signal shape in [§FS-002-security-providers.design.1.1](security-providers.md#1-qualifying-reflection-metadata), combined with an
unconditional entry, a build-time condition, and an unsatisfied and satisfied run-time condition,
observed across JDK paths that return, report, or select a provider or service through
configuration, [`ServiceLoader`][service-loader], factories, or default selection.

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
