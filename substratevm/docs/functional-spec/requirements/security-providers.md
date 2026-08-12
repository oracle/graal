# REQ-security-providers: Requirements for JCA Security Provider Support

[§FS-security-providers](../security-providers.md) specifies what Native Image does with Java
Cryptography Architecture (JCA) security providers.
This document states what must remain true of that behavior across changes: the properties a change
is accepted against, and the obligation that discharges each one.

Each requirement is written so that it can be falsified, and states:

- a **domain**, an enumerable set the requirement quantifies over, taken from the functional
  specification so that coverage remains auditable;
- an **oracle or witness**: HotSpot behavior for the properties that compare the two runtimes, or
  the metadata whose existence the requirement asserts;
- a **falsifier**, the shape of a counterexample; and
- the **evidence** that discharges it, named as an execution mode of
  §FS-001-native-image-semantics.3 or as an audit of a finite artifact.

[§1](#1-construction-parity) through [§8](#8-tracing-round-trip) are discharged by executable
evidence.
[§9](#9-obligations-discharged-by-architecture) states the two obligations that no finite set of
executable tests can discharge.

This document uses the [notation](../README.md#notation) of the functional specifications.

> A statement that no counterexample could contradict is a goal, not a requirement.
> Motivation and rejected alternatives belong in [the functional decisions](../decisions/), not
> here.

## 1. Construction Parity

**Requirement.** For every provider implementation class that the JDK can construct on HotSpot
through a provider constructor or a provider method
([§FS-security-providers.1.1](../security-providers.md#11-registered-providers-and-services)),
there must exist reachability metadata, expressible in the standard reflection metadata format,
that lets a native executable acquire that provider through the same JDK API and use its services.
Native Image must not require a security-provider-specific build option, an option that names a
provider, or a change to the application source.
The transition options of
[§FS-security-providers.7](../security-providers.md#7-transition-to-the-future-defaults) are the
only build options this requirement permits, and they must become unnecessary when the behavior
they select becomes the default (§REQ-001-spec-compatibility.2).

**Domain.** The construction-path shapes admitted by
[§FS-security-providers.1.1](../security-providers.md#11-registered-providers-and-services) and
[§FS-security-providers.2.2](../security-providers.md#22-provider-construction): a public provider
constructor on the class path; a provider method declared by a service provider class in an
explicit module; a provider method whose implementation class differs from its service provider
class; a provider method that returns a non-public implementation class; a provider class that the
configured list names more than once; and a provider whose run-time name differs from the
configured name.

**Falsifier.** A provider that HotSpot constructs and that no reflection metadata makes acquirable
in a native executable.

**Evidence.** One `WITH_REGISTRATION` test per shape that acquires the provider and uses one of its
services.
The domain and the set of tests must be the same list: a construction path added to
[§FS-security-providers.1.1](../security-providers.md#11-registered-providers-and-services) without
a corresponding test leaves this requirement undischarged.

## 2. Metadata Closure

**Requirement.** The inputs that register a security provider must be exactly the reflection
metadata signals of
[§FS-security-providers.2.1](../security-providers.md#21-qualifying-reflection-metadata) and the
platform-owned signal of
[§FS-security-providers.2.4](../security-providers.md#24-securerandom-providers).
Native Image must not define a metadata category, a configuration file, a command-line option, or
an exception type that exists only for security providers.
Missing-registration diagnostics and collected metadata must use the ordinary reachability metadata
format ([§FS-security-providers.4.3](../security-providers.md#43-missing-reflection-registration)
and [§FS-security-providers.6.1](../security-providers.md#61-provider-and-service-coverage)).

**Domain.** Three finite, inspectable artifacts: the reachability metadata schema, the Native Image
option list, and the exception types the provider paths can throw.

**Falsifier.** Any entry in one of those three artifacts that exists only for security providers.

**Evidence.** An audit of the three artifacts rather than a run, together with the
`NO_REGISTRATION` assertion that a missing registration produces
`org.graalvm.nativeimage.MissingReflectionRegistrationError` and not a security-provider-specific
substitute.

> This requirement is the acceptance criterion for
> [§DF-standard-jca-semantics.2](../decisions/standard-jca-semantics.md#2-decision).

## 3. Diagnostic Sufficiency

**Requirement.** When an operation fails because a provider class is not registered, the diagnostic
must identify the provider implementation class by its binary name and contain a metadata entry
that is *sufficient*: adding that entry verbatim to `reachability-metadata.json` and rebuilding
must make the same operation succeed, without producing a further diagnostic for the same element.
For a provider that has a supported construction path, one round trip must resolve the provider and
its services.
For an application-supplied provider that has no supported construction path, the entry is
sufficient for the provider class alone, and each service implementation must be diagnosed the same
way when it is first used
([§FS-security-providers.4.3](../security-providers.md#43-missing-reflection-registration)).

**Witness.** The metadata entry printed in the diagnostic.

**Falsifier.** A diagnostic whose entry does not repair the failure it reports, or that leaves the
user to infer a second entry for the same element.

**Evidence.** A `NO_REGISTRATION` round trip: run the executable, capture the diagnostic, extract
the suggested entry, rebuild with that entry added, and assert both that the operation succeeds and
that no diagnostic names the same element again.

## 4. No Exposure of an Unregistered Provider

**Requirement.** An unregistered provider must never become observable to application code: it must
not be returned, enumerated, named by algorithm discovery, or used to back an engine or a
security-service facade, and it must not supply a service that requires Java Cryptography Extension
(JCE) verification unless Native Image established a successful verification outcome for its class
at build time
([§FS-security-providers.4.1](../security-providers.md#41-unregistered-providers) and
[§FS-security-providers.5.3](../security-providers.md#53-jce-verification)).
The operation must fail before application code receives any object that represents that provider.

**Domain.** The acquisition paths listed in
[§FS-security-providers.1.2](../security-providers.md#12-jdk-managed-providers-and-acquisition).
That list is explicitly not exhaustive, so executable evidence discharges this requirement for the
listed paths only; [§9.2](#92-the-acquisition-boundary) states how the remainder is discharged.

**Falsifier.** An execution in which application code holds a `Provider`, a `Provider.Service`, an
engine, or a facade that corresponds to an unregistered provider; a `Security.getAlgorithms(String)`
result that names one; or a JCE-verified service supplied without a successful build-time
verification outcome.

**Evidence.** One `NO_REGISTRATION` assertion per listed acquisition path, and a verification matrix
that covers a successful outcome, a failed outcome, and an unregistered provider.

## 5. Standard Semantics Modulo Registration

**Requirement.** For a program in which every used provider is registered, the observable behavior
of the API surface in
[§FS-security-providers.1.2](../security-providers.md#12-jdk-managed-providers-and-acquisition)
must be the behavior of HotSpot: the same return values, the same exception types, and the same
provider order.
Where the two runtimes differ, the difference must be exactly the registration filter:
`Security.getProviders()` must return the HotSpot list with the unregistered providers removed and
the relative order of the remaining providers preserved
([§FS-security-providers.1.3](../security-providers.md#13-run-time-provider-list)).

**Oracle.** The same program executed on HotSpot.

**Falsifier.** A program whose providers are all registered and whose native and HotSpot
observations differ, or a native provider list that is not the filtered HotSpot list.

**Evidence.** Differential runs of the same program on HotSpot and as a native executable, compared
value by value rather than against a recorded expectation.

## 6. Reachability Independence

**Requirement.** With explicit provider registration enabled, provider availability must be a
function of the configured security properties and the registration metadata alone.
Two builds of the same application that differ only in code that is reachable but performs no
provider access must produce the same run-time provider list, the same lookup results, and the same
service availability.

**Domain.** Pairs of builds that differ by one reachable but unexecuted JCA factory call or
security-service facade.

**Falsifier.** A pair of such builds whose provider list, lookup results, or service availability
differ.

**Evidence.** Paired builds compared directly.
The earlier service-driven inclusion behavior of
[§FS-security-providers.7.3](../security-providers.md#73-earlier-service-driven-inclusion-behavior)
violates this requirement by design; it is an intentional deviation, so it must remain confined to
the compatibility mode that selects it (§REQ-001-spec-compatibility.3).

## 7. Transition Compatibility

**Requirement.** Every supported option combination of
[§FS-security-providers.7](../security-providers.md#7-transition-to-the-future-defaults) must be a
specified configuration, the earlier behavior must remain reachable without a change to the
application source, and the selected behavior must be a build-time property that no run-time system
property can change (§REQ-001-spec-compatibility.2).

**Domain.** The three supported combinations: legacy inclusion with build-time initialization,
legacy inclusion with run-time initialization, and explicit registration with run-time
initialization.

**Falsifier.** A behavior specified by
[§FS-security-providers.1](../security-providers.md#1-provider-reflection-registration) through
[§FS-security-providers.6](../security-providers.md#6-tracing-metadata) that no option combination
selects, an earlier behavior that no combination preserves, or an executable whose provider
behavior changes with a run-time system property.

**Evidence.** The option matrix run against the same suite with per-combination expectations, and
one executable run twice with the future-defaults system property flipped.

## 8. Tracing Round-Trip

**Requirement.** Metadata collected by the Tracing Agent or by native metadata tracing must be both
sufficient and minimal.
Sufficient: an executable built from that metadata performs the traced provider operations without
additional provider metadata.
Minimal: the metadata registers no provider that the traced application did not use, and tracing
performs none of the observed operations a second time
([§FS-security-providers.6](../security-providers.md#6-tracing-metadata)).

**Domain.** Traced runs of the programs that cover
[§FS-security-providers.6.1](../security-providers.md#61-provider-and-service-coverage): provider
lookup, provider enumeration and filtering, provider-list mutation, service selection through a
factory, and service instantiation through `Provider.Service.newInstance(Object)`.

**Falsifier.** A traced run whose metadata does not rebuild into a working executable, metadata that
names a provider the application never used, or a traced run whose provider construction,
initialization, or caching differs from the same run without tracing.

**Evidence.** `AGENT_REGISTRATION` and `NATIVE_TRACING_REGISTRATION` for sufficiency,
`AGENT_DIFFERENTIAL_REGISTRATION` for minimality, and assertions on the traced run itself for the
observational transparency required by
[§FS-security-providers.6.2](../security-providers.md#62-observational-transparency).

## 9. Obligations Discharged by Architecture

Two claims of the functional specification are universal negatives over the implementation.
No finite set of executable tests can discharge them, so
[§AR-security-providers](../../architecture/security-providers.md) must discharge them
structurally, and a change that adds a second path must update that architecture record.

### 9.1 Closure of the Registration Signals

[§FS-security-providers.2.1](../security-providers.md#21-qualifying-reflection-metadata) states that
a provider class is registered under no circumstance other than the listed signals.
Every build-time provider registration must therefore pass through a single chokepoint that the
architecture record names, so that the claim is checked in one place instead of being re-argued for
each build-time path that can reach a provider class.

### 9.2 The Acquisition Boundary

[§FS-security-providers.1.2](../security-providers.md#12-jdk-managed-providers-and-acquisition)
states that its list of acquisition paths is not exhaustive, so
[§4](#4-no-exposure-of-an-unregistered-provider) cannot be discharged path by path.
All run-time provider-list construction and provider lookup must therefore pass through a single
filter that the architecture record names, so that the safety property is argued once about that
filter and holds for the paths no test enumerates.
