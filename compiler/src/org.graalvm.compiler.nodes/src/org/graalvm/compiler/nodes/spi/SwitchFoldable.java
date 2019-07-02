/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.nodes.spi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInterface;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.nodes.util.GraphUtil;

public interface SwitchFoldable extends NodeInterface {
    Comparator<KeyData> sorter = Comparator.comparingInt((KeyData k) -> k.key);

    Node getNext();

    ValueNode switchValue();

    boolean updateSwitchData(List<KeyData> keyData, List<AbstractBeginNode> successors, double[] cumulative, List<AbstractBeginNode> duplicates);

    void addDefault(List<AbstractBeginNode> successors);

    boolean isInSwitch(ValueNode switchValue);

    void cutOffCascadeNode();

    void cutOffLowestCascadeNode();

    /**
     * Heuristics that tries to determine whether or not a foldable node was profiled.
     */
    default boolean isNonInitializedProfile() {
        return false;
    }

    final class KeyData {
        public final int key;
        public final double keyProbability;
        public final int keySuccessor;

        public KeyData(int key, double keyProbability, int keySuccessor) {
            this.key = key;
            this.keyProbability = keyProbability;
            this.keySuccessor = keySuccessor;
        }
    }

    static void sort(List<KeyData> keyData) {
        keyData.sort(sorter);
    }

    static boolean isDuplicateKey(int key, List<KeyData> keyData) {
        for (KeyData kd : keyData) {
            if (kd.key == key) {
                // No duplicates
                return true;
            }
        }
        return false;
    }

    static Node skipUpBegins(Node node) {
        Node result = node;
        while (result instanceof BeginNode && result.hasNoUsages()) {
            result = result.predecessor();
        }
        return result;
    }

    static Node skipDownBegins(Node node) {
        Node result = node;
        while (result instanceof BeginNode && result.hasNoUsages()) {
            result = ((BeginNode) result).next();
        }
        return result;
    }

    static boolean maybeIsInSwitch(LogicNode condition) {
        return condition instanceof IntegerEqualsNode && ((IntegerEqualsNode) condition).getY().isJavaConstant();
    }

    static boolean sameSwitchValue(LogicNode condition, ValueNode switchValue) {
        return ((IntegerEqualsNode) condition).getX() == switchValue;
    }

    default SwitchFoldable getParentSwitchNode(ValueNode switchValue) {
        Node result = skipUpBegins(asNode().predecessor());
        if (result instanceof SwitchFoldable && ((SwitchFoldable) result).isInSwitch(switchValue)) {
            return (SwitchFoldable) result;
        }
        return null;
    }

    default SwitchFoldable getChildSwitchNode(ValueNode switchValue) {
        Node result = skipDownBegins(getNext());
        if (result instanceof SwitchFoldable && ((SwitchFoldable) result).isInSwitch(switchValue)) {
            return (SwitchFoldable) result;
        }
        return null;
    }

    /**
     * Collapses a cascade of foldables (IfNode, FixedGuard and IntegerSwitch) into a single switch.
     */
    default boolean switchTransformationOptimization(SimplifierTool tool) {
        if (switchValue() == null || (getParentSwitchNode(switchValue()) == null && getChildSwitchNode(switchValue()) == null)) {
            // Don't bother trying if there is nothing to do.
            return false;
        }
        SwitchFoldable topMostSwitchNode = this;
        ValueNode switchValue = switchValue();
        Stamp switchStamp = switchValue.stamp(NodeView.DEFAULT);

        // Abort if we do not have an int
        if (!(switchStamp instanceof IntegerStamp)) {
            return false;
        }
        if (PrimitiveStamp.getBits(switchStamp) > 32) {
            return false;
        }

        // PlaceHolder for cascade traversal.
        SwitchFoldable iteratingNode = this;

        // Find top-most foldable.
        while (iteratingNode != null) {
            topMostSwitchNode = iteratingNode;
            iteratingNode = iteratingNode.getParentSwitchNode(switchValue);
        }
        List<SwitchFoldable.KeyData> keyData = new ArrayList<>();
        List<AbstractBeginNode> successors = new ArrayList<>();
        List<AbstractBeginNode> unreachable = new ArrayList<>();
        double[] cumulative = {1.0d};

        iteratingNode = topMostSwitchNode;
        SwitchFoldable lowestSwitchNode = topMostSwitchNode;

        // If this stays true, we will need to spawn an uniform distribution.
        boolean uninitializedProfiles = true;

        // Go down the if cascade, collecting necessary data
        while (iteratingNode != null) {
            lowestSwitchNode = iteratingNode;
            if (!(iteratingNode.updateSwitchData(keyData, successors, cumulative, unreachable))) {
                return false;
            }
            if (!iteratingNode.isNonInitializedProfile()) {
                uninitializedProfiles = false;
            }
            iteratingNode = iteratingNode.getChildSwitchNode(switchValue);
        }

        if (keyData.size() < 4 || lowestSwitchNode == topMostSwitchNode) {
            // Abort if it's not worth the hassle
            return false;
        }

        // At that point, we will commit the optimization.
        Graph graph = asNode().graph();

        // Sort the keys
        sort(keyData);

        // Spawn the required data structures
        int newKeyCount = keyData.size();
        int[] keys = new int[newKeyCount];
        double[] keyProbabilities = new double[newKeyCount + 1];
        int[] keySuccessors = new int[newKeyCount + 1];
        double uniform = 1 / (double) (newKeyCount + 1);
        for (int i = 0; i < newKeyCount; i++) {
            SwitchFoldable.KeyData data = keyData.get(i);
            keys[i] = data.key;
            keyProbabilities[i] = uninitializedProfiles ? uniform : data.keyProbability;
            keySuccessors[i] = data.keySuccessor;
        }

        // Add default
        keyProbabilities[newKeyCount] = uninitializedProfiles ? uniform : cumulative[0];
        keySuccessors[newKeyCount] = successors.size();
        lowestSwitchNode.addDefault(successors);

        // Spin an adapter if the value is narrower than an int
        ValueNode adapter = null;
        if (((IntegerStamp) switchStamp).getBits() < 32) {
            adapter = new SignExtendNode(switchValue, 32);
            graph.addOrUnique(adapter);
        } else {
            adapter = switchValue;
        }

        // Spawn the switch node
        IntegerSwitchNode toInsert = new IntegerSwitchNode(adapter, successors.size(), keys, keyProbabilities, keySuccessors);
        graph.add(toInsert);

        // Detach the cascade from the graph
        lowestSwitchNode.cutOffLowestCascadeNode();
        iteratingNode = lowestSwitchNode;
        while (iteratingNode != null) {
            if (iteratingNode != lowestSwitchNode) {
                iteratingNode.cutOffCascadeNode();
            }
            iteratingNode = iteratingNode.getParentSwitchNode(switchValue);
        }

        // Place the new Switch node
        topMostSwitchNode.asNode().replaceAtPredecessor(toInsert);
        topMostSwitchNode.asNode().replaceAtUsages(toInsert);

        // Remove the cascade and unreachable code
        GraphUtil.killCFG((FixedNode) topMostSwitchNode);
        for (AbstractBeginNode duplicate : unreachable) {
            GraphUtil.killCFG(duplicate);
        }

        // Attach the branches to the switch.
        int pos = 0;
        for (AbstractBeginNode begin : successors) {
            if (!begin.isAlive()) {
                graph.add(begin.next());
                graph.add(begin);
                begin.setNext(begin.next());
            }
            toInsert.setBlockSuccessor(pos++, begin);
        }

        tool.addToWorkList(toInsert);

        return true;
    }
}
