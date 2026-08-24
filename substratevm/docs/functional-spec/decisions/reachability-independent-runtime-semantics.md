# FD-002-reachability-independent-runtime-semantics: Keep Run-Time Semantics Independent of Reachability

## 1. Context

Reachability shows that code might execute, but it cannot predict values supplied at run time.
It also changes when an application adds a dependency, moves code, or introduces a dormant branch.
Users therefore cannot predict or reliably control reachability as part of run-time behavior.

## 2. Decision

Given the same configuration, metadata, and run-time inputs, adding or removing reachable but
unexecuted code must not change the observable behavior of retained operations.
Reachability may decide which code enters the executable and may activate an explicitly specified
inclusion condition.
It must not silently change a run-time lookup result, available catalog, or selected implementation.

## 3. Canonical Example

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
The dormant `Signature` call must not make a provider or service available or change provider
selection.
The security-provider reachability requirement applies this example directly
(§FS-002-security-providers.8.6).
