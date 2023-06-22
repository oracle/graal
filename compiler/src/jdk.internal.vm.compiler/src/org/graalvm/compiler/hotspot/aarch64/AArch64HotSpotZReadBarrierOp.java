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
package org.graalvm.compiler.hotspot.aarch64;

import static jdk.vm.ci.code.ValueUtil.asRegister;

import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.aarch64.AArch64AddressValue;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;

public class AArch64HotSpotZReadBarrierOp extends AArch64HotSpotZBarrieredOp {
    public static final LIRInstructionClass<AArch64HotSpotZReadBarrierOp> TYPE = LIRInstructionClass.create(AArch64HotSpotZReadBarrierOp.class);

    private final MemoryOrderMode memoryOrder;
    @State protected LIRFrameState state;

    public AArch64HotSpotZReadBarrierOp(Variable result, AArch64AddressValue loadAddress, MemoryOrderMode memoryOrder, LIRFrameState state, GraalHotSpotVMConfig config,
                    ForeignCallLinkage callTarget) {
        super(TYPE, result, loadAddress, config, callTarget);
        this.memoryOrder = memoryOrder;
        this.state = state;
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
        emitBarrier(crb, masm);
    }
}
