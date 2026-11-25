# Polyglot Embedding Guidelines

For applications embedding guest languages with `org.graalvm.polyglot`.
The before and after examples are illustrative; they omit imports and surrounding code.

**For review agents:** Work through the index one guideline at a time. For each, record a finding with a code reference, explain why the rule does not apply, or note what you checked and found no issue with. Mark anything you could not verify; do not count it as a pass.

## Index

**[Engine configuration and deployment](#engine-configuration-and-deployment) · 5 guidelines**

| Code | Guideline |
| --- | --- |
| [`PG-ENGINE-OPTIONS`](#configure-shared-engine-options-on-the-engine) | Set engine and instrument options on the builder that owns the shared engine. |
| [`PG-ISOLATE-CONFIGURATION`](#match-isolate-configuration-to-permitted-languages-and-dependencies) | Match the permitted language IDs and installed artifacts to the isolate configuration. |
| [`PG-LANGUAGE-DEPENDENCIES`](#include-the-required-language-artifacts-in-the-deployment) | Package the guest language artifacts and verify their availability in the deployed engine. |
| [`PG-RESOURCE-COPY`](#prepare-resource-trees-through-the-public-copy-operation) | Use Engine.copyResources to prepare the selected components’ resource trees. |
| [`PG-EXPERIMENTAL-OPTIONS`](#keep-experimental-options-out-of-default-production-configuration) | Keep experimental options disabled unless the application deliberately opts in. |

**[Host permissions and file access](#host-permissions-and-file-access) · 4 guidelines**

| Code | Guideline |
| --- | --- |
| [`PG-HOST-PRIVILEGES`](#audit-the-actual-grants-of-a-host-access-policy) | Inspect the operations a host-access policy permits instead of relying on its name. |
| [`PG-INTERNAL-RESOURCES`](#expose-internal-resources-through-the-read-only-adapter) | Expose internal resources through the read-only adapter without granting unrelated file access. |
| [`PG-COMPOSITE-FILESYSTEM`](#configure-the-composites-working-directory-not-its-delegates) | Configure relative-path resolution on the composite file system before it routes requests. |
| [`PG-IO-PRIVILEGES`](#choose-io-grants-with-an-explicit-upgrade-policy) | Choose I/O grants explicitly so an upgrade does not silently widen access. |

**[Context lifecycle and cancellation](#context-lifecycle-and-cancellation) · 6 guidelines**

| Code | Guideline |
| --- | --- |
| [`PG-ENTER-LEAVE`](#restore-explicit-context-entry-on-exceptional-paths) | Pair explicit context entry with a finally block that restores the thread’s previous state. |
| [`PG-CANCELLATION`](#do-not-reuse-a-cancelled-context-or-conflate-failure-properties) | Check exception properties independently and retire a cancelled context before retrying. |
| [`PG-ENTERED-WAITS`](#avoid-keeping-an-idle-coordinator-entered-while-workers-run) | Leave the context before an idle coordinator waits for guest workers. |
| [`PG-CALLBACK-SCOPING`](#scope-host-callback-parameters-to-their-actual-lifetime) | Copy callback data or deliberately extend its lifetime before method scope ends. |
| [`PG-CONTEXT-LIFETIME`](#close-owned-contexts-and-engines-without-relying-on-gc) | Close owned contexts before their shared engine instead of waiting for garbage collection. |
| [`PG-GUEST-EXIT`](#keep-guest-exit-from-terminating-the-host-process) | Prevent a guest exit request from ending a host process that must continue running. |

**[Sources, evaluation, and reuse](#sources-evaluation-and-reuse) · 3 guidelines**

| Code | Guideline |
| --- | --- |
| [`PG-MEMBER-DISPATCH`](#capture-a-callable-only-when-repeated-calls-should-use-that-value) | Capture a guest callable once only if later calls should ignore changes to the member. |
| [`PG-SOURCE-CACHE`](#retain-reusable-sources-for-the-intended-code-cache-lifetime) | Keep reusable sources alive for the period when contexts should share eligible cached code. |
| [`PG-PARSED-EXECUTION`](#do-not-treat-a-parsed-value-as-a-memoized-result) | Execute a parsed value once and retain its result when evaluation must not repeat. |

**[Guest values and Java mappings](#guest-values-and-java-mappings) · 8 guidelines**

| Code | Guideline |
| --- | --- |
| [`PG-INTERFACE-MAPPING`](#match-typed-interfaces-to-guest-members-and-mapping-policy) | Match Java interface methods to guest members and grant only the mappings the adapter needs. |
| [`PG-VALUE-CAPABILITIES`](#inspect-guest-values-through-public-capabilities) | Check a guest value’s public capabilities before converting it to a host type. |
| [`PG-LANGUAGE-BINDINGS`](#select-the-intended-languages-top-level-bindings) | Read a language export from that language’s bindings, not the polyglot exchange bindings. |
| [`PG-NUMERIC-CONVERSION`](#request-the-numeric-value-rather-than-guessing-its-java-wrapper) | Request the numeric conversion the host needs rather than guessing a Java wrapper class. |
| [`PG-ITERATOR-CONTRACT`](#preserve-proxy-cursor-and-failure-semantics) | Advance a proxy iterator only after successfully obtaining the next element. |
| [`PG-TARGET-MAPPING`](#separate-conversion-order-from-overload-precedence) | Choose mapping predicates and precedence for the Java overloads the guest should select. |
| [`PG-HASH-ENTRIES`](#use-the-hash-protocol-for-guest-dictionary-entries) | Use hash-entry operations for dictionaries and distinguish missing keys from guest nulls. |
| [`PG-FAILURE-PROTOCOL`](#handle-the-public-operations-documented-failure) | Translate the documented failure of the specific Polyglot operation being called. |

**[Output, logging, and diagnostics](#output-logging-and-diagnostics) · 3 guidelines**

| Code | Guideline |
| --- | --- |
| [`PG-RESOURCE-OUTPUT`](#bound-host-side-output-independently-of-guest-memory) | Bound or discard guest output independently of guest memory limits. |
| [`PG-DIAGNOSTICS`](#isolate-guest-driven-diagnostic-failures) | Record a host-only diagnostic before attempting guest inspection that may itself fail. |
| [`PG-LOG-ROUTING`](#choose-one-intended-logging-destination-per-scope) | Configure the log destination at the scope that should own the capture. |

## Engine configuration and deployment

<a name="configure-shared-engine-options-on-the-engine"></a>

### `PG-ENGINE-OPTIONS` — Configure shared engine options on the engine

**When it applies:** Multiple contexts use an explicitly constructed `Engine`.

**What goes wrong:** You attach an explicit engine and then set engine or instrument options on each `Context.Builder`.

**Why it matters:** With an explicit engine, a context builder accepts language options, not engine or instrument options. Build-time validation rejects misplaced options; a shared engine cannot have contradictory per-context engine configuration.

**How to fix it:** Set engine and instrument options on `Engine.Builder` before building the shared engine. Set language options at the intended engine-default or context-override level.

**Before**

```java
try (Engine engine = Engine.create();
                Context context = Context.newBuilder()
                    .engine(engine)
                    .option(engineOption, optionValue)
                    .build()) {
    context.eval(source);
}
```

**After**

```java
try (Engine engine = Engine.newBuilder()
                    .option(engineOption, optionValue)
                    .build();
                Context context = Context.newBuilder()
                    .engine(engine)
                    .build()) {
    context.eval(source);
}
```

---

<a name="match-isolate-configuration-to-permitted-languages-and-dependencies"></a>

### `PG-ISOLATE-CONFIGURATION` — Match isolate configuration to permitted languages and dependencies

**When it applies:** You explicitly select polyglot isolate execution through public Engine or Context builders, available since 25.1, or configure its policy-dependent options.

**What goes wrong:** You omit required permitted IDs, ignore an explicit engine's inherited IDs, mismatch artifacts and execution mode, or copy resource-option values without their sandbox-policy constraints.

**Why it matters:** The engine needs an explicit language set to select isolate execution. Finding an isolate artifact is not the same as finding a regular language dependency for non-isolated execution.

**How to fix it:** Supply the intended permitted language IDs and dependencies appropriate to the selected execution mode and platform. For an explicit shared engine, configure spawning on `Engine.Builder`, not again on its context builders.

**Before**

```java
Engine engine = Engine.newBuilder().spawnIsolate(true).build();
```

**After**

```java
try (Engine engine = Engine.newBuilder(languageId)
                .spawnIsolate(true).build()) {
    try (Context context = Context.newBuilder(languageId)
                    .engine(engine).build()) {
        runApplication(context);
    }
}
```

---

<a name="include-the-required-language-artifacts-in-the-deployment"></a>

### `PG-LANGUAGE-DEPENDENCIES` — Include the required language artifacts in the deployment

**When it applies:** Your host application uses the Maven-based dependency setup available since Polyglot API 23.1.

**What goes wrong:** You assume the API dependency installs a guest language, or copy a language or tool dependency without its required `pom` type.

**Why it matters:** Compiling against `org.graalvm.polyglot` establishes API availability, not availability of each language or tool in the deployed application. The language and tool dependencies described by the embedding guide are POM artifacts that supply their dependencies.

**How to fix it:** Include the API dependency and the selected language or tool dependencies using the embedding guide's setup for your release. For those language and tool dependencies, specify `<type>pom</type>` and keep the selected artifact versions aligned.

**Before**

```java
static void checkDeployment(String requiredLanguageId) {
    try (Engine engine = Engine.create()) {
        // Engine creation alone does not establish that this language is installed.
    }
}
```

**After**

```java
static void checkDeployment(String requiredLanguageId) {
    try (Engine engine = Engine.create()) {
        if (!engine.getLanguages().containsKey(requiredLanguageId)) {
            throw new IllegalStateException(
                    "Required guest language is not installed: " + requiredLanguageId);
        }
    }
}
```

---

<a name="prepare-resource-trees-through-the-public-copy-operation"></a>

### `PG-RESOURCE-COPY` — Prepare resource trees through the public copy operation

**When it applies:** You prepare an application-managed resource directory for installed languages or tools.

**What goes wrong:** You reconstruct a resource tree from public component listings and copy only each selected component's apparent own files.

**Why it matters:** Selecting a public component and identifying every resource needed by its implementation are different tasks. A hand-built tree can omit dependent components or support resources and can have a layout that the runtime does not recognize.

**How to fix it:** Use `Engine.copyResources` (since 23.1) with the installed component IDs your application selects, or omit IDs to copy resources for all installed components. Use public language and instrument descriptors for application-facing selection, not guessed implementation IDs or resource-directory names.

**Before**

```java
for (String languageId : selectedLanguageIds) {
    copyOnlyAdvertisedFiles(languageId, resourceDirectory);
}
```

**After**

```java
void prepareResources(Path resourceDirectory, String... selectedLanguageIds)
                throws IOException {
    try (Engine installed = Engine.create()) {
        for (String id : selectedLanguageIds) {
            if (!installed.getLanguages().containsKey(id)) {
                throw new IllegalArgumentException("Unavailable language: " + id);
            }
        }
    }
    boolean copied = Engine.copyResources(resourceDirectory, selectedLanguageIds);
    recordResourcePreparation(resourceDirectory, copied);
}
```

---

<a name="keep-experimental-options-out-of-default-production-configuration"></a>

### `PG-EXPERIMENTAL-OPTIONS` — Keep experimental options out of default production configuration

**When it applies:** You build reusable engine or context configuration, especially when forwarding application-supplied options.

**What goes wrong:** You unconditionally enable experimental options in a general-purpose production builder to avoid option-validation failures.

**Why it matters:** The builders reject experimental options by default, and their contracts explicitly advise against using them in production. An unconditional opt-in removes that default check for every option supplied through the builder, not just the option that motivated it.

**How to fix it:** Keep the default gate disabled in production and configure supported options deliberately. If a non-production experiment requires an experimental option, make that opt-in explicit in the experiment's configuration rather than a shared production default.

**Before**

```java
String evaluate(Source source, Map<String, String> engineOptions) {
    try (Engine engine = Engine.newBuilder()
                    .allowExperimentalOptions(true)
                    .options(engineOptions)
                    .build();
                    Context context = Context.newBuilder()
                                    .engine(engine)
                                    .build()) {
        return context.eval(source).asString();
    }
}
```

**After**

```java
String evaluate(Source source, Map<String, String> stableEngineOptions) {
    try (Engine engine = Engine.newBuilder()
                    .options(stableEngineOptions)
                    .build();
                    Context context = Context.newBuilder()
                                    .engine(engine)
                                    .build()) {
        return context.eval(source).asString();
    }
}
```

---

## Host permissions and file access

<a name="audit-the-actual-grants-of-a-host-access-policy"></a>

### `PG-HOST-PRIVILEGES` — Audit the actual grants of a host access policy

**When it applies:** You configure context privileges, expose host objects, or permit guests to implement host interfaces or extend host classes.

**What goes wrong:** You treat a `HostAccess` policy as a blanket restriction on guest behavior, or assume methods on guest-created implementations stay inaccessible because they lack your export annotation.

**Why it matters:** `HostAccess` controls access to Java host objects, not all guest objects or guest-language capabilities. For example, disabling host buffer access does not disable a guest's own buffer values.

**How to fix it:** Choose the host-access baseline explicitly when the application owns that decision. Do not substitute `allowAllAccess(true)` when the requirement concerns only host-member access: it changes defaults for unrelated privileges and future privileges too.

**Before**

```java
public interface Service {
    void onResult(int result);
    void administrativeAction();
}

HostAccess access = HostAccess.newBuilder(HostAccess.NONE)
                .allowImplementations(Service.class)
                .allowBufferAccess(false)
                .build();
// Wrong assumptions: unannotated implementation methods are hidden,
// and all guest buffer operations have been disabled.
```

**After**

```java
public interface ResultCallback {
    void onResult(int result);
}

HostAccess access = HostAccess.newBuilder(HostAccess.NONE)
                .allowImplementations(ResultCallback.class)
                .allowBufferAccess(false) // Only the host-buffer access grant.
                .build();
try (Context context = Context.newBuilder(source.getLanguage())
                .allowHostAccess(access).build()) {
    // The application deliberately permits this narrow guest implementation.
    ResultCallback callback = context.eval(source).as(ResultCallback.class);
    callback.onResult(42); // Invokes guest code; keep its lifetime inside context.
}
```

---

<a name="expose-internal-resources-through-the-read-only-adapter"></a>

### `PG-INTERNAL-RESOURCES` — Expose internal resources through the read-only adapter

**When it applies:** A custom file system must allow access to installed language homes or internal resources.

**What goes wrong:** You use the deprecated `FileSystem.allowInternalResourceAccess` assuming it exposes only read access, or assume the replacement makes the application's entire file system read-only.

**Why it matters:** The old adapter preserves historical write access to internal resource paths, subject to the underlying default file system's permissions. The replacement protects internal resources, while unrelated paths retain the configured delegate's permissions.

**How to fix it:** On versions providing it (since 25.1), use `FileSystem.allowInternalResources` for the read-only internal-resource view. Choose the delegate's permissions independently for application paths.

**Before**

```java
FileSystem exposed = FileSystem.allowInternalResourceAccess(applicationFiles);
IOAccess io = IOAccess.newBuilder().fileSystem(exposed).build();
```

**After**

```java
FileSystem exposed = FileSystem.allowInternalResources(applicationFiles);
IOAccess io = IOAccess.newBuilder().fileSystem(exposed).build();
try (Context context = Context.newBuilder(languageId)
                .allowIO(io).build()) {
    runApplication(context);
}
```

---

<a name="configure-the-composites-working-directory-not-its-delegates"></a>

### `PG-COMPOSITE-FILESYSTEM` — Configure the composite's working directory, not its delegates

**When it applies:** You compose public FileSystem implementations with FileSystem.newCompositeFileSystem.

**What goes wrong:** You assume the composite forwards every operation, or configure a selected delegate's working directory expecting that to configure the composite's relative-path routing.

**Why it matters:** The composite owns its working directory and does not call setCurrentWorkingDirectory on its delegates. Once explicitly set, it resolves and normalizes relative paths before selecting a delegate, and handles toAbsolutePath itself.

**How to fix it:** Configure the composite itself and make path selectors consistent with the path forms it supplies. Use the same `Path` type, filename separator, and path-list separator for every participating file system; the fallback parses paths.

**Before**

```java
FileSystem composite = FileSystem.newCompositeFileSystem(fallback, routes);
selectedDelegate.setCurrentWorkingDirectory(directory); // Wrong owner.
Path target = composite.toAbsolutePath(composite.parsePath("input.dat"));
// Assumes that setting one delegate configured the composite.
```

**After**

```java
FileSystem configureFiles(FileSystem fallback,
                FileSystem.Selector[] routes, Path directory) {
    FileSystem composite = FileSystem.newCompositeFileSystem(fallback, routes);
    composite.setCurrentWorkingDirectory(directory);
    return composite;
}

FileSystem composite = configureFiles(fallback, routes, directory);
Path target = composite.toAbsolutePath(composite.parsePath("input.dat"));
```

---

<a name="choose-io-grants-with-an-explicit-upgrade-policy"></a>

### `PG-IO-PRIVILEGES` — Choose I/O grants with an explicit upgrade policy

**When it applies:** You configure guest file or socket access, including when reviewing a runtime upgrade.

**What goes wrong:** You use `IOAccess.ALL` as though it were a permanently fixed list of today's privileges, or interpret `IOAccess.NONE` as merely blocking direct host-file access while leaving arbitrary guest IO enabled.

**Why it matters:** The presets include an evolution policy: newly added privileges default to true for `ALL` and false for `NONE`. A broad preset can therefore grant more authority after an upgrade without a source-code change.

**How to fix it:** Use `IOAccess.NONE` when the application needs no ordinary guest IO, or start with a fresh deny-by-default builder and enable only the required privileges. Audit each granted capability and the selected sandbox policy together.

**Before**

```java
void execute(Source program) {
    try (Context context = Context.newBuilder()
                    .allowIO(IOAccess.ALL).build()) {
        context.eval(program);
    }
}
```

**After**

```java
void execute(Source program) {
    IOAccess io = IOAccess.newBuilder()
                    .allowHostFileAccess(false)
                    .allowHostSocketAccess(true)
                    .build();
    try (Context context = Context.newBuilder()
                    .allowIO(io).build()) {
        context.eval(program);
    }
}
```

---

## Context lifecycle and cancellation

<a name="restore-explicit-context-entry-on-exceptional-paths"></a>

### `PG-ENTER-LEAVE` — Restore explicit context entry on exceptional paths

**When it applies:** You explicitly enter a polyglot context for a batch of work and keep the context alive afterward.

**What goes wrong:** You put `leave()` after work that can throw, allowing an exception to skip restoration of the thread's entered-context state.

**Why it matters:** Explicit entry changes thread-local execution state. If an exception skips the matching leave, later work can observe an unintended entered context or unbalanced entry nesting.

**How to fix it:** Pair a successful explicit `enter()` with `leave()` in a `finally` block when the scope returns with the context still open. For ordinary individual operations, prefer implicit entry unless explicit batching serves a purpose.

**Before**

```java
context.enter();
context.eval(source); // An exception skips leave().
context.leave();
```

**After**

```java
context.enter();
try {
    context.eval(source);
} finally {
    context.leave();
}
```

---

<a name="do-not-reuse-a-cancelled-context-or-conflate-failure-properties"></a>

### `PG-CANCELLATION` — Do not reuse a cancelled context or conflate failure properties

**When it applies:** You classify execution failures, impose resource limits, or stop running guest work.

**What goes wrong:** You treat cancellation and resource exhaustion as equivalent, reset limits to revive a cancelled context, or assume forced cancellation finishes guest cleanup.

**Why it matters:** Exception properties describe different facts and can overlap. `isCancelled()` means the context is no longer usable; an explicit `close(true)` can cancel execution without exhausting a resource.

**How to fix it:** Record relevant exception properties independently, retire cancelled contexts and their context-bound values, and make any retry a deliberate application decision with a suitable new context. Do not automatically replay work that may already have produced side effects.

**Before**

```java
try {
    context.eval(source);
} catch (PolyglotException failure) {
    if (failure.isResourceExhausted()) {
        context.resetLimits();
        context.eval(source); // May be a closed context; also replays side effects.
    } else if (failure.isCancelled()) {
        recordResourceLimit(); // Cancellation need not be resource exhaustion.
    } else {
        throw failure;
    }
}
```

**After**

```java
// This operation owns the context; no context-bound Value escapes.
void executeOnce(Source source) {
    try (Context context = Context.newBuilder(source.getLanguage()).build()) {
        context.eval(source);
    } catch (PolyglotException failure) {
        recordFailure(failure.isCancelled(), failure.isResourceExhausted(),
                        failure.isInterrupted(), failure.isExit());
        if (failure.isHostException()) {
            recordHostFailure(failure.asHostException());
        }
        throw failure; // No attempt to revive or blindly replay this context.
    }
}
```

---

<a name="avoid-keeping-an-idle-coordinator-entered-while-workers-run"></a>

### `PG-ENTERED-WAITS` — Avoid keeping an idle coordinator entered while workers run

**When it applies:** An explicitly entered host thread waits for independent workers using the same context.

**What goes wrong:** A coordinator remains entered while waiting without servicing events that workers need it to process.

**Why it matters:** Cross-thread runtime coordination can require the entered coordinator to reach a safepoint. Worker completion can then depend on a thread that is blocked waiting for that very completion.

**How to fix it:** When the coordinator has no guest work to perform, leave the context before waiting and let workers enter only for their own work. Where long host work must remain entered, make event servicing deliberate using public `Context.safepoint()` at appropriate bounded intervals; do not replace all waits with a busy loop.

**Before**

```java
context.enter();
try {
    Future<?> worker = executor.submit(() -> context.eval(source));
    worker.get(); // Coordinator remains entered and does not service events.
} finally {
    context.leave();
}
```

**After**

```java
Future<?> worker = executor.submit(() -> context.eval(source));
worker.get(); // Coordinator never entered; eval enters for the worker operation.
```

---

<a name="scope-host-callback-parameters-to-their-actual-lifetime"></a>

### `PG-CALLBACK-SCOPING` — Scope host callback parameters to their actual lifetime

**When it applies:** Guest code passes values to host callbacks, especially when host and guest use separate heaps.

**What goes wrong:** You leave callback parameters unscoped by default, or enable scoping but retain a callback's unpinned guest value for later use.

**Why it matters:** Separate host and guest garbage collectors cannot automatically resolve cycles spanning both heaps. Long-lived cross-heap references can retain objects unnecessarily.

**How to fix it:** Enable method scoping on the intended host-access policy, and consume callback arguments during the callback whenever possible. Copy the required data to an independently owned host representation instead of keeping the guest value when that meets the application contract.

**Before**

```java
public final class Results {
    private Value last;

    @HostAccess.Export
    public void accept(Value result) {
        last = result; // Retains a guest reference after the callback.
    }
}
```

**After**

```java
HostAccess access = HostAccess.newBuilder(HostAccess.EXPLICIT)
                .methodScoping(true)
                .build();

public final class Results {
    private String lastText;

    @HostAccess.Export
    public void accept(Value result) {
        lastText = result.asString(); // Consume while the scope is still valid.
    }
}

try (Context context = Context.newBuilder(languageId)
                .allowHostAccess(access).build()) {
    Results results = new Results();
    context.getBindings(languageId).putMember("results", results);
    runGuestCallbacks(context);
}
```

---

<a name="close-owned-contexts-and-engines-without-relying-on-gc"></a>

### `PG-CONTEXT-LIFETIME` — Close owned contexts and engines without relying on GC

**When it applies:** Your application owns contexts or engines that become unused, or coordinates stopping active execution before ending their lifetimes.

**What goes wrong:** You rely on GC for prompt closure, retain context-bound references while expecting automatic closure, or treat an interruption timeout as successful termination before releasing resources used by active execution.

**Why it matters:** Automatic closure depends on reachability and execution state, and garbage collection is nondeterministic. A retained Value or PolyglotException can retain its context.

**How to fix it:** Explicitly close owned contexts and engines when their intended lifetimes end, preferably with try-with-resources. Close per-operation contexts before the shared engine; do not close an engine still owned by another application component.

**Before**

```java
int[] evaluateBatch(Source[] sources) {
    int[] results = new int[sources.length];
    Engine engine = Engine.create();
    for (int i = 0; i < sources.length; i++) {
        Context context = Context.newBuilder().engine(engine).build();
        results[i] = context.eval(sources[i]).asInt();
    }
    return results; // Neither contexts nor engine are closed.
}
```

**After**

```java
int[] evaluateBatch(Source[] sources) {
    int[] results = new int[sources.length];
    try (Engine engine = Engine.create()) {
        for (int i = 0; i < sources.length; i++) {
            try (Context context = Context.newBuilder().engine(engine).build()) {
                results[i] = context.eval(sources[i]).asInt();
            }
        }
    }
    return results;
}
```

---

<a name="keep-guest-exit-from-terminating-the-host-process"></a>

### `PG-GUEST-EXIT` — Keep guest exit from terminating the host process

**When it applies:** A long-lived host application executes guest code that can request application exit, including an embedded test harness or service.

**What goes wrong:** You enable process-wide exit for embedded execution even though only the guest context should end, or assume guest exit always leaves the context usable.

**Why it matters:** `Context.Builder.useSystemExit(true)` permits a guest hard exit to terminate the whole host application. No surrounding host exception handler can then be relied on to continue the service or report the remaining work.

**How to fix it:** Make the host's lifecycle policy explicit with `useSystemExit(false)` when guest exit must not end the host process. Handle exit through `isExit()` and `getExitStatus()` at the embedding boundary, and propagate unrelated failures.

**Before**

```java
void runGuest(Source source) {
    try (Context context = Context.newBuilder()
                    .useSystemExit(true)
                    .build()) {
        context.eval(source); // A guest hard exit may terminate this host.
    }
    reportHostContinues();
}
```

**After**

```java
void runGuest(Source source) {
    try (Context context = Context.newBuilder()
                    .useSystemExit(false)
                    .build()) {
        context.eval(source);
    } catch (PolyglotException failure) {
        if (!failure.isExit()) {
            throw failure;
        }
        reportGuestExit(failure.getExitStatus());
    }
    reportHostContinues();
}
```

---

## Sources, evaluation, and reuse

<a name="capture-a-callable-only-when-repeated-calls-should-use-that-value"></a>

### `PG-MEMBER-DISPATCH` — Capture a callable only when repeated calls should use that value

**When it applies:** You repeatedly call an independently executable member whose identity is intended to remain fixed for the operation.

**What goes wrong:** You repeatedly look up that stable callable, or mechanically replace invokeMember with getMember(...).execute(...) without checking the receiver and lookup semantics.

**Why it matters:** Capturing a member once avoids repeating its lookup, but also captures one value rather than observing later member replacement or repeating getter effects. Value.invokeMember has object-oriented member invocation semantics; executing a detached value is not universally equivalent.

**How to fix it:** For a stable, independently executable export, read it once and retain that Value for the intended batch or context-owned lifetime. Use invokeMember when receiver semantics or fresh member resolution are required.

**Before**

```java
void runBatch(Value exports, int[] inputs) {
    for (int input : inputs) {
        exports.getMember("work").executeVoid(input);
    }
}
```

**After**

```java
void runBatch(Value exports, int[] inputs) {
    if (inputs.length == 0) {
        return; // Preserve the no-lookup behavior of an empty batch.
    }
    Value work = exports.getMember("work");
    if (work == null || !work.canExecute()) {
        throw new IllegalArgumentException("Missing executable work export");
    }
    for (int input : inputs) {
        work.executeVoid(input);
    }
}
```

---

<a name="retain-reusable-sources-for-the-intended-code-cache-lifetime"></a>

### `PG-SOURCE-CACHE` — Retain reusable sources for the intended code-cache lifetime

**When it applies:** You evaluate the same program in multiple polyglot contexts and want eligible code-cache reuse.

**What goes wrong:** You discard the program's `Source` after preparation, rebuild it for each execution, or expect different engines to share their cached code.

**Why it matters:** A cached source's reachability matters to cache lifetime, and an engine defines the scope of cross-context sharing. Keeping only the program text or a source name does not keep that `Source` alive.

**How to fix it:** Retain a reusable `Source` for the desired bounded lifetime and use one explicitly owned engine for contexts intended to share eligible code. Keep guest state in fresh contexts when required; do not retain a context-bound `Value` merely to anchor a source cache.

**Before**

```java
void execute(String languageId, String programText) {
    try (Context context = Context.create(languageId)) {
        context.eval(Source.create(languageId, programText));
    }
}
```

**After**

```java
final class ProgramRunner implements AutoCloseable {
    private static final Source PROGRAM =
                    Source.create(LANGUAGE_ID, PROGRAM_TEXT);
    private final Engine engine = Engine.create();

    void execute() {
        try (Context context = Context.newBuilder(LANGUAGE_ID)
                        .engine(engine).build()) {
            context.eval(PROGRAM);
        }
    }

    @Override
    public void close() {
        engine.close();
    }
}
```

---

<a name="do-not-treat-a-parsed-value-as-a-memoized-result"></a>

### `PG-PARSED-EXECUTION` — Do not treat a parsed value as a memoized result

**When it applies:** You use `Context.parse` to validate or prepare a source before evaluating it.

**What goes wrong:** You execute the parsed value just to retrieve a supposedly cached result, or assume successful parsing means the source already ran.

**Why it matters:** `Context.parse` returns an executable value, not the evaluation result. Each execution evaluates the source again and can repeat its effects.

**How to fix it:** Separate preparation, deliberate execution, and storage of an application-required result. The parsed entry accepts an empty argument list; do not treat it as an arbitrary guest function accepting application parameters.

**Before**

```java
try (Context context = Context.newBuilder(source.getLanguage()).build()) {
    Value parsed = context.parse(source);
    parsed.execute(); // Performs the evaluation once.
    Value result = parsed.execute(); // Not a read of that result: evaluates again.
    consume(result);
}
```

**After**

```java
try (Context context = Context.newBuilder(source.getLanguage()).build()) {
    Value parsed = context.parse(source);
    Value result = parsed.execute();
    consume(result);
    inspectSameResult(result); // Application must not re-execute the entry.
}
```

---

## Guest values and Java mappings

<a name="match-typed-interfaces-to-guest-members-and-mapping-policy"></a>

### `PG-INTERFACE-MAPPING` — Match typed interfaces to guest members and mapping policy

**When it applies:** You map a member-bearing guest object to a Java interface with `Value.as`.

**What goes wrong:** You treat the interface name as a guest type declaration, assume every interface is implementable under the configured host-access policy, or expect conversion to validate every future method call.

**Why it matters:** Member-based interface mapping connects Java method names to guest members and converts arguments and results using the target-mapping rules. A missing member can fail when that Java method is called, and an incompatible argument or result can fail conversion then.

**How to fix it:** Define the Java methods from the guest object's actual member contract. Enable implementation of the intended interface and the required member-to-interface mapping, not unrelated host access.

**Before**

```java
try (Context context = Context.newBuilder(source.getLanguage())
                .allowHostAccess(HostAccess.NONE).build()) {
    Value result = context.eval(source);
    Service service = result.as(Service.class); // Required mapping is not granted.
    service.format("item");
}
```

**After**

```java
public interface Service {
    String format(String input);
    boolean ready();
}

HostAccess access = HostAccess.newBuilder(HostAccess.EXPLICIT)
                .allowImplementations(Service.class)
                .allowMutableTargetMappings(
                    HostAccess.MutableTargetMapping.MEMBERS_TO_JAVA_INTERFACE)
                .build();
try (Context context = Context.newBuilder(source.getLanguage())
                .allowHostAccess(access).build()) {
    Value result = context.eval(source);
    Service service = result.as(Service.class);
    if (service.ready()) {
        consume(service.format("item"));
    }
}
```

---

<a name="inspect-guest-values-through-public-capabilities"></a>

### `PG-VALUE-CAPABILITIES` — Inspect guest values through public capabilities

**When it applies:** You inspect or convert a guest result at a host integration boundary.

**What goes wrong:** You depend on a guest implementation's Java class or assume that default mapping to `Object` returns the particular Java wrapper you want.

**Why it matters:** `Value` exposes language-independent capabilities rather than a guest implementation's private class hierarchy. The default Java mapping is not a request for one exact target type.

**How to fix it:** Define the capability the integration accepts, then use its public query and accessor. For a boundary accepting only string values, use `isString()` and `asString()`; reject other values under the application's own contract instead of guessing private carriers or parsing display text.

**Before**

```java
String readLabel(Value result) {
    return (String) result.as(Object.class);
}
```

**After**

```java
String readLabel(Value result) {
    if (!result.isString()) {
        throw new IllegalArgumentException("Expected a string label");
    }
    return result.asString();
}
```

---

<a name="select-the-intended-languages-top-level-bindings"></a>

### `PG-LANGUAGE-BINDINGS` — Select the intended language's top-level bindings

**When it applies:** Your integration reads a symbol that a particular guest language exposes through its top-level bindings.

**What goes wrong:** You depend on a private globals-dictionary implementation or look in the polyglot exchange namespace as though it were automatically that language's global scope.

**Why it matters:** Language bindings and polyglot bindings are different namespaces. `Context.getBindings(languageId)` provides the selected language's top-most bindings as members.

**How to fix it:** Retrieve the intended language bindings and access the exported symbol through the member API. Use `getPolyglotBindings()` only when the integration actually uses that explicit host/guest symbol-exchange namespace.

**Before**

```java
Value requiredGlobal(Context context, String languageId, String name) {
    return context.getPolyglotBindings().getMember(name);
}
```

**After**

```java
Value requiredGlobal(Context context, String languageId, String name) {
    Value bindings = context.getBindings(languageId);
    Value symbol = bindings.getMember(name);
    if (symbol == null) {
        throw new IllegalArgumentException("Missing top-level symbol: " + name);
    }
    return symbol;
}
```

---

<a name="request-the-numeric-value-rather-than-guessing-its-java-wrapper"></a>

### `PG-NUMERIC-CONVERSION` — Request the numeric value rather than guessing its Java wrapper

**When it applies:** You retrieve a numeric result through `org.graalvm.polyglot.Value`, configure host big-integer exposure, or depend on numeric conversion across guest boundaries.

**What goes wrong:** You convert a guest number to `Object` and cast it to `Long` because its value is integral or an earlier execution returned that wrapper.

**Why it matters:** The default `Object` mapping of a number does not specify its concrete `Number` subclass. A numeric value's range is a different contract from the Java class chosen to represent it.

**How to fix it:** Request the numeric conversion that matches your boundary contract. For a losslessly representable `long`, test `fitsInLong()` and use `asLong()`.

**Before**

```java
long readCount(Value result) {
    Object mapped = result.as(Object.class);
    return (Long) mapped;
}
```

**After**

```java
long readCount(Value result) {
    if (!result.fitsInLong()) {
        throw new IllegalArgumentException("Expected an exact long value");
    }
    return result.asLong();
}
```

---

<a name="preserve-proxy-cursor-and-failure-semantics"></a>

### `PG-ITERATOR-CONTRACT` — Preserve proxy cursor and failure semantics

**When it applies:** You implement `ProxyIterator`, expose mutable backing data, or map an iterator value to a Java `Iterator`.

**What goes wrong:** You return a sentinel at exhaustion, advance an array-backed cursor when reading its element fails, or treat an earlier `hasNext()` result as a reservation of an element.

**Why it matters:** Null can be an ordinary element, whereas exhaustion is reported with `NoSuchElementException`. Underlying mutation can change availability between calls.

**How to fix it:** Keep availability, successful consumption, and failed access distinct. For a custom array-backed proxy, advance only after the element read succeeds, and translate an exhausted index to `NoSuchElementException`.

**Before**

```java
if (index >= elements.getSize()) {
    return null; // Ambiguous with a real null element.
}
return elements.get(index++); // Advances even if get fails.
```

**After**

```java
@Override
public boolean hasNext() {
    return index < elements.getSize();
}

@Override
public Object getNext() {
    if (!hasNext()) {
        throw new NoSuchElementException();
    }
    try {
        Object result = elements.get(index);
        index++;
        return result;
    } catch (ArrayIndexOutOfBoundsException exhausted) {
        throw new NoSuchElementException();
    }
}
```

---

<a name="separate-conversion-order-from-overload-precedence"></a>

### `PG-TARGET-MAPPING` — Separate conversion order from overload precedence

**When it applies:** You customize Java target mappings or depend on the Java collection produced for an Object-typed guest value.

**What goes wrong:** You assume a mapping that executes before a default conversion must win overload selection, or apply a compatibility mapping to a broader trait domain than the behavior being restored.

**Why it matters:** Conversion order and overload precedence are related but distinct contracts. `LOW` mappings run before default lossy wrappers but have equal overload precedence to those wrappers; `LOWEST` mappings run after default mappings.

**How to fix it:** Choose the exact target type, mapping predicate, required grants, and precedence deliberately. For the default Object mapping, eligible array-like values map to List before the member-based Map case; preceding null, host-object, and primitive mappings still apply.

**Before**

```java
HostAccess access = HostAccess.newBuilder(HostAccess.EXPLICIT)
                .targetTypeMapping(Value.class, Object.class,
                    v -> v.hasArrayElements(), // Also captures array-only values.
                    v -> v.as(Map.class))
                .build();
// Also wrong: assuming the default HIGH precedence wins every overload tie.
```

**After**

```java
HostAccess access = HostAccess.newBuilder(HostAccess.EXPLICIT)
                .allowMutableTargetMappings(
                    HostAccess.MutableTargetMapping.MEMBERS_TO_JAVA_MAP,
                    HostAccess.MutableTargetMapping.ARRAY_TO_JAVA_LIST)
                .targetTypeMapping(Value.class, Object.class,
                    v -> v.hasMembers() && v.hasArrayElements(),
                    v -> v.as(Map.class), HostAccess.TargetMappingPrecedence.LOW)
                .build();
try (Context context = Context.newBuilder(source.getLanguage())
                .allowHostAccess(access).build()) {
    Object mapped = context.eval(source).as(Object.class);
    consumeInsideContext(mapped);
}
```

---

<a name="use-the-hash-protocol-for-guest-dictionary-entries"></a>

### `PG-HASH-ENTRIES` — Use the hash protocol for guest dictionary entries

**When it applies:** Your host integration accepts a guest value as a map rather than a language-specific object interface.

**What goes wrong:** You treat dictionary keys as object members or call a member named `get` as though it were a language-independent map protocol.

**Why it matters:** Members and hash entries are separate capabilities. A value can expose methods as members without exposing its dictionary keys there.

**How to fix it:** Define whether the integration accepts a map or an object with named members. For a map contract, require `hasHashEntries()` and use the hash-entry operations.

**Before**

```java
Value requiredEntry(Value dictionary, String key) {
    Value getter = dictionary.getMember("get");
    return getter.execute(key);
}
```

**After**

```java
Value requiredEntry(Value dictionary, String key) {
    if (!dictionary.hasHashEntries()) {
        throw new IllegalArgumentException("Expected a map value");
    }
    Value entry = dictionary.getHashValue(key);
    if (entry == null) {
        throw new IllegalArgumentException("Missing required map entry: " + key);
    }
    return entry;
}
```

---

<a name="handle-the-public-operations-documented-failure"></a>

### `PG-FAILURE-PROTOCOL` — Handle the public operation's documented failure

**When it applies:** Your host code reads guest values and translates unsupported operations into an application-level error or fallback, or configures a handler for failures crossing back to the host.

**What goes wrong:** You depend on a private implementation exception or assume every failed value operation is a `PolyglotException`.

**Why it matters:** The public operation distinguishes unsupported access from guest execution failure. Catching the wrong exception can miss the intended unsupported-operation case while incorrectly translating a real guest error.

**How to fix it:** Follow the exact public method's documented result and exception contract. For `Value.getMember`, an absent member returns Java null; a receiver without members or an unreadable existing member can throw `UnsupportedOperationException`.

**Before**

```java
Value requiredMember(Value receiver, String name) {
    try {
        return receiver.getMember(name);
    } catch (PolyglotException failure) {
        throw new IllegalArgumentException("Member access unsupported", failure);
    }
}
```

**After**

```java
Value requiredMember(Value receiver, String name) {
    final Value member;
    try {
        member = receiver.getMember(name);
    } catch (UnsupportedOperationException unsupported) {
        throw new IllegalArgumentException(
                        "Member access unsupported: " + name, unsupported);
    }
    if (member == null) {
        throw new IllegalArgumentException("Missing member: " + name);
    }
    return member;
}
```

---

## Output, logging, and diagnostics

<a name="bound-host-side-output-independently-of-guest-memory"></a>

### `PG-RESOURCE-OUTPUT` — Bound host-side output independently of guest memory

**When it applies:** Guest output is redirected to a host buffer, file, queue or logging pipeline, especially for untrusted workloads.

**What goes wrong:** You assume a guest heap limit also bounds a host `ByteArrayOutputStream`, downstream copies, or disk usage.

**Why it matters:** Output leaves the guest heap and consumes resources owned by its host-side recipient. An unbounded recipient or queue can exhaust host memory or disk even when guest memory is limited.

**How to fix it:** Choose an explicit recipient policy: discard output, retain a bounded tail, stop at a quota, or use bounded storage with a defined overflow policy. Bound both output channels and the downstream pipeline, not just the first stream.

**Before**

```java
ByteArrayOutputStream captured = new ByteArrayOutputStream();
try (Context context = Context.newBuilder(source.getLanguage())
                .out(captured).err(captured).build()) {
    context.eval(source); // Guest heap limits do not cap captured.
}
byte[] copy = captured.toByteArray(); // Another host-side allocation.
```

**After**

```java
try (Context context = Context.newBuilder(source.getLanguage())
                .out(OutputStream.nullOutputStream())
                .err(OutputStream.nullOutputStream()).build()) {
    context.eval(source);
}
```

---

<a name="isolate-guest-driven-diagnostic-failures"></a>

### `PG-DIAGNOSTICS` — Isolate guest-driven diagnostic failures

**When it applies:** An error reporter reads guest exception members to enrich a diagnostic.

**What goes wrong:** You treat guest member reads as inert host metadata access and let a second guest exception replace the original failure or abort an interactive reporter.

**Why it matters:** Value.getMember can execute guest behavior and throw a PolyglotException. Further Value operations can also fail after the context has closed.

**How to fix it:** Emit a host-only baseline diagnostic first, keep any optional guest-object inspection inside the appropriate context lifetime, and handle its documented failures separately. Preserve the original exception and record the diagnostic failure rather than recursively inspecting that new exception's guest object.

**Before**

```java
catch (PolyglotException original) {
    Value error = original.getGuestObject();
    System.err.println(error.getMember("details").asString());
}
```

**After**

```java
catch (PolyglotException original) {
    original.printStackTrace(System.err); // No application guest-member traversal.
    RuntimeException reportingFailure = null;
    Value error = original.getGuestObject();
    if (error != null) {
        try {
            Value details = error.getMember("details");
            if (details != null && details.isString()) {
                System.err.println(details.asString());
            }
        } catch (PolyglotException | IllegalStateException |
                        UnsupportedOperationException diagnosticFailure) {
            reportingFailure = diagnosticFailure;
            System.err.println("Additional guest diagnostic unavailable.");
            diagnosticFailure.printStackTrace(System.err);
        }
    }
    applyFailurePolicy(original, reportingFailure);
}
```

---

<a name="choose-one-intended-logging-destination-per-scope"></a>

### `PG-LOG-ROUTING` — Choose one intended logging destination per scope

**When it applies:** You combine `log.file` with a builder-supplied logging handler or stream.

**What goes wrong:** You expect `log.file` to duplicate or override logging already routed through an explicit handler at that scope.

**Why it matters:** An explicit builder handler takes precedence over the file option at the same owner scope. A context-level handler and an engine-level handler have different lifetimes; their settings are not interchangeable global defaults.

**How to fix it:** Select the destination explicitly, remove conflicting configuration, and let the intended owner manage its handler's lifetime. Use `Context.Builder.logHandler` for a context-owned capture, or `Engine.Builder.logHandler` for engine-owned logging across its contexts.

**Before**

```java
ByteArrayOutputStream capture = new ByteArrayOutputStream();
try (Context context = Context.newBuilder(source.getLanguage())
                .option("log.file", "guest.log")
                .logHandler(capture)
                .build()) {
    context.eval(source);
    // Wrong expectation: these same-scope settings duplicate logs to guest.log.
}
```

**After**

```java
byte[] runWithCapturedLogs(Source source) {
    ByteArrayOutputStream capture = new ByteArrayOutputStream();
    try (Context context = Context.newBuilder(source.getLanguage())
                    .option("log.level", "INFO")
                    .logHandler(capture)
                    .build()) {
        context.eval(source);
    }
    return capture.toByteArray();
}
```
