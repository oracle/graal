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
package jdk.graal.compiler.vector.nodes.op;

import java.util.List;

import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.type.VectorStamp;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;

@NodeInfo
public final class VectorPhi extends ValuePhiNode implements VectorTransformation {
    public static final NodeClass<VectorPhi> TYPE = NodeClass.create(VectorPhi.class);

    public VectorPhi(VectorStamp stamp, AbstractMergeNode merge) {
        super(TYPE, stamp, merge);
    }

    public VectorPhi(VectorStamp stamp, AbstractMergeNode merge, ValueNode... values) {
        super(TYPE, stamp, merge, values);
    }

    @Override
    public List<ValueNode> getVectorInputs() {
        return values();
    }

    @Override
    public VectorStamp getVectorStamp() {
        return (VectorStamp) stamp(NodeView.DEFAULT);
    }

    @Override
    public VectorNode simplify(VectorSimplifier simplifier) {
        for (int i = 0; i < valueCount(); i++) {
            VectorNode input = (VectorNode) valueAt(i);
            setValueAt(i, simplifier.simplify(input).asNode());
        }
        return this;
    }

    @Override
    public VectorTransformation createCopy(FixedNode insertBefore, ValueNode... inputs) {
        VectorPhi copy = graph().addWithoutUnique(new VectorPhi(getVectorStamp(), merge()));
        for (ValueNode input : inputs) {
            addInput(input);
        }
        return copy;
    }

    @Override
    public PhiNode duplicateOn(AbstractMergeNode newMerge) {
        return graph().addWithoutUnique(new VectorPhi(getVectorStamp(), newMerge));
    }

    @Override
    public VectorPhi duplicateWithValues(AbstractMergeNode newMerge, ValueNode... newValues) {
        return new VectorPhi(getVectorStamp(), newMerge, newValues);
    }
}
