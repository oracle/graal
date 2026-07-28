# DF-complete-security-provider-registration: Register Providers as Complete Service Units

## 1. Context

### 1.1 A Provider Is a Catalog Before It Is a Factory

A Java Cryptography Architecture (JCA) provider is not merely a class that implements one
cryptographic operation.
It is a catalog of named capabilities.
When a provider is constructed, it publishes `Provider.Service` descriptors for message digests,
signatures, key stores, secure random number generators, and any other service types that it
supports.
Each descriptor names a service type, an algorithm, an implementation class, aliases, and
attributes such as supported key classes.

The JDK exposes that catalog through several related APIs.
`Security.getProviders()` exposes the providers themselves.
`Provider.getService()` and `Provider.getServices()` expose their service descriptors.
`Security.getAlgorithms()` and the filtering overloads of `Security.getProviders()` derive
algorithm availability from those descriptors.
JCA factories such as `MessageDigest.getInstance()` and `Signature.getInstance()` search the same
catalog before constructing an engine.

Service discovery and service construction are nevertheless different operations.
Looking up a `Provider.Service` is ordinarily a map lookup in the provider.
It does not reflectively inspect the service implementation.
Construction happens later, usually when `Provider.Service.newInstance()` loads the named
implementation class and invokes an applicable constructor.
A provider can also supply a specialized `Provider.Service` that overrides this construction path
and creates the implementation directly.

This separation is unremarkable on the JVM because all provider implementation classes remain
available on the class path or module path.
It becomes important in Native Image because a closed-world build must decide which classes and
reflective constructors to retain.
A provider descriptor can survive in a native executable even when the implementation that it names
does not, unless Native Image deliberately keeps the two together.

### 1.2 Two Levels of Dynamic Construction

A JDK-managed provider can itself be constructed dynamically.
The JDK can read its class name from security properties, discover it through a service-provider
descriptor, or select it through an internal default path.
The provider therefore needs a supported reflective construction path before it can participate in
the run-time provider list.

Once constructed, the provider introduces a second level of dynamic construction.
Its service implementation names come from the provider catalog rather than ordinary Java call
edges.
Native Image must retain the applicable implementation constructors and any auxiliary metadata
needed by the engine.
For example, some engines accept a constructor parameter, some services declare supported key
classes, and JKS and X.509 support require additional implementation metadata.

The provider registration and service availability requirements are specified by
[§FS-security-providers.1.1](../security-providers.md#11-registered-providers-and-services)
and [§FS-security-providers.2.3](../security-providers.md#23-registration-effects).
The design question is where to place the application-controlled boundary between these two
levels.
Registering only the provider is compact but requires Native Image to complete its service catalog.
Requiring registration of the provider and every service appears more granular but creates a
partially registered object unless every discovery API applies exactly the same filter.

### 1.3 Reflection Metadata Does Not Describe a JCA Service

Ordinary reflection metadata identifies Java classes, constructors, methods, and fields.
It does not identify a `(provider, service type, algorithm)` tuple.
That distinction matters because a Java class is not always equivalent to one JCA service.

Several algorithms can share one implementation class.
Aliases can name the same service through different algorithm strings.
A provider can use one service class with constructor parameters to produce several variants.
Conversely, a specialized `Provider.Service` can construct an implementation without using the
implementation constructor that ordinary reflection metadata would appear to register.

Treating an implementation constructor as an individual-service signal would therefore be
imprecise.
Registering one constructor could enable several service entries, while failing to register a
constructor would not necessarily disable a service whose provider overrides `newInstance()`.
A separate service identity and a run-time allowlist would still be needed to define exactly what
the executable exposes.

## 2. Decision

### 2.1 The Provider Is the Registration Unit

Native Image treats a registered provider as one complete service-registration unit.
Qualifying reflection metadata for the provider is the application-controlled registration signal.
The application does not enumerate reflection metadata for every implementation class named by that
provider.
This registration boundary follows [§DF-standard-jca-semantics.2](standard-jca-semantics.md#2-decision).

At build time, Native Image constructs the registered provider and reads the provider's own catalog.
It retains every valid service whose implementation class it can resolve.
For each retained service, Native Image registers the reflective construction metadata and
auxiliary metadata required to use that implementation.
The provider remains the authority for its service types, algorithms, aliases, attributes, and
implementation mappings.

The same rule applies when Native Image registers a provider through a platform rule rather than
application metadata.
Such a rule changes who supplies the provider-registration signal, but it does not create a
different kind of provider or a smaller service catalog.

### 2.2 One Catalog Must Answer Every Availability Question

At run time, provider discovery and service acquisition observe one consistent catalog.
A service reported as available can be selected and constructed.
An omitted provider cannot leak service names into algorithm discovery, and an included provider
does not advertise a supported, resolvable service whose construction metadata was intentionally
discarded.

This invariant applies whether an application reaches a service through a named JCA factory, a
factory overload that accepts a `Provider` object, direct `Provider.Service` access, provider
enumeration, or a JDK default path.
It also avoids making success depend on whether the same service was reached through a public engine
factory or an internal JDK facade.

### 2.3 Reachability Does Not Subdivide an Explicitly Registered Provider

Reachability still determines whether application code and JDK paths are present in an executable.
It does not, however, redefine the contents of a provider after that provider has been explicitly
registered.
A run-time algorithm string can select any service that belongs to the registered provider without
requiring Native Image to predict that string at build time.

Reachability of a JCA factory can continue to drive service-type registration only under the
compatibility behavior in
[§FS-security-providers.7.3](../security-providers.md#73-earlier-service-driven-inclusion-behavior).
When explicit provider registration is enabled, reaching a service factory does not register an
otherwise unregistered provider and does not select a subset of an already registered provider.

## 3. Why Completeness Is the Stable Boundary

### 3.1 Registration Must Mean More Than Provider Construction

Registering only the provider constructor would answer one narrow question: whether the JDK can
create the provider object.
It would not answer whether that object tells the truth about its capabilities.
The object would still populate its normal service registry, and all discovery APIs would see that
registry before any service implementation constructor was invoked.

Deferring the real availability check until `Provider.Service.newInstance()` would move failures
away from the acquisition boundary.
An application could discover an algorithm, select its provider, retrieve its service descriptor,
and only then receive a missing-reflection failure.
The same algorithm might instead produce `NoSuchAlgorithmException` through another entry point.
The result would depend on how far a particular JDK path progressed before it encountered the
missing constructor, rather than on one definition of service availability.

The specification rejects this partial exposure in
[§FS-security-providers.4.1](../security-providers.md#41-unregistered-providers).
Completing the provider at build time makes provider registration a useful promise instead of merely
permission to allocate the outer object.

### 3.2 The Provider Already Owns the Necessary Information

The provider catalog is the most accurate source of service information.
It contains provider-specific services, aliases, attributes, and implementation mappings that a
generic Native Image configuration format should not duplicate.
Reading this catalog at build time also allows Native Image to apply existing service-specific
handling in one place.

This approach keeps reflection metadata stable when a provider changes an internal implementation
class without changing its public algorithms.
Users register the provider that they intend to make available.
They do not need to know which internal class implements SHA-256 in a particular JDK update or which
constructor a third-party provider uses for a cipher SPI.

### 3.3 The Cost Is Paid at an Intentional Boundary

Complete registration can retain services that a particular execution never selects.
That is a real size cost, but it is attached to an explicit choice to include the provider.
An executable that does not register or otherwise require the provider does not pay that cost.

The alternative is not cost-free precision.
Precise partial providers require a new service identity, filtering rules, run-time checks, and
compatibility behavior for every route through the JCA.
The resulting implementation would be larger and harder to reason about even when the retained
application code became somewhat smaller.

Provider completeness therefore chooses a coarse but stable boundary.
It accepts a measurable inclusion cost in exchange for a single availability model and ordinary JCA
behavior after the executable is built.

## 4. Rejected Alternatives

### 4.1 Keep Every Descriptor and Fail During Construction

One possible two-stage model would register the provider first and treat reflection metadata for
each implementation constructor as a second permission.
The full provider catalog would remain visible, but `Provider.Service.newInstance()` would fail when
the selected implementation lacked metadata.

This model needs few Native Image substitutions because the existing reflection machinery can
report the missing constructor.
Its simplicity is deceptive.
`Provider.getService()` would return a descriptor that cannot provide a service.
`Provider.getServices()` and `Security.getAlgorithms()` would advertise unavailable algorithms.
Provider filtering could select a provider that later fails to instantiate its advertised
implementation.

The failure would also expose Native Image mechanics through APIs that normally describe JCA
availability.
Exact reachability metadata could produce a missing-registration error, while a different factory
path could translate the same underlying absence into a standard JCA exception.
This alternative was rejected because it preserves the catalog structurally but breaks its meaning.

### 4.2 Filter Services Whose Implementations Lack Metadata

A stricter two-stage model would hide every service whose implementation constructor was not
registered.
The provider could remain in the provider list while exposing a reduced, internally consistent
catalog.
This is the strongest alternative in principle, but it requires Native Image to own a second service
registry beside the provider's registry.

The filter would have to cover `Provider.getService()`, `Provider.getServices()`,
`Security.getAlgorithms()`, both forms of `Security.getProviders()` filtering, all JCA factories,
security-service facades, aliases, default and fallback paths, and direct service construction.
It would have to handle providers initialized at build time and providers initialized at run time.
It would also have to define what happens when application code constructs a provider, calls
`Security.addProvider()`, or supplies that provider object directly to a factory.

Filtering only the public lookup methods would not be enough.
Some JDK paths use specialized defaults or provider-specific service construction.
A provider can override service behavior, and a custom provider can mutate its catalog after
construction.
Keeping these paths consistent would require a canonical run-time allowlist and broad interception
of JDK and provider behavior.

The metadata signal would remain ambiguous even after that work.
An implementation class shared by several algorithms does not say which service entries to expose.
Constructor metadata does not describe aliases or a specialized `newInstance()` override.
This alternative was rejected because ordinary reflection metadata cannot precisely specify its
policy, and enforcing the policy would add substantial run-time machinery.

### 4.3 Retain Only Reachable Service Types

The earlier compatibility behavior registers implementations by reachable engine type.
For example, reachability of `MessageDigest.getInstance()` can retain message digest services
without retaining key store services.
This is an effective closed-world optimization when factory reachability is the intended inclusion
signal.

It is not a complete definition of an explicitly registered provider.
Applications can call `Provider.getService()` with a run-time service type and algorithm.
They can enumerate services and select one according to configuration that Native Image cannot
evaluate at build time.
JDK facades and defaults can also acquire services without following the public engine-factory
shape.

Using the shared methods `Provider.getService()`, `Provider.getServices()`, or
`Provider.Service.newInstance()` as conservative triggers does not restore useful precision.
Nearly every JCA factory eventually reaches `Provider.getService()` and
`Provider.Service.newInstance()`.
The first reachable engine would therefore retain every service of every included provider.
The optimization would distinguish an executable with no security use from one with some security
use, but it would no longer distinguish message digests from key stores or signatures.

This alternative remains appropriate as transition compatibility behavior.
It was rejected as the semantics of explicit provider registration because it makes the provider's
contents depend on incidental call-graph shapes and does not cover dynamic service access.

### 4.4 Enumerate Every Service Implementation in Reflection Metadata

Another model would keep ordinary reflection metadata but require a provider entry followed by
entries for every service implementation constructor.
A complete provider registration would then be a long expansion of provider internals.

This format would be verbose and fragile.
Provider upgrades could replace implementation classes or constructors without changing any public
algorithm.
Metadata copied between JDK versions could silently produce a partial provider.
Third-party provider users would need to inspect internal service mappings that the provider already
publishes programmatically.

It would also fail to provide true per-service precision because multiple service entries can share
one implementation class.
The metadata would describe Java implementation reachability, not the service catalog that users
intend to expose.
This alternative was rejected because it transfers provider-internal maintenance to application
configuration without establishing a coherent service identity.

### 4.5 Introduce Security-Provider-Specific Metadata

A dedicated metadata category could directly name a provider and either select all services or list
service type and algorithm pairs.
Unlike raw reflection metadata, such a format could express the intended JCA-level identity.

The format would still duplicate information already owned by the provider.
It would need rules for aliases, provider version changes, unknown custom service types, shared
implementations, specialized service construction, and services added programmatically.
Selective entries would still require the run-time filtering model described in section 4.2.
The Tracing Agent and missing-registration diagnostics would also need a new metadata vocabulary.

A new metadata category is justified only if selective provider catalogs become a product feature
rather than an implementation optimization.
No size evidence currently establishes that need.
This alternative was rejected for the present design because provider reflection metadata already
supplies a stable registration signal and complete registration needs no second run-time catalog.

## 5. Consequences

Provider reflection metadata remains concise and independent of provider implementation details.
A supported provider constructor or `provider()` method is enough to request the provider.
Native Image derives service implementation metadata from the provider's authoritative catalog.

Registering a provider can increase executable size because Native Image retains every resolvable
valid service and its construction metadata.
The size effect can be larger for providers that implement many unrelated engines.
This cost is visible and attributable to provider registration rather than to incidental
reachability of a shared JCA helper method.

Users receive a simpler failure model.
An unregistered provider is unavailable.
A registered provider exposes a complete supported catalog.
Factory selection, provider enumeration, service enumeration, and direct service access do not
disagree because Native Image pruned an implementation behind an advertised descriptor.

This decision favors consistent JCA behavior over service-level pruning.
Service-level pruning can be reconsidered if measurements show a material executable-size
regression.
Any future design must define a stable service identity and preserve one consistent catalog across
all provider discovery and service acquisition paths.
