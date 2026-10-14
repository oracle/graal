---
layout: docs
toc_group: truffle
link_title: Automatic Close of Unreachable Context and Engine Objects in Polyglot API
permalink: /graalvm-as-a-platform/language-implementation-framework/CloseOnGc/
---

# Automatic Close of Unreachable Context and Engine Objects in Polyglot API

As of Polyglot version 24.2.0, `Context` and `Engine` objects are automatically closed when their public API objects become weakly reachable.
This feature provides fallback cleanup for unreachable `Context` and `Engine` instances.
However, garbage collection does not provide timely or unconditional cleanup.
Applications should manage the lifecycle of `Context` and `Engine` explicitly, ideally using a try-with-resources statement.

## Context Lifecycle Management

A `Context` instance is eligible for automatic closure when no strong references to it remain. These references can include the following:

* Direct strong references to the `Context` object.
* Strong references to objects bound to the `Context`, such as `Value` and `PolyglotException` instances.
* Strong references back to the `Context` from guest state. In particular, guest state can retain a host object that refers to the `Context` or to a context-bound object such as `Value`.
* Active polyglot threads or any host thread that has explicitly entered the `Context`. A `Context` that is explicitly [entered](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.html#enter()) will not be garbage-collected until it is explicitly [left](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.html#leave()).
* Inner `Context` objects, as inner contexts prevent their parent contexts from being closed if they remain reachable.

When any of these conditions hold, the `Context` instance remains active and will not be garbage-collected and closed.
The presence of active system threads does not prevent a `Context` from being closed.

### Guest-to-Host Reference Cycles

Automatic closure is not triggered if guest state retains a host object that directly or indirectly refers to the `Context` or to a context-bound Polyglot API object such as `Value`.
Explicitly close contexts that can contain such references.

A host callback invoked by guest code usually does not need to capture its owning `Context`.
It can obtain the currently entered context for the duration of each invocation:

```java
final class GuestCallback implements ProxyExecutable {
    @Override
    public Object execute(Value... arguments) {
        Context context = Context.getCurrent();
        // Use the context only during this invocation.
        return null;
    }
}
```

Do not cache the context returned by `Context.getCurrent()` in the callback.
If a callback can tolerate the context becoming unavailable, it can instead retain a `WeakReference<Context>` and handle `WeakReference.get()` returning `null`.

## Engine Lifecycle Management

The `Engine` object follows a similar approach to lifecycle management.
An `Engine` will only be closed when it no longer has any strong references to it, such as:

* Direct references to the `Engine`.
* Strong references to `Language`, `Instrument`, or `ExecutionListener` instances that were obtained from the `Engine`.

Furthermore, an `Engine` cannot be closed if there is an active `Context` within it or if any `Context` objects that use this `Engine` remain reachable.
Similar to `Context` objects, the presence of active system threads does not interfere with the automatic closure of an `Engine`.

## TruffleContext and Inner Contexts

In scenarios where a language implementation creates inner contexts using [TruffleLanguage.Env.newInnerContextBuilder()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#newInnerContextBuilder(java.lang.String...)), both the Polyglot API `Context` representing this inner context and the `TruffleContext` must be unreachable to enable automatic closure.
This situation is illustrated in the diagram below, showing an engine with one top-level context and one inner context. Strong references are represented by solid lines, and weak references by dashed lines.

![Context References](context_references.png "Context References")

When an inner `Context` is no longer referenced by the embedder, and the corresponding `TruffleContext` instance is no longer referenced by its language implementation, weak references from `PolyglotContext` to `Context` and `TruffleContext` are added to a reference queue.
During the next lifecycle event (such as when a new context is created or closed), this queue is processed, and any unreachable context is closed automatically.
