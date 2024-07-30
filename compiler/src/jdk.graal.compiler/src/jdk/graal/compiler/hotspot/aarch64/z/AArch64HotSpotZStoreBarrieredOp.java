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

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.ZWriteBarrierSetLIRGeneratorTool;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.aarch64.AArch64AddressValue;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Base class for ops that require a store barrier. It ensures that the main values used in the
 * store barrier are available and have the correct lifetimes.
 */
public abstract class AArch64HotSpotZStoreBarrieredOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64HotSpotZStoreBarrieredOp> TYPE = LIRInstructionClass.create(AArch64HotSpotZStoreBarrieredOp.class);

    @LIRInstruction.Def({REG}) protected AllocatableValue result;
    @LIRInstruction.Alive({COMPOSITE}) protected AArch64AddressValue storeAddress;
    @LIRInstruction.Temp protected AllocatableValue tmp;
    @LIRInstruction.Temp protected AllocatableValue tmp2;

    protected final GraalHotSpotVMConfig config;
    protected final ForeignCallLinkage callTarget;

    protected final ZWriteBarrierSetLIRGeneratorTool.StoreKind storeKind;

    protected AArch64HotSpotZStoreBarrieredOp(LIRInstructionClass<? extends AArch64HotSpotZStoreBarrieredOp> type,
                    AllocatableValue result,
                    AArch64AddressValue storeAddress,
                    AllocatableValue tmp,
                    AllocatableValue tmp2,
                    GraalHotSpotVMConfig config,
                    ForeignCallLinkage callTarget,
                    ZWriteBarrierSetLIRGeneratorTool.StoreKind storeKind) {
        super(type);
        this.result = result;
        this.storeAddress = storeAddress;
        this.tmp = tmp;
        this.tmp2 = tmp2;
        this.config = config;
        this.callTarget = callTarget;
        this.storeKind = storeKind;
    }
}
