/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.aarch64.z;

import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.ZWriteBarrierSetLIRGeneratorTool;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.aarch64.AArch64AtomicMove;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Code generation for the atomic compare and swap with store barrier.
 */
@Opcode("CAS_Z")
public final class AArch64HotSpotZCompareAndSwapOp extends AArch64AtomicMove.CompareAndSwapOp {
    public static final LIRInstructionClass<AArch64HotSpotZCompareAndSwapOp> TYPE = LIRInstructionClass.create(AArch64HotSpotZCompareAndSwapOp.class);

    private final boolean isLogic;
    private final GraalHotSpotVMConfig config;
    private final ForeignCallLinkage callTarget;
    @Temp private AllocatableValue tmp;
    @Temp private AllocatableValue tmp2;

    public AArch64HotSpotZCompareAndSwapOp(boolean isLogic,
                    AArch64Kind accessKind,
                    MemoryOrderMode memoryOrder,
                    boolean setConditionFlags,
                    AllocatableValue result,
                    Value expectedValue,
                    AllocatableValue newValue,
                    AllocatableValue addressValue,
                    GraalHotSpotVMConfig config,
                    ForeignCallLinkage callTarget,
                    AllocatableValue tmp, AllocatableValue tmp2) {
        super(TYPE, accessKind, memoryOrder, setConditionFlags, result, expectedValue, newValue, addressValue);
        this.isLogic = isLogic;
        this.config = config;
        this.callTarget = callTarget;
        this.tmp = tmp;
        this.tmp2 = tmp2;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        // Use ANY_SIZE here because the store barrier performs 16 and 64 access for atomics.
        AArch64Address location = AArch64Address.createBaseRegisterOnlyAddress(AArch64Address.ANY_SIZE, asRegister(addressValue));
        Register tmpRegister = asRegister(tmp);
        AArch64HotSpotZBarrierSetLIRGenerator.emitStoreBarrier(crb, masm, this, config, location, tmpRegister, ZWriteBarrierSetLIRGeneratorTool.StoreKind.Atomic,
                        callTarget, null);
        Register tmp2Register = asRegister(tmp2);
        // Color newValue and expectedValue into a temporary registers
        AArch64HotSpotZBarrierSetLIRGenerator.zColor(crb, masm, config, tmpRegister, asRegister(newValue));
        AArch64HotSpotZBarrierSetLIRGenerator.zColor(crb, masm, config, tmp2Register, asRegister(expectedValue));
        Register address = asRegister(addressValue);
        // Produce the colored result into a temporary register
        Register result = asRegister(resultValue);
        Register expected = tmp2Register;
        emitCompareAndSwap(masm, accessKind, address, result, expected, tmpRegister, memoryOrder, setConditionFlags);
        if (!isLogic) {
            AArch64HotSpotZBarrierSetLIRGenerator.zUncolor(masm, config, asRegister(resultValue));
        }
    }
}
