# FD-001-hotspot-compatible-semantics-by-default: Match HotSpot Without Additional Flags

- Status: Accepted
- Decision date: Unknown (not recorded).

## reason: Reason

Native Image uses a closed world.
Reflection can require metadata, and unrestricted dynamic class loading is not supported.
Those limits should not create a separate enablement flag for each standard Java feature.
Years of community issues and broader community experience show that feature-specific enablement
flags turn otherwise standard Java behavior into recurring Native Image integration failures.
Users often do not know that a transitive dependency relies on the feature, so an application that
works on HotSpot fails only after native compilation, with symptoms far removed from the missing
flag.
Carrying that experience forward is not acceptable for Native Image.

Prioritize predictable Java behavior and ordinary metadata over feature-specific enablement because
users should not need to discover every standard Java feature used by their transitive dependencies.
A permanent feature flag would preserve the cause of these integration failures.

## decision: Decision

For the same application, JDK version, configuration, platform, and run-time inputs, a native
executable must have the same observable run-time behavior as the application on HotSpot, except
for documented reflection restrictions and unsupported dynamic class loading.
Standard Java behavior must not require an additional Native Image flag.
A flag may select an intentional deviation or a temporary compatibility transition, but it must
not be a permanent prerequisite for HotSpot-compatible behavior.
The security-provider construction-parity requirement is one application of this decision
([§FS-002-security-providers.requirements.1](../security-providers.md#requirements1-construction-parity)).

## alternatives: Alternatives Considered

### alternatives.feature-flags: Keep Permanent Feature Enablement Flags

- Behavior: Users supply a separate Native Image flag to enable a standard Java feature, even when
  they have supplied its ordinary reflection metadata.
- Benefit: Explicit switches let users select features and preserve existing build configurations.
- Reason not chosen: Users must discover hidden dependencies and maintain Native Image-specific
  enablement knowledge for behavior that already works on HotSpot.
  A temporary compatibility option can preserve existing builds during a transition without making
  the flag a permanent prerequisite.

## consequences: Consequences

A functional specification states its ordinary metadata requirements and any intentional deviation.
It does not introduce a flag merely to enable standard Java behavior.
Users still supply metadata for reflection, and unrestricted dynamic class loading remains
unsupported.
Implementations must support the ordinary metadata path; a feature enablement flag cannot serve as
its permanent substitute.
Existing flags may need a compatibility transition before their removal.

## participants: Participants

- Decision makers: Thomas Würthinger, Christian Wirth, Vojin Jovanović.
- Consulted: Unknown (not recorded).
- Unresolved objections: Not recorded.
