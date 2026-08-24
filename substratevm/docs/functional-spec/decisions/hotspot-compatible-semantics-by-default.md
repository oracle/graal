# FD-001-hotspot-compatible-semantics-by-default: Match HotSpot Without Additional Flags

## 1. Context

Native Image uses a closed world.
Reflection can require metadata, and unrestricted dynamic class loading is not supported.
Those limits should not create a separate enablement flag for each standard Java feature.
Years of community issues and broader community experience show that feature-specific enablement
flags turn otherwise standard Java behavior into recurring Native Image integration failures.
Users often do not know that a transitive dependency relies on the feature, so an application that
works on HotSpot fails only after native compilation, with symptoms far removed from the missing
flag.
Carrying that experience forward is not acceptable for Native Image.

## 2. Decision

For the same application, JDK version, configuration, platform, and run-time inputs, a native
executable must have the same observable run-time behavior as the application on HotSpot, except
for documented reflection restrictions and unsupported dynamic class loading.
Standard Java behavior must not require an additional Native Image flag.
A flag may select an intentional deviation or a temporary compatibility transition, but it must
not be a permanent prerequisite for HotSpot-compatible behavior.

## 3. Consequence

A functional specification states its ordinary metadata requirements and any intentional
deviation.
It does not introduce a flag merely to enable standard Java behavior.
The security-provider construction-parity requirement is one application of this decision
(§FS-002-security-providers.8.1).
