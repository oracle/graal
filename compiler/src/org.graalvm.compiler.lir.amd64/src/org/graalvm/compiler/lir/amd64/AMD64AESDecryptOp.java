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
package org.graalvm.compiler.lir.amd64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.amd64.AMD64AESEncryptOp.loadKey;
import static org.graalvm.compiler.lir.amd64.AMD64HotSpotHelper.pointerConstant;
import static org.graalvm.compiler.lir.amd64.AMD64HotSpotHelper.recordExternalAddress;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.StubPort;
import org.graalvm.compiler.lir.asm.ArrayDataPointerConstant;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

// @formatter:off
@StubPort(path      = "src/hotspot/cpu/x86/stubGenerator_x86_64_aes.cpp",
          lineStart = 1001,
          lineEnd   = 1094,
          commit    = "9d76ac8a4453bc51d9dca2ad6c60259cfb2c4203",
          sha1      = "c1bcb7c379a8c694f005688d419cba6238276b8a")
// @formatter:on
public final class AMD64AESDecryptOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64AESDecryptOp> TYPE = LIRInstructionClass.create(AMD64AESDecryptOp.class);

    private final int lengthOffset;

    @Alive({REG}) private Value fromValue;
    @Alive({REG}) private Value toValue;
    @Alive({REG}) private Value keyValue;

    @Temp({REG}) private Value keyLenValue;

    @Temp({REG}) private Value xmmResultValue;
    @Temp({REG}) private Value xmmKeyShufMaskValue;

    @Temp({REG}) private Value xmmTempValue1;
    @Temp({REG}) private Value xmmTempValue2;
    @Temp({REG}) private Value xmmTempValue3;
    @Temp({REG}) private Value xmmTempValue4;

    public AMD64AESDecryptOp(LIRGeneratorTool tool, Value fromValue, Value toValue, Value keyValue, int lengthOffset) {
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

    private ArrayDataPointerConstant keyShuffleMask = pointerConstant(16, new int[]{
            // @formatter:off
            0x00010203, 0x04050607, 0x08090a0b, 0x0c0d0e0f
            // @formatter:on
    });

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
        // On win64 xmm6-xmm15 must be preserved so don't use them.
        Register xmmTemp1 = asRegister(xmmTempValue1);
        Register xmmTemp2 = asRegister(xmmTempValue2);
        Register xmmTemp3 = asRegister(xmmTempValue3);
        Register xmmTemp4 = asRegister(xmmTempValue4);

        // keylen could be only {11, 13, 15} * 4 = {44, 52, 60}
        masm.movl(keylen, new AMD64Address(key, lengthOffset));

        masm.movdqu(xmmKeyShufMask, recordExternalAddress(crb, keyShuffleMask));
        masm.movdqu(xmmResult, new AMD64Address(from, 0));

        // for decryption java expanded key ordering is rotated one position from what we want
        // so we start from 0x10 here and hit 0x00 last
        // we don't know if the key is aligned, hence not using load-execute form
        loadKey(masm, xmmTemp1, key, 0x10, xmmKeyShufMask);
        loadKey(masm, xmmTemp2, key, 0x20, xmmKeyShufMask);
        loadKey(masm, xmmTemp3, key, 0x30, xmmKeyShufMask);
        loadKey(masm, xmmTemp4, key, 0x40, xmmKeyShufMask);

        masm.pxor(xmmResult, xmmTemp1);
        masm.aesdec(xmmResult, xmmTemp2);
        masm.aesdec(xmmResult, xmmTemp3);
        masm.aesdec(xmmResult, xmmTemp4);

        loadKey(masm, xmmTemp1, key, 0x50, xmmKeyShufMask);
        loadKey(masm, xmmTemp2, key, 0x60, xmmKeyShufMask);
        loadKey(masm, xmmTemp3, key, 0x70, xmmKeyShufMask);
        loadKey(masm, xmmTemp4, key, 0x80, xmmKeyShufMask);

        masm.aesdec(xmmResult, xmmTemp1);
        masm.aesdec(xmmResult, xmmTemp2);
        masm.aesdec(xmmResult, xmmTemp3);
        masm.aesdec(xmmResult, xmmTemp4);

        loadKey(masm, xmmTemp1, key, 0x90, xmmKeyShufMask);
        loadKey(masm, xmmTemp2, key, 0xa0, xmmKeyShufMask);
        loadKey(masm, xmmTemp3, key, 0x00, xmmKeyShufMask);

        masm.cmplAndJcc(keylen, 44, ConditionFlag.Equal, labelDoLast, true);

        masm.aesdec(xmmResult, xmmTemp1);
        masm.aesdec(xmmResult, xmmTemp2);

        loadKey(masm, xmmTemp1, key, 0xb0, xmmKeyShufMask);
        loadKey(masm, xmmTemp2, key, 0xc0, xmmKeyShufMask);

        masm.cmplAndJcc(keylen, 52, ConditionFlag.Equal, labelDoLast, true);

        masm.aesdec(xmmResult, xmmTemp1);
        masm.aesdec(xmmResult, xmmTemp2);

        loadKey(masm, xmmTemp1, key, 0xd0, xmmKeyShufMask);
        loadKey(masm, xmmTemp2, key, 0xe0, xmmKeyShufMask);

        masm.bind(labelDoLast);
        masm.aesdec(xmmResult, xmmTemp1);
        masm.aesdec(xmmResult, xmmTemp2);

        // for decryption the aesdeclast operation is always on key+0x00
        masm.aesdeclast(xmmResult, xmmTemp3);
        masm.movdqu(new AMD64Address(to), xmmResult);  // store the result
    }
}
