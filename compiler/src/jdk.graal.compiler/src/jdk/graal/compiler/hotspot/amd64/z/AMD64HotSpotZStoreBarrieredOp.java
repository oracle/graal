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
package jdk.graal.compiler.hotspot.amd64.z;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.ZWriteBarrierSetLIRGeneratorTool.StoreKind;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Base class for ops that require a store barrier. It ensures that the main values used in the
 * store barrier are available and have the correct lifetimes.
 */
public abstract class AMD64HotSpotZStoreBarrieredOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64HotSpotZStoreBarrieredOp> TYPE = LIRInstructionClass.create(AMD64HotSpotZStoreBarrieredOp.class);

    @Def({REG}) protected AllocatableValue result;
    @Alive({COMPOSITE}) protected AMD64AddressValue storeAddress;
    @Temp protected AllocatableValue tmp;
    @Temp protected AllocatableValue tmp2;

    protected final GraalHotSpotVMConfig config;
    protected final ForeignCallLinkage callTarget;
    protected final StoreKind storeKind;

    protected AMD64HotSpotZStoreBarrieredOp(LIRInstructionClass<? extends AMD64HotSpotZStoreBarrieredOp> type,
                    AllocatableValue result,
                    AMD64AddressValue storeAddress,
                    AllocatableValue tmp,
                    AllocatableValue tmp2,
                    GraalHotSpotVMConfig config,
                    ForeignCallLinkage callTarget,
                    StoreKind storeKind) {
        super(type);
        this.result = result;
        this.storeAddress = storeAddress;
        this.tmp = tmp;
        this.tmp2 = tmp2;
        this.config = config;
        this.callTarget = callTarget;
        this.storeKind = storeKind;
    }

    protected void emitPreWriteBarrier(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register resultReg, LIRFrameState state) {
        AMD64HotSpotZBarrierSetLIRGenerator.emitPreWriteBarrier(crb, masm, this, config, storeAddress.toAddress(masm), resultReg, storeKind, asRegister(tmp), asRegister(tmp2), callTarget,
                        state);
    }
}
