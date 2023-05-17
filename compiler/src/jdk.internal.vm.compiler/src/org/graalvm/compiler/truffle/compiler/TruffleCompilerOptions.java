/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionGroup;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptionDescriptor;

/*
 * Do not refer to any compiler classes here to guarantee lazy class loading.
 */
@OptionGroup(prefix = "compiler.", registerAsService = false)
public class TruffleCompilerOptions {

    //@formatter:off

    private static final String PERFORMANCE_WARNING_SYNTAX = " (syntax: none|all|<perfWarning>,<perfWarning>,...)";
    private static final String PERFORMANCE_WARNING_LIST = "Performance warnings are: call, instanceof, store, frame_merge, trivial.";

    @Option(help = "Whether to use the economy configuration in the first-tier compilations. (default: true, syntax: true|false)", type = OptionType.Expert) //
    public static final OptionKey<Boolean> FirstTierUseEconomy = new OptionKey<>(true);

    @Option(help = "Treat performance warnings as error. Handling of the error depends on the CompilationFailureAction option value. " +
                    PERFORMANCE_WARNING_LIST + PERFORMANCE_WARNING_SYNTAX, type = OptionType.Debug) //
    public static final OptionKey<String> TreatPerformanceWarningsAsErrors = new OptionKey<>("");

    @Option(help = "Print information for inlining decisions.", type = OptionType.Debug) //
    public static final OptionKey<Boolean> TraceInlining = new OptionKey<>(false);

    @Option(help = "Print stack trace when deoptimizing a frame from the stack with `FrameInstance#getFrame(READ_WRITE|MATERIALIZE)`.", type = OptionType.Debug) //
    public static final OptionKey<Boolean> TraceDeoptimizeFrame = new OptionKey<>(false);

    private static final String EXPANSION_VALUES = "Accepted values are:%n" +
                    "    true - Collect data for the default tier 'truffleTier'.%n" +
                    "    false - No data will be collected.%n" +
                    "Or one or multiple tiers separated by comma (e.g. truffleTier,lowTier):%n" +
                    "    peTier - After partial evaluation without additional phases applied.%n" +
                    "    truffleTier - After partial evaluation with additional phases applied.%n" +
                    "    lowTier - After low tier phases were applied.";

    private static final String EXPANSION_SYNTAX = "(syntax: true|false|peTier|truffleTier|lowTier|<tier>,<tier>,...)";

    @Option(help = "Print a tree of all expanded Java methods with statistics after each compilation. " + EXPANSION_SYNTAX + "%n" + EXPANSION_VALUES, type = OptionType.Debug) //
    public static final OptionKey<CompilationTiers> TraceMethodExpansion = new OptionKey<>(CompilationTiers.defaultValue());

    @Option(help = "Print a tree of all expanded Truffle nodes with statistics after each compilation. " +  EXPANSION_SYNTAX + "%n" + EXPANSION_VALUES, type = OptionType.Debug) //
    public static final OptionKey<CompilationTiers> TraceNodeExpansion = new OptionKey<>(CompilationTiers.defaultValue());

    @Option(help = "Print statistics on expanded Java methods during partial evaluation at the end of a run." + EXPANSION_SYNTAX + "%n" +  EXPANSION_VALUES, type = OptionType.Debug) //
    public static final OptionKey<CompilationTiers> MethodExpansionStatistics = new OptionKey<>(CompilationTiers.defaultValue());

    @Option(help = "Print statistics on expanded Truffle nodes during partial evaluation at the end of a run." +  EXPANSION_SYNTAX + "%n" + EXPANSION_VALUES, type = OptionType.Debug) //
    public static final OptionKey<CompilationTiers> NodeExpansionStatistics = new OptionKey<>(CompilationTiers.defaultValue());

    @Option(help = "Restrict inlined methods to ','-separated list of includes (or excludes prefixed with '~'). No restriction by default. (usage: <name>,<name>,...)", type = OptionType.Debug) //
    public static final OptionKey<String> InlineOnly = new OptionKey<>(null);

    @Option(help = "Enable automatic inlining of guest language call targets (default: true, usage: true|false).", type = OptionType.Expert) //
    public static final OptionKey<Boolean> Inlining = new OptionKey<>(true);

    @Option(help = "Maximum depth for recursive inlining (default: 2, usage: [0, inf)).", type = OptionType.Expert) //
    public static final OptionKey<Integer> InliningRecursionDepth = new OptionKey<>(2);

    @Option(help = "Enable inlining across Truffle boundary", type = OptionType.Debug) //
    public static final OptionKey<Boolean> InlineAcrossTruffleBoundary = new OptionKey<>(false);

    @Option(help = "Print potential performance problems, " + PERFORMANCE_WARNING_LIST + PERFORMANCE_WARNING_SYNTAX,  type = OptionType.Debug) //
    public static final OptionKey<PerformanceWarnings> TracePerformanceWarnings = new OptionKey<>(PerformanceWarnings.defaultValue());

    @Option(help = "Run the partial escape analysis iteratively in Truffle compilation.", type = OptionType.Debug) //
    public static final OptionKey<Boolean> IterativePartialEscape = new OptionKey<>(false);

    @Option(help = "Method filter for host methods in which to add instrumentation (syntax: <method>,<method>,....)",  type = OptionType.Debug) //
    public static final OptionKey<String> InstrumentFilter = new OptionKey<>("*.*.*");

    @Option(help = "Maximum number of instrumentation counters available (default: 10000, syntax: [1, inf))", type = OptionType.Debug) //
    public static final OptionKey<Integer> InstrumentationTableSize = new OptionKey<>(10000);

    @Option(help = "Stop partial evaluation when the graph exceeded this size (default: 150000, syntax: [1, inf))", type = OptionType.Debug) //
    public static final OptionKey<Integer> MaximumGraalGraphSize = new OptionKey<>(150_000);

    // TODO delete or deprecate?
    @Option(help = "Ignore further truffle inlining decisions when the graph exceeded this many nodes (default: 150000,  syntax: [1, inf))", type = OptionType.Debug) //
    public static final OptionKey<Integer> MaximumInlineNodeCount = new OptionKey<>(150000);

    @Option(help = "Exclude assertion code from Truffle compilations (default: true)",type = OptionType.Debug) //
    public static final OptionKey<Boolean> ExcludeAssertions = new OptionKey<>(true);

    @Option(help = "Enable node source positions in truffle partial evaluations.", type = OptionType.Debug) //
    public static final OptionKey<Boolean> NodeSourcePositions = new OptionKey<>(false);
    @Option(help = "Instrument Truffle boundaries and output profiling information to the standard output.", type = OptionType.Debug) //
    public static final OptionKey<Boolean> InstrumentBoundaries = new OptionKey<>(false);

    @Option(help = "Instrument Truffle boundaries by considering different inlining sites as different branches.", type = OptionType.Debug) //
    public static final OptionKey<Boolean> InstrumentBoundariesPerInlineSite = new OptionKey<>(false);

    @Option(help = "Instrument branches and output profiling information to the standard output.", type = OptionType.Debug) //
    public static final OptionKey<Boolean> InstrumentBranches = new OptionKey<>(false);

    @Option(help = "Instrument branches by considering different inlining sites as different branches.", type = OptionType.Debug) //
    public static final OptionKey<Boolean> InstrumentBranchesPerInlineSite = new OptionKey<>(false);

    @Option(help = "Delay, in milliseconds, after which the encoded graph cache is dropped when a Truffle compiler thread becomes idle (default: 10000).", //
                   type = OptionType.Expert) //
    public static final OptionKey<Integer> EncodedGraphCachePurgeDelay = new OptionKey<>(10_000);

    @Option(help = "Cache encoded graphs across Truffle compilations to speed up partial evaluation. (default: true).", type = OptionType.Expert) //
    public static final OptionKey<Boolean> EncodedGraphCache = new OptionKey<>(true);

    @Option(help = "Print detailed information for inlining (i.e. the entire explored call tree).", type = OptionType.Debug) //
    public static final OptionKey<Boolean> TraceInliningDetails = new OptionKey<>(false);

    @Option(help = "Explicitly pick a inlining policy by name. If empty (default) the highest priority chosen by default.",  type = OptionType.Debug) //
    public static final OptionKey<String> InliningPolicy = new OptionKey<>("");

    @Option(help = "The base expansion budget for language-agnostic inlining (default: 12000). Syntax: [1, inf)", type = OptionType.Expert) //
    public static final OptionKey<Integer> InliningExpansionBudget = new OptionKey<>(12_000);

    @Option(help = "The base inlining budget for language-agnostic inlining (default: 12000). Syntax: [1, inf)", type = OptionType.Expert) //
    public static final OptionKey<Integer> InliningInliningBudget = new OptionKey<>(12_000);

    @Option(help = "Use the graph size as a cost model during inlining (default: false).", type = OptionType.Debug) //
    public static final OptionKey<Boolean> InliningUseSize = new OptionKey<>(false);

    //@formatter:on

    public record PerformanceWarnings(Set<PerformanceWarningKind> tiers) {

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
            for (int i = 0; i < strings.length; i++) {
                tiers.add(CompilationTier.parse(strings[i]));
            }
            return Collections.unmodifiableSet(tiers);
        }

    }

    private static String indent(int nameLength) {
        int len = Math.max(1, 16 - nameLength);
        return new String(new char[len]).replace('\0', ' ');
    }

    public enum PerformanceWarningKind {
        VIRTUAL_RUNTIME_CALL("call", "Enables virtual call warnings"),
        VIRTUAL_INSTANCEOF("instanceof", "Enables virtual instanceof warnings"),
        VIRTUAL_STORE("store", "Enables virtual store warnings"),
        FRAME_INCOMPATIBLE_MERGE("frame_merge", "Enables warnings about deopts inserted for incompatible frame slot merges"),
        TRIVIAL_FAIL("trivial", "Enables trivial fail warnings"),
        // keep optional until all warnings in downstream are resolved
        MISSING_LOOP_FREQUENCY_INFO("loop", "Enables missing loop frequency warnings", true);

        private static final EconomicMap<String, PerformanceWarningKind> kindByName;
        static {
            kindByName = EconomicMap.create();
            for (PerformanceWarningKind kind : PerformanceWarningKind.values()) {
                kindByName.put(kind.name, kind);
            }
        }

        final String name;
        final String help;
        final boolean isOptional;

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

        public static PerformanceWarningKind forName(String name) {
            PerformanceWarningKind kind = kindByName.get(name);
            if (kind == null) {
                throw new IllegalArgumentException("Unknown PerformanceWarningKind name " + name);
            }
            return kind;
        }
    }

    public enum CompilationTier {
        peTier,
        truffleTier,
        lowTier;

        static CompilationTier parse(String name) {
            try {
                return CompilationTier.valueOf(name);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(String.format("Unknown tier option value '%s'. %s", name, EXPANSION_VALUES));
            }
        }
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

    public static boolean existsOption(String key) {
        return TruffleCompilerImpl.OPTION_DESCRIPTORS.get(key) != null;
    }

    public static String validateOption(String key, String uncheckedValue) {
        org.graalvm.compiler.options.OptionDescriptor descriptor = TruffleCompilerImpl.OPTION_DESCRIPTORS.get(key);
        if (descriptor == null) {
            return "Option with key '" + key + "' not found.";
        }
        try {
            Object value = TruffleCompilerOptions.parseCustom(descriptor, uncheckedValue);
            if (value == null) {
                value = OptionsParser.parseOptionValue(descriptor, uncheckedValue);
            }
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    public static TruffleCompilerOptionDescriptor[] listOptions() {
        List<TruffleCompilerOptionDescriptor> convertedDescriptors = new ArrayList<>();

        for (org.graalvm.compiler.options.OptionDescriptor descriptor : TruffleCompilerImpl.OPTION_DESCRIPTORS) {
            convertedDescriptors.add(new TruffleCompilerOptionDescriptor(descriptor));
        }
        return convertedDescriptors.toArray(new TruffleCompilerOptionDescriptor[convertedDescriptors.size()]);
    }

}
