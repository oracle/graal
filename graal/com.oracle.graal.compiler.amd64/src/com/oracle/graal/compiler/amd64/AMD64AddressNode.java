/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.graal.compiler.amd64;

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.asm.amd64.AMD64Address.Scale;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.memory.address.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Represents an address of the form [base + index*scale + displacement]. Both base and index are
 * optional.
 */
@NodeInfo
public class AMD64AddressNode extends AddressNode implements LIRLowerable {

    public static final NodeClass<AMD64AddressNode> TYPE = NodeClass.create(AMD64AddressNode.class);

    @OptionalInput private ValueNode base;

    @OptionalInput private ValueNode index;
    private Scale scale;

    private int displacement;

    public AMD64AddressNode(ValueNode base) {
        this(base, null);
    }

    public AMD64AddressNode(ValueNode base, ValueNode index) {
        super(TYPE);
        this.base = base;
        this.index = index;
        this.scale = Scale.Times1;
    }

    public void generate(NodeLIRBuilderTool gen) {
        AMD64LIRGenerator tool = (AMD64LIRGenerator) gen.getLIRGeneratorTool();

        AllocatableValue baseValue = base == null ? Value.ILLEGAL : tool.asAllocatable(gen.operand(base));
        AllocatableValue indexValue = index == null ? Value.ILLEGAL : tool.asAllocatable(gen.operand(index));

        LIRKind kind = tool.getLIRKind(stamp());
        gen.setResult(this, new AMD64AddressValue(kind, baseValue, indexValue, scale, displacement));
    }

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

    public Scale getScale() {
        return scale;
    }

    public void setScale(Scale scale) {
        this.scale = scale;
    }

    public int getDisplacement() {
        return displacement;
    }

    public void setDisplacement(int displacement) {
        this.displacement = displacement;
    }
}
