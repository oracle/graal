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

import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbx;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.BelowEqual;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/be2b92bd8b43841cc2b9c22ed4fde29be30d47bb/src/hotspot/cpu/x86/stubGenerator_x86_64.cpp#L1374-L1410",
          sha1 = "acf2eea69d799b0a1a38edaff048ff30f5257016")
@SyncPort(from = "https://github.com/openjdk/jdk/blob/0487aa61c67de695d008af4fe75c2a3072261a6f/src/hotspot/cpu/x86/macroAssembler_x86_md5.cpp#L52-L209",
          sha1 = "5cb0a6acf3329957f7f5868a32d4d228f98595ab")
// @formatter:on
public final class AMD64MD5Op extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64MD5Op> TYPE = LIRInstructionClass.create(AMD64MD5Op.class);

    @Alive({OperandFlag.REG}) private Value bufValue;
    @Alive({OperandFlag.REG}) private Value stateValue;
    @Alive({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value ofsValue;
    @Alive({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value limitValue;

    @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value bufTempValue;
    @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value ofsTempValue;
    @Temp({OperandFlag.REG}) private Value[] temps;
    private final boolean multiBlock;

    public AMD64MD5Op(LIRGeneratorTool tool, AllocatableValue bufValue, AllocatableValue stateValue) {
        this(tool, bufValue, stateValue, Value.ILLEGAL, Value.ILLEGAL, false);
    }

    public AMD64MD5Op(LIRGeneratorTool tool, AllocatableValue bufValue, AllocatableValue stateValue, AllocatableValue ofsValue,
                    AllocatableValue limitValue, boolean multiBlock) {
        super(TYPE);

        this.bufValue = bufValue;
        this.stateValue = stateValue;
        this.ofsValue = ofsValue;
        this.limitValue = limitValue;

        this.multiBlock = multiBlock;

        this.temps = new Value[]{
                        rax.asValue(),
                        rbx.asValue(),
                        rcx.asValue(),
                        rdi.asValue(),
                        rdx.asValue(),
                        rsi.asValue(),
        };

        if (multiBlock) {
            this.bufTempValue = tool.newVariable(bufValue.getValueKind());
            this.ofsTempValue = tool.newVariable(ofsValue.getValueKind());
        } else {
            this.bufTempValue = Value.ILLEGAL;
            this.ofsTempValue = Value.ILLEGAL;
        }
    }

    private static void md5FF(AMD64MacroAssembler masm, Register buf, Register r1, Register r2, Register r3, Register r4, int k, int s, int t) {
        masm.addl(r1, t);
        masm.movl(rsi, r3);
        masm.addl(r1, new AMD64Address(buf, k * 4));
        masm.xorl(rsi, r4);
        masm.andl(rsi, r2);
        masm.xorl(rsi, r4);
        masm.addl(r1, rsi);
        masm.roll(r1, s);
        masm.addl(r1, r2);
    }

    private static void md5GG(AMD64MacroAssembler masm, Register buf, Register r1, Register r2, Register r3, Register r4, int k, int s, int t) {
        masm.addl(r1, t);
        masm.movl(rsi, r4);
        masm.movl(rdi, r4);
        masm.addl(r1, new AMD64Address(buf, k * 4));
        masm.notl(rsi);
        masm.andl(rdi, r2);
        masm.andl(rsi, r3);
        masm.orl(rsi, rdi);
        masm.addl(r1, rsi);
        masm.roll(r1, s);
        masm.addl(r1, r2);
    }

    private static void md5HH(AMD64MacroAssembler masm, Register buf, Register r1, Register r2, Register r3, Register r4, int k, int s, int t) {
        masm.addl(r1, t);
        masm.movl(rsi, r3);
        masm.addl(r1, new AMD64Address(buf, k * 4));
        masm.xorl(rsi, r4);
        masm.xorl(rsi, r2);
        masm.addl(r1, rsi);
        masm.roll(r1, s);
        masm.addl(r1, r2);
    }

    private static void md5II(AMD64MacroAssembler masm, Register buf, Register r1, Register r2, Register r3, Register r4, int k, int s, int t) {
        masm.addl(r1, t);
        masm.movl(rsi, r4);
        masm.notl(rsi);
        masm.addl(r1, new AMD64Address(buf, k * 4));
        masm.orl(rsi, r2);
        masm.xorl(rsi, r3);
        masm.addl(r1, rsi);
        masm.roll(r1, s);
        masm.addl(r1, r2);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        GraalError.guarantee(bufValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid bufValue kind: %s", bufValue);
        GraalError.guarantee(stateValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid stateValue kind: %s", stateValue);

        Register buf;
        Register state = asRegister(stateValue);
        Register ofs;
        Register limit;

        if (multiBlock) {
            GraalError.guarantee(ofsValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid ofsValue kind: %s", ofsValue);
            GraalError.guarantee(limitValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid limitValue kind: %s", limitValue);

            buf = asRegister(bufTempValue);
            ofs = asRegister(ofsTempValue);
            limit = asRegister(limitValue);

            masm.movq(buf, asRegister(bufValue));
            masm.movl(ofs, asRegister(ofsValue));
        } else {
            buf = asRegister(bufValue);
            ofs = Register.None;
            limit = Register.None;
        }

        Label loop0 = new Label();

        masm.movq(rdi, state);
        masm.movl(rax, new AMD64Address(rdi, 0));
        masm.movl(rbx, new AMD64Address(rdi, 4));
        masm.movl(rcx, new AMD64Address(rdi, 8));
        masm.movl(rdx, new AMD64Address(rdi, 12));

        masm.bind(loop0);

        // Round 1
        md5FF(masm, buf, rax, rbx, rcx, rdx, 0, 7, 0xd76aa478);
        md5FF(masm, buf, rdx, rax, rbx, rcx, 1, 12, 0xe8c7b756);
        md5FF(masm, buf, rcx, rdx, rax, rbx, 2, 17, 0x242070db);
        md5FF(masm, buf, rbx, rcx, rdx, rax, 3, 22, 0xc1bdceee);
        md5FF(masm, buf, rax, rbx, rcx, rdx, 4, 7, 0xf57c0faf);
        md5FF(masm, buf, rdx, rax, rbx, rcx, 5, 12, 0x4787c62a);
        md5FF(masm, buf, rcx, rdx, rax, rbx, 6, 17, 0xa8304613);
        md5FF(masm, buf, rbx, rcx, rdx, rax, 7, 22, 0xfd469501);
        md5FF(masm, buf, rax, rbx, rcx, rdx, 8, 7, 0x698098d8);
        md5FF(masm, buf, rdx, rax, rbx, rcx, 9, 12, 0x8b44f7af);
        md5FF(masm, buf, rcx, rdx, rax, rbx, 10, 17, 0xffff5bb1);
        md5FF(masm, buf, rbx, rcx, rdx, rax, 11, 22, 0x895cd7be);
        md5FF(masm, buf, rax, rbx, rcx, rdx, 12, 7, 0x6b901122);
        md5FF(masm, buf, rdx, rax, rbx, rcx, 13, 12, 0xfd987193);
        md5FF(masm, buf, rcx, rdx, rax, rbx, 14, 17, 0xa679438e);
        md5FF(masm, buf, rbx, rcx, rdx, rax, 15, 22, 0x49b40821);

        // Round 2
        md5GG(masm, buf, rax, rbx, rcx, rdx, 1, 5, 0xf61e2562);
        md5GG(masm, buf, rdx, rax, rbx, rcx, 6, 9, 0xc040b340);
        md5GG(masm, buf, rcx, rdx, rax, rbx, 11, 14, 0x265e5a51);
        md5GG(masm, buf, rbx, rcx, rdx, rax, 0, 20, 0xe9b6c7aa);
        md5GG(masm, buf, rax, rbx, rcx, rdx, 5, 5, 0xd62f105d);
        md5GG(masm, buf, rdx, rax, rbx, rcx, 10, 9, 0x02441453);
        md5GG(masm, buf, rcx, rdx, rax, rbx, 15, 14, 0xd8a1e681);
        md5GG(masm, buf, rbx, rcx, rdx, rax, 4, 20, 0xe7d3fbc8);
        md5GG(masm, buf, rax, rbx, rcx, rdx, 9, 5, 0x21e1cde6);
        md5GG(masm, buf, rdx, rax, rbx, rcx, 14, 9, 0xc33707d6);
        md5GG(masm, buf, rcx, rdx, rax, rbx, 3, 14, 0xf4d50d87);
        md5GG(masm, buf, rbx, rcx, rdx, rax, 8, 20, 0x455a14ed);
        md5GG(masm, buf, rax, rbx, rcx, rdx, 13, 5, 0xa9e3e905);
        md5GG(masm, buf, rdx, rax, rbx, rcx, 2, 9, 0xfcefa3f8);
        md5GG(masm, buf, rcx, rdx, rax, rbx, 7, 14, 0x676f02d9);
        md5GG(masm, buf, rbx, rcx, rdx, rax, 12, 20, 0x8d2a4c8a);

        // Round 3
        md5HH(masm, buf, rax, rbx, rcx, rdx, 5, 4, 0xfffa3942);
        md5HH(masm, buf, rdx, rax, rbx, rcx, 8, 11, 0x8771f681);
        md5HH(masm, buf, rcx, rdx, rax, rbx, 11, 16, 0x6d9d6122);
        md5HH(masm, buf, rbx, rcx, rdx, rax, 14, 23, 0xfde5380c);
        md5HH(masm, buf, rax, rbx, rcx, rdx, 1, 4, 0xa4beea44);
        md5HH(masm, buf, rdx, rax, rbx, rcx, 4, 11, 0x4bdecfa9);
        md5HH(masm, buf, rcx, rdx, rax, rbx, 7, 16, 0xf6bb4b60);
        md5HH(masm, buf, rbx, rcx, rdx, rax, 10, 23, 0xbebfbc70);
        md5HH(masm, buf, rax, rbx, rcx, rdx, 13, 4, 0x289b7ec6);
        md5HH(masm, buf, rdx, rax, rbx, rcx, 0, 11, 0xeaa127fa);
        md5HH(masm, buf, rcx, rdx, rax, rbx, 3, 16, 0xd4ef3085);
        md5HH(masm, buf, rbx, rcx, rdx, rax, 6, 23, 0x04881d05);
        md5HH(masm, buf, rax, rbx, rcx, rdx, 9, 4, 0xd9d4d039);
        md5HH(masm, buf, rdx, rax, rbx, rcx, 12, 11, 0xe6db99e5);
        md5HH(masm, buf, rcx, rdx, rax, rbx, 15, 16, 0x1fa27cf8);
        md5HH(masm, buf, rbx, rcx, rdx, rax, 2, 23, 0xc4ac5665);

        // Round 4
        md5II(masm, buf, rax, rbx, rcx, rdx, 0, 6, 0xf4292244);
        md5II(masm, buf, rdx, rax, rbx, rcx, 7, 10, 0x432aff97);
        md5II(masm, buf, rcx, rdx, rax, rbx, 14, 15, 0xab9423a7);
        md5II(masm, buf, rbx, rcx, rdx, rax, 5, 21, 0xfc93a039);
        md5II(masm, buf, rax, rbx, rcx, rdx, 12, 6, 0x655b59c3);
        md5II(masm, buf, rdx, rax, rbx, rcx, 3, 10, 0x8f0ccc92);
        md5II(masm, buf, rcx, rdx, rax, rbx, 10, 15, 0xffeff47d);
        md5II(masm, buf, rbx, rcx, rdx, rax, 1, 21, 0x85845dd1);
        md5II(masm, buf, rax, rbx, rcx, rdx, 8, 6, 0x6fa87e4f);
        md5II(masm, buf, rdx, rax, rbx, rcx, 15, 10, 0xfe2ce6e0);
        md5II(masm, buf, rcx, rdx, rax, rbx, 6, 15, 0xa3014314);
        md5II(masm, buf, rbx, rcx, rdx, rax, 13, 21, 0x4e0811a1);
        md5II(masm, buf, rax, rbx, rcx, rdx, 4, 6, 0xf7537e82);
        md5II(masm, buf, rdx, rax, rbx, rcx, 11, 10, 0xbd3af235);
        md5II(masm, buf, rcx, rdx, rax, rbx, 2, 15, 0x2ad7d2bb);
        md5II(masm, buf, rbx, rcx, rdx, rax, 9, 21, 0xeb86d391);

        masm.movq(rdi, state);
        masm.addl(rax, new AMD64Address(rdi, 0));
        masm.movl(new AMD64Address(rdi, 0), rax);
        masm.addl(rbx, new AMD64Address(rdi, 4));
        masm.movl(new AMD64Address(rdi, 4), rbx);
        masm.addl(rcx, new AMD64Address(rdi, 8));
        masm.movl(new AMD64Address(rdi, 8), rcx);
        masm.addl(rdx, new AMD64Address(rdi, 12));
        masm.movl(new AMD64Address(rdi, 12), rdx);

        if (multiBlock) {
            // increment data pointer and loop if more to process
            masm.addq(buf, 64);
            masm.addl(ofs, 64);
            masm.movl(rsi, ofs);
            masm.cmpl(rsi, limit);
            masm.jcc(BelowEqual, loop0);
            masm.movl(rax, rsi); // return ofs
        }
    }
}
