/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, BELLSOFT. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.amd64.AMD64ComplexVectorOp;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import java.util.EnumSet;

import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

@Opcode("AMD64_COPY_LONGS")
public final class AMD64CopyLongsOp extends AMD64ComplexVectorOp {
    public static final LIRInstructionClass<AMD64CopyLongsOp> TYPE = LIRInstructionClass.create(AMD64CopyLongsOp.class);

    private final boolean forward;
    private final int useAVX3Threshold;

    @Use({REG}) private Value rsrc;
    @Use({REG}) private Value rdst;
    @Use({REG}) private Value rlen;

    @Temp({REG}) private Value rsrcTemp;
    @Temp({REG}) private Value rdstTemp;
    @Temp({REG}) private Value rlenTemp;

    @Temp({REG}) private Value rtmp;
    @Temp({REG}) private Value[] vtmp;

    public AMD64CopyLongsOp(LIRGeneratorTool tool, EnumSet<CPUFeature> runtimeCheckedCPUFeatures, int useAVX3Threshold, boolean forward, Value src, Value dst, Value len) {
        super(TYPE, tool, runtimeCheckedCPUFeatures, AVXKind.AVXSize.ZMM);
        this.forward = forward;

        assert CodeUtil.isPowerOf2(useAVX3Threshold) : "AVX3Threshold must be power of 2";
        this.useAVX3Threshold = useAVX3Threshold;

        assert asRegister(src).equals(rsi);
        assert asRegister(dst).equals(rdi);
        assert asRegister(len).equals(rdx);

        rsrcTemp = rsrc = src;
        rdstTemp = rdst = dst;
        rlenTemp = rlen = len;

        rtmp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
        vtmp = allocateVectorRegisters(tool, JavaKind.Byte, 4);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        if (forward) {
            emitCopyForward(masm);
        } else {
            emitCopyBackward(masm);
        }
    }

    private void emitCopyForward(AMD64MacroAssembler masm) {
        Label copyBytes = new Label();
        Label copy8Bytes = new Label();
        Label exit = new Label();

        Register src = asRegister(rsrc);
        Register dst = asRegister(rdst);
        Register len = asRegister(rlen);
        Register tmp = asRegister(rtmp);

        masm.leaq(src, new AMD64Address(src, len, Stride.S1, -8));
        masm.leaq(dst, new AMD64Address(dst, len, Stride.S1, -8));
        masm.shrq(len, 3);  // bytes -> qwords
        masm.negq(len);
        masm.jmp(copyBytes);

        // Copy trailing qwords
        masm.bind(copy8Bytes);
        masm.movq(tmp, new AMD64Address(src, len, Stride.S8, 8));
        masm.movq(new AMD64Address(dst, len, Stride.S8, 8), tmp);
        masm.incqAndJcc(len, ConditionFlag.NotZero, copy8Bytes, true);
        masm.jmp(exit);

        // Copy in multi-bytes chunks
        emitCopyForward(masm, src, dst, len, tmp, copyBytes, copy8Bytes);
        masm.bind(exit);
    }

    private void emitCopyForward(AMD64MacroAssembler masm, Register src, Register dst, Register len, Register tmp,
                    Label copyBytes, Label copy8Bytes) {
        Label loop = new Label();
        masm.align(16);
        if (supports(CPUFeature.AVX)) {
            Register tmp0 = asRegister(vtmp[0]);
            Register tmp1 = asRegister(vtmp[1]);
            Register tmp2 = asRegister(vtmp[2]);
            Register tmp3 = asRegister(vtmp[3]);
            Label end = new Label();
            // Copy 64-bytes per iteration
            if (supportsAVX512VLBWAndZMM()) {
                Label avx512Loop = new Label();
                Label avx2Loop = new Label();
                Label copy32Bytes = new Label();
                Label aboveThreshold = new Label();
                Label belowThreshold = new Label();

                masm.bind(copyBytes);
                masm.cmpqAndJcc(len, -useAVX3Threshold / 8, ConditionFlag.Less, aboveThreshold, true);
                masm.jmpb(belowThreshold);

                masm.bind(avx512Loop);
                masm.vmovdqu64(tmp0, new AMD64Address(src, len, Stride.S8, -56));
                masm.vmovdqu64(new AMD64Address(dst, len, Stride.S8, -56), tmp0);

                masm.bind(aboveThreshold);
                masm.addqAndJcc(len, 8, ConditionFlag.LessEqual, avx512Loop, true);
                masm.jmpb(copy32Bytes);

                masm.bind(avx2Loop);
                masm.vmovdqu(tmp0, new AMD64Address(src, len, Stride.S8, -56));
                masm.vmovdqu(new AMD64Address(dst, len, Stride.S8, -56), tmp0);
                masm.vmovdqu(tmp1, new AMD64Address(src, len, Stride.S8, -24));
                masm.vmovdqu(new AMD64Address(dst, len, Stride.S8, -24), tmp1);

                masm.bind(belowThreshold);
                masm.addqAndJcc(len, 8, ConditionFlag.LessEqual, avx2Loop, true);

                masm.bind(copy32Bytes);
                masm.subqAndJcc(len, 4, ConditionFlag.Greater, end, true);
            } else {
                masm.bind(loop);
                if (supportsAVX2AndYMM()) {
                    masm.vmovdqu(tmp0, new AMD64Address(src, len, Stride.S8, -56));
                    masm.vmovdqu(new AMD64Address(dst, len, Stride.S8, -56), tmp0);
                    masm.vmovdqu(tmp1, new AMD64Address(src, len, Stride.S8, -24));
                    masm.vmovdqu(new AMD64Address(dst, len, Stride.S8, -24), tmp1);
                } else { // AVX1 and XMM
                    masm.movdqu(tmp0, new AMD64Address(src, len, Stride.S8, -56));
                    masm.movdqu(new AMD64Address(dst, len, Stride.S8, -56), tmp0);
                    masm.movdqu(tmp1, new AMD64Address(src, len, Stride.S8, -40));
                    masm.movdqu(new AMD64Address(dst, len, Stride.S8, -40), tmp1);
                    masm.movdqu(tmp2, new AMD64Address(src, len, Stride.S8, -24));
                    masm.movdqu(new AMD64Address(dst, len, Stride.S8, -24), tmp2);
                    masm.movdqu(tmp3, new AMD64Address(src, len, Stride.S8, -8));
                    masm.movdqu(new AMD64Address(dst, len, Stride.S8, -8), tmp3);
                }
                masm.bind(copyBytes);
                masm.addqAndJcc(len, 8, ConditionFlag.LessEqual, loop, true);
                masm.subqAndJcc(len, 4, ConditionFlag.Greater, end, true);
            }
            // Copy trailing 32 bytes
            if (supportsAVX2AndYMM()) {
                masm.vmovdqu(tmp0, new AMD64Address(src, len, Stride.S8, -24));
                masm.vmovdqu(new AMD64Address(dst, len, Stride.S8, -24), tmp0);
            } else { // AVX1 and XMM
                masm.movdqu(tmp0, new AMD64Address(src, len, Stride.S8, -24));
                masm.movdqu(new AMD64Address(dst, len, Stride.S8, -24), tmp0);
                masm.movdqu(tmp1, new AMD64Address(src, len, Stride.S8, -8));
                masm.movdqu(new AMD64Address(dst, len, Stride.S8, -8), tmp1);
            }
            masm.addq(len, 4);
            masm.bind(end);
        } else {
            // Copy 32-byte chunks
            masm.bind(loop);
            masm.movq(tmp, new AMD64Address(src, len, Stride.S8, -24));
            masm.movq(new AMD64Address(dst, len, Stride.S8, -24), tmp);
            masm.movq(tmp, new AMD64Address(src, len, Stride.S8, -16));
            masm.movq(new AMD64Address(dst, len, Stride.S8, -16), tmp);
            masm.movq(tmp, new AMD64Address(src, len, Stride.S8, -8));
            masm.movq(new AMD64Address(dst, len, Stride.S8, -8), tmp);
            masm.movq(tmp, new AMD64Address(src, len, Stride.S8, 0));
            masm.movq(new AMD64Address(dst, len, Stride.S8, 0), tmp);

            masm.bind(copyBytes);
            masm.addqAndJcc(len, 4, ConditionFlag.LessEqual, loop, true);
        }
        masm.subqAndJcc(len, 4, ConditionFlag.Less, copy8Bytes, false);
    }

    private void emitCopyBackward(AMD64MacroAssembler masm) {
        Label copyBytes = new Label();
        Label copy8Bytes = new Label();
        Label exit = new Label();

        Register src = asRegister(rsrc);
        Register dst = asRegister(rdst);
        Register len = asRegister(rlen);
        Register tmp = asRegister(rtmp);

        masm.shrq(len, 3);  // bytes -> qwords
        masm.jmp(copyBytes);

        // Copy trailing qwords
        masm.bind(copy8Bytes);
        masm.movq(tmp, new AMD64Address(src, len, Stride.S8, -8));
        masm.movq(new AMD64Address(dst, len, Stride.S8, -8), tmp);
        masm.decqAndJcc(len, ConditionFlag.NotZero, copy8Bytes, true);
        masm.jmp(exit);

        // Copy in multi-bytes chunks
        emitCopyBackward(masm, src, dst, len, tmp, copyBytes, copy8Bytes);
        masm.bind(exit);
    }

    private void emitCopyBackward(AMD64MacroAssembler masm, Register src, Register dst, Register len, Register tmp,
                    Label copyBytes, Label copy8Bytes) {
        Label loop = new Label();
        masm.align(16);
        if (supports(CPUFeature.AVX)) {
            Register tmp0 = asRegister(vtmp[0]);
            Register tmp1 = asRegister(vtmp[1]);
            Register tmp2 = asRegister(vtmp[2]);
            Register tmp3 = asRegister(vtmp[3]);
            Label end = new Label();
            // Copy 64-bytes per iteration
            if (supportsAVX512VLBWAndZMM()) {
                Label avx512Loop = new Label();
                Label avx2Loop = new Label();
                Label copy32Bytes = new Label();
                Label aboveThreshold = new Label();
                Label belowThreshold = new Label();

                masm.bind(copyBytes);
                masm.cmpqAndJcc(len, useAVX3Threshold / 8, ConditionFlag.Greater, aboveThreshold, true);
                masm.jmpb(belowThreshold);

                masm.bind(avx512Loop);
                masm.vmovdqu64(tmp0, new AMD64Address(src, len, Stride.S8, 0));
                masm.vmovdqu64(new AMD64Address(dst, len, Stride.S8, 0), tmp0);

                masm.bind(aboveThreshold);
                masm.subqAndJcc(len, 8, ConditionFlag.GreaterEqual, avx512Loop, true);
                masm.jmpb(copy32Bytes);

                masm.bind(avx2Loop);
                masm.vmovdqu(tmp0, new AMD64Address(src, len, Stride.S8, 32));
                masm.vmovdqu(new AMD64Address(dst, len, Stride.S8, 32), tmp0);
                masm.vmovdqu(tmp1, new AMD64Address(src, len, Stride.S8, 0));
                masm.vmovdqu(new AMD64Address(dst, len, Stride.S8, 0), tmp1);

                masm.bind(belowThreshold);
                masm.subqAndJcc(len, 8, ConditionFlag.GreaterEqual, avx2Loop, true);

                masm.bind(copy32Bytes);
                masm.addqAndJcc(len, 4, ConditionFlag.Less, end, true);
            } else {
                masm.bind(loop);
                if (supportsAVX2AndYMM()) {
                    masm.vmovdqu(tmp0, new AMD64Address(src, len, Stride.S8, 32));
                    masm.vmovdqu(new AMD64Address(dst, len, Stride.S8, 32), tmp0);
                    masm.vmovdqu(tmp1, new AMD64Address(src, len, Stride.S8, 0));
                    masm.vmovdqu(new AMD64Address(dst, len, Stride.S8, 0), tmp1);
                } else { // AVX1 and XMM
                    masm.movdqu(tmp0, new AMD64Address(src, len, Stride.S8, 48));
                    masm.movdqu(new AMD64Address(dst, len, Stride.S8, 48), tmp0);
                    masm.movdqu(tmp1, new AMD64Address(src, len, Stride.S8, 32));
                    masm.movdqu(new AMD64Address(dst, len, Stride.S8, 32), tmp1);
                    masm.movdqu(tmp2, new AMD64Address(src, len, Stride.S8, 16));
                    masm.movdqu(new AMD64Address(dst, len, Stride.S8, 16), tmp2);
                    masm.movdqu(tmp3, new AMD64Address(src, len, Stride.S8, 0));
                    masm.movdqu(new AMD64Address(dst, len, Stride.S8, 0), tmp3);
                }
                masm.bind(copyBytes);
                masm.subqAndJcc(len, 8, ConditionFlag.GreaterEqual, loop, true);
                masm.addqAndJcc(len, 4, ConditionFlag.Less, end, true);
            }
            // Copy trailing 32 bytes
            if (supportsAVX2AndYMM()) {
                masm.vmovdqu(tmp0, new AMD64Address(src, len, Stride.S8, 0));
                masm.vmovdqu(new AMD64Address(dst, len, Stride.S8, 0), tmp0);
            } else { // AVX1 and XMM
                masm.movdqu(tmp0, new AMD64Address(src, len, Stride.S8, 16));
                masm.movdqu(new AMD64Address(dst, len, Stride.S8, 16), tmp0);
                masm.movdqu(tmp1, new AMD64Address(src, len, Stride.S8, 0));
                masm.movdqu(new AMD64Address(dst, len, Stride.S8, 0), tmp1);
            }
            masm.subq(len, 4);
            masm.bind(end);
        } else {
            // Copy 32-bytes per iteration
            masm.bind(loop);
            masm.movq(tmp, new AMD64Address(src, len, Stride.S8, 24));
            masm.movq(new AMD64Address(dst, len, Stride.S8, 24), tmp);
            masm.movq(tmp, new AMD64Address(src, len, Stride.S8, 16));
            masm.movq(new AMD64Address(dst, len, Stride.S8, 16), tmp);
            masm.movq(tmp, new AMD64Address(src, len, Stride.S8, 8));
            masm.movq(new AMD64Address(dst, len, Stride.S8, 8), tmp);
            masm.movq(tmp, new AMD64Address(src, len, Stride.S8, 0));
            masm.movq(new AMD64Address(dst, len, Stride.S8, 0), tmp);

            masm.bind(copyBytes);
            masm.subqAndJcc(len, 4, ConditionFlag.GreaterEqual, loop, true);
        }
        masm.addqAndJcc(len, 4, ConditionFlag.Greater, copy8Bytes, false);
    }
}
