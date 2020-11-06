/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.truffle.compiler.phases;

import static org.graalvm.compiler.truffle.common.TruffleCompilerRuntime.getRuntime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.ValueProxyNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.virtual.EscapeObjectState;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.compiler.PerformanceInformationHandler;
import org.graalvm.compiler.truffle.compiler.substitutions.KnownTruffleTypes;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.compiler.virtual.nodes.MaterializedObjectState;
import org.graalvm.compiler.virtual.nodes.VirtualObjectState;

import com.oracle.truffle.api.frame.FrameSlot;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This phase inspects the FrameState virtual objects corresponding to truffle virtual frames.
 * <p>
 * For each slot in each virtual frame:
 * <ul>
 * <li>If the slot is cleared (ie: tag is illegal), then we null out the slot in both supporting
 * arrays
 * <li>If the slot is determined to contain an object (respectively a primitive), we null out the
 * counterpart slot in the primitive array (respectively the object array)
 * <li>If nothing can be inferred about the kind of entry in the slot, we leave it alone (most
 * likely corresponds to two branches putting different kinds merging execution)
 * <li>If we determine one of the slots to be cleared in a branch, but not the other and they merge
 * execution, we report a performance warning.
 * </ul>
 * <p>
 * This behavior has two main points of interest:
 * <ul>
 * <li>Allow languages to implement liveness analysis through the
 * {@link com.oracle.truffle.api.frame.Frame#clear(FrameSlot)} method, which will eliminate stale
 * locals early, and report potential errors in the implementation.
 * <li>Stack-based interpreters re-use slots for different kinds. By clearing the counterpart array,
 * we expect stale values to not be kept alive for longer than necessary.
 * </ul>
 */
public class FrameClearPhase extends BasePhase<CoreProviders> {
    private final CanonicalizerPhase canonicalizer;
    private final CompilableTruffleAST compilable;

    // Information on the FrameWithoutBoxing class
    private final ResolvedJavaType frameType;
    private final int tagArrayIndex;
    private final int objectArrayIndex;
    private final int primitiveArrayIndex;

    // Values for tags in the tag slot of the frames
    private final int illegalTag;
    private final int objectTag;

    // Constants to insert on clear.
    private ValueNode nullConstant;
    private ValueNode zeroConstant;

    // Cached phis that have been proven can be illegals
    private final EconomicSet<ValueNode> knownIllegals = EconomicSet.create();
    // Cached phis that have been proven not to be illegals
    private final EconomicSet<ValueNode> knownSafe = EconomicSet.create();
    // Cached tag nodes that have already reported a performance warning.
    private final EconomicSet<ValueNode> reported = EconomicSet.create();

    public FrameClearPhase(KnownTruffleTypes knownTruffleTypes, CanonicalizerPhase canonicalizer, CompilableTruffleAST compilable) {
        this(knownTruffleTypes.classFrameClass, knownTruffleTypes.fieldTags, knownTruffleTypes.fieldLocals, knownTruffleTypes.fieldPrimitiveLocals, canonicalizer, compilable);
    }

    private FrameClearPhase(ResolvedJavaType frameType,
                    ResolvedJavaField tagArray, ResolvedJavaField objectArray, ResolvedJavaField primitiveArray,
                    CanonicalizerPhase canonicalizer,
                    CompilableTruffleAST compilable) {
        this.frameType = frameType;
        this.tagArrayIndex = findFieldIndex(frameType, tagArray);
        this.objectArrayIndex = findFieldIndex(frameType, objectArray);
        this.primitiveArrayIndex = findFieldIndex(frameType, primitiveArray);
        assert tagArrayIndex >= 0 && objectArrayIndex >= 0 && primitiveArrayIndex >= 0;

        TruffleCompilerRuntime runtime = getRuntime();
        this.illegalTag = runtime.getFrameSlotKindTagForJavaKind(JavaKind.Illegal);
        this.objectTag = runtime.getFrameSlotKindTagForJavaKind(JavaKind.Object);
        this.canonicalizer = canonicalizer;
        this.compilable = compilable;
    }

    private static int findFieldIndex(ResolvedJavaType type, ResolvedJavaField field) {
        ResolvedJavaField[] fields = type.getInstanceFields(true);
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].equals(field)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        doRun(graph, context);
    }

    private void doRun(StructuredGraph graph, CoreProviders context) {
        nullConstant = ConstantNode.defaultForKind(JavaKind.Object, graph);
        zeroConstant = ConstantNode.defaultForKind(JavaKind.Long, graph);

        for (FrameState fs : graph.getNodes(FrameState.TYPE)) {
            EconomicMap<VirtualObjectNode, EscapeObjectState> objectStates = getObjectStateMappings(fs);
            for (EscapeObjectState objectState : objectStates.getValues()) {
                // Iterate over escaped truffle frames in the FrameState
                if ((objectState instanceof VirtualObjectState) && objectState.object().type().equals(frameType)) {
                    VirtualObjectState vObjState = (VirtualObjectState) objectState;
                    ValueNode tagArrayValue = vObjState.values().get(tagArrayIndex);
                    // make sure everything is virtual
                    if ((tagArrayValue instanceof VirtualArrayNode) &&
                                    (vObjState.values().get(objectArrayIndex) instanceof VirtualArrayNode) &&
                                    (vObjState.values().get(primitiveArrayIndex) instanceof VirtualArrayNode)) {
                        EscapeObjectState tagArrayVirtual = objectStates.get((VirtualArrayNode) tagArrayValue);
                        EscapeObjectState objectArrayVirtual = objectStates.get((VirtualArrayNode) vObjState.values().get(objectArrayIndex));
                        EscapeObjectState primitiveArrayVirtual = objectStates.get((VirtualArrayNode) vObjState.values().get(primitiveArrayIndex));

                        assert tagArrayVirtual instanceof VirtualObjectState && objectArrayVirtual instanceof VirtualObjectState && primitiveArrayVirtual instanceof VirtualObjectState;

                        int length = ((VirtualArrayNode) tagArrayValue).entryCount();
                        for (int i = 0; i < length; i++) {
                            maybeClearFrameSlot((VirtualObjectState) tagArrayVirtual, (VirtualObjectState) objectArrayVirtual, (VirtualObjectState) primitiveArrayVirtual, i);
                        }
                    }
                }
            }
        }

        // Get rid of newly unused nodes.
        canonicalizer.apply(graph, context);
    }

    private void maybeClearFrameSlot(VirtualObjectState tagArrayVirtual, VirtualObjectState objectArrayVirtual, VirtualObjectState primitiveArrayVirtual, int i) {
        ValueNode tagNode = tagArrayVirtual.values().get(i);
        if (tagNode == null) {
            // Uninitialized slot, will be initialized to default on deopt anyway.
            return;
        }
        if (!tagNode.isJavaConstant()) {
            // frame slot can be of multiple kinds here
            assert tagNode.stamp(NodeView.DEFAULT) instanceof PrimitiveStamp;
            PrimitiveStamp tagStamp = (PrimitiveStamp) tagNode.stamp(NodeView.DEFAULT);

            boolean canBeIllegal;
            if (tagStamp.isUnrestricted()) {
                // Circular phi node: explore phi graph for an illegal constant.
                canBeIllegal = unbalancedIllegal(tagNode);
            } else {
                // Regular node: just check stamp.
                canBeIllegal = stampHasIllegal(tagStamp);
            }

            if (canBeIllegal) {
                // Entry can be illegal: report.
                logPerformanceWarningClearIntroducedPhi(tagNode);
            } else if (!stampHasObject(tagStamp)) {
                // Phi tag with no object possible: free object slot.
                objectArrayVirtual.values().set(i, nullConstant);
            }
            /*-
             * No need to check for primitive kind, it will be there:
             * - Cannot be illegal.
             * - Has at least 2 possible values.
             * - At most 1 possible kind other than primitive.
             * => one of the possible kind values will always be primitive.
             */
        } else {
            int tag = tagNode.asJavaConstant().asInt();
            if (tag == illegalTag) {
                // Cleared slot
                objectArrayVirtual.values().set(i, nullConstant);
                primitiveArrayVirtual.values().set(i, zeroConstant);
            } else {
                // Clear counterpart
                VirtualObjectState toClear = (tag == objectTag) ? primitiveArrayVirtual : objectArrayVirtual;
                ValueNode toSet = (tag == objectTag) ? zeroConstant : nullConstant;
                toClear.values().set(i, toSet);
            }
        }
    }

    private boolean stampHasIllegal(PrimitiveStamp stamp) {
        return !stamp.join(StampFactory.forInteger(stamp.getBits(), illegalTag, illegalTag)).isEmpty();
    }

    private boolean stampHasObject(PrimitiveStamp stamp) {
        return !stamp.join(StampFactory.forInteger(stamp.getBits(), objectTag, objectTag)).isEmpty();
    }

    private class UnbalancedIllegalExplorer {
        private final EconomicSet<ValueNode> visited = EconomicSet.create();

        /*-
         * Note that this is not a complete exploration of the values: We are relying on the fact
         * that accesses to tags are only done through the provided methods of the
         * FrameWithoutBoxing class.
         *
         * In particular, since these methods only ever puts constants in the tag slot, we will
         * assume that a tag can either be:
         * - A constant
         * - A value with restricted stamp (Phi of constants, or ConditionalNode, for example)
         * - A phi between constants and other such phis (loops may introduce phis with itself as
         * input, yielding unrestricted stamps).
         *
         * Any other type of value will be considered a performance warning.
         */
        private boolean explore(ValueNode node) {
            ValueNode toProcess = node;
            while (toProcess instanceof ValueProxyNode) {
                toProcess = ((ValueProxyNode) toProcess).value();
            }
            if (toProcess instanceof ConstantNode) {
                assert toProcess.getStackKind() == JavaKind.Int;
                return toProcess.asJavaConstant().asInt() == illegalTag;
            } else if (!toProcess.stamp(NodeView.DEFAULT).isUnrestricted()) {
                assert toProcess.stamp(NodeView.DEFAULT) instanceof PrimitiveStamp;
                PrimitiveStamp stamp = (PrimitiveStamp) toProcess.stamp(NodeView.DEFAULT);
                return stampHasIllegal(stamp);
            } else if (toProcess instanceof ValuePhiNode) {
                if (knownIllegals.contains(toProcess)) {
                    return true;
                }
                if (visited.contains(toProcess) || knownSafe.contains(toProcess)) {
                    return false;
                }
                visited.add(node);
                for (ValueNode value : ((ValuePhiNode) toProcess).values()) {
                    if (explore(value)) {
                        knownIllegals.add(toProcess);
                        return true;
                    }
                }
                knownSafe.add(toProcess);
                return false;
            } else {
                /* tags 'should' only ever be constants or phi of tags. */
                return true;
            }
        }
    }

    /**
     * Traverses phi graphs to find if phi can be both an illegal and something else.
     */
    private boolean unbalancedIllegal(ValueNode node) {
        return new UnbalancedIllegalExplorer().explore(node);
    }

    // Remaps virtual objects to their escaped states corresponding to the given frame state.
    private static EconomicMap<VirtualObjectNode, EscapeObjectState> getObjectStateMappings(FrameState fs) {
        EconomicMap<VirtualObjectNode, EscapeObjectState> objectStates = EconomicMap.create(Equivalence.IDENTITY);
        FrameState current = fs;
        do {
            if (current.virtualObjectMappingCount() > 0) {
                for (EscapeObjectState state : current.virtualObjectMappings()) {
                    if (!objectStates.containsKey(state.object())) {
                        if (!(state instanceof MaterializedObjectState) || ((MaterializedObjectState) state).materializedValue() != state.object()) {
                            objectStates.put(state.object(), state);
                        }
                    }
                }
            }
            current = current.outerFrameState();
        } while (current != null);
        return objectStates;
    }

    @SuppressWarnings("try")
    private void logPerformanceWarningClearIntroducedPhi(ValueNode location) {
        if (PerformanceInformationHandler.isWarningEnabled(PolyglotCompilerOptions.PerformanceWarningKind.FRAME_CLEAR_PHI)) {
            if (reported.contains(location)) {
                return;
            }
            StructuredGraph graph = location.graph();
            DebugContext debug = location.getDebug();
            try (DebugContext.Scope s = debug.scope("TrufflePerformanceWarnings", graph)) {
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("location", location);
                properties.put("method", compilable.getName());
                PerformanceInformationHandler.logPerformanceWarning(PolyglotCompilerOptions.PerformanceWarningKind.FRAME_CLEAR_PHI, compilable,
                                Collections.emptyList(),
                                "Frame clear introduces new phis in the graph. " +
                                                "This is most likely due to a faulty liveness analysis implementation, or an unexpected control-flow construction. " +
                                                "Make sure all control-flows in the graph are as expected.",
                                properties);
                debug.dump(DebugContext.VERBOSE_LEVEL, graph, "perf warn: Frame clear introduces new phis in the graph: %s", location);
                reported.add(location);
            } catch (Throwable t) {
                debug.handle(t);
            }
        }
    }
}
