/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.ADC;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.SBB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MIOp.TESTB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64Shift.ROL;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.BYTE;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.QWORD;
import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.amd64.AMD64.r13;
import static jdk.vm.ci.amd64.AMD64.r14;
import static jdk.vm.ci.amd64.AMD64.r15;
import static jdk.vm.ci.amd64.AMD64.r8;
import static jdk.vm.ci.amd64.AMD64.r9;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbp;
import static jdk.vm.ci.amd64.AMD64.rbx;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fe611a2a3386d097f636c15bd4d396a82dc695e/src/hotspot/cpu/x86/sharedRuntime_x86_64.cpp#L3372-L3492",
          sha1 = "834e6cda5a0cf753588301ae48cddb317ee77849")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fe611a2a3386d097f636c15bd4d396a82dc695e/src/hotspot/cpu/x86/sharedRuntime_x86_64.cpp#L3550-L3564",
          sha1 = "8d20d3426e9f53c2c87a2806d8c27ffd09852255")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fe611a2a3386d097f636c15bd4d396a82dc695e/src/hotspot/cpu/x86/sharedRuntime_x86_64.cpp#L3570-L3598",
          sha1 = "f3033ce5d13b7a6deea1c3361aa93eca2aae9255")
// @formatter:on
public final class AMD64BigIntegerMontgomeryMultiplyOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64BigIntegerMontgomeryMultiplyOp> TYPE = LIRInstructionClass.create(AMD64BigIntegerMontgomeryMultiplyOp.class);

    private static final int MAX_LONGWORDS_MUL = 8192 / (8 * 4);

    private static final int MULTIPLY_HELPER_PHASE2_N = -0x48;
    private static final int MULTIPLY_HELPER_LEN2 = -0x40;
    private static final int MULTIPLY_HELPER_LEN = -0x3c;
    private static final int MULTIPLY_HELPER_PHASE2_CARRY = -0x38;
    private static final int MULTIPLY_HELPER_N = -0x30;

    @UseKill({OperandFlag.REG}) private Value aValue;
    @UseKill({OperandFlag.REG}) private Value bValue;
    @UseKill({OperandFlag.REG}) private Value nValue;
    @UseKill({OperandFlag.REG}) private Value lenValue;
    @UseKill({OperandFlag.REG}) private Value invValue;
    @UseKill({OperandFlag.REG}) private Value productValue;

    @Temp({OperandFlag.REG}) private Value[] temps;

    public AMD64BigIntegerMontgomeryMultiplyOp(Value aValue, Value bValue, Value nValue, Value lenValue, Value invValue, Value productValue) {
        super(TYPE);

        GraalError.guarantee(asRegister(aValue).equals(rdi), "expect aValue at rdi, but was %s", aValue);
        GraalError.guarantee(asRegister(bValue).equals(rsi), "expect bValue at rsi, but was %s", bValue);
        GraalError.guarantee(asRegister(nValue).equals(rdx), "expect nValue at rdx, but was %s", nValue);
        GraalError.guarantee(asRegister(lenValue).equals(rcx), "expect lenValue at rcx, but was %s", lenValue);
        GraalError.guarantee(asRegister(invValue).equals(r8), "expect invValue at r8, but was %s", invValue);
        GraalError.guarantee(asRegister(productValue).equals(r9), "expect productValue at r9, but was %s", productValue);

        this.aValue = aValue;
        this.bValue = bValue;
        this.nValue = nValue;
        this.lenValue = lenValue;
        this.invValue = invValue;
        this.productValue = productValue;

        this.temps = AMD64LIRHelper.registersToValues(new Register[]{
                        rax,
                        r10,
                        r11,
                        xmm0,
                        xmm1,
        });
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        GraalError.guarantee(aValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid aValue kind: %s", aValue);
        GraalError.guarantee(bValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid bValue kind: %s", bValue);
        GraalError.guarantee(nValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid nValue kind: %s", nValue);
        GraalError.guarantee(lenValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid lenValue kind: %s", lenValue);
        GraalError.guarantee(invValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid invValue kind: %s", invValue);
        GraalError.guarantee(productValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid productValue kind: %s", productValue);

        masm.movq(r10, asRegister(bValue));
        masm.movq(asRegister(bValue), asRegister(nValue));
        masm.movl(asRegister(nValue), asRegister(lenValue));

        masm.push(rbp);
        masm.movq(rbp, rsp);
        masm.push(r15);
        masm.push(r14);
        masm.push(r13);
        masm.push(r12);
        masm.push(rbx);
        masm.subq(rsp, 0x38);

        Label stubReturn = new Label();
        emitMontgomeryMultiply(masm, stubReturn);

        masm.bind(stubReturn);
    }

    private static void emitWrapperEpilogue(AMD64MacroAssembler masm) {
        masm.leaq(rsp, new AMD64Address(rbp, -0x28));
        masm.pop(rbx);
        masm.pop(r12);
        masm.pop(r13);
        masm.pop(r14);
        masm.pop(r15);
        masm.pop(rbp);
    }

    /**
     * Make very sure we don't use so much space that the stack might overflow. 512 jints
     * corresponds to an 16384-bit integer and will use here a total of 8k bytes of stack space.
     */
    private static void emitMontgomeryMultiply(AMD64MacroAssembler masm, Label stubReturn) {
        Label done = new Label();
        Label callHelperEvenN = new Label();
        Label callHelperNoReverse = new Label();
        Label lenOneReverse = new Label();
        Label reverseA = new Label();
        Label reverseADone = new Label();
        Label reverseB = new Label();
        Label reverseBDone = new Label();
        Label reverseN = new Label();
        Label reverseNOdd = new Label();
        Label reverseNDone = new Label();
        Label callHelperEvenNDone = new Label();
        Label reverseResult = new Label();
        Label reverseResultCommon = new Label();
        Label reverseResultDone = new Label();
        Label reverseResultLenOne = new Label();
        Label tooLarge = new Label();

        masm.movq(new AMD64Address(rbp, -0x58), r8);
        masm.movq(new AMD64Address(rbp, -0x48), r9);

        masm.movl(rbx, rcx);
        masm.shrl(rbx, 0x1f);
        masm.addl(rbx, rcx);
        masm.sarl(rbx, 1);
        masm.cmplAndJcc(rcx, MAX_LONGWORDS_MUL * 2 + 1, ConditionFlag.Greater, tooLarge, false);
        masm.movl(rcx, rbx);
        masm.movq(rax, rdi);
        masm.movslq(r12, rbx);
        masm.shll(rcx, 5);
        masm.shlq(r12, 3);
        masm.movslq(rcx, rcx);
        masm.addq(rcx, 0x10);
        masm.subq(rsp, rcx);
        masm.lead(rcx, new AMD64Address(rbx, rbx, Stride.S1));
        masm.leaq(rdi, new AMD64Address(rsp, 0x0f));
        masm.movslq(r8, rcx);
        masm.addl(rcx, rbx);
        masm.andq(rdi, -16);
        masm.movslq(rcx, rcx);
        masm.leaq(r15, new AMD64Address(Register.None, r8, Stride.S8));
        masm.leaq(r11, new AMD64Address(rdi, r12, Stride.S1));
        masm.shlq(rcx, 3);
        masm.leaq(r14, new AMD64Address(r11, r12, Stride.S1));
        masm.movq(new AMD64Address(rbp, -0x60), rcx);
        masm.leaq(r13, new AMD64Address(r14, r12, Stride.S1));
        masm.cmplAndJcc(rdx, 1, ConditionFlag.LessEqual, callHelperNoReverse, false);
        masm.cmplAndJcc(rbx, 1, ConditionFlag.Equal, lenOneReverse, false);

        masm.leaq(rdx, new AMD64Address(r12, -0x10));
        masm.movq(rcx, rax);
        masm.movq(new AMD64Address(rbp, -0x50), rdx);
        masm.leaq(r8, new AMD64Address(rdi, rdx, Stride.S1));
        masm.movl(rdx, rbx);
        masm.shrl(rdx, 1);
        masm.shlq(rdx, 4);
        masm.leaq(r9, new AMD64Address(rdx, rax, Stride.S1));
        masm.align(32);

        // Keep the reverse_words copies open-coded: each one preserves HotSpot's local register
        // choices and branch layout for libjvm assembly parity.
        masm.bind(reverseA);
        masm.movdqu(xmm1, new AMD64Address(rcx));
        masm.addq(rcx, 16);
        masm.subq(r8, 16);
        masm.movdqa(xmm0, xmm1);
        masm.psllq(xmm1, 32);
        masm.psrlq(xmm0, 32);
        masm.por(xmm0, xmm1);
        masm.shufpd(xmm0, xmm0, 1);
        masm.movups(new AMD64Address(r8, 16), xmm0);
        masm.cmpq(r9, rcx);
        masm.jcc(ConditionFlag.NotEqual, reverseA);
        masm.movl(r8, rbx);
        masm.andl(r8, 0xfffffffe);
        masm.movl(r9, r8);
        masm.leaq(rcx, new AMD64Address(Register.None, r9, Stride.S8));
        masm.negq(rcx);
        masm.cmplAndJcc(rbx, r8, ConditionFlag.Equal, reverseADone, false);
        masm.movq(rax, new AMD64Address(rax, r9, Stride.S8));
        ROL.miOp.emit(masm, QWORD, rax, (byte) 32);
        masm.movq(new AMD64Address(r11, rcx, Stride.S1, -8), rax);

        masm.bind(reverseADone);
        masm.movq(rax, r10);
        masm.leaq(r15, new AMD64Address(rdi, r15, Stride.S1, -0x10));
        masm.addq(rdx, r10);
        masm.align(32);

        masm.bind(reverseB);
        masm.movdqu(xmm1, new AMD64Address(rax));
        masm.addq(rax, 16);
        masm.subq(r15, 16);
        masm.movdqa(xmm0, xmm1);
        masm.psllq(xmm1, 32);
        masm.psrlq(xmm0, 32);
        masm.por(xmm0, xmm1);
        masm.shufpd(xmm0, xmm0, 1);
        masm.movaps(new AMD64Address(r15, 16), xmm0);
        masm.cmpq(rdx, rax);
        masm.jcc(ConditionFlag.NotEqual, reverseB);
        masm.cmplAndJcc(rbx, r8, ConditionFlag.Equal, reverseBDone, false);
        masm.movq(rax, new AMD64Address(r10, r9, Stride.S8));
        ROL.miOp.emit(masm, QWORD, rax, (byte) 32);
        masm.movq(new AMD64Address(r14, rcx, Stride.S1, -8), rax);

        masm.bind(reverseBDone);
        masm.movl(rcx, rbx);
        masm.movq(rdx, new AMD64Address(rbp, -0x60));
        masm.movq(rax, rsi);
        masm.shrl(rcx, 1);
        masm.shlq(rcx, 4);
        masm.leaq(rdx, new AMD64Address(rdi, rdx, Stride.S1, -0x10));
        masm.addq(rcx, rsi);
        masm.align(32);

        masm.bind(reverseN);
        masm.movdqu(xmm1, new AMD64Address(rax));
        masm.addq(rax, 16);
        masm.subq(rdx, 16);
        masm.movdqa(xmm0, xmm1);
        masm.psllq(xmm1, 32);
        masm.psrlq(xmm0, 32);
        masm.por(xmm0, xmm1);
        masm.shufpd(xmm0, xmm0, 1);
        masm.movups(new AMD64Address(rdx, 16), xmm0);
        masm.cmpq(rax, rcx);
        masm.jcc(ConditionFlag.NotEqual, reverseN);
        masm.movl(rax, rbx);
        masm.andl(rax, 0xfffffffe);
        TESTB.emit(masm, BYTE, rbx, 1);
        masm.jcc(ConditionFlag.Equal, callHelperEvenN);
        masm.shlq(rax, 3);
        masm.movq(rdx, r13);
        masm.addq(rsi, rax);
        masm.subq(rdx, rax);

        masm.bind(reverseNOdd);
        masm.movq(rax, new AMD64Address(rsi));
        masm.movq(r8, new AMD64Address(rbp, -0x58));
        masm.movq(rsi, r11);
        masm.movl(r9, rbx);
        masm.movq(rcx, r13);
        ROL.miOp.emit(masm, QWORD, rax, (byte) 32);
        masm.movq(new AMD64Address(rdx, -8), rax);
        masm.movq(rdx, r14);
        // The standalone libjvm helper returns to the following reverseNDone block. The Graal port
        // emits the helper inline and jumps to that continuation explicitly.
        emitMontgomeryMultiplyHelper(masm, reverseNDone);

        masm.bind(reverseNDone);
        masm.movq(rax, new AMD64Address(rbp, -0x48));
        masm.leaq(rsi, new AMD64Address(rax, r12, Stride.S1));
        masm.cmplAndJcc(rbx, 1, ConditionFlag.Equal, reverseResultLenOne, false);

        masm.bind(reverseResult);
        masm.leaq(rax, new AMD64Address(r12, -0x10));
        masm.movq(new AMD64Address(rbp, -0x50), rax);

        masm.bind(reverseResultCommon);
        masm.movl(rcx, rbx);
        masm.movq(rdx, new AMD64Address(rbp, -0x48));
        masm.movq(rdi, new AMD64Address(rbp, -0x50));
        masm.movq(rax, r13);
        masm.shrl(rcx, 1);
        masm.shlq(rcx, 4);
        masm.addq(rdx, rdi);
        masm.addq(rcx, r13);
        masm.align(32);

        masm.bind(reverseResultDone);
        masm.movdqu(xmm1, new AMD64Address(rax));
        masm.addq(rax, 16);
        masm.subq(rdx, 16);
        masm.movdqa(xmm0, xmm1);
        masm.psllq(xmm1, 32);
        masm.psrlq(xmm0, 32);
        masm.por(xmm0, xmm1);
        masm.shufpd(xmm0, xmm0, 1);
        masm.movups(new AMD64Address(rdx, 16), xmm0);
        masm.cmpq(rcx, rax);
        masm.jcc(ConditionFlag.NotEqual, reverseResultDone);
        masm.movl(rax, rbx);
        masm.andl(rax, 0xfffffffe);
        masm.andl(rbx, 1);
        masm.jcc(ConditionFlag.Equal, done);
        masm.shlq(rax, 3);
        masm.addq(r13, rax);
        masm.subq(rsi, rax);

        masm.bind(reverseResultLenOne);
        masm.movq(rax, new AMD64Address(r13));
        ROL.miOp.emit(masm, QWORD, rax, (byte) 32);
        masm.movq(new AMD64Address(rsi, -8), rax);

        masm.bind(done);
        emitWrapperEpilogue(masm);
        masm.jmp(stubReturn);

        masm.align(16);
        masm.bind(callHelperEvenN);
        masm.movq(r8, new AMD64Address(rbp, -0x58));
        masm.movq(rsi, r11);
        masm.movl(r9, rbx);
        masm.movq(rcx, r13);
        masm.movq(rdx, r14);
        // Keep this continuation physically separate from reverseNDone: libjvm has a second
        // standalone-helper return block here, and duplicating it preserves that layout.
        emitMontgomeryMultiplyHelper(masm, callHelperEvenNDone);

        masm.bind(callHelperEvenNDone);
        masm.movq(rax, new AMD64Address(rbp, -0x48));
        masm.leaq(rsi, new AMD64Address(rax, r12, Stride.S1));
        masm.jmp(reverseResultCommon);

        masm.bind(callHelperNoReverse);
        masm.movq(r8, new AMD64Address(rbp, -0x58));
        masm.movl(r9, rbx);
        masm.movq(rcx, r13);
        masm.movq(rdx, r14);
        masm.movq(rsi, r11);
        emitMontgomeryMultiplyHelper(masm, done);

        masm.bind(lenOneReverse);
        masm.movq(rax, new AMD64Address(rax));
        masm.movq(rdx, r13);
        ROL.miOp.emit(masm, QWORD, rax, (byte) 32);
        masm.movq(new AMD64Address(r11, -8), rax);
        masm.movq(rax, new AMD64Address(r10));
        ROL.miOp.emit(masm, QWORD, rax, (byte) 32);
        masm.movq(new AMD64Address(r14, -8), rax);
        masm.jmp(reverseNOdd);

        masm.bind(tooLarge);
        masm.illegal();
    }

    /**
     * Fast Montgomery multiplication. The derivation of the algorithm is in A Cryptographic Library
     * for the Motorola DSP56000, Dusse and Kaliski, Proc. EUROCRYPT 90, pp. 230-237.
     */
    static void emitMontgomeryMultiplyHelper(AMD64MacroAssembler masm, Label continuation) {
        Label entryNext = new Label();
        Label phase1Inner = new Label();
        Label phase2 = new Label();
        Label phase2Outer = new Label();
        Label phase2Inner = new Label();
        Label phase2Store = new Label();
        Label reduce = new Label();
        Label reduceInner = new Label();
        Label done = new Label();
        Label phase2Empty = new Label();
        Label lenZero = new Label();
        Label lenOne = new Label();
        Label phase1Done = new Label();

        masm.push(rbp);
        masm.movq(r10, rdi);
        masm.movq(r11, rcx);
        masm.movq(rbp, rsp);
        masm.push(r15);
        masm.push(r14);
        masm.push(r13);
        masm.movq(r13, rsi);
        masm.push(r12);
        masm.movq(r12, rdx);
        masm.push(rbx);
        masm.lead(rbx, new AMD64Address(r9, r9, Stride.S1));
        masm.movl(new AMD64Address(rbp, MULTIPLY_HELPER_LEN), r9);
        masm.movl(new AMD64Address(rbp, MULTIPLY_HELPER_LEN2), rbx);
        masm.testl(r9, r9);
        masm.jcc(ConditionFlag.LessEqual, lenZero);
        masm.movq(rax, new AMD64Address(rsi));
        masm.movq(r15, r8);
        masm.xorl(rsi, rsi);
        masm.movq(rdi, new AMD64Address(rdi));
        masm.xorl(rcx, rcx);
        masm.movq(r8, rsi);
        masm.mulq(rdi);
        masm.addq(rsi, rax);
        ADC.getRMOpcode(QWORD).emit(masm, QWORD, r8, rdx);
        masm.adcq(rcx, 0);
        masm.movq(rdi, r15);
        masm.imulq(rdi, rsi);
        masm.movq(new AMD64Address(r11), rdi);
        masm.movq(rax, new AMD64Address(r12));
        masm.mulq(rdi);
        masm.addq(rsi, rax);
        ADC.getRMOpcode(QWORD).emit(masm, QWORD, r8, rdx);
        masm.adcq(rcx, 0);
        masm.cmpl(new AMD64Address(rbp, MULTIPLY_HELPER_LEN), 1);
        masm.movq(rdi, r8);
        masm.jcc(ConditionFlag.Equal, lenOne);
        masm.movl(r14, 1);

        masm.bind(entryNext);
        masm.movq(new AMD64Address(rbp, MULTIPLY_HELPER_N), r12);
        masm.leaq(r9, new AMD64Address(Register.None, r14, Stride.S8));
        masm.xorl(r8, r8);
        masm.xorl(rsi, rsi);
        masm.leaq(rbx, new AMD64Address(r13, r9, Stride.S1));
        masm.addq(r9, r12);
        masm.align(16);

        masm.bind(phase1Inner);
        masm.movq(r12, new AMD64Address(r10, r8, Stride.S8));
        masm.movq(rax, new AMD64Address(rbx));
        masm.subq(r9, 8);
        masm.subq(rbx, 8);
        masm.mulq(r12);
        masm.addq(rdi, rax);
        ADC.getRMOpcode(QWORD).emit(masm, QWORD, rcx, rdx);
        masm.adcq(rsi, 0);
        masm.movq(r12, new AMD64Address(r11, r8, Stride.S8));
        masm.addq(r8, 1);
        masm.movq(rax, new AMD64Address(r9, 8));
        masm.mulq(r12);
        masm.addq(rdi, rax);
        ADC.getRMOpcode(QWORD).emit(masm, QWORD, rcx, rdx);
        masm.adcq(rsi, 0);
        masm.cmpl(r8, r14);
        masm.jcc(ConditionFlag.Less, phase1Inner);
        masm.movq(r8, new AMD64Address(r10, r14, Stride.S8));
        masm.movq(rax, new AMD64Address(r13));
        masm.mulq(r8);
        masm.addq(rdi, rax);
        ADC.getRMOpcode(QWORD).emit(masm, QWORD, rcx, rdx);
        masm.adcq(rsi, 0);
        masm.movq(r8, rdi);
        masm.movq(r12, new AMD64Address(rbp, MULTIPLY_HELPER_N));
        masm.imulq(r8, r15);
        masm.movq(new AMD64Address(r11, r14, Stride.S8), r8);
        masm.addq(r14, 1);
        masm.movq(rax, new AMD64Address(r12));
        masm.mulq(r8);
        masm.addq(rdi, rax);
        ADC.getRMOpcode(QWORD).emit(masm, QWORD, rcx, rdx);
        masm.adcq(rsi, 0);
        masm.cmplAndJcc(new AMD64Address(rbp, MULTIPLY_HELPER_LEN), r14, ConditionFlag.Greater, phase1Done, false);
        masm.movl(rbx, new AMD64Address(rbp, MULTIPLY_HELPER_LEN2));
        masm.cmplAndJcc(new AMD64Address(rbp, MULTIPLY_HELPER_LEN), rbx, ConditionFlag.GreaterEqual, reduce, false);

        masm.bind(phase2);
        masm.movslq(rbx, new AMD64Address(rbp, MULTIPLY_HELPER_LEN));
        masm.movq(new AMD64Address(rbp, MULTIPLY_HELPER_PHASE2_N), r12);
        masm.xorl(r14, r14);
        masm.movq(rax, rbx);
        masm.shlq(rbx, 3);
        masm.leaq(r9, new AMD64Address(r13, rbx, Stride.S1));
        masm.lead(r15, new AMD64Address(rax, -1));
        masm.addq(rbx, r12);
        masm.movl(r13, 1);
        masm.align(16);

        masm.bind(phase2Outer);
        masm.cmplAndJcc(new AMD64Address(rbp, MULTIPLY_HELPER_LEN), r13, ConditionFlag.LessEqual, phase2Empty, false);
        masm.movl(rax, r15);
        masm.movl(new AMD64Address(rbp, MULTIPLY_HELPER_N), r13);
        masm.movl(r8, r13);
        masm.movq(rdi, rcx);
        masm.subl(rax, r13);
        masm.movq(new AMD64Address(rbp, MULTIPLY_HELPER_PHASE2_CARRY), r14);
        masm.movq(rcx, rsi);
        masm.shlq(r8, 3);
        masm.leaq(r12, new AMD64Address(r14, rax, Stride.S1, 2));
        masm.xorl(rsi, rsi);
        masm.shlq(r12, 3);

        masm.bind(phase2Inner);
        masm.movq(r13, r8);
        masm.movq(r14, new AMD64Address(r10, r8, Stride.S1));
        masm.negq(r13);
        masm.movq(rax, new AMD64Address(r9, r13, Stride.S1));
        masm.mulq(r14);
        masm.addq(rdi, rax);
        ADC.getRMOpcode(QWORD).emit(masm, QWORD, rcx, rdx);
        masm.adcq(rsi, 0);
        masm.movq(r14, new AMD64Address(r11, r8, Stride.S1));
        masm.addq(r8, 8);
        masm.movq(rax, new AMD64Address(rbx, r13, Stride.S1));
        masm.mulq(r14);
        masm.addq(rdi, rax);
        ADC.getRMOpcode(QWORD).emit(masm, QWORD, rcx, rdx);
        masm.adcq(rsi, 0);
        masm.cmpq(r12, r8);
        masm.jcc(ConditionFlag.NotEqual, phase2Inner);
        masm.movl(r13, new AMD64Address(rbp, MULTIPLY_HELPER_N));
        masm.movq(r14, new AMD64Address(rbp, MULTIPLY_HELPER_PHASE2_CARRY));

        masm.bind(phase2Store);
        masm.addl(r13, 1);
        masm.movq(new AMD64Address(r11, r14, Stride.S8), rdi);
        masm.addq(r9, 8);
        masm.addq(r14, 1);
        masm.addq(rbx, 8);
        masm.lead(rax, new AMD64Address(r13, r15, Stride.S1));
        masm.cmplAndJcc(new AMD64Address(rbp, MULTIPLY_HELPER_LEN2), rax, ConditionFlag.Greater, phase2Outer, false);
        masm.movq(r12, new AMD64Address(rbp, MULTIPLY_HELPER_PHASE2_N));

        masm.bind(reduce);
        masm.testq(rcx, rcx);
        masm.jcc(ConditionFlag.Equal, done);
        masm.movslq(rsi, new AMD64Address(rbp, MULTIPLY_HELPER_LEN));
        masm.xorl(r8, r8);
        masm.align(16);

        masm.bind(reduceInner);
        masm.movq(rax, r8);
        masm.movq(rdx, rsi);
        masm.emitByte(0xF8); // clc
        Label subLoop = new Label();
        masm.bind(subLoop);
        masm.movq(rdi, new AMD64Address(r12, rax, Stride.S8));
        SBB.getMROpcode(QWORD).emit(masm, QWORD, new AMD64Address(r11, rax, Stride.S8), rdi);
        masm.incq(rax);
        masm.decq(rdx);
        masm.jcc(ConditionFlag.NotEqual, subLoop);
        masm.movq(rdi, rcx);
        SBB.getMIOpcode(QWORD, true).emit(masm, QWORD, rdi, 0);
        masm.movq(rcx, rdi);
        masm.testq(rdi, rdi);
        masm.jcc(ConditionFlag.NotEqual, reduceInner);

        masm.bind(done);
        masm.pop(rbx);
        masm.pop(r12);
        masm.pop(r13);
        masm.pop(r14);
        masm.pop(r15);
        masm.pop(rbp);
        masm.jmp(continuation);

        masm.align(16);
        masm.bind(phase2Empty);
        masm.movq(rdi, rcx);
        masm.movq(rcx, rsi);
        masm.xorl(rsi, rsi);
        masm.jmp(phase2Store);

        masm.bind(lenZero);
        masm.xorl(rsi, rsi);
        masm.xorl(rcx, rcx);
        masm.cmplAndJcc(new AMD64Address(rbp, MULTIPLY_HELPER_LEN), rbx, ConditionFlag.Less, phase2, false);
        masm.jmp(done);

        masm.bind(lenOne);
        masm.movq(rsi, rcx);
        masm.movq(rcx, r8);
        masm.jmp(phase2);

        masm.bind(phase1Done);
        masm.movq(rdi, rcx);
        masm.movq(rcx, rsi);
        masm.jmp(entryNext);
    }

    @Override
    public boolean modifiesStackPointer() {
        return true;
    }
}
