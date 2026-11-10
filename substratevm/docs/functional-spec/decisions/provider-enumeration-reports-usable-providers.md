# FD-003-provider-enumeration-reports-usable-providers: Enumerate Only Usable Security Providers

- Status: Accepted
- Decision date: Unknown (not recorded).

## reason: Reason

The JDK security configuration can name providers that Native Image does not include because their
classes have no provider-registration signal.
The provider-enumeration APIs must define whether such configured but unregistered providers are
absent, cause enumeration to fail, or remain visible until an application accesses them.

Reflection offers a superficially similar query-versus-use distinction.
Query metadata can expose a [`Method`][method] descriptor without permitting its invocation.
The security-provider API has no equivalent descriptor type.
[`Security.getProviders()`][security] returns live [`Provider`][provider] instances whose construction
establishes their names, properties, and service catalogs.
Applications can inspect and mutate those instances, pass them to provider-object factory
overloads, and instantiate their services.
Returning a [`Provider`][provider] is therefore already provider access, not a harmless query that
can fail at a later boundary.

A placeholder cannot preserve the [`Provider`][provider] contract.
It would have the wrong implementation class or would require construction of the implementation
that registration intentionally excludes.
It could not accurately answer service filters without retaining the omitted service catalog, and
an application could observe or use it through APIs that have no later interception point.

Failing an entire enumeration is safer than returning placeholders, but it makes every usable
provider undiscoverable when any configured provider is intentionally absent.
Standard JDK configurations contain providers that many applications do not use.
Requiring metadata for all of them before enumeration succeeds would undermine closed-world
selection and encourage retaining all providers solely to inspect the active list.

For example, suppose the configured provider list contains `SUN` followed by `SunEC`, but only `SUN`
has provider-registration metadata.
An application enumerates the providers to inspect their services:

```java
for (Provider provider : Security.getProviders()) {
    System.out.println(provider.getName());
    System.out.println(provider.getClass().getName());
    System.out.println(provider.getServices());
}
```

With the chosen behavior, the loop receives the real `SUN` provider and can inspect its services.
Failing enumeration because `SunEC` is unregistered would prevent the application from inspecting
`SUN`, even though it never requests an elliptic-curve service.
Returning a placeholder for `SunEC` would expose the placeholder's class and an incomplete or
invented service catalog through the same loop.
These observations already differ from those of a real provider, before any service is invoked.
Constructing the real `SunEC` to obtain its class and catalog would instead acquire the provider
whose registration is missing.
If the application explicitly calls `Security.getProvider("SunEC")`, that acquisition reports the
missing reflection registration rather than returning a placeholder.

Prioritize a truthful, usable provider list over exposing every configured entry because the JDK
provides no query-only provider handle and unused entries should not prevent discovery of usable
providers.
The chosen behavior also preserves actionable diagnostics at acquisition operations that identify
which configured provider the application requires.
This is an unavoidable difference from reflection unless the JDK introduces a provider descriptor
that is separate from a live [`Provider`][provider] instance.

## decision: Decision

[`Security.getProviders()`][security], the filtered [`Security.getProviders`][security] overloads,
and [`Security.getAlgorithms(String)`][security] enumerate only providers and services that are
usable in the run-time provider list.
They omit a configured but unregistered provider and do not fail solely because such an entry
exists elsewhere in the configuration.

Native Image does not construct a placeholder or partially functional [`Provider`][provider] for an
unregistered configuration.
An operation that identifies and attempts to acquire that provider, such as
[`Security.getProvider(String)`][security] or a named Java Cryptography Architecture (JCA) factory,
fails before returning it and reports the missing reflection registration.
The functional specification defines the unregistered-provider behavior in
[§FS-002-security-providers.design.3.1](../security-providers.md#1-unregistered-providers).

## alternatives: Alternatives Considered

### alternatives.deferred-access: Return All Configured Providers and Fail on Later Access

- Behavior: Enumeration exposes every configured provider, with an unregistered provider failing
  only when the application later uses it.
- Benefit: Applications can discover the complete configuration, analogous to reflection's
  query-then-use model.
- Reason not chosen: This requires a provider-descriptor or proxy contract that the JDK API does not
  define.
  Returning a real provider would expose it before registration, while returning a proxy would
  change class identity, provider identity, properties, services, and provider-object factory
  behavior.

### alternatives.fail-enumeration: Fail Provider Enumeration When Any Configuration Is Unregistered

- Behavior: Enumeration fails if any configured provider lacks registration, analogous to a
  reflection bulk query that lacks complete query metadata.
- Benefit: Applications receive a missing-metadata diagnostic instead of an incomplete list, without
  receiving a placeholder that violates the provider contract.
- Reason not chosen: One unused provider would prevent discovery of every registered provider.
  Filtered queries also cannot determine whether an unregistered provider matches a service
  criterion without retaining or constructing its catalog.

## consequences: Consequences

Provider enumeration can contain fewer entries than the same configured JDK on HotSpot when the
native executable omits provider registration metadata.
Registering the provider class makes the provider and its complete retained service catalog visible
after rebuilding.
Building with `-H:Preserve=all` retains all JDK providers when an application requires complete JDK
enumeration behavior.

Revisit this decision if the JDK introduces a provider descriptor that permits discovery without
constructing or exposing a live provider.

## participants: Participants

- Decision makers: Vojin Jovanovic.
- Consulted: Astra, Fable, Opus 5.5.
- Unresolved objections: Not recorded.

[method]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/reflect/Method.html
[provider]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Provider.html
[security]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Security.html
