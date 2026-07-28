# DF-standard-jca-semantics: Preserve Standard JCA Semantics Without Provider-Specific Flags

## 1. Context

The Java Cryptography Architecture (JCA) discovers and constructs security providers dynamically.
The JDK can read provider class names from security properties, discover providers through service
descriptors, and select providers through standard factory and fallback paths.
On the JVM, these operations can load any available class without an advance declaration.

Native Image uses closed-world analysis and must retain every class and reflective operation that
can occur at run time.
This requirement creates an inclusion boundary, but it does not require a second,
security-provider-specific configuration model.
Ordinary reflection metadata already describes access to provider types, constructors, and
qualifying `provider()` methods.

Provider-specific command-line options would expose Native Image implementation details in the
application's security configuration.
They would also make otherwise standard JCA behavior depend on how the native executable was built
rather than on the application's Java configuration.
The registration requirements and standard run-time behavior are specified by
[§FS-security-providers.1](../security-providers.md#1-provider-reflection-registration)
and [§FS-security-providers.3](../security-providers.md#3-permitted-run-time-access).

## 2. Decision

Native Image uses ordinary reflection metadata as the application-controlled registration signal
for a security provider.
When the platform must preserve an implicit JDK dependency, it supplies an equivalent registration
signal instead of requiring the application to identify the provider implementation.

After registration, Native Image preserves the observable behavior of the standard JCA APIs for
the supported provider operations.
Applications select and configure providers through standard Java APIs, security properties, and
service descriptors.
They do not need a provider-specific Native Image command-line option.

Options that select compatibility behavior during the transition to the planned defaults do not
become permanent provider-registration requirements.
Tracing and missing-registration diagnostics use ordinary reachability metadata rather than a
security-provider-specific metadata category.

## 3. Rejected Alternatives

A Native Image option that names every enabled provider was rejected because reflection metadata
already expresses the required dynamic class access.
Such an option would create a second registration mechanism and make standard Java configuration
insufficient.

Unconditionally retaining every provider was rejected because it would increase executable size
and include provider implementations that the application did not request.

Inferring all provider access from static reachability was rejected because provider names,
algorithms, and service selections can arrive only at run time.
Static analysis cannot reliably reconstruct the application's dynamic JCA configuration.

## 4. Consequences

Applications use one general Native Image mechanism for dynamic Java access.
Security-provider support does not require a permanent provider-specific enablement option or
metadata format.

An application can still need reflection metadata when it requests a provider that Native Image
cannot infer from an implicit platform dependency.
This is the ordinary closed-world registration boundary, not a change to JCA run-time semantics.

Once registered, a provider participates through the standard JCA APIs.
Missing registration produces the ordinary reachability-metadata diagnostic defined by the
functional specification.
