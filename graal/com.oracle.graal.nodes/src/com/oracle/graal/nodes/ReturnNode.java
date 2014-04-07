/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

public final class ReturnNode extends ControlSinkNode implements LIRLowerable, IterableNodeType {

    @Input private ValueNode result;
    @Input(InputType.Association) private MemoryMapNode memoryMap;

    public ValueNode result() {
        return result;
    }

    /**
     * Constructs a new Return instruction.
     *
     * @param result the instruction producing the result for this return; {@code null} if this is a
     *            void return
     */
    public ReturnNode(ValueNode result) {
        this(result, null);
    }

    public ReturnNode(ValueNode result, MemoryMapNode memoryMap) {
        super(StampFactory.forVoid());
        this.result = result;
        this.memoryMap = memoryMap;
    }

    @Override
    public boolean verify() {
        return super.verify();
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        if (this.result() == null) {
            gen.getLIRGeneratorTool().emitReturn(null);
        } else {
            gen.getLIRGeneratorTool().emitReturn(gen.operand(this.result()));
        }
    }

    public void setMemoryMap(MemoryMapNode memoryMap) {
        updateUsages(this.memoryMap, memoryMap);
        this.memoryMap = memoryMap;
    }

    public MemoryMapNode getMemoryMap() {
        return memoryMap;
    }
}
