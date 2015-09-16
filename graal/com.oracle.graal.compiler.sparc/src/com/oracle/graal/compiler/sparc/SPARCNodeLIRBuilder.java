/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.jvmci.code.CallingConvention;
import jdk.internal.jvmci.common.JVMCIError;
import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.JavaType;
import jdk.internal.jvmci.meta.LIRKind;
import jdk.internal.jvmci.meta.Value;

import com.oracle.graal.compiler.gen.NodeLIRBuilder;
import com.oracle.graal.compiler.match.ComplexMatchResult;
import com.oracle.graal.compiler.match.MatchRule;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.lir.sparc.SPARCBreakpointOp;
import com.oracle.graal.lir.sparc.SPARCJumpOp;
import com.oracle.graal.nodes.BreakpointNode;
import com.oracle.graal.nodes.DeoptimizingNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.SignExtendNode;
import com.oracle.graal.nodes.calc.ZeroExtendNode;
import com.oracle.graal.nodes.memory.Access;

/**
 * This class implements the SPARC specific portion of the LIR generator.
 */
public abstract class SPARCNodeLIRBuilder extends NodeLIRBuilder {

    public SPARCNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        super(graph, lirGen);
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
        append(new SPARCBreakpointOp(parameters));
    }

    @Override
    protected JumpOp newJumpOp(LabelRef ref) {
        return new SPARCJumpOp(ref);
    }

    protected LIRFrameState getState(Access access) {
        if (access instanceof DeoptimizingNode) {
            return state((DeoptimizingNode) access);
        }
        return null;
    }

    private ComplexMatchResult emitSignExtendMemory(Access access, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        JavaKind toKind = null;
        JavaKind fromKind = null;
        if (fromBits == toBits) {
            return null;
        } else if (toBits > 32) {
            toKind = JavaKind.Long;
        } else if (toBits > 16) {
            toKind = JavaKind.Int;
        } else {
            toKind = JavaKind.Short;
        }
        switch (fromBits) {
            case 8:
                fromKind = JavaKind.Byte;
                break;
            case 16:
                fromKind = JavaKind.Short;
                break;
            case 32:
                fromKind = JavaKind.Int;
                break;
            default:
                throw JVMCIError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
        }

        JavaKind localFromKind = fromKind;
        JavaKind localToKind = toKind;
        return builder -> {
            Value v = getLIRGeneratorTool().emitSignExtendLoad(LIRKind.value(localFromKind), operand(access.getAddress()), getState(access));
            return getLIRGeneratorTool().emitReinterpret(LIRKind.value(localToKind), v);
        };
    }

    private ComplexMatchResult emitZeroExtendMemory(Access access, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        JavaKind toKind = null;
        JavaKind fromKind = null;
        if (fromBits == toBits) {
            return null;
        } else if (toBits > 32) {
            toKind = JavaKind.Long;
        } else if (toBits > 16) {
            toKind = JavaKind.Int;
        } else {
            toKind = JavaKind.Short;
        }
        switch (fromBits) {
            case 8:
                fromKind = JavaKind.Byte;
                break;
            case 16:
                fromKind = JavaKind.Short;
                break;
            case 32:
                fromKind = JavaKind.Int;
                break;
            default:
                throw JVMCIError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
        }

        JavaKind localFromKind = fromKind;
        JavaKind localToKind = toKind;
        return builder -> {
            // Loads are always zero extending load
            Value v = getLIRGeneratorTool().emitLoad(LIRKind.value(localFromKind), operand(access.getAddress()), getState(access));
            return getLIRGeneratorTool().emitReinterpret(LIRKind.value(localToKind), v);
        };
    }

    @MatchRule("(SignExtend Read=access)")
    @MatchRule("(SignExtend FloatingRead=access)")
    public ComplexMatchResult signExtend(SignExtendNode root, Access access) {
        return emitSignExtendMemory(access, root.getInputBits(), root.getResultBits());
    }

    @MatchRule("(ZeroExtend Read=access)")
    @MatchRule("(ZeroExtend FloatingRead=access)")
    public ComplexMatchResult zeroExtend(ZeroExtendNode root, Access access) {
        return emitZeroExtendMemory(access, root.getInputBits(), root.getResultBits());
    }

    @Override
    public SPARCLIRGenerator getLIRGeneratorTool() {
        return (SPARCLIRGenerator) super.getLIRGeneratorTool();
    }

    @Override
    protected void emitPrologue(StructuredGraph graph) {
        getLIRGeneratorTool().emitLoadConstantTableBase();
        super.emitPrologue(graph);
    }
}
