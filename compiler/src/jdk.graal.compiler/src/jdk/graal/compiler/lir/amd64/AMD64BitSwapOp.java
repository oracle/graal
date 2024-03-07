/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.amd64;

import static jdk.vm.ci.amd64.AMD64.CPUFeature.GFNI;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/be2b92bd8b43841cc2b9c22ed4fde29be30d47bb/src/hotspot/cpu/x86/c2_MacroAssembler_x86.cpp#L6275-L6357",
          sha1 = "34c6e1ee7916fc7190cbcbc237eaf2b510f7dd0e")
// @formatter:on
public final class AMD64BitSwapOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64BitSwapOp> TYPE = LIRInstructionClass.create(AMD64BitSwapOp.class);

    @Def({LIRInstruction.OperandFlag.REG}) protected Value dstValue;
    @Alive({LIRInstruction.OperandFlag.REG}) protected Value srcValue;

    @Temp({LIRInstruction.OperandFlag.REG}) protected Value rtmpValue;
    @Temp({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.ILLEGAL}) protected Value rtmp2Value;

    @Temp({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.ILLEGAL}) protected Value xtmp1Value;
    @Temp({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.ILLEGAL}) protected Value xtmp2Value;

    public AMD64BitSwapOp(LIRGeneratorTool tool, Value dstValue, Value srcValue) {
        super(TYPE);
        this.dstValue = dstValue;
        this.srcValue = srcValue;

        this.rtmpValue = tool.newVariable(dstValue.getValueKind());

        if (tool.target().arch.getFeatures().contains(GFNI)) {
            this.rtmp2Value = Value.ILLEGAL;

            LIRKind lirKind = LIRKind.value(AMD64Kind.DOUBLE);
            this.xtmp1Value = tool.newVariable(lirKind);
            this.xtmp2Value = tool.newVariable(lirKind);
        } else {
            if (dstValue.getPlatformKind() == AMD64Kind.QWORD) {
                this.rtmp2Value = tool.newVariable(dstValue.getValueKind());
            } else {
                this.rtmp2Value = Value.ILLEGAL;
            }

            this.xtmp1Value = Value.ILLEGAL;
            this.xtmp2Value = Value.ILLEGAL;
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register dst = asRegister(dstValue);
        Register src = asRegister(srcValue);
        Register rtmp = asRegister(rtmpValue);

        switch ((AMD64Kind) dstValue.getPlatformKind()) {
            case DWORD:
                if (masm.supports(GFNI)) {
                    // Galois field instruction based bit reversal based on following algorithm.
                    // http://0x80.pl/articles/avx512-galois-field-for-bit-shuffling.html
                    Register xtmp1 = asRegister(xtmp1Value);
                    Register xtmp2 = asRegister(xtmp2Value);

                    masm.movq(rtmp, 0x8040201008040201L);
                    masm.movdq(xtmp1, src);
                    masm.movdq(xtmp2, rtmp);

                    masm.gf2p8affineqb(xtmp1, xtmp2, 0);
                    masm.movdq(dst, xtmp1);
                } else {
                    // Swap even and odd numbered bits.
                    masm.movl(rtmp, src);
                    masm.andl(rtmp, 0x55555555);
                    masm.shll(rtmp, 1);
                    masm.movl(dst, src);
                    masm.andl(dst, 0xAAAAAAAA);
                    masm.shrl(dst, 1);
                    masm.orl(dst, rtmp);

                    // Swap LSB and MSB 2 bits of each nibble.
                    masm.movl(rtmp, dst);
                    masm.andl(rtmp, 0x33333333);
                    masm.shll(rtmp, 2);
                    masm.andl(dst, 0xCCCCCCCC);
                    masm.shrl(dst, 2);
                    masm.orl(dst, rtmp);

                    // Swap LSB and MSB 4 bits of each byte.
                    masm.movl(rtmp, dst);
                    masm.andl(rtmp, 0x0F0F0F0F);
                    masm.shll(rtmp, 4);
                    masm.andl(dst, 0xF0F0F0F0);
                    masm.shrl(dst, 4);
                    masm.orl(dst, rtmp);
                }
                masm.bswapl(dst);
                break;
            case QWORD:
                if (masm.supports(GFNI)) {
                    // Galois field instruction based bit reversal based on following algorithm.
                    // http://0x80.pl/articles/avx512-galois-field-for-bit-shuffling.html
                    Register xtmp1 = asRegister(xtmp1Value);
                    Register xtmp2 = asRegister(xtmp2Value);

                    masm.movq(rtmp, 0x8040201008040201L);
                    masm.movdq(xtmp1, src);
                    masm.movdq(xtmp2, rtmp);

                    masm.gf2p8affineqb(xtmp1, xtmp2, 0);
                    masm.movdq(dst, xtmp1);
                } else {
                    Register rtmp1 = rtmp;
                    Register rtmp2 = asRegister(rtmp2Value);

                    // Swap even and odd numbered bits.
                    masm.movq(rtmp1, src);
                    masm.movq(rtmp2, 0x5555555555555555L);
                    masm.andq(rtmp1, rtmp2);
                    masm.shlq(rtmp1, 1);
                    masm.movq(dst, src);
                    masm.notq(rtmp2);
                    masm.andq(dst, rtmp2);
                    masm.shrq(dst, 1);
                    masm.orq(dst, rtmp1);

                    // Swap LSB and MSB 2 bits of each nibble.
                    masm.movq(rtmp1, dst);
                    masm.movq(rtmp2, 0x3333333333333333L);
                    masm.andq(rtmp1, rtmp2);
                    masm.shlq(rtmp1, 2);
                    masm.notq(rtmp2);
                    masm.andq(dst, rtmp2);
                    masm.shrq(dst, 2);
                    masm.orq(dst, rtmp1);

                    // Swap LSB and MSB 4 bits of each byte.
                    masm.movq(rtmp1, dst);
                    masm.movq(rtmp2, 0x0F0F0F0F0F0F0F0FL);
                    masm.andq(rtmp1, rtmp2);
                    masm.shlq(rtmp1, 4);
                    masm.notq(rtmp2);
                    masm.andq(dst, rtmp2);
                    masm.shrq(dst, 4);
                    masm.orq(dst, rtmp1);
                }
                masm.bswapq(dst);
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(dstValue.getPlatformKind());
        }
    }
}
