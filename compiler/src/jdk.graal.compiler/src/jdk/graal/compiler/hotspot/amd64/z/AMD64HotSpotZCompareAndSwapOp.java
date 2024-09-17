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

import static jdk.graal.compiler.hotspot.amd64.z.AMD64HotSpotZBarrierSetLIRGenerator.zColor;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.ZWriteBarrierSetLIRGeneratorTool.StoreKind;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Code generation for the atomic compare and swap with store barrier.
 */
@Opcode("CAS_Z")
public final class AMD64HotSpotZCompareAndSwapOp extends AMD64HotSpotZStoreBarrieredOp {
    public static final LIRInstructionClass<AMD64HotSpotZCompareAndSwapOp> TYPE = LIRInstructionClass.create(AMD64HotSpotZCompareAndSwapOp.class);

    private final boolean isLogic;
    private final AMD64Kind accessKind;

    @Use protected AllocatableValue cmpValue;
    @Alive protected AllocatableValue newValue;

    public AMD64HotSpotZCompareAndSwapOp(boolean isLogic,
                    AMD64Kind accessKind,
                    AllocatableValue result,
                    AMD64AddressValue address,
                    AllocatableValue cmpValue,
                    AllocatableValue newValue,
                    AllocatableValue tmp,
                    AllocatableValue tmp2,
                    GraalHotSpotVMConfig config,
                    ForeignCallLinkage callTarget) {
        super(TYPE, result, address, tmp, tmp2, config, callTarget, StoreKind.Atomic);
        this.isLogic = isLogic;
        this.accessKind = accessKind;
        this.cmpValue = cmpValue;
        this.newValue = newValue;
        assert asRegister(cmpValue).equals(AMD64.rax) : cmpValue;
        assert asRegister(result).equals(AMD64.rax) : result;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        GraalError.guarantee(accessKind == AMD64Kind.QWORD, "ZGC only supports uncompressed oops");
        assert LIRValueUtil.differentRegisters(cmpValue, newValue, storeAddress);

        emitPreWriteBarrier(crb, masm, asRegister(tmp), null);
        zColor(crb, masm, asRegister(tmp), asRegister(newValue));
        AMD64HotSpotZBarrierSetLIRGenerator.zColor(crb, masm, asRegister(cmpValue));
        if (crb.target.isMP) {
            masm.lock();
        }
        masm.cmpxchgq(asRegister(tmp), storeAddress.toAddress(masm));
        if (!isLogic) {
            Register ref = asRegister(cmpValue);
            AMD64HotSpotZBarrierSetLIRGenerator.zUncolor(crb, masm, ref);
        }
    }
}
