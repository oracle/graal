/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.profiler.impl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.CPUSamplerData;
import com.oracle.truffle.tools.profiler.ProfilerNode;
import org.graalvm.shadowed.org.json.JSONArray;
import org.graalvm.shadowed.org.json.JSONObject;

@Option.Group(CPUSamplerInstrument.ID)
class CPUSamplerCLI extends ProfilerCLI {

    public static final long MILLIS_TO_NANOS = 1_000_000L;
    public static final double MAX_OVERHEAD_WARNING_THRESHOLD = 0.2;
    public static final String DEFAULT_FLAMEGRAPH_FILE = "flamegraph.svg";

    enum Output {
        HISTOGRAM,
        CALLTREE,
        JSON,
        FLAMEGRAPH;

        private static String valueList() {
            StringBuilder message = new StringBuilder();
            Output[] values = Output.values();
            for (int i = 0; i < values.length; i++) {
                Output value = values[i];
                message.append(value.name().toLowerCase());
                message.append(i < values.length - 1 ? ", " : "");
            }
            return message.toString();
        }

        private static Output fromString(String s) {
            try {
                return Output.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Output can be: " + Output.valueList() + ".");
            }
        }

        @Override
        public String toString() {
            return this.name().toLowerCase();
        }
    }

    static final OptionType<EnableOptionData> ENABLE_OPTION_TYPE = new OptionType<>("Enable",
                    s -> {
                        switch (s) {
                            case "":
                            case "true":
                                return new EnableOptionData(true, null);
                            case "false":
                                return new EnableOptionData(false, null);
                            default: {
                                try {
                                    Output output = Output.fromString(s);
                                    return new EnableOptionData(true, output);
                                } catch (IllegalArgumentException e) {
                                    throw new IllegalArgumentException("CPUSampler can be configured with the following values: true, false, " + Output.valueList() + ".");
                                }
                            }
                        }
                    });

    static final class EnableOptionData {
        final boolean enabled;
        final Output output;

        private EnableOptionData(boolean enabled, Output output) {
            this.enabled = enabled;
            this.output = output;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            EnableOptionData that = (EnableOptionData) o;
            return enabled == that.enabled && output == that.output;
        }

        @Override
        public int hashCode() {
            return Objects.hash(enabled, output);
        }

        @Override
        public String toString() {
            return enabled ? output.toString() : "false";
        }
    }

    static final OptionType<Output> CLI_OUTPUT_TYPE = new OptionType<>("Output", Output::fromString);

    static final OptionType<int[]> SHOW_TIERS_OUTPUT_TYPE = new OptionType<>("ShowTiers",
                    new Function<String, int[]>() {
                        @Override
                        public int[] apply(String s) {
                            if ("false".equals(s)) {
                                return null;
                            }
                            if ("true".equals(s)) {
                                return new int[0];
                            }
                            try {
                                String[] tierStrings = s.split(",");
                                int[] tiers = new int[tierStrings.length];
                                for (int i = 0; i < tierStrings.length; i++) {
                                    tiers[i] = Integer.parseInt(tierStrings[i]);
                                }
                                return tiers;
                            } catch (NumberFormatException e) {
                                // Ignored
                            }
                            throw new IllegalArgumentException("ShowTiers can be: true, false or a comma separated list of integers");
                        }
                    });

    @Option(name = "", help = "Enable/Disable the CPU sampler, or enable with specific Output - as specified by the Output option (default: false). Choosing an output with this options defaults to printing the output to std out, " +
                    "except for the flamegraph which is printed to a flamegraph.svg file.", usageSyntax = "true|false|<Output>", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<EnableOptionData> ENABLED = new OptionKey<>(new EnableOptionData(false, null), ENABLE_OPTION_TYPE);

    @Option(name = "Period", help = "Period in milliseconds to sample the stack (default: 10)", usageSyntax = "<ms>", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Long> SAMPLE_PERIOD = new OptionKey<>(10L);

    @Option(name = "Delay", help = "Delay the sampling for this many milliseconds (default: 0).", usageSyntax = "<ms>", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Long> DELAY_PERIOD = new OptionKey<>(0L);

    @Option(name = "StackLimit", help = "Maximum number of maximum stack elements (default: 10000).", usageSyntax = "[1, inf)", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Integer> STACK_LIMIT = new OptionKey<>(10000);

    @Option(name = "Output", help = "Specify the output format to one of: histogram, calltree, json or flamegraph (default: histogram).", //
                    usageSyntax = "histogram|calltree|json|flamegraph", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Output> OUTPUT = new OptionKey<>(Output.HISTOGRAM, CLI_OUTPUT_TYPE);

    @Option(help = "Specify whether to show compilation information for entries. You can specify 'true' to show all compilation information, 'false' for none, or a comma separated list of compilation tiers. " +
                    "Note: Interpreter is considered Tier 0. (default: false)", usageSyntax = "true|false|0,1,2", category = OptionCategory.EXPERT, stability = OptionStability.STABLE) //
    static final OptionKey<int[]> ShowTiers = new OptionKey<>(null, SHOW_TIERS_OUTPUT_TYPE);

    @Option(name = "FilterRootName", help = "Wildcard filter for program roots. (eg. Math.*) (default: no filter).", usageSyntax = "<filter>", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<WildcardFilter> FILTER_ROOT = new OptionKey<>(WildcardFilter.DEFAULT, WildcardFilter.WILDCARD_FILTER_TYPE);

    @Option(name = "FilterFile", help = "Wildcard filter for source file paths. (eg. *program*.sl) (default: no filter).", usageSyntax = "<filter>", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<WildcardFilter> FILTER_FILE = new OptionKey<>(WildcardFilter.DEFAULT, WildcardFilter.WILDCARD_FILTER_TYPE);

    @Option(name = "FilterMimeType", help = "Only profile the language with given mime-type. (eg. application/javascript) (default: profile all)", usageSyntax = "<mime-type>", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<String> FILTER_MIME_TYPE = new OptionKey<>("");

    @Option(name = "FilterLanguage", help = "Only profile the language with given ID. (eg. js) (default: profile all).", usageSyntax = "<languageId>", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<String> FILTER_LANGUAGE = new OptionKey<>("");

    @Option(name = "SampleInternal", help = "Capture internal elements.", category = OptionCategory.INTERNAL) //
    static final OptionKey<Boolean> SAMPLE_INTERNAL = new OptionKey<>(false);

    @Option(name = "SummariseThreads", help = "Print output as a summary of all 'per thread' profiles.", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Boolean> SUMMARISE_THREADS = new OptionKey<>(false);

    @Option(name = "GatherHitTimes", help = "Save a timestamp for each taken sample.", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Boolean> GATHER_HIT_TIMES = new OptionKey<>(false);

    @Option(name = "OutputFile", help = "Save output to the given file. Output is printed to output stream by default.", usageSyntax = "<path>", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<String> OUTPUT_FILE = new OptionKey<>("");

    @Option(name = "MinSamples", help = "Remove elements from output if they have less samples than this value (default: 0)", usageSyntax = "[0, inf)", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Integer> MIN_SAMPLES = new OptionKey<>(0);

    @Option(name = "SampleContextInitialization", help = "Enables sampling of code executed during context initialization", category = OptionCategory.EXPERT, stability = OptionStability.STABLE) //
    static final OptionKey<Boolean> SAMPLE_CONTEXT_INITIALIZATION = new OptionKey<>(false);

    static void handleOutput(TruffleInstrument.Env env, CPUSampler sampler) {
        PrintStream out = chooseOutputStream(env);
        List<CPUSamplerData> data = sampler.getDataList();
        OptionValues options = env.getOptions();
        switch (chooseOutput(options)) {
            case HISTOGRAM:
                printWarnings(sampler, out);
                printSamplingHistogram(out, options, data);
                break;
            case CALLTREE:
                printWarnings(sampler, out);
                printSamplingCallTree(out, options, data);
                break;
            case JSON:
                printSamplingJson(out, options, data);
                break;
            case FLAMEGRAPH:
                SVGSamplerOutput.printSamplingFlameGraph(out, data);
        }
    }

    private static PrintStream chooseOutputStream(TruffleInstrument.Env env) {
        OptionValues options = env.getOptions();
        final String outputPath = getOutputPath(env, options);
        if (outputPath != null) {
            try {
                final File file = new File(outputPath);
                new PrintStream(env.out()).println("Printing output to " + file.getAbsolutePath());
                return new PrintStream(new FileOutputStream(file));
            } catch (FileNotFoundException e) {
                throw handleFileNotFound();
            }
        }
        return new PrintStream(env.out());
    }

    private static String getOutputPath(TruffleInstrument.Env env, OptionValues options) {
        if (OUTPUT_FILE.hasBeenSet(options)) {
            return OUTPUT_FILE.getValue(env.getOptions());
        }
        if (ENABLED.getValue(options).output == Output.FLAMEGRAPH) {
            return DEFAULT_FLAMEGRAPH_FILE;
        }
        return null;
    }

    private static Output chooseOutput(OptionValues options) {
        if (OUTPUT.hasBeenSet(options)) {
            return options.get(OUTPUT);
        }
        EnableOptionData enabled = ENABLED.getValue(options);
        if (enabled.output != null) {
            return enabled.output;
        }
        return OUTPUT.getDefaultValue();
    }

    private static void printSamplingCallTree(PrintStream out, OptionValues options, List<CPUSamplerData> data) {
        for (CPUSamplerData entry : data) {
            new SamplingCallTree(entry, options).print(out);
        }
    }

    private static void printSamplingHistogram(PrintStream out, OptionValues options, List<CPUSamplerData> data) {
        for (CPUSamplerData entry : data) {
            new SamplingHistogram(entry, options).print(out);
        }
    }

    private static void printWarnings(CPUSampler sampler, PrintStream out) {
        if (sampler.hasStackOverflowed()) {
            printDiv(out);
            out.println("Warning: The stack has overflowed the sampled stack limit of " + sampler.getStackLimit() + " during execution!");
            out.println("         The printed data is incomplete or incorrect!");
            out.println("         Use --" + CPUSamplerInstrument.ID + ".StackLimit=<" + STACK_LIMIT.getType().getName() + "> to set the sampled stack limit.");
            printDiv(out);
        }
        if (sampleDurationTooLong(sampler)) {
            printDiv(out);
            out.println("Warning: Average sample duration took over 20% of the sampling period.");
            out.println("         An overhead above 20% can severely impact the reliability of the sampling data. Use one of these approaches to reduce the overhead:");
            out.println("         Use --" + CPUSamplerInstrument.ID + ".StackLimit=<" + STACK_LIMIT.getType().getName() + "> to reduce the number of frames sampled,");
            out.println("         or use --" + CPUSamplerInstrument.ID + ".Period=<" + SAMPLE_PERIOD.getType().getName() + "> to increase the sampling period.");
            printDiv(out);
        }
    }

    private static boolean sampleDurationTooLong(CPUSampler sampler) {
        for (CPUSamplerData value : sampler.getDataList()) {
            if (value.getSampleDuration().getAverage() > MAX_OVERHEAD_WARNING_THRESHOLD * sampler.getPeriod() * MILLIS_TO_NANOS) {
                return true;
            }
        }
        return false;
    }

    private static void printDiv(PrintStream out) {
        out.println("-------------------------------------------------------------------------------- ");
    }

    private static void printSamplingJson(PrintStream out, OptionValues options, List<CPUSamplerData> data) {
        boolean gatheredHitTimes = options.get(GATHER_HIT_TIMES);
        JSONObject output = new JSONObject();
        output.put("tool", CPUSamplerInstrument.ID);
        output.put("version", CPUSamplerInstrument.VERSION);
        JSONArray contexts = new JSONArray();
        for (CPUSamplerData samplerData : data) {
            contexts.put(perContextData(samplerData, gatheredHitTimes));
        }
        output.put("contexts", contexts);
        out.println(output);
    }

    private static JSONObject perContextData(CPUSamplerData samplerData, boolean gatheredHitTimes) {
        JSONObject output = new JSONObject();
        output.put("sample_count", samplerData.getSamples());
        output.put("period", samplerData.getSampleInterval());
        output.put("gathered_hit_times", gatheredHitTimes);
        JSONArray profile = new JSONArray();
        for (Map.Entry<Thread, Collection<ProfilerNode<CPUSampler.Payload>>> entry : samplerData.getThreadData().entrySet()) {
            JSONObject perThreadProfile = new JSONObject();
            perThreadProfile.put("thread", entry.getKey().toString());
            perThreadProfile.put("samples", getSamplesRec(entry.getValue()));
            profile.put(perThreadProfile);
        }
        output.put("profile", profile);
        return output;
    }

    private static JSONArray getSamplesRec(Collection<ProfilerNode<CPUSampler.Payload>> nodes) {
        JSONArray samples = new JSONArray();
        for (ProfilerNode<CPUSampler.Payload> node : nodes) {
            JSONObject sample = new JSONObject();
            sample.put("root_name", node.getRootName());
            sample.put("source_section", sourceSectionToJSON(node.getSourceSection()));
            CPUSampler.Payload payload = node.getPayload();
            sample.put("hit_count", payload.getHitCount());
            sample.put("self_hit_count", payload.getSelfHitCount());
            sample.put("self_hit_times", payload.getSelfHitTimes());
            int[] selfTierCount = new int[payload.getNumberOfTiers()];
            for (int i = 0; i < selfTierCount.length; i++) {
                selfTierCount[i] = payload.getTierSelfCount(i);
            }
            sample.put("self_tier_count", selfTierCount);
            int[] tierCount = new int[payload.getNumberOfTiers()];
            for (int i = 0; i < tierCount.length; i++) {
                tierCount[i] = payload.getTierSelfCount(i);
            }
            sample.put("tier_count", tierCount);
            sample.put("children", getSamplesRec(node.getChildren()));
            samples.put(sample);
        }
        return samples;
    }

    private static void printLegend(PrintStream out, String type, long samples, long period, long missed, int[] showTiers, Integer[] tiers) {
        out.printf("Sampling %s. Recorded %s samples with period %dms. Missed %s samples.%n", type, samples, period, missed);
        out.println("  Self Time: Time spent on the top of the stack.");
        out.println("  Total Time: Time spent somewhere on the stack.");
        if (showTiers == null) {
            return;
        }
        if (showTiers.length == 0) {
            for (int i : tiers) {
                out.println("  T" + i + ": Percent of time spent in " + (i == 0 ? "interpreter." : "code compiled by tier " + i + " compiler."));
            }
            return;
        }
        for (int tier : showTiers) {
            if (contains(tiers, tier)) {
                out.println("  T" + tier + ": Percent of time spent in " + (tier == 0 ? "interpreter." : "code compiled by tier " + tier + " compiler."));
            } else {
                out.println("  T" + tier + ": No samples of tier " + tier + " found during execution. It is excluded from the report.");
            }
        }
    }

    private static double percent(long samples, long totalSamples) {
        if (totalSamples == 0) {
            return 0.0;
        }
        return ((double) samples * 100) / totalSamples;
    }

    private static String[] makeTitleAndFormat(int nameLength, int[] showTiers, Integer[] tiers) {
        StringBuilder titleBuilder = new StringBuilder(format(" %-" + nameLength + "s ||             Total Time    ", "Name"));
        StringBuilder formatBuilder = new StringBuilder(" %-" + nameLength + "s ||       %10dms %5.1f%% ");
        maybeAddTiers(titleBuilder, formatBuilder, showTiers, tiers);
        titleBuilder.append("||              Self Time    ");
        formatBuilder.append("||       %10dms %5.1f%% ");
        maybeAddTiers(titleBuilder, formatBuilder, showTiers, tiers);
        titleBuilder.append("|| Location             ");
        formatBuilder.append("|| %s");
        String[] strings = new String[2];
        strings[0] = titleBuilder.toString();
        strings[1] = formatBuilder.toString();
        return strings;
    }

    private static void maybeAddTiers(StringBuilder titleBuilder, StringBuilder formatBuilder, int[] showTiers, Integer[] tiers) {
        if (showTiers == null) {
            return;
        }
        if (showTiers.length == 0) {
            for (Integer i : tiers) {
                titleBuilder.append("|   T").append(i).append("   ");
                formatBuilder.append("| %5.1f%% ");
            }
            return;
        }
        for (int i = 0; i < showTiers.length; i++) {
            int selectedTier = showTiers[i];
            if (contains(tiers, selectedTier)) {
                titleBuilder.append("|   T").append(selectedTier).append("   ");
                formatBuilder.append("| %5.1f%% ");
            }
        }
    }

    private static boolean contains(Integer[] tiers, int selectedTier) {
        for (Integer tier : tiers) {
            if (tier == selectedTier) {
                return true;
            }
        }
        return false;
    }

    private static Integer[] sortedArray(Set<Integer> tiers) {
        Integer[] sorted = tiers.toArray(new Integer[0]);
        Arrays.sort(sorted);
        return sorted;
    }

    private static final class SamplingHistogram {
        private final Map<Thread, List<OutputEntry>> histogram = new HashMap<>();
        private final boolean summariseThreads;
        private final int minSamples;
        private final int[] showTiers;
        private final long samplePeriod;
        private final long samplesTaken;
        private final long samplesMissed;
        private Set<Integer> tiers = new HashSet<>();
        private Integer[] sortedTiers;
        private int maxNameLength = 10;
        private final String title;
        private final String format;

        SamplingHistogram(CPUSamplerData data, OptionValues options) {
            this.summariseThreads = options.get(SUMMARISE_THREADS);
            this.minSamples = options.get(MIN_SAMPLES);
            this.showTiers = options.get(ShowTiers);
            this.samplePeriod = options.get(SAMPLE_PERIOD);
            this.samplesTaken = data.getSamples();
            Map<Thread, SourceLocationNodes> perThreadSourceLocationPayloads = new HashMap<>();
            this.samplesMissed = data.missedSamples();
            for (Thread thread : data.getThreadData().keySet()) {
                perThreadSourceLocationPayloads.put(thread, computeSourceLocationPayloads(data.getThreadData().get(thread)));
            }
            maybeSummarizeThreads(perThreadSourceLocationPayloads);
            for (Map.Entry<Thread, SourceLocationNodes> threadEntry : perThreadSourceLocationPayloads.entrySet()) {
                histogram.put(threadEntry.getKey(), histogramEntries(threadEntry));
            }
            sortedTiers = sortedArray(tiers);
            String[] titleAndFormat = makeTitleAndFormat(maxNameLength, showTiers, sortedTiers);
            this.title = titleAndFormat[0];
            this.format = titleAndFormat[1];
        }

        private ArrayList<OutputEntry> histogramEntries(Map.Entry<Thread, SourceLocationNodes> threadEntry) {
            ArrayList<OutputEntry> histogramEntries = new ArrayList<>();
            for (Map.Entry<SourceLocation, List<ProfilerNode<CPUSampler.Payload>>> sourceLocationEntry : threadEntry.getValue().locations.entrySet()) {
                histogramEntries.add(histogramEntry(sourceLocationEntry));
            }
            histogramEntries.sort((o1, o2) -> Integer.compare(o2.totalSelfSamples, o1.totalSelfSamples));
            return histogramEntries;
        }

        private OutputEntry histogramEntry(Map.Entry<SourceLocation, List<ProfilerNode<CPUSampler.Payload>>> sourceLocationEntry) {
            SourceLocation location = sourceLocationEntry.getKey();
            OutputEntry outputEntry = new OutputEntry(location);
            maxNameLength = Math.max(maxNameLength, location.getRootName().length());

            for (ProfilerNode<CPUSampler.Payload> node : sourceLocationEntry.getValue()) {
                CPUSampler.Payload payload = node.getPayload();

                int numberOfTiers = payload.getNumberOfTiers();
                if (outputEntry.tierToSelfSamples.length < numberOfTiers) {
                    outputEntry.tierToSelfSamples = Arrays.copyOf(outputEntry.tierToSelfSamples, numberOfTiers);
                }
                if (outputEntry.tierToSamples.length < numberOfTiers) {
                    outputEntry.tierToSamples = Arrays.copyOf(outputEntry.tierToSamples, numberOfTiers);
                }

                for (int i = 0; i < numberOfTiers; i++) {
                    int selfHitCountsValue = payload.getTierSelfCount(i);
                    outputEntry.totalSelfSamples += selfHitCountsValue;
                    outputEntry.tierToSelfSamples[i] += selfHitCountsValue;
                    tiers.add(i);

                    // if there is a recursive parent summed up for total time we must not sum up
                    // again
                    if (node.isRecursive()) {
                        continue;
                    }

                    int hitCountsValue = payload.getTierTotalCount(i);
                    outputEntry.totalSamples += hitCountsValue;
                    outputEntry.tierToSamples[i] += hitCountsValue;

                }
            }
            return outputEntry;
        }

        private void maybeSummarizeThreads(Map<Thread, SourceLocationNodes> perThreadSourceLocationPayloads) {
            if (summariseThreads) {
                SourceLocationNodes summary = new SourceLocationNodes(new HashMap<>());
                for (SourceLocationNodes sourceLocationNodes : perThreadSourceLocationPayloads.values()) {
                    for (Map.Entry<SourceLocation, List<ProfilerNode<CPUSampler.Payload>>> entry : sourceLocationNodes.locations.entrySet()) {
                        summary.locations.computeIfAbsent(entry.getKey(), s -> new ArrayList<>()).addAll(entry.getValue());
                    }
                }
                perThreadSourceLocationPayloads.clear();
                perThreadSourceLocationPayloads.put(new Thread("Summary"), summary);
            }
        }

        private static SourceLocationNodes computeSourceLocationPayloads(Collection<ProfilerNode<CPUSampler.Payload>> profilerNodes) {
            Map<SourceLocation, List<ProfilerNode<CPUSampler.Payload>>> histogram = new HashMap<>();
            computeSourceLocationPayloadsImpl(profilerNodes, histogram);
            return new SourceLocationNodes(histogram);
        }

        private static void computeSourceLocationPayloadsImpl(Collection<ProfilerNode<CPUSampler.Payload>> children, Map<SourceLocation, List<ProfilerNode<CPUSampler.Payload>>> histogram) {
            for (ProfilerNode<CPUSampler.Payload> treeNode : children) {
                List<ProfilerNode<CPUSampler.Payload>> nodes = histogram.computeIfAbsent(new SourceLocation(treeNode.getSourceSection(), treeNode.getRootName()), s -> new ArrayList<>());
                nodes.add(treeNode);
                computeSourceLocationPayloadsImpl(treeNode.getChildren(), histogram);
            }
        }

        void print(PrintStream out) {
            String sep = repeat("-", title.length());
            out.println(sep);
            printLegend(out, "Histogram", samplesTaken, samplePeriod, samplesMissed, showTiers, sortedTiers);
            out.println(sep);
            for (Map.Entry<Thread, List<OutputEntry>> threadListEntry : histogram.entrySet()) {
                out.println(threadListEntry.getKey());
                out.println(title);
                out.println(sep);
                for (OutputEntry entry : threadListEntry.getValue()) {
                    if (minSamples > 0 && entry.totalSelfSamples < minSamples) {
                        continue;
                    }
                    out.println(entry.format(format, showTiers, SamplingHistogram.this.samplePeriod, 0, samplesTaken, sortedTiers));
                }
                out.println(sep);
            }
        }

        private static final class SourceLocationNodes {
            final Map<SourceLocation, List<ProfilerNode<CPUSampler.Payload>>> locations;

            SourceLocationNodes(Map<SourceLocation, List<ProfilerNode<CPUSampler.Payload>>> locations) {
                this.locations = locations;
            }
        }
    }

    private static class OutputEntry {
        // break after 128 depth spaces to handle deep recursions
        private static final int DEPTH_BREAK = 128;
        final SourceLocation location;
        int[] tierToSamples = new int[0];
        int[] tierToSelfSamples = new int[0];
        int totalSelfSamples = 0;
        int totalSamples = 0;

        OutputEntry(SourceLocation location) {
            this.location = location;
        }

        OutputEntry(ProfilerNode<CPUSampler.Payload> node) {
            location = new SourceLocation(node.getSourceSection(), node.getRootName());
            CPUSampler.Payload payload = node.getPayload();
            this.totalSamples = payload.getHitCount();
            this.totalSelfSamples = payload.getSelfHitCount();
            this.tierToSamples = new int[payload.getNumberOfTiers()];
            for (int i = 0; i < tierToSamples.length; i++) {
                tierToSamples[i] = payload.getTierTotalCount(i);
            }
            this.tierToSelfSamples = new int[payload.getNumberOfTiers()];
            for (int i = 0; i < tierToSamples.length; i++) {
                tierToSelfSamples[i] = payload.getTierSelfCount(i);
            }
        }

        static int computeIndentSize(int depth) {
            int indent = depth % DEPTH_BREAK;
            if (indent != depth) {
                indent += formatIndentBreakLabel(depth - indent).length();
            }
            return indent;
        }

        private static String formatIndentBreakLabel(int skippedDepth) {
            return CPUSamplerCLI.format("(\u21B3%s) ", skippedDepth);
        }

        String format(String format, int[] showTiers, long samplePeriod, int depth, long globalTotalSamples, Integer[] tiers) {
            List<Object> args = new ArrayList<>();
            int indent = depth % DEPTH_BREAK;
            if (indent != depth) {
                args.add(formatIndentBreakLabel(depth - indent) + repeat(" ", indent) + location.getRootName());
            } else {
                args.add(repeat(" ", indent) + location.getRootName());
            }
            args.add(totalSamples * samplePeriod);
            args.add(percent(totalSamples, globalTotalSamples));
            maybeAddTiers(args, tierToSamples, totalSamples, showTiers, tiers);
            args.add(totalSelfSamples * samplePeriod);
            args.add(percent(totalSelfSamples, globalTotalSamples));
            maybeAddTiers(args, tierToSelfSamples, totalSelfSamples, showTiers, tiers);
            args.add(getShortDescription(location.getSourceSection()));
            return CPUSamplerCLI.format(format, args.toArray());
        }

        private static void maybeAddTiers(List<Object> args, int[] samples, int total, int[] showTiers, Integer[] tiers) {
            if (showTiers == null) {
                return;
            }
            if (showTiers.length == 0) {
                for (int i : tiers) {
                    if (i < samples.length) {
                        args.add(percent(samples[i], total));
                    } else {
                        args.add(0.0);
                    }
                }
                return;
            }
            for (int showTier : showTiers) {
                if (contains(tiers, showTier)) {
                    if (showTier < samples.length) {
                        args.add(percent(samples[showTier], total));
                    } else {
                        args.add(0.0);
                    }
                }
            }
        }
    }

    private static class SamplingCallTree {
        private final boolean summariseThreads;
        private final int minSamples;
        private final int[] showTiers;
        private final long samplePeriod;
        private final long samplesTaken;
        private final long samplesMissed;
        private final String title;
        private final String format;
        private final Map<Thread, Collection<CallTreeOutputEntry>> entries = new HashMap<>();
        private int maxNameLength = 10;
        private final Set<Integer> tiers = new HashSet<>();
        private final Integer[] sortedTiers;

        SamplingCallTree(CPUSamplerData data, OptionValues options) {
            this.summariseThreads = options.get(SUMMARISE_THREADS);
            this.minSamples = options.get(MIN_SAMPLES);
            this.showTiers = options.get(ShowTiers);
            this.samplePeriod = options.get(SAMPLE_PERIOD);
            this.samplesTaken = data.getSamples();
            this.samplesMissed = data.missedSamples();
            Map<Thread, Collection<ProfilerNode<CPUSampler.Payload>>> threadData = data.getThreadData();
            makeEntries(threadData);
            calculateMaxValues(threadData);
            sortedTiers = sortedArray(tiers);
            String[] titleAndFormat = makeTitleAndFormat(maxNameLength, showTiers, sortedTiers);
            this.title = titleAndFormat[0];
            this.format = titleAndFormat[1];

        }

        private void calculateMaxValues(Map<Thread, Collection<ProfilerNode<CPUSampler.Payload>>> threadData) {
            for (Map.Entry<Thread, Collection<ProfilerNode<CPUSampler.Payload>>> entry : threadData.entrySet()) {
                for (ProfilerNode<CPUSampler.Payload> node : entry.getValue()) {
                    calculateMaxValuesRec(node, 0);
                }
            }
        }

        private void calculateMaxValuesRec(ProfilerNode<CPUSampler.Payload> node, int depth) {
            maxNameLength = Math.max(maxNameLength, node.getRootName().length() + OutputEntry.computeIndentSize(depth));
            tiers.add(node.getPayload().getNumberOfTiers() - 1);
            for (ProfilerNode<CPUSampler.Payload> child : node.getChildren()) {
                calculateMaxValuesRec(child, depth + 1);
            }
        }

        private void makeEntries(Map<Thread, Collection<ProfilerNode<CPUSampler.Payload>>> threadData) {
            if (summariseThreads) {
                List<CallTreeOutputEntry> callTreeEntries = new ArrayList<>();
                for (Map.Entry<Thread, Collection<ProfilerNode<CPUSampler.Payload>>> entry : threadData.entrySet()) {
                    for (ProfilerNode<CPUSampler.Payload> node : entry.getValue()) {
                        mergeEntry(callTreeEntries, node, 0);
                    }
                }
                entries.put(new Thread("Summary"), callTreeEntries);
            } else {
                for (Map.Entry<Thread, Collection<ProfilerNode<CPUSampler.Payload>>> entry : threadData.entrySet()) {
                    List<CallTreeOutputEntry> callTreeEntries = new ArrayList<>();
                    for (ProfilerNode<CPUSampler.Payload> node : entry.getValue()) {
                        callTreeEntries.add(makeEntry(node, 0));
                    }
                    entries.put(entry.getKey(), callTreeEntries);
                }
            }
        }

        private void mergeEntry(List<CallTreeOutputEntry> callTreeEntries, ProfilerNode<CPUSampler.Payload> node, int depth) {
            for (CallTreeOutputEntry callTreeEntry : callTreeEntries) {
                if (callTreeEntry.corresponds(node)) {
                    callTreeEntry.merge(node.getPayload());
                    for (ProfilerNode<CPUSampler.Payload> child : node.getChildren()) {
                        mergeEntry(callTreeEntry.children, child, depth + 1);
                    }
                    return;
                }
            }
            callTreeEntries.add(makeEntry(node, depth));
        }

        private CallTreeOutputEntry makeEntry(ProfilerNode<CPUSampler.Payload> node, int depth) {
            maxNameLength = Math.max(maxNameLength, node.getRootName().length() + OutputEntry.computeIndentSize(depth));
            tiers.add(node.getPayload().getNumberOfTiers() - 1);
            CallTreeOutputEntry entry = new CallTreeOutputEntry(node);
            for (ProfilerNode<CPUSampler.Payload> child : node.getChildren()) {
                entry.children.add(makeEntry(child, depth + 1));
            }
            return entry;
        }

        void print(PrintStream out) {
            String sep = repeat("-", title.length());
            out.println(sep);
            printLegend(out, "Call Tree", samplesTaken, samplePeriod, samplesMissed, showTiers, sortedTiers);
            out.println(sep);
            out.println(title);
            out.println(sep);
            for (Map.Entry<Thread, Collection<CallTreeOutputEntry>> threadData : entries.entrySet()) {
                for (CallTreeOutputEntry entry : threadData.getValue()) {
                    recursivePrint(out, entry, 0);
                }
            }
            out.println(sep);
        }

        private void recursivePrint(PrintStream out, CallTreeOutputEntry entry, int depth) {
            if (minSamples > 0 && entry.totalSelfSamples < minSamples) {
                return;
            }
            out.println(entry.format(format, showTiers, samplePeriod, depth, samplesTaken, sortedTiers));
            List<CallTreeOutputEntry> sortedChildren = new ArrayList<>(entry.children);
            sortedChildren.sort((o1, o2) -> Long.compare(o2.totalSamples, o1.totalSamples));
            for (CallTreeOutputEntry child : sortedChildren) {
                recursivePrint(out, child, depth + 1);
            }
        }

        private static class CallTreeOutputEntry extends OutputEntry {
            List<CallTreeOutputEntry> children = new ArrayList<>();

            CallTreeOutputEntry(ProfilerNode<CPUSampler.Payload> node) {
                super(node);
            }

            boolean corresponds(ProfilerNode<CPUSampler.Payload> node) {
                return location.getSourceSection().equals(node.getSourceSection()) && location.getRootName().equals(node.getRootName());
            }

            void merge(CPUSampler.Payload payload) {
                this.totalSamples += payload.getHitCount();
                this.totalSelfSamples += payload.getSelfHitCount();
                if (payload.getNumberOfTiers() > tierToSamples.length) {
                    tierToSamples = Arrays.copyOf(tierToSamples, payload.getNumberOfTiers());
                }
                for (int i = 0; i < payload.getNumberOfTiers(); i++) {
                    tierToSamples[i] += payload.getTierTotalCount(i);
                }
                if (payload.getNumberOfTiers() > tierToSelfSamples.length) {
                    tierToSelfSamples = Arrays.copyOf(tierToSelfSamples, payload.getNumberOfTiers());
                }
                for (int i = 0; i < payload.getNumberOfTiers(); i++) {
                    tierToSamples[i] += payload.getTierTotalCount(i);
                }
            }
        }
    }

    private static String format(String format, Object... args) {
        return String.format(Locale.ENGLISH, format, args);
    }

}
