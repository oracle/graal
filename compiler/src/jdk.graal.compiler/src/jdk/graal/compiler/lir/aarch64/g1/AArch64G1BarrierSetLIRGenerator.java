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
package jdk.graal.compiler.lir.aarch64.g1;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.graal.compiler.lir.gen.G1WriteBarrierSetLIRGeneratorTool;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Architecture specific G1 barrier set generator.
 */
public class AArch64G1BarrierSetLIRGenerator implements G1WriteBarrierSetLIRGeneratorTool {
    private final AArch64G1BarrierSetLIRTool barrierSetLIRTool;

    public AArch64G1BarrierSetLIRGenerator(AArch64G1BarrierSetLIRTool barrierSetLIRTool) {
        this.barrierSetLIRTool = barrierSetLIRTool;
    }

    @Override
    public void emitPreWriteBarrier(LIRGeneratorTool lirTool, Value address, AllocatableValue expectedObject, boolean nonNull) {
        AllocatableValue temp = lirTool.newVariable(LIRKind.value(AArch64Kind.QWORD));
        // If the assembly must load the value then it's needs a temporary to store it
        AllocatableValue temp2 = expectedObject.equals(Value.ILLEGAL) ? lirTool.newVariable(LIRKind.value(AArch64Kind.QWORD)) : Value.ILLEGAL;

        // Load the address into a register
        AllocatableValue addressValue = lirTool.newVariable(address.getValueKind());
        lirTool.emitMove(addressValue, address);

        ForeignCallLinkage callTarget = lirTool.getForeignCalls().lookupForeignCall(barrierSetLIRTool.preWriteBarrierDescriptor());
        lirTool.getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
        lirTool.append(new AArch64G1PreWriteBarrierOp(addressValue, expectedObject, temp, temp2, callTarget, nonNull, barrierSetLIRTool));
    }

    @Override
    public void emitPostWriteBarrier(LIRGeneratorTool lirTool, Value address, Value value, boolean nonNull) {
        AllocatableValue temp = lirTool.newVariable(LIRKind.value(AArch64Kind.QWORD));
        AllocatableValue temp2 = lirTool.newVariable(LIRKind.value(AArch64Kind.QWORD));

        AArch64LIRInstruction op;
        if (barrierSetLIRTool.supportsLowLatencyBarriers()) {
            op = new AArch64G1PostWriteBarrierOp(address, value, temp, temp2, nonNull, barrierSetLIRTool);
        } else {
            ForeignCallLinkage callTarget = lirTool.getForeignCalls().lookupForeignCall(barrierSetLIRTool.postWriteBarrierDescriptor());
            lirTool.getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
            op = new AArch64G1CardQueuePostWriteBarrierOp(address, value, temp, temp2, callTarget, nonNull, barrierSetLIRTool);
        }
        lirTool.append(op);
    }
}
