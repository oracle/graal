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
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.ZWriteBarrierSetLIRGeneratorTool;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.aarch64.AArch64AtomicMove;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Code generation for atomic read and write with read barrier.
 */
public class AArch64HotSpotZAtomicReadAndWriteOp extends AArch64AtomicMove.AtomicReadAndWriteOp {
    public static final LIRInstructionClass<AArch64HotSpotZAtomicReadAndWriteOp> TYPE = LIRInstructionClass.create(AArch64HotSpotZAtomicReadAndWriteOp.class);
    private final GraalHotSpotVMConfig config;
    private final ForeignCallLinkage callTarget;

    public AArch64HotSpotZAtomicReadAndWriteOp(AArch64Kind platformKind, Variable result, AllocatableValue address, AllocatableValue newValue, GraalHotSpotVMConfig config,
                    ForeignCallLinkage callTarget) {
        super(TYPE, platformKind, result, address, newValue);
        this.config = config;
        this.callTarget = callTarget;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        // Use ANY_SIZE here because the store barrier performs 16 and 64 access for atomics.
        AArch64Address address = AArch64Address.createBaseRegisterOnlyAddress(AArch64Address.ANY_SIZE, asRegister(addressValue));
        AArch64HotSpotZBarrierSetLIRGenerator.emitStoreBarrier(crb, masm, this, config, address, asRegister(resultValue), ZWriteBarrierSetLIRGeneratorTool.StoreKind.Atomic,
                        callTarget, null);
        try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister()) {
            Register rscratch1 = sc1.getRegister();
            AArch64HotSpotZBarrierSetLIRGenerator.zColor(crb, masm, config, rscratch1, asRegister(newValue));
            emitSwap(masm, accessKind, asRegister(addressValue), asRegister(resultValue), rscratch1);
            AArch64HotSpotZBarrierSetLIRGenerator.zUncolor(masm, config, asRegister(resultValue));
        }
    }
}
