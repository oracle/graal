# Preserving AOT bytecodes for Ristretto

`-H:JITPreserveBytecodesForRuntime=<method-filter>` retains selected AOT method bodies for
inlining into Ristretto runtime compilations and for interpreting their inlined frames after
deoptimization. A nonblank selection requires a Ristretto-enabled image and is rejected otherwise.
Ordinary AOT methods keep their native
entries; this option does not enable re-JIT of arbitrary AOT frames.

## Required adapter bytecodes

Ristretto preserves a small implementation baseline even when the option is absent or blank:
all declared overloads of `Invokers$Holder.invokeExact_MT` and
`DirectMethodHandle$Holder.newInvokeSpecial`, plus `DirectMethodHandle.allocateInstance`,
`DirectMethodHandle.constructorMethod`, `MethodHandle.type`, `Invokers.checkExactType` and `Invokers.checkCustomized`
(in `java.lang.invoke`). The baseline is enabled only in Ristretto images.

These real bytecodes allow normal inlining to eliminate constructor MethodHandle adapter calls
while retaining the logical frames needed for deoptimization. The `newInvokeSpecial` frame saves
the allocated object, invokes the constructor with a void result, then returns the saved object.
Application constructors are not preserved automatically.

User filters add application selections. Their exclusions cannot remove required adapter bodies,
and strict additional-reachability checks apply to application selections, not this baseline.
Missing, unsupported or ultimately pruned required bodies fail image construction with a baseline
diagnostic. The internal `invokeBasic` and `linkToSpecial` sites in these adapter bodies use
concrete-signature interpreter intrinsics; this does not enable signature-polymorphic calls in
arbitrary preserved application bodies.

## Selecting methods

The option uses Graal's `MethodFilter` syntax. For example:

- `com.example.Work.compute` selects declared overloads and makes eligible exact selections
  analysis roots.
- `com.example.Work.compute(java.lang.String;int)` selects an exact parameter list. Parameters
  are separated by semicolons; use fully qualified names to disambiguate overloads.
- `com.example.Work.*` considers only methods already reachable during analysis.
- Negative entries, such as `~com.example.Work.slow`, exclude matching candidates.

A fully resolvable exact selection is mandatory: a missing, ambiguous, unsupported or ultimately
unretained body fails image construction. Exact instance-method roots still need an instantiated
receiver type. Wildcards do not instantiate arbitrary classes or make every matching method
reachable. Exact selections are resolved before analysis and rooted in the first `duringAnalysis`
callback, after ordinary roots are visible to the additional-reachability policy.

## Eligible bodies

Preservation keeps original bytecodes, so operations removed or rewritten by AOT optimization
still need executable interpreter semantics and image metadata. Selection rejects:

- class initializers and methods without bytecodes;
- methods forbidden by interpreter callability or heap-access restrictions;
- bodies declared in SVM core types or allocating SVM core types;
- direct accesses to deleted, guarantee-folded or injected-accessor fields, and inaccessible fields;
- bodies containing direct signature-polymorphic MethodHandle or VarHandle calls, including
  calls in branches that AOT compilation can remove;
- other direct calls that the hosted VM cannot link;
- unresolved runtime-deferred constant dynamics, including nested bootstrap arguments, and
  non-allowlisted invokedynamic bootstraps.

`InterpreterFeature.executableByInterpreter` excludes native C entry points/functions and
stub calling conventions because the interpreter entry stub supports SVM's internal Java calling
convention. An `@Uninterruptible` method is eligible only when `mayBeInlined=true` and
`callerMustBe=false`: that annotation permits calls from uninterruptible code without requiring
the method itself to execute uninterruptibly. Transitive `@RestrictHeapAccess` restrictions must
also remain unrestricted. `InterpreterFeature.callableByInterpreter` excludes `@Fold` methods
and methods whose holder, return type or parameters use `WordBase`; their hosted semantics or
word representation cannot be invoked through ordinary Java interpreter calls.

Substitution alone is not a blanket exclusion. The checks above determine whether the original
operations still have valid runtime semantics. Supported resolved constants include Java null.
Unresolved ordinary symbols retain their existing lazy failure behavior where supported.

Application bodies containing signature-polymorphic calls are rejected before preservation registers their dependencies.
Exact selections fail image construction; wildcard selections skip the body and keep its
ordinary AOT entry. Supported preserved callers can still call that entry. Runtime-loaded
MethodHandle and VarHandle calls continue to use normal runtime constant-pool resolution.

A selected body exposes its original dependencies during analysis, including branches removed
from the AOT graph. The dependency pass retains necessary callees, types, field accesses,
image-heap constants and dynamic-call appendices. Heap restrictions are checked again before
compilation. Pool hydration then prunes bodies whose final callees cannot execute. Newly resolved
unsafe field, invocation or constant semantics at final analysis always fail image construction.

## Bootstrap Metadata Inspection

Preservation inspects the loaded constant pool without invoking deferred bootstrap methods.
Before inspecting an `invokedynamic` site, it checks the already resolved values of every dynamic constant in the class's pool.
If any dynamic constant is unresolved, the body is excluded before the indy metadata lookup, which could otherwise execute that constant's bootstrap.
This also excludes an otherwise safe indy when an unrelated method in the same class has an unresolved dynamic constant, even if the constant's bootstrap is allowlisted.
Already resolved constants, including a resolved Java null, do not cause this exclusion.
Ordinary indy sites in pools without unresolved dynamic constants retain the bootstrap allowlist checks.
Unresolved constants loaded by `LDC` retain their existing nested-bootstrap classification.

The `ce-next-preserve-java-base` and `crema-ee-preserve-java-base` mx profiles select reachable java.base package families.
In the CE profile, preservation options apply to `lib:jvm`; the JVMCI compiler, agents, and Native Image launcher retain their own configuration.
The profiles and `ristretto_preserve_bytecodes` CI gate use the ordinary builder configuration.
You do not need to set a bootstrap metadata VM flag.
Affected exact selections fail with a reason; wildcard selections skip the body and can report that reason through the opt-in diagnostics below.

For the enterprise `svmtest` command:

```sh
mx svmtest --distribution=SVM_ENTERPRISE_TEST_INTERPRETER RistrettoPreserveBytecodesTest
```

## Failure policies and diagnostics

Both failure switches currently default to `false`:

| Option | Enabled | Disabled (default) |
|---|---|---|
| `JITPreserveBytecodesFailOnAdditionalReachability` | Fail before registering a new body dependency. | Retain the dependency; opt-in logging is deduplicated by dependency. |
| `JITPreserveBytecodesFailOnLateRejection` | Fail when a wildcard-selected body is removed after retention. | Remove the body; dependencies already registered can remain. Log the rejection only when requested. |

Use `-H:+<option>` to enable either policy. The additional-reachability check describes state at
registration time, not a counterfactual analysis with preservation disabled. Feature infrastructure
metadata is separate from per-body dependencies.

Enable `-H:Log=PreserveBytecodes` to log added reachability, unsupported wildcard matches and
late wildcard rejections. These diagnostics are quiet by default. Debug logging can be directed
to a file with `-J-Djdk.graal.LogFile=preserved-bytecodes.log`.

Early wildcard exclusions are summarized with
at most 20 details. Exact selections remain mandatory regardless of the late-rejection flag, and
neither flag disables final-analysis safety checks.

## Hosted ownership

`RistrettoFeature` delegates lifecycle checks to `RistrettoPreserveBytecodeSupport`. Selection,
eligibility inspection and dependency registration have separate hosted helpers. All mutable
selection, rejection and pending-appendix state belongs to one image build.

`CremaFeature` initializes generic interpreter fields. After that callback, `RistrettoFeature`
hydrates method-body pools and checks which selected bodies survived. Debugger images use
`DebuggerFeature` for complete interpreter pools; the current option validator rejects combining
`DebuggerWithInterpreter` (or JDWP) with Ristretto in one image.
Pool classes, unresolved entries and exception-handler metadata must be registered during
analysis because hydration populates them after analysis has sealed reachability.

GR-78803 tracks future jar/instrumentation-based body loading. GR-80125 tracks a replacement
for the current sequential analysis-registration callback.
