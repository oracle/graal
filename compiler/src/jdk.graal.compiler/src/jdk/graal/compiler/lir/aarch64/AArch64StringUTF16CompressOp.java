/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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
package jdk.graal.compiler.lir.aarch64;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/aaaa86b57172d45d1126c50efc270c6e49aba7a5/src/hotspot/cpu/aarch64/macroAssembler_aarch64.cpp#L6305-L6315",
          sha1 = "857dc6f9a492da6c8e20afb2139ae393efd228ac")
// @formatter:on
@Opcode("AArch64_STRING_COMPRESS")
public final class AArch64StringUTF16CompressOp extends AArch64ComplexVectorOp {
    public static final LIRInstructionClass<AArch64StringUTF16CompressOp> TYPE = LIRInstructionClass.create(AArch64StringUTF16CompressOp.class);

    @Def({REG}) protected AllocatableValue resultValue;
    @Alive({REG}) protected AllocatableValue lenValue;
    @Alive({REG}) protected AllocatableValue srcValue;
    @Alive({REG}) protected AllocatableValue dstValue;
    @Temp({REG}) protected AllocatableValue[] temp;
    @Temp({REG}) protected Value[] vectorTemp;

    public AArch64StringUTF16CompressOp(LIRGeneratorTool tool, AllocatableValue src, AllocatableValue dst, AllocatableValue len, AllocatableValue result) {
        super(TYPE);
        GraalError.guarantee(result.getPlatformKind().equals(AArch64Kind.DWORD), "int value expected");
        GraalError.guarantee(len.getPlatformKind().equals(AArch64Kind.DWORD), "int value expected");
        GraalError.guarantee(src.getPlatformKind().equals(AArch64Kind.QWORD), "pointer value expected");
        GraalError.guarantee(dst.getPlatformKind().equals(AArch64Kind.QWORD), "pointer value expected");

        this.lenValue = len;
        this.srcValue = src;
        this.dstValue = dst;
        resultValue = result;
        temp = allocateTempRegisters(tool, 3);
        vectorTemp = AArch64EncodeArrayOp.allocateVectorRegisters(tool, LIRGeneratorTool.CharsetName.ISO_8859_1);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register src = asRegister(temp[0]);
        Register dst = asRegister(temp[1]);
        Register len = asRegister(temp[2]);
        Register res = asRegister(resultValue);

        masm.mov(64, src, asRegister(srcValue));
        masm.mov(64, dst, asRegister(dstValue));
        masm.mov(32, len, asRegister(lenValue));

        AArch64EncodeArrayOp.emitEncodeArrayOp(masm, res, src, dst, len, vectorTemp, LIRGeneratorTool.CharsetName.ISO_8859_1);
        if (JavaVersionUtil.JAVA_SPEC < 22) {
            // legacy behavior: if (result != length) { result = 0; }
            masm.cmp(32, res, asRegister(lenValue));
            masm.csel(32, res, res, zr, AArch64Assembler.ConditionFlag.EQ);
        }
    }
}
