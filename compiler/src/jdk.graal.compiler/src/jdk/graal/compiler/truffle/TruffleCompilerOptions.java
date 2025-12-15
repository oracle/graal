/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.truffle;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.compiler.TruffleCompilerOptionDescriptor;
import com.oracle.truffle.compiler.TruffleCompilerOptionDescriptor.Type;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.java.BytecodeParserOptions;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.options.OptionsContainer;
import jdk.graal.compiler.options.OptionsParser;

/*
 * Do not refer to any compiler classes here to guarantee lazy class loading.
 */
public class TruffleCompilerOptions implements OptionsContainer {
    @Override
    public boolean optionsAreDiscoverable() {
        return false;
    }

    @Override
    public String getNamePrefix() {
        return "compiler.";
    }

    //@formatter:off

    public enum CompilationTier {
        lowTier,
        peTier,
        truffleTier;

        static CompilationTier parse(String name) {
            try {
                return CompilationTier.valueOf(name);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(String.format("Unknown tier option value '%s'. %s", name, EXPANSION_VALUES));
            }
        }
    }
    public record PerformanceWarnings(Set<PerformanceWarningKind> kinds) {

        public static PerformanceWarnings defaultValue() {
            return new PerformanceWarnings(Set.of());
        }

        public static PerformanceWarnings parse(String value) {
            return new PerformanceWarnings(parseImpl(value));
        }

        private static Set<PerformanceWarningKind> parseImpl(String value) {
            if ("none".equals(value)) {
                return EnumSet.noneOf(PerformanceWarningKind.class);
            } else if ("all".equals(value)) {
                Set<PerformanceWarningKind> result = EnumSet.allOf(PerformanceWarningKind.class);
                result.removeIf(PerformanceWarningKind::isOptional);
                return result;
            } else {
                Set<PerformanceWarningKind> result = EnumSet.noneOf(PerformanceWarningKind.class);
                for (String name : value.split(",")) {
                    if ("bailout".equals(name)) {
                        /*
                         * The PerformanceWarningKind.BAILOUT was removed but 'bailout' can still
                         * appear in option value due to backward compatibility. We need to ignore
                         * the 'bailout' option value.
                         */
                        continue;
                    }
                    try {
                        result.add(PerformanceWarningKind.forName(name));
                    } catch (IllegalArgumentException e) {
                        String message = String.format("The \"%s\" is not a valid performance warning kind. Valid values are%n", name);
                        for (PerformanceWarningKind kind : PerformanceWarningKind.values()) {
                            message = message + String.format("%s%s%s%n", kind.name, indent(kind.name.length()), kind.help);
                        }
                        message = message + String.format("all%sEnables all performance warnings%n", indent(3));
                        message = message + String.format("none%sDisables performance warnings%n", indent(4));
                        throw new IllegalArgumentException(message);
                    }
                }
                return result;
            }
        }

    }

    public record CompilationTiers(Set<CompilationTier> tiers) {

        public static CompilationTiers defaultValue() {
            return new CompilationTiers(Set.of());
        }

        public static CompilationTiers parse(String s) {
            return new CompilationTiers(parseImpl(s));
        }

        private static Set<CompilationTier> parseImpl(String s) {
            if (s.equals("true")) {
                return Collections.singleton(CompilationTier.truffleTier);
            } else if (s.equals("false")) {
                return Collections.emptySet();
            }
            String[] strings = s.split(",");
            EnumSet<CompilationTier> tiers = EnumSet.noneOf(CompilationTier.class);
            for(String string: strings){
                tiers.add(CompilationTier.parse(string));
            }
            return Collections.unmodifiableSet(tiers);
        }

    }

    public enum PerformanceWarningKind {
        FRAME_INCOMPATIBLE_MERGE("frame_merge", "Enables warnings about deopts inserted for incompatible frame slot merges"),
        // keep optional until all warnings in downstream are resolved
        MISSING_LOOP_FREQUENCY_INFO("loop", "Enables missing loop frequency warnings", true),
        TRIVIAL_FAIL("trivial", "Enables trivial fail warnings"),
        VIRTUAL_INSTANCEOF("instanceof", "Enables virtual instanceof warnings"),
        VIRTUAL_RUNTIME_CALL("call", "Enables virtual call warnings"),
        VIRTUAL_STORE("store", "Enables virtual store warnings");

        private static final EconomicMap<String, PerformanceWarningKind> kindByName;
        static {
            kindByName = EconomicMap.create();
            for (PerformanceWarningKind kind : PerformanceWarningKind.values()) {
                kindByName.put(kind.name, kind);
            }
        }

        public static PerformanceWarningKind forName(String name) {
            PerformanceWarningKind kind = kindByName.get(name);
            if (kind == null) {
                throw new IllegalArgumentException("Unknown PerformanceWarningKind name " + name);
            }
            return kind;
        }
        final String help;
        final boolean isOptional;

        final String name;

        PerformanceWarningKind(String name, String help) {
            this(name, help, false);
        }

        PerformanceWarningKind(String name, String help, boolean isOptional) {
            this.name = name;
            this.help = help;
            this.isOptional = isOptional;
        }

        boolean isOptional() {
            return isOptional;
        }
    }

    @Option(help = "Forces diagnostics for compilation failures (default: false).") //
    public static final OptionKey<Boolean> DiagnoseFailure = new OptionKey<>(false);

    @Option(help = "Cache encoded graphs across Truffle compilations to speed up partial evaluation. (default: true).", type = OptionType.Expert) //
    public static final OptionKey<Boolean> EncodedGraphCache = new OptionKey<>(true);

    @Option(help = "Exclude assertion code from Truffle compilations (default: true)",type = OptionType.Debug) //
    public static final OptionKey<Boolean> ExcludeAssertions = new OptionKey<>(true);

    @Option(help = "Explicitly pick a first tier inlining policy by name (None, TrivialOnly). If empty (default) the lowest priority policy (TrivialOnly) is chosen.", type = OptionType.Debug) //
    public static final OptionKey<String> FirstTierInliningPolicy = new OptionKey<>("");

    @Option(help = "Whether to use the economy configuration in the first-tier compilations. (default: true, syntax: true|false)", type = OptionType.Expert) //
    public static final OptionKey<Boolean> FirstTierUseEconomy = new OptionKey<>(true);

    @Option(help = "Enable inlining across Truffle boundary", type = OptionType.Debug) //
    public static final OptionKey<Boolean> InlineAcrossTruffleBoundary = new OptionKey<>(false);

    @Option(help = "Restrict inlined methods to ','-separated list of includes (or excludes prefixed with '~'). No restriction by default. (usage: <name>,<name>,...)", type = OptionType.Debug) //
    public static final OptionKey<String> InlineOnly = new OptionKey<>(null);

    @Option(help = "Enable automatic inlining of guest language call targets (default: true, usage: true|false).", type = OptionType.Expert) //
    public static final OptionKey<Boolean> Inlining = new OptionKey<>(true);

    @Option(help = "The base expansion budget for language-agnostic inlining (default: 12000). Syntax: [1, inf)", type = OptionType.Expert) //
    public static final OptionKey<Integer> InliningExpansionBudget = new OptionKey<>(12_000);

    @Option(help = "The base inlining budget for language-agnostic inlining (default: 12000). Syntax: [1, inf)", type = OptionType.Expert) //
    public static final OptionKey<Integer> InliningInliningBudget = new OptionKey<>(12_000);

    @Option(help = "Explicitly pick a inlining policy by name. If empty (default) the highest priority chosen by default.",  type = OptionType.Debug) //
    public static final OptionKey<String> InliningPolicy = new OptionKey<>("");

    @Option(help = "Maximum depth for recursive inlining (default: 2, usage: [0, inf)).", type = OptionType.Expert) //
    public static final OptionKey<Integer> InliningRecursionDepth = new OptionKey<>(2);

    @Option(help = "Use the graph size as a cost model during inlining (default: false).", type = OptionType.Debug) //
    public static final OptionKey<Boolean> InliningUseSize = new OptionKey<>(false);

    @Option(help = "Maximum number of instrumentation counters available (default: 10000, syntax: [1, inf))", type = OptionType.Debug) //
    public static final OptionKey<Integer> InstrumentationTableSize = new OptionKey<>(10000);

    @Option(help = "Instrument Truffle boundaries and output profiling information to the standard output.", type = OptionType.Debug) //
    public static final OptionKey<Boolean> InstrumentBoundaries = new OptionKey<>(false);

    @Option(help = "Instrument Truffle boundaries by considering different inlining sites as different branches.", type = OptionType.Debug) //
    public static final OptionKey<Boolean> InstrumentBoundariesPerInlineSite = new OptionKey<>(false);
    @Option(help = "Instrument branches and output profiling information to the standard output.", type = OptionType.Debug) //
    public static final OptionKey<Boolean> InstrumentBranches = new OptionKey<>(false);

    @Option(help = "Instrument branches by considering different inlining sites as different branches.", type = OptionType.Debug) //
    public static final OptionKey<Boolean> InstrumentBranchesPerInlineSite = new OptionKey<>(false);

    @Option(help = "Method filter for host methods in which to add instrumentation (syntax: <method>,<method>,....)",  type = OptionType.Debug) //
    public static final OptionKey<String> InstrumentFilter = new OptionKey<>("*.*.*");

    @Option(help = "Run the partial escape analysis iteratively in Truffle compilation.", type = OptionType.Debug) //
    public static final OptionKey<Boolean> IterativePartialEscape = new OptionKey<>(false);

    @Option(help = "Time limit in seconds before a compilation expires and throws a bailout (0 to disable the limit). ", type = OptionType.Debug) //
    public static final OptionKey<Double> CompilationTimeout = new OptionKey<>(100D);

    @Option(help = "Logs inlined targets for statistical purposes (default: false).") //
    public static final OptionKey<Boolean> LogInlinedTargets = new OptionKey<>(false);

    @Option(help = "Stop partial evaluation when the graph exceeded this size, disabled if < 0. (default: -1, syntax: [-inf, inf))", type = OptionType.Debug) //
    public static final OptionKey<Integer> MaximumGraalGraphSize = new OptionKey<>(-1);

    
    private static final String EXPANSION_SYNTAX = "(syntax: true|false|peTier|truffleTier|lowTier|<tier>,<tier>,...)";

    private static final String EXPANSION_VALUES = "Accepted values are:%n" +
    "    true - Collect data for the default tier 'truffleTier'.%n" +
    "    false - No data will be collected.%n" +
    "Or one or multiple tiers separated by comma (e.g. truffleTier,lowTier):%n" +
    "    peTier - After partial evaluation without additional phases applied.%n" +
    "    truffleTier - After partial evaluation with additional phases applied.%n" +
    "    lowTier - After low tier phases were applied.";

    @Option(help = "Print statistics on expanded Java methods during partial evaluation at the end of a run." + EXPANSION_SYNTAX + "%n" +  EXPANSION_VALUES, type = OptionType.Debug) //
    public static final OptionKey<CompilationTiers> MethodExpansionStatistics = new OptionKey<>(CompilationTiers.defaultValue());

    @Option(help = "Print statistics on expanded Truffle nodes during partial evaluation at the end of a run." +  EXPANSION_SYNTAX + "%n" + EXPANSION_VALUES, type = OptionType.Debug) //
    public static final OptionKey<CompilationTiers> NodeExpansionStatistics = new OptionKey<>(CompilationTiers.defaultValue());

    @Option(help = "Enable node source positions in truffle partial evaluations.", type = OptionType.Debug) //
    public static final OptionKey<Boolean> NodeSourcePositions = new OptionKey<>(false);

    @Option(help = "Threshold for enabling deopt cycle detection for a call target. When the number of successful compilation of the call target reaches the threshold, " + //
            "deopt cycle detection is enabled for the call target. (negative integer means the detection is never enabled, default: 15)")
    public static final OptionKey<Integer> DeoptCycleDetectionThreshold = new OptionKey<>(15);

    @Option(help = "Maximum allowed repeats of the same compiled code for the same compilable. " + //
            "Works only if the detection of repeated compilation is enabled after DeoptCycleDetectionThreshold has been reached for the compilable. (negative integer means 0, default: 0)", type = OptionType.Debug) //
    public static final OptionKey<Integer> DeoptCycleDetectionAllowedRepeats = new OptionKey<>(0);

    @Option(help = "Allow assumptions during parsing of seed graphs for partial evaluation. Disables the persistent encoded graph cache 'engine.EncodedGraphCache'. (default: false).", type = OptionType.Debug) //
    public static final OptionKey<Boolean> ParsePEGraphsWithAssumptions = new OptionKey<>(false);

    @Option(help = "Print information for inlining decisions.", type = OptionType.Debug) //
    public static final OptionKey<Boolean> TraceInlining = new OptionKey<>(false);

    @Option(help = "Print detailed information for inlining (i.e. the entire explored call tree).", type = OptionType.Debug) //
    public static final OptionKey<Boolean> TraceInliningDetails = new OptionKey<>(false);

    @Option(help = "Print a tree of all expanded Java methods with statistics after each compilation. " + EXPANSION_SYNTAX + "%n" + EXPANSION_VALUES, type = OptionType.Debug) //
    public static final OptionKey<CompilationTiers> TraceMethodExpansion = new OptionKey<>(CompilationTiers.defaultValue());


    //@formatter:on

    @Option(help = "Print a tree of all expanded Truffle nodes with statistics after each compilation. " + EXPANSION_SYNTAX + "%n" + EXPANSION_VALUES, type = OptionType.Debug) //
    public static final OptionKey<CompilationTiers> TraceNodeExpansion = new OptionKey<>(CompilationTiers.defaultValue());

    private static final String PERFORMANCE_WARNING_LIST = "Performance warnings are: call, instanceof, store, frame_merge, trivial.";

    private static final String PERFORMANCE_WARNING_SYNTAX = " (syntax: none|all|<perfWarning>,<perfWarning>,...)";

    @Option(help = "Print potential performance problems, " + PERFORMANCE_WARNING_LIST + PERFORMANCE_WARNING_SYNTAX, type = OptionType.Debug) //
    public static final OptionKey<PerformanceWarnings> TracePerformanceWarnings = new OptionKey<>(PerformanceWarnings.defaultValue());

    @Option(help = "Number of stack trace elements printed by TraceTruffleTransferToInterpreter, TraceTruffleAssumptions and TraceDeoptimizeFrame (default: 20). Syntax: [1, inf).", type = OptionType.Debug) //
    public static final OptionKey<Integer> TraceStackTraceLimit = new OptionKey<>(20);

    @Option(help = "Treat performance warnings as error. Handling of the error depends on the CompilationFailureAction option value. " +
                    PERFORMANCE_WARNING_LIST + PERFORMANCE_WARNING_SYNTAX, type = OptionType.Debug) //
    public static final OptionKey<PerformanceWarnings> TreatPerformanceWarningsAsErrors = new OptionKey<>(PerformanceWarnings.defaultValue());

    private static OptionValues enableNodeSourcePositions(OptionValues values) {
        if (GraalOptions.TrackNodeSourcePosition.getValue(values)) {
            // already enabled nothing to do
            return values;
        } else {
            return new OptionValues(values, GraalOptions.TrackNodeSourcePosition, Boolean.TRUE);
        }
    }

    public static boolean optionExists(String key) {
        return TruffleCompilerImpl.OPTION_DESCRIPTORS.get(key) != null;
    }

    private static String indent(int nameLength) {
        int len = Math.max(1, 16 - nameLength);
        return new String(new char[len]).replace('\0', ' ');
    }

    public static TruffleCompilerOptionDescriptor[] listOptions() {
        TruffleCompilerOptionDescriptor[] convertedDescriptors = new TruffleCompilerOptionDescriptor[TruffleCompilerImpl.OPTION_DESCRIPTORS.size()];
        int i = 0;
        for (OptionDescriptor descriptor : TruffleCompilerImpl.OPTION_DESCRIPTORS.getValues()) {
            convertedDescriptors[i++] = createCompilerOptionDescriptor(descriptor);
        }
        return convertedDescriptors;
    }

    private static TruffleCompilerOptionDescriptor createCompilerOptionDescriptor(OptionDescriptor d) {
        return new TruffleCompilerOptionDescriptor(d.getName(), matchGraalOptionType(d), d.isDeprecated(), d.getHelp().getFirst(), d.getDeprecationMessage());
    }

    private static Type matchGraalOptionType(OptionDescriptor d) {
        return switch (d.getOptionType()) {
            case User -> Type.USER;
            case Expert -> Type.EXPERT;
            case Debug -> Type.DEBUG;
        };
    }

    static Object parseCustom(OptionDescriptor descriptor, String uncheckedValue) {
        Class<?> type = descriptor.getOptionValueType();
        if (type == PerformanceWarnings.class) {
            return PerformanceWarnings.parse(uncheckedValue);
        } else if (type == CompilationTiers.class) {
            return CompilationTiers.parse(uncheckedValue);
        }
        return null;
    }

    static OptionValues updateValues(OptionValues graalOptions, EconomicMap<OptionKey<?>, Object> parsedTruffleOptions) {
        OptionValues options = graalOptions;
        if (ExpansionStatistics.isEnabled(options)) {
            options = enableNodeSourcePositions(options);
        }
        /*
         * Truffle runtime compiles never want explicit exception edges for out of memory errors
         * except requested. The reason for this is that we cannot foresee what happens to partial
         * evaluation results and performance if interpreter basic blocks are suddenly disabled for
         * PEA.
         */
        options = new OptionValues(options, BytecodeParserOptions.DoNotMoveAllocationsWithOOMEHandlers, false);

        EconomicMap<OptionKey<?>, Object> extraPairs = OptionValues.asMap(BytecodeParserOptions.DoNotMoveAllocationsWithOOMEHandlers, false);
        if (!CompilationAlarm.Options.CompilationExpirationPeriod.hasBeenSet(options)) {
            // Forward the truffle timeout to the compiler
            double compilationExpiration;
            if (parsedTruffleOptions.containsKey(CompilationTimeout)) {
                compilationExpiration = (double) parsedTruffleOptions.get(CompilationTimeout);
            } else {
                compilationExpiration = CompilationTimeout.getValue(options);
            }
            extraPairs.put(CompilationAlarm.Options.CompilationExpirationPeriod, compilationExpiration);
        }
        return new OptionValues(options, extraPairs);
    }

    public static String validateOption(String key, String uncheckedValue) {
        OptionDescriptor descriptor = TruffleCompilerImpl.OPTION_DESCRIPTORS.get(key);
        if (descriptor == null) {
            return "Option with key '" + key + "' not found.";
        }
        try {
            Object value = TruffleCompilerOptions.parseCustom(descriptor, uncheckedValue);
            if (value == null) {
                OptionsParser.parseOptionValue(descriptor, uncheckedValue);
            }
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    public static boolean maximumGraalGraphSizeEnabled(OptionValues options) {
        int val = MaximumGraalGraphSize.getValue(options);
        return val > 0;
    }
}
