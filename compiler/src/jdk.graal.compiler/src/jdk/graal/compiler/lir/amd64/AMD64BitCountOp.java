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
package jdk.graal.compiler.lir.amd64;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

/**
 * Population count of an integer or long value.
 */
public final class AMD64BitCountOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64BitCountOp> TYPE = LIRInstructionClass.create(AMD64BitCountOp.class);

    @Def protected Value dstValue;
    @Alive protected Value srcValue;

    @Temp protected Value rtmpValue;
    @Temp({REG, ILLEGAL}) protected Value rtmp1Value;

    public AMD64BitCountOp(LIRGeneratorTool tool, Value dstValue, Value srcValue) {
        super(TYPE);

        this.dstValue = dstValue;
        this.srcValue = srcValue;

        this.rtmpValue = tool.newVariable(srcValue.getValueKind());

        if (srcValue.getPlatformKind() == AMD64Kind.DWORD) {
            this.rtmp1Value = Value.ILLEGAL;
        } else {
            this.rtmp1Value = tool.newVariable(srcValue.getValueKind());
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register dst = asRegister(dstValue);
        Register src = asRegister(srcValue);
        Register rtmp = asRegister(rtmpValue);

        switch ((AMD64Kind) srcValue.getPlatformKind()) {
            case DWORD:
                masm.movl(dst, src);
                masm.movl(rtmp, src);
                masm.shrl(rtmp, 1);
                masm.andl(rtmp, 0x55555555);
                masm.subl(dst, rtmp);
                masm.movl(rtmp, dst);
                masm.andl(rtmp, 0x33333333);
                masm.shrl(dst, 2);
                masm.andl(dst, 0x33333333);
                masm.addl(rtmp, dst);
                masm.movl(dst, rtmp);
                masm.shrl(dst, 4);
                masm.addl(dst, rtmp);
                masm.andl(dst, 0xf0f0f0f);
                masm.movl(rtmp, dst);
                masm.shrl(rtmp, 8);
                masm.addl(rtmp, dst);
                masm.movl(dst, rtmp);
                masm.shrl(dst, 16);
                masm.addl(dst, rtmp);
                masm.andl(dst, 0x3f);
                break;
            default:
                Register longImm = asRegister(rtmp1Value);

                masm.movq(dst, src);
                masm.movq(rtmp, src);
                masm.shrq(dst, 1);
                masm.movq(longImm, 0x5555555555555555L);
                masm.andq(dst, longImm);
                masm.subq(rtmp, dst);
                masm.movq(dst, rtmp);
                masm.movq(longImm, 0x3333333333333333L);
                masm.andq(dst, longImm);
                masm.shrq(rtmp, 2);
                masm.andq(rtmp, longImm);
                masm.addq(dst, rtmp);
                masm.movq(rtmp, dst);
                masm.shrq(rtmp, 4);
                masm.addq(rtmp, dst);
                masm.movq(longImm, 0xf0f0f0f0f0f0f0fL);
                masm.andq(rtmp, longImm);
                masm.movq(dst, rtmp);
                masm.shrq(dst, 8);
                masm.addq(dst, rtmp);
                masm.movq(rtmp, dst);
                masm.shrq(rtmp, 16);
                masm.addq(rtmp, dst);
                masm.movq(dst, rtmp);
                masm.shrq(dst, 32);
                masm.addq(dst, rtmp);
                masm.andl(dst, 0x7f);
        }
    }
}
