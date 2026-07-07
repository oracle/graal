/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.consumer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.vector.nodes.AbstractVectorNode;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.op.ConcatVectorNode;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * Utilities for splitting up vector operations that have {@link ConcatVectorNode} inputs.
 */
public class SplitConcatUtil {

    public static List<ValueNode> getVectorInputsForNextOperation(List<ValueNode> vectorInputs, ArrayList<VectorEntry> concatInputs) {
        int concatIndex = 0;
        List<ValueNode> result = new ArrayList<>(vectorInputs.size());
        for (ValueNode input : vectorInputs) {
            if (input instanceof ConcatVectorNode) {
                VectorEntry vectorEntry = concatInputs.get(concatIndex++);
                result.add(vectorEntry.vector.asNode());
            } else {
                result.add(input);
            }
        }
        assert concatIndex == concatInputs.size() : concatIndex + " " + concatInputs.size();
        return result;
    }

    public static void shiftInputs(StructuredGraph graph, ConstantReflectionProvider constantReflection, List<ValueNode> currentVectorInputs,
                    ArrayList<VectorEntry> concatInputs, ValueNode length) {
        // we shift the vector inputs that were used in the last operation by the number of elements
        // that were processed in the last operation
        int concatIndex = 0;
        for (int i = 0; i < currentVectorInputs.size(); i++) {
            ValueNode input = currentVectorInputs.get(i);
            if (input instanceof ConcatVectorNode) {
                VectorEntry vectorEntry = concatInputs.get(concatIndex++);
                vectorEntry.vector = AbstractVectorNode.shift(vectorEntry.vector, length, null, constantReflection);
                vectorEntry.length = BinaryArithmeticNode.sub(graph, vectorEntry.length, length, NodeView.DEFAULT);
            } else {
                currentVectorInputs.set(i, AbstractVectorNode.shift((VectorNode) input, length, null, constantReflection).asNode());
            }
        }
        assert concatIndex == concatInputs.size() : concatIndex + " " + concatInputs;
    }

    public static ValueNode minLength(StructuredGraph graph, ConstantReflectionProvider constantReflection, ArrayList<VectorEntry> vectorEntries, ValueNode remainingLength) {
        // only compare length values that are actually different from each other
        EconomicSet<ValueNode> lengthNodes = EconomicSet.create(Equivalence.IDENTITY);
        lengthNodes.add(remainingLength);
        for (VectorEntry entry : vectorEntries) {
            lengthNodes.add(entry.length);
        }

        // we can't assume that the length values are sorted in any way, so we create a hierarchy of
        // ConditionalNodes to determine the minimum value.
        Iterator<ValueNode> iter = lengthNodes.iterator();
        ValueNode prevLength = iter.next();
        while (iter.hasNext()) {
            ValueNode currentLength = iter.next();
            LogicNode lessThan = CompareNode.createCompareNode(graph, CanonicalCondition.BT, currentLength, prevLength, constantReflection, NodeView.DEFAULT);
            prevLength = graph.addOrUnique(ConditionalNode.create(lessThan, currentLength, prevLength, NodeView.DEFAULT));
        }
        return prevLength;
    }

    public static class CombinationTable {
        protected final ArrayList<ArrayList<VectorEntry>> rows = new ArrayList<>();

        public void add(ConcatVectorNode concatNode) {
            if (rows.isEmpty()) {
                rows.add(new ArrayList<>());
                rows.add(new ArrayList<>());
            } else {
                duplicateRows();
            }

            addColumn(concatNode);
        }

        private void duplicateRows() {
            int oldSize = rows.size();
            for (int i = 0; i < oldSize; i++) {
                ArrayList<VectorEntry> row = new ArrayList<>(rows.get(i));
                rows.add(row);
            }
        }

        private void addColumn(ConcatVectorNode concatNode) {
            VectorEntry x = new VectorEntry(concatNode.x(), concatNode.getXLength());
            for (int i = 0; i < rows.size() / 2; i++) {
                ArrayList<VectorEntry> row = rows.get(i);
                row.add(x);
            }

            VectorEntry y = new VectorEntry(concatNode.y(), concatNode.getYLength());
            for (int i = rows.size() / 2; i < rows.size(); i++) {
                ArrayList<VectorEntry> row = rows.get(i);
                row.add(y);
            }
        }
    }

    public static class VectorEntry {
        VectorNode vector;
        ValueNode length;

        VectorEntry(VectorNode vector, ValueNode length) {
            this.vector = vector;
            this.length = length;
        }
    }
}
