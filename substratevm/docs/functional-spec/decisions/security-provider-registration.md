# AR-009-security-provider-registration: Register Security Providers Through Ordinary Reflection Metadata

## 1. Context

### 1.1 Dynamic Provider Configuration Meets the Closed World

The Java Cryptography Architecture (JCA) discovers and constructs security providers dynamically.
The JDK can read provider class names from security properties, discover providers through
service-provider descriptors, and select providers through standard factory and fallback paths.
On HotSpot, these operations can load any available class without an advance declaration.

Native Image uses closed-world analysis and must retain every class and reflective operation that
can occur at run time.
This creates an inclusion boundary, but it does not require a second, security-provider-specific
configuration model: ordinary reflection metadata already describes access to provider types,
constructors, and qualifying `provider()` methods.
Provider-specific command-line options would expose Native Image implementation details in the
application's security configuration, and they would make otherwise standard JCA behavior depend on
how the executable was built rather than on the application's Java configuration.
The registration requirements and standard run-time behavior are specified by
[§FS-002-security-providers.1](../security-providers.md#1-provider-reflection-registration) and
[§FS-002-security-providers.3](../security-providers.md#3-permitted-run-time-access).

### 1.2 Registration and Construction Are Different Questions

Native Image must answer two independent questions about a provider implementation class: does the
application intend this provider to be present in the executable (*registration*), and can the JDK
create an instance of it without the application supplying one (*construction*)?

JCA answers only the construction question, by delegating to the `java.util.ServiceLoader`
instantiation contract: a provider in an explicit module can declare a public static no-argument
`provider` method whose return type is assignable to the service type; otherwise the provider is
instantiated through a public no-argument constructor on a public class.
`sun.security.jca.ProviderConfig` reaches a configured provider through that mechanism and falls
back to a legacy class-name load that agrees with the same rule; no JDK path constructs a
JDK-managed provider through a non-public constructor.
Both steps of the `provider()` path are reflective, so ordinary reflection metadata is not merely a
convenient signal for that path: it is the metadata the JDK's own call path consumes.

The registration question is not a JCA concept at all.
On HotSpot every provider class on the class path is loadable, so "is this provider intended to be
present" exists only because Native Image builds a closed world.
The two questions are genuinely independent:
[§FS-002-security-providers.5](../security-providers.md#5-programmatically-supplied-providers) lets
an application construct a provider itself and pass it to a JCA factory, so a provider class
without a no-argument constructor must still count as registered, while a constructible provider
class can be registered without the application ever naming the type.
The metadata mechanisms also make the two signals asymmetric: every `reachability-metadata.json`
type entry grants type access, so a constructor entry adds nothing there, but
`RuntimeReflection.register(Executable)` — used by features and library-supplied build plugins —
registers a member without marking the declaring type as accessed.

### 1.3 A Provider Is a Catalog That Reflection Metadata Cannot Describe

A JCA provider is a catalog of named capabilities, not merely a class that implements one
cryptographic operation.
When constructed, it publishes `Provider.Service` descriptors that name a service type, an
algorithm, an implementation class, aliases, and attributes.
Provider discovery (`Security.getProviders`, `Security.getAlgorithms`, `Provider.getServices`) and
JCA factories consult that catalog, but discovery and construction are different operations:
looking up a `Provider.Service` is a map lookup, while construction reflectively loads the named
implementation class later, usually inside `Provider.Service.newInstance()`.
On the JVM this separation is unremarkable because every implementation class remains available.
In a closed-world build, a service descriptor can survive in the executable while the
implementation it names does not, unless Native Image deliberately keeps the two together.

Ordinary reflection metadata identifies Java classes and members, not
`(provider, service type, algorithm)` tuples.
Several algorithms can share one implementation class, aliases can name one service through
different algorithm strings, and a specialized `Provider.Service` can construct its implementation
without the constructor that metadata would appear to register.
A per-service reflection signal would therefore be imprecise in both directions, and defining
exactly what the executable exposes would still need a separate service identity and a run-time
allowlist.

### 1.4 Conditions on the Signal

A reachability metadata entry can carry a condition.
The current metadata format, `reachability-metadata.json`, expresses exactly one condition kind:
`"condition": {"typeReached": ...}`, a run-time-checked condition under which the entry behaves as
absent until the condition type is reached at run time; the earlier configuration files express
`typeReachable`, a build-time-checked condition discharged entirely during analysis.
A provider signal that ignored its entry's condition would give two answers to one registration
question: `Class.forName` on the provider class would throw a missing-registration error until the
condition type is reached, while a JCA factory would select the provider it had just refused to
load.

Provider registration is also unlike ordinary element registration in that it causes derived
effects: it retains the provider's construction path and complete service catalog, admits the
provider to the run-time provider list, and establishes a JCE verification outcome
([§FS-002-security-providers.2.3](../security-providers.md#23-registration-effects),
[§FS-002-security-providers.5.3](../security-providers.md#53-jce-verification)).
A condition must gate those effects coherently, or the guard is one-sided.
Two properties of the run-time machinery constrain where a run-time-checked condition can be
evaluated: the JDK builds its provider list once and caches it, so a condition can become satisfied
before or after that construction, and a provider-object factory call never consults the list, so
an evaluation point tied to list construction cannot cover it.

### 1.5 The Platform's Own Provider Dependencies

The JDK itself introduces provider dependencies.
`SecureRandom` constructors and factories select JDK-managed providers, so requiring application
reflection metadata for those providers would make a commonly used JDK facility fail for reasons
that expose provider implementation details.
Native Image also uses `SecureRandom` internally to seed runtime-compilation hardening (runtime
constant blinding and code-offset randomization); registering that internal source in every
executable would make `SecureRandom` appear application-reachable and retain the complete default
provider even in an otherwise empty executable.

## 2. Decision

### 2.1 Ordinary Reflection Metadata Is the Registration Signal

Native Image uses ordinary reflection metadata as the application-controlled registration signal
for a security provider.
Applications select and configure providers through standard Java APIs, security properties, and
service descriptors; they do not need a provider-specific Native Image command-line option,
metadata category, or exception type.
Tracing and missing-registration diagnostics use ordinary reachability metadata, and options that
select compatibility behavior during the transition to the planned defaults do not become
permanent provider-registration requirements.
When the platform must preserve an implicit JDK dependency, it supplies an equivalent registration
signal ([§2.5](#25-the-securerandom-platform-signal)) instead of requiring the application to
identify the provider implementation.

### 2.2 Type Access Registers, and Construction Follows the JDK

Type access is the provider registration signal: registering access to the provider implementation
type registers the provider implementation class.
Access to a declared no-argument constructor or to a qualifying `provider()` method also registers
the provider implementation class that the construction path resolves.
This is a subsumption rule, not a third independent policy: a build plugin that registers only a
construction path has stated a stronger intent than type access, and Native Image must not treat
that provider as unregistered while simultaneously being able to construct it.

Construction is a strictly stronger property layered on registration, bounded by what the JDK does
rather than by what Native Image could reach.
Native Image constructs a registered provider only through the `java.util.ServiceLoader` paths of
[§1.2](#12-registration-and-construction-are-different-questions), so a provider that the JDK can
construct on HotSpot remains constructible in a native executable, and no other.
Type access alone never makes a provider JDK-constructible, so a type-only entry registers an
application-supplied provider without promising that the JDK can create it.
The two boundaries produce different failures: an unregistered provider is absent, while a
registered provider that is not JDK-constructible is usable only through the narrower
application-supplied operations.

### 2.3 The Provider Is the Registration Unit

Native Image treats a registered provider as one complete service-registration unit.
At build time, it constructs the registered provider, reads the provider's own catalog, and
retains every valid service whose implementation class it can resolve, together with the
reflective construction metadata and auxiliary metadata required to use each implementation.
The application does not enumerate reflection metadata for the implementation classes the provider
names; the provider remains the authority for its service types, algorithms, aliases, attributes,
and implementation mappings.
The same completeness applies when a platform rule rather than application metadata supplies the
registration signal.

At run time, one catalog answers every availability question.
A service reported as available can be selected and constructed, an omitted provider does not leak
service names into algorithm discovery, and an included provider does not advertise a supported,
resolvable service whose construction metadata was intentionally discarded — whether the
application reaches the service through a named JCA factory, a factory overload that accepts a
`Provider` object, direct `Provider.Service` access, provider enumeration, or a JDK default path.

Reachability does not subdivide an explicitly registered provider.
A run-time algorithm string can select any service that belongs to the registered provider without
requiring Native Image to predict that string at build time; reachability of a JCA factory drives
service-type registration only under the compatibility behavior of
[§FS-002-security-providers.7.3](../security-providers.md#73-earlier-service-driven-inclusion-behavior).

### 2.4 Conditions Gate Registration and Every Derived Effect

A qualifying registration signal carries the condition of the metadata entry that registers it.
A signal whose condition type is not reachable at build time contributes no registration, so both
condition kinds gate inclusion identically during analysis.

A registration is *active* when some signal that produced it is unconditional or has a satisfied
run-time-checked condition, and a provider whose registration is not active is an unregistered
provider for every run-time rule.
Activation is evaluated at the single acquisition-boundary filter that
[§REQ-002-security-providers.9.2](../requirements/security-providers.md#92-the-acquisition-boundary)
names, so the check is made once for the enumerated acquisition paths and for the paths no test
enumerates.
Satisfied conditions stay satisfied, so activation is monotonic and the observable provider list
only grows toward the filtered configured list.
The run-time provider list keeps a configured provider with an inactive registration unconstructed
and unobservable; the provider becomes observable at its configured position on the first
acquisition after activation, independent of when the list was initialized.
Every derived effect carries the union of the conditions of the signals that caused it: the
retained construction path, the service metadata, and the JCE verification outcome are guarded
exactly as the registration itself, and are never widened to unconditional.

### 2.5 The SecureRandom Platform Signal

When a `SecureRandom` acquisition path is reachable, Native Image registers every configured
provider that declares a `SecureRandom` service.
The acquisition path is a platform-owned provider-registration signal, so the application does not
need to supply reflection metadata for these implicit JDK dependencies.
The signal carries no metadata condition, so its registrations are always active, and each
provider is registered completely under [§2.3](#23-the-provider-is-the-registration-unit).

This is not service-driven inclusion: the platform supplies a registration signal, the ordinary
complete-provider semantics apply after registration, and reachability of other JCA factories does
not register their providers when explicit provider registration is enabled.
This bounded registration condition follows
[§AR-006-reachability-independent-runtime-semantics.2](reachability-independent-runtime-semantics.md#2-decision).

Native Image registers its internal secure runtime-randomness singleton only in executables that
include runtime compilation, which is the only subsystem that consumes that singleton.
Ordinary executables therefore do not retain the default provider merely because Native Image has
an optional internal hardening mechanism.

## 3. Rejected Alternatives

### 3.1 A Provider-Specific Option or Metadata Category

A Native Image option that names every enabled provider, or a dedicated metadata category that
names a provider and selects services, was rejected because ordinary reflection metadata already
expresses the required dynamic class access.
Either mechanism would create a second registration model, make standard Java configuration
insufficient, and require the Tracing Agent and missing-registration diagnostics to learn a new
metadata vocabulary.
A dedicated format would also duplicate information the provider already owns and would need rules
for aliases, provider version changes, unknown custom service types, shared implementations, and
specialized service construction; selective entries would still require the run-time filtering
model rejected in [§3.4](#34-partial-providers).
A new metadata category is justified only if selective provider catalogs become a product feature
rather than an implementation optimization; no size evidence currently establishes that need.

### 3.2 Unconditional or Inferred Inclusion

Unconditionally retaining every provider was rejected because it would increase executable size
and include provider implementations that the application did not request.
Inferring all provider access from static reachability was rejected because provider names,
algorithms, and service selections can arrive only at run time; static analysis cannot reliably
reconstruct the application's dynamic JCA configuration.

### 3.3 Narrower or Wider Registration Signals

Accepting only a declared no-argument constructor was rejected because the missing-registration
diagnostic instructs the user to add a *type* entry, so a build that still rejected the provider
would send the user around the same loop; because it would make application-supplied providers
unregistrable whenever the provider class has no no-argument constructor; and because it would
exclude provider classes whose only JDK instantiation path is `provider()`.
Dropping the `provider()` path was rejected because it is the `ServiceLoader` instantiation
contract the JDK itself uses: a provider class that exposes a singleton through `provider()` has
no usable no-argument constructor and would become permanently unavailable as a JDK-managed
provider even though it works on HotSpot.
Requiring type access together with a construction path was rejected because a constructor entry
already implies type access in the metadata format, and because it would reject the
application-supplied providers that
[§FS-002-security-providers.5](../security-providers.md#5-programmatically-supplied-providers)
permits.
Widening construction to every member Native Image could invoke — a non-public constructor, or
`provider()` on a class-path provider — was rejected because a native executable would then accept
provider configurations that fail on HotSpot.

### 3.4 Partial Providers

Keeping every service descriptor and failing during construction was rejected because it preserves
the catalog structurally while breaking its meaning: `Provider.getService()` would return a
descriptor that cannot provide a service, `Security.getAlgorithms()` would advertise unavailable
algorithms, and the observed failure would depend on how far a particular JDK path progressed
before encountering the missing constructor.

Filtering out the services whose implementations lack metadata is the strongest alternative in
principle, but it requires Native Image to own a second service registry beside the provider's
own.
The filter would have to cover every discovery API, every JCA factory, security-service facades,
aliases, default and fallback paths, direct service construction, providers initialized at build
time and at run time, and provider objects the application constructs itself.
Even then the metadata signal would remain ambiguous: an implementation class shared by several
algorithms does not say which service entries to expose, and constructor metadata describes
neither aliases nor a specialized `newInstance()` override.

Enumerating every service implementation in reflection metadata was rejected because provider
upgrades can replace implementation classes without changing any public algorithm, so copied
metadata would silently produce a partial provider, and the entries would describe implementation
reachability rather than the service catalog users intend to expose.

Retaining only reachable service types — the earlier compatibility behavior — was rejected as the
semantics of explicit registration because applications can select services from run-time
configuration that analysis cannot evaluate, JDK facades acquire services without the public
engine-factory shape, and nearly every JCA factory eventually reaches the shared methods
`Provider.getService()` and `Provider.Service.newInstance()`, so conservative triggers on those
methods would retain every service of every included provider anyway.
It remains appropriate as transition compatibility behavior
([§FS-002-security-providers.7.3](../security-providers.md#73-earlier-service-driven-inclusion-behavior)).

### 3.5 Weaker Condition Semantics

Ignoring run-time-checked conditions on provider signals, or collapsing them to
build-time-checked conditions, was rejected because `typeReached` is the only condition the
current metadata format can express, so every condition a user writes for a provider would be
silently defeated, and the registration answer would split between the reflection surface, which
honors the condition, and the JCA surface, which does not.
Evaluating conditions once, when the run-time provider list initializes, was rejected because
provider visibility would then depend on whether unrelated code touched the `Security` API before
or after the condition type was reached, and because provider-object factory calls bypass the
list, which would force a second evaluation point that could drift from the first.
Constructing providers eagerly and hiding them until activation was rejected because it runs class
initialization and constructors while the condition that guards them is unsatisfied.
Forbidding conditions on entries that register providers was rejected because it would make
ordinary metadata files provider-aware and turn tooling-emitted conditional metadata that happens
to name a provider class into a build error.

### 3.6 Cheaper SecureRandom Handling

Registering the default providers whenever the `SecureRandom` type is reachable was rejected
because the Native Image runtime can make the type reachable independently of application use,
making the condition effectively unconditional.
Retaining only the default `SecureRandom` implementation and its digest dependency was rejected
because the resulting provider object could advertise omitted services, which is the partial
provider rejected in [§3.4](#34-partial-providers).
Replacing internal `SecureRandom` use with a non-cryptographic generator was rejected because the
generator seeds runtime constant blinding and code-offset randomization.

## 4. Consequences

A user adding metadata has one rule to learn: name the provider implementation type to register
it.
The missing-registration diagnostic can keep suggesting a plain type entry, and that suggestion is
sufficient for any provider that declares a supported construction path; Native Image retains the
construction path and derives service metadata from the provider's authoritative catalog, so
reflection metadata stays stable when a provider changes an internal implementation class.

Registering a provider can increase executable size because Native Image retains every resolvable
valid service and its construction metadata.
That cost is visible and attributable to an explicit choice — the registration signal, or the
reachable `SecureRandom` acquisition path — rather than to incidental reachability of a shared JCA
helper method, and an executable that registers no provider does not pay it.
Service-level pruning can be reconsidered if measurements show a material executable-size
regression; any future design must define a stable service identity and preserve one consistent
catalog across all provider discovery and service acquisition paths.

Users receive one failure model.
An unregistered provider is absent; a registered provider exposes a complete supported catalog; an
inactive conditional registration produces the same diagnostics as a missing one, and after
activation the provider appears at its configured position without a list rebuild.
Factory selection, provider enumeration, service enumeration, and direct service access never
disagree because Native Image pruned an implementation behind an advertised descriptor.

The registration and construction rules are expressed once and consumed at every decision point:
the build-time constructibility predicate, the build-time instantiation that reads a provider's
catalog, and the run-time provider-list loader all derive their answers from them, and the
build-time query that recognizes registration signals exposes each signal's condition rather than
a boolean.
A construction path that the build accepts is the path the run time takes, and a configured
provider that satisfies neither rule is reported rather than silently omitted.
