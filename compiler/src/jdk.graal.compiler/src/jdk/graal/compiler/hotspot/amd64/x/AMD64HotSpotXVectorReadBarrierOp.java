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
package jdk.graal.compiler.hotspot.amd64.x;

import static jdk.vm.ci.amd64.AMD64.r15;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.EnumSet;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64Call;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

public class AMD64HotSpotXVectorReadBarrierOp extends AMD64HotSpotXBarrieredOp {
    public static final LIRInstructionClass<AMD64HotSpotXVectorReadBarrierOp> TYPE = LIRInstructionClass.create(AMD64HotSpotXVectorReadBarrierOp.class);

    private final AVXSize size;
    private final AMD64Assembler.VexMoveOp op;

    @Temp({OperandFlag.REG}) protected AllocatableValue temp;
    @State protected LIRFrameState state;

    public AMD64HotSpotXVectorReadBarrierOp(AVXSize size, AMD64Assembler.VexMoveOp op, Variable result, AMD64AddressValue loadAddress, LIRFrameState state, GraalHotSpotVMConfig config,
                    ForeignCallLinkage callTarget, Variable temp) {
        super(TYPE, result, loadAddress, config, callTarget);
        this.size = size;
        this.op = op;
        this.state = state;
        this.temp = temp;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        if (state != null) {
            crb.recordImplicitException(masm.position(), state);
        }
        Register resultReg = asRegister(result);
        op.emit(masm, size, resultReg, loadAddress.toAddress());

        AMD64Address address = loadAddress.toAddress();

        final Label entryPoint = new Label();
        final Label continuation = new Label();

        Register tempReg = asRegister(temp);
        EnumSet<AMD64.CPUFeature> features = masm.getFeatures();
        GraalError.guarantee(features.contains(AMD64.CPUFeature.AVX), "Unexpected vector LIR without AVX");
        /*
         * Depending on the target, we may need to use a broadcast larger than the read's size. This
         * is benign as we will only use the part matching the read's size.
         */
        AVXSize broadcastSize;
        VexRMOp broadcastOp;
        if (masm.supportsFullAVX512()) {
            broadcastSize = size;
            broadcastOp = VexRMOp.EVPBROADCASTQ;
        } else if (features.contains(AMD64.CPUFeature.AVX2)) {
            broadcastSize = size;
            broadcastOp = VexRMOp.VPBROADCASTQ;
        } else {
            broadcastSize = AVXSize.YMM;
            broadcastOp = VexRMOp.VBROADCASTSD;
        }
        broadcastOp.emit(masm, broadcastSize, tempReg, new AMD64Address(r15, config.threadAddressBadMaskOffset));
        masm.vptest(resultReg, tempReg, size);
        masm.jcc(AMD64Assembler.ConditionFlag.NotZero, entryPoint);
        crb.getLIR().addSlowPath(this, () -> {
            masm.bind(entryPoint);

            int count = size.getBytes() / 8;
            CallingConvention cc = callTarget.getOutgoingCallingConvention();
            AMD64Address cArg0 = (AMD64Address) crb.asAddress(cc.getArgument(0));
            AMD64Address cArg1 = (AMD64Address) crb.asAddress(cc.getArgument(1));

            // Use stack argument space for temporary register
            masm.movq(cArg1, rax);
            masm.leaq(rax, address);
            masm.movq(cArg0, rax);
            masm.movq(rax, cArg1);
            masm.movslq(cArg1, count);
            AMD64Call.directCall(crb, masm, callTarget, null, false, null);
            masm.movq(resultReg, cArg0);
            op.emit(masm, size, resultReg, loadAddress.toAddress());

            // Return to inline code
            masm.jmp(continuation);
        });
        masm.bind(continuation);
    }
}
