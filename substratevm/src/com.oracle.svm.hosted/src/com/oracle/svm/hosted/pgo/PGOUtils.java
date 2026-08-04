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

import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.EXTREMELY_SLOW_PATH_PROBABILITY;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.infrastructure.Universe;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;

import jdk.vm.ci.meta.AbstractJavaProfile;
import jdk.vm.ci.meta.AbstractProfiledItem;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaMethodProfile.ProfiledMethod;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.JavaTypeProfile.ProfiledType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.TriState;

public final class PGOUtils {

    private PGOUtils() {
    }

    /**
     * Creates and returns a {@link JavaTypeProfile} which is based on an existing type profile (if
     * provided) and the type occurrence counts from profiling. An existing profile provides the
     * initial set of types, the nullness information and the {@code notRecordedProbability}. The
     * probabilities are based on the type occurrences from profiling. If the
     * {@code injectNotRecordedProbability} parameter is {@code true}, the
     * {@code notRecordedProbability} is guaranteed to be greater than 0.
     */
    public static JavaTypeProfile updateJavaTypeProfile(JavaTypeProfile typeProfile, Optional<Map<AnalysisType, Long>> typeOccurrences, Universe converter, boolean injectNotRecordedProbability) {
        Map<HostedType, Long> typeOccurrencesAfterAnalysis = adjustToStaticTypeProfiles(typeOccurrences.get(), typeProfile, converter);
        final double notRecordedProbability = computeNotRecordedProbability(typeProfile, injectNotRecordedProbability);

        Map<HostedType, Double> typeProbabilities = computeProbabilities(typeOccurrencesAfterAnalysis, notRecordedProbability);

        TriState nullSeen = typeProfile != null ? typeProfile.getNullSeen() : TriState.TRUE;
        ProfiledType[] profiledTypesArray = typeOccurrencesAfterAnalysis.keySet().stream()
                        .map(resolvedType -> new ProfiledType(resolvedType, typeProbabilities.get(resolvedType)))
                        .sorted()
                        .toArray(ProfiledType[]::new);

        return new JavaTypeProfile(nullSeen, notRecordedProbability, profiledTypesArray);
    }

    /**
     * Creates a {@link JavaMethodProfile} based on a static method profile and map of methods to
     * their occurrence count. The methods in the resulting profile will be a subset of
     * {@code possibleTargets} and {@code staticMethodProfile}, filtering out invalid targets.
     *
     * @param staticMethodProfile The static method profile, with potential targets and initial not
     *            recorded probability.
     * @param methodOccurrences A mapping of methods to their occurrence count, which can be
     *            gathered through sampling.
     * @param possibleTargets The set of possible implementations of the profiled virtual method.
     * @param converter The universe.
     * @param injectNotRecordedProbability Whether the not recorded probability should be clamped to
     *            a minimum epsilon.
     * @return A new {@link JavaMethodProfile} with invalid targets removed.
     */
    public static JavaMethodProfile overwriteJavaMethodProfile(JavaMethodProfile staticMethodProfile, Map<AnalysisMethod, Long> methodOccurrences, Set<ResolvedJavaMethod> possibleTargets,
                    Universe converter, boolean injectNotRecordedProbability) {
        Map<HostedMethod, Long> methodOccurrencesAfterAnalysis = filterInvalidTargets(methodOccurrences, staticMethodProfile, possibleTargets, converter);
        // TODO: GR-67461 - It might be better to throw away the static notRecordedProbability and
        // just use some small non zero value.
        final double notRecordedProbability = computeNotRecordedProbability(staticMethodProfile, injectNotRecordedProbability);

        Map<HostedMethod, Double> methodProbabilities = computeProbabilities(methodOccurrencesAfterAnalysis, notRecordedProbability);

        ProfiledMethod[] profiledMethodsArray = methodProbabilities.entrySet().stream()
                        .map(entry -> new ProfiledMethod(entry.getKey(), entry.getValue()))
                        .sorted()
                        .toArray(ProfiledMethod[]::new);

        return new JavaMethodProfile(notRecordedProbability, profiledMethodsArray);
    }

    /**
     * Computes a notRecordedProbability based on the existing profile and the option to inject a
     * notRecordedProbability greater than 0.
     */
    private static <T extends AbstractProfiledItem<U>, U> double computeNotRecordedProbability(AbstractJavaProfile<T, U> javaProfile, boolean injectNotRecordedProbability) {
        final double initialNotRecordedProbability = javaProfile == null ? EXTREMELY_SLOW_PATH_PROBABILITY : javaProfile.getNotRecordedProbability();
        final double injectedNotRecordedProbability = injectNotRecordedProbability ? EXTREMELY_SLOW_PATH_PROBABILITY : 0.0d;
        return Math.max(initialNotRecordedProbability, injectedNotRecordedProbability);
    }

    public static JavaMethodProfile updateJavaMethodProfile(JavaMethodProfile methodProfile, JavaTypeProfile newTypeProfile) {
        if (methodProfile == null) {
            return null;
        }
        ProfiledMethod[] profiledMethods = Arrays.stream(methodProfile.getMethods())
                        .map(profiledMethod -> createProfiledMethod(profiledMethod.getMethod(), newTypeProfile))
                        .sorted()
                        .toArray(ProfiledMethod[]::new);
        double notRecordedProbability = newTypeProfile.getNotRecordedProbability();

        return new JavaMethodProfile(notRecordedProbability, profiledMethods);
    }

    public static JavaMethodProfile createJavaMethodProfile(Map<HostedMethod, Long> methodOccurrences) {
        List<ProfiledMethod> profiledMethods = new ArrayList<>();
        long sum = methodOccurrences.values().stream().mapToLong(v -> v).sum();
        for (Map.Entry<HostedMethod, Long> entry : methodOccurrences.entrySet()) {
            HostedMethod method = entry.getKey();
            long count = entry.getValue();
            ProfiledMethod profiledMethod = new ProfiledMethod(method, (1.0 * count) / sum);
            profiledMethods.add(profiledMethod);
        }
        Collections.sort(profiledMethods);
        return new JavaMethodProfile(EXTREMELY_SLOW_PATH_PROBABILITY, profiledMethods.toArray(new ProfiledMethod[0]));
    }

    private static ProfiledMethod createProfiledMethod(ResolvedJavaMethod concreteMethod, JavaTypeProfile typeProfile) {
        double probability = 0.0;
        AnalysisMethod concreteAnalysisMethod = ((HostedMethod) concreteMethod).getWrapped();
        for (ProfiledType profiledType : typeProfile.getTypes()) {
            /* At this point hosted hubs are not built yet, so we must use wrapped AnalysisType. */
            AnalysisType receiverType = ((HostedType) profiledType.getType()).getWrapped();
            if (concreteAnalysisMethod.equals(receiverType.resolveConcreteMethod(concreteAnalysisMethod, concreteAnalysisMethod.getDeclaringClass()))) {
                probability += profiledType.getProbability();
            }
        }
        return new ProfiledMethod(concreteMethod, Math.min(1.0, probability));
    }

    private static Map<HostedType, Long> adjustToStaticTypeProfiles(Map<AnalysisType, Long> typeOccurrences, JavaTypeProfile typeProfile, Universe converter) {
        Map<HostedType, Long> hostedTypesMap = new HashMap<>();
        if (typeProfile != null) {
            /* Take all types seen by analysis with 0 occurrences. */
            Arrays.stream(typeProfile.getTypes()).forEach(t -> hostedTypesMap.put((HostedType) t.getType(), 0L));
        }
        /* Overwrite those for which we have profiles. */
        typeOccurrences.forEach((key, value) -> hostedTypesMap.put((HostedType) converter.lookup(key), value));
        return hostedTypesMap;
    }

    /**
     * Since methods recorded in the iprof file might not actually be possible targets in
     * {@code universe}, filter them out of the method occurrence map according to the possible
     * targets provided by {@code staticMethodProfile} and {@code possibleTargets}. Maps the
     * remaining entries to a {@link HostedMethod} using {@code universe}.
     *
     * @return {@link HostedMethod} to occurrence map after filtering out invalid methods.
     */
    private static Map<HostedMethod, Long> filterInvalidTargets(Map<AnalysisMethod, Long> iprofMethodOccurrences, JavaMethodProfile staticMethodProfile,
                    Set<ResolvedJavaMethod> possibleTargets, Universe universe) {
        Set<ResolvedJavaMethod> staticProfileTargets = staticMethodProfile == null ? null : Arrays.stream(staticMethodProfile.getMethods()).map(ProfiledMethod::getMethod).collect(Collectors.toSet());
        Map<HostedMethod, Long> filteredMethodOccurrences = new HashMap<>(iprofMethodOccurrences.size());
        for (Map.Entry<AnalysisMethod, Long> entry : iprofMethodOccurrences.entrySet()) {
            HostedMethod hostedMethodFromIprof = (HostedMethod) universe.lookup(entry.getKey());
            boolean entrySatisfiesPossibleTargets = possibleTargets == null || possibleTargets.contains(hostedMethodFromIprof);
            boolean entrySatisfiesStaticProfile = staticProfileTargets == null || staticProfileTargets.contains(hostedMethodFromIprof);
            if (entrySatisfiesPossibleTargets && entrySatisfiesStaticProfile) {
                filteredMethodOccurrences.put(hostedMethodFromIprof, entry.getValue());
            }
        }
        return filteredMethodOccurrences;
    }

    /**
     * Converts a map of key to number of occurrences into a map from key to its probability,
     * calculated as the number of occurrences for that key over the total number of occurrences
     * across all keys, with the not recorded probability representing keys not present in the map.
     *
     * @return A map from key to that key's probability. The sum of probabilities should be about
     *         (1.0 - notRecordedProbability).
     * @param <T> The type of the key.
     */
    private static <T> Map<T, Double> computeProbabilities(Map<T, Long> occurrences, double notRecordedProbability) {
        if (occurrences.isEmpty()) {
            return Collections.emptyMap();
        }
        long total = occurrences.values().stream().mapToLong(v -> v).sum();
        assert total > 0 : "Total occurrence count should be positive. Points that never get invoked should not reach here.";
        // Protect against div by 0 error when assertions are disabled.
        long clampedTotal = Math.max(total, 1);
        return occurrences.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, v -> (1.0 - notRecordedProbability) * v.getValue() / clampedTotal));
    }

    public static boolean isSupportedType(AnalysisType analysisType) {
        return analysisType.isReachable() || analysisType.isWordType();
    }
}
