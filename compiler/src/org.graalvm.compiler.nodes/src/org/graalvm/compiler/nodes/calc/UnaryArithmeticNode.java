/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.calc;

import static org.graalvm.compiler.nodes.calc.BinaryArithmeticNode.getArithmeticOpTable;

import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.UnaryOp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ArithmeticOperation;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;

@NodeInfo
public abstract class UnaryArithmeticNode<OP> extends UnaryNode implements ArithmeticOperation, ArithmeticLIRLowerable {

    @SuppressWarnings("rawtypes") public static final NodeClass<UnaryArithmeticNode> TYPE = NodeClass.create(UnaryArithmeticNode.class);

    protected UnaryArithmeticNode(NodeClass<? extends UnaryArithmeticNode<OP>> c, UnaryOp<OP> opForStampComputation, ValueNode value) {
        super(c, opForStampComputation.foldStamp(value.stamp(NodeView.DEFAULT)), value);
    }

    protected abstract UnaryOp<OP> getOp(ArithmeticOpTable table);

    protected final UnaryOp<OP> getOp(ValueNode forValue) {
        return getOp(getArithmeticOpTable(forValue));
    }

    @Override
    public final UnaryOp<OP> getArithmeticOp() {
        return getOp(getValue());
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        assert newStamp.isCompatible(getValue().stamp(NodeView.DEFAULT));
        return getOp(getValue()).foldStamp(newStamp);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode synonym = findSynonym(forValue, getOp(forValue));
        if (synonym != null) {
            return synonym;
        }
        return this;
    }

    protected static <OP> ValueNode findSynonym(ValueNode forValue, UnaryOp<OP> op) {
        if (forValue.isConstant()) {
            return ConstantNode.forPrimitive(op.foldStamp(forValue.stamp(NodeView.DEFAULT)), op.foldConstant(forValue.asConstant()));
        }
        return null;
    }
}
