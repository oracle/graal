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
import org.graalvm.compiler.core.common.Stride;
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

    public final void incrementq(Register reg, int value) {
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

    public final void movflt(Register dst, Register src) {
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

    public final void movflt(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        if (isAVX512Register(dst)) {
            VexMoveOp.VMOVSS.emit(this, AVXSize.XMM, dst, src);
        } else {
            movss(dst, src);
        }
    }

    public final void movflt(AMD64Address dst, Register src) {
        assert src.getRegisterCategory().equals(AMD64.XMM);
        if (isAVX512Register(src)) {
            VexMoveOp.VMOVSS.emit(this, AVXSize.XMM, dst, src);
        } else {
            movss(dst, src);
        }
    }

    public final void movdbl(Register dst, Register src) {
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

    public final void movdbl(Register dst, AMD64Address src) {
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

    public final void movdbl(AMD64Address dst, Register src) {
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
    public final void alignBeforeCall(boolean align, int prefixInstructionSize) {
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

    public final void testqAndJcc(Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMIOpAndJcc(AMD64MIOp.TEST, QWORD, src, imm32, cc, branchTarget, isShortJmp, false, null);
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

    public final void testqAndJcc(Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyRMOpAndJcc(AMD64RMOp.TEST, QWORD, src1, src2, cc, branchTarget, isShortJmp);
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

    public final void andqAndJcc(Register dst, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMIOpAndJcc(AND.getMIOpcode(QWORD, isByte(imm32)), QWORD, dst, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final void addlAndJcc(Register dst, Register src, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyRMOpAndJcc(ADD.getRMOpcode(DWORD), DWORD, dst, src, cc, branchTarget, isShortJmp);
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

    public final void inclAndJcc(Register dst, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMOpAndJcc(INC, DWORD, dst, cc, branchTarget, isShortJmp);
    }

    public final void incqAndJcc(Register dst, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMOpAndJcc(INC, QWORD, dst, cc, branchTarget, isShortJmp);
    }

    public final void declAndJcc(Register dst, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMOpAndJcc(DEC, DWORD, dst, cc, branchTarget, isShortJmp);
    }

    public final void decqAndJcc(Register dst, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMOpAndJcc(DEC, QWORD, dst, cc, branchTarget, isShortJmp);
    }

    public final void xorlAndJcc(Register dst, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyMIOpAndJcc(XOR.getMIOpcode(DWORD, isByte(imm32)), DWORD, dst, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final void xorlAndJcc(Register dst, AMD64Address src, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyRMOpAndJcc(XOR.getRMOpcode(DWORD), DWORD, dst, src, cc, branchTarget, isShortJmp, null);
    }

    public final void xorqAndJcc(Register dst, AMD64Address src, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        applyRMOpAndJcc(XOR.getRMOpcode(QWORD), QWORD, dst, src, cc, branchTarget, isShortJmp, null);
    }

    public enum ExtendMode {
        ZERO_EXTEND,
        SIGN_EXTEND
    }

    public final void movSZx(OperandSize operandSize, ExtendMode extendMode, Register dst, AMD64Address src) {
        movSZx(Stride.fromInt(operandSize.getBytes()), extendMode, dst, src);
    }

    /**
     * Load one, two, four or eight bytes, according to {@code scaleSrc}, into {@code dst} and zero-
     * or sign-extend depending on {@code extendMode}.
     */
    public final void movSZx(Stride strideSrc, ExtendMode extendMode, Register dst, AMD64Address src) {
        switch (strideSrc) {
            case S1:
                if (extendMode == ExtendMode.SIGN_EXTEND) {
                    movsbq(dst, src);
                } else {
                    movzbq(dst, src);
                }
                break;
            case S2:
                if (extendMode == ExtendMode.SIGN_EXTEND) {
                    movswq(dst, src);
                } else {
                    movzwq(dst, src);
                }
                break;
            case S4:
                if (extendMode == ExtendMode.SIGN_EXTEND) {
                    movslq(dst, src);
                } else {
                    // there is no movzlq
                    movl(dst, src);
                }
                break;
            case S8:
                movq(dst, src);
                break;
            default:
                throw new IllegalStateException();
        }
    }

    public final void pmovSZx(AVXSize size, ExtendMode extendMode, Register dst, Stride strideDst, Register src, Stride strideSrc,
                    int displacement) {
        pmovSZx(size, extendMode, dst, strideDst, src, strideSrc, null, displacement);
    }

    /**
     * Load elements from address {@code (src, index, displacement)} into vector register
     * {@code dst}, and zero- or sign-extend them to fit {@code scaleDst}.
     *
     * @param size vector size. May be {@link AVXSize#XMM} or {@link AVXSize#YMM}.
     * @param dst a XMM or YMM vector register.
     * @param strideDst target stride. Must be greater or equal to {@code scaleSrc}.
     * @param src the source address.
     * @param strideSrc source stride. Must be smaller or equal to {@code scaleDst}.
     * @param index address index offset, scaled by {@code scaleSrc}.
     * @param displacement address displacement in bytes. If {@code scaleDst} is greater than
     *            {@code scaleSrc}, this displacement is scaled by the ratio of the former and
     *            latter scales, e.g. if {@code scaleDst} is {@link Stride#S4} and {@code scaleSrc}
     *            is {@link Stride#S2}, the displacement is halved.
     */
    public final void pmovSZx(AVXSize size, ExtendMode extendMode, Register dst, Stride strideDst, Register src, Stride strideSrc, Register index, int displacement) {
        assert size == AVXSize.QWORD || size == AVXSize.XMM || size == AVXSize.YMM;
        int scaledDisplacement = scaleDisplacement(strideDst, strideSrc, displacement);
        AMD64Address address = index == null ? new AMD64Address(src, scaledDisplacement) : new AMD64Address(src, index, strideSrc, scaledDisplacement);
        pmovSZx(size, extendMode, dst, strideDst, address, strideSrc);
    }

    public final void pmovSZx(AVXSize size, ExtendMode extendMode, Register dst, Stride strideDst, AMD64Address src, Stride strideSrc) {
        if (strideSrc.value < strideDst.value) {
            if (size.getBytes() < AVXSize.XMM.getBytes()) {
                movdqu(pmovSZxGetSrcLoadSize(size, strideDst, strideSrc), dst, src);
                if (isAVX()) {
                    loadAndExtendAVX(size, extendMode, dst, strideDst, dst, strideSrc);
                } else {
                    loadAndExtendSSE(extendMode, dst, strideDst, dst, strideSrc);
                }
            } else {
                if (isAVX()) {
                    loadAndExtendAVX(size, extendMode, dst, strideDst, src, strideSrc);
                } else {
                    loadAndExtendSSE(extendMode, dst, strideDst, src, strideSrc);
                }
            }
        } else {
            assert strideSrc.value == strideDst.value;
            movdqu(size, dst, src);
        }
    }

    public final void pmovSZx(AVXSize size, ExtendMode extendMode, Register dst, Stride strideDst, Register src, Stride strideSrc) {
        if (strideSrc.value < strideDst.value) {
            if (size.getBytes() < AVXSize.XMM.getBytes()) {
                movdqu(pmovSZxGetSrcLoadSize(size, strideDst, strideSrc), dst, src);
                if (isAVX()) {
                    loadAndExtendAVX(size, extendMode, dst, strideDst, dst, strideSrc);
                } else {
                    loadAndExtendSSE(extendMode, dst, strideDst, dst, strideSrc);
                }
            } else {
                if (isAVX()) {
                    loadAndExtendAVX(size, extendMode, dst, strideDst, src, strideSrc);
                } else {
                    loadAndExtendSSE(extendMode, dst, strideDst, src, strideSrc);
                }
            }
        } else {
            assert strideSrc.value == strideDst.value;
            movdqu(size, dst, src);
        }
    }

    private static AVXSize pmovSZxGetSrcLoadSize(AVXSize size, Stride strideDst, Stride strideSrc) {
        int srcBytes = size.getBytes() >> (strideDst.log2 - strideSrc.log2);
        switch (srcBytes) {
            case 4:
                return AVXSize.DWORD;
            case 8:
                return AVXSize.QWORD;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    public final void pmovmsk(AVXSize size, Register dst, Register src) {
        if (isAVX()) {
            VexRMOp.VPMOVMSKB.emit(this, size, dst, src);
        } else {
            // SSE
            pmovmskb(dst, src);
        }
    }

    public final void movdqu(AVXSize size, Register dst, AMD64Address src) {
        if (isAVX()) {
            getVMOVOp(size).emit(this, size, dst, src);
        } else {
            switch (size) {
                case DWORD:
                    movdl(dst, src);
                    break;
                case QWORD:
                    movdq(dst, src);
                    break;
                default:
                    movdqu(dst, src);
                    break;
            }
        }
    }

    public final void movdqu(AVXSize size, AMD64Address dst, Register src) {
        if (isAVX()) {
            getVMOVOp(size).emit(this, size, dst, src);
        } else {
            switch (size) {
                case DWORD:
                    SSEMROp.MOVD.emit(this, DWORD, dst, src);
                    break;
                case QWORD:
                    movdq(dst, src);
                    break;
                default:
                    movdqu(dst, src);
                    break;
            }
        }
    }

    public final void movdqu(AVXSize size, Register dst, Register src) {
        if (isAVX()) {
            getVMOVOp(size).emit(this, size, dst, src);
        } else {
            switch (size) {
                case DWORD:
                    movdl(dst, src);
                    break;
                case QWORD:
                    movdq(dst, src);
                    break;
                default:
                    movdqu(dst, src);
                    break;
            }
        }
    }

    private static VexMoveOp getVMOVOp(AVXSize size) {
        switch (size) {
            case DWORD:
                return VexMoveOp.VMOVD;
            case QWORD:
                return VexMoveOp.VMOVQ;
            default:
                return VexMoveOp.VMOVDQU32;
        }
    }

    /**
     * Compares all packed bytes/words/dwords in {@code dst} to {@code src}. Matching values are set
     * to all ones (0xff, 0xffff, ...), non-matching values are set to zero.
     */
    public final void pcmpeq(AVXSize vectorSize, Stride elementStride, Register dst, Register src) {
        pcmpeq(vectorSize, elementStride.value, dst, src);
    }

    /**
     * Compares all packed bytes/words/dwords in {@code dst} to {@code src}. Matching values are set
     * to all ones (0xff, 0xffff, ...), non-matching values are set to zero.
     */
    public final void pcmpeq(AVXSize vectorSize, JavaKind elementKind, Register dst, Register src) {
        pcmpeq(vectorSize, elementKind.getByteCount(), dst, src);
    }

    private void pcmpeq(AVXSize vectorSize, int elementSize, Register dst, Register src) {
        switch (elementSize) {
            case 1:
                pcmpeqb(vectorSize, dst, src);
                break;
            case 2:
                pcmpeqw(vectorSize, dst, src);
                break;
            case 4:
                pcmpeqd(vectorSize, dst, src);
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    public final void pcmpeqw(AVXSize vectorSize, Register dst, Register src) {
        if (isAVX()) {
            VexRVMOp.VPCMPEQW.emit(this, vectorSize, dst, src, dst);
        } else { // SSE
            pcmpeqw(dst, src);
        }
    }

    public final void pcmpeqd(AVXSize vectorSize, Register dst, Register src) {
        if (isAVX()) {
            VexRVMOp.VPCMPEQD.emit(this, vectorSize, dst, src, dst);
        } else { // SSE
            pcmpeqd(dst, src);
        }
    }

    public final void pcmpeqb(AVXSize size, Register dst, Register src) {
        if (isAVX()) {
            VexRVMOp.VPCMPEQB.emit(this, size, dst, src, dst);
        } else { // SSE
            pcmpeqb(dst, src);
        }
    }

    /**
     * Compares all packed bytes/words/dwords in {@code dst} to {@code src}. Matching values are set
     * to all ones (0xff, 0xffff, ...), non-matching values are set to zero.
     */
    public final void pcmpeq(AVXSize size, Stride elementStride, Register dst, AMD64Address src) {
        pcmpeq(size, elementStride.value, dst, src);
    }

    /**
     * Compares all packed bytes/words/dwords in {@code dst} to {@code src}. Matching values are set
     * to all ones (0xff, 0xffff, ...), non-matching values are set to zero.
     */
    public final void pcmpeq(AVXSize size, JavaKind elementKind, Register dst, AMD64Address src) {
        pcmpeq(size, elementKind.getByteCount(), dst, src);
    }

    private void pcmpeq(AVXSize vectorSize, int elementSize, Register dst, AMD64Address src) {
        switch (elementSize) {
            case 1:
                pcmpeqb(vectorSize, dst, src);
                break;
            case 2:
                pcmpeqw(vectorSize, dst, src);
                break;
            case 4:
                pcmpeqd(vectorSize, dst, src);
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    public final void pcmpeqb(AVXSize size, Register dst, AMD64Address src) {
        if (isAVX()) {
            VexRVMOp.VPCMPEQB.emit(this, size, dst, dst, src);
        } else { // SSE
            pcmpeqb(dst, src);
        }
    }

    public final void pcmpeqw(AVXSize size, Register dst, AMD64Address src) {
        if (isAVX()) {
            VexRVMOp.VPCMPEQW.emit(this, size, dst, dst, src);
        } else { // SSE
            pcmpeqw(dst, src);
        }
    }

    public final void pcmpeqd(AVXSize size, Register dst, AMD64Address src) {
        if (isAVX()) {
            VexRVMOp.VPCMPEQD.emit(this, size, dst, dst, src);
        } else { // SSE
            pcmpeqd(dst, src);
        }
    }

    public final void pcmpgtb(AVXSize size, Register dst, Register src) {
        if (isAVX()) {
            VexRVMOp.VPCMPGTB.emit(this, size, dst, dst, src);
        } else { // SSE
            pcmpgtb(dst, src);
        }
    }

    public final void pcmpgtd(AVXSize size, Register dst, Register src) {
        if (isAVX()) {
            VexRVMOp.VPCMPGTD.emit(this, size, dst, dst, src);
        } else { // SSE
            pcmpgtd(dst, src);
        }
    }

    private static int scaleDisplacement(Stride strideDst, Stride strideSrc, int displacement) {
        if (strideSrc.value < strideDst.value) {
            assert (displacement & ((1 << (strideDst.log2 - strideSrc.log2)) - 1)) == 0;
            return displacement >> (strideDst.log2 - strideSrc.log2);
        }
        assert strideSrc.value == strideDst.value;
        return displacement;
    }

    public final void loadAndExtendAVX(AVXSize size, ExtendMode extendMode, Register dst, Stride strideDst, Register src, Stride strideSrc) {
        getAVXLoadAndExtendOp(strideDst, strideSrc, extendMode).emit(this, size, dst, src);
    }

    public final void loadAndExtendAVX(AVXSize size, ExtendMode extendMode, Register dst, Stride strideDst, AMD64Address src, Stride strideSrc) {
        getAVXLoadAndExtendOp(strideDst, strideSrc, extendMode).emit(this, size, dst, src);
    }

    private static VexRMOp getAVXLoadAndExtendOp(Stride strideDst, Stride strideSrc, ExtendMode extendMode) {
        switch (strideSrc) {
            case S1:
                switch (strideDst) {
                    case S2:
                        return extendMode == ExtendMode.SIGN_EXTEND ? VexRMOp.VPMOVSXBW : VexRMOp.VPMOVZXBW;
                    case S4:
                        return extendMode == ExtendMode.SIGN_EXTEND ? VexRMOp.VPMOVSXBD : VexRMOp.VPMOVZXBD;
                    case S8:
                        return extendMode == ExtendMode.SIGN_EXTEND ? VexRMOp.VPMOVSXBQ : VexRMOp.VPMOVZXBQ;
                }
                throw GraalError.shouldNotReachHere();
            case S2:
                switch (strideDst) {
                    case S4:
                        return extendMode == ExtendMode.SIGN_EXTEND ? VexRMOp.VPMOVSXWD : VexRMOp.VPMOVZXWD;
                    case S8:
                        return extendMode == ExtendMode.SIGN_EXTEND ? VexRMOp.VPMOVSXWQ : VexRMOp.VPMOVZXWQ;
                }
                throw GraalError.shouldNotReachHere();
            case S4:
                return extendMode == ExtendMode.SIGN_EXTEND ? VexRMOp.VPMOVSXDQ : VexRMOp.VPMOVZXDQ;
        }
        throw GraalError.shouldNotReachHere();
    }

    public final void loadAndExtendSSE(ExtendMode extendMode, Register dst, Stride strideDst, AMD64Address src, Stride strideSrc) {
        boolean signExtend = extendMode == ExtendMode.SIGN_EXTEND;
        switch (strideSrc) {
            case S1:
                switch (strideDst) {
                    case S2:
                        if (signExtend) {
                            pmovsxbw(dst, src);
                        } else {
                            pmovzxbw(dst, src);
                        }
                        return;
                    case S4:
                        if (signExtend) {
                            pmovsxbd(dst, src);
                        } else {
                            pmovzxbd(dst, src);
                        }
                        return;
                    case S8:
                        if (signExtend) {
                            pmovsxbq(dst, src);
                        } else {
                            pmovzxbq(dst, src);
                        }
                        return;
                }
                throw GraalError.shouldNotReachHere();
            case S2:
                switch (strideDst) {
                    case S4:
                        if (signExtend) {
                            pmovsxwd(dst, src);
                        } else {
                            pmovzxwd(dst, src);
                        }
                        return;
                    case S8:
                        if (signExtend) {
                            pmovsxwq(dst, src);
                        } else {
                            pmovzxwq(dst, src);
                        }
                        return;
                }
                throw GraalError.shouldNotReachHere();
            case S4:
                if (signExtend) {
                    pmovsxdq(dst, src);
                } else {
                    pmovzxdq(dst, src);
                }
                return;
        }
        throw GraalError.shouldNotReachHere();
    }

    public final void loadAndExtendSSE(ExtendMode extendMode, Register dst, Stride strideDst, Register src, Stride strideSrc) {
        boolean signExtend = extendMode == ExtendMode.SIGN_EXTEND;
        switch (strideSrc) {
            case S1:
                switch (strideDst) {
                    case S2:
                        if (signExtend) {
                            pmovsxbw(dst, src);
                        } else {
                            pmovzxbw(dst, src);
                        }
                        return;
                    case S4:
                        if (signExtend) {
                            pmovsxbd(dst, src);
                        } else {
                            pmovzxbd(dst, src);
                        }
                        return;
                    case S8:
                        if (signExtend) {
                            pmovsxbq(dst, src);
                        } else {
                            pmovzxbq(dst, src);
                        }
                        return;
                }
                throw GraalError.shouldNotReachHere();
            case S2:
                switch (strideDst) {
                    case S4:
                        if (signExtend) {
                            pmovsxwd(dst, src);
                        } else {
                            pmovzxwd(dst, src);
                        }
                        return;
                    case S8:
                        if (signExtend) {
                            pmovsxwq(dst, src);
                        } else {
                            pmovzxwq(dst, src);
                        }
                        return;
                }
                throw GraalError.shouldNotReachHere();
            case S4:
                if (signExtend) {
                    pmovsxdq(dst, src);
                } else {
                    pmovzxdq(dst, src);
                }
                return;
        }
        throw GraalError.shouldNotReachHere();
    }

    public final void packuswb(AVXSize size, Register dst, Register src) {
        if (isAVX()) {
            VexRVMOp.VPACKUSWB.emit(this, size, dst, dst, src);
        } else {
            packuswb(dst, src);
        }
    }

    public final void packusdw(AVXSize size, Register dst, Register src) {
        if (isAVX()) {
            VexRVMOp.VPACKUSDW.emit(this, size, dst, dst, src);
        } else {
            packusdw(dst, src);
        }
    }

    public final void palignr(AVXSize size, Register dst, Register src, int imm8) {
        palignr(size, dst, dst, src, imm8);
    }

    public final void palignr(AVXSize size, Register dst, Register src1, Register src2, int imm8) {
        if (isAVX()) {
            VexRVMIOp.VPALIGNR.emit(this, size, dst, src1, src2, imm8);
        } else {
            // SSE
            if (!dst.equals(src1)) {
                movdqu(dst, src1);
            }
            palignr(dst, src2, imm8);
        }
    }

    public final void pand(AVXSize size, Register dst, Register src) {
        pand(size, dst, dst, src);
    }

    public final void pand(AVXSize size, Register dst, Register src1, Register src2) {
        if (isAVX()) {
            VexRVMOp.VPAND.emit(this, size, dst, src1, src2);
        } else {
            // SSE
            if (!dst.equals(src1)) {
                movdqu(dst, src1);
            }
            pand(dst, src2);
        }
    }

    public final void pand(AVXSize size, Register dst, AMD64Address src) {
        if (isAVX()) {
            VexRVMOp.VPAND.emit(this, size, dst, dst, src);
        } else {
            // SSE
            pand(dst, src);
        }
    }

    /**
     * PAND with unaligned memory operand.
     */
    public final void pandU(AVXSize size, Register dst, AMD64Address src, Register tmp) {
        if (isAVX()) {
            VexRVMOp.VPAND.emit(this, size, dst, dst, src);
        } else {
            // SSE
            movdqu(tmp, src);
            pand(dst, tmp);
        }
    }

    public final void pandn(AVXSize size, Register dst, Register src) {
        if (isAVX()) {
            VexRVMOp.VPANDN.emit(this, size, dst, dst, src);
        } else {
            // SSE
            pandn(dst, src);
        }
    }

    public final void por(AVXSize size, Register dst, Register src) {
        if (isAVX()) {
            VexRVMOp.VPOR.emit(this, size, dst, dst, src);
        } else {
            por(dst, src);
        }
    }

    public final void pxor(AVXSize size, Register dst, Register src) {
        pxor(size, dst, dst, src);
    }

    public final void pxor(AVXSize size, Register dst, Register src1, Register src2) {
        if (isAVX()) {
            VexRVMOp.VPXOR.emit(this, size, dst, src1, src2);
        } else {
            if (!dst.equals(src1)) {
                movdqu(dst, src1);
            }
            pxor(dst, src2);
        }
    }

    public final void psllw(AVXSize size, Register dst, int imm8) {
        psllw(size, dst, dst, imm8);
    }

    public final void psllw(AVXSize size, Register dst, Register src, int imm8) {
        if (isAVX()) {
            VexShiftOp.VPSLLW.emit(this, size, dst, src, imm8);
        } else {
            // SSE
            if (!dst.equals(src)) {
                movdqu(dst, src);
            }
            psllw(dst, imm8);
        }
    }

    public final void psrlw(AVXSize size, Register dst, int imm8) {
        psrlw(size, dst, dst, imm8);
    }

    public final void psrlw(AVXSize size, Register dst, Register src, int imm8) {
        if (isAVX()) {
            VexShiftOp.VPSRLW.emit(this, size, dst, src, imm8);
        } else {
            // SSE
            if (!dst.equals(src)) {
                movdqu(dst, src);
            }
            psrlw(dst, imm8);
        }
    }

    public final void pslld(AVXSize size, Register dst, int imm8) {
        pslld(size, dst, dst, imm8);
    }

    public final void pslld(AVXSize size, Register dst, Register src, int imm8) {
        if (isAVX()) {
            VexShiftOp.VPSLLD.emit(this, size, dst, src, imm8);
        } else {
            // SSE
            if (!dst.equals(src)) {
                movdqu(dst, src);
            }
            pslld(dst, imm8);
        }
    }

    public final void psrld(AVXSize size, Register dst, int imm8) {
        psrld(size, dst, dst, imm8);
    }

    public final void psrld(AVXSize size, Register dst, Register src, int imm8) {
        if (isAVX()) {
            VexShiftOp.VPSRLD.emit(this, size, dst, src, imm8);
        } else {
            // SSE
            if (!dst.equals(src)) {
                movdqu(dst, src);
            }
            psrld(dst, imm8);
        }
    }

    public final void pshufb(AVXSize size, Register dst, Register src) {
        if (isAVX()) {
            VexRVMOp.VPSHUFB.emit(this, size, dst, dst, src);
        } else {
            // SSE
            pshufb(dst, src);
        }
    }

    public final void pshufb(AVXSize size, Register dst, AMD64Address src) {
        if (isAVX()) {
            VexRVMOp.VPSHUFB.emit(this, size, dst, dst, src);
        } else {
            // SSE
            pshufb(dst, src);
        }
    }

    public final void ptest(AVXSize size, Register dst) {
        ptest(size, dst, dst);
    }

    public final void ptest(AVXSize size, Register dst, Register src) {
        if (isAVX()) {
            VexRMOp.VPTEST.emit(this, size, dst, src);
        } else {
            ptest(dst, src);
        }
    }

    /**
     * PTEST with unaligned memory operand.
     */
    public final void ptestU(AVXSize size, Register dst, AMD64Address src, Register tmp) {
        if (isAVX()) {
            VexRMOp.VPTEST.emit(this, size, dst, src);
        } else {
            movdqu(tmp, src);
            ptest(dst, tmp);
        }
    }

    public boolean isAVX() {
        return supports(CPUFeature.AVX);
    }

    public static boolean isAVX(AMD64 arch) {
        return arch.getFeatures().contains(CPUFeature.AVX);
    }
}
