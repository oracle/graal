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

package com.oracle.graal.compiler.sparc;

import com.oracle.graal.graph.*;
import com.oracle.graal.lir.sparc.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.memory.address.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.jvmci.meta.*;

/**
 * Represents an address of the form [base + index].
 */
@NodeInfo
public class SPARCIndexedAddressNode extends AddressNode implements LIRLowerable {

    public static final NodeClass<SPARCIndexedAddressNode> TYPE = NodeClass.create(SPARCIndexedAddressNode.class);

    @Input private ValueNode base;
    @Input private ValueNode index;

    public SPARCIndexedAddressNode(ValueNode base, ValueNode index) {
        super(TYPE);
        this.base = base;
        this.index = index;
    }

    public void generate(NodeLIRBuilderTool gen) {
        SPARCLIRGenerator tool = (SPARCLIRGenerator) gen.getLIRGeneratorTool();

        AllocatableValue baseValue = tool.asAllocatable(gen.operand(base));
        AllocatableValue indexValue = tool.asAllocatable(gen.operand(index));

        LIRKind kind = tool.getLIRKind(stamp());
        gen.setResult(this, new SPARCIndexedAddressValue(kind, baseValue, indexValue));
    }

    public ValueNode getBase() {
        return base;
    }

    public void setBase(ValueNode base) {
        updateUsages(this.base, base);
        this.base = base;
    }

    public ValueNode getIndex() {
        return index;
    }

    public void setIndex(ValueNode index) {
        updateUsages(this.index, index);
        this.index = index;
    }
}
