/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.aarch64;

import com.oracle.graal.compiler.gen.NodeLIRBuilder;
import com.oracle.graal.lir.aarch64.AArch64BreakpointOp;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodes.BreakpointNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.Value;

/**
 * This class implements the SPARC specific portion of the LIR generator.
 */
public abstract class AArch64NodeLIRBuilder extends NodeLIRBuilder {

    public AArch64NodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen, AArch64NodeMatchRules nodeMatchRules) {
        super(graph, lirGen, nodeMatchRules);
    }

    @Override
    protected boolean peephole(ValueNode valueNode) {
        // No peephole optimizations for now
        return false;
    }

    @Override
    public void visitBreakpointNode(BreakpointNode node) {
        JavaType[] sig = new JavaType[node.arguments().size()];
        for (int i = 0; i < sig.length; i++) {
            sig[i] = node.arguments().get(i).stamp().javaType(gen.getMetaAccess());
        }

        Value[] parameters = visitInvokeArguments(gen.getResult().getFrameMapBuilder().getRegisterConfig().getCallingConvention(CallingConvention.Type.JavaCall, null, sig, gen.target(), false),
                        node.arguments());
        append(new AArch64BreakpointOp(parameters));
    }

    @Override
    public AArch64LIRGenerator getLIRGeneratorTool() {
        return (AArch64LIRGenerator) super.getLIRGeneratorTool();
    }

    @Override
    protected void emitPrologue(StructuredGraph graph) {
        // XXX Maybe we need something like this.
        // getLIRGeneratorTool().emitLoadConstantTableBase();
        super.emitPrologue(graph);
    }
}
