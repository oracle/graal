/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionMap;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.SandboxPolicy;

import com.oracle.truffle.api.Option;

@Option.Group(PolyglotEngineImpl.OPTION_GROUP_ENGINE)
final class PolyglotEngineOptions {
    static final String PREINITIALIZE_CONTEXT_NAME = "PreinitializeContexts";
    private static final String INSTRUMENT_EXCEPTIONS_ARE_THROWN_NAME = "InstrumentExceptionsAreThrown";

    @Option(name = PREINITIALIZE_CONTEXT_NAME, category = OptionCategory.EXPERT, deprecated = true, help = "Preinitialize language contexts for given languages.")//
    static final OptionKey<String> PreinitializeContexts = new OptionKey<>("");

    /**
     * When the option is set the exceptions thrown by instruments are propagated rather than logged
     * into err.
     */
    @Option(name = INSTRUMENT_EXCEPTIONS_ARE_THROWN_NAME, category = OptionCategory.INTERNAL, help = "Propagates exceptions thrown by instruments. (default: true)", usageSyntax = "true|false")//
    static final OptionKey<Boolean> InstrumentExceptionsAreThrown = new OptionKey<>(true);

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "Propagates cancel execution exception into UncaughtExceptionHandler. " +
                    "For testing purposes only.")//
    static final OptionKey<Boolean> TriggerUncaughtExceptionHandlerForCancel = new OptionKey<>(false);

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "Show internal frames specific to the language implementation in stack traces.")//
    static final OptionKey<Boolean> ShowInternalStackFrames = new OptionKey<>(false);

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "Printed PolyglotException stacktrace unconditionally contains the stacktrace of the original internal exception " +
                    "as well as the stacktrace of the creation of the PolyglotException instance.")//
    static final OptionKey<Boolean> PrintInternalStackTrace = new OptionKey<>(false);

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "Enables conservative context references. " +
                    "This allows invalid sharing between contexts. " +
                    "For testing purposes only.", deprecated = true, deprecationMessage = "Has no longer any effect. Scheduled for removal in in 22.1.")//
    static final OptionKey<Boolean> UseConservativeContextReferences = new OptionKey<>(false);

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "Enables specialization statistics for nodes generated with Truffle DSL and prints the result on exit. " +
                    "In order for this flag to be functional -Atruffle.dsl.GenerateSpecializationStatistics=true needs to be set at build time. " + //
                    "Enabling this flag and the compiler option has major implications on the performance and footprint of the interpreter. " + //
                    "Do not use in production environments.")//
    static final OptionKey<Boolean> SpecializationStatistics = new OptionKey<>(false);

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "Traces thread local events and when they are processed on the individual threads. " +
                    "Prints messages with the [engine] [tl] prefix.")//
    static final OptionKey<Boolean> TraceThreadLocalActions = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL, help = "" +
                    "How long to wait for other threads to reach a synchronous ThreadLocalAction before cancelling it, in seconds. 0 means no limit.", usageSyntax = "[0, inf)")//
    static final OptionKey<Integer> SynchronousThreadLocalActionMaxWait = new OptionKey<>(60);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL, help = "" +
                    "Print thread stacktraces when a synchronous ThreadLocalAction is waiting for more than SynchronousThreadLocalActionMaxWait seconds.")//
    static final OptionKey<Boolean> SynchronousThreadLocalActionPrintStackTraces = new OptionKey<>(false);

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "" +
                    "Repeadly submits thread local actions and collects statistics about safepoint intervals in the process. " +
                    "Prints event and interval statistics when the context is closed for each thread. " +
                    "This option significantly slows down execution and is therefore intended for testing purposes only.")//
    static final OptionKey<Boolean> SafepointALot = new OptionKey<>(false);

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "" +
                    "Show Java stacktraces for missing polls longer than the supplied number of milliseconds. Implies SafepointALot.", usageSyntax = "[0, inf)")//
    static final OptionKey<Integer> TraceMissingSafepointPollInterval = new OptionKey<>(0);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL, help = "" +
                    "Prints the stack trace for all threads for a time interval. By default 0, which disables the output.", usageSyntax = "[1, inf)")//
    static final OptionKey<Long> TraceStackTraceInterval = new OptionKey<>(0L);

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "" +
                    "Print warning when the engine is using a default Truffle runtime (default: true).", usageSyntax = "true|false", sandbox = SandboxPolicy.UNTRUSTED)//
    static final OptionKey<Boolean> WarnInterpreterOnly = new OptionKey<>(true);

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "" +
                    "Print warning when a deprecated option is used (default: true).", usageSyntax = "true|false", sandbox = SandboxPolicy.UNTRUSTED)//
    static final OptionKey<Boolean> WarnOptionDeprecation = new OptionKey<>(true);

    @Option(category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL, help = "" +
                    "Warn that the virtual thread support is experimental (default: true).", usageSyntax = "true|false", sandbox = SandboxPolicy.UNTRUSTED)//
    static final OptionKey<Boolean> WarnVirtualThreadSupport = new OptionKey<>(true);

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "" +
                    "Warn when an isolated context uses host access without host method scoping (default: true).", usageSyntax = "true|false", sandbox = SandboxPolicy.UNTRUSTED)//
    static final OptionKey<Boolean> WarnMethodScoping = new OptionKey<>(true);

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "" +
                    "Use pre-initialized context when it's available (default: true).", usageSyntax = "true|false")//
    static final OptionKey<Boolean> UsePreInitializedContext = new OptionKey<>(true);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL, help = "" +
                    "On property accesses, the Static Object Model does not perform shape checks and uses unsafe casts")//
    static final OptionKey<Boolean> RelaxStaticObjectSafetyChecks = new OptionKey<>(false);

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "" +
                    "Option to force enable code sharing for this engine, even if the context was created with a bound engine. This option is intended for testing purposes only.")//
    static final OptionKey<Boolean> ForceCodeSharing = new OptionKey<>(false);

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "" +
                    "Option to force disable code sharing for this engine, even if the context was created with an explicit engine. This option is intended for testing purposes only.")//
    static final OptionKey<Boolean> DisableCodeSharing = new OptionKey<>(false);

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "" +
                    "Enables printing of code sharing related information to the logger. This option is intended to support debugging language implementations.")//
    static final OptionKey<Boolean> TraceCodeSharing = new OptionKey<>(false);

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, sandbox = SandboxPolicy.UNTRUSTED, usageSyntax = "true|false", help = "" +
                    "Asserts that enter and return are always called in pairs on ProbeNode, verifies correct behavior of wrapper nodes. Java asserts need to be turned on for this option to have an effect. (default: false)")//
    static final OptionKey<Boolean> AssertProbes = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Print information for source cache misses/evictions/failures.")//
    static final OptionKey<Boolean> TraceSourceCache = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Print information for all source cache events including hits and uncached misses.")//
    static final OptionKey<Boolean> TraceSourceCacheDetails = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Print source cache statistics for an engine when the engine is closed.") //
    public static final OptionKey<Boolean> SourceCacheStatistics = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Print source cache statistics for an engine when the engine is closed. With the details enabled, statistics for all individual sources are printed.") //
    public static final OptionKey<Boolean> SourceCacheStatisticDetails = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "" + //
                    "Trace every executed bytecode instruction. " + //
                    "Very high overhead, use only for debugging, never in production. " + //
                    "Supported only by Bytecode DSL interpreters. " + //
                    "Combine with engine.BytecodeMethodFilter and engine.BytecodeLanguageFilter to limit output.")//
    public static final OptionKey<Boolean> TraceBytecode = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false|<kind>[,<kind>...]", stability = OptionStability.STABLE, help = "" + //
                    "Trace on-stack bytecode interpreter transitions while bytecode is executing (for example uncached-to-cached updates, on-stack bytecode updates, and deoptimization transfers). " + //
                    "Set to `true` to trace all transitions, or use a comma-separated subset of transition kinds. " + //
                    "Available kinds are `transferToInterpreter`, `bytecode`, `tier`, `tag`, `instrumentation`. " + //
                    "The `bytecode` kind traces all bytecode updates; `tier`, `tag`, and `instrumentation` select subsets of bytecode updates. " + //
                    "Supported only by Bytecode DSL interpreters. " + //
                    "Combine with `--engine.BytecodeMethodFilter` and `--engine.BytecodeLanguageFilter` to limit output.")//
    public static final OptionKey<List<BytecodeTransitionKind>> TraceBytecodeTransition = new OptionKey<>(null, createBytecodeTransitionType());

    @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false|<group>[,<group>...]", stability = OptionStability.STABLE, help = "" + //
                    "Collect and print a histogram of executed bytecode opcodes. " + //
                    "Set to 'true' to enable basic mode or use a comma separated list to configure grouping (e.g. source,root). " + //
                    "Available groupings are root, tier, source, language, thread. " + //
                    "Grouping order matters and controls the primary, secondary, ... nesting in the printed histogram. " + //
                    "This feature adds high overhead, use for profiling in non-production runs only. " + //
                    "Supported only by Bytecode DSL interpreters. " + //
                    "Prints when the engine is closed by default, or periodically if BytecodeHistogramInterval > 0.") //
    public static final OptionKey<List<BytecodeHistogramGrouping>> BytecodeHistogram = new OptionKey<>(null, createBytecodeHistogramType());

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "" + //
                    "Print and reset the opcode histogram at a fixed interval while BytecodeHistogram is enabled. " + //
                    "Use 0 to disable periodic printing and print only once at shutdown. " + //
                    "Examples: 250ms, 2s, 1m.") //
    public static final OptionKey<Duration> BytecodeHistogramInterval = new OptionKey<>(Duration.ofMillis(0), createDurationType());

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "" + //
                    "Limit tracing and statistics to selected methods. " + //
                    "Matches against `RootNode.getQualifiedName()`. " + //
                    "Provide a comma-separated list of includes, or excludes prefixed with `~`. " + //
                    "An empty value means no restriction. " + //
                    "Whitespace around commas is ignored. " + //
                    "Applies to `--engine.TraceBytecode`, `--engine.TraceBytecodeTransition`, and `--engine.BytecodeHistogram`.") //
    public static final OptionKey<String> BytecodeMethodFilter = new OptionKey<>("");

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "" + //
                    "Limit tracing and statistics to specific language IDs. " + //
                    "Provide a comma-separated list of language IDs, for example: `js`, `python`. " + //
                    "An empty value includes all languages. " + //
                    "Applies to `--engine.TraceBytecode`, `--engine.TraceBytecodeTransition`, and `--engine.BytecodeHistogram`.") //
    public static final OptionKey<String> BytecodeLanguageFilter = new OptionKey<>("");

    enum StaticObjectStorageStrategies {
        DEFAULT,
        ARRAY_BASED,
        FIELD_BASED
    }

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "" +
                    "Set the storage strategy used by the Static Object Model. Accepted values are: ['default', 'array-based', 'field-based']", usageSyntax = "default|array-based|field-based")//
    static final OptionKey<StaticObjectStorageStrategies> StaticObjectStorageStrategy = new OptionKey<>(StaticObjectStorageStrategies.DEFAULT,
                    new OptionType<>("strategy", new Function<String, StaticObjectStorageStrategies>() {
                        @Override
                        public StaticObjectStorageStrategies apply(String s) {
                            switch (s) {
                                case "default":
                                    return StaticObjectStorageStrategies.DEFAULT;
                                case "array-based":
                                    return StaticObjectStorageStrategies.ARRAY_BASED;
                                case "field-based":
                                    return StaticObjectStorageStrategies.FIELD_BASED;
                                default:
                                    throw new IllegalArgumentException("Unexpected value for engine option 'SomStorageStrategy': " + s);
                            }
                        }
                    }));

    @Option(category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL, sandbox = SandboxPolicy.UNTRUSTED, usageSyntax = "Ignore|Print|Throw", help = CloseOnGCExceptionAction.HELP)//
    static final OptionKey<CloseOnGCExceptionAction> CloseOnGCFailureAction = new OptionKey<>(CloseOnGCExceptionAction.Print);

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Spawn an isolate with isolated heap for this engine. Can be set to true or false or to the set of languages that should be initialized.", //
                    usageSyntax = "true|false|<language>,<language>,...", sandbox = SandboxPolicy.UNTRUSTED)//
    static final OptionKey<String[]> SpawnIsolate = new OptionKey<>(null, new OptionType<>("<boolean|languages>", (s) -> {
        if (s.equals("true")) {
            return new String[0];
        } else if (s.equals("false")) {
            return null;
        } else {
            if (s.isEmpty()) {
                return new String[0];
            }
            return s.split(",");
        }
    }));

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "Path to the isolate library.", usageSyntax = "<path>", sandbox = SandboxPolicy.UNTRUSTED)//
    static final OptionKey<String> IsolateLibrary = new OptionKey<>(null, OptionType.defaultType(String.class));

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "Path to the external isolate launcher.", usageSyntax = "<path>", sandbox = SandboxPolicy.UNTRUSTED)//
    static final OptionKey<String> IsolateLauncher = new OptionKey<>(null, OptionType.defaultType(String.class));

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Stack space headroom for calls to the host. A value of 0 disables this check.", usageSyntax = "[0, inf)<B>|<KB>|<MB>|<GB>", sandbox = SandboxPolicy.UNTRUSTED)//
    static final OptionKey<Long> HostCallStackHeadRoom = new OptionKey<>(0L, createSizeInBytesType("engine.HostCallStackHeadRoom", 0L));

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Stack space headroom for any interpreter call. Supported only in the AOT mode.", //
                    usageSyntax = "[0, inf)<B>|<KB>|<MB>|<GB>", sandbox = SandboxPolicy.UNTRUSTED)//
    static final OptionKey<Long> InterpreterCallStackHeadRoom = new OptionKey<>(0L, createSizeInBytesType("engine.InterpreterCallStackHeadRoom", 0));

    // TODO: Usage syntax for OptionMap
    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Isolate VM options.", sandbox = SandboxPolicy.CONSTRAINED)//
    public static final OptionKey<OptionMap<String>> IsolateOption = OptionKey.mapOf(String.class);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL, help = "Enable memory protection for the isolate.", usageSyntax = "true|false", sandbox = SandboxPolicy.UNTRUSTED)//
    static final OptionKey<Boolean> IsolateMemoryProtection = new OptionKey<>(false);

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Enable untrusted code execution defenses.", usageSyntax = "none|hardware|software", sandbox = SandboxPolicy.UNTRUSTED)//
    static final OptionKey<UntrustedCodeMitigationPolicy> UntrustedCodeMitigation = new OptionKey<>(UntrustedCodeMitigationPolicy.NONE);

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "Set maximum polyglot isolate heap size. " +
                    "This is a hard limit for the size of the isolate heap including both guest applications retained data and data allocated by the runtime.", usageSyntax = "[32MB, inf)<B>|<KB>|<MB>|<GB>", sandbox = SandboxPolicy.UNTRUSTED)//
    static final OptionKey<Long> MaxIsolateMemory = new OptionKey<>(-1L, createSizeInBytesType("engine.MaxIsolateMemory", 32 * SizeUnit.MEGABYTE.factor));

    @Option(category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL, help = "Defines how an isolated heap is implemented for isolated engines. " +
                    "'internal' runs the isolate within the current VM, using native-image isolation, 'external' runs the isolate in a separate external process.", usageSyntax = "internal|external", sandbox = SandboxPolicy.UNTRUSTED)//
    static final OptionKey<IsolatePolicy> IsolateMode = new OptionKey<>(IsolatePolicy.INTERNAL);

    /**
     * Isolate-specific options that are invalid unless polyglot isolation is enabled
     * ({@code engine.SpawnIsolate=true}).
     * <p>
     * This list intentionally excludes {@code engine.IsolateMode}, {@code engine.IsolateLibrary},
     * and {@code engine.IsolateLauncher}, because CI jobs provide these options globally even for
     * tests that do not run with isolation enabled.
     */
    static final String[] ISOLATE_SPECIFIC_OPTIONS = new String[]{
                    "engine.HostCallStackHeadRoom",
                    "engine.IsolateOption",
                    "engine.IsolateMemoryProtection",
                    "engine.UntrustedCodeMitigation",
                    "engine.MaxIsolateMemory",
    };

    static final String[] ISOLATE_SPECIFIC_MAP_OPTIONS = new String[]{
                    "engine.IsolateOption.",
    };

    /*
     * Experimentally determined maximum stack size required for an interpreter call.
     */
    private static final long INTERPRETER_CALL_OVERHEAD = 4096L;
    /*
     * Experimentally determined maximum stack size required for one AST node.
     */
    private static final long INTERPRETER_NODE_OVERHEAD = 128L;

    /**
     * Checks options whether memory protection of an isolate using MPK is required. The memory
     * protection can be enabled either by {@code IsolateMemoryProtection} option or using
     * {@code UntrustedCodeMitigation} meta-option. The {@code UntrustedCodeMitigation=hardware} is
     * an alias for {@code IsolateMemoryProtection=true}.
     */
    static boolean isIsolateMemoryProtection(OptionValues optionValues) {
        return IsolateMemoryProtection.getValue(optionValues) || UntrustedCodeMitigation.getValue(optionValues) == UntrustedCodeMitigationPolicy.HARDWARE;
    }

    static long getMinInterpreterCallStackHeadRoom(int maxASTDepth) {
        /*
         * The following formula was determined based on the stack space consumption of the GraalJS
         * interpreter on a large JavaScript application. For safety the constants in the formula
         * are approximately 3 times as high as the maximum observed values.
         */
        return INTERPRETER_CALL_OVERHEAD + INTERPRETER_NODE_OVERHEAD * maxASTDepth;
    }

    static Map<String, String> filterHostOptions(OptionDescriptors engineOptionsDescriptors, Map<String, String> options) {
        OptionDescriptors localDescriptors = EngineAccessor.RUNTIME.getRuntimeOptionDescriptors();
        OptionDescriptors localOptions = EngineAccessor.LANGUAGE.createOptionDescriptorsUnion(localDescriptors, engineOptionsDescriptors);
        return options.entrySet().stream().filter((entry) -> filterHostOption(entry.getKey(), localOptions)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static boolean filterHostOption(String optionKey, OptionDescriptors localEngineOptions) {
        if (optionKey.startsWith("log.")) {
            return true;
        } else if (optionKey.equals("engine.InterpreterCallStackHeadRoom")) {
            return false;
        }
        return localEngineOptions.get(optionKey) != null;
    }

    enum CloseOnGCExceptionAction {
        Ignore,
        Print,
        PrintAll,
        Throw;

        private static final String HELP = "Specifies the action to take when closing a garbage-collected engine or context fails.%n" +
                        "The accepted values are:%n" +
                        "    Ignore:     Ignore the exception that occurs during close.%n" +
                        "    Print:      Log the failure only for the first occurrence; suppress subsequent ones (default value).%n" +
                        "    PrintAll:   Log each failure.%n" +
                        "    Throw:      Throw an exception instead of logging the failure.";
    }

    enum BytecodeHistogramGrouping {
        root,
        tier,
        source,
        language,
        thread,
    }

    enum BytecodeTransitionKind {
        transferToInterpreter,
        bytecode,
        tier,
        tag,
        instrumentation,
    }

    private static OptionType<List<BytecodeHistogramGrouping>> createBytecodeHistogramType() {
        return new OptionType<>("histogram", new Function<String, List<BytecodeHistogramGrouping>>() {

            private static final OptionType<BytecodeHistogramGrouping> TYPE = OptionType.defaultType(BytecodeHistogramGrouping.class);

            @SuppressWarnings("ResultOfMethodCallIgnored")
            @Override
            public List<BytecodeHistogramGrouping> apply(String t) {
                if ("true".equals(t)) {
                    return List.of();
                } else if ("false".equals(t)) {
                    return null;
                } else {
                    List<BytecodeHistogramGrouping> result = new ArrayList<>();
                    for (String s : t.split(",")) {
                        result.add(TYPE.convert(s));
                    }
                    return List.copyOf(result);
                }
            }

        });
    }

    private static OptionType<List<BytecodeTransitionKind>> createBytecodeTransitionType() {
        return new OptionType<>("transition", new Function<String, List<BytecodeTransitionKind>>() {

            private static final OptionType<BytecodeTransitionKind> TYPE = OptionType.defaultType(BytecodeTransitionKind.class);

            @SuppressWarnings("ResultOfMethodCallIgnored")
            @Override
            public List<BytecodeTransitionKind> apply(String t) {
                String value = t == null ? null : t.trim();
                if (value == null || value.isBlank() || "false".equals(value)) {
                    return null;
                } else if ("true".equals(value)) {
                    return List.of(BytecodeTransitionKind.values());
                } else {
                    List<BytecodeTransitionKind> result = new ArrayList<>();
                    for (String s : value.split(",")) {
                        result.add(TYPE.convert(s.trim()));
                    }
                    return List.copyOf(result);
                }
            }

        });
    }

    private static OptionType<Duration> createDurationType() {
        return new OptionType<>("time", new Function<String, Duration>() {

            @SuppressWarnings("ResultOfMethodCallIgnored")
            @Override
            public Duration apply(String t) {
                try {
                    ChronoUnit foundUnit = null;
                    String foundUnitName = null;
                    for (ChronoUnit unit : ChronoUnit.values()) {
                        String unitName = getUnitName(unit);
                        if (unitName == null) {
                            continue;
                        }
                        if (t.endsWith(unitName)) {
                            foundUnit = unit;
                            foundUnitName = unitName;
                            break;
                        }
                    }
                    if (foundUnit == null || foundUnitName == null) {
                        throw invalidValue(t);
                    }
                    String subString = t.substring(0, t.length() - foundUnitName.length());
                    long value = Long.parseLong(subString);
                    if (value < 0) {
                        throw invalidValue(t);
                    }
                    return Duration.of(value, foundUnit);
                } catch (NumberFormatException | ArithmeticException | DateTimeParseException e) {
                    throw invalidValue(t);
                }
            }

            private String getUnitName(ChronoUnit unit) {
                switch (unit) {
                    case MILLIS:
                        return "ms";
                    case SECONDS:
                        return "s";
                    case MINUTES:
                        return "m";
                    case HOURS:
                        return "h";
                    case DAYS:
                        return "d";
                }
                return null;
            }

            private IllegalArgumentException invalidValue(String value) {
                throw new IllegalArgumentException("Invalid duration '" + value + "' specified. " //
                                + "A valid duration consists of a positive integer value followed by a chronological time unit. " //
                                + "For example '15ms' or '6s'. Valid time units are " //
                                + "'ms' for milliseconds, " //
                                + "'s' for seconds, " //
                                + "'m' for minutes, " //
                                + "'h' for hours, and " //
                                + "'d' for days.");
            }
        });
    }

    enum UntrustedCodeMitigationPolicy {
        NONE,
        HARDWARE,
        SOFTWARE;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    enum IsolatePolicy {
        INTERNAL,
        EXTERNAL;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    enum SizeUnit {
        GIGABYTE("GB", 1024 * 1024 * 1024),
        MEGABYTE("MB", 1024 * 1024),
        KILOBYTE("KB", 1024),
        BYTE("B", 1);

        private final String symbol;
        private final long factor;

        SizeUnit(String symbol, long factor) {
            this.symbol = symbol;
            this.factor = factor;
        }
    }

    private static OptionType<Long> createSizeInBytesType(String optionName, long min) {
        return new OptionType<>("sizeinbytes", new Function<String, Long>() {

            @Override
            public Long apply(String s) {
                try {
                    SizeUnit foundUnit = null;
                    for (SizeUnit unit : SizeUnit.values()) {
                        if (s.endsWith(unit.symbol)) {
                            foundUnit = unit;
                            break;
                        }
                    }
                    if (foundUnit == null) {
                        throw invalidValue(s);
                    }
                    String subString = s.substring(0, s.length() - foundUnit.symbol.length());
                    long value = Long.parseLong(subString);
                    if (value < 0) {
                        throw invalidValue(s);
                    }
                    value = Math.multiplyExact(value, foundUnit.factor);
                    if (value < min) {
                        throw invalidRange(s);
                    }
                    return value;
                } catch (NumberFormatException | ArithmeticException e) {
                    throw invalidValue(s);
                }
            }

            private IllegalArgumentException invalidValue(String value) {
                throw new IllegalArgumentException("Invalid size of '" + value + "' specified for the '" + optionName + "' option. " //
                                + "A valid size consists of a positive integer value and a byte-based size unit. " //
                                + "For example '512KB' or '100MB'. Valid size units are " //
                                + "'B' for bytes, " //
                                + "'KB' for kilobytes, " //
                                + "'MB' for megabytes, and " //
                                + "'GB' for gigabytes ");
            }

            private IllegalArgumentException invalidRange(String value) {
                throw new IllegalArgumentException("Invalid size of '" + value + "' specified for the '" + optionName + "' option. " //
                                + String.format("Valid size must be greater or equal to %s.", toUnitString(min)));
            }

            private String toUnitString(long value) {
                String suffix = "B";
                long scaledValue = value;
                if (value > SizeUnit.GIGABYTE.factor && value % SizeUnit.GIGABYTE.factor == 0) {
                    suffix = SizeUnit.GIGABYTE.symbol;
                    scaledValue /= SizeUnit.GIGABYTE.factor;
                } else if (value > SizeUnit.MEGABYTE.factor && value % SizeUnit.MEGABYTE.factor == 0) {
                    suffix = SizeUnit.MEGABYTE.symbol;
                    scaledValue /= SizeUnit.MEGABYTE.factor;
                } else if (value > SizeUnit.KILOBYTE.factor && value % SizeUnit.KILOBYTE.factor == 0) {
                    suffix = SizeUnit.KILOBYTE.symbol;
                    scaledValue /= SizeUnit.KILOBYTE.factor;
                }
                return scaledValue + suffix;
            }
        });
    }
}
