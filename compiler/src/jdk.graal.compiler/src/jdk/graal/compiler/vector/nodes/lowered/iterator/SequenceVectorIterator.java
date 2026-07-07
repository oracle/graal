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
package jdk.graal.compiler.vector.nodes.lowered.iterator;

import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.lowered.UnitSequenceVector;
import jdk.graal.compiler.vector.nodes.op.MapVectorNode;
import jdk.graal.compiler.vector.nodes.op.VectorPhi;
import jdk.graal.compiler.vector.nodes.producer.SequenceVectorNode;

import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.vm.ci.meta.ConstantReflectionProvider;

public final class SequenceVectorIterator extends DefaultVectorIterator {

    private final VectorNode current;

    public static SequenceVectorIterator createInitialIterator(SequenceVectorNode seq) {
        VectorNode unitSequence = seq.graph().unique(new UnitSequenceVector(seq.getVectorStamp(), seq.direction()));
        VectorNode sequence = MapVectorNode.map(seq.graph(), graph -> {
            ValueNode mul = BinaryArithmeticNode.mul(graph, graph.getParameter(0), graph.getParameter(2), NodeView.DEFAULT);
            return BinaryArithmeticNode.add(graph, mul, graph.getParameter(1), NodeView.DEFAULT);
        }, unitSequence.asNode(), seq.getInitial(), seq.getStride());
        return new SequenceVectorIterator(sequence);
    }

    public static SequenceVectorIterator createPhiIterator(SequenceVectorNode seq, AbstractMergeNode merge) {
        VectorPhi phi = seq.graph().addWithoutUnique(new VectorPhi(seq.getVectorStamp(), merge));
        return new SequenceVectorIterator(phi);
    }

    private SequenceVectorIterator(VectorNode current) {
        this.current = current;
    }

    @Override
    public void addPhiInput(VectorIterator input) {
        PhiNode phi = (VectorPhi) current;
        SequenceVectorIterator seq = (SequenceVectorIterator) input;
        phi.addInput(seq.current.asNode());
    }

    @Override
    public VectorNode getVector(VectorNode vector, VectorIterationState state, FixedNode position, ConstantReflectionProvider constantReflection, int stepLength) {
        return current;
    }

    @Override
    public VectorIterator next(VectorNode vector, ValueNode stepLength) {
        SequenceVectorNode seq = (SequenceVectorNode) vector;
        StructuredGraph graph = seq.graph();

        ValueNode lengthNode = IntegerConvertNode.convert(stepLength, seq.getInitial().stamp(NodeView.DEFAULT), NodeView.DEFAULT);
        ValueNode stepNode = BinaryArithmeticNode.mul(graph, seq.getStride(), lengthNode, NodeView.DEFAULT);

        VectorNode next = MapVectorNode.map(graph, sub -> BinaryArithmeticNode.add(sub, sub.getParameter(0), sub.getParameter(1), NodeView.DEFAULT), current.asNode(), stepNode);
        return new SequenceVectorIterator(next);
    }
}
