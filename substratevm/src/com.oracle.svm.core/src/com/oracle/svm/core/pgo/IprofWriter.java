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
package com.oracle.svm.core.pgo;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import jdk.graal.compiler.util.json.JsonWriter;
import jdk.vm.ci.meta.MetaUtil;

/**
 * Writes all recorded profiles into one iprof file, the JSON format that {@code --pgo} reads (see
 * {@link IprofFormat}): the {@code version}, the {@code types} and {@code methods} tables, then
 * {@code conditionalProfiles} (branches and switches), {@code callCountProfiles},
 * {@code virtualInvokeProfiles} (receiver types), {@code monitorProfiles} and
 * {@code samplingProfiles} (stack samples).
 * <p>
 * Each {@code conditionalProfiles} entry of a branch describes one bytecode branch at one calling
 * context: the sum over every physical copy of that branch the compiler created (see
 * {@link #branchEntries}).
 */
public final class IprofWriter {

    /** Version of the iprof format written, the schema version the file declares. */
    private static final String IPROF_VERSION = IprofFormat.VERSION_1_1_0;

    private static final Comparator<ContextKey> CONTEXT_COMPARATOR = Comparator.comparing(ContextKey::frames, ProfileFrame.CONTEXT_LIST_ORDER);

    private static final Comparator<BranchIdentity> BRANCH_IDENTITY_COMPARATOR = Comparator
                    .comparing(BranchIdentity::context, CONTEXT_COMPARATOR)
                    .thenComparingInt(BranchIdentity::trueSuccessorBci)
                    .thenComparingInt(BranchIdentity::falseSuccessorBci)
                    .thenComparing(BranchIdentity::conditionKind)
                    .thenComparing(BranchIdentity::conditionFingerprint, Long::compareUnsigned);

    private IprofWriter() {
    }

    /**
     * Writes all profile categories. A null monitor map omits {@code monitorProfiles}; a non-null
     * empty map records that monitor profiling was enabled but observed no monitor operations.
     * <p>
     * The file is written next to {@code path} and moved over it once complete, so a write that
     * fails leaves the previous profile at {@code path}, not a truncated one. The name of that
     * temporary file holds the process id and the time, so that concurrent dumps do not share it.
     */
    public static DumpStatistics write(Path path, List<BranchProfileKey> branchKeys, List<StackSampleRecorder.DecodedSample> stackSamples,
                    List<TypeProfileRecorder.DecodedReceiverProfile> receiverProfiles, List<CallCountProfileKey> callCountKeys,
                    List<SwitchProfileKey> switchKeys, Map<String, Long> monitorProfiles) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + "." + ProcessHandle.current().pid() + "." + System.nanoTime() + ".tmp");
        try {
            DumpStatistics statistics;
            try (JsonWriter writer = new JsonWriter(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                statistics = write(writer, branchKeys, stackSamples, receiverProfiles, callCountKeys, switchKeys, monitorProfiles);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException notSupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return statistics;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static DumpStatistics write(JsonWriter writer, List<BranchProfileKey> branchKeys, List<StackSampleRecorder.DecodedSample> stackSamples,
                    List<TypeProfileRecorder.DecodedReceiverProfile> receiverProfiles, List<CallCountProfileKey> callCountKeys,
                    List<SwitchProfileKey> switchKeys, Map<String, Long> monitorProfiles) throws IOException {
        List<BranchProfileKey> activeBranchKeys = branchKeys.stream()
                        .filter(key -> key.getTrueCount() != 0 || key.getFalseCount() != 0)
                        .toList();
        Map<BranchIdentity, BranchCounts> branchCounts = aggregateBranchKeys(activeBranchKeys);
        Map<ContextKey, Map.Entry<BranchIdentity, BranchCounts>> branchEntries = branchEntries(branchCounts);
        Map<ContextKey, Long> callCounts = aggregateCallCounts(callCountKeys);
        Map<ContextKey, long[]> switchProfiles = aggregateSwitchProfiles(switchKeys);
        /* A context represented by an If profile is ambiguous for a context-only switch record. */
        switchProfiles.keySet().removeAll(branchEntries.keySet());
        Metadata metadata = Metadata.create(branchCounts.keySet(), stackSamples, receiverProfiles, callCounts.keySet(), switchProfiles.keySet(), monitorProfiles);
        /*
         * An entry whose context holds a method that the methods table leaves out, because its
         * descriptor is malformed, is left out too: the file would refer to a method it does not
         * define.
         */
        int skippedEntries = removeUndescribed(branchEntries.keySet(), metadata) + removeUndescribed(switchProfiles.keySet(), metadata) + removeUndescribed(callCounts.keySet(), metadata);
        List<TypeProfileRecorder.DecodedReceiverProfile> receivers = receiverProfiles.stream().filter(profile -> metadata.describes(ContextKey.of(profile.context()))).toList();
        List<StackSampleRecorder.DecodedSample> samples = stackSamples.stream().filter(sample -> metadata.describes(ContextKey.of(sample.context()))).toList();
        skippedEntries += receiverProfiles.size() - receivers.size() + stackSamples.size() - samples.size();
        writer.appendObjectStart();
        writer.appendKeyValue(IprofFormat.VERSION, IPROF_VERSION).appendSeparator();
        writeTypes(writer, metadata).appendSeparator();
        writeMethods(writer, metadata).appendSeparator();
        writeConditionalProfiles(writer, metadata, branchEntries, switchProfiles);
        if (!callCounts.isEmpty()) {
            writer.appendSeparator();
            writeCallCountProfiles(writer, metadata, callCounts);
        }
        if (!receivers.isEmpty()) {
            writer.appendSeparator();
            writeReceiverProfiles(writer, metadata, receivers);
        }
        if (monitorProfiles != null) {
            writer.appendSeparator();
            writeMonitorProfiles(writer, metadata, monitorProfiles);
        }
        if (!samples.isEmpty()) {
            writer.appendSeparator();
            writeSamplingProfiles(writer, metadata, samples);
        }
        writer.appendObjectEnd();
        long branchEvents = activeBranchKeys.stream().mapToLong(key -> key.getTrueCount() + key.getFalseCount()).sum();
        long monitorEvents = monitorProfiles == null ? 0 : monitorProfiles.values().stream().mapToLong(Long::longValue).sum();
        return new DumpStatistics(metadata.typesByName.size(), metadata.methodsByDescriptor.size(), branchEntries.size() + switchProfiles.size(), receivers.size(), callCounts.size(),
                        switchProfiles.size(), monitorProfiles == null ? 0 : monitorProfiles.size(), branchEvents, monitorEvents, skippedEntries);
    }

    /** Removes the contexts that hold a method the methods table leaves out; returns how many. */
    private static int removeUndescribed(Set<ContextKey> contexts, Metadata metadata) {
        int before = contexts.size();
        contexts.removeIf(context -> !metadata.describes(context));
        return before - contexts.size();
    }

    private static Map<ContextKey, long[]> aggregateSwitchProfiles(List<SwitchProfileKey> switchKeys) {
        Map<ContextKey, int[]> shapes = new HashMap<>();
        Map<ContextKey, long[]> counts = new TreeMap<>(CONTEXT_COMPARATOR);
        Set<ContextKey> ambiguous = new HashSet<>();
        for (SwitchProfileKey key : switchKeys) {
            ContextKey context = ContextKey.of(key.context());
            int[] successorBcis = new int[key.successorCount()];
            Set<Integer> distinctBcis = new HashSet<>();
            for (int i = 0; i < successorBcis.length; i++) {
                successorBcis[i] = key.successorBci(i);
                if (!distinctBcis.add(successorBcis[i])) {
                    ambiguous.add(context);
                }
            }
            int[] previousShape = shapes.putIfAbsent(context, successorBcis);
            if (previousShape != null && !Arrays.equals(previousShape, successorBcis)) {
                ambiguous.add(context);
                continue;
            }
            long[] aggregate = counts.computeIfAbsent(context, _ -> new long[successorBcis.length]);
            for (int i = 0; i < aggregate.length; i++) {
                aggregate[i] += key.count(i);
            }
        }
        ambiguous.forEach(counts::remove);
        Map<ContextKey, long[]> result = new TreeMap<>(CONTEXT_COMPARATOR);
        counts.forEach((context, successorCounts) -> {
            if (Arrays.stream(successorCounts).anyMatch(count -> count != 0)) {
                int[] successorBcis = shapes.get(context);
                long[] records = new long[successorCounts.length * 3];
                for (int i = 0; i < successorCounts.length; i++) {
                    records[i * 3] = successorBcis[i];
                    records[i * 3 + 1] = i;
                    records[i * 3 + 2] = successorCounts[i];
                }
                result.put(context, records);
            }
        });
        return result;
    }

    private static Map<ContextKey, Long> aggregateCallCounts(List<CallCountProfileKey> keys) {
        Map<ContextKey, Long> result = new TreeMap<>(CONTEXT_COMPARATOR);
        for (CallCountProfileKey key : keys) {
            long count = key.count();
            if (count != 0) {
                ContextKey context = ContextKey.of(key.context());
                result.merge(context, count, Long::sum);
            }
        }
        return result;
    }

    /** Sums the counts of branch keys with the same identity (context, successors and condition). */
    private static Map<BranchIdentity, BranchCounts> aggregateBranchKeys(List<BranchProfileKey> branchKeys) {
        Map<BranchIdentity, BranchCounts> aggregate = new HashMap<>();
        for (BranchProfileKey key : branchKeys) {
            BranchIdentity identity = BranchIdentity.from(key);
            BranchCounts counts = aggregate.computeIfAbsent(identity, _ -> new BranchCounts(new long[2]));
            counts.counts()[0] += key.getTrueCount();
            counts.counts()[1] += key.getFalseCount();
        }
        return aggregate.entrySet().stream().sorted(Map.Entry.comparingByKey(BRANCH_IDENTITY_COMPARATOR))
                        .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);
    }

    /**
     * A branch entry is keyed by context alone and describes the bytecode branch, so it is the sum
     * over every physical copy of that branch: a loop header's peeled guard plus its in-loop exit
     * condition, or a peeled iteration plus the loop body. Copies that route to different successor
     * BCIs are not the same branch anymore (the compiler rewired them); such contexts stay ambiguous
     * and get no entry.
     * Copies whose successors have no bytecode position (an inlined intrinsic or substitution, an
     * exception edge) all look alike by their successors, so they count as the same branch only when
     * they test the same condition.
     */
    private static Map<ContextKey, Map.Entry<BranchIdentity, BranchCounts>> branchEntries(Map<BranchIdentity, BranchCounts> branchCounts) {
        Map<ContextKey, List<Map.Entry<BranchIdentity, BranchCounts>>> byContext = new HashMap<>();
        for (Map.Entry<BranchIdentity, BranchCounts> entry : branchCounts.entrySet()) {
            byContext.computeIfAbsent(entry.getKey().context, _ -> new ArrayList<>()).add(entry);
        }
        Map<ContextKey, Map.Entry<BranchIdentity, BranchCounts>> result = new TreeMap<>(CONTEXT_COMPARATOR);
        byContext.forEach((context, entries) -> {
            BranchIdentity first = entries.getFirst().getKey();
            boolean unknownSuccessors = first.trueSuccessorBci < 0 || first.falseSuccessorBci < 0;
            long trueCount = 0;
            long falseCount = 0;
            for (Map.Entry<BranchIdentity, BranchCounts> entry : entries) {
                BranchIdentity key = entry.getKey();
                long[] counts = entry.getValue().counts();
                if (unknownSuccessors && (!key.conditionKind.equals(first.conditionKind) || key.conditionFingerprint != first.conditionFingerprint)) {
                    return;
                }
                if (key.trueSuccessorBci == first.trueSuccessorBci && key.falseSuccessorBci == first.falseSuccessorBci) {
                    trueCount += counts[0];
                    falseCount += counts[1];
                } else if (key.trueSuccessorBci == first.falseSuccessorBci && key.falseSuccessorBci == first.trueSuccessorBci) {
                    /* Same branch with a negated condition: successors swapped. */
                    trueCount += counts[1];
                    falseCount += counts[0];
                } else {
                    return;
                }
            }
            if (entries.size() == 1) {
                result.put(context, entries.getFirst());
            } else {
                result.put(context, Map.entry(first, new BranchCounts(new long[]{trueCount, falseCount})));
            }
        });
        return result;
    }

    private static JsonWriter writeTypes(JsonWriter writer, Metadata metadata) throws IOException {
        writer.quote(IprofFormat.TYPES).appendFieldSeparator().appendArrayStart();
        boolean first = true;
        for (Map.Entry<String, Integer> type : metadata.typesByName.entrySet()) {
            if (!first) {
                writer.appendSeparator();
            }
            first = false;
            writer.appendObjectStart().appendKeyValue(IprofFormat.ID, type.getValue()).appendSeparator()
                            .appendKeyValue(IprofFormat.NAME, type.getKey()).appendObjectEnd();
        }
        return writer.appendArrayEnd();
    }

    private static JsonWriter writeMethods(JsonWriter writer, Metadata metadata) throws IOException {
        writer.quote(IprofFormat.METHODS).appendFieldSeparator().appendArrayStart();
        boolean first = true;
        for (Map.Entry<String, Integer> methodEntry : metadata.methodsByDescriptor.entrySet()) {
            if (!first) {
                writer.appendSeparator();
            }
            first = false;
            ParsedMethod method = ParsedMethod.parse(methodEntry.getKey());
            writer.appendObjectStart().appendKeyValue(IprofFormat.ID, methodEntry.getValue()).appendSeparator()
                            .appendKeyValue(IprofFormat.NAME, method.name).appendSeparator()
                            .quote(IprofFormat.SIGNATURE).appendFieldSeparator().appendArrayStart();
            writer.printValue(metadata.typesByName.get(method.declaringType)).appendSeparator()
                            .printValue(metadata.typesByName.get(method.returnType));
            for (String parameterType : method.parameterTypes) {
                writer.appendSeparator().printValue(metadata.typesByName.get(parameterType));
            }
            writer.appendArrayEnd().appendObjectEnd();
        }
        return writer.appendArrayEnd();
    }

    private static JsonWriter writeConditionalProfiles(JsonWriter writer, Metadata metadata,
                    Map<ContextKey, Map.Entry<BranchIdentity, BranchCounts>> branchEntries, Map<ContextKey, long[]> switchProfiles) throws IOException {
        writer.quote(IprofFormat.CONDITIONAL_PROFILES).appendFieldSeparator().appendArrayStart();
        boolean first = true;
        for (Map.Entry<ContextKey, Map.Entry<BranchIdentity, BranchCounts>> entry : branchEntries.entrySet()) {
            if (!first) {
                writer.appendSeparator();
            }
            first = false;
            writeBranchEntry(writer, metadata, entry.getKey(), entry.getValue().getKey(), entry.getValue().getValue());
        }
        for (Map.Entry<ContextKey, long[]> profile : switchProfiles.entrySet()) {
            if (!first) {
                writer.appendSeparator();
            }
            first = false;
            writer.appendObjectStart().appendKeyValue(IprofFormat.CTX, context(metadata, profile.getKey())).appendSeparator()
                            .quote(IprofFormat.RECORDS).appendFieldSeparator().appendArrayStart();
            for (int i = 0; i < profile.getValue().length; i++) {
                if (i != 0) {
                    writer.appendSeparator();
                }
                writer.printValue(profile.getValue()[i]);
            }
            writer.appendArrayEnd().appendObjectEnd();
        }
        return writer.appendArrayEnd();
    }

    private static JsonWriter writeCallCountProfiles(JsonWriter writer, Metadata metadata, Map<ContextKey, Long> profiles) throws IOException {
        writer.quote(IprofFormat.CALL_COUNT_PROFILES).appendFieldSeparator().appendArrayStart();
        boolean first = true;
        for (Map.Entry<ContextKey, Long> profile : profiles.entrySet()) {
            if (!first) {
                writer.appendSeparator();
            }
            first = false;
            writer.appendObjectStart().appendKeyValue(IprofFormat.CTX, context(metadata, profile.getKey())).appendSeparator()
                            .quote(IprofFormat.RECORDS).appendFieldSeparator().appendArrayStart().printValue(profile.getValue()).appendArrayEnd().appendObjectEnd();
        }
        return writer.appendArrayEnd();
    }

    private static JsonWriter writeReceiverProfiles(JsonWriter writer, Metadata metadata, List<TypeProfileRecorder.DecodedReceiverProfile> profiles) throws IOException {
        writer.quote(IprofFormat.VIRTUAL_INVOKE_PROFILES).appendFieldSeparator().appendArrayStart();
        boolean firstProfile = true;
        for (TypeProfileRecorder.DecodedReceiverProfile profile : profiles) {
            if (!firstProfile) {
                writer.appendSeparator();
            }
            firstProfile = false;
            ContextKey context = ContextKey.of(profile.context());
            writer.appendObjectStart().appendKeyValue(IprofFormat.CTX, context(metadata, context)).appendSeparator()
                            .quote(IprofFormat.RECORDS).appendFieldSeparator().appendArrayStart();
            boolean firstReceiver = true;
            for (Map.Entry<String, Long> receiver : new TreeMap<>(profile.countsByTypeDescriptor()).entrySet()) {
                if (!firstReceiver) {
                    writer.appendSeparator();
                }
                firstReceiver = false;
                writer.printValue(metadata.typesByName.get(ParsedMethod.typeName(receiver.getKey()))).appendSeparator().printValue(receiver.getValue());
            }
            writer.appendArrayEnd().appendObjectEnd();
        }
        return writer.appendArrayEnd();
    }

    private static JsonWriter writeMonitorProfiles(JsonWriter writer, Metadata metadata, Map<String, Long> profiles) throws IOException {
        writer.quote(IprofFormat.MONITOR_PROFILES).appendFieldSeparator().appendArrayStart();
        if (!profiles.isEmpty()) {
            /* The iprof format uses one global monitor record anchored at sentinel context 0:0. */
            writer.appendObjectStart().appendKeyValue(IprofFormat.CTX, "0:0").appendSeparator()
                            .quote(IprofFormat.RECORDS).appendFieldSeparator().appendArrayStart();
            boolean first = true;
            for (Map.Entry<String, Long> profile : new TreeMap<>(profiles).entrySet()) {
                if (!first) {
                    writer.appendSeparator();
                }
                first = false;
                writer.printValue(metadata.typesByName.get(ParsedMethod.typeName(profile.getKey()))).appendSeparator().printValue(profile.getValue());
            }
            writer.appendArrayEnd().appendObjectEnd();
        }
        return writer.appendArrayEnd();
    }

    /** {@code samplingProfiles}: sampled call stacks innermost first with their sample counts. */
    private static void writeSamplingProfiles(JsonWriter writer, Metadata metadata, List<StackSampleRecorder.DecodedSample> stackSamples) throws IOException {
        writer.quote(IprofFormat.SAMPLING_PROFILES).appendFieldSeparator().appendArrayStart();
        boolean first = true;
        for (StackSampleRecorder.DecodedSample sample : stackSamples) {
            if (!first) {
                writer.appendSeparator();
            }
            first = false;
            ContextKey context = ContextKey.of(sample.context());
            writer.appendObjectStart().appendKeyValue(IprofFormat.CTX, context(metadata, context)).appendSeparator()
                            .quote(IprofFormat.RECORDS).appendFieldSeparator().appendArrayStart().printValue(sample.count()).appendArrayEnd().appendObjectEnd();
        }
        writer.appendArrayEnd();
    }

    private static void writeBranchEntry(JsonWriter writer, Metadata metadata, ContextKey context, BranchIdentity identity, BranchCounts counts) throws IOException {
        writer.appendObjectStart().appendKeyValue(IprofFormat.CTX, context(metadata, context)).appendSeparator();
        writer.quote(IprofFormat.RECORDS).appendFieldSeparator().appendArrayStart()
                        .printValue(identity.trueSuccessorBci).appendSeparator().printValue(0).appendSeparator().printValue(counts.counts()[0]).appendSeparator()
                        .printValue(identity.falseSuccessorBci).appendSeparator().printValue(1).appendSeparator().printValue(counts.counts()[1])
                        .appendArrayEnd().appendObjectEnd();
    }

    /** The {@code ctx} string of a context: method ids and bcis, innermost frame first, such as {@code 7:12<3:5}. */
    private static String context(Metadata metadata, ContextKey context) {
        StringBuilder result = new StringBuilder();
        for (ProfileFrame frame : context.frames) {
            if (!result.isEmpty()) {
                result.append(IprofFormat.FRAME_SEPARATOR);
            }
            result.append(metadata.methodsByDescriptor.get(frame.methodDescriptor())).append(IprofFormat.METHOD_BCI_SEPARATOR).append(frame.bci());
        }
        return result.toString();
    }

    /**
     * What a write produced, for the summary line that {@link ProfileDumper} prints. Only
     * {@code branchEvents} and {@code monitorEvents} count events; the other recorders print their
     * own event counts. {@code skippedEntries} counts the entries left out because their context
     * holds a method with a malformed descriptor.
     */
    public record DumpStatistics(int types, int methods, int conditionalProfiles, int receiverProfiles, int callCountProfiles, int switchProfiles,
                    int monitorTypes, long branchEvents, long monitorEvents, int skippedEntries) {
    }

    /** A calling context as a map key: frames, innermost first, compared by value. */
    private record ContextKey(List<ProfileFrame> frames) {
        private static ContextKey of(ProfileFrame[] context) {
            return new ContextKey(List.of(context));
        }

        private void addMethodDescriptorsTo(Set<String> descriptors) {
            for (ProfileFrame frame : frames) {
                descriptors.add(frame.methodDescriptor());
            }
        }
    }

    /**
     * The identity of a branch key. The condition kind and fingerprint only matter for copies whose
     * successors have no bytecode position, see {@link #branchEntries}.
     */
    private record BranchIdentity(ContextKey context, int trueSuccessorBci, int falseSuccessorBci, String conditionKind, long conditionFingerprint) {
        private static BranchIdentity from(BranchProfileKey key) {
            ContextKey context = ContextKey.of(key.getContext());
            return new BranchIdentity(context, key.getTrueSuccessorBci(), key.getFalseSuccessorBci(), key.getConditionKind(), key.getConditionFingerprint());
        }
    }

    /** The true and false counts of one branch key, or of the keys summed into it. */
    private record BranchCounts(long[] counts) {
    }

    /**
     * The {@code types} and {@code methods} tables of the file. Profile entries name types and
     * methods by id instead of spelling them out, so the writer first collects every method that
     * appears in a calling context and every type those methods' signatures and the receiver and
     * monitor profiles mention, and numbers them in sorted order.
     */
    private static final class Metadata {
        /** Ids by type name and by method descriptor; both iterate in sorted order. */
        private final Map<String, Integer> typesByName;
        private final Map<String, Integer> methodsByDescriptor;

        private Metadata(Map<String, Integer> typesByName, Map<String, Integer> methodsByDescriptor) {
            this.typesByName = typesByName;
            this.methodsByDescriptor = methodsByDescriptor;
        }

        /** Whether the methods table holds every method of {@code context}. */
        private boolean describes(ContextKey context) {
            for (ProfileFrame frame : context.frames) {
                if (!methodsByDescriptor.containsKey(frame.methodDescriptor())) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Collects the methods of every context and the types they and the type profiles name. A
         * method whose descriptor is malformed is left out of the methods table, and so are the
         * entries whose contexts hold it ({@link #describes}).
         */
        private static Metadata create(Iterable<BranchIdentity> branchIdentities, List<StackSampleRecorder.DecodedSample> stackSamples,
                        List<TypeProfileRecorder.DecodedReceiverProfile> receiverProfiles, Iterable<ContextKey> callCountProfiles,
                        Iterable<ContextKey> switchProfiles, Map<String, Long> monitorProfiles) {
            Set<String> descriptors = new HashSet<>();
            for (BranchIdentity identity : branchIdentities) {
                identity.context.addMethodDescriptorsTo(descriptors);
            }
            for (StackSampleRecorder.DecodedSample sample : stackSamples) {
                ContextKey.of(sample.context()).addMethodDescriptorsTo(descriptors);
            }
            for (TypeProfileRecorder.DecodedReceiverProfile profile : receiverProfiles) {
                ContextKey.of(profile.context()).addMethodDescriptorsTo(descriptors);
            }
            for (ContextKey context : callCountProfiles) {
                context.addMethodDescriptorsTo(descriptors);
            }
            for (ContextKey context : switchProfiles) {
                context.addMethodDescriptorsTo(descriptors);
            }
            List<String> methods = new ArrayList<>(descriptors.size());
            Set<String> types = new HashSet<>();
            for (String descriptor : descriptors) {
                ParsedMethod method = ParsedMethod.parseOrNull(descriptor);
                if (method == null) {
                    continue;
                }
                methods.add(descriptor);
                types.add(method.declaringType);
                types.add(method.returnType);
                types.addAll(method.parameterTypes);
            }
            for (TypeProfileRecorder.DecodedReceiverProfile profile : receiverProfiles) {
                for (String descriptor : profile.countsByTypeDescriptor().keySet()) {
                    types.add(ParsedMethod.typeName(descriptor));
                }
            }
            if (monitorProfiles != null) {
                for (String descriptor : monitorProfiles.keySet()) {
                    types.add(ParsedMethod.typeName(descriptor));
                }
            }
            return new Metadata(assignIds(types), assignIds(methods));
        }

        /** Numbers {@code values} in sorted order, so that the ids depend only on what the file holds. */
        private static Map<String, Integer> assignIds(Collection<String> values) {
            String[] sorted = values.toArray(new String[0]);
            Arrays.sort(sorted);
            Map<String, Integer> ids = new LinkedHashMap<>();
            for (String value : sorted) {
                ids.put(value, ids.size());
            }
            return ids;
        }
    }

    /**
     * A canonical method descriptor split into its declaring type, name, return type and parameter
     * types for the {@code types} and {@code methods} tables. For example,
     * {@code Ljava/lang/String;.charAt(I)C} gives {@code java.lang.String}, {@code charAt},
     * {@code char} and {@code [int]}.
     */
    private static final class ParsedMethod {
        private final String declaringType;
        private final String name;
        private final String returnType;
        private final List<String> parameterTypes;

        private ParsedMethod(String declaringType, String name, String returnType, List<String> parameterTypes) {
            this.declaringType = declaringType;
            this.name = name;
            this.returnType = returnType;
            this.parameterTypes = parameterTypes;
        }

        /** {@link #parse}, or null if {@code methodDescriptor} is malformed. */
        private static ParsedMethod parseOrNull(String methodDescriptor) {
            try {
                return parse(methodDescriptor);
            } catch (IllegalArgumentException | IndexOutOfBoundsException malformed) {
                return null;
            }
        }

        private static ParsedMethod parse(String methodDescriptor) {
            int declaringEnd = methodDescriptor.indexOf(";.");
            int parametersStart = methodDescriptor.indexOf('(', declaringEnd + 2);
            int parametersEnd = methodDescriptor.indexOf(')', parametersStart + 1);
            if (declaringEnd < 1 || parametersStart < declaringEnd + 3 || parametersEnd < parametersStart) {
                throw new IllegalArgumentException("Malformed canonical method descriptor: " + methodDescriptor);
            }
            String declaring = typeName(methodDescriptor.substring(0, declaringEnd + 1));
            String name = methodDescriptor.substring(declaringEnd + 2, parametersStart);
            List<String> parameters = new ArrayList<>();
            int cursor = parametersStart + 1;
            while (cursor < parametersEnd) {
                int end = typeDescriptorEnd(methodDescriptor, cursor, parametersEnd);
                parameters.add(typeName(methodDescriptor.substring(cursor, end)));
                cursor = end;
            }
            int returnEnd = typeDescriptorEnd(methodDescriptor, parametersEnd + 1, methodDescriptor.length());
            if (returnEnd != methodDescriptor.length()) {
                throw new IllegalArgumentException("Trailing data in canonical method descriptor: " + methodDescriptor);
            }
            return new ParsedMethod(declaring, name, typeName(methodDescriptor.substring(parametersEnd + 1, returnEnd)), List.copyOf(parameters));
        }

        private static int typeDescriptorEnd(String descriptor, int start, int limit) {
            int cursor = start;
            while (cursor < limit && descriptor.charAt(cursor) == '[') {
                cursor++;
            }
            if (cursor >= limit) {
                throw new IllegalArgumentException("Incomplete type descriptor in " + descriptor);
            }
            if (descriptor.charAt(cursor) == 'L') {
                int semicolon = descriptor.indexOf(';', cursor + 1);
                if (semicolon < 0 || semicolon >= limit) {
                    throw new IllegalArgumentException("Incomplete object descriptor in " + descriptor);
                }
                return semicolon + 1;
            }
            if ("ZBSCIJFDV".indexOf(descriptor.charAt(cursor)) < 0) {
                throw new IllegalArgumentException("Unknown type descriptor in " + descriptor);
            }
            return cursor + 1;
        }

        /**
         * The iprof name of a type, which is its {@link Class#getName()}: {@code java.lang.String},
         * {@code [I} and {@code int} for the descriptors {@code Ljava/lang/String;}, {@code [I} and
         * {@code I}.
         */
        static String typeName(String descriptor) {
            return MetaUtil.internalNameToJava(descriptor, true, true);
        }
    }
}
