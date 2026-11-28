/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.pgo;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.stream.IntStream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.pgo.IprofFormat;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.pgo.IprofParser.IprofFormatException;
import com.oracle.svm.hosted.pgo.IprofParser.ParsedIprofProfile;
import com.oracle.svm.hosted.pgo.phases.PGOApplyProfilesPhase;
import com.oracle.svm.hosted.pgo.profiles.PGODiagnostics.CallCountDiagnostics;
import com.oracle.svm.hosted.pgo.profiles.PGODiagnostics.ConditionalDiagnostics;
import com.oracle.svm.hosted.pgo.profiles.PGODiagnostics.SamplingDiagnostics;
import com.oracle.svm.hosted.pgo.profiles.PGODiagnostics.VirtualInvokeDiagnostics;
import com.oracle.svm.hosted.pgo.profiles.IprofProfileResolver;
import com.oracle.svm.hosted.pgo.profiles.PGODiagnostics;
import com.oracle.svm.hosted.pgo.profiles.PGOProfilesLookup;
import com.oracle.svm.hosted.pgo.profiles.SamplingHotness;
import com.oracle.svm.hosted.pgo.profiles.ProfiledInliningProvider;
import com.oracle.svm.hosted.pgo.profiles.IprofProfilesLookup;
import com.oracle.svm.hosted.phases.priorityinline.SubstratePriorityInliningPhase;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.option.APIOption;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.util.LogUtils;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.AbstractInliningPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;

/**
 * Optimizes the image with a profile recorded by a {@code --pgo-instrument} image ({@code --pgo}).
 * <p>
 * The feature parses the iprof file or files, resolves their branch, switch, receiver, call-count
 * and stack-sample entries against the hosted universe, and applies them to every compilation root
 * before the inliner runs and to every callee the priority inliner expands.
 */
@AutomaticallyRegisteredFeature
final class PGOProfileUseFeature implements InternalFeature {

    /** Options of profile use; all but {@code --pgo} turn parts of it off or add output. */
    static final class Options {
        // @formatter:off
        /** The iprof files to use; setting it enables this feature. */
        @APIOption(name = "pgo")//
        @Option(help = "Optimize the image with profiles recorded by a --pgo-instrument image: one .iprof file, a directory of .iprof files, " +
                       "or a comma-separated list of files, each with an optional :weight. " +
                       "Raises the default optimization level -O2 to -O3; an -O given after this option wins.")//
        static final HostedOptionKey<String> PGOProfileUse = new ProfilePathOption();

        /** Off to measure what the context fallback adds to branch and switch profiles. */
        @Option(help = "When a branch or switch has no profile under its exact inlining context, add up the profiles recorded for the same site " +
                       "under longer contexts that contain it as their inner part. If there are none, drop the outermost caller and repeat, " +
                       "down to the site's own method.")//
        static final HostedOptionKey<Boolean> PGOConditionalContextFallback = new HostedOptionKey<>(true);

        /** Off to measure the fallback, as the {@code pgo-shallow-no-fallback} gate run does. */
        @Option(help = "When an indirect call has no receiver profile under its exact inlining context, drop the outermost caller and repeat " +
                       "until a context recorded for the same call matches.")//
        static final HostedOptionKey<Boolean> PGOReceiverContextFallback = new HostedOptionKey<>(true);

        /** Off to measure what {@code callCountProfiles} add to the inliner's hotness. */
        @Option(help = "Use the profile's call counts to rate how hot a call edge is relative to its compilation root.")//
        static final HostedOptionKey<Boolean> PGOUseCallCounts = new HostedOptionKey<>(true);

        /** Off to measure stack samples, as the {@code pgo-samples-disabled} gate run does. */
        @Option(help = "Use the profile's stack samples: sampled hotness of roots and contexts, sampled callee profiles and self time.")//
        static final HostedOptionKey<Boolean> PGOUseStackSamples = new HostedOptionKey<>(true);

        /** The {@code pgo end-to-end} gate task turns this on and checks the printed numbers. */
        @Option(help = "Print detailed statistics on how the profile was resolved and applied. By default one summary line is printed.")//
        static final HostedOptionKey<Boolean> PGOPrintStatistics = new HostedOptionKey<>(false);
        // @formatter:on

        /**
         * The key of {@code --pgo}. A non-empty value also updates the options that profile use
         * depends on (see {@link #onValueUpdate}).
         */
        private static final class ProfilePathOption extends HostedOptionKey<String> {
            private ProfilePathOption() {
                super("");
            }

            /**
             * Turns on {@link GraalOptions#TrackNodeSourcePosition}, which the profile lookup needs
             * to know each node's inlining context, and raises the default -O2 to -O3.
             */
            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, String oldValue, String newValue) {
                super.onValueUpdate(values, oldValue, newValue);
                if (newValue != null && !newValue.isEmpty()) {
                    GraalOptions.TrackNodeSourcePosition.update(values, true);
                    /* Optimize at the level the profile was recorded at. */
                    PGOUtils.raiseDefaultToO3(values);
                }
            }
        }
    }

    /** Weight of a profile given without {@code :weight}. */
    private static final double DEFAULT_WEIGHT = 1.0;
    /** Prefix of the lines the feature prints during the build. */
    private static final String LOG_PREFIX = "PGO";
    /** The merged profile; {@link #beforeCompilation} resolves it and clears this field. */
    private ParsedIprofProfile parsedProfile;
    /** The resolved profile, or {@code null} if {@code --pgo} names no .iprof file. */
    private IprofProfilesLookup profilesLookup;
    /**
     * Hotness from {@code samplingProfiles} for the inliner, or {@code null} if the profile has
     * neither {@code samplingProfiles} nor {@code callCountProfiles}.
     */
    private SamplingHotness samplingHotness;
    /** The image's universe, set in {@link #beforeCompilation}. */
    private HostedUniverse hostedUniverse;

    private static String profilePath() {
        return Options.PGOProfileUse.getValue();
    }

    /**
     * Whether {@code --pgo} is set.
     *
     * @return {@code true} if the image is built with a profile
     */
    static boolean enabled() {
        String path = profilePath();
        return path != null && !path.isEmpty();
    }

    /**
     * Whether to print the detailed statistics; the {@code pgo end-to-end} gate task reads them.
     *
     * @return the value of {@code PGOPrintStatistics}
     */
    private static boolean printStatistics() {
        return Options.PGOPrintStatistics.getValue();
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return enabled();
    }

    /** Parses and merges the iprof files early, so that a bad file stops the build at once. */
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        parsedProfile = parseProfile(profilePath());
    }

    /** The --pgo option as the user gave it, for messages. */
    private static String profileOption() {
        return SubstrateOptionsParser.commandArgument(Options.PGOProfileUse, profilePath());
    }

    /** Prints {@code format} with {@code args} on one line with the {@value #LOG_PREFIX} prefix. */
    private static void info(String format, Object... args) {
        LogUtils.prefixInfo(LOG_PREFIX, format, args);
    }

    /**
     * Parses the option value {@code path[:weight][,path[:weight]...]}: one iprof file, or several
     * that are merged with their counts scaled by the given weights (default 1), as
     * {@code llvm-profdata merge --weighted-input} and {@code gcov-tool merge -w} do for training
     * runs of different workloads or lengths. A directory stands for all .iprof files in it.
     *
     * @param optionValue the value of {@code --pgo}
     * @return the merged profile, or {@code null} if the option names no .iprof file
     */
    private static ParsedIprofProfile parseProfile(String optionValue) {
        List<IprofProfileMerger.WeightedProfile> profiles = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String element : optionValue.split(",")) {
            String spec = element.strip();
            if (spec.isEmpty()) {
                LogUtils.warning("An empty entry in %s adds no profile.", profileOption());
                continue;
            }
            if (Files.isDirectory(Path.of(spec))) {
                /*
                 * A directory of training runs, e.g. written with -XX:ProfilesDumpFile=dir/%p.iprof.
                 * The files are merged in name order, so the same directory always gives the same
                 * merged counts and the same image.
                 */
                List<Path> iprofs = listProfiles(spec);
                if (iprofs.isEmpty()) {
                    LogUtils.warning("The directory %s in %s contains no .iprof files, so it adds no profile.", spec, profileOption());
                }
                for (Path iprof : iprofs) {
                    profiles.add(new IprofProfileMerger.WeightedProfile(parseSingleProfile(iprof), DEFAULT_WEIGHT));
                    names.add(iprof.toString());
                }
                continue;
            }
            ProfileSpec profileSpec = ProfileSpec.parse(spec);
            if (profileSpec.ignoredWeight() != null) {
                LogUtils.warning("'%s' after the last ':' in %s is not a number, so it is read as part of the file name, not as a weight.", profileSpec.ignoredWeight(), spec);
            }
            if (!(Double.isFinite(profileSpec.weight()) && profileSpec.weight() > 0.0)) {
                throw UserError.abort("The profile weight in %s must be a positive number: %s", profileOption(), spec);
            }
            profiles.add(new IprofProfileMerger.WeightedProfile(parseSingleProfile(Path.of(profileSpec.path())), profileSpec.weight()));
            names.add(profileSpec.path());
        }
        if (profiles.isEmpty()) {
            LogUtils.warning("%s names no .iprof file, so the image is built without a profile.", profileOption());
            return null;
        }
        if (profiles.size() > 1) {
            reportOverlaps(profiles, names);
        }
        try {
            return IprofProfileMerger.merge(profiles);
        } catch (IprofFormatException e) {
            throw UserError.abort("The iprof files in %s cannot be merged: %s", profileOption(), e.getMessage());
        }
    }

    /**
     * One element {@code path[:weight]} of the option value. The text after the last ':' is a
     * weight only if it is a number; otherwise it is part of the path, as in a file name that
     * contains ':'. A file whose name is the whole element is always read as a path. If no file has
     * that name and the text after the ':' is not a number, the text was probably meant as a weight,
     * so {@code ignoredWeight} holds it for a warning.
     *
     * @param path the iprof file
     * @param weight the factor its counts are scaled by in the merge
     * @param ignoredWeight the text after the last ':' if it is not a number and was read as part
     *            of a path that names no file, otherwise {@code null}
     */
    record ProfileSpec(String path, double weight, String ignoredWeight) {
        /**
         * Splits one element of the option value into path and weight.
         *
         * @param spec the element, stripped of white space
         * @return the path and weight; the weight is not checked to be positive
         */
        static ProfileSpec parse(String spec) {
            int separator = spec.lastIndexOf(':');
            String suffix = separator > 0 ? spec.substring(separator + 1) : null;
            /* A path separator after the ':' means the ':' belongs to the path, as in C:\profiles\a.iprof. */
            if (suffix == null || suffix.indexOf('/') >= 0 || suffix.indexOf('\\') >= 0 || exists(spec)) {
                return new ProfileSpec(spec, DEFAULT_WEIGHT, null);
            }
            try {
                return new ProfileSpec(spec.substring(0, separator).strip(), Double.parseDouble(suffix), null);
            } catch (NumberFormatException e) {
                return new ProfileSpec(spec, DEFAULT_WEIGHT, suffix);
            }
        }

        private static boolean exists(String path) {
            try {
                return Files.exists(Path.of(path));
            } catch (InvalidPathException e) {
                return false;
            }
        }
    }

    /** The .iprof files in {@code directory} by name; aborts the build if it cannot be listed. */
    private static List<Path> listProfiles(String directory) {
        try (var files = Files.list(Path.of(directory))) {
            return files.filter(f -> f.toString().endsWith(".iprof")).sorted().toList();
        } catch (IOException e) {
            throw UserError.abort("Could not list the directory %s in %s: %s", directory, profileOption(), e.getMessage());
        }
    }

    /**
     * Tells the user how alike the training runs are, with the sampling overlap of every pair of
     * {@code profiles}: 1 means two runs spent their samples in the same methods in the same
     * proportions. A low overlap means the merged profile is a compromise. One line gives the
     * lowest and the median pair, by their {@code names}; with {@code PGOPrintStatistics} every
     * input also gets its row of the matrix.
     */
    private static void reportOverlaps(List<IprofProfileMerger.WeightedProfile> profiles, List<String> names) {
        double[][] overlaps = IprofProfileMerger.samplingOverlaps(profiles.stream().map(IprofProfileMerger.WeightedProfile::profile).toList());
        int count = profiles.size();
        List<Double> pairs = new ArrayList<>();
        int lowestFirst = -1;
        int lowestSecond = -1;
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                if (Double.isNaN(overlaps[i][j])) {
                    continue;
                }
                pairs.add(overlaps[i][j]);
                if (lowestFirst < 0 || overlaps[i][j] < overlaps[lowestFirst][lowestSecond]) {
                    lowestFirst = i;
                    lowestSecond = j;
                }
            }
        }
        String summary;
        if (pairs.isEmpty()) {
            summary = "not defined, fewer than two inputs have stack samples";
        } else {
            Collections.sort(pairs);
            int middle = pairs.size() / 2;
            double median = pairs.size() % 2 == 1 ? pairs.get(middle) : (pairs.get(middle - 1) + pairs.get(middle)) / 2;
            summary = String.format("lowest %.2f (%s and %s), median %.2f over %d pairs", overlaps[lowestFirst][lowestSecond], names.get(lowestFirst), names.get(lowestSecond), median,
                            pairs.size());
        }
        long withoutSamples = IntStream.range(0, count).filter(i -> Double.isNaN(overlaps[i][i])).count();
        info("merging %d profiles; sampling overlap between inputs (1 = same hot methods in the same proportions): %s%s", count, summary,
                        withoutSamples == 0 ? "" : "; " + withoutSamples + " inputs have no stack samples");
        if (printStatistics()) {
            for (int i = 0; i < count; i++) {
                StringBuilder row = new StringBuilder();
                for (int j = 0; j < count; j++) {
                    row.append(j == 0 ? "" : " ").append(i == j ? "-" : Double.isNaN(overlaps[i][j]) ? "n/a" : String.format("%.2f", overlaps[i][j]));
                }
                info("sampling overlap of input %d (%s) with inputs 0 to %d: %s", i, names.get(i), count - 1, row);
            }
        }
    }

    /** Parses the iprof file {@code path}; aborts the build if it is unreadable or malformed. */
    private static ParsedIprofProfile parseSingleProfile(Path path) {
        if (!Files.isReadable(path)) {
            throw UserError.abort("The iprof file %s in %s is not readable.", path, profileOption());
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return new IprofParser().parse(reader);
        } catch (IprofFormatException e) {
            throw UserError.abort("The iprof file %s in %s is malformed: %s", path, profileOption(), e.getMessage());
        } catch (IOException e) {
            throw UserError.abort("Could not read the iprof file %s in %s: %s", path, profileOption(), e.getMessage());
        }
    }

    /**
     * Resolves the profile against the hosted universe, publishes it as the
     * {@link PGOProfilesLookup} singleton and builds the sampled hotness for the inliner.
     */
    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        hostedUniverse = ((FeatureImpl.BeforeCompilationAccessImpl) access).getUniverse();
        if (parsedProfile != null) {
            profilesLookup = IprofProfileResolver.resolve(parsedProfile, hostedUniverse, PGOApplyProfilesPhase.Options.PGOPrintProfileQualityDetails.getValue());
            profilesLookup.setConditionalContextFallback(Options.PGOConditionalContextFallback.getValue());
            profilesLookup.setVirtualInvokeContextFallback(Options.PGOReceiverContextFallback.getValue());
            profilesLookup.setUseCallCounts(Options.PGOUseCallCounts.getValue());
            profilesLookup.setUseStackSamples(Options.PGOUseStackSamples.getValue());
            ImageSingletons.add(PGOProfilesLookup.class, profilesLookup);
            reportResolution(profilesLookup.diagnostics());
            if (profilesLookup.getSampleCounts().isPresent()) {
                samplingHotness = new SamplingHotness(profilesLookup.getSampleCounts().get());
                if (printStatistics()) {
                    info("sampling hotness: %d samples over %d sampled methods (%d idle samples excluded)",
                                    samplingHotness.nonIdleSamples(), samplingHotness.sampledMethodCount(), samplingHotness.idleSamples());
                }
            } else if (profilesLookup.profileCategoryRecorded(IprofFormat.CALL_COUNT_PROFILES)) {
                /* Install the same context-aware provider for call-count-only profiles. */
                samplingHotness = new SamplingHotness(Map.of());
            }
        }
        parsedProfile = null;
    }

    /**
     * Prints how many entries of each category in {@code diagnostics} match this image, records
     * the profile relevance, and warns about categories that options leave unused or that do not
     * match.
     */
    private static void reportResolution(PGODiagnostics diagnostics) {
        if (printStatistics()) {
            info("iprof version %s", diagnostics.version());
            info("%s", diagnostics.conditional().summary());
            if (diagnostics.virtualInvoke().totalEntries() > 0) {
                info("%s", diagnostics.virtualInvoke().summary());
            }
            if (diagnostics.sampling().totalEntries() > 0) {
                info("%s", diagnostics.sampling().summary());
            }
            if (diagnostics.callCount().totalEntries() > 0) {
                info("%s", diagnostics.callCount().summary());
            }
            /* Their entries count as unresolved; this line says why. */
            info("descriptors shared by two or more elements of the image, so that no profile entry is applied to them: %d method descriptors, %d type descriptors",
                            diagnostics.ambiguousImageMethods(), diagnostics.ambiguousImageTypes());
        } else {
            /* One line tells whether the profile matches this program; the details are opt-in. */
            info("profile resolved: %s; details with %s", resolutionSummary(diagnostics), SubstrateOptionsParser.commandArgument(Options.PGOPrintStatistics, "+"));
        }
        PGOApplyProfilesPhase.ProfileQuality.updateProfileRelevance(matchedEntries(diagnostics), totalEntries(diagnostics));
        reportUnusedCategories(diagnostics);
        warnAboutUnmatchedCategories(diagnostics);
    }

    /** Entries of all categories in {@code diagnostics}. */
    private static int totalEntries(PGODiagnostics diagnostics) {
        return diagnostics.conditional().totalEntries() + diagnostics.virtualInvoke().totalEntries() + diagnostics.sampling().totalEntries() + diagnostics.callCount().totalEntries();
    }

    /**
     * Entries of all categories in {@code diagnostics} that match the image; a sampled stack cut at
     * a missing frame still matches in part.
     */
    private static int matchedEntries(PGODiagnostics diagnostics) {
        return diagnostics.conditional().resolvedEntries() + diagnostics.virtualInvoke().resolvedEntries() + diagnostics.sampling().resolvedEntries() +
                        diagnostics.sampling().truncatedEntries() + diagnostics.callCount().resolvedEntries();
    }

    /** Names each category in {@code diagnostics} that options keep the build from using. */
    private static void reportUnusedCategories(PGODiagnostics diagnostics) {
        List<String> unused = new ArrayList<>();
        boolean hasConditionalEntries = diagnostics.conditional().totalEntries() > 0;
        addIfUnused(unused, hasConditionalEntries, PGOApplyProfilesPhase.Options.PGOUseBranchProfiles, "branch profiles");
        addIfUnused(unused, hasConditionalEntries, PGOApplyProfilesPhase.Options.PGOUseSwitchProfiles, "switch profiles");
        addIfUnused(unused, diagnostics.virtualInvoke().totalEntries() > 0, PGOApplyProfilesPhase.Options.PGOUseReceiverProfiles, "receiver profiles");
        addIfUnused(unused, diagnostics.callCount().totalEntries() > 0, Options.PGOUseCallCounts, "call counts");
        addIfUnused(unused, diagnostics.sampling().totalEntries() > 0, Options.PGOUseStackSamples, "stack samples");
        if (!unused.isEmpty()) {
            info("not used, as options say: %s", String.join(", ", unused));
        }
    }

    /** Adds {@code category} to {@code unused} if {@code inProfile} and {@code option} is off. */
    private static void addIfUnused(List<String> unused, boolean inProfile, HostedOptionKey<Boolean> option, String category) {
        if (inProfile && !option.getValue()) {
            unused.add(category + " (" + SubstrateOptionsParser.commandArgument(option, "-") + ")");
        }
    }

    /**
     * Warns about every category of data in {@code diagnostics} none of whose entries matches this
     * image, and about a profile without entries. Both usually mean that the profile was recorded
     * for another program. Then warns once about entries and receiver types that match only in
     * part.
     */
    private static void warnAboutUnmatchedCategories(PGODiagnostics diagnostics) {
        ConditionalDiagnostics conditionalDiagnostics = diagnostics.conditional();
        warnIfNoneMatches("branch and switch", conditionalDiagnostics.totalEntries(), conditionalDiagnostics.resolvedEntries());
        warnIfNoneMatches("receiver", diagnostics.virtualInvoke().totalEntries(), diagnostics.virtualInvoke().resolvedEntries());
        warnIfNoneMatches("call-count", diagnostics.callCount().totalEntries(), diagnostics.callCount().resolvedEntries());
        warnIfNoneMatches("stack-sample", diagnostics.sampling().totalEntries(), diagnostics.sampling().resolvedEntries() + diagnostics.sampling().truncatedEntries());
        if (totalEntries(diagnostics) == 0) {
            LogUtils.warning("The profile in %s has no entries, so it changes nothing in the image.", profileOption());
        }
        warnAboutMissingElements(diagnostics);
    }

    /**
     * Warns once when the image lacks some of the methods and types that the entries in
     * {@code diagnostics} name, for every category of which other entries match. The instrumented
     * build compiled code that this build does not have, for example because the program changed.
     * A category that matches nowhere has its own warning. A sampled stack counts when any of its
     * frames is missing, also when only an outer frame is and the inner part of the stack is still
     * used.
     */
    private static void warnAboutMissingElements(PGODiagnostics diagnostics) {
        ConditionalDiagnostics conditionalDiagnostics = diagnostics.conditional();
        long missingEntries = missingIfOthersMatch(conditionalDiagnostics.resolvedEntries(), conditionalDiagnostics.unresolvedEntries());
        VirtualInvokeDiagnostics virtualInvokeDiagnostics = diagnostics.virtualInvoke();
        missingEntries += missingIfOthersMatch(virtualInvokeDiagnostics.resolvedEntries(), virtualInvokeDiagnostics.unresolvedEntries() + virtualInvokeDiagnostics.noReceiverInImageEntries());
        long droppedReceiverTypes = virtualInvokeDiagnostics.resolvedEntries() > 0 ? virtualInvokeDiagnostics.droppedReceiverRecords() : 0;
        CallCountDiagnostics callCountDiagnostics = diagnostics.callCount();
        missingEntries += missingIfOthersMatch(callCountDiagnostics.resolvedEntries(), callCountDiagnostics.unresolvedEntries());
        SamplingDiagnostics samplingDiagnostics = diagnostics.sampling();
        /* As in warnAboutUnmatchedCategories, a stack cut at a missing frame matches in part. */
        missingEntries += missingIfOthersMatch(samplingDiagnostics.resolvedEntries() + samplingDiagnostics.truncatedEntries(),
                        samplingDiagnostics.unresolvedEntries() + samplingDiagnostics.truncatedEntries());
        if (missingEntries > 0 || droppedReceiverTypes > 0) {
            LogUtils.warning("%d entries and %d receiver types of the profile in %s name methods or types that this image does not have, or has twice under one name, " +
                            "so they are not used, or, for a sampled stack, used only in part. The profile was probably recorded for another version of the program. %s lists them.",
                            missingEntries, droppedReceiverTypes, profileOption(), SubstrateOptionsParser.commandArgument(PGOApplyProfilesPhase.Options.PGOPrintProfileQualityDetails, "+"));
        }
    }

    /** {@code missing}, or 0 if no entry is {@code matched}: that case has its own warning. */
    private static int missingIfOthersMatch(int matched, int missing) {
        return matched > 0 ? missing : 0;
    }

    /** Warns if {@code category} has {@code entries} but none of them is {@code matched}. */
    private static void warnIfNoneMatches(String category, int entries, int matched) {
        if (entries > 0 && matched == 0) {
            LogUtils.warning("None of the %d %s entries of the profile in %s matches this image, so they are not used. The profile may have been recorded for another program.",
                            entries, category, profileOption());
        }
    }

    @Override
    public void registerGraalPhases(Providers providers, Suites suites, boolean hosted, boolean fallback) {
        /*
         * The profile describes the image's ahead-of-time code, so the suites for runtime
         * compilation (hosted is false) get no PGO phases. Nor do the fallback suites, which retry
         * a method whose regular compilation failed with fewer optimizations, or hosted suites
         * created before beforeCompilation, which sets hostedUniverse.
         */
        if (!hosted || fallback || hostedUniverse == null) {
            return;
        }
        PhaseSuite<HighTierContext> highTier = suites.getHighTier();
        if (samplingHotness != null) {
            installProfiledInliner(highTier);
        }
        if (profilesLookup != null) {
            /*
             * Each root gets its profiles right before the inliner: the inliner then decides with
             * the root's profiled branch probabilities and receiver types, and the profile meets
             * the graph in the shape that --pgo-instrument counted, because the instrumentation
             * phases run at the same point. The callees get theirs when the inliner expands them
             * (SubstratePriorityInliningPhase.applyPGOProfiles). Without an inliner the phase runs
             * first in the high tier.
             */
            ApplyRootProfilesPhase applyRootProfiles = new ApplyRootProfilesPhase(hostedUniverse, profilesLookup, samplingHotness);
            ListIterator<BasePhase<? super HighTierContext>> inliner = highTier.findPhase(AbstractInliningPhase.class);
            if (inliner != null) {
                inliner.previous();
                inliner.add(applyRootProfiles);
            } else {
                highTier.prependPhase(applyRootProfiles);
            }
        }
    }

    /**
     * Replaces the priority inliner in {@code highTier} with a copy whose inlining provider knows
     * sampled hotness and call counts, through the copy constructor that
     * {@link SubstratePriorityInliningPhase} has for replacing the provider. The phase keeps its
     * position in the suite.
     */
    private void installProfiledInliner(PhaseSuite<HighTierContext> highTier) {
        ListIterator<BasePhase<? super HighTierContext>> position = highTier.findPhase(SubstratePriorityInliningPhase.class);
        if (position == null) {
            return;
        }
        SubstratePriorityInliningPhase current = (SubstratePriorityInliningPhase) position.previous();
        position.set(new SubstratePriorityInliningPhase(current, new ProfiledInliningProvider(hostedUniverse, samplingHotness, profilesLookup), profilesLookup));
    }

    /**
     * Applies the profile to a compilation root before the inliner. {@link PGOApplyProfilesPhase}
     * is a single-run subphase, so a new one is created for every graph.
     */
    private static final class ApplyRootProfilesPhase extends BasePhase<HighTierContext> {
        /** The image's universe, to map profile entries to hosted methods. */
        private final HostedUniverse universe;
        /** The resolved profile. */
        private final IprofProfilesLookup profilesLookup;
        /** Gives each root its global profile provider; {@code null} without sampled hotness. */
        private final SamplingHotness hotness;

        private ApplyRootProfilesPhase(HostedUniverse universe, IprofProfilesLookup profilesLookup, SamplingHotness hotness) {
            this.universe = universe;
            this.profilesLookup = profilesLookup;
            this.hotness = hotness;
        }

        @Override
        protected void run(StructuredGraph graph, HighTierContext context) {
            if (hotness != null && graph.method() instanceof HostedMethod method) {
                /* Runs before the inliner and duplication, which read the provider from the root graph. */
                graph.setGlobalProfileProvider(hotness.providerFor(method));
            }
            PGOApplyProfilesPhase.createContextInsensitive(universe, profilesLookup).apply(graph, context);
        }
    }

    /**
     * Matched entries per category in {@code diagnostics}, e.g.
     * {@code branches and switches 7505/7505, receivers 1610/1615}. A sampled stack cut at a frame
     * that the image lacks counts as matched: its inner part is used.
     */
    private static String resolutionSummary(PGODiagnostics diagnostics) {
        StringBuilder result = new StringBuilder();
        result.append("branches and switches ").append(diagnostics.conditional().resolvedEntries()).append('/').append(diagnostics.conditional().totalEntries());
        if (diagnostics.virtualInvoke().totalEntries() > 0) {
            result.append(", receivers ").append(diagnostics.virtualInvoke().resolvedEntries()).append('/').append(diagnostics.virtualInvoke().totalEntries());
        }
        if (diagnostics.callCount().totalEntries() > 0) {
            result.append(", call counts ").append(diagnostics.callCount().resolvedEntries()).append('/').append(diagnostics.callCount().totalEntries());
        }
        if (diagnostics.sampling().totalEntries() > 0) {
            SamplingDiagnostics samplingDiagnostics = diagnostics.sampling();
            result.append(", stack samples ").append(samplingDiagnostics.resolvedEntries() + samplingDiagnostics.truncatedEntries()).append('/').append(samplingDiagnostics.totalEntries());
        }
        return result.toString();
    }

    /** Prints the profile quality and, with {@code PGOPrintStatistics}, the application report. */
    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        if (profilesLookup != null && (PGOApplyProfilesPhase.Options.PGOPrintProfileQuality.getValue() || PGOApplyProfilesPhase.Options.PGOPrintProfileQualityDetails.getValue())) {
            reportProfileQuality(profilesLookup);
        }
        if (!printStatistics()) {
            return;
        }
        reportApplication(profilesLookup);
        if (samplingHotness != null) {
            info("sampling hotness: %d compilation roots with samples, %d without", samplingHotness.sampledRootCount(), samplingHotness.unsampledRootCount());
        }
    }

    /** Prints how the compiler used each category of the profile {@code lookup}, one line each. */
    private static void reportApplication(IprofProfilesLookup lookup) {
        if (lookup == null) {
            return;
        }
        long conditionalHits = lookup.conditionalHits();
        long conditionalQueries = conditionalHits + lookup.conditionalMisses();
        long fallbackHits = lookup.conditionalContextFallbackHits();
        int matchedContexts = lookup.matchedConditionalContextCount();
        int availableContexts = lookup.availableConditionalContextCount();
        info("branches and switches: %d queries, %d hits (%.1f%%, %d via shortened context, avg %.1f frames dropped), %d misses; " +
                        "contexts: %d/%d used (%.1f%%), %d fully applied, %d partially applied, %d matched-not-applied, %d unused",
                        conditionalQueries, conditionalHits, conditionalQueries == 0 ? 0.0 : 100.0 * conditionalHits / conditionalQueries, fallbackHits,
                        fallbackHits == 0 ? 0.0 : (double) lookup.conditionalContextFallbackDroppedFrames() / fallbackHits, lookup.conditionalMisses(),
                        matchedContexts, availableContexts, availableContexts == 0 ? 0.0 : 100.0 * matchedContexts / availableContexts, lookup.fullyAppliedConditionalContextCount(),
                        lookup.partiallyAppliedConditionalContextCount(), lookup.unappliedMatchedConditionalContextCount(), lookup.unusedConditionalContextCount());
        if (lookup.availableVirtualInvokeContextCount() > 0) {
            long receiverFallbackHits = lookup.virtualInvokeContextFallbackHits();
            info("virtual invokes: %d queries, %d hits (%d via shortened context, avg %.1f frames dropped), %d misses; contexts: %d/%d used; impossible receivers dropped: %d records, %d events",
                            lookup.virtualInvokeHits() + lookup.virtualInvokeMisses(), lookup.virtualInvokeHits(), receiverFallbackHits,
                            receiverFallbackHits == 0 ? 0.0 : (double) lookup.virtualInvokeContextFallbackDroppedFrames() / receiverFallbackHits, lookup.virtualInvokeMisses(),
                            lookup.matchedVirtualInvokeContextCount(), lookup.availableVirtualInvokeContextCount(), lookup.impossibleReceiverRecords(), lookup.impossibleReceiverEvents());
        }
        if (lookup.diagnostics().callCount().totalEntries() > 0) {
            info("call counts: %d call-edge lookups, %d found a count; %d method lookups, %d found a count",
                            lookup.contextCallCountHits() + lookup.contextCallCountMisses(), lookup.contextCallCountHits(), lookup.methodCallCountHits() + lookup.methodCallCountMisses(),
                            lookup.methodCallCountHits());
        }
    }

    /**
     * Prints the report of {@code PGOPrintProfileQuality} for {@code lookup}. Relevance is the
     * share of profile entries that match this image; applicability is the share of profile
     * lookups in compilation roots that found a profile. {@code PGOPrintProfileQualityDetails}
     * adds every entry dropped while loading the profile and every root context that found no
     * profile.
     */
    private static void reportProfileQuality(IprofProfilesLookup lookup) {
        PGODiagnostics diagnostics = lookup.diagnostics();
        info("profile relevance %.1f%%: %d of %d profile entries match this image", PGOApplyProfilesPhase.ProfileQuality.getProfileRelevance(), matchedEntries(diagnostics),
                        totalEntries(diagnostics));
        long found = PGOApplyProfilesPhase.ProfileQuality.getProfileSuccessCount();
        long missing = PGOApplyProfilesPhase.ProfileQuality.getProfileFailCount();
        info("profile applicability %.1f%%: %d of %d lookups in %d compilation roots found a profile", PGOApplyProfilesPhase.ProfileQuality.getProfileApplicability(), found, found + missing,
                        PGOApplyProfilesPhase.ProfileQuality.getFunctionsCount());
        if (PGOApplyProfilesPhase.Options.PGOPrintProfileQualityDetails.getValue()) {
            for (String dropped : diagnostics.droppedEntries()) {
                info("dropped while loading: %s", dropped);
            }
            for (NodeSourcePosition context : PGOApplyProfilesPhase.ProfileQuality.getProfileFailContexts()) {
                info("no profile for %s", describe(context));
            }
            info("example resolved conditional contexts: %s", lookup.conditionalContextExamples(10));
        }
    }

    /**
     * {@code context} on one line, innermost frame first, such as
     * {@code java.lang.String.charAt(int):12 < com.example.Foo.bar():5}.
     */
    private static String describe(NodeSourcePosition context) {
        StringBuilder result = new StringBuilder();
        for (NodeSourcePosition position = context; position != null; position = position.getCaller()) {
            result.append(result.isEmpty() ? "" : " < ").append(position.getMethod().format("%H.%n(%p)")).append(':').append(position.getBCI());
        }
        return result.toString();
    }
}
