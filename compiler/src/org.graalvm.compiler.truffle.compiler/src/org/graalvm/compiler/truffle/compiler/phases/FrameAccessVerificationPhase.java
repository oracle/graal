/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator.LoopInfo;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator.NodeIteratorClosure;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.compiler.PerformanceInformationHandler;
import org.graalvm.compiler.truffle.compiler.nodes.frame.NewFrameNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameAccessType;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameAccessorNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameClearNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameCopyNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameSetNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameSwapNode;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.SpeculationLog.Speculation;

/**
 * This phase performs a pass over the control flow graph and checks whether the frame slot tags
 * match at all merges. If they do not match, then a deoptimization is inserted that invalidates the
 * frame intrinsic speculation.
 *
 * This analysis will insert {@link VirtualFrameSetNode}s to change the type of uninitialized slots
 * whenever this is necessary to produce matching types at merges.
 */
public final class FrameAccessVerificationPhase extends BasePhase<CoreProviders> {

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private static final byte NONE = (byte) 0xff;
    private static final int TYPE_MASK = 0xf;
    private static final int MODE_MASK = 0x30;

    private static final int MODE_CLEARED = 0x00;
    private static final int MODE_VALUE = 0x10;

    private static byte cleared(byte tag) {
        assert tag <= TYPE_MASK;
        return (byte) ((tag & TYPE_MASK) | MODE_CLEARED);
    }

    private static byte withValue(byte tag) {
        assert tag <= TYPE_MASK;
        return (byte) ((tag & TYPE_MASK) | MODE_VALUE);
    }

    private static byte type(byte tag) {
        return (byte) (tag & TYPE_MASK);
    }

    private static byte mode(byte tag) {
        return (byte) (tag & MODE_MASK);
    }

    private final CompilableTruffleAST compilable;

    private abstract static class Effect {
        final NewFrameNode frame;
        final AbstractEndNode insertBefore;
        final int index;

        Effect(NewFrameNode frame, AbstractEndNode insertBefore, int index) {
            this.frame = frame;
            this.insertBefore = insertBefore;
            this.index = index;
        }

        abstract void apply();
    }

    private final class DeoptEffect extends Effect {

        DeoptEffect(NewFrameNode frame, AbstractEndNode insertBefore, int index) {
            super(frame, insertBefore, index);
        }

        @SuppressWarnings("try")
        private void logPerformanceWarningClearIntroducedPhi(Node location) {
            if (PerformanceInformationHandler.isWarningEnabled(PolyglotCompilerOptions.PerformanceWarningKind.FRAME_INCOMPATIBLE_MERGE)) {
                Graph graph = location.graph();
                DebugContext debug = location.getDebug();
                try (DebugContext.Scope s = debug.scope("TrufflePerformanceWarnings", graph)) {
                    Map<String, Object> properties = new LinkedHashMap<>();
                    properties.put("location", location);
                    properties.put("method", compilable.getName());
                    properties.put("index", index);
                    PerformanceInformationHandler.logPerformanceWarning(PolyglotCompilerOptions.PerformanceWarningKind.FRAME_INCOMPATIBLE_MERGE, compilable,
                                    Collections.emptyList(),
                                    "Incompatible frame slot types at merge: this disables the frame intrinsics optimization and potentially causes frames to be materialized. " +
                                                    "Ensure that frame slots are cleared before a control flow merge if they don't contain the same type of value.",
                                    properties);
                    debug.dump(DebugContext.VERBOSE_LEVEL, graph, "perf warn: Incompatible frame slot types for slot %d at %s", index, location);
                } catch (Throwable t) {
                    debug.handle(t);
                }
            }
        }

        @Override
        void apply() {
            if (insertBefore.isAlive()) {
                StructuredGraph graph = insertBefore.graph();
                FixedWithNextNode predecessor = (FixedWithNextNode) insertBefore.predecessor();
                logPerformanceWarningClearIntroducedPhi(predecessor);
                predecessor.setNext(null);
                GraphUtil.killCFG(insertBefore);
                if (predecessor.isAlive()) {
                    Speculation speculation = graph.getSpeculationLog().speculate(frame.getIntrinsifyAccessorsSpeculation());
                    DeoptimizeNode deopt = new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.RuntimeConstraint, speculation);
                    predecessor.setNext(graph.add(deopt));
                }
            }
        }
    }

    private static final class ClearPrimitiveEffect extends Effect {
        final byte accessTag;
        final VirtualFrameAccessType type;

        ClearPrimitiveEffect(NewFrameNode frame, AbstractEndNode insertBefore, int index, byte accessTag, VirtualFrameAccessType type) {
            super(frame, insertBefore, index);
            this.accessTag = accessTag;
            this.type = type;
        }

        @Override
        void apply() {
            if (insertBefore.isAlive()) {
                StructuredGraph graph = insertBefore.graph();
                ConstantNode defaultForKind = ConstantNode.defaultForKind(NewFrameNode.asJavaKind(accessTag), graph);
                graph.addBeforeFixed(insertBefore, graph.add(new VirtualFrameSetNode(frame, index, accessTag, defaultForKind, type, false)));
            }
        }
    }

    private final ArrayList<Effect> effects = new ArrayList<>();

    public FrameAccessVerificationPhase(CompilableTruffleAST compilable) {
        this.compilable = compilable;
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        if (graph.getNodes(NewFrameNode.TYPE).isNotEmpty()) {
            ReentrantNodeIterator.apply(new ReentrantIterator(), graph.start(), new State());
            for (Effect effect : effects) {
                effect.apply();
            }
        }
    }

    private final class State implements Cloneable {

        private final HashMap<NewFrameNode, byte[]> states = new HashMap<>();
        private final HashMap<NewFrameNode, byte[]> indexedStates = new HashMap<>();

        @Override
        public State clone() {
            State newState = new State();
            copy(states, newState.states);
            copy(indexedStates, newState.indexedStates);
            return newState;
        }

        private void copy(HashMap<NewFrameNode, byte[]> from, HashMap<NewFrameNode, byte[]> to) {
            for (Map.Entry<NewFrameNode, byte[]> entry : from.entrySet()) {
                to.put(entry.getKey(), entry.getValue().clone());
            }
        }

        public void add(NewFrameNode frame) {
            assert !states.containsKey(frame) && !indexedStates.containsKey(frame);
            byte[] entries = frame.getFrameSize() == 0 ? EMPTY_BYTE_ARRAY : frame.getFrameSlotKinds().clone();
            states.put(frame, entries);
            byte[] indexedEntries = frame.getIndexedFrameSize() == 0 ? EMPTY_BYTE_ARRAY : frame.getIndexedFrameSlotKinds().clone();
            indexedStates.put(frame, indexedEntries);
        }

        public byte[] get(VirtualFrameAccessorNode accessor) {
            boolean isLegacy = accessor.getType() == VirtualFrameAccessType.Legacy;
            HashMap<NewFrameNode, byte[]> map = isLegacy ? states : indexedStates;
            return map.get(accessor.getFrame());
        }

        public boolean equalsState(State other) {
            assert states.keySet().equals(other.states.keySet());
            assert indexedStates.keySet().equals(other.indexedStates.keySet());
            for (Map.Entry<NewFrameNode, byte[]> entry : states.entrySet()) {
                byte[] entries = entry.getValue();
                byte[] otherEntries = other.states.get(entry.getKey());
                if (!Arrays.equals(entries, otherEntries)) {
                    return false;
                }
            }
            for (Map.Entry<NewFrameNode, byte[]> entry : indexedStates.entrySet()) {
                byte[] entries = entry.getValue();
                byte[] otherEntries = other.indexedStates.get(entry.getKey());
                if (!Arrays.equals(entries, otherEntries)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static boolean inRange(byte[] array, int index) {
        return index >= 0 && index < array.length;
    }

    private final class ReentrantIterator extends NodeIteratorClosure<State> {

        @Override
        protected State processNode(FixedNode node, State currentState) {
            if (node instanceof NewFrameNode) {
                currentState.add((NewFrameNode) node);
            } else if (node instanceof VirtualFrameAccessorNode) {
                VirtualFrameAccessorNode accessor = (VirtualFrameAccessorNode) node;
                VirtualFrameAccessType type = accessor.getType();
                if (type != VirtualFrameAccessType.Auxiliary) {
                    /*
                     * Ignoring operations with invalid indexes - these will be handled during PEA.
                     */
                    byte[] entries = currentState.get(accessor);
                    if (inRange(entries, accessor.getFrameSlotIndex())) {
                        if (node instanceof VirtualFrameSetNode) {
                            if (accessor.getAccessTag() == NewFrameNode.FrameSlotKindObjectTag) {
                                entries[accessor.getFrameSlotIndex()] = cleared(NewFrameNode.FrameSlotKindLongTag);
                            } else {
                                entries[accessor.getFrameSlotIndex()] = withValue(NewFrameNode.asStackTag((byte) accessor.getAccessTag()));
                            }
                        } else if (node instanceof VirtualFrameClearNode) {
                            entries[accessor.getFrameSlotIndex()] = cleared(NewFrameNode.FrameSlotKindLongTag);
                        } else if (node instanceof VirtualFrameCopyNode) {
                            VirtualFrameCopyNode copy = (VirtualFrameCopyNode) node;
                            if (inRange(entries, copy.getFrameSlotIndex())) {
                                entries[copy.getTargetSlotIndex()] = entries[copy.getFrameSlotIndex()];
                            }
                        } else if (node instanceof VirtualFrameSwapNode) {
                            VirtualFrameSwapNode swap = (VirtualFrameSwapNode) node;
                            if (inRange(entries, swap.getFrameSlotIndex())) {
                                byte temp = entries[swap.getTargetSlotIndex()];
                                entries[swap.getTargetSlotIndex()] = entries[swap.getFrameSlotIndex()];
                                entries[swap.getFrameSlotIndex()] = temp;
                            }
                        }
                    }
                }
            }
            return currentState;
        }

        @Override
        protected State merge(AbstractMergeNode merge, List<State> states) {
            return merge(merge, states, effects);
        }

        private State merge(AbstractMergeNode merge, List<State> states, ArrayList<Effect> firstEndEffects) {
            State result = states.get(0).clone();
            // determine the set of frames that are alive after this merge
            HashSet<NewFrameNode> frames = new HashSet<>(result.states.keySet());
            for (int i = 1; i < states.size(); i++) {
                frames.retainAll(states.get(i).states.keySet());
            }

            byte[] entries = new byte[states.size()];
            byte[][] entryArrays = new byte[states.size()][];

            for (NewFrameNode frame : frames) {
                for (int i = 0; i < states.size(); i++) {
                    entryArrays[i] = states.get(i).states.get(frame);
                }
                byte[] resultEntries = result.states.get(frame);
                for (int entryIndex = 0; entryIndex < resultEntries.length; entryIndex++) {
                    for (int i = 0; i < states.size(); i++) {
                        entries[i] = entryArrays[i][entryIndex];
                    }
                    mergeEntries(merge, frame, resultEntries, entries, entryIndex, VirtualFrameAccessType.Legacy, firstEndEffects);
                }
                for (int i = 0; i < states.size(); i++) {
                    entryArrays[i] = states.get(i).indexedStates.get(frame);
                }
                resultEntries = result.indexedStates.get(frame);
                for (int entryIndex = 0; entryIndex < resultEntries.length; entryIndex++) {
                    for (int i = 0; i < states.size(); i++) {
                        entries[i] = entryArrays[i][entryIndex];
                    }
                    mergeEntries(merge, frame, resultEntries, entries, entryIndex, VirtualFrameAccessType.Indexed, firstEndEffects);
                }
            }

            result.states.keySet().retainAll(frames);
            result.indexedStates.keySet().retainAll(frames);

            return result;
        }

        private void mergeEntries(AbstractMergeNode merge, NewFrameNode frame, byte[] resultEntries, byte[] entries, int entryIndex, VirtualFrameAccessType accessType,
                        ArrayList<Effect> firstEndEffects) {
            byte result = entries[0];
            boolean allMatch = true;
            for (int i = 1; i < entries.length; i++) {
                if (entries[i] != result) {
                    allMatch = false;
                    break;
                }
            }
            if (!allMatch) {
                // not a simple match, look for non-cleared types
                byte definitiveType = NONE;
                for (int i = 0; i < entries.length; i++) {
                    byte entry = entries[i];
                    assert entry != NewFrameNode.NO_TYPE_MARKER : "no set/clear nodes with this index should be generated by TruffleGraphBuilderPlugin";
                    if (mode(entry) == MODE_VALUE) {
                        if (definitiveType == NONE) {
                            definitiveType = type(entry);
                        } else if (definitiveType == type(entry)) {
                            // match
                        } else {
                            // different definitive types at merge
                            (i == 0 ? firstEndEffects : effects).add(new DeoptEffect(frame, merge.phiPredecessorAt(i), entryIndex));
                            entries[i] = withValue(definitiveType);
                        }
                    }
                }
                // insert VirtualFrameSetNodes as necessary to ensure similar types at phis
                result = definitiveType == NONE ? NewFrameNode.FrameSlotKindLongTag : definitiveType;
                for (int i = 0; i < entries.length; i++) {
                    if (type(entries[i]) != result) {
                        (i == 0 ? firstEndEffects : effects).add(new ClearPrimitiveEffect(frame, merge.phiPredecessorAt(i), entryIndex, result, accessType));
                    }
                }
                result = definitiveType == NONE ? cleared(result) : withValue(result);
            }
            resultEntries[entryIndex] = result;
        }

        @Override
        protected State afterSplit(AbstractBeginNode node, State oldState) {
            return oldState.clone();
        }

        @Override
        protected EconomicMap<LoopExitNode, State> processLoop(LoopBeginNode loop, State initial) {
            State initialState = initial;
            LoopInfo<State> info;
            /*
             * Loops are processed iteratively until the merged state is the same as the initial
             * state.
             */
            while (true) {
                int sizeBeforeLoop = effects.size();
                info = ReentrantNodeIterator.processLoop(this, loop, initialState.clone());
                ArrayList<State> states = new ArrayList<>();
                states.add(initialState);
                assert loop.forwardEndCount() == 1;
                for (int i = 1; i < loop.phiPredecessorCount(); i++) {
                    states.add(info.endStates.get((LoopEndNode) loop.phiPredecessorAt(i)));
                }

                ArrayList<Effect> preLoopEffects = new ArrayList<>();
                State mergeResult = merge(loop, states, preLoopEffects);
                if (mergeResult.equalsState(initialState)) {
                    effects.addAll(preLoopEffects);
                    break;
                }
                initialState = mergeResult;
                while (effects.size() > sizeBeforeLoop) {
                    effects.remove(effects.size() - 1);
                }
                effects.addAll(preLoopEffects);
            }
            return info.exitStates;
        }
    }
}
