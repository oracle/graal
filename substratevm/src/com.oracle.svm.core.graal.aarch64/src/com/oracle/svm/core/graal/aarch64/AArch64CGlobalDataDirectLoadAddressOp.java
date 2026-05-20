/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.aarch64;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.graal.code.CGlobalDataDirectReference;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;

import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

@Platforms(Platform.HOSTED_ONLY.class)
public sealed class AArch64CGlobalDataDirectLoadAddressOp extends AArch64LIRInstruction permits AArch64LoadLayeredMethodOffsetConstantOp {
    public static final LIRInstructionClass<AArch64CGlobalDataDirectLoadAddressOp> TYPE = LIRInstructionClass.create(AArch64CGlobalDataDirectLoadAddressOp.class);

    @Def(REG) protected AllocatableValue result;

    private final CGlobalDataInfo dataInfo;
    private final int addend;

    AArch64CGlobalDataDirectLoadAddressOp(CGlobalDataInfo dataInfo, AllocatableValue result) {
        this(dataInfo, result, 0);
    }

    AArch64CGlobalDataDirectLoadAddressOp(CGlobalDataInfo dataInfo, AllocatableValue result, int addend) {
        this(TYPE, dataInfo, result, addend);
    }

    protected AArch64CGlobalDataDirectLoadAddressOp(LIRInstructionClass<? extends AArch64CGlobalDataDirectLoadAddressOp> type, CGlobalDataInfo dataInfo, AllocatableValue result, int addend) {
        super(type);
        this.dataInfo = dataInfo;
        this.result = result;
        this.addend = addend;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        int addressBitSize = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
        assert addressBitSize == 64;

        crb.compilationResult.recordDataPatch(masm.position(), new CGlobalDataDirectReference(dataInfo));
        Register resultRegister = asRegister(result);
        if (dataInfo.isSymbolReference()) {
            // Pure symbol reference: the data contains the symbol's address, load it
            masm.adrpLdr(addressBitSize, resultRegister, resultRegister);
        } else {
            // Data: load its address
            masm.adrpAdd(resultRegister);
        }
        if (addend != 0) {
            masm.add(64, resultRegister, resultRegister, addend);
        }
    }
}
