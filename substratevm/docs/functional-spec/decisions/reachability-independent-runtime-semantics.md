# FD-002-reachability-independent-runtime-semantics: Keep Run-Time Semantics Independent of Reachability

- Status: Accepted
- Decision date: Unknown (not recorded).

## reason: Reason

Reachability shows that code might execute, but it cannot predict values supplied at run time.
It also changes when an application adds a dependency, moves code, or introduces a dormant branch.
Users therefore cannot predict or reliably control reachability as part of run-time behavior.

Prioritize stable behavior for the same configuration, metadata, and run-time inputs over automatic
inclusion inferred from incidental reachable calls.
Otherwise, adding an unexecuted branch can change a lookup result or available catalog without an
intentional configuration change.
Explicitly specified inclusion conditions make such effects part of a contract users can control.

## decision: Decision

Given the same configuration, metadata, and run-time inputs, adding or removing reachable but
unexecuted code must not change the observable behavior of retained operations.
Reachability may decide which code enters the executable and may activate an explicitly specified
inclusion condition.
It must not silently change a run-time lookup result, available catalog, or selected implementation.
The security-provider reachability requirement applies this decision directly
([§FS-002-security-providers.requirements.6](../security-providers.md#requirements6-reachability-independence)).

### decision.examples: Examples

These examples use explicit provider registration.
Unless an example explicitly changes registration, the extra code introduces no provider-registration
signal and activates no specified registration condition.

#### decision.examples.dormant-call: Reachable but Unexecuted Code

Consider this code, where `probe` is a run-time input:

```java
static void run(String providerName, boolean probe) throws GeneralSecurityException {
    Provider selected = Security.getProvider(providerName);

    if (probe) {
        Signature.getInstance("SHA256withRSA");
    }

    System.out.println(selected == null ? "missing" : selected.getName());
}
```

Compare two builds: one contains the `if (probe)` block and one omits it.
Run both with the same provider configuration, metadata, `providerName`, and `probe=false`.
Analysis must consider the block reachable, but neither run executes it.
Both runs must print the same result and expose the same providers and services.
The dormant [`Signature`][signature] call must not make a provider or service available or change
provider selection.

#### decision.examples.preserve: Preserving an Unused Library Method

Suppose an application calls `Security.getProvider(providerName)` and never calls the following
library method:

```java
package example.probes;

import java.security.GeneralSecurityException;
import java.security.Signature;

public final class OptionalProbe {
    public static void probe() throws GeneralSecurityException {
        Signature.getInstance("SHA256withRSA");
    }
}
```

Compare a normal build with one that adds `-H:Preserve=package=example.probes`.
The package contains no provider implementations or registration metadata, and no provider
registration condition depends on it.
Both builds use the same provider configuration, provider-registration metadata, and run-time inputs.
Preservation retains `OptionalProbe.probe()` and makes its call to `Signature.getInstance` reachable,
even though the application never executes it.
The provider lookup must still return the same provider or report the same missing registration.
Preserving a utility method must not incidentally add providers, expose more algorithms, or change
provider preference order.

#### decision.examples.preserve-all: Explicit Preservation of Providers

Now build the same application with `-H:Preserve=all`.
[Preservation](../../../../docs/reference-manual/native-image/BuildOptions.md#preserving-packages-modules-or-classes)
also retains provider implementations and their construction metadata, so additional configured JDK
providers can legitimately become available.
This changes the registration inputs to provider selection.
The decision therefore does not require the provider list to match that of a build with less metadata.

Instead, compare two builds that both use `-H:Preserve=all`: one contains the dormant `probe` block
from the first example, and the other omits it.
With the same provider configuration and run-time inputs, and `probe=false`, both builds must have
the same provider lookup results, provider order, and available services.
Availability follows the explicit preservation settings, not the presence of the dormant call.

#### decision.examples.layers: Retaining Extra Code in a Shared Layer

Suppose a shared layer contains reusable library code, and an application layer calls
`Security.getProvider(providerName)` without calling `OptionalProbe.probe()`.
Compare two builds of that application:

- The first uses a shared layer that does not retain `OptionalProbe.probe()`.
- The second uses a shared layer that retains the method as a library entry point for potential
  application-layer callers.

Keep the provider configuration, effective provider-registration metadata across the layers, and
application run-time inputs the same.
The second shared layer makes the signature factory reachable, but the application never executes it.
Both executables must return the same provider or report the same missing registration, and expose
the same provider order and services.
A shared layer must not determine a different available-provider set merely because it retains
more library entry points than this application uses.

These builds must respect the existing layer constraints: every layer uses the same initial
security properties ([§FS-002-security-providers.design.6.1](../security-providers.md#1-run-time-provider-list-initialization)),
and descriptor-based classpath providers are registered in the initial layer because a later layer
cannot extend an existing provider service descriptor
([§FS-002-security-providers.design.6.2](../security-providers.md#2-provider-service-descriptors)).
Adding provider registration in a layer changes the inputs to this comparison; retaining an unused
factory call alone does not.

## alternatives: Alternatives Considered

### alternatives.inferred-inclusion: Infer Availability from Reachable Calls

- Behavior: A reachable API call retains associated implementations or services, making them
  available to other operations even when the call never executes.
- Benefit: Applications can acquire those implementations without explicit registration metadata.
- Reason not chosen: A dependency change or dormant branch can silently change lookup results,
  service catalogs, or implementation selection for unchanged run-time inputs.
  Reachability does not establish which implementations those inputs actually require.

## consequences: Consequences

Users can reason about retained operations through configuration, metadata, and run-time inputs.
Applications that previously depended on incidental reachability may need explicit metadata.
A functional specification must identify any permitted inclusion condition and its effects.
Validation must compare builds that differ only in reachable but unexecuted code, including code
retained through preservation or shared layers, as in the examples.
Reachability analysis can still remove code that cannot execute.

## participants: Participants

- Decision makers: Thomas Wuerthinger, Christian Wimmer, Vojin Jovanovic.
- Consulted: GraalVM Meetup 2025
- Unresolved objections: Not recorded.

[signature]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Signature.html
