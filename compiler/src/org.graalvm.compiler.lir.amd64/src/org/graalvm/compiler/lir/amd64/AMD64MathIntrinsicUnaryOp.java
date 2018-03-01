/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.amd64;

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.meta.Value;

public final class AMD64MathIntrinsicUnaryOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64MathIntrinsicUnaryOp> TYPE = LIRInstructionClass.create(AMD64MathIntrinsicUnaryOp.class);

    public enum UnaryIntrinsicOpcode {
        LOG,
        LOG10,
        SIN,
        COS,
        TAN,
        EXP
    }

    @Opcode private final UnaryIntrinsicOpcode opcode;
    @Def protected Value result;
    @Use protected Value input;
    @Temp({REG}) protected Value[] temps;

    public AMD64MathIntrinsicUnaryOp(LIRGeneratorTool tool, UnaryIntrinsicOpcode opcode, Value result, Value input, Value stackTemp) {
        super(TYPE);
        this.opcode = opcode;
        this.result = result;
        this.input = input;
        switch (opcode) {
            case LOG:
                temps = AMD64MathLog.temps.clone();
                break;
            case LOG10:
                temps = AMD64MathLog10.temps.clone();
                break;
            case SIN:
                temps = AMD64MathSin.temps.clone();
                break;
            case COS:
                temps = AMD64MathCos.temps.clone();
                break;
            case TAN:
                temps = AMD64MathTan.temps.clone();
                break;
            case EXP:
                temps = AMD64MathExp.temps.clone();
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    public AMD64MathIntrinsicUnaryOp(LIRGeneratorTool tool, UnaryIntrinsicOpcode opcode, Value result, Value input) {
        this(tool, opcode, result, input, Value.ILLEGAL);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        switch (opcode) {
            case LOG:
                new AMD64MathLog().generate(masm, crb, result);
                break;
            case LOG10:
                new AMD64MathLog10().generate(masm, crb, result);
                break;
            case SIN:
                new AMD64MathSin().generate(masm, crb, result);
                break;
            case COS:
                new AMD64MathCos().generate(masm, crb, result);
                break;
            case TAN:
                new AMD64MathTan().generate(masm, crb, result);
                break;
            case EXP:
                new AMD64MathExp().generate(masm, crb, result);
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

}
