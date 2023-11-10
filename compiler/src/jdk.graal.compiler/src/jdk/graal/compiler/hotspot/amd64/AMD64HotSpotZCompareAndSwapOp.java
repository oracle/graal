/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.amd64;

import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

@Opcode("CAS_Z")
public final class AMD64HotSpotZCompareAndSwapOp extends AMD64HotSpotZBarrieredOp {
    public static final LIRInstructionClass<AMD64HotSpotZCompareAndSwapOp> TYPE = LIRInstructionClass.create(AMD64HotSpotZCompareAndSwapOp.class);

    private final AMD64Kind accessKind;

    @Use protected AllocatableValue cmpValue;
    @Alive protected AllocatableValue newValue;
    @Temp protected AllocatableValue temp;

    public AMD64HotSpotZCompareAndSwapOp(AMD64Kind accessKind, AllocatableValue result, AMD64AddressValue address, AllocatableValue cmpValue, AllocatableValue newValue,
                    AllocatableValue temp, GraalHotSpotVMConfig config, ForeignCallLinkage callTarget) {
        super(TYPE, result, address, config, callTarget);
        this.accessKind = accessKind;
        this.cmpValue = cmpValue;
        this.newValue = newValue;
        this.temp = temp;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        GraalError.guarantee(accessKind == AMD64Kind.QWORD, "ZGC only supports uncomppressed oops");
        assert asRegister(cmpValue).equals(AMD64.rax) : cmpValue;
        assert asRegister(result).equals(AMD64.rax) : result;
        assert LIRValueUtil.differentRegisters(cmpValue, newValue, temp, loadAddress);

        Label success = new Label();
        Label barrierOk = new Label();
        Register newReg = asRegister(newValue);
        // Make a copy of the value used in the compare
        masm.movq(asRegister(temp), asRegister(cmpValue));
        if (crb.target.isMP) {
            masm.lock();
        }
        masm.cmpxchgq(newReg, loadAddress.toAddress());
        // if the cmpxchgq succeeds then we are done
        masm.jccb(AMD64Assembler.ConditionFlag.Zero, success);
        /*
         * The cmpxchg could have failed because the memory location needs a read barrier. On
         * failure rax contains the value from the memory location so perform a read barrier on that
         * value. If it's a valid oop then we are done but must recompute the condition codes.
         * Otherwise we must retry the cmpxchgq.
         */
        emitBarrier(crb, masm, AMD64.rax, barrierOk);
        masm.movq(asRegister(cmpValue), asRegister(temp));
        if (crb.target.isMP) {
            masm.lock();
        }
        masm.cmpxchgq(newReg, loadAddress.toAddress());
        masm.bind(barrierOk);
        masm.cmpq(asRegister(temp), asRegister(result));
        masm.bind(success);
    }
}
