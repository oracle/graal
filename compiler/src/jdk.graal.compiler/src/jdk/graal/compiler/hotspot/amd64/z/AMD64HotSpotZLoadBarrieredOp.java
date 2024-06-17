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
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Base class for LIR ops that require a read barrier. This ensures the Def/Alive restrictions for
 * safe register allocation are met by any subclass.
 */
public abstract class AMD64HotSpotZLoadBarrieredOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64HotSpotZLoadBarrieredOp> TYPE = LIRInstructionClass.create(AMD64HotSpotZLoadBarrieredOp.class);

    @Def({REG}) protected AllocatableValue result;
    @Alive({COMPOSITE}) protected AMD64AddressValue loadAddress;

    protected final GraalHotSpotVMConfig config;
    protected final ForeignCallLinkage callTarget;

    protected AMD64HotSpotZLoadBarrieredOp(LIRInstructionClass<? extends AMD64HotSpotZLoadBarrieredOp> type,
                    AllocatableValue result,
                    AMD64AddressValue loadAddress,
                    GraalHotSpotVMConfig config,
                    ForeignCallLinkage callTarget) {
        super(type);
        this.result = result;
        this.loadAddress = loadAddress;
        this.config = config;
        this.callTarget = callTarget;
    }

    /**
     * Emit a barrier testing the {@code result} register.
     */
    protected void emitLoadBarrier(CompilationResultBuilder crb, AMD64MacroAssembler masm, boolean isNotStrong) {
        AMD64HotSpotZBarrierSetLIRGenerator.emitLoadBarrier(crb, masm, asRegister(result), callTarget, loadAddress.toAddress(), this, null, isNotStrong);
    }
}
