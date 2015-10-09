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

import static jdk.vm.ci.sparc.SPARCKind.BYTE;
import static jdk.vm.ci.sparc.SPARCKind.DWORD;
import static jdk.vm.ci.sparc.SPARCKind.HWORD;
import static jdk.vm.ci.sparc.SPARCKind.WORD;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.sparc.SPARCKind;

import com.oracle.graal.compiler.gen.NodeMatchRules;
import com.oracle.graal.compiler.match.ComplexMatchResult;
import com.oracle.graal.compiler.match.MatchRule;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodes.DeoptimizingNode;
import com.oracle.graal.nodes.calc.SignExtendNode;
import com.oracle.graal.nodes.calc.ZeroExtendNode;
import com.oracle.graal.nodes.memory.Access;

/**
 * This class implements the SPARC specific portion of the LIR generator.
 */
public class SPARCNodeMatchRules extends NodeMatchRules {

    public SPARCNodeMatchRules(LIRGeneratorTool gen) {
        super(gen);
    }

    protected LIRFrameState getState(Access access) {
        if (access instanceof DeoptimizingNode) {
            return state((DeoptimizingNode) access);
        }
        return null;
    }

    private ComplexMatchResult emitSignExtendMemory(Access access, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        SPARCKind toKind = null;
        SPARCKind fromKind = null;
        if (fromBits == toBits) {
            return null;
        } else if (toBits > WORD.getSizeInBits()) {
            toKind = DWORD;
        } else if (toBits > HWORD.getSizeInBits()) {
            toKind = WORD;
        } else if (toBits > BYTE.getSizeInBits()) {
            toKind = HWORD;
        }
        switch (fromBits) {
            case 8:
                fromKind = BYTE;
                break;
            case 16:
                fromKind = HWORD;
                break;
            case 32:
                fromKind = WORD;
                break;
            default:
                throw JVMCIError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
        }
        SPARCKind localFromKind = fromKind;
        SPARCKind localToKind = toKind;
        return builder -> {
            Value v = getLIRGeneratorTool().emitSignExtendLoad(LIRKind.value(localFromKind), operand(access.getAddress()), getState(access));
            return getArithmeticLIRGenerator().emitReinterpret(LIRKind.value(localToKind), v);
        };
    }

    private ComplexMatchResult emitZeroExtendMemory(Access access, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        SPARCKind toKind = null;
        SPARCKind fromKind = null;
        if (fromBits == toBits) {
            return null;
        } else if (toBits > WORD.getSizeInBits()) {
            toKind = DWORD;
        } else if (toBits > HWORD.getSizeInBits()) {
            toKind = WORD;
        } else if (toBits > BYTE.getSizeInBits()) {
            toKind = HWORD;
        }
        switch (fromBits) {
            case 8:
                fromKind = BYTE;
                break;
            case 16:
                fromKind = HWORD;
                break;
            case 32:
                fromKind = WORD;
                break;
            default:
                throw JVMCIError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
        }
        SPARCKind localFromKind = fromKind;
        SPARCKind localToKind = toKind;
        return builder -> {
            // Loads are always zero extending load
            Value v = getLIRGeneratorTool().emitLoad(LIRKind.value(localFromKind), operand(access.getAddress()), getState(access));
            return getArithmeticLIRGenerator().emitReinterpret(LIRKind.value(localToKind), v);
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

    protected SPARCArithmeticLIRGenerator getArithmeticLIRGenerator() {
        return (SPARCArithmeticLIRGenerator) getLIRGeneratorTool().getArithmetic();
    }
}
