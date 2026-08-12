# DF-provider-registration-signals: Separate the Registration Signal from the Construction Path

## 1. Context

Native Image must answer two independent questions about a provider implementation class:

- **Registration**: does the application intend this provider to be present in the executable?
- **Construction**: can the JDK create an instance of it without the application supplying one?

JCA answers only the second question, and it answers it by delegating to a rule that the platform
already specifies.
The `java.util.ServiceLoader` class specification defines how a service provider is instantiated: a
provider method is a public static method named `provider` with no formal parameters whose return
type is assignable to the service type, and the service loader invokes that method when the provider
declares one; otherwise the provider is instantiated through a provider constructor, which is a
public constructor with no formal parameters.
The same specification requires the service provider class to be public and makes more than one
public static no-args `provider` method a `ServiceConfigurationError`.
For the provider-method path, the service provider class can be an interface, an abstract class, or
a class that is not assignable to the service type.
The method's returned object supplies the provider implementation class.

The provider method belongs to module-deployed providers: the specification introduces it for a
service provider developed in a module and states that a provider deployed as an automatic module
has no support for one.
`ServiceLoader` correspondingly consults a provider method only for a provider in an explicit
module, so a class-path provider is always instantiated through its provider constructor.

`sun.security.jca.ProviderConfig` reaches a configured provider through that mechanism, matching an
iterated provider by its provider name, and falls back to a legacy class-name load that calls
`Class.newInstance()` when `ServiceLoader` yields nothing.
That fallback is an implementation detail rather than specified behavior, but it agrees with the
specified rule, because it too requires a public class and a public no-argument constructor.
No JDK path constructs a JDK-managed provider through a non-public constructor.

Both steps of the `provider()` path are reflective: `ServiceLoader` looks the method up through
`JavaLangAccess.getDeclaredPublicMethods` and invokes it through `Method.invoke`.
Ordinary reflection metadata is therefore not merely a convenient registration signal for this
path; it is the metadata the JDK's own call path consumes.

Native Image mirrors these instantiation paths so that a provider that the JDK can construct on
HotSpot remains constructible in a native executable, and no other.

JCA answers nothing about the first question.
On HotSpot every provider class on the class path is loadable, so "is this provider intended to be
present" is not a JCA concept at all; it exists only because Native Image builds a closed world.
That signal therefore belongs to the Native Image metadata model, and
[§DF-standard-jca-semantics.2](standard-jca-semantics.md#2-decision) requires it to be ordinary
reflection metadata rather than a provider-specific mechanism.

The two questions are genuinely independent, not two spellings of the same fact.
[§FS-security-providers.5](../security-providers.md#5-programmatically-supplied-providers) lets an
application construct a provider itself and pass it to a JCA factory.
Such a provider class need not declare a nullary constructor at all, yet Native Image must still
recognize it as registered so that JCE verification and provider-object factory calls work.
Conversely, a provider class can be constructible without the application ever naming the type.

The metadata mechanisms make the two signals asymmetric in a way that is invisible in a
specification written only in terms of user-authored JSON.
Every `reachability-metadata.json` type entry registers the type, so an entry that lists only a
constructor already grants type access; the constructor signal adds nothing there.
`RuntimeReflection.register(Executable)`, used by features and by library-supplied build plugins,
registers a member without marking the declaring type as accessed.

## 2. Decision

Type access is the provider registration signal.
Registering access to the provider implementation type registers the provider implementation
class.

Access to a declared nullary constructor or to a qualifying `provider()` method also registers the
provider implementation class that the construction path resolves.
This is a subsumption rule, not a third independent policy: a build plugin that registers only a
construction path has stated a stronger intent than type access, and Native Image must not treat
that provider as unregistered while simultaneously being able to construct it.

Construction is a strictly stronger property layered on registration.
Native Image constructs a registered provider only through a declared nullary constructor on its
public, concrete implementation class or a public static nullary `provider()` method whose return
type is assignable to `Provider` on a public service provider class in an explicit module.
Type access alone never makes a provider JDK-constructible, so a type-only entry registers an
application-supplied provider without promising that the JDK can create it.

The registration boundary and the construction boundary therefore produce different failures.
An unregistered provider is absent.
A registered provider that is not JDK-constructible is usable only through the narrower
application-supplied operations.

## 3. Rejected Alternatives

**Accept only a declared nullary constructor.**
This is the smallest rule, and it was rejected for three reasons.
It breaks the diagnostic loop required by
[§FS-security-providers.4.3](../security-providers.md#43-missing-reflection-registration): the
missing-registration error identifies the provider *type* and instructs the user to add a type
entry, so a build that then still rejects the provider would send the user around the same loop.
It makes application-supplied providers unregistrable whenever the provider class has no nullary
constructor, which is exactly the case
[§FS-security-providers.5.1](../security-providers.md#51-provider-object-factory-calls) exists to
support.
It also excludes provider classes whose only JDK instantiation path is `provider()`.

**Drop the `provider()` alternative.**
Rejected because it is not a Native Image invention: it is the `ServiceLoader` instantiation
contract that the JDK itself uses when resolving configured provider entries.
A provider class that exposes a singleton or selects an implementation in `provider()` has no
usable nullary constructor, so dropping it would make such providers permanently unavailable as
JDK-managed providers even though they work on HotSpot.

**Require type access together with a construction path.**
Rejected because it adds no expressiveness in the metadata format, where a constructor entry
already implies type access, and because it would reject application-supplied providers that
[§FS-security-providers.5](../security-providers.md#5-programmatically-supplied-providers) permits.

**Introduce a provider-specific registration flag.**
Rejected by [§DF-standard-jca-semantics.2](standard-jca-semantics.md#2-decision); repeating it here
would create a second registration mechanism for the same fact.

**Widen construction to every member Native Image could invoke.**
Native Image can reach a non-public constructor, and it can call `provider()` on a class-path
provider, so treating both as construction paths would need no run-time cooperation from the JDK.
This alternative was rejected because it makes a native executable accept provider configurations
that fail on HotSpot, which is the provider-specific semantics that
[§DF-standard-jca-semantics.2](standard-jca-semantics.md#2-decision) rules out.
Construction is bounded by what the JDK does, not by what Native Image can reach.

## 4. Consequences

A user adding metadata has one rule to learn: name the provider implementation type to register it.
When the JDK, rather than the application, must create the provider, a service provider
configuration must supply a supported construction path; Native Image retains that path
automatically.
The missing-registration diagnostic can keep suggesting a plain type entry, and that suggestion is
sufficient for any provider that already declares a supported construction path.

Section 2.2 of the functional specification states the construction rule in the JDK's own terms, so
Native Image cannot treat a provider as constructible that `ServiceLoader` would refuse to
instantiate, and cannot move a HotSpot failure to a different place in the native executable.

The service provider class and provider implementation class coincide on the constructor path but
can differ on the provider-method path.
Native Image applies the registration decision, service catalog, and JCE verification result to the
implementation class.
It separately retains the service provider class and provider method so that run-time acquisition
does not bypass the factory by attempting to construct the returned implementation class directly.

The rule is expressed once and consumed at every decision point: the build-time constructibility
predicate, the build-time instantiation used to read a provider's service catalog, and the run-time
provider-list loader all derive their answer from it.
A construction path that the build accepts is therefore the path the run time takes, and a
provider that satisfies neither is reported rather than silently omitted.

Registration is deliberately cheap to express and construction is deliberately explicit, so the
size cost specified by
[§FS-security-providers.2.3](../security-providers.md#23-registration-effects) attaches to the
signal that actually asks Native Image to build the provider.
