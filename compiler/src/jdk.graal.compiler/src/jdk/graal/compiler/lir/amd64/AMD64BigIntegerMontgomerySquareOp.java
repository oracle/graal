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
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fe611a2a3386d097f636c15bd4d396a82dc695e/src/hotspot/cpu/x86/sharedRuntime_x86_64.cpp#L3494-L3548",
          sha1 = "11a13521661a8915b469c5abe683d270d832a818")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fe611a2a3386d097f636c15bd4d396a82dc695e/src/hotspot/cpu/x86/sharedRuntime_x86_64.cpp#L3566-L3630",
          sha1 = "7a40734dd82edde5167c10e7bba76faf2592f33b")
// @formatter:on
public final class AMD64BigIntegerMontgomerySquareOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64BigIntegerMontgomerySquareOp> TYPE = LIRInstructionClass.create(AMD64BigIntegerMontgomerySquareOp.class);

    private static final int MAX_LONGWORDS_SQR = 8192 / (8 * 3);
    // The threshold at which squaring is advantageous was determined
    // experimentally on an i7-3930K (Ivy Bridge) CPU @ 3.5GHz.
    private static final int MONTGOMERY_SQUARING_THRESHOLD_LONGWORDS = 64 / 2;
    private static final int SQUARE_HELPER_COUNT = -0x50;
    private static final int SQUARE_HELPER_INV = -0x48;
    private static final int SQUARE_HELPER_TMP = -0x40;
    private static final int SQUARE_HELPER_INDEX = -0x38;
    private static final int SQUARE_HELPER_LEN2 = -0x30;
    private static final int SQUARE_HELPER_LEN = -0x2c;

    @Use private Value aValue;
    @Use private Value nValue;
    @Use private Value lenValue;
    @Use private Value invValue;
    @Use private Value productValue;

    @Temp private Value[] temps;

    public AMD64BigIntegerMontgomerySquareOp(Value aValue, Value nValue, Value lenValue, Value invValue, Value productValue) {
        super(TYPE);

        GraalError.guarantee(asRegister(aValue).equals(rdi), "expect aValue at rdi, but was %s", aValue);
        GraalError.guarantee(asRegister(nValue).equals(rsi), "expect nValue at rsi, but was %s", nValue);
        GraalError.guarantee(asRegister(lenValue).equals(rdx), "expect lenValue at rdx, but was %s", lenValue);
        GraalError.guarantee(asRegister(invValue).equals(rcx), "expect invValue at rcx, but was %s", invValue);
        GraalError.guarantee(asRegister(productValue).equals(r8), "expect productValue at r8, but was %s", productValue);

        this.aValue = aValue;
        this.nValue = nValue;
        this.lenValue = lenValue;
        this.invValue = invValue;
        this.productValue = productValue;

        this.temps = AMD64LIRHelper.registersToValues(new Register[]{
                        rax,
                        rcx,
                        rdx,
                        rsi,
                        rdi,
                        r8,
                        r9,
                        r10,
                        r11,
                        xmm0,
                        xmm1,
        });
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        GraalError.guarantee(aValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid aValue kind: %s", aValue);
        GraalError.guarantee(nValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid nValue kind: %s", nValue);
        GraalError.guarantee(lenValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid lenValue kind: %s", lenValue);
        GraalError.guarantee(invValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid invValue kind: %s", invValue);
        GraalError.guarantee(productValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid productValue kind: %s", productValue);

        masm.push(rbp);
        masm.movq(rbp, rsp);
        masm.push(r15);
        masm.push(r14);
        masm.push(r13);
        masm.push(r12);
        masm.push(rbx);
        masm.subq(rsp, 0x38);

        Label stubReturn = new Label();
        emitMontgomerySquare(masm, stubReturn);

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
     * corresponds to an 16384-bit integer and will use here a total of 6k bytes of stack space.
     */
    private static void emitMontgomerySquare(AMD64MacroAssembler masm, Label stubReturn) {
        Label done = new Label();
        Label useMultiply = new Label();
        Label multiplyDone = new Label();
        Label callMultiplyNoReverse = new Label();
        Label reverseALenOne = new Label();
        Label reverseA = new Label();
        Label reverseADone = new Label();
        Label reverseN = new Label();
        Label reverseNLenOne = new Label();
        Label reverseNDone = new Label();
        Label reverseResult = new Label();
        Label reverseResultRestored = new Label();
        Label reverseResultLoop = new Label();
        Label reverseResultLenOne = new Label();
        Label tooLarge = new Label();

        masm.movq(new AMD64Address(rbp, -0x50), rcx);
        masm.movl(rbx, rdx);
        masm.shrl(rbx, 0x1f);
        masm.addl(rbx, rdx);
        masm.sarl(rbx, 1);
        masm.cmplAndJcc(rdx, MAX_LONGWORDS_SQR * 2 + 1, ConditionFlag.Greater, tooLarge, false);
        masm.lead(rcx, new AMD64Address(rbx, rbx, Stride.S1));
        masm.movq(r9, rdi);
        masm.movslq(r12, rbx);
        masm.movq(r10, rsi);
        masm.lead(rax, new AMD64Address(rcx, rbx, Stride.S1));
        masm.shlq(r12, 3);
        masm.movslq(rcx, rcx);
        masm.shll(rax, 3);
        masm.emitByte(0x48);
        masm.emitByte(0x98); // cdqe
        masm.addq(rax, 0x17);
        masm.andq(rax, -16);
        masm.subq(rsp, rax);
        masm.leaq(rax, new AMD64Address(Register.None, rcx, Stride.S8));
        masm.leaq(rdi, new AMD64Address(rsp, 0x0f));
        masm.movq(new AMD64Address(rbp, -0x48), rax);
        masm.andq(rdi, -16);
        masm.leaq(rsi, new AMD64Address(rdi, r12, Stride.S1));
        masm.leaq(r13, new AMD64Address(rsi, r12, Stride.S1));
        masm.cmplAndJcc(rdx, 1, ConditionFlag.LessEqual, callMultiplyNoReverse, false);
        masm.movq(r14, r8);
        masm.cmplAndJcc(rbx, 1, ConditionFlag.Equal, reverseALenOne, false);
        masm.movq(rcx, rax);
        masm.movl(rax, rbx);
        masm.leaq(r11, new AMD64Address(rdi, r12, Stride.S1, -0x10));
        masm.movq(r8, r9);
        masm.shrl(rax, 1);
        masm.shlq(rax, 4);
        masm.leaq(r15, new AMD64Address(rax, r9, Stride.S1));
        masm.align(32);

        // Keep the reverse_words copies open-coded: each one preserves HotSpot's local register
        // choices and branch layout for libjvm assembly parity.
        masm.bind(reverseA);
        masm.movdqu(xmm1, new AMD64Address(r8));
        masm.addq(r8, 16);
        masm.subq(r11, 16);
        masm.movdqa(xmm0, xmm1);
        masm.psllq(xmm1, 32);
        masm.psrlq(xmm0, 32);
        masm.por(xmm0, xmm1);
        masm.shufpd(xmm0, xmm0, 1);
        masm.movups(new AMD64Address(r11, 16), xmm0);
        masm.cmpq(r8, r15);
        masm.jcc(ConditionFlag.NotEqual, reverseA);
        masm.movl(r8, rbx);
        masm.movq(new AMD64Address(rbp, -0x48), rcx);
        masm.andl(r8, 0xfffffffe);
        masm.movl(r15, r8);
        masm.leaq(r11, new AMD64Address(Register.None, r15, Stride.S8));
        masm.movq(rcx, r11);
        masm.negq(rcx);
        masm.movq(new AMD64Address(rbp, -0x58), rcx);
        masm.cmplAndJcc(rbx, r8, ConditionFlag.Equal, reverseADone, false);
        masm.movq(r9, new AMD64Address(r9, r15, Stride.S8));
        ROL.miOp.emit(masm, QWORD, r9, (byte) 32);
        masm.movq(new AMD64Address(rsi, rcx, Stride.S1, -8), r9);

        masm.bind(reverseADone);
        masm.movq(rcx, new AMD64Address(rbp, -0x48));
        masm.movq(r9, r10);
        masm.addq(rax, r10);
        masm.leaq(rcx, new AMD64Address(rdi, rcx, Stride.S1, -0x10));
        masm.align(32);

        masm.bind(reverseN);
        masm.movdqu(xmm1, new AMD64Address(r9));
        masm.addq(r9, 16);
        masm.subq(rcx, 16);
        masm.movdqa(xmm0, xmm1);
        masm.psllq(xmm1, 32);
        masm.psrlq(xmm0, 32);
        masm.por(xmm0, xmm1);
        masm.shufpd(xmm0, xmm0, 1);
        masm.movaps(new AMD64Address(rcx, 16), xmm0);
        masm.cmpq(r9, rax);
        masm.jcc(ConditionFlag.NotEqual, reverseN);
        masm.cmplAndJcc(rbx, r8, ConditionFlag.Equal, reverseNDone, false);
        masm.movq(rax, new AMD64Address(rbp, -0x58));
        masm.addq(r10, r11);
        masm.addq(rax, r13);
        masm.bind(reverseNLenOne);
        masm.movq(rcx, new AMD64Address(r10));
        ROL.miOp.emit(masm, QWORD, rcx, (byte) 32);
        masm.movq(new AMD64Address(rax, -8), rcx);

        masm.bind(reverseNDone);
        masm.cmplAndJcc(rdx, MONTGOMERY_SQUARING_THRESHOLD_LONGWORDS * 2 - 1, ConditionFlag.LessEqual, useMultiply, false);
        masm.movq(rcx, new AMD64Address(rbp, -0x50));
        masm.movl(r8, rbx);
        masm.movq(rdx, r13);
        emitMontgomerySquareHelper(masm, reverseResult);

        masm.bind(reverseResult);
        masm.leaq(rsi, new AMD64Address(r14, r12, Stride.S1));
        masm.bind(reverseResultRestored);
        masm.movl(rcx, rbx);
        masm.movq(rax, r13);
        masm.leaq(rdx, new AMD64Address(r14, r12, Stride.S1, -0x10));
        masm.shrl(rcx, 1);
        masm.shlq(rcx, 4);
        masm.addq(rcx, r13);
        masm.align(32);

        masm.bind(reverseResultLoop);
        masm.movdqa(xmm1, new AMD64Address(rax));
        masm.addq(rax, 16);
        masm.subq(rdx, 16);
        masm.movdqa(xmm0, xmm1);
        masm.psllq(xmm1, 32);
        masm.psrlq(xmm0, 32);
        masm.por(xmm0, xmm1);
        masm.shufpd(xmm0, xmm0, 1);
        masm.movups(new AMD64Address(rdx, 16), xmm0);
        masm.cmpq(rcx, rax);
        masm.jcc(ConditionFlag.NotEqual, reverseResultLoop);
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

        masm.bind(useMultiply);
        masm.movq(r8, new AMD64Address(rbp, -0x50));
        masm.movq(rdx, rsi);
        masm.movl(r9, rbx);
        masm.movq(rsi, rdi);
        masm.movq(rcx, r13);
        AMD64BigIntegerMontgomeryMultiplyOp.emitMontgomeryMultiplyHelper(masm, multiplyDone);

        masm.bind(multiplyDone);
        masm.leaq(rsi, new AMD64Address(r14, r12, Stride.S1));
        masm.cmplAndJcc(rbx, 1, ConditionFlag.NotEqual, reverseResultRestored, false);
        masm.jmp(reverseResultLenOne);

        masm.align(16);
        masm.bind(callMultiplyNoReverse);
        masm.movq(r8, new AMD64Address(rbp, -0x50));
        masm.movq(rdx, rsi);
        masm.movl(r9, rbx);
        masm.movq(rcx, r13);
        masm.movq(rsi, rdi);
        AMD64BigIntegerMontgomeryMultiplyOp.emitMontgomeryMultiplyHelper(masm, done);

        masm.bind(reverseALenOne);
        masm.movq(rax, new AMD64Address(r9));
        ROL.miOp.emit(masm, QWORD, rax, (byte) 32);
        masm.movq(new AMD64Address(rsi, -8), rax);
        masm.movq(rax, r13);
        masm.jmp(reverseNLenOne);

        masm.bind(tooLarge);
        masm.illegal();
    }

    /**
     * Fast Montgomery squaring. This uses asymptotically 25% fewer multiplies so it should be up to
     * 25% faster than Montgomery multiplication. However, its loop control is more complex and it
     * may actually run slower on some machines.
     */
    private static void emitMontgomerySquareHelper(AMD64MacroAssembler masm, Label continuation) {
        Label phase1 = new Label();
        Label phase1Pairs = new Label();
        Label phase1Tail = new Label();
        Label phase1OddOrNoTail = new Label();
        Label phase1Store = new Label();
        Label phase2 = new Label();
        Label phase2Outer = new Label();
        Label phase2Pairs = new Label();
        Label phase2AfterPairs = new Label();
        Label phase2AfterPairsTest = new Label();
        Label phase2Tail = new Label();
        Label phase2Store = new Label();
        Label reduce = new Label();
        Label reduceOuter = new Label();
        Label done = new Label();
        Label phase1NoPairs = new Label();
        Label phase1Even = new Label();
        Label phase1NoTail = new Label();
        Label phase2NoTail = new Label();
        Label phase2NoPairs = new Label();
        Label lenZero = new Label();

        masm.push(rbp);
        masm.movslq(rax, r8);
        masm.movq(r10, rdx);
        masm.movq(r9, rdi);
        masm.lead(rdx, new AMD64Address(rax, rax, Stride.S1));
        masm.movq(rbp, rsp);
        masm.push(r15);
        masm.movq(r15, rsi);
        masm.push(r14);
        masm.push(r13);
        masm.push(r12);
        masm.push(rbx);
        masm.movq(new AMD64Address(rbp, SQUARE_HELPER_INV), rcx);
        masm.movl(new AMD64Address(rbp, SQUARE_HELPER_LEN), rax);
        masm.movl(new AMD64Address(rbp, SQUARE_HELPER_LEN2), rdx);
        masm.testl(rax, rax);
        masm.jcc(ConditionFlag.LessEqual, lenZero);
        masm.movq(new AMD64Address(rbp, SQUARE_HELPER_COUNT), rax);
        masm.xorl(rcx, rcx);
        masm.xorl(rsi, rsi);
        masm.xorl(rdi, rdi);

        masm.bind(phase1);
        masm.lead(r12, new AMD64Address(rcx, 1));
        masm.movl(new AMD64Address(rbp, SQUARE_HELPER_INDEX), rcx);
        masm.movl(r14, rcx);
        masm.sarl(r12, 1);
        masm.jcc(ConditionFlag.Equal, phase1NoPairs);
        masm.movq(new AMD64Address(rbp, SQUARE_HELPER_TMP), rcx);
        masm.movslq(rbx, r12);
        masm.xorl(r8, r8);
        masm.xorl(r13, r13);
        masm.leaq(r11, new AMD64Address(Register.None, rcx, Stride.S8));
        masm.shlq(rbx, 3);

        masm.bind(phase1Pairs);
        masm.movq(rcx, new AMD64Address(r9, r8, Stride.S1));
        masm.movq(rax, new AMD64Address(r9, r11, Stride.S1));
        masm.mulq(rcx);
        masm.addq(rdi, rax);
        ADC.getRMOpcode(QWORD).emit(masm, QWORD, rsi, rdx);
        masm.adcq(r13, 0);
        masm.addq(rdi, rax);
        ADC.getRMOpcode(QWORD).emit(masm, QWORD, rsi, rdx);
        masm.adcq(r13, 0);
        masm.movq(rcx, new AMD64Address(r10, r8, Stride.S1));
        masm.addq(r8, 8);
        masm.movq(rax, new AMD64Address(r15, r11, Stride.S1));
        masm.subq(r11, 8);
        masm.mulq(rcx);
        masm.addq(rdi, rax);
        ADC.getRMOpcode(QWORD).emit(masm, QWORD, rsi, rdx);
        masm.adcq(r13, 0);
        masm.cmpq(rbx, r8);
        masm.jcc(ConditionFlag.NotEqual, phase1Pairs);
        masm.movq(rcx, new AMD64Address(rbp, SQUARE_HELPER_TMP));
        masm.addq(rbx, r9);
        TESTB.emit(masm, BYTE, new AMD64Address(rbp, SQUARE_HELPER_INDEX), 1);
        masm.jcc(ConditionFlag.Equal, phase1Even);
        masm.cmpl(r12, rcx);
        masm.jcc(ConditionFlag.GreaterEqual, phase1NoTail);

        masm.bind(phase1Tail);
        masm.movslq(rdx, r12);
        masm.movq(rax, rcx);
        masm.movq(r8, rdi);
        masm.movq(rdi, rsi);
        masm.subq(rax, rdx);
        masm.leaq(r11, new AMD64Address(r10, rdx, Stride.S8));
        masm.movq(rsi, r13);
        masm.leaq(rbx, new AMD64Address(r15, rax, Stride.S8));
        masm.movl(rax, r14);
        masm.subl(rax, r12);
        masm.addq(rax, rdx);
        masm.leaq(r12, new AMD64Address(r10, rax, Stride.S8));
        masm.align(16);

        masm.bind(phase1OddOrNoTail);
        masm.movq(r13, new AMD64Address(r11));
        masm.addq(r11, 8);
        masm.movq(rax, new AMD64Address(rbx));
        masm.subq(rbx, 8);
        masm.mulq(r13);
        masm.addq(r8, rax);
        ADC.getRMOpcode(QWORD).emit(masm, QWORD, rdi, rdx);
        masm.adcq(rsi, 0);
        masm.cmpq(r12, r11);
        masm.jcc(ConditionFlag.NotEqual, phase1OddOrNoTail);

        masm.bind(phase1Store);
        masm.movq(r11, new AMD64Address(rbp, SQUARE_HELPER_INV));
        masm.imulq(r11, r8);
        masm.movq(new AMD64Address(r10, rcx, Stride.S8), r11);
        masm.movq(rax, new AMD64Address(r15));
        masm.addq(rcx, 1);
        masm.mulq(r11);
        masm.addq(r8, rax);
        ADC.getRMOpcode(QWORD).emit(masm, QWORD, rdi, rdx);
        masm.adcq(rsi, 0);
        masm.movq(rax, new AMD64Address(rbp, SQUARE_HELPER_COUNT));
        masm.cmpq(rcx, rax);
        masm.jcc(ConditionFlag.NotEqual, phase1);
        masm.movl(rbx, new AMD64Address(rbp, SQUARE_HELPER_LEN2));
        masm.movq(rdx, rdi);
        masm.cmplAndJcc(new AMD64Address(rbp, SQUARE_HELPER_LEN), rbx, ConditionFlag.GreaterEqual, reduce, false);

        masm.bind(phase2);
        masm.movslq(rbx, new AMD64Address(rbp, SQUARE_HELPER_LEN));
        masm.movl(r12, 1);
        masm.xorl(r14, r14);
        masm.movq(rdi, rdx);
        masm.movq(new AMD64Address(rbp, SQUARE_HELPER_INDEX), rbx);
        masm.movl(rax, rbx);
        masm.align(16);

        masm.bind(phase2Outer);
        masm.movl(rcx, new AMD64Address(rbp, SQUARE_HELPER_LEN));
        masm.movl(r13, r12);
        masm.subl(rcx, r12);
        masm.movl(rbx, rcx);
        masm.shrl(rbx, 0x1f);
        masm.addl(rbx, rcx);
        masm.sarl(rbx, 1);
        masm.addl(rbx, r12);
        masm.cmpl(r12, rbx);
        masm.jcc(ConditionFlag.GreaterEqual, phase2NoPairs);
        masm.movq(rcx, new AMD64Address(rbp, SQUARE_HELPER_INDEX));
        masm.movl(r8, r12);
        masm.movl(new AMD64Address(rbp, SQUARE_HELPER_TMP), r14);
        masm.movl(new AMD64Address(rbp, SQUARE_HELPER_INV), rax);
        masm.leaq(r11, new AMD64Address(rcx, r14, Stride.S1));
        masm.xorl(rcx, rcx);
        masm.subq(r11, r8);
        masm.shlq(r11, 3);
        masm.align(16);

        masm.bind(phase2Pairs);
        masm.movq(r12, new AMD64Address(r9, r8, Stride.S8));
        masm.movq(rax, new AMD64Address(r9, r11, Stride.S1));
        masm.mulq(r12);
        masm.addq(rdi, rax);
        ADC.getRMOpcode(QWORD).emit(masm, QWORD, rsi, rdx);
        masm.adcq(rcx, 0);
        masm.addq(rdi, rax);
        ADC.getRMOpcode(QWORD).emit(masm, QWORD, rsi, rdx);
        masm.adcq(rcx, 0);
        masm.movq(r12, new AMD64Address(r10, r8, Stride.S8));
        masm.addq(r8, 1);
        masm.movq(rax, new AMD64Address(r15, r11, Stride.S1));
        masm.subq(r11, 8);
        masm.mulq(r12);
        masm.addq(rdi, rax);
        ADC.getRMOpcode(QWORD).emit(masm, QWORD, rsi, rdx);
        masm.adcq(rcx, 0);
        masm.cmpl(rbx, r8);
        masm.jcc(ConditionFlag.Greater, phase2Pairs);

        masm.bind(phase2AfterPairs);
        masm.movl(rdx, new AMD64Address(rbp, SQUARE_HELPER_TMP));
        masm.movl(r12, r13);
        masm.lead(r13, new AMD64Address(r13, -1));
        masm.movl(rax, new AMD64Address(rbp, SQUARE_HELPER_INV));
        masm.subl(r13, rdx);
        masm.addl(r13, rbx);

        masm.bind(phase2AfterPairsTest);
        TESTB.emit(masm, BYTE, rax, 1);
        masm.jcc(ConditionFlag.NotEqual, phase2Tail);
        masm.movslq(rax, r13);
        masm.movq(rax, new AMD64Address(r9, rax, Stride.S8));
        masm.mulq(rax);
        masm.addq(rdi, rax);
        ADC.getRMOpcode(QWORD).emit(masm, QWORD, rsi, rdx);
        masm.adcq(rcx, 0);

        masm.bind(phase2Tail);
        masm.movl(rbx, new AMD64Address(rbp, SQUARE_HELPER_LEN));
        masm.cmpl(rbx, r13);
        masm.jcc(ConditionFlag.LessEqual, phase2NoTail);
        masm.movq(rax, new AMD64Address(rbp, SQUARE_HELPER_INDEX));
        masm.movslq(rdx, r13);
        masm.leaq(r8, new AMD64Address(r10, rdx, Stride.S8));
        masm.addq(rax, r14);
        masm.subq(rax, rdx);
        masm.leaq(r11, new AMD64Address(r15, rax, Stride.S8));
        masm.movl(rax, rbx);
        masm.subl(rax, r13);
        masm.addq(rax, rdx);
        masm.movq(rdx, rdi);
        masm.movq(rdi, rsi);
        masm.movq(rsi, rcx);
        masm.leaq(rbx, new AMD64Address(r10, rax, Stride.S8));
        masm.movq(rcx, rdx);

        Label phase2TailLoop = new Label();
        masm.align(16);
        masm.bind(phase2TailLoop);
        masm.movq(r13, new AMD64Address(r8));
        masm.addq(r8, 8);
        masm.movq(rax, new AMD64Address(r11));
        masm.subq(r11, 8);
        masm.mulq(r13);
        masm.addq(rcx, rax);
        ADC.getRMOpcode(QWORD).emit(masm, QWORD, rdi, rdx);
        masm.adcq(rsi, 0);
        masm.cmpq(r8, rbx);
        masm.jcc(ConditionFlag.NotEqual, phase2TailLoop);
        masm.movq(rdx, rcx);

        masm.bind(phase2Store);
        masm.movl(rax, new AMD64Address(rbp, SQUARE_HELPER_LEN));
        masm.movq(new AMD64Address(r10, r14, Stride.S8), rdx);
        masm.addq(r14, 1);
        masm.addl(r12, 1);
        masm.movl(rdx, new AMD64Address(rbp, SQUARE_HELPER_LEN2));
        masm.addl(rax, r14);
        masm.cmpl(rax, rdx);
        masm.jcc(ConditionFlag.Less, phase2Outer);
        masm.movq(rdx, rdi);

        masm.bind(reduce);
        masm.testq(rdx, rdx);
        masm.jcc(ConditionFlag.Equal, done);
        masm.movslq(rax, new AMD64Address(rbp, SQUARE_HELPER_LEN));
        masm.xorl(r8, r8);
        masm.align(16);

        masm.bind(reduceOuter);
        masm.movq(rcx, r8);
        masm.movq(rsi, rax);
        masm.emitByte(0xF8); // clc
        Label reduceInner = new Label();
        masm.bind(reduceInner);
        masm.movq(rdi, new AMD64Address(r15, rcx, Stride.S8));
        SBB.getMROpcode(QWORD).emit(masm, QWORD, new AMD64Address(r10, rcx, Stride.S8), rdi);
        masm.incq(rcx);
        masm.decq(rsi);
        masm.jcc(ConditionFlag.NotEqual, reduceInner);
        masm.movq(rdi, rdx);
        SBB.getMIOpcode(QWORD, true).emit(masm, QWORD, rdi, 0);
        masm.movq(rdx, rdi);
        masm.testq(rdi, rdi);
        masm.jcc(ConditionFlag.NotEqual, reduceOuter);

        masm.bind(done);
        masm.pop(rbx);
        masm.pop(r12);
        masm.pop(r13);
        masm.pop(r14);
        masm.pop(r15);
        masm.pop(rbp);
        masm.jmp(continuation);

        masm.align(16);
        masm.bind(phase1NoPairs);
        masm.movq(rbx, r9);
        masm.xorl(r13, r13);

        masm.bind(phase1Even);
        masm.movq(rax, new AMD64Address(rbx));
        masm.mulq(rax);
        masm.addq(rdi, rax);
        ADC.getRMOpcode(QWORD).emit(masm, QWORD, rsi, rdx);
        masm.adcq(r13, 0);
        masm.cmpl(r12, rcx);
        masm.jcc(ConditionFlag.Less, phase1Tail);

        masm.bind(phase1NoTail);
        masm.movq(r8, rdi);
        masm.movq(rdi, rsi);
        masm.movq(rsi, r13);
        masm.jmp(phase1Store);

        masm.align(16);
        masm.bind(phase2NoTail);
        masm.movq(rdx, rdi);
        masm.movq(rdi, rsi);
        masm.movq(rsi, rcx);
        masm.jmp(phase2Store);

        masm.align(16);
        masm.bind(phase2NoPairs);
        masm.xorl(rcx, rcx);
        masm.jmp(phase2AfterPairsTest);

        masm.bind(lenZero);
        masm.movl(rbx, rdx);
        masm.xorl(rsi, rsi);
        masm.xorl(rdx, rdx);
        masm.cmplAndJcc(new AMD64Address(rbp, SQUARE_HELPER_LEN), rbx, ConditionFlag.Less, phase2, false);
        masm.jmp(done);
    }

    @Override
    public boolean modifiesStackPointer() {
        return true;
    }
}
