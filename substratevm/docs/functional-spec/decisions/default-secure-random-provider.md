# AR-004-default-secure-random-provider: Retain the Complete Default Provider for SecureRandom

## 1. Context

`SecureRandom` constructors and factories select JDK-managed providers.
Requiring application reflection metadata for those providers would make a commonly used JDK
facility fail for reasons that expose provider implementation details.

Native Image also uses `SecureRandom` to seed runtime-compilation hardening. Registering that
internal random source in every executable makes `SecureRandom` appear application-reachable and
retains the complete SUN provider even in an otherwise empty executable.

Provider registration is intentionally complete: exposing a provider while omitting services that
the provider advertises would create inconsistent discovery and factory results.
This constraint is specified by
[§FS-002-security-providers.1.1](../security-providers.md#11-registered-providers-and-services)
and [§FS-002-security-providers.4.1](../security-providers.md#41-unregistered-providers).

## 2. Decision

When a `SecureRandom` acquisition path is reachable, Native Image registers every configured
provider that declares a `SecureRandom` service.
The acquisition path is a platform-owned conditional provider-registration signal, so the
application does not need to supply reflection metadata for those implicit JDK dependencies.
Each provider is registered completely, so provider discovery and service acquisition remain
consistent.

Native Image registers its internal secure runtime-randomness singleton only in executables that
include runtime compilation, which is the only subsystem that consumes that singleton. Ordinary
executables therefore do not retain SUN merely because Native Image has an optional internal
hardening mechanism.

This is not service-driven inclusion.
The platform supplies the registration signal, and the ordinary complete-provider semantics apply
after registration.
Reachability of other JCA factories does not register their providers when explicit
security-provider registration is enabled.
This bounded registration condition follows
[§AR-006-reachability-independent-runtime-semantics.2](reachability-independent-runtime-semantics.md#2-decision).

## 3. Rejected Alternatives

Conditionally registering SUN whenever the `SecureRandom` type is reachable was rejected because
the Native Image runtime can make the type reachable independently of application use. It would
make the condition effectively unconditional and impose the complete provider cost on ordinary
executables.

Retaining only the default `SecureRandom` implementation and its SHA dependency was rejected
because the resulting SUN object could advertise omitted services. Correctly supporting partial
providers would require one canonical filtered service registry across every provider-discovery and
factory API.

Replacing internal `SecureRandom` with a non-cryptographic generator was rejected because the
generator seeds runtime constant blinding and code-offset randomization.

## 4. Consequences

Applications can use `SecureRandom` constructors and factories under explicit provider
registration without provider-specific metadata.
Such applications, and runtime-compilation images that use the internal secure random source, pay
the full size cost of the configured providers that declare `SecureRandom` services.
Ordinary executables that do not acquire `SecureRandom` avoid that cost.

Provider registration remains all-or-nothing, so users do not observe algorithms that are named by
SUN but absent from the executable.
