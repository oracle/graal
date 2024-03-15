/*
 * Copyright (c) 2009, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.ADC;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.ADD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.AND;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.CMP;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.OR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.SBB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.SUB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.XOR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MOp.DEC;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MOp.INC;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MOp.MUL;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MOp.NEG;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MOp.NOT;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64RMIOp.SHA1RNDS4;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.ADCX;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.ADOX;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.IMUL;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.SHA1MSG1;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.SHA1MSG2;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.SHA1NEXTE;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.SHA256MSG1;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.SHA256MSG2;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.SHA256RNDS2;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64Shift.RCL;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64Shift.RCR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64Shift.ROL;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64Shift.ROR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexGeneralPurposeRVMOp.MULX;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VCVTPS2PH;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.RORXL;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.RORXQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VCVTPH2PS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VGF2P8AFFINEQB;
import static jdk.graal.compiler.core.common.NumUtil.isByte;
import static jdk.graal.compiler.core.common.NumUtil.isInt;
import static jdk.graal.compiler.core.common.NumUtil.isShiftCount;
import static jdk.graal.compiler.core.common.NumUtil.isUByte;
import static jdk.vm.ci.amd64.AMD64.CPU;
import static jdk.vm.ci.amd64.AMD64.MASK;
import static jdk.vm.ci.amd64.AMD64.XMM;
import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.amd64.AMD64.r13;
import static jdk.vm.ci.amd64.AMD64.rbp;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512BW;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512CD;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512DQ;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512F;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX512VL;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.F16C;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.GFNI;
import static jdk.vm.ci.code.MemoryBarriers.STORE_LOAD;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.graal.compiler.asm.BranchTargetOutOfBoundsException;
import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;

import org.graalvm.collections.EconomicSet;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.code.TargetDescription;

/**
 * This class implements an assembler that can encode most X86 instructions.
 */
public class AMD64Assembler extends AMD64BaseAssembler {

    public static class Options {
        // @formatter:off
        @Option(help = "Forces branch instructions to align with 32-bytes boundaries, to mitigate the jcc erratum. " +
                       "See https://www.intel.com/content/dam/support/us/en/documents/processors/mitigations-jump-conditional-code-erratum.pdf for more details. " +
                       "If not set explicitly, the default value is determined according to the CPU model..", type = OptionType.User)
        public static final OptionKey<Boolean> UseBranchesWithin32ByteBoundary = new OptionKey<>(false);
        // @formatter:on
    }

    private final boolean useBranchesWithin32ByteBoundary;
    private boolean optimizeLongJumps;

    /**
     * Constructs an assembler for the AMD64 architecture.
     */
    public AMD64Assembler(TargetDescription target) {
        super(target);
        useBranchesWithin32ByteBoundary = false;
        optimizeLongJumps = GraalOptions.OptimizeLongJumps.getDefaultValue();
    }

    public AMD64Assembler(TargetDescription target, OptionValues optionValues) {
        super(target);
        useBranchesWithin32ByteBoundary = Options.UseBranchesWithin32ByteBoundary.getValue(optionValues);
        optimizeLongJumps = GraalOptions.OptimizeLongJumps.getValue(optionValues);
    }

    public AMD64Assembler(TargetDescription target, OptionValues optionValues, boolean hasIntelJccErratum) {
        super(target);
        if (Options.UseBranchesWithin32ByteBoundary.hasBeenSet(optionValues)) {
            useBranchesWithin32ByteBoundary = Options.UseBranchesWithin32ByteBoundary.getValue(optionValues);
        } else {
            useBranchesWithin32ByteBoundary = hasIntelJccErratum;
        }
        optimizeLongJumps = GraalOptions.OptimizeLongJumps.getValue(optionValues);
    }

    /**
     * The x86 condition codes used for conditional jumps/moves.
     */
    public enum ConditionFlag {
        Zero(0x4, "|zero|"),
        NotZero(0x5, "|nzero|"),
        Equal(0x4, "="),
        NotEqual(0x5, "!="),
        Less(0xc, "<"),
        LessEqual(0xe, "<="),
        Greater(0xf, ">"),
        GreaterEqual(0xd, ">="),
        Below(0x2, "|<|"),
        BelowEqual(0x6, "|<=|"),
        Above(0x7, "|>|"),
        AboveEqual(0x3, "|>=|"),
        Overflow(0x0, "|of|"),
        NoOverflow(0x1, "|nof|"),
        CarrySet(0x2, "|carry|"),
        CarryClear(0x3, "|ncarry|"),
        Negative(0x8, "|neg|"),
        Positive(0x9, "|pos|"),
        Parity(0xa, "|par|"),
        NoParity(0xb, "|npar|");

        private final int value;
        private final String operator;

        ConditionFlag(int value, String operator) {
            this.value = value;
            this.operator = operator;
        }

        public ConditionFlag negate() {
            switch (this) {
                case Zero:
                    return NotZero;
                case NotZero:
                    return Zero;
                case Equal:
                    return NotEqual;
                case NotEqual:
                    return Equal;
                case Less:
                    return GreaterEqual;
                case LessEqual:
                    return Greater;
                case Greater:
                    return LessEqual;
                case GreaterEqual:
                    return Less;
                case Below:
                    return AboveEqual;
                case BelowEqual:
                    return Above;
                case Above:
                    return BelowEqual;
                case AboveEqual:
                    return Below;
                case Overflow:
                    return NoOverflow;
                case NoOverflow:
                    return Overflow;
                case CarrySet:
                    return CarryClear;
                case CarryClear:
                    return CarrySet;
                case Negative:
                    return Positive;
                case Positive:
                    return Negative;
                case Parity:
                    return NoParity;
                case NoParity:
                    return Parity;
            }
            throw new IllegalArgumentException();
        }

        public int getValue() {
            return value;
        }

        @Override
        public String toString() {
            return operator;
        }
    }

    /**
     * Operand size and register type constraints.
     */
    private enum OpAssertion {
        NoOperandAssertion(null, null),
        ByteAssertion(CPU, CPU, OperandSize.BYTE),
        ByteOrLargerAssertion(CPU, CPU, OperandSize.BYTE, OperandSize.WORD, OperandSize.DWORD, OperandSize.QWORD),
        WordOrLargerAssertion(CPU, CPU, OperandSize.WORD, OperandSize.DWORD, OperandSize.QWORD),
        DwordOrLargerAssertion(CPU, CPU, OperandSize.DWORD, OperandSize.QWORD),
        WordOrQwordAssertion(CPU, CPU, OperandSize.WORD, OperandSize.QWORD),
        QwordAssertion(CPU, CPU, OperandSize.QWORD),
        FloatAssertion(XMM, XMM, OperandSize.SS, OperandSize.SD, OperandSize.PS, OperandSize.PD),
        ScalarFloatAssertion(XMM, XMM, OperandSize.SS, OperandSize.SD),
        PackedFloatAssertion(XMM, XMM, OperandSize.PS, OperandSize.PD),
        SingleAssertion(XMM, XMM, OperandSize.SS),
        DoubleAssertion(XMM, XMM, OperandSize.SD),
        PackedSingleAssertion(XMM, XMM, OperandSize.PS),
        PackedDoubleAssertion(XMM, XMM, OperandSize.PD),
        IntToFloatAssertion(XMM, CPU, OperandSize.DWORD, OperandSize.QWORD),
        DwordToFloatAssertion(XMM, CPU, OperandSize.DWORD),
        QwordToFloatAssertion(XMM, CPU, OperandSize.QWORD),
        FloatToIntAssertion(CPU, XMM, OperandSize.DWORD, OperandSize.QWORD);

        private final RegisterCategory resultCategory;
        private final RegisterCategory inputCategory;
        private final OperandSize[] allowedSizes;

        OpAssertion(RegisterCategory resultCategory, RegisterCategory inputCategory, OperandSize... allowedSizes) {
            this.resultCategory = resultCategory;
            this.inputCategory = inputCategory;
            this.allowedSizes = allowedSizes;
        }

        protected boolean checkOperands(AMD64Op op, OperandSize size, Register resultReg, Register inputReg) {
            assert resultReg == null || resultCategory.equals(resultReg.getRegisterCategory()) : "invalid result register " + resultReg + " used in " + op;
            assert inputReg == null || inputCategory.equals(inputReg.getRegisterCategory()) : "invalid input register " + inputReg + " used in " + op;

            for (OperandSize s : allowedSizes) {
                if (size == s) {
                    return true;
                }
            }

            assert false : "invalid operand size " + size + " used in " + op;
            return false;
        }

    }

    protected static final int P_0F = 0x0F;
    protected static final int P_0F38 = 0x380F;
    protected static final int P_0F3A = 0x3A0F;

    /**
     * Base class for AMD64 opcodes.
     */
    public static class AMD64Op {

        private final String opcode;

        protected final int prefix1;
        protected final int prefix2;
        protected final int op;

        final boolean dstIsByte;
        final boolean srcIsByte;

        private final OpAssertion assertion;
        private final CPUFeature feature;

        protected AMD64Op(String opcode, int prefix1, int prefix2, int op, OpAssertion assertion, CPUFeature feature) {
            this(opcode, prefix1, prefix2, op, assertion == OpAssertion.ByteAssertion, assertion == OpAssertion.ByteAssertion, assertion, feature);
        }

        protected AMD64Op(String opcode, int prefix1, int prefix2, int op, boolean dstIsByte, boolean srcIsByte, OpAssertion assertion, CPUFeature feature) {
            this.opcode = opcode;
            this.prefix1 = prefix1;
            this.prefix2 = prefix2;
            this.op = op;

            this.dstIsByte = dstIsByte;
            this.srcIsByte = srcIsByte;

            this.assertion = assertion;
            this.feature = feature;
        }

        protected final void emitOpcode(AMD64Assembler asm, OperandSize size, int rxb, int dstEnc, int srcEnc) {
            if (prefix1 != 0) {
                asm.emitByte(prefix1);
            }
            if (size.getSizePrefix() != 0) {
                asm.emitByte(size.getSizePrefix());
            }
            int rexPrefix = 0x40 | rxb;
            if (size == OperandSize.QWORD) {
                rexPrefix |= 0x08;
            }
            if (rexPrefix != 0x40 || (dstIsByte && dstEnc >= 4) || (srcIsByte && srcEnc >= 4)) {
                asm.emitByte(rexPrefix);
            }
            if (prefix2 > 0xFF) {
                asm.emitShort(prefix2);
            } else if (prefix2 > 0) {
                asm.emitByte(prefix2);
            }
            asm.emitByte(op);
        }

        protected final boolean verify(AMD64Assembler asm, OperandSize size, Register resultReg, Register inputReg) {
            assert feature == null || asm.supports(feature) : String.format("unsupported feature %s required for %s", feature, opcode);
            assert assertion.checkOperands(this, size, resultReg, inputReg) : "Operands must verify for " + inputReg + " " + resultReg;
            return true;
        }

        public OperandSize[] getAllowedSizes() {
            return assertion.allowedSizes;
        }

        protected final boolean isSSEInstruction() {
            if (feature == null) {
                return false;
            }
            switch (feature) {
                case SSE:
                case SSE2:
                case SSE3:
                case SSSE3:
                case SSE4A:
                case SSE4_1:
                case SSE4_2:
                    return true;
                default:
                    return false;
            }
        }

        public final OpAssertion getAssertion() {
            return assertion;
        }

        @Override
        public String toString() {
            return opcode;
        }
    }

    /**
     * Base class for AMD64 opcodes with immediate operands.
     */
    public static class AMD64ImmOp extends AMD64Op {

        private final boolean immIsByte;

        protected AMD64ImmOp(String opcode, boolean immIsByte, int prefix, int op, OpAssertion assertion) {
            this(opcode, immIsByte, prefix, op, assertion, null);
        }

        protected AMD64ImmOp(String opcode, boolean immIsByte, int prefix, int op, OpAssertion assertion, CPUFeature feature) {
            super(opcode, 0, prefix, op, assertion, feature);
            this.immIsByte = immIsByte;
        }

        protected final void emitImmediate(AMD64Assembler asm, OperandSize size, int imm) {
            if (immIsByte) {
                assert imm == (byte) imm : imm;
                asm.emitByte(imm);
            } else {
                size.emitImmediate(asm, imm);
            }
        }

        public final int immediateSize(OperandSize size) {
            if (immIsByte) {
                return 1;
            } else {
                return size.immediateSize();
            }
        }
    }

    /**
     * Opcode with operand order of either RM or MR for 2 address forms.
     */
    public abstract static class AMD64RROp extends AMD64Op {

        protected AMD64RROp(String opcode, int prefix1, int prefix2, int op, OpAssertion assertion, CPUFeature feature) {
            super(opcode, prefix1, prefix2, op, assertion, feature);
        }

        protected AMD64RROp(String opcode, int prefix1, int prefix2, int op, boolean dstIsByte, boolean srcIsByte, OpAssertion assertion, CPUFeature feature) {
            super(opcode, prefix1, prefix2, op, dstIsByte, srcIsByte, assertion, feature);
        }

        public abstract void emit(AMD64Assembler asm, OperandSize size, Register dst, Register src);
    }

    /**
     * Opcode with operand order of RM.
     */
    public static class AMD64RMOp extends AMD64RROp {
        // @formatter:off
        public static final AMD64RMOp IMUL   = new AMD64RMOp("IMUL",         P_0F, 0xAF, OpAssertion.ByteOrLargerAssertion);
        public static final AMD64RMOp BSF    = new AMD64RMOp("BSF",          P_0F, 0xBC, OpAssertion.WordOrLargerAssertion);
        public static final AMD64RMOp BSR    = new AMD64RMOp("BSR",          P_0F, 0xBD, OpAssertion.WordOrLargerAssertion);
        // POPCNT, TZCNT, and LZCNT support word operation. However, the legacy size prefix should
        // be emitted before the mandatory prefix 0xF3. Since we are not emitting bit count for
        // 16-bit operands, here we simply use DwordOrLargerAssertion.
        public static final AMD64RMOp POPCNT = new AMD64RMOp("POPCNT", 0xF3, P_0F, 0xB8, OpAssertion.DwordOrLargerAssertion, CPUFeature.POPCNT);
        public static final AMD64RMOp TZCNT  = new AMD64RMOp("TZCNT",  0xF3, P_0F, 0xBC, OpAssertion.DwordOrLargerAssertion, CPUFeature.BMI1);
        public static final AMD64RMOp LZCNT  = new AMD64RMOp("LZCNT",  0xF3, P_0F, 0xBD, OpAssertion.DwordOrLargerAssertion, CPUFeature.LZCNT);
        public static final AMD64RMOp MOVZXB = new AMD64RMOp("MOVZXB",       P_0F, 0xB6, false, true, OpAssertion.WordOrLargerAssertion);
        public static final AMD64RMOp MOVZX  = new AMD64RMOp("MOVZX",        P_0F, 0xB7, OpAssertion.DwordOrLargerAssertion);
        public static final AMD64RMOp MOVSXB = new AMD64RMOp("MOVSXB",       P_0F, 0xBE, false, true, OpAssertion.WordOrLargerAssertion);
        public static final AMD64RMOp MOVSX  = new AMD64RMOp("MOVSX",        P_0F, 0xBF, OpAssertion.DwordOrLargerAssertion);
        public static final AMD64RMOp MOVSXD = new AMD64RMOp("MOVSXD",             0x63, OpAssertion.QwordAssertion);
        public static final AMD64RMOp MOVB   = new AMD64RMOp("MOVB",               0x8A, OpAssertion.ByteAssertion);
        public static final AMD64RMOp MOV    = new AMD64RMOp("MOV",                0x8B, OpAssertion.WordOrLargerAssertion);
        public static final AMD64RMOp CMP    = new AMD64RMOp("CMP",                0x3B, OpAssertion.WordOrLargerAssertion);

        // TEST is documented as MR operation, but it's symmetric, and using it as RM operation is more convenient.
        public static final AMD64RMOp TESTB  = new AMD64RMOp("TEST",               0x84, OpAssertion.ByteAssertion);
        public static final AMD64RMOp TEST   = new AMD64RMOp("TEST",               0x85, OpAssertion.WordOrLargerAssertion);

        // ADX instructions
        public static final AMD64RMOp ADCX   = new AMD64RMOp("ADCX", 0x66, P_0F38, 0xF6, OpAssertion.DwordOrLargerAssertion, CPUFeature.ADX);
        public static final AMD64RMOp ADOX   = new AMD64RMOp("ADOX", 0xF3, P_0F38, 0xF6, OpAssertion.DwordOrLargerAssertion, CPUFeature.ADX);

        // SHA instructions
        public static final AMD64RMOp SHA1MSG1    = new AMD64RMOp("SHA1MSG1",    P_0F38, 0xC9, OpAssertion.PackedSingleAssertion, CPUFeature.SHA);
        public static final AMD64RMOp SHA1MSG2    = new AMD64RMOp("SHA1MSG2",    P_0F38, 0xCA, OpAssertion.PackedSingleAssertion, CPUFeature.SHA);
        public static final AMD64RMOp SHA1NEXTE   = new AMD64RMOp("SHA1NEXTE",   P_0F38, 0xC8, OpAssertion.PackedSingleAssertion, CPUFeature.SHA);

        public static final AMD64RMOp SHA256MSG1  = new AMD64RMOp("SHA256MSG1",  P_0F38, 0xCC, OpAssertion.PackedSingleAssertion, CPUFeature.SHA);
        public static final AMD64RMOp SHA256MSG2  = new AMD64RMOp("SHA256MSG2",  P_0F38, 0xCD, OpAssertion.PackedSingleAssertion, CPUFeature.SHA);
        public static final AMD64RMOp SHA256RNDS2 = new AMD64RMOp("SHA256RNDS2", P_0F38, 0xCB, OpAssertion.PackedSingleAssertion, CPUFeature.SHA);

        // @formatter:on

        protected AMD64RMOp(String opcode, int op, OpAssertion assertion) {
            this(opcode, 0, 0, op, assertion, null);
        }

        protected AMD64RMOp(String opcode, int prefix, int op, OpAssertion assertion) {
            this(opcode, 0, prefix, op, assertion, null);
        }

        protected AMD64RMOp(String opcode, int prefix, int op, OpAssertion assertion, CPUFeature feature) {
            super(opcode, 0, prefix, op, assertion, feature);
        }

        protected AMD64RMOp(String opcode, int prefix1, int prefix2, int op, OpAssertion assertion, CPUFeature feature) {
            super(opcode, prefix1, prefix2, op, assertion, feature);
        }

        protected AMD64RMOp(String opcode, int prefix, int op, boolean dstIsByte, boolean srcIsByte, OpAssertion assertion) {
            super(opcode, 0, prefix, op, dstIsByte, srcIsByte, assertion, null);
        }

        @Override
        public void emit(AMD64Assembler asm, OperandSize size, Register dst, Register src) {
            assert verify(asm, size, dst, src);
            assert !isSSEInstruction();
            emitOpcode(asm, size, getRXB(dst, src), dst.encoding, src.encoding);
            asm.emitModRM(dst, src);
        }

        public void emit(AMD64Assembler asm, OperandSize size, Register dst, AMD64Address src) {
            assert verify(asm, size, dst, null);
            assert !isSSEInstruction();
            emitOpcode(asm, size, getRXB(dst, src), dst.encoding, 0);
            asm.emitOperandHelper(dst, src, 0);
        }
    }

    /**
     * Opcode with operand order of MR.
     */
    public static class AMD64MROp extends AMD64RROp {
        // @formatter:off
        public static final AMD64MROp MOVB   = new AMD64MROp("MOVB", 0x88, OpAssertion.ByteAssertion);
        public static final AMD64MROp MOV    = new AMD64MROp("MOV",  0x89, OpAssertion.WordOrLargerAssertion);
       // @formatter:on

        protected AMD64MROp(String opcode, int op, OpAssertion assertion) {
            this(opcode, 0, 0, op, assertion, null);
        }

        protected AMD64MROp(String opcode, int prefix, int op, OpAssertion assertion) {
            this(opcode, 0, prefix, op, assertion, null);
        }

        protected AMD64MROp(String opcode, int prefix1, int prefix2, int op, OpAssertion assertion, CPUFeature feature) {
            super(opcode, prefix1, prefix2, op, assertion, feature);
        }

        @Override
        public void emit(AMD64Assembler asm, OperandSize size, Register dst, Register src) {
            assert verify(asm, size, src, dst);
            assert !isSSEInstruction();
            emitOpcode(asm, size, getRXB(src, dst), src.encoding, dst.encoding);
            asm.emitModRM(src, dst);
        }

        public void emit(AMD64Assembler asm, OperandSize size, AMD64Address dst, Register src) {
            assert verify(asm, size, src, null);
            assert !isSSEInstruction();
            emitOpcode(asm, size, getRXB(src, dst), src.encoding, 0);
            asm.emitOperandHelper(src, dst, 0);
        }
    }

    /**
     * Opcodes with operand order of M.
     */
    public static final class AMD64MOp extends AMD64Op {
        // @formatter:off
        public static final AMD64MOp NOTB = new AMD64MOp("NOT",  0xF6, 2, OpAssertion.ByteAssertion);
        public static final AMD64MOp NEGB = new AMD64MOp("NEG",  0xF6, 3, OpAssertion.ByteAssertion);
        public static final AMD64MOp NOT  = new AMD64MOp("NOT",  0xF7, 2);
        public static final AMD64MOp NEG  = new AMD64MOp("NEG",  0xF7, 3);
        public static final AMD64MOp MUL  = new AMD64MOp("MUL",  0xF7, 4);
        public static final AMD64MOp IMUL = new AMD64MOp("IMUL", 0xF7, 5);
        public static final AMD64MOp DIV  = new AMD64MOp("DIV",  0xF7, 6);
        public static final AMD64MOp IDIV = new AMD64MOp("IDIV", 0xF7, 7);
        public static final AMD64MOp INCB = new AMD64MOp("INC",  0xFE, 0, OpAssertion.ByteAssertion);
        public static final AMD64MOp DECB = new AMD64MOp("DEC",  0xFE, 1, OpAssertion.ByteAssertion);
        public static final AMD64MOp INC  = new AMD64MOp("INC",  0xFF, 0);
        public static final AMD64MOp DEC  = new AMD64MOp("DEC",  0xFF, 1);
        public static final AMD64MOp PUSH = new AMD64MOp("PUSH", 0xFF, 6, OpAssertion.WordOrQwordAssertion);
        public static final AMD64MOp POP  = new AMD64MOp("POP",  0x8F, 0, OpAssertion.WordOrQwordAssertion);
        // @formatter:on

        private final int ext;

        protected AMD64MOp(String opcode, int op, int ext) {
            this(opcode, 0, op, ext, OpAssertion.WordOrLargerAssertion);
        }

        protected AMD64MOp(String opcode, int op, int ext, OpAssertion assertion) {
            this(opcode, 0, op, ext, assertion);
        }

        protected AMD64MOp(String opcode, int prefix, int op, int ext, OpAssertion assertion) {
            super(opcode, 0, prefix, op, assertion, null);
            this.ext = ext;
        }

        public void emit(AMD64Assembler asm, OperandSize size, Register dst) {
            assert verify(asm, size, dst, null);
            emitOpcode(asm, size, getRXB(null, dst), 0, dst.encoding);
            asm.emitModRM(ext, dst);
        }

        public void emit(AMD64Assembler asm, OperandSize size, AMD64Address dst) {
            assert verify(asm, size, null, null);
            emitOpcode(asm, size, getRXB(null, dst), 0, 0);
            asm.emitOperandHelper(ext, dst, 0);
        }
    }

    /**
     * Opcodes with operand order of MI.
     */
    public static class AMD64MIOp extends AMD64ImmOp {
        // @formatter:off
        public static final AMD64MIOp MOVB = new AMD64MIOp("MOVB", true,  0xC6, 0, OpAssertion.ByteAssertion);
        public static final AMD64MIOp MOV  = new AMD64MIOp("MOV",  false, 0xC7, 0);
        public static final AMD64MIOp TEST = new AMD64MIOp("TEST", false, 0xF7, 0);
        // @formatter:on

        private final int ext;

        protected AMD64MIOp(String opcode, boolean immIsByte, int op, int ext) {
            this(opcode, immIsByte, op, ext, OpAssertion.WordOrLargerAssertion);
        }

        protected AMD64MIOp(String opcode, boolean immIsByte, int op, int ext, OpAssertion assertion) {
            this(opcode, immIsByte, 0, op, ext, assertion);
        }

        protected AMD64MIOp(String opcode, boolean immIsByte, int prefix, int op, int ext, OpAssertion assertion) {
            super(opcode, immIsByte, prefix, op, assertion);
            this.ext = ext;
        }

        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, int imm) {
            emit(asm, size, dst, imm, false);
        }

        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, int imm, boolean annotateImm) {
            assert verify(asm, size, dst, null);
            int insnPos = asm.position();
            emitOpcode(asm, size, getRXB(null, dst), 0, dst.encoding);
            asm.emitModRM(ext, dst);
            int immPos = asm.position();
            emitImmediate(asm, size, imm);
            int nextInsnPos = asm.position();
            if (annotateImm && asm.codePatchingAnnotationConsumer != null) {
                asm.codePatchingAnnotationConsumer.accept(new OperandDataAnnotation(insnPos, immPos, nextInsnPos - immPos, nextInsnPos));
            }
        }

        public final void emit(AMD64Assembler asm, OperandSize size, AMD64Address dst, int imm) {
            emit(asm, size, dst, imm, false);
        }

        public final void emit(AMD64Assembler asm, OperandSize size, AMD64Address dst, int imm, boolean annotateImm) {
            assert verify(asm, size, null, null);
            int insnPos = asm.position();
            emitOpcode(asm, size, getRXB(null, dst), 0, 0);
            asm.emitOperandHelper(ext, dst, immediateSize(size));
            int immPos = asm.position();
            emitImmediate(asm, size, imm);
            int nextInsnPos = asm.position();
            if (annotateImm && asm.codePatchingAnnotationConsumer != null) {
                asm.codePatchingAnnotationConsumer.accept(new OperandDataAnnotation(insnPos, immPos, nextInsnPos - immPos, nextInsnPos));
            }
        }
    }

    /**
     * Denotes the preferred nds register (VEX.vvvv) for VEX-encoding of an SSE instruction.
     *
     * For RM instructions where VEX.vvvv is reserved and must be 1111b, we should use
     * {@link PreferredNDS#NONE}. For RVM instructions, the default should be
     * {@link PreferredNDS#DST} to mimic the semantic of {@code dst <- op (dst, src)}. We should
     * only use {@link PreferredNDS#SRC} for unary instructions, e.g., ROUNDSS. This would help us
     * avoid an implicit dependency to {@code dst} register.
     *
     * Note that when {@code src} is a memory address, we will choose {@code dst} as {@code nds}
     * even if {@link PreferredNDS#SRC} is specified, which implies an implicit dependency to
     * {@code dst}. In {@link jdk.graal.compiler.lir.amd64.vector.AMD64VectorUnary.AVXConvertOp}, we
     * manually insert an {@code XOR} instruction for {@code dst}.
     */
    private enum PreferredNDS {
        NONE,
        DST,
        SRC;

        public Register getNds(Register reg, @SuppressWarnings("unused") AMD64Address address) {
            switch (this) {
                case DST:
                case SRC:
                    return reg;
                default:
                    return Register.None;
            }
        }

        public Register getNds(Register dst, Register src) {
            switch (this) {
                case DST:
                    return dst;
                case SRC:
                    return src;
                default:
                    return Register.None;
            }
        }
    }

    /**
     * Opcodes with operand order of RMI.
     *
     * We only have one form of round as the operation is always treated with single variant input,
     * making its extension to 3 address forms redundant.
     */
    public static class AMD64RMIOp extends AMD64ImmOp {
        // @formatter:off
        public static final AMD64RMIOp IMUL    = new AMD64RMIOp("IMUL", false, 0x69);
        public static final AMD64RMIOp IMUL_SX = new AMD64RMIOp("IMUL", true,  0x6B);
        public static final AMD64RMIOp ROUNDSS = new AMD64RMIOp("ROUNDSS", true, P_0F3A, 0x0A, PreferredNDS.SRC, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);
        public static final AMD64RMIOp ROUNDSD = new AMD64RMIOp("ROUNDSD", true, P_0F3A, 0x0B, PreferredNDS.SRC, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);

        public static final AMD64RMIOp SHA1RNDS4 = new AMD64RMIOp("SHA1RNDS4", true, P_0F3A, 0xCC, PreferredNDS.NONE, OpAssertion.PackedSingleAssertion, CPUFeature.SHA);
        // @formatter:on

        private final PreferredNDS preferredNDS;

        protected AMD64RMIOp(String opcode, boolean immIsByte, int op) {
            super(opcode, immIsByte, 0, op, OpAssertion.WordOrLargerAssertion, null);
            this.preferredNDS = PreferredNDS.NONE;
        }

        protected AMD64RMIOp(String opcode, boolean immIsByte, int prefix, int op, PreferredNDS preferredNDS, OpAssertion assertion, CPUFeature feature) {
            super(opcode, immIsByte, prefix, op, assertion, feature);
            this.preferredNDS = preferredNDS;
        }

        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, Register src, int imm) {
            assert verify(asm, size, dst, src);
            if (isSSEInstruction()) {
                asm.simdPrefix(dst, preferredNDS.getNds(dst, src), src, size, prefix1, prefix2, false);
                asm.emitByte(op);
                asm.emitModRM(dst, src);
            } else {
                emitOpcode(asm, size, getRXB(dst, src), dst.encoding, src.encoding);
                asm.emitModRM(dst, src);
            }
            emitImmediate(asm, size, imm);
        }

        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, AMD64Address src, int imm) {
            assert verify(asm, size, dst, null);
            if (isSSEInstruction()) {
                asm.simdPrefix(dst, preferredNDS.getNds(dst, src), src, size, prefix1, prefix2, false);
                asm.emitByte(op);
            } else {
                emitOpcode(asm, size, getRXB(dst, src), dst.encoding, 0);
            }
            asm.emitOperandHelper(dst, src, immediateSize(size));
            emitImmediate(asm, size, imm);
        }
    }

    /**
     * Opcodes with no operands.
     */
    public static final class AMD64Z0Op extends AMD64Op {

        public static final AMD64Z0Op PUSHFQ = new AMD64Z0Op("PUSHFQ", 0x9c);

        protected AMD64Z0Op(String opcode, int op) {
            this(opcode, 0, 0, op, null);
        }

        protected AMD64Z0Op(String opcode, int prefix1, int prefix2, int op, CPUFeature feature) {
            super(opcode, prefix1, prefix2, op, OpAssertion.NoOperandAssertion, feature);
        }

        public void emit(AMD64Assembler asm) {
            this.emitOpcode(asm, OperandSize.BYTE, getRXB(null, (Register) null), 0, 0);
        }

    }

    public static class SSEOp extends AMD64RMOp {
        // @formatter:off
        public static final SSEOp CVTSI2SS  = new SSEOp("CVTSI2SS",  0xF3, P_0F, 0x2A, PreferredNDS.DST,  OpAssertion.IntToFloatAssertion);
        public static final SSEOp CVTSI2SD  = new SSEOp("CVTSI2SD",  0xF2, P_0F, 0x2A, PreferredNDS.DST,  OpAssertion.IntToFloatAssertion);
        public static final SSEOp CVTTSS2SI = new SSEOp("CVTTSS2SI", 0xF3, P_0F, 0x2C, PreferredNDS.NONE, OpAssertion.FloatToIntAssertion);
        public static final SSEOp CVTTSD2SI = new SSEOp("CVTTSD2SI", 0xF2, P_0F, 0x2C, PreferredNDS.NONE, OpAssertion.FloatToIntAssertion);
        public static final SSEOp UCOMIS    = new SSEOp("UCOMIS",          P_0F, 0x2E, PreferredNDS.NONE, OpAssertion.PackedFloatAssertion);
        public static final SSEOp SQRT      = new SSEOp("SQRT",            P_0F, 0x51, PreferredNDS.SRC,  OpAssertion.ScalarFloatAssertion);
        public static final SSEOp AND       = new SSEOp("AND",             P_0F, 0x54, PreferredNDS.DST,  OpAssertion.PackedFloatAssertion);
        public static final SSEOp ANDN      = new SSEOp("ANDN",            P_0F, 0x55, PreferredNDS.DST,  OpAssertion.PackedFloatAssertion);
        public static final SSEOp OR        = new SSEOp("OR",              P_0F, 0x56, PreferredNDS.DST,  OpAssertion.PackedFloatAssertion);
        public static final SSEOp XOR       = new SSEOp("XOR",             P_0F, 0x57, PreferredNDS.DST,  OpAssertion.PackedFloatAssertion);
        public static final SSEOp ADD       = new SSEOp("ADD",             P_0F, 0x58, PreferredNDS.DST);
        public static final SSEOp MUL       = new SSEOp("MUL",             P_0F, 0x59, PreferredNDS.DST);
        public static final SSEOp CVTSS2SD  = new SSEOp("CVTSS2SD",        P_0F, 0x5A, PreferredNDS.SRC,  OpAssertion.SingleAssertion);
        public static final SSEOp CVTSD2SS  = new SSEOp("CVTSD2SS",        P_0F, 0x5A, PreferredNDS.SRC,  OpAssertion.DoubleAssertion);
        public static final SSEOp SUB       = new SSEOp("SUB",             P_0F, 0x5C, PreferredNDS.DST);
        public static final SSEOp MIN       = new SSEOp("MIN",             P_0F, 0x5D, PreferredNDS.DST);
        public static final SSEOp DIV       = new SSEOp("DIV",             P_0F, 0x5E, PreferredNDS.DST);
        public static final SSEOp MAX       = new SSEOp("MAX",             P_0F, 0x5F, PreferredNDS.DST);
        public static final SSEOp PSUBUSB   = new SSEOp("PSUBUSB",         P_0F, 0xD8, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);
        public static final SSEOp PSUBUSW   = new SSEOp("PSUBUSW",         P_0F, 0xD9, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);
        public static final SSEOp PMINUB    = new SSEOp("PMINUB",          P_0F, 0xDA, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);
        public static final SSEOp PMINUW    = new SSEOp("PMINUW",        P_0F38, 0x3A, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);
        public static final SSEOp PMINUD    = new SSEOp("PMINUD",        P_0F38, 0x3B, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);

        public static final SSEOp PACKUSWB  = new SSEOp("PACKUSWB",        P_0F, 0x67, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);
        public static final SSEOp PACKUSDW  = new SSEOp("PACKUSDW",      P_0F38, 0x2B, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion,  CPUFeature.SSE4_1);

        public static final SSEOp PSHUFB    = new SSEOp("PSHUFB",        P_0F38, 0x00, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion, CPUFeature.SSSE3);

        // MOVD/MOVQ and MOVSS/MOVSD are the same opcode, just with different operand size prefix
        public static final SSEOp MOVD      = new SSEOp("MOVD",      0x66, P_0F, 0x6E, PreferredNDS.NONE, OpAssertion.DwordToFloatAssertion);
        public static final SSEOp MOVQ      = new SSEOp("MOVQ",      0x66, P_0F, 0x6E, PreferredNDS.NONE, OpAssertion.QwordToFloatAssertion);
        public static final SSEOp MOVSS     = new SSEOp("MOVSS",           P_0F, 0x10, PreferredNDS.SRC,  OpAssertion.SingleAssertion);
        public static final SSEOp MOVSD     = new SSEOp("MOVSD",           P_0F, 0x10, PreferredNDS.SRC,  OpAssertion.DoubleAssertion);
        // @formatter:on

        private final PreferredNDS preferredNDS;

        protected SSEOp(String opcode, int prefix, int op, PreferredNDS preferredNDS) {
            this(opcode, 0, prefix, op, preferredNDS, OpAssertion.FloatAssertion);
        }

        protected SSEOp(String opcode, int prefix, int op, PreferredNDS preferredNDS, CPUFeature feature) {
            this(opcode, 0, prefix, op, preferredNDS, OpAssertion.FloatAssertion, feature);
        }

        protected SSEOp(String opcode, int prefix, int op, PreferredNDS preferredNDS, OpAssertion assertion) {
            this(opcode, 0, prefix, op, preferredNDS, assertion);
        }

        protected SSEOp(String opcode, int prefix, int op, PreferredNDS preferredNDS, OpAssertion assertion, CPUFeature feature) {
            this(opcode, 0, prefix, op, preferredNDS, assertion, feature);
        }

        protected SSEOp(String opcode, int mandatoryPrefix, int prefix, int op, PreferredNDS preferredNDS, OpAssertion assertion) {
            this(opcode, mandatoryPrefix, prefix, op, preferredNDS, assertion, CPUFeature.SSE2);
        }

        protected SSEOp(String opcode, int mandatoryPrefix, int prefix, int op, PreferredNDS preferredNDS, OpAssertion assertion, CPUFeature feature) {
            super(opcode, mandatoryPrefix, prefix, op, assertion, feature);
            this.preferredNDS = preferredNDS;
        }

        @Override
        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, Register src) {
            assert verify(asm, size, dst, src);
            assert isSSEInstruction();
            Register nds = preferredNDS.getNds(dst, src);
            asm.simdPrefix(dst, nds, src, size, prefix1, prefix2, size == OperandSize.QWORD);
            asm.emitByte(op);
            asm.emitModRM(dst, src);
        }

        @Override
        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, AMD64Address src) {
            assert verify(asm, size, dst, null);
            assert isSSEInstruction();
            // MOVSS/SD are not RVM instruction when the dst is an address
            Register nds = (this == MOVSS || this == MOVSD) ? Register.None : preferredNDS.getNds(dst, src);
            asm.simdPrefix(dst, nds, src, size, prefix1, prefix2, size == OperandSize.QWORD);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src, 0);
        }
    }

    /**
     * Opcode with operand order of MR.
     */
    public static class SSEMROp extends AMD64MROp {
        // @formatter:off
        // MOVD and MOVQ are the same opcode, just with different operand size prefix
        // Note that as MR opcodes, they have reverse operand order, so the IntToFloatingAssertion must be used.
        public static final SSEMROp MOVD  = new SSEMROp("MOVD", 0x66, P_0F, 0x7E, PreferredNDS.NONE, OpAssertion.DwordToFloatAssertion);
        public static final SSEMROp MOVQ  = new SSEMROp("MOVQ", 0x66, P_0F, 0x7E, PreferredNDS.NONE, OpAssertion.QwordToFloatAssertion);
        // MOVSS and MOVSD are the same opcode, just with different operand size prefix
        public static final SSEMROp MOVSS = new SSEMROp("MOVSS",      P_0F, 0x11, PreferredNDS.SRC,  OpAssertion.SingleAssertion);
        public static final SSEMROp MOVSD = new SSEMROp("MOVSD",      P_0F, 0x11, PreferredNDS.SRC,  OpAssertion.DoubleAssertion);
        // @formatter:on

        private final PreferredNDS preferredNDS;

        protected SSEMROp(String opcode, int prefix, int op, PreferredNDS preferredNDS, OpAssertion assertion) {
            this(opcode, 0, prefix, op, preferredNDS, assertion);
        }

        protected SSEMROp(String opcode, int prefix1, int prefix2, int op, PreferredNDS preferredNDS, OpAssertion assertion) {
            super(opcode, prefix1, prefix2, op, assertion, CPUFeature.SSE2);
            this.preferredNDS = preferredNDS;
        }

        @Override
        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, Register src) {
            assert verify(asm, size, src, dst);
            assert isSSEInstruction();
            asm.simdPrefix(src, preferredNDS.getNds(dst, src), dst, size, prefix1, prefix2, size == OperandSize.QWORD);
            asm.emitByte(op);
            asm.emitModRM(src, dst);
        }

        @Override
        public final void emit(AMD64Assembler asm, OperandSize size, AMD64Address dst, Register src) {
            assert verify(asm, size, src, null);
            assert isSSEInstruction();
            // MOVSS/SD are not RVM instruction when the dst is an address
            Register nds = (this == MOVSS || this == MOVSD) ? Register.None : preferredNDS.getNds(src, dst);
            asm.simdPrefix(src, nds, dst, size, prefix1, prefix2, size == OperandSize.QWORD);
            asm.emitByte(op);
            asm.emitOperandHelper(src, dst, 0);
        }
    }

    /**
     * Arithmetic operation with operand order of RM, MR or MI.
     */
    public static final class AMD64BinaryArithmetic {
        // @formatter:off
        public static final AMD64BinaryArithmetic ADD = new AMD64BinaryArithmetic("ADD", 0);
        public static final AMD64BinaryArithmetic OR  = new AMD64BinaryArithmetic("OR",  1);
        public static final AMD64BinaryArithmetic ADC = new AMD64BinaryArithmetic("ADC", 2);
        public static final AMD64BinaryArithmetic SBB = new AMD64BinaryArithmetic("SBB", 3);
        public static final AMD64BinaryArithmetic AND = new AMD64BinaryArithmetic("AND", 4);
        public static final AMD64BinaryArithmetic SUB = new AMD64BinaryArithmetic("SUB", 5);
        public static final AMD64BinaryArithmetic XOR = new AMD64BinaryArithmetic("XOR", 6);
        public static final AMD64BinaryArithmetic CMP = new AMD64BinaryArithmetic("CMP", 7);
        // @formatter:on

        private final AMD64MIOp byteImmOp;
        private final AMD64MROp byteMrOp;
        private final AMD64RMOp byteRmOp;

        private final AMD64MIOp immOp;
        private final AMD64MIOp immSxOp;
        private final AMD64MROp mrOp;
        private final AMD64RMOp rmOp;

        private AMD64BinaryArithmetic(String opcode, int code) {
            int baseOp = code << 3;

            byteImmOp = new AMD64MIOp(opcode, true, 0, 0x80, code, OpAssertion.ByteAssertion);
            byteMrOp = new AMD64MROp(opcode, 0, baseOp, OpAssertion.ByteAssertion);
            byteRmOp = new AMD64RMOp(opcode, 0, baseOp | 0x02, OpAssertion.ByteAssertion);

            immOp = new AMD64MIOp(opcode, false, 0, 0x81, code, OpAssertion.WordOrLargerAssertion);
            immSxOp = new AMD64MIOp(opcode, true, 0, 0x83, code, OpAssertion.WordOrLargerAssertion);
            mrOp = new AMD64MROp(opcode, 0, baseOp | 0x01, OpAssertion.WordOrLargerAssertion);
            rmOp = new AMD64RMOp(opcode, 0, baseOp | 0x03, OpAssertion.WordOrLargerAssertion);
        }

        public AMD64MIOp getMIOpcode(OperandSize size, boolean sx) {
            if (size == OperandSize.BYTE) {
                return byteImmOp;
            } else if (sx) {
                return immSxOp;
            } else {
                return immOp;
            }
        }

        public AMD64MROp getMROpcode(OperandSize size) {
            if (size == OperandSize.BYTE) {
                return byteMrOp;
            } else {
                return mrOp;
            }
        }

        public AMD64RMOp getRMOpcode(OperandSize size) {
            if (size == OperandSize.BYTE) {
                return byteRmOp;
            } else {
                return rmOp;
            }
        }
    }

    /**
     * Shift operation with operand order of M1, MC or MI.
     */
    public static final class AMD64Shift {
        // @formatter:off
        public static final AMD64Shift ROL = new AMD64Shift("ROL", 0);
        public static final AMD64Shift ROR = new AMD64Shift("ROR", 1);
        public static final AMD64Shift RCL = new AMD64Shift("RCL", 2);
        public static final AMD64Shift RCR = new AMD64Shift("RCR", 3);
        public static final AMD64Shift SHL = new AMD64Shift("SHL", 4);
        public static final AMD64Shift SHR = new AMD64Shift("SHR", 5);
        public static final AMD64Shift SAR = new AMD64Shift("SAR", 7);
        // @formatter:on

        public final AMD64MOp m1Op;
        public final AMD64MOp mcOp;
        public final AMD64MIOp miOp;

        private AMD64Shift(String opcode, int code) {
            m1Op = new AMD64MOp(opcode, 0, 0xD1, code, OpAssertion.WordOrLargerAssertion);
            mcOp = new AMD64MOp(opcode, 0, 0xD3, code, OpAssertion.WordOrLargerAssertion);
            miOp = new AMD64MIOp(opcode, true, 0, 0xC1, code, OpAssertion.WordOrLargerAssertion);
        }
    }

    private enum EVEXFeatureAssertion {
        /*
         * With very few exceptions (namely, KMOV{B,D,Q}), all AVX512 instructions require AVX512F.
         * For simplicity, include AVX512F in all AVX512 assertions, and mention it in the name of
         * the assertion even though it is implied.
         */
        AVX512F_ALL(EnumSet.of(AVX512F), EnumSet.of(AVX512F), EnumSet.of(AVX512F)),
        AVX512F_128ONLY(EnumSet.of(AVX512F), null, null),
        AVX512F_VL(EnumSet.of(AVX512F, AVX512VL), EnumSet.of(AVX512F, AVX512VL), EnumSet.of(AVX512F)),
        AVX512F_CD_VL(EnumSet.of(AVX512F, AVX512CD, AVX512VL), EnumSet.of(AVX512F, AVX512CD, AVX512VL), EnumSet.of(AVX512F, AVX512CD)),
        AVX512F_DQ_ALL(EnumSet.of(AVX512F, AVX512DQ), EnumSet.of(AVX512F, AVX512DQ), EnumSet.of(AVX512F, AVX512DQ)),
        AVX512F_DQ_VL(EnumSet.of(AVX512F, AVX512DQ, AVX512VL), EnumSet.of(AVX512F, AVX512DQ, AVX512VL), EnumSet.of(AVX512F, AVX512DQ)),
        AVX512F_BW_ALL(EnumSet.of(AVX512F, AVX512BW), EnumSet.of(AVX512F, AVX512BW), EnumSet.of(AVX512F, AVX512BW)),
        AVX512F_BW_VL(EnumSet.of(AVX512F, AVX512BW, AVX512VL), EnumSet.of(AVX512F, AVX512BW, AVX512VL), EnumSet.of(AVX512F, AVX512BW)),
        AVX512F_VL_256_512(null, EnumSet.of(AVX512F, AVX512VL), EnumSet.of(AVX512F)),
        AVX512F_DQ_VL_256_512(null, EnumSet.of(AVX512F, AVX512DQ, AVX512VL), EnumSet.of(AVX512F, AVX512DQ)),
        AVX512F_DQ_512ONLY(null, null, EnumSet.of(AVX512F, AVX512DQ)),
        AVX512F_512ONLY(null, null, EnumSet.of(AVX512F));

        private final EnumSet<CPUFeature> l128features;
        private final EnumSet<CPUFeature> l256features;
        private final EnumSet<CPUFeature> l512features;

        EVEXFeatureAssertion(EnumSet<CPUFeature> l128features, EnumSet<CPUFeature> l256features, EnumSet<CPUFeature> l512features) {
            this.l128features = l128features;
            this.l256features = l256features;
            this.l512features = l512features;
        }

        public boolean check(EnumSet<CPUFeature> features, int l) {
            switch (l) {
                case VEXPrefixConfig.L128:
                    GraalError.guarantee(l128features != null && features.containsAll(l128features), "emitting illegal 128 bit instruction, required features: %s", l128features);
                    break;
                case VEXPrefixConfig.L256:
                    GraalError.guarantee(l256features != null && features.containsAll(l256features), "emitting illegal 256 bit instruction, required features: %s", l256features);
                    break;
                case VEXPrefixConfig.L512:
                    GraalError.guarantee(l512features != null && features.containsAll(l512features), "emitting illegal 512 bit instruction, required features: %s", l512features);
                    break;
            }
            return true;
        }

        public boolean supports(EnumSet<CPUFeature> features, AVXKind.AVXSize avxSize) {
            switch (avxSize) {
                case XMM:
                    return l128features != null && features.containsAll(l128features);
                case YMM:
                    return l256features != null && features.containsAll(l256features);
                case ZMM:
                    return l512features != null && features.containsAll(l512features);
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(avxSize); // ExcludeFromJacocoGeneratedReport
            }
        }
    }

    /**
     * A {@link RegisterCategory} that is the union of other categories, used to express constraints
     * like "a {@link AMD64#CPU} or {@link AMD64#MASK} register".
     */
    private static class UnionRegisterCategory extends RegisterCategory {
        private final RegisterCategory[] categories;

        UnionRegisterCategory(String name, RegisterCategory... categories) {
            super(name);
            this.categories = categories;
        }

        public boolean contains(Register r) {
            for (RegisterCategory category : categories) {
                if (r.getRegisterCategory().equals(category)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final UnionRegisterCategory CPU_OR_MASK = new UnionRegisterCategory("CPU_OR_MASK", CPU, MASK);

    private static boolean categoryContains(RegisterCategory category, Register register) {
        return register.getRegisterCategory().equals(category) || (category instanceof UnionRegisterCategory && ((UnionRegisterCategory) category).contains(register));
    }

    private enum VEXOpAssertion {
        AVX1(CPUFeature.AVX, CPUFeature.AVX, null),
        AVX1_2(CPUFeature.AVX, CPUFeature.AVX2, null),
        AVX2(CPUFeature.AVX2, CPUFeature.AVX2, null),
        AVX1_128ONLY(CPUFeature.AVX, null, null),
        AVX1_256ONLY(null, CPUFeature.AVX, null),
        AVX2_256ONLY(null, CPUFeature.AVX2, null),
        XMM_CPU(CPUFeature.AVX, null, null, null, XMM, null, CPU, null),
        XMM_XMM_CPU(CPUFeature.AVX, null, null, null, XMM, XMM, CPU, null),
        CPU_XMM(CPUFeature.AVX, null, null, null, CPU, null, XMM, null),
        AVX1_2_CPU_XMM(CPUFeature.AVX, CPUFeature.AVX2, null, null, CPU, null, XMM, null),
        BMI1(CPUFeature.BMI1, null, null, null, CPU, CPU, CPU, null),
        BMI2(CPUFeature.BMI2, null, null, null, CPU, CPU, CPU, null),
        FMA(CPUFeature.FMA, null, null, null, XMM, XMM, XMM, null),
        FMA_AVX512F_128ONLY(CPUFeature.FMA, null, null, EVEXFeatureAssertion.AVX512F_128ONLY, XMM, XMM, XMM, null),

        XMM_CPU_AVX512F_128ONLY(CPUFeature.AVX, null, null, EVEXFeatureAssertion.AVX512F_128ONLY, XMM, null, CPU, null),
        CPU_XMM_AVX512F_128ONLY(CPUFeature.AVX, null, null, EVEXFeatureAssertion.AVX512F_128ONLY, CPU, null, XMM, null),
        XMM_XMM_XMM_AVX512F_128ONLY(CPUFeature.AVX, null, null, EVEXFeatureAssertion.AVX512F_128ONLY, XMM, XMM, XMM, null),
        XMM_XMM_CPU_AVX512F_128ONLY(CPUFeature.AVX, null, null, EVEXFeatureAssertion.AVX512F_128ONLY, XMM, XMM, CPU, null),
        AVX1_AVX512F_128_ONLY(CPUFeature.AVX, CPUFeature.AVX, EVEXFeatureAssertion.AVX512F_128ONLY),
        AVX1_AVX512F_ALL(CPUFeature.AVX, CPUFeature.AVX, EVEXFeatureAssertion.AVX512F_ALL),
        AVX1_AVX512F_VL(CPUFeature.AVX, CPUFeature.AVX, EVEXFeatureAssertion.AVX512F_VL),
        AVX1_256ONLY_AVX512F_VL(null, CPUFeature.AVX, EVEXFeatureAssertion.AVX512F_VL),
        AVX1_128ONLY_AVX512F_128ONLY(CPUFeature.AVX, null, EVEXFeatureAssertion.AVX512F_128ONLY),
        AVX1_AVX2_AVX512F_BW(CPUFeature.AVX, CPUFeature.AVX2, EVEXFeatureAssertion.AVX512F_BW_ALL),
        AVX1_AVX2_AVX512BW_VL(CPUFeature.AVX, CPUFeature.AVX2, EVEXFeatureAssertion.AVX512F_BW_VL),
        AVX1_AVX2_AVX512F_VL(CPUFeature.AVX, CPUFeature.AVX2, EVEXFeatureAssertion.AVX512F_VL),
        AVX2_AVX512BW_VL(CPUFeature.AVX2, CPUFeature.AVX2, EVEXFeatureAssertion.AVX512F_BW_VL),
        AVX2_AVX512F_VL(CPUFeature.AVX2, CPUFeature.AVX2, EVEXFeatureAssertion.AVX512F_VL),
        AVX512BW_VL(CPUFeature.AVX512VL, CPUFeature.AVX512VL, EVEXFeatureAssertion.AVX512F_BW_VL),
        AVX512F_VL(CPUFeature.AVX512VL, CPUFeature.AVX512VL, EVEXFeatureAssertion.AVX512F_VL),
        AVX512F_VL_256_512(null, CPUFeature.AVX512VL, EVEXFeatureAssertion.AVX512F_VL_256_512),
        AVX512DQ_VL(CPUFeature.AVX512VL, CPUFeature.AVX512VL, EVEXFeatureAssertion.AVX512F_DQ_VL),
        AVX512DQ_VL_256_512(null, CPUFeature.AVX512VL, EVEXFeatureAssertion.AVX512F_DQ_VL_256_512),
        AVX512DQ_512ONLY(null, null, EVEXFeatureAssertion.AVX512F_DQ_512ONLY),
        AVX512F_512ONLY(null, null, EVEXFeatureAssertion.AVX512F_512ONLY),
        AVX_AVX512F_VL_256_512(null, CPUFeature.AVX, EVEXFeatureAssertion.AVX512F_VL),
        AVX2_AVX512F_VL_256_512(null, CPUFeature.AVX2, EVEXFeatureAssertion.AVX512F_VL),
        AVX1_AVX512DQ_VL(CPUFeature.AVX, CPUFeature.AVX, EVEXFeatureAssertion.AVX512F_DQ_VL),

        AVX512F_CPU_OR_MASK(CPUFeature.AVX512F, null, null, EVEXFeatureAssertion.AVX512F_ALL, CPU_OR_MASK, null, CPU_OR_MASK, null),
        AVX512DQ_CPU_OR_MASK(CPUFeature.AVX512DQ, null, null, EVEXFeatureAssertion.AVX512F_DQ_ALL, CPU_OR_MASK, null, CPU_OR_MASK, null),
        AVX512BW_CPU_OR_MASK(CPUFeature.AVX512BW, null, null, EVEXFeatureAssertion.AVX512F_BW_ALL, CPU_OR_MASK, null, CPU_OR_MASK, null),
        AVX512F_MASK(CPUFeature.AVX512F, null, null, null, MASK, MASK, MASK, null),
        AVX512DQ_MASK(CPUFeature.AVX512DQ, null, null, null, MASK, MASK, MASK, null),
        AVX512BW_MASK(CPUFeature.AVX512BW, null, null, null, MASK, MASK, MASK, null),
        MASK_XMM_XMM_AVX512BW_VL(CPUFeature.AVX512VL, CPUFeature.AVX512VL, null, EVEXFeatureAssertion.AVX512F_BW_VL, MASK, XMM, XMM, null),
        MASK_NULL_XMM_AVX512BW_VL(CPUFeature.AVX512VL, CPUFeature.AVX512VL, null, EVEXFeatureAssertion.AVX512F_BW_VL, MASK, null, XMM, null),
        MASK_NULL_XMM_AVX512DQ_VL(CPUFeature.AVX512VL, CPUFeature.AVX512VL, null, EVEXFeatureAssertion.AVX512F_DQ_VL, MASK, null, XMM, null),
        MASK_NULL_XMM_AVX512DQ(CPUFeature.AVX512DQ, CPUFeature.AVX512DQ, null, EVEXFeatureAssertion.AVX512F_DQ_ALL, MASK, null, XMM, null),
        MASK_XMM_XMM_AVX512F_VL(CPUFeature.AVX512VL, CPUFeature.AVX512VL, null, EVEXFeatureAssertion.AVX512F_VL, MASK, XMM, XMM, null),
        AVX1_128ONLY_CLMUL(CPUFeature.AVX, null, CPUFeature.CLMUL, null, XMM, XMM, XMM, XMM),
        AVX1_128ONLY_AES(CPUFeature.AVX, null, CPUFeature.AES, null, XMM, XMM, XMM, XMM),
        AVX1_GFNI_AVX512F_VL(GFNI, GFNI, CPUFeature.AVX, EVEXFeatureAssertion.AVX512F_VL, XMM, XMM, XMM, null),
        F16C_AVX512F_VL(F16C, F16C, null, EVEXFeatureAssertion.AVX512F_VL, XMM, XMM, XMM, null);

        private final CPUFeature l128feature;
        private final CPUFeature l256feature;
        private final CPUFeature extendedFeature;
        private final EVEXFeatureAssertion l512features;

        private final RegisterCategory rCategory;
        private final RegisterCategory vCategory;
        private final RegisterCategory mCategory;
        private final RegisterCategory imm8Category;

        VEXOpAssertion(CPUFeature l128feature, CPUFeature l256feature, EVEXFeatureAssertion l512features) {
            this(l128feature, l256feature, null, l512features, XMM, XMM, XMM, XMM);
        }

        VEXOpAssertion(CPUFeature l128feature, CPUFeature l256feature, CPUFeature extendedFeature, EVEXFeatureAssertion l512features, RegisterCategory rCategory, RegisterCategory vCategory,
                        RegisterCategory mCategory, RegisterCategory imm8Category) {
            this.l128feature = l128feature;
            this.l256feature = l256feature;
            this.extendedFeature = extendedFeature;
            this.l512features = l512features;
            this.rCategory = rCategory;
            this.vCategory = vCategory;
            this.mCategory = mCategory;
            this.imm8Category = imm8Category;
        }

        public boolean check(EnumSet<CPUFeature> features, AVXKind.AVXSize size, Register r, Register v, Register m) {
            return check(features, getLFlag(size), r, v, m, null);
        }

        public boolean check(EnumSet<CPUFeature> features, AVXKind.AVXSize size, Register r, Register v, Register m, Register imm8) {
            return check(features, getLFlag(size), r, v, m, imm8);
        }

        public boolean check(EnumSet<CPUFeature> features, int l, Register r, Register v, Register m, Register imm8) {
            if (isAVX512Register(r) || isAVX512Register(v) || isAVX512Register(m) || l == VEXPrefixConfig.L512) {
                GraalError.guarantee(l512features != null && l512features.check(features, l), "emitting illegal 512 bit instruction, required features: %s", l512features);
            } else if (l == VEXPrefixConfig.L128) {
                GraalError.guarantee(l128feature != null && features.contains(l128feature), "emitting illegal 128 bit instruction, required feature: %s", l128feature);
            } else if (l == VEXPrefixConfig.L256) {
                GraalError.guarantee(l256feature != null && features.contains(l256feature), "emitting illegal 256 bit instruction, required feature: %s", l256feature);
            }
            if (r != null) {
                GraalError.guarantee(categoryContains(rCategory, r), "expected r in category %s, got %s", rCategory, r);
            }
            if (v != null) {
                GraalError.guarantee(categoryContains(vCategory, v), "expected v in category %s, got %s", vCategory, v);
            }
            if (m != null) {
                GraalError.guarantee(categoryContains(mCategory, m), "expected m in category %s, got %s", mCategory, m);
            }
            if (imm8 != null) {
                GraalError.guarantee(imm8.getRegisterCategory().equals(imm8Category), "expected imm8 in category %s, got %s", imm8Category, imm8);
            }
            if (extendedFeature != null) {
                GraalError.guarantee(features.contains(extendedFeature), "emitting illegal instruction, required extended feature: %s", extendedFeature);
            }
            return true;
        }

        public boolean supports(EnumSet<CPUFeature> features, AVXKind.AVXSize avxSize, boolean useZMMRegisters) {
            boolean extendedFeatureCheck = extendedFeature != null ? features.contains(extendedFeature) : true;
            if (useZMMRegisters || avxSize == AVXKind.AVXSize.ZMM) {
                return l512features != null && l512features.supports(features, avxSize) && extendedFeatureCheck;
            } else if (avxSize == AVXKind.AVXSize.XMM) {
                return l128feature != null && features.contains(l128feature) && extendedFeatureCheck;
            } else if (avxSize == AVXKind.AVXSize.YMM) {
                return l256feature != null && features.contains(l256feature) && extendedFeatureCheck;
            }
            throw GraalError.shouldNotReachHereUnexpectedValue(avxSize); // ExcludeFromJacocoGeneratedReport
        }
    }

    /**
     * Base class for VEX-encoded instructions.
     */
    public static class VexOp {
        protected final int pp;
        protected final int mmmmm;
        protected final int w;
        protected final int op;

        private final String opcode;
        protected final VEXOpAssertion assertion;

        protected final EVEXTuple evexTuple;
        protected final int wEvex;

        protected VexOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            this.pp = pp;
            this.mmmmm = mmmmm;
            this.w = w;
            this.op = op;
            this.opcode = opcode;
            this.assertion = assertion;
            this.evexTuple = evexTuple;
            this.wEvex = wEvex;
        }

        protected VexOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            this(opcode, pp, mmmmm, w, op, assertion, EVEXTuple.INVALID, VEXPrefixConfig.WIG);
        }

        public final boolean isSupported(AMD64Assembler vasm, AVXKind.AVXSize size) {
            return isSupported(vasm, size, false);
        }

        public final boolean isSupported(AMD64Assembler vasm, AVXKind.AVXSize size, boolean useZMMRegisters) {
            return assertion.supports(vasm.getFeatures(), size, useZMMRegisters);
        }

        public final boolean isSupported(AMD64 arch, AMD64Kind kind) {
            return assertion.supports(arch.getFeatures(), AVXKind.getRegisterSize(kind), false);
        }

        @Override
        public String toString() {
            return opcode;
        }

        protected final int getDisp8Scale(boolean useEvex, AVXKind.AVXSize size) {
            return useEvex ? evexTuple.getDisp8ScalingFactor(size) : DEFAULT_DISP8_SCALE;
        }

    }

    /**
     * VEX-encoded instructions with an operand order of RM, but the M operand must be a register.
     */
    public static class VexRROp extends VexOp {
        // @formatter:off
        public static final VexRROp VMASKMOVDQU = new VexRROp("VMASKMOVDQU", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xF7, VEXOpAssertion.AVX1_128ONLY,  EVEXTuple.INVALID, VEXPrefixConfig.WIG);

        public static final VexRROp KTESTB      = new VexRROp("KTESTB",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0,  0x99, VEXOpAssertion.AVX512DQ_MASK, EVEXTuple.INVALID, VEXPrefixConfig.W0);
        public static final VexRROp KTESTW      = new VexRROp("KTESTW",      VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W0,  0x99, VEXOpAssertion.AVX512DQ_MASK, EVEXTuple.INVALID, VEXPrefixConfig.W0);
        public static final VexRROp KTESTD      = new VexRROp("KTESTD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1,  0x99, VEXOpAssertion.AVX512BW_MASK, EVEXTuple.INVALID, VEXPrefixConfig.W1);
        public static final VexRROp KTESTQ      = new VexRROp("KTESTQ",      VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W1,  0x99, VEXOpAssertion.AVX512BW_MASK, EVEXTuple.INVALID, VEXPrefixConfig.W1);

        public static final VexRROp KORTESTB    = new VexRROp("KORTESTB",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0,  0x98, VEXOpAssertion.AVX512DQ_MASK, EVEXTuple.INVALID, VEXPrefixConfig.W0);
        public static final VexRROp KORTESTW    = new VexRROp("KORTESTW",    VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W0,  0x98, VEXOpAssertion.AVX512F_MASK,  EVEXTuple.INVALID, VEXPrefixConfig.W0);
        public static final VexRROp KORTESTD    = new VexRROp("KORTESTD",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1,  0x98, VEXOpAssertion.AVX512BW_MASK, EVEXTuple.INVALID, VEXPrefixConfig.W1);
        public static final VexRROp KORTESTQ    = new VexRROp("KORTESTQ",    VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W1,  0x98, VEXOpAssertion.AVX512BW_MASK, EVEXTuple.INVALID, VEXPrefixConfig.W1);

        public static final VexRROp KNOTB       = new VexRROp("KNOTB",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0,  0x44, VEXOpAssertion.AVX512DQ_MASK, EVEXTuple.INVALID, VEXPrefixConfig.W0);
        public static final VexRROp KNOTW       = new VexRROp("KNOTW",       VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W0,  0x44, VEXOpAssertion.AVX512F_MASK,  EVEXTuple.INVALID, VEXPrefixConfig.W0);
        public static final VexRROp KNOTD       = new VexRROp("KNOTD",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1,  0x44, VEXOpAssertion.AVX512BW_MASK, EVEXTuple.INVALID, VEXPrefixConfig.W1);
        public static final VexRROp KNOTQ       = new VexRROp("KNOTQ",       VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W1,  0x44, VEXOpAssertion.AVX512BW_MASK, EVEXTuple.INVALID, VEXPrefixConfig.W1);
        // @formatter:on

        protected VexRROp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, null, src), "emitting invalid instruction");
            assert op != 0x1A || op != 0x5A : op;
            asm.vexPrefix(dst, Register.None, src, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitModRM(dst, src);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src, Register mask, int z, int b) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, null, src), "emitting invalid instruction");
            assert op != 0x1A || op != 0x5A : op;
            asm.vexPrefix(dst, Register.None, src, mask, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature, z, b);
            asm.emitByte(op);
            asm.emitModRM(dst, src);
        }
    }

    /**
     * VEX-encoded instructions with an operand order of RM.
     */
    public static class VexRMOp extends VexRROp {
        // @formatter:off
        public static final VexRMOp VAESIMC         = new VexRMOp("VAESIMC",         VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0xDB, VEXOpAssertion.AVX1_128ONLY_AES);
        public static final VexRMOp VCVTTSS2SI      = new VexRMOp("VCVTTSS2SI",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0x2C, VEXOpAssertion.CPU_XMM_AVX512F_128ONLY,   EVEXTuple.T1F_32BIT, VEXPrefixConfig.W0);
        public static final VexRMOp VCVTTSS2SQ      = new VexRMOp("VCVTTSS2SQ",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W1,  0x2C, VEXOpAssertion.CPU_XMM_AVX512F_128ONLY,   EVEXTuple.T1F_32BIT, VEXPrefixConfig.W1);
        public static final VexRMOp VCVTTSD2SI      = new VexRMOp("VCVTTSD2SI",      VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0x2C, VEXOpAssertion.CPU_XMM_AVX512F_128ONLY,   EVEXTuple.T1F_64BIT, VEXPrefixConfig.W0);
        public static final VexRMOp VCVTTSD2SQ      = new VexRMOp("VCVTTSD2SQ",      VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W1,  0x2C, VEXOpAssertion.CPU_XMM_AVX512F_128ONLY,   EVEXTuple.T1F_64BIT, VEXPrefixConfig.W1);
        public static final VexRMOp VCVTPS2PD       = new VexRMOp("VCVTPS2PD",       VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5A, VEXOpAssertion.AVX1_AVX512F_VL,           EVEXTuple.HVM,       VEXPrefixConfig.W0);
        public static final VexRMOp VCVTPD2PS       = new VexRMOp("VCVTPD2PS",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5A, VEXOpAssertion.AVX1_AVX512F_VL,           EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRMOp VCVTDQ2PS       = new VexRMOp("VCVTDQ2PS",       VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5B, VEXOpAssertion.AVX1_AVX512F_VL,           EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRMOp VCVTQQ2PS       = new VexRMOp("VCVTQQ2PS",       VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.W1,  0x5B, VEXOpAssertion.AVX512DQ_VL,               EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRMOp VCVTQQ2PD       = new VexRMOp("VCVTQQ2PD",       VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W1,  0xE6, VEXOpAssertion.AVX512DQ_VL,               EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRMOp VCVTTPS2DQ      = new VexRMOp("VCVTTPS2DQ",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5B, VEXOpAssertion.AVX1_AVX512F_VL,           EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRMOp VCVTTPS2QQ      = new VexRMOp("VCVTTPS2QQ",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0x7A, VEXOpAssertion.AVX512DQ_VL,               EVEXTuple.HVM,       VEXPrefixConfig.W0);
        public static final VexRMOp VCVTTPD2DQ      = new VexRMOp("VCVTTPD2DQ",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xE6, VEXOpAssertion.AVX1_AVX512F_VL,           EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRMOp VCVTTPD2QQ      = new VexRMOp("VCVTTPD2QQ",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W1,  0x7A, VEXOpAssertion.AVX512DQ_VL,               EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRMOp VCVTDQ2PD       = new VexRMOp("VCVTDQ2PD",       VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xE6, VEXOpAssertion.AVX1_AVX512F_VL,           EVEXTuple.HVM,       VEXPrefixConfig.W0);
        public static final VexRMOp VBROADCASTSS    = new VexRMOp("VBROADCASTSS",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x18, VEXOpAssertion.AVX1_AVX512F_VL,           EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRMOp VBROADCASTSD    = new VexRMOp("VBROADCASTSD",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x19, VEXOpAssertion.AVX1_256ONLY_AVX512F_VL,   EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRMOp VBROADCASTF128  = new VexRMOp("VBROADCASTF128",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x1A, VEXOpAssertion.AVX1_256ONLY);
        public static final VexRMOp VPBROADCASTI128 = new VexRMOp("VPBROADCASTI128", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x5A, VEXOpAssertion.AVX2_256ONLY);
        public static final VexRMOp VPBROADCASTB    = new VexRMOp("VPBROADCASTB",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x78, VEXOpAssertion.AVX2_AVX512BW_VL,          EVEXTuple.T1S_8BIT,  VEXPrefixConfig.W0);
        public static final VexRMOp VPBROADCASTW    = new VexRMOp("VPBROADCASTW",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x79, VEXOpAssertion.AVX2_AVX512BW_VL,          EVEXTuple.T1S_16BIT, VEXPrefixConfig.W0);
        public static final VexRMOp VPBROADCASTD    = new VexRMOp("VPBROADCASTD",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x58, VEXOpAssertion.AVX2_AVX512F_VL,           EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRMOp VPBROADCASTQ    = new VexRMOp("VPBROADCASTQ",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x59, VEXOpAssertion.AVX2_AVX512F_VL,           EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRMOp VPMOVMSKB       = new VexRMOp("VPMOVMSKB",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xD7, VEXOpAssertion.AVX1_2_CPU_XMM);
        public static final VexRMOp VPMOVB2M        = new VexRMOp("VPMOVB2M",        VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x29, VEXOpAssertion.MASK_NULL_XMM_AVX512BW_VL, EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRMOp VPMOVW2M        = new VexRMOp("VPMOVW2M",        VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x29, VEXOpAssertion.MASK_NULL_XMM_AVX512BW_VL, EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRMOp VPMOVD2M        = new VexRMOp("VPMOVD2M",        VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x39, VEXOpAssertion.MASK_NULL_XMM_AVX512DQ_VL, EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRMOp VPMOVQ2M        = new VexRMOp("VPMOVQ2M",        VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x39, VEXOpAssertion.MASK_NULL_XMM_AVX512DQ_VL, EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRMOp VPMOVSXBW       = new VexRMOp("VPMOVSXBW",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x20, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,     EVEXTuple.HVM,       VEXPrefixConfig.WIG);
        public static final VexRMOp VPMOVSXBD       = new VexRMOp("VPMOVSXBD",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x21, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,      EVEXTuple.QVM,       VEXPrefixConfig.WIG);
        public static final VexRMOp VPMOVSXBQ       = new VexRMOp("VPMOVSXBQ",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x22, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,      EVEXTuple.OVM,       VEXPrefixConfig.WIG);
        public static final VexRMOp VPMOVSXWD       = new VexRMOp("VPMOVSXWD",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x23, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,      EVEXTuple.HVM,       VEXPrefixConfig.WIG);
        public static final VexRMOp VPMOVSXWQ       = new VexRMOp("VPMOVSXWQ",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x24, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,      EVEXTuple.QVM,       VEXPrefixConfig.WIG);
        public static final VexRMOp VPMOVSXDQ       = new VexRMOp("VPMOVSXDQ",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x25, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,      EVEXTuple.HVM,       VEXPrefixConfig.WIG);
        public static final VexRMOp VPMOVZXBW       = new VexRMOp("VPMOVZXBW",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x30, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,     EVEXTuple.HVM,       VEXPrefixConfig.WIG);
        public static final VexRMOp VPMOVZXBD       = new VexRMOp("VPMOVZXBD",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x31, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,      EVEXTuple.QVM,       VEXPrefixConfig.WIG);
        public static final VexRMOp VPMOVZXBQ       = new VexRMOp("VPMOVZXBQ",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x32, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,      EVEXTuple.OVM,       VEXPrefixConfig.WIG);
        public static final VexRMOp VPMOVZXWD       = new VexRMOp("VPMOVZXWD",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x33, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,      EVEXTuple.HVM,       VEXPrefixConfig.WIG);
        public static final VexRMOp VPMOVZXWQ       = new VexRMOp("VPMOVZXWQ",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x34, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,      EVEXTuple.QVM,       VEXPrefixConfig.WIG);
        public static final VexRMOp VPMOVZXDQ       = new VexRMOp("VPMOVZXDQ",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x35, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,      EVEXTuple.HVM,       VEXPrefixConfig.WIG);
        public static final VexRMOp VPTEST          = new VexRMOp("VPTEST",          VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x17);
        public static final VexRMOp VSQRTPD         = new VexRMOp("VSQRTPD",         VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x51, VEXOpAssertion.AVX1_AVX512F_VL,           EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRMOp VSQRTPS         = new VexRMOp("VSQRTPS",         VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x51, VEXOpAssertion.AVX1_AVX512F_VL,           EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRMOp VUCOMISS        = new VexRMOp("VUCOMISS",        VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x2E, VEXOpAssertion.AVX1_AVX512F_128_ONLY,     EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRMOp VUCOMISD        = new VexRMOp("VUCOMISD",        VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x2E, VEXOpAssertion.AVX1_AVX512F_128_ONLY,     EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRMOp VPABSB          = new VexRMOp("VPABSB",          VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x1C, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,     EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRMOp VPABSW          = new VexRMOp("VPABSW",          VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x1D, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,     EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRMOp VPABSD          = new VexRMOp("VPABSD",          VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x1E, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,      EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRMOp VPABSQ          = new VexRMOp("VPABSQ",          VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x1F, VEXOpAssertion.AVX512F_VL,                EVEXTuple.FVM,       VEXPrefixConfig.W1);

        public static final VexRMOp VCVTPH2PS       = new VexRMOp("VCVTPH2PS",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x13, VEXOpAssertion.F16C_AVX512F_VL,           EVEXTuple.HVM,       VEXPrefixConfig.W0);
        // @formatter:on

        protected VexRMOp(String opcode, int pp, int mmmmm, int w, int op) {
            this(opcode, pp, mmmmm, w, op, VEXOpAssertion.AVX1, EVEXTuple.INVALID, VEXPrefixConfig.WIG);
        }

        protected VexRMOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            this(opcode, pp, mmmmm, w, op, assertion, EVEXTuple.INVALID, VEXPrefixConfig.WIG);
        }

        protected VexRMOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, AMD64Address src) {
            emit(asm, size, dst, src, Register.None, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src, Register mask, int z, int b) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, null, src), "emitting invalid instruction");
            asm.vexPrefix(dst, Register.None, src, mask, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature, z, b);
            asm.emitByte(op);
            asm.emitModRM(dst, src);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, AMD64Address src, Register mask, int z, int b) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, null, null), "emitting invalid instruction");
            boolean useEvex = asm.vexPrefix(dst, Register.None, src, mask, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature, z, b);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src, 0, getDisp8Scale(useEvex, size));
        }
    }

    /**
     * A general class of VEX-encoded move instructions. These support both RM and MR operand
     * orders.
     */
    public abstract static class VexGeneralMoveOp extends VexRMOp {
        protected VexGeneralMoveOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
        }

        public abstract void emit(AMD64Assembler asm, AVXKind.AVXSize size, AMD64Address dst, Register src);
    }

    /**
     * VEX-encoded move instructions.
     * <p>
     * These instructions have two opcodes: op is the forward move instruction with an operand order
     * of RM, and opReverse is the reverse move instruction with an operand order of MR.
     */
    public static final class VexMoveOp extends VexGeneralMoveOp {
        // @formatter:off
        public static final VexMoveOp VMOVDQA32 = new VexMoveOp("VMOVDQA32", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0x6F, 0x7F, VEXOpAssertion.AVX1_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexMoveOp VMOVDQA64 = new VexMoveOp("VMOVDQA64", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0x6F, 0x7F, VEXOpAssertion.AVX1_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexMoveOp VMOVDQU32 = new VexMoveOp("VMOVDQU32", VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0x6F, 0x7F, VEXOpAssertion.AVX1_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexMoveOp VMOVDQU64 = new VexMoveOp("VMOVDQU64", VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0x6F, 0x7F, VEXOpAssertion.AVX1_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexMoveOp VMOVAPS   = new VexMoveOp("VMOVAPS",   VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0x28, 0x29, VEXOpAssertion.AVX1_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexMoveOp VMOVAPD   = new VexMoveOp("VMOVAPD",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0x28, 0x29, VEXOpAssertion.AVX1_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexMoveOp VMOVUPS   = new VexMoveOp("VMOVUPS",   VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0x10, 0x11, VEXOpAssertion.AVX1_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexMoveOp VMOVUPD   = new VexMoveOp("VMOVUPD",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0x10, 0x11, VEXOpAssertion.AVX1_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W1);
        // VMOVSS and VMOVSD are RVM instructions when both src and dest are registers.
        public static final VexMoveOp VMOVSS    = new VexMoveOp("VMOVSS",    VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0x10, 0x11, VEXOpAssertion.AVX1_AVX512F_ALL,        EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexMoveOp VMOVSD    = new VexMoveOp("VMOVSD",    VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0x10, 0x11, VEXOpAssertion.AVX1_AVX512F_ALL,        EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexMoveOp VMOVD     = new VexMoveOp("VMOVD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0,  0x6E, 0x7E, VEXOpAssertion.XMM_CPU_AVX512F_128ONLY, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexMoveOp VMOVQ     = new VexMoveOp("VMOVQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1,  0x6E, 0x7E, VEXOpAssertion.XMM_CPU_AVX512F_128ONLY, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        // @formatter:on

        private final int opReverse;

        private VexMoveOp(String opcode, int pp, int mmmmm, int w, int op, int opReverse, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
            this.opReverse = opReverse;
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, AMD64Address dst, Register src) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, src, null, null), "emitting invalid instruction");
            boolean useEvex = asm.vexPrefix(src, Register.None, dst, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(opReverse);
            asm.emitOperandHelper(src, dst, 0, getDisp8Scale(useEvex, size));
        }

        public void emitReverse(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, src, null, dst), "emitting invalid instruction");
            asm.vexPrefix(src, Register.None, dst, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(opReverse);
            asm.emitModRM(src, dst);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, null, src), "emitting invalid instruction");
            // MOVSS/SD are RVM instruction when both operands are registers
            Register nds = (this == VMOVSS || this == VMOVSD) ? src : Register.None;
            asm.vexPrefix(dst, nds, src, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitModRM(dst, src);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src, Register mask, int z, int b) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, null, src), "emitting invalid instruction");
            // MOVSS/SD are RVM instruction when both operands are registers
            Register nds = (this == VMOVSS || this == VMOVSD) ? src : Register.None;
            asm.vexPrefix(dst, nds, src, mask, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature, z, b);
            asm.emitByte(op);
            asm.emitModRM(dst, src);
        }

    }

    public interface VexRRIOp {
        void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src, int imm8);
    }

    /**
     * VEX-encoded instructions with an operand order of RMI.
     */
    public static class VexRMIOp extends VexOp implements VexRRIOp {
        // @formatter:off
        public static final VexRMIOp VAESKEYGENASSIST = new VexRMIOp("VAESKEYGENASSIST", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.WIG, 0xDF, VEXOpAssertion.AVX1_128ONLY_AES);
        public static final VexRMIOp VPERMQ           = new VexRMIOp("VPERMQ",           VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x00, VEXOpAssertion.AVX2_AVX512F_VL_256_512, EVEXTuple.FVM, VEXPrefixConfig.W1);
        public static final VexRMIOp VPSHUFLW         = new VexRMIOp("VPSHUFLW",         VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x70, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,   EVEXTuple.FVM, VEXPrefixConfig.WIG);
        public static final VexRMIOp VPSHUFHW         = new VexRMIOp("VPSHUFHW",         VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x70, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,   EVEXTuple.FVM, VEXPrefixConfig.WIG);
        public static final VexRMIOp VPSHUFD          = new VexRMIOp("VPSHUFD",          VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x70, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,    EVEXTuple.FVM, VEXPrefixConfig.W0);
        public static final VexRMIOp RORXL            = new VexRMIOp("RORXL",            VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0xF0, VEXOpAssertion.BMI2);
        public static final VexRMIOp RORXQ            = new VexRMIOp("RORXQ",            VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0xF0, VEXOpAssertion.BMI2);
        // @formatter:on

        protected VexRMIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion, EVEXTuple.INVALID, VEXPrefixConfig.WIG);
        }

        protected VexRMIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src, int imm8) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, null, src), "emitting invalid instruction");
            asm.vexPrefix(dst, Register.None, src, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitModRM(dst, src);
            asm.emitByte(imm8);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, AMD64Address src, int imm8) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, null, null), "emitting invalid instruction");
            boolean useEvex = asm.vexPrefix(dst, Register.None, src, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src, 1, getDisp8Scale(useEvex, size));
            asm.emitByte(imm8);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src, int imm8, Register mask, int z, int b) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, null, src), "emitting invalid instruction");
            asm.evexPrefix(dst, mask, Register.None, src, size, pp, mmmmm, wEvex, z, b);
            asm.emitByte(op);
            asm.emitModRM(dst, src);
            asm.emitByte(imm8);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, AMD64Address src, int imm8, Register mask, int z, int b) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, null, null), "emitting invalid instruction");
            asm.evexPrefix(dst, mask, Register.None, src, size, pp, mmmmm, wEvex, z, b);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src, 1, getDisp8Scale(true, size));
            asm.emitByte(imm8);
        }
    }

    /**
     * EVEX-encoded instructions with an operand order of RMI.
     */
    public static final class EvexRMIOp extends VexRMIOp {
        // @formatter:off
        public static final EvexRMIOp VFPCLASSSS = new EvexRMIOp("VFPCLASS", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x67, VEXOpAssertion.MASK_NULL_XMM_AVX512DQ, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final EvexRMIOp VFPCLASSSD = new EvexRMIOp("VFPCLASD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0x67, VEXOpAssertion.MASK_NULL_XMM_AVX512DQ, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        // @formatter:on

        private EvexRMIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src, int imm8) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, null, src), "emitting invalid instruction");
            emit(asm, size, dst, src, imm8, Register.None, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, AMD64Address src, int imm8) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, null, null), "emitting invalid instruction");
            emit(asm, size, dst, src, imm8, Register.None, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        }
    }

    /**
     * VEX-encoded instructions with an operand order of MR.
     */
    public static final class VexMROp extends VexRROp {
        // @formatter:off
        public static final VexMROp VPCOMPRESSD = new VexMROp("VPCOMPRESSD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, 0x8B, VEXOpAssertion.AVX512F_VL, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        // @formatter:on

        private VexMROp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src, Register mask, int z, int b) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, src, null, dst), "emitting invalid instruction");
            asm.vexPrefix(src, Register.None, dst, mask, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature, z, b);
            asm.emitByte(op);
            asm.emitModRM(src, dst);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, AMD64Address dst, Register src, Register mask, int z, int b) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, src, null, null), "emitting invalid instruction");
            boolean useEvex = asm.vexPrefix(src, Register.None, dst, mask, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature, z, b);
            asm.emitByte(op);
            asm.emitOperandHelper(src, dst, 1, getDisp8Scale(useEvex, size));
        }
    }

    /**
     * VEX-encoded instructions with an operand order of MRI.
     */
    public static final class VexMRIOp extends VexOp implements VexRRIOp {
        // @formatter:off
        public static final VexMRIOp VPEXTRB       = new VexMRIOp("VPEXTRB",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x14, VEXOpAssertion.XMM_CPU);
        public static final VexMRIOp VPEXTRW       = new VexMRIOp("VPEXTRW",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x15, VEXOpAssertion.XMM_CPU);
        public static final VexMRIOp VPEXTRD       = new VexMRIOp("VPEXTRD",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x16, VEXOpAssertion.XMM_CPU);
        public static final VexMRIOp VPEXTRQ       = new VexMRIOp("VPEXTRQ",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0x16, VEXOpAssertion.XMM_CPU);

        // AVX/AVX2 128-bit extract
        public static final VexMRIOp VEXTRACTF128  = new VexMRIOp("VEXTRACTF128",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x19, VEXOpAssertion.AVX1_256ONLY);
        public static final VexMRIOp VEXTRACTI128  = new VexMRIOp("VEXTRACTI128",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x39, VEXOpAssertion.AVX2_256ONLY);

        // AVX-512 extract
        public static final VexMRIOp VEXTRACTF32X4 = new VexMRIOp("VEXTRACTF32X4", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x19, VEXOpAssertion.AVX512F_VL_256_512,  EVEXTuple.T4_32BIT, VEXPrefixConfig.W0);
        public static final VexMRIOp VEXTRACTI32X4 = new VexMRIOp("VEXTRACTI32X4", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x39, VEXOpAssertion.AVX512F_VL_256_512,  EVEXTuple.T4_32BIT, VEXPrefixConfig.W0);
        public static final VexMRIOp VEXTRACTF64X2 = new VexMRIOp("VEXTRACTF64X2", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0x19, VEXOpAssertion.AVX512DQ_VL_256_512, EVEXTuple.T2_64BIT, VEXPrefixConfig.W1);
        public static final VexMRIOp VEXTRACTI64X2 = new VexMRIOp("VEXTRACTI64X2", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0x39, VEXOpAssertion.AVX512DQ_VL_256_512, EVEXTuple.T2_64BIT, VEXPrefixConfig.W1);

        public static final VexMRIOp VEXTRACTF32X8 = new VexMRIOp("VEXTRACTF32X8", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x1B, VEXOpAssertion.AVX512DQ_512ONLY,    EVEXTuple.T8_32BIT, VEXPrefixConfig.W0);
        public static final VexMRIOp VEXTRACTI32X8 = new VexMRIOp("VEXTRACTI32X8", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x3B, VEXOpAssertion.AVX512DQ_512ONLY,    EVEXTuple.T8_32BIT, VEXPrefixConfig.W0);
        public static final VexMRIOp VEXTRACTF64X4 = new VexMRIOp("VEXTRACTF64X2", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0x1B, VEXOpAssertion.AVX512F_512ONLY,     EVEXTuple.T4_64BIT, VEXPrefixConfig.W1);
        public static final VexMRIOp VEXTRACTI64X4 = new VexMRIOp("VEXTRACTI64X2", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0x3B, VEXOpAssertion.AVX512F_512ONLY,     EVEXTuple.T4_64BIT, VEXPrefixConfig.W1);

        // Half precision floating-point values conversion
        public static final VexMRIOp VCVTPS2PH     = new VexMRIOp("VCVTPS2PH",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x1D, VEXOpAssertion.F16C_AVX512F_VL,     EVEXTuple.HVM,      VEXPrefixConfig.W0);
        // @formatter:on

        private VexMRIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        private VexMRIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src, int imm8) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, src, null, dst), "emitting invalid instruction");
            asm.vexPrefix(src, Register.None, dst, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitModRM(src, dst);
            asm.emitByte(imm8);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, AMD64Address dst, Register src, int imm8) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, src, null, null), "emitting invalid instruction");
            boolean useEvex = asm.vexPrefix(src, Register.None, dst, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitOperandHelper(src, dst, 1, getDisp8Scale(useEvex, size));
            asm.emitByte(imm8);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src, int imm8, Register mask, int z, int b) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, src, null, dst), "emitting invalid instruction");
            asm.vexPrefix(src, Register.None, dst, mask, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature, z, b);
            asm.emitByte(op);
            asm.emitModRM(src, dst);
            asm.emitByte(imm8);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, AMD64Address dst, Register src, int imm8, Register mask, int z, int b) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, src, null, null), "emitting invalid instruction");
            boolean useEvex = asm.vexPrefix(src, Register.None, dst, mask, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature, z, b);
            asm.emitByte(op);
            asm.emitOperandHelper(src, dst, 1, getDisp8Scale(useEvex, size));
            asm.emitByte(imm8);
        }
    }

    /**
     * VEX-encoded instructions with an operand order of RVMR.
     */
    public static class VexRVMROp extends VexOp {
        // @formatter:off
        public static final VexRVMROp VPBLENDVB = new VexRVMROp("VPBLENDVB", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x4C, VEXOpAssertion.AVX1_2);
        public static final VexRVMROp VBLENDVPS = new VexRVMROp("VBLENDVPS", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x4A, VEXOpAssertion.AVX1);
        public static final VexRVMROp VBLENDVPD = new VexRVMROp("VBLENDVPD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x4B, VEXOpAssertion.AVX1);
        // @formatter:on

        protected VexRVMROp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register mask, Register src1, Register src2) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, mask, src1, src2), "emitting invalid instruction");
            asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
            asm.emitByte(mask.encoding() << 4);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register mask, Register src1, AMD64Address src2) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, mask, src1, null), "emitting invalid instruction");
            boolean useEvex = asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src2, 0, getDisp8Scale(useEvex, size));
            asm.emitByte(mask.encoding() << 4);
        }
    }

    public static class VexRVROp extends VexOp {
        // @formatter:off
        public static final VexRVROp KANDW  = new VexRVROp("KANDW",  VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x41, VEXOpAssertion.AVX512F_MASK);
        public static final VexRVROp KANDB  = new VexRVROp("KANDB",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x41, VEXOpAssertion.AVX512DQ_MASK);
        public static final VexRVROp KANDQ  = new VexRVROp("KANDQ",  VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0x41, VEXOpAssertion.AVX512BW_MASK);
        public static final VexRVROp KANDD  = new VexRVROp("KANDD",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0x41, VEXOpAssertion.AVX512BW_MASK);

        public static final VexRVROp KANDNW = new VexRVROp("KANDNW", VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x42, VEXOpAssertion.AVX512F_MASK);
        public static final VexRVROp KANDNB = new VexRVROp("KANDNB", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x42, VEXOpAssertion.AVX512DQ_MASK);
        public static final VexRVROp KANDNQ = new VexRVROp("KANDNQ", VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0x42, VEXOpAssertion.AVX512BW_MASK);
        public static final VexRVROp KANDND = new VexRVROp("KANDND", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0x42, VEXOpAssertion.AVX512BW_MASK);

        public static final VexRVROp KORW   = new VexRVROp("KORW",   VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x45, VEXOpAssertion.AVX512F_MASK);
        public static final VexRVROp KORB   = new VexRVROp("KORB",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x45, VEXOpAssertion.AVX512DQ_MASK);
        public static final VexRVROp KORQ   = new VexRVROp("KORQ",   VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0x45, VEXOpAssertion.AVX512BW_MASK);
        public static final VexRVROp KORD   = new VexRVROp("KORD",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0x45, VEXOpAssertion.AVX512BW_MASK);

        public static final VexRVROp KXORW  = new VexRVROp("KXORW",  VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x47, VEXOpAssertion.AVX512F_MASK);
        public static final VexRVROp KXORB  = new VexRVROp("KXORB",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x47, VEXOpAssertion.AVX512DQ_MASK);
        public static final VexRVROp KXORQ  = new VexRVROp("KXORQ",  VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0x47, VEXOpAssertion.AVX512BW_MASK);
        public static final VexRVROp KXORD  = new VexRVROp("KXORD",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0x47, VEXOpAssertion.AVX512BW_MASK);
        // @formatter:on

        protected VexRVROp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src1, Register src2) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, src1, src2), "emitting invalid instruction");
            // format VEX.L1 ... kdest, ksrc1, ksrc2
            asm.emitVEX(1, pp, mmmmm, w, 0, src1.encoding(), false);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
        }
    }

    /**
     * VEX-encoded instructions with an operand order of RVM.
     */
    public static class VexRVMOp extends VexOp {
        // @formatter:off
        public static final VexRVMOp VANDPS          = new VexRVMOp("VANDPS",      VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x54, VEXOpAssertion.AVX1_AVX512DQ_VL,             EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VANDPD          = new VexRVMOp("VANDPD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x54, VEXOpAssertion.AVX1_AVX512DQ_VL,             EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VANDNPS         = new VexRVMOp("VANDNPS",     VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x55, VEXOpAssertion.AVX1_AVX512DQ_VL,             EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VANDNPD         = new VexRVMOp("VANDNPD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x55, VEXOpAssertion.AVX1_AVX512DQ_VL,             EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VORPS           = new VexRVMOp("VORPS",       VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x56, VEXOpAssertion.AVX1_AVX512DQ_VL,             EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VORPD           = new VexRVMOp("VORPD",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x56, VEXOpAssertion.AVX1_AVX512DQ_VL,             EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VXORPS          = new VexRVMOp("VXORPS",      VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x57, VEXOpAssertion.AVX1_AVX512DQ_VL,             EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VXORPD          = new VexRVMOp("VXORPD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x57, VEXOpAssertion.AVX1_AVX512DQ_VL,             EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VADDPS          = new VexRVMOp("VADDPS",      VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x58, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VADDPD          = new VexRVMOp("VADDPD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x58, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VADDSS          = new VexRVMOp("VADDSS",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x58, VEXOpAssertion.AVX1_AVX512F_128_ONLY,        EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMOp VADDSD          = new VexRVMOp("VADDSD",      VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x58,VEXOpAssertion.AVX1_AVX512F_128_ONLY,         EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMOp VMULPS          = new VexRVMOp("VMULPS",      VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x59, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VMULPD          = new VexRVMOp("VMULPD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x59, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VMULSS          = new VexRVMOp("VMULSS",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x59, VEXOpAssertion.AVX1_AVX512F_128_ONLY,        EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMOp VMULSD          = new VexRVMOp("VMULSD",      VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x59, VEXOpAssertion.AVX1_AVX512F_128_ONLY,        EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMOp VSUBPS          = new VexRVMOp("VSUBPS",      VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5C, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VSUBPD          = new VexRVMOp("VSUBPD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5C, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VSUBSS          = new VexRVMOp("VSUBSS",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5C, VEXOpAssertion.AVX1_AVX512F_128_ONLY,        EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMOp VSUBSD          = new VexRVMOp("VSUBSD",      VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5C, VEXOpAssertion.AVX1_AVX512F_128_ONLY,        EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMOp VMINPS          = new VexRVMOp("VMINPS",      VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5D, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VMINPD          = new VexRVMOp("VMINPD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5D, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VMINSS          = new VexRVMOp("VMINSS",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5D, VEXOpAssertion.AVX1_128ONLY_AVX512F_128ONLY, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMOp VMINSD          = new VexRVMOp("VMINSD",      VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5D, VEXOpAssertion.AVX1_128ONLY_AVX512F_128ONLY, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMOp VDIVPS          = new VexRVMOp("VDIVPS",      VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5E, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VDIVPD          = new VexRVMOp("VDIVPD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5E, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VDIVSS          = new VexRVMOp("VDIVSS",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5E, VEXOpAssertion.AVX1_AVX512F_128_ONLY,        EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMOp VDIVSD          = new VexRVMOp("VDIVSD",      VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5E, VEXOpAssertion.AVX1_AVX512F_128_ONLY,        EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMOp VMAXPS          = new VexRVMOp("VMAXPS",      VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5F, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VMAXPD          = new VexRVMOp("VMAXPD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5F, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VMAXSS          = new VexRVMOp("VMAXSS",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5F, VEXOpAssertion.AVX1_128ONLY_AVX512F_128ONLY, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMOp VMAXSD          = new VexRVMOp("VMAXSD",      VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5F, VEXOpAssertion.AVX1_128ONLY_AVX512F_128ONLY, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMOp VPACKUSDW       = new VexRVMOp("VPACKUSDW",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x2B, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPACKUSWB       = new VexRVMOp("VPACKUSWB",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x67, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VADDSUBPS       = new VexRVMOp("VADDSUBPS",   VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xD0, VEXOpAssertion.AVX1);
        public static final VexRVMOp VADDSUBPD       = new VexRVMOp("VADDSUBPD",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xD0, VEXOpAssertion.AVX1);
        public static final VexRVMOp VPAND           = new VexRVMOp("VPAND",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xDB, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VPANDN          = new VexRVMOp("VPANDN",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xDF, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VPOR            = new VexRVMOp("VPOR",        VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xEB, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VPXOR           = new VexRVMOp("VPXOR",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xEF, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VPADDB          = new VexRVMOp("VPADDB",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xFC, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPADDW          = new VexRVMOp("VPADDW",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xFD, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPADDD          = new VexRVMOp("VPADDD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xFE, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPADDQ          = new VexRVMOp("VPADDQ",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xD4, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VPMAXSB         = new VexRVMOp("VPMAXSB",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x3C, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPMAXSW         = new VexRVMOp("VPMAXSW",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xEE, VEXOpAssertion.AVX1_AVX2_AVX512F_BW,         EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPMAXSD         = new VexRVMOp("VPMAXSD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x3D, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPMAXSQ         = new VexRVMOp("VPMAXSQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x3D, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VPMAXUB         = new VexRVMOp("VPMAXUB",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0xDE, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPMAXUW         = new VexRVMOp("VPMAXUW",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x3E, VEXOpAssertion.AVX1_AVX2_AVX512F_BW,         EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPMAXUD         = new VexRVMOp("VPMAXUD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x3F, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPMAXUQ         = new VexRVMOp("VPMAXUQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x3F, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VPMINSB         = new VexRVMOp("VPMINSB",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x38, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPMINSW         = new VexRVMOp("VPMINSW",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xEA, VEXOpAssertion.AVX1_AVX2_AVX512F_BW,         EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPMINSD         = new VexRVMOp("VPMINSD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x39, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPMINSQ         = new VexRVMOp("VPMINSQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x39, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VPMINUB         = new VexRVMOp("VPMINUB",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0xDA, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPMINUW         = new VexRVMOp("VPMINUW",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x3A, VEXOpAssertion.AVX1_AVX2_AVX512F_BW,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPMINUD         = new VexRVMOp("VPMINUD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x3B, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPMINUQ         = new VexRVMOp("VPMINUQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x3B, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VPMULHUW        = new VexRVMOp("VPMULHUW",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xE4, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPMULHW         = new VexRVMOp("VPMULHW",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xE5, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPMULLW         = new VexRVMOp("VPMULLW",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xD5, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPMULLD         = new VexRVMOp("VPMULLD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x40, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPMULLQ         = new VexRVMOp("VPMULLQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x40, VEXOpAssertion.AVX512DQ_VL,                  EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VPSUBUSB        = new VexRVMOp("VPSUBUSB",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xD8, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPSUBUSW        = new VexRVMOp("VPSUBUSW",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xD9, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPSUBB          = new VexRVMOp("VPSUBB",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xF8, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPSUBW          = new VexRVMOp("VPSUBW",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xF9, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPSUBD          = new VexRVMOp("VPSUBD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xFA, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPSUBQ          = new VexRVMOp("VPSUBQ",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xFB, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VPSHUFB         = new VexRVMOp("VPSHUFB",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x00, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPCMPEQB        = new VexRVMOp("VPCMPEQB",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x74, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPEQB_AVX512 = new VexRVMOp("VPCMPEQB",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x74, VEXOpAssertion.MASK_XMM_XMM_AVX512BW_VL,     EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPCMPEQW        = new VexRVMOp("VPCMPEQW",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x75, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPEQW_AVX512 = new VexRVMOp("VPCMPEQW",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x75, VEXOpAssertion.MASK_XMM_XMM_AVX512BW_VL,     EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPCMPEQD        = new VexRVMOp("VPCMPEQD",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x76, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPEQD_AVX512 = new VexRVMOp("VPCMPEQD",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x76, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL,      EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPCMPEQQ        = new VexRVMOp("VPCMPEQQ",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x29, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPEQQ_AVX512 = new VexRVMOp("VPCMPEQQ",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x29, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL,      EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VPCMPGTB        = new VexRVMOp("VPCMPGTB",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x64, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPGTB_AVX512 = new VexRVMOp("VPCMPGTB",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x64, VEXOpAssertion.MASK_XMM_XMM_AVX512BW_VL,     EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPCMPGTW        = new VexRVMOp("VPCMPGTW",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x65, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPGTW_AVX512 = new VexRVMOp("VPCMPGTW",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x65, VEXOpAssertion.MASK_XMM_XMM_AVX512BW_VL,     EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPCMPGTD        = new VexRVMOp("VPCMPGTD",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x66, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPGTD_AVX512 = new VexRVMOp("VPCMPGTD",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x66, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL,      EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPCMPGTQ        = new VexRVMOp("VPCMPGTQ",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x37, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPGTQ_AVX512 = new VexRVMOp("VPCMPGTQ",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x37, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL,      EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VFMADD231SS     = new VexRVMOp("VFMADD231SS", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0xB9, VEXOpAssertion.FMA_AVX512F_128ONLY,          EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMOp VFMADD231SD     = new VexRVMOp("VFMADD231SD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0xB9, VEXOpAssertion.FMA_AVX512F_128ONLY,          EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMOp VSQRTSD         = new VexRVMOp("VSQRTSD",     VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x51, VEXOpAssertion.AVX1_AVX512F_ALL,             EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMOp VSQRTSS         = new VexRVMOp("VSQRTSS",     VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x51, VEXOpAssertion.AVX1_AVX512F_ALL,             EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);

        public static final VexRVMOp VPERMW          = new VexRVMOp("VPERMW",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x8D, VEXOpAssertion.AVX512BW_VL,                  EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VPERMD          = new VexRVMOp("VPERMD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x36, VEXOpAssertion.AVX2_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W0);

        public static final VexRVMOp VPBLENDMB       = new VexRVMOp("VPBLENDMB",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x66, VEXOpAssertion.AVX512BW_VL,                  EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPBLENDMW       = new VexRVMOp("VPBLENDMW",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x66, VEXOpAssertion.AVX512BW_VL,                  EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VPBLENDMD       = new VexRVMOp("VPBLENDMD",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x64, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPBLENDMQ       = new VexRVMOp("VPBLENDMQ",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x64, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VBLENDMPS       = new VexRVMOp("VBLENDMPS",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x65, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VBLENDMPD       = new VexRVMOp("VBLENDMPD",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x65, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VPERMT2B        = new VexRVMOp("VPERMT2B",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x7D, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W0);

        public static final VexRVMOp MOVLHPS         = new VexRVMOp("MOVLHPS",     VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x16, VEXOpAssertion.XMM_XMM_XMM_AVX512F_128ONLY,  EVEXTuple.FVM,       VEXPrefixConfig.W0);

        public static final VexRVMOp VPHADDW         = new VexRVMOp("VPHADDW",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x01, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPHADDD         = new VexRVMOp("VPHADDD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x02, VEXOpAssertion.AVX1_2);
        // @formatter:on

        protected VexRVMOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        private VexRVMOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src1, Register src2) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, src1, src2), "emitting invalid instruction");
            asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src1, AMD64Address src2) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, src1, null), "emitting invalid instruction");
            boolean useEvex = asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src2, 0, getDisp8Scale(useEvex, size));
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src1, Register src2, Register mask) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, src1, src2), "emitting invalid instruction");
            asm.vexPrefix(dst, src1, src2, mask, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src1, AMD64Address src2, Register mask) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, src1, null), "emitting invalid instruction");
            boolean useEvex = asm.vexPrefix(dst, src1, src2, mask, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src2, 0, getDisp8Scale(useEvex, size));
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src1, Register src2, Register mask, int z, int b) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, src1, src2), "emitting invalid instruction");
            asm.vexPrefix(dst, src1, src2, mask, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature, z, b);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src1, AMD64Address src2, Register mask, int z, int b) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, src1, null), "emitting invalid instruction");
            boolean useEvex = asm.vexPrefix(dst, src1, src2, mask, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature, z, b);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src2, 0, getDisp8Scale(useEvex, size));
        }

        public boolean isPacked() {
            return pp == VEXPrefixConfig.P_ || pp == VEXPrefixConfig.P_66;
        }
    }

    public static final class VexRVMConvertOp extends VexRVMOp {
        // @formatter:off
        public static final VexRVMConvertOp VCVTSD2SS = new VexRVMConvertOp("VCVTSD2SS", VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0x5A, VEXOpAssertion.XMM_XMM_XMM_AVX512F_128ONLY, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMConvertOp VCVTSS2SD = new VexRVMConvertOp("VCVTSS2SD", VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0x5A, VEXOpAssertion.XMM_XMM_XMM_AVX512F_128ONLY, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMConvertOp VCVTSI2SD = new VexRVMConvertOp("VCVTSI2SD", VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0,  0x2A, VEXOpAssertion.XMM_XMM_CPU_AVX512F_128ONLY, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMConvertOp VCVTSQ2SD = new VexRVMConvertOp("VCVTSQ2SD", VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1,  0x2A, VEXOpAssertion.XMM_XMM_CPU_AVX512F_128ONLY, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMConvertOp VCVTSI2SS = new VexRVMConvertOp("VCVTSI2SS", VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0,  0x2A, VEXOpAssertion.XMM_XMM_CPU_AVX512F_128ONLY, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMConvertOp VCVTSQ2SS = new VexRVMConvertOp("VCVTSQ2SS", VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1,  0x2A, VEXOpAssertion.XMM_XMM_CPU_AVX512F_128ONLY, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        // @formatter:on

        private VexRVMConvertOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
        }
    }

    public static final class VexGeneralPurposeRVMOp extends VexRVMOp {
        // @formatter:off
        public static final VexGeneralPurposeRVMOp ANDN = new VexGeneralPurposeRVMOp("ANDN",   VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0xF2, VEXOpAssertion.BMI1);
        public static final VexGeneralPurposeRVMOp MULX = new VexGeneralPurposeRVMOp("MULX",   VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0xF6, VEXOpAssertion.BMI2);
        public static final VexGeneralPurposeRVMOp PDEP = new VexGeneralPurposeRVMOp("PDEP",   VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0xF5, VEXOpAssertion.BMI2);
        public static final VexGeneralPurposeRVMOp PEXT = new VexGeneralPurposeRVMOp("PEXT",   VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0xF5, VEXOpAssertion.BMI2);
        // @formatter:on

        private VexGeneralPurposeRVMOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src1, Register src2) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), VEXPrefixConfig.LZ, dst, src1, src2, null), "emitting invalid instruction");
            assert size == AVXKind.AVXSize.DWORD || size == AVXKind.AVXSize.QWORD : size;
            asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, size == AVXKind.AVXSize.DWORD ? VEXPrefixConfig.W0 : VEXPrefixConfig.W1, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src1, Register src2, Register mask, int z, int b) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), VEXPrefixConfig.LZ, dst, src1, src2, null), "emitting invalid instruction");
            assert size == AVXKind.AVXSize.DWORD || size == AVXKind.AVXSize.QWORD : size;
            asm.vexPrefix(dst, src1, src2, mask, size, pp, mmmmm, size == AVXKind.AVXSize.DWORD ? VEXPrefixConfig.W0 : VEXPrefixConfig.W1, wEvex, false, assertion.l128feature, assertion.l256feature,
                            z, b);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src1, AMD64Address src2) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), VEXPrefixConfig.LZ, dst, src1, null, null), "emitting invalid instruction");
            assert size == AVXKind.AVXSize.DWORD || size == AVXKind.AVXSize.QWORD : size;
            asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, size == AVXKind.AVXSize.DWORD ? VEXPrefixConfig.W0 : VEXPrefixConfig.W1, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src2, 0);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src1, AMD64Address src2, Register mask, int z, int b) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), VEXPrefixConfig.LZ, dst, src1, null, null), "emitting invalid instruction");
            assert size == AVXKind.AVXSize.DWORD || size == AVXKind.AVXSize.QWORD : size;
            asm.vexPrefix(dst, src1, src2, mask, size, pp, mmmmm, size == AVXKind.AVXSize.DWORD ? VEXPrefixConfig.W0 : VEXPrefixConfig.W1, wEvex, false, assertion.l128feature, assertion.l256feature,
                            z, b);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src2, 0);
        }
    }

    public static final class VexGeneralPurposeRMVOp extends VexOp {
        // @formatter:off
        public static final VexGeneralPurposeRMVOp BEXTR  = new VexGeneralPurposeRMVOp("BEXTR",  VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0xF7, VEXOpAssertion.BMI1);
        public static final VexGeneralPurposeRMVOp BZHI   = new VexGeneralPurposeRMVOp("BZHI",   VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0xF5, VEXOpAssertion.BMI2);
        public static final VexGeneralPurposeRMVOp SARX   = new VexGeneralPurposeRMVOp("SARX",   VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0xF7, VEXOpAssertion.BMI2);
        public static final VexGeneralPurposeRMVOp SHRX   = new VexGeneralPurposeRMVOp("SHRX",   VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0xF7, VEXOpAssertion.BMI2);
        public static final VexGeneralPurposeRMVOp SHLX   = new VexGeneralPurposeRMVOp("SHLX",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0xF7, VEXOpAssertion.BMI2);
        // @formatter:on

        private VexGeneralPurposeRMVOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src1, Register src2) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), VEXPrefixConfig.LZ, dst, src2, src1, null), "emitting invalid instruction");
            assert size == AVXKind.AVXSize.DWORD || size == AVXKind.AVXSize.QWORD : size;
            asm.vexPrefix(dst, src2, src1, size, pp, mmmmm, size == AVXKind.AVXSize.DWORD ? VEXPrefixConfig.W0 : VEXPrefixConfig.W1, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitModRM(dst, src1);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, AMD64Address src1, Register src2) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), VEXPrefixConfig.LZ, dst, src2, null, null), "emitting invalid instruction");
            assert size == AVXKind.AVXSize.DWORD || size == AVXKind.AVXSize.QWORD : size;
            asm.vexPrefix(dst, src2, src1, size, pp, mmmmm, size == AVXKind.AVXSize.DWORD ? VEXPrefixConfig.W0 : VEXPrefixConfig.W1, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src1, 0);
        }
    }

    public static final class VexAESOp extends VexRVMOp {
        // @formatter:off
        public static final VexAESOp VAESENC     = new VexAESOp("VAESENC",     0xDC, VEXOpAssertion.AVX1_128ONLY_AES);
        public static final VexAESOp VAESENCLAST = new VexAESOp("VAESENCLAST", 0xDD, VEXOpAssertion.AVX1_128ONLY_AES);
        public static final VexAESOp VAESDEC     = new VexAESOp("VAESDEC",     0xDE, VEXOpAssertion.AVX1_128ONLY_AES);
        public static final VexAESOp VAESDECLAST = new VexAESOp("VAESDECLAST", 0xDF, VEXOpAssertion.AVX1_128ONLY_AES);
        // @formatter:on

        private VexAESOp(String opcode, int op, VEXOpAssertion assertion) {
            // VEX.NDS.128.66.0F38.WIG - w not specified, so ignored.
            super(opcode, VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, op, assertion);
        }

        public void emit(AMD64Assembler asm, Register result, Register state, Register key) {
            emit(asm, AVXKind.AVXSize.XMM, result, state, key);
        }

        public void emit(AMD64Assembler asm, Register result, Register state, AMD64Address keyLocation) {
            emit(asm, AVXKind.AVXSize.XMM, result, state, keyLocation);
        }
    }

    /**
     * VEX-encoded vector gather instructions with an operand order of RMV.
     */
    public static final class VexGatherOp extends VexOp {
        // @formatter:off
        public static final VexGatherOp VPGATHERDD = new VexGatherOp("VPGATHERDD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, 0x90, VEXOpAssertion.AVX2);
        public static final VexGatherOp VPGATHERQD = new VexGatherOp("VPGATHERQD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, 0x91, VEXOpAssertion.AVX2);
        public static final VexGatherOp VPGATHERDQ = new VexGatherOp("VPGATHERDQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1, 0x90, VEXOpAssertion.AVX2);
        public static final VexGatherOp VPGATHERQQ = new VexGatherOp("VPGATHERQQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1, 0x91, VEXOpAssertion.AVX2);
        public static final VexGatherOp VGATHERDPD = new VexGatherOp("VGATHERDPD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1, 0x92, VEXOpAssertion.AVX2);
        public static final VexGatherOp VGATHERQPD = new VexGatherOp("VGATHERQPD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1, 0x93, VEXOpAssertion.AVX2);
        public static final VexGatherOp VGATHERDPS = new VexGatherOp("VGATHERDPS", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, 0x92, VEXOpAssertion.AVX2);
        public static final VexGatherOp VGATHERQPS = new VexGatherOp("VGATHERQPS", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, 0x93, VEXOpAssertion.AVX2);
        // @formatter:on

        protected VexGatherOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion, EVEXTuple.INVALID, VEXPrefixConfig.WIG);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, AMD64Address address, Register mask) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, mask, null, null), "emitting invalid instruction");
            assert size == AVXKind.AVXSize.XMM || size == AVXKind.AVXSize.YMM : size;
            asm.vexPrefix(dst, mask, address, size, pp, mmmmm, w, wEvex, true, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, address, 0);
        }
    }

    /**
     * EVEX-encoded vector gather instructions with an operand order of RM.
     */
    public static final class EvexGatherOp extends VexOp {
        // @formatter:off
        public static final EvexGatherOp VPGATHERDD = new EvexGatherOp("VPGATHERDD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, 0x90, VEXOpAssertion.AVX512F_VL, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final EvexGatherOp VPGATHERQD = new EvexGatherOp("VPGATHERQD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, 0x91, VEXOpAssertion.AVX512F_VL, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final EvexGatherOp VPGATHERDQ = new EvexGatherOp("VPGATHERDQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, 0x90, VEXOpAssertion.AVX512F_VL, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final EvexGatherOp VPGATHERQQ = new EvexGatherOp("VPGATHERQQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, 0x91, VEXOpAssertion.AVX512F_VL, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final EvexGatherOp VGATHERDPD = new EvexGatherOp("VGATHERDPD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, 0x92, VEXOpAssertion.AVX512F_VL, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final EvexGatherOp VGATHERQPD = new EvexGatherOp("VGATHERQPD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, 0x93, VEXOpAssertion.AVX512F_VL, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final EvexGatherOp VGATHERDPS = new EvexGatherOp("VGATHERDPS", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, 0x92, VEXOpAssertion.AVX512F_VL, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final EvexGatherOp VGATHERQPS = new EvexGatherOp("VGATHERQPS", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, 0x93, VEXOpAssertion.AVX512F_VL, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        // @formatter:on

        protected EvexGatherOp(String opcode, int pp, int mmmmm, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, wEvex, op, assertion, evexTuple, wEvex);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, AMD64Address address, Register mask, int z, int b) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, null, null, null), "emitting invalid instruction");
            assert size == AVXSize.XMM || size == AVXSize.YMM || size == AVXSize.ZMM : size;
            asm.vexPrefix(dst, Register.None, address, mask, size, pp, mmmmm, w, wEvex, true, assertion.l128feature, assertion.l256feature, z, b);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, address, 0);
        }
    }

    public static final class VexGeneralPurposeRMOp extends VexRMOp {
        // @formatter:off
        public static final VexGeneralPurposeRMOp BLSI    = new VexGeneralPurposeRMOp("BLSI",   VEXPrefixConfig.P_,    VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0xF3, 3, VEXOpAssertion.BMI1);
        public static final VexGeneralPurposeRMOp BLSMSK  = new VexGeneralPurposeRMOp("BLSMSK", VEXPrefixConfig.P_,    VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0xF3, 2, VEXOpAssertion.BMI1);
        public static final VexGeneralPurposeRMOp BLSR    = new VexGeneralPurposeRMOp("BLSR",   VEXPrefixConfig.P_,    VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0xF3, 1, VEXOpAssertion.BMI1);
        // @formatter:on
        private final int ext;

        private VexGeneralPurposeRMOp(String opcode, int pp, int mmmmm, int w, int op, int ext, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
            this.ext = ext;
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, null, null), "emitting invalid instruction");
            asm.vexPrefix(AMD64.cpuRegisters[ext], dst, src, size, pp, mmmmm, size == AVXKind.AVXSize.DWORD ? VEXPrefixConfig.W0 : VEXPrefixConfig.W1, wEvex, false, assertion.l128feature,
                            assertion.l256feature);
            asm.emitByte(op);
            asm.emitModRM(ext, src);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src, Register mask, int z, int b) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, null, null), "emitting invalid instruction");
            asm.vexPrefix(AMD64.cpuRegisters[ext], dst, src, mask, size, pp, mmmmm, size == AVXKind.AVXSize.DWORD ? VEXPrefixConfig.W0 : VEXPrefixConfig.W1, wEvex, false, assertion.l128feature,
                            assertion.l256feature, z, b);
            asm.emitByte(op);
            asm.emitModRM(ext, src);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, AMD64Address src) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, null, null), "emitting invalid instruction");
            asm.vexPrefix(AMD64.cpuRegisters[ext], dst, src, size, pp, mmmmm, size == AVXKind.AVXSize.DWORD ? VEXPrefixConfig.W0 : VEXPrefixConfig.W1, wEvex, false, assertion.l128feature,
                            assertion.l256feature);
            asm.emitByte(op);
            asm.emitOperandHelper(ext, src, 0);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, AMD64Address src, Register mask, int z, int b) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, null, null), "emitting invalid instruction");
            asm.vexPrefix(AMD64.cpuRegisters[ext], dst, src, mask, size, pp, mmmmm, size == AVXKind.AVXSize.DWORD ? VEXPrefixConfig.W0 : VEXPrefixConfig.W1, wEvex, false, assertion.l128feature,
                            assertion.l256feature, z, b);
            asm.emitByte(op);
            asm.emitOperandHelper(ext, src, 0);
        }
    }

    /**
     * VEX-encoded shift instructions with an operand order of either RVM or VMI.
     */
    public static final class VexShiftOp extends VexRVMOp implements VexRRIOp {
        // @formatter:off
        public static final VexShiftOp VPSRLW = new VexShiftOp("VPSRLW", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xD1, 0x71, 2, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL, EVEXTuple.M128, VEXPrefixConfig.WIG);
        public static final VexShiftOp VPSRLD = new VexShiftOp("VPSRLD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xD2, 0x72, 2, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,  EVEXTuple.M128, VEXPrefixConfig.W0);
        public static final VexShiftOp VPSRLQ = new VexShiftOp("VPSRLQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xD3, 0x73, 2, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,  EVEXTuple.M128, VEXPrefixConfig.W1);
        public static final VexShiftOp VPSRAW = new VexShiftOp("VPSRAW", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xE1, 0x71, 4, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL, EVEXTuple.M128, VEXPrefixConfig.WIG);
        public static final VexShiftOp VPSRAD = new VexShiftOp("VPSRAD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xE2, 0x72, 4, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,  EVEXTuple.M128, VEXPrefixConfig.W0);
        public static final VexShiftOp VPSRAQ = new VexShiftOp("VPSRAQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1,  0xE2, 0x72, 4, VEXOpAssertion.AVX512F_VL,            EVEXTuple.M128, VEXPrefixConfig.W1);
        public static final VexShiftOp VPSLLW = new VexShiftOp("VPSLLW", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xF1, 0x71, 6, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL, EVEXTuple.M128, VEXPrefixConfig.WIG);
        public static final VexShiftOp VPSLLD = new VexShiftOp("VPSLLD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xF2, 0x72, 6, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,  EVEXTuple.M128, VEXPrefixConfig.W0);
        public static final VexShiftOp VPSLLQ = new VexShiftOp("VPSLLQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xF3, 0x73, 6, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,  EVEXTuple.M128, VEXPrefixConfig.W1);
        // @formatter:on

        private final int immOp;
        private final int r;

        private VexShiftOp(String opcode, int pp, int mmmmm, int w, int op, int immOp, int r, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
            this.immOp = immOp;
            this.r = r;
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src, int imm8) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, null, dst, src), "emitting invalid instruction");
            asm.vexPrefix(null, dst, src, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(immOp);
            asm.emitModRM(r, src);
            asm.emitByte(imm8);
        }
    }

    public static final class VexShiftImmOp extends VexOp implements VexRRIOp {
        // @formatter:off
        public static final VexShiftImmOp VPSLLDQ = new VexShiftImmOp("VPSLLDQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG,  0x73, 7, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL, EVEXTuple.FVM, VEXPrefixConfig.WIG);
        public static final VexShiftImmOp VPSRLDQ = new VexShiftImmOp("VPSRLDQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG,  0x73, 3, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL, EVEXTuple.FVM, VEXPrefixConfig.WIG);
        // @formatter:on

        private final int r;

        private VexShiftImmOp(String opcode, int pp, int mmmmm, int w, int op, int r, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
            this.r = r;
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src, int imm8) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, null, dst, src), "emitting invalid instruction");
            asm.vexPrefix(null, dst, src, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitModRM(r, src);
            asm.emitByte(imm8);
        }
    }

    /**
     * Masked (i.e., conditional) SIMD loads and stores.
     */
    public static final class VexMaskedMoveOp extends VexOp {
        // @formatter:off
        public static final VexMaskedMoveOp VMASKMOVPS = new VexMaskedMoveOp("VMASKMOVPS", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, 0x2C, 0x2E);
        public static final VexMaskedMoveOp VMASKMOVPD = new VexMaskedMoveOp("VMASKMOVPD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, 0x2D, 0x2F);
        public static final VexMaskedMoveOp VPMASKMOVD = new VexMaskedMoveOp("VPMASKMOVD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, 0x8C, 0x8E, VEXOpAssertion.AVX2);
        public static final VexMaskedMoveOp VPMASKMOVQ = new VexMaskedMoveOp("VPMASKMOVQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1, 0x8C, 0x8E, VEXOpAssertion.AVX2);
        // @formatter:on

        private final int opReverse;

        private VexMaskedMoveOp(String opcode, int pp, int mmmmm, int w, int op, int opReverse) {
            this(opcode, pp, mmmmm, w, op, opReverse, VEXOpAssertion.AVX1);
        }

        private VexMaskedMoveOp(String opcode, int pp, int mmmmm, int w, int op, int opReverse, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
            this.opReverse = opReverse;
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register mask, AMD64Address src) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, mask, null), "emitting invalid instruction");
            asm.vexPrefix(dst, mask, src, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src, 0);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, AMD64Address dst, Register mask, Register src) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, src, mask, null), "emitting invalid instruction");
            boolean useEvex = asm.vexPrefix(src, mask, dst, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(opReverse);
            asm.emitOperandHelper(src, dst, 0, getDisp8Scale(useEvex, size));
        }
    }

    /**
     * VEX-encoded mask move instructions to and from mask (K) registers.
     */
    public static final class VexMoveMaskOp extends VexGeneralMoveOp {
        // @formatter:off
        public static final VexMoveMaskOp KMOVW = new VexMoveMaskOp("KMOVW", VEXPrefixConfig.P_,   VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, VEXPrefixConfig.W0, VEXOpAssertion.AVX512F_CPU_OR_MASK);
        public static final VexMoveMaskOp KMOVB = new VexMoveMaskOp("KMOVB", VEXPrefixConfig.P_66, VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, VEXPrefixConfig.W0, VEXOpAssertion.AVX512DQ_CPU_OR_MASK);
        public static final VexMoveMaskOp KMOVQ = new VexMoveMaskOp("KMOVQ", VEXPrefixConfig.P_,   VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, VEXPrefixConfig.W1, VEXOpAssertion.AVX512BW_CPU_OR_MASK);
        public static final VexMoveMaskOp KMOVD = new VexMoveMaskOp("KMOVD", VEXPrefixConfig.P_66, VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, VEXPrefixConfig.W0, VEXOpAssertion.AVX512BW_CPU_OR_MASK);
        // @formatter:on

        /*
         * This family of instructions has a uniform set of four opcodes depending on whether the
         * source and destination are a K register, memory, or a general purpose register.
         */
        // @formatter:off
        private static final int OP_K_FROM_K_MEM = 0x90;
        private static final int OP_MEM_FROM_K   = 0x91;
        private static final int OP_K_FROM_CPU   = 0x92;
        private static final int OP_CPU_FROM_K   = 0x93;
        // @formatter:on

        /** The value of the pp field if one of the operands is a general purpose register. */
        private final int ppCPU;

        /** The value of the w field if one of the operands is a general purpose register. */
        private final int wCPU;

        private VexMoveMaskOp(String opcode, int pp, int ppCPU, int mmmmm, int w, int wCPU, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, OP_K_FROM_K_MEM, assertion, EVEXTuple.INVALID, w);
            this.ppCPU = ppCPU;
            this.wCPU = wCPU;
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, null, src), "emitting invalid instruction");
            GraalError.guarantee(!(inRC(CPU, dst) && inRC(CPU, src)), "source and destination can't both be CPU registers");
            int actualOp = op(dst, src);
            int actualPP = pp(dst, src);
            int actualW = w(dst, src);
            asm.vexPrefix(dst, Register.None, src, size, actualPP, mmmmm, actualW, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(actualOp);
            asm.emitModRM(dst, src);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, AMD64Address dst, Register src) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, src, null, null), "emitting invalid instruction");
            GraalError.guarantee(inRC(MASK, src), "source must be a mask register");
            asm.vexPrefix(src, Register.None, dst, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(OP_MEM_FROM_K);
            asm.emitOperandHelper(src, dst, 0);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, AMD64Address src) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, null, null), "emitting invalid instruction");
            GraalError.guarantee(inRC(MASK, dst), "destination must be a mask register");
            asm.vexPrefix(dst, Register.None, src, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(OP_K_FROM_K_MEM);
            asm.emitOperandHelper(dst, src, 0);
        }

        private static int op(Register dst, Register src) {
            if (inRC(MASK, dst)) {
                if (inRC(MASK, src)) {
                    return OP_K_FROM_K_MEM;
                } else {
                    assert inRC(CPU, src);
                    return OP_K_FROM_CPU;
                }
            } else {
                assert inRC(CPU, dst) && inRC(MASK, src) : src + " " + dst;
                return OP_CPU_FROM_K;
            }
        }

        private int pp(Register dst, Register src) {
            if (inRC(CPU, dst) || inRC(CPU, src)) {
                return ppCPU;
            } else {
                return pp;
            }
        }

        private int w(Register dst, Register src) {
            if (inRC(CPU, dst) || inRC(CPU, src)) {
                return wCPU;
            } else {
                return w;
            }
        }
    }

    /**
     * VEX-encoded instructions with an operand order of RVMI.
     */
    public static final class VexRVMIOp extends VexOp {
        // @formatter:off
        public static final VexRVMIOp VPINSRB      = new VexRVMIOp("VPINSRB",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x20, VEXOpAssertion.XMM_XMM_CPU);
        public static final VexRVMIOp VPINSRW      = new VexRVMIOp("VPINSRW",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0xC4, VEXOpAssertion.XMM_XMM_CPU);
        public static final VexRVMIOp VPINSRD      = new VexRVMIOp("VPINSRD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x22, VEXOpAssertion.XMM_XMM_CPU);
        public static final VexRVMIOp VPINSRQ      = new VexRVMIOp("VPINSRQ",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x22, VEXOpAssertion.XMM_XMM_CPU);

        public static final VexRVMIOp VSHUFPS      = new VexRVMIOp("VSHUFPS",      VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xC6, VEXOpAssertion.AVX1_AVX512F_VL,          EVEXTuple.FVM,      VEXPrefixConfig.W0);
        public static final VexRVMIOp VSHUFPD      = new VexRVMIOp("VSHUFPD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xC6, VEXOpAssertion.AVX1_AVX512F_VL,          EVEXTuple.FVM,      VEXPrefixConfig.W1);
        public static final VexRVMIOp VPTERNLOGD   = new VexRVMIOp("VPTERNLOGD",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x25, VEXOpAssertion.AVX512F_VL,               EVEXTuple.FVM,      VEXPrefixConfig.W0);
        public static final VexRVMIOp VPTERNLOGQ   = new VexRVMIOp("VPTERNLOGQ",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x25, VEXOpAssertion.AVX512F_VL,               EVEXTuple.FVM,      VEXPrefixConfig.W1);

        // AVX/AVX2 insert
        public static final VexRVMIOp VINSERTF128  = new VexRVMIOp("VINSERTF128",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x18, VEXOpAssertion.AVX1_256ONLY);
        public static final VexRVMIOp VINSERTI128  = new VexRVMIOp("VINSERTI128",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x38, VEXOpAssertion.AVX2_256ONLY);

        // AVX2 128-bit permutation
        public static final VexRVMIOp VPERM2I128   = new VexRVMIOp("VPERM2I128",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x46, VEXOpAssertion.AVX2_256ONLY);
        public static final VexRVMIOp VPERM2F128   = new VexRVMIOp("VPERM2F128",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x06, VEXOpAssertion.AVX1_256ONLY);
        // Carry-Less Multiplication Quadword
        public static final VexRVMIOp VPCLMULQDQ   = new VexRVMIOp("VPCLMULQDQ",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.WIG, 0x44, VEXOpAssertion.AVX1_128ONLY_CLMUL);
        // Packed Align Right
        public static final VexRVMIOp VPALIGNR     = new VexRVMIOp("VPALIGNR",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.WIG, 0x0F, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,    EVEXTuple.FVM,      VEXPrefixConfig.WIG);
        // Blend Packed Dwords
        public static final VexRVMIOp VPBLENDD     = new VexRVMIOp("VPBLENDD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x02, VEXOpAssertion.AVX2);

        // AVX-512 insert
        public static final VexRVMIOp VINSERTF32X4 = new VexRVMIOp("VINSERTF32X4", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x18, VEXOpAssertion.AVX512F_VL_256_512,       EVEXTuple.T4_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMIOp VINSERTI32X4 = new VexRVMIOp("VINSERTI32X4", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x38, VEXOpAssertion.AVX512F_VL_256_512,       EVEXTuple.T4_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMIOp VINSERTF64X2 = new VexRVMIOp("VINSERTF64X2", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x18, VEXOpAssertion.AVX512DQ_VL_256_512,      EVEXTuple.T2_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMIOp VINSERTI64X2 = new VexRVMIOp("VINSERTI64X2", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x38, VEXOpAssertion.AVX512DQ_VL_256_512,      EVEXTuple.T2_64BIT, VEXPrefixConfig.W1);

        public static final VexRVMIOp VINSERTF32X8 = new VexRVMIOp("VINSERTF32X8", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x1A, VEXOpAssertion.AVX512DQ_512ONLY,         EVEXTuple.T8_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMIOp VINSERTI32X8 = new VexRVMIOp("VINSERTI32X8", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x3A, VEXOpAssertion.AVX512DQ_512ONLY,         EVEXTuple.T8_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMIOp VINSERTF64X4 = new VexRVMIOp("VINSERTF64X4", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x1A, VEXOpAssertion.AVX512F_512ONLY,          EVEXTuple.T4_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMIOp VINSERTI64X4 = new VexRVMIOp("VINSERTI64X4", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x3A, VEXOpAssertion.AVX512F_512ONLY,          EVEXTuple.T4_64BIT, VEXPrefixConfig.W1);

        public static final VexRVMIOp VALIGND      = new VexRVMIOp("VALIGND",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x03, VEXOpAssertion.AVX512F_VL,               EVEXTuple.FVM,      VEXPrefixConfig.W0);
        public static final VexRVMIOp VALIGNQ      = new VexRVMIOp("VALIGNQ",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x03, VEXOpAssertion.AVX512F_VL,               EVEXTuple.FVM,      VEXPrefixConfig.W1);

        // AVX-512 unsigned comparisons
        public static final VexRVMIOp VPCMPUB      = new VexRVMIOp("VPCMPUB",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x3E, VEXOpAssertion.MASK_XMM_XMM_AVX512BW_VL, EVEXTuple.FVM,      VEXPrefixConfig.W0);
        public static final VexRVMIOp VPCMPUW      = new VexRVMIOp("VPCMPUW",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x3E, VEXOpAssertion.MASK_XMM_XMM_AVX512BW_VL, EVEXTuple.FVM,      VEXPrefixConfig.W1);
        public static final VexRVMIOp VPCMPUD      = new VexRVMIOp("VPCMPUD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x1E, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL,  EVEXTuple.FVM,      VEXPrefixConfig.W0);
        public static final VexRVMIOp VPCMPUQ      = new VexRVMIOp("VPCMPUQ",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x1E, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL,  EVEXTuple.FVM,      VEXPrefixConfig.W1);

        // Galois Field New Instructions
        public static final VexRVMIOp VGF2P8AFFINEQB = new VexRVMIOp("VGF2P8AFFINEQB", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0xCE, VEXOpAssertion.AVX1_GFNI_AVX512F_VL,  EVEXTuple.FVM, VEXPrefixConfig.W1);
        // @formatter:on

        private VexRVMIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        private VexRVMIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src1, Register src2, int imm8) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, src1, src2), "emitting invalid instruction");
            assert (imm8 & 0xFF) == imm8 : imm8;
            asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
            asm.emitByte(imm8);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src1, AMD64Address src2, int imm8) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, src1, null), "emitting invalid instruction");
            assert (imm8 & 0xFF) == imm8 : imm8;
            boolean useEvex = asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src2, 1, getDisp8Scale(useEvex, size));
            asm.emitByte(imm8);
        }
    }

    /**
     * VEX-encoded comparison operation with an operand order of RVMI. The immediate operand is a
     * comparison operator.
     */
    public static final class VexFloatCompareOp extends VexOp {
        // @formatter:off
        public static final VexFloatCompareOp VCMPPS        = new VexFloatCompareOp("VCMPPS", VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xC2);
        public static final VexFloatCompareOp VCMPPS_AVX512 = new VexFloatCompareOp("VCMPPS", VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W0,  0xC2, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL, EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexFloatCompareOp VCMPPD        = new VexFloatCompareOp("VCMPPD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xC2);
        public static final VexFloatCompareOp VCMPPD_AVX512 = new VexFloatCompareOp("VCMPPD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1,  0xC2, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL, EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexFloatCompareOp VCMPSS        = new VexFloatCompareOp("VCMPSS", VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xC2);
        public static final VexFloatCompareOp VCMPSS_AVX512 = new VexFloatCompareOp("VCMPSS", VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0,  0xC2, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexFloatCompareOp VCMPSD        = new VexFloatCompareOp("VCMPSD", VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xC2);
        public static final VexFloatCompareOp VCMPSD_AVX512 = new VexFloatCompareOp("VCMPSD", VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1,  0xC2, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        // @formatter:on

        public enum Predicate {
            EQ_OQ(0x00),
            LT_OS(0x01),
            LE_OS(0x02),
            UNORD_Q(0x03),
            NEQ_UQ(0x04),
            NLT_US(0x05),
            NLE_US(0x06),
            ORD_Q(0x07),
            EQ_UQ(0x08),
            NGE_US(0x09),
            NGT_US(0x0a),
            FALSE_OQ(0x0b),
            NEQ_OQ(0x0c),
            GE_OS(0x0d),
            GT_OS(0x0e),
            TRUE_UQ(0x0f),
            EQ_OS(0x10),
            LT_OQ(0x11),
            LE_OQ(0x12),
            UNORD_S(0x13),
            NEQ_US(0x14),
            NLT_UQ(0x15),
            NLE_UQ(0x16),
            ORD_S(0x17),
            EQ_US(0x18),
            NGE_UQ(0x19),
            NGT_UQ(0x1a),
            FALSE_OS(0x1b),
            NEQ_OS(0x1c),
            GE_OQ(0x1d),
            GT_OQ(0x1e),
            TRUE_US(0x1f);

            private int imm8;

            Predicate(int imm8) {
                this.imm8 = imm8;
            }

            public static Predicate getPredicate(Condition condition, boolean unorderedIsTrue) {
                if (unorderedIsTrue) {
                    switch (condition) {
                        case EQ:
                            return EQ_UQ;
                        case NE:
                            return NEQ_UQ;
                        case LT:
                            return NGE_UQ;
                        case LE:
                            return NGT_UQ;
                        case GT:
                            return NLE_UQ;
                        case GE:
                            return NLT_UQ;
                        default:
                            throw GraalError.shouldNotReachHereUnexpectedValue(condition); // ExcludeFromJacocoGeneratedReport
                    }
                } else {
                    switch (condition) {
                        case EQ:
                            return EQ_OQ;
                        case NE:
                            return NEQ_OQ;
                        case LT:
                            return LT_OQ;
                        case LE:
                            return LE_OQ;
                        case GT:
                            return GT_OQ;
                        case GE:
                            return GE_OQ;
                        default:
                            throw GraalError.shouldNotReachHereUnexpectedValue(condition); // ExcludeFromJacocoGeneratedReport
                    }
                }
            }
        }

        private VexFloatCompareOp(String opcode, int pp, int mmmmm, int w, int op) {
            super(opcode, pp, mmmmm, w, op, VEXOpAssertion.AVX1);
        }

        private VexFloatCompareOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src1, Register src2, Predicate p) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, src1, src2), "emitting invalid instruction");
            asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
            asm.emitByte(p.imm8);
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, Register dst, Register src1, AMD64Address src2, Predicate p) {
            GraalError.guarantee(assertion.check(asm.getFeatures(), size, dst, src1, null), "emitting invalid instruction");
            boolean useEvex = asm.vexPrefix(dst, src1, src2, size, pp, mmmmm, w, wEvex, false, assertion.l128feature, assertion.l256feature);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src2, 1, getDisp8Scale(useEvex, size));
            asm.emitByte(p.imm8);
        }
    }

    public final void emit(VexRMOp op, Register dst, Register src, AVXKind.AVXSize size) {
        op.emit(this, size, dst, src);
    }

    public final void emit(VexRMOp op, Register dst, AMD64Address src, AVXKind.AVXSize size) {
        op.emit(this, size, dst, src);
    }

    public final void emit(VexRMIOp op, Register dst, Register src, int imm8, AVXKind.AVXSize size) {
        op.emit(this, size, dst, src, imm8);
    }

    public final void emit(VexMRIOp op, Register dst, Register src, int imm8, AVXKind.AVXSize size) {
        op.emit(this, size, dst, src, imm8);
    }

    public final void emit(VexRVMOp op, Register dst, Register src1, Register src2, AVXKind.AVXSize size) {
        op.emit(this, size, dst, src1, src2);
    }

    public final void emit(VexGeneralPurposeRMVOp op, Register dst, Register src1, Register src2, AVXKind.AVXSize size) {
        op.emit(this, size, dst, src1, src2);
    }

    public final void addl(AMD64Address dst, int imm32) {
        ADD.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void addl(Register dst, int imm32) {
        ADD.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void addl(Register dst, Register src) {
        ADD.rmOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void addl(Register dst, AMD64Address src) {
        ADD.rmOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void adcl(Register dst, int imm32) {
        ADC.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void addpd(Register dst, Register src) {
        SSEOp.ADD.emit(this, OperandSize.PD, dst, src);
    }

    public final void addpd(Register dst, AMD64Address src) {
        SSEOp.ADD.emit(this, OperandSize.PD, dst, src);
    }

    public final void addsd(Register dst, Register src) {
        SSEOp.ADD.emit(this, OperandSize.SD, dst, src);
    }

    public final void addsd(Register dst, AMD64Address src) {
        SSEOp.ADD.emit(this, OperandSize.SD, dst, src);
    }

    private void addrNop4() {
        // 4 bytes: NOP DWORD PTR [EAX+0]
        emitByte(0x0F);
        emitByte(0x1F);
        emitByte(0x40); // emitRm(cbuf, 0x1, EAXEnc, EAXEnc);
        emitByte(0); // 8-bits offset (1 byte)
    }

    private void addrNop5() {
        // 5 bytes: NOP DWORD PTR [EAX+EAX*0+0] 8-bits offset
        emitByte(0x0F);
        emitByte(0x1F);
        emitByte(0x44); // emitRm(cbuf, 0x1, EAXEnc, 0x4);
        emitByte(0x00); // emitRm(cbuf, 0x0, EAXEnc, EAXEnc);
        emitByte(0); // 8-bits offset (1 byte)
    }

    private void addrNop7() {
        // 7 bytes: NOP DWORD PTR [EAX+0] 32-bits offset
        emitByte(0x0F);
        emitByte(0x1F);
        emitByte(0x80); // emitRm(cbuf, 0x2, EAXEnc, EAXEnc);
        emitInt(0); // 32-bits offset (4 bytes)
    }

    private void addrNop8() {
        // 8 bytes: NOP DWORD PTR [EAX+EAX*0+0] 32-bits offset
        emitByte(0x0F);
        emitByte(0x1F);
        emitByte(0x84); // emitRm(cbuf, 0x2, EAXEnc, 0x4);
        emitByte(0x00); // emitRm(cbuf, 0x0, EAXEnc, EAXEnc);
        emitInt(0); // 32-bits offset (4 bytes)
    }

    public final void andl(Register dst, int imm32) {
        AND.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void andl(Register dst, Register src) {
        AND.rmOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void mull(Register src) {
        MUL.emit(this, OperandSize.DWORD, src);
    }

    public final void andpd(Register dst, Register src) {
        SSEOp.AND.emit(this, OperandSize.PD, dst, src);
    }

    public final void andpd(Register dst, AMD64Address src) {
        SSEOp.AND.emit(this, OperandSize.PD, dst, src);
    }

    public final void bsfq(Register dst, Register src) {
        prefixq(dst, src);
        emitByte(0x0F);
        emitByte(0xBC);
        emitModRM(dst, src);
    }

    public final void bsrl(Register dst, Register src) {
        prefix(dst, src);
        emitByte(0x0F);
        emitByte(0xBD);
        emitModRM(dst, src);
    }

    public final void popcntl(Register dst, Register src) {
        AMD64RMOp.POPCNT.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void bswapl(Register reg) {
        prefix(reg);
        emitByte(0x0F);
        emitModRM(1, reg);
    }

    public final void cdql() {
        emitByte(0x99);
    }

    public final void cmovl(ConditionFlag cc, Register dst, Register src) {
        prefix(dst, src);
        emitByte(0x0F);
        emitByte(0x40 | cc.getValue());
        emitModRM(dst, src);
    }

    public final void cmovl(ConditionFlag cc, Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x40 | cc.getValue());
        emitOperandHelper(dst, src, 0);
    }

    public final void cmpb(Register dst, Register src) {
        CMP.byteRmOp.emit(this, OperandSize.BYTE, dst, src);
    }

    public final void cmpw(Register dst, Register src) {
        CMP.rmOp.emit(this, OperandSize.WORD, dst, src);
    }

    public final void cmpl(Register dst, int imm32) {
        CMP.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void cmpl(Register dst, Register src) {
        CMP.rmOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void cmpl(Register dst, AMD64Address src) {
        CMP.rmOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void cmpl(AMD64Address dst, int imm32) {
        CMP.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
    }

    /**
     * The 8-bit cmpxchg compares the value at adr with the contents of X86.rax, and stores reg into
     * adr if so; otherwise, the value at adr is loaded into X86.rax,. The ZF is set if the compared
     * values were equal, and cleared otherwise.
     */
    public final void cmpxchgb(Register reg, AMD64Address adr) { // cmpxchg
        prefixb(adr, reg);
        emitByte(0x0F);
        emitByte(0xB0);
        emitOperandHelper(reg, adr, 0);
    }

    /**
     * The 16-bit cmpxchg compares the value at adr with the contents of X86.rax, and stores reg
     * into adr if so; otherwise, the value at adr is loaded into X86.rax,. The ZF is set if the
     * compared values were equal, and cleared otherwise.
     */
    public final void cmpxchgw(Register reg, AMD64Address adr) { // cmpxchg
        emitByte(0x66); // Switch to 16-bit mode.
        prefix(adr, reg);
        emitByte(0x0F);
        emitByte(0xB1);
        emitOperandHelper(reg, adr, 0);
    }

    /**
     * The 32-bit cmpxchg compares the value at adr with the contents of X86.rax, and stores reg
     * into adr if so; otherwise, the value at adr is loaded into X86.rax,. The ZF is set if the
     * compared values were equal, and cleared otherwise.
     */
    public final void cmpxchgl(Register reg, AMD64Address adr) { // cmpxchg
        prefix(adr, reg);
        emitByte(0x0F);
        emitByte(0xB1);
        emitOperandHelper(reg, adr, 0);
    }

    public final void cvtsi2sdl(Register dst, Register src) {
        SSEOp.CVTSI2SD.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void cvttsd2sil(Register dst, Register src) {
        SSEOp.CVTTSD2SI.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void decl(AMD64Address dst) {
        DEC.emit(this, OperandSize.DWORD, dst);
    }

    public final void divsd(Register dst, Register src) {
        SSEOp.DIV.emit(this, OperandSize.SD, dst, src);
    }

    public final void hlt() {
        emitByte(0xF4);
    }

    public final void imull(Register dst, Register src, int value) {
        if (isByte(value)) {
            AMD64RMIOp.IMUL_SX.emit(this, OperandSize.DWORD, dst, src, value);
        } else {
            AMD64RMIOp.IMUL.emit(this, OperandSize.DWORD, dst, src, value);
        }
    }

    public final void incl(AMD64Address dst) {
        INC.emit(this, OperandSize.DWORD, dst);
    }

    public static final int JCC_ERRATUM_MITIGATION_BOUNDARY = 0x20;
    public static final int OPCODE_IN_BYTES = 1;
    public static final int MODRM_IN_BYTES = 1;

    protected static int getPrefixInBytes(OperandSize size, Register dst, boolean dstIsByte) {
        boolean needsRex = needsRex(dst, dstIsByte);
        if (size == OperandSize.WORD) {
            return needsRex ? 2 : 1;
        }
        return size == OperandSize.QWORD || needsRex ? 1 : 0;
    }

    protected static int getPrefixInBytes(OperandSize size, AMD64Address src) {
        boolean needsRex = needsRex(src.getBase()) || needsRex(src.getIndex());
        if (size == OperandSize.WORD) {
            return needsRex ? 2 : 1;
        }
        return size == OperandSize.QWORD || needsRex ? 1 : 0;
    }

    protected static int getPrefixInBytes(OperandSize size, Register dst, boolean dstIsByte, Register src, boolean srcIsByte) {
        boolean needsRex = needsRex(dst, dstIsByte) || needsRex(src, srcIsByte);
        if (size == OperandSize.WORD) {
            return needsRex ? 2 : 1;
        }
        return size == OperandSize.QWORD || needsRex ? 1 : 0;
    }

    protected static int getPrefixInBytes(OperandSize size, Register dst, boolean dstIsByte, AMD64Address src) {
        boolean needsRex = needsRex(dst, dstIsByte) || needsRex(src.getBase()) || needsRex(src.getIndex());
        if (size == OperandSize.WORD) {
            return needsRex ? 2 : 1;
        }
        return size == OperandSize.QWORD || needsRex ? 1 : 0;
    }

    protected boolean mayCrossBoundary(int opStart, int opEnd) {
        return (opStart / JCC_ERRATUM_MITIGATION_BOUNDARY) != ((opEnd - 1) / JCC_ERRATUM_MITIGATION_BOUNDARY) || (opEnd % JCC_ERRATUM_MITIGATION_BOUNDARY) == 0;
    }

    private static int bytesUntilBoundary(int pos) {
        return JCC_ERRATUM_MITIGATION_BOUNDARY - (pos % JCC_ERRATUM_MITIGATION_BOUNDARY);
    }

    protected boolean ensureWithinBoundary(int opStart) {
        if (useBranchesWithin32ByteBoundary) {
            int nextOpStart = position();
            int opEnd = nextOpStart - 1;
            if (mayCrossBoundary(opStart, opEnd)) {
                throw new GraalError("instruction at %d of size %d bytes crosses a JCC erratum boundary", opStart, nextOpStart - opStart);
            }
        }
        return true;
    }

    /**
     * If this assembler is configured to mitigate the Intel JCC erratum, emits nops at the current
     * position such that an instruction of size {@code bytesToEmit} will not cross a
     * {@value #JCC_ERRATUM_MITIGATION_BOUNDARY}.
     *
     * @return the number of nop bytes emitted
     */
    protected final int mitigateJCCErratum(int bytesToEmit) {
        return mitigateJCCErratum(position(), bytesToEmit);
    }

    /**
     * If this assembler is configured to mitigate the Intel JCC erratum, emits nops at the current
     * position such that an instruction of size {@code bytesToEmit} at {@code position} will not
     * cross a {@value #JCC_ERRATUM_MITIGATION_BOUNDARY}.
     *
     * @return the number of nop bytes emitted
     */
    protected final int mitigateJCCErratum(int position, int bytesToEmit) {
        if (useBranchesWithin32ByteBoundary) {
            int bytesUntilBoundary = bytesUntilBoundary(position);
            if (bytesUntilBoundary < bytesToEmit) {
                nop(bytesUntilBoundary);
                return bytesUntilBoundary;
            }
        }
        return 0;
    }

    public void jcc(ConditionFlag cc, int jumpTarget, boolean forceDisp32) {
        final int shortSize = JumpType.JCCB.instrSize;
        final int longSize = JumpType.JCC.instrSize;

        long disp = jumpTarget - position();
        if (!forceDisp32 && isByte(disp - shortSize)) {
            mitigateJCCErratum(shortSize);
            // After alignment, isByte(disp - shortSize) might not hold. Need to check again.
            disp = jumpTarget - position();
            if (isByte(disp - shortSize)) {
                // 0111 tttn #8-bit disp
                int pos = position();
                emitByte(0x70 | cc.getValue());
                emitByte((int) ((disp - shortSize) & 0xFF));
                trackJump(JumpType.JCCB, pos);
                return;
            }
        }

        // 0000 1111 1000 tttn #32-bit disp
        assert forceDisp32 || isInt(disp - longSize) : "must be 32bit offset (call4)";
        mitigateJCCErratum(longSize);
        int pos = position();
        disp = jumpTarget - position();
        emitByte(0x0F);
        emitByte(0x80 | cc.getValue());
        emitInt((int) (disp - longSize));
        trackJump(JumpType.JCC, pos);
    }

    /**
     * Conditional jump to a target that will be patched in later. Therefore, no jump target is
     * provided to this method.
     */
    public final void jcc(ConditionFlag cc) {
        annotatePatchingImmediate(2, 4);
        int pos = position();
        emitByte(0x0F);
        emitByte(0x80 | cc.getValue());
        emitInt(0);
        trackJump(JumpType.JCC, pos);
    }

    public final void jcc(ConditionFlag cc, Label l) {
        assert (0 <= cc.getValue()) && (cc.getValue() < 16) : "illegal cc";
        if (l.isBound()) {
            jcc(cc, l.position(), false);
        } else if (canUseShortJump(nextJumpIdx)) {
            jccb(cc, l);
        } else {
            mitigateJCCErratum(6);
            // Note: could eliminate cond. jumps to this jump if condition
            // is the same however, seems to be rather unlikely case.
            // Note: use jccb() if label to be bound is very close to get
            // an 8-bit displacement
            l.addPatchAt(position(), this);
            int pos = position();
            emitByte(0x0F);
            emitByte(0x80 | cc.getValue());
            emitInt(0);
            trackJump(JumpType.JCC, pos);
        }
    }

    public final void jccb(ConditionFlag cc, Label l) {
        if (force4ByteNonZeroDisplacements) {
            jcc(cc, l);
            return;
        }
        final int shortSize = JumpType.JCCB.instrSize;
        mitigateJCCErratum(shortSize);
        int pos = position();
        if (l.isBound()) {
            int entry = l.position();
            assert isByte(entry - (position() + shortSize)) : "Displacement too large for a short jmp: " + (entry - (position() + shortSize));
            long disp = entry - position();
            // 0111 tttn #8-bit disp
            emitByte(0x70 | cc.getValue());
            emitByte((int) ((disp - shortSize) & 0xFF));
            trackJump(JumpType.JCCB, pos);
        } else {
            l.addPatchAt(position(), this);
            emitByte(0x70 | cc.getValue());
            emitByte(0);
            trackJump(JumpType.JCCB, pos);
        }
    }

    public final void jcc(ConditionFlag cc, Label branchTarget, boolean isShortJmp) {
        if (branchTarget == null) {
            // jump to placeholder
            jcc(cc, 0, true);
        } else if (isShortJmp) {
            jccb(cc, branchTarget);
        } else {
            jcc(cc, branchTarget);
        }
    }

    /**
     * Emit a jmp instruction given a known target address.
     *
     * @return the position where the jmp instruction starts.
     */
    public final int jmp(int jumpTarget, boolean forceDisp32) {
        final int shortSize = JumpType.JMPB.instrSize;
        final int longSize = JumpType.JMP.instrSize;
        // For long jmp, the jmp instruction will cross the jcc-erratum-mitigation-boundary when the
        // current position is between [0x1b, 0x1f]. For short jmp [0x1e, 0x1f], which is covered by
        // the long jmp triggering range.
        if (!forceDisp32) {
            // We first align the next jmp assuming it will be short jmp.
            mitigateJCCErratum(shortSize);
            int pos = position();
            long disp = jumpTarget - pos;
            if (isByte(disp - shortSize)) {
                emitByte(0xEB);
                emitByte((int) ((disp - shortSize) & 0xFF));
                trackJump(JumpType.JMPB, pos);

                return pos;
            }
        }

        mitigateJCCErratum(longSize);
        int pos = position();
        long disp = jumpTarget - pos;
        emitByte(0xE9);
        emitInt((int) (disp - longSize));
        trackJump(JumpType.JMP, pos);
        return pos;
    }

    @Override
    public void halt() {
        hlt();
    }

    @Override
    public final void jmp(Label l) {
        if (l.isBound()) {
            jmp(l.position(), false);
        } else if (canUseShortJump(nextJumpIdx)) {
            jmpb(l);
        } else {
            // By default, forward jumps are always 32-bit displacements, since
            // we can't yet know where the label will be bound. If you're sure that
            // the forward jump will not run beyond 256 bytes, use jmpb to
            // force an 8-bit displacement.
            mitigateJCCErratum(5);
            int pos = position();
            l.addPatchAt(pos, this);
            emitByte(0xE9);
            emitInt(0);
            trackJump(JumpType.JMP, pos);
        }
    }

    public final void jmp(Label l, boolean isShortJmp) {
        if (isShortJmp) {
            jmpb(l);
        } else {
            jmp(l);
        }
    }

    protected final void jmpWithoutAlignment(Register entry) {
        prefix(entry);
        emitByte(0xFF);
        emitModRM(4, entry);
    }

    public void jmp(Register entry) {
        int bytesToEmit = needsRex(entry) ? 3 : 2;
        mitigateJCCErratum(bytesToEmit);
        int beforeJmp = position();
        jmpWithoutAlignment(entry);
        assert beforeJmp + bytesToEmit == position() : beforeJmp + " " + bytesToEmit + " " + position();
    }

    public void jmp(AMD64Address adr) {
        int bytesToEmit = getPrefixInBytes(OperandSize.DWORD, adr) + OPCODE_IN_BYTES + addressInBytes(adr);
        mitigateJCCErratum(bytesToEmit);
        int beforeJmp = position();
        prefix(adr);
        emitByte(0xFF);
        emitOperandHelper(AMD64.rsp, adr, 0);
        assert beforeJmp + bytesToEmit == position() : beforeJmp + " " + bytesToEmit + " " + position();
    }

    /**
     * This method should be synchronized with
     * {@link AMD64BaseAssembler#emitOperandHelper(Register, AMD64Address, int)}}.
     */
    protected int addressInBytes(AMD64Address addr) {
        Register base = addr.getBase();
        Register index = addr.getIndex();
        int disp = addr.getDisplacement();

        if (base.equals(AMD64.rip)) {
            return 5;
        } else if (base.isValid()) {
            final boolean isZeroDisplacement = addr.getDisplacementAnnotation() == null && disp == 0 && !base.equals(rbp) && !base.equals(r13);
            boolean isByteDisplacement = addr.getDisplacementAnnotation() == null && isByte(disp) && !(force4ByteNonZeroDisplacements && disp != 0);
            if (index.isValid()) {
                if (isZeroDisplacement) {
                    return 2;
                } else if (isByteDisplacement) {
                    return 3;
                } else {
                    return 6;
                }
            } else if (base.equals(rsp) || base.equals(r12)) {
                if (disp == 0) {
                    return 2;
                } else if (isByteDisplacement) {
                    return 3;
                } else {
                    return 6;
                }
            } else {
                if (isZeroDisplacement) {
                    return 1;
                } else if (isByteDisplacement) {
                    return 2;
                } else {
                    return 5;
                }
            }
        } else {
            return 6;
        }
    }

    public final void jmpb(Label l) {
        if (force4ByteNonZeroDisplacements) {
            jmp(l);
            return;
        }
        final int shortSize = JumpType.JMPB.instrSize;
        mitigateJCCErratum(shortSize);
        if (l.isBound()) {
            // Displacement is relative to byte just after jmpb instruction
            int pos = position();
            int displacement = l.position() - pos - shortSize;
            GraalError.guarantee(isByte(displacement), "Displacement too large to be encoded as a byte: %d", displacement);
            emitByte(0xEB);
            emitByte(displacement & 0xFF);
            trackJump(JumpType.JMPB, pos);
        } else {
            int pos = position();
            l.addPatchAt(pos, this);
            emitByte(0xEB);
            emitByte(0);
            trackJump(JumpType.JMPB, pos);
        }
    }

    public final void lead(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x8D);
        emitOperandHelper(dst, src, 0);
    }

    public final void leaq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x8D);
        emitOperandHelper(dst, src, 0);
    }

    public final void leave() {
        emitByte(0xC9);
    }

    public final void lock() {
        emitByte(0xF0);
    }

    public final void movapd(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, Register.None, src, OperandSize.PD, P_0F, false);
        emitByte(0x28);
        emitModRM(dst, src);
    }

    public final void movaps(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, Register.None, src, OperandSize.PS, P_0F, false);
        emitByte(0x28);
        emitModRM(dst, src);
    }

    public final void movb(Register dst, AMD64Address src) {
        prefixb(src, dst);
        emitByte(0x8A);
        emitOperandHelper(dst, src, 0);
    }

    public final void movb(AMD64Address dst, int imm8) {
        prefix(dst);
        emitByte(0xC6);
        emitOperandHelper(0, dst, 1);
        emitByte(imm8);
    }

    public final void movb(AMD64Address dst, Register src) {
        assert inRC(CPU, src) : "must have byte register";
        prefixb(dst, src);
        emitByte(0x88);
        emitOperandHelper(src, dst, 0);
    }

    public final void movl(Register dst, int imm32) {
        movl(dst, imm32, false);
    }

    public final void movl(Register dst, int imm32, boolean annotateImm) {
        int insnPos = position();
        prefix(dst);
        emitByte(0xB8 + encode(dst));
        int immPos = position();
        emitInt(imm32);
        int nextInsnPos = position();
        if (annotateImm && codePatchingAnnotationConsumer != null) {
            codePatchingAnnotationConsumer.accept(new OperandDataAnnotation(insnPos, immPos, nextInsnPos - immPos, nextInsnPos));
        }
    }

    public final void movl(Register dst, Register src) {
        prefix(dst, src);
        emitByte(0x8B);
        emitModRM(dst, src);
    }

    public final void movl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x8B);
        emitOperandHelper(dst, src, 0);
    }

    /**
     * @param wide use 4 byte encoding for displacements that would normally fit in a byte
     */
    public final void movl(Register dst, AMD64Address src, boolean wide) {
        prefix(src, dst);
        emitByte(0x8B);
        emitOperandHelper(dst, src, wide, 0);
    }

    public final void movl(AMD64Address dst, int imm32) {
        prefix(dst);
        emitByte(0xC7);
        emitOperandHelper(0, dst, 4);
        emitInt(imm32);
    }

    public final void movl(AMD64Address dst, Register src) {
        prefix(dst, src);
        emitByte(0x89);
        emitOperandHelper(src, dst, 0);
    }

    /**
     * New CPUs require use of movsd and movss to avoid partial register stall when loading from
     * memory. But for old Opteron use movlpd instead of movsd. The selection is done in
     * {@link AMD64MacroAssembler#movdbl(Register, AMD64Address)} and
     * {@link AMD64MacroAssembler#movflt(Register, Register)}.
     */
    public final void movlpd(Register dst, AMD64Address src) {
        assert inRC(XMM, dst);
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0x12);
        emitOperandHelper(dst, src, 0);
    }

    public final void movlhps(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PS, P_0F, false);
        emitByte(0x16);
        emitModRM(dst, src);
    }

    public final void movq(Register dst, AMD64Address src) {
        movq(dst, src, false);
    }

    public final void movq(Register dst, AMD64Address src, boolean force4BytesDisplacement) {
        if (inRC(XMM, dst)) {
            // Insn: MOVQ xmm, r/m64
            // Code: F3 0F 7E /r
            // An alternative instruction would be 66 REX.W 0F 6E /r. We prefer the REX.W free
            // format, because it would allow us to emit 2-bytes-prefixed vex-encoding instruction
            // when applicable.
            simdPrefix(dst, Register.None, src, OperandSize.SS, P_0F, false);
            emitByte(0x7E);
            emitOperandHelper(dst, src, force4BytesDisplacement, 0);
        } else {
            // gpr version of movq
            prefixq(src, dst);
            emitByte(0x8B);
            emitOperandHelper(dst, src, force4BytesDisplacement, 0);
        }
    }

    public final void movq(Register dst, Register src) {
        assert inRC(CPU, dst) && inRC(CPU, src) : src + " " + dst;
        prefixq(dst, src);
        emitByte(0x8B);
        emitModRM(dst, src);
    }

    public final void movq(AMD64Address dst, Register src) {
        if (inRC(XMM, src)) {
            // Insn: MOVQ r/m64, xmm
            // Code: 66 0F D6 /r
            // An alternative instruction would be 66 REX.W 0F 7E /r. We prefer the REX.W free
            // format, because it would allow us to emit 2-bytes-prefixed vex-encoding instruction
            // when applicable.
            simdPrefix(src, Register.None, dst, OperandSize.PD, P_0F, false);
            emitByte(0xD6);
            emitOperandHelper(src, dst, 0);
        } else {
            // gpr version of movq
            prefixq(dst, src);
            emitByte(0x89);
            emitOperandHelper(src, dst, 0);
        }
    }

    public final void movsbl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0xBE);
        emitOperandHelper(dst, src, 0);
    }

    public final void movsbl(Register dst, Register src) {
        prefix(dst, false, src, true);
        emitByte(0x0F);
        emitByte(0xBE);
        emitModRM(dst, src);
    }

    public final void movsbq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0xBE);
        emitOperandHelper(dst, src, 0);
    }

    public final void movsbq(Register dst, Register src) {
        prefixq(dst, src);
        emitByte(0x0F);
        emitByte(0xBE);
        emitModRM(dst, src);
    }

    public final void movsd(Register dst, Register src) {
        SSEOp.MOVSD.emit(this, OperandSize.SD, dst, src);
    }

    public final void movsd(Register dst, AMD64Address src) {
        SSEOp.MOVSD.emit(this, OperandSize.SD, dst, src);
    }

    public final void movsd(AMD64Address dst, Register src) {
        SSEMROp.MOVSD.emit(this, OperandSize.SD, dst, src);
    }

    public final void movss(Register dst, Register src) {
        SSEOp.MOVSS.emit(this, OperandSize.SS, dst, src);
    }

    public final void movss(Register dst, AMD64Address src) {
        SSEOp.MOVSS.emit(this, OperandSize.SS, dst, src);
    }

    public final void movss(AMD64Address dst, Register src) {
        SSEMROp.MOVSS.emit(this, OperandSize.SS, dst, src);
    }

    public final void mulpd(Register dst, Register src) {
        SSEOp.MUL.emit(this, OperandSize.PD, dst, src);
    }

    public final void mulpd(Register dst, AMD64Address src) {
        SSEOp.MUL.emit(this, OperandSize.PD, dst, src);
    }

    public final void mulsd(Register dst, Register src) {
        SSEOp.MUL.emit(this, OperandSize.SD, dst, src);
    }

    public final void mulsd(Register dst, AMD64Address src) {
        SSEOp.MUL.emit(this, OperandSize.SD, dst, src);
    }

    public final void mulss(Register dst, Register src) {
        SSEOp.MUL.emit(this, OperandSize.SS, dst, src);
    }

    public final void movswl(Register dst, Register src) {
        AMD64RMOp.MOVSX.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void movswl(Register dst, AMD64Address src) {
        AMD64RMOp.MOVSX.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void movswq(Register dst, AMD64Address src) {
        AMD64RMOp.MOVSX.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void movw(AMD64Address dst, int imm16) {
        emitByte(0x66); // switch to 16-bit mode
        prefix(dst);
        emitByte(0xC7);
        emitOperandHelper(0, dst, 2);
        emitShort(imm16);
    }

    public final void movw(AMD64Address dst, Register src) {
        emitByte(0x66);
        prefix(dst, src);
        emitByte(0x89);
        emitOperandHelper(src, dst, 0);
    }

    public final void movw(Register dst, AMD64Address src) {
        emitByte(0x66);
        prefix(src, dst);
        emitByte(0x8B);
        emitOperandHelper(dst, src, 0);
    }

    public final void movzbl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0xB6);
        emitOperandHelper(dst, src, 0);
    }

    public final void movzbl(Register dst, Register src) {
        AMD64RMOp.MOVZXB.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void movzbq(Register dst, Register src) {
        AMD64RMOp.MOVZXB.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void movzbq(Register dst, AMD64Address src) {
        AMD64RMOp.MOVZXB.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void movzwl(Register dst, AMD64Address src) {
        AMD64RMOp.MOVZX.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void movzwq(Register dst, AMD64Address src) {
        AMD64RMOp.MOVZX.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void negl(Register dst) {
        NEG.emit(this, OperandSize.DWORD, dst);
    }

    public final void notl(Register dst) {
        NOT.emit(this, OperandSize.DWORD, dst);
    }

    public final void notq(Register dst) {
        NOT.emit(this, OperandSize.QWORD, dst);
    }

    @Override
    public final void ensureUniquePC() {
        nop();
    }

    public final void nop() {
        nop(1);
    }

    public void nop(int count) {
        intelNops(count);
    }

    @SuppressWarnings("fallthrough")
    private void intelNops(int count) {
        //
        // Using multi-bytes nops "0x0F 0x1F [address]" for Intel
        // 1: 0x90
        // 2: 0x66 0x90
        // 3: 0x66 0x66 0x90 (don't use "0x0F 0x1F 0x00" - need patching safe padding)
        // 4: 0x0F 0x1F 0x40 0x00
        // 5: 0x0F 0x1F 0x44 0x00 0x00
        // 6: 0x66 0x0F 0x1F 0x44 0x00 0x00
        // 7: 0x0F 0x1F 0x80 0x00 0x00 0x00 0x00
        // 8: 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00
        // 9: 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00
        // 10: 0x66 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00
        // 11: 0x66 0x66 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00

        // The rest coding is Intel specific - don't use consecutive address nops

        // 12: 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00 0x66 0x66 0x66 0x90
        // 13: 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00 0x66 0x66 0x66 0x90
        // 14: 0x66 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00 0x66 0x66 0x66 0x90
        // 15: 0x66 0x66 0x66 0x0F 0x1F 0x84 0x00 0x00 0x00 0x00 0x00 0x66 0x66 0x66 0x90

        int i = count;
        while (i >= 15) {
            // For Intel don't generate consecutive addess nops (mix with regular nops)
            i -= 15;
            emitByte(0x66);   // size prefix
            emitByte(0x66);   // size prefix
            emitByte(0x66);   // size prefix
            addrNop8();
            emitByte(0x66);   // size prefix
            emitByte(0x66);   // size prefix
            emitByte(0x66);   // size prefix
            emitByte(0x90);
            // nop
        }
        switch (i) {
            case 14:
                emitByte(0x66); // size prefix
                // fall through
            case 13:
                emitByte(0x66); // size prefix
                // fall through
            case 12:
                addrNop8();
                emitByte(0x66); // size prefix
                emitByte(0x66); // size prefix
                emitByte(0x66); // size prefix
                emitByte(0x90);
                // nop
                break;
            case 11:
                emitByte(0x66); // size prefix
                // fall through
            case 10:
                emitByte(0x66); // size prefix
                // fall through
            case 9:
                emitByte(0x66); // size prefix
                // fall through
            case 8:
                addrNop8();
                break;
            case 7:
                addrNop7();
                break;
            case 6:
                emitByte(0x66); // size prefix
                // fall through
            case 5:
                addrNop5();
                break;
            case 4:
                addrNop4();
                break;
            case 3:
                // Don't use "0x0F 0x1F 0x00" - need patching safe padding
                emitByte(0x66); // size prefix
                // fall through
            case 2:
                emitByte(0x66); // size prefix
                // fall through
            case 1:
                emitByte(0x90);
                // nop
                break;
            default:
                assert i == 0 : i;
        }
    }

    public final void orl(Register dst, Register src) {
        OR.rmOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void orl(Register dst, AMD64Address src) {
        OR.rmOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void orl(AMD64Address dst, Register src) {
        OR.mrOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void orl(Register dst, int imm32) {
        OR.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void packuswb(Register dst, Register src) {
        SSEOp.PACKUSWB.emit(this, OperandSize.PD, dst, src);
    }

    public final void packusdw(Register dst, Register src) {
        SSEOp.PACKUSDW.emit(this, OperandSize.PD, dst, src);
    }

    public final void pop(Register dst) {
        prefix(dst);
        emitByte(0x58 + encode(dst));
    }

    public void popfq() {
        emitByte(0x9D);
    }

    public final void ptest(Register dst, Register src) {
        GraalError.guarantee(supports(CPUFeature.SSE4_1), "PTEST requires SSE4.1");
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, Register.None, src, OperandSize.PD, P_0F38, false);
        emitByte(0x17);
        emitModRM(dst, src);
    }

    public final void ptest(Register dst, AMD64Address src) {
        GraalError.guarantee(supports(CPUFeature.SSE4_1), "PTEST requires SSE4.1");
        assert inRC(XMM, dst);
        simdPrefix(dst, Register.None, src, OperandSize.PD, P_0F38, false);
        emitByte(0x17);
        emitOperandHelper(dst, src, 0);
    }

    public final void pcmpeqb(Register dst, Register src) {
        assert supports(CPUFeature.SSE2);
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0x74);
        emitModRM(dst, src);
    }

    public final void pcmpeqb(Register dst, AMD64Address src) {
        assert supports(CPUFeature.SSE2);
        assert inRC(XMM, dst);
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0x74);
        emitOperandHelper(dst, src, 0);
    }

    public final void pcmpeqw(Register dst, Register src) {
        assert supports(CPUFeature.SSE2);
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0x75);
        emitModRM(dst, src);
    }

    public final void pcmpeqw(Register dst, AMD64Address src) {
        assert supports(CPUFeature.SSE2);
        assert inRC(XMM, dst);
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0x75);
        emitOperandHelper(dst, src, 0);
    }

    public final void pcmpeqd(Register dst, Register src) {
        assert supports(CPUFeature.SSE2);
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0x76);
        emitModRM(dst, src);
    }

    public final void pcmpeqd(Register dst, AMD64Address src) {
        assert supports(CPUFeature.SSE2);
        assert inRC(XMM, dst);
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0x76);
        emitOperandHelper(dst, src, 0);
    }

    public final void pminub(Register dst, Register src) {
        SSEOp.PMINUB.emit(this, OperandSize.PD, dst, src);
    }

    public final void pminuw(Register dst, Register src) {
        SSEOp.PMINUW.emit(this, OperandSize.PD, dst, src);
    }

    public final void pminud(Register dst, Register src) {
        SSEOp.PMINUD.emit(this, OperandSize.PD, dst, src);
    }

    public final void pcmpgtb(Register dst, Register src) {
        assert supports(CPUFeature.SSE2);
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0x64);
        emitModRM(dst, src);
    }

    public final void pcmpgtd(Register dst, Register src) {
        assert supports(CPUFeature.SSE2);
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0x66);
        emitModRM(dst, src);
    }

    public final void pcmpestri(Register dst, AMD64Address src, int imm8) {
        assert supports(CPUFeature.SSE4_2);
        assert inRC(XMM, dst);
        simdPrefix(dst, Register.None, src, OperandSize.PD, P_0F3A, false);
        emitByte(0x61);
        emitOperandHelper(dst, src, 0);
        emitByte(imm8);
    }

    public final void pcmpestri(Register dst, Register src, int imm8) {
        assert supports(CPUFeature.SSE4_2);
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, Register.None, src, OperandSize.PD, P_0F3A, false);
        emitByte(0x61);
        emitModRM(dst, src);
        emitByte(imm8);
    }

    public final void pmovmskb(Register dst, Register src) {
        assert supports(CPUFeature.SSE2);
        assert inRC(CPU, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, Register.None, src, OperandSize.PD, P_0F, false);
        emitByte(0xD7);
        emitModRM(dst, src);
    }

    private void pmovSZx(Register dst, AMD64Address src, int op) {
        assert supports(CPUFeature.SSE4_1);
        assert inRC(XMM, dst);
        simdPrefix(dst, Register.None, src, OperandSize.PD, P_0F38, false);
        emitByte(op);
        emitOperandHelper(dst, src, 0);
    }

    private void pmovSZx(Register dst, Register src, int op) {
        assert supports(CPUFeature.SSE4_1);
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, Register.None, src, OperandSize.PD, P_0F38, false);
        emitByte(op);
        emitModRM(dst, src);
    }

    public final void pmovsxbw(Register dst, AMD64Address src) {
        pmovSZx(dst, src, 0x20);
    }

    public final void pmovsxbd(Register dst, AMD64Address src) {
        pmovSZx(dst, src, 0x21);
    }

    public final void pmovsxbq(Register dst, AMD64Address src) {
        pmovSZx(dst, src, 0x22);
    }

    public final void pmovsxwd(Register dst, AMD64Address src) {
        pmovSZx(dst, src, 0x23);
    }

    public final void pmovsxwq(Register dst, AMD64Address src) {
        pmovSZx(dst, src, 0x24);
    }

    public final void pmovsxdq(Register dst, AMD64Address src) {
        pmovSZx(dst, src, 0x25);
    }

    public final void pmovzxbw(Register dst, AMD64Address src) {
        pmovSZx(dst, src, 0x30);
    }

    public final void pmovzxbd(Register dst, AMD64Address src) {
        pmovSZx(dst, src, 0x31);
    }

    public final void pmovzxbq(Register dst, AMD64Address src) {
        pmovSZx(dst, src, 0x32);
    }

    public final void pmovzxwd(Register dst, AMD64Address src) {
        pmovSZx(dst, src, 0x33);
    }

    public final void pmovzxwq(Register dst, AMD64Address src) {
        pmovSZx(dst, src, 0x34);
    }

    public final void pmovzxdq(Register dst, AMD64Address src) {
        pmovSZx(dst, src, 0x35);
    }

    public final void pmovsxbw(Register dst, Register src) {
        pmovSZx(dst, src, 0x20);
    }

    public final void pmovsxbd(Register dst, Register src) {
        pmovSZx(dst, src, 0x21);
    }

    public final void pmovsxbq(Register dst, Register src) {
        pmovSZx(dst, src, 0x22);
    }

    public final void pmovsxwd(Register dst, Register src) {
        pmovSZx(dst, src, 0x23);
    }

    public final void pmovsxwq(Register dst, Register src) {
        pmovSZx(dst, src, 0x24);
    }

    public final void pmovsxdq(Register dst, Register src) {
        pmovSZx(dst, src, 0x25);
    }

    public final void pmovzxbw(Register dst, Register src) {
        pmovSZx(dst, src, 0x30);
    }

    public final void pmovzxbd(Register dst, Register src) {
        pmovSZx(dst, src, 0x31);
    }

    public final void pmovzxbq(Register dst, Register src) {
        pmovSZx(dst, src, 0x32);
    }

    public final void pmovzxwd(Register dst, Register src) {
        pmovSZx(dst, src, 0x33);
    }

    public final void pmovzxwq(Register dst, Register src) {
        pmovSZx(dst, src, 0x34);
    }

    public final void pmovzxdq(Register dst, Register src) {
        pmovSZx(dst, src, 0x35);
    }

    public final void gf2p8affineqb(Register dst, Register src, int imm8) {
        GraalError.guarantee(supports(GFNI), "gf2p8affineqb requires GFNI");
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;

        if (supports(CPUFeature.AVX)) {
            VGF2P8AFFINEQB.emit(this, AVXKind.AVXSize.XMM, dst, dst, src, imm8);
        } else {
            simdPrefix(dst, dst, src, OperandSize.PD, P_0F3A, false);
            emitByte(0xCE);
            emitModRM(dst, src);
            emitByte(imm8);
        }
    }

    public final void vcvtps2ph(Register dst, Register src, int imm8) {
        GraalError.guarantee(supports(F16C), "vcvtps2ph requires F16C");
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;

        VCVTPS2PH.emit(this, AVXKind.AVXSize.XMM, dst, src, imm8);
    }

    public final void vcvtph2ps(Register dst, Register src) {
        GraalError.guarantee(supports(F16C), "vcvtph2ps requires F16C");
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;

        VCVTPH2PS.emit(this, AVXKind.AVXSize.XMM, dst, src);
    }

    public final void push(Register src) {
        assert inRC(CPU, src);
        prefix(src);
        emitByte(0x50 + encode(src));
    }

    public final void push(int imm32) {
        emitByte(0x68);
        emitInt(imm32);
    }

    public void pushfq() {
        emitByte(0x9c);
    }

    public final void paddd(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0xFE);
        emitModRM(dst, src);
    }

    public final void paddd(Register dst, AMD64Address src) {
        assert inRC(XMM, dst);
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0xFE);
        emitOperandHelper(dst, src, 0);
    }

    public final void paddq(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0xD4);
        emitModRM(dst, src);
    }

    public final void pextrb(AMD64Address dst, Register src, int imm8) {
        assert supports(CPUFeature.SSE4_1);
        assert inRC(XMM, src);
        simdPrefix(src, Register.None, dst, OperandSize.PD, P_0F3A, false);
        emitByte(0x14);
        emitOperandHelper(src, dst, 0);
        emitByte(imm8);
    }

    public final void pextrw(Register dst, Register src, int imm8) {
        assert inRC(CPU, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, Register.None, src, OperandSize.PD, P_0F, false);
        emitByte(0xC5);
        emitModRM(dst, src);
        emitByte(imm8);
    }

    public final void pextrw(AMD64Address dst, Register src, int imm8) {
        assert supports(CPUFeature.SSE4_1);
        assert inRC(XMM, src);
        simdPrefix(src, Register.None, dst, OperandSize.PD, P_0F3A, false);
        emitByte(0x15);
        emitOperandHelper(src, dst, 0);
        emitByte(imm8);
    }

    public final void pextrd(AMD64Address dst, Register src, int imm8) {
        assert supports(CPUFeature.SSE4_1);
        assert inRC(XMM, src);
        simdPrefix(src, Register.None, dst, OperandSize.PD, P_0F3A, false);
        emitByte(0x16);
        emitOperandHelper(src, dst, 0);
        emitByte(imm8);
    }

    public final void pextrq(Register dst, Register src, int imm8) {
        assert supports(CPUFeature.SSE4_1);
        assert inRC(CPU, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(src, Register.None, dst, OperandSize.PD, P_0F3A, true);
        emitByte(0x16);
        emitModRM(src, dst);
        emitByte(imm8);
    }

    public final void pextrq(AMD64Address dst, Register src, int imm8) {
        assert supports(CPUFeature.SSE4_1);
        assert inRC(XMM, src);
        simdPrefix(src, Register.None, dst, OperandSize.PD, P_0F3A, true);
        emitByte(0x16);
        emitOperandHelper(src, dst, 0);
        emitByte(imm8);
    }

    public final void pinsrb(Register dst, AMD64Address src, int imm8) {
        assert supports(CPUFeature.SSE4_1);
        assert inRC(XMM, dst);
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F3A, false);
        emitByte(0x20);
        emitOperandHelper(dst, src, 0);
        emitByte(imm8);
    }

    public final void pinsrw(Register dst, Register src, int imm8) {
        assert supports(CPUFeature.SSE2);
        assert inRC(XMM, dst) && inRC(CPU, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0xC4);
        emitModRM(dst, src);
        emitByte(imm8);
    }

    public final void pinsrw(Register dst, AMD64Address src, int imm8) {
        assert inRC(XMM, dst);
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0xC4);
        emitOperandHelper(dst, src, 0);
        emitByte(imm8);
    }

    public final void pinsrd(Register dst, AMD64Address src, int imm8) {
        assert supports(CPUFeature.SSE4_1);
        assert inRC(XMM, dst);
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F3A, false);
        emitByte(0x22);
        emitOperandHelper(dst, src, 0);
        emitByte(imm8);
    }

    public final void pinsrq(Register dst, Register src, int imm8) {
        assert supports(CPUFeature.SSE4_1);
        assert inRC(XMM, dst) && inRC(CPU, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F3A, true);
        emitByte(0x22);
        emitModRM(dst, src);
        emitByte(imm8);
    }

    public final void pinsrq(Register dst, AMD64Address src, int imm8) {
        assert supports(CPUFeature.SSE4_1);
        assert inRC(XMM, dst);
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F3A, true);
        emitByte(0x22);
        emitOperandHelper(dst, src, 0);
        emitByte(imm8);
    }

    public final void por(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0xEB);
        emitModRM(dst, src);
    }

    public final void palignr(Register dst, Register src, int imm8) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F3A, false);
        emitByte(0x0F);
        emitModRM(dst, src);
        emitByte(imm8);
    }

    public final void pblendw(Register dst, Register src, int imm8) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F3A, false);
        emitByte(0x0E);
        emitModRM(dst, src);
        emitByte(imm8);
    }

    public final void pand(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0xDB);
        emitModRM(dst, src);
    }

    public final void pand(Register dst, AMD64Address src) {
        assert inRC(XMM, dst);
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0xDB);
        emitOperandHelper(dst, src, 0);
    }

    public final void pandn(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0xDF);
        emitModRM(dst, src);
    }

    public final void pxor(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0xEF);
        emitModRM(dst, src);
    }

    public final void psllw(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert inRC(XMM, dst);
        // XMM6 is for /6 encoding: 66 0F 71 /6 ib
        simdPrefix(AMD64.xmm6, dst, dst, OperandSize.PD, P_0F, false);
        emitByte(0x71);
        emitModRM(6, dst);
        emitByte(imm8 & 0xFF);
    }

    public final void pslld(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert inRC(XMM, dst);
        // XMM6 is for /6 encoding: 66 0F 72 /6 ib
        simdPrefix(AMD64.xmm6, dst, dst, OperandSize.PD, P_0F, false);
        emitByte(0x72);
        emitModRM(6, dst);
        emitByte(imm8 & 0xFF);
    }

    public final void pslldq(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert inRC(XMM, dst);
        // XMM7 is for /7 encoding: 66 0F 73 /7 ib
        simdPrefix(AMD64.xmm7, dst, dst, OperandSize.PD, P_0F, false);
        emitByte(0x73);
        emitModRM(7, dst);
        emitByte(imm8 & 0xFF);
    }

    public final void psllq(Register dst, Register shift) {
        assert inRC(XMM, dst) && inRC(XMM, shift) : dst + " " + shift;
        simdPrefix(dst, dst, shift, OperandSize.PD, P_0F, false);
        emitByte(0xF3);
        emitModRM(dst, shift);
    }

    public final void psllq(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert inRC(XMM, dst);
        // XMM6 is for /6 encoding: 66 0F 73 /6 ib
        simdPrefix(AMD64.xmm6, dst, dst, OperandSize.PD, P_0F, false);
        emitByte(0x73);
        emitModRM(6, dst);
        emitByte(imm8);
    }

    public final void psrad(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert inRC(XMM, dst);
        // XMM4 is for /4 encoding: 66 0F 72 /4 ib
        simdPrefix(AMD64.xmm4, dst, dst, OperandSize.PD, P_0F, false);
        emitByte(0x72);
        emitModRM(4, dst);
        emitByte(imm8);
    }

    public final void psrlw(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert inRC(XMM, dst);
        // XMM2 is for /2 encoding: 66 0F 72 /2 ib
        simdPrefix(AMD64.xmm2, dst, dst, OperandSize.PD, P_0F, false);
        emitByte(0x71);
        emitModRM(2, dst);
        emitByte(imm8);
    }

    public final void psrld(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert inRC(XMM, dst);
        // XMM2 is for /2 encoding: 66 0F 72 /2 ib
        simdPrefix(AMD64.xmm2, dst, dst, OperandSize.PD, P_0F, false);
        emitByte(0x72);
        emitModRM(2, dst);
        emitByte(imm8);
    }

    public final void psrlq(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert inRC(XMM, dst);
        // XMM2 is for /2 encoding: 66 0F 73 /2 ib
        simdPrefix(AMD64.xmm2, dst, dst, OperandSize.PD, P_0F, false);
        emitByte(0x73);
        emitModRM(2, dst);
        emitByte(imm8);
    }

    public final void psrldq(Register dst, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert inRC(XMM, dst);
        simdPrefix(AMD64.xmm3, dst, dst, OperandSize.PD, P_0F, false);
        emitByte(0x73);
        emitModRM(3, dst);
        emitByte(imm8);
    }

    public final void pshufb(Register dst, Register src) {
        SSEOp.PSHUFB.emit(this, OperandSize.PD, dst, src);
    }

    public final void pshufb(Register dst, AMD64Address src) {
        SSEOp.PSHUFB.emit(this, OperandSize.PD, dst, src);
    }

    public final void pshuflw(Register dst, Register src, int imm8) {
        GraalError.guarantee(supports(CPUFeature.SSE2), "pshuflw requires SSE2");
        assert isUByte(imm8) : "invalid value";
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, Register.None, src, OperandSize.SD, P_0F, false);
        emitByte(0x70);
        emitModRM(dst, src);
        emitByte(imm8);
    }

    public final void pshufd(Register dst, Register src, int imm8) {
        assert isUByte(imm8) : "invalid value";
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, Register.None, src, OperandSize.PD, P_0F, false);
        emitByte(0x70);
        emitModRM(dst, src);
        emitByte(imm8);
    }

    public final void psubusb(Register dst, Register src) {
        SSEOp.PSUBUSB.emit(this, OperandSize.PD, dst, src);
    }

    public final void psubusb(Register dst, AMD64Address src) {
        SSEOp.PSUBUSB.emit(this, OperandSize.PD, dst, src);
    }

    public final void psubusw(Register dst, Register src) {
        SSEOp.PSUBUSW.emit(this, OperandSize.PD, dst, src);
    }

    public final void psubusw(Register dst, AMD64Address src) {
        SSEOp.PSUBUSW.emit(this, OperandSize.PD, dst, src);
    }

    public final void psubd(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0xFA);
        emitModRM(dst, src);
    }

    public final void punpcklbw(Register dst, Register src) {
        assert supports(CPUFeature.SSE2);
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0x60);
        emitModRM(dst, src);
    }

    public final void pclmulqdq(Register dst, Register src, int imm8) {
        assert supports(CPUFeature.CLMUL);
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F3A, false);
        emitByte(0x44);
        emitModRM(dst, src);
        emitByte(imm8);
    }

    public final void vpshufb(Register dst, Register src1, Register src2, AVXKind.AVXSize size) {
        VexRVMOp.VPSHUFB.emit(this, size, dst, src1, src2);
    }

    public final void vpshufd(Register dst, Register src, int imm8, AVXKind.AVXSize size) {
        VexRMIOp.VPSHUFD.emit(this, size, dst, src, imm8);
    }

    public final void vpclmulqdq(Register dst, Register nds, Register src, int imm8) {
        VexRVMIOp.VPCLMULQDQ.emit(this, AVXKind.AVXSize.XMM, dst, nds, src, imm8);
    }

    public final void vpblendd(Register dst, Register nds, Register src, int imm8, AVXKind.AVXSize size) {
        VexRVMIOp.VPBLENDD.emit(this, size, dst, nds, src, imm8);
    }

    public final void vpclmullqlqdq(Register dst, Register nds, Register src) {
        VexRVMIOp.VPCLMULQDQ.emit(this, AVXKind.AVXSize.XMM, dst, nds, src, 0x00);
    }

    public final void vpclmulhqlqdq(Register dst, Register nds, Register src) {
        VexRVMIOp.VPCLMULQDQ.emit(this, AVXKind.AVXSize.XMM, dst, nds, src, 0x01);
    }

    public final void vpclmullqhqdq(Register dst, Register nds, Register src) {
        VexRVMIOp.VPCLMULQDQ.emit(this, AVXKind.AVXSize.XMM, dst, nds, src, 0x10);
    }

    public final void vpclmulhqhqdq(Register dst, Register nds, Register src) {
        VexRVMIOp.VPCLMULQDQ.emit(this, AVXKind.AVXSize.XMM, dst, nds, src, 0x11);
    }

    public final void vpsrlq(Register dst, Register src, int imm8, AVXKind.AVXSize size) {
        VexShiftOp.VPSRLQ.emit(this, size, dst, src, imm8);
    }

    public final void vpsllq(Register dst, Register src, int imm8, AVXKind.AVXSize size) {
        VexShiftOp.VPSLLQ.emit(this, size, dst, src, imm8);
    }

    public final void rcpps(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, Register.None, src, OperandSize.PS, P_0F, false);
        emitByte(0x53);
        emitModRM(dst, src);
    }

    public void ret(int imm16) {
        if (imm16 == 0) {
            mitigateJCCErratum(1);
            emitByte(0xC3);
        } else {
            mitigateJCCErratum(3);
            emitByte(0xC2);
            emitShort(imm16);
        }
    }

    public final void sarl(Register dst, int imm8) {
        prefix(dst);
        assert isShiftCount(imm8 >> 1) : "illegal shift count";
        if (imm8 == 1) {
            emitByte(0xD1);
            emitModRM(7, dst);
        } else {
            emitByte(0xC1);
            emitModRM(7, dst);
            emitByte(imm8);
        }
    }

    public final void sarl(Register dst) {
        // Signed divide dst by 2, CL times.
        prefix(dst);
        emitByte(0xD3);
        emitModRM(7, dst);
    }

    public final void shll(Register dst, int imm8) {
        assert isShiftCount(imm8 >> 1) : "illegal shift count";
        prefix(dst);
        if (imm8 == 1) {
            emitByte(0xD1);
            emitModRM(4, dst);
        } else {
            emitByte(0xC1);
            emitModRM(4, dst);
            emitByte(imm8);
        }
    }

    public final void shll(Register dst) {
        // Multiply dst by 2, CL times.
        prefix(dst);
        emitByte(0xD3);
        emitModRM(4, dst);
    }

    // Insn: SHLX r32a, r/m32, r32b

    public final void shlxl(Register dst, Register src1, Register src2) {
        VexGeneralPurposeRMVOp.SHLX.emit(this, AVXKind.AVXSize.DWORD, dst, src1, src2);
    }

    public final void shrl(Register dst, int imm8) {
        assert isShiftCount(imm8 >> 1) : "illegal shift count";
        prefix(dst);
        emitByte(0xC1);
        emitModRM(5, dst);
        emitByte(imm8);
    }

    public final void shrl(Register dst) {
        // Unsigned divide dst by 2, CL times.
        prefix(dst);
        emitByte(0xD3);
        emitModRM(5, dst);
    }

    public final void subl(AMD64Address dst, int imm32) {
        SUB.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void subl(Register dst, int imm32) {
        SUB.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void subl(Register dst, Register src) {
        SUB.rmOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void subpd(Register dst, Register src) {
        SSEOp.SUB.emit(this, OperandSize.PD, dst, src);
    }

    public final void subsd(Register dst, Register src) {
        SSEOp.SUB.emit(this, OperandSize.SD, dst, src);
    }

    public final void subsd(Register dst, AMD64Address src) {
        SSEOp.SUB.emit(this, OperandSize.SD, dst, src);
    }

    public final void testl(Register dst, int imm32) {
        // not using emitArith because test
        // doesn't support sign-extension of
        // 8bit operands
        if (dst.encoding == 0) {
            emitByte(0xA9);
            emitInt(imm32);
        } else {
            AMD64MIOp.TEST.emit(this, OperandSize.DWORD, dst, imm32);
        }
    }

    public final void testl(Register dst, Register src) {
        AMD64RMOp.TEST.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void testl(Register dst, AMD64Address src) {
        AMD64RMOp.TEST.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void unpckhpd(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0x15);
        emitModRM(dst, src);
    }

    public final void unpcklpd(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0x14);
        emitModRM(dst, src);
    }

    public final void xorb(Register dst, AMD64Address src) {
        XOR.byteRmOp.emit(this, OperandSize.BYTE, dst, src);
    }

    public final void xorl(Register dst, Register src) {
        XOR.rmOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void xorl(Register dst, int imm32) {
        XOR.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void xorq(Register dst, Register src) {
        XOR.rmOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void xorpd(Register dst, Register src) {
        SSEOp.XOR.emit(this, OperandSize.PD, dst, src);
    }

    /**
     * Caller needs to ensure that loading 128-bit memory from src won't cause a segment fault.
     * E.g., constants stored into the data section should be aligned to 16 bytes.
     */
    public final void xorpd(Register dst, AMD64Address src) {
        SSEOp.XOR.emit(this, OperandSize.PD, dst, src);
    }

    public final void xorps(Register dst, Register src) {
        SSEOp.XOR.emit(this, OperandSize.PS, dst, src);
    }

    /**
     * Caller needs to ensure that loading 128-bit memory from src won't cause a segment fault.
     * E.g., constants stored into the data section should be aligned to 16 bytes.
     */
    public final void xorps(Register dst, AMD64Address src) {
        SSEOp.XOR.emit(this, OperandSize.PS, dst, src);
    }

    public final void ucomiss(Register dst, Register src) {
        SSEOp.UCOMIS.emit(this, OperandSize.PS, dst, src);
    }

    public final void ucomisd(Register dst, Register src) {
        SSEOp.UCOMIS.emit(this, OperandSize.PD, dst, src);
    }

    public final void decl(Register dst) {
        // Use two-byte form (one-byte form is a REX prefix in 64-bit mode)
        DEC.emit(this, OperandSize.DWORD, dst);
    }

    public final void incl(Register dst) {
        // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
        INC.emit(this, OperandSize.DWORD, dst);
    }

    public final void imull(Register dst, Register src) {
        IMUL.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void addq(Register dst, int imm32) {
        ADD.getMIOpcode(OperandSize.QWORD, isByte(imm32)).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void addq(AMD64Address dst, int imm32) {
        ADD.getMIOpcode(OperandSize.QWORD, isByte(imm32)).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void addq(Register dst, Register src) {
        ADD.rmOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void addq(Register dst, AMD64Address src) {
        ADD.rmOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void addq(AMD64Address dst, Register src) {
        ADD.mrOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void adcq(Register dst, int imm32) {
        ADC.getMIOpcode(OperandSize.QWORD, isByte(imm32)).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void adcxq(Register dst, Register src) {
        ADCX.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void adoxq(Register dst, Register src) {
        ADOX.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void andq(Register dst, int imm32) {
        AND.getMIOpcode(OperandSize.QWORD, isByte(imm32)).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void andq(Register dst, AMD64Address src) {
        AND.getRMOpcode(OperandSize.QWORD).emit(this, OperandSize.QWORD, dst, src);
    }

    public final void andq(Register dst, Register src) {
        AND.getRMOpcode(OperandSize.QWORD).emit(this, OperandSize.QWORD, dst, src);
    }

    public final void mulq(Register src) {
        MUL.emit(this, OperandSize.QWORD, src);
    }

    public final void mulxq(Register dst1, Register dst2, Register src) {
        MULX.emit(this, AVXKind.AVXSize.QWORD, dst1, dst2, src);
    }

    public final void roll(Register dst, int imm8) {
        GraalError.guarantee(isByte(imm8), "only byte immediate is supported");
        ROL.miOp.emit(this, OperandSize.DWORD, dst, (byte) imm8);
    }

    public final void rorq(Register dst, int imm8) {
        GraalError.guarantee(isByte(imm8), "only byte immediate is supported");
        ROR.miOp.emit(this, OperandSize.QWORD, dst, (byte) imm8);
    }

    public final void rorxl(Register dst, Register src, int imm8) {
        RORXL.emit(this, AVXKind.AVXSize.QWORD, dst, src, (byte) imm8);
    }

    public final void rorxq(Register dst, Register src, int imm8) {
        RORXQ.emit(this, AVXKind.AVXSize.QWORD, dst, src, (byte) imm8);
    }

    public final void rclq(Register dst, int imm8) {
        GraalError.guarantee(isByte(imm8), "only byte immediate is supported");
        RCL.miOp.emit(this, OperandSize.QWORD, dst, (byte) imm8);
    }

    public final void rcrq(Register dst, int imm8) {
        GraalError.guarantee(isByte(imm8), "only byte immediate is supported");
        RCR.miOp.emit(this, OperandSize.QWORD, dst, (byte) imm8);
    }

    public final void bsrq(Register dst, Register src) {
        prefixq(dst, src);
        emitByte(0x0F);
        emitByte(0xBD);
        emitModRM(dst, src);
    }

    public final void bswapq(Register reg) {
        prefixq(reg);
        emitByte(0x0F);
        emitByte(0xC8 + encode(reg));
    }

    public final void cdqq() {
        rexw();
        emitByte(0x99);
    }

    public final void repStosb() {
        emitByte(0xf3);
        rexw();
        emitByte(0xaa);
    }

    public final void repStosq() {
        emitByte(0xf3);
        rexw();
        emitByte(0xab);
    }

    public final void cmovq(ConditionFlag cc, Register dst, Register src) {
        prefixq(dst, src);
        emitByte(0x0F);
        emitByte(0x40 | cc.getValue());
        emitModRM(dst, src);
    }

    public final void setb(ConditionFlag cc, Register dst) {
        prefix(dst, true);
        emitByte(0x0F);
        emitByte(0x90 | cc.getValue());
        emitModRM(0, dst);
    }

    public final void cmovq(ConditionFlag cc, Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0x40 | cc.getValue());
        emitOperandHelper(dst, src, 0);
    }

    public final void cmpq(Register dst, int imm32) {
        CMP.getMIOpcode(OperandSize.QWORD, isByte(imm32)).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void cmpq(Register dst, Register src) {
        CMP.rmOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void cmpq(Register dst, AMD64Address src) {
        CMP.rmOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void cmpxchgq(Register reg, AMD64Address adr) {
        prefixq(adr, reg);
        emitByte(0x0F);
        emitByte(0xB1);
        emitOperandHelper(reg, adr, 0);
    }

    public final void cvtdq2pd(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, Register.None, src, OperandSize.SS, P_0F, false);
        emitByte(0xE6);
        emitModRM(dst, src);
    }

    public final void cvtsi2sdq(Register dst, Register src) {
        SSEOp.CVTSI2SD.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void cvttss2sil(Register dst, Register src) {
        SSEOp.CVTTSS2SI.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void cvttsd2siq(Register dst, Register src) {
        SSEOp.CVTTSD2SI.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void cvttpd2dq(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, Register.None, src, OperandSize.PD, P_0F, false);
        emitByte(0xE6);
        emitModRM(dst, src);
    }

    public final void decq(Register dst) {
        // Use two-byte form (one-byte form is a REX prefix in 64-bit mode)
        DEC.emit(this, OperandSize.QWORD, dst);
    }

    public final void decq(AMD64Address dst) {
        DEC.emit(this, OperandSize.QWORD, dst);
    }

    public final void imulq(Register dst, Register src) {
        IMUL.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void incq(Register dst) {
        // Don't use it directly. Use Macroincrementq() instead.
        // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
        INC.emit(this, OperandSize.QWORD, dst);
    }

    public final void incq(AMD64Address dst) {
        INC.emit(this, OperandSize.QWORD, dst);
    }

    public final void movq(Register dst, long imm64) {
        movq(dst, imm64, false);
    }

    public final void movq(Register dst, long imm64, boolean annotateImm) {
        int insnPos = position();
        prefixq(dst);
        emitByte(0xB8 + encode(dst));
        int immPos = position();
        emitLong(imm64);
        int nextInsnPos = position();
        if (annotateImm && codePatchingAnnotationConsumer != null) {
            codePatchingAnnotationConsumer.accept(new OperandDataAnnotation(insnPos, immPos, nextInsnPos - immPos, nextInsnPos));
        }
    }

    public final void movslq(Register dst, int imm32) {
        prefixq(dst);
        emitByte(0xC7);
        emitModRM(0, dst);
        emitInt(imm32);
    }

    public final void movdq(Register dst, AMD64Address src) {
        SSEOp.MOVQ.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void movdq(AMD64Address dst, Register src) {
        SSEMROp.MOVQ.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void movdq(Register dst, Register src) {
        if (inRC(XMM, dst) && inRC(CPU, src)) {
            SSEOp.MOVQ.emit(this, OperandSize.QWORD, dst, src);
        } else if (inRC(XMM, src) && inRC(CPU, dst)) {
            SSEMROp.MOVQ.emit(this, OperandSize.QWORD, dst, src);
        } else {
            throw new InternalError("should not reach here");
        }
    }

    public final void movdl(Register dst, Register src) {
        if (inRC(XMM, dst) && inRC(CPU, src)) {
            SSEOp.MOVD.emit(this, OperandSize.DWORD, dst, src);
        } else if (inRC(XMM, src) && inRC(CPU, dst)) {
            SSEMROp.MOVD.emit(this, OperandSize.DWORD, dst, src);
        } else {
            throw new InternalError("should not reach here");
        }
    }

    public final void movdl(Register dst, AMD64Address src) {
        SSEOp.MOVD.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void movddup(Register dst, Register src) {
        assert supports(CPUFeature.SSE3);
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, Register.None, src, OperandSize.SD, P_0F, false);
        emitByte(0x12);
        emitModRM(dst, src);
    }

    public final void movdqu(Register dst, AMD64Address src) {
        assert inRC(XMM, dst);
        simdPrefix(dst, Register.None, src, OperandSize.SS, P_0F, false);
        emitByte(0x6F);
        emitOperandHelper(dst, src, 0);
    }

    public final void movdqu(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, Register.None, src, OperandSize.SS, P_0F, false);
        emitByte(0x6F);
        emitModRM(dst, src);
    }

    // Insn: VMOVDQU xmm2/m128, xmm1

    public final void movdqu(AMD64Address dst, Register src) {
        assert inRC(XMM, src);
        // Code: VEX.128.F3.0F.WIG 7F /r
        simdPrefix(src, Register.None, dst, OperandSize.SS, P_0F, false);
        emitByte(0x7F);
        emitOperandHelper(src, dst, 0);
    }

    public final void movdqa(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, Register.None, src, OperandSize.PD, P_0F, false);
        emitByte(0x6F);
        emitModRM(dst, src);
    }

    public final void movslq(AMD64Address dst, int imm32) {
        prefixq(dst);
        emitByte(0xC7);
        emitOperandHelper(0, dst, 4);
        emitInt(imm32);
    }

    public final void movslq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x63);
        emitOperandHelper(dst, src, 0);
    }

    public final void movslq(Register dst, Register src) {
        prefixq(dst, src);
        emitByte(0x63);
        emitModRM(dst, src);
    }

    public final void negq(Register dst) {
        prefixq(dst);
        emitByte(0xF7);
        emitModRM(3, dst);
    }

    public final void orq(Register dst, Register src) {
        OR.rmOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void orq(Register dst, AMD64Address src) {
        OR.rmOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void orq(Register dst, int imm32) {
        OR.getMIOpcode(OperandSize.QWORD, isByte(imm32)).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void shlq(Register dst, int imm8) {
        assert isShiftCount(imm8 >> 1) : "illegal shift count";
        prefixq(dst);
        if (imm8 == 1) {
            emitByte(0xD1);
            emitModRM(4, dst);
        } else {
            emitByte(0xC1);
            emitModRM(4, dst);
            emitByte(imm8);
        }
    }

    public final void shlq(Register dst) {
        // Multiply dst by 2, CL times.
        prefixq(dst);
        emitByte(0xD3);
        emitModRM(4, dst);
    }

    public final void shrq(Register dst, int imm8) {
        assert isShiftCount(imm8 >> 1) : "illegal shift count";
        prefixq(dst);
        if (imm8 == 1) {
            emitByte(0xD1);
            emitModRM(5, dst);
        } else {
            emitByte(0xC1);
            emitModRM(5, dst);
            emitByte(imm8);
        }
    }

    public final void shrq(Register dst) {
        prefixq(dst);
        emitByte(0xD3);
        // Unsigned divide dst by 2, CL times.
        emitModRM(5, dst);
    }

    public final void sarq(Register dst, int imm8) {
        assert isShiftCount(imm8 >> 1) : "illegal shift count";
        prefixq(dst);
        if (imm8 == 1) {
            emitByte(0xD1);
            emitModRM(7, dst);
        } else {
            emitByte(0xC1);
            emitModRM(7, dst);
            emitByte(imm8);
        }
    }

    public final void sarq(Register dst) {
        // signed divide dst by 2, CL times.
        prefixq(dst);
        emitByte(0xD3);
        emitModRM(7, dst);
    }

    public final void sbbq(Register dst, Register src) {
        SBB.rmOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void subq(Register dst, int imm32) {
        SUB.getMIOpcode(OperandSize.QWORD, isByte(imm32)).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void subq(AMD64Address dst, int imm32) {
        SUB.getMIOpcode(OperandSize.QWORD, isByte(imm32)).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void subqWide(Register dst, int imm32) {
        // don't use the sign-extending version, forcing a 32-bit immediate
        SUB.getMIOpcode(OperandSize.QWORD, false).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void subq(Register dst, Register src) {
        SUB.rmOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void sqrtsd(Register dst, Register src) {
        SSEOp.SQRT.emit(this, OperandSize.SD, dst, src);
    }

    public final void testq(Register dst, Register src) {
        AMD64RMOp.TEST.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void testq(Register dst, AMD64Address src) {
        AMD64RMOp.TEST.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void btrq(Register src, int imm8) {
        prefixq(src);
        emitByte(0x0F);
        emitByte(0xBA);
        emitModRM(6, src);
        emitByte(imm8);
    }

    public final void xaddb(AMD64Address dst, Register src) {
        prefixb(dst, src);
        emitByte(0x0F);
        emitByte(0xC0);
        emitOperandHelper(src, dst, 0);
    }

    public final void xaddw(AMD64Address dst, Register src) {
        emitByte(0x66); // Switch to 16-bit mode.
        prefix(dst, src);
        emitByte(0x0F);
        emitByte(0xC1);
        emitOperandHelper(src, dst, 0);
    }

    public final void xaddl(AMD64Address dst, Register src) {
        prefix(dst, src);
        emitByte(0x0F);
        emitByte(0xC1);
        emitOperandHelper(src, dst, 0);
    }

    public final void xaddq(AMD64Address dst, Register src) {
        prefixq(dst, src);
        emitByte(0x0F);
        emitByte(0xC1);
        emitOperandHelper(src, dst, 0);
    }

    public final void xchgb(Register dst, AMD64Address src) {
        prefixb(src, dst);
        emitByte(0x86);
        emitOperandHelper(dst, src, 0);
    }

    public final void xchgw(Register dst, AMD64Address src) {
        emitByte(0x66);
        prefix(src, dst);
        emitByte(0x87);
        emitOperandHelper(dst, src, 0);
    }

    public final void xchgl(Register dst, AMD64Address src) {
        prefix(src, dst);
        emitByte(0x87);
        emitOperandHelper(dst, src, 0);
    }

    public final void xchgq(Register dst, AMD64Address src) {
        prefixq(src, dst);
        emitByte(0x87);
        emitOperandHelper(dst, src, 0);
    }

    public final void sha1msg1(Register dst, Register src) {
        SHA1MSG1.emit(this, OperandSize.PS, dst, src);
    }

    public final void sha1msg2(Register dst, Register src) {
        SHA1MSG2.emit(this, OperandSize.PS, dst, src);
    }

    public final void sha1nexte(Register dst, Register src) {
        SHA1NEXTE.emit(this, OperandSize.PS, dst, src);
    }

    public final void sha1rnds4(Register dst, Register src, int imm8) {
        SHA1RNDS4.emit(this, OperandSize.PS, dst, src, imm8);
    }

    public final void sha256msg1(Register dst, Register src) {
        SHA256MSG1.emit(this, OperandSize.PS, dst, src);
    }

    public final void sha256msg2(Register dst, Register src) {
        SHA256MSG2.emit(this, OperandSize.PS, dst, src);
    }

    public final void sha256rnds2(Register dst, Register src) {
        SHA256RNDS2.emit(this, OperandSize.PS, dst, src);
    }

    public final void membar(int barriers) {
        if (isTargetMP()) {
            // We only have to handle StoreLoad
            if ((barriers & STORE_LOAD) != 0) {
                // All usable chips support "locked" instructions which suffice
                // as barriers, and are much faster than the alternative of
                // using cpuid instruction. We use here a locked add [rsp],0.
                // This is conveniently otherwise a no-op except for blowing
                // flags.
                // Any change to this code may need to revisit other places in
                // the code where this idiom is used, in particular the
                // orderAccess code.
                lock();
                addl(new AMD64Address(AMD64.rsp, 0), 0); // Assert the lock# signal here
            }
        }
    }

    @Override
    protected final void patchJumpTarget(int branch, int branchTarget) {
        int op = getByte(branch);
        // @formatter:off
        assert op == 0xE8 // call
                        || op == 0x00 // jump table entry
                        || op == 0xE9 // jmp
                        || op == 0xEB // short jmp
                        || (op & 0xF0) == 0x70 // short jcc
                        || op == 0x0F && (getByte(branch + 1) & 0xF0) == 0x80 // jcc
                        : "Invalid opcode at patch point branch=" + branch + ", branchTarget=" + branchTarget + ", op=" + op;
        // @formatter:on

        if (op == 0x00) {
            int offsetToJumpTableBase = getShort(branch + 1);
            int jumpTableBase = branch - offsetToJumpTableBase;
            int imm32 = branchTarget - jumpTableBase;
            emitInt(imm32, branch);
        } else if (op == 0xEB || (op & 0xF0) == 0x70) {

            // short offset operators (jmp and jcc)
            final int imm8 = branchTarget - (branch + 2);
            /*
             * Since a wrongly patched short branch can potentially lead to working but really bad
             * behaving code we should always fail with an exception instead of having an assert.
             */
            if (!isByte(imm8)) {
                throw new BranchTargetOutOfBoundsException(true, "Displacement too large to be encoded as a byte: %d", imm8);
            }
            emitByte(imm8, branch + 1);

        } else {

            int off = 1;
            if (op == 0x0F) {
                off = 2;
            }

            int imm32 = branchTarget - (branch + 4 + off);
            emitInt(imm32, branch + off);
        }
    }

    public void nullCheck(AMD64Address address) {
        testl(AMD64.rax, address);
    }

    @Override
    public void align(int modulus) {
        align(modulus, position());
    }

    /**
     * Ensure that the code at {@code target} bytes offset from the current {@link #position()} is
     * aligned according to {@code modulus}.
     */
    public void align(int modulus, int target) {
        if (target % modulus != 0) {
            nop(modulus - (target % modulus));
        }
    }

    /**
     * Emits a direct call instruction. Note that the actual call target is not specified, because
     * all calls need patching anyway. Therefore, 0 is emitted as the call target, and the user is
     * responsible to add the call address to the appropriate patching tables.
     */
    protected final void call() {
        annotatePatchingImmediate(1, 4);
        emitByte(0xE8);
        emitInt(0);
    }

    public final void call(Label l) {
        if (l.isBound()) {
            emitByte(0xE8);
            emitInt(l.position());
        } else {
            l.addPatchAt(position(), this);
            emitByte(0xE8);
            emitInt(0);
        }
    }

    public final void call(Register src) {
        prefix(src);
        emitByte(0xFF);
        emitModRM(2, src);
    }

    public final void endbranch() {
        emitByte(0xf3);
        emitByte(0x0f);
        emitByte(0x1e);
        emitByte(0xfa);
    }

    public final void int3() {
        emitByte(0xCC);
    }

    public final void pause() {
        emitByte(0xF3);
        emitByte(0x90);
    }

    private void emitx87(int b1, int b2, int i) {
        assert 0 <= i && i < 8 : "illegal stack offset";
        emitByte(b1);
        emitByte(b2 + i);
    }

    public final void fldd(AMD64Address src) {
        emitByte(0xDD);
        emitOperandHelper(0, src, 0);
    }

    public final void flds(AMD64Address src) {
        emitByte(0xD9);
        emitOperandHelper(0, src, 0);
    }

    public final void fldln2() {
        emitByte(0xD9);
        emitByte(0xED);
    }

    public final void fldlg2() {
        emitByte(0xD9);
        emitByte(0xEC);
    }

    public final void fyl2x() {
        emitByte(0xD9);
        emitByte(0xF1);
    }

    public final void fstps(AMD64Address src) {
        emitByte(0xD9);
        emitOperandHelper(3, src, 0);
    }

    public final void fstpd(AMD64Address src) {
        emitByte(0xDD);
        emitOperandHelper(3, src, 0);
    }

    private void emitFPUArith(int b1, int b2, int i) {
        assert 0 <= i && i < 8 : "illegal FPU register: " + i;
        emitByte(b1);
        emitByte(b2 + i);
    }

    public void ffree(int i) {
        emitFPUArith(0xDD, 0xC0, i);
    }

    public void fincstp() {
        emitByte(0xD9);
        emitByte(0xF7);
    }

    public void fxch(int i) {
        emitFPUArith(0xD9, 0xC8, i);
    }

    public void fnstswAX() {
        emitByte(0xDF);
        emitByte(0xE0);
    }

    public void fwait() {
        emitByte(0x9B);
    }

    public void fprem() {
        emitByte(0xD9);
        emitByte(0xF8);
    }

    public final void fsin() {
        emitByte(0xD9);
        emitByte(0xFE);
    }

    public final void fcos() {
        emitByte(0xD9);
        emitByte(0xFF);
    }

    public final void fptan() {
        emitByte(0xD9);
        emitByte(0xF2);
    }

    public final void fstp(int i) {
        emitx87(0xDD, 0xD8, i);
    }

    @Override
    public AMD64Address makeAddress(int transferSize, Register base, int displacement) {
        return makeAddress(base, displacement);
    }

    public AMD64Address makeAddress(Register base, int displacement) {
        return new AMD64Address(base, displacement);
    }

    @Override
    public AMD64Address getPlaceholder(int instructionStartPosition) {
        return new AMD64Address(AMD64.rip, Register.None, Stride.S1, 0, null, instructionStartPosition);
    }

    private void prefetchPrefix(AMD64Address src) {
        prefix(src);
        emitByte(0x0F);
    }

    public void prefetchnta(AMD64Address src) {
        prefetchPrefix(src);
        emitByte(0x18);
        emitOperandHelper(0, src, 0);
    }

    void prefetchr(AMD64Address src) {
        assert supports(CPUFeature.AMD_3DNOW_PREFETCH);
        prefetchPrefix(src);
        emitByte(0x0D);
        emitOperandHelper(0, src, 0);
    }

    public void prefetcht0(AMD64Address src) {
        assert supports(CPUFeature.SSE);
        prefetchPrefix(src);
        emitByte(0x18);
        emitOperandHelper(1, src, 0);
    }

    public void prefetcht1(AMD64Address src) {
        assert supports(CPUFeature.SSE);
        prefetchPrefix(src);
        emitByte(0x18);
        emitOperandHelper(2, src, 0);
    }

    public void prefetcht2(AMD64Address src) {
        assert supports(CPUFeature.SSE);
        prefix(src);
        emitByte(0x0f);
        emitByte(0x18);
        emitOperandHelper(3, src, 0);
    }

    public void prefetchw(AMD64Address src) {
        assert supports(CPUFeature.AMD_3DNOW_PREFETCH);
        prefix(src);
        emitByte(0x0f);
        emitByte(0x0D);
        emitOperandHelper(1, src, 0);
    }

    public void rdtsc() {
        emitByte(0x0F);
        emitByte(0x31);
    }

    public void rdtscp() {
        emitByte(0x0F);
        emitByte(0x01);
        emitByte(0xF9);
    }

    public void rdpid(Register dst) {
        // GR-43733: Replace string by feature when we remove support for Java 17
        assert supports("RDPID");
        emitByte(0xF3);
        prefix(dst);
        emitByte(0x0F);
        emitByte(0xC7);
        emitModRM(7, dst);
    }

    /**
     * Emits an instruction which is considered to be illegal. This is used if we deliberately want
     * to crash the program (debugging etc.).
     */
    public void illegal() {
        emitByte(0x0f);
        emitByte(0x0b);
    }

    public void lfence() {
        emitByte(0x0f);
        emitByte(0xae);
        emitByte(0xe8);
    }

    public void sfence() {
        assert supports(CPUFeature.SSE2);
        emitByte(0x0f);
        emitByte(0xae);
        emitByte(0xf8);
    }

    public void clflush(AMD64Address adr) {
        prefix(adr);
        // opcode family is 0x0F 0xAE
        emitByte(0x0f);
        emitByte(0xae);
        // extended opcode byte is 7
        emitOperandHelper(7, adr, 0);
    }

    public void wrpkru() {
        emitByte(0x0F);
        emitByte(0x01);
        emitByte(0xEF);
    }

    public void rdpkru() {
        emitByte(0x0F);
        emitByte(0x01);
        emitByte(0xEE);
    }

    public final void vpaddd(Register dst, Register nds, Register src, AVXKind.AVXSize size) {
        VexRVMOp.VPADDD.emit(this, size, dst, nds, src);
    }

    public final void vpaddd(Register dst, Register nds, AMD64Address src, AVXKind.AVXSize size) {
        VexRVMOp.VPADDD.emit(this, size, dst, nds, src);
    }

    public final void vpaddq(Register dst, Register nds, Register src, AVXKind.AVXSize size) {
        VexRVMOp.VPADDQ.emit(this, size, dst, nds, src);
    }

    public final void vpaddq(Register dst, Register nds, AMD64Address src, AVXKind.AVXSize size) {
        VexRVMOp.VPADDQ.emit(this, size, dst, nds, src);
    }

    public final void vpand(Register dst, Register nds, Register src, AVXKind.AVXSize size) {
        VexRVMOp.VPAND.emit(this, size, dst, nds, src);
    }

    public final void vpandn(Register dst, Register nds, Register src) {
        VexRVMOp.VPANDN.emit(this, AVXKind.AVXSize.YMM, dst, nds, src);
    }

    public final void vpor(Register dst, Register nds, Register src, AVXKind.AVXSize size) {
        VexRVMOp.VPOR.emit(this, size, dst, nds, src);
    }

    public final void vptest(Register dst, Register src, AVXKind.AVXSize size) {
        VexRMOp.VPTEST.emit(this, size, dst, src);
    }

    public final void vpxor(Register dst, Register nds, Register src, AVXKind.AVXSize size) {
        VexRVMOp.VPXOR.emit(this, size, dst, nds, src);
    }

    public final void vpxor(Register dst, Register nds, AMD64Address src, AVXKind.AVXSize size) {
        VexRVMOp.VPXOR.emit(this, size, dst, nds, src);
    }

    public final void vpsllw(Register dst, Register src, int imm8) {
        VexShiftOp.VPSLLW.emit(this, AVXKind.AVXSize.YMM, dst, src, imm8);
    }

    public final void vpsrlw(Register dst, Register src, int imm8) {
        VexShiftOp.VPSRLW.emit(this, AVXKind.AVXSize.YMM, dst, src, imm8);
    }

    public final void vpslld(Register dst, Register src, int imm8, AVXKind.AVXSize size) {
        VexShiftOp.VPSLLD.emit(this, size, dst, src, imm8);
    }

    public final void vpslldq(Register dst, Register src, int imm8, AVXKind.AVXSize size) {
        VexShiftImmOp.VPSLLDQ.emit(this, size, dst, src, imm8);
    }

    public final void vpsrld(Register dst, Register src, int imm8, AVXKind.AVXSize size) {
        VexShiftOp.VPSRLD.emit(this, size, dst, src, imm8);
    }

    public final void vpsrldq(Register dst, Register src, int imm8, AVXKind.AVXSize size) {
        VexShiftImmOp.VPSRLDQ.emit(this, size, dst, src, imm8);
    }

    public final void vpcmpeqb(Register dst, Register src1, Register src2) {
        VexRVMOp.VPCMPEQB.emit(this, AVXKind.AVXSize.YMM, dst, src1, src2);
    }

    public final void vpcmpeqw(Register dst, Register src1, Register src2) {
        VexRVMOp.VPCMPEQW.emit(this, AVXKind.AVXSize.YMM, dst, src1, src2);
    }

    public final void vpcmpeqd(Register dst, Register src1, Register src2) {
        VexRVMOp.VPCMPEQD.emit(this, AVXKind.AVXSize.YMM, dst, src1, src2);
    }

    public final void vpmovmskb(Register dst, Register src) {
        VexRMOp.VPMOVMSKB.emit(this, AVXKind.AVXSize.YMM, dst, src);
    }

    public final void vmovdqu(Register dst, AMD64Address src) {
        VexMoveOp.VMOVDQU32.emit(this, AVXKind.AVXSize.YMM, dst, src);
    }

    public final void vmovdqu(Register dst, Register src) {
        VexMoveOp.VMOVDQU32.emit(this, AVXKind.AVXSize.YMM, dst, src);
    }

    public final void vmovdqu(AMD64Address dst, Register src) {
        assert inRC(XMM, src);
        VexMoveOp.VMOVDQU32.emit(this, AVXKind.AVXSize.YMM, dst, src);
    }

    public final void vmovdqu64(Register dst, AMD64Address src) {
        VexMoveOp.VMOVDQU64.emit(this, AVXKind.AVXSize.ZMM, dst, src);
    }

    public final void vmovdqu64(AMD64Address dst, Register src) {
        assert inRC(XMM, src);
        VexMoveOp.VMOVDQU64.emit(this, AVXKind.AVXSize.ZMM, dst, src);
    }

    public final void vpmovzxbw(Register dst, AMD64Address src) {
        assert supports(CPUFeature.AVX2);
        VexRMOp.VPMOVZXBW.emit(this, AVXKind.AVXSize.YMM, dst, src);
    }

    public final void vpalignr(Register dst, Register nds, Register src, int imm8, AVXKind.AVXSize size) {
        VexRVMIOp.VPALIGNR.emit(this, size, dst, nds, src, imm8);
    }

    public final void vperm2f128(Register dst, Register nds, Register src, int imm8) {
        VexRVMIOp.VPERM2F128.emit(this, AVXKind.AVXSize.YMM, dst, nds, src, imm8);
    }

    public final void vperm2i128(Register dst, Register nds, Register src, int imm8) {
        VexRVMIOp.VPERM2I128.emit(this, AVXKind.AVXSize.YMM, dst, nds, src, imm8);
    }

    public final void vzeroupper() {
        emitVEX(VEXPrefixConfig.L128, VEXPrefixConfig.P_, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0, 0, true);
        emitByte(0x77);
    }

    public final void aesenc(Register dst, Register src) {
        assert supports(CPUFeature.AES);
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F38, false);
        emitByte(0xDC);
        emitModRM(dst, src);
    }

    public final void aesenclast(Register dst, Register src) {
        assert supports(CPUFeature.AES);
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F38, false);
        emitByte(0xDD);
        emitModRM(dst, src);
    }

    public final void aesdec(Register dst, Register src) {
        assert supports(CPUFeature.AES);
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F38, false);
        emitByte(0xDE);
        emitModRM(dst, src);
    }

    public final void aesdeclast(Register dst, Register src) {
        assert supports(CPUFeature.AES);
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F38, false);
        emitByte(0xDF);
        emitModRM(dst, src);
    }

    // Insn: KORTESTD k1, k2

    // This instruction produces ZF or CF flags
    public final void kortestd(Register src1, Register src2) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, src1) && inRC(MASK, src2) : src1 + " " + src2;
        // Code: VEX.L0.66.0F.W1 98 /r
        vexPrefix(src1, Register.None, src2, AVXKind.AVXSize.XMM, VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, VEXPrefixConfig.W1, true);
        emitByte(0x98);
        emitModRM(src1, src2);
    }

    // Insn: KORTESTQ k1, k2

    // This instruction produces ZF or CF flags
    public final void kortestq(Register src1, Register src2) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, src1) && inRC(MASK, src2) : Assertions.errorMessage(src1, src2);
        // Code: VEX.L0.0F.W1 98 /r
        vexPrefix(src1, Register.None, src2, AVXKind.AVXSize.XMM, VEXPrefixConfig.P_, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, VEXPrefixConfig.W1, true);
        emitByte(0x98);
        emitModRM(src1, src2);
    }

    public final void kmovb(Register dst, Register src) {
        VexMoveMaskOp.KMOVB.emit(this, AVXKind.AVXSize.XMM, dst, src);
    }

    public final void kmovb(AMD64Address dst, Register src) {
        VexMoveMaskOp.KMOVB.emit(this, AVXKind.AVXSize.XMM, dst, src);
    }

    public final void kmovb(Register dst, AMD64Address src) {
        VexMoveMaskOp.KMOVB.emit(this, AVXKind.AVXSize.XMM, dst, src);
    }

    public final void kmovw(Register dst, Register src) {
        VexMoveMaskOp.KMOVW.emit(this, AVXKind.AVXSize.XMM, dst, src);
    }

    public final void kmovw(AMD64Address dst, Register src) {
        VexMoveMaskOp.KMOVW.emit(this, AVXKind.AVXSize.XMM, dst, src);
    }

    public final void kmovw(Register dst, AMD64Address src) {
        VexMoveMaskOp.KMOVW.emit(this, AVXKind.AVXSize.XMM, dst, src);
    }

    public final void kmovd(Register dst, Register src) {
        VexMoveMaskOp.KMOVD.emit(this, AVXKind.AVXSize.XMM, dst, src);
    }

    public final void kmovd(AMD64Address dst, Register src) {
        VexMoveMaskOp.KMOVD.emit(this, AVXKind.AVXSize.XMM, dst, src);
    }

    public final void kmovd(Register dst, AMD64Address src) {
        VexMoveMaskOp.KMOVD.emit(this, AVXKind.AVXSize.XMM, dst, src);
    }

    public final void kmovq(Register dst, Register src) {
        VexMoveMaskOp.KMOVQ.emit(this, AVXKind.AVXSize.XMM, dst, src);
    }

    public final void kmovq(AMD64Address dst, Register src) {
        VexMoveMaskOp.KMOVQ.emit(this, AVXKind.AVXSize.XMM, dst, src);
    }

    public final void kmovq(Register dst, AMD64Address src) {
        VexMoveMaskOp.KMOVQ.emit(this, AVXKind.AVXSize.XMM, dst, src);
    }

    // Insn: KTESTD k1, k2
    public final void ktestd(Register src1, Register src2) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, src1) && inRC(MASK, src2) : src1 + " " + src2;
        // Code: VEX.L0.66.0F.W1 99 /r
        vexPrefix(src1, Register.None, src2, AVXKind.AVXSize.XMM, VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, VEXPrefixConfig.W1, true);
        emitByte(0x99);
        emitModRM(src1, src2);
    }

    // Insn: KTESTQ k1, k2
    public final void ktestq(Register src1, Register src2) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, src1) && inRC(MASK, src2) : src1 + " " + src2;
        // Code: VEX.L0.0F.W1 99 /r
        vexPrefix(src1, Register.None, src2, AVXKind.AVXSize.XMM, VEXPrefixConfig.P_, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, VEXPrefixConfig.W1, true);
        emitByte(0x99);
        emitModRM(src1, src2);
    }

    public final void evmovdqu64(Register dst, AMD64Address src) {
        assert supports(CPUFeature.AVX512F);
        assert inRC(XMM, dst);
        evexPrefix(dst, Register.None, Register.None, src, AVXKind.AVXSize.ZMM, VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        emitByte(0x6F);
        emitOperandHelper(dst, src, 0, EVEXTuple.FVM.getDisp8ScalingFactor(AVXKind.AVXSize.ZMM));
    }

    public final void evmovdqu64(AMD64Address dst, Register src) {
        assert supports(CPUFeature.AVX512F);
        assert inRC(XMM, src);
        evexPrefix(src, Register.None, Register.None, dst, AVXKind.AVXSize.ZMM, VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        emitByte(0x7F);
        emitOperandHelper(src, dst, 0, EVEXTuple.FVM.getDisp8ScalingFactor(AVXKind.AVXSize.ZMM));
    }

    // Insn: VPMOVZXBW zmm1, m256
    public final void evpmovzxbw(Register dst, AMD64Address src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(XMM, dst);
        // Code: EVEX.512.66.0F38.WIG 30 /r
        evexPrefix(dst, Register.None, Register.None, src, AVXKind.AVXSize.ZMM, VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        emitByte(0x30);
        emitOperandHelper(dst, src, 0, EVEXTuple.HVM.getDisp8ScalingFactor(AVXKind.AVXSize.ZMM));
    }

    public final void evpcmpeqb(Register kdst, Register nds, AMD64Address src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, kdst) && inRC(XMM, nds) : kdst + " " + nds;
        evexPrefix(kdst, Register.None, nds, src, AVXKind.AVXSize.ZMM, VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        emitByte(0x74);
        emitOperandHelper(kdst, src, 0, EVEXTuple.FVM.getDisp8ScalingFactor(AVXKind.AVXSize.ZMM));
    }

    // Insn: VMOVDQU16 zmm1 {k1}{z}, zmm2/m512
    // -----
    // Insn: VMOVDQU16 zmm1, m512
    public final void evmovdqu16(Register dst, AMD64Address src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(XMM, dst);
        // Code: EVEX.512.F2.0F.W1 6F /r
        evexPrefix(dst, Register.None, Register.None, src, AVXKind.AVXSize.ZMM, VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        emitByte(0x6F);
        emitOperandHelper(dst, src, 0, EVEXTuple.FVM.getDisp8ScalingFactor(AVXKind.AVXSize.ZMM));
    }

    // Insn: VMOVDQU16 zmm1, k1:z, m512
    public final void evmovdqu16(Register dst, Register mask, AMD64Address src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(XMM, dst) && inRC(MASK, mask) : dst + " " + mask;
        // Code: EVEX.512.F2.0F.W1 6F /r
        evexPrefix(dst, mask, Register.None, src, AVXKind.AVXSize.ZMM, VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, EVEXPrefixConfig.Z1, EVEXPrefixConfig.B0);
        emitByte(0x6F);
        emitOperandHelper(dst, src, 0, EVEXTuple.FVM.getDisp8ScalingFactor(AVXKind.AVXSize.ZMM));
    }

    // Insn: VMOVDQU16 zmm2/m512 {k1}{z}, zmm1
    // -----
    // Insn: VMOVDQU16 m512, zmm1
    public final void evmovdqu16(AMD64Address dst, Register src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(XMM, src);
        // Code: EVEX.512.F2.0F.W1 7F /r
        evexPrefix(src, Register.None, Register.None, dst, AVXKind.AVXSize.ZMM, VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        emitByte(0x7F);
        emitOperandHelper(src, dst, 0, EVEXTuple.FVM.getDisp8ScalingFactor(AVXKind.AVXSize.ZMM));
    }

    // Insn: VMOVDQU16 m512, k1, zmm1
    public final void evmovdqu16(AMD64Address dst, Register mask, Register src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, mask) && inRC(XMM, src) : mask + " " + src;
        // Code: EVEX.512.F2.0F.W1 7F /r
        evexPrefix(src, mask, Register.None, dst, AVXKind.AVXSize.ZMM, VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        emitByte(0x7F);
        emitOperandHelper(src, dst, 0, EVEXTuple.FVM.getDisp8ScalingFactor(AVXKind.AVXSize.ZMM));
    }

    // Insn: VPBROADCASTW zmm1 {k1}{z}, reg
    // -----
    // Insn: VPBROADCASTW zmm1, reg
    public final void evpbroadcastw(Register dst, Register src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(XMM, dst) && inRC(CPU, src) : dst + " " + src;
        // Code: EVEX.512.66.0F38.W0 7B /r
        evexPrefix(dst, Register.None, Register.None, src, AVXKind.AVXSize.ZMM, VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        emitByte(0x7B);
        emitModRM(dst, src);
    }

    // Insn: VPCMPUW k1 {k2}, zmm2, zmm3/m512, imm8
    // -----
    // Insn: VPCMPUW k1, zmm2, zmm3, imm8
    public final void evpcmpuw(Register kdst, Register nds, Register src, int vcc) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, kdst) && inRC(XMM, nds) && inRC(XMM, src) : kdst + " " + src;
        // Code: EVEX.NDS.512.66.0F3A.W1 3E /r ib
        evexPrefix(kdst, Register.None, nds, src, AVXKind.AVXSize.ZMM, VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        emitByte(0x3E);
        emitModRM(kdst, src);
        emitByte(vcc);
    }

    // Insn: VPCMPUW k1 {k2}, zmm2, zmm3/m512, imm8
    // -----
    // Insn: VPCMPUW k1, k2, zmm2, zmm3, imm8
    public final void evpcmpuw(Register kdst, Register mask, Register nds, Register src, int vcc) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, kdst) && inRC(MASK, mask) : kdst + " " + mask;
        assert inRC(XMM, nds) && inRC(XMM, src) : nds + " " + src;
        // Code: EVEX.NDS.512.66.0F3A.W1 3E /r ib
        evexPrefix(kdst, mask, nds, src, AVXKind.AVXSize.ZMM, VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        emitByte(0x3E);
        emitModRM(kdst, src);
        emitByte(vcc);
    }

    // Insn: VPCMPQTB k1 {k2}, zmm2, zmm3/m512
    // -----
    // Insn: VPCMPQTB k1, zmm2, m512
    public final void evpcmpgtb(Register kdst, Register nds, AMD64Address src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, kdst);
        assert inRC(XMM, nds);
        // Code: EVEX.NDS.512.66.0F.WIG 64 /r
        evexPrefix(kdst, Register.None, nds, src, AVXKind.AVXSize.ZMM, VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        emitByte(0x64);
        emitOperandHelper(kdst, src, 0, EVEXTuple.FVM.getDisp8ScalingFactor(AVXKind.AVXSize.ZMM));
    }

    // Insn: VPCMPQTB k1 {k2}, zmm2, zmm3/m512
    // -----
    // Insn: VPCMPQTB k1, k2, zmm2, m512
    public final void evpcmpgtb(Register kdst, Register mask, Register nds, AMD64Address src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, kdst) && inRC(MASK, mask) : kdst + " " + mask;
        assert inRC(XMM, nds);
        // Code: EVEX.NDS.512.66.0F.WIG 64 /r
        evexPrefix(kdst, mask, nds, src, AVXKind.AVXSize.ZMM, VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        emitByte(0x64);
        emitOperandHelper(kdst, src, 0, EVEXTuple.FVM.getDisp8ScalingFactor(AVXKind.AVXSize.ZMM));
    }

    // Insn: VPMOVWB ymm1/m256 {k1}{z}, zmm2
    // -----
    // Insn: VPMOVWB m256, zmm2
    public final void evpmovwb(AMD64Address dst, Register src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(XMM, src);
        // Code: EVEX.512.F3.0F38.W0 30 /r
        evexPrefix(src, Register.None, Register.None, dst, AVXKind.AVXSize.ZMM, VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        emitByte(0x30);
        emitOperandHelper(src, dst, 0, EVEXTuple.HVM.getDisp8ScalingFactor(AVXKind.AVXSize.ZMM));
    }

    // Insn: VPMOVWB m256, k1, zmm2
    public final void evpmovwb(AMD64Address dst, Register mask, Register src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, mask) && inRC(XMM, src) : mask + " " + src;
        // Code: EVEX.512.F3.0F38.W0 30 /r
        evexPrefix(src, mask, Register.None, dst, AVXKind.AVXSize.ZMM, VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        emitByte(0x30);
        emitOperandHelper(src, dst, 0, EVEXTuple.HVM.getDisp8ScalingFactor(AVXKind.AVXSize.ZMM));
    }

    // Insn: VPMOVZXBW zmm1 {k1}{z}, ymm2/m256
    // -----
    // Insn: VPMOVZXBW zmm1, k1, m256
    public final void evpmovzxbw(Register dst, Register mask, AMD64Address src) {
        assert supports(CPUFeature.AVX512BW);
        assert inRC(MASK, mask) && inRC(XMM, dst) : mask + " " + dst;
        // Code: EVEX.512.66.0F38.WIG 30 /r
        evexPrefix(dst, mask, Register.None, src, AVXKind.AVXSize.ZMM, VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        emitByte(0x30);
        emitOperandHelper(dst, src, 0, EVEXTuple.HVM.getDisp8ScalingFactor(AVXKind.AVXSize.ZMM));
    }

    // Insn: vfpclassss k2 {k1}, xmm2/m32, imm8
    public final void vfpclassss(Register dst, Register mask, Register src, int imm8) {
        assert supports(CPUFeature.AVX512DQ);
        assert inRC(MASK, mask) && inRC(MASK, mask) && inRC(XMM, src) : mask + " " + src;
        // Code: EVEX.LIG.66.0F3A.W0 67 /r
        evexPrefix(dst, mask, Register.None, src, AVXKind.AVXSize.XMM, VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        emitByte(0x67);
        emitModRM(dst, src);
        emitByte(imm8);
    }

    /**
     * Wraps information for different jump instructions, such as the instruction and displacement
     * size and the position of the displacement within the instruction.
     */
    public enum JumpType {
        JMP(5, 1, 4),
        JMPB(2, 1, 1),
        JCC(6, 2, 4),
        JCCB(2, 1, 1);

        JumpType(int instrSize, int dispPos, int dispSize) {
            assert instrSize == dispPos + dispSize : "Invalid JumpInfo: instrSize=" + instrSize + ", dispPos=" + dispPos + ", dispSize=" + dispSize;
            this.instrSize = instrSize;
            this.dispPos = dispPos;
            this.dispSize = dispSize;
        }

        /**
         * Size of the instruction in bytes.
         */
        public final int instrSize;
        /**
         * Size of the jump displacement in bytes.
         */
        public final int dispSize;
        /**
         * Position (in bytes) of the jump displacement within the instruction.
         */
        public final int dispPos;
    }

    /**
     * Collects information about emitted jumps. Used for optimizing long jumps in a second code
     * emit pass.
     */
    public static class JumpInfo {
        /**
         * Accounts for unknown alignments when deciding if a forward jump should be emitted with a
         * single byte displacement (called "short" jump). Only forward jumps with displacements <
         * (127 - {@link #ALIGNMENT_COMPENSATION_HEURISTIC}) are emitted as short jumps.
         */
        private static final int ALIGNMENT_COMPENSATION_HEURISTIC = 32;

        /**
         * The index of this jump within the emitted code. Corresponds to the emit order, e.g.,
         * {@code idx = 5} denotes the 6th emitted jump.
         */
        public final int jumpIdx;

        /**
         * The position (bytes from the beginning of the method) of the instruction.
         */
        public final int instrPos;
        /**
         * The type of the jump instruction.
         */
        public final JumpType type;
        /**
         * The position (bytes from the beginning of the method) of the displacement.
         */
        public int displacementPosition;

        private final AMD64Assembler asm;

        JumpInfo(int jumpIdx, JumpType type, int pos, AMD64Assembler asm) {
            this.jumpIdx = jumpIdx;
            this.type = type;
            this.instrPos = pos;
            this.displacementPosition = pos + type.dispPos;
            this.asm = asm;
        }

        /**
         * Read the jump displacement from the code buffer. If the corresponding jump label has not
         * been bound yet, the displacement is still uninitialized (=0).
         */
        public int getDisplacement() {
            switch (type.dispSize) {
                case Byte.BYTES:
                    return asm.getByte(instrPos + type.dispPos);
                case Integer.BYTES:
                    return asm.getInt(instrPos + type.dispPos);
                default:
                    throw new RuntimeException("Unhandled jump displacement size: " + type.dispSize);
            }
        }

        /**
         * Returns true if this jump fulfills all following conditions:
         *
         * <ul>
         * <li>it is a relative jmp or jcc with a 4 byte displacement</li>
         * <li>it is a forward jump</li>
         * <li>it has an actual jump distance < (127 -
         * {@link JumpInfo#ALIGNMENT_COMPENSATION_HEURISTIC})</li>
         * </ul>
         * The jump distance of replaceable jumps is reduced by
         * {@link JumpInfo#ALIGNMENT_COMPENSATION_HEURISTIC} to heuristically compensate alignments.
         */
        public boolean canBeOptimized() {
            // check if suitable op:
            if (type != JumpType.JCC && type != JumpType.JMP) {
                return false;
            }

            int displacement = getDisplacement();
            // backward jumps are already emitted in optimal size
            if (displacement < 0) {
                return false;
            }
            // Check if displacement (heuristically compensating alignments) fits in single byte.
            return isByte(getDisplacement() + ALIGNMENT_COMPENSATION_HEURISTIC);
        }

        public boolean isLongJmp() {
            return asm.getByte(instrPos) == 0xE9;
        }

        public boolean isLongJcc() {
            return asm.getByte(instrPos) == 0x0F && (asm.getByte(instrPos + 1) & 0xF0) == 0x80;
        }
    }

    /**
     * Emit order index of next jump (jmp | jcc) to be emitted. Used for finding the same jumps
     * across different code emits. This requires the order of emitted code from the same LIR to be
     * deterministic.
     */
    private int nextJumpIdx = 0;

    /**
     * Checks if the jump at the given index can be replaced by a equivalent instruction with
     * smaller displacement size. This replacement can only be done if
     * {@link AMD64BaseAssembler#force4ByteNonZeroDisplacements} allows emitting short jumps and if
     * a previous code emit has found that this jump will have a sufficiently small displacement.
     */
    private boolean canUseShortJump(int jumpIdx) {
        return !force4ByteNonZeroDisplacements && optimizeLongJumps && longToShortJumps != null && longToShortJumps.contains(jumpIdx);
    }

    /**
     * Information about emitted jumps, which can be processed after patching of jump targets.
     */
    private List<JumpInfo> jumpInfo = new ArrayList<>();

    /**
     * Stores the emit index of jumps which can be replaced by single byte displacement versions.
     * Subsequent code emits can use this information to emit short jumps for these indices.
     */
    private EconomicSet<Integer> longToShortJumps = EconomicSet.create();

    private void trackJump(JumpType type, int pos) {
        jumpInfo.add(new JumpInfo(nextJumpIdx++, type, pos, this));
    }

    /**
     * The maximum number of acceptable bailouts when optimizing long jumps. Only checked if
     * assertions are enabled.
     */
    private static final int MAX_OPTIMIZE_LONG_JUMPS_BAILOUTS = 20;

    /**
     * Accumulated number of bailouts when optimizing long jumps. Such bailouts are ok in rare
     * cases. Only checked if assertions are enabled.
     */
    private static AtomicInteger optimizeLongJumpsBailouts = new AtomicInteger(0);

    /**
     * Disables optimizing long jumps for this compilation. If assertions are enabled, checks that
     * the accumulated number of bailouts when optimizing long jumps does not exceed
     * {@link AMD64Assembler#MAX_OPTIMIZE_LONG_JUMPS_BAILOUTS}. This would indicate a regression in
     * the algorithm / the heuristics.
     */
    public void disableOptimizeLongJumpsAfterException() {
        assert optimizeLongJumpsBailouts.incrementAndGet() < MAX_OPTIMIZE_LONG_JUMPS_BAILOUTS : "Replacing 4byte-displacement jumps with 1byte-displacement jumps has resulted in too many BranchTargetOutOfBoundsExceptions. " +
                        "Please check the algorithm or disable the optimization by setting OptimizeLongJumps=false!";
        optimizeLongJumps = false;
    }

    @Override
    public void reset() {
        if (optimizeLongJumps) {
            longToShortJumps.clear();
            for (JumpInfo j : jumpInfo) {
                if (j.canBeOptimized()) {
                    /*
                     * Mark the indices of emitted jumps (jmp | jcc) which could have been replaced
                     * by short jumps (8bit displacement). The order in which jumps are emitted from
                     * the same LIR is required to be deterministic!
                     */
                    longToShortJumps.add(j.jumpIdx);
                }
            }
        }
        super.reset();
        nextJumpIdx = 0;
        jumpInfo = new ArrayList<>();
    }

}
