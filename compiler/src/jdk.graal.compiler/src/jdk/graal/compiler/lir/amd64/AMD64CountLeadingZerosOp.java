/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

public final class AMD64CountLeadingZerosOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64CountLeadingZerosOp> TYPE = LIRInstructionClass.create(AMD64CountLeadingZerosOp.class);

    @Def protected Value dstValue;
    @Alive protected Value srcValue;

    @Temp protected Value rtmpValue;

    public AMD64CountLeadingZerosOp(LIRGeneratorTool tool, Value dstValue, Value srcValue) {
        super(TYPE);

        this.dstValue = dstValue;
        this.srcValue = srcValue;

        this.rtmpValue = tool.newVariable(srcValue.getValueKind());
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register dst = asRegister(dstValue);
        Register src = asRegister(srcValue);
        Register rtmp = asRegister(rtmpValue);

        switch ((AMD64Kind) srcValue.getPlatformKind()) {
            case DWORD:
                masm.bsrl(rtmp, src);
                masm.movl(dst, 0x1f);
                masm.subl(dst, rtmp);
                masm.testl(src, src);
                masm.movl(rtmp, 0x20);
                masm.cmovl(ConditionFlag.Zero, dst, rtmp);
                break;
            default:
                masm.bsrq(rtmp, src);
                masm.movl(dst, 0x3f);
                masm.subl(dst, rtmp);
                masm.testq(src, src);
                masm.movl(rtmp, 0x40);
                masm.cmovl(ConditionFlag.Zero, dst, rtmp);
        }
    }
}
