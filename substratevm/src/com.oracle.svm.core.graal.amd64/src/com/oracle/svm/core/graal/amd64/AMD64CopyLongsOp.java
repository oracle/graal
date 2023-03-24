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
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.amd64.AMD64ComplexVectorOp;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import java.util.EnumSet;

import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.*;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

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
        super(TYPE, tool, runtimeCheckedCPUFeatures, ZMM);
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
        Label L_copy_bytes = new Label();
        Label L_copy_8_bytes = new Label();
        Label L_exit = new Label();

        Register src = asRegister(rsrc);
        Register dst = asRegister(rdst);
        Register len = asRegister(rlen);
        Register tmp = asRegister(rtmp);

        masm.leaq(src, new AMD64Address(src, len, Stride.S1, -8));
        masm.leaq(dst, new AMD64Address(dst, len, Stride.S1, -8));
        masm.shrq(len, 3);  // bytes -> qwords
        masm.negq(len);
        masm.jmp(L_copy_bytes);

        // Copy trailing qwords
        masm.bind(L_copy_8_bytes);
        masm.movq(tmp, new AMD64Address(src, len, Stride.S8, 8));
        masm.movq(new AMD64Address(dst, len, Stride.S8, 8), tmp);
        masm.incqAndJcc(len, ConditionFlag.NotZero, L_copy_8_bytes, true);
        masm.jmp(L_exit);

        // Copy in multi-bytes chunks
        emitCopyForward(masm, src, dst, len, tmp, L_copy_bytes, L_copy_8_bytes);
        masm.bind(L_exit);
    }

    private void emitCopyForward(AMD64MacroAssembler masm, Register src, Register dst, Register len, Register tmp,
                                 Label L_copy_bytes, Label L_copy_8_bytes) {
        Label L_loop = new Label();
        masm.align(16);
        if (supports(CPUFeature.AVX)) {
            Register tmp0 = asRegister(vtmp[0]);
            Register tmp1 = asRegister(vtmp[1]);
            Register tmp2 = asRegister(vtmp[2]);
            Register tmp3 = asRegister(vtmp[3]);
            Label L_end = new Label();
            // Copy 64-bytes per iteration
            if (supportsAVX512VLBWAndZMM()) {
                Label L_loop_avx512 = new Label();
                Label L_loop_avx2 = new Label();
                Label L_32_byte_head = new Label();
                Label L_above_threshold = new Label();
                Label L_below_threshold = new Label();

                masm.bind(L_copy_bytes);
                masm.cmpqAndJcc(len, -useAVX3Threshold / 8, ConditionFlag.Less, L_above_threshold, true);
                masm.jmpb(L_below_threshold);

                masm.bind(L_loop_avx512);
                masm.vmovdqu64(tmp0, new AMD64Address(src, len, Stride.S8, -56));
                masm.vmovdqu64(new AMD64Address(dst, len, Stride.S8, -56), tmp0);

                masm.bind(L_above_threshold);
                masm.addqAndJcc(len, 8, ConditionFlag.LessEqual, L_loop_avx512, true);
                masm.jmpb(L_32_byte_head);

                masm.bind(L_loop_avx2);
                masm.vmovdqu(tmp0, new AMD64Address(src, len, Stride.S8, -56));
                masm.vmovdqu(new AMD64Address(dst, len, Stride.S8, -56), tmp0);
                masm.vmovdqu(tmp1, new AMD64Address(src, len, Stride.S8, -24));
                masm.vmovdqu(new AMD64Address(dst, len, Stride.S8, -24), tmp1);

                masm.bind(L_below_threshold);
                masm.addqAndJcc(len, 8, ConditionFlag.LessEqual, L_loop_avx2, true);

                masm.bind(L_32_byte_head);
                masm.subqAndJcc(len, 4, ConditionFlag.Greater, L_end, true);
            } else {
                masm.bind(L_loop);
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
                masm.bind(L_copy_bytes);
                masm.addqAndJcc(len, 8, ConditionFlag.LessEqual, L_loop, true);
                masm.subqAndJcc(len, 4, ConditionFlag.Greater, L_end, true);
            }
            // Copy trailing 32 bytes
            if (supportsAVX2AndYMM()) {
                masm.vmovdqu(tmp0, new AMD64Address(src, len, Stride.S8, -24));
                masm.vmovdqu(new AMD64Address(dst, len, Stride.S8, -24), tmp0);
            } else { // AVX1 and XMM
                masm.movdqu(tmp0, new AMD64Address(src, len, Stride.S8, -24));
                masm.movdqu(new AMD64Address(dst, len, Stride.S8, -24), tmp0);
                masm.movdqu(tmp1, new AMD64Address(src, len, Stride.S8,  -8));
                masm.movdqu(new AMD64Address(dst, len, Stride.S8,  -8), tmp1);
            }
            masm.addq(len, 4);
            masm.bind(L_end);
        } else {
            // Copy 32-byte chunks
            masm.bind(L_loop);
            masm.movq(tmp, new AMD64Address(src, len, Stride.S8, -24));
            masm.movq(new AMD64Address(dst, len, Stride.S8, -24), tmp);
            masm.movq(tmp, new AMD64Address(src, len, Stride.S8, -16));
            masm.movq(new AMD64Address(dst, len, Stride.S8, -16), tmp);
            masm.movq(tmp, new AMD64Address(src, len, Stride.S8,  -8));
            masm.movq(new AMD64Address(dst, len, Stride.S8,  -8), tmp);
            masm.movq(tmp, new AMD64Address(src, len, Stride.S8,  -0));
            masm.movq(new AMD64Address(dst, len, Stride.S8,  -0), tmp);

            masm.bind(L_copy_bytes);
            masm.addqAndJcc(len, 4, ConditionFlag.LessEqual, L_loop, true);
        }
        masm.subqAndJcc(len, 4, ConditionFlag.Less, L_copy_8_bytes, false);
    }

    private void emitCopyBackward(AMD64MacroAssembler masm) {
        Label L_copy_bytes = new Label();
        Label L_copy_8_bytes = new Label();
        Label L_exit = new Label();

        Register src = asRegister(rsrc);
        Register dst = asRegister(rdst);
        Register len = asRegister(rlen);
        Register tmp = asRegister(rtmp);

        masm.shrq(len, 3);  // bytes -> qwords
        masm.jmp(L_copy_bytes);

        // Copy trailing qwords
        masm.bind(L_copy_8_bytes);
        masm.movq(tmp, new AMD64Address(src, len, Stride.S8, -8));
        masm.movq(new AMD64Address(dst, len, Stride.S8, -8), tmp);
        masm.decqAndJcc(len, ConditionFlag.NotZero, L_copy_8_bytes, true);
        masm.jmp(L_exit);

        // Copy in multi-bytes chunks
        emitCopyBackward(masm, src, dst, len, tmp, L_copy_bytes, L_copy_8_bytes);
        masm.bind(L_exit);
    }

    private void emitCopyBackward(AMD64MacroAssembler masm, Register src, Register dst, Register len, Register tmp,
                             Label L_copy_bytes, Label L_copy_8_bytes) {
        Label L_loop = new Label();
        masm.align(16);
        if (supports(CPUFeature.AVX)) {
            Register tmp0 = asRegister(vtmp[0]);
            Register tmp1 = asRegister(vtmp[1]);
            Register tmp2 = asRegister(vtmp[2]);
            Register tmp3 = asRegister(vtmp[3]);
            Label L_end = new Label();
            // Copy 64-bytes per iteration
            if (supportsAVX512VLBWAndZMM()) {
                Label L_loop_avx512 = new Label();
                Label L_loop_avx2 = new Label();
                Label L_32_byte_head = new Label();
                Label L_above_threshold = new Label();
                Label L_below_threshold = new Label();

                masm.bind(L_copy_bytes);
                masm.cmpqAndJcc(len, useAVX3Threshold / 8, ConditionFlag.Greater, L_above_threshold, true);
                masm.jmpb(L_below_threshold);

                masm.bind(L_loop_avx512);
                masm.vmovdqu64(tmp0, new AMD64Address(src, len, Stride.S8, 0));
                masm.vmovdqu64(new AMD64Address(dst, len, Stride.S8, 0), tmp0);

                masm.bind(L_above_threshold);
                masm.subqAndJcc(len, 8, ConditionFlag.GreaterEqual, L_loop_avx512, true);
                masm.jmpb(L_32_byte_head);

                masm.bind(L_loop_avx2);
                masm.vmovdqu(tmp0, new AMD64Address(src, len, Stride.S8, 32));
                masm.vmovdqu(new AMD64Address(dst, len, Stride.S8, 32), tmp0);
                masm.vmovdqu(tmp1, new AMD64Address(src, len, Stride.S8, 0));
                masm.vmovdqu(new AMD64Address(dst, len, Stride.S8, 0), tmp1);

                masm.bind(L_below_threshold);
                masm.subqAndJcc(len, 8, ConditionFlag.GreaterEqual, L_loop_avx2, true);

                masm.bind(L_32_byte_head);
                masm.addqAndJcc(len, 4, ConditionFlag.Less, L_end, true);
            } else {
                masm.bind(L_loop);
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
                masm.bind(L_copy_bytes);
                masm.subqAndJcc(len, 8, ConditionFlag.GreaterEqual, L_loop, true);
                masm.addqAndJcc(len, 4, ConditionFlag.Less, L_end, true);
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
            masm.bind(L_end);
        } else {
            // Copy 32-bytes per iteration
            masm.bind(L_loop);
            masm.movq(tmp, new AMD64Address(src, len, Stride.S8, 24));
            masm.movq(new AMD64Address(dst, len, Stride.S8, 24), tmp);
            masm.movq(tmp, new AMD64Address(src, len, Stride.S8, 16));
            masm.movq(new AMD64Address(dst, len, Stride.S8, 16), tmp);
            masm.movq(tmp, new AMD64Address(src, len, Stride.S8, 8));
            masm.movq(new AMD64Address(dst, len, Stride.S8, 8), tmp);
            masm.movq(tmp, new AMD64Address(src, len, Stride.S8, 0));
            masm.movq(new AMD64Address(dst, len, Stride.S8, 0), tmp);

            masm.bind(L_copy_bytes);
            masm.subqAndJcc(len, 4, ConditionFlag.GreaterEqual, L_loop, true);
        }
        masm.addqAndJcc(len, 4, ConditionFlag.Greater, L_copy_8_bytes, false);
    }
}
