# DF-reachability-independent-runtime-semantics: Keep Run-Time Semantics Independent of Reachability

## 1. Context

Native Image uses reachability analysis to determine which program elements an executable
contains.
Applications can nevertheless make choices from run-time inputs that static analysis cannot
predict.
These choices include class names, resource names, service implementations, serialization types,
security providers, and algorithm names.

Reachability is an implementation property of the closed-world build.
If incidental call-graph reachability changes the meaning of retained operations, two applications
with the same configuration and run-time inputs can observe different Java behavior.
Adding an unused call could make a dynamic value valid, expose an additional catalog entry, or
change which implementation an existing operation selects.
Such differences would expose the mechanics of closed-world analysis as run-time semantics.

## 2. Decision

Given the same build configuration, registered metadata, and run-time inputs, Native Image
preserves the same observable behavior for retained operations regardless of incidental
reachability during image construction.
Reachability determines which code and explicitly conditional components enter the closed world.
It does not act as a proxy for run-time values or silently redefine the behavior of components that
the build has retained and configured.

An explicit specification can define reachability as a platform-owned inclusion condition when the
platform itself introduces an otherwise implicit dependency.
Such a condition decides whether to include a complete component.
It does not permit reachability to select an undocumented subset of that component or alter its
behavior after inclusion.

For security providers, reachability of a factory method, service type, algorithm constant, facade,
or fallback path does not select a subset of an explicitly registered provider's services.
It also does not register an otherwise unregistered provider, except where
[§FS-security-providers.2.4](../security-providers.md#24-securerandom-providers) defines the bounded
`SecureRandom` inclusion rule.
The earlier service-driven behavior in
[§FS-security-providers.7.3](../security-providers.md#73-earlier-service-driven-inclusion-behavior)
remains a documented transition compatibility mode, not the planned run-time semantics.

## 3. Rejected Alternatives

Treating every reachable operation as evidence for all dynamic values it might consume was rejected
because reachability does not identify the value selected at run time.
It can retain unrelated implementations while still omitting values supplied through external
configuration.

Pruning an explicitly registered component according to the callers visible during analysis was
rejected because dynamic entry points do not follow one statically recognizable call shape.
The resulting component would expose a build-dependent partial interface.

Evaluating constant arguments as an implicit permission boundary was rejected because it would make
a constant and the same value read from run-time configuration behave differently.

## 4. Consequences

Dynamic behavior still requires the metadata or platform registration specified for that
mechanism.
Missing registration remains a defined closed-world boundary.
After the build includes a component, incidental reachability does not create a second,
less-visible permission boundary inside it.

This rule can retain implementations with no statically visible callers when an explicitly
registered component exposes them dynamically.
That size cost gives registration one stable meaning and prevents behavior from changing when an
application restructures equivalent calls or moves a value from source code to run-time
configuration.
