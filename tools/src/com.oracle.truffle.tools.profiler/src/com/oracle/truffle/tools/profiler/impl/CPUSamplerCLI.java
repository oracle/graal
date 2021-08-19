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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.CPUSamplerData;
import com.oracle.truffle.tools.profiler.ProfilerNode;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

@Option.Group(CPUSamplerInstrument.ID)
class CPUSamplerCLI extends ProfilerCLI {

    public static final long MILLIS_TO_NANOS = 1_000_000L;
    public static final double MAX_OVERHEAD_WARNING_THRESHOLD = 0.2;

    enum Output {
        HISTOGRAM,
        CALLTREE,
        JSON,
    }

    static final OptionType<Output> CLI_OUTPUT_TYPE = new OptionType<>("Output",
                    new Function<String, Output>() {
                        @Override
                        public Output apply(String s) {
                            try {
                                return Output.valueOf(s.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                throw new IllegalArgumentException("Output can be: histogram, calltree or json");
                            }
                        }
                    });

    static final OptionType<ShowTiers> SHOW_TIERS_OUTPUT_TYPE = new OptionType<>("ShowTiers",
                    new Function<String, ShowTiers>() {
                        @Override
                        public ShowTiers apply(String s) {
                            if ("false".equals(s)) {
                                return new ShowTiers(false, new int[0]);
                            }
                            if ("true".equals(s)) {
                                return new ShowTiers(true, new int[0]);
                            }
                            try {
                                String[] tierStrings = s.split(",");
                                int[] tiers = new int[tierStrings.length];
                                for (int i = 0; i < tierStrings.length; i++) {
                                    tiers[i] = Integer.parseInt(tierStrings[i]);
                                }
                                return new ShowTiers(true, tiers);
                            } catch (NumberFormatException e) {
                                // Ignored
                            }
                            throw new IllegalArgumentException("ShowTiers can be: true, false or a comma separated list of integers");
                        }
                    });

    @SuppressWarnings("deprecation") static final OptionType<CPUSampler.Mode> CLI_MODE_TYPE = new OptionType<>("Mode",
                    new Function<String, CPUSampler.Mode>() {
                        @Override
                        public CPUSampler.Mode apply(String s) {
                            try {
                                return CPUSampler.Mode.valueOf(s.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                throw new IllegalArgumentException("Mode can be: compiled, roots or statements.");
                            }
                        }
                    });

    @Option(name = "", help = "Enable the CPU sampler.", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);

    // @formatter:off
    @SuppressWarnings("deprecation")
    @Option(name = "Mode", help = "Deprecated. Has no effect.", category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<CPUSampler.Mode> MODE = new OptionKey<>(CPUSampler.Mode.EXCLUDE_INLINED_ROOTS, CLI_MODE_TYPE);
    // @formatter:on
    @Option(name = "Period", help = "Period in milliseconds to sample the stack.", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Long> SAMPLE_PERIOD = new OptionKey<>(10L);

    @Option(name = "Delay", help = "Delay the sampling for this many milliseconds (default: 0).", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Long> DELAY_PERIOD = new OptionKey<>(0L);

    @Option(name = "StackLimit", help = "Maximum number of maximum stack elements.", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Integer> STACK_LIMIT = new OptionKey<>(10000);

    @Option(name = "Output", help = "Print a 'histogram', 'calltree' or 'json' as output (default:HISTOGRAM).", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Output> OUTPUT = new OptionKey<>(Output.HISTOGRAM, CLI_OUTPUT_TYPE);

    @Option(help = "Specify whether to show compilation information for entries. You can specify 'true' to show all compilation information, 'false' for none, or a comma separated list of compilation tiers. " +
                    "Note: Interpreter is considered Tier 0. (default: false).", category = OptionCategory.EXPERT, stability = OptionStability.STABLE) //
    static final OptionKey<ShowTiers> ShowTiers = new OptionKey<>(new ShowTiers(false, new int[0]), SHOW_TIERS_OUTPUT_TYPE);

    @Option(name = "FilterRootName", help = "Wildcard filter for program roots. (eg. Math.*, default:*).", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Object[]> FILTER_ROOT = new OptionKey<>(new Object[0], WILDCARD_FILTER_TYPE);

    @Option(name = "FilterFile", help = "Wildcard filter for source file paths. (eg. *program*.sl, default:*).", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Object[]> FILTER_FILE = new OptionKey<>(new Object[0], WILDCARD_FILTER_TYPE);

    @Option(name = "FilterMimeType", help = "Only profile languages with mime-type. (eg. +, default:no filter).", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<String> FILTER_MIME_TYPE = new OptionKey<>("");

    @Option(name = "FilterLanguage", help = "Only profile languages with given ID. (eg. js, default:no filter).", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<String> FILTER_LANGUAGE = new OptionKey<>("");

    @Option(name = "SampleInternal", help = "Capture internal elements (default:false).", category = OptionCategory.INTERNAL) //
    static final OptionKey<Boolean> SAMPLE_INTERNAL = new OptionKey<>(false);

    @Option(name = "SummariseThreads", help = "Print output as a summary of all 'per thread' profiles. (default: false)", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Boolean> SUMMARISE_THREADS = new OptionKey<>(false);

    @Option(name = "GatherHitTimes", help = "Save a timestamp for each taken sample (default:false).", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Boolean> GATHER_HIT_TIMES = new OptionKey<>(false);

    @Option(name = "OutputFile", help = "Save output to the given file. Output is printed to output stream by default.", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<String> OUTPUT_FILE = new OptionKey<>("");

    @Option(name = "MinSamples", help = "Remove elements from output if they have less samples than this value (default: 0).", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Integer> MIN_SAMPLES = new OptionKey<>(0);

    @Option(name = "SampleContextInitialization", help = "Enables sampling of code executed during context initialization (default: false).", category = OptionCategory.EXPERT, stability = OptionStability.STABLE) //
    static final OptionKey<Boolean> SAMPLE_CONTEXT_INITIALIZATION = new OptionKey<>(false);

    static void handleOutput(TruffleInstrument.Env env, CPUSampler sampler) {
        try (PrintStream out = chooseOutputStream(env, OUTPUT_FILE)) {
            Map<TruffleContext, CPUSamplerData> data = sampler.getData();
            OptionValues options = env.getOptions();
            switch (options.get(OUTPUT)) {
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
            }
        }
    }

    private static void printSamplingCallTree(PrintStream out, OptionValues options, Map<TruffleContext, CPUSamplerData> data) {
        for (Map.Entry<TruffleContext, CPUSamplerData> entry : data.entrySet()) {
            new SamplingCallTree(entry.getValue(), options).print(out);
        }
    }

    private static void printSamplingHistogram(PrintStream out, OptionValues options, Map<TruffleContext, CPUSamplerData> data) {
        for (Map.Entry<TruffleContext, CPUSamplerData> entry : data.entrySet()) {
            new SamplingHistogram(entry.getValue(), options).print(out);
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
        for (CPUSamplerData value : sampler.getData().values()) {
            if (value.getSampleDuration().getAverage() > MAX_OVERHEAD_WARNING_THRESHOLD * sampler.getPeriod() * MILLIS_TO_NANOS) {
                return true;
            }
        }
        return false;
    }

    private static void printDiv(PrintStream out) {
        out.println("-------------------------------------------------------------------------------- ");
    }

    private static void printSamplingJson(PrintStream out, OptionValues options, Map<TruffleContext, CPUSamplerData> data) {
        boolean gatheredHitTimes = options.get(GATHER_HIT_TIMES);
        JSONObject output = new JSONObject();
        output.put("tool", CPUSamplerInstrument.ID);
        output.put("version", CPUSamplerInstrument.VERSION);
        JSONArray contexts = new JSONArray();
        for (CPUSamplerData samplerData : data.values()) {
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

    private static void printLegend(PrintStream out, String type, long samples, long period, CPUSamplerCLI.ShowTiers showTiers, int maxTier) {
        out.printf("Sampling %s. Recorded %s samples with period %dms.%n", type, samples, period);
        out.println("  Self Time: Time spent on the top of the stack.");
        out.println("  Total Time: Time spent somewhere on the stack.");
        if (showTiers.show) {
            if (showTiers.tiers.length == 0) {
                for (int i = 0; i <= maxTier; i++) {
                    out.println("  T" + i + ": Percent of time spent in " + (i == 0 ? "interpreter." : "code compiled by tier " + i + " compiler."));
                }
            } else {
                for (int tier : showTiers.tiers) {
                    out.println("  T" + tier + ": Percent of time spent in " + (tier == 0 ? "interpreter." : "code compiled by tier " + tier + " compiler."));
                }
            }

        }
    }

    private static double percent(long samples, long totalSamples) {
        if (totalSamples == 0) {
            return 0.0;
        }
        return ((double) samples * 100) / totalSamples;
    }

    private static String[] makeTitleAndFormat(int nameLength, CPUSamplerCLI.ShowTiers showTiers, int maxTier) {
        StringBuilder titleBuilder = new StringBuilder(String.format(" %-" + nameLength + "s ||             Total Time    ", "Name"));
        StringBuilder formatBuilder = new StringBuilder(" %-" + nameLength + "s ||       %10dms %5.1f%% ");
        maybeAddTiers(titleBuilder, formatBuilder, showTiers, maxTier);
        titleBuilder.append("||              Self Time    ");
        formatBuilder.append("||       %10dms %5.1f%% ");
        maybeAddTiers(titleBuilder, formatBuilder, showTiers, maxTier);
        titleBuilder.append("|| Location             ");
        formatBuilder.append("|| %s");
        String[] strings = new String[2];
        strings[0] = titleBuilder.toString();
        strings[1] = formatBuilder.toString();
        return strings;
    }

    private static void maybeAddTiers(StringBuilder titleBuilder, StringBuilder formatBuilder, CPUSamplerCLI.ShowTiers showTiers, int maxTier) {
        if (showTiers.show) {
            if (showTiers.tiers.length == 0) {
                for (int i = 0; i < maxTier + 1; i++) {
                    titleBuilder.append("|   T").append(i).append("   ");
                    formatBuilder.append("| %5.1f%% ");
                }
            } else {
                for (int i = 0; i < showTiers.tiers.length; i++) {
                    int selectedTier = showTiers.tiers[i];
                    titleBuilder.append("|   T").append(selectedTier).append("   ");
                    formatBuilder.append("| %5.1f%% ");
                }
            }
        }
    }

    static final class ShowTiers {
        final boolean show;
        final int[] tiers;

        ShowTiers(boolean showTiers, int[] tiers) {
            this.show = showTiers;
            this.tiers = tiers;
        }
    }

    private static final class SamplingHistogram {
        private final Map<Thread, List<OutputEntry>> histogram = new HashMap<>();
        private final boolean summariseThreads;
        private final int minSamples;
        private final ShowTiers showTiers;
        private final long samplePeriod;
        private final long samplesTaken;
        private int maxTier = 0;
        private int maxNameLength = 10;
        private final String title;
        private final String format;

        SamplingHistogram(CPUSamplerData data, OptionValues options) {
            this.summariseThreads = options.get(SUMMARISE_THREADS);
            this.minSamples = options.get(MIN_SAMPLES);
            this.showTiers = options.get(ShowTiers);
            this.samplePeriod = options.get(SAMPLE_PERIOD);
            this.samplesTaken = data.getSamples();
            Map<Thread, SourceLocationPayloads> perThreadSourceLocationPayloads = new HashMap<>();
            for (Thread thread : data.getThreadData().keySet()) {
                perThreadSourceLocationPayloads.put(thread, computeSourceLocationPayloads(data.getThreadData().get(thread)));
            }
            maybeSummarizeThreads(perThreadSourceLocationPayloads);
            for (Map.Entry<Thread, SourceLocationPayloads> threadEntry : perThreadSourceLocationPayloads.entrySet()) {
                histogram.put(threadEntry.getKey(), histogramEntries(threadEntry));
            }
            String[] titleAndFormat = makeTitleAndFormat(maxNameLength, showTiers, maxTier);
            this.title = titleAndFormat[0];
            this.format = titleAndFormat[1];
        }

        private ArrayList<OutputEntry> histogramEntries(Map.Entry<Thread, SourceLocationPayloads> threadEntry) {
            ArrayList<OutputEntry> histogramEntries = new ArrayList<>();
            for (Map.Entry<SourceLocation, List<CPUSampler.Payload>> sourceLocationEntry : threadEntry.getValue().locations.entrySet()) {
                histogramEntries.add(histogramEntry(sourceLocationEntry));
            }
            histogramEntries.sort((o1, o2) -> Integer.compare(o2.totalSelfSamples, o1.totalSelfSamples));
            return histogramEntries;
        }

        private OutputEntry histogramEntry(Map.Entry<SourceLocation, List<CPUSampler.Payload>> sourceLocationEntry) {
            OutputEntry outputEntry = new OutputEntry(sourceLocationEntry.getKey());
            maxNameLength = Math.max(maxNameLength, sourceLocationEntry.getKey().getRootName().length());
            for (CPUSampler.Payload payload : sourceLocationEntry.getValue()) {
                for (int i = 0; i < payload.getNumberOfTiers(); i++) {
                    int selfHitCountsValue = payload.getTierSelfCount(i);
                    outputEntry.totalSelfSamples += selfHitCountsValue;
                    if (outputEntry.tierToSelfSamples.length < i + 1) {
                        outputEntry.tierToSelfSamples = Arrays.copyOf(outputEntry.tierToSelfSamples, outputEntry.tierToSelfSamples.length + 1);
                    }
                    outputEntry.tierToSelfSamples[i] += selfHitCountsValue;
                    maxTier = Math.max(maxTier, i);
                }
            }
            for (CPUSampler.Payload payload : sourceLocationEntry.getValue()) {
                for (int i = 0; i < payload.getNumberOfTiers(); i++) {
                    int hitCountsValue = payload.getTierTotalCount(i);
                    outputEntry.totalSamples += hitCountsValue;
                    if (outputEntry.tierToSamples.length < i + 1) {
                        outputEntry.tierToSamples = Arrays.copyOf(outputEntry.tierToSamples, outputEntry.tierToSamples.length + 1);
                    }
                    outputEntry.tierToSamples[i] += hitCountsValue;
                    maxTier = Math.max(maxTier, i);
                }
            }
            return outputEntry;
        }

        private void maybeSummarizeThreads(Map<Thread, SourceLocationPayloads> perThreadSourceLocationPayloads) {
            if (summariseThreads) {
                SourceLocationPayloads summary = new SourceLocationPayloads(new HashMap<>());
                for (SourceLocationPayloads sourceLocationPayloads : perThreadSourceLocationPayloads.values()) {
                    for (Map.Entry<SourceLocation, List<CPUSampler.Payload>> entry : sourceLocationPayloads.locations.entrySet()) {
                        summary.locations.computeIfAbsent(entry.getKey(), s -> new ArrayList<>()).addAll(entry.getValue());
                    }
                }
                perThreadSourceLocationPayloads.clear();
                perThreadSourceLocationPayloads.put(new Thread("Summary"), summary);
            }
        }

        private static SourceLocationPayloads computeSourceLocationPayloads(Collection<ProfilerNode<CPUSampler.Payload>> profilerNodes) {
            Map<SourceLocation, List<CPUSampler.Payload>> histogram = new HashMap<>();
            computeSourceLocationPayloadsImpl(profilerNodes, histogram);
            return new SourceLocationPayloads(histogram);
        }

        private static void computeSourceLocationPayloadsImpl(Collection<ProfilerNode<CPUSampler.Payload>> children, Map<SourceLocation, List<CPUSampler.Payload>> histogram) {
            for (ProfilerNode<CPUSampler.Payload> treeNode : children) {
                List<CPUSampler.Payload> nodes = histogram.computeIfAbsent(new SourceLocation(treeNode.getSourceSection(), treeNode.getRootName()),
                                new Function<SourceLocation, List<CPUSampler.Payload>>() {
                                    @Override
                                    public List<CPUSampler.Payload> apply(SourceLocation s) {
                                        return new ArrayList<>();
                                    }
                                });
                nodes.add(treeNode.getPayload());
                computeSourceLocationPayloadsImpl(treeNode.getChildren(), histogram);
            }
        }

        void print(PrintStream out) {
            String sep = repeat("-", title.length());
            out.println(sep);
            printLegend(out, "Histogram", samplesTaken, samplePeriod, showTiers, maxTier);
            out.println(sep);
            for (Map.Entry<Thread, List<OutputEntry>> threadListEntry : histogram.entrySet()) {
                out.println(threadListEntry.getKey());
                out.println(title);
                out.println(sep);
                for (OutputEntry entry : threadListEntry.getValue()) {
                    if (minSamples > 0 && entry.totalSelfSamples < minSamples) {
                        continue;
                    }
                    out.println(entry.format(format, showTiers, SamplingHistogram.this.samplePeriod, 0, samplesTaken));
                }
                out.println(sep);
            }
        }

        private static final class SourceLocationPayloads {
            final Map<SourceLocation, List<CPUSampler.Payload>> locations;

            SourceLocationPayloads(Map<SourceLocation, List<CPUSampler.Payload>> locations) {
                this.locations = locations;
            }
        }
    }

    private static class OutputEntry {
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

        String format(String format, CPUSamplerCLI.ShowTiers showTiers, long samplePeriod, int indent, long globalTotalSamples) {
            List<Object> args = new ArrayList<>();
            args.add(repeat(" ", indent) + location.getRootName());
            args.add(totalSamples * samplePeriod);
            args.add(percent(totalSamples, globalTotalSamples));
            maybeAddTiers(args, tierToSamples, totalSamples, showTiers);
            args.add(totalSelfSamples * samplePeriod);
            args.add(percent(totalSelfSamples, globalTotalSamples));
            maybeAddTiers(args, tierToSelfSamples, totalSelfSamples, showTiers);
            args.add(getShortDescription(location.getSourceSection()));
            return String.format(format, args.toArray());
        }

        private static void maybeAddTiers(List<Object> args, int[] samples, int total, CPUSamplerCLI.ShowTiers showTiers) {
            if (showTiers.show) {
                if (showTiers.tiers.length == 0) {
                    for (int i = 0; i < samples.length; i++) {
                        args.add(percent(samples[i], total));
                    }
                } else {
                    for (int i = 0; i < showTiers.tiers.length; i++) {
                        if (showTiers.tiers[i] < samples.length) {
                            args.add(percent(samples[showTiers.tiers[i]], total));
                        } else {
                            args.add(0.0);
                        }
                    }
                }
            }
        }
    }

    private static class SamplingCallTree {
        private final boolean summariseThreads;
        private final int minSamples;
        private final ShowTiers showTiers;
        private final long samplePeriod;
        private final long samplesTaken;
        private final String title;
        private final String format;
        private final Map<Thread, Collection<CallTreeOutputEntry>> entries = new HashMap<>();
        private int maxNameLength = 10;
        private int maxTier;

        SamplingCallTree(CPUSamplerData data, OptionValues options) {
            this.summariseThreads = options.get(SUMMARISE_THREADS);
            this.minSamples = options.get(MIN_SAMPLES);
            this.showTiers = options.get(ShowTiers);
            this.samplePeriod = options.get(SAMPLE_PERIOD);
            this.samplesTaken = data.getSamples();
            Map<Thread, Collection<ProfilerNode<CPUSampler.Payload>>> threadData = data.getThreadData();
            makeEntries(threadData);
            calculateMaxValues(threadData);
            String[] titleAndFormat = makeTitleAndFormat(maxNameLength, showTiers, maxTier);
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
            maxNameLength = Math.max(maxNameLength, node.getRootName().length() + depth);
            maxTier = Math.max(maxTier, node.getPayload().getNumberOfTiers() - 1);
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
            maxNameLength = Math.max(maxNameLength, node.getRootName().length() + depth);
            maxTier = Math.max(maxTier, node.getPayload().getNumberOfTiers() - 1);
            CallTreeOutputEntry entry = new CallTreeOutputEntry(node);
            for (ProfilerNode<CPUSampler.Payload> child : node.getChildren()) {
                entry.children.add(makeEntry(child, depth + 1));
            }
            return entry;
        }

        void print(PrintStream out) {
            String sep = repeat("-", title.length());
            out.println(sep);
            printLegend(out, "Call Tree", samplesTaken, samplePeriod, showTiers, maxTier);
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
            out.println(entry.format(format, showTiers, samplePeriod, depth, samplesTaken));
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
}
