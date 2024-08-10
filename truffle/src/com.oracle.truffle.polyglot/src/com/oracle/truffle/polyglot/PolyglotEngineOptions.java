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

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "Traces thread local events and when they are processed on the individual threads." +
                    "Prints messages with the [engine] [tl] prefix. ")//
    static final OptionKey<Boolean> TraceThreadLocalActions = new OptionKey<>(false);

    @Option(category = OptionCategory.INTERNAL, stability = OptionStability.EXPERIMENTAL, help = "" +
                    "Repeadly submits thread local actions and collects statistics about safepoint intervals in the process. " +
                    "Prints event and interval statistics when the context is closed for each thread. " +
                    "This option significantly slows down execution and is therefore intended for testing purposes only.")//
    static final OptionKey<Boolean> SafepointALot = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL, help = "" +
                    "Prints the stack trace for all threads for a time interval. By default 0, which disables the output.", usageSyntax = "[1, inf)")//
    static final OptionKey<Long> TraceStackTraceInterval = new OptionKey<>(0L);

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "" +
                    "Print warning when the engine is using a default Truffle runtime (default: true).", usageSyntax = "true|false", sandbox = SandboxPolicy.UNTRUSTED)//
    static final OptionKey<Boolean> WarnInterpreterOnly = new OptionKey<>(true);

    @Option(category = OptionCategory.USER, stability = OptionStability.STABLE, help = "" +
                    "Print warning when a deprecated option is used (default: true).", usageSyntax = "true|false", sandbox = SandboxPolicy.UNTRUSTED)//
    static final OptionKey<Boolean> WarnOptionDeprecation = new OptionKey<>(true);

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

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Print information for source cache misses/evictions/failures.")//
    static final OptionKey<Boolean> TraceSourceCache = new OptionKey<>(false);

    @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Print information for all source cache events including hits and uncached misses.")//
    static final OptionKey<Boolean> TraceSourceCacheDetails = new OptionKey<>(false);

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
}
