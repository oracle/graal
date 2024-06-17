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
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.aarch64.AArch64AddressValue;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;

/**
 * Emit the load barrier for a read.
 */
public class AArch64HotSpotZReadBarrierOp extends AArch64HotSpotZBarrieredOp {
    public static final LIRInstructionClass<AArch64HotSpotZReadBarrierOp> TYPE = LIRInstructionClass.create(AArch64HotSpotZReadBarrierOp.class);

    private final MemoryOrderMode memoryOrder;
    @State protected LIRFrameState state;
    private final boolean isNotStrong;

    public AArch64HotSpotZReadBarrierOp(Variable result, AArch64AddressValue loadAddress, MemoryOrderMode memoryOrder, LIRFrameState state, GraalHotSpotVMConfig config,
                    ForeignCallLinkage callTarget, boolean isNotStrong) {
        super(TYPE, result, loadAddress, config, callTarget);
        this.memoryOrder = memoryOrder;
        this.state = state;
        this.isNotStrong = isNotStrong;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        int loadPosition;
        final Register resultReg = asRegister(result);
        switch (memoryOrder) {
            case PLAIN:
            case OPAQUE: // no fences are needed for opaque memory accesses
                loadPosition = masm.position();
                masm.ldr(64, resultReg, loadAddress.toAddress(), false);
                break;
            case ACQUIRE:
            case VOLATILE: {
                try (AArch64MacroAssembler.ScratchRegister scratch1 = masm.getScratchRegister()) {
                    AArch64Address address = loadAddress.toAddress();
                    final Register addrReg;
                    if (address.isBaseRegisterOnly()) {
                        // Can directly use the base register as the address
                        addrReg = address.getBase();
                    } else {
                        addrReg = scratch1.getRegister();
                        masm.loadAddress(addrReg, address);
                    }
                    loadPosition = masm.position();
                    masm.ldar(64, resultReg, addrReg);
                }
                break;
            }
            default:
                throw GraalError.shouldNotReachHere("Unexpected memory order");
        }
        if (state != null) {
            crb.recordImplicitException(loadPosition, state);
        }
        emitBarrier(crb, masm, isNotStrong);
    }
}
