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
package com.oracle.graal.lir.sparc;

import static com.oracle.graal.asm.sparc.SPARCAssembler.OP3;
import static com.oracle.graal.asm.sparc.SPARCAssembler.isSimm13;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.CONST;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static com.oracle.graal.lir.LIRValueUtil.asJavaConstant;
import static com.oracle.graal.lir.LIRValueUtil.isJavaConstant;
import static jdk.internal.jvmci.code.ValueUtil.asRegister;
import static jdk.internal.jvmci.code.ValueUtil.isRegister;
import static jdk.internal.jvmci.common.JVMCIError.shouldNotReachHere;
import static jdk.internal.jvmci.sparc.SPARC.g0;
import jdk.internal.jvmci.meta.JavaConstant;
import jdk.internal.jvmci.meta.Value;

import com.oracle.graal.asm.sparc.SPARCAssembler.Op3s;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.asm.CompilationResultBuilder;

public class SPARCOP3Op extends SPARCLIRInstruction {
    public static final LIRInstructionClass<SPARCOP3Op> TYPE = LIRInstructionClass.create(SPARCOP3Op.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(1);

    @Opcode private final Op3s op3;
    @Use({REG}) protected Value a;
    @Use({REG, CONST}) protected Value b;
    @Use({REG}) protected Value result;

    public SPARCOP3Op(Op3s op3, Value a, Value b) {
        this(op3, a, b, g0.asValue());
    }

    public SPARCOP3Op(Op3s op3, Value a, Value b, Value result) {
        super(TYPE, SIZE);
        this.op3 = op3;
        this.a = a;
        this.b = b;
        this.result = result;
    }

    @Override
    protected void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        emitOp3(masm, op3, a, b, result);
    }

    public static void emitOp3(SPARCMacroAssembler masm, Op3s op3, Value a, Value b) {
        emitOp3(masm, op3, a, b, g0.asValue());
    }

    public static void emitOp3(SPARCMacroAssembler masm, Op3s op3, Value a, Value b, Value result) {
        assert isRegister(a);
        if (isJavaConstant(b)) {
            JavaConstant constant = asJavaConstant(b);
            long simm13;
            if (constant.isNull()) {
                simm13 = 0;
            } else {
                simm13 = constant.asLong(); // Cast is safe, as isSimm13 assertion is done
            }
            assert isSimm13(constant);
            OP3.emit(masm, op3, asRegister(a), (int) simm13, asRegister(result));
        } else if (isRegister(b)) {
            OP3.emit(masm, op3, asRegister(a), asRegister(b), asRegister(result));
        } else {
            throw shouldNotReachHere(String.format("Got values a: %s b: %s", a, b));
        }
    }
}
