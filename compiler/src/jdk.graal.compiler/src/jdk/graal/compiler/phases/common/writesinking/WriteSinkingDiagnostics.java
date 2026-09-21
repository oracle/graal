/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.phases.common.writesinking;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState.CacheEntry;

import jdk.graal.compiler.debug.CSVUtil;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.GlobalMetrics;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.util.GraphUtil;

/**
 * Compact effect, opportunity, and rejection counters for low-tier write sinking. The effect
 * counters describe graph changes that were actually made, the opportunity counters provide
 * denominators for benchmark summaries, and the rejection counters explain why candidate writes did
 * not sink. Physical-write counters deduplicate each {@link WriteNode}; candidate counters count
 * distinct write/loop opportunities and may overlap across detailed reasons.
 */
public final class WriteSinkingDiagnostics {
    private static final int DETAILS_SCHEMA_VERSION = 3;
    private static final String UNKNOWN = "unknown";
    private static final String CSV_FORMAT = CSVUtil.buildFormatString("%s", "%s", "%s", "%s", "%s", "%s", "%s", "%s", "%s", "%s", "%s", "%s", "%s", "%s", "%s", "%s");
    private static final Object DETAILS_LOCK = new Object();
    private static final Set<Path> DETAILS_FILES_WITH_HEADER = new LinkedHashSet<>();

    /**
     * Scale factor for frequency-weighted runtime estimates. Counter values with the {@code X1000}
     * suffix must be divided by this value before being interpreted.
     */
    private static final long FREQUENCY_SCALE = 1000L;

    /**
     * Number of compiled methods processed by the write-sinking phase, including methods without
     * loops and methods that write sinking does not change.
     */
    private static final CounterKey methodsProcessedCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_MethodsProcessed");
    /**
     * Number of loops present in methods processed by the write-sinking phase, including nested
     * loops and loops that write sinking does not change.
     */
    private static final CounterKey loopsProcessedCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_LoopsProcessed");
    /**
     * Number of compiled methods whose graph was changed by write sinking.
     */
    private static final CounterKey methodsChangedCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_MethodsChanged");
    /**
     * Number of loops whose body had at least one write removed by write sinking.
     */
    private static final CounterKey loopsChangedCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_LoopsChanged");
    /**
     * Number of write nodes removed from loop bodies by write sinking.
     */
    private static final CounterKey writesRemovedFromLoopsCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_WritesRemovedFromLoops");
    /**
     * Number of write nodes inserted at loop exits to preserve the removed writes' memory effects.
     */
    private static final CounterKey writesInsertedAtExitsCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_WritesInsertedAtExits");
    /**
     * Number of inserted exit writes represented as conditional writes because the source loop might
     * execute zero times.
     */
    private static final CounterKey conditionalWritesInsertedCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_ConditionalWritesInserted");
    /**
     * Frequency-weighted estimate of writes removed from loop bodies, scaled by
     * {@link #FREQUENCY_SCALE}. Divide this counter by {@code 1000} to get the approximate weighted
     * write count.
     */
    private static final CounterKey estimatedRuntimeWritesRemovedCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_EstimatedRuntimeWritesRemovedX1000");
    /**
     * Frequency-weighted estimate of writes inserted at loop exits, scaled by
     * {@link #FREQUENCY_SCALE}. Divide this counter by {@code 1000} to get the approximate weighted
     * write count.
     */
    private static final CounterKey estimatedRuntimeWritesInsertedCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_EstimatedRuntimeWritesInsertedX1000");
    /**
     * Approximate graph-node delta from write sinking. Removing a loop-body write contributes
     * {@code -1}; inserting either an unconditional or transient conditional exit write contributes
     * {@code +1}.
     */
    private static final CounterKey estimatedCodeSizeDeltaNodesCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_EstimatedCodeSizeDeltaNodes");
    /**
     * Number of loops whose inclusive loop body contains at least one physical write.
     */
    private static final CounterKey loopsWithWritesCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_LoopsWithWrites");
    /**
     * Number of distinct physical writes in loop bodies. A write nested in multiple loops is
     * counted once.
     */
    private static final CounterKey writesInLoopsCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_WritesInLoops");
    /**
     * Number of loops containing at least one write whose field or array shape is eligible for
     * write sinking.
     */
    private static final CounterKey loopsWithEligibleWritesCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_LoopsWithEligibleWrites");
    /**
     * Number of distinct physical writes in loops that pass write-shape eligibility checks.
     */
    private static final CounterKey writesEligibleCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_WritesEligible");
    /**
     * Number of loops containing at least one write that analysis proves can sink out.
     */
    private static final CounterKey loopsWithSinkableWritesCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_LoopsWithSinkableWrites");
    /**
     * Number of distinct physical writes in loops that analysis proves can sink out.
     */
    private static final CounterKey writesSinkableCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_WritesSinkable");

    /**
     * Number of loops that contain no physical writes in their inclusive loop body.
     */
    private static final CounterKey loopOutcomeNoWritesCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_LoopOutcome_NoWrites");
    /**
     * Number of loops skipped because a {@code GraalDirectives.neverWriteSink()} marker disables
     * sinking for that loop region.
     */
    private static final CounterKey loopOutcomeExplicitlyDisabledCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_LoopOutcome_ExplicitlyDisabled");
    /**
     * Number of loops for which all movable candidates were forced to commit or invalidated by a
     * global memory kill before any write could be sunk.
     */
    private static final CounterKey loopOutcomeGlobalKillOrForcedCommitCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_LoopOutcome_GlobalKillOrForcedCommit");
    /**
     * Number of loops with writes, but none with a structurally eligible field or array write shape.
     */
    private static final CounterKey loopOutcomeNoEligibleWritesCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_LoopOutcome_NoEligibleWrites");
    /**
     * Number of loops with eligible writes where analysis found no write that could legally sink
     * through the loop.
     */
    private static final CounterKey loopOutcomeNoSinkableWritesCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_LoopOutcome_NoSinkableWrites");
    /**
     * Number of loops with at least one sinkable write where no loop-body write was ultimately
     * removed. This covers sinkable opportunities that are abandoned by later legality or merge
     * constraints.
     */
    private static final CounterKey loopOutcomeSinkableNotChangedCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_LoopOutcome_SinkableNotChanged");

    /**
     * Number of distinct physical writes rejected because their declared Java kind cannot be
     * interpreted for write-sinking location matching.
     */
    private static final CounterKey writesRejectedUnknownJavaTypeCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_WritesRejected_UnknownJavaType");
    /**
     * Number of distinct physical writes rejected because the array or field offset is not a
     * compile-time constant.
     */
    private static final CounterKey writesRejectedNonConstantOffsetCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_WritesRejected_NonConstantOffset");
    /**
     * Number of distinct physical writes rejected because the location identity names a field, but
     * the base type and constant offset do not rediscover that field.
     */
    private static final CounterKey writesRejectedFieldIdentityOffsetMismatchCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_WritesRejected_FieldIdentityOffsetMismatch");
    /**
     * Number of distinct physical writes rejected because the location is {@code INIT_LOCATION} but
     * the write shape still cannot be treated as an initialization write.
     */
    private static final CounterKey writesRejectedInitLocationWithoutFieldCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_WritesRejected_InitLocationWithoutField");
    /**
     * Number of distinct physical writes rejected because the location has a specific name but is
     * not a Java field or initialization location accepted by write sinking.
     */
    private static final CounterKey writesRejectedNamedLocationWithoutFieldCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_WritesRejected_NamedLocationWithoutField");
    /**
     * Number of distinct physical writes rejected because the constant object offset has no
     * recognized Java field or named location.
     */
    private static final CounterKey writesRejectedRawObjectOffsetWithoutFieldCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_WritesRejected_RawObjectOffsetWithoutField");
    /**
     * Number of distinct physical writes rejected because the write kind is incompatible with the
     * resolved field kind.
     */
    private static final CounterKey writesRejectedFieldKindMismatchCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_WritesRejected_FieldKindMismatch");
    /**
     * Number of distinct physical writes rejected because the write or active barrier set requires
     * write-barrier handling that write sinking does not move yet.
     */
    private static final CounterKey writesRejectedRequiresWriteBarrierCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_WritesRejected_RequiresWriteBarrier");
    /**
     * Number of distinct physical writes rejected because the target field is volatile and must not
     * be moved.
     */
    private static final CounterKey writesRejectedVolatileCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_WritesRejected_Volatile");
    /**
     * Number of distinct physical writes rejected by the {@code WriteSinkingExcludeFields} option.
     */
    private static final CounterKey writesRejectedExcludedFieldCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_WritesRejected_ExcludedField");

    /**
     * Number of distinct write/loop candidates rejected because the address depends on the loop
     * induction state, so an exit write would not target one stable location.
     */
    private static final CounterKey candidatesRejectedLoopDependentAddressCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_CandidatesRejected_LoopDependentAddress");
    /**
     * Number of distinct write/loop candidates invalidated by a memory read of the same location.
     */
    private static final CounterKey candidatesInvalidatedMemoryAccessReadCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_CandidatesInvalidated_MemoryAccessRead");
    /**
     * Number of distinct write/loop candidates invalidated by a single-location memory kill.
     */
    private static final CounterKey candidatesInvalidatedSingleMemoryKillCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_CandidatesInvalidated_SingleMemoryKill");
    /**
     * Number of distinct write/loop candidates invalidated by a multi-location memory kill.
     */
    private static final CounterKey candidatesInvalidatedMultiMemoryKillCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_CandidatesInvalidated_MultiMemoryKill");
    /**
     * Number of distinct write/loop candidates invalidated by a global memory kill.
     */
    private static final CounterKey candidatesInvalidatedGlobalKillCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_CandidatesInvalidated_GlobalKill");
    /**
     * Number of distinct write/loop candidates invalidated because stateful control flow forced
     * movable writes to commit before the loop exit.
     */
    private static final CounterKey candidatesInvalidatedForcedCommitCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_CandidatesInvalidated_ForcedCommit");
    /**
     * Number of distinct write/loop candidates invalidated at a merge because branch values or
     * write-barrier metadata could not be merged safely.
     */
    private static final CounterKey candidatesInvalidatedMergeValueOrBarrierCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_CandidatesInvalidated_MergeValueOrBarrier");
    /**
     * Number of distinct write/loop candidates invalidated because merge ordering could not
     * preserve the required memory effects.
     */
    private static final CounterKey candidatesInvalidatedMergeOrderingConflictCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_CandidatesInvalidated_MergeOrderingConflict");

    /**
     * Primary, mutually prioritized rejection bucket for write/loop candidates rejected by a loop
     * policy, such as explicit disablement or global-kill policy.
     */
    private static final CounterKey primaryRejectedLoopPolicyCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_CandidatePrimaryRejected_LoopPolicy");
    /**
     * Primary, mutually prioritized rejection bucket for write/loop candidates whose physical write
     * failed structural or semantic eligibility checks.
     */
    private static final CounterKey primaryRejectedWritePropertyCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_CandidatePrimaryRejected_WriteProperty");
    /**
     * Primary, mutually prioritized rejection bucket for write/loop candidates rejected because the
     * written address depends on the loop.
     */
    private static final CounterKey primaryRejectedLoopDependentAddressCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_CandidatePrimaryRejected_LoopDependentAddress");
    /**
     * Primary, mutually prioritized rejection bucket for write/loop candidates invalidated by reads
     * or memory kills.
     */
    private static final CounterKey primaryRejectedMemoryInterferenceCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_CandidatePrimaryRejected_MemoryInterference");
    /**
     * Primary, mutually prioritized rejection bucket for write/loop candidates rejected by merge
     * value, barrier, or ordering constraints.
     */
    private static final CounterKey primaryRejectedMergeOrOrderingConflictCounter = DebugContext.counter("Enterprise_LowTier_WriteSinking_CandidatePrimaryRejected_MergeOrOrderingConflict");

    private WriteSinkingDiagnostics() {
    }

    /**
     * Records a method processed by write sinking and the number of loops in its low-tier graph.
     */
    public static void recordProcessedGraph(DebugContext debug, int loopCount) {
        methodsProcessedCounter.increment(debug);
        for (int i = 0; i < loopCount; i++) {
            loopsProcessedCounter.increment(debug);
        }
    }

    /**
     * Records a write deleted from a loop body by write sinking.
     */
    public static void recordWriteRemoved(DebugContext debug, HIRBlock writeBlock) {
        writesRemovedFromLoopsCounter.increment(debug);
        estimatedRuntimeWritesRemovedCounter.add(debug, scaledFrequency(writeBlock));
        estimatedCodeSizeDeltaNodesCounter.add(debug, -1);
    }

    /**
     * Records a write inserted at a loop exit by write sinking.
     */
    public static void recordWriteInserted(DebugContext debug, HIRBlock commitBlock, boolean conditional) {
        writesInsertedAtExitsCounter.increment(debug);
        if (conditional) {
            conditionalWritesInsertedCounter.increment(debug);
        }
        estimatedRuntimeWritesInsertedCounter.add(debug, scaledFrequency(commitBlock));
        estimatedCodeSizeDeltaNodesCounter.increment(debug);
    }

    /**
     * Records a loop whose body had at least one write removed.
     */
    public static void recordLoopChanged(DebugContext debug) {
        loopsChangedCounter.increment(debug);
    }

    /**
     * Records a method whose graph was changed by write sinking.
     */
    public static void recordMethodChanged(DebugContext debug) {
        methodsChangedCounter.increment(debug);
    }

    /**
     * Creates per-method diagnostic state for one write-sinking phase invocation.
     */
    public static MethodMetrics createMethodMetrics(ControlFlowGraph cfg, String detailsCSV) {
        if (!cfg.graph.getDebug().areCountersEnabled() && (detailsCSV == null || detailsCSV.isEmpty())) {
            return MethodMetrics.DISABLED;
        }
        return new MethodMetrics(cfg, detailsCSV);
    }

    /**
     * Returns a recorder that accepts all diagnostic events but deliberately emits nothing.
     */
    public static MethodMetrics disabledMethodMetrics() {
        return MethodMetrics.DISABLED;
    }

    /**
     * Structural or semantic write properties that make a physical write ineligible for sinking.
     */
    public enum WriteRejection {
        UNKNOWN_JAVA_TYPE,
        NON_CONSTANT_OFFSET,
        FIELD_IDENTITY_OFFSET_MISMATCH,
        INIT_LOCATION_WITHOUT_FIELD,
        NAMED_LOCATION_WITHOUT_FIELD,
        RAW_OBJECT_OFFSET_WITHOUT_FIELD,
        FIELD_KIND_MISMATCH,
        REQUIRES_WRITE_BARRIER,
        VOLATILE,
        EXCLUDED_FIELD
    }

    /**
     * Per-loop reasons that a write-sinking candidate is rejected or invalidated after it has a
     * trackable write shape.
     */
    public enum CandidateRejection {
        LOOP_DEPENDENT_ADDRESS,
        MEMORY_ACCESS_READ,
        SINGLE_MEMORY_KILL,
        MULTI_MEMORY_KILL,
        GLOBAL_KILL,
        FORCED_COMMIT,
        MERGE_VALUE_OR_BARRIER,
        MERGE_ORDERING_CONFLICT
    }

    /**
     * Mutually exclusive result for a loop not counted by {@code LoopsChanged}.
     */
    public enum LoopOutcome {
        NO_WRITES,
        EXPLICITLY_DISABLED,
        GLOBAL_KILL_OR_FORCED_COMMIT,
        NO_ELIGIBLE_WRITES,
        NO_SINKABLE_WRITES,
        SINKABLE_NOT_CHANGED
    }

    private enum PrimaryCandidateRejection {
        LOOP_POLICY,
        WRITE_PROPERTY,
        LOOP_DEPENDENT_ADDRESS,
        MEMORY_INTERFERENCE,
        MERGE_OR_ORDERING_CONFLICT
    }

    /**
     * Per-method diagnostic collector. It keeps deduplication state local to a phase invocation and
     * emits aggregate counters only after all loops have been analyzed and rewritten.
     */
    public static final class MethodMetrics {
        private static final MethodMetrics DISABLED = new MethodMetrics();

        private final boolean enabled;
        private final StructuredGraph graph;
        private final String detailsCSV;
        private final String compiledMethod;
        private final Set<WriteNode> writesInLoops = newStableSet();
        private final Set<WriteNode> eligibleWrites = newStableSet();
        private final Set<WriteNode> sinkableWrites = newStableSet();
        private final Set<CFGLoop<HIRBlock>> changedLoops = newStableSet();
        private final Map<WriteNode, Set<CFGLoop<HIRBlock>>> writeLoops = new LinkedHashMap<>();
        private final Map<CacheEntry, WriteNode> representativeWrites = new LinkedHashMap<>();
        private final Map<CFGLoop<HIRBlock>, LoopMetrics> loopMetrics = new LinkedHashMap<>();
        private final Map<WriteRejection, Set<WriteNode>> writeRejections = new EnumMap<>(WriteRejection.class);
        private final Map<CandidateRejection, Set<CandidateKey>> candidateRejections = new EnumMap<>(CandidateRejection.class);
        private final Map<PrimaryCandidateRejection, Set<CandidateKey>> primaryCandidateRejections = new EnumMap<>(PrimaryCandidateRejection.class);
        private final List<DetailRow> detailRows = new ArrayList<>();

        private MethodMetrics() {
            this.enabled = false;
            this.graph = null;
            this.detailsCSV = null;
            this.compiledMethod = UNKNOWN;
        }

        MethodMetrics(ControlFlowGraph cfg, String detailsCSV) {
            this.enabled = true;
            this.graph = cfg.graph;
            this.detailsCSV = detailsCSV;
            this.compiledMethod = formatCompiledMethod(graph);
            for (CFGLoop<HIRBlock> loop : cfg.getLoops()) {
                LoopMetrics metrics = new LoopMetrics();
                loopMetrics.put(loop, metrics);
                for (HIRBlock block : loop.getBlocks()) {
                    for (FixedNode node : block.getNodes()) {
                        if (node instanceof WriteNode write) {
                            metrics.writes.add(write);
                            writesInLoops.add(write);
                            writeLoops.computeIfAbsent(write, (unused) -> newStableSet()).add(loop);
                        }
                    }
                }
            }
        }

        /**
         * Records that {@code write} has a trackable shape before loop-specific analysis decides
         * whether it can sink.
         */
        public void recordEligibleWrite(WriteNode write) {
            if (!enabled) {
                return;
            }
            if (!writeLoops.containsKey(write)) {
                return;
            }
            eligibleWrites.add(write);
            representativeWrites.putIfAbsent(WriteSinkingUtil.getEntryFromWrite(write), write);
            forEachContainingLoop(write, (loop, metrics) -> metrics.eligibleWrites.add(write));
        }

        /**
         * Records why a physical write failed structural eligibility checks.
         */
        public void recordWriteRejected(WriteNode write, WriteRejection reason) {
            if (!enabled) {
                return;
            }
            if (!writeLoops.containsKey(write)) {
                return;
            }
            if (writeRejections.computeIfAbsent(reason, (unused) -> newStableSet()).add(write)) {
                addWriteRejectionDetails(write, reason);
            }
            forEachContainingLoop(write, (loop, metrics) -> {
                metrics.recordWriteRejected(write, reason);
                recordPrimary(loop, write, PrimaryCandidateRejection.WRITE_PROPERTY);
            });
        }

        /**
         * Records that analysis proved {@code write} can sink through {@code loop}.
         */
        public void recordSinkableWrite(CFGLoop<HIRBlock> loop, WriteNode write) {
            if (!enabled) {
                return;
            }
            sinkableWrites.add(write);
            LoopMetrics metrics = loopMetrics.get(loop);
            if (metrics != null) {
                metrics.sinkableWrites.add(write);
            }
        }

        /**
         * Records a per-loop candidate rejection or invalidation.
         */
        public void recordCandidateRejected(CFGLoop<HIRBlock> loop, CacheEntry key, CandidateRejection reason) {
            if (!enabled) {
                return;
            }
            if (loop == null || key == null) {
                return;
            }
            CandidateKey candidateKey = new CandidateKey(loop, key);
            if (candidateRejections.computeIfAbsent(reason, (unused) -> newCandidateSet()).add(candidateKey)) {
                addCandidateRejectionDetail(loop, key, reason);
            }
            recordPrimary(loop, key, primaryReason(reason));
        }

        /**
         * Records that {@code loop} had at least one write removed from its body.
         */
        public void recordLoopChanged(CFGLoop<HIRBlock> loop) {
            if (!enabled) {
                return;
            }
            changedLoops.add(loop);
        }

        /**
         * Records a policy or analysis outcome for a loop that write sinking did not change.
         */
        public void recordLoopOutcome(CFGLoop<HIRBlock> loop, LoopOutcome outcome) {
            if (!enabled) {
                return;
            }
            recordLoopOutcome(loop, outcome, defaultNote(outcome));
        }

        /**
         * Records a policy or analysis outcome for a loop that write sinking did not change.
         */
        public void recordLoopOutcome(CFGLoop<HIRBlock> loop, LoopOutcome outcome, String detailNote) {
            if (!enabled) {
                return;
            }
            LoopMetrics metrics = loopMetrics.get(loop);
            if (metrics != null && !changedLoops.contains(loop)) {
                metrics.outcome = outcome;
                addLoopOutcomeDetail(loop, outcome, detailNote);
                if (outcome == LoopOutcome.EXPLICITLY_DISABLED || outcome == LoopOutcome.GLOBAL_KILL_OR_FORCED_COMMIT) {
                    for (WriteNode write : metrics.writes) {
                        recordPrimary(loop, write, PrimaryCandidateRejection.LOOP_POLICY);
                    }
                }
            }
        }

        /**
         * Returns whether a loop has at least one write in its inclusive body.
         */
        public boolean hasWrites(CFGLoop<HIRBlock> loop) {
            if (!enabled) {
                return false;
            }
            LoopMetrics metrics = loopMetrics.get(loop);
            return metrics != null && !metrics.writes.isEmpty();
        }

        /**
         * Returns whether a loop has at least one eligible write in its inclusive body.
         */
        public boolean hasEligibleWrites(CFGLoop<HIRBlock> loop) {
            if (!enabled) {
                return false;
            }
            LoopMetrics metrics = loopMetrics.get(loop);
            return metrics != null && !metrics.eligibleWrites.isEmpty();
        }

        /**
         * Returns whether a loop has at least one sinkable write in its inclusive body.
         */
        public boolean hasSinkableWrites(CFGLoop<HIRBlock> loop) {
            if (!enabled) {
                return false;
            }
            LoopMetrics metrics = loopMetrics.get(loop);
            return metrics != null && !metrics.sinkableWrites.isEmpty();
        }

        /**
         * Emits aggregate counters for all opportunity and rejection data collected in this method.
         */
        public void emit(DebugContext debug) {
            if (!enabled) {
                return;
            }
            long loopsWithWrites = 0;
            long loopsWithEligibleWrites = 0;
            long loopsWithSinkableWrites = 0;
            for (Map.Entry<CFGLoop<HIRBlock>, LoopMetrics> entry : loopMetrics.entrySet()) {
                CFGLoop<HIRBlock> loop = entry.getKey();
                LoopMetrics metrics = entry.getValue();
                if (!metrics.writes.isEmpty()) {
                    loopsWithWrites++;
                }
                if (!metrics.eligibleWrites.isEmpty()) {
                    loopsWithEligibleWrites++;
                }
                if (!metrics.sinkableWrites.isEmpty()) {
                    loopsWithSinkableWrites++;
                }
                if (!changedLoops.contains(loop)) {
                    LoopOutcome outcome = metrics.outcome;
                    if (outcome == null) {
                        outcome = defaultOutcome(metrics);
                    }
                    counter(outcome).increment(debug);
                }
            }
            loopsWithWritesCounter.add(debug, loopsWithWrites);
            writesInLoopsCounter.add(debug, writesInLoops.size());
            loopsWithEligibleWritesCounter.add(debug, loopsWithEligibleWrites);
            writesEligibleCounter.add(debug, eligibleWrites.size());
            loopsWithSinkableWritesCounter.add(debug, loopsWithSinkableWrites);
            writesSinkableCounter.add(debug, sinkableWrites.size());
            for (Map.Entry<WriteRejection, Set<WriteNode>> entry : writeRejections.entrySet()) {
                counter(entry.getKey()).add(debug, entry.getValue().size());
            }
            for (Map.Entry<CandidateRejection, Set<CandidateKey>> entry : candidateRejections.entrySet()) {
                counter(entry.getKey()).add(debug, entry.getValue().size());
            }
            for (Map.Entry<PrimaryCandidateRejection, Set<CandidateKey>> entry : primaryCandidateRejections.entrySet()) {
                counter(entry.getKey()).add(debug, entry.getValue().size());
            }
            writeDetailsCSV();
        }

        /**
         * Computes the fallback mutually exclusive loop outcome when the rewrite pass did not
         * record a more specific policy or analysis result.
         */
        private static LoopOutcome defaultOutcome(LoopMetrics metrics) {
            if (metrics.writes.isEmpty()) {
                return LoopOutcome.NO_WRITES;
            }
            if (metrics.eligibleWrites.isEmpty()) {
                return LoopOutcome.NO_ELIGIBLE_WRITES;
            }
            if (metrics.sinkableWrites.isEmpty()) {
                return LoopOutcome.NO_SINKABLE_WRITES;
            }
            return LoopOutcome.SINKABLE_NOT_CHANGED;
        }

        /**
         * Applies {@code action} to every loop whose inclusive body contains {@code write}. This is
         * intentionally inclusive so outer loops get opportunity counters for writes in nested
         * loops.
         */
        private void forEachContainingLoop(WriteNode write, LoopConsumer action) {
            Set<CFGLoop<HIRBlock>> loops = writeLoops.get(write);
            if (loops == null) {
                return;
            }
            for (CFGLoop<HIRBlock> loop : loops) {
                LoopMetrics metrics = loopMetrics.get(loop);
                if (metrics != null) {
                    action.accept(loop, metrics);
                }
            }
        }

        /**
         * Records the coarse rejection bucket for a loop/write opportunity. These primary buckets
         * are deduplicated independently from the detailed rejection counters.
         */
        private void recordPrimary(CFGLoop<HIRBlock> loop, Object key, PrimaryCandidateRejection reason) {
            primaryCandidateRejections.computeIfAbsent(reason, (unused) -> newCandidateSet()).add(new CandidateKey(loop, key));
        }

        /**
         * Emits one detail row for each loop that contains a physically rejected write.
         */
        private void addWriteRejectionDetails(WriteNode write, WriteRejection reason) {
            if (!detailsEnabled()) {
                return;
            }
            forEachContainingLoop(write, (loop, metrics) -> detailRows.add(DetailRow.create(compiledMethod, "WRITE_REJECTED", category(reason), reason.name(), defaultNote(reason), loop, write,
                            write.getLocationIdentity() == null ? UNKNOWN : write.getLocationIdentity().toString(), write)));
        }

        /**
         * Emits one detail row for a rejected loop/write candidate, using a representative write
         * when one has already been recorded for the candidate key.
         */
        private void addCandidateRejectionDetail(CFGLoop<HIRBlock> loop, CacheEntry key, CandidateRejection reason) {
            if (!detailsEnabled()) {
                return;
            }
            WriteNode write = representativeWrites.get(key);
            Node source = write == null ? key.address : write;
            detailRows.add(DetailRow.create(compiledMethod, eventName(reason), category(reason), reason.name(), defaultNote(reason), loop, write,
                            key.identity == null ? UNKNOWN : key.identity.toString(), source));
        }

        /**
         * Emits a loop-level detail row for unchanged loops. The {@code NO_ELIGIBLE_WRITES} case is
         * refined to the primary write rejection observed in that loop.
         */
        private void addLoopOutcomeDetail(CFGLoop<HIRBlock> loop, LoopOutcome outcome, String detailNote) {
            if (!detailsEnabled() || outcome == LoopOutcome.NO_WRITES) {
                return;
            }
            LoopMetrics metrics = loopMetrics.get(loop);
            Node source = loop.getHeader().getBeginNode();
            if (outcome == LoopOutcome.NO_ELIGIBLE_WRITES) {
                WriteRejection primaryReason = metrics == null ? null : metrics.primaryWriteRejection();
                detailRows.add(DetailRow.create(compiledMethod, "LOOP_OUTCOME", category(primaryReason), noEligibleReason(primaryReason), noEligibleNote(primaryReason), loop, null, UNKNOWN, source));
            } else {
                detailRows.add(DetailRow.create(compiledMethod, "LOOP_OUTCOME", category(outcome), outcome.name(), detailNote, loop, null, UNKNOWN, source));
            }
        }

        /**
         * Returns whether the opt-in per-method/per-loop details CSV is enabled for this method.
         */
        private boolean detailsEnabled() {
            return detailsCSV != null && !detailsCSV.isEmpty();
        }

        /**
         * Writes collected detail rows after aggregate counters have been emitted.
         */
        private void writeDetailsCSV() {
            if (detailRows.isEmpty() || !detailsEnabled()) {
                return;
            }
            DetailsCSVWriter.write(detailsCSV, detailRows);
        }
    }

    private static final class LoopMetrics {
        final Set<WriteNode> writes = newStableSet();
        final Set<WriteNode> eligibleWrites = newStableSet();
        final Set<WriteNode> sinkableWrites = newStableSet();
        final Map<WriteRejection, Set<WriteNode>> rejectedWrites = new EnumMap<>(WriteRejection.class);
        LoopOutcome outcome;

        /**
         * Adds {@code write} to the rejection set used for the loop's primary ineligibility reason.
         */
        void recordWriteRejected(WriteNode write, WriteRejection reason) {
            rejectedWrites.computeIfAbsent(reason, (unused) -> newStableSet()).add(write);
        }

        /**
         * Returns the write rejection that affected the most writes in this loop, using enum order
         * as the deterministic tie breaker.
         */
        WriteRejection primaryWriteRejection() {
            WriteRejection primary = null;
            int primaryCount = 0;
            for (WriteRejection reason : WriteRejection.values()) {
                Set<WriteNode> writesForReason = rejectedWrites.get(reason);
                int count = writesForReason == null ? 0 : writesForReason.size();
                if (count > primaryCount) {
                    primary = reason;
                    primaryCount = count;
                }
            }
            return primary;
        }
    }

    /**
     * Deduplication key for diagnostics that count a write opportunity within a specific loop.
     */
    private record CandidateKey(CFGLoop<HIRBlock> loop, Object key) {
    }

    private enum ReasonCategory {
        WRITE_BASE_TYPE,
        WRITE_ADDRESS_SHAPE,
        WRITE_FIELD_RESOLUTION,
        WRITE_FIELD_SEMANTICS,
        WRITE_BARRIER,
        WRITE_EXCLUSION_POLICY,
        LOOP_POLICY,
        LOOP_DEPENDENT_ADDRESS,
        MEMORY_INTERFERENCE,
        MERGE_OR_ORDERING_CONFLICT
    }

    /**
     * One serialized row in the opt-in details CSV.
     */
    private record DetailRow(String compiledMethod, String event, String reasonCategory, String reason, String detailNote, String loopHeaderId, String loopDepth,
                    String loopFrequency, String writeNodeId, String writeLocation, String sourceMethod, String sourceBci, String sourceFile, String sourceLine,
                    String sourceStack) {
        static DetailRow create(String compiledMethod, String event, ReasonCategory category, String reason, String detailNote, CFGLoop<HIRBlock> loop, WriteNode write,
                        String writeLocation, Node source) {
            SourceInfo sourceInfo = SourceInfo.create(source, compiledMethod);
            return new DetailRow(compiledMethod, event, category.name(), reason, detailNote, WriteSinkingDiagnostics.loopHeaderId(loop), WriteSinkingDiagnostics.loopDepth(loop),
                            WriteSinkingDiagnostics.loopFrequency(loop), nodeId(write),
                            writeLocation, sourceInfo.method(), sourceInfo.bci(), sourceInfo.file(), sourceInfo.line(), sourceInfo.stack());
        }
    }

    /**
     * Best-effort source location columns for a details CSV row.
     */
    private record SourceInfo(String method, String bci, String file, String line, String stack) {
        /**
         * Builds best-effort source information for a detail row without requiring source-position
         * tracking to be enabled.
         */
        static SourceInfo create(Node node, String fallbackMethod) {
            int bci = -1;
            if (node != null && node.getNodeSourcePosition() != null) {
                bci = node.getNodeSourcePosition().getBCI();
            }
            StackTraceElement[] stackTrace = node == null ? new StackTraceElement[0] : GraphUtil.approxSourceStackTraceElement(node);
            if (stackTrace.length == 0) {
                return new SourceInfo(fallbackMethod, String.valueOf(bci), UNKNOWN, "-1", "");
            }
            StackTraceElement top = stackTrace[0];
            StringBuilder stack = new StringBuilder();
            for (StackTraceElement element : stackTrace) {
                if (stack.length() > 0) {
                    stack.append(" | ");
                }
                stack.append(element);
            }
            String file = top.getFileName() == null ? UNKNOWN : top.getFileName();
            return new SourceInfo(top.getClassName() + "." + top.getMethodName(), String.valueOf(bci), file, String.valueOf(top.getLineNumber()), stack.toString());
        }
    }

    private static final class DetailsCSVWriter {
        /**
         * Appends detail rows to the configured CSV file and writes the header once per process per
         * output path.
         */
        private static void write(String detailsCSV, List<DetailRow> rows) {
            Path path = GlobalMetrics.generateFileName(detailsCSV);
            synchronized (DETAILS_LOCK) {
                try {
                    Path parent = path.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    boolean needsHeader = DETAILS_FILES_WITH_HEADER.add(path) && (!Files.exists(path) || Files.size(path) == 0);
                    try (PrintStream out = new PrintStream(Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND), true)) {
                        if (needsHeader) {
                            CSVUtil.Escape.println(out, CSV_FORMAT, "schema_version", "compiled_method", "event", "reason_category", "reason", "detail_note", "loop_header_id", "loop_depth",
                                            "loop_frequency", "write_node_id", "write_location", "source_method", "source_bci", "source_file", "source_line", "source_stack");
                        }
                        for (DetailRow row : rows) {
                            CSVUtil.Escape.println(out, CSV_FORMAT, String.valueOf(DETAILS_SCHEMA_VERSION), row.compiledMethod(), row.event(), row.reasonCategory(), row.reason(),
                                            row.detailNote(), row.loopHeaderId(), row.loopDepth(), row.loopFrequency(), row.writeNodeId(), row.writeLocation(), row.sourceMethod(),
                                            row.sourceBci(), row.sourceFile(), row.sourceLine(), row.sourceStack());
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("Could not write write-sinking details CSV " + path, e);
                }
            }
        }
    }

    @FunctionalInterface
    private interface LoopConsumer {
        void accept(CFGLoop<HIRBlock> loop, LoopMetrics metrics);
    }

    /**
     * Creates a deterministic insertion-ordered set for stable counters and CSV rows.
     */
    private static <T> Set<T> newStableSet() {
        return new LinkedHashSet<>();
    }

    /**
     * Creates a deterministic candidate-key set for primary and detailed candidate counters.
     */
    private static Set<CandidateKey> newCandidateSet() {
        return new LinkedHashSet<>();
    }

    /**
     * Maps detailed candidate invalidations to the coarser primary rejection buckets.
     */
    private static PrimaryCandidateRejection primaryReason(CandidateRejection reason) {
        return switch (reason) {
            case LOOP_DEPENDENT_ADDRESS -> PrimaryCandidateRejection.LOOP_DEPENDENT_ADDRESS;
            case MERGE_VALUE_OR_BARRIER, MERGE_ORDERING_CONFLICT -> PrimaryCandidateRejection.MERGE_OR_ORDERING_CONFLICT;
            case MEMORY_ACCESS_READ, SINGLE_MEMORY_KILL, MULTI_MEMORY_KILL, GLOBAL_KILL, FORCED_COMMIT -> PrimaryCandidateRejection.MEMORY_INTERFERENCE;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(reason); // ExcludeFromJacocoGeneratedReport
        };
    }

    /**
     * Formats the compiled method column used in the details CSV.
     */
    private static String formatCompiledMethod(StructuredGraph graph) {
        if (graph.method() != null) {
            return graph.method().format("%H.%n(%p)%R");
        }
        return graph.name;
    }

    /**
     * Distinguishes initial candidate rejection from later invalidation during traversal.
     */
    private static String eventName(CandidateRejection reason) {
        return switch (reason) {
            case LOOP_DEPENDENT_ADDRESS -> "CANDIDATE_REJECTED";
            case MEMORY_ACCESS_READ, SINGLE_MEMORY_KILL, MULTI_MEMORY_KILL, GLOBAL_KILL, FORCED_COMMIT, MERGE_VALUE_OR_BARRIER, MERGE_ORDERING_CONFLICT -> "CANDIDATE_INVALIDATED";
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(reason); // ExcludeFromJacocoGeneratedReport
        };
    }

    /**
     * Returns the CSV reason category for a loop/write candidate rejection.
     */
    private static ReasonCategory category(CandidateRejection reason) {
        return switch (reason) {
            case LOOP_DEPENDENT_ADDRESS -> ReasonCategory.LOOP_DEPENDENT_ADDRESS;
            case MEMORY_ACCESS_READ, SINGLE_MEMORY_KILL, MULTI_MEMORY_KILL, GLOBAL_KILL, FORCED_COMMIT -> ReasonCategory.MEMORY_INTERFERENCE;
            case MERGE_VALUE_OR_BARRIER, MERGE_ORDERING_CONFLICT -> ReasonCategory.MERGE_OR_ORDERING_CONFLICT;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(reason); // ExcludeFromJacocoGeneratedReport
        };
    }

    /**
     * Returns the CSV reason category for a physical write rejection.
     */
    private static ReasonCategory category(WriteRejection reason) {
        if (reason == null) {
            return ReasonCategory.WRITE_ADDRESS_SHAPE;
        }
        return switch (reason) {
            case UNKNOWN_JAVA_TYPE -> ReasonCategory.WRITE_BASE_TYPE;
            case NON_CONSTANT_OFFSET -> ReasonCategory.WRITE_ADDRESS_SHAPE;
            case FIELD_IDENTITY_OFFSET_MISMATCH, INIT_LOCATION_WITHOUT_FIELD, NAMED_LOCATION_WITHOUT_FIELD, RAW_OBJECT_OFFSET_WITHOUT_FIELD -> ReasonCategory.WRITE_FIELD_RESOLUTION;
            case FIELD_KIND_MISMATCH, VOLATILE -> ReasonCategory.WRITE_FIELD_SEMANTICS;
            case REQUIRES_WRITE_BARRIER -> ReasonCategory.WRITE_BARRIER;
            case EXCLUDED_FIELD -> ReasonCategory.WRITE_EXCLUSION_POLICY;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(reason); // ExcludeFromJacocoGeneratedReport
        };
    }

    /**
     * Returns the CSV reason category for a loop-level outcome.
     */
    private static ReasonCategory category(LoopOutcome outcome) {
        return switch (outcome) {
            case EXPLICITLY_DISABLED, GLOBAL_KILL_OR_FORCED_COMMIT -> ReasonCategory.LOOP_POLICY;
            case NO_ELIGIBLE_WRITES -> ReasonCategory.WRITE_ADDRESS_SHAPE;
            case NO_SINKABLE_WRITES, SINKABLE_NOT_CHANGED -> ReasonCategory.MEMORY_INTERFERENCE;
            case NO_WRITES -> ReasonCategory.LOOP_POLICY;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(outcome); // ExcludeFromJacocoGeneratedReport
        };
    }

    /**
     * Human-readable detail note for a physical write rejection.
     */
    private static String defaultNote(WriteRejection reason) {
        return switch (reason) {
            case UNKNOWN_JAVA_TYPE -> "write base type has no Java type";
            case NON_CONSTANT_OFFSET -> "write offset is not a compile-time constant";
            case FIELD_IDENTITY_OFFSET_MISMATCH -> "field location identity does not match the base type and constant offset";
            case INIT_LOCATION_WITHOUT_FIELD -> "initialization location did not match an accepted initialization-write shape";
            case NAMED_LOCATION_WITHOUT_FIELD -> "named location is not an accepted Java field or initialization location";
            case RAW_OBJECT_OFFSET_WITHOUT_FIELD -> "constant object offset has no recognized field or named location";
            case FIELD_KIND_MISMATCH -> "field kind and written value kind differ";
            case REQUIRES_WRITE_BARRIER -> "write requires barrier handling that write sinking does not move yet";
            case VOLATILE -> "volatile field write must not move";
            case EXCLUDED_FIELD -> "field matched WriteSinkingExcludeFields";
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(reason); // ExcludeFromJacocoGeneratedReport
        };
    }

    /**
     * Converts the primary write rejection in a loop into the specific
     * {@code INELIGIBLE_*} loop-outcome reason.
     */
    private static String noEligibleReason(WriteRejection reason) {
        if (reason == null) {
            return "INELIGIBLE_UNCLASSIFIED_WRITE";
        }
        return switch (reason) {
            case UNKNOWN_JAVA_TYPE -> "INELIGIBLE_RAW_OR_UNKNOWN_BASE";
            case NON_CONSTANT_OFFSET -> "INELIGIBLE_DYNAMIC_OFFSET";
            case FIELD_IDENTITY_OFFSET_MISMATCH -> "INELIGIBLE_FIELD_IDENTITY_OFFSET_MISMATCH";
            case INIT_LOCATION_WITHOUT_FIELD -> "INELIGIBLE_INIT_LOCATION_WITHOUT_FIELD";
            case NAMED_LOCATION_WITHOUT_FIELD -> "INELIGIBLE_NAMED_LOCATION_WITHOUT_FIELD";
            case RAW_OBJECT_OFFSET_WITHOUT_FIELD -> "INELIGIBLE_RAW_OBJECT_OFFSET_WITHOUT_FIELD";
            case FIELD_KIND_MISMATCH -> "INELIGIBLE_FIELD_KIND_MISMATCH";
            case REQUIRES_WRITE_BARRIER -> "INELIGIBLE_REQUIRES_WRITE_BARRIER";
            case VOLATILE -> "INELIGIBLE_VOLATILE_FIELD";
            case EXCLUDED_FIELD -> "INELIGIBLE_EXCLUDED_FIELD";
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(reason); // ExcludeFromJacocoGeneratedReport
        };
    }

    /**
     * Builds the loop-level detail note for a {@link LoopOutcome#NO_ELIGIBLE_WRITES} row.
     */
    private static String noEligibleNote(WriteRejection reason) {
        if (reason == null) {
            return "loop writes are structurally ineligible, but no write-level rejection reason was recorded";
        }
        return "loop writes are structurally ineligible; primary write rejection: " + defaultNote(reason);
    }

    /**
     * Human-readable detail note for a candidate rejection or invalidation.
     */
    private static String defaultNote(CandidateRejection reason) {
        return switch (reason) {
            case LOOP_DEPENDENT_ADDRESS -> "write address depends on loop state";
            case MEMORY_ACCESS_READ -> "same-location read observes movable write";
            case SINGLE_MEMORY_KILL -> "single-location memory kill invalidates movable write";
            case MULTI_MEMORY_KILL -> "multi-location memory kill invalidates movable write";
            case GLOBAL_KILL -> "global memory kill invalidates movable write";
            case FORCED_COMMIT -> "stateful control flow forces movable writes to commit";
            case MERGE_VALUE_OR_BARRIER -> "branch values or barrier metadata cannot merge";
            case MERGE_ORDERING_CONFLICT -> "merge ordering cannot preserve memory effects";
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(reason); // ExcludeFromJacocoGeneratedReport
        };
    }

    /**
     * Human-readable detail note for an unchanged-loop outcome.
     */
    private static String defaultNote(LoopOutcome outcome) {
        return switch (outcome) {
            case NO_WRITES -> "loop contains no physical writes";
            case EXPLICITLY_DISABLED -> "neverWriteSink marker active";
            case GLOBAL_KILL_OR_FORCED_COMMIT -> "movable writes killed globally or forced to commit before loop exit";
            case NO_ELIGIBLE_WRITES -> "loop writes are structurally ineligible";
            case NO_SINKABLE_WRITES -> "analysis found no legal sinkable write";
            case SINKABLE_NOT_CHANGED -> "sinkable writes found but no loop-body write was removed";
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(outcome); // ExcludeFromJacocoGeneratedReport
        };
    }

    /**
     * Formats the loop header node id for the details CSV.
     */
    private static String loopHeaderId(CFGLoop<HIRBlock> loop) {
        return loop == null ? "" : nodeId(loop.getHeader().getBeginNode());
    }

    /**
     * Formats the loop nesting depth for the details CSV.
     */
    private static String loopDepth(CFGLoop<HIRBlock> loop) {
        return loop == null ? "" : String.valueOf(loop.getDepth());
    }

    /**
     * Formats the loop header relative frequency for the details CSV.
     */
    private static String loopFrequency(CFGLoop<HIRBlock> loop) {
        return loop == null ? "" : String.valueOf(loop.getHeader().getRelativeFrequency());
    }

    /**
     * Formats a graph node id for stable diagnostic output.
     */
    private static String nodeId(Node node) {
        return node == null ? "" : node.toString(Verbosity.Id);
    }

    /**
     * Returns the aggregate counter for a mutually exclusive loop outcome.
     */
    private static CounterKey counter(LoopOutcome outcome) {
        return switch (outcome) {
            case NO_WRITES -> loopOutcomeNoWritesCounter;
            case EXPLICITLY_DISABLED -> loopOutcomeExplicitlyDisabledCounter;
            case GLOBAL_KILL_OR_FORCED_COMMIT -> loopOutcomeGlobalKillOrForcedCommitCounter;
            case NO_ELIGIBLE_WRITES -> loopOutcomeNoEligibleWritesCounter;
            case NO_SINKABLE_WRITES -> loopOutcomeNoSinkableWritesCounter;
            case SINKABLE_NOT_CHANGED -> loopOutcomeSinkableNotChangedCounter;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(outcome); // ExcludeFromJacocoGeneratedReport
        };
    }

    /**
     * Returns the aggregate counter for a physical write rejection.
     */
    private static CounterKey counter(WriteRejection reason) {
        return switch (reason) {
            case UNKNOWN_JAVA_TYPE -> writesRejectedUnknownJavaTypeCounter;
            case NON_CONSTANT_OFFSET -> writesRejectedNonConstantOffsetCounter;
            case FIELD_IDENTITY_OFFSET_MISMATCH -> writesRejectedFieldIdentityOffsetMismatchCounter;
            case INIT_LOCATION_WITHOUT_FIELD -> writesRejectedInitLocationWithoutFieldCounter;
            case NAMED_LOCATION_WITHOUT_FIELD -> writesRejectedNamedLocationWithoutFieldCounter;
            case RAW_OBJECT_OFFSET_WITHOUT_FIELD -> writesRejectedRawObjectOffsetWithoutFieldCounter;
            case FIELD_KIND_MISMATCH -> writesRejectedFieldKindMismatchCounter;
            case REQUIRES_WRITE_BARRIER -> writesRejectedRequiresWriteBarrierCounter;
            case VOLATILE -> writesRejectedVolatileCounter;
            case EXCLUDED_FIELD -> writesRejectedExcludedFieldCounter;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(reason); // ExcludeFromJacocoGeneratedReport
        };
    }

    /**
     * Returns the aggregate counter for a candidate rejection or invalidation.
     */
    private static CounterKey counter(CandidateRejection reason) {
        return switch (reason) {
            case LOOP_DEPENDENT_ADDRESS -> candidatesRejectedLoopDependentAddressCounter;
            case MEMORY_ACCESS_READ -> candidatesInvalidatedMemoryAccessReadCounter;
            case SINGLE_MEMORY_KILL -> candidatesInvalidatedSingleMemoryKillCounter;
            case MULTI_MEMORY_KILL -> candidatesInvalidatedMultiMemoryKillCounter;
            case GLOBAL_KILL -> candidatesInvalidatedGlobalKillCounter;
            case FORCED_COMMIT -> candidatesInvalidatedForcedCommitCounter;
            case MERGE_VALUE_OR_BARRIER -> candidatesInvalidatedMergeValueOrBarrierCounter;
            case MERGE_ORDERING_CONFLICT -> candidatesInvalidatedMergeOrderingConflictCounter;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(reason); // ExcludeFromJacocoGeneratedReport
        };
    }

    /**
     * Returns the aggregate counter for a coarse primary candidate rejection.
     */
    private static CounterKey counter(PrimaryCandidateRejection reason) {
        return switch (reason) {
            case LOOP_POLICY -> primaryRejectedLoopPolicyCounter;
            case WRITE_PROPERTY -> primaryRejectedWritePropertyCounter;
            case LOOP_DEPENDENT_ADDRESS -> primaryRejectedLoopDependentAddressCounter;
            case MEMORY_INTERFERENCE -> primaryRejectedMemoryInterferenceCounter;
            case MERGE_OR_ORDERING_CONFLICT -> primaryRejectedMergeOrOrderingConflictCounter;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(reason); // ExcludeFromJacocoGeneratedReport
        };
    }

    /**
     * Scales a block frequency into the integer {@code X1000} counter domain.
     */
    private static long scaledFrequency(HIRBlock block) {
        if (block == null) {
            return FREQUENCY_SCALE;
        }
        double frequency = block.getRelativeFrequency();
        if (!Double.isFinite(frequency) || frequency <= 0D) {
            return 0L;
        }
        double scaled = frequency * FREQUENCY_SCALE;
        if (scaled >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.round(scaled);
    }
}
