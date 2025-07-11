/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/b1fa1ecc988fb07f191892a459625c2c8f2de3b5/src/hotspot/cpu/x86/stubGenerator_x86_64_aes.cpp#L1061-L1154",
          sha1 = "5f90f9e46352c45f8777b555fcb42f33cfa51619")
// @formatter:on
public final class AMD64AESEncryptOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64AESEncryptOp> TYPE = LIRInstructionClass.create(AMD64AESEncryptOp.class);

    public static final int AES_BLOCK_SIZE = 16;

    private final int lengthOffset;

    @Alive({OperandFlag.REG}) private Value fromValue;
    @Alive({OperandFlag.REG}) private Value toValue;
    @Alive({OperandFlag.REG}) private Value keyValue;

    @Temp({OperandFlag.REG}) private Value keyLenValue;

    @Temp({OperandFlag.REG}) private Value xmmResultValue;
    @Temp({OperandFlag.REG}) private Value xmmKeyShufMaskValue;

    @Temp({OperandFlag.REG}) private Value xmmTempValue1;
    @Temp({OperandFlag.REG}) private Value xmmTempValue2;
    @Temp({OperandFlag.REG}) private Value xmmTempValue3;
    @Temp({OperandFlag.REG}) private Value xmmTempValue4;

    public AMD64AESEncryptOp(LIRGeneratorTool tool, AllocatableValue fromValue, AllocatableValue toValue, AllocatableValue keyValue, int lengthOffset) {
        super(TYPE);
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.keyValue = keyValue;

        this.lengthOffset = lengthOffset;
        this.keyLenValue = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));

        LIRKind lirKind = LIRKind.value(AMD64Kind.V128_BYTE);
        this.xmmResultValue = tool.newVariable(lirKind);
        this.xmmKeyShufMaskValue = tool.newVariable(lirKind);
        this.xmmTempValue1 = tool.newVariable(lirKind);
        this.xmmTempValue2 = tool.newVariable(lirKind);
        this.xmmTempValue3 = tool.newVariable(lirKind);
        this.xmmTempValue4 = tool.newVariable(lirKind);
    }

    static ArrayDataPointerConstant keyShuffleMask = pointerConstant(16, new int[]{
            // @formatter:off
            0x00010203, 0x04050607, 0x08090a0b, 0x0c0d0e0f
            // @formatter:on
    });

    static void loadKey(AMD64MacroAssembler masm, Register xmmDst, Register key, int offset, Register xmmShufMask) {
        masm.movdqu(xmmDst, new AMD64Address(key, offset));
        masm.pshufb(AVXSize.XMM, xmmDst, xmmShufMask);
    }

    static void loadKey(AMD64MacroAssembler masm, Register xmmDst, Register key, int offset, CompilationResultBuilder crb) {
        masm.movdqu(xmmDst, new AMD64Address(key, offset));
        masm.pshufb(AVXSize.XMM, xmmDst, recordExternalAddress(crb, keyShuffleMask));
    }

    static Register asXMMRegister(int index) {
        return AMD64.xmmRegistersAVX512.get(index);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        GraalError.guarantee(fromValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid fromValue kind: %s", fromValue);
        GraalError.guarantee(toValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid toValue kind: %s", toValue);
        GraalError.guarantee(keyValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid keyValue kind: %s", keyValue);

        Label labelDoLast = new Label();

        Register from = asRegister(fromValue); // source array address
        Register to = asRegister(toValue);     // destination array address
        Register key = asRegister(keyValue);   // key array address
        Register keylen = asRegister(keyLenValue);

        Register xmmResult = asRegister(xmmResultValue);
        Register xmmKeyShufMask = asRegister(xmmKeyShufMaskValue);
        Register xmmTemp1 = asRegister(xmmTempValue1);
        Register xmmTemp2 = asRegister(xmmTempValue2);
        Register xmmTemp3 = asRegister(xmmTempValue3);
        Register xmmTemp4 = asRegister(xmmTempValue4);

        // keylen could be only {11, 13, 15} * 4 = {44, 52, 60}
        masm.movl(keylen, new AMD64Address(key, lengthOffset));

        masm.movdqu(xmmKeyShufMask, recordExternalAddress(crb, keyShuffleMask));
        masm.movdqu(xmmResult, new AMD64Address(from));  // get 16 bytes of input

        // For encryption, the java expanded key ordering is just what we need
        // we don't know if the key is aligned, hence not using load-execute form

        loadKey(masm, xmmTemp1, key, 0x00, xmmKeyShufMask);
        masm.pxor(xmmResult, xmmTemp1);

        loadKey(masm, xmmTemp1, key, 0x10, xmmKeyShufMask);
        loadKey(masm, xmmTemp2, key, 0x20, xmmKeyShufMask);
        loadKey(masm, xmmTemp3, key, 0x30, xmmKeyShufMask);
        loadKey(masm, xmmTemp4, key, 0x40, xmmKeyShufMask);

        masm.aesenc(xmmResult, xmmTemp1);
        masm.aesenc(xmmResult, xmmTemp2);
        masm.aesenc(xmmResult, xmmTemp3);
        masm.aesenc(xmmResult, xmmTemp4);

        loadKey(masm, xmmTemp1, key, 0x50, xmmKeyShufMask);
        loadKey(masm, xmmTemp2, key, 0x60, xmmKeyShufMask);
        loadKey(masm, xmmTemp3, key, 0x70, xmmKeyShufMask);
        loadKey(masm, xmmTemp4, key, 0x80, xmmKeyShufMask);

        masm.aesenc(xmmResult, xmmTemp1);
        masm.aesenc(xmmResult, xmmTemp2);
        masm.aesenc(xmmResult, xmmTemp3);
        masm.aesenc(xmmResult, xmmTemp4);

        loadKey(masm, xmmTemp1, key, 0x90, xmmKeyShufMask);
        loadKey(masm, xmmTemp2, key, 0xa0, xmmKeyShufMask);

        masm.cmplAndJcc(keylen, 44, ConditionFlag.Equal, labelDoLast, true);

        masm.aesenc(xmmResult, xmmTemp1);
        masm.aesenc(xmmResult, xmmTemp2);

        loadKey(masm, xmmTemp1, key, 0xb0, xmmKeyShufMask);
        loadKey(masm, xmmTemp2, key, 0xc0, xmmKeyShufMask);

        masm.cmplAndJcc(keylen, 52, ConditionFlag.Equal, labelDoLast, true);

        masm.aesenc(xmmResult, xmmTemp1);
        masm.aesenc(xmmResult, xmmTemp2);

        loadKey(masm, xmmTemp1, key, 0xd0, xmmKeyShufMask);
        loadKey(masm, xmmTemp2, key, 0xe0, xmmKeyShufMask);

        masm.bind(labelDoLast);
        masm.aesenc(xmmResult, xmmTemp1);
        masm.aesenclast(xmmResult, xmmTemp2);
        masm.movdqu(new AMD64Address(to), xmmResult);        // store the result
    }
}
