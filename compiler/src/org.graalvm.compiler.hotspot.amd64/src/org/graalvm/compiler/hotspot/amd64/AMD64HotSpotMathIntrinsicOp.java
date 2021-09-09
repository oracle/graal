/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.amd64;

import static jdk.vm.ci.code.ValueUtil.asRegister;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.Value;

/**
 * This provides the default implementation expected by some HotSpot based lowerings of Math
 * intrinsics. Depending on the release different patterns might be used.
 */
public final class AMD64HotSpotMathIntrinsicOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64HotSpotMathIntrinsicOp> TYPE = LIRInstructionClass.create(AMD64HotSpotMathIntrinsicOp.class);

    @Opcode private final UnaryMathIntrinsicNode.UnaryOperation opcode;
    @Def protected Value result;
    @Use protected Value input;
    @Temp({OperandFlag.STACK, OperandFlag.ILLEGAL}) protected Value temp;

    public AMD64HotSpotMathIntrinsicOp(UnaryMathIntrinsicNode.UnaryOperation opcode, Value result, Value input, Value temp) {
        super(TYPE);
        this.opcode = opcode;
        this.result = result;
        this.input = input;
        this.temp = temp;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        AMD64Address stackSlot = (AMD64Address) crb.asAddress(temp);
        switch (opcode) {
            case LOG:
                masm.flog(asRegister(result, AMD64Kind.DOUBLE), asRegister(input, AMD64Kind.DOUBLE), false, stackSlot);
                break;
            case LOG10:
                masm.flog(asRegister(result, AMD64Kind.DOUBLE), asRegister(input, AMD64Kind.DOUBLE), true, stackSlot);
                break;
            case SIN:
                masm.fsin(asRegister(result, AMD64Kind.DOUBLE), asRegister(input, AMD64Kind.DOUBLE), stackSlot);
                break;
            case COS:
                masm.fcos(asRegister(result, AMD64Kind.DOUBLE), asRegister(input, AMD64Kind.DOUBLE), stackSlot);
                break;
            case TAN:
                masm.ftan(asRegister(result, AMD64Kind.DOUBLE), asRegister(input, AMD64Kind.DOUBLE), stackSlot);
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }
}
