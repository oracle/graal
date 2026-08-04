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
package com.oracle.svm.hosted.pgo.phases;

import static com.oracle.svm.hosted.pgo.phases.PGOApplyProfilesPhase.Options.PGOPrintProfileQuality;
import static com.oracle.svm.hosted.pgo.phases.PGOApplyProfilesPhase.Options.PGOPrintProfileQualityDetails;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.EXTREMELY_SLOW_PATH_PROBABILITY;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.hosted.cai.PrefixTree;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.pgo.PGOUtils;
import com.oracle.svm.hosted.pgo.ProfilingUtilities;
import com.oracle.svm.hosted.pgo.profiles.PGOProfilesLookup;
import com.oracle.svm.shared.option.HostedOptionKey;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.IndirectCallTargetNode;
import jdk.graal.compiler.nodes.ProfileData;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.phases.SingleRunSubphase;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class PGOApplyProfilesPhase extends SingleRunSubphase<HighTierContext> {

    public static class Options {
        // @formatter:off
        @Option(help = "Print a list of all calling-contexts that are dropped while loading profiles, as well as all contexts for which no profiles where found. NOTE: this is very verbose.")//
        public static final HostedOptionKey<Boolean> PGOPrintProfileQualityDetails = new HostedOptionKey<>(false);

        @Option(help = "Print the quality metrics (relevance and applicability) for the provided profiles i.e. iprof file(s).")//
        public static final HostedOptionKey<Boolean> PGOPrintProfileQuality = new HostedOptionKey<>(false);
        // @formatter:on
    }

    private static final String PGO_APPLY_PROFILES_PHASE = PGOApplyProfilesPhase.class.getSimpleName();
    private static final String CONDITIONAL_PROFILES = "conditionalProfiles";
    private static final String VIRTUAL_INVOKE_PROFILES = "virtualInvokeProfiles";
    private static final String VIRTUAL_INVOKE_METHOD_PROFILES = "virtualInvokeMethodProfiles";
    private static final String INSTANCE_OF_PROFILES = "instanceOfProfiles";
    private static final int INVALID_BRANCH_BCI = -5;
    public static final int CONDITIONAL_RECORD_SIZE = 3;
    private static final int CONDITIONAL_RECORD_BCI_POSITION = 0;
    public static final int CONDITIONAL_RECORD_KEY_POSITION = 1;
    public static final int CONDITIONAL_RECORD_COUNTER_POSITION = 2;
    private final PGOProfilesLookup pgoProfiles;
    private final NodeSourcePosition inliningContext;
    private final HostedUniverse hUniverse;
    private final PrefixTree.Cursor compilationRootContext;
    /**
     * If this flag is set the graph is considered hot even if the
     * {@link jdk.graal.compiler.nodes.StructuredGraph.GlobalProfileProvider} says otherwise. This
     * is to ensure that we apply sampling based profiles correctly during expansion of
     * {@link CutoffNode cutoff nodes} if
     * the compilation root is hot.
     */
    private final boolean forceHot;
    /**
     * Defines which of the profile quality fields ({@link ProfileQuality}) are being updated.
     * <p>
     * Should be tracking only when applying
     * {@link #createContextInsensitive(HostedUniverse, PGOProfilesLookup) context insensitive
     * profiles} and if PGOPrintProfileQuality and PGOPrintProfileQualityDetails are set
     * (respectively).
     * <p>
     */
    private final ProfileQuality.Level profileQualityLevel;

    public static PGOApplyProfilesPhase createForExpandingHotCutoffs(NodeSourcePosition inliningContext, HostedUniverse hUniverse, PrefixTree.Cursor compilationRootContext,
                    PGOProfilesLookup pgoProfiles) {
        return new PGOApplyProfilesPhase(inliningContext, hUniverse, compilationRootContext, pgoProfiles, true, ProfileQuality.Level.NOT_TRACKING);
    }

    public static PGOApplyProfilesPhase createForExpandingCutoffs(NodeSourcePosition inliningContext, HostedUniverse hUniverse, PGOProfilesLookup pgoProfiles) {
        return new PGOApplyProfilesPhase(inliningContext, hUniverse, null, pgoProfiles, false, ProfileQuality.Level.NOT_TRACKING);
    }

    public static PGOApplyProfilesPhase createForBeforeHotCompilationPhase(HostedUniverse hUniverse, PrefixTree.Cursor compilationRootContext, PGOProfilesLookup pgoProfiles) {
        return new PGOApplyProfilesPhase(null, hUniverse, compilationRootContext, pgoProfiles, false, ProfileQuality.Level.NOT_TRACKING);
    }

    public static PGOApplyProfilesPhase createContextInsensitive(HostedUniverse hUniverse, PGOProfilesLookup pgoProfiles) {

        ProfileQuality.Level level = ProfileQuality.Level.NOT_TRACKING;
        if (PGOPrintProfileQuality.getValue()) {
            level = ProfileQuality.Level.TRACKING;
            if (PGOPrintProfileQualityDetails.getValue()) {
                level = ProfileQuality.Level.TRACKING_DETAILS;
            }
        }
        return new PGOApplyProfilesPhase(null, hUniverse, null, pgoProfiles, false, level);
    }

    private PGOApplyProfilesPhase(NodeSourcePosition inliningContext, HostedUniverse hUniverse, PrefixTree.Cursor compilationRootContext,
                    PGOProfilesLookup pgoProfiles, boolean forceHot, ProfileQuality.Level level) {
        this.pgoProfiles = pgoProfiles;
        this.inliningContext = inliningContext;
        this.hUniverse = hUniverse;
        this.compilationRootContext = compilationRootContext;
        this.forceHot = forceHot;
        this.profileQualityLevel = level;
    }

    /// Returns the bytecode indexes extracted from the fixed-size entries in a conditional profile
    /// record.
    public static int[] bytecodeIndicesForConditionals(long[] records) {
        int[] byteCodeIndexes = new int[records.length / CONDITIONAL_RECORD_SIZE];
        for (int i = CONDITIONAL_RECORD_BCI_POSITION; i < records.length; i += CONDITIONAL_RECORD_SIZE) {
            byteCodeIndexes[i / CONDITIONAL_RECORD_SIZE] = (int) records[i];
        }
        return byteCodeIndexes;
    }

    public static boolean validConditionalBci(long bci) {
        return bci != INVALID_BRANCH_BCI;
    }

    public static int[] conditionalMappings(long[] records) {
        /*
         * We map branches that share the same target in order to calculate corresponding
         * probabilities.
         */
        int[] mappings = new int[records.length / PGOApplyProfilesPhase.CONDITIONAL_RECORD_SIZE];
        for (int i = CONDITIONAL_RECORD_KEY_POSITION; i < records.length; i += PGOApplyProfilesPhase.CONDITIONAL_RECORD_SIZE) {
            mappings[i / PGOApplyProfilesPhase.CONDITIONAL_RECORD_SIZE] = (int) records[i];
        }
        return mappings;
    }

    public static int conditionalRecordCount(int recordsLength) {
        return recordsLength / CONDITIONAL_RECORD_SIZE;
    }

    /**
     * Calculates the probabilities for the conditional successors based on the records provided by
     * the profile.
     *
     * @return Optionally an array of doubles containing a probability value for each branch of the
     *         conditional.
     */
    public static Optional<double[]> distributeConditionalProbabilities(long[] records) {
        int maxKey = 0;
        for (int i = 0; i < records.length; i += CONDITIONAL_RECORD_SIZE) {
            if (records[i + CONDITIONAL_RECORD_KEY_POSITION] > maxKey) {
                /*
                 * Maximal key in case some don't exist. This happens if some branches have negative
                 * bci values.
                 */
                maxKey = (int) records[i + CONDITIONAL_RECORD_KEY_POSITION];
            }
        }
        long[] sumByKey = new long[maxKey + 1];
        int[] mapByKey = new int[maxKey + 1];
        for (int i = 0; i < records.length; i += CONDITIONAL_RECORD_SIZE) {
            mapByKey[(int) records[i + CONDITIONAL_RECORD_KEY_POSITION]]++;
            sumByKey[(int) records[i + CONDITIONAL_RECORD_KEY_POSITION]] += records[i + CONDITIONAL_RECORD_COUNTER_POSITION];
        }
        long sum = Arrays.stream(sumByKey).sum();
        if (sum == 0) {
            /* We insert this check in case not-executed points are written in profiles. */
            return Optional.empty();
        }
        int branchNum = conditionalRecordCount(records.length);
        double[] probabilities = new double[branchNum];
        for (int i = 0; i < branchNum; i++) {
            int key = (int) records[i * CONDITIONAL_RECORD_SIZE + CONDITIONAL_RECORD_KEY_POSITION];
            probabilities[i] = (1.0 * sumByKey[key]) / (mapByKey[key] * sum);
        }
        return Optional.of(clampProbabilities(probabilities));
    }

    /**
     * Ensures that all the 0 values for probabilities are actually EXTREMELY_SLOW_PATH_PROBABILITY,
     * while ensuring the other values are fairly decreased to maintain the probability total.
     */
    static double[] clampProbabilities(double[] probabilities) {
        /*
         * To avoid branches with zero probabilities, they are set to
         * EXTREMELY_SLOW_PATH_PROBABILITY. To obtain one in sum, we subtract the extra from other
         * branches using scaling to perform a fair probability distribution.
         */
        assertProbabilities(probabilities);
        int zeroProbabilityCnt = (int) Arrays.stream(probabilities).filter(p -> p == 0.0).count();
        if (zeroProbabilityCnt == 0) {
            return probabilities;
        }
        double adjustment = zeroProbabilityCnt * EXTREMELY_SLOW_PATH_PROBABILITY;
        double scaleFactor = 1 / (1 - (probabilities.length - zeroProbabilityCnt) * EXTREMELY_SLOW_PATH_PROBABILITY);
        double[] adjustedProbabilities = Arrays.stream(probabilities).map(p -> clampProbability(p, adjustment, scaleFactor)).toArray();
        assertProbabilities(adjustedProbabilities);
        return adjustedProbabilities;
    }

    private static double clampProbability(double probability, double adjustment, double scaleFactor) {
        return probability == 0.0 ? EXTREMELY_SLOW_PATH_PROBABILITY : probability - adjustment * (probability - EXTREMELY_SLOW_PATH_PROBABILITY) * scaleFactor;
    }

    private static void assertProbabilities(double[] probabilities) {
        double sum = Arrays.stream(probabilities).sum();
        assert sum > 0.999 && sum < 1.001 : "Total probability sum is :" + sum;
    }

    @Override
    protected void run(StructuredGraph graph, HighTierContext phaseContext) {
        try (DebugContext.Scope _ = graph.getDebug().scope(PGO_APPLY_PROFILES_PHASE)) {
            incrementMethodCounter();
            updateProfilesForInvokes(graph);
            updateProfilesForConditionals(graph);
            updateProfilesForInstanceofs(graph);
        }
    }

    private void incrementMethodCounter() {
        if (profileQualityLevel.includes(ProfileQuality.Level.TRACKING)) {
            ProfileQuality.functions.incrementAndGet();
        }
    }

    private void updateProfilesForConditionals(StructuredGraph graph) {
        if (!pgoProfiles.profileCategoryRecorded(CONDITIONAL_PROFILES)) {
            return;
        }
        forEachRelevantControlSplitNode(graph, this::updateConditionalProbabilitiesBasedOnSamples);
        forEachRelevantControlSplitNode(graph, this::updateConditionalProbabilities);
    }

    private void updateProfilesForInvokes(StructuredGraph graph) {
        if (!pgoProfiles.profileCategoryRecorded(VIRTUAL_INVOKE_PROFILES) && !pgoProfiles.profileCategoryRecorded(VIRTUAL_INVOKE_METHOD_PROFILES)) {
            return;
        }
        Consumer<MethodCallTargetNode> updateInvokeProfile = forceHot || graph.globalProfileProvider().hotCaller() ?  //
                        this::updateInvokeProfileForHotCaller : //
                        this::updateInvokeProfileForColdCompilationUnit;
        forEachIndirectInvoke(graph, updateInvokeProfile);
    }

    /**
     * Updates profiles of {@link InstanceOfNode} with the type occurrence information gathered
     * during profiling.
     */
    private void updateProfilesForInstanceofs(StructuredGraph graph) {
        if (!pgoProfiles.profileCategoryRecorded(INSTANCE_OF_PROFILES)) {
            return;
        }
        for (InstanceOfNode iof : graph.getNodes().filter(InstanceOfNode.class)) {
            NodeSourcePosition context = createPointContext(iof.getNodeSourcePosition(), inliningContext);
            Optional<Map<JavaType, Long>> typeOccurrences = pgoProfiles.getInstanceofProfile(context);

            if (typeOccurrences.isPresent()) {
                // collect type occurrence information
                Optional<Map<AnalysisType, Long>> resolvedTypeOccurrences = Optional.of(typeOccurrences.get().entrySet().stream().filter(entry -> entry.getKey() instanceof AnalysisType).collect(
                                Collectors.toMap(e -> (AnalysisType) e.getKey(), Map.Entry::getValue)));
                if (!resolvedTypeOccurrences.get().isEmpty()) {
                    // update an existing profile or create one with the type occurrence information
                    var updatedTypeProfile = PGOUtils.updateJavaTypeProfile(iof.profile(), resolvedTypeOccurrences, hUniverse, true);
                    iof.setProfile(updatedTypeProfile, iof.getAnchor());
                    countSuccess();
                } else {
                    countFailure(context);
                }
            } else {
                countFailure(context);
            }
        }
    }

    private void updateInvokeProfileForColdCompilationUnit(MethodCallTargetNode methodCallTargetNode) {
        updateInvokeWithNewProfiles(methodCallTargetNode);
    }

    private static void forEachIndirectInvoke(StructuredGraph graph, Consumer<MethodCallTargetNode> consumer) {
        List<MethodCallTargetNode> callTargetNodes = graph.getNodes(MethodCallTargetNode.TYPE).snapshot();
        for (MethodCallTargetNode callTarget : callTargetNodes) {
            if (callTarget.invokeKind().isIndirect()) {
                consumer.accept(callTarget);
            }
        }
    }

    private void updateInvokeProfileForHotCaller(MethodCallTargetNode callTarget) {
        updateInvokeProfileForColdCompilationUnit(callTarget);
        if (compilationRootContext == null) {
            // This will happen for recursive hot calls
            return;
        }
        JavaMethodProfile methodProfile = validateProfile(callTarget, compilationRootContext.profileFor(hUniverse, callTarget.getNodeSourcePosition()));
        if (methodProfile != null) {
            // TODO BS GR-42090 Consider having Java method-sampling profile.
            ((SubstrateMethodCallTargetNode) callTarget).setJavaMethodProfile(methodProfile);
        }
    }

    private static Set<ResolvedJavaMethod> getVirtualMethodImplementations(CallTargetNode callTarget) {
        if (callTarget == null || callTarget.targetMethod() == null) {
            return null;
        }

        HostedMethod hostedMethod = (HostedMethod) callTarget.targetMethod();
        Set<ResolvedJavaMethod> implementations = new HashSet<>(Arrays.asList(hostedMethod.getImplementations()));
        implementations.add(hostedMethod);
        return implementations;
    }

    /**
     * Ensures that the given {@link JavaMethodProfile} is valid for the given
     * {@link CallTargetNode}.
     *
     * In practice this means filtering out methods in the profile that aren't implementations of
     * the call target. Normally this cannot happen through normal Java semantics, but when applying
     * profiles gathered through instrumentation/sampling we could end up with calls that are, for
     * example, inserted by SVM into the executable (e.g. enterSlowPathSafepointCheckObject).
     *
     * @param callTarget The target for which we wish to verify the profile.
     * @param methodProfile The profile we wish to verify.
     * @return A valid version of the give profile or null if not possible.
     */
    // TODO BS GR-50363 This method should not need to be public.
    public static JavaMethodProfile validateProfile(CallTargetNode callTarget, JavaMethodProfile methodProfile) {
        Set<ResolvedJavaMethod> implementations = getVirtualMethodImplementations(callTarget);
        if (methodProfile == null || implementations == null) {
            return callTarget instanceof IndirectCallTargetNode ? methodProfile : null;
        }
        List<JavaMethodProfile.ProfiledMethod> validMethods = new ArrayList<>();
        double invalidMethodProbability = methodProfile.getNotRecordedProbability();
        boolean profileValid = true;
        for (JavaMethodProfile.ProfiledMethod profiledMethod : methodProfile.getMethods()) {
            if (implementations.contains(profiledMethod.getMethod())) {
                validMethods.add(profiledMethod);
            } else {
                profileValid = false;
                invalidMethodProbability += profiledMethod.getProbability();
            }
        }
        if (validMethods.isEmpty()) {
            /*
             * GR-66538 means that sometimes, no targets indicated in the profile are actually
             * possible targets. We return null to avoid applying an empty profile. Such an empty
             * profile would have no method targets, and a not recorded probability of 1 + epsilon
             * (since the probability of invalid targets is folded into the not recorded
             * probability). Even after a fix to GR-66538, this check can be kept as a safeguard in
             * case other situations arise that can result in an empty profile.
             */
            return null;
        }
        // Avoid allocating a new JavaMethodProfile if the given one is valid.
        return profileValid ? methodProfile : new JavaMethodProfile(invalidMethodProbability, validMethods.toArray(new JavaMethodProfile.ProfiledMethod[0]));
    }

    /**
     * Includes the name of the declaring class in the formatted string. Prints in method name
     * lexicographic order so we can easily match the output with a regex.
     */
    private static String formatJavaMethodProfile(JavaMethodProfile profile) {
        if (profile == null) {
            return "NULL PROFILE";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        if (profile.getMethods() != null) {
            List<JavaMethodProfile.ProfiledMethod> sortedMethods = Arrays.stream(profile.getMethods()).sorted((meth1, meth2) -> {
                String fullName1 = meth1.getMethod().format("%H.%n");
                String fullName2 = meth2.getMethod().format("%H.%n");
                return fullName1.compareTo(fullName2);
            }).toList();
            for (JavaMethodProfile.ProfiledMethod meth : sortedMethods) {
                builder.append('{');
                builder.append(meth.getMethod().format("%H.%n"));
                builder.append(", ");
                builder.append(meth.getProbability());
                builder.append("}, ");
            }
        }
        builder.append(profile.getNotRecordedProbability());
        builder.append("]");
        return builder.toString();
    }

    private void updateInvokeWithNewProfiles(MethodCallTargetNode callTarget) {
        NodeSourcePosition context = createPointContext(callTarget.getNodeSourcePosition(), inliningContext);
        Optional<Map<AnalysisType, Long>> typeOccurrences = pgoProfiles.getVirtualInvokeProfile(context);
        Optional<Map<AnalysisMethod, Long>> methodOccurrences = pgoProfiles.getVirtualInvokeMethodProfile(context);
        boolean appliedProfile = false;
        if (methodOccurrences.isPresent()) {
            /*
             * Set initial method profile. Profiles inferred from type occurrences will override
             * this if present.
             *
             * TODO: GR-66859 - Maybe there is a way to merge profiles generated from method
             * occurrences with those inferred from type occurrences?
             */
            SubstrateMethodCallTargetNode substrateCallTarget = (SubstrateMethodCallTargetNode) callTarget;
            // The static method profile, if it exists, gives equal probability to all possible
            // callees.
            JavaMethodProfile staticMethodProfile = substrateCallTarget.getStaticMethodProfile();
            /*
             * GR-66538 - Method profiles generated from samples might include impossible callees
             * due to some issue with how samples are collected. [updateJavaMethodProfile] will
             * filter those out using [possibleTargets] and [staticMethodProfile], if present. If
             * all entries in the new profile get filtered out then give up and do not overwrite the
             * existing profile.
             */
            Set<ResolvedJavaMethod> possibleTargets = getVirtualMethodImplementations(callTarget);
            JavaMethodProfile newMethodProfile = PGOUtils.overwriteJavaMethodProfile(staticMethodProfile, methodOccurrences.get(), possibleTargets, hUniverse, true);
            if (newMethodProfile.getMethods().length > 0) {
                callTarget.graph().getDebug().log("Created method profile for context:%n%s%nProfile: %s", context, formatJavaMethodProfile(newMethodProfile));
                substrateCallTarget.setJavaMethodProfile(newMethodProfile);
                appliedProfile = true;
            }
        }
        if (typeOccurrences.isPresent()) {
            SubstrateMethodCallTargetNode substrateCallTarget = (SubstrateMethodCallTargetNode) callTarget;
            JavaTypeProfile javaTypeProfile = PGOUtils.updateJavaTypeProfile(substrateCallTarget.getTypeProfile(), typeOccurrences, hUniverse, true);
            JavaMethodProfile javaMethodProfile = PGOUtils.updateJavaMethodProfile(substrateCallTarget.getMethodProfile(), javaTypeProfile);
            callTarget.graph().getDebug().log("Inferred method profile for context:%n%s%nProfile: %s", context, formatJavaMethodProfile(javaMethodProfile));
            substrateCallTarget.setDynamicProfiles(javaTypeProfile, javaMethodProfile);
            appliedProfile = true;
        }
        if (appliedProfile) {
            countSuccess();
        } else {
            callTarget.graph().getDebug().log("Failed to obtain method profile for context:%n%s", context);
            countFailure(context);
        }
    }

    private void countSuccess() {
        if (profileQualityLevel.includes(ProfileQuality.Level.TRACKING)) {
            ProfileQuality.profileSuccessCounter.incrementAndGet();
        }
    }

    private void countFailure(NodeSourcePosition context) {
        if (profileQualityLevel.includes(ProfileQuality.Level.TRACKING)) {
            ProfileQuality.profileFailCounter.incrementAndGet();
            if (profileQualityLevel.includes(ProfileQuality.Level.TRACKING_DETAILS)) {
                ProfileQuality.profileFailContexts.add(context);
            }
        }
    }

    private static void forEachRelevantControlSplitNode(StructuredGraph graph, Consumer<ControlSplitNode> nodeConsumer) {
        EconomicMap<NodeSourcePosition, List<ControlSplitNode>> conditionalNodes = ProfilingUtilities.relevantConditionalNodesFromGraph(graph);
        for (List<ControlSplitNode> nodes : conditionalNodes.getValues()) {
            for (ControlSplitNode node : nodes) {
                nodeConsumer.accept(node);
            }
        }
    }

    @SuppressWarnings("unused")
    private void updateConditionalProbabilitiesBasedOnSamples(ControlSplitNode controlSplitNode) {
        // TODO GR-51733 BS Infer conditional based on samples.
    }

    private void updateConditionalProbabilities(ControlSplitNode conditionalNode) {
        NodeSourcePosition context = createPointContext(conditionalNode.getNodeSourcePosition(), inliningContext);
        Optional<PGOProfilesLookup.ProfiledValue<long[]>> conditionalSuccessors = pgoProfiles.getConditionalProfile(context);
        conditionalSuccessors.ifPresentOrElse(s -> {
            setSuccessorsProbabilities(s.source(), s.value(), conditionalNode);
            countSuccess();
        }, () -> countFailure(context));
    }

    private static void setSuccessorsProbabilities(ProfileData.ProfileSource source, long[] conditionalSuccessors, ControlSplitNode conditionalNode) {
        List<Node> successors = conditionalNode.successors().snapshot();
        List<Node> aliveSuccessors = successors.stream().filter(Node::isAlive).collect(Collectors.toList());
        Optional<Map<Integer, Double>> aggregatedProbabilities = aggregatedProbabilities(conditionalSuccessors);
        if (aggregatedProbabilities.isEmpty()) {
            return;
        }
        List<Node> matchingProfiles = successorsMatchingProfiles(aliveSuccessors, aggregatedProbabilities.get());
        matchingProfiles.forEach(
                        s -> conditionalNode.setProbability((AbstractBeginNode) s, BranchProbabilityData.create(aggregatedProbabilities.get().get(s.getNodeSourcePosition().getBCI()), source)));
    }

    /**
     * We dump switch node cases in profiles as follows. Each branch record contains a bci, an index
     * of the case and a count. For those cases sharing the same target, one branch has a valid bci,
     * and the others a non-valid bci, and an index that matches the index of the corresponding case
     * with the valid bci. Here, we aggregate counts for all these cases and return them mapped to
     * valid bci values which will be contained in the {@link SwitchNode} successors. Then, we can
     * rewrite or verify aggregated probabilities in successor nodes.
     */
    public static Optional<Map<Integer, Double>> aggregatedProbabilities(long[] records) {
        int[] bcis = bytecodeIndicesForConditionals(records);
        int[] mappings = conditionalMappings(records);
        Optional<double[]> probabilities = distributeConditionalProbabilities(records);
        if (probabilities.isEmpty()) {
            return Optional.empty();
        }

        Map<Integer, Double> aggregatedProbabilitiesByKey = new HashMap<>();
        for (int i = 0; i < mappings.length; i++) {
            if (aggregatedProbabilitiesByKey.containsKey(mappings[i])) {
                aggregatedProbabilitiesByKey.put(mappings[i], aggregatedProbabilitiesByKey.get(mappings[i]) + probabilities.get()[i]);
            } else {
                aggregatedProbabilitiesByKey.put(mappings[i], probabilities.get()[i]);
            }
        }

        Map<Integer, Double> aggregatedProbabilitiesPerSuccessor = new HashMap<>();
        for (int i = 0; i < bcis.length; i++) {
            if (validConditionalBci(bcis[i])) {
                aggregatedProbabilitiesPerSuccessor.put(bcis[i], aggregatedProbabilitiesByKey.get(mappings[i]));
            }
        }
        return Optional.of(aggregatedProbabilitiesPerSuccessor);
    }

    public static List<Node> successorsMatchingProfiles(List<Node> successors, Map<Integer, Double> probabilities) {
        return successors.stream().filter(s -> probabilities.containsKey(s.getNodeSourcePosition().getBCI())).collect(Collectors.toList());
    }

    public static NodeSourcePosition createPointContext(NodeSourcePosition nsp, NodeSourcePosition inliningContext) {
        if (inliningContext == null) {
            return nsp;
        }
        // addCaller adds inliningContext's entire chain of calls.
        return nsp.addCaller(inliningContext);
    }

    public static final class ProfileQuality {

        /**
         * Counters for how many times looking up a profile returned a profile and how many times
         * not.
         * <p>
         * Only updated if
         * {@link Options#PGOPrintProfileQuality}
         * is set.
         */
        private static final AtomicLong profileSuccessCounter = new AtomicLong();
        private static final AtomicLong profileFailCounter = new AtomicLong();
        private static final AtomicLong numOfEntries = new AtomicLong();
        private static final AtomicLong numOfMatchedEntries = new AtomicLong();
        /**
         * Counters for how many methods the phase was applied to and a list of all contexts for
         * which no profile was provided. This peripherally meant for debugging purposes.
         * <p>
         * Only updated if
         * {@link Options#PGOPrintProfileQualityDetails}
         * is set, and methods are only counted when we are applying context insensitive profiles
         * (see {@link #createContextInsensitive})
         *
         */
        private static final AtomicLong functions = new AtomicLong();
        private static final List<NodeSourcePosition> profileFailContexts = Collections.synchronizedList(new ArrayList<>());

        // Should not instantiate
        private ProfileQuality() {
        }

        public static long getProfileSuccessCount() {
            return profileSuccessCounter.get();
        }

        public static long getProfileFailCount() {
            return profileFailCounter.get();
        }

        public static long getFunctionsCount() {
            return functions.get();
        }

        public static List<NodeSourcePosition> getProfileFailContexts() {
            return Collections.unmodifiableList(profileFailContexts);
        }

        public static double getProfileApplicability() {
            long successCounter = getProfileSuccessCount();
            long failCounter = getProfileFailCount();
            long totalCount = successCounter + failCounter;
            return totalCount == 0 ? 0.0 : 100.0 * successCounter / totalCount;
        }

        public static double getProfileRelevance() {
            long entries = numOfEntries.get();
            long matchedEntries = numOfMatchedEntries.get();
            return entries == 0 ? 0.0 : 100.0 * matchedEntries / entries;
        }

        public static void updateProfileRelevance(long matchedEntries, long totalEntries) {
            numOfEntries.addAndGet(totalEntries);
            numOfMatchedEntries.addAndGet(matchedEntries);
        }

        enum Level {
            NOT_TRACKING,
            /**
             * Tracks only the number of successful and failed profile lookups.
             *
             * see {@link ProfileQuality#profileSuccessCounter}
             */
            TRACKING,
            /**
             * Tracks the number of successful and failed profile lookups as well as all the
             * contexts for which a profile was not found and total number of methods to which the
             * tracking applies.
             */
            TRACKING_DETAILS;

            boolean includes(Level level) {
                return this.ordinal() >= level.ordinal();
            }
        }

    }
}
