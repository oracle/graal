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

package jdk.graal.compiler.phases.common.writesinking.data;

import static jdk.graal.compiler.phases.common.writesinking.WriteSinkingUtil.canOverrideOldData;
import static jdk.graal.compiler.phases.common.writesinking.WriteSinkingUtil.getWriteRejectionReason;
import static jdk.graal.compiler.phases.common.writesinking.WriteSinkingUtil.isForcedCommitPoint;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MultiMemoryKill;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.graph.ReentrantBlockIterator;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.phases.common.writesinking.LoopSinkAnalyser;
import jdk.graal.compiler.phases.common.writesinking.WriteSinkingDiagnostics;
import jdk.graal.compiler.phases.common.writesinking.WriteSinkingDiagnostics.CandidateRejection;
import jdk.graal.compiler.phases.common.writesinking.WriteSinkingDiagnostics.MethodMetrics;
import jdk.graal.compiler.phases.common.writesinking.WriteSinkingDiagnostics.WriteRejection;
import jdk.graal.compiler.phases.common.writesinking.WriteSinkingUtil;

/**
 * Shared {@link ReentrantBlockIterator} closure for write-sinking analysis and graph rewriting.
 * <p>
 * It visits fixed nodes in scheduled block order while {@link ReentrantBlockIterator} handles
 * merges and nested loops. Write sinking reuses this walk for two closely related passes:
 * {@link LoopSinkAnalyser} records which writes can be deferred through a loop, and the application
 * closure removes those writes and commits them at loop exits. Sharing the same node walk keeps
 * both passes aligned for memory reads, memory kills, forced commit points, and field-exclusion
 * checks.
 */
public abstract class MovableWriteClosure<T extends MovableWriteMergeProcessor.DefaultProcessableState> extends ReentrantBlockIterator.BlockIteratorClosure<T> {
    /**
     * Identifies which stage of write sinking is using the shared scheduled-node walk.
     */
    protected enum TraversalMode {
        ANALYSIS("analysis"),
        APPLICATION("application");

        private final String logName;

        TraversalMode(String logName) {
            this.logName = logName;
        }
    }

    /**
     * Compiler providers needed for write eligibility checks, such as resolving field metadata from
     * the write address and location identity.
     */
    final CoreProviders context;

    /**
     * Optional field-name filter whose matching writes are intentionally left in place.
     */
    protected final MethodFilter excludeFieldsFilter;

    /**
     * Traversal stage using this closure. This affects only debug logging; analysis and application
     * behavior are defined by the subclass hooks.
     */
    protected final TraversalMode traversalMode;

    /**
     * Per-method diagnostics collector. This is a no-op recorder when diagnostics are disabled.
     */
    protected final MethodMetrics diagnostics;

    /**
     * Creates a closure that processes movable-write state while walking fixed blocks.
     */
    public MovableWriteClosure(CoreProviders context,
                    MethodFilter excludeFieldsFilter,
                    TraversalMode traversalMode) {
        this(context, excludeFieldsFilter, traversalMode, WriteSinkingDiagnostics.disabledMethodMetrics());
    }

    /**
     * Creates a closure that processes movable-write state while recording diagnostics in
     * {@code diagnostics}.
     */
    public MovableWriteClosure(CoreProviders context,
                    MethodFilter excludeFieldsFilter,
                    TraversalMode traversalMode,
                    MethodMetrics diagnostics) {
        this.context = context;
        this.excludeFieldsFilter = excludeFieldsFilter;
        this.traversalMode = traversalMode;
        this.diagnostics = diagnostics;
    }

    @Override
    protected T processBlock(HIRBlock block, T currentState) {
        for (FixedNode node : block.getNodes()) {
            processNode(node, currentState);
        }
        return currentState;
    }

    /**
     * Processes a single fixed node and updates {@code state} according to write-sinking rules.
     */
    public final void processNode(FixedNode node, T state) {
        if (!tryRegisterSingleLocationWrite(node, state)) {
            processNonSunkNode(node, state);
        }
    }

    /**
     * Attempts to classify and register a single-location write for sinking. Returns {@code true}
     * only when the write has been handled completely by the write path, including the
     * field-exclusion case where the write is intentionally left in place and must not also be
     * processed as a normal memory kill.
     */
    private boolean tryRegisterSingleLocationWrite(FixedNode node, T state) {
        WriteNode write = asSingleLocationWrite(node);
        if (write == null) {
            return false;
        }

        MovableWriteState.CacheEntry identifier = WriteSinkingUtil.getEntryFromWrite(write);
        WriteRejection rejection = getWriteRejectionReason(identifier.base, identifier.offset, write, context.getMetaAccess());
        if (rejection != null) {
            diagnostics.recordWriteRejected(write, rejection);
            return false;
        }

        diagnostics.recordEligibleWrite(write);
        killPriorIncompatibleWrite(state, write, identifier);
        if (isExcludedField(write, identifier)) {
            return true;
        }
        return registerWrite(state, write, identifier);
    }

    /**
     * Returns {@code node} as a write with one precise killed location, or {@code null} when it is not
     * a write-sinking candidate at this traversal level.
     */
    private static WriteNode asSingleLocationWrite(FixedNode node) {
        if (node instanceof WriteNode write && write.getKilledLocationIdentity().isSingle()) {
            return write;
        }
        return null;
    }

    /**
     * Commits any movable write to the same cache entry when the new write cannot safely override
     * the previous value or barrier state.
     */
    private void killPriorIncompatibleWrite(T state, WriteNode write, MovableWriteState.CacheEntry identifier) {
        MovableWriteState.CacheData data = state.getCacheData(identifier);
        if (data != null && !canOverrideOldData(write, data)) {
            LocationIdentity identity = write.getLocationIdentity();
            int killedWrites = killWritesUntil(state, identity, identifier, write, CandidateRejection.MERGE_VALUE_OR_BARRIER);
            this.logWriteKills(identity, write, identifier, killedWrites);
        }
    }

    /**
     * Handles the debug-only field exclusion option. Excluded writes stay in their original
     * position, but are considered handled here so they do not invalidate movable writes like an
     * unrelated memory kill would.
     */
    private boolean isExcludedField(WriteNode write, MovableWriteState.CacheEntry identifier) {
        if (!WriteSinkingUtil.isFieldExcluded(this.excludeFieldsFilter, identifier.identity)) {
            return false;
        }

        DebugContext debug = write.getDebug();
        debug.log(DebugContext.VERBOSE_LEVEL, "Excluding field \"%s\" during write sinking of \"%s\"", identifier,
                        write.graph().asJavaMethod().format("%H.%n(%p)"));
        diagnostics.recordWriteRejected(write, WriteRejection.EXCLUDED_FIELD);
        return true;
    }

    /**
     * Processes reads, kills, forced commits, and subclass-specific fixed nodes for everything that
     * was not handled as a sunk or explicitly excluded write.
     */
    private void processNonSunkNode(FixedNode node, T state) {
        if (node instanceof MemoryAccess memoryAccess) {
            killIdentity(state, memoryAccess.getLocationIdentity(), node, CandidateRejection.MEMORY_ACCESS_READ);
        } else if (MemoryKill.isSingleMemoryKill(node)) {
            LocationIdentity locationIdentity = ((SingleMemoryKill) node).getKilledLocationIdentity();
            killIdentity(state, locationIdentity, node, locationIdentity.isAny() ? CandidateRejection.GLOBAL_KILL : CandidateRejection.SINGLE_MEMORY_KILL);
        } else if (MemoryKill.isMultiMemoryKill(node)) {
            for (LocationIdentity killedLocationIdentity : ((MultiMemoryKill) node).getKilledLocationIdentities()) {
                killIdentity(state, killedLocationIdentity, node, killedLocationIdentity.isAny() ? CandidateRejection.GLOBAL_KILL : CandidateRejection.MULTI_MEMORY_KILL);
            }
        }
        if (isForcedCommitPoint(node)) {
            killIdentity(state, LocationIdentity.any(), node, CandidateRejection.FORCED_COMMIT);
        }
        processOther(state, node);
    }

    /**
     * Commits or kills movable writes for {@code locationIdentity} at {@code commitPoint}.
     */
    protected void killIdentity(T state, LocationIdentity locationIdentity, FixedNode commitPoint) {
        killIdentity(state, locationIdentity, commitPoint, locationIdentity.isAny() ? CandidateRejection.GLOBAL_KILL : CandidateRejection.SINGLE_MEMORY_KILL);
    }

    /**
     * Commits or kills movable writes for {@code locationIdentity} at {@code commitPoint}, recording
     * the invalidation reason for diagnostics.
     */
    protected void killIdentity(T state, LocationIdentity locationIdentity, FixedNode commitPoint, CandidateRejection reason) {
        int killedWrites = killWritesUntil(state, locationIdentity, null, commitPoint, reason);
        this.logWriteKills(locationIdentity, commitPoint, null, killedWrites);
    }

    /**
     * Logs how many movable writes were removed or committed because a fixed node made
     * {@code locationIdentity} observable. The optional {@code identifier} is present when the kill
     * is bounded by a specific write key rather than the whole location.
     */
    private void logWriteKills(LocationIdentity locationIdentity, FixedNode commitPoint, MovableWriteState.CacheEntry identifier, int killedWrites) {
        DebugContext debug = commitPoint.getDebug();
        debug.log(DebugContext.VERBOSE_LEVEL, "Killed location identity \"%s\" due to \"%s\" and removed %d writes during %s (targeting \"%s\")", locationIdentity, commitPoint, killedWrites,
                        traversalMode.logName, identifier);
    }

    /**
     * Commits movable writes in {@code state} until the memory effects killed by {@code commitPoint}
     * are no longer hidden behind the traversal state.
     */
    protected abstract int killWritesUntil(T state, LocationIdentity locationIdentity, MovableWriteState.CacheEntry identifier, FixedNode commitPoint, CandidateRejection reason);

    /**
     * Handles an eligible write after the common traversal has classified its exact write key.
     */
    protected abstract boolean registerWrite(T state, WriteNode write, MovableWriteState.CacheEntry identifier);

    /**
     * Lets subclasses process fixed nodes that are not handled by the common write, read, or memory
     * kill logic.
     */
    protected abstract void processOther(T state, FixedNode node);
}
