/*
 * Copyright (c) 2009, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.XOR;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64MOp.DEC;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64MOp.INC;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.DWORD;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.QWORD;
import static org.graalvm.compiler.core.common.NumUtil.isByte;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;

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

    public AMD64MacroAssembler(TargetDescription target, OptionValues optionValues, boolean hasIntelJccErratum) {
        super(target, optionValues, hasIntelJccErratum);
    }

    public final void decrementq(Register reg) {
        decrementq(reg, 1);
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

    public final void incrementq(Register reg) {
        incrementq(reg, 1);
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
            AMD64Address high = new AMD64Address(dst.getBase(), dst.getIndex(), dst.getScale(), dst.getDisplacement() + 4, dst.getDisplacementAnnotation(), dst.instructionStartPosition);
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

    public final void flog(Register dest, Register value, boolean base10, AMD64Address tmp) {
        if (base10) {
            fldlg2();
        } else {
            fldln2();
        }
        trigPrologue(value, tmp);
        fyl2x();
        trigEpilogue(dest, tmp);
    }

    public final void fsin(Register dest, Register value, AMD64Address tmp) {
        trigPrologue(value, tmp);
        fsin();
        trigEpilogue(dest, tmp);
    }

    public final void fcos(Register dest, Register value, AMD64Address tmp) {
        trigPrologue(value, tmp);
        fcos();
        trigEpilogue(dest, tmp);
    }

    public final void ftan(Register dest, Register value, AMD64Address tmp) {
        trigPrologue(value, tmp);
        fptan();
        fstp(0); // ftan pushes 1.0 in addition to the actual result, pop
        trigEpilogue(dest, tmp);
    }

    public final void fpop() {
        ffree(0);
        fincstp();
    }

    private void trigPrologue(Register value, AMD64Address tmp) {
        assert value.getRegisterCategory().equals(AMD64.XMM);
        movdbl(tmp, value);
        fldd(tmp);
    }

    private void trigEpilogue(Register dest, AMD64Address tmp) {
        assert dest.getRegisterCategory().equals(AMD64.XMM);
        fstpd(tmp);
        movdbl(dest, tmp);
    }

    /**
     * Emits alignment before a direct call to a fixed address. The alignment consists of two parts:
     * 1) when {@code align} is true, the fixed address, i.e., the displacement of the call
     * instruction, should be aligned to 4 bytes; 2) when {@code useBranchesWithin32ByteBoundary} is
     * true, the call instruction should be aligned with 32-bytes boundary.
     *
     * @param prefixInstructionSize size of the additional instruction to be emitted before the call
     *            instruction. This is used in HotSpot inline cache convention where a movq
     *            instruction of the cached receiver type to {@code rax} register must be emitted
     *            before the call instruction.
     */
    public void alignBeforeCall(boolean align, int prefixInstructionSize) {
        emitAlignmentForDirectCall(align, prefixInstructionSize);
        if (mitigateJCCErratum(position() + prefixInstructionSize, 5) != 0) {
            // If JCC erratum padding was emitted, the displacement may be unaligned again. The
            // first call to emitAlignmentForDirectCall is essential as it may trigger the
            // JCC erratum padding.
            emitAlignmentForDirectCall(align, prefixInstructionSize);
        }
    }

    private void emitAlignmentForDirectCall(boolean align, int additionalInstructionSize) {
        if (align) {
            // make sure that the 4-byte call displacement will be 4-byte aligned
            int displacementPos = position() + getMachineCodeCallDisplacementOffset() + additionalInstructionSize;
            if (displacementPos % 4 != 0) {
                nop(4 - displacementPos % 4);
            }
        }
    }

    private static final int DIRECT_CALL_INSTRUCTION_CODE = 0xE8;
    private static final int DIRECT_CALL_INSTRUCTION_SIZE = 5;

    /**
     * Emits an indirect call instruction.
     */
    public final int indirectCall(Register callReg) {
        return indirectCall(callReg, false);
    }

    /**
     * Emits an indirect call instruction.
     *
     * The {@code NativeCall::is_call_before(address pc)} function in HotSpot determines that there
     * is a direct call instruction whose last byte is at {@code pc - 1} if the byte at
     * {@code pc - 5} is 0xE8. An indirect call can thus be incorrectly decoded as a direct call if
     * the preceding instructions match this pattern. To avoid this,
     * {@code mitigateDecodingAsDirectCall == true} will insert sufficient nops to avoid the false
     * decoding.
     *
     * @return the position of the emitted call instruction
     */
    public final int indirectCall(Register callReg, boolean mitigateDecodingAsDirectCall) {
        int indirectCallSize = needsRex(callReg) ? 3 : 2;
        int insertedNops = mitigateJCCErratum(indirectCallSize);

        if (mitigateDecodingAsDirectCall) {
            int indirectCallPos = position();
            int directCallPos = indirectCallPos - (DIRECT_CALL_INSTRUCTION_SIZE - indirectCallSize);
            if (directCallPos < 0 || getByte(directCallPos) == DIRECT_CALL_INSTRUCTION_CODE) {
                // the previous insertedNops bytes can be trusted -- we assume none of our nops
                // include 0xe8.
                int prefixNops = DIRECT_CALL_INSTRUCTION_SIZE - indirectCallSize - insertedNops;
                if (prefixNops > 0) {
                    nop(prefixNops);
                }
            }
        }

        int beforeCall = position();
        call(callReg);
        assert beforeCall + indirectCallSize == position();
        if (mitigateDecodingAsDirectCall) {
            int directCallPos = position() - DIRECT_CALL_INSTRUCTION_SIZE;
            GraalError.guarantee(directCallPos >= 0 && getByte(directCallPos) != DIRECT_CALL_INSTRUCTION_CODE,
                            "This indirect call can be decoded as a direct call.");
        }
        return beforeCall;
    }

    public final int directCall(long address, Register scratch) {
        int bytesToEmit = needsRex(scratch) ? 13 : 12;
        mitigateJCCErratum(bytesToEmit);
        int beforeCall = position();
        movq(scratch, address);
        call(scratch);
        assert beforeCall + bytesToEmit == position();
        return beforeCall;
    }

    public final int directJmp(long address, Register scratch) {
        int bytesToEmit = needsRex(scratch) ? 13 : 12;
        mitigateJCCErratum(bytesToEmit);
        int beforeJmp = position();
        movq(scratch, address);
        jmpWithoutAlignment(scratch);
        assert beforeJmp + bytesToEmit == position();
        return beforeJmp;
    }

    // This should guarantee that the alignment in AMD64Assembler.jcc methods will be not triggered.
    private void alignFusedPair(Label branchTarget, boolean isShortJmp, int prevOpInBytes) {
        assert prevOpInBytes < 26 : "Fused pair may be longer than 0x20 bytes.";
        if (branchTarget == null) {
            mitigateJCCErratum(prevOpInBytes + 6);
        } else if (isShortJmp) {
            mitigateJCCErratum(prevOpInBytes + 2);
        } else if (!branchTarget.isBound()) {
            mitigateJCCErratum(prevOpInBytes + 6);
        } else {
            long disp = branchTarget.position() - (position() + prevOpInBytes);
            // assuming short jump first
            if (isByte(disp - 2)) {
                mitigateJCCErratum(prevOpInBytes + 2);
                // After alignment, isByte(disp - shortSize) might not hold. Need to check
                // again.
                disp = branchTarget.position() - (position() + prevOpInBytes);
                if (isByte(disp - 2)) {
                    return;
                }
            }
            mitigateJCCErratum(prevOpInBytes + 6);
        }
    }

    private void applyMIOpAndJcc(AMD64MIOp op, OperandSize size, Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp, boolean annotateImm,
                    IntConsumer applyBeforeFusedPair) {
        final int bytesToEmit = getPrefixInBytes(size, src, op.srcIsByte) + OPCODE_IN_BYTES + MODRM_IN_BYTES + op.immediateSize(size);
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        if (applyBeforeFusedPair != null) {
            applyBeforeFusedPair.accept(beforeFusedPair);
        }
        op.emit(this, size, src, imm32, annotateImm);
        assert beforeFusedPair + bytesToEmit == position();
        jcc(cc, branchTarget, isShortJmp);
        assert ensureWithinBoundary(beforeFusedPair);
    }

    private void applyMIOpAndJcc(AMD64MIOp op, OperandSize size, AMD64Address src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp, boolean annotateImm,
                    IntConsumer applyBeforeFusedPair) {
        final int bytesToEmit = getPrefixInBytes(size, src) + OPCODE_IN_BYTES + addressInBytes(src) + op.immediateSize(size);
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        if (applyBeforeFusedPair != null) {
            applyBeforeFusedPair.accept(beforeFusedPair);
        }
        op.emit(this, size, src, imm32, annotateImm);
        assert beforeFusedPair + bytesToEmit == position();
        jcc(cc, branchTarget, isShortJmp);
        assert ensureWithinBoundary(beforeFusedPair);
    }

    private int applyRMOpAndJcc(AMD64RMOp op, OperandSize size, Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        final int bytesToEmit = getPrefixInBytes(size, src1, op.dstIsByte, src2, op.srcIsByte) + OPCODE_IN_BYTES + MODRM_IN_BYTES;
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        op.emit(this, size, src1, src2);
        final int beforeJcc = position();
        assert beforeFusedPair + bytesToEmit == beforeJcc;
        jcc(cc, branchTarget, isShortJmp);
        assert ensureWithinBoundary(beforeFusedPair);
        return beforeJcc;
    }

    private int applyRMOpAndJcc(AMD64RMOp op, OperandSize size, Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp, IntConsumer applyBeforeFusedPair) {
        final int bytesToEmit = getPrefixInBytes(size, src1, op.dstIsByte, src2) + OPCODE_IN_BYTES + addressInBytes(src2);
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        if (applyBeforeFusedPair != null) {
            applyBeforeFusedPair.accept(beforeFusedPair);
        }
        op.emit(this, size, src1, src2);
        final int beforeJcc = position();
        assert beforeFusedPair + bytesToEmit == beforeJcc;
        jcc(cc, branchTarget, isShortJmp);
        assert ensureWithinBoundary(beforeFusedPair);
        return beforeJcc;
    }

    public void applyMOpAndJcc(AMD64MOp op, OperandSize size, Register dst, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        final int bytesToEmit = getPrefixInBytes(size, dst, op.srcIsByte) + OPCODE_IN_BYTES + MODRM_IN_BYTES;
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        op.emit(this, size, dst);
        assert beforeFusedPair + bytesToEmit == position();
        jcc(cc, branchTarget, isShortJmp);
        assert ensureWithinBoundary(beforeFusedPair);
    }

    public final void testAndJcc(OperandSize size, Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMIOpAndJcc(AMD64MIOp.TEST, size, src, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final void testlAndJcc(Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMIOpAndJcc(AMD64MIOp.TEST, DWORD, src, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final void testAndJcc(OperandSize size, AMD64Address src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp, IntConsumer applyBeforeFusedPair) {
        applyMIOpAndJcc(AMD64MIOp.TEST, size, src, imm32, cc, branchTarget, isShortJmp, false, applyBeforeFusedPair);
    }

    public final void testAndJcc(OperandSize size, Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyRMOpAndJcc(AMD64RMOp.TEST, size, src1, src2, cc, branchTarget, isShortJmp);
    }

    public final void testlAndJcc(Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyRMOpAndJcc(AMD64RMOp.TEST, DWORD, src1, src2, cc, branchTarget, isShortJmp);
    }

    public final int testqAndJcc(Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        return applyRMOpAndJcc(AMD64RMOp.TEST, QWORD, src1, src2, cc, branchTarget, isShortJmp);
    }

    public final void testAndJcc(OperandSize size, Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyRMOpAndJcc(AMD64RMOp.TEST, size, src1, src2, cc, branchTarget, isShortJmp, null);
    }

    public final void testAndJcc(OperandSize size, Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp, IntConsumer applyBeforeFusedPair) {
        applyRMOpAndJcc(AMD64RMOp.TEST, size, src1, src2, cc, branchTarget, isShortJmp, applyBeforeFusedPair);
    }

    public final void testbAndJcc(Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyRMOpAndJcc(AMD64RMOp.TESTB, OperandSize.BYTE, src1, src2, cc, branchTarget, isShortJmp);
    }

    public final void testbAndJcc(Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyRMOpAndJcc(AMD64RMOp.TESTB, OperandSize.BYTE, src1, src2, cc, branchTarget, isShortJmp, null);
    }

    public final void cmpAndJcc(OperandSize size, Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMIOpAndJcc(CMP.getMIOpcode(size, isByte(imm32)), size, src, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final void cmpAndJcc(OperandSize size, Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp, boolean annotateImm, IntConsumer applyBeforeFusedPair) {
        applyMIOpAndJcc(CMP.getMIOpcode(size, isByte(imm32)), size, src, imm32, cc, branchTarget, isShortJmp, annotateImm, applyBeforeFusedPair);
    }

    public final void cmplAndJcc(Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMIOpAndJcc(CMP.getMIOpcode(DWORD, isByte(imm32)), DWORD, src, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final void cmpqAndJcc(Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMIOpAndJcc(CMP.getMIOpcode(QWORD, isByte(imm32)), QWORD, src, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final void cmpAndJcc(OperandSize size, AMD64Address src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMIOpAndJcc(CMP.getMIOpcode(size, NumUtil.isByte(imm32)), size, src, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final void cmpAndJcc(OperandSize size, AMD64Address src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp, boolean annotateImm, IntConsumer applyBeforeFusedPair) {
        applyMIOpAndJcc(CMP.getMIOpcode(size, NumUtil.isByte(imm32)), size, src, imm32, cc, branchTarget, isShortJmp, annotateImm, applyBeforeFusedPair);
    }

    public final void cmpAndJcc(OperandSize size, Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyRMOpAndJcc(CMP.getRMOpcode(size), size, src1, src2, cc, branchTarget, isShortJmp);
    }

    public final void cmpAndJcc(OperandSize size, Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyRMOpAndJcc(CMP.getRMOpcode(size), size, src1, src2, cc, branchTarget, isShortJmp, null);
    }

    public final void cmplAndJcc(Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyRMOpAndJcc(CMP.getRMOpcode(DWORD), DWORD, src1, src2, cc, branchTarget, isShortJmp);
    }

    public final int cmpqAndJcc(Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        return applyRMOpAndJcc(CMP.getRMOpcode(QWORD), QWORD, src1, src2, cc, branchTarget, isShortJmp);
    }

    public final void cmpAndJcc(OperandSize size, Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp, IntConsumer applyBeforeFusedPair) {
        applyRMOpAndJcc(CMP.getRMOpcode(size), size, src1, src2, cc, branchTarget, isShortJmp, applyBeforeFusedPair);
    }

    public final void cmplAndJcc(Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyRMOpAndJcc(CMP.getRMOpcode(DWORD), DWORD, src1, src2, cc, branchTarget, isShortJmp, null);
    }

    public final int cmpqAndJcc(Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        return applyRMOpAndJcc(CMP.getRMOpcode(QWORD), QWORD, src1, src2, cc, branchTarget, isShortJmp, null);
    }

    public final void cmpAndJcc(OperandSize size, Register src1, Supplier<AMD64Address> src2, ConditionFlag cc, Label branchTarget) {
        AMD64Address placeHolder = getPlaceholder(position());
        final AMD64RMOp op = CMP.getRMOpcode(size);
        final int bytesToEmit = getPrefixInBytes(size, src1, op.dstIsByte, placeHolder) + OPCODE_IN_BYTES + addressInBytes(placeHolder);
        alignFusedPair(branchTarget, false, bytesToEmit);
        final int beforeFusedPair = position();
        AMD64Address src2AsAddress = src2.get();
        op.emit(this, size, src1, src2AsAddress);
        assert beforeFusedPair + bytesToEmit == position();
        jcc(cc, branchTarget, false);
        assert ensureWithinBoundary(beforeFusedPair);
    }

    public final void andlAndJcc(Register dst, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMIOpAndJcc(AND.getMIOpcode(DWORD, isByte(imm32)), DWORD, dst, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final void addqAndJcc(Register dst, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMIOpAndJcc(ADD.getMIOpcode(QWORD, isByte(imm32)), QWORD, dst, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final void sublAndJcc(Register dst, Register src, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyRMOpAndJcc(SUB.getRMOpcode(DWORD), DWORD, dst, src, cc, branchTarget, isShortJmp);
    }

    public final void subqAndJcc(Register dst, Register src, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyRMOpAndJcc(SUB.getRMOpcode(QWORD), QWORD, dst, src, cc, branchTarget, isShortJmp);
    }

    public final void sublAndJcc(Register dst, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMIOpAndJcc(SUB.getMIOpcode(DWORD, isByte(imm32)), DWORD, dst, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final void subqAndJcc(Register dst, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMIOpAndJcc(SUB.getMIOpcode(QWORD, isByte(imm32)), QWORD, dst, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final void incqAndJcc(Register dst, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMOpAndJcc(INC, QWORD, dst, cc, branchTarget, isShortJmp);
    }

    public final void decqAndJcc(Register dst, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMOpAndJcc(DEC, QWORD, dst, cc, branchTarget, isShortJmp);
    }

    public final void xorlAndJcc(Register dst, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMIOpAndJcc(XOR.getMIOpcode(DWORD, isByte(imm32)), DWORD, dst, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public enum ExtendMode {
        ZERO_EXTEND,
        SIGN_EXTEND
    }

    public static void movSZx(AMD64MacroAssembler asm, OperandSize operandSize, ExtendMode extendMode, Register dst, AMD64Address src) {
        movSZx(asm, AMD64Address.Scale.fromInt(operandSize.getBytes()), extendMode, dst, src);
    }

    /**
     * Load one, two, four or eight bytes, according to {@code scaleSrc}, into {@code dst} and zero-
     * or sign-extend depending on {@code extendMode}.
     */
    public static void movSZx(AMD64MacroAssembler asm, AMD64Address.Scale scaleSrc, ExtendMode extendMode, Register dst, AMD64Address src) {
        switch (scaleSrc) {
            case Times1:
                if (extendMode == ExtendMode.SIGN_EXTEND) {
                    asm.movsbq(dst, src);
                } else {
                    asm.movzbq(dst, src);
                }
                break;
            case Times2:
                if (extendMode == ExtendMode.SIGN_EXTEND) {
                    asm.movswq(dst, src);
                } else {
                    asm.movzwq(dst, src);
                }
                break;
            case Times4:
                if (extendMode == ExtendMode.SIGN_EXTEND) {
                    asm.movslq(dst, src);
                } else {
                    // there is no movzlq
                    asm.movl(dst, src);
                }
                break;
            case Times8:
                asm.movq(dst, src);
                break;
            default:
                throw new IllegalStateException();
        }
    }

    public static void pmovSZx(AMD64MacroAssembler asm, AVXSize size, ExtendMode extendMode, Register dst, AMD64Address.Scale scaleDst, Register src, AMD64Address.Scale scaleSrc,
                    int displacement) {
        pmovSZx(asm, size, dst, extendMode, scaleDst, src, scaleSrc, null, displacement);
    }

    /**
     * Load elements from address {@code (src, index, displacement)} into vector register
     * {@code dst}, and zero- or sign-extend them to fit {@code scaleDst}.
     *
     * @param size vector size. May be {@link AVXSize#XMM} or {@link AVXSize#YMM}.
     * @param dst a XMM or YMM vector register.
     * @param scaleDst target stride. Must be greater or equal to {@code scaleSrc}.
     * @param src the source address.
     * @param scaleSrc source stride. Must be smaller or equal to {@code scaleDst}.
     * @param index address index offset, scaled by {@code scaleSrc}.
     * @param displacement address displacement in bytes. If {@code scaleDst} is greater than
     *            {@code scaleSrc}, this displacement is scaled by the ratio of the former and
     *            latter scales, e.g. if {@code scaleDst} is {@link AMD64Address.Scale#Times4} and
     *            {@code scaleSrc} is {@link AMD64Address.Scale#Times2}, the displacement is halved.
     */
    public static void pmovSZx(AMD64MacroAssembler asm, AVXSize size, Register dst, ExtendMode extendMode, AMD64Address.Scale scaleDst, Register src, AMD64Address.Scale scaleSrc, Register index,
                    int displacement) {
        assert size == AVXSize.XMM || size == AVXSize.YMM;
        int scaledDisplacement = scaleDisplacement(scaleDst, scaleSrc, displacement);
        AMD64Address address = index == null ? new AMD64Address(src, scaledDisplacement) : new AMD64Address(src, index, scaleSrc, scaledDisplacement);
        pmovSZx(asm, size, extendMode, dst, scaleDst, address, scaleSrc);
    }

    public static void pmovSZx(AMD64MacroAssembler asm, AVXSize size, ExtendMode extendMode, Register dst, AMD64Address.Scale scaleDst, AMD64Address src, AMD64Address.Scale scaleSrc) {
        if (scaleSrc.value < scaleDst.value) {
            if (isAVX(asm)) {
                loadAndExtendAVX(asm, size, extendMode, dst, scaleDst, src, scaleSrc);
            } else {
                loadAndExtendSSE(asm, extendMode, dst, scaleDst, src, scaleSrc);
            }
        } else {
            assert scaleSrc.value == scaleDst.value;
            movdqu(asm, size, dst, src);
        }
    }

    public static void pmovSZx(AMD64MacroAssembler asm, AVXSize size, ExtendMode extendMode, Register dst, AMD64Address.Scale scaleDst, Register src, AMD64Address.Scale scaleSrc) {
        if (scaleSrc.value < scaleDst.value) {
            if (isAVX(asm)) {
                getAVXLoadAndExtendOp(scaleDst, scaleSrc, extendMode).emit(asm, size, dst, src);
            } else {
                loadAndExtendSSE(asm, extendMode, dst, scaleDst, src, scaleSrc);
            }
        } else {
            assert scaleSrc.value == scaleDst.value;
            movdqu(asm, size, dst, src);
        }
    }

    public static void pmovmsk(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src) {
        if (isAVX(asm)) {
            VexRMOp.VPMOVMSKB.emit(asm, size, dst, src);
        } else {
            // SSE
            asm.pmovmskb(dst, src);
        }
    }

    public static void movdqu(AMD64MacroAssembler asm, AVXSize size, Register dst, AMD64Address src) {
        if (isAVX(asm)) {
            VexMoveOp.VMOVDQU32.emit(asm, size, dst, src);
        } else {
            asm.movdqu(dst, src);
        }
    }

    public static void movdqu(AMD64MacroAssembler asm, AVXSize size, AMD64Address dst, Register src) {
        if (isAVX(asm)) {
            VexMoveOp.VMOVDQU32.emit(asm, size, dst, src);
        } else {
            asm.movdqu(dst, src);
        }
    }

    public static void movdqu(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src) {
        if (isAVX(asm)) {
            VexMoveOp.VMOVDQU32.emit(asm, size, dst, src);
        } else {
            asm.movdqu(dst, src);
        }
    }

    /**
     * Compares all packed bytes/words/dwords in {@code dst} to {@code src}. Matching values are set
     * to all ones (0xff, 0xffff, ...), non-matching values are set to zero.
     */
    public static void pcmpeq(AMD64MacroAssembler asm, AVXSize vectorSize, AMD64Address.Scale elementStride, Register dst, Register src) {
        pcmpeq(asm, vectorSize, elementStride.value, dst, src);
    }

    /**
     * Compares all packed bytes/words/dwords in {@code dst} to {@code src}. Matching values are set
     * to all ones (0xff, 0xffff, ...), non-matching values are set to zero.
     */
    public static void pcmpeq(AMD64MacroAssembler asm, AVXSize vectorSize, JavaKind elementKind, Register dst, Register src) {
        pcmpeq(asm, vectorSize, elementKind.getByteCount(), dst, src);
    }

    private static void pcmpeq(AMD64MacroAssembler asm, AVXSize vectorSize, int elementSize, Register dst, Register src) {
        switch (elementSize) {
            case 1:
                pcmpeqb(asm, vectorSize, dst, src);
                break;
            case 2:
                pcmpeqw(asm, vectorSize, dst, src);
                break;
            case 4:
                pcmpeqd(asm, vectorSize, dst, src);
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    public static void pcmpeqb(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src) {
        if (isAVX(asm)) {
            VexRVMOp.VPCMPEQB.emit(asm, size, dst, src, dst);
        } else { // SSE
            asm.pcmpeqb(dst, src);
        }
    }

    public static void pcmpeqw(AMD64MacroAssembler asm, AVXSize vectorSize, Register dst, Register src) {
        if (isAVX(asm)) {
            VexRVMOp.VPCMPEQW.emit(asm, vectorSize, dst, src, dst);
        } else { // SSE
            asm.pcmpeqw(dst, src);
        }
    }

    public static void pcmpeqd(AMD64MacroAssembler asm, AVXSize vectorSize, Register dst, Register src) {
        if (isAVX(asm)) {
            VexRVMOp.VPCMPEQD.emit(asm, vectorSize, dst, src, dst);
        } else { // SSE
            asm.pcmpeqd(dst, src);
        }
    }

    /**
     * Compares all packed bytes/words/dwords in {@code dst} to {@code src}. Matching values are set
     * to all ones (0xff, 0xffff, ...), non-matching values are set to zero.
     */
    public static void pcmpeq(AMD64MacroAssembler asm, AVXSize size, AMD64Address.Scale elementStride, Register dst, AMD64Address src) {
        pcmpeq(asm, size, elementStride.value, dst, src);
    }

    /**
     * Compares all packed bytes/words/dwords in {@code dst} to {@code src}. Matching values are set
     * to all ones (0xff, 0xffff, ...), non-matching values are set to zero.
     */
    public static void pcmpeq(AMD64MacroAssembler asm, AVXSize size, JavaKind elementKind, Register dst, AMD64Address src) {
        pcmpeq(asm, size, elementKind.getByteCount(), dst, src);
    }

    private static void pcmpeq(AMD64MacroAssembler asm, AVXSize vectorSize, int elementSize, Register dst, AMD64Address src) {
        switch (elementSize) {
            case 1:
                pcmpeqb(asm, vectorSize, dst, src);
                break;
            case 2:
                pcmpeqw(asm, vectorSize, dst, src);
                break;
            case 4:
                pcmpeqd(asm, vectorSize, dst, src);
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    public static void pcmpeqb(AMD64MacroAssembler asm, AVXSize size, Register dst, AMD64Address src) {
        if (isAVX(asm)) {
            VexRVMOp.VPCMPEQB.emit(asm, size, dst, dst, src);
        } else { // SSE
            asm.pcmpeqb(dst, src);
        }
    }

    public static void pcmpeqw(AMD64MacroAssembler asm, AVXSize size, Register dst, AMD64Address src) {
        if (isAVX(asm)) {
            VexRVMOp.VPCMPEQW.emit(asm, size, dst, dst, src);
        } else { // SSE
            asm.pcmpeqw(dst, src);
        }
    }

    public static void pcmpeqd(AMD64MacroAssembler asm, AVXSize size, Register dst, AMD64Address src) {
        if (isAVX(asm)) {
            VexRVMOp.VPCMPEQD.emit(asm, size, dst, dst, src);
        } else { // SSE
            asm.pcmpeqd(dst, src);
        }
    }

    public static void pcmpgtb(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src) {
        if (isAVX(asm)) {
            VexRVMOp.VPCMPGTB.emit(asm, size, dst, dst, src);
        } else { // SSE
            asm.pcmpgtb(dst, src);
        }
    }

    public static void pcmpgtd(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src) {
        if (isAVX(asm)) {
            VexRVMOp.VPCMPGTD.emit(asm, size, dst, dst, src);
        } else { // SSE
            asm.pcmpgtd(dst, src);
        }
    }

    private static int scaleDisplacement(AMD64Address.Scale scaleDst, AMD64Address.Scale scaleSrc, int displacement) {
        if (scaleSrc.value < scaleDst.value) {
            assert (displacement & ((1 << (scaleDst.log2 - scaleSrc.log2)) - 1)) == 0;
            return displacement >> (scaleDst.log2 - scaleSrc.log2);
        }
        assert scaleSrc.value == scaleDst.value;
        return displacement;
    }

    public static void loadAndExtendAVX(AMD64MacroAssembler asm, AVXSize size, ExtendMode extendMode, Register dst, AMD64Address.Scale scaleDst, AMD64Address src, AMD64Address.Scale scaleSrc) {
        getAVXLoadAndExtendOp(scaleDst, scaleSrc, extendMode).emit(asm, size, dst, src);
    }

    private static VexRMOp getAVXLoadAndExtendOp(AMD64Address.Scale scaleDst, AMD64Address.Scale scaleSrc, ExtendMode extendMode) {
        switch (scaleSrc) {
            case Times1:
                switch (scaleDst) {
                    case Times2:
                        return extendMode == ExtendMode.SIGN_EXTEND ? VexRMOp.VPMOVSXBW : VexRMOp.VPMOVZXBW;
                    case Times4:
                        return extendMode == ExtendMode.SIGN_EXTEND ? VexRMOp.VPMOVSXBD : VexRMOp.VPMOVZXBD;
                    case Times8:
                        return extendMode == ExtendMode.SIGN_EXTEND ? VexRMOp.VPMOVSXBQ : VexRMOp.VPMOVZXBQ;
                }
                throw GraalError.shouldNotReachHere();
            case Times2:
                switch (scaleDst) {
                    case Times4:
                        return extendMode == ExtendMode.SIGN_EXTEND ? VexRMOp.VPMOVSXWD : VexRMOp.VPMOVZXWD;
                    case Times8:
                        return extendMode == ExtendMode.SIGN_EXTEND ? VexRMOp.VPMOVSXWQ : VexRMOp.VPMOVZXWQ;
                }
                throw GraalError.shouldNotReachHere();
            case Times4:
                return extendMode == ExtendMode.SIGN_EXTEND ? VexRMOp.VPMOVSXDQ : VexRMOp.VPMOVZXDQ;
        }
        throw GraalError.shouldNotReachHere();
    }

    public static void loadAndExtendSSE(AMD64MacroAssembler asm, ExtendMode extendMode, Register dst, AMD64Address.Scale scaleDst, AMD64Address src, AMD64Address.Scale scaleSrc) {
        boolean signExtend = extendMode == ExtendMode.SIGN_EXTEND;
        switch (scaleSrc) {
            case Times1:
                switch (scaleDst) {
                    case Times2:
                        if (signExtend) {
                            asm.pmovsxbw(dst, src);
                        } else {
                            asm.pmovzxbw(dst, src);
                        }
                        return;
                    case Times4:
                        if (signExtend) {
                            asm.pmovsxbd(dst, src);
                        } else {
                            asm.pmovzxbd(dst, src);
                        }
                        return;
                    case Times8:
                        if (signExtend) {
                            asm.pmovsxbq(dst, src);
                        } else {
                            asm.pmovzxbq(dst, src);
                        }
                        return;
                }
                throw GraalError.shouldNotReachHere();
            case Times2:
                switch (scaleDst) {
                    case Times4:
                        if (signExtend) {
                            asm.pmovsxwd(dst, src);
                        } else {
                            asm.pmovzxwd(dst, src);
                        }
                        return;
                    case Times8:
                        if (signExtend) {
                            asm.pmovsxwq(dst, src);
                        } else {
                            asm.pmovzxwq(dst, src);
                        }
                        return;
                }
                throw GraalError.shouldNotReachHere();
            case Times4:
                if (signExtend) {
                    asm.pmovsxdq(dst, src);
                } else {
                    asm.pmovzxdq(dst, src);
                }
                return;
        }
        throw GraalError.shouldNotReachHere();
    }

    public static void loadAndExtendSSE(AMD64MacroAssembler asm, ExtendMode extendMode, Register dst, AMD64Address.Scale scaleDst, Register src, AMD64Address.Scale scaleSrc) {
        boolean signExtend = extendMode == ExtendMode.SIGN_EXTEND;
        switch (scaleSrc) {
            case Times1:
                switch (scaleDst) {
                    case Times2:
                        if (signExtend) {
                            asm.pmovsxbw(dst, src);
                        } else {
                            asm.pmovzxbw(dst, src);
                        }
                        return;
                    case Times4:
                        if (signExtend) {
                            asm.pmovsxbd(dst, src);
                        } else {
                            asm.pmovzxbd(dst, src);
                        }
                        return;
                    case Times8:
                        if (signExtend) {
                            asm.pmovsxbq(dst, src);
                        } else {
                            asm.pmovzxbq(dst, src);
                        }
                        return;
                }
                throw GraalError.shouldNotReachHere();
            case Times2:
                switch (scaleDst) {
                    case Times4:
                        if (signExtend) {
                            asm.pmovsxwd(dst, src);
                        } else {
                            asm.pmovzxwd(dst, src);
                        }
                        return;
                    case Times8:
                        if (signExtend) {
                            asm.pmovsxwq(dst, src);
                        } else {
                            asm.pmovzxwq(dst, src);
                        }
                        return;
                }
                throw GraalError.shouldNotReachHere();
            case Times4:
                if (signExtend) {
                    asm.pmovsxdq(dst, src);
                } else {
                    asm.pmovzxdq(dst, src);
                }
                return;
        }
        throw GraalError.shouldNotReachHere();
    }

    public static void packuswb(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src) {
        if (isAVX(asm)) {
            VexRVMOp.VPACKUSWB.emit(asm, size, dst, dst, src);
        } else {
            asm.packuswb(dst, src);
        }
    }

    public static void packusdw(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src) {
        if (isAVX(asm)) {
            VexRVMOp.VPACKUSDW.emit(asm, size, dst, dst, src);
        } else {
            asm.packusdw(dst, src);
        }
    }

    public static void palignr(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src, int imm8) {
        palignr(asm, size, dst, dst, src, imm8);
    }

    public static void palignr(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src1, Register src2, int imm8) {
        if (isAVX(asm)) {
            VexRVMIOp.VPALIGNR.emit(asm, size, dst, src1, src2, imm8);
        } else {
            // SSE
            if (!dst.equals(src1)) {
                asm.movdqu(dst, src1);
            }
            asm.palignr(dst, src2, imm8);
        }
    }

    public static void pand(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src) {
        pand(asm, size, dst, dst, src);
    }

    public static void pand(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src1, Register src2) {
        if (isAVX(asm)) {
            VexRVMOp.VPAND.emit(asm, size, dst, src1, src2);
        } else {
            // SSE
            if (!dst.equals(src1)) {
                asm.movdqu(dst, src1);
            }
            asm.pand(dst, src2);
        }
    }

    public static void pand(AMD64MacroAssembler asm, AVXSize size, Register dst, AMD64Address src) {
        if (isAVX(asm)) {
            VexRVMOp.VPAND.emit(asm, size, dst, dst, src);
        } else {
            // SSE
            asm.pand(dst, src);
        }
    }

    /**
     * PAND with unaligned memory operand.
     */
    public static void pandU(AMD64MacroAssembler asm, AVXSize size, Register dst, AMD64Address src, Register tmp) {
        if (isAVX(asm)) {
            VexRVMOp.VPAND.emit(asm, size, dst, dst, src);
        } else {
            // SSE
            asm.movdqu(tmp, src);
            asm.pand(dst, tmp);
        }
    }

    public static void pandn(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src) {
        if (isAVX(asm)) {
            VexRVMOp.VPANDN.emit(asm, size, dst, dst, src);
        } else {
            // SSE
            asm.pandn(dst, src);
        }
    }

    public static void por(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src) {
        if (isAVX(asm)) {
            VexRVMOp.VPOR.emit(asm, size, dst, dst, src);
        } else {
            asm.por(dst, src);
        }
    }

    public static void pxor(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src) {
        pxor(asm, size, dst, dst, src);
    }

    public static void pxor(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src1, Register src2) {
        if (isAVX(asm)) {
            VexRVMOp.VPXOR.emit(asm, size, dst, src1, src2);
        } else {
            if (!dst.equals(src1)) {
                asm.movdqu(dst, src1);
            }
            asm.pxor(dst, src2);
        }
    }

    public static void psllw(AMD64MacroAssembler asm, AVXSize size, Register dst, int imm8) {
        psllw(asm, size, dst, dst, imm8);
    }

    public static void psllw(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src, int imm8) {
        if (isAVX(asm)) {
            VexShiftOp.VPSLLW.emit(asm, size, dst, src, imm8);
        } else {
            // SSE
            if (!dst.equals(src)) {
                asm.movdqu(dst, src);
            }
            asm.psllw(dst, imm8);
        }
    }

    public static void psrlw(AMD64MacroAssembler asm, AVXSize size, Register dst, int imm8) {
        psrlw(asm, size, dst, dst, imm8);
    }

    public static void psrlw(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src, int imm8) {
        if (isAVX(asm)) {
            VexShiftOp.VPSRLW.emit(asm, size, dst, src, imm8);
        } else {
            // SSE
            if (!dst.equals(src)) {
                asm.movdqu(dst, src);
            }
            asm.psrlw(dst, imm8);
        }
    }

    public static void pslld(AMD64MacroAssembler asm, AVXSize size, Register dst, int imm8) {
        pslld(asm, size, dst, dst, imm8);
    }

    public static void pslld(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src, int imm8) {
        if (isAVX(asm)) {
            VexShiftOp.VPSLLD.emit(asm, size, dst, src, imm8);
        } else {
            // SSE
            if (!dst.equals(src)) {
                asm.movdqu(dst, src);
            }
            asm.pslld(dst, imm8);
        }
    }

    public static void psrld(AMD64MacroAssembler asm, AVXSize size, Register dst, int imm8) {
        psrld(asm, size, dst, dst, imm8);
    }

    public static void psrld(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src, int imm8) {
        if (isAVX(asm)) {
            VexShiftOp.VPSRLD.emit(asm, size, dst, src, imm8);
        } else {
            // SSE
            if (!dst.equals(src)) {
                asm.movdqu(dst, src);
            }
            asm.psrld(dst, imm8);
        }
    }

    public static void pshufb(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src) {
        if (isAVX(asm)) {
            VexRVMOp.VPSHUFB.emit(asm, size, dst, dst, src);
        } else {
            // SSE
            asm.pshufb(dst, src);
        }
    }

    public static void pshufb(AMD64MacroAssembler asm, AVXSize size, Register dst, AMD64Address src) {
        if (isAVX(asm)) {
            VexRVMOp.VPSHUFB.emit(asm, size, dst, dst, src);
        } else {
            // SSE
            asm.pshufb(dst, src);
        }
    }

    public static void ptest(AMD64MacroAssembler asm, AVXSize size, Register dst) {
        ptest(asm, size, dst, dst);
    }

    public static void ptest(AMD64MacroAssembler asm, AVXSize size, Register dst, Register src) {
        if (isAVX(asm)) {
            VexRMOp.VPTEST.emit(asm, size, dst, src);
        } else {
            asm.ptest(dst, src);
        }
    }

    /**
     * PTEST with unaligned memory operand.
     */
    public static void ptestU(AMD64MacroAssembler asm, AVXSize size, Register dst, AMD64Address src, Register tmp) {
        if (isAVX(asm)) {
            VexRMOp.VPTEST.emit(asm, size, dst, src);
        } else {
            asm.movdqu(tmp, src);
            asm.ptest(dst, tmp);
        }
    }

    public static void movlhps(AMD64MacroAssembler asm, Register dst, Register src) {
        if (isAVX(asm)) {
            VexRVMOp.MOVLHPS.emit(asm, AVXSize.XMM, dst, dst, src);
        } else {
            asm.movlhps(dst, src);
        }
    }

    public static void movdl(AMD64MacroAssembler asm, Register dst, Register src) {
        if (isAVX(asm)) {
            VexMoveOp.VMOVD.emit(asm, AVXSize.DWORD, dst, src);
        } else {
            asm.movdl(dst, src);
        }
    }

    public static void movdl(AMD64MacroAssembler asm, Register dst, AMD64Address src) {
        if (isAVX(asm)) {
            VexMoveOp.VMOVD.emit(asm, AVXSize.DWORD, dst, src);
        } else {
            asm.movdl(dst, src);
        }
    }

    public static boolean isAVX(AMD64MacroAssembler asm) {
        return asm.supports(CPUFeature.AVX);
    }

    public static boolean isAVX(AMD64 arch) {
        return arch.getFeatures().contains(CPUFeature.AVX);
    }
}
