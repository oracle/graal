/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Function;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
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

    @Option(category = OptionCategory.EXPERT, usageSyntax = "true|false|<group>[,<group>...]", stability = OptionStability.STABLE, help = "" + //
                    "Collect and print a histogram of executed bytecode opcodes. " + //
                    "Set to 'true' to enable basic mode or use a comma separated list to configure grouping (e.g. source,root)." + //
                    "Available groupings are root, tier, source, language, thread." + //
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
                    "Provide a comma-separated list of includes, or excludes prefixed with '~'. " + //
                    "Empty means no restriction. " + //
                    "Whitespace around commas is ignored. " + //
                    "Applies to engine.TraceBytecode and engine.BytecodeHistogram.") //
    public static final OptionKey<String> BytecodeMethodFilter = new OptionKey<>("");

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "" + //
                    "Limit tracing and statistics to specific language IDs. " + //
                    "Provide a comma-separated list of language IDs, for example: js, python. " + //
                    "Empty means that all languages are included. " + //
                    "Applies to engine.TraceBytecode and engine.BytecodeHistogram.") //
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
}
