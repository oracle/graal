/*
 * Copyright (c) 2009, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.asm.amd64;

import static jdk.graal.compiler.core.common.NumUtil.isByte;
import static jdk.vm.ci.amd64.AMD64.rip;

import java.util.function.IntConsumer;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.InvokeTarget;

/**
 * This class implements commonly used X86 code patterns.
 */
public class AMD64MacroAssembler extends AMD64Assembler {

    public AMD64MacroAssembler(TargetDescription target) {
        super(target);
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
        if (value == 1) {
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
        if (value == 1) {
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
        if (value == 1) {
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
        if (value == 1) {
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
        if (value == 1) {
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
        if (value == 1) {
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
        if (value == 1) {
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
        if (value == 1) {
            incl(dst);
        } else {
            addl(dst, value);
        }
    }

    public final void movflt(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM) : dst + " " + src;
        if (isAVX512Register(dst) || isAVX512Register(src)) {
            VexMoveOp.EVMOVAPS.emit(this, AVXKind.AVXSize.XMM, dst, src);
        } else {
            movaps(dst, src);
        }
    }

    public final void movflt(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        if (isAVX512Register(dst)) {
            VexMoveOp.EVMOVSS.emit(this, AVXKind.AVXSize.XMM, dst, src);
        } else {
            movss(dst, src);
        }
    }

    public final void movflt(AMD64Address dst, Register src) {
        assert src.getRegisterCategory().equals(AMD64.XMM);
        if (isAVX512Register(src)) {
            VexMoveOp.EVMOVSS.emit(this, AVXKind.AVXSize.XMM, dst, src);
        } else {
            movss(dst, src);
        }
    }

    public final void movdbl(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM) : dst + " " + src;
        if (isAVX512Register(dst) || isAVX512Register(src)) {
            VexMoveOp.EVMOVAPD.emit(this, AVXKind.AVXSize.XMM, dst, src);
        } else {
            movapd(dst, src);
        }
    }

    public final void movdbl(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        if (isAVX512Register(dst)) {
            VexMoveOp.EVMOVSD.emit(this, AVXKind.AVXSize.XMM, dst, src);
        } else {
            movsd(dst, src);
        }
    }

    public final void movdbl(AMD64Address dst, Register src) {
        assert src.getRegisterCategory().equals(AMD64.XMM);
        if (isAVX512Register(src)) {
            VexMoveOp.EVMOVSD.emit(this, AVXKind.AVXSize.XMM, dst, src);
        } else {
            movsd(dst, src);
        }
    }

    /**
     * Non-atomic write of a 64-bit constant to memory. Do not use if the address might be a
     * volatile field!
     */
    public final void movlong(AMD64Address dst, long src, boolean annotateImm) {
        GraalError.guarantee(!annotateImm, "patching not implemented for 8-byte stores");
        if (NumUtil.isInt(src)) {
            emitAMD64MIOp(AMD64MIOp.MOV, OperandSize.QWORD, dst, (int) src, false);
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
     * Functional interface used for performing actions after a call has been emitted.
     */
    public interface PostCallAction {
        PostCallAction NONE = (before, after) -> {
        };

        void apply(int beforePos, int afterPos);
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
    @SuppressWarnings("unused")
    public int indirectCall(PostCallAction postCallAction, Register callReg, boolean mitigateDecodingAsDirectCall, InvokeTarget callTarget,
                    CallingConvention.Type callingConventionType) {
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
        assert beforeCall + indirectCallSize == position() : Assertions.errorMessage(beforeCall, indirectCallSize, position());
        if (mitigateDecodingAsDirectCall) {
            int directCallPos = position() - DIRECT_CALL_INSTRUCTION_SIZE;
            GraalError.guarantee(directCallPos >= 0 && getByte(directCallPos) != DIRECT_CALL_INSTRUCTION_CODE,
                            "This indirect call can be decoded as a direct call.");
        }

        int afterCall = position();
        if (postCallAction != PostCallAction.NONE) {
            postCallAction.apply(beforeCall, afterCall);
        }

        return beforeCall;
    }

    @SuppressWarnings("unused")
    public int directCall(PostCallAction postCallAction, long address, Register scratch, InvokeTarget callTarget) {
        int bytesToEmit = needsRex(scratch) ? 13 : 12;
        mitigateJCCErratum(bytesToEmit);
        int beforeCall = position();
        movq(scratch, address);
        call(scratch);
        int afterCall = position();
        assert beforeCall + bytesToEmit == afterCall : Assertions.errorMessage(beforeCall, bytesToEmit, afterCall);

        if (postCallAction != PostCallAction.NONE) {
            postCallAction.apply(beforeCall, afterCall);
        }
        return beforeCall;
    }

    public int directJmp(long address, Register scratch) {
        int bytesToEmit = needsRex(scratch) ? 13 : 12;
        mitigateJCCErratum(bytesToEmit);
        int beforeJmp = position();
        movq(scratch, address);
        jmpWithoutAlignment(scratch);
        assert beforeJmp + bytesToEmit == position() : Assertions.errorMessage(beforeJmp, bytesToEmit, position());
        return beforeJmp;
    }

    /** Emits a jmp instruction with an immediate to be patched. Includes JCC erratum mitigation. */
    public void jmp() {
        mitigateJCCErratum(5);
        rawJmpNoJCCErratumMitigation();
    }

    /**
     * Emits a jmp instruction with an immediate to be patched. Does <em>not</em> include JCC
     * erratum mitigation. Most use cases should use {@link #jmp()} instead. This method should only
     * be used directly when predictable code size is more important than the performance penalty of
     * a possibly misaligned jump.
     */
    public void rawJmpNoJCCErratumMitigation() {
        annotatePatchingImmediate(1, 4);
        emitByte(0xE9);
        emitInt(0);
    }

    @SuppressWarnings("unused")
    public int call(PostCallAction postCallAction, InvokeTarget callTarget) {
        int beforeCall = position();
        call();
        int afterCall = position();
        if (postCallAction != PostCallAction.NONE) {
            postCallAction.apply(beforeCall, afterCall);
        }

        return beforeCall;
    }

    // This should guarantee that the alignment in AMD64Assembler.jcc methods will be not triggered.
    private void alignFusedPair(Label branchTarget, boolean isShortJmp, int prevOpInBytes) {
        GraalError.guarantee(prevOpInBytes < 26, "Fused pair may be longer than 0x20 bytes.");
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

    /**
     * Intel macro fusion has specific operand constraints for {@code op}:
     * <ul>
     * <li>It can have either an immediate operand or a memory source operand, but not both.</li>
     * <li>It cannot have a memory destination operand.</li>
     * <li>It cannot have a RIP-relative memory operand.</li>
     * </ul>
     */
    private int applyMIOpAndJcc(AMD64MIOp op, OperandSize size, Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp, boolean annotateImm,
                    IntConsumer applyBeforeFusedPair) {
        final int bytesToEmit = getPrefixInBytes(size, src, op.srcIsByte) + OPCODE_IN_BYTES + MODRM_IN_BYTES + op.immediateSize(size);
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        if (applyBeforeFusedPair != null) {
            applyBeforeFusedPair.accept(beforeFusedPair);
        }
        op.emit(this, size, src, imm32, annotateImm);
        final int beforeJcc = position();
        assert beforeFusedPair + bytesToEmit == beforeJcc : Assertions.errorMessage(beforeFusedPair, bytesToEmit, position());
        jcc(cc, branchTarget, isShortJmp);
        assert ensureWithinBoundary(beforeFusedPair);
        return beforeJcc;
    }

    /**
     * See {@link #applyMIOpAndJcc}.
     */
    private int applyRMOpAndJcc(AMD64RMOp op, OperandSize size, Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        final int bytesToEmit = getPrefixInBytes(size, src1, op.dstIsByte, src2, op.srcIsByte) + OPCODE_IN_BYTES + MODRM_IN_BYTES;
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        op.emit(this, size, src1, src2);
        final int beforeJcc = position();
        assert beforeFusedPair + bytesToEmit == beforeJcc : Assertions.errorMessage(beforeFusedPair, bytesToEmit, beforeJcc);
        jcc(cc, branchTarget, isShortJmp);
        assert ensureWithinBoundary(beforeFusedPair);
        return beforeJcc;
    }

    /**
     * See {@link #applyMIOpAndJcc}.
     */
    private int applyRMOpAndJcc(AMD64RMOp op, OperandSize size, Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp, IntConsumer applyBeforeFusedPair) {
        GraalError.guarantee(!rip.equals(src2.getBase()), "RIP-relative memory operand cannot be fused");
        /*
         * The extra bytes introduced by MemoryReadInterceptor are also included in the fused pair
         * size, which may lead to imprecision. However, this does not affect the correctness of the
         * Intel JCC erratum, as it ensures that both the instrumented logic and the fused pair
         * remain within the 32-byte boundary. If the total size exceeds 32 bytes, the assertion in
         * alignFusedPair will detect it.
         */
        final int bytesToEmit = getPrefixInBytes(size, src1, op.dstIsByte, src2) + OPCODE_IN_BYTES + addressInBytes(src2) + extraSourceAddressBytes(src2);
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        if (applyBeforeFusedPair != null) {
            applyBeforeFusedPair.accept(beforeFusedPair);
        }
        op.emit(this, size, src1, src2);
        final int beforeJcc = position();
        assert beforeFusedPair + bytesToEmit == beforeJcc : Assertions.errorMessage(beforeFusedPair, bytesToEmit, beforeJcc);
        jcc(cc, branchTarget, isShortJmp);
        assert ensureWithinBoundary(beforeFusedPair);
        return beforeJcc;
    }

    /**
     * See {@link #applyMIOpAndJcc}.
     */
    public int applyMOpAndJcc(AMD64MOp op, OperandSize size, Register dst, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        final int bytesToEmit = getPrefixInBytes(size, dst, op.srcIsByte) + OPCODE_IN_BYTES + MODRM_IN_BYTES;
        alignFusedPair(branchTarget, isShortJmp, bytesToEmit);
        final int beforeFusedPair = position();
        op.emit(this, size, dst);
        final int beforeJcc = position();
        assert beforeFusedPair + bytesToEmit == beforeJcc : Assertions.errorMessage(beforeFusedPair, bytesToEmit, position());
        jcc(cc, branchTarget, isShortJmp);
        assert ensureWithinBoundary(beforeFusedPair);
        return beforeJcc;
    }

    public final int testAndJcc(OperandSize size, Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        AMD64MIOp op = size == OperandSize.BYTE ? AMD64MIOp.TESTB : AMD64MIOp.TEST;
        return applyMIOpAndJcc(op, size, src, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final int testlAndJcc(Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        return applyMIOpAndJcc(AMD64MIOp.TEST, OperandSize.DWORD, src, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final int testqAndJcc(Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        return applyMIOpAndJcc(AMD64MIOp.TEST, OperandSize.QWORD, src, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final int testAndJcc(OperandSize size, Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        AMD64RMOp op = size == OperandSize.BYTE ? AMD64RMOp.TESTB : AMD64RMOp.TEST;
        return applyRMOpAndJcc(op, size, src1, src2, cc, branchTarget, isShortJmp);
    }

    public final int testlAndJcc(Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        return applyRMOpAndJcc(AMD64RMOp.TEST, OperandSize.DWORD, src1, src2, cc, branchTarget, isShortJmp);
    }

    public final int testqAndJcc(Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        return applyRMOpAndJcc(AMD64RMOp.TEST, OperandSize.QWORD, src1, src2, cc, branchTarget, isShortJmp);
    }

    public final int testAndJcc(OperandSize size, Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        AMD64RMOp op = size == OperandSize.BYTE ? AMD64RMOp.TESTB : AMD64RMOp.TEST;
        return applyRMOpAndJcc(op, size, src1, src2, cc, branchTarget, isShortJmp, null);
    }

    public final int testAndJcc(OperandSize size, Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp, IntConsumer applyBeforeFusedPair) {
        AMD64RMOp op = size == OperandSize.BYTE ? AMD64RMOp.TESTB : AMD64RMOp.TEST;
        return applyRMOpAndJcc(op, size, src1, src2, cc, branchTarget, isShortJmp, applyBeforeFusedPair);
    }

    public final int testbAndJcc(Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        return applyRMOpAndJcc(AMD64RMOp.TESTB, OperandSize.BYTE, src1, src2, cc, branchTarget, isShortJmp);
    }

    public final int testbAndJcc(Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        return applyRMOpAndJcc(AMD64RMOp.TESTB, OperandSize.BYTE, src1, src2, cc, branchTarget, isShortJmp, null);
    }

    public final int cmpAndJcc(OperandSize size, Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        GraalError.guarantee(canBeFusedWithAddSubCmp(cc), "cmp cannot be fused with JCC on %s", cc);
        return applyMIOpAndJcc(AMD64BinaryArithmetic.CMP.getMIOpcode(size, isByte(imm32)), size, src, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final int cmpAndJcc(OperandSize size, Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp, boolean annotateImm, IntConsumer applyBeforeFusedPair) {
        GraalError.guarantee(canBeFusedWithAddSubCmp(cc), "cmp cannot be fused with JCC on %s", cc);
        return applyMIOpAndJcc(AMD64BinaryArithmetic.CMP.getMIOpcode(size, isByte(imm32)), size, src, imm32, cc, branchTarget, isShortJmp, annotateImm, applyBeforeFusedPair);
    }

    public final int cmplAndJcc(Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        GraalError.guarantee(canBeFusedWithAddSubCmp(cc), "cmp cannot be fused with JCC on %s", cc);
        return applyMIOpAndJcc(AMD64BinaryArithmetic.CMP.getMIOpcode(OperandSize.DWORD, isByte(imm32)), OperandSize.DWORD, src, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final int cmpqAndJcc(Register src, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        GraalError.guarantee(canBeFusedWithAddSubCmp(cc), "cmp cannot be fused with JCC on %s", cc);
        return applyMIOpAndJcc(AMD64BinaryArithmetic.CMP.getMIOpcode(OperandSize.QWORD, isByte(imm32)), OperandSize.QWORD, src, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final int cmpAndJcc(OperandSize size, Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        GraalError.guarantee(canBeFusedWithAddSubCmp(cc), "cmp cannot be fused with JCC on %s", cc);
        return applyRMOpAndJcc(AMD64BinaryArithmetic.CMP.getRMOpcode(size), size, src1, src2, cc, branchTarget, isShortJmp);
    }

    public final int cmpAndJcc(OperandSize size, Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        GraalError.guarantee(canBeFusedWithAddSubCmp(cc), "cmp cannot be fused with JCC on %s", cc);
        return applyRMOpAndJcc(AMD64BinaryArithmetic.CMP.getRMOpcode(size), size, src1, src2, cc, branchTarget, isShortJmp, null);
    }

    public final int cmplAndJcc(Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        GraalError.guarantee(canBeFusedWithAddSubCmp(cc), "cmp cannot be fused with JCC on %s", cc);
        return applyRMOpAndJcc(AMD64BinaryArithmetic.CMP.getRMOpcode(OperandSize.DWORD), OperandSize.DWORD, src1, src2, cc, branchTarget, isShortJmp);
    }

    public final int cmpqAndJcc(Register src1, Register src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        GraalError.guarantee(canBeFusedWithAddSubCmp(cc), "cmp cannot be fused with JCC on %s", cc);
        return applyRMOpAndJcc(AMD64BinaryArithmetic.CMP.getRMOpcode(OperandSize.QWORD), OperandSize.QWORD, src1, src2, cc, branchTarget, isShortJmp);
    }

    public final int cmpAndJcc(OperandSize size, Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp, IntConsumer applyBeforeFusedPair) {
        GraalError.guarantee(canBeFusedWithAddSubCmp(cc), "cmp cannot be fused with JCC on %s", cc);
        return applyRMOpAndJcc(AMD64BinaryArithmetic.CMP.getRMOpcode(size), size, src1, src2, cc, branchTarget, isShortJmp, applyBeforeFusedPair);
    }

    public final int cmplAndJcc(Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        GraalError.guarantee(canBeFusedWithAddSubCmp(cc), "cmp cannot be fused with JCC on %s", cc);
        return applyRMOpAndJcc(AMD64BinaryArithmetic.CMP.getRMOpcode(OperandSize.DWORD), OperandSize.DWORD, src1, src2, cc, branchTarget, isShortJmp, null);
    }

    public final int cmpqAndJcc(Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        GraalError.guarantee(canBeFusedWithAddSubCmp(cc), "cmp cannot be fused with JCC on %s", cc);
        return applyRMOpAndJcc(AMD64BinaryArithmetic.CMP.getRMOpcode(OperandSize.QWORD), OperandSize.QWORD, src1, src2, cc, branchTarget, isShortJmp, null);
    }

    public final int cmpqAndJcc(Register src1, AMD64Address src2, ConditionFlag cc, Label branchTarget, boolean isShortJmp, IntConsumer applyBeforeFusedPair) {
        GraalError.guarantee(canBeFusedWithAddSubCmp(cc), "cmp cannot be fused with JCC on %s", cc);
        return applyRMOpAndJcc(AMD64BinaryArithmetic.CMP.getRMOpcode(OperandSize.QWORD), OperandSize.QWORD, src1, src2, cc, branchTarget, isShortJmp, applyBeforeFusedPair);
    }

    public final int andlAndJcc(Register dst, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        return applyMIOpAndJcc(AMD64BinaryArithmetic.AND.getMIOpcode(OperandSize.DWORD, isByte(imm32)), OperandSize.DWORD, dst, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final int andqAndJcc(Register dst, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        return applyMIOpAndJcc(AMD64BinaryArithmetic.AND.getMIOpcode(OperandSize.QWORD, isByte(imm32)), OperandSize.QWORD, dst, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final int andqAndJcc(Register dst, Register src, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        return applyRMOpAndJcc(AMD64BinaryArithmetic.AND.getRMOpcode(OperandSize.QWORD), OperandSize.QWORD, dst, src, cc, branchTarget, isShortJmp);
    }

    public final int addqAndJcc(Register dst, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        GraalError.guarantee(canBeFusedWithAddSubCmp(cc), "add cannot be fused with JCC on %s", cc);
        return applyMIOpAndJcc(AMD64BinaryArithmetic.ADD.getMIOpcode(OperandSize.QWORD, isByte(imm32)), OperandSize.QWORD, dst, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final int sublAndJcc(Register dst, Register src, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        GraalError.guarantee(canBeFusedWithAddSubCmp(cc), "sub cannot be fused with JCC on %s", cc);
        return applyRMOpAndJcc(AMD64BinaryArithmetic.SUB.getRMOpcode(OperandSize.DWORD), OperandSize.DWORD, dst, src, cc, branchTarget, isShortJmp);
    }

    public final int subqAndJcc(Register dst, Register src, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        GraalError.guarantee(canBeFusedWithAddSubCmp(cc), "sub cannot be fused with JCC on %s", cc);
        return applyRMOpAndJcc(AMD64BinaryArithmetic.SUB.getRMOpcode(OperandSize.QWORD), OperandSize.QWORD, dst, src, cc, branchTarget, isShortJmp);
    }

    public final int sublAndJcc(Register dst, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        GraalError.guarantee(canBeFusedWithAddSubCmp(cc), "sub cannot be fused with JCC on %s", cc);
        return applyMIOpAndJcc(AMD64BinaryArithmetic.SUB.getMIOpcode(OperandSize.DWORD, isByte(imm32)), OperandSize.DWORD, dst, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final int subqAndJcc(Register dst, int imm32, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        GraalError.guarantee(canBeFusedWithAddSubCmp(cc), "sub cannot be fused with JCC on %s", cc);
        return applyMIOpAndJcc(AMD64BinaryArithmetic.SUB.getMIOpcode(OperandSize.QWORD, isByte(imm32)), OperandSize.QWORD, dst, imm32, cc, branchTarget, isShortJmp, false, null);
    }

    public final int incqAndJcc(Register dst, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        GraalError.guarantee(canBeFusedWithIncDec(cc), "inc cannot be fused with JCC on %s", cc);
        return applyMOpAndJcc(AMD64MOp.INC, OperandSize.QWORD, dst, cc, branchTarget, isShortJmp);
    }

    public final int declAndJcc(Register dst, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        GraalError.guarantee(canBeFusedWithIncDec(cc), "dec cannot be fused with JCC on %s", cc);
        return applyMOpAndJcc(AMD64MOp.DEC, OperandSize.DWORD, dst, cc, branchTarget, isShortJmp);
    }

    public final int decqAndJcc(Register dst, ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        GraalError.guarantee(canBeFusedWithIncDec(cc), "dec cannot be fused with JCC on %s", cc);
        return applyMOpAndJcc(AMD64MOp.DEC, OperandSize.QWORD, dst, cc, branchTarget, isShortJmp);
    }

    /**
     * Checks if the current jcc instruction can be macro-fused with a preceding add/sub/cmp
     * instruction.
     * <p>
     * Intel macro fusion with the aforementioned instructions is supported for jcc instructions
     * with the following conditions:
     * <ul>
     * <li>{@link AMD64Assembler.ConditionFlag#Zero} (jz)</li>
     * <li>{@link AMD64Assembler.ConditionFlag#Equal} (je)</li>
     * <li>{@link AMD64Assembler.ConditionFlag#CarrySet} (jc)</li>
     * <li>{@link AMD64Assembler.ConditionFlag#Below} (jb)</li>
     * <li>{@link AMD64Assembler.ConditionFlag#Above} (ja)</li>
     * <li>{@link AMD64Assembler.ConditionFlag#Less} (jl)</li>
     * <li>{@link AMD64Assembler.ConditionFlag#Greater} (jg)</li>
     * <li>and their inverses</li>
     * </ul>
     *
     * @return true if macro fusion is possible, false otherwise.
     */
    private static boolean canBeFusedWithAddSubCmp(ConditionFlag cc) {
        return switch (cc) {
            case Zero, NotZero, Equal, NotEqual, CarrySet, CarryClear, Less, LessEqual, Greater, GreaterEqual, Above, AboveEqual, Below, BelowEqual -> true;
            default -> false;
        };
    }

    /**
     * Checks if the current jcc instruction can be macro-fused with a preceding inc/dec
     * instruction.
     * <p>
     * Intel macro fusion with the aforementioned instructions is supported for jcc instructions
     * with the following conditions:
     * <ul>
     * <li>{@link AMD64Assembler.ConditionFlag#Zero} (jz)</li>
     * <li>{@link AMD64Assembler.ConditionFlag#Equal} (je)</li>
     * <li>{@link AMD64Assembler.ConditionFlag#Less} (jl)</li>
     * <li>{@link AMD64Assembler.ConditionFlag#Greater} (jg)</li>
     * <li>and their inverses</li>
     * </ul>
     *
     * @return true if macro fusion is possible, false otherwise.
     */
    private static boolean canBeFusedWithIncDec(ConditionFlag cc) {
        return switch (cc) {
            case Zero, NotZero, Equal, NotEqual, Less, LessEqual, Greater, GreaterEqual -> true;
            default -> false;
        };
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

    public final void pmovSZxQWORD(ExtendMode extendMode, Register dst, Stride strideDst, Register src, Stride strideSrc, Register index, int displacement) {
        int scaledDisplacement = scaleDisplacement(strideDst, strideSrc, displacement);
        AMD64Address address = new AMD64Address(src, index, strideSrc, scaledDisplacement);

        if (strideSrc.value < strideDst.value) {
            GraalError.guarantee(strideDst.log2 - strideSrc.log2 == 1, "unsupported stride pair %s %s", strideSrc, strideDst);
            if (isAVX()) {
                VexMoveOp.VMOVD.emit(this, AVXKind.AVXSize.XMM, dst, address);
                loadAndExtendAVX(AVXKind.AVXSize.QWORD, extendMode, dst, strideDst, dst, strideSrc);
            } else {
                movdl(dst, address);
                loadAndExtendSSE(extendMode, dst, strideDst, dst, strideSrc);
            }
        } else {
            GraalError.guarantee(strideSrc.value == strideDst.value, "source stride must be smaller or equal to target stride");
            if (isAVX()) {
                VexMoveOp.VMOVQ.emit(this, AVXKind.AVXSize.XMM, dst, address);
            } else {
                movdq(dst, address);
            }
        }
    }

    /**
     * Load elements from address {@code (src, index, displacement)} into vector register
     * {@code dst}, and zero- or sign-extend them to fit {@code scaleDst}.
     *
     * @param size vector size. May be {@link AVXKind.AVXSize#XMM} or {@link AVXKind.AVXSize#YMM}.
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
    public final void pmovSZx(AVXKind.AVXSize size, ExtendMode extendMode, Register dst, Stride strideDst, Register src, Stride strideSrc, Register index, int displacement) {
        GraalError.guarantee(size == AVXKind.AVXSize.XMM || size == AVXKind.AVXSize.YMM, "unsupported AVXSize %s", size);
        GraalError.guarantee(strideSrc.value <= strideDst.value, "source stride must be smaller or equal to target stride");

        int scaledDisplacement = scaleDisplacement(strideDst, strideSrc, displacement);
        AMD64Address address = new AMD64Address(src, index, strideSrc, scaledDisplacement);
        pmovSZx(size, extendMode, dst, strideDst, address, strideSrc);
    }

    public final void pmovSZx(AVXKind.AVXSize size, ExtendMode extendMode, Register dst, Stride strideDst, AMD64Address src, Stride strideSrc) {
        GraalError.guarantee(size == AVXKind.AVXSize.XMM || size == AVXKind.AVXSize.YMM, "unsupported AVXSize %s", size);

        if (strideSrc.value < strideDst.value) {
            if (isAVX()) {
                loadAndExtendAVX(size, extendMode, dst, strideDst, src, strideSrc);
            } else {
                loadAndExtendSSE(extendMode, dst, strideDst, src, strideSrc);
            }
        } else {
            GraalError.guarantee(strideSrc.value == strideDst.value, "source stride must be smaller or equal to target stride");
            movdqu(size, dst, src);
        }
    }

    public final void pmovSZx(AVXKind.AVXSize size, ExtendMode extendMode, Register dst, Stride strideDst, Register src, Stride strideSrc) {
        GraalError.guarantee(size == AVXKind.AVXSize.XMM || size == AVXKind.AVXSize.YMM, "unsupported AVXSize %s", size);

        if (strideSrc.value < strideDst.value) {
            if (isAVX()) {
                loadAndExtendAVX(size, extendMode, dst, strideDst, src, strideSrc);
            } else {
                loadAndExtendSSE(extendMode, dst, strideDst, src, strideSrc);
            }
        } else {
            GraalError.guarantee(strideSrc.value == strideDst.value, "source stride must be smaller or equal to target stride");
            movdqu(size, dst, src);
        }
    }

    public final void pmovmsk(AVXKind.AVXSize size, Register dst, Register src) {
        if (isAVX()) {
            VexRMOp.VPMOVMSKB.emit(this, size, dst, src);
        } else {
            pmovmskb(dst, src);
        }
    }

    public final void movdqu(AVXKind.AVXSize size, Register dst, AMD64Address src) {
        GraalError.guarantee(size == AVXKind.AVXSize.XMM || size == AVXKind.AVXSize.YMM, "unsupported AVXSize %s", size);
        if (isAVX()) {
            VexMoveOp.VMOVDQU32.emit(this, size, dst, src);
        } else {
            movdqu(dst, src);
        }
    }

    public final void movdqu(AVXKind.AVXSize size, AMD64Address dst, Register src) {
        GraalError.guarantee(size == AVXKind.AVXSize.XMM || size == AVXKind.AVXSize.YMM, "unsupported AVXSize %s", size);
        if (isAVX()) {
            VexMoveOp.VMOVDQU32.emit(this, size, dst, src);
        } else {
            movdqu(dst, src);
        }
    }

    public final void movdqu(AVXKind.AVXSize size, Register dst, Register src) {
        GraalError.guarantee(size == AVXKind.AVXSize.XMM || size == AVXKind.AVXSize.YMM, "unsupported AVXSize %s", size);
        if (isAVX()) {
            VexMoveOp.VMOVDQU32.emit(this, size, dst, src);
        } else {
            movdqu(dst, src);
        }
    }

    /**
     * Compares all packed bytes/words/dwords in {@code dst} to {@code src}. Matching values are set
     * to all ones (0xff, 0xffff, ...), non-matching values are set to zero.
     */
    public final void pcmpeq(AVXKind.AVXSize vectorSize, Stride elementSize, Register dst, Register src) {
        switch (elementSize) {
            case S1:
                pcmpeqb(vectorSize, dst, src);
                break;
            case S2:
                pcmpeqw(vectorSize, dst, src);
                break;
            case S4:
                pcmpeqd(vectorSize, dst, src);
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    public final void pcmpeqw(AVXKind.AVXSize vectorSize, Register dst, Register src) {
        if (isAVX()) {
            VexRVMOp.VPCMPEQW.emit(this, vectorSize, dst, src, dst);
        } else { // SSE
            pcmpeqw(dst, src);
        }
    }

    public final void pcmpeqd(AVXKind.AVXSize vectorSize, Register dst, Register src) {
        if (isAVX()) {
            VexRVMOp.VPCMPEQD.emit(this, vectorSize, dst, src, dst);
        } else { // SSE
            pcmpeqd(dst, src);
        }
    }

    public final void pcmpeqb(AVXKind.AVXSize size, Register dst, Register src) {
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
    public final void pcmpeq(AVXKind.AVXSize vectorSize, Stride elementSize, Register dst, AMD64Address src) {
        switch (elementSize) {
            case S1:
                pcmpeqb(vectorSize, dst, src);
                break;
            case S2:
                pcmpeqw(vectorSize, dst, src);
                break;
            case S4:
                pcmpeqd(vectorSize, dst, src);
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    public final void pcmpeqb(AVXKind.AVXSize size, Register dst, AMD64Address src) {
        if (isAVX()) {
            VexRVMOp.VPCMPEQB.emit(this, size, dst, dst, src);
        } else { // SSE
            pcmpeqb(dst, src);
        }
    }

    public final void pcmpeqw(AVXKind.AVXSize size, Register dst, AMD64Address src) {
        if (isAVX()) {
            VexRVMOp.VPCMPEQW.emit(this, size, dst, dst, src);
        } else { // SSE
            pcmpeqw(dst, src);
        }
    }

    public final void pcmpeqd(AVXKind.AVXSize size, Register dst, AMD64Address src) {
        if (isAVX()) {
            VexRVMOp.VPCMPEQD.emit(this, size, dst, dst, src);
        } else { // SSE
            pcmpeqd(dst, src);
        }
    }

    public final void pcmpgtb(AVXKind.AVXSize size, Register dst, Register src) {
        if (isAVX()) {
            VexRVMOp.VPCMPGTB.emit(this, size, dst, dst, src);
        } else { // SSE
            pcmpgtb(dst, src);
        }
    }

    public final void pcmpgtd(AVXKind.AVXSize size, Register dst, Register src) {
        if (isAVX()) {
            VexRVMOp.VPCMPGTD.emit(this, size, dst, dst, src);
        } else { // SSE
            pcmpgtd(dst, src);
        }
    }

    public final void pminu(AVXKind.AVXSize vectorSize, Stride elementSize, Register dst, Register src1, Register src2) {
        switch (elementSize) {
            case S1:
                pminub(vectorSize, dst, src1, src2);
                break;
            case S2:
                pminuw(vectorSize, dst, src1, src2);
                break;
            case S4:
                pminud(vectorSize, dst, src1, src2);
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    public final void pminub(AVXKind.AVXSize size, Register dst, Register src1, Register src2) {
        simdRVMOp(VexRVMOp.VPMINUB, SSEOp.PMINUB, size, dst, src1, src2, true);
    }

    public final void pminuw(AVXKind.AVXSize size, Register dst, Register src1, Register src2) {
        simdRVMOp(VexRVMOp.VPMINUW, SSEOp.PMINUW, size, dst, src1, src2, true);
    }

    public final void pminud(AVXKind.AVXSize size, Register dst, Register src1, Register src2) {
        simdRVMOp(VexRVMOp.VPMINUD, SSEOp.PMINUD, size, dst, src1, src2, true);
    }

    private void simdRVMOp(VexRVMOp avxOp, SSEOp sseOp, AVXKind.AVXSize vectorSize, Register dst, Register src1, Register src2, boolean isCommutative) {
        if (isAVX()) {
            avxOp.emit(this, vectorSize, dst, src1, src2);
        } else {
            threeVectorOpSSE(sseOp, dst, src1, src2, isCommutative);
        }
    }

    private void threeVectorOpSSE(SSEOp op, Register dst, Register src1, Register src2, boolean isCommutative) {
        if (dst.equals(src1)) {
            op.emit(this, OperandSize.PD, dst, src2);
        } else if (dst.equals(src2)) {
            if (isCommutative) {
                op.emit(this, OperandSize.PD, dst, src1);
            } else {
                throw GraalError.shouldNotReachHere("can't simulate non-commutative 3-vector AVX op on SSE when dst == src2!"); // ExcludeFromJacocoGeneratedReport
            }
        } else {
            movdqu(dst, src1);
            op.emit(this, OperandSize.PD, dst, src2);
        }
    }

    private static int scaleDisplacement(Stride strideDst, Stride strideSrc, int displacement) {
        if (strideSrc.value < strideDst.value) {
            assert (displacement & ((1 << (strideDst.log2 - strideSrc.log2)) - 1)) == 0 : Assertions.errorMessage(displacement, strideDst, strideSrc);
            return displacement >> (strideDst.log2 - strideSrc.log2);
        }
        assert strideSrc.value == strideDst.value : Assertions.errorMessage(strideSrc, strideDst);
        return displacement;
    }

    public final void loadAndExtendAVX(AVXKind.AVXSize size, ExtendMode extendMode, Register dst, Stride strideDst, Register src, Stride strideSrc) {
        getAVXLoadAndExtendOp(strideDst, strideSrc, extendMode).emit(this, size, dst, src);
    }

    public final void loadAndExtendAVX(AVXKind.AVXSize size, ExtendMode extendMode, Register dst, Stride strideDst, AMD64Address src, Stride strideSrc) {
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
                throw GraalError.shouldNotReachHereUnexpectedValue(strideDst); // ExcludeFromJacocoGeneratedReport
            case S2:
                switch (strideDst) {
                    case S4:
                        return extendMode == ExtendMode.SIGN_EXTEND ? VexRMOp.VPMOVSXWD : VexRMOp.VPMOVZXWD;
                    case S8:
                        return extendMode == ExtendMode.SIGN_EXTEND ? VexRMOp.VPMOVSXWQ : VexRMOp.VPMOVZXWQ;
                }
                throw GraalError.shouldNotReachHereUnexpectedValue(strideDst); // ExcludeFromJacocoGeneratedReport
            case S4:
                return extendMode == ExtendMode.SIGN_EXTEND ? VexRMOp.VPMOVSXDQ : VexRMOp.VPMOVZXDQ;
        }
        throw GraalError.shouldNotReachHereUnexpectedValue(strideSrc); // ExcludeFromJacocoGeneratedReport
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
                throw GraalError.shouldNotReachHereUnexpectedValue(strideDst); // ExcludeFromJacocoGeneratedReport
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
                throw GraalError.shouldNotReachHereUnexpectedValue(strideDst); // ExcludeFromJacocoGeneratedReport
            case S4:
                if (signExtend) {
                    pmovsxdq(dst, src);
                } else {
                    pmovzxdq(dst, src);
                }
                return;
        }
        throw GraalError.shouldNotReachHereUnexpectedValue(strideSrc); // ExcludeFromJacocoGeneratedReport
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
                throw GraalError.shouldNotReachHereUnexpectedValue(strideDst); // ExcludeFromJacocoGeneratedReport
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
                throw GraalError.shouldNotReachHereUnexpectedValue(strideDst); // ExcludeFromJacocoGeneratedReport
            case S4:
                if (signExtend) {
                    pmovsxdq(dst, src);
                } else {
                    pmovzxdq(dst, src);
                }
                return;
        }
        throw GraalError.shouldNotReachHereUnexpectedValue(strideSrc); // ExcludeFromJacocoGeneratedReport
    }

    public final void packuswb(AVXKind.AVXSize size, Register dst, Register src) {
        packuswb(size, dst, dst, src);
    }

    public final void packuswb(AVXKind.AVXSize size, Register dst, Register src1, Register src2) {
        simdRVMOp(VexRVMOp.VPACKUSWB, SSEOp.PACKUSWB, size, dst, src1, src2, false);
    }

    public final void packusdw(AVXKind.AVXSize size, Register dst, Register src) {
        packusdw(size, dst, dst, src);
    }

    public final void packusdw(AVXKind.AVXSize size, Register dst, Register src1, Register src2) {
        simdRVMOp(VexRVMOp.VPACKUSDW, SSEOp.PACKUSDW, size, dst, src1, src2, false);
    }

    public final void palignr(AVXKind.AVXSize size, Register dst, Register src, int imm8) {
        palignr(size, dst, dst, src, imm8);
    }

    public final void palignr(AVXKind.AVXSize size, Register dst, Register src1, Register src2, int imm8) {
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

    public final void pand(AVXKind.AVXSize size, Register dst, Register src) {
        pand(size, dst, dst, src);
    }

    public final void pand(AVXKind.AVXSize size, Register dst, Register src1, Register src2) {
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

    public final void pand(AVXKind.AVXSize size, Register dst, AMD64Address src) {
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
    public final void pandU(AVXKind.AVXSize size, Register dst, AMD64Address src, Register tmp) {
        if (isAVX()) {
            VexRVMOp.VPAND.emit(this, size, dst, dst, src);
        } else {
            // SSE
            movdqu(tmp, src);
            pand(dst, tmp);
        }
    }

    public final void pandn(AVXKind.AVXSize size, Register dst, Register src) {
        if (isAVX()) {
            VexRVMOp.VPANDN.emit(this, size, dst, dst, src);
        } else {
            // SSE
            pandn(dst, src);
        }
    }

    public final void por(AVXKind.AVXSize size, Register dst, Register src) {
        if (isAVX()) {
            VexRVMOp.VPOR.emit(this, size, dst, dst, src);
        } else {
            por(dst, src);
        }
    }

    public final void pxor(AVXKind.AVXSize size, Register dst, Register src) {
        pxor(size, dst, dst, src);
    }

    public final void pxor(AVXKind.AVXSize size, Register dst, Register src1, Register src2) {
        if (isAVX()) {
            VexRVMOp.VPXOR.emit(this, size, dst, src1, src2);
        } else {
            if (!dst.equals(src1)) {
                movdqu(dst, src1);
            }
            pxor(dst, src2);
        }
    }

    public final void psllw(AVXKind.AVXSize size, Register dst, Register src, int imm8) {
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

    public final void psrlw(AVXKind.AVXSize size, Register dst, Register src, int imm8) {
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

    public final void pslld(AVXKind.AVXSize size, Register dst, Register src, int imm8) {
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

    public final void psrld(AVXKind.AVXSize size, Register dst, Register src, int imm8) {
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

    public final void pshufb(AVXKind.AVXSize size, Register dst, Register src1, Register src2) {
        simdRVMOp(VexRVMOp.VPSHUFB, SSEOp.PSHUFB, size, dst, src1, src2, false);
    }

    public final void pshufb(AVXKind.AVXSize size, Register dst, Register src) {
        pshufb(size, dst, dst, src);
    }

    public final void pshufb(AVXKind.AVXSize size, Register dst, AMD64Address src) {
        if (isAVX()) {
            VexRVMOp.VPSHUFB.emit(this, size, dst, dst, src);
        } else {
            // SSE
            pshufb(dst, src);
        }
    }

    public final void ptest(AVXKind.AVXSize size, Register dst, Register src) {
        if (isAVX()) {
            VexRMOp.VPTEST.emit(this, size, dst, src);
        } else {
            ptest(dst, src);
        }
    }

    /**
     * PTEST with unaligned memory operand.
     */
    public final void ptestU(AVXKind.AVXSize size, Register dst, AMD64Address src, Register tmp) {
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

    public final void moveInt(Register dst, int imm) {
        if (imm == 0) {
            Register zeroValueRegister = getZeroValueRegister();
            if (!Register.None.equals(zeroValueRegister)) {
                movl(dst, zeroValueRegister);
                return;
            }
        }
        movl(dst, imm);
    }

    public final void moveInt(Register dst, int imm, boolean annotateImm) {
        if (!annotateImm) {
            moveInt(dst, imm);
        } else {
            movl(dst, imm, true);
        }
    }

    public final void moveInt(AMD64Address dst, int imm) {
        if (imm == 0) {
            Register zeroValueRegister = getZeroValueRegister();
            if (!Register.None.equals(zeroValueRegister)) {
                movl(dst, zeroValueRegister);
                return;
            }
        }
        movl(dst, imm);
    }

    public final void moveInt(AMD64Address dst, int imm, boolean annotateImm) {
        if (!annotateImm) {
            moveInt(dst, imm);
        } else {
            AMD64MIOp.MOV.emit(this, OperandSize.DWORD, dst, imm, true);
        }
    }

    public final void moveIntSignExtend(Register result, int imm) {
        if (imm == 0) {
            Register zeroValueRegister = getZeroValueRegister();
            if (!Register.None.equals(zeroValueRegister)) {
                movl(result, zeroValueRegister);
                return;
            }
        }
        movslq(result, imm);
    }

    private static AMD64MROp toMR(AMD64MIOp op) {
        if (op == AMD64MIOp.MOVB) {
            return AMD64MROp.MOVB;
        } else if (op == AMD64MIOp.MOV) {
            return AMD64MROp.MOV;
        } else if (op == AMD64MIOp.TEST) {
            return AMD64MROp.TEST;
        }
        return null;
    }

    public final void emitAMD64MIOp(AMD64MIOp opcode, OperandSize size, Register dst, int imm, boolean annotateImm) {
        if (imm == 0) {
            Register zeroValueRegister = getZeroValueRegister();
            AMD64MROp mrOp = toMR(opcode);
            if (!Register.None.equals(zeroValueRegister) && mrOp != null) {
                mrOp.emit(this, size, dst, zeroValueRegister);
                return;
            }
        }
        opcode.emit(this, size, dst, imm, annotateImm);
    }

    public final void emitAMD64MIOp(AMD64MIOp opcode, OperandSize size, AMD64Address dst, int imm, boolean annotateImm) {
        if (imm == 0) {
            Register zeroValueRegister = getZeroValueRegister();
            AMD64MROp mrOp = toMR(opcode);
            if (!Register.None.equals(zeroValueRegister) && mrOp != null) {
                mrOp.emit(this, size, dst, zeroValueRegister);
                return;
            }
        }
        opcode.emit(this, size, dst, imm, annotateImm);
    }

    /**
     * Returns a register whose content is always zero.
     */
    public Register getZeroValueRegister() {
        return Register.None;
    }
}
