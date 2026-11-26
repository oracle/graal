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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.svm.hosted.pgo.IprofParser.CallCountEntry;
import com.oracle.svm.hosted.pgo.IprofParser.ConditionalEntry;
import com.oracle.svm.hosted.pgo.IprofParser.ContextFrame;
import com.oracle.svm.hosted.pgo.IprofParser.IprofFormatException;
import com.oracle.svm.hosted.pgo.IprofParser.ProfileMethod;
import com.oracle.svm.hosted.pgo.IprofParser.ParsedIprofProfile;
import com.oracle.svm.hosted.pgo.IprofParser.SamplingEntry;
import com.oracle.svm.hosted.pgo.IprofParser.VirtualInvokeEntry;
import com.oracle.svm.hosted.pgo.phases.PGOApplyProfilesPhase;

/**
 * Merges several parsed iprof files into one, the way {@code llvm-profdata merge} and
 * {@code gcov-tool merge} combine training runs: type and method ids of every input are remapped
 * into one namespace by their canonical identity, and the counts of entries with the same identity
 * are added, each input scaled by its weight.
 * <p>
 * Counts add correctly only across runs of the same instrumented image (or images whose contexts
 * still resolve); entries that no longer resolve are dropped by the resolver as for a single file.
 * The relative importance of each input is proportional to its counts, so a longer training run
 * weighs more; weights let the caller rebalance (a workload run for a quarter of the time of
 * another gets weight 4 to count the same).
 */
final class IprofProfileMerger {

    /**
     * One input of a merge: a parsed profile and the factor applied to all of its counts.
     *
     * @param profile the parsed profile
     * @param weight the factor its counts are multiplied by; finite and positive
     */
    record WeightedProfile(ParsedIprofProfile profile, double weight) {
        /**
         * Checks the weight.
         *
         * @param profile the parsed profile
         * @param weight the factor its counts are multiplied by
         * @throws IllegalArgumentException if {@code weight} is not finite or not positive
         */
        WeightedProfile {
            if (!Double.isFinite(weight) || !(weight > 0.0)) {
                throw new IllegalArgumentException("Profile weight must be finite and positive: " + weight);
            }
        }
    }

    /**
     * The identity of a method across files: the names of its declaring class, return type and
     * parameter types, and its name. Ids differ between files; this identity does not.
     *
     * @param declaringType the name of the declaring class
     * @param name the method's name
     * @param returnType the name of the return type
     * @param parameterTypes the names of the parameter types, in order
     */
    private record MethodIdentity(String declaringType, String name, String returnType, List<String> parameterTypes) {
    }

    /** The merged {@code types} table: merged type id by type name, in order of first use. */
    private final Map<String, Integer> typeIds = new LinkedHashMap<>();
    /** Merged method id by method identity, ids given in order of first use. */
    private final Map<MethodIdentity, Integer> methodIds = new LinkedHashMap<>();
    /** The merged {@code methods} table, with merged type ids in the signatures. */
    private final Map<Integer, ProfileMethod> methodsById = new LinkedHashMap<>();
    /** The merged {@code conditionalProfiles}: scaled count of each arm, by merged context. */
    private final Map<List<ContextFrame>, Map<Arm, Long>> conditionalCounts = new LinkedHashMap<>();
    /** The merged {@code callCountProfiles}: scaled count by merged context. */
    private final Map<List<ContextFrame>, Long> callCounts = new LinkedHashMap<>();
    /**
     * The merged {@code virtualInvokeProfiles}: scaled count by merged receiver type id, by merged
     * context.
     */
    private final Map<List<ContextFrame>, Map<Integer, Long>> receiverCounts = new LinkedHashMap<>();
    /** The merged {@code samplingProfiles}: scaled sample count by merged stack. */
    private final Map<List<ContextFrame>, Long> sampleCounts = new LinkedHashMap<>();
    /** The iprof version of the first input, which every other input must have too. */
    private String version;

    private IprofProfileMerger() {
    }

    /**
     * The similarity of the sampling data of every pair of the given profiles. The overlap of two
     * profiles is in [0, 1], as {@code gcov-tool overlap} defines it for arc counters: the sum over
     * all methods of {@code min(a_i / sum(a), b_i / sum(b))} where {@code a_i} is the inclusive
     * sample count of method {@code i}. 1 means the two runs spent their time in the same methods in
     * the same proportions; values well below 1 mean the runs exercise different code and the
     * merged profile is a compromise between them. The overlap is {@link Double#NaN} if either
     * profile has no stack samples, because it is not defined then.
     *
     * @param profiles the profiles to compare
     * @return the overlap matrix, {@code profiles.size()} by {@code profiles.size()}: entry
     *         {@code [i][j]} for profiles {@code i} and {@code j}; it is symmetric and its diagonal
     *         is 1, or {@link Double#NaN} for a profile without stack samples
     */
    static double[][] samplingOverlaps(List<ParsedIprofProfile> profiles) {
        List<Map<MethodIdentity, Long>> samplesByMethod = profiles.stream().map(IprofProfileMerger::inclusiveSamplesByMethod).toList();
        double[][] result = new double[profiles.size()][profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            for (int j = i; j < profiles.size(); j++) {
                result[i][j] = overlap(samplesByMethod.get(i), samplesByMethod.get(j));
                result[j][i] = result[i][j];
            }
        }
        return result;
    }

    /**
     * The overlap that {@link #samplingOverlaps} defines, of two profiles given as inclusive sample
     * counts by method, or {@link Double#NaN} if either has no samples.
     */
    private static double overlap(Map<MethodIdentity, Long> firstSamples, Map<MethodIdentity, Long> secondSamples) {
        double firstTotal = firstSamples.values().stream().mapToLong(Long::longValue).sum();
        double secondTotal = secondSamples.values().stream().mapToLong(Long::longValue).sum();
        if (firstTotal == 0 || secondTotal == 0) {
            return Double.NaN;
        }
        double result = 0.0;
        for (Map.Entry<MethodIdentity, Long> entry : firstSamples.entrySet()) {
            Long other = secondSamples.get(entry.getKey());
            if (other != null) {
                result += Math.min(entry.getValue() / firstTotal, other / secondTotal);
            }
        }
        return result;
    }

    /**
     * The inclusive sample count of each method of {@code profile}: the samples of every stack the
     * method is on, counted once per stack even when the method is on it more than once (as in
     * recursion). Frames whose method id is not in the {@code methods} table are skipped.
     */
    private static Map<MethodIdentity, Long> inclusiveSamplesByMethod(ParsedIprofProfile profile) {
        Map<MethodIdentity, Long> samplesByMethod = new HashMap<>();
        for (SamplingEntry entry : profile.samplingEntries()) {
            Set<Integer> seen = new HashSet<>();
            for (ContextFrame frame : entry.context()) {
                if (seen.add(frame.methodId())) {
                    ProfileMethod method = profile.methodsById().get(frame.methodId());
                    if (method != null) {
                        samplesByMethod.merge(methodIdentity(profile, method), entry.count(), IprofProfileMerger::addCounts);
                    }
                }
            }
        }
        return samplesByMethod;
    }

    /** The identity of {@code method}, with the type names that {@code profile} declares. */
    private static MethodIdentity methodIdentity(ParsedIprofProfile profile, ProfileMethod method) {
        return new MethodIdentity(typeName(profile, method.declaringTypeId()), method.name(), typeName(profile, method.returnTypeId()),
                        Arrays.stream(method.parameterTypeIds()).mapToObj(id -> typeName(profile, id)).toList());
    }

    /**
     * Merges the profiles into one, each count scaled by the weight of its profile. A single
     * profile is returned as it is: the compiler uses only ratios of counts, which one weight for
     * all counts does not change.
     *
     * @param profiles the profiles to merge, at least one
     * @return the merged profile
     * @throws IllegalArgumentException if {@code profiles} is empty
     * @throws IprofFormatException if the profiles have different versions or refer to undeclared
     *             ids, or if a scaled count, a merged count or the total of a merged section does
     *             not fit in a long
     */
    static ParsedIprofProfile merge(List<WeightedProfile> profiles) {
        if (profiles.isEmpty()) {
            throw new IllegalArgumentException("At least one profile is required");
        }
        if (profiles.size() == 1) {
            return profiles.get(0).profile();
        }
        IprofProfileMerger merger = new IprofProfileMerger();
        for (WeightedProfile weighted : profiles) {
            merger.addProfile(weighted.profile(), weighted.weight());
        }
        return merger.mergedProfile();
    }

    /**
     * Adds {@code profile} to the merge: gives its types and methods merged ids, and adds its
     * counts, scaled by {@code weight}, to those of the same merged context (and arm or receiver
     * type).
     */
    private void addProfile(ParsedIprofProfile profile, double weight) {
        if (version == null) {
            version = profile.version();
        } else if (!version.equals(profile.version())) {
            throw new IprofFormatException("Cannot merge iprof versions " + version + " and " + profile.version());
        }
        Map<Integer, Integer> inputToMergedTypeId = new HashMap<>();
        for (Map.Entry<Integer, String> type : profile.typeNamesById().entrySet()) {
            inputToMergedTypeId.put(type.getKey(), typeIds.computeIfAbsent(type.getValue(), _ -> typeIds.size()));
        }
        Map<Integer, Integer> inputToMergedMethodId = new HashMap<>();
        for (ProfileMethod method : profile.methodsById().values()) {
            MethodIdentity identity = methodIdentity(profile, method);
            Integer id = methodIds.get(identity);
            if (id == null) {
                id = methodIds.size();
                methodIds.put(identity, id);
                methodsById.put(id, new ProfileMethod(id, method.name(), inputToMergedTypeId.get(method.declaringTypeId()), inputToMergedTypeId.get(method.returnTypeId()),
                                Arrays.stream(method.parameterTypeIds()).map(inputToMergedTypeId::get).toArray()));
            }
            inputToMergedMethodId.put(method.methodId(), id);
        }
        for (ConditionalEntry entry : profile.conditionalEntries()) {
            addConditionalRecords(conditionalCounts.computeIfAbsent(remap(entry.context(), inputToMergedMethodId), _ -> new LinkedHashMap<>()), entry.records(), weight);
        }
        for (CallCountEntry entry : profile.callCountEntries()) {
            callCounts.merge(remap(entry.context(), inputToMergedMethodId), scale(entry.count(), weight), IprofProfileMerger::addCounts);
        }
        for (VirtualInvokeEntry entry : profile.virtualInvokeEntries()) {
            Map<Integer, Long> typeCounts = receiverCounts.computeIfAbsent(remap(entry.context(), inputToMergedMethodId), _ -> new LinkedHashMap<>());
            long[] records = entry.records();
            for (int i = 0; i + 1 < records.length; i += IprofParser.VIRTUAL_INVOKE_RECORD_SIZE) {
                typeCounts.merge(inputToMergedTypeId.get((int) records[i]), scale(records[i + 1], weight), IprofProfileMerger::addCounts);
            }
        }
        for (SamplingEntry entry : profile.samplingEntries()) {
            sampleCounts.merge(remap(entry.context(), inputToMergedMethodId), scale(entry.count(), weight), IprofProfileMerger::addCounts);
        }
    }

    /** The name {@code profile} declares for {@code typeId}; an undeclared id is a format error. */
    private static String typeName(ParsedIprofProfile profile, int typeId) {
        String name = profile.typeNamesById().get(typeId);
        if (name == null) {
            throw new IprofFormatException("Method table refers to undeclared type id " + typeId);
        }
        return name;
    }

    /**
     * Rewrites the method ids of {@code context} from input ids to merged ids with
     * {@code inputToMergedMethodId}; an undeclared method id is a format error.
     */
    private static List<ContextFrame> remap(List<ContextFrame> context, Map<Integer, Integer> inputToMergedMethodId) {
        List<ContextFrame> result = new ArrayList<>(context.size());
        for (ContextFrame frame : context) {
            Integer methodId = inputToMergedMethodId.get(frame.methodId());
            if (methodId == null) {
                throw new IprofFormatException("Context refers to undeclared method id " + frame.methodId());
            }
            result.add(new ContextFrame(methodId, frame.bci()));
        }
        return result;
    }

    /**
     * A branch or switch arm of a conditional record: its successor bci and successor index.
     *
     * @param successorBci the BCI of the successor
     * @param successorIndex the index of the successor among those of the branch or switch
     */
    private record Arm(long successorBci, long successorIndex) {
    }

    /**
     * Adds conditional records, {@code [successorBci, successorIndex, count]} triples, to
     * {@code target} by arm, each count scaled by {@code weight}.
     */
    private static void addConditionalRecords(Map<Arm, Long> target, long[] records, double weight) {
        for (int i = 0; i + 2 < records.length; i += PGOApplyProfilesPhase.CONDITIONAL_RECORD_SIZE) {
            target.merge(new Arm(records[i], records[i + 1]), scale(records[i + 2], weight), IprofProfileMerger::addCounts);
        }
    }

    /** The arm counts as conditional records, one triple per arm in insertion order. */
    private static long[] toConditionalRecords(Map<Arm, Long> armCounts) {
        long[] result = new long[armCounts.size() * PGOApplyProfilesPhase.CONDITIONAL_RECORD_SIZE];
        int i = 0;
        for (Map.Entry<Arm, Long> entry : armCounts.entrySet()) {
            result[i++] = entry.getKey().successorBci();
            result[i++] = entry.getKey().successorIndex();
            result[i++] = entry.getValue();
        }
        return result;
    }

    /**
     * {@code count} times {@code weight}, rounded. Weight 1 returns {@code count} as it is, because
     * a long above 2^53 does not convert to a double exactly. A product that does not fit in a long
     * is a format error.
     */
    private static long scale(long count, double weight) {
        if (weight == 1.0) {
            return count;
        }
        double scaled = count * weight;
        if (!Double.isFinite(scaled) || scaled > Long.MAX_VALUE) {
            throw new IprofFormatException("Weighted profile count exceeds a signed 64-bit integer: " + count + " * " + weight);
        }
        return Math.round(scaled);
    }

    /** {@code left + right}; a sum that overflows a long is a format error. */
    private static long addCounts(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IprofFormatException("Merged profile counts overflow a signed 64-bit integer");
        }
    }

    /**
     * Builds the merged profile from the merged tables.
     *
     * @return the merged profile
     * @throws IprofFormatException if the counts of a merged section add up to more than
     *             {@link Long#MAX_VALUE}
     */
    private ParsedIprofProfile mergedProfile() {
        Map<Integer, String> typeNamesById = new LinkedHashMap<>();
        typeIds.forEach((name, id) -> typeNamesById.put(id, name));
        List<ConditionalEntry> conditionalEntries = new ArrayList<>();
        conditionalCounts.forEach((context, armCounts) -> conditionalEntries.add(new ConditionalEntry(context, toConditionalRecords(armCounts))));
        List<CallCountEntry> callCountEntries = new ArrayList<>();
        callCounts.forEach((context, count) -> callCountEntries.add(new CallCountEntry(context, count)));
        List<VirtualInvokeEntry> virtualInvokeEntries = new ArrayList<>();
        receiverCounts.forEach((context, typeCounts) -> {
            long[] records = new long[typeCounts.size() * IprofParser.VIRTUAL_INVOKE_RECORD_SIZE];
            int i = 0;
            for (Map.Entry<Integer, Long> entry : typeCounts.entrySet()) {
                records[i++] = entry.getKey();
                records[i++] = entry.getValue();
            }
            virtualInvokeEntries.add(new VirtualInvokeEntry(context, records));
        });
        List<SamplingEntry> samplingEntries = new ArrayList<>();
        sampleCounts.forEach((context, count) -> samplingEntries.add(new SamplingEntry(context, count)));
        return new ParsedIprofProfile(version, typeNamesById, methodsById, conditionalEntries, callCountEntries, virtualInvokeEntries, samplingEntries);
    }
}
