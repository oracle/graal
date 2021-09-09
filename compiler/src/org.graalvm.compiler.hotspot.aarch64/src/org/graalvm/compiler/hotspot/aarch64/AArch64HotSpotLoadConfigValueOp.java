/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Red Hat Inc. All rights reserved.
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
package org.graalvm.compiler.hotspot.aarch64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;

import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.HotSpotMarkId;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.aarch64.AArch64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

public final class AArch64HotSpotLoadConfigValueOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64HotSpotLoadConfigValueOp> TYPE = LIRInstructionClass.create(AArch64HotSpotLoadConfigValueOp.class);

    @Def({OperandFlag.REG}) protected AllocatableValue result;
    private final HotSpotMarkId markId;

    public AArch64HotSpotLoadConfigValueOp(HotSpotMarkId markId, AllocatableValue result) {
        super(TYPE);
        this.result = result;
        this.markId = markId;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        if (GeneratePIC.getValue(crb.getOptions())) {
            AArch64Kind kind = (AArch64Kind) result.getPlatformKind();
            Register reg = asRegister(result);
            masm.adrpAdd(reg);
            switch (kind) {
                case BYTE:
                    masm.ldrs(32, 8, reg, AArch64Address.createBaseRegisterOnlyAddress(8, reg));
                    break;
                case WORD:
                    masm.ldrs(32, 16, reg, AArch64Address.createBaseRegisterOnlyAddress(16, reg));
                    break;
                case DWORD:
                    masm.ldr(32, reg, AArch64Address.createBaseRegisterOnlyAddress(32, reg));
                    break;
                case QWORD:
                    masm.ldr(64, reg, AArch64Address.createBaseRegisterOnlyAddress(64, reg));
                    break;
                default:
                    throw GraalError.unimplemented();
            }
            masm.nop();
        } else {
            throw GraalError.unimplemented();
        }
        crb.recordMark(markId);
    }

}
