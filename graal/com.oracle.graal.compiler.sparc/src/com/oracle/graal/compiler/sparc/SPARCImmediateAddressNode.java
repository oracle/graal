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

import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.sparc.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.memory.address.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.jvmci.meta.*;

/**
 * Represents an address of the form [base + simm13].
 */
@NodeInfo
public class SPARCImmediateAddressNode extends AddressNode implements LIRLowerable {

    public static final NodeClass<SPARCImmediateAddressNode> TYPE = NodeClass.create(SPARCImmediateAddressNode.class);

    @Input private ValueNode base;
    private int displacement;

    public SPARCImmediateAddressNode(ValueNode base, int displacement) {
        super(TYPE);
        assert SPARCAssembler.isSimm13(displacement);
        this.base = base;
        this.displacement = displacement;
    }

    public void generate(NodeLIRBuilderTool gen) {
        SPARCLIRGenerator tool = (SPARCLIRGenerator) gen.getLIRGeneratorTool();

        AllocatableValue baseValue = tool.asAllocatable(gen.operand(base));

        LIRKind kind = tool.getLIRKind(stamp());
        gen.setResult(this, new SPARCImmediateAddressValue(kind, baseValue, displacement));
    }

    public ValueNode getBase() {
        return base;
    }

    public void setBase(ValueNode base) {
        updateUsages(this.base, base);
        this.base = base;
    }

    public int getDisplacement() {
        return displacement;
    }

    public void setDisplacement(int displacement) {
        this.displacement = displacement;
    }
}
