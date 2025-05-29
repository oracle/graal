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
package jdk.graal.compiler.lir.amd64.g1;

import static jdk.graal.compiler.core.common.GraalOptions.VerifyAssemblyGCBarriers;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.gen.G1WriteBarrierSetLIRGeneratorTool;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Architecture specific G1 barrier set generator.
 */
public class AMD64G1BarrierSetLIRGenerator implements G1WriteBarrierSetLIRGeneratorTool {
    private final AMD64G1BarrierSetLIRTool barrierSetLIRTool;

    public AMD64G1BarrierSetLIRGenerator(AMD64G1BarrierSetLIRTool barrierSetLIRTool) {
        this.barrierSetLIRTool = barrierSetLIRTool;
    }

    @Override
    public void emitPreWriteBarrier(LIRGeneratorTool lirTool, Value address, AllocatableValue expectedObject, boolean nonNull) {
        AllocatableValue temp = lirTool.newVariable(LIRKind.value(AMD64Kind.QWORD));
        // If the assembly must load the value then it's needs a temporary to store it
        AllocatableValue temp2 = expectedObject.equals(Value.ILLEGAL) ? lirTool.newVariable(LIRKind.value(AMD64Kind.QWORD)) : Value.ILLEGAL;
        OptionValues options = lirTool.getResult().getLIR().getOptions();
        AllocatableValue temp3 = VerifyAssemblyGCBarriers.getValue(options) ? lirTool.newVariable(LIRKind.value(AMD64Kind.QWORD)) : Value.ILLEGAL;
        ForeignCallLinkage callTarget = lirTool.getForeignCalls().lookupForeignCall(this.barrierSetLIRTool.preWriteBarrierDescriptor());
        lirTool.getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
        lirTool.append(new AMD64G1PreWriteBarrierOp(address, expectedObject, temp, temp2, temp3, callTarget, nonNull, this.barrierSetLIRTool));
    }

    @Override
    public void emitPostWriteBarrier(LIRGeneratorTool lirTool, Value address, Value value, boolean nonNull) {
        AllocatableValue temp = lirTool.newVariable(LIRKind.value(AMD64Kind.QWORD));
        AMD64LIRInstruction op;
        if (barrierSetLIRTool.supportsLowLatencyBarriers()) {
            op = new AMD64G1PostWriteBarrierOp(address, value, temp, nonNull, this.barrierSetLIRTool);
        } else {
            AllocatableValue temp2 = lirTool.newVariable(LIRKind.value(AMD64Kind.QWORD));
            ForeignCallLinkage callTarget = lirTool.getForeignCalls().lookupForeignCall(this.barrierSetLIRTool.postWriteBarrierDescriptor());
            lirTool.getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
            op = new AMD64G1CardQueuePostWriteBarrierOp(address, value, temp, temp2, callTarget, nonNull, this.barrierSetLIRTool);
        }
        lirTool.append(op);
    }
}
