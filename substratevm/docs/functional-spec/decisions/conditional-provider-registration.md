# AR-008-conditional-provider-registration: Evaluate Metadata Conditions at the Acquisition Boundary

## 1. Context

A reachability metadata entry can carry a condition on a condition type.
The current metadata format, `reachability-metadata.json`, expresses exactly one condition kind:
`"condition": {"typeReached": ...}`, a run-time-checked condition under which the entry behaves as
absent until the condition type is reached at run time.
The earlier configuration files express `typeReachable`, a build-time-checked condition that is
discharged entirely during analysis.
Conditions are the documented mechanism for keeping metadata from growing the executable and from
widening its dynamic-access surface, so metadata emitted by tooling uses them routinely.

[§AR-007-standard-jca-semantics.2](standard-jca-semantics.md#2-decision) makes ordinary reflection
metadata the provider registration signal, and
[§REQ-002-security-providers.2](../requirements/security-providers.md#2-metadata-closure) forbids a
provider-specific metadata category.
Conditions are part of ordinary reflection metadata semantics: a signal that ignored its entry's
condition would not be the ordinary metadata the decision requires, and the same entry would give
two answers to one registration question — `Class.forName` on the provider class would throw a
missing-registration error until the condition type is reached, while a JCA factory would select
the provider it had just refused to load.

A provider registration signal is unlike an ordinary element registration in one respect: it causes
derived effects.
Registration retains the provider's construction path and complete service catalog
([§FS-002-security-providers.2.3](../security-providers.md#23-registration-effects)), admits the
provider to the run-time provider list
([§FS-002-security-providers.1.3](../security-providers.md#13-run-time-provider-list)), and
establishes a JCE verification outcome
([§FS-002-security-providers.5.3](../security-providers.md#53-jce-verification)).
A condition on the signal must gate those effects coherently, or the guard is one-sided: the
provider class would be gated while its services and verification outcome remained available.

Two properties of the run-time machinery constrain where a run-time-checked condition can be
evaluated.
The JDK builds its provider list once and caches it, so a condition can become satisfied before or
after that construction.
And a provider-object factory call
([§FS-002-security-providers.5.1](../security-providers.md#51-provider-object-factory-calls)) never
consults the list, so an evaluation point tied to list construction cannot cover it.

## 2. Decision

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

## 3. Rejected Alternatives

**Ignore run-time-checked conditions on provider signals.**
Treat a signal whose condition type was reachable at build time as unconditional at run time.
Rejected because `typeReached` is the only condition the current metadata format can express, so
every condition a user writes for a provider would be silently defeated; because it splits the
registration answer between the reflection surface, which honors the condition, and the JCA
surface, which does not; and because it makes the signal something other than the ordinary
reflection metadata that [§AR-007-standard-jca-semantics.2](standard-jca-semantics.md#2-decision)
requires.

**Collapse run-time-checked conditions to build-time-checked conditions for provider signals.**
An honest, documented weakening of the same shape.
Rejected for the same reasons: the entry's author asked for run-time gating, the collapse defeats
it silently at run time, and the asymmetry against ordinary reflection on the same class remains.

**Evaluate conditions once, when the run-time provider list initializes.**
Rejected because provider visibility would then depend on whether unrelated code first touched the
`Security` API before or after the condition type was reached — timing-dependent semantics of the
kind [§AR-006-reachability-independent-runtime-semantics.2](reachability-independent-runtime-semantics.md#2-decision)
exists to rule out — and because provider-object factory calls bypass the list, which would force a
second evaluation point that could drift from the first.

**Construct eagerly and hide the provider until its registration is active.**
Rejected because it runs the provider's class initialization and constructor while the condition
that guards them is unsatisfied.
Deferring exactly that code is a reason conditions exist, and its side effects would be observable.

**Forbid conditions on entries that register providers.**
Rejected because it would make ordinary metadata files provider-aware, a provider-specific metadata
rule that [§REQ-002-security-providers.2](../requirements/security-providers.md#2-metadata-closure)
forbids, and because tooling-emitted conditional metadata that happens to name a provider class
would become a build error.

## 4. Consequences

The catalog of registered providers records the union of each provider's run-time conditions, and
the single run-time filter evaluates that condition set.
The check is cheap: a satisfied condition is sticky, so after activation it is a cached boolean.

Provider construction stays lazy.
The JDK's provider-list machinery already models a configured provider that has not been loaded,
and an inactive registration is exactly that state, so activation requires no list rebuild and
preserves the configured order as providers become observable.

At build time, the query that recognizes registration signals must expose each signal's condition
rather than a boolean, and the reflection metadata the provider registrar emits for construction
paths and service implementations must carry the propagated union condition instead of an
unconditional one.

Diagnostics are unchanged: an inactive registration produces the same missing-registration
diagnostics as an unregistered provider, and the suggested entry remains the plain type entry,
matching ordinary conditional reflection.

The platform-owned `SecureRandom` signal of
[§FS-002-security-providers.2.4](../security-providers.md#24-securerandom-providers) carries no
metadata condition, so its registrations are always active.

[§REQ-002-security-providers.10](../requirements/security-providers.md#10-condition-fidelity) is
the acceptance criterion for this decision, and the architecture record
[§AR-002-security-providers](../../architecture/security-providers.md) must name where activation
is evaluated.
