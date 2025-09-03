/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.phases;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;

/**
 * Makes sure that all {@link IntegerSwitchNode}s in the graph are contiguous and start at zero.
 * <p>
 * Switches that don't do this (degenerate switches) are replaced with equivalent nodes.
 */
public class WasmSwitchPhase extends BasePhase<CoreProviders> {
    private final CanonicalizerPhase canonicalizer;

    public WasmSwitchPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        for (IntegerSwitchNode switchNode : graph.getNodes().filter(IntegerSwitchNode.class)) {
            if (replaceTwoCase(graph, switchNode)) {
                continue;
            }

            if (correctContiguousOffset(graph, switchNode)) {
                continue;
            }

            // TODO GR-41660 implement more lowering strategies

            // Fall back to a large chain of if-else blocks
            replaceWithIfChain(graph, switchNode);
        }

        // Verification
        graph.getNodes().filter(IntegerSwitchNode.class).forEach(switchNode -> {
            assert isSimplified(switchNode) : switchNode;
        });

        canonicalizer.apply(graph, context);
    }

    /**
     * Replaces a switch with only two cases (a keyed case and the default case) with an
     * {@link IfNode}.
     */
    private static boolean replaceTwoCase(StructuredGraph graph, IntegerSwitchNode switchNode) {
        if (switchNode.keyCount() != 1) {
            return false;
        }

        // We only have a single key and a default case, replace with if-else
        int key = switchNode.intKeyAt(0);
        AbstractBeginNode keySuccessor = switchNode.successorAtKey(key);
        AbstractBeginNode defaultSuccessor = switchNode.defaultSuccessor();

        BranchProbabilityData probabilityData = BranchProbabilityData.create(switchNode.probability(keySuccessor), switchNode.profileSource());

        LogicNode condition = graph.addOrUniqueWithInputs(IntegerEqualsNode.create(switchNode.switchValue(), ConstantNode.forInt(key), NodeView.DEFAULT));
        IfNode ifNode = new IfNode(condition, keySuccessor, defaultSuccessor, probabilityData);
        replaceSwitch(graph, switchNode, ifNode);

        return true;
    }

    /**
     * For contiguous switches that don't start at 0, corrects the switch node so that it starts at
     * 0 (subtracts the smallest key from the input value).
     * <p>
     * This way, the value can be directly used to index a jump table.
     */
    private static boolean correctContiguousOffset(StructuredGraph graph, IntegerSwitchNode switchNode) {
        if (!isContiguous(switchNode)) {
            return false;
        }

        int firstKey = switchNode.intKeyAt(0);

        if (firstKey == 0) {
            // The switch node is already in the desired form.
            assert isSimplified(switchNode) : switchNode;
            return true;
        }

        ValueNode correctedValue = SubNode.sub(graph, switchNode.value(), ConstantNode.forInt(firstKey), NodeView.DEFAULT);
        // The new keys are jus from 0 to keyCount - 1
        int[] newKeys = IntStream.range(0, switchNode.keyCount()).toArray();

        // The successors stay the same
        AbstractBeginNode[] blockSuccessors = new AbstractBeginNode[switchNode.blockSuccessorCount()];
        for (int i = 0; i < blockSuccessors.length; i++) {
            blockSuccessors[i] = switchNode.blockSuccessor(i);
        }

        IntegerSwitchNode newSwitch = new IntegerSwitchNode(correctedValue, blockSuccessors, newKeys, switchNode.getKeySuccessors(), switchNode.getProfileData());

        replaceSwitch(graph, switchNode, newSwitch);
        return true;
    }

    /**
     * Replaces the given {@link IntegerSwitchNode} with a chain of {@link IfNode}s to check all
     * possible cases.
     */
    private static void replaceWithIfChain(StructuredGraph graph, IntegerSwitchNode switchNode) {
        int numSuccessors = switchNode.getSuccessorCount();
        Map<Integer, List<Integer>> successorToKeys = getSuccessorToKeysMap(switchNode);
        assert numSuccessors > 1 : numSuccessors;

        /*
         * The if-else chain is built from the bottom up, starting with the default case and adding
         * IfNodes before it.
         */
        FixedNode nextElseBranch = switchNode.defaultSuccessor();
        switchNode.setBlockSuccessor(switchNode.defaultSuccessorIndex(), null);

        for (int successorIndex = 0; successorIndex < numSuccessors; successorIndex++) {
            AbstractBeginNode successor = switchNode.blockSuccessor(successorIndex);

            if (switchNode.defaultSuccessorIndex() == successorIndex) {
                continue;
            }

            List<Integer> keys = successorToKeys.get(successorIndex);
            assert !keys.isEmpty() : "Successor " + successor + " does not have any associated keys";
            // If any of these nodes are true, we need to jump to the successor.
            Stream<LogicNode> keyChecks = keys.stream().map(ConstantNode::forInt).map(keyNode -> IntegerEqualsNode.create(switchNode.switchValue(), keyNode, NodeView.DEFAULT)).map(
                            graph::addOrUniqueWithInputs);

            LogicNode anyKeyCheck = keyChecks.reduce((l1, l2) -> LogicNode.or(l1, l2, BranchProbabilityData.unknown())).get();

            double successorProbability = switchNode.probability(successor);
            BranchProbabilityData probabilityData = BranchProbabilityData.create(successorProbability, switchNode.profileSource());

            /*
             * Unset the successors in switch node, otherwise it would have multiple predecessors.
             */
            switchNode.setBlockSuccessor(successorIndex, null);
            nextElseBranch = graph.addOrUniqueWithInputs(new IfNode(anyKeyCheck, successor, nextElseBranch, probabilityData));
        }

        assert nextElseBranch instanceof ControlSplitNode : nextElseBranch;
        replaceSwitch(graph, switchNode, (IfNode) nextElseBranch);
    }

    /**
     * Maps the index of a successor of the given switch to all key values that jump to that
     * successor.
     */
    public static Map<Integer, List<Integer>> getSuccessorToKeysMap(IntegerSwitchNode switchNode) {
        int numSuccessors = switchNode.getSuccessorCount();
        Map<Integer, List<Integer>> successorToKeys = IntStream.range(0, numSuccessors).boxed().collect(Collectors.toUnmodifiableMap(Function.identity(), i -> new ArrayList<>()));

        for (int keyIndex = 0; keyIndex < switchNode.keyCount(); keyIndex++) {
            int key = switchNode.intKeyAt(keyIndex);
            int successorIndex = switchNode.successorIndexAtKey(key);
            successorToKeys.get(successorIndex).add(key);
        }

        return successorToKeys;
    }

    /**
     * Replaces an old switch node with a new control split.
     */
    private static void replaceSwitch(StructuredGraph graph, IntegerSwitchNode oldSwitch, ControlSplitNode newNode) {
        for (int i = 0; i < oldSwitch.getSuccessorCount(); i++) {
            if (newNode.successors().contains(oldSwitch.blockSuccessor(i))) {
                /*
                 * Unset the successors in old node, otherwise the new switch can't be added to the
                 * graph (the non-merge successors would have multiple predecessors)
                 */
                oldSwitch.setBlockSuccessor(i, null);
            }
        }

        ControlSplitNode added = graph.addOrUnique(newNode);

        /* Replace old switch with the new switch */
        ((FixedWithNextNode) oldSwitch.predecessor()).setNext(added);

        // Remove the old switch and the dead successors.
        GraphUtil.killCFG(oldSwitch);
    }

    /**
     * Checks if the given switch node is contiguous and starts at zero.
     */
    public static boolean isSimplified(IntegerSwitchNode switchNode) {
        int firstKey = switchNode.intKeyAt(0);
        return isContiguous(switchNode) && firstKey == 0;
    }

    /**
     * Checks if the given switch node has contiguous keys.
     */
    public static boolean isContiguous(IntegerSwitchNode switchNode) {
        assert switchNode.isSorted() : switchNode;
        int keyCount = switchNode.keyCount();
        assert NumUtil.assertPositiveInt(keyCount);
        int firstKey = switchNode.intKeyAt(0);
        int lastKey = switchNode.intKeyAt(keyCount - 1);

        return lastKey - firstKey + 1 == keyCount;
    }
}
