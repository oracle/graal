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

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.ZWriteBarrierSetLIRGeneratorTool;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.aarch64.AArch64AddressValue;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Emit the store barrier for a normal write.
 */
public class AArch64HotSpotZPreWriteBarrierOp extends AArch64HotSpotZStoreBarrieredOp {
    public static final LIRInstructionClass<AArch64HotSpotZPreWriteBarrierOp> TYPE = LIRInstructionClass.create(AArch64HotSpotZPreWriteBarrierOp.class);

    @Alive({REG}) protected Value writeValue;
    private final boolean emitPreWriteBarrier;
    @State protected LIRFrameState state;

    protected AArch64HotSpotZPreWriteBarrierOp(Value writeValue,
                    AArch64AddressValue loadAddress,
                    AllocatableValue tmp,
                    AllocatableValue tmp2,
                    GraalHotSpotVMConfig config,
                    ForeignCallLinkage callTarget,
                    AllocatableValue result,
                    ZWriteBarrierSetLIRGeneratorTool.StoreKind storeKind,
                    boolean emitPreWriteBarrier,
                    LIRFrameState state) {
        super(TYPE, result, loadAddress, tmp, tmp2, config, callTarget, storeKind);
        this.writeValue = writeValue;
        this.emitPreWriteBarrier = emitPreWriteBarrier;
        this.state = state;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        if (emitPreWriteBarrier) {
            AArch64HotSpotZBarrierSetLIRGenerator.emitStoreBarrier(crb, masm, this, config, storeAddress.toAddress(), asRegister(result),
                            storeKind, callTarget, state);
        }
        AArch64HotSpotZBarrierSetLIRGenerator.zColor(crb, masm, config, asRegister(result), asRegister(writeValue));
    }
}
