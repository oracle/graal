# Project Terminus

Project Terminus is the effort to make Native Image self-hosted.
The goal is for Native Image to build a native executable of itself, so the resulting `native-image` builder can run without an underlying host JVM such as HotSpot.

The public tracking issue is [Project Terminus: Self-hosting Native Image](https://github.com/oracle/graal/issues/12236).
Reference epic: `GR-66203`.

Related documents:

- [Project Terminus Terminology](terminus-terminology.md)
- [Project Terminus Migration Guide](terminus-migration.md)

## Goals

- Build Native Image as a native executable that does not require a host JVM.
- Preserve compatibility for the public Native Image API in the `org.graalvm.nativeimage` packages of the [GraalVM SDK](https://www.graalvm.org/sdk/javadoc/index.html).
- Strengthen the boundary between code and state used by the image builder and code and state that belongs to the application being built.

## Motivation

Native Image currently runs as a JVM process while it builds a native executable.
The native executable contains ahead-of-time compiled machine code and an image heap derived from objects that exist during image build.

When the builder runs on HotSpot, build-time objects and application objects can share the same JVM heap.
That makes it harder to reason about whether state belongs only to the builder, only to the generated executable, or to both.
For example, application initialization at build time can capture host-specific state that is not valid when the generated executable runs.

Self-hosting Native Image also reduces the dependency on HotSpot as the execution environment for the builder.
That opens the door to running the builder in environments where porting or supporting HotSpot is not desirable.

## Build Time and Run Time

Project Terminus uses these terms.
For a more detailed terminology diagram, see [Project Terminus Terminology](terminus-terminology.md).

- Build time is the execution of Native Image that processes classes and JAR files to build a native executable.
- Run time is the execution of the generated native executable.

The current implementation historically mixes build-time and run-time state because the builder uses the HotSpot heap as the source heap for image heap construction.
Project Terminus separates those concerns more explicitly.

## Host and Guest Contexts

Project Terminus introduces separate contexts with separate heaps:

- The host context contains the Native Image builder classes and data structures used to perform ahead-of-time compilation.
- The guest context contains application classes and application initialization state.

The current host context is HotSpot.
The long-term goal is to support a Native Image based host context as well.

The target guest context is an Espresso context.
An Espresso-backed guest context lets the host query guest types, methods, fields, constants, and data through JVMCI and related access APIs without loading every application type as a host `Class<?>`.

## Guest Classes

The guest context contains the code and metadata that belongs to the application being built.
That includes:

- Application classes.
- JDK classes referenced by the application.
- Native Image run-time classes needed by the generated executable.

Native Image run-time classes can be reached from features, substitutions, invocation plugins, and other image-building mechanisms.
Under Project Terminus, hosted code should not assume that every guest type has a corresponding host `Class<?>`.

## Reflection and JVMCI

Native Image has historically used a mixture of core reflection and JVMCI to inspect classes, methods, and fields.
Core reflection depends on loading types as `Class<?>` objects through standard class loading.
That is a poor fit for a closed-world image build where application types are owned by the guest context.

Project Terminus moves hosted code toward JVMCI and VMAccess-based APIs when it needs to inspect or manipulate guest-owned program elements.
For example:

- Use `ResolvedJavaType` instead of `Class<?>` for guest types.
- Use `ResolvedJavaMethod` instead of `Method` for guest methods.
- Use `ResolvedJavaField` instead of `Field` for guest fields.
- Use `GuestAccess` and `VMAccess` when interacting with the selected guest context.

Class literals can still be useful as refactoring-safe names for known hosted classes.
They should not be used as a general substitute for guest type identity.

For API-level migration guidance, see [Project Terminus Migration Guide](terminus-migration.md).

## Class Loading Boundary

Class loader based isolation is not sufficient for Project Terminus.
Some core JDK classes must be defined by the boot class loader, so ordinary class loader boundaries cannot fully isolate builder state from guest state.
Project Terminus instead relies on a stronger context boundary with separate heaps.

## Feature Execution Model

Native Image supports public external features through the [`Feature`](https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/hosted/Feature.java) API.
External features use the public Native Image API and can currently rely on core reflection.

Native Image also has internal features through [`InternalFeature`](https://github.com/oracle/graal/blob/master/substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/feature/InternalFeature.java).
Internal features can use JVMCI and Native Image internals.

Project Terminus requires feature code to respect the host and guest boundary:

- User-facing feature behavior must remain compatible with the public Native Image API.
- Code that works with guest-owned classes, methods, fields, modules, or objects should use guest-aware APIs.
- Internal feature code must be audited so guest build-time work and host build-time work run in the appropriate context.

## Current Design Direction

The current public design direction is:

- Load application classes and Native Image run-time classes into an Espresso context.
- Keep separate copies of JDK classes where necessary, one in the host context and one in the Espresso guest context.
- Run closed-world analysis on Espresso-loaded guest types.
- Avoid assuming that an arbitrary `AnalysisType` can be converted to a `java.lang.Class`.
- Run user features in the guest context while exposing the required host services through interoperation.
- Move hosted code from core reflection to JVMCI, `GuestAccess`, and `VMAccess` for guest-owned state.
- Build the image heap from guest heap data and host-owned image-building data structures without blurring ownership.

## VMAccess

JVMCI does not cover every operation needed to manage a guest context.
Project Terminus also needs APIs for operations such as creating a guest context, invoking guest methods, accessing guest constants, and exposing selected compiler services.

The `VMAccess` API provides this broader contract.
`GuestAccess` is the Native Image entry point that delegates to the configured `VMAccess` implementation.

The host implementation preserves the pre-Terminus style of running against a host JVM and is useful as a transition path.
The Espresso implementation is the target path for a fully isolated guest context.
