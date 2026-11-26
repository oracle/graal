# Truffle Language Implementation Guidelines

For language implementations using Truffle, the Truffle DSL, or the Bytecode DSL.
The before and after examples are illustrative; they omit imports and surrounding code.

**For review agents:** Work through the index one guideline at a time. For each, record a finding with a code reference, explain why the rule does not apply, or note what you checked and found no issue with. Mark anything you could not verify; do not count it as a pass.

## Index

**[Language registration, options, and parsing](#language-registration-options-and-parsing) · 14 guidelines**

| Code | Guideline |
| --- | --- |
| [`TR-REGISTRATION-VERSION`](#separate-implementation-version-from-guest-compatibility) | Register the implementation version separately from the guest edition the parser accepts. |
| [`TR-ENV-OPTIONS`](#read-user-configuration-through-declared-language-options) | Declare user settings as discoverable language options and read their typed values per context. |
| [`TR-OPTION-COMPAT`](#include-shared-code-options-in-language-compatibility) | Compare effective option values before sharing code whose meaning depends on them. |
| [`TR-SOURCE-OPTIONS`](#reuse-typed-options-from-the-parsing-request) | Read declared source options as typed values from the parsing request. |
| [`TR-INTERNAL-SOURCES`](#choose-internal-source-visibility-deliberately) | Choose internal source visibility according to guest-visible behavior rather than file origin. |
| [`TR-FEATURE-GATING`](#validate-enabled-features-at-the-actual-parser-entry) | Check whether a feature is enabled at the parser entry that recognizes it. |
| [`TR-SOURCE-CONTENT`](#match-source-content-to-the-parsers-byte-or-character-contract) | Declare byte- or character-based MIME types to match the parser’s input contract. |
| [`TR-SOURCE-DETECTION`](#recognize-ambiguous-binary-formats-through-their-content) | Inspect a bounded content prefix when a filename cannot identify the binary format reliably. |
| [`TR-SANDBOX-OPTIONS`](#review-sandbox-eligibility-separately-from-option-stability) | Assess sandbox eligibility independently of whether an option is stable. |
| [`TR-OPTION-TEXT`](#advertise-the-tokens-your-option-converter-accepts) | Advertise the exact option tokens the converter accepts. |
| [`TR-CONTEXT-INITIALIZATION`](#finish-context-creation-before-cross-language-initialization) | Finish creating context state before starting initialization that depends on another language. |
| [`TR-DEPENDENCY-PARSING`](#parse-implementation-dependencies-without-widening-guest-access) | Use internal parsing for trusted implementation dependencies without widening guest evaluation access. |
| [`TR-PARSE-SIDE-EFFECTS`](#build-reusable-code-during-parsing-not-guest-execution-state) | Parse reusable code and perform execution-time initialization when its target runs. |
| [`TR-INTERNAL-RESOURCE-VERSION`](#bound-resource-version-tokens-without-losing-their-inputs) | Hash an unambiguous encoding of resource version inputs into a bounded path-safe token. |

**[Context lifecycle, sharing, and host services](#context-lifecycle-sharing-and-host-services) · 16 guidelines**

| Code | Guideline |
| --- | --- |
| [`TR-OWNERSHIP`](#keep-context-owned-values-out-of-caches-shared-across-contexts) | Keep context-owned guest values out of caches that another context can reuse. |
| [`TR-DISPOSE`](#do-not-run-guest-code-during-context-disposal) | Run guest shutdown actions before disposal and reserve disposal for releasing resources. |
| [`TR-NATIVE-LIFETIME`](#keep-native-backed-views-within-their-storage-lifetime) | Copy native-backed data into managed storage if the view can outlive its allocation. |
| [`TR-INNER-CONTEXT`](#do-not-reuse-an-incompatible-outer-lookup-node-inside-an-inner-context) | Use a lookup valid for the inner execution rather than reusing an incompatible outer node. |
| [`TR-SAFEPOINTS`](#cooperate-during-long-running-and-blocking-work) | Poll on long-running paths and use the safepoint blocking protocol while entered. |
| [`TR-THREAD-ACCESS`](#do-not-grant-concurrent-context-access-just-to-suppress-an-error) | Allow concurrent context entry only if the language’s state actually supports it. |
| [`TR-RESOURCE-CLEANUP`](#release-context-owned-external-resources-explicitly) | Release context-owned external resources explicitly and no later than disposal. |
| [`TR-SOURCE-CACHE-LIFETIME`](#keep-source-cache-anchors-for-the-intended-reuse-lifetime) | Retain reusable sources under an owner whose scope matches the intended parse-cache lifetime. |
| [`TR-EXECUTION-MODES`](#exercise-the-modes-that-mutate-reusable-code) | Exercise shared-engine as well as isolated execution when testing code reuse. |
| [`TR-BLOCKING-CONTRACT`](#handle-the-at-most-once-contract-of-leave-and-reenter-waits) | Make leave-and-reenter handoffs tolerate interruption before their callback starts. |
| [`TR-THREAD-SHUTDOWN`](#stop-and-join-owned-threads-in-the-correct-lifecycle-phase) | Stop and join language-owned workers before the required lifecycle hook returns. |
| [`TR-NATIVE-PERMISSIONS`](#honor-native-access-permission-when-selecting-a-backend) | Check native-access permission before selecting a backend that requires it. |
| [`TR-FILE-SYSTEM-CONTRACT`](#use-the-configured-file-systems-actual-capabilities) | Handle the capabilities of the configured file system rather than assuming host defaults. |
| [`TR-FILE-PRIVILEGES`](#match-file-access-to-the-operations-resource-authority) | Resolve guest-controlled file access through the public file API and its configured policy. |
| [`TR-ENV-CAPABILITIES`](#derive-host-service-availability-from-the-environment-grant) | Derive host-service availability from the environment grant for that operation. |
| [`TR-CONTEXT-TERMINATION`](#match-inner-context-termination-to-entry-and-exit-policy) | Choose ordinary close, external cancellation, or guest hard exit according to the termination policy. |

**[ASTs, frames, and object layouts](#asts-frames-and-object-layouts) · 15 guidelines**

| Code | Guideline |
| --- | --- |
| [`TR-INTERNAL-STORAGE`](#keep-private-backing-slots-out-of-guest-property-names) | Use private property keys rather than guest-visible names for internal storage. |
| [`TR-CACHE-KEY`](#include-all-semantic-inputs-in-stable-cache-keys) | Include every semantic input in a cache key and keep its contents stable. |
| [`TR-EXECUTION-STATE`](#keep-invocation-state-out-of-shared-ast-nodes) | Store invocation state in a frame or local variable so reentry cannot overwrite it. |
| [`TR-ADOPTION`](#preserve-the-asts-parent-child-structure) | Give ordinary cached children one AST parent and let adoption establish the relationship. |
| [`TR-LANGUAGE-REFERENCE`](#look-up-shared-language-metadata-without-a-context-detour) | Look up shared language metadata through a valid language reference rather than a context detour. |
| [`TR-IDENTITY-CACHE`](#make-identity-guarded-metadata-recur-at-the-cache) | Reuse compatible immutable metadata when a cache guard relies on its identity. |
| [`TR-SHARED-SHAPES`](#prepare-dynamic-object-storage-before-concurrent-publication) | Prepare dynamic-object storage for shared access before publishing it to other threads. |
| [`TR-TYPED-ROOT-CALLS`](#preserve-useful-root-type-information-on-interpreter-call-paths) | Retain a known concrete root type where interpreter dispatch can use it safely. |
| [`TR-LAYOUT-LOOKUP`](#supply-privileged-access-for-a-custom-object-layout) | Supply a lookup with the privileges required by the custom object layout. |
| [`TR-STATIC-SHAPE-SAFETY`](#preserve-receiver-safety-before-disabling-shape-checks) | Disable receiver-shape checks only if every incoming access is proven compatible. |
| [`TR-STATIC-SHAPE-CONSTRUCTION`](#keep-property-handles-with-their-declaring-layout) | Keep each static property handle with the layout that declared it. |
| [`TR-ARGUMENT-BOUNDS`](#implement-missing-argument-semantics-before-indexed-loads) | Handle omitted arguments before an indexed load and distinguish them from supplied guest nulls. |
| [`TR-FRAME-DESCRIPTOR`](#read-slot-metadata-from-its-owning-frame-layout) | Interpret each slot using the frame descriptor that owns the variable. |
| [`TR-FRAME-ARGUMENT-OWNERSHIP`](#preserve-caller-arguments-when-adapting-a-call) | Pack callee arguments without overwriting borrowed caller entries. |
| [`TR-FRAME-SLOT-POLICY`](#preserve-descriptor-based-slot-generalization) | Record permanent local-slot generalization in the owning descriptor as well as the current frame. |

**[Truffle DSL specializations and caches](#truffle-dsl-specializations-and-caches) · 11 guidelines**

| Code | Guideline |
| --- | --- |
| [`TR-SPECIALIZATION-ORDER`](#order-disjoint-specializations-from-representative-frequencies) | Measure specialization frequencies before reordering disjoint cases. |
| [`TR-UNCACHED-PE`](#keep-uncached-dispatch-off-guest-compilation-paths) | Use cached dispatch on guest compilation paths that need receiver specialization. |
| [`TR-CACHE-INITIALIZER`](#handle-expected-lookup-failures-outside-cache-initializers) | Represent an expected lookup miss as data rather than throwing from a cache initializer. |
| [`TR-INLINING-TARGET`](#pass-the-actual-storage-node-to-inlined-helpers) | Pass the caller’s actual storage node to an inlined helper. |
| [`TR-CACHE-VALIDITY`](#guard-cached-results-against-every-relevant-change) | Guard cached results against changes to the data on which they depend. |
| [`TR-GUARD-IDEMPOTENCE`](#treat-guard-idempotence-as-a-correctness-promise) | Mark changing guards non-idempotent or make their dynamic inputs explicit. |
| [`TR-CACHE-RETENTION`](#audit-cache-retention-within-a-live-context) | Check what a cache entry keeps alive, including the backing storage of substring keys. |
| [`TR-DSL-FOOTPRINT`](#choose-dsl-helper-layout-for-its-actual-usage) | Choose eager, inline, or lazy helper storage according to how often the path runs. |
| [`TR-SPECIALIZATION-SEMANTICS`](#preserve-results-across-specialization-transitions) | Preserve guest results when a generic specialization replaces a narrow fast path. |
| [`TR-DSL-EXECUTE-ENTRIES`](#keep-ordinary-helpers-separate-from-dsl-execute-entries) | Reserve execute signatures for DSL entry points and give ordinary helpers distinct names. |
| [`TR-SLOW-PATH-EXCEPTIONS`](#deoptimize-before-handing-off-interpreter-re-specialization) | Transfer to the interpreter before handing off an update to specialization state. |

**[Compilation and partial evaluation](#compilation-and-partial-evaluation) · 12 guidelines**

| Code | Guideline |
| --- | --- |
| [`TR-PROFILES`](#profile-important-data-dependent-branches-in-handwritten-operations) | Use an execution-site profile to expose important data-dependent branches to the compiler. |
| [`TR-COMPILATION-FINAL`](#invalidate-compiled-assumptions-before-changing-compilation-final-state) | Invalidate affected compiled assumptions before changing compilation-final state. |
| [`TR-LOOP-EXPLOSION`](#scope-loop-explosion-to-the-intended-loops) | Explode only the intended fixed-size loop rather than an enclosing input-sized loop. |
| [`TR-PE-ASSERTIONS`](#assert-required-partial-evaluation-constants-instead-of-ignoring-a-query) | Assert that a value is a partial-evaluation constant when compilation requires it. |
| [`TR-DEOPT-STABILITY`](#bound-invalidation-by-shared-code-state-not-by-each-new-object) | Bound invalidations by changes in shared compiled state rather than by the number of objects. |
| [`TR-BOUNDARY-SCOPE`](#keep-boundaries-around-the-work-that-must-remain-opaque) | Place a compilation boundary around only the work that needs to remain opaque. |
| [`TR-DEOPT-STATE`](#preserve-interpreter-state-needed-after-deoptimization) | Preserve every piece of interpreter state needed at a supported deoptimization point. |
| [`TR-PE-VISIBILITY`](#keep-required-interpreter-dispatch-resolvable-during-partial-evaluation) | Identify unresolved interpreter calls before adding casts intended to help partial evaluation. |
| [`TR-RESULT-CARRIERS`](#avoid-temporary-containers-solely-to-feed-a-child-operation) | Pass values directly to a child operation when a temporary container serves no other purpose. |
| [`TR-AST-DUPLICATION`](#avoid-replicating-one-body-across-equivalent-alternatives) | Share a body across alternatives only when condition order and observable behavior stay intact. |
| [`TR-COMPILATION-PREPARATION`](#keep-compiler-preparation-repeatable-and-context-independent) | Make compilation preparation safe to repeat without an entered context. |
| [`TR-ALLOCATION-PLACEMENT`](#fix-allocation-placement-only-for-a-required-failure-boundary) | Fix allocation placement only when failure must be observed at a specific guest boundary. |

**[Interop and data representation](#interop-and-data-representation) · 12 guidelines**

| Code | Guideline |
| --- | --- |
| [`TR-INTEROP-CONVERSION`](#convert-foreign-values-through-their-interop-protocol) | Convert foreign values through their interop messages rather than their host classes. |
| [`TR-INTEROP-CONSISTENCY`](#keep-capability-predicates-and-operations-consistent) | Make each interop predicate agree with its operation’s success and failure contract. |
| [`TR-FOREIGN-IDENTITY`](#use-interop-identity-for-foreign-objects) | Use interop identity and its corresponding hash for foreign objects with identity. |
| [`TR-SPI-INTEROP`](#use-the-interop-protocol-inside-guest-execution) | Use interop messages rather than host Polyglot wrappers inside guest execution nodes. |
| [`TR-TRANSFER-BUFFERS`](#bound-temporary-storage-for-guest-requested-transfers) | Transfer a validated buffer range in bounded chunks instead of allocating the full request size. |
| [`TR-STRING-RANGE`](#distinguish-character-ranges-from-byte-based-ranges) | Use the range query that matches the character or byte property the operation needs. |
| [`TR-STRING-LIMITS`](#enforce-guest-string-limits-before-allocation-and-growth) | Enforce string limits in guest units before allocating or growing a builder. |
| [`TR-EXPECTED-GUEST-ERRORS`](#report-specified-failures-through-the-guest-error-protocol) | Report invalid guest operands through the guest operation’s specified failure protocol. |
| [`TR-BUFFER-RANGES`](#use-byte-based-view-ranges-across-buffer-representations) | Compute checked byte offsets and lengths from the actual buffer view. |
| [`TR-STRING-CONVERSION`](#avoid-a-host-string-round-trip-for-encoded-guest-data) | Convert encoded guest data directly using its actual encoding, range, and ownership. |
| [`TR-INTEROP-ARRAY-SIZE`](#preserve-foreign-sizes-and-centralize-trait-selection) | Validate foreign sizes in long arithmetic and choose deliberately among overlapping interop traits. |
| [`TR-BYTE-ARRAY-ACCESS`](#separate-unaligned-immutable-reads-from-concurrent-memory-access) | Distinguish immutable unaligned reads from accesses that require concurrent memory ordering. |

**[Bytecode execution and the Bytecode DSL](#bytecode-execution-and-the-bytecode-dsl) · 12 guidelines**

| Code | Guideline |
| --- | --- |
| [`TR-LOCAL-FASTPATH`](#use-built-in-local-operations-for-ordinary-local-accesses) | Use built-in bytecode operations for ordinary local loads and stores. |
| [`TR-FRAME-RESOLUTION`](#resolve-captured-execution-frames-through-bytecode-aware-apis) | Resolve captured frames and their roots through the matching bytecode APIs. |
| [`TR-FRAME-LIFETIME`](#copy-captured-frame-state-before-resuming-when-it-must-remain-stable) | Copy a captured bytecode frame before resuming if its earlier state must remain available. |
| [`TR-BOXING`](#preserve-primitive-paths-across-custom-operation-boundaries) | Keep primitive values unboxed across bytecode operations with matching typed specializations. |
| [`TR-SERIALIZATION`](#invalidate-persisted-bytecode-when-compatibility-assumptions-change) | Validate framework and interpreter compatibility before loading persisted bytecode. |
| [`TR-OSR-STATE`](#resume-osr-at-the-location-described-by-the-frame-state) | Resume on-stack replacement at the instruction that matches the transferred frame state. |
| [`TR-BYTECODE-LOCATION`](#keep-a-bytecode-index-paired-with-its-bytecode-node) | Carry each bytecode index with the bytecode node that gives it meaning. |
| [`TR-BYTECODE-UNWIND`](#keep-required-cleanup-on-the-configured-unwind-path) | Put cleanup required during unwind on the configured finally path. |
| [`TR-CONTINUATION-SCOPE`](#distinguish-single-method-suspension-from-whole-stack-capture) | Treat a continuation as suspension of one invocation, not a snapshot of all its callers. |
| [`TR-BYTECODE-LABEL-SCOPE`](#keep-label-creation-and-emission-in-the-same-builder-scope) | Create and emit each bytecode label within its owning root or block. |
| [`TR-SHORT-CIRCUIT-SEMANTICS`](#preserve-evaluation-order-and-the-selected-operand) | Preserve operand order, skipped effects, and the selected value in short-circuit operations. |
| [`TR-STATIC-FRAME-LIVENESS`](#rewrite-cleared-static-slots-before-reading-them) | Write a valid value on every path from clearing a static slot to its next read. |

**[Threaded interpreter dispatch](#threaded-interpreter-dispatch) · 4 guidelines**

| Code | Guideline |
| --- | --- |
| [`TR-HANDLER-UNWIND`](#store-handler-return-state-directly-into-its-logical-local) | Store a threaded handler’s return value directly into its configured logical local. |
| [`TR-HANDLER-IDENTITY`](#do-not-depend-on-the-identity-of-fully-expanded-handler-state) | Treat fully expanded handler arguments as fields, not wrappers with stable identity. |
| [`TR-HANDLER-TEMPLATES`](#keep-template-state-valid-at-each-threaded-dispatch) | Update related template fields together so every threaded dispatch sees a valid combination. |
| [`TR-HANDLER-DISPATCH`](#keep-per-instruction-work-inside-threaded-handlers) | Place required per-instruction work where threaded handler transfers cannot skip it. |

**[Instrumentation, source locations, and diagnostics](#instrumentation-source-locations-and-diagnostics) · 12 guidelines**

| Code | Guideline |
| --- | --- |
| [`TR-SOURCE-SECTIONS`](#associate-root-locations-with-the-actual-parsed-source) | Derive root source locations from the source supplied by the parsing request. |
| [`TR-SOURCE-METADATA`](#refresh-lazy-source-information-before-reading-it) | Read lazy source information from the updated bytecode location or node. |
| [`TR-LANGUAGE-VIEWS`](#keep-tooling-language-views-out-of-execution-data-flow) | Keep tooling views separate from values used for guest execution. |
| [`TR-STATEMENT-TAGS`](#tag-language-statements-rather-than-source-lines) | Tag executable guest statements rather than treating source lines as stepping units. |
| [`TR-WRAPPER-DELEGATION`](#preserve-non-execution-behavior-through-instrumentation-wrappers) | Delegate metadata as well as execution when an instrumentation wrapper must preserve node behavior. |
| [`TR-PROBE-PAIRING`](#complete-manual-instrumentation-events-on-every-exit) | Complete manual instrumentation events on exceptional exits as well as normal returns. |
| [`TR-INSTRUMENTATION-PREPARATION`](#make-root-preparation-stabilize-for-repeated-tags) | Reuse existing instrumentation preparation and add only what newly requested tags need. |
| [`TR-INSTRUCTION-TRACING`](#do-not-substitute-instruction-tracing-for-semantic-events) | Emit semantic events at the guest operation they describe rather than inferring them from dispatch. |
| [`TR-ROOT-BODY-SCOPE`](#establish-prolog-state-before-the-root-body-event) | Establish required prolog state before the instrumentable root body begins. |
| [`TR-ALLOCATION-EVENTS`](#report-allocation-intent-before-creating-the-guest-value) | Report allocation intent before creating the guest value and report the result after success. |
| [`TR-DIAGNOSTIC-SAFETY`](#keep-compiler-diagnostics-independent-of-guest-execution) | Build compiler diagnostics from host metadata without invoking guest code. |
| [`TR-INSTRUMENTABLE-MATERIALIZATION`](#preserve-syntax-and-source-locations-when-materializing-nodes) | Preserve syntax, evaluation order, and source ranges when materializing instrumentable nodes. |

## Language registration, options, and parsing

<a name="separate-implementation-version-from-guest-compatibility"></a>

### `TR-REGISTRATION-VERSION` — Separate implementation version from guest compatibility

**When it applies:** You declare `@TruffleLanguage.Registration` metadata or select a supported guest specification edition.

**What goes wrong:** You put a list of supported guest editions in the registration's `version` and treat that metadata as compatibility configuration.

**Why it matters:** The registration identifies the language implementation's version, exposed through `Language.getVersion()`. It does not select a parser mode or declare which guest specification editions the implementation accepts.

**How to fix it:** Omit `version` when the implementation should inherit `Engine.getVersion()`, which is the current default. For an independently versioned implementation, supply that implementation's release version instead.

**Before**

```java
@TruffleLanguage.Registration(id = "sample", name = "Sample",
                version = "EDITION_1|EDITION_2")
final class SampleLanguage extends SampleLanguageBase {
}
```

**After**

```java
@TruffleLanguage.Registration(id = "sample", name = "Sample")
final class SampleLanguage extends SampleLanguageBase {
    enum Edition {
        EDITION_1, EDITION_2
    }

    @Option(name = "Edition", help = "Guest specification edition.",
                    category = OptionCategory.USER,
                    usageSyntax = "EDITION_1|EDITION_2")
    static final OptionKey<Edition> EDITION = new OptionKey<>(Edition.EDITION_2);

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new SampleLanguageOptionDescriptors();
    }

    static Edition configuredEdition(Env env) {
        return EDITION.getValue(env.getOptions());
    }
}
```

**Further reading:** See [Registration Version Contract](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Registration.html#version()) and [Language Options](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/Options/).

---

<a name="read-user-configuration-through-declared-language-options"></a>

### `TR-ENV-OPTIONS` — Read user configuration through declared language options

**When it applies:** You expose a language setting that callers can choose when creating a context.

**What goes wrong:** You read a Java system property once into a static field and use it for every context.

**Why it matters:** Callers cannot discover the setting through the language’s option descriptors, and two contexts cannot choose different values.

**How to fix it:** Declare an `OptionKey`, return the generated descriptors, and read the typed value from `Env.getOptions()` into context-owned state.

**Before**

```java
static final boolean STRICT_INPUT =
                Boolean.getBoolean("example.StrictInput");

@Override
protected LanguageState createContext(Env env) {
    return new LanguageState(env, STRICT_INPUT);
}
```

**After**

```java
@Option(help = "Reject input outside the strict language subset.",
                category = OptionCategory.USER,
                stability = OptionStability.STABLE)
static final OptionKey<Boolean> StrictInput = new OptionKey<>(false);

@Override
protected OptionDescriptors getOptionDescriptors() {
    return new ExampleLanguageOptionDescriptors();
}

@Override
protected LanguageState createContext(Env env) {
    return new LanguageState(env, env.getOptions().get(StrictInput));
}
```

**Further reading:** See [Language and Launcher Options](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/Options/) and [Environment Option Values](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#getOptions()).

---

<a name="include-shared-code-options-in-language-compatibility"></a>

### `TR-OPTION-COMPAT` — Include shared-code options in language compatibility

**When it applies:** Options affect shared or reused language artifacts, or runtime patching activates features on a preinitialized context.

**What goes wrong:** You leave `areOptionsCompatible` at its default `true` while different option values require different code, layouts, or immutable language configuration.

**Why it matters:** Compatibility determines whether contexts can share or reuse a language instance and thereby influences parse-cache reuse. A source alone does not identify the meaning of code when relevant configuration differs.

**How to fix it:** Compare the effective values of every option that affects shared artifacts or language-instance invariants. Return `false` when those configurations cannot safely share the same instance.

**Before**

```java
@Override
protected boolean areOptionsCompatible(OptionValues first, OptionValues next) {
    return true; // Wrong if shared code embeds either option below.
}
```

**After**

```java
@Override
protected boolean areOptionsCompatible(OptionValues first, OptionValues next) {
    return first.get(PARSE_MODE).equals(next.get(PARSE_MODE)) &&
                    first.get(LAYOUT_VERSION).equals(next.get(LAYOUT_VERSION));
}
```

**Further reading:** See [Language-Instance Code Sharing](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/AOTOverview/#code-sharing-within-the-same-isolateprocess) and [areOptionsCompatible and example](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#areOptionsCompatible(org.graalvm.options.OptionValues,org.graalvm.options.OptionValues)).

---

<a name="reuse-typed-options-from-the-parsing-request"></a>

### `TR-SOURCE-OPTIONS` — Reuse typed options from the parsing request

**When it applies:** You declare language options, including source options consumed during parsing or source-based instrumentation filtering.

**What goes wrong:** Parser helpers reparse options already supplied with the request, or option converters and validators report supported invalid input through the wrong exception contract.

**Why it matters:** The source-based API can parse and validate options again. The parsing request already supplies the option values for its source and language.

**How to fix it:** Declare supported source options through getSourceOptionDescriptors and consume request.getOptionValues during parsing. Pass the needed typed values or OptionValues to synchronous parser helpers, rather than retaining the request or adding another source-option cache.

**Before**

```java
@Override
protected CallTarget parse(ParsingRequest request) {
    Source source = request.getSource();
    OptionValues options = source.getOptions(this); // Parses again.
    return parseUnit(source, options);
}
```

**After**

```java
@Override
protected CallTarget parse(ParsingRequest request) {
    Source source = request.getSource();
    OptionValues options = request.getOptionValues();
    return parseUnit(source, options);
}

private CallTarget parseUnit(Source source, OptionValues options) {
    ParseSettings settings = settingsFromDeclaredOptions(options);
    return buildRoot(source, settings).getCallTarget();
}
```

**Further reading:** See [ParsingRequest.getOptionValues](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.ParsingRequest.html#getOptionValues()) and [Source option descriptors](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#getSourceOptionDescriptors()).

---

<a name="choose-internal-source-visibility-deliberately"></a>

### `TR-INTERNAL-SOURCES` — Choose internal source visibility deliberately

**When it applies:** You create sources for generated implementation code, runtime helpers, or bundled libraries, or use source metadata to identify an implementation component.

**What goes wrong:** You classify every bundled library as either internal or user-visible solely from its packaging, assume that marking a source internal guarantees that no tool can expose it, or mistake its name or internal flag for proof of component origin.

**Why it matters:** Internal sources describe implementation-oriented code that is normally irrelevant to guest programmers. Compatibility can still require particular library frames or debugging locations to remain visible.

**How to fix it:** Base the classification on intended guest-visible behavior and compatibility, not just whether the implementation ships the file. Distinguish implementation helpers from library code with guest-observable locations.

**Before**

```java
Source createSource(SourceUnit unit) {
    return Source.newBuilder(languageId, unit.text(), unit.name())
                    .internal(unit.origin() != Origin.USER)
                    .build();
}
```

**After**

```java
Source createSource(SourceUnit unit) {
    boolean internal = switch (unit.origin()) {
        case USER -> false;
        case IMPLEMENTATION_HELPER -> true;
        case LIBRARY -> libraryVisibility.isInternal(unit.libraryId());
    };
    return Source.newBuilder(languageId, unit.text(), unit.name())
                    .internal(internal)
                    .build();
}
```

**Further reading:** See [Source.isInternal](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.html#isInternal()) and [SourceBuilder.internal](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.SourceBuilder.html#internal(boolean)).

---

<a name="validate-enabled-features-at-the-actual-parser-entry"></a>

### `TR-FEATURE-GATING` — Validate enabled features at the actual parser entry

**When it applies:** Language configuration permits or rejects optional syntax or binary instructions.

**What goes wrong:** You trust a familiar source producer to omit disabled constructs instead of enforcing the parser's configured language variant.

**Why it matters:** A user can supply source or a binary directly, bypassing that producer. A producer's command-line flag does not establish the runtime parser's accepted-input contract.

**How to fix it:** Check feature availability where the runtime recognizes the optional construct, before accepting or lowering it. Take the enabled-feature configuration from its actual context or source owner, and preserve that configuration's cache-validity requirements.

**Before**

```java
Instruction parseInstruction(BinaryReader input) {
    int opcode = input.readOpcode();
    if (opcode == OPTIONAL_OPCODE) {
        // Assumes the usual frontend never emits this when disabled.
        return parseOptionalInstruction(input);
    }
    return parseCoreInstruction(opcode, input);
}
```

**After**

```java
final class BinaryParser {
    private final boolean optionalFeatureEnabled;

    BinaryParser(boolean optionalFeatureEnabled) {
        this.optionalFeatureEnabled = optionalFeatureEnabled;
    }

    Instruction parseInstruction(BinaryReader input) {
        int opcode = input.readOpcode();
        if (opcode == OPTIONAL_OPCODE) {
            if (!optionalFeatureEnabled) {
                throw disabledFeatureSyntaxError(input.position());
            }
            return parseOptionalInstruction(input);
        }
        return parseCoreInstruction(opcode, input);
    }
}
```

**Further reading:** See [ParsingRequest.getOptionValues](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.ParsingRequest.html#getOptionValues()).

---

<a name="match-source-content-to-the-parsers-byte-or-character-contract"></a>

### `TR-SOURCE-CONTENT` — Match source content to the parser's byte or character contract

**When it applies:** Your parser supports a binary format, needs original bytes for textual decoding, or adds a MIME type.

**What goes wrong:** You add a format detector or MIME constant without matching registration and source construction to the parser's required content kind.

**Why it matters:** MIME registration tells source construction whether to preserve bytes or interpret characters. A character decode/re-encode round trip can alter arbitrary binary input before your parser sees it.

**How to fix it:** Register byte-consuming MIME types in `Registration.byteMimeTypes` and character-consuming ones in `characterMimeTypes`. An explicit source language ID does not exempt an explicitly supplied MIME type from that language's supported set.

**Before**

```java
@TruffleLanguage.Registration(id = "example", name = "Example",
                characterMimeTypes = "application/x-example-binary")
final class ExampleLanguage extends TruffleLanguage<State> {
    @Override
    protected CallTarget parse(ParsingRequest request) {
        String decoded = request.getSource().getCharacters().toString();
        return parseBinary(ByteSequence.create(
                        decoded.getBytes(StandardCharsets.UTF_8)));
    }
    // Context creation and the binary parser are omitted.
}
```

**After**

```java
@TruffleLanguage.Registration(id = "example", name = "Example",
                byteMimeTypes = "application/x-example-binary",
                defaultMimeType = "application/x-example-binary")
final class ExampleLanguage extends TruffleLanguage<State> {
    @Override
    protected CallTarget parse(ParsingRequest request) {
        return parseBinary(request.getSource().getBytes());
    }
    // Context creation and the binary parser are omitted.
}
```

**Further reading:** See [Byte MIME Types](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Registration.html#byteMimeTypes()) and [Character MIME Types](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Registration.html#characterMimeTypes()).

---

<a name="recognize-ambiguous-binary-formats-through-their-content"></a>

### `TR-SOURCE-DETECTION` — Recognize ambiguous binary formats through their content

**When it applies:** Your language registers a file-type detector for a binary format whose filename extension is ambiguous.

**What goes wrong:** You classify every file with that extension as your format without checking its distinguishing content.

**Why it matters:** Unrelated formats can share an extension. Incorrect detection can select the wrong parser or source kind before format validation runs.

**How to fix it:** When your format has a reliable magic prefix, inspect a bounded prefix through the supplied `TruffleFile` and return `null` when it is not recognized. Keep full format validation in the parser, register the detector, and separately register the appropriate byte-based MIME type.

**Before**

```java
public String findMimeType(TruffleFile file) {
    return file.getName().endsWith(".dump") ? MIME_TYPE : null;
}
```

**After**

```java
public final class BinaryDetector implements TruffleFile.FileTypeDetector {
    private static final String MIME_TYPE = "application/x-example-binary";
    private static final byte[] MAGIC = {0x45, 0x58, 0x4d, 0x50};

    @Override
    public String findMimeType(TruffleFile file) throws IOException {
        try (InputStream input = file.newInputStream()) {
            byte[] prefix = input.readNBytes(MAGIC.length);
            return Arrays.equals(prefix, MAGIC) ? MIME_TYPE : null;
        }
    }

    @Override
    public Charset findEncoding(TruffleFile file) {
        return null; // This detector recognizes binary content only.
    }
}
```

**Further reading:** See [File-Type Detector Contract](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleFile.FileTypeDetector.html) and [MIME Detection](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleFile.FileTypeDetector.html#findMimeType(com.oracle.truffle.api.TruffleFile)).

---

<a name="review-sandbox-eligibility-separately-from-option-stability"></a>

### `TR-SANDBOX-OPTIONS` — Review sandbox eligibility separately from option stability

**When it applies:** You stabilize a language option, expose a new feature, or change the sandbox policies allowed by a language or option.

**What goes wrong:** You treat a stable option as automatically eligible for stricter sandbox policies, or relax its policy without assessing the feature and the language together.

**Why it matters:** Option stability and sandbox eligibility are independent contracts. An option's policy cannot establish that the whole language, its enabled dependencies, and the feature's reachable capabilities satisfy the selected policy.

**How to fix it:** Decide option stability separately from the most restrictive sandbox policy that has actually been assessed. Keep the option's existing restriction when making its interface stable unless a separate review justifies changing that restriction.

**Before**

```java
@Option(name = "ExtendedFeature", help = "Enable the extended feature.",
                category = OptionCategory.USER,
                stability = OptionStability.STABLE,
                sandbox = SandboxPolicy.UNTRUSTED)
static final OptionKey<Boolean> EXTENDED_FEATURE = new OptionKey<>(false);
```

**After**

```java
@Option(name = "ExtendedFeature", help = "Enable the extended feature.",
                category = OptionCategory.USER,
                stability = OptionStability.STABLE,
                sandbox = SandboxPolicy.TRUSTED)
static final OptionKey<Boolean> EXTENDED_FEATURE = new OptionKey<>(false);
```

**Further reading:** See [Option Stability](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/Option.html#stability()) and [Option Sandbox Eligibility](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/Option.html#sandbox()).

---

<a name="advertise-the-tokens-your-option-converter-accepts"></a>

### `TR-OPTION-TEXT` — Advertise the tokens your option converter accepts

**When it applies:** You declare boolean, enum-valued, numeric, or file-valued options, customize a converter, or supply help or usage syntax.

**What goes wrong:** You lowercase or otherwise prettify help text without making the declared converter accept those tokens, or infer a custom converter's domain from its enum default value.

**Why it matters:** Usage syntax describes accepted values; it does not change conversion. A custom OptionType can accept strings unrelated to the Java enum names or toString output.

**How to fix it:** Specify the external token contract once and keep conversion and help consistent. In the checked implementation, the default enum OptionType uses exact toString strings, not arbitrary case-insensitive matching.

**Before**

```java
enum Mode { FAST, COMPACT }

OptionType<Mode> type = new OptionType<>("Mode", Mode::valueOf);
OptionKey<Mode> key = new OptionKey<>(Mode.FAST, type);
OptionDescriptor descriptor = OptionDescriptor.newBuilder(key, "sample.Mode")
                .usageSyntax("fast|compact") // Converter accepts FAST or COMPACT.
                .build();
```

**After**

```java
OptionType<Mode> type = new OptionType<>("Mode", text -> switch (text) {
    case "fast" -> Mode.FAST;
    case "compact" -> Mode.COMPACT;
    default -> throw new IllegalArgumentException("Expected fast or compact");
});
OptionKey<Mode> key = new OptionKey<>(Mode.FAST, type);
OptionDescriptor descriptor = OptionDescriptor.newBuilder(key, "sample.Mode")
                .usageSyntax("fast|compact")
                .build();
```

**Further reading:** See [Option Declaration Usage Syntax](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/Option.html#usageSyntax()) and [Option Type Conversion](https://www.graalvm.org/sdk/javadoc/org/graalvm/options/OptionType.html).

---

<a name="finish-context-creation-before-cross-language-initialization"></a>

### `TR-CONTEXT-INITIALIZATION` — Finish context creation before cross-language initialization

**When it applies:** Context creation or initialization can request per-context state, invoke another language, or trigger instrumentation callbacks, including during inner-context construction.

**What goes wrong:** You assume your context is already available before `createContext` returns it, or assign required state only after the runtime can notify listeners that initialization has completed.

**Why it matters:** The runtime has not finished registering the context being created. Initializing another language can notify listeners that parse or otherwise reenter the first language, producing cyclic access to a context that is still being created.

**How to fix it:** Keep `createContext` and constructors it calls simple: construct the per-context state and return it. Put complex initialization in `initializeContext`, after the runtime has registered that state.

**Before**

```java
@Override
protected LanguageContext createContext(Env env) {
    LanguageContext state = new LanguageContext(env);
    state.dependency = env.parseInternal(state.dependencySource);
    return state; // A callback during parsing cannot yet access this context.
}
```

**After**

```java
@Override
protected LanguageContext createContext(Env env) {
    return new LanguageContext(env);
}

@Override
protected void initializeContext(LanguageContext state) {
    state.dependency = state.env.parseInternal(state.dependencySource);
}
```

**Further reading:** See [Context Creation Contract](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#createContext(com.oracle.truffle.api.TruffleLanguage.Env)) and [Complex Context Initialization](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#initializeContext(C)).

---

<a name="parse-implementation-dependencies-without-widening-guest-access"></a>

### `TR-DEPENDENCY-PARSING` — Parse implementation dependencies without widening guest access

**When it applies:** Your implementation accesses a language dependency's services or parses sources, including deserialized code, for an internal service or guest-requested evaluation.

**What goes wrong:** You bypass framework parsing and its cache with a private parser, require public guest access to an implementation dependency, use `parseInternal` to bypass the public access policy for guest-provided requests, or assume a static factory initializes a language dependency.

**Why it matters:** Public guest evaluation and implementation-owned dependency execution have different access contracts. An embedder can disable public access to a language that remains available to your implementation as a declared dependency.

**How to fix it:** Declare implementation dependencies through `TruffleLanguage.Registration.dependentLanguages` and parse their trusted sources with `Env.parseInternal`. Use `Env.parsePublic` for guest-requested evaluation, subject to the operation's public evaluation permissions and your language's own policy.

**Before**

```java
void initializeHelper(LanguageContext state) {
    // Fails when the dependency is not publicly accessible to guest programs.
    state.helperTarget = state.env.parsePublic(state.helperSource);
}

Object evaluateGuest(LanguageContext state, Source guestSource) {
    // Wrong: internal dependency access is not guest evaluation authority.
    return state.env.parseInternal(guestSource).call();
}
```

**After**

```java
void initializeHelper(LanguageContext state) {
    state.helperTarget = state.env.parseInternal(state.helperSource);
}

Object evaluateGuest(LanguageContext state, Source guestSource) {
    LanguageInfo target = state.env.getPublicLanguages().get(guestSource.getLanguage());
    if (target == null || !state.env.isPolyglotEvalAllowed(target)) {
        throw deniedGuestEvaluation(guestSource.getLanguage());
    }
    return state.env.parsePublic(guestSource).call();
}

Object invokeHelper(LanguageContext state, Object input) {
    return state.helperTarget.call(input);
}
```

**Further reading:** See [Implementation Parsing](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#parseInternal(com.oracle.truffle.api.source.Source,java.lang.String...)) and [Public Guest Parsing](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#parsePublic(com.oracle.truffle.api.source.Source,java.lang.String...)).

---

<a name="build-reusable-code-during-parsing-not-guest-execution-state"></a>

### `TR-PARSE-SIDE-EFFECTS` — Build reusable code during parsing, not guest execution state

**When it applies:** You implement `TruffleLanguage.parse(ParsingRequest)` or move initialization between parsing and execution.

**What goes wrong:** You execute user code or initialize its runtime entity during parsing as though parsing were the execution entry for every evaluation.

**Why it matters:** The parse contract requires constructing the AST without executing user code. The framework can reuse a parsed target from a language-instance source cache, including across compatible contexts.

**How to fix it:** Parse and validate the source into reusable code. Make the returned target perform the entity creation, initialization, and execution required by the language's actual invocation semantics.

**Before**

```java
@Override
protected CallTarget parse(ParsingRequest request) {
    BodyNode body = parseBody(request.getSource());
    initializeModuleAndRunUserHooks(currentContext());
    return new EntryRoot(this, body).getCallTarget();
}
```

**After**

```java
@Override
protected CallTarget parse(ParsingRequest request) {
    BodyNode body = parseBody(request.getSource()); // Construction and validation only.
    return new EntryRoot(this, body).getCallTarget();
}

final class EntryRoot extends RootNode {
    private static final ContextReference<LanguageContext> CONTEXT =
                    ContextReference.create(ExampleLanguage.class);
    @Child private BodyNode body;

    EntryRoot(ExampleLanguage language, BodyNode body) {
        super(language);
        this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        LanguageContext state = CONTEXT.get(this);
        ensureModuleInitialized(state);
        return body.execute(frame);
    }
}
```

**Further reading:** See [Parsing and Entity Initialization Contract](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest)).

---

<a name="bound-resource-version-tokens-without-losing-their-inputs"></a>

### `TR-INTERNAL-RESOURCE-VERSION` — Bound resource version tokens without losing their inputs

**When it applies:** You implement `InternalResource.versionHash`, especially for resources assembled from several independently versioned components.

**What goes wrong:** You concatenate component hashes into an overlong version string, or shorten it by discarding version inputs that can change the unpacked resource.

**Why it matters:** The resource version is a host-filesystem path component with a current maximum length of 128 bytes, not 128 Java characters. Unpacking throws `IOException` if that limit is exceeded.

**How to fix it:** Produce a deterministic, path-safe token covering all inputs that determine this resource version. For a composite version, aggregate the component digests into one fixed-size digest instead of concatenating their printable forms.

**Before**

```java
@Override
public String versionHash(Env env) throws IOException {
    return String.join("", componentHashes(env)); // 192 ASCII bytes.
}
```

**After**

```java
@Override
public String versionHash(Env env) throws IOException {
    try {
        MessageDigest aggregate = MessageDigest.getInstance("SHA-256");
        for (String componentHash : componentHashes(env)) {
            aggregate.update(componentHash.getBytes(StandardCharsets.US_ASCII));
        }
        return HexFormat.of().formatHex(aggregate.digest()); // 64 ASCII bytes.
    } catch (NoSuchAlgorithmException e) {
        throw new IOException("Resource version digest unavailable", e);
    }
}
```

**Further reading:** See [Internal Resource Cache and Deployment](https://www.graalvm.org/latest/reference-manual/embed-languages/#managing-internal-resources) and [Resource Version Limit](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/InternalResource.html#versionHash(com.oracle.truffle.api.InternalResource.Env)).

---

## Context lifecycle, sharing, and host services

<a name="keep-context-owned-values-out-of-caches-shared-across-contexts"></a>

### `TR-OWNERSHIP` — Keep context-owned values out of caches shared across contexts

**When it applies:** ASTs, language instances, contexts, or runtime caches can outlive the state they retain.

**What goes wrong:** You store a context-owned guest object or native handle in storage shared by multiple contexts.

**Why it matters:** A guest array can retain its context-specific class, and a native handle can belong to one context's native runtime. Reusing either from another context can produce incorrect behavior or retain the original context.

**How to fix it:** Keep context-owned objects in the context, or cache only context-independent metadata and construct guest objects in the current context. A shared object can also use a stable identifier to locate a handle in context-owned storage.

**Before**

```java
// A language instance can be shared by several contexts.
final Map<FormatKey, GuestArray> cachedTypes = new HashMap<>();

GuestArray argumentTypes(FormatKey key) {
    return cachedTypes.computeIfAbsent(key, this::createGuestTypes);
}
```

**After**

```java
// Only context-independent metadata lives in the shared language.
final Map<FormatKey, int[]> cachedTypes = new HashMap<>();

GuestArray argumentTypes(FormatKey key, MyContext context) {
    int[] types = cachedTypes.computeIfAbsent(key, this::parseTypeCodes);
    return context.createGuestTypes(types);
}
```

**Further reading:** See [Context-Independent Code](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/AOTOverview/#supporting-context-independent-code).

---

<a name="do-not-run-guest-code-during-context-disposal"></a>

### `TR-DISPOSE` — Do not run guest code during context disposal

**When it applies:** You implement language context shutdown hooks.

**What goes wrong:** `disposeContext` invokes guest callbacks or assumes guest finalization completed successfully.

**Why it matters:** Disposal releases resources after the guest-executing shutdown phases. The contract forbids running guest language code or accessing other languages through the environment during disposal.

**How to fix it:** Put guest-executing shutdown actions in the appropriate earlier lifecycle hook and reserve disposal for resource release that does not execute guest code. Use the current `exitContext` and `finalizeContext` contracts to choose that earlier hook; neither guarantees arbitrary guest cleanup will run during cancellation or hard exit.

**Before**

```java
@Override
protected void disposeContext(MyContext context) {
    context.invokeGuestShutdownHooks();
    context.releaseNativeResources();
}
```

**After**

```java
@Override
protected void exitContext(MyContext context, ExitMode mode, int code) {
    context.runGuestShutdownHooksAccordingToExitMode(mode, code);
}

@Override
protected void disposeContext(MyContext context) {
    context.releaseNativeResources();
}
```

**Further reading:** See [Context Exit Phases](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/Exit/) and [disposeContext](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#disposeContext(C)).

---

<a name="keep-native-backed-views-within-their-storage-lifetime"></a>

### `TR-NATIVE-LIFETIME` — Keep native-backed views within their storage lifetime

**When it applies:** You expose native-backed strings, buffers, temporary copies, mappings, or managed handles whose retention and release lifetime matters.

**What goes wrong:** You assume a reachable Java value keeps the required native allocation or original handle owner valid, or that checking one execution-entry route covers every raw alias.

**Why it matters:** A native-backed string can escape into shared AST caches and outlive its originating context or native library. Keeping a pointer wrapper reachable is insufficient if context shutdown can independently invalidate the memory it represents.

**How to fix it:** Copy into managed storage before the native memory can become invalid, or prove and enforce the longer native lifetime. For borrowed module or call-scoped memory, copying at creation is often the appropriate ownership boundary.

**Before**

```java
TruffleString name = fromNative.execute(
                pointer, 0, byteLength, encoding, false);
sharedNames.put(key, name);
// Closing the originating context can invalidate pointer's storage.
```

**After**

```java
TruffleString name = fromNative.execute(
                pointer, 0, byteLength, encoding, true);
sharedNames.put(key, name);
```

**Further reading:** See [FromNativePointerNode.execute](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.FromNativePointerNode.html#execute(java.lang.Object,int,int,com.oracle.truffle.api.strings.TruffleString.Encoding,boolean)).

---

<a name="do-not-reuse-an-incompatible-outer-lookup-node-inside-an-inner-context"></a>

### `TR-INNER-CONTEXT` — Do not reuse an incompatible outer lookup node inside an inner context

**When it applies:** Language code switches contexts or performs callback work requiring a particular current context and compatible lookup location.

**What goes wrong:** You use an incompatible outer node for an inner-context lookup, or mistake an active underlying context for the currently entered one.

**Why it matters:** The lookup node supplies an execution-location and sharing association, not merely an optional hint with no contract. The outer node may not be valid for execution in the inner context's sharing layer.

**How to fix it:** Use an appropriate node from the inner execution or a node-free lookup where no suitable location exists. You can continue sharing the static context-reference object; the incompatible argument is the adopted node, not the reference itself.

**Before**

```java
Object previous = inner.enter(this);
try {
    MyContext current = CONTEXT_REFERENCE.get(this); // Outer node.
    inspect(current);
} finally {
    inner.leave(this, previous);
}
```

**After**

```java
Object previous = inner.enter(this);
try {
    MyContext current = CONTEXT_REFERENCE.get(null);
    inspect(current);
} finally {
    inner.leave(this, previous);
}
```

**Further reading:** See [ContextReference.get](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.ContextReference.html#get(com.oracle.truffle.api.nodes.Node)) and [TruffleContext](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleContext.html).

---

<a name="cooperate-during-long-running-and-blocking-work"></a>

### `TR-SAFEPOINTS` — Cooperate during long-running and blocking work

**When it applies:** Language execution performs custom loops, recursive work, or potentially long waits while a polyglot context is entered.

**What goes wrong:** You assume every Java loop or boundary method automatically polls guest safepoints, or wait for another thread without allowing thread-local actions to run.

**Why it matters:** Cancellation, inspection, and other thread-local actions depend on the executing thread reaching safepoints. A waiting thread can deadlock with a worker whose completion requires an action on that waiting thread.

**How to fix it:** Ensure execution reaches safepoints within bounded intervals, including on paths that do not call back into guest functions. Root and loop nodes already provide automatic polls; cover additional custom loops and long-running boundary work explicitly where necessary.

**Before**

```java
long total(int[] values) {
    long result = 0;
    for (int value : values) {
        result += value;
    }
    return result;
}
```

**After**

```java
long total(Node location, int[] values) {
    long result = 0;
    for (int value : values) {
        result += value;
        TruffleSafepoint.poll(location);
    }
    return result;
}
```

**Further reading:** See [Safepoint Tutorial](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/Safepoint/#language-support) and [TruffleSafepoint](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleSafepoint.html).

---

<a name="do-not-grant-concurrent-context-access-just-to-suppress-an-error"></a>

### `TR-THREAD-ACCESS` — Do not grant concurrent context access just to suppress an error

**When it applies:** You implement thread-access policy or callbacks that can run on other threads.

**What goes wrong:** You return `true` from `isThreadAccessAllowed` merely because a callback otherwise fails with a concurrent-access error.

**Why it matters:** The hook grants permission; it does not synchronize guest objects, context state, or native resources. Allowing concurrent execution changes the implementation's obligations for all reachable mutable state, not just the failing callback.

**How to fix it:** Keep concurrent entry disallowed unless the language implementation actually supports it. For language-owned background work that needs only thread-safe host data, use `Env.createSystemThread`. A system thread cannot enter the context or run guest code; stop and join it during `disposeContext`. An existing host callback can signal the same thread-safe data directly without entering the context.

**Before**

```java
@Override
protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
    return true; // Only intended to make a background callback work.
}

void backgroundCallback() {
    enterOriginalContextAndInvokeGuestOperation();
}
```

**After**

```java
@Override
protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
    return singleThreaded;
}

void backgroundCallback(ConcurrentWaitRegistry registry, WaitKey key) {
    registry.signal(key); // Thread-safe host-only operation; no context entry.
}
```

**Further reading:** See [thread-access policy](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#isThreadAccessAllowed(java.lang.Thread,boolean)) and [creating and shutting down system threads](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#createSystemThread(java.lang.Runnable,java.lang.ThreadGroup)).

---

<a name="release-context-owned-external-resources-explicitly"></a>

### `TR-RESOURCE-CLEANUP` — Release context-owned external resources explicitly

**When it applies:** A language context owns file channels, native allocations, or similar external resources.

**What goes wrong:** You assume implementing `AutoCloseable` calls `close` automatically, or defer all resource release until Java garbage collection or object finalization.

**Why it matters:** Closing a context does not itself invoke arbitrary Java resource wrappers' close methods unless the language connects that lifecycle. A small heap wrapper can retain a large native allocation without creating proportional Java heap pressure, so garbage collection is not a timely release policy.

**How to fix it:** Close resources when no longer needed and connect remaining owned resources to context shutdown, no later than disposal. Keep guest-executing cleanup in the appropriate earlier lifecycle phase; disposal itself must only release resources without guest execution.

**Before**

```java
final class MyContext {
    final NativeAllocation memory;
    // The wrapper is AutoCloseable, but nothing calls close().
}

@Override
protected void disposeContext(MyContext context) {
    // Assume the Java collector will eventually free native memory.
}
```

**After**

```java
@Override
protected void disposeContext(MyContext context) {
    context.memory.close();
}

// A lifecycle test uses a language test hook, not a new guest-visible API.
NativeAllocation allocation = testLanguage.allocationOwnedBy(context);
context.close();
assertTrue(allocation.isReleased());
```

**Further reading:** See [finalizeContext](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#finalizeContext(C)) and [disposeContext](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#disposeContext(C)).

---

<a name="keep-source-cache-anchors-for-the-intended-reuse-lifetime"></a>

### `TR-SOURCE-CACHE-LIFETIME` — Keep source cache anchors for the intended reuse lifetime

**When it applies:** Internal sources are parsed repeatedly or parsed code should remain reusable across contexts.

**What goes wrong:** You assume `cached(true)` permanently retains parsed code, or keep only generated source text and discard every `Source` reference while expecting its code cache to survive.

**Why it matters:** The source participates in the code cache's reachability lifetime. Losing the client-side source anchor can allow eviction and repeated parsing or compilation; retaining every source indefinitely can instead retain unused code.

**How to fix it:** Use `Env.parseInternal` or `parsePublic` as appropriate rather than bypassing framework parse caching without an equivalent lifetime design. Retain sources for the intended reuse window in an owner with a deliberate retention policy.

**Before**

```java
CallTarget parseHelper(TruffleLanguage.Env env, SourceKey key) {
    Source source = buildHelperSource(key);
    return env.parseInternal(source); // No deliberate source-lifetime anchor.
}
```

**After**

```java
final Map<SourceKey, Source> helperSources = new HashMap<>();

CallTarget parseHelper(TruffleLanguage.Env env, SourceKey key) {
    Source source = helperSources.computeIfAbsent(key, this::buildHelperSource);
    return env.parseInternal(source);
}
```

**Further reading:** See [SourceBuilder.cached](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.SourceBuilder.html#cached(boolean)) and [Code sharing overview](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/AOTOverview/#code-sharing-within-the-same-isolateprocess).

---

<a name="exercise-the-modes-that-mutate-reusable-code"></a>

### `TR-EXECUTION-MODES` — Exercise the modes that mutate reusable code

**When it applies:** A change affects code sharing, quickening, another mutation of reusable execution state, or the interpreter's execution and compilation modes.

**What goes wrong:** Tests miss the changed transition or ownership mode, or a performance check overlooks host compilation or treats per-iteration instrumentation as an unperturbed peak measurement.

**Why it matters:** The single-context path can hide ownership and invalidation errors. Merely using several threads does not exercise a race if those threads never reach the relevant quickening or cache transition on reusable code.

**How to fix it:** Test both isolated and explicitly shared engines where supported. Choose workloads that actually trigger the changed transition, verify results in each context, and include concurrent mutation of shared code when that is supported.

**Before**

```java
try (Context context = Context.newBuilder().build()) {
    verifyResult(context.eval(workload));
}
```

**After**

```java
try (Engine engine = Engine.create()) {
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
        Callable<Void> task = () -> {
            try (Context context = Context.newBuilder().engine(engine).build()) {
                startTogether.await(timeoutSeconds, TimeUnit.SECONDS);
                for (int n = 0; n < repetitions; n++) {
                    verifyResult(context.eval(transitionWorkload));
                }
            }
            return null;
        };
        Future<Void> first = workers.submit(task);
        Future<Void> second = workers.submit(task);
        first.get(timeoutSeconds, TimeUnit.SECONDS);
        second.get(timeoutSeconds, TimeUnit.SECONDS);
    } finally {
        workers.shutdownNow();
        assertTrue(workers.awaitTermination(timeoutSeconds, TimeUnit.SECONDS));
    }
}
```

**Further reading:** See [Bytecode execution tiers](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/BytecodeTier.html) and [Context-independent code](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/AOTOverview/#supporting-context-independent-code).

---

<a name="handle-the-at-most-once-contract-of-leave-and-reenter-waits"></a>

### `TR-BLOCKING-CONTRACT` — Handle the at-most-once contract of leave-and-reenter waits

**When it applies:** You temporarily leave the current context with the interruptible `TruffleContext.leaveAndEnter` overload, available since 23.1.

**What goes wrong:** You assume the callback must execute exactly once and its normal result is the only possible return value.

**Why it matters:** The callback executes at most once, not necessarily once: processing an action before leaving can fail first. An independently thrown `InterruptedException`, without context cancellation or exit, produces a null result.

**How to fix it:** Keep the callback host-only and make its handoff protocol tolerate failure before callback invocation. Handle independent interruption explicitly instead of dereferencing the result or treating it as successful completion.

**Before**

```java
Object item = context.leaveAndEnter(location,
                Interrupter.THREAD_INTERRUPT, BlockingQueue::take, queue);
consumeGuestInput(item.toString()); // Null is not a successful queue result.
```

**After**

```java
record WaitResult(boolean independentlyInterrupted, Object item) {}

WaitResult awaitHostInput(TruffleContext context, Node location,
                BlockingQueue<Object> queue) {
    Object item = context.leaveAndEnter(location,
                    Interrupter.THREAD_INTERRUPT, BlockingQueue::take, queue);
    if (item == null) {
        return new WaitResult(true, null);
    }
    return new WaitResult(false, item);
}
```

**Further reading:** See [leaveAndEnter](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleContext.html#leaveAndEnter(com.oracle.truffle.api.nodes.Node,com.oracle.truffle.api.TruffleSafepoint.Interrupter,com.oracle.truffle.api.TruffleSafepoint.InterruptibleFunction,T)) and [Safepoint support for blocking calls](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/Safepoint/#language-support).

---

<a name="stop-and-join-owned-threads-in-the-correct-lifecycle-phase"></a>

### `TR-THREAD-SHUTDOWN` — Stop and join owned threads in the correct lifecycle phase

**When it applies:** Your language creates polyglot threads or internal system threads through its environment.

**What goes wrong:** Shutdown merely requests interruption, defers joining until after the responsible lifecycle hook returns, or uses the same hook for both thread kinds.

**Why it matters:** Requesting termination does not establish that a thread has stopped using context-owned state. Polyglot threads created through `Env.newTruffleThreadBuilder` must be stopped and joined during `finalizeContext`.

**How to fix it:** Track the actual threads your language starts, reject new work during shutdown, request termination using the worker's real protocol, and join before the required hook returns. Do not indiscriminately join embedder-owned or other languages' threads.

**Before**

```java
@Override
protected void disposeContext(MyContext context) {
    context.systemWorker.interrupt(); // Worker may still be using resources.
    context.releaseWorkerResources();
}
```

**After**

```java
// MyContext.stopRequested is volatile. The worker is a createSystemThread
// thread, never the thread performing context disposal.
@Override
protected void disposeContext(MyContext context) {
    context.stopRequested = true;
    Thread worker = context.systemWorker;
    boolean interrupted = false;
    try {
        if (worker != null) {
            worker.interrupt();
            for (;;) {
                try {
                    worker.join();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true; // Still must establish termination.
                }
            }
        }
        context.releaseWorkerResources();
    } finally {
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
```

**Further reading:** See [Context-Accessing Thread Builder](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#newTruffleThreadBuilder(java.lang.Runnable)) and [finalizeContext](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#finalizeContext(C)).

---

<a name="honor-native-access-permission-when-selecting-a-backend"></a>

### `TR-NATIVE-PERMISSIONS` — Honor native-access permission when selecting a backend

**When it applies:** A language selects a native backend or routes implementation-owned and user-provided native operations.

**What goes wrong:** A nominally non-native backend still selects native operations despite denied permission, or user-provided native code reaches a trusted implementation backend that bypasses its authorization.

**Why it matters:** A backend name is not a permission check. A later implementation choice can bypass the intended boundary even when the default implementation is safe.

**How to fix it:** Check the current context's `Env.isNativeAccessAllowed()` when choosing an implementation that needs native access. Select a genuinely safe implementation or reject the request when permission is absent.

**Before**

```java
MemoryBackend selectBackend(TruffleLanguage.Env env, String requested) {
    return backends.get(requested).create();
}
```

**After**

```java
MemoryBackend selectBackend(TruffleLanguage.Env env, String requested) {
    BackendFactory factory = backends.get(requested);
    if (factory.requiresNativeAccess() && !env.isNativeAccessAllowed()) {
        throw new SecurityException("Native memory backend is not permitted");
    }
    return factory.create();
}
```

**Further reading:** See [Env.isNativeAccessAllowed](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#isNativeAccessAllowed()) and [Native Function Interface](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/NFI/).

---

<a name="use-the-configured-file-systems-actual-capabilities"></a>

### `TR-FILE-SYSTEM-CONTRACT` — Use the configured file system's actual capabilities

**When it applies:** Guest file operations use TruffleFile and require platform-dependent path or attribute behavior.

**What goes wrong:** You assume TruffleFile presents a POSIX file system on every host or for every embedder-provided file system.

**Why it matters:** TruffleFile abstracts access, not a universal POSIX personality. The default file system reflects host behavior, and a configured file system can support a different set of optional operations.

**How to fix it:** Define which file-system capabilities the guest operation requires and translate unsupported capabilities deliberately. Use the environment's configured path separators and TruffleFile path operations rather than inferring the configured file system from host-only properties.

**Before**

```java
Set<PosixFilePermission> readGuestPermissions(TruffleFile file)
                throws IOException {
    // Assumes all TruffleFile implementations support POSIX permissions.
    return file.getPosixPermissions();
}
```

**After**

```java
Set<PosixFilePermission> readGuestPermissions(TruffleFile file)
                throws IOException {
    try {
        return file.getPosixPermissions();
    } catch (UnsupportedOperationException unsupported) {
        throw unsupportedFileSystemFeature("POSIX permissions", unsupported);
    }
}
```

**Further reading:** See [FileSystem SPI](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html) and [TruffleFile.getPosixPermissions](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleFile.html#getPosixPermissions(java.nio.file.LinkOption...)).

---

<a name="match-file-access-to-the-operations-resource-authority"></a>

### `TR-FILE-PRIVILEGES` — Match file access to the operation's resource authority

**When it applies:** You choose file access for guest-controlled user-file operations, optional debugger source content, or implementation-resource loading, including checks on relative paths.

**What goes wrong:** You grant implementation-resource authority to a user-file operation merely because runtime or debugging code performs it, impose an unsupported universal filter on legitimate implementation-resource access, or omit preliminary checks from an optional lookup's denied-access handling.

**Why it matters:** Internal file access can read language-provided resources even when ordinary file-system privileges are denied. A path inside guest debug metadata does not acquire that authority just because a debugger wants its source.

**How to fix it:** Use `Env.getPublicTruffleFile` for guest-controlled user-file access and let the configured file system and I/O policy decide which operations are permitted. Reserve internal-resource access for language-provided files whose authority and origin justify it.

**Before**

```java
Source loadDebugSource(TruffleLanguage.Env env, String language,
                String guestSuppliedPath) throws IOException {
    TruffleFile file = env.getInternalTruffleFile(guestSuppliedPath);
    return Source.newBuilder(language, file).build();
}
```

**After**

```java
Source loadDebugSource(TruffleLanguage.Env env, String language,
                String guestSuppliedPath) throws IOException {
    TruffleFile file = env.getPublicTruffleFile(guestSuppliedPath);
    return Source.newBuilder(language, file).build();
}
```

**Further reading:** See [Env.getPublicTruffleFile](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#getPublicTruffleFile(java.lang.String)) and [Env.getInternalTruffleFile](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#getInternalTruffleFile(java.lang.String)).

---

<a name="derive-host-service-availability-from-the-environment-grant"></a>

### `TR-ENV-CAPABILITIES` — Derive host-service availability from the environment grant

**When it applies:** A language feature uses environment-configured host services, permissions, or streams, including from an inner context.

**What goes wrong:** You treat a language option's default true value as a host permission, or fail context initialization because that default conflicts with a denied environment capability.

**Why it matters:** A language feature preference does not grant host access. A host can deliberately deny a capability while still allowing the rest of the language to run.

**How to fix it:** Query the environment capability for the particular host operation and derive availability from that grant. An additional language option may narrow availability when the feature needs that policy; it cannot widen the environment's permission.

**Before**

```java
void initialize(TruffleLanguage.Env env) {
    boolean requested = languageOptions.networkingEnabled(); // Defaults to true.
    if (requested && !env.isSocketIOAllowed()) {
        throw CompilerDirectives.shouldNotReachHere("Networking denied");
    }
    networkingAvailable = requested;
}
```

**After**

```java
void initialize(TruffleLanguage.Env env) {
    networkingAvailable = env.isSocketIOAllowed();
}

Object openNetworkResource(Object address, Node location) {
    if (!networkingAvailable) {
        throw guestServiceUnavailable("Networking is unavailable", location);
    }
    return openPermittedNetworkResource(address);
}
```

**Further reading:** See [Env.isSocketIOAllowed](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#isSocketIOAllowed()) and [Env.isCreateProcessAllowed](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#isCreateProcessAllowed()).

---

<a name="match-inner-context-termination-to-entry-and-exit-policy"></a>

### `TR-CONTEXT-TERMINATION` — Match inner-context termination to entry and exit policy

**When it applies:** Your language owns an inner context and exposes termination from another execution thread.

**What goes wrong:** You call `closeExited` from a thread that has not entered that context, or treat hard exit, cancellation, and ordinary close as interchangeable ways to force shutdown hooks.

**Why it matters:** `TruffleContext.closeExited` requires the target context to be entered on the calling thread. Hard-exit notifications run synchronously there before forced unwinding; the method does not wait for execution on other threads to stop.

**How to fix it:** Decide whether the operation means guest hard exit, external cancellation, or ordinary close before choosing the API. Use `closeExited` for the supported entered hard-exit path, not as an external controller's cancellation API.

**Before**

```java
final class InnerTaskController {
    private final TruffleContext inner;

    InnerTaskController(TruffleContext inner) {
        this.inner = inner;
    }

    void cancelTask() {
        inner.closeExited(null, 0); // Requires entry and means hard exit.
    }
}
```

**After**

```java
final class InnerTaskController {
    private final TruffleContext inner;

    InnerTaskController(TruffleContext inner) {
        this.inner = inner;
    }

    void cancelTask() {
        // This controller has no active entry of inner on this thread.
        inner.closeCancelled(null, "Task cancelled by its controller");
    }
}
```

**Further reading:** See [Hard-Exit Entry and Notifications](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleContext.html#closeExited(com.oracle.truffle.api.nodes.Node,int)), [Cancellation Entry and Waiting](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleContext.html#closeCancelled(com.oracle.truffle.api.nodes.Node,java.lang.String)), and [Ordinary Close Preconditions](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleContext.html#close()).

---

## ASTs, frames, and object layouts

<a name="keep-private-backing-slots-out-of-guest-property-names"></a>

### `TR-INTERNAL-STORAGE` — Keep private backing slots out of guest property names

**When it applies:** You store implementation-only backing values on guest objects using the dynamic object model, or derive guest property names or counts from that storage.

**What goes wrong:** You derive an ordinary guest-addressable property name from a visible name plus a supposedly private suffix, or assume a generic shape property count describes exactly the guest-visible property subset.

**Why it matters:** A naming convention does not make a property private. Guest access can collide with or expose the implementation slot if the language maps those names to ordinary properties.

**How to fix it:** Use a storage mechanism with the required private-slot semantics, such as a `HiddenKey`, rather than an ordinary string property. A `HiddenKey` is identity-based and excluded from property enumeration.

**Before**

```java
private static final String BACKING_NAME = "value$implementationStorage";

void initializeBacking(DynamicObject receiver, Object value,
                DynamicObject.PutNode put) {
    put.execute(receiver, BACKING_NAME, value);
}

Object readBacking(DynamicObject receiver, Object missing,
                DynamicObject.GetNode get) {
    return get.execute(receiver, BACKING_NAME, missing);
}
```

**After**

```java
private static final HiddenKey BACKING_KEY = new HiddenKey("value backing");

void initializeBacking(DynamicObject receiver, Object value,
                DynamicObject.PutNode put) {
    put.execute(receiver, BACKING_KEY, value);
}

Object readBacking(DynamicObject receiver, Object missing,
                DynamicObject.GetNode get) {
    return get.execute(receiver, BACKING_KEY, missing);
}
```

**Further reading:** See [HiddenKey Contract](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/object/HiddenKey.html) and [Dynamic Object Model](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/DynamicObjectModel/).

---

<a name="include-all-semantic-inputs-in-stable-cache-keys"></a>

### `TR-CACHE-KEY` — Include all semantic inputs in stable cache keys

**When it applies:** You cache compiled artifacts or derived metadata using strings, encodings, domain-relative identifiers, or version-relative code locations.

**What goes wrong:** You omit an input or owning domain that gives a cache key its meaning, or use a mutable string representation as a map key.

**Why it matters:** A hit can return an artifact compiled for different semantics. Mutation can also invalidate the key's equality or hash behavior after insertion.

**How to fix it:** Include every relevant semantic input and freeze the key's contents. Use a deliberate equality/hash policy for Truffle strings, with the required common encoding.

**Before**

```java
CallTarget target = compiledFormats.get(textKey);
if (target == null) {
    target = compileFormat(textKey, encoding);
    compiledFormats.put(textKey, target);
}
```

**After**

```java
record FormatKey(StringKey text, Encoding encoding) {}

StringKey textKey = immutableStringKey(format, encoding);
FormatKey key = new FormatKey(textKey, encoding);
CallTarget target = compiledFormats.get(key);
if (target == null) {
    target = compileFormat(textKey, encoding);
    compiledFormats.put(key, target);
}
```

**Further reading:** See [String Equality and Comparison](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/TruffleStrings/#string-equality-and-comparison).

---

<a name="keep-invocation-state-out-of-shared-ast-nodes"></a>

### `TR-EXECUTION-STATE` — Keep invocation state out of shared AST nodes

**When it applies:** A parsed AST can execute concurrently in different contexts, be reentered before an earlier invocation returns, or outlive the module/context whose values it uses.

**What goes wrong:** You store an invocation's temporary buffer, stack pointer, or intermediate result, or a module/context-owned value, in a reusable AST node field.

**Why it matters:** AST nodes represent reusable code, not one execution. With `ContextPolicy.SHARED`, parsed ASTs can execute concurrently even if each individual context rejects concurrent access.

**How to fix it:** Keep temporary state in execution-local variables, the execution frame, or an explicitly invocation-owned state object. Thread-local storage alone does not solve reentrancy.

**Before**

```java
final class EvaluateNode extends Node {
    private final Object[] arguments = new Object[2];

    Object execute(Object first, Object second) {
        arguments[0] = first;
        arguments[1] = second;
        return invokeGuest(arguments);
    }
}
```

**After**

```java
final class EvaluateNode extends Node {
    Object execute(Object first, Object second) {
        Object[] arguments = {first, second};
        return invokeGuest(arguments);
    }
}
```

**Further reading:** See [SHARED Context Policy](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.ContextPolicy.html#SHARED).

---

<a name="preserve-the-asts-parent-child-structure"></a>

### `TR-ADOPTION` — Preserve the AST's parent-child structure

**When it applies:** Handwritten nodes own executable cached children, or node logic relies on AST ancestry.

**What goes wrong:** You store an ordinary adoptable helper in a static field or an unannotated Java field, or assume that a child's immediate parent always has one language-specific class.

**Why it matters:** The framework discovers children through the AST child contract, not arbitrary Java references. Adoption, replacement, cloning, and source-location lookup depend on that structure.

**How to fix it:** Give each AST its own ordinary cached helper, declare it with `@Child` or `@Children`, and let adoption establish its parent relationship. Use `insert` and the appropriate child assignment for additions to an already adopted tree; insertion of instrumentable nodes during execution also needs the documented insertion notification.

**Before**

```java
final class ReserveNode extends Node {
    private static final AllocateNode allocate = AllocateNodeGen.create();

    Object execute(VirtualFrame frame, int size) {
        return allocate.execute(frame, size);
    }
}
```

**After**

```java
final class ReserveNode extends Node {
    @Child private AllocateNode allocate = AllocateNodeGen.create();

    Object execute(VirtualFrame frame, int size) {
        return allocate.execute(frame, size);
    }
}
```

**Further reading:** See [Node.Child](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/Node.Child.html) and [Node.insert](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html#insert(T)).

---

<a name="look-up-shared-language-metadata-without-a-context-detour"></a>

### `TR-LANGUAGE-REFERENCE` — Look up shared language metadata without a context detour

**When it applies:** Execution or an AST tooling callback needs metadata owned by the language instance or by the current context.

**What goes wrong:** You hide language-owned metadata behind a context lookup, or derive current-context metadata through a dynamic guest receiver solely in the hope that it becomes constant.

**Why it matters:** In multi-context execution, a context reference need not fold, but the language associated with a root remains fixed. This distinction matters when a language-owned option or layout is needed as a partial-evaluation constant, including a bounded exploded loop.

**How to fix it:** Use a `LanguageReference` with an available partial-evaluation-constant, adopted node from the executing AST, or pass the already obtained language explicitly. Current references avoid the need to retain an extra language field in every AST node.

**Before**

```java
Object execute(VirtualFrame frame) {
    MyLanguage language = MyContext.get(this).getLanguage();
    return evaluateWithLayout(frame, language.layout());
}
```

**After**

```java
private static final LanguageReference<MyLanguage> LANGUAGE =
                LanguageReference.create(MyLanguage.class);

Object execute(VirtualFrame frame) {
    MyLanguage language = LANGUAGE.get(this);
    return evaluateWithLayout(frame, language.layout());
}
```

**Further reading:** See [LanguageReference.get](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.LanguageReference.html#get(com.oracle.truffle.api.nodes.Node)) and [Context-Independent Code](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/AOTOverview/#supporting-context-independent-code).

---

<a name="make-identity-guarded-metadata-recur-at-the-cache"></a>

### `TR-IDENTITY-CACHE` — Make identity-guarded metadata recur at the cache

**When it applies:** An inline cache or executable-code cache expects a stable identity to recur.

**What goes wrong:** Each request constructs a fresh metadata wrapper or root key, while the cache expects that identity to recur.

**Why it matters:** Semantically identical requests then miss the identity guard, consume specialization entries, and can reach the generic path without ever benefiting from the cache. A correct ownership guard does not make a constantly changing key stable.

**How to fix it:** Reuse safely shareable immutable metadata at its producer, or guard an already stable semantic component with the required validity checks. Do not blindly replace identity comparison with `equals`, intern context-owned guest objects, or canonicalize metadata that contains mutable context state.

**Before**

```java
final class Request {
    final Layout layout;

    Metadata metadata() {
        return new Metadata(layout.typeCodes()); // Fresh identity on every call.
    }
}

@Specialization(guards = "metadata == cachedMetadata", limit = "3")
static Result doCached(Metadata metadata,
                @Cached("metadata") Metadata cachedMetadata,
                @Cached("prepare(cachedMetadata)") Prepared prepared) {
    return prepared.execute();
}
```

**After**

```java
final class Layout {
    final Metadata metadata;

    Layout(TypeCode[] immutableTypeCodes) {
        metadata = new Metadata(immutableTypeCodes);
    }
}

final class Request {
    final Layout layout;

    Metadata metadata() {
        return layout.metadata;
    }
}
```

**Further reading:** See [Cached](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/Cached.html) and [Dynamic Object Caching Considerations](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/DynamicObjectModel/#caching-considerations).

---

<a name="prepare-dynamic-object-storage-before-concurrent-publication"></a>

### `TR-SHARED-SHAPES` — Prepare dynamic-object storage before concurrent publication

**When it applies:** Your language permits the same DynamicObject to be accessed by multiple threads.

**What goes wrong:** You publish an ordinary non-shared-shape object to another thread and assume safe publication alone prevents property storage from moving or being reused.

**Why it matters:** Shape transitions can change object storage. Shared shapes prevent storage locations from being reused for other fields, which is a separate obligation from publishing the reference and synchronizing writes.

**How to fix it:** Establish the language's shared-object invariant before concurrent access, using the supported shared-shape operation rather than manually changing storage. Resolve any necessary obsolete-layout transitions while the object is still private; existing shape-validity guards may already enforce that requirement.

**Before**

```java
final class PublishNode extends Node {
    void publish(DynamicObject object) {
        readyObjects.add(object);
    }
}
```

**After**

```java
final class PublishNode extends Node {
    @Child private DynamicObject.MarkSharedNode markShared =
                    DynamicObject.MarkSharedNode.create();

    void publish(DynamicObject object) {
        markShared.execute(object);
        readyObjects.add(object);
    }
}
```

**Further reading:** See [DynamicObject.MarkSharedNode](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/object/DynamicObject.MarkSharedNode.html) and [Shape.makeSharedShape contract](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/object/Shape.html#makeSharedShape()).

---

<a name="preserve-useful-root-type-information-on-interpreter-call-paths"></a>

### `TR-TYPED-ROOT-CALLS` — Preserve useful root type information on interpreter call paths

**When it applies:** A measured interpreter call path can retain a known concrete root type and uses the root-based call APIs introduced in 25.6.

**What goes wrong:** You discard useful concrete root type information behind an opaque `CallTarget` and assume this has no effect on host compilation, or bypass the call protocol by invoking `execute` directly.

**Why it matters:** A root-typed call can expose the root's `execute` implementation to the host compiler and permit deeper inlining. This is an opportunity, not a guarantee that all root calls inline or become faster.

**How to fix it:** Where ownership and dispatch semantics permit it, retain the concrete root type and use its supported call entry point. Configure and initialize the root before exposing it for execution.

**Before**

```java
final class FunctionCode {
    private final CallTarget target;

    FunctionCode(WorkRoot configuredRoot) {
        target = configuredRoot.getCallTarget();
    }

    Object invoke(Node caller, int callerBci, Object[] arguments) {
        return target.call(caller, callerBci, arguments);
    }
}
```

**After**

```java
final class FunctionCode {
    private final WorkRoot root;

    FunctionCode(WorkRoot configuredRoot) {
        configuredRoot.initializeCallTarget();
        root = configuredRoot;
    }

    Object invoke(Node caller, int callerBci, Object[] arguments) {
        return root.call(caller, callerBci, arguments);
    }
}
```

**Further reading:** See [RootNode.call, since 25.6](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html#call(com.oracle.truffle.api.nodes.Node,int,java.lang.Object[])) and [Host Compilation](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/HostOptimization/).

---

<a name="supply-privileged-access-for-a-custom-object-layout"></a>

### `TR-LAYOUT-LOOKUP` — Supply privileged access for a custom object layout

**When it applies:** You build shapes for a custom `DynamicObject` subclass using the lookup-taking layout API, available since 24.2.

**What goes wrong:** You pass a public or unrelated lookup and assume that naming the layout class grants access to its dynamic fields.

**Why it matters:** The builder requires a full-privilege lookup from the layout class or a class in the same module. Java inheritance alone does not prove that a chosen lookup can access the required layout fields, including inherited fields.

**How to fix it:** Create the lookup in the layout class, or deliberately use a full-privilege lookup from its module. Keep the shape's layout class consistent with the instances that receive that shape.

**Before**

```java
final class PayloadObject extends DynamicObject {
    @DynamicField private Object value;

    PayloadObject(Shape shape) {
        super(shape);
    }

    static Shape createShape() {
        return Shape.newBuilder()
                .layout(PayloadObject.class, MethodHandles.publicLookup())
                .build();
    }
}
```

**After**

```java
final class PayloadObject extends DynamicObject {
    @DynamicField private Object value;

    PayloadObject(Shape shape) {
        super(shape);
    }

    static Shape createShape() {
        return Shape.newBuilder()
                .layout(PayloadObject.class, MethodHandles.lookup())
                .build();
    }
}
```

**Further reading:** See [Shape.Builder.layout](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/object/Shape.Builder.html#layout(java.lang.Class,java.lang.invoke.MethodHandles.Lookup)).

---

<a name="preserve-receiver-safety-before-disabling-shape-checks"></a>

### `TR-STATIC-SHAPE-SAFETY` — Preserve receiver safety before disabling shape checks

**When it applies:** You choose Static Object Model property types or configure it to omit receiver-shape checks.

**What goes wrong:** You narrow a property's type without covering every stored value, or disable safety checks without proving that every access uses a compatible receiver.

**Why it matters:** An incompatible receiver can cause accesses to incompatible storage and memory corruption, not merely a recoverable guest type error. An optimization assumption about common inputs is not a safety invariant.

**How to fix it:** Keep receiver-shape checks enabled unless a verifier or equivalent language checks establish compatibility for every access. Review construction, inheritance, foreign inputs, and all alternate access paths before relying on that invariant.

**Before**

```java
final class PayloadLayout {
    final StaticProperty value = new DefaultStaticProperty("value");
    final StaticShape<DefaultStaticObjectFactory> shape;

    PayloadLayout(TruffleLanguage<?> language) {
        shape = StaticShape.newBuilder(language)
                        .property(value, Object.class, false)
                        .safetyChecks(false)
                        .build();
    }

    Object read(Object receiver) {
        // A caller's expected layout is not a verified receiver contract.
        return value.getObject(receiver);
    }
}
```

**After**

```java
final class PayloadLayout {
    final StaticProperty value = new DefaultStaticProperty("value");
    final StaticShape<DefaultStaticObjectFactory> shape;

    PayloadLayout(TruffleLanguage<?> language) {
        shape = StaticShape.newBuilder(language)
                        .property(value, Object.class, false)
                        .safetyChecks(true)
                        .build();
    }

    Object create(Object initialValue) {
        Object receiver = shape.getFactory().create();
        value.setObject(receiver, initialValue);
        return receiver;
    }

    Object read(Object receiver) {
        return value.getObject(receiver); // Framework checks compatibility.
    }
}
```

**Further reading:** See [Static Object Model Safety Checks](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/StaticObjectModel/#safety-checks) and [StaticShape.Builder.safetyChecks](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/StaticShape.Builder.html#safetyChecks(boolean)).

---

<a name="keep-property-handles-with-their-declaring-layout"></a>

### `TR-STATIC-SHAPE-CONSTRUCTION` — Keep property handles with their declaring layout

**When it applies:** You construct independent static-object layouts, extend a parent shape, or provide a custom superclass and factory interface.

**What goes wrong:** You reuse one `StaticProperty` object across builders, confuse its ownership with its string ID, or reuse a builder after building its shape.

**Why it matters:** A property handle is associated with its declaring layout, not a global name-based accessor. IDs must be non-null, nonempty, and unique within one builder, but distinct builders can use the same ID with distinct handles.

**How to fix it:** Allocate fresh property handles for independently built layouts and retain each handle alongside its shape. Register each handle with only its declaring builder, build once, and allocate through the resulting factory.

**Before**

```java
StaticProperty value = new DefaultStaticProperty("value");
StaticShape.Builder first = StaticShape.newBuilder(language);
StaticShape.Builder second = StaticShape.newBuilder(language);
first.property(value, int.class, false);
second.property(value, int.class, false); // The same handle cannot belong to both.
StaticShape<DefaultStaticObjectFactory> firstShape = first.build();
StaticShape<DefaultStaticObjectFactory> secondShape = second.build();
```

**After**

```java
record IntLayout(StaticShape<DefaultStaticObjectFactory> shape,
                StaticProperty value) {
    Object create(int initialValue) {
        Object object = shape.getFactory().create();
        value.setInt(object, initialValue);
        return object;
    }
}

static IntLayout newLayout(TruffleLanguage<?> language) {
    StaticProperty value = new DefaultStaticProperty("value");
    StaticShape.Builder builder = StaticShape.newBuilder(language);
    builder.property(value, int.class, false);
    return new IntLayout(builder.build(), value);
}

IntLayout first = newLayout(language);
IntLayout second = newLayout(language); // Same ID, distinct handle and layout.
Object firstObject = first.create(11);
Object secondObject = second.create(22);
int firstValue = first.value().getInt(firstObject);
int secondValue = second.value().getInt(secondObject);
```

**Further reading:** See [Static Object Model Construction](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/StaticObjectModel/#getting-started) and [Property Registration](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/StaticShape.Builder.html#property(com.oracle.truffle.api.staticobject.StaticProperty,java.lang.Class,boolean)).

---

<a name="implement-missing-argument-semantics-before-indexed-loads"></a>

### `TR-ARGUMENT-BOUNDS` — Implement missing-argument semantics before indexed loads

**When it applies:** The calling convention permits fewer arguments than a function body may read, or guest arity errors must be reported by the language.

**What goes wrong:** You assume that `LoadArgument` supplies a guest default, missing-value sentinel, or guest arity error when the requested argument is absent.

**Why it matters:** The operation reads an indexed entry from the frame's argument array. Its generated builder contract specifies `IndexOutOfBoundsException` for an out-of-range index; it does not implement your language's calling convention.

**How to fix it:** Establish argument count and layout under the actual calling convention. Normalize arguments at call entry when that is the chosen protocol, or implement the required bounds/default/error behavior before reading an optional entry.

**Before**

```java
b.beginReturn();
    b.emitLoadArgument(1); // An absent array entry is not MissingArgument.INSTANCE.
b.endReturn();
```

**After**

```java
enum MissingArgument { INSTANCE }

@Operation
public static final class ReadOptionalSecondArgument {
    @Specialization
    public static Object read(VirtualFrame frame) {
        Object[] arguments = frame.getArguments();
        if (arguments.length > 1) {
            return arguments[1];
        }
        return MissingArgument.INSTANCE;
    }
}

// In the parser:
b.beginReturn();
    b.emitReadOptionalSecondArgument();
b.endReturn();
```

**Further reading:** See [Frame Argument Array](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/frame/Frame.html#getArguments()) and [Bytecode operations](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/package-summary.html).

---

<a name="read-slot-metadata-from-its-owning-frame-layout"></a>

### `TR-FRAME-DESCRIPTOR` — Read slot metadata from its owning frame layout

**When it applies:** Handwritten frame access or scope inspection uses an indexed slot to look up a variable's name or other layout metadata.

**What goes wrong:** You interpret a lexical frame's slot index using the enclosing root's frame descriptor without establishing that both frames have the same layout.

**Why it matters:** An indexed slot is meaningful within its owning descriptor, not a universal variable identifier. A language can give a captured or block-scope frame a descriptor different from the currently executing root's descriptor.

**How to fix it:** Resolve the frame that owns the variable according to the language's lexical-scope semantics, then obtain its descriptor with `Frame.getFrameDescriptor()`. Pair the slot index with that layout throughout lookup and execution; changing only the descriptor does not repair an index selected for a different layout.

**Before**

```java
final class ScopedSlotNameNode extends Node {
    private final int slot;

    ScopedSlotNameNode(int slot) {
        this.slot = slot;
    }

    Object execute(Frame declaringFrame) {
        FrameDescriptor descriptor = getRootNode().getFrameDescriptor();
        return descriptor.getSlotName(slot);
    }
}
```

**After**

```java
final class ScopedSlotNameNode extends Node {
    private final FrameDescriptor declaringLayout;
    private final int slot;

    ScopedSlotNameNode(FrameDescriptor declaringLayout, int slot) {
        this.declaringLayout = declaringLayout;
        this.slot = slot;
    }

    Object execute(Frame declaringFrame) {
        FrameDescriptor descriptor = declaringFrame.getFrameDescriptor();
        assert descriptor == declaringLayout;
        return descriptor.getSlotName(slot);
    }
}
```

**Further reading:** See [Frame Layout Owner](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/frame/Frame.html#getFrameDescriptor()) and [Indexed Slot Names](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/frame/FrameDescriptor.html#getSlotName(int)).

---

<a name="preserve-caller-arguments-when-adapting-a-call"></a>

### `TR-FRAME-ARGUMENT-OWNERSHIP` — Preserve caller arguments when adapting a call

**When it applies:** A forwarding node changes a receiver, method descriptor, or other argument metadata before invoking another guest target.

**What goes wrong:** You overwrite a borrowed argument array as though adapting the callee also transferred ownership of the caller's activation state.

**Why it matters:** `Frame.getArguments()` retrieves the arguments used for that activation; it does not promise a detached copy. If the adapter receives that array or another live caller-owned array, changing its entries can change what the caller observes after return, during a callback, or while unwinding a failure.

**How to fix it:** Preserve the caller's required entries while preparing the callee's arguments. Use an existing repacking operation with the correct metadata semantics, construct a new callee argument array, or copy the borrowed array before changing its entries when that layout is intentionally preserved.

**Before**

```java
final class ForwardNode extends Node {
    @Child private DirectCallNode target;

    ForwardNode(CallTarget callee) {
        target = DirectCallNode.create(callee);
    }

    Object execute(VirtualFrame caller, Object newReceiver) {
        Object[] calleeArguments = caller.getArguments();
        calleeArguments[0] = newReceiver; // Also changes the live caller.
        return target.call(calleeArguments);
    }
}
```

**After**

```java
final class ForwardNode extends Node {
    @Child private DirectCallNode target;

    ForwardNode(CallTarget callee) {
        target = DirectCallNode.create(callee);
    }

    Object execute(VirtualFrame caller, Object newReceiver) {
        Object[] calleeArguments = caller.getArguments().clone();
        calleeArguments[0] = newReceiver;
        return target.call(calleeArguments);
    }
}
```

**Further reading:** See [Frame Argument Access](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/frame/Frame.html#getArguments()) and [Direct Call Arguments](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/DirectCallNode.html#call(java.lang.Object...)).

---

<a name="preserve-descriptor-based-slot-generalization"></a>

### `TR-FRAME-SLOT-POLICY` — Preserve descriptor-based slot generalization

**When it applies:** A handwritten interpreter uses a frame descriptor's indexed slot kinds as a shared type policy for ordinary, non-static local slots.

**What goes wrong:** A generic write updates the current frame with `setObject` but leaves the descriptor's primitive slot kind unchanged after removing the guard or helper that previously generalized it.

**Why it matters:** The descriptor policy and a particular frame's actual value tag are different state. Several frames can share one descriptor.

**How to fix it:** When the language generalizes a local from a primitive representation to a permanent object representation, update its owning descriptor as well as writing the current frame. Audit every writer, including the generic specialization left after a DSL guard or replacement refactoring.

**Before**

```java
final class LocalWriteNode extends Node {
    private final int slot;

    LocalWriteNode(int slot) {
        this.slot = slot;
    }

    void executeWrite(VirtualFrame frame, Object value) {
        FrameDescriptor layout = frame.getFrameDescriptor();
        FrameSlotKind kind = layout.getSlotKind(slot);
        if (value instanceof Long &&
                        (kind == FrameSlotKind.Illegal || kind == FrameSlotKind.Long)) {
            layout.setSlotKind(slot, FrameSlotKind.Long);
            frame.setLong(slot, (Long) value);
        } else {
            frame.setObject(slot, value); // Shared policy can still say Long.
        }
    }
}
```

**After**

```java
final class LocalWriteNode extends Node {
    private final int slot;

    LocalWriteNode(int slot) {
        this.slot = slot;
    }

    void executeWrite(VirtualFrame frame, Object value) {
        FrameDescriptor layout = frame.getFrameDescriptor();
        FrameSlotKind kind = layout.getSlotKind(slot);
        if (value instanceof Long &&
                        (kind == FrameSlotKind.Illegal || kind == FrameSlotKind.Long)) {
            layout.setSlotKind(slot, FrameSlotKind.Long);
            frame.setLong(slot, (Long) value);
        } else {
            layout.setSlotKind(slot, FrameSlotKind.Object);
            frame.setObject(slot, value);
        }
    }
}
```

**Further reading:** See [Indexed Slot-Kind Update](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/frame/FrameDescriptor.html#setSlotKind(int,com.oracle.truffle.api.frame.FrameSlotKind)) and [Per-Frame Value Tag](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/frame/Frame.html#getTag(int)).

---

## Truffle DSL specializations and caches

<a name="order-disjoint-specializations-from-representative-frequencies"></a>

### `TR-SPECIALIZATION-ORDER` — Order disjoint specializations from representative frequencies

**When it applies:** You tune the interpreter dispatch order of DSL specializations that can be reordered without changing which semantics apply.

**What goes wrong:** You order cases from intuition about type popularity, or copy another operation's ordering without checking its workload.

**Why it matters:** Initial specialization selection follows declaration order. A getter and a mutator can see different input populations, and a common specialization can otherwise require unnecessary preceding dispatch checks.

**How to fix it:** Collect specialization executions on representative workloads and consider placing frequent disjoint cases first. Use the specialization histogram with the required generated statistics support; enabling its runtime option alone does not instrument previously generated nodes.

**Before**

```java
@Specialization
static Object readCold(ColdValue value) {
    return value.payload();
}

@Specialization
static Object readHot(HotValue value) {
    return value.payload();
}
```

**After**

```java
abstract class ReadPayloadNode extends Node {
    abstract Object execute(Object value);

    @Specialization
    static Object readHot(HotValue value) {
        return value.payload();
    }

    @Specialization
    static Object readCold(ColdValue value) {
        return value.payload();
    }

    @Fallback
    static Object readOther(Object value) {
        return value;
    }
}

record HotValue(Object payload) { }
record ColdValue(Object payload) { }
```

**Further reading:** See [Specialization Declaration Order](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/Specialization.html) and [Specialization Histogram](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/SpecializationHistogram/).

---

<a name="keep-uncached-dispatch-off-guest-compilation-paths"></a>

### `TR-UNCACHED-PE` — Keep uncached dispatch off guest compilation paths

**When it applies:** You choose cached/uncached helper construction, especially for an operation that should remain visible to partial evaluation and guest compilation.

**What goes wrong:** You invoke an uncached helper for an ordinary compilation-sensitive operation, add a boundary merely to accommodate it, or erase a required cached/uncached factory distinction.

**Why it matters:** Uncached dispatch is designed for slow-path runtime use rather than call-site-specific specialization. Uncached libraries have boundary behavior that prevents the compiler from seeing through their work as it can with the cached version.

**How to fix it:** Adopt a cached library or use the DSL's cached parameters on compilation-sensitive paths. Keep genuinely cold runtime work behind an intentional boundary when uncached dispatch is appropriate there.

**Before**

```java
@Override
public Object execute(VirtualFrame frame) {
    Object receiver = readReceiver(frame);
    return InteropLibrary.getUncached().isNull(receiver);
}
```

**After**

```java
@Child private InteropLibrary interop =
                InteropLibrary.getFactory().createDispatched(3);

@Override
public Object execute(VirtualFrame frame) {
    Object receiver = readReceiver(frame);
    return interop.isNull(receiver);
}
```

**Further reading:** See [LibraryFactory](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/library/LibraryFactory.html) and [GenerateUncached](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/GenerateUncached.html).

---

<a name="handle-expected-lookup-failures-outside-cache-initializers"></a>

### `TR-CACHE-INITIALIZER` — Handle expected lookup failures outside cache initializers

**When it applies:** A DSL cache initializer performs a lookup that can legitimately fail for guest input.

**What goes wrong:** You throw the guest-input failure while trying to initialize the specialization's cached value.

**Why it matters:** A failing initializer cannot install the specialization state that would handle the same input on later executions. Repeated misses can repeatedly enter specialization and deoptimization paths instead of reaching a stable error path.

**How to fix it:** Return an explicit miss sentinel from the initializer and handle it in the specialization body. A `null` sentinel is appropriate only when the cached parameter permits it; do not declare `neverDefault = true` or `@NeverDefault` for such an initializer.

**Before**

```java
@Specialization(guards = "name.equals(cachedName)", limit = "3")
FieldInfo lookupCached(String name,
                @Cached("name") String cachedName,
                @Cached("requireField(cachedName)") FieldInfo field) {
    return field;
}

FieldInfo requireField(String name) {
    FieldInfo field = schema.find(name);
    if (field == null) {
        throw new IllegalArgumentException("Unknown field: " + name);
    }
    return field;
}
```

**After**

```java
@Specialization(guards = "name.equals(cachedName)", limit = "3")
FieldInfo lookupCached(String name,
                @Cached("name") String cachedName,
                @Cached(value = "schema.find(cachedName)",
                                neverDefault = false) FieldInfo field)
                throws UnknownIdentifierException {
    if (field == null) {
        throw UnknownIdentifierException.create(name);
    }
    return field;
}

@Specialization(replaces = "lookupCached")
FieldInfo lookupGeneric(String name) throws UnknownIdentifierException {
    FieldInfo field = schema.find(name);
    if (field == null) {
        throw UnknownIdentifierException.create(name);
    }
    return field;
}
```

**Further reading:** See [Cached](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/Cached.html) and [Cached.neverDefault](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/Cached.html#neverDefault()).

---

<a name="pass-the-actual-storage-node-to-inlined-helpers"></a>

### `TR-INLINING-TARGET` — Pass the actual storage node to inlined helpers

**When it applies:** A specialization uses an inlined node or profile, including exported library messages and specializations with generated per-instance storage.

**What goes wrong:** You pass Java `this` to an inlined helper without checking where the DSL placed that helper's state.

**Why it matters:** The node argument identifies storage for the inlined fields, not merely a convenient source location. The DSL can place fields in generated specialization data rather than the enclosing node.

**How to fix it:** In an inlined node, forward its dynamic `Node` execute parameter to its inlined helpers. In a cached node with generated specialization storage, bind the appropriate DSL node instead of relying on Java `this`.

**Before**

```java
abstract class LookupNode extends Node {
    abstract Object execute(Object key);

    @Specialization(guards = "key == cachedKey", limit = "3")
    Object lookup(Object key,
                    @Cached("key") Object cachedKey,
                    @Cached InlinedBranchProfile missing) {
        Object value = find(key);
        if (value == null) {
            missing.enter(this); // Not necessarily this profile's storage.
            throw unknownKey(key);
        }
        return value;
    }
}
```

**After**

```java
abstract class LookupNode extends Node {
    abstract Object execute(Object key);

    @Specialization(guards = "key == cachedKey", limit = "3")
    static Object lookup(Object key,
                    @Bind("this") Node node,
                    @Cached("key") Object cachedKey,
                    @Cached InlinedBranchProfile missing) {
        Object value = find(key);
        if (value == null) {
            missing.enter(node);
            throw unknownKey(key);
        }
        return value;
    }
}
```

**Further reading:** See [Passing Along Nodes Correctly](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/DSLNodeObjectInlining/#passing-along-nodes-correctly) and [GenerateInline](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/GenerateInline.html).

---

<a name="guard-cached-results-against-every-relevant-change"></a>

### `TR-CACHE-VALIDITY` — Guard cached results against every relevant change

**When it applies:** You retain a lookup result or derived metadata across executions, or associate versioned metadata with executable code.

**What goes wrong:** You treat `@Cached` as automatic mutation-aware memoization, or associate retained code with metadata from the wrong version.

**Why it matters:** The initializer runs at specialization instantiation, and subsequent executions reuse that value. A correct result for the first invocation can therefore become stale while the specialization still accepts the input.

**How to fix it:** Identify what makes the result valid: immutable inputs, an executed guard against the current state, or an invalidatable assumption covering all relevant mutations. Distinguish a cached execution helper from the values it returns: retaining the helper does not by itself retain each result or manage its resource lifetime. Here the cell publishes a new immutable entry whenever its value changes.

**Before**

```java
@Specialization(guards = "cell == cachedCell", limit = "3")
static Object read(Cell cell,
                @Cached("cell") Cell cachedCell,
                @Cached(value = "cachedCell.entry.value", neverDefault = false)
                Object cachedValue) {
    return cachedValue; // Stale after this same cell publishes a new Entry.
}
```

**After**

```java
@Specialization(guards = "entry == cachedEntry", limit = "3")
static Object readCached(Cell cell,
                @Bind("cell.entry") Entry entry,
                @Cached("entry") Entry cachedEntry,
                @Cached(value = "cachedEntry.value", neverDefault = false)
                Object cachedValue) {
    return cachedValue;
}

@Specialization(replaces = "readCached")
static Object readGeneric(Cell cell) {
    return cell.entry.value;
}
```

**Further reading:** See [Specialization.assumptions](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/Specialization.html#assumptions()) and [Assumption](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/Assumption.html).

---

<a name="treat-guard-idempotence-as-a-correctness-promise"></a>

### `TR-GUARD-IDEMPOTENCE` — Treat guard idempotence as a correctness promise

**When it applies:** DSL guards call helper methods, especially helpers reading current-context or thread-local state without explicit dynamic arguments.

**What goes wrong:** You add `@Idempotent` although the same explicit call can return different answers, or treat a stable answer as proof that hidden guard updates are safe.

**Why it matters:** The DSL can execute an idempotent guard only during specialization and omit it from the fast path. A false promise can reuse a specialization under a different context or changed state where its condition no longer holds.

**How to fix it:** Use `@NonIdempotent` for helpers whose answer can change for the same receiver and arguments, or redesign the guard to bind the relevant dynamic state explicitly. Use `@Idempotent` only with a stable proof for all supported context policies and lifecycle transitions.

**Before**

```java
@Idempotent // Wrong: another invocation may run in a different context.
boolean acceptsCurrentMode() {
    return CONTEXT.get(this).isFeatureEnabled();
}
```

**After**

```java
@NonIdempotent
boolean acceptsCurrentMode() {
    return CONTEXT.get(this).isFeatureEnabled();
}

@Specialization(guards = "acceptsCurrentMode()")
Object enabled(Object value) {
    return evaluateEnabled(value);
}

@Specialization(guards = "!acceptsCurrentMode()")
Object disabled(Object value) {
    return evaluateDisabled(value);
}
```

**Further reading:** See [Guard Expression Contract](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/Specialization.html#guards()) and [NonIdempotent](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/NonIdempotent.html).

---

<a name="audit-cache-retention-within-a-live-context"></a>

### `TR-CACHE-RETENTION` — Audit cache retention within a live context

**When it applies:** A long-lived AST, runtime cache, or suspended result slot can outlive guest objects, modules, temporary functions, or foreign classes within a context.

**What goes wrong:** You assume a single-context guard or moving the cache into the context solves every memory-lifetime problem.

**Why it matters:** Isolation and reachability are different properties. Objects that guest code no longer references can remain strongly reachable from reusable code or a context-owned map for the rest of the context's lifetime.

**How to fix it:** Cache smaller context-independent metadata, avoid unnecessary retention, or use a weak/bounded lifetime strategy appropriate to the operation. Do not weaken every cache indiscriminately.

**Before**

```java
// The key may be a small view into a much larger allocation.
longLivedKeys.add(key);
```

**After**

```java
TruffleString compact = materializeSubstring.execute(key, encoding);
TruffleString retained = asManaged.execute(compact, encoding, false);
longLivedKeys.add(retained);
```

**Further reading:** See [MaterializeSubstringNode](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.MaterializeSubstringNode.html) and [AsManagedNode](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.AsManagedNode.html).

---

<a name="choose-dsl-helper-layout-for-its-actual-usage"></a>

### `TR-DSL-FOOTPRINT` — Choose DSL helper layout for its actual usage

**When it applies:** DSL specializations allocate or share helpers, expose an inline API, change library exports, or add optimization metadata to common guest objects.

**What goes wrong:** You assume unused helper variants or inline state cost nothing, that sharing or laziness always reduces cost, or that reporting policy and receiver-specialized library acceptance can be ignored.

**Why it matters:** Inline state occupies storage in the caller or specialization data even when the helper is never executed. An ordinary cached helper can be initialized when its enclosing specialization activates, before its conditional use.

**How to fix it:** For a large helper behind a genuinely rare branch, consider a lazy cached wrapper or a properly adopted lazy child. Keep hot helpers direct when lazy dispatch would cost more than the footprint saved.

**Before**

```java
@Specialization
static int doValue(Object value, @Cached HeavyFallbackNode helper) {
    if (needsFallback(value)) {
        return helper.execute(value);
    }
    return fastResult(value);
}
```

**After**

```java
@GenerateInline(true)
@GenerateCached(false)
abstract static class LazyFallbackNode extends Node {
    abstract HeavyFallbackNode execute(Node storage);

    @Specialization
    static HeavyFallbackNode get(@Cached(inline = false) HeavyFallbackNode helper) {
        return helper;
    }
}

@Specialization
static int doValue(Object value, @Bind("this") Node storage,
                @Cached LazyFallbackNode lazy) {
    if (needsFallback(value)) {
        return lazy.execute(storage).execute(value);
    }
    return fastResult(value);
}
```

**Further reading:** See [DSL footprint and helper-call guidelines](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/DSLGuidelines/#avoid-unused-large-inline-nodes) and [Specialization-Time Cache Initialization](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/Cached.html).

---

<a name="preserve-results-across-specialization-transitions"></a>

### `TR-SPECIALIZATION-SEMANTICS` — Preserve results across specialization transitions

**When it applies:** DSL specializations distinguish overlapping representations or null-like sentinels, a generic specialization replaces a narrow fast path, or a typed operation can produce an unexpected result.

**What goes wrong:** An input changes meaning after another input activates a specialization, a broad Object specialization shadows a later narrow specialization, or you treat a typed-result failure as impossible without checking its contract.

**Why it matters:** Specialization is an implementation strategy, not permission to change the operation's semantics as the node warms up. The DSL tries applicable specializations in declaration order during specialization; it cannot infer that two implementations produce equivalent results.

**How to fix it:** Put narrow paths before broad ones, distinguish overlapping representations with guards when their handling differs, and ensure replacing paths preserve all required behavior. Use `replaces` only when the generic path handles the replaced path's domain correctly and abandoning that fast path is intentional.

**Before**

```java
@Specialization
static int doInt(int value) {
    if (value < 0) {
        throw negativeValueError();
    }
    return value;
}

@Specialization(replaces = "doInt")
static int doGeneric(Object value) {
    return convertOtherRepresentation(value); // Does not handle Integer correctly.
}
```

**After**

```java
@Specialization
static int doInt(int value) {
    return requireNonNegative(value);
}

@Specialization(replaces = "doInt")
static int doGeneric(Object value) {
    if (value instanceof Integer number) {
        return requireNonNegative(number);
    }
    return convertOtherRepresentation(value);
}

static int requireNonNegative(int value) {
    if (value < 0) {
        throw negativeValueError();
    }
    return value;
}
```

**Further reading:** See [Specialization and Caching Guidelines](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/DSLGuidelines/) and [Specialization.replaces](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/Specialization.html#replaces()).

---

<a name="keep-ordinary-helpers-separate-from-dsl-execute-entries"></a>

### `TR-DSL-EXECUTE-ENTRIES` — Keep ordinary helpers separate from DSL execute entries

**When it applies:** You add helper methods to a node whose dispatch is generated by the Truffle DSL.

**What goes wrong:** An overridable ordinary helper starts with `execute`, unintentionally making it another DSL execution entry point.

**Why it matters:** The DSL recognizes execute methods by their prefix and signature, not by your intended role for the body. A generated override can route that helper through specialization dispatch instead of running the helper body.

**How to fix it:** Use a non-execute name for ordinary helper work. Keep intended generated execute entry points distinct from `@Specialization` implementations, which should also use other names such as `doValue`.

**Before**

```java
abstract class IncrementNode extends Node {
    public abstract int execute(int value);

    @Specialization
    protected int doValue(int value) {
        return executeHelper(value); // Can dispatch back through doValue.
    }

    protected int executeHelper(int value) {
        return value + 1;
    }
}
```

**After**

```java
abstract class IncrementNode extends Node {
    public abstract int execute(int value);

    @Specialization
    protected int doValue(int value) {
        return incrementValue(value);
    }

    protected int incrementValue(int value) {
        return value + 1;
    }
}
```

**Further reading:** See [Specialization and Execute Method Contract](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/Specialization.html) and [DSL Node Layout](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/DSLGuidelines/#avoid-duplicated-specializations).

---

<a name="deoptimize-before-handing-off-interpreter-re-specialization"></a>

### `TR-SLOW-PATH-EXCEPTIONS` — Deoptimize before handing off interpreter re-specialization

**When it applies:** A nested fast-path helper aborts to an enclosing caller that must re-specialize interpreter state or install a generic execution path.

**What goes wrong:** You throw and catch an arbitrary private exception and assume that its name or rarity keeps the fallback out of compiled execution.

**Why it matters:** An ordinary private exception does not itself establish a deoptimizing slow-path boundary. Compiled throw/catch control flow can still reach the fallback, including writes to state on which compiled code depends.

**How to fix it:** Use a local `CompilerDirectives.transferToInterpreterAndInvalidate()` when the check can handle re-specialization itself. For a nested helper that must hand that work to a catching caller, use `SlowPathException` where the checked-exception interface permits it, or explicitly transfer before throwing the private handoff signal. The example accepts only `Integer` and `Long`.

**Before**

```java
final class IntegerValueNode extends Node {
    @CompilationFinal private boolean generic;

    long execute(Object value) {
        if (!generic) {
            try {
                return readInt(value);
            } catch (RewriteSignal signal) {
                generic = true; // No invalidation protocol.
            }
        }
        return ((Number) value).longValue();
    }

    private static long readInt(Object value) {
        if (value instanceof Integer integer) {
            return integer.longValue();
        }
        throw new RewriteSignal();
    }

    private static final class RewriteSignal extends RuntimeException {
    }
}
```

**After**

```java
final class IntegerValueNode extends Node {
    @CompilationFinal private boolean generic;

    long execute(Object value) {
        if (!generic) {
            try {
                return readInt(value);
            } catch (SlowPathException signal) {
                generic = true;
            }
        }
        return ((Number) value).longValue();
    }

    private static long readInt(Object value) throws SlowPathException {
        if (value instanceof Integer integer) {
            return integer.longValue();
        }
        throw new SlowPathException();
    }
}
```

**Further reading:** See [Deoptimizing Slow Paths](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/PartialEvaluation/#invalidation-and-re-specialization-using-slow-path-code) and [Explicit Invalidating Transfer](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/CompilerDirectives.html#transferToInterpreterAndInvalidate()).

---

## Compilation and partial evaluation

<a name="profile-important-data-dependent-branches-in-handwritten-operations"></a>

### `TR-PROFILES` — Profile important data-dependent branches in handwritten operations

**When it applies:** A handwritten operation contains a compilation-sensitive guest-data-dependent branch whose behavior is not already captured by specialization or another profile.

**What goes wrong:** You assume that the surrounding operation machinery also profiles every Java condition in its body.

**Why it matters:** Unprofiled handwritten branches can deny partial evaluation useful information about which paths execution has observed. A DSL specialization does not automatically describe every branch nested inside its implementation.

**How to fix it:** Use a suitable condition, counting-condition, branch, or loop profile for the behavior you need to communicate. Do not add profiles mechanically to constant conditions or already-profiled logic.

**Before**

```java
@Specialization
static Object select(Object value, @Bind Node node) {
    if (isSpecial(value)) {
        return specialCase(value);
    }
    return ordinaryCase(value);
}
```

**After**

```java
@Specialization
static Object select(Object value, @Bind Node node,
                @Cached InlinedConditionProfile special) {
    if (special.profile(node, isSpecial(value))) {
        return specialCase(value);
    }
    return ordinaryCase(value);
}
```

**Further reading:** See [Condition profiles](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/profiles/InlinedConditionProfile.html) and [Counted-Loop Condition Injection](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/profiles/LoopConditionProfile.html#inject(boolean)).

---

<a name="invalidate-compiled-assumptions-before-changing-compilation-final-state"></a>

### `TR-COMPILATION-FINAL` — Invalidate compiled assumptions before changing compilation-final state

**When it applies:** Compiled code can constant-fold a mutable field annotated with `@CompilationFinal`, including an array reference or its annotated contents, or a Static Object Model property registered with `storeAsFinal`.

**What goes wrong:** You replace or modify the folded state without invalidation, or assume that a write happening in the interpreter automatically makes existing compiled code observe it.

**Why it matters:** Compiled code can continue using the previous constant instead of reading the new field value. Changing an unrelated annotation or disabling an optimization elsewhere can hide the symptom without repairing the mutation protocol.

**How to fix it:** Invalidate before changing state on which compiled code can depend, or remove the annotation when the field should be read dynamically. For state owned by a nonconstant runtime object, do not invalidate shared code merely because each new context initializes its own field; use an appropriate dynamic slow path or eager initialization instead.

**Before**

```java
@CompilationFinal(dimensions = 0) private Object[] storage;

void grow(int newSize) {
    storage = Arrays.copyOf(storage, newSize);
}
```

**After**

```java
@CompilationFinal(dimensions = 0) private Object[] storage;

void grow(int newSize) {
    CompilerDirectives.transferToInterpreterAndInvalidate();
    storage = Arrays.copyOf(storage, newSize);
}
```

**Further reading:** See [CompilationFinal](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/CompilerDirectives.CompilationFinal.html) and [Handoff With Invalidation](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/CompilerDirectives.html#transferToInterpreterAndInvalidate()).

---

<a name="scope-loop-explosion-to-the-intended-loops"></a>

### `TR-LOOP-EXPLOSION` — Scope loop explosion to the intended loops

**When it applies:** Handwritten execution code uses `@ExplodeLoop` to expose a fixed computation or implement bytecode dispatch.

**What goes wrong:** You annotate a method without checking every loop originating in that method, use a bytecode-dispatch explosion strategy for unrelated helper loops, or explode an input-sized loop without a partial-evaluation-constant bound.

**Why it matters:** The annotation covers loops originating throughout the annotated method, not just the nearest loop, and does not propagate into helpers through ordinary partial-evaluation inlining. Explicit early inlining is different: it can expose a helper's control flow to the caller's loop explosion before partial evaluation.

**How to fix it:** Give fixed-size helper loops their own annotated method and choose the strategy for their control flow. When a small dispatch helper must participate in its caller's explosion, consider `@EarlyInline` (since 25.1), which requests early inlining of eligible direct calls; it is not a guarantee that every call can inline.

**Before**

```java
@ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
static int process(int[] input, int[] components) {
    int total = 0;
    for (int value : input) {
        total += value; // Runtime-sized, but also covered by the annotation.
    }
    for (int i = 0; i < 4; i++) {
        total += components[i];
    }
    return total;
}
```

**After**

```java
static int process(int[] input, int[] components) {
    int total = 0;
    for (int value : input) {
        total += value;
    }
    return total + sumComponents(components);
}

@ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
static int sumComponents(int[] components) {
    int total = 0;
    for (int i = 0; i < 4; i++) {
        total += components[i];
    }
    return total;
}
```

**Further reading:** See [Shaping Loops for Partial Evaluation](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/PartialEvaluation/#shaping-loops-for-partial-evaluation) and [ExplodeLoop](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/ExplodeLoop.html).

---

<a name="assert-required-partial-evaluation-constants-instead-of-ignoring-a-query"></a>

### `TR-PE-ASSERTIONS` — Assert required partial-evaluation constants instead of ignoring a query

**When it applies:** A compilation-sensitive execution path relies on metadata or an index folding during partial evaluation.

**What goes wrong:** You call `CompilerDirectives.isPartialEvaluationConstant(value)` and discard its boolean result, expecting a compiler assertion or a request to make the value constant, assert constancy that the operation only recommends, or assume later optimization satisfies a current PE-constant precondition.

**Why it matters:** The directive queries whether the compiler sees a constant; ignoring the result establishes no invariant. An assertion also cannot manufacture constancy when the data flow does not provide it.

**How to fix it:** Use `CompilerAsserts.partialEvaluationConstant` where successful compilation requires the value to fold. Make the owning metadata and access path suitable for folding first.

**Before**

```java
static Object readField(RecordValue value, int fieldKind, int fieldIndex) {
    CompilerDirectives.isPartialEvaluationConstant(fieldKind);
    return switch (fieldKind) {
        case INT_KIND -> value.readInt(fieldIndex);
        default -> value.readObject(fieldIndex);
    };
}
```

**After**

```java
// Callers supply kind/index from constant, immutable operation metadata.
static Object readField(RecordValue value, int fieldKind, int fieldIndex) {
    CompilerAsserts.partialEvaluationConstant(fieldKind);
    CompilerAsserts.partialEvaluationConstant(fieldIndex);
    return switch (fieldKind) {
        case INT_KIND -> value.readInt(fieldIndex);
        default -> value.readObject(fieldIndex);
    };
}
```

**Further reading:** See [CompilerAsserts](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/CompilerAsserts.html#partialEvaluationConstant(int)) and [isPartialEvaluationConstant](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/CompilerDirectives.html#isPartialEvaluationConstant(java.lang.Object)).

---

<a name="bound-invalidation-by-shared-code-state-not-by-each-new-object"></a>

### `TR-DEOPT-STABILITY` — Bound invalidation by shared-code state, not by each new object

**When it applies:** Shared compiled code lazily initializes contexts, guest objects, wrappers, or call targets, or repeatedly renews speculative metadata assumptions.

**What goes wrong:** You invalidate the compiled caller every time an individual object's lazy field is empty, assuming that initialization happens only once because it happens once per object.

**Why it matters:** One compiled path may encounter arbitrarily many fresh objects or contexts. A `@CompilationFinal` annotation does not make a field fold when its receiver is nonconstant, and per-object initialization may leave the compiled caller no more specialized than before.

**How to fix it:** Identify what changes in the compilation unit and why the invalidations will stop. Check the complete state transition that makes the recurring slow condition false, not merely an associated flag update.

**Before**

```java
@CompilationFinal Object resource;

Object getResource() {
    if (resource == null) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        initializeResource();
    }
    return resource;
}
```

**After**

```java
@CompilationFinal Object resource;

Object getResource() {
    if (resource == null) {
        if (CompilerDirectives.isPartialEvaluationConstant(this)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        initializeResource();
    }
    return resource;
}

@TruffleBoundary
void initializeResource() {
    resource = createOwnedResource();
}
```

**Further reading:** See [CyclicAssumption invalidation warning](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/utilities/CyclicAssumption.html) and [Compilation-Final Field of a Non-Constant Object](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/DeoptCyclePatterns/#compilation-final-field-of-a-non-constant-object).

---

<a name="keep-boundaries-around-the-work-that-must-remain-opaque"></a>

### `TR-BOUNDARY-SCOPE` — Keep boundaries around the work that must remain opaque

**When it applies:** Interpreter helpers call complex runtime work or combine opaque operations with wrapper construction, checks, or data flow that partial evaluation should still see.

**What goes wrong:** You put a boundary around the whole helper merely because one nested operation needs it, or apply a universal rule that exception allocation must always be behind a boundary.

**Why it matters:** A boundary prevents partial evaluation from seeing through the call. Moving wrapper allocation and surrounding data flow behind it can prevent cross-call escape analysis, while moving exception construction can change whether an exceptional branch simplifies to a guard.

**How to fix it:** Start with the narrow operation that needs to be opaque and leave useful compilable work visible. For exceptions, distinguish expensive formatting or excluded runtime work from the cheap construction/control-flow path; verify the resulting graph rather than prescribing one boundary position for all exception factories.

**Before**

```java
@TruffleBoundary
static NativeValue lookup(ResourceOwner owner, String name) {
    long address = lookupNative(owner.handle(), name);
    return new NativeValue(owner, address);
}
```

**After**

```java
static NativeValue lookup(ResourceOwner owner, String name) {
    long address = lookupAddress(owner.handle(), name);
    return new NativeValue(owner, address);
}

@TruffleBoundary
static long lookupAddress(long handle, String name) {
    return lookupNative(handle, name);
}
```

**Further reading:** See [TruffleBoundary](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/CompilerDirectives.TruffleBoundary.html) and [Interpreter and Runtime Separation](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/PartialEvaluation/#separating-interpreter-and-runtime-code).

---

<a name="preserve-interpreter-state-needed-after-deoptimization"></a>

### `TR-DEOPT-STATE` — Preserve interpreter state needed after deoptimization

**When it applies:** Interpreter bookkeeping differs by execution tier and guest operations can deoptimize before the method returns.

**What goes wrong:** You omit creating invocation state in compiled execution merely because the compiled fast path does not currently consume it.

**Why it matters:** Deoptimization resumes interpreter execution with the logical state represented by the compiled activation. It does not restart the method and recreate an object that the original tier-dependent branch skipped.

**How to fix it:** Preserve the logical state needed by every supported resume point, and gate only bookkeeping operations whose omission is semantically safe. An unconditional logical allocation may be virtualized and reconstructed on deoptimization, but do not assume that every allocation or arithmetic update is eliminated.

**Before**

```java
int[] counters = CompilerDirectives.hasNextTier() ? new int[1] : null;
executeGuestOperation(frame); // May deoptimize into this activation.
if (CompilerDirectives.hasNextTier()) {
    counters[0]++; // Can resume here with counters == null.
}
```

**After**

```java
int[] counters = new int[1]; // Part of the invocation's logical state.
executeGuestOperation(frame);
if (CompilerDirectives.hasNextTier()) {
    counters[0]++;
}
```

**Further reading:** See [Partial evaluation and frame reconstruction](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/PartialEvaluation/#working-with-virtualframes).

---

<a name="keep-required-interpreter-dispatch-resolvable-during-partial-evaluation"></a>

### `TR-PE-VISIBILITY` — Keep required interpreter dispatch resolvable during partial evaluation

**When it applies:** A hot interpreter path delegates through metadata, libraries, or conditional wrappers whose representation or control flow can change independently of the guest computation.

**What goes wrong:** You accept a refactor that leaves unresolved interpreter dispatch or duplicates complex expansion across dynamic wrapper branches, then treat a passing functional gate as proof that partial evaluation still removes the interpreter abstraction.

**Why it matters:** A receiver that is neither a PE constant nor known to have a resolvable concrete implementation can leave an interpreter call in the residual program. A dependency's memory-saving representation split can therefore change your compilation behavior without changing its functional API.

**How to fix it:** Inspect performance warnings and the relevant graph to identify the actual unresolved call. If you control representation creation, use a compatible concrete representation for this path or a language-owned representation whose operations remain visible.

**Before**

```java
final class MatchData {
    final Region region;

    MatchData(int groupCount) {
        region = Region.create(groupCount); // May choose several implementations.
    }
}

int begin(MatchData match, int group) {
    return match.region.getBegin(group); // Check whether PE can resolve this call.
}
```

**After**

```java
final class MatchData {
    final DenseRegion region;

    MatchData(int groupCount) {
        region = new DenseRegion(groupCount);
    }
}

int begin(MatchData match, int group) {
    return match.region.getBegin(group);
}
```

**Further reading:** See [Writing Interpreters for Partial Evaluation](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/PartialEvaluation/#writing-interpreters-for-partial-evaluation) and [Optimizing Truffle Interpreters](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/Optimizing/).

---

<a name="avoid-temporary-containers-solely-to-feed-a-child-operation"></a>

### `TR-RESULT-CARRIERS` — Avoid temporary containers solely to feed a child operation

**When it applies:** An AST or DSL helper packages operands and control flags into a temporary object that an immediate consumer unpacks.

**What goes wrong:** You allocate an array and a result wrapper solely to pass several existing values between execution nodes, assuming compiled escape analysis removes all cost.

**Why it matters:** Interpreter execution still performs that packaging, and compiled allocation elimination depends on the actual graph. A container shape used as a hidden direction flag also obscures the operation's data flow.

**How to fix it:** Consider making the consumer a child of the node that already has the values and passing them as explicit execute arguments. Preserve evaluation order, aliasing behavior, and the operation's direction decision.

**Before**

```java
record Operands(Object[] pointers) {
}

Object execute(Object source, Object destination, long count) {
    boolean backwards = mustCopyBackwards(source, destination, count);
    Object[] pointers = backwards
                    ? new Object[]{source, destination, null}
                    : new Object[]{source, destination};
    return copy.execute(new Operands(pointers), count);
}
```

**After**

```java
abstract class CopyOperationNode extends Node {
    abstract Object execute(Object source, Object destination,
                    long count, boolean backwards);
}

final class CopyHeadNode extends Node {
    @Child private CopyOperationNode copy;

    CopyHeadNode(CopyOperationNode copy) {
        this.copy = copy;
    }

    Object execute(Object source, Object destination, long count) {
        boolean backwards = mustCopyBackwards(source, destination, count);
        return copy.execute(source, destination, count, backwards);
    }
}
```

**Further reading:** See [NodeChild.executeWith](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/NodeChild.html#executeWith()) and [Partial Evaluation](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/PartialEvaluation/).

---

<a name="avoid-replicating-one-body-across-equivalent-alternatives"></a>

### `TR-AST-DUPLICATION` — Avoid replicating one body across equivalent alternatives

**When it applies:** A translator emits the same executable body for several alternative conditions, or a continuation/dispatch design expands one body at many entry sites.

**What goes wrong:** You clone the body for each condition or route every alternative through a separate call to the same large PE-visible helper, assuming source-level reuse prevents compiled duplication.

**Why it matters:** Each cloned tree carries its own nodes and specialization state. Partial evaluation can also explore the same Java helper separately at each call site.

**How to fix it:** Where the language semantics permit it, combine the ordered conditions and execute a single body after the first successful condition. Preserve short-circuiting, evaluation order, exceptions, source tags, and the number of guest-visible effects.

**Before**

```java
ExpressionNode result = translateElse(elseSyntax);
for (int i = conditions.length - 1; i >= 0; i--) {
    result = new ConditionalNode(translateCondition(conditions[i]),
                    translateBody(bodySyntax), result);
}
return result;
```

**After**

```java
final class AnyConditionNode extends ConditionNode {
    @Children private ConditionNode[] conditions;

    AnyConditionNode(ConditionNode[] conditions) {
        this.conditions = conditions;
    }

    @Override
    @ExplodeLoop
    boolean executeBoolean(VirtualFrame frame) {
        for (ConditionNode condition : conditions) {
            if (condition.executeBoolean(frame)) {
                return true;
            }
        }
        return false;
    }
}

ExpressionNode translateAlternatives(Syntax[] alternatives,
                Syntax bodySyntax, Syntax elseSyntax) {
    ConditionNode[] tests = new ConditionNode[alternatives.length];
    for (int i = 0; i < alternatives.length; i++) {
        tests[i] = translateCondition(alternatives[i]);
    }
    return new ConditionalNode(new AnyConditionNode(tests),
                    translateBody(bodySyntax), translateElse(elseSyntax));
}
```

**Further reading:** See [Duplication Seen by Partial Evaluation](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/DSLGuidelines/#avoid-duplicated-specializations) and [AST Child Ownership](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html#isAdoptable()).

---

<a name="keep-compiler-preparation-repeatable-and-context-independent"></a>

### `TR-COMPILATION-PREPARATION` — Keep compiler preparation repeatable and context-independent

**When it applies:** You override `RootNode.prepareForCompilation` to prepare code or cache analysis for compilation.

**What goes wrong:** You treat the callback as a once-only guest execution phase, read an entered context, or acquire execution-thread locks while preparing the root.

**Why it matters:** Preparation can repeat for recompilation or potential inlinees and can run concurrently without synchronization supplied by the runtime. Exploring an inlinee does not guarantee it will ultimately be inlined.

**How to fix it:** Make preparation thread-safe, repeatable, and independent of an entered context. Cache reusable work under a publication protocol that fits the data and release every synchronization primitive before returning, including exceptional paths.

**Before**

```java
private boolean prepared;
private Analysis analysis;

@Override
protected boolean prepareForCompilation(boolean rootCompilation,
                int compilationTier, boolean lastTier) {
    if (!prepared) { // Concurrent callbacks can both enter.
        synchronized (executionLock) {
            analysis = analyzeContext(currentContext());
            prepared = true;
        }
    }
    return true;
}
```

**After**

```java
private final ImmutableCode code; // Assigned during root construction.
private final AtomicReference<Analysis> preparedAnalysis = new AtomicReference<>();

@Override
protected boolean prepareForCompilation(boolean rootCompilation,
                int compilationTier, boolean lastTier) {
    if (preparedAnalysis.get() == null) {
        Analysis computed = analyzeImmutableCode(code);
        preparedAnalysis.compareAndSet(null, computed);
    }
    return true;
}

@Override
public Object execute(VirtualFrame frame) {
    return executeNormally(frame, code); // Does not require preparation.
}
```

**Further reading:** See [Compilation Preparation Contract](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html#prepareForCompilation(boolean,int,boolean)).

---

<a name="fix-allocation-placement-only-for-a-required-failure-boundary"></a>

### `TR-ALLOCATION-PLACEMENT` — Fix allocation placement only for a required failure boundary

**When it applies:** A supported guest operation requires an actual allocation failure to be attributed to a particular allocation site and handled there during compiled execution.

**What goes wrong:** You treat `CompilerDirectives.ensureAllocatedHere` as a free placement hint that still permits allocation elimination, or apply it to an arbitrary object obtained elsewhere.

**Why it matters:** The current directive both prevents movement of the allocation and prevents its elimination. It deliberately gives up optimization opportunities; it is not a generic allocation-speed or memory-reduction hint.

**How to fix it:** First establish the operation's required allocation-failure boundary and whether precise placement is necessary. Where it is, pass the fresh allocation directly to `ensureAllocatedHere` at that site, within the intended handler.

**Before**

```java
byte[] tryAllocateStorage(int length) {
    byte[] storage = new byte[length]; // Outside the intended handler.
    try {
        return CompilerDirectives.ensureAllocatedHere(storage);
    } catch (OutOfMemoryError failure) {
        return null;
    }
}
```

**After**

```java
byte[] tryAllocateStorage(int length) {
    try {
        return CompilerDirectives.ensureAllocatedHere(new byte[length]);
    } catch (OutOfMemoryError failure) {
        return null;
    }
}
```

**Further reading:** See [Precise Allocation Placement Contract](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/CompilerDirectives.html#ensureAllocatedHere(T)) and [Allocation Elision and Virtual Frames](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/PartialEvaluation/#working-with-virtualframes).

---

## Interop and data representation

<a name="convert-foreign-values-through-their-interop-protocol"></a>

### `TR-INTEROP-CONVERSION` — Convert foreign values through their interop protocol

**When it applies:** An operation accepts foreign values through a generic interoperability boundary or transfers them into a language-owned representation.

**What goes wrong:** You cast a foreign string to `String`, a foreign numeric element to `Byte`, or infer its supported interop type solely from its Java representation.

**Why it matters:** A value can implement the string or number protocol without using that particular host class. A Java cast rejects valid representations and can bypass the required range or precision check.

**How to fix it:** Use the matching `InteropLibrary` conversion at the boundary and translate an unsupported conversion into the operation's specified guest error. For a lossless byte requirement, use `asByte`; for a string requirement, use `asString` or the appropriate string representation API.

**Before**

```java
String requireName(Object value) {
    return (String) value;
}

byte requireByte(Object value) {
    return (Byte) value;
}
```

**After**

```java
final class ForeignArgumentsNode extends Node {
    @Child private InteropLibrary interop =
                    InteropLibrary.getFactory().createDispatched(3);

    String requireName(Object value) throws UnsupportedMessageException {
        return interop.asString(value);
    }

    byte requireByte(Object value) throws UnsupportedMessageException {
        return interop.asByte(value); // Requires a losslessly representable number.
    }
}
```

**Further reading:** See [Numeric Category Versus Representation](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html#isNumber(java.lang.Object)) and [Lossless BigInteger Conversion](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html#asBigInteger(java.lang.Object)).

---

<a name="keep-capability-predicates-and-operations-consistent"></a>

### `TR-INTEROP-CONSISTENCY` — Keep capability predicates and operations consistent

**When it applies:** You export interop or debugger node messages, or consume a foreign object's capabilities.

**What goes wrong:** A predicate advertises an operation whose implementation returns an invalid sentinel, silently does nothing, or violates its declared result type; alternatively, a caller assumes a cached library makes every capability constant.

**Why it matters:** Consumers rely on each message's declared predicate, result and failure contract. A missing source location is not a successful `getSourceLocation()` returning Java null; an exception message must be an interop string when present.

**How to fix it:** Implement each predicate/operation pair according to its specific Javadoc, including its documented unsupported-operation and unknown-identifier exceptions. Do not apply one blanket exception rule to every message.

**Before**

```java
@ExportMessage
boolean hasSourceLocation() {
    return sourceSection != null;
}

@ExportMessage
SourceSection getSourceLocation() {
    return sourceSection; // Null violates the operation's contract.
}
```

**After**

```java
@ExportMessage
boolean hasSourceLocation() {
    return sourceSection != null;
}

@ExportMessage
SourceSection getSourceLocation() throws UnsupportedMessageException {
    if (sourceSection == null) {
        throw UnsupportedMessageException.create();
    }
    return sourceSection;
}
```

**Further reading:** See [Source-location capability](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html#hasSourceLocation(java.lang.Object)) and [Source-location access](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html#getSourceLocation(java.lang.Object)).

---

<a name="use-interop-identity-for-foreign-objects"></a>

### `TR-FOREIGN-IDENTITY` — Use interop identity for foreign objects

**When it applies:** You compare or export interop object identity, or key a context-owned cache by a foreign object's identity.

**What goes wrong:** You equate foreign identity with host-wrapper identity, or return a definitive negative result for an exported identity relationship your implementation does not specify.

**Why it matters:** Distinct host wrappers can represent the same foreign object. Interop identity is negotiated by the values' implementations; it is not guaranteed to match their wrapper identity.

**How to fix it:** Use `InteropLibrary.isIdentical` for foreign objects with identity, and use the matching `identityHashCode` if a hash-based data structure needs it. Equal hashes select candidates; they do not establish identity because distinct foreign objects can collide.

**Before**

```java
boolean sameForeignObject(Object left, Object right) {
    return left == right; // Compares host wrappers, not the foreign contract.
}
```

**After**

```java
final class ForeignIdentityNode extends Node {
    @Child private InteropLibrary leftInterop =
                    InteropLibrary.getFactory().createDispatched(3);
    @Child private InteropLibrary rightInterop =
                    InteropLibrary.getFactory().createDispatched(3);

    boolean sameForeignObject(Object left, Object right) {
        return leftInterop.isIdentical(left, right, rightInterop);
    }

    int hashForeignObject(Object value) throws UnsupportedMessageException {
        return leftInterop.identityHashCode(value);
    }
}
```

**Further reading:** See [InteropLibrary.isIdentical](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html#isIdentical(java.lang.Object,java.lang.Object,com.oracle.truffle.api.interop.InteropLibrary)) and [identityHashCode](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html#identityHashCode(java.lang.Object)).

---

<a name="use-the-interop-protocol-inside-guest-execution"></a>

### `TR-SPI-INTEROP` — Use the interop protocol inside guest execution

**When it applies:** Language execution nodes operate on foreign or host values, including partial-evaluation tests of those nodes.

**What goes wrong:** You implement a guest execution operation through the embedding-facing `org.graalvm.polyglot.Value` API and expect it to optimize like a direct interop message.

**Why it matters:** The embedding API is a host entry boundary, not the language implementation's internal interop dispatch API. A test routed through that boundary can miss the compilable operation it was intended to exercise.

**How to fix it:** Use `InteropLibrary` on interop values inside language execution nodes. Use `Env.asGuestValue` at the appropriate setup boundary for an already allocated host object, not a `Value` wrapper as the node's internal receiver.

**Before**

```java
Object executeForeign(Object receiver) {
    return Context.getCurrent().asValue(receiver).hasBufferElements();
}
```

**After**

```java
@Child private InteropLibrary interop =
                InteropLibrary.getFactory().createDispatched(3);

boolean executeForeign(Object guestReceiver) {
    return interop.hasBufferElements(guestReceiver);
}

// Setup, outside the compiled execution root:
Object guestReceiver = env.asGuestValue(hostBuffer);
```

**Further reading:** See [Interop protocol and cached dispatch](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/InteropMigration/) and [InteropLibrary](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html).

---

<a name="bound-temporary-storage-for-guest-requested-transfers"></a>

### `TR-TRANSFER-BUFFERS` — Bound temporary storage for guest-requested transfers

**When it applies:** A language runtime copies or decodes guest or foreign data through temporary host arrays, native buffers, or intermediate guest views.

**What goes wrong:** A guest-controlled maximum length directly sizes a temporary buffer, a valid long-length request is rejected merely because one temporary array takes an int length, or decoding constructs guest views that are copied and discarded before publication.

**Why it matters:** An otherwise valid operation can allocate an enormous temporary buffer or fail before transferring anything. Changing to a fixed-size buffer without preserving the operation's partial-versus-full transfer contract also changes guest-visible behavior.

**How to fix it:** Bound temporary storage independently of the total transfer size. If the operation allows a partial transfer, process a bounded amount and return the amount actually transferred. The example assumes that the validated range remains stable during the copy.

**Before**

```java
@TruffleBoundary
static void copy(InteropLibrary buffers, Object source, long offset,
                long length, OutputStream sink)
                throws IOException, UnsupportedMessageException,
                InvalidBufferOffsetException {
    byte[] scratch = new byte[Math.toIntExact(length)];
    buffers.readBuffer(source, offset, scratch, 0, scratch.length);
    sink.write(scratch);
}
```

**After**

```java
@TruffleBoundary
static void copy(Node location, InteropLibrary buffers, Object source,
                long offset, long length, OutputStream sink)
                throws IOException, UnsupportedMessageException,
                InvalidBufferOffsetException {
    byte[] scratch = new byte[(int) Math.min(length, 64 * 1024L)];
    long done = 0;
    while (done < length) {
        TruffleSafepoint.poll(location);
        int chunk = (int) Math.min(length - done, scratch.length);
        buffers.readBuffer(source, offset + done, scratch, 0, chunk);
        sink.write(scratch, 0, chunk);
        done += chunk;
    }
}
```

**Further reading:** See [InteropLibrary.readBuffer](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html#readBuffer(java.lang.Object,long,byte%5B%5D,int,int)) and [Safepoint integration](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/Safepoint/).

---

<a name="distinguish-character-ranges-from-byte-based-ranges"></a>

### `TR-STRING-RANGE` — Distinguish character ranges from byte-based ranges

**When it applies:** TruffleString code classifies characters, reports their positions, or exposes encoded storage to a native ABI, especially after changing internal encoding or compaction.

**What goes wrong:** You use GetByteCodeRangeNode as an encoding-independent ASCII-content test, interpret an encoded byte index as a code-point position, or assume native storage fixes the ABI's element width.

**Why it matters:** Its ASCII result is restricted to byte-based encodings. A UTF-16 or UTF-32 string containing only ASCII characters does not receive that result.

**How to fix it:** Choose the query for the property the fast path actually requires. Use GetCodeRangeNode for precise character-range classification and retain GetByteCodeRangeNode when its byte-based classification is intentional.

**Before**

```java
@Child private TruffleString.GetByteCodeRangeNode ranges =
                TruffleString.GetByteCodeRangeNode.create();

boolean containsOnlyAscii(TruffleString text) {
    return ranges.execute(text, TruffleString.Encoding.UTF_32) ==
                    TruffleString.CodeRange.ASCII;
}
```

**After**

```java
@Child private TruffleString.GetCodeRangeNode ranges =
                TruffleString.GetCodeRangeNode.create();

boolean containsOnlyAscii(TruffleString text) {
    return ranges.execute(text, TruffleString.Encoding.UTF_32) ==
                    TruffleString.CodeRange.ASCII;
}
```

**Further reading:** See [String Storage and Encodings](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/TruffleStrings/#encodings) and [Conservative Code Range](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.GetCodeRangeImpreciseNode.html#execute(com.oracle.truffle.api.strings.AbstractTruffleString,com.oracle.truffle.api.strings.TruffleString.Encoding)).

---

<a name="enforce-guest-string-limits-before-allocation-and-growth"></a>

### `TR-STRING-LIMITS` — Enforce guest string limits before allocation and growth

**When it applies:** An escaping, formatting, or concatenation operation can expand a guest string subject to a language-defined limit.

**What goes wrong:** You preallocate a multiple of input length and append unchecked, relying on an eventual host OutOfMemoryError to enforce the guest limit.

**Why it matters:** Output can grow beyond its initial estimate, and unchecked capacity arithmetic can overflow before allocation. A framework builder does not know a language's configured string-length policy.

**How to fix it:** Cap initial capacity, perform overflow-safe growth checks before each append, and use the same units as the guest limit. Do not assume a fixed expansion ratio works for every input or operation.

**Before**

```java
TruffleStringBuilderUTF8 out = TruffleStringBuilder.createUTF8(
                2 * input.byteLength(TruffleString.Encoding.UTF_8));
for (TruffleString piece : escapedPieces(input)) {
    append.execute(out, piece);
}
return finish.execute(out);
```

**After**

```java
int inputBytes = input.byteLength(TruffleString.Encoding.UTF_8);
int initialCapacity = Math.min(Math.min(inputBytes, guestByteLimit), 4096);
TruffleStringBuilderUTF8 out = TruffleStringBuilder.createUTF8(initialCapacity);
for (TruffleString piece : escapedPieces(input)) {
    long nextBytes = (long) out.byteLength() +
                    piece.byteLength(TruffleString.Encoding.UTF_8);
    if (nextBytes > guestByteLimit) {
        throw stringLimitError();
    }
    append.execute(out, piece);
}
return finish.execute(out);
```

**Further reading:** See [TruffleString creation and builders](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/TruffleStrings/#instantiation) and [TruffleStringBuilder](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleStringBuilder.html).

---

<a name="report-specified-failures-through-the-guest-error-protocol"></a>

### `TR-EXPECTED-GUEST-ERRORS` — Report specified failures through the guest error protocol

**When it applies:** Guest-controlled input can violate an operation's documented precondition, or a lifecycle hook can fail before ordinary guest execution begins.

**What goes wrong:** You report an expected guest failure using an internal host assertion or an unclassified Java implementation exception, or hide an impossible internal state behind an ordinary guest error.

**Why it matters:** Misclassification can prevent guest code from handling a specified failure or disguise an implementation defect as normal guest behavior. A condition normally prevented by a guest standard library is not necessarily an impossible language-runtime state.

**How to fix it:** Identify which invalid inputs belong to the guest operation's supported error domain and produce its specified guest exception or error result. Use the language's existing error construction protocol; `AbstractTruffleException` is the public base for guest exceptions, including supported parse errors.

**Before**

```java
Object performOperation(int handle, Node location) {
    Resource resource = resources.get(handle);
    if (!(resource instanceof WritableResource writable)) {
        throw CompilerDirectives.shouldNotReachHere("Unexpected resource");
    }
    try {
        return writable.perform();
    } catch (IOException failure) {
        throw guestIOError(failure.getMessage(), location);
    }
}
```

**After**

```java
Object performOperation(int handle, Node location) {
    Resource resource = resources.get(handle);
    if (!(resource instanceof WritableResource writable)) {
        throw guestIOError("Handle is not a writable resource", location);
    }
    try {
        return writable.perform();
    } catch (IOException failure) {
        throw guestIOError(failure.getMessage(), location);
    }
}

static GuestIOError guestIOError(String message, Node location) {
    return new GuestIOError(message, location);
}

static final class GuestIOError extends AbstractTruffleException {
    GuestIOError(String message, Node location) {
        super(message, location);
    }
}
```

**Further reading:** See [AbstractTruffleException](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/exception/AbstractTruffleException.html) and [Interop Failure Translation](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/InteropMigration/#fast-path-sending-interop-messages).

---

<a name="use-byte-based-view-ranges-across-buffer-representations"></a>

### `TR-BUFFER-RANGES` — Use byte-based view ranges across buffer representations

**When it applies:** A guest operation reads or writes a typed view over heap, direct, or foreign buffer storage.

**What goes wrong:** You confuse element and byte counts, lose an oversized length while narrowing, operate outside the live view, or assume every buffer exposes a Java array.

**Why it matters:** Multi-byte elements occupy more than one byte, and a view can cover only a subrange of its backing storage. A direct buffer need not have an accessible array, while a foreign buffer can require interop messages.

**How to fix it:** Identify the operation's units explicitly and derive a checked byte offset and byte length from the actual view. Use the storage representation's supported operations, or use the interop buffer protocol for values that support it.

**Before**

```java
int length = view.elementCount(); // Incorrect for multi-byte elements.
byte[] storage = view.byteBuffer().array(); // Not valid for every buffer.
System.arraycopy(bytes, 0, storage, 0, length); // Ignores the view offset.
```

**After**

```java
void writeView(Object storage, long byteOffset, long elementCount,
                int bytesPerElement, byte[] bytes, InteropLibrary buffers)
                throws UnsupportedMessageException, InvalidBufferOffsetException {
    if (elementCount < 0 || bytesPerElement <= 0) {
        throw guestRangeError();
    }
    long byteLength;
    try {
        byteLength = Math.multiplyExact(elementCount, (long) bytesPerElement);
    } catch (ArithmeticException overflow) {
        throw guestRangeError();
    }
    if (byteLength != bytes.length) {
        throw guestRangeError();
    }
    if (!buffers.hasBufferElements(storage) || !buffers.isBufferWritable(storage)) {
        throw guestBufferError();
    }
    long capacity = buffers.getBufferSize(storage);
    if (byteOffset < 0 || byteOffset > capacity || byteLength > capacity - byteOffset) {
        throw guestRangeError();
    }
    for (int i = 0; i < bytes.length; i++) {
        buffers.writeBufferByte(storage, byteOffset + i, bytes[i]);
    }
}
```

**Further reading:** See [Bulk Buffer Read](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html#readBuffer(java.lang.Object,long,byte[],int,int)) and [Byte Offset and Byte Order](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html#readBufferInt(java.lang.Object,java.nio.ByteOrder,long)).

---

<a name="avoid-a-host-string-round-trip-for-encoded-guest-data"></a>

### `TR-STRING-CONVERSION` — Avoid a host string round trip for encoded guest data

**When it applies:** Internal symbols or byte sequences already contain the required guest text, an interop operation returns a string for an encoding-specific consumer, or Java text crosses a guest string boundary.

**What goes wrong:** You decode an internal representation to `java.lang.String` solely to convert it back, encode Java text solely to reconstruct a guest string through bytes, or assume an interop string already has the consumer's encoding.

**Why it matters:** The intermediate representation adds conversion work and can add allocations or copies without being the required result. It can also change encoding or malformed-input behavior if its decoder does not match the original representation.

**How to fix it:** Keep the internal symbol or byte representation until the guest-facing conversion point. Use a supported direct conversion with the actual encoding, range, and ownership policy. The caller guarantees that the selected byte range contains well-formed UTF-8.

**Before**

```java
@Specialization
static TruffleString convert(byte[] bytes, int byteOffset, int byteLength,
                @Cached TruffleString.FromJavaStringNode fromJava) {
    String temporary = new String(bytes, byteOffset, byteLength,
                    StandardCharsets.UTF_8);
    return fromJava.execute(temporary, TruffleString.Encoding.UTF_8);
}
```

**After**

```java
@Specialization
static TruffleString convert(byte[] bytes, int byteOffset, int byteLength,
                @Cached TruffleString.FromByteArrayNode fromBytes) {
    return fromBytes.execute(bytes, byteOffset, byteLength,
                    TruffleString.Encoding.UTF_8, true);
}
```

**Further reading:** See [Truffle String Instantiation](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/TruffleStrings/#instantiation) and [Direct Byte Conversion](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/strings/TruffleString.FromByteArrayNode.html#execute(byte[],int,int,com.oracle.truffle.api.strings.TruffleString.Encoding,boolean)).

---

<a name="preserve-foreign-sizes-and-centralize-trait-selection"></a>

### `TR-INTEROP-ARRAY-SIZE` — Preserve foreign sizes and centralize trait selection

**When it applies:** A runtime checks a range in a foreign array, exports Java-indexed guest storage through interop, or exposes a guest array view over a foreign object that may also support buffer access.

**What goes wrong:** You narrow a foreign size or interop index to `int` before checking bounds, reuse a guest-length accessor that caps the size, or independently choose array-versus-buffer behavior at each caller.

**Why it matters:** `InteropLibrary.getArraySize` returns a `long`; a valid foreign array can exceed the guest array-length representation. Narrowing or capping first changes which ranges are accepted.

**How to fix it:** Keep the underlying size and index arithmetic in `long` for validation. Apply a guest-specific cap or checked conversion only at an API boundary that requires that representation.

**Before**

```java
int size = (int) arrays.getArraySize(receiver);
if (start + count > size) {
    throw invalidGuestRange();
}
```

**After**

```java
static void checkArrayRange(InteropLibrary arrays, Object receiver,
                int start, int count) throws UnsupportedMessageException {
    long size = arrays.getArraySize(receiver);
    if (start < 0 || count < 0 || (long) start + count > size) {
        throw invalidGuestRange();
    }
}
```

**Further reading:** See [Foreign Array Size](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html#getArraySize(java.lang.Object)) and [Foreign Buffer Size in Bytes](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html#getBufferSize(java.lang.Object)).

---

<a name="separate-unaligned-immutable-reads-from-concurrent-memory-access"></a>

### `TR-BYTE-ARRAY-ACCESS` — Separate unaligned immutable reads from concurrent memory access

**When it applies:** An interpreter decodes multibyte values from byte arrays, especially immutable bytecode or metadata whose reads should simplify during partial evaluation.

**What goes wrong:** You assume a normal `ByteArraySupport.getLong` read must fold because its logical byte offset is zero, or switch to `getLongUnaligned` on shared mutable memory solely to encourage folding.

**Why it matters:** Logical offsets are relative to the first array element, not the start of the physical object. The element base and its alignment can vary with runtime and object layout.

**How to fix it:** Choose the byte order, range, mutability, and synchronization contract before choosing the read operation. For immutable storage, consider `getIntUnaligned` or `getLongUnaligned`, available since 24.2, when normal unaligned reads prevent useful folding.

**Before**

```java
final class ReadHeaderBeforeNode extends Node {
    private static final ByteArraySupport LE = ByteArraySupport.littleEndian();
    @CompilationFinal(dimensions = 1) private final byte[] image;

    ReadHeaderBeforeNode(byte[] ownedImmutableImage) {
        this.image = ownedImmutableImage;
    }

    long execute() {
        // A one-byte tag precedes this eight-byte little-endian field.
        return LE.getLong(image, 1);
    }
}
```

**After**

```java
final class ReadHeaderNode extends Node {
    private static final ByteArraySupport LE = ByteArraySupport.littleEndian();
    @CompilationFinal(dimensions = 1) private final byte[] image;

    ReadHeaderNode(byte[] ownedImmutableImage) {
        this.image = ownedImmutableImage;
    }

    long execute() {
        return LE.getLongUnaligned(image, 1);
    }
}
```

**Further reading:** See [ByteArraySupport Alignment and Thread Safety](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/memory/ByteArraySupport.html) and [Explicit Unaligned Long Read](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/memory/ByteArraySupport.html#getLongUnaligned(byte[],int)).

---

## Bytecode execution and the Bytecode DSL

<a name="use-built-in-local-operations-for-ordinary-local-accesses"></a>

### `TR-LOCAL-FASTPATH` — Use built-in local operations for ordinary local accesses

**When it applies:** Bytecode DSL interpreter operations read or write locals during ordinary execution.

**What goes wrong:** You use frame-inspection APIs, internal frame indices, or an unnecessary accessor where a built-in local operation can express the same behavior.

**Why it matters:** Built-in local operations provide the preferred efficient path and participate in quickening and boxing elimination. Inspection APIs introduce indirection intended for different use cases.

**How to fix it:** Prefer `LoadLocal`, `StoreLocal`, and the applicable built-in clearing operation. Pass a loaded value as a dynamic operand when a custom operation only consumes it.

**Before**

```java
@Operation
@ConstantOperand(type = LocalAccessor.class)
public static final class ProcessLocal {
    @Specialization
    public static Object perform(VirtualFrame frame, LocalAccessor local,
                    @Bind BytecodeNode bytecode) {
        return process(local.getObject(bytecode, frame));
    }
}

b.emitProcessLocal(local);
```

**After**

```java
@Operation
public static final class ProcessValue {
    @Specialization
    public static Object perform(Object value) {
        return process(value);
    }
}

b.beginProcessValue();
    b.emitLoadLocal(local);
b.endProcessValue();
```

**Further reading:** See [Bytecode locals](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/BytecodeLocal.html) and [LocalAccessor](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/LocalAccessor.html).

---

<a name="resolve-captured-execution-frames-through-bytecode-aware-apis"></a>

### `TR-FRAME-RESOLUTION` — Resolve captured execution frames through bytecode-aware apis

**When it applies:** You configure or inspect captured Bytecode DSL frames, including frames of resumed continuations or other interpreters, or arrange inspection of another thread's live guest stack.

**What goes wrong:** You assume the frame returned directly by a stack-trace or frame-instance API contains your interpreter's guest locals, manually infer continuation-wrapper layouts, or rely on a capture override that generated code replaces.

**Why it matters:** The captured physical frame is not always the actual frame used for guest execution. A continuation can introduce a wrapper, and frame resolution also needs the correct bytecode location.

**How to fix it:** Use `BytecodeFrame.get` for captured frame data and the applicable generated bytecode descriptor for root resolution. Do not duplicate assumptions about internal continuation argument layouts.

**Before**

```java
Frame frame = element.getFrame();
if (frame != null) {
    Object value = bytecode.getLocalValue(
                    element.getBytecodeIndex(), frame, localOffset);
    inspect(value);
}
```

**After**

```java
BytecodeFrame frame = BytecodeFrame.get(element);
if (frame != null) {
    Object value = frame.getLocalValue(localOffset);
    inspect(value);
}
```

**Further reading:** See [Typed Descriptor Root Resolution](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/BytecodeDescriptor.html#cast(com.oracle.truffle.api.CallTarget)) and [BytecodeFrame](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/BytecodeFrame.html).

---

<a name="copy-captured-frame-state-before-resuming-when-it-must-remain-stable"></a>

### `TR-FRAME-LIFETIME` — Copy captured frame state before resuming when it must remain stable

**When it applies:** You walk frames, retain captured state, or expose tooling scopes across resumed execution, including block-scoped Bytecode DSL locals.

**What goes wrong:** You persist a borrowed frame, interpret later non-copied frame contents using an old capture location, or use a suspension-bound tooling scope after execution resumes.

**Why it matters:** Materialization is not a snapshot. Execution can change values, end lexical scopes, and reuse frame slots for different locals.

**How to fix it:** For a captured Bytecode DSL frame, capture a copy or call `BytecodeFrame.copy()` before execution continues when you need a stable snapshot. A copy preserves frame-slot state; it does not deep-copy the guest objects stored in those slots or observe later writes.

**Before**

```java
BytecodeFrame captured = captureCurrentBytecodeFrame();
resumeGuestExecution();
inspect(captured.getLocalValue(localOffset));
```

**After**

```java
BytecodeFrame snapshot = captureCurrentBytecodeFrame().copy();
resumeGuestExecution();
inspect(snapshot.getLocalValue(localOffset));
```

**Further reading:** See [Captured bytecode frames](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/BytecodeFrame.html) and [BytecodeFrame.copy](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/BytecodeFrame.html#copy()).

---

<a name="preserve-primitive-paths-across-custom-operation-boundaries"></a>

### `TR-BOXING` — Preserve primitive paths across custom operation boundaries

**When it applies:** A typed execution path should preserve primitive results, including Bytecode DSL custom operations and iterator helpers.

**What goes wrong:** You expose only `Object` specializations, hide a specialized node behind object-only forwarding, or introduce an object sentinel that disrupts an otherwise primitive result path.

**Why it matters:** Boxing elimination requires producing and consuming operations to agree on an unboxed representation. An object-only boundary can force values through a boxed representation even when the underlying work supports primitives.

**How to fix it:** Configure `boxingEliminationTypes` with primitive class literals such as `int.class`, not boxed classes such as `Integer.class`. Treat primitive paths as speculative when an operation can respecialize: a later execution may produce an object, so handwritten dispatch around it needs the valid dynamic type/fallback path.

**Before**

```java
@Operation
public static final class Discard {
    @Specialization
    public static void doObject(Object value) {
    }
}
```

**After**

```java
@Operation
public static final class Discard {
    @Specialization
    public static void doInt(int value) {
    }

    @Specialization
    public static void doDouble(double value) {
    }

    @Specialization
    public static void doObject(Object value) {
    }
}
```

**Further reading:** See [Boxing elimination types](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/GenerateBytecode.html#boxingEliminationTypes()) and [Forced quickening](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/ForceQuickening.html).

---

<a name="invalidate-persisted-bytecode-when-compatibility-assumptions-change"></a>

### `TR-SERIALIZATION` — Invalidate persisted bytecode when compatibility assumptions change

**When it applies:** Serialized bytecode or implementation archives survive interpreter rebuilds, runtime upgrades, or changes to their deployment platform.

**What goes wrong:** You validate only a custom encoding version, omitting other build, runtime, or platform assumptions on which the persisted representation depends.

**Why it matters:** Persisted interpreter data can also depend on framework-generated serialization, the interpreter's definition, and build- or platform-specific metadata. Those can change independently of the custom constant serializer.

**How to fix it:** Store and check a compatibility stamp covering the framework build and interpreter format before deserialization. Update the interpreter format version when serialization assumptions change.

**Before**

```java
if (readEncodingVersion(input) != CONSTANT_ENCODING_VERSION) {
    return parse(source);
}
return deserializeBytecode(input);
```

**After**

```java
record CacheStamp(String truffleBuild, int interpreterFormat) {}

CacheStamp expected = new CacheStamp(
                TRUFFLE_BUILD_ID, INTERPRETER_FORMAT_VERSION);
if (!readCacheStamp(input).equals(expected)) {
    return parse(source);
}
return deserializeBytecode(input);
```

**Further reading:** See [Builder bytecode serialization](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/BytecodeRootNodes.html) and [GenerateBytecode.enableSerialization](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/GenerateBytecode.html#enableSerialization()).

---

<a name="resume-osr-at-the-location-described-by-the-frame-state"></a>

### `TR-OSR-STATE` — Resume OSR at the location described by the frame state

**When it applies:** A bytecode interpreter enters OSR, including handwritten restart helpers and Bytecode DSL operations that inspect frame arguments.

**What goes wrong:** A shared OSR helper computes the resume address from the triggering opcode's size even when the caller has prepared the frame for another destination, or an operation assumes OSR frame arguments always follow the ordinary guest-call layout.

**Why it matters:** The OSR target, operand-stack layout, locals, and additional interpreter state jointly describe one execution point. A valid instruction address is insufficient if the frame describes a different point; entering at an operand byte can even decode a different instruction.

**How to fix it:** Compute the logical successor or restart address at the caller and pass it explicitly to the OSR helper. Prepare the frame for that same address, and carry any additional target-specific state according to the OSR API contract.

**Before**

```java
Object tryAtBackEdge(VirtualFrame frame, int triggeringBci) {
    if (BytecodeOSRNode.pollOSRBackEdge(this)) {
        return BytecodeOSRNode.tryOSR(
                        this, triggeringBci + 1, null, null, frame);
    }
    return null;
}

// Frame is now prepared for function entry, not after the current opcode.
prepareRestartFrame(frame);
Object result = tryAtBackEdge(frame, currentBci);
if (result != null) {
    return result;
}
resumeBci = entryBci;
```

**After**

```java
Object tryAtBackEdge(VirtualFrame frame, int resumeBci) {
    if (BytecodeOSRNode.pollOSRBackEdge(this)) {
        return BytecodeOSRNode.tryOSR(
                        this, resumeBci, null, null, frame);
    }
    return null;
}

prepareRestartFrame(frame);
resumeBci = entryBci;
Object result = tryAtBackEdge(frame, resumeBci);
if (result != null) {
    return result;
}
// Continue ordinary dispatch at resumeBci with the same prepared frame.
```

**Further reading:** See [Bytecode OSR](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/OnStackReplacement/#osr-for-bytecode-interpreters) and [BytecodeOSRNode.tryOSR](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/BytecodeOSRNode.html#tryOSR(com.oracle.truffle.api.nodes.BytecodeOSRNode,int,java.lang.Object,java.lang.Runnable,com.oracle.truffle.api.frame.VirtualFrame)).

---

<a name="keep-a-bytecode-index-paired-with-its-bytecode-node"></a>

### `TR-BYTECODE-LOCATION` — Keep a bytecode index paired with its bytecode node

**When it applies:** Bytecode DSL operations retain a program location, a handwritten interpreter translates between internal bytecode and original source locations, or stack-trace capture must preserve the activation's location.

**What goes wrong:** You retain a bare bytecode index, then interpret it using whatever bytecode node the root returns later.

**Why it matters:** A bytecode node can be replaced as execution tiers or metadata configurations change. Its index is meaningful only in that node's instruction layout; fetching a newer node does not translate an old index.

**How to fix it:** Within an operation, bind a `BytecodeLocation`, or bind the bytecode node and index together when an allocation-free immediate use is appropriate. Retain the location rather than only its numeric index.

**Before**

```java
@Specialization
static void remember(@Bind("$bytecodeIndex") int bci) {
    savedIndex = bci;
}

// Later, after the root may have changed its bytecode:
inspect(root.getBytecodeNode().getBytecodeLocation(savedIndex));
```

**After**

```java
@Specialization
static void remember(@Bind BytecodeLocation location) {
    saveLocation(location); // Storage follows the required execution/owner lifetime.
}

// Later, translate node and index together if up-to-date metadata is wanted.
BytecodeLocation current = loadLocation().update();
inspect(current);
```

**Further reading:** See [Location From a Stack Frame](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/BytecodeLocation.html#get(com.oracle.truffle.api.frame.FrameInstance)) and [Bytecode index modes](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/BytecodeIndexMode.html).

---

<a name="keep-required-cleanup-on-the-configured-unwind-path"></a>

### `TR-BYTECODE-UNWIND` — Keep required cleanup on the configured unwind path

**When it applies:** Your Bytecode DSL interpreter needs handler-scoped cleanup or configures GenerateBytecode.unwindExceptions, available since 25.5, for language-internal transfers.

**What goes wrong:** You expect a selected unwind exception to run a TryCatchOtherwise otherwise handler, exception interception hook, or exceptional epilog, or explicitly configure ThreadDeath as an ordinary unwind exception.

**Why it matters:** Selected exceptions bypass guest catch handlers and both exceptional and otherwise handling in TryCatchOtherwise. They also bypass interception hooks and EpilogExceptional.

**How to fix it:** Put cleanup that must run during a selected unwind in TryFinally. Select your language's intended exception types rather than depending on otherwise handling for those transfers.

**Before**

```java
b.beginTryCatchOtherwise(b::emitReleaseResource);
    b.emitBody();
    b.emitHandleGuestErrorAndReleaseResource();
b.endTryCatchOtherwise();
```

**After**

```java
b.beginTryFinally(b::emitReleaseResource);
    b.beginTryCatch();
        b.emitBody();
        b.emitHandleGuestError();
    b.endTryCatch();
b.endTryFinally();
```

**Further reading:** See [Bytecode exception handling](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/GenerateBytecode.html#unwindExceptions()).

---

<a name="distinguish-single-method-suspension-from-whole-stack-capture"></a>

### `TR-CONTINUATION-SCOPE` — Distinguish single-method suspension from whole-stack capture

**When it applies:** You implement generators or coroutines using yielding bytecode roots and calls between guest methods.

**What goes wrong:** You assume that yielding in a callee automatically suspends its callers and preserves the remainder of their execution.

**Why it matters:** Bytecode DSL continuations suspend the current method, not the entire call stack. A yield returns control and a result to the caller; it does not automatically capture the caller's activation.

**How to fix it:** Define whether the guest feature needs single-method suspension or a separate caller-state protocol. For intentional stackless generators, distinguish a yielded continuation from ordinary completion and let the caller continue according to that generator contract.

**Before**

```java
Object invokeGenerator(RootCallTarget generator, Object argument) {
    Object result = generator.call(argument);
    return consumeCompletedValue(result); // A yield also returns here.
}
```

**After**

```java
sealed interface GuestStep permits GuestYield, GuestDone { }
record GuestYield(ContinuationResult continuation) implements GuestStep { }
record GuestDone(Object value) implements GuestStep { }

GuestStep invokeGenerator(RootCallTarget generator, Object argument) {
    Object result = generator.call(argument);
    if (result instanceof ContinuationResult yielded) {
        return new GuestYield(yielded);
    }
    return new GuestDone(result);
}

GuestStep caller(RootCallTarget generator, Object argument) {
    GuestStep step = invokeGenerator(generator, argument);
    recordStepInCaller(step); // Runs after either yield or completion.
    return step;
}
```

**Further reading:** See [Continuation results](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/ContinuationResult.html) and [Yield Result and Bound Continuation State](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/Yield.html).

---

<a name="keep-label-creation-and-emission-in-the-same-builder-scope"></a>

### `TR-BYTECODE-LABEL-SCOPE` — Keep label creation and emission in the same builder scope

**When it applies:** You translate guest control flow using `BytecodeLabel` and generated builder branches.

**What goes wrong:** You treat a label as an unrestricted bytecode address, emit it in a different scope from its creation, or branch into a nested operation.

**Why it matters:** `createLabel` establishes the label's owning `Root` or `Block` scope; `emitLabel` places its target in that same scope. A branch must be directly or indirectly nested in the creation scope.

**How to fix it:** Create and emit the label in the same `Root` or `Block`. A branch can come from a nested block to exit to that outer target, but cannot enter a nested scope from outside it.

**Before**

```java
// Builder fragments inside an already open Root.
b.beginBlock();
    BytecodeLabel exit = b.createLabel();
    b.beginBlock();
        b.emitBranch(exit);
        b.emitLabel(exit); // Wrong scope: this is not the creation Block.
    b.endBlock();
b.endBlock();
```

**After**

```java
b.beginBlock();
    BytecodeLabel exit = b.createLabel();
    b.beginBlock();
        b.emitBranch(exit); // Nested source can exit to the outer target.
    b.endBlock();
    b.emitLabel(exit);
b.endBlock();
```

**Further reading:** See [Bytecode labels and branch scope](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/BytecodeLabel.html).

---

<a name="preserve-evaluation-order-and-the-selected-operand"></a>

### `TR-SHORT-CIRCUIT-SEMANTICS` — Preserve evaluation order and the selected operand

**When it applies:** You implement a lazy guest operator or lower a value-producing conditional.

**What goes wrong:** You choose a short-circuit operator from its name alone, reverse the operands, or preserve only a boolean test while changing the selected result or skipped effects.

**Why it matters:** Ordinary `@Operation` operands are eager. A short-circuit operation instead evaluates operands in order, with AND stopping on converted false and OR stopping on converted true.

**How to fix it:** Specify the operand order, stop condition, skipped effects, and result representation together. Choose converter polarity and the corresponding AND/OR operator from that specification.

**Before**

```java
@ShortCircuitOperation(name = "FirstPresent",
                operator = ShortCircuitOperation.Operator.OR_RETURN_VALUE,
                booleanConverter = IsMissing.class)
// Other root declarations omitted.
```

**After**

```java
@ShortCircuitOperation(name = "FirstPresent",
                operator = ShortCircuitOperation.Operator.AND_RETURN_VALUE,
                booleanConverter = IsMissing.class)
abstract class ExampleRoot extends RootNode implements BytecodeRootNode {
    // Other root annotations, constructor, and implementation are omitted here.
    @Operation
    public static final class IsMissing {
        @Specialization
        public static boolean test(Object value) {
            return value == Missing.INSTANCE;
        }
    }
}
```

**Further reading:** See [Short-circuit operations](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/ShortCircuitOperation.html) and [Operator Stop and Result Policies](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/ShortCircuitOperation.Operator.html).

---

<a name="rewrite-cleared-static-slots-before-reading-them"></a>

### `TR-STATIC-FRAME-LIVENESS` — Rewrite cleared static slots before reading them

**When it applies:** A frame layout uses `FrameSlotKind.Static` and liveness analysis clears dead values or controls which slots cross an execution transition.

**What goes wrong:** You treat `clearStatic` as storing a readable default value, or clear a slot on one predecessor while a merged path can still read its old value.

**Why it matters:** A cleared static slot must not be read until rewritten. The static accessors do not supply dynamic type checks that repair an inconsistent language-owned layout or liveness analysis.

**How to fix it:** Track each slot's live value and representation on every incoming path. Write the required value before any subsequent read of a cleared slot, and clear only after the last permitted use.

**Before**

```java
int finish(VirtualFrame frame, int temporarySlot) {
    frame.clearStatic(temporarySlot);
    return frame.getIntStatic(temporarySlot); // Not a reset-to-zero operation.
}
```

**After**

```java
int calculate(VirtualFrame frame, int temporarySlot, boolean chooseLeft,
                int left, int right) {
    if (chooseLeft) {
        frame.setIntStatic(temporarySlot, left);
    } else {
        frame.setIntStatic(temporarySlot, right);
    }
    // Both incoming paths define the same live int slot.
    int result = frame.getIntStatic(temporarySlot);
    frame.clearPrimitiveStatic(temporarySlot);
    // The result is in a Java local; no later path reads the cleared frame slot.
    return result;
}
```

**Further reading:** See [Static Clearing Contract](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/frame/Frame.html#clearStatic(int)) and [OSR Frame Transfer and Live Variables](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/OnStackReplacement/#osr-for-bytecode-interpreters).

---

## Threaded interpreter dispatch

<a name="store-handler-return-state-directly-into-its-logical-local"></a>

### `TR-HANDLER-UNWIND` — Store handler return state directly into its logical local

**When it applies:** An interpreter uses `HostCompilerDirectives.BytecodeInterpreterHandlerConfig.Argument(returnValue = true)` to describe an argument updated by a handler result.

**What goes wrong:** You first store that result into a temporary and then copy it into the logical state variable represented by the configured argument.

**Why it matters:** Threaded-handler unwind repair relies on the immediate result-store shape to identify which local must be updated on an exceptional edge. The later Java assignment to the intended state local need not execute when an exception unwinds.

**How to fix it:** Assign each configured handler result directly to its logical local. Keep the argument configuration consistent across interpreter switch methods and preserve this assignment shape when refactoring or generating dispatch code.

**Before**

```java
switch (opcode) {
    case LOAD -> {
        int next = loadHandler(pc, state, bytecode, frame);
        pc = next;
    }
    case CALL -> {
        int next = callHandler(pc, state, bytecode, frame);
        pc = next;
    }
}
```

**After**

```java
switch (opcode) {
    case LOAD -> pc = loadHandler(pc, state, bytecode, frame);
    case CALL -> pc = callHandler(pc, state, bytecode, frame);
}
```

**Further reading:** See [Handler returnValue](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/HostCompilerDirectives.BytecodeInterpreterHandlerConfig.Argument.html#returnValue()) and [One Compilation per Bytecode Handler](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/one-compilation-per-bytecode-handler/).

---

<a name="do-not-depend-on-the-identity-of-fully-expanded-handler-state"></a>

### `TR-HANDLER-IDENTITY` — Do not depend on the identity of fully expanded handler state

**When it applies:** A handwritten bytecode interpreter opts into host-handler argument expansion with `ExpansionKind.VIRTUAL` (API available since 25.1).

**What goes wrong:** A handler uses the expanded wrapper's Java identity to recover execution state, assuming it is the same object that the switch loop passed.

**Why it matters:** Full expansion passes fields without preserving the original wrapper reference. A handler stub reconstructs a wrapper and transports updated fields back; field-value transfer does not preserve wrapper identity across stub invocations.

**How to fix it:** Treat the fully expanded wrapper as a field carrier, not as an identity token. Carry any stable execution-owner reference explicitly, or use a non-full-expansion configuration when the original wrapper identity is part of the contract.

**Before**

```java
// The switch configures its State argument with @Argument(expand = VIRTUAL).
static final class State {
    int stackPointer;
}

@BytecodeInterpreterHandler(READ)
static int read(Frame frame, State state) {
    ExecutionOwner owner = ownersByStateIdentity.get(state);
    return owner.read(frame, state.stackPointer);
}
```

**After**

```java
// The switch still fully expands State, including the explicit owner field.
static final class State {
    final ExecutionOwner owner;
    int stackPointer;

    State(ExecutionOwner owner) {
        this.owner = owner;
    }
}

@BytecodeInterpreterHandler(READ)
static int read(Frame frame, State state) {
    return state.owner.read(frame, state.stackPointer);
}
```

**Further reading:** See [Full Parameter Expansion](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/one-compilation-per-bytecode-handler/#full-parameter-expansion) and [ExpansionKind](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/HostCompilerDirectives.BytecodeInterpreterHandlerConfig.Argument.ExpansionKind.html).

---

<a name="keep-template-state-valid-at-each-threaded-dispatch"></a>

### `TR-HANDLER-TEMPLATES` — Keep template state valid at each threaded dispatch

**When it applies:** A handwritten interpreter uses threaded bytecode-handler templates, available since 25.5.

**What goes wrong:** You treat a template variable as arbitrary runtime state, assign values outside its configured range, or update several branch-dependent template fields through independent control-flow merges.

**Why it matters:** Template fields select handler variants rather than ordinary expanded argument slots. Each field must be an `int` of a `VIRTUAL` argument, with a configured variant count of at least two.

**How to fix it:** Keep a small explicit state machine and update related template fields together in one `if` or `switch`. Different runtime branches may select different constants; the branch condition itself need not be constant.

**Before**

```java
static final class State {
    int level;
    int mode;
}

static void selectNext(State state, boolean keepOperand, boolean alternateMode) {
    if (keepOperand) {
        state.level = 1;
    } else {
        state.level = 0;
    }
    if (alternateMode) {
        state.mode = 1;
    } else {
        state.mode = 0;
    }
}
```

**After**

```java
static void selectNext(State state, boolean keepOperand, boolean alternateMode) {
    int selection = (keepOperand ? 1 : 0) | (alternateMode ? 2 : 0);
    switch (selection) {
        case 0 -> { state.level = 0; state.mode = 0; }
        case 1 -> { state.level = 1; state.mode = 0; }
        case 2 -> { state.level = 0; state.mode = 1; }
        case 3 -> { state.level = 1; state.mode = 1; }
        default -> throw new AssertionError(selection);
    }
}
```

**Further reading:** See [Bytecode Handler Templates](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/one-compilation-per-bytecode-handler/#bytecode-handler-templates) and [Field.templateVariable](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/HostCompilerDirectives.BytecodeInterpreterHandlerConfig.Argument.Field.html#templateVariable()).

---

<a name="keep-per-instruction-work-inside-threaded-handlers"></a>

### `TR-HANDLER-DISPATCH` — Keep per-instruction work inside threaded handlers

**When it applies:** You mark a dispatch loop with `HostCompilerDirectives.markThreadedSwitch`, or use `BytecodeInterpreterHandler` and its associated configuration with tail-call threading enabled.

**What goes wrong:** You assume a switch marker guarantees threading, put required per-instruction work after a handler call in the outer switch, or configure incompatible handler arguments because you forget the implicit receiver.

**Why it matters:** A threaded handler can jump directly to the next handler without returning to the switch. Caller-side work runs only when threading terminates, not after each instruction.

**How to fix it:** Put required per-instruction work in the handler before it returns, or at another explicitly supported point that runs for every applicable threaded instruction. Keep participating handler signatures and modifiers compatible and keep the configuration consistent across the interpreter's switch methods.

**Before**

```java
@BytecodeInterpreterHandler(ADD)
int addHandler(int pc, State state, short[] code, Frame frame) {
    state.push(state.pop() + state.pop());
    return pc + 1;
}

// Inside the configured dispatch loop:
switch (opcode) {
    case ADD -> {
        pc = addHandler(pc, state, code, frame);
        recordCompletedInstruction(state); // Not reached after every threaded ADD.
    }
    // Other handlers have the same calling convention.
}
```

**After**

```java
@BytecodeInterpreterHandler(ADD)
int addHandler(int pc, State state, short[] code, Frame frame) {
    state.push(state.pop() + state.pop());
    recordCompletedInstruction(state);
    return pc + 1;
}

// Inside the configured dispatch loop:
switch (opcode) {
    case ADD -> pc = addHandler(pc, state, code, frame);
    // Apply the same per-instruction policy in every applicable handler.
}
```

**Further reading:** See [Handler Threading](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/HostCompilerDirectives.BytecodeInterpreterHandler.html#threading()) and [Opcode Fetch Contract](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/HostCompilerDirectives.BytecodeInterpreterFetchOpcode.html).

---

## Instrumentation, source locations, and diagnostics

<a name="associate-root-locations-with-the-actual-parsed-source"></a>

### `TR-SOURCE-SECTIONS` — Associate root locations with the actual parsed source

**When it applies:** You construct sources or provide root or operation source locations for tooling.

**What goes wrong:** You fabricate a separate empty source for every function merely to return a non-null source section, or lose the originating location on an uncached failure path.

**Why it matters:** The section no longer identifies the input that produced the root. Sampling and other tools can receive plausible-looking but misleading source identities even though an instrumentation availability check passes.

**How to fix it:** Carry the source from the parsing request into the roots it produces. Provide actual ranges where supported; when a range is unavailable, create an unavailable section on that real source rather than fabricating one source per root.

**Before**

```java
SourceSection section = Source.newBuilder(languageId, "", functionName)
                .build().createUnavailableSection();
```

**After**

```java
final class FunctionRoot extends RootNode {
    private final SourceSection section;

    FunctionRoot(TruffleLanguage<?> language, Source parsedSource) {
        super(language);
        section = parsedSource.createUnavailableSection();
    }

    @Override
    public SourceSection getSourceSection() {
        return section;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return executeFunction(frame);
    }
}
```

**Further reading:** See [Source Range Coordinates](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/source/Source.html#createSection(int,int,int,int)) and [Nearest AST Source Location](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html#getEncapsulatingSourceSection()).

---

<a name="refresh-lazy-source-information-before-reading-it"></a>

### `TR-SOURCE-METADATA` — Refresh lazy source information before reading it

**When it applies:** You inspect source or diagnostic metadata, retain source-level function identity, reconstruct source descriptors, or load source content.

**What goes wrong:** You ignore updated source metadata, force text loading or executable-code resolution solely to obtain identity metadata, or require an entered context during a reconstruction phase that must also work without one.

**Why it matters:** Source-only reparsing need not invalidate compiled code when the executable bytecode is unchanged. Compiled code can therefore retain a node without the requested source information even after a newer node exists.

**How to fix it:** Use `BytecodeNode.ensureSourceInformation` or `BytecodeLocation.ensureSourceInformation` and read from the returned updated object. Keep a location's node/index association intact.

**Before**

```java
SourceSection findSection(BytecodeLocation location) {
    location.ensureSourceInformation(); // Updated location is discarded.
    return location.getSourceSection();
}
```

**After**

```java
SourceSection findSection(BytecodeLocation location) {
    BytecodeLocation withSources = location.ensureSourceInformation();
    return withSources.getSourceSection();
}
```

**Further reading:** See [Bytecode source configuration](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/BytecodeConfig.html) and [BytecodeNode.ensureSourceInformation](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/BytecodeNode.html#ensureSourceInformation()).

---

<a name="keep-tooling-language-views-out-of-execution-data-flow"></a>

### `TR-LANGUAGE-VIEWS` — Keep tooling language views out of execution data flow

**When it applies:** You implement language views for displaying or inspecting values, or derive semantic values from display-oriented helpers.

**What goes wrong:** You substitute a language-view wrapper for the underlying execution value, or store diagnostic text where the operation requires a semantic string or name.

**Why it matters:** A language view supplies language-specific presentation and inspection traits; it is not a general execution-conversion adapter. Delegation through another library can reach the original delegate without using the view's customized interop messages.

**How to fix it:** Keep the original execution value and a separately scoped inspection view. Use the view for the intended tooling operation and the original value for execution.

**Before**

```java
Object value = readForeignInput();
value = createLanguageInspectionView(value);
showInDebugger(value);
executeOperation(value); // Tooling wrapper has escaped into execution.
```

**After**

```java
Object value = readForeignInput();
Object inspectionView = createLanguageInspectionView(value);
showInDebugger(inspectionView);
executeOperation(value);
```

**Further reading:** See [getLanguageView](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html#getLanguageView(C,java.lang.Object)).

---

<a name="tag-language-statements-rather-than-source-lines"></a>

### `TR-STATEMENT-TAGS` — Tag language statements rather than source lines

**When it applies:** Your parser or AST marks guest statement locations for instrumentation and debugging.

**What goes wrong:** You emit a StatementTag whenever the source line changes, treating source-section changes as statement boundaries.

**Why it matters:** A statement can span several lines, and several statements can share a line. Statement tags describe the language's stepping locations, not formatting.

**How to fix it:** Derive statement tags from the parsed syntax and the language's intended stepping behavior. Keep source-range construction separate from the decision to introduce a statement tag.

**Before**

```java
for (SyntaxPart part : statement.parts()) {
    if (part.startLine() != previousLine) {
        closePreviousStatementRegion();
        b.beginTag(StatementTag.class);
        openStatementRegion = true;
    }
    emitPart(b, part);
    previousLine = part.startLine();
}
closePreviousStatementRegion();
```

**After**

```java
for (StatementSyntax statement : body.statements()) {
    beginSourceRange(b, statement.sourceSection());
    b.beginTag(StatementTag.class);
        emitStatement(b, statement);
    b.endTag(StatementTag.class);
    endSourceRange(b);
}
```

**Further reading:** See [StatementTag](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/StandardTags.StatementTag.html) and [Stable Tag Semantics](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/InstrumentableNode.html#hasTag(java.lang.Class)).

---

<a name="preserve-non-execution-behavior-through-instrumentation-wrappers"></a>

### `TR-WRAPPER-DELEGATION` — Preserve non-execution behavior through instrumentation wrappers

**When it applies:** Instrumentable nodes expose metadata or other non-execution methods used by execution or diagnostics.

**What goes wrong:** You assume GenerateWrapper delegates every method to the wrapped node, including concrete metadata methods with default implementations.

**Why it matters:** The generator overrides eligible execution/resume methods and delegates other abstract methods. It does not override arbitrary concrete methods.

**How to fix it:** Make metadata methods abstract when generated delegation is the intended contract and implement them in the actual nodes. Alternatively, preserve explicit wrapper delegation in each applicable concrete implementation, accounting for subclasses rather than testing for just one generated wrapper class.

**Before**

```java
@GenerateWrapper
abstract class ExpressionNode extends Node implements InstrumentableNode {
    public abstract Object execute(VirtualFrame frame);

    public String expressionText() {
        return null;
    }

    public WrapperNode createWrapper(ProbeNode probe) {
        return new ExpressionNodeWrapper(this, probe);
    }
}

final class NameNode extends ExpressionNode {
    public String expressionText() {
        return "name";
    }
    // execute and the remaining instrumentation methods are omitted.
}
```

**After**

```java
@GenerateWrapper
abstract class ExpressionNode extends Node implements InstrumentableNode {
    public abstract Object execute(VirtualFrame frame);
    public abstract String expressionText();

    public WrapperNode createWrapper(ProbeNode probe) {
        return new ExpressionNodeWrapper(this, probe);
    }
}
```

**Further reading:** See [Instrumentation Inserts Wrapping Nodes](https://www.graalvm.org/latest/graalvm-as-a-platform/implement-instrument/#execution-event-node) and [GenerateWrapper](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/GenerateWrapper.html).

---

<a name="complete-manual-instrumentation-events-on-every-exit"></a>

### `TR-PROBE-PAIRING` — Complete manual instrumentation events on every exit

**When it applies:** Your interpreter reports execution events through `ProbeNode` or configures generated wrappers for suspension and resumption.

**What goes wrong:** A return opcode or exceptional exit bypasses completion of an entered instrumentation region, a handwritten wrapper ignores unwind requests, or suspension/resumption is reported as ordinary failure/fresh entry.

**Why it matters:** Instrumentation clients depend on the pairing and execution-path protocol, not merely a sequence of source-line changes. A normal function return can still leave a manually entered statement event unfinished.

**How to fix it:** Prefer `@GenerateWrapper` where it can express the instrumentation. For a manual integration, follow the current `ProbeNode` protocol, track whether the return callback was already invoked, and preserve unwind actions.

**Before**

```java
Object execute(VirtualFrame frame) {
    probe.onEnter(frame);
    Object result = delegate.executeGeneric(frame);
    if (isEarlyReturn(result)) {
        return unwrapReturn(result); // No completion event.
    }
    probe.onReturnValue(frame, result);
    return result;
}
```

**After**

```java
Object execute(VirtualFrame frame) {
    for (;;) {
        boolean returnCalled = false;
        try {
            probe.onEnter(frame);
            Object result = delegate.executeGeneric(frame);
            returnCalled = true;
            probe.onReturnValue(frame, result);
            return result;
        } catch (Throwable failure) {
            Object action = probe.onReturnExceptionalOrUnwind(
                            frame, failure, returnCalled);
            if (action == ProbeNode.UNWIND_ACTION_REENTER) {
                continue;
            }
            if (action != null) {
                return action;
            }
            throw failure;
        }
    }
}
```

**Further reading:** See [ProbeNode and Recommended Wrapper](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/ProbeNode.html) and [Exceptional Completion and Unwind](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/ProbeNode.html#onReturnExceptionalOrUnwind(com.oracle.truffle.api.frame.VirtualFrame,java.lang.Throwable,boolean)).

---

<a name="make-root-preparation-stabilize-for-repeated-tags"></a>

### `TR-INSTRUMENTATION-PREPARATION` — Make root preparation stabilize for repeated tags

**When it applies:** A root overrides `RootNode.prepareForInstrumentation`, or a Bytecode DSL interpreter updates its prepared source or instrumentation information.

**What goes wrong:** You manually forward the callback to other roots or assume it runs exactly once, then rebuild or invalidate the same prepared state on each notification.

**Why it matters:** The current public contract permits repeated calls and requires the implementation to stabilize when the same tags have already been prepared. Manual forwarding can bypass the framework's preparation lifecycle and introduce repeated work or inconsistent state.

**How to fix it:** Let the framework drive preparation and make the override recognize information already prepared for the requested tags. Prepare additional supported information when a new tag is requested; do not rebuild existing state for an unchanged request.

**Before**

```java
@Override
protected void prepareForInstrumentation(Set<Class<?>> tags) {
    atomic(() -> {
        if (tags.contains(StandardTags.StatementTag.class)) {
            materializeStatementInformation(); // Repeats on every notification.
        }
    });
}
```

**After**

```java
private boolean statementsPrepared;

@Override
protected void prepareForInstrumentation(Set<Class<?>> tags) {
    atomic(() -> {
        if (tags.contains(StandardTags.StatementTag.class) && !statementsPrepared) {
            materializeStatementInformation();
            statementsPrepared = true;
        }
    });
}
```

**Further reading:** See [Root Instrumentation Preparation](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/RootNode.html#prepareForInstrumentation(java.util.Set)).

---

<a name="do-not-substitute-instruction-tracing-for-semantic-events"></a>

### `TR-INSTRUCTION-TRACING` — Do not substitute instruction tracing for semantic events

**When it applies:** A Bytecode DSL language needs specified execution hooks, or uses instruction tracing to debug or profile its interpreter.

**What goes wrong:** You implement a required guest event by counting instruction-tracer callbacks and assume there is permanently one callback for each original guest operation.

**Why it matters:** `InstructionTracer` observes the bytecode instructions the interpreter executes. That is not a stable one-to-one mapping to the language's original operations or semantic event points.

**How to fix it:** Define the required semantic event points in your language and emit suitable `@Instrumentation` operations or instrumentation tags at those points. Enable the corresponding configuration when the feature is active.

**Before**

```java
InstructionTracer tracer = (access, bytecode, bci, frame) -> {
    // Wrong: bytecode instructions are not guest expression completions.
    notifyExpressionCompleted();
};
roots.addInstructionTracer(tracer);
```

**After**

```java
@Instrumentation
public static final class ObserveResult {
    @Specialization
    @TruffleBoundary
    public static Object observe(Object value) {
        notifyExpressionCompleted(value);
        return value;
    }
}

// Parser fragment at a language-defined expression-completion point:
b.beginObserveResult();
emitExpression(b, expression);
b.endObserveResult();

// Configure the generated interpreter when this feature is active:
BytecodeConfig config = MyRootNodeGen.newConfigBuilder()
                .addInstrumentation(ObserveResult.class).build();
roots.update(config);
```

**Further reading:** See [Instrumentation Operation Contract](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/Instrumentation.html) and [Instruction tracing](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/InstructionTracer.html).

---

<a name="establish-prolog-state-before-the-root-body-event"></a>

### `TR-ROOT-BODY-SCOPE` — Establish prolog state before the root-body event

**When it applies:** You expose function-body instrumentation and debugger scopes whose state is established by a root prolog.

**What goes wrong:** A node advertises `StandardTags.RootBodyTag` but establishes its required scope or copies arguments into local slots only inside that tagged node's execution.

**Why it matters:** The root-body tag excludes the root prolog and epilog. Its entry event must already expose the local state established by that prolog, including argument-to-local initialization when your implementation uses it.

**How to fix it:** Execute required prolog setup before entering the instrumentable body child. Keep genuine guest initialization at its specified semantic point; do not eagerly initialize every lexical declaration merely to make it visible to a tool.

**Before**

```java
final class BodyNode extends InstrumentableExpressionNode {
    @Child private ExpressionNode statements;

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.RootBodyTag.class || super.hasTag(tag);
    }

    @Override
    Object execute(VirtualFrame frame) {
        initializeProlog(frame); // The body's onEnter event already fired.
        return statements.execute(frame);
    }
}
```

**After**

```java
abstract class FunctionRoot extends RootNode {
    @Child private BodyNode body;

    FunctionRoot(TruffleLanguage<?> language, BodyNode body) {
        super(language);
        this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        initializeProlog(frame);
        return body.execute(frame); // Entry observers now see prolog state.
    }

    protected abstract void initializeProlog(VirtualFrame frame);
}

final class BodyNode extends InstrumentableExpressionNode {
    @Child private ExpressionNode statements;

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.RootBodyTag.class || super.hasTag(tag);
    }

    @Override
    Object execute(VirtualFrame frame) {
        return statements.execute(frame);
    }
}
```

**Further reading:** See [Root-Body Tag Contract](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/StandardTags.RootBodyTag.html) and [Scope Query and Event Phase](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/NodeLibrary.html#getScope(java.lang.Object,com.oracle.truffle.api.frame.Frame,boolean)).

---

<a name="report-allocation-intent-before-creating-the-guest-value"></a>

### `TR-ALLOCATION-EVENTS` — Report allocation intent before creating the guest value

**When it applies:** Your implementation reports guest-value allocation or reallocation through `AllocationReporter`.

**What goes wrong:** You allocate the value first and then pass it through a helper that emits both allocation notifications.

**Why it matters:** `onEnter` reports the intent to allocate or reallocate; `onReturnValue` reports the resulting value afterward. Clients can observe nested allocation order, so two adjacent notifications after construction are not equivalent to bracketing construction.

**How to fix it:** Obtain the reporter through the appropriate `Env.lookup(AllocationReporter.class)` and keep its reference in a final or compilation-final field when used on compiled paths. That storage requirement does not itself establish cross-context sharing or engine-wide identity.

**Before**

```java
GuestBox allocate(Object payload) {
    GuestBox result = new GuestBox(payload);
    return report(result);
}

GuestBox report(GuestBox result) {
    reporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
    reporter.onReturnValue(result, 0, AllocationReporter.SIZE_UNKNOWN);
    return result;
}
```

**After**

```java
final class GuestAllocator {
    private final AllocationReporter reporter;

    GuestAllocator(TruffleLanguage.Env env) {
        reporter = env.lookup(AllocationReporter.class);
    }

    GuestBox allocate(Object payload) {
        reporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
        GuestBox result = new GuestBox(payload);
        reporter.onReturnValue(result, 0, AllocationReporter.SIZE_UNKNOWN);
        return result;
    }
}

final class GuestBox implements TruffleObject {
    final Object payload;

    GuestBox(Object payload) {
        this.payload = payload;
    }
}
```

**Further reading:** See [Allocation Reporter Contract and Example](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/AllocationReporter.html) and [Allocation Intent](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/AllocationReporter.html#onEnter(java.lang.Object,long,long)).

---

<a name="keep-compiler-diagnostics-independent-of-guest-execution"></a>

### `TR-DIAGNOSTIC-SAFETY` — Keep compiler diagnostics independent of guest execution

**When it applies:** You expose node debug properties or render language-owned objects for compiler diagnostics.

**What goes wrong:** You obtain diagnostic text through guest conversion, execute guest callbacks, or look up a context that you assume is entered.

**Why it matters:** Compiler diagnostics can be requested on a compiler thread without an entered guest context. `Node.getDebugProperties()` explicitly forbids guest execution and dependence on context entry.

**How to fix it:** Report stable host metadata that you already own, such as operation names, source indexes, and declared arity. For debug properties, prefer strings, boxed primitives, classes, or enums.

**Before**

```java
@Override
public Map<String, Object> getDebugProperties() {
    return Map.of("operand", formatGuestValue(operand)); // Can execute guest code.
}
```

**After**

```java
final class OperationNode extends Node {
    private final String operationName;
    private final int arity;

    OperationNode(String operationName, int arity) {
        this.operationName = operationName;
        this.arity = arity;
    }

    @Override
    public Map<String, Object> getDebugProperties() {
        return Map.of("operation", operationName, "arity", arity);
    }
}
```

**Further reading:** See [Node Debug Property Contract](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/nodes/Node.html#getDebugProperties()).

---

<a name="preserve-syntax-and-source-locations-when-materializing-nodes"></a>

### `TR-INSTRUMENTABLE-MATERIALIZATION` — Preserve syntax and source locations when materializing nodes

**When it applies:** An optimized AST node implements `InstrumentableNode.materializeInstrumentableNodes` to restore syntax required by supported instrumentation tags.

**What goes wrong:** You materialize only one familiar executable form, discard an operand's source section in favor of its parent's, or return a replacement that needs another materialization during the same traversal.

**Why it matters:** Ordinary execution can succeed while instrumentation rejects another supported form or reports events and stack locations for the wrong source expression. Materialization restores syntactic structure, not merely the final computed value.

**How to fix it:** Cover every executable form that can reach the materialization route under the language's supported syntax and tags. Preserve evaluation order, values, guest failures, and each represented expression's source section.

**Before**

```java
@Override
public InstrumentableNode materializeInstrumentableNodes(
                Set<Class<? extends Tag>> tags) {
    if (tags.contains(StandardTags.ExpressionTag.class)) {
        return new AddNode(
                        new LiteralNode(leftValue, getSourceSection()),
                        new LiteralNode(rightValue, getSourceSection()),
                        getSourceSection()); // Loses both literal locations.
    }
    return this;
}
```

**After**

```java
@GenerateWrapper
abstract class ExpressionNode extends Node implements InstrumentableNode {
    public abstract int executeInt(VirtualFrame frame);
    @Override public abstract SourceSection getSourceSection();

    @Override
    public final boolean isInstrumentable() {
        return getSourceSection() != null;
    }

    @Override
    public final boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.ExpressionTag.class;
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new ExpressionNodeWrapper(this, probe);
    }
}

final class FoldedAddNode extends ExpressionNode {
    private final int leftValue;
    private final int rightValue;
    private final SourceSection leftSource;
    private final SourceSection rightSource;
    private final SourceSection source;
    // Constructor stores the parser's values and original source sections.

    @Override public SourceSection getSourceSection() { return source; }

    @Override
    public int executeInt(VirtualFrame frame) {
        return leftValue + rightValue;
    }

    @Override
    public InstrumentableNode materializeInstrumentableNodes(
                    Set<Class<? extends Tag>> tags) {
        if (!tags.contains(StandardTags.ExpressionTag.class)) {
            return this;
        }
        return new AddNode(new LiteralNode(leftValue, leftSource),
                        new LiteralNode(rightValue, rightSource), source);
    }
}

final class LiteralNode extends ExpressionNode {
    private final int value;
    private final SourceSection source;
    // Constructor stores value and source.
    @Override public SourceSection getSourceSection() { return source; }
    @Override public int executeInt(VirtualFrame frame) { return value; }
}

final class AddNode extends ExpressionNode {
    @Child private ExpressionNode left;
    @Child private ExpressionNode right;
    private final SourceSection source;
    // Constructor stores both fresh children and the sum's source section.
    @Override public SourceSection getSourceSection() { return source; }

    @Override
    public int executeInt(VirtualFrame frame) {
        int leftValue = left.executeInt(frame);
        return leftValue + right.executeInt(frame);
    }
}
```

**Further reading:** See [Instrumentation Source Sections and Events](https://www.graalvm.org/latest/graalvm-as-a-platform/implement-instrument/#source-section-listener) and [Materialization Contract](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/InstrumentableNode.html#materializeInstrumentableNodes(java.util.Set)).
