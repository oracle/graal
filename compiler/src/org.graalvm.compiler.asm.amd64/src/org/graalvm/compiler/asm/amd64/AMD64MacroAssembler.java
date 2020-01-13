/*
 * Copyright (c) 2009, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.asm.amd64;

import static org.graalvm.compiler.asm.amd64.AMD64AsmOptions.UseIncDec;
import static org.graalvm.compiler.asm.amd64.AMD64AsmOptions.UseXmmLoadAndClearUpper;
import static org.graalvm.compiler.asm.amd64.AMD64AsmOptions.UseXmmRegToRegMoveAll;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.ADD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.AND;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.CMP;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.SUB;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64MOp.DEC;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64MOp.INC;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.DWORD;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.QWORD;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.WORD;
import static org.graalvm.compiler.core.common.NumUtil.isByte;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.VMConstant;

/**
 * This class implements commonly used X86 code patterns.
 */
public class AMD64MacroAssembler extends AMD64Assembler {

    public AMD64MacroAssembler(TargetDescription target) {
        super(target);
    }

    public AMD64MacroAssembler(TargetDescription target, OptionValues optionValues) {
        super(target, optionValues);
    }

    public final void decrementq(Register reg, int value) {
        if (value == Integer.MIN_VALUE) {
            subq(reg, value);
            return;
        }
        if (value < 0) {
            incrementq(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            decq(reg);
        } else {
            subq(reg, value);
        }
    }

    public final void decrementq(AMD64Address dst, int value) {
        if (value == Integer.MIN_VALUE) {
            subq(dst, value);
            return;
        }
        if (value < 0) {
            incrementq(dst, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            decq(dst);
        } else {
            subq(dst, value);
        }
    }

    public void incrementq(Register reg, int value) {
        if (value == Integer.MIN_VALUE) {
            addq(reg, value);
            return;
        }
        if (value < 0) {
            decrementq(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            incq(reg);
        } else {
            addq(reg, value);
        }
    }

    public final void incrementq(AMD64Address dst, int value) {
        if (value == Integer.MIN_VALUE) {
            addq(dst, value);
            return;
        }
        if (value < 0) {
            decrementq(dst, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            incq(dst);
        } else {
            addq(dst, value);
        }
    }

    public final void movptr(Register dst, AMD64Address src) {
        movq(dst, src);
    }

    public final void movptr(AMD64Address dst, Register src) {
        movq(dst, src);
    }

    public final void movptr(AMD64Address dst, int src) {
        movslq(dst, src);
    }

    public final void cmpptr(Register src1, Register src2) {
        cmpq(src1, src2);
    }

    public final void cmpptr(Register src1, AMD64Address src2) {
        cmpq(src1, src2);
    }

    public final void decrementl(Register reg) {
        decrementl(reg, 1);
    }

    public final void decrementl(Register reg, int value) {
        if (value == Integer.MIN_VALUE) {
            subl(reg, value);
            return;
        }
        if (value < 0) {
            incrementl(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            decl(reg);
        } else {
            subl(reg, value);
        }
    }

    public final void decrementl(AMD64Address dst, int value) {
        if (value == Integer.MIN_VALUE) {
            subl(dst, value);
            return;
        }
        if (value < 0) {
            incrementl(dst, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            decl(dst);
        } else {
            subl(dst, value);
        }
    }

    public final void incrementl(Register reg, int value) {
        if (value == Integer.MIN_VALUE) {
            addl(reg, value);
            return;
        }
        if (value < 0) {
            decrementl(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            incl(reg);
        } else {
            addl(reg, value);
        }
    }

    public final void incrementl(AMD64Address dst, int value) {
        if (value == Integer.MIN_VALUE) {
            addl(dst, value);
            return;
        }
        if (value < 0) {
            decrementl(dst, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            incl(dst);
        } else {
            addl(dst, value);
        }
    }

    public void movflt(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        if (UseXmmRegToRegMoveAll) {
            if (isAVX512Register(dst) || isAVX512Register(src)) {
                VexMoveOp.VMOVAPS.emit(this, AVXSize.XMM, dst, src);
            } else {
                movaps(dst, src);
            }
        } else {
            if (isAVX512Register(dst) || isAVX512Register(src)) {
                VexMoveOp.VMOVSS.emit(this, AVXSize.XMM, dst, src);
            } else {
                movss(dst, src);
            }
        }
    }

    public void movflt(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        if (isAVX512Register(dst)) {
            VexMoveOp.VMOVSS.emit(this, AVXSize.XMM, dst, src);
        } else {
            movss(dst, src);
        }
    }

    public void movflt(AMD64Address dst, Register src) {
        assert src.getRegisterCategory().equals(AMD64.XMM);
        if (isAVX512Register(src)) {
            VexMoveOp.VMOVSS.emit(this, AVXSize.XMM, dst, src);
        } else {
            movss(dst, src);
        }
    }

    public void movdbl(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        if (UseXmmRegToRegMoveAll) {
            if (isAVX512Register(dst) || isAVX512Register(src)) {
                VexMoveOp.VMOVAPD.emit(this, AVXSize.XMM, dst, src);
            } else {
                movapd(dst, src);
            }
        } else {
            if (isAVX512Register(dst) || isAVX512Register(src)) {
                VexMoveOp.VMOVSD.emit(this, AVXSize.XMM, dst, src);
            } else {
                movsd(dst, src);
            }
        }
    }

    public void movdbl(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        if (UseXmmLoadAndClearUpper) {
            if (isAVX512Register(dst)) {
                VexMoveOp.VMOVSD.emit(this, AVXSize.XMM, dst, src);
            } else {
                movsd(dst, src);
            }
        } else {
            assert !isAVX512Register(dst);
            movlpd(dst, src);
        }
    }

    public void movdbl(AMD64Address dst, Register src) {
        assert src.getRegisterCategory().equals(AMD64.XMM);
        if (isAVX512Register(src)) {
            VexMoveOp.VMOVSD.emit(this, AVXSize.XMM, dst, src);
        } else {
            movsd(dst, src);
        }
    }

    /**
     * Non-atomic write of a 64-bit constant to memory. Do not use if the address might be a
     * volatile field!
     */
    public final void movlong(AMD64Address dst, long src) {
        if (NumUtil.isInt(src)) {
            AMD64MIOp.MOV.emit(this, OperandSize.QWORD, dst, (int) src);
        } else {
            AMD64Address high = new AMD64Address(dst.getBase(), dst.getIndex(), dst.getScale(), dst.getDisplacement() + 4);
            movl(dst, (int) (src & 0xFFFFFFFF));
            movl(high, (int) (src >> 32));
        }
    }

    public final void setl(ConditionFlag cc, Register dst) {
        setb(cc, dst);
        movzbl(dst, dst);
    }

    public final void setq(ConditionFlag cc, Register dst) {
        setb(cc, dst);
        movzbq(dst, dst);
    }

    public final void flog(Register dest, Register value, boolean base10) {
        if (base10) {
            fldlg2();
        } else {
            fldln2();
        }
        AMD64Address tmp = trigPrologue(value);
        fyl2x();
        trigEpilogue(dest, tmp);
    }

    public final void fsin(Register dest, Register value) {
        AMD64Address tmp = trigPrologue(value);
        fsin();
        trigEpilogue(dest, tmp);
    }

    public final void fcos(Register dest, Register value) {
        AMD64Address tmp = trigPrologue(value);
        fcos();
        trigEpilogue(dest, tmp);
    }

    public final void ftan(Register dest, Register value) {
        AMD64Address tmp = trigPrologue(value);
        fptan();
        fstp(0); // ftan pushes 1.0 in addition to the actual result, pop
        trigEpilogue(dest, tmp);
    }

    public final void fpop() {
        ffree(0);
        fincstp();
    }

    private AMD64Address trigPrologue(Register value) {
        assert value.getRegisterCategory().equals(AMD64.XMM);
        AMD64Address tmp = new AMD64Address(AMD64.rsp);
        subq(AMD64.rsp, AMD64Kind.DOUBLE.getSizeInBytes());
        movdbl(tmp, value);
        fldd(tmp);
        return tmp;
    }

    private void trigEpilogue(Register dest, AMD64Address tmp) {
        assert dest.getRegisterCategory().equals(AMD64.XMM);
        fstpd(tmp);
        movdbl(dest, tmp);
        addq(AMD64.rsp, AMD64Kind.DOUBLE.getSizeInBytes());
    }

    /**
     * Emit a direct call to a fixed address, which will be patched later during code installation.
     *
     * @param align indicates whether the displacement bytes (offset by
     *            {@code callDisplacementOffset}) of this call instruction should be aligned to
     *            {@code wordSize}.
     * @return where the actual call instruction starts.
     */
    public final int directCall(boolean align, int callDisplacementOffset, int wordSize) {
        emitAlignmentForDirectCall(align, callDisplacementOffset, wordSize);
        testAndAlign(5);
        // After padding to mitigate JCC erratum, the displacement may be unaligned again. The
        // previous pass is essential because JCC erratum padding may not trigger without the
        // displacement alignment.
        emitAlignmentForDirectCall(align, callDisplacementOffset, wordSize);
        int beforeCall = position();
        call();
        return beforeCall;
    }

    private void emitAlignmentForDirectCall(boolean align, int callDisplacementOffset, int wordSize) {
        if (align) {
            // make sure that the displacement word of the call ends up word aligned
            int offset = position();
            offset += callDisplacementOffset;
            int modulus = wordSize;
            if (offset % modulus != 0) {
                nop(modulus - offset % modulus);
            }
        }
    }

    public final int indirectCall(Register callReg) {
        int bytesToEmit = needsRex(callReg) ? 3 : 2;
        testAndAlign(bytesToEmit);
        int beforeCall = position();
        call(callReg);
        assert beforeCall + bytesToEmit == position();
        return beforeCall;
    }

    public final int directCall(long address, Register scratch) {
        int bytesToEmit = needsRex(scratch) ? 13 : 12;
        testAndAlign(bytesToEmit);
        int beforeCall = position();
        movq(scratch, address);
        call(scratch);
        assert beforeCall + bytesToEmit == position();
        return beforeCall;
    }

    public final int directJmp(long address, Register scratch) {
        int bytesToEmit = needsRex(scratch) ? 13 : 12;
        testAndAlign(bytesToEmit);
        int beforeJmp = position();
        movq(scratch, address);
        jmpWithoutAlignment(scratch);
        assert beforeJmp + bytesToEmit == position();
        return beforeJmp;
    }

    private void jcc(ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        if (branchTarget == null) {
            // jump to placeholder
            jcc(cc, 0, true);
        } else if (isShortJmp) {
            jccb(cc, branchTarget);
        } else {
            jcc(cc, branchTarget);
        }
    }

    // This should guarantee that the alignment in AMD64Assembler.jcc methods will be not triggered.
    private void alignFusedPair(Label branchTarget, boolean isShortJmp, int prevOpInBytes) {
        assert prevOpInBytes < 26 : "Fused pair may be longer than 0x20 bytes.";
        if (branchTarget == null) {
            testAndAlign(prevOpInBytes + 6);
        } else if (isShortJmp) {
            testAndAlign(prevOpInBytes + 2);
        } else if (!branchTarget.isBound()) {
            testAndAlign(prevOpInBytes + 6);
        } else {
            long disp = branchTarget.position() - (position() + prevOpInBytes);
            // assuming short jump first
            if (isByte(disp - 2)) {
                testAndAlign(prevOpInBytes + 2);
                // After alignment, isByte(disp - shortSize) might not hold. Need to check
                // again.
                disp = branchTarget.position() - (position() + prevOpInBytes);
                if (isByte(disp - 2)) {
                    return;
                }
            }
            testAndAlign(prevOpInBytes + 6);
        }
    }

    private static final int OPCODE_IN_BYTES = 1;
    private static final int MODRM_IN_BYTES = 1;

    private static final boolean needsRex(Register src, boolean srcIsByte) {
        return srcIsByte ? src.encoding >= 4 : needsRex(src);
    }

    private static final int getPrefixInBytes(OperandSize size, Register dst, boolean dstIsByte) {
        boolean needsRex = needsRex(dst, dstIsByte);
        if (size == WORD) {
            return needsRex ? 2 : 1;
        }
        return size == QWORD || needsRex ? 1 : 0;
    }

    private static final int getPrefixInBytes(OperandSize size, AMD64Address src) {
        boolean needsRex = needsRex(src.getBase()) || needsRex(src.getIndex());
        if (size == WORD) {
            return needsRex ? 2 : 1;
        }
        return size == QWORD || needsRex ? 1 : 0;
    }

    private static final int getPrefixInBytes(OperandSize size, Register dst, boolean dstIsByte, Register src, boolean srcIsByte) {
        boolean needsRex = needsRex(dst, dstIsByte) || needsRex(src, srcIsByte);
        if (size == WORD) {
            return needsRex ? 2 : 1;
        }
        return size == QWORD || needsRex ? 1 : 0;
    }

    private static final int getPrefixInBytes(OperandSize size, Register dst, boolean dstIsByte, AMD64Address src) {
        boolean needsRex = needsRex(dst, dstIsByte) || needsRex(src.getBase()) || needsRex(src.getIndex());
        if (size == WORD) {
            return needsRex ? 2 : 1;
        }
        return size == QWORD || needsRex ? 1 : 0;
    }

    public final void testAndJcc(OperandSize size, Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        final AMD64MIOp op = AMD64MIOp.TEST;
        final int bytesToEmit = getPrefixInBytes(size, src, op.srcIsByte) + OPCODE_IN_BYTES + MODRM_IN_BYTES + op.immediateSize(size);
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        op.emit(this, size, src, imm32);
        assert beforeFusedPair + bytesToEmit == position();
        jcc(cc, branchTarget, isShortJmp);
        assert useBranchesWithin32ByteBoundary && !mayCrossBoundary(beforeFusedPair, position());
    }

    public final void testlAndJcc(Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        testAndJcc(DWORD, src, imm32, cc, branchTarget, isShortJmp);
    }

    public final void testAndJcc(OperandSize size, AMD64Address src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp, CompilationResultBuilder crb, LIRFrameState state) {
        final AMD64MIOp op = AMD64MIOp.TEST;
        final int bytesToEmit = getPrefixInBytes(size, src) + OPCODE_IN_BYTES + addressInBytes(src) + op.immediateSize(size);
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        if (crb != null && state != null) {
            crb.recordImplicitException(position(), state);
        }
        op.emit(this, size, src, imm32, false);
        assert beforeFusedPair + bytesToEmit == position();
        jcc(cc, branchTarget, isShortJmp);
        assert useBranchesWithin32ByteBoundary && !mayCrossBoundary(beforeFusedPair, position());
    }

    public final int testAndJcc(OperandSize size, Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        final AMD64RMOp op = AMD64RMOp.TEST;
        final int bytesToEmit = getPrefixInBytes(size, src1, op.dstIsByte, src2, op.srcIsByte) + OPCODE_IN_BYTES + MODRM_IN_BYTES;
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        op.emit(this, size, src1, src2);
        final int beforeJcc = position();
        assert beforeFusedPair + bytesToEmit == beforeJcc;
        jcc(cc, branchTarget, isShortJmp);
        assert useBranchesWithin32ByteBoundary && !mayCrossBoundary(beforeFusedPair, position());
        return beforeJcc;
    }

    public final void testlAndJcc(Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        testAndJcc(DWORD, src1, src2, cc, branchTarget, isShortJmp);
    }

    public final int testqAndJcc(Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        return testAndJcc(QWORD, src1, src2, cc, branchTarget, isShortJmp);
    }

    public final void testAndJcc(OperandSize size, Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp, CompilationResultBuilder crb, LIRFrameState state) {
        final AMD64RMOp op = AMD64RMOp.TEST;
        final int bytesToEmit = getPrefixInBytes(size, src1, op.dstIsByte, src2) + OPCODE_IN_BYTES + addressInBytes(src2);
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        if (crb != null && state != null) {
            crb.recordImplicitException(position(), state);
        }
        op.emit(this, size, src1, src2);
        assert beforeFusedPair + bytesToEmit == position();
        jcc(cc, branchTarget, isShortJmp);
        assert useBranchesWithin32ByteBoundary && !mayCrossBoundary(beforeFusedPair, position());
    }

    public final void testbAndJcc(Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        final AMD64RMOp op = AMD64RMOp.TESTB;
        final int bytesToEmit = getPrefixInBytes(OperandSize.BYTE, src1, op.dstIsByte, src2, op.srcIsByte) + OPCODE_IN_BYTES + MODRM_IN_BYTES;
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        op.emit(this, OperandSize.BYTE, src1, src2);
        assert beforeFusedPair + bytesToEmit == position();
        jcc(cc, branchTarget, isShortJmp);
        assert useBranchesWithin32ByteBoundary && !mayCrossBoundary(beforeFusedPair, position());
    }

    public final void testbAndJcc(Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        final AMD64RMOp op = AMD64RMOp.TESTB;
        final int bytesToEmit = getPrefixInBytes(OperandSize.BYTE, src1, op.dstIsByte, src2) + OPCODE_IN_BYTES + addressInBytes(src2);
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        op.emit(this, OperandSize.BYTE, src1, src2);
        assert beforeFusedPair + bytesToEmit == position();
        jcc(cc, branchTarget, isShortJmp);
        assert useBranchesWithin32ByteBoundary && !mayCrossBoundary(beforeFusedPair, position());
    }

    public final int cmpAndJcc(OperandSize size, Register src, int imm32, VMConstant inlinedConstant, ConditionFlag cc, Label branchTarget, boolean isShortJmp, CompilationResultBuilder crb) {
        final AMD64MIOp op = CMP.getMIOpcode(size, isByte(imm32));
        final int bytesToEmit = getPrefixInBytes(size, src, op.srcIsByte) + OPCODE_IN_BYTES + MODRM_IN_BYTES + op.immediateSize(size);
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        if (crb != null && inlinedConstant != null) {
            crb.recordInlineDataInCode(inlinedConstant);
        }
        op.emit(this, size, src, imm32, inlinedConstant != null);
        final int beforeJcc = position();
        assert beforeFusedPair + bytesToEmit == beforeJcc;
        jcc(cc, branchTarget, isShortJmp);
        assert useBranchesWithin32ByteBoundary && !mayCrossBoundary(beforeFusedPair, position());
        return beforeJcc;
    }

    public final void cmplAndJcc(Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        cmpAndJcc(DWORD, src, imm32, null, cc, branchTarget, isShortJmp, null);
    }

    public final void cmpqAndJcc(Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        cmpAndJcc(QWORD, src, imm32, null, cc, branchTarget, isShortJmp, null);
    }

    public final void cmpAndJcc(OperandSize size, AMD64Address src, int imm32, VMConstant inlinedConstant, ConditionFlag cc, Label branchTarget, boolean isShortJmp, CompilationResultBuilder crb,
                    LIRFrameState state) {
        final AMD64MIOp op = CMP.getMIOpcode(size, NumUtil.isByte(imm32));
        final int bytesToEmit = getPrefixInBytes(size, src) + OPCODE_IN_BYTES + addressInBytes(src) + op.immediateSize(size);
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        if (crb != null) {
            if (inlinedConstant != null) {
                crb.recordInlineDataInCode(inlinedConstant);
            }
            if (state != null) {
                crb.recordImplicitException(position(), state);
            }
        }
        op.emit(this, size, src, imm32, inlinedConstant != null);
        assert beforeFusedPair + bytesToEmit == position();
        jcc(cc, branchTarget, isShortJmp);
        assert useBranchesWithin32ByteBoundary && !mayCrossBoundary(beforeFusedPair, position());
    }

    public final int cmpAndJcc(OperandSize size, Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        final AMD64RMOp op = CMP.getRMOpcode(size);
        final int bytesToEmit = getPrefixInBytes(size, src1, op.dstIsByte, src2, op.srcIsByte) + OPCODE_IN_BYTES + MODRM_IN_BYTES;
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        op.emit(this, size, src1, src2);
        final int beforeJcc = position();
        assert beforeFusedPair + bytesToEmit == beforeJcc;
        jcc(cc, branchTarget, isShortJmp);
        assert useBranchesWithin32ByteBoundary && !mayCrossBoundary(beforeFusedPair, position());
        return beforeJcc;
    }

    public final void cmplAndJcc(Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        cmpAndJcc(DWORD, src1, src2, cc, branchTarget, isShortJmp);
    }

    public final int cmpqAndJcc(Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        return cmpAndJcc(QWORD, src1, src2, cc, branchTarget, isShortJmp);
    }

    public final int cmpAndJcc(OperandSize size, Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp, CompilationResultBuilder crb, LIRFrameState state) {
        final AMD64RMOp op = CMP.getRMOpcode(size);
        final int bytesToEmit = getPrefixInBytes(size, src1, op.dstIsByte, src2) + OPCODE_IN_BYTES + addressInBytes(src2);
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        if (crb != null && state != null) {
            crb.recordImplicitException(position(), state);
        }
        op.emit(this, size, src1, src2);
        final int beforeJcc = position();
        assert beforeFusedPair + bytesToEmit == beforeJcc;
        jcc(cc, branchTarget, isShortJmp);
        assert useBranchesWithin32ByteBoundary && !mayCrossBoundary(beforeFusedPair, position());
        return beforeJcc;
    }

    public final void cmplAndJcc(Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        cmpAndJcc(DWORD, src1, src2, cc, branchTarget, isShortJmp, null, null);
    }

    public final int cmpqAndJcc(Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        return cmpAndJcc(QWORD, src1, src2, cc, branchTarget, isShortJmp, null, null);
    }

    public final void cmpAndJcc(OperandSize size, Register src1, Constant src2, ConditionFlag cc, Label branchTarget, CompilationResultBuilder crb) {
        AMD64Address src2AsAddress = (AMD64Address) crb.recordDataReferenceInCode(src2, size.getBytes());
        cmpAndJcc(size, src1, src2AsAddress, cc, branchTarget, false, crb, null);
    }

    public final void andlAndJcc(Register dst, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        final AMD64MIOp op = AND.getMIOpcode(DWORD, isByte(imm32));
        final int bytesToEmit = getPrefixInBytes(DWORD, dst, op.srcIsByte) + OPCODE_IN_BYTES + MODRM_IN_BYTES + op.immediateSize(DWORD);
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        op.emit(this, DWORD, dst, imm32);
        assert beforeFusedPair + bytesToEmit == position();
        jcc(cc, branchTarget, isShortJmp);
        assert useBranchesWithin32ByteBoundary && !mayCrossBoundary(beforeFusedPair, position());
    }

    public final void addqAndJcc(Register dst, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        final AMD64MIOp op = ADD.getMIOpcode(QWORD, isByte(imm32));
        final int bytesToEmit = getPrefixInBytes(QWORD, dst, op.srcIsByte) + OPCODE_IN_BYTES + MODRM_IN_BYTES + op.immediateSize(QWORD);
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        op.emit(this, QWORD, dst, imm32);
        assert beforeFusedPair + bytesToEmit == position();
        jcc(cc, branchTarget, isShortJmp);
        assert useBranchesWithin32ByteBoundary && !mayCrossBoundary(beforeFusedPair, position());
    }

    public final void subAndJcc(OperandSize size, Register dst, Register src, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        final AMD64RMOp op = SUB.getRMOpcode(size);
        final int bytesToEmit = getPrefixInBytes(size, dst, op.dstIsByte, src, op.srcIsByte) + OPCODE_IN_BYTES + MODRM_IN_BYTES;
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        op.emit(this, size, dst, src);
        assert beforeFusedPair + bytesToEmit == position();
        jcc(cc, branchTarget, isShortJmp);
        assert useBranchesWithin32ByteBoundary && !mayCrossBoundary(beforeFusedPair, position());
    }

    public final void sublAndJcc(Register dst, Register src, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        subAndJcc(DWORD, dst, src, cc, branchTarget, isShortJmp);
    }

    public final void subqAndJcc(Register dst, Register src, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        subAndJcc(QWORD, dst, src, cc, branchTarget, isShortJmp);
    }

    public final void subAndJcc(OperandSize size, Register dst, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        final AMD64MIOp op = SUB.getMIOpcode(size, isByte(imm32));
        final int bytesToEmit = getPrefixInBytes(size, dst, op.srcIsByte) + OPCODE_IN_BYTES + MODRM_IN_BYTES + op.immediateSize(size);
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        op.emit(this, size, dst, imm32);
        assert beforeFusedPair + bytesToEmit == position();
        jcc(cc, branchTarget, isShortJmp);
        assert useBranchesWithin32ByteBoundary && !mayCrossBoundary(beforeFusedPair, position());
    }

    public final void sublAndJcc(Register dst, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        subAndJcc(DWORD, dst, imm32, cc, branchTarget, isShortJmp);
    }

    public final void subqAndJcc(Register dst, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        subAndJcc(QWORD, dst, imm32, cc, branchTarget, isShortJmp);
    }

    public final void incqAndJcc(Register dst, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        final AMD64MOp op = INC;
        final int bytesToEmit = getPrefixInBytes(QWORD, dst, op.srcIsByte) + OPCODE_IN_BYTES + MODRM_IN_BYTES;
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        op.emit(this, QWORD, dst);
        assert beforeFusedPair + bytesToEmit == position();
        jcc(cc, branchTarget, isShortJmp);
        assert useBranchesWithin32ByteBoundary && !mayCrossBoundary(beforeFusedPair, position());
    }

    public final void decqAndJcc(Register dst, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        final AMD64MOp op = DEC;
        final int bytesToEmit = getPrefixInBytes(QWORD, dst, op.srcIsByte) + OPCODE_IN_BYTES + MODRM_IN_BYTES;
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        op.emit(this, QWORD, dst);
        assert beforeFusedPair + bytesToEmit == position();
        jcc(cc, branchTarget, isShortJmp);
        assert useBranchesWithin32ByteBoundary && !mayCrossBoundary(beforeFusedPair, position());
    }

}
