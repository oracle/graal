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

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.aarch64.AArch64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.graal.code.CGlobalDataReference;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

public final class AArch64CGlobalDataLoadAddressOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64CGlobalDataLoadAddressOp> TYPE = LIRInstructionClass.create(AArch64CGlobalDataLoadAddressOp.class);

    @Def(REG) private AllocatableValue result;

    private final CGlobalDataInfo dataInfo;

    AArch64CGlobalDataLoadAddressOp(CGlobalDataInfo dataInfo, AllocatableValue result) {
        super(TYPE);
        this.dataInfo = dataInfo;
        this.result = result;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        int bits = result.getPlatformKind().getSizeInBytes() * 8;
        if (SubstrateUtil.HOSTED) {
            // AOT compilation: record patch that is fixed up later
            int before = masm.position();
            Register resultRegister = asRegister(result);
            if (dataInfo.isSymbolReference()) {
                // Pure symbol reference: the data contains the symbol's address, load it
                AArch64Address address = AArch64Address.createScaledImmediateAddress(resultRegister, 0x0);
                masm.adrpLdr(64, resultRegister, address);
            } else {
                // Data: load its address
                AArch64Address address = masm.getPlaceholder(before);
                masm.loadAddress(resultRegister, address, 1);
            }
            crb.compilationResult.recordDataPatch(before, new CGlobalDataReference(dataInfo));
        } else {
            // Runtime compilation: compute the actual address
            Pointer globalsBase = CGlobalDataInfo.CGLOBALDATA_RUNTIME_BASE_ADDRESS.get();
            Pointer address = globalsBase.add(dataInfo.getOffset());
            masm.mov(asRegister(result), address.rawValue());
            if (dataInfo.isSymbolReference()) { // load data, which contains symbol's address
                masm.ldr(bits, asRegister(result), AArch64Address.createBaseRegisterOnlyAddress(asRegister(result)));
            }
        }
    }
}
