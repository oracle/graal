# FD-003-provider-enumeration-reports-usable-providers: Enumerate Only Usable Security Providers

## 1. Context

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

## 2. Decision

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
This behavior deliberately does not match reflection's query-then-use model.
The JDK exposes no query-only provider handle, and no available alternative preserves the
[`Provider`][provider] contract without fully including and constructing the provider.

## 3. Rationale

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

The chosen behavior makes a returned [`Provider[]`][provider] a truthful list of usable provider
objects.
It also preserves actionable diagnostics at acquisition operations that identify which configured
provider the application requires.
This is an unavoidable difference from reflection unless the JDK introduces a provider descriptor
that is separate from a live [`Provider`][provider] instance.

## 4. Rejected Alternatives

### 4.1 Return All Configured Providers and Fail on Later Access

This alternative would require a new provider-descriptor or proxy contract that the JDK API does
not define.
Returning a real provider would expose it before registration, while returning a proxy would change
class identity, provider identity, properties, services, and provider-object factory behavior.

### 4.2 Fail Provider Enumeration When Any Configuration Is Unregistered

This alternative would make [`Security.getProviders()`][security] analogous to a reflection bulk
query that lacks complete query metadata.
It was rejected because one unused provider would prevent discovery of every registered provider.
Filtered queries have the additional problem that Native Image cannot know whether an
unregistered provider matches a service criterion without retaining or constructing its catalog.

## 5. Consequences

Provider enumeration can contain fewer entries than the same configured JDK on HotSpot when the
native executable omits provider registration metadata.
Registering the provider class makes the provider and its complete retained service catalog visible
after rebuilding.
Building with `-H:Preserve=all` retains all JDK providers when an application requires complete JDK
enumeration behavior.

[method]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/reflect/Method.html
[provider]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Provider.html
[security]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Security.html
