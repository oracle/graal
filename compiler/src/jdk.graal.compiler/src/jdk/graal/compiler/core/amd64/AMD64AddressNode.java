/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.core.amd64;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Represents an address of the form [base + index*scale + displacement]. Both base and index are
 * optional.
 */
@NodeInfo
public class AMD64AddressNode extends AddressNode implements Simplifiable, LIRLowerable {

    public static final NodeClass<AMD64AddressNode> TYPE = NodeClass.create(AMD64AddressNode.class);

    @OptionalInput private ValueNode base;

    @OptionalInput private ValueNode index;
    private Stride stride;

    private int displacement;

    public AMD64AddressNode(ValueNode base) {
        this(base, null);
    }

    public AMD64AddressNode(ValueNode base, ValueNode index) {
        super(TYPE);
        this.base = base;
        this.index = index;
        this.stride = Stride.S1;
    }

    public void canonicalizeIndex(SimplifierTool tool) {
        if (index instanceof AddNode && ((IntegerStamp) index.stamp(NodeView.DEFAULT)).getBits() == 64) {
            AddNode add = (AddNode) index;
            ValueNode valX = add.getX();
            if (valX instanceof PhiNode) {
                PhiNode phi = (PhiNode) valX;
                if (phi.merge() instanceof LoopBeginNode) {
                    LoopBeginNode loopNode = (LoopBeginNode) phi.merge();
                    if (!loopNode.isSimpleLoop()) {
                        ValueNode valY = add.getY();
                        if (valY instanceof ConstantNode) {
                            int addBy = valY.asJavaConstant().asInt();
                            displacement = displacement + stride.value * addBy;
                            replaceFirstInput(index, phi);
                            tool.addToWorkList(index);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();

        AllocatableValue baseValue = base == null ? Value.ILLEGAL : tool.asAllocatable(gen.operand(base));
        AllocatableValue indexValue = index == null ? Value.ILLEGAL : tool.asAllocatable(gen.operand(index));

        AllocatableValue baseReference = LIRKind.derivedBaseFromValue(baseValue);
        AllocatableValue indexReference;
        if (index == null) {
            indexReference = null;
        } else if (stride.equals(Stride.S1)) {
            indexReference = LIRKind.derivedBaseFromValue(indexValue);
        } else {
            if (LIRKind.isValue(indexValue)) {
                indexReference = null;
            } else {
                indexReference = Value.ILLEGAL;
            }
        }

        LIRKind kind = LIRKind.combineDerived(tool.getLIRKind(stamp(NodeView.DEFAULT)), baseReference, indexReference);
        gen.setResult(this, new AMD64AddressValue(kind, baseValue, indexValue, stride, displacement));
    }

    @Override
    public ValueNode getBase() {
        return base;
    }

    public void setBase(ValueNode base) {
        // allow modification before inserting into the graph
        if (isAlive()) {
            updateUsages(this.base, base);
        }
        this.base = base;
    }

    @Override
    public ValueNode getIndex() {
        return index;
    }

    public void setIndex(ValueNode index) {
        // allow modification before inserting into the graph
        if (isAlive()) {
            updateUsages(this.index, index);
        }
        this.index = index;
    }

    public Stride getScale() {
        return stride;
    }

    public void setScale(Stride stride) {
        this.stride = stride;
    }

    public int getDisplacement() {
        return displacement;
    }

    public void setDisplacement(int displacement) {
        this.displacement = displacement;
    }

    @Override
    public long getMaxConstantDisplacement() {
        return displacement;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        canonicalizeIndex(tool);
    }
}
