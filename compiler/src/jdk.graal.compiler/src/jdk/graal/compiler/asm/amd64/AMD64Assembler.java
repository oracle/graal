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

import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.B0;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.Z0;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.Z1;
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
import static jdk.vm.ci.code.MemoryBarriers.STORE_LOAD;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.asm.BranchTargetOutOfBoundsException;
import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.amd64.MemoryReadInterceptor;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.code.TargetDescription;

/**
 * This class implements an assembler that can encode most X86 instructions.
 */
public class AMD64Assembler extends AMD64BaseAssembler implements MemoryReadInterceptor {

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

    public AMD64Assembler(TargetDescription target, OptionValues optionValues, boolean hasIntelJccErratum) {
        super(target);
        if (Options.UseBranchesWithin32ByteBoundary.hasBeenSet(optionValues)) {
            useBranchesWithin32ByteBoundary = Options.UseBranchesWithin32ByteBoundary.getValue(optionValues);
        } else {
            useBranchesWithin32ByteBoundary = !GraalOptions.ReduceCodeSize.getValue(optionValues) && hasIntelJccErratum;
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
        PackedDoubleIntToFloatAssertion(XMM, CPU, OperandSize.PD),
        PackedDoubleFloatToIntAssertion(CPU, XMM, OperandSize.PD),
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
            GraalError.guarantee(resultReg == null || resultCategory.equals(resultReg.getRegisterCategory()), "invalid result register %s used in %s ", resultReg, op);
            GraalError.guarantee(inputReg == null || inputCategory.equals(inputReg.getRegisterCategory()), "invalid input register %s used in %s ", inputReg, op);
            GraalError.guarantee(resultReg == null || !inRC(CPU, resultReg) || (resultReg.encoding < 16), "APX register %s used in %s is not yet supported", resultReg, op);
            GraalError.guarantee(inputReg == null || !inRC(CPU, inputReg) || (inputReg.encoding < 16), "APX register %s used in %s is not yet supported", inputReg, op);

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
                case AES:
                case CLMUL:
                case GFNI:
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
                assert NumUtil.isUByte(imm) || NumUtil.isByte(imm) : imm;
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

        public static final AMD64RMOp XCHGB  = new AMD64RMOp("XCHGB",              0x86, OpAssertion.ByteAssertion);
        public static final AMD64RMOp XCHG   = new AMD64RMOp("XCHG",               0x87, OpAssertion.WordOrLargerAssertion);
        // TEST is documented as MR operation, but it's symmetric, and using it as RM operation is more convenient.
        public static final AMD64RMOp TESTB  = new AMD64RMOp("TEST",               0x84, OpAssertion.ByteAssertion);
        public static final AMD64RMOp TEST   = new AMD64RMOp("TEST",               0x85, OpAssertion.WordOrLargerAssertion);
        public static final AMD64RMOp CMPXCHG = new AMD64RMOp("CMPXCHG",     P_0F, 0xB1, OpAssertion.WordOrLargerAssertion);
        // ADX instructions
        public static final AMD64RMOp ADCX   = new AMD64RMOp("ADCX", 0x66, P_0F38, 0xF6, OpAssertion.DwordOrLargerAssertion, CPUFeature.ADX);
        public static final AMD64RMOp ADOX   = new AMD64RMOp("ADOX", 0xF3, P_0F38, 0xF6, OpAssertion.DwordOrLargerAssertion, CPUFeature.ADX);

        // SHA instructions
        public static final AMD64RMOp SHA1MSG1    = new AMD64RMOp("SHA1MSG1",    P_0F38, 0xC9, OpAssertion.PackedSingleAssertion, CPUFeature.SHA);
        public static final AMD64RMOp SHA1MSG2    = new AMD64RMOp("SHA1MSG2",    P_0F38, 0xCA, OpAssertion.PackedSingleAssertion, CPUFeature.SHA);
        public static final AMD64RMOp SHA1NEXTE   = new AMD64RMOp("SHA1NEXTE",   P_0F38, 0xC8, OpAssertion.PackedSingleAssertion, CPUFeature.SHA);

        public static final AMD64RMOp SHA256MSG1  = new AMD64RMOp("SHA256MSG1",  P_0F38, 0xCC, OpAssertion.PackedSingleAssertion, CPUFeature.SHA);
        public static final AMD64RMOp SHA256MSG2  = new AMD64RMOp("SHA256MSG2",  P_0F38, 0xCD, OpAssertion.PackedSingleAssertion, CPUFeature.SHA);

        // xmm0 is implicit additional source to this instruction.
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
            asm.interceptMemorySrcOperands(src);
            emitOpcode(asm, size, getRXB(dst, src), dst.encoding, 0);
            asm.emitOperandHelper(dst, src, 0);
        }

        public void emit(AMD64Assembler asm, OperandSize size, Register dst, AMD64Address src, boolean force4Byte) {
            assert verify(asm, size, dst, null);
            assert !isSSEInstruction();
            asm.interceptMemorySrcOperands(src);
            emitOpcode(asm, size, getRXB(dst, src), dst.encoding, 0);
            asm.emitOperandHelper(dst, src, force4Byte, 0);
        }
    }

    /**
     * Opcode with operand order of MR.
     */
    public static class AMD64MROp extends AMD64RROp {
        // @formatter:off
        public static final AMD64MROp MOVB     = new AMD64MROp("MOVB",           0x88, OpAssertion.ByteAssertion);
        public static final AMD64MROp MOV      = new AMD64MROp("MOV",            0x89, OpAssertion.WordOrLargerAssertion);
        public static final AMD64MROp TEST     = new AMD64MROp("TEST",           0x85, OpAssertion.WordOrLargerAssertion);
        public static final AMD64MROp CMPXCHGB = new AMD64MROp("CMPXCHGB", P_0F, 0xB0, OpAssertion.ByteAssertion);
        public static final AMD64MROp CMPXCHG  = new AMD64MROp("CMPXCHG",  P_0F, 0xB1, OpAssertion.WordOrLargerAssertion);

        public static final AMD64MROp XADDB    = new AMD64MROp("XADDB",    P_0F, 0xC0, OpAssertion.ByteAssertion);
        public static final AMD64MROp XADD     = new AMD64MROp("XADD",     P_0F, 0xC1, OpAssertion.WordOrLargerAssertion);
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
        public static final AMD64MOp DEC  = new AMD64MOp("DEC",  0xFF, 1);
        public static final AMD64MOp DECB = new AMD64MOp("DEC",  0xFE, 1, OpAssertion.ByteAssertion);
        public static final AMD64MOp DIV  = new AMD64MOp("DIV",  0xF7, 6);
        public static final AMD64MOp IDIV = new AMD64MOp("IDIV", 0xF7, 7);
        public static final AMD64MOp IMUL = new AMD64MOp("IMUL", 0xF7, 5);
        public static final AMD64MOp INC  = new AMD64MOp("INC",  0xFF, 0);
        public static final AMD64MOp INCB = new AMD64MOp("INC",  0xFE, 0, OpAssertion.ByteAssertion);
        public static final AMD64MOp MUL  = new AMD64MOp("MUL",  0xF7, 4);
        public static final AMD64MOp NEG  = new AMD64MOp("NEG",  0xF7, 3);
        public static final AMD64MOp NEGB = new AMD64MOp("NEG",  0xF6, 3, OpAssertion.ByteAssertion);
        public static final AMD64MOp NOT  = new AMD64MOp("NOT",  0xF7, 2);
        public static final AMD64MOp NOTB = new AMD64MOp("NOT",  0xF6, 2, OpAssertion.ByteAssertion);
        public static final AMD64MOp POP  = new AMD64MOp("POP",  0x8F, 0, OpAssertion.WordOrQwordAssertion);
        public static final AMD64MOp PUSH = new AMD64MOp("PUSH", 0xFF, 6, OpAssertion.WordOrQwordAssertion);
        public static final AMD64MOp SAR  = new AMD64MOp("SAR",  0xD3, 7, OpAssertion.WordOrLargerAssertion);
        public static final AMD64MOp SAR1 = new AMD64MOp("SAR1", 0xD1, 7, OpAssertion.WordOrLargerAssertion);
        public static final AMD64MOp SHL  = new AMD64MOp("SHL",  0xD3, 4, OpAssertion.WordOrLargerAssertion);
        public static final AMD64MOp SHL1 = new AMD64MOp("SHL1", 0xD1, 4, OpAssertion.WordOrLargerAssertion);
        public static final AMD64MOp SHR  = new AMD64MOp("SHR",  0xD3, 5, OpAssertion.WordOrLargerAssertion);
        public static final AMD64MOp SHR1 = new AMD64MOp("SHR1", 0xD1, 5, OpAssertion.WordOrLargerAssertion);
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
        public static final AMD64MIOp BT    = new AMD64MIOp("BT",   true,  P_0F, 0xBA, 4, true, OpAssertion.WordOrLargerAssertion);
        public static final AMD64MIOp BTR   = new AMD64MIOp("BTR",  true,  P_0F, 0xBA, 6, true, OpAssertion.WordOrLargerAssertion);
        public static final AMD64MIOp BTS   = new AMD64MIOp("BTS",  true,  P_0F, 0xBA, 5, true, OpAssertion.WordOrLargerAssertion);
        public static final AMD64MIOp MOVB  = new AMD64MIOp("MOVB", true,        0xC6, 0, false, OpAssertion.ByteAssertion);
        public static final AMD64MIOp MOV   = new AMD64MIOp("MOV",  false,       0xC7, 0, false, OpAssertion.WordOrLargerAssertion);
        public static final AMD64MIOp SAR   = new AMD64MIOp("SAR",  true,        0xC1, 7, true, OpAssertion.WordOrLargerAssertion);
        public static final AMD64MIOp SHL   = new AMD64MIOp("SHL",  true,        0xC1, 4, true, OpAssertion.WordOrLargerAssertion);
        public static final AMD64MIOp SHR   = new AMD64MIOp("SHR",  true,        0xC1, 5, true, OpAssertion.WordOrLargerAssertion);
        public static final AMD64MIOp TESTB = new AMD64MIOp("TEST", true,        0xF6, 0, true, OpAssertion.ByteAssertion);
        public static final AMD64MIOp TEST  = new AMD64MIOp("TEST", false,       0xF7, 0, true, OpAssertion.WordOrLargerAssertion);
        // @formatter:on

        private final int ext;
        /**
         * Defines if the Op reads from memory and makes the result observable by the user (e.g.
         * spilling to a register or in a flag).
         */
        private final boolean isMemRead;

        protected AMD64MIOp(String opcode, boolean immIsByte, int op, int ext, boolean isMemRead) {
            this(opcode, immIsByte, op, ext, isMemRead, OpAssertion.WordOrLargerAssertion);
        }

        protected AMD64MIOp(String opcode, boolean immIsByte, int op, int ext, boolean isMemRead, OpAssertion assertion) {
            this(opcode, immIsByte, 0, op, ext, isMemRead, assertion);
        }

        protected AMD64MIOp(String opcode, boolean immIsByte, int prefix, int op, int ext, boolean isMemRead, OpAssertion assertion) {
            super(opcode, immIsByte, prefix, op, assertion);
            this.ext = ext;
            this.isMemRead = isMemRead;
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

        public final void emit(AMD64Assembler asm, OperandSize size, AMD64Address address, int imm) {
            emit(asm, size, address, imm, false);
        }

        public final void emit(AMD64Assembler asm, OperandSize size, AMD64Address address, int imm, boolean annotateImm) {
            assert verify(asm, size, null, null);
            if (isMemRead) {
                asm.interceptMemorySrcOperands(address);
            }
            int insnPos = asm.position();
            emitOpcode(asm, size, getRXB(null, address), 0, 0);
            asm.emitOperandHelper(ext, address, immediateSize(size));
            int immPos = asm.position();
            emitImmediate(asm, size, imm);
            int nextInsnPos = asm.position();
            if (annotateImm && asm.codePatchingAnnotationConsumer != null) {
                asm.codePatchingAnnotationConsumer.accept(new OperandDataAnnotation(insnPos, immPos, nextInsnPos - immPos, nextInsnPos));
            }
        }

        public boolean isMemRead() {
            return isMemRead;
        }
    }

    /**
     * Denotes the preferred nds register (VEX.vvvv) for VEX-encoding of an SSE instruction.
     * <p>
     * For RM instructions where VEX.vvvv is reserved and must be 1111b, we should use
     * {@link PreferredNDS#NONE}. For RVM instructions, the default should be
     * {@link PreferredNDS#DST} to mimic the semantic of {@code dst <- op (dst, src)}. We should
     * only use {@link PreferredNDS#SRC} for unary instructions, e.g., ROUNDSS. This would help us
     * avoid an implicit dependency to {@code dst} register.
     * <p>
     * Note that when {@code src} is a memory address, we will choose {@code dst} as {@code nds}
     * even if {@link PreferredNDS#SRC} is specified, which implies an implicit dependency to
     * {@code dst}. In
     * {@link jdk.graal.compiler.lir.amd64.vector.AMD64VectorUnary.AVXConvertToFloatOp}, we manually
     * insert an {@code XOR} instruction for {@code dst}.
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
     * <p>
     * We only have one form of round as the operation is always treated with single variant input,
     * making its extension to 3 address forms redundant.
     */
    public static class AMD64RMIOp extends AMD64ImmOp {
        // @formatter:off
        public static final AMD64RMIOp IMUL    = new AMD64RMIOp("IMUL", false, 0x69);
        public static final AMD64RMIOp IMUL_SX = new AMD64RMIOp("IMUL", true,  0x6B);

        public static final AMD64RMIOp SHA1RNDS4 = new AMD64RMIOp("SHA1RNDS4", true, P_0F3A, 0xCC, OpAssertion.PackedSingleAssertion, CPUFeature.SHA);
        // @formatter:on

        protected AMD64RMIOp(String opcode, boolean immIsByte, int op) {
            super(opcode, immIsByte, 0, op, OpAssertion.WordOrLargerAssertion, null);
        }

        protected AMD64RMIOp(String opcode, boolean immIsByte, int prefix, int op, OpAssertion assertion, CPUFeature feature) {
            super(opcode, immIsByte, prefix, op, assertion, feature);
        }

        public void emit(AMD64Assembler asm, OperandSize size, Register dst, Register src, int imm) {
            assert verify(asm, size, dst, src);
            emitOpcode(asm, size, getRXB(dst, src), dst.encoding, src.encoding);
            asm.emitModRM(dst, src);
            emitImmediate(asm, size, imm);
        }

        public void emit(AMD64Assembler asm, OperandSize size, Register dst, AMD64Address src, int imm) {
            assert verify(asm, size, dst, null);
            asm.interceptMemorySrcOperands(src);
            emitOpcode(asm, size, getRXB(dst, src), dst.encoding, 0);
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
        public static final SSEOp CVTDQ2PD  = new SSEOp("CVTDQ2PD",        P_0F, 0xE6, PreferredNDS.NONE, OpAssertion.SingleAssertion);
        public static final SSEOp CVTSI2SS  = new SSEOp("CVTSI2SS",  0xF3, P_0F, 0x2A, PreferredNDS.DST,  OpAssertion.IntToFloatAssertion);
        public static final SSEOp CVTSI2SD  = new SSEOp("CVTSI2SD",  0xF2, P_0F, 0x2A, PreferredNDS.DST,  OpAssertion.IntToFloatAssertion);
        public static final SSEOp CVTTSS2SI = new SSEOp("CVTTSS2SI", 0xF3, P_0F, 0x2C, PreferredNDS.NONE, OpAssertion.FloatToIntAssertion);
        public static final SSEOp CVTTSD2SI = new SSEOp("CVTTSD2SI", 0xF2, P_0F, 0x2C, PreferredNDS.NONE, OpAssertion.FloatToIntAssertion);
        public static final SSEOp CVTTPD2DQ = new SSEOp("CVTTPD2DQ",       P_0F, 0xE6, PreferredNDS.NONE, OpAssertion.PackedDoubleAssertion);

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
        public static final SSEOp CVTSD2SI  = new SSEOp("CVTSD2SI",  0XF2, P_0F, 0x2D, PreferredNDS.NONE,  OpAssertion.FloatToIntAssertion);
        public static final SSEOp SUB       = new SSEOp("SUB",             P_0F, 0x5C, PreferredNDS.DST);
        public static final SSEOp MIN       = new SSEOp("MIN",             P_0F, 0x5D, PreferredNDS.DST);
        public static final SSEOp DIV       = new SSEOp("DIV",             P_0F, 0x5E, PreferredNDS.DST);
        public static final SSEOp MAX       = new SSEOp("MAX",             P_0F, 0x5F, PreferredNDS.DST);

        public static final SSEOp PADDD     = new SSEOp("PADDD",           P_0F, 0xFE, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);
        public static final SSEOp PADDQ     = new SSEOp("PADDQ",           P_0F, 0xD4, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);
        public static final SSEOp PSLLQ     = new SSEOp("PSLLQ",           P_0F, 0xF3, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);
        public static final SSEOp PSUBB     = new SSEOp("PSUBB",           P_0F, 0xF8, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);
        public static final SSEOp PSUBW     = new SSEOp("PSUBW",           P_0F, 0xF9, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);
        public static final SSEOp PSUBD     = new SSEOp("PSUBD",           P_0F, 0xFA, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);
        public static final SSEOp PSUBUSB   = new SSEOp("PSUBUSB",         P_0F, 0xD8, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);
        public static final SSEOp PSUBUSW   = new SSEOp("PSUBUSW",         P_0F, 0xD9, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);
        public static final SSEOp PMINUB    = new SSEOp("PMINUB",          P_0F, 0xDA, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);
        public static final SSEOp PMINUW    = new SSEOp("PMINUW",        P_0F38, 0x3A, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);
        public static final SSEOp PMINUD    = new SSEOp("PMINUD",        P_0F38, 0x3B, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);

        public static final SSEOp PACKUSWB  = new SSEOp("PACKUSWB",        P_0F, 0x67, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);
        public static final SSEOp PACKUSDW  = new SSEOp("PACKUSDW",      P_0F38, 0x2B, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion,  CPUFeature.SSE4_1);

        public static final SSEOp PCMPEQB   = new SSEOp("PCMPEQB",         P_0F, 0x74, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);
        public static final SSEOp PCMPEQW   = new SSEOp("PCMPEQW",         P_0F, 0x75, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);
        public static final SSEOp PCMPEQD   = new SSEOp("PCMPEQD",         P_0F, 0x76, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);

        public static final SSEOp PCMPGTB   = new SSEOp("PCMPGTB",         P_0F, 0x64, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);
        public static final SSEOp PCMPGTW   = new SSEOp("PCMPGTW",         P_0F, 0x65, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);
        public static final SSEOp PCMPGTD   = new SSEOp("PCMPGTD",         P_0F, 0x66, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);

        public static final SSEOp PMOVSXBW  = new SSEOp("PMOVSXBW",      P_0F38, 0x20, PreferredNDS.NONE, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);
        public static final SSEOp PMOVSXBD  = new SSEOp("PMOVSXBD",      P_0F38, 0x21, PreferredNDS.NONE, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);
        public static final SSEOp PMOVSXBQ  = new SSEOp("PMOVSXBQ",      P_0F38, 0x22, PreferredNDS.NONE, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);
        public static final SSEOp PMOVSXWD  = new SSEOp("PMOVSXWD",      P_0F38, 0x23, PreferredNDS.NONE, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);
        public static final SSEOp PMOVSXWQ  = new SSEOp("PMOVSXWQ",      P_0F38, 0x24, PreferredNDS.NONE, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);
        public static final SSEOp PMOVSXDQ  = new SSEOp("PMOVSXDQ",      P_0F38, 0x25, PreferredNDS.NONE, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);
        public static final SSEOp PMOVZXBW  = new SSEOp("PMOVZXBW",      P_0F38, 0x30, PreferredNDS.NONE, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);
        public static final SSEOp PMOVZXBD  = new SSEOp("PMOVZXBD",      P_0F38, 0x31, PreferredNDS.NONE, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);
        public static final SSEOp PMOVZXBQ  = new SSEOp("PMOVZXBQ",      P_0F38, 0x32, PreferredNDS.NONE, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);
        public static final SSEOp PMOVZXWD  = new SSEOp("PMOVZXWD",      P_0F38, 0x33, PreferredNDS.NONE, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);
        public static final SSEOp PMOVZXWQ  = new SSEOp("PMOVZXWQ",      P_0F38, 0x34, PreferredNDS.NONE, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);
        public static final SSEOp PMOVZXDQ  = new SSEOp("PMOVZXDQ",      P_0F38, 0x35, PreferredNDS.NONE, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);

        public static final SSEOp PAND      = new SSEOp("PAND",            P_0F, 0xDB, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion, CPUFeature.SSE2);
        public static final SSEOp PANDN     = new SSEOp("PANDN",           P_0F, 0xDF, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion, CPUFeature.SSE2);
        public static final SSEOp POR       = new SSEOp("POR",             P_0F, 0xEB, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion, CPUFeature.SSE2);
        public static final SSEOp PXOR      = new SSEOp("PXOR",            P_0F, 0xEF, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion, CPUFeature.SSE2);

        public static final SSEOp RCPPS     = new SSEOp("RCPPS",           P_0F, 0x53, PreferredNDS.NONE, OpAssertion.PackedSingleAssertion, CPUFeature.SSE);

        public static final SSEOp PTEST     = new SSEOp("PTEST",         P_0F38, 0x17, PreferredNDS.NONE, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE4_1);
        public static final SSEOp PSHUFB    = new SSEOp("PSHUFB",        P_0F38, 0x00, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion, CPUFeature.SSSE3);
        public static final SSEOp PUNPCKLBW = new SSEOp("PUNPCKLBW",       P_0F, 0x60, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion);

        public static final SSEOp AESDEC    = new SSEOp("AESDEC",        P_0F38, 0xDE, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion, CPUFeature.AES);
        public static final SSEOp AESDECLAST = new SSEOp("AESDECLAST",   P_0F38, 0xDF, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion, CPUFeature.AES);
        public static final SSEOp AESENC    = new SSEOp("AESENC",        P_0F38, 0xDC, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion, CPUFeature.AES);
        public static final SSEOp AESENCLAST = new SSEOp("AESENCLAST",   P_0F38, 0xDD, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion, CPUFeature.AES);

        // MOVD/MOVQ and MOVSS/MOVSD are the same opcode, just with different operand size prefix
        public static final SSEOp MOVD      = new SSEOp("MOVD",      0x66, P_0F, 0x6E, PreferredNDS.NONE, OpAssertion.DwordToFloatAssertion);
        public static final SSEOp MOVQ      = new SSEOp("MOVQ",      0x66, P_0F, 0x6E, PreferredNDS.NONE, OpAssertion.QwordToFloatAssertion);
        public static final SSEOp MOVSS     = new SSEOp("MOVSS",           P_0F, 0x10, PreferredNDS.SRC,  OpAssertion.SingleAssertion);
        public static final SSEOp MOVSD     = new SSEOp("MOVSD",           P_0F, 0x10, PreferredNDS.SRC,  OpAssertion.DoubleAssertion);

        public static final SSEOp MOVAPD    = new SSEOp("MOVAPD",          P_0F, 0x28, PreferredNDS.NONE, OpAssertion.PackedDoubleAssertion);
        public static final SSEOp MOVAPS    = new SSEOp("MOVAPS",          P_0F, 0x28, PreferredNDS.NONE, OpAssertion.PackedFloatAssertion);

        public static final SSEOp MOVDDUP   = new SSEOp("MOVDDUP",         P_0F, 0x12, PreferredNDS.NONE,  OpAssertion.DoubleAssertion, CPUFeature.SSE3);
        public static final SSEOp MOVDQA    = new SSEOp("MOVDQA",          P_0F, 0x6F, PreferredNDS.NONE,  OpAssertion.PackedDoubleAssertion);
        public static final SSEOp MOVDQU    = new SSEOp("MOVDQU",          P_0F, 0x6F, PreferredNDS.NONE,  OpAssertion.SingleAssertion);

        public static final SSEOp UNPCKHPD  = new SSEOp("UNPCKHPD",        P_0F, 0x15, PreferredNDS.DST,   OpAssertion.PackedDoubleAssertion);
        public static final SSEOp UNPCKLPD  = new SSEOp("UNPCKLPD",        P_0F, 0x14, PreferredNDS.DST,   OpAssertion.PackedDoubleAssertion);
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
            asm.interceptMemorySrcOperands(src);
            // MOVSS/SD are not RVM instruction when the dst is an address
            Register nds = (this == MOVSS || this == MOVSD) ? Register.None : preferredNDS.getNds(dst, src);
            asm.simdPrefix(dst, nds, src, size, prefix1, prefix2, size == OperandSize.QWORD);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src, 0);
        }
    }

    public static class SSERIOp extends AMD64ImmOp {
        // @formatter:off
        public static final SSERIOp PSLLW  = new SSERIOp("PSLLW",  true, P_0F, 0x71, 6, PreferredNDS.DST, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE2);
        public static final SSERIOp PSLLD  = new SSERIOp("PSLLD",  true, P_0F, 0x72, 6, PreferredNDS.DST, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE2);
        public static final SSERIOp PSLLDQ = new SSERIOp("PSLLDQ", true, P_0F, 0x73, 7, PreferredNDS.DST, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE2);
        public static final SSERIOp PSLLQ  = new SSERIOp("PSLLQ",  true, P_0F, 0x73, 6, PreferredNDS.DST, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE2);
        public static final SSERIOp PSRAW  = new SSERIOp("PSRAW",  true, P_0F, 0x71, 4, PreferredNDS.DST, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE2);
        public static final SSERIOp PSRAD  = new SSERIOp("PSRAD",  true, P_0F, 0x72, 4, PreferredNDS.DST, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE2);
        public static final SSERIOp PSRLW  = new SSERIOp("PSRLW",  true, P_0F, 0x71, 2, PreferredNDS.DST, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE2);
        public static final SSERIOp PSRLD  = new SSERIOp("PSRLD",  true, P_0F, 0x72, 2, PreferredNDS.DST, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE2);
        public static final SSERIOp PSRLDQ = new SSERIOp("PSRLDQ", true, P_0F, 0x73, 3, PreferredNDS.DST, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE2);
        public static final SSERIOp PSRLQ  = new SSERIOp("PSRLQ",  true, P_0F, 0x73, 2, PreferredNDS.DST, OpAssertion.PackedDoubleAssertion, CPUFeature.SSE2);
        // @formatter:on

        private final int ext;
        private final PreferredNDS preferredNDS;

        protected SSERIOp(String opcode, boolean immIsByte, int prefix, int op, int ext, PreferredNDS preferredNDS, OpAssertion assertion, CPUFeature feature) {
            super(opcode, immIsByte, prefix, op, assertion, feature);
            this.ext = ext;
            this.preferredNDS = preferredNDS;
        }

        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, int imm) {
            assert verify(asm, size, dst, null);
            assert isSSEInstruction();
            // pass xmm0 to make our assertion happy
            asm.simdPrefix(AMD64.xmm0, preferredNDS.getNds(dst, Register.None), dst, size, prefix1, prefix2, false);
            asm.emitByte(op);
            asm.emitModRM(ext, dst);
            emitImmediate(asm, size, imm);
        }
    }

    public static class SSERMIOp extends AMD64RMIOp {
        // @formatter:off
        public static final SSERMIOp ROUNDSS       = new SSERMIOp("ROUNDSS",       true, P_0F3A,       0x0A, PreferredNDS.SRC,  OpAssertion.PackedDoubleAssertion,           CPUFeature.SSE4_1);
        public static final SSERMIOp ROUNDSD       = new SSERMIOp("ROUNDSD",       true, P_0F3A,       0x0B, PreferredNDS.SRC,  OpAssertion.PackedDoubleAssertion,           CPUFeature.SSE4_1);

        public static final SSERMIOp PCMPESTRI     = new SSERMIOp("PCMPESTRI",     true, P_0F3A,       0x61, PreferredNDS.NONE, OpAssertion.PackedDoubleAssertion,           CPUFeature.SSE4_2);
        public static final SSERMIOp PCLMULQDQ     = new SSERMIOp("PCLMULQDQ",     true, P_0F3A,       0x44, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion,           CPUFeature.CLMUL);
        public static final SSERMIOp GF2P8AFFINEQB = new SSERMIOp("GF2P8AFFINEQB", true, P_0F3A, true, 0xCE, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion,           CPUFeature.GFNI);

        public static final SSERMIOp PINSRB        = new SSERMIOp("PINSRB",        true, P_0F3A,       0x20, PreferredNDS.DST,  OpAssertion.PackedDoubleIntToFloatAssertion, CPUFeature.SSE4_1);
        public static final SSERMIOp PINSRW        = new SSERMIOp("PINSRW",        true,   P_0F,       0xC4, PreferredNDS.DST,  OpAssertion.PackedDoubleIntToFloatAssertion, CPUFeature.SSE2);
        public static final SSERMIOp PINSRD        = new SSERMIOp("PINSRD",        true, P_0F3A,       0x22, PreferredNDS.DST,  OpAssertion.PackedDoubleIntToFloatAssertion, CPUFeature.SSE4_1);
        public static final SSERMIOp PINSRQ        = new SSERMIOp("PINSRQ",        true, P_0F3A, true, 0x22, PreferredNDS.DST,  OpAssertion.PackedDoubleIntToFloatAssertion, CPUFeature.SSE4_1);

        public static final SSERMIOp PALIGNR       = new SSERMIOp("PALIGNR",       true, P_0F3A,       0x0F, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion,           CPUFeature.SSSE3);
        public static final SSERMIOp PBLENDW       = new SSERMIOp("PBLENDW",       true, P_0F3A,       0x0E, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion,           CPUFeature.SSE4_1);
        public static final SSERMIOp PSHUFD        = new SSERMIOp("PSHUFD",        true, P_0F,         0x70, PreferredNDS.NONE, OpAssertion.PackedDoubleAssertion,           CPUFeature.SSE2);
        public static final SSERMIOp PSHUFLW       = new SSERMIOp("PSHUFLW",       true, P_0F,         0x70, PreferredNDS.NONE, OpAssertion.DoubleAssertion,                 CPUFeature.SSE2);

        public static final SSERMIOp SHUFPD        = new SSERMIOp("SHUFPD",        true, P_0F,         0xC6, PreferredNDS.DST,  OpAssertion.PackedDoubleAssertion,           CPUFeature.SSE2);
        // @formatter:on

        private final PreferredNDS preferredNDS;
        private final boolean w;

        protected SSERMIOp(String opcode, boolean immIsByte, int prefix, int op, PreferredNDS preferredNDS, OpAssertion assertion, CPUFeature feature) {
            this(opcode, immIsByte, prefix, false, op, preferredNDS, assertion, feature);
        }

        protected SSERMIOp(String opcode, boolean immIsByte, int prefix, boolean w, int op, PreferredNDS preferredNDS, OpAssertion assertion, CPUFeature feature) {
            super(opcode, immIsByte, prefix, op, assertion, feature);
            this.preferredNDS = preferredNDS;
            this.w = w;
        }

        @Override
        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, Register src, int imm) {
            assert verify(asm, size, dst, src);
            assert isSSEInstruction();
            asm.simdPrefix(dst, preferredNDS.getNds(dst, src), src, size, prefix1, prefix2, w);
            asm.emitByte(op);
            asm.emitModRM(dst, src);
            emitImmediate(asm, size, imm);
        }

        @Override
        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, AMD64Address src, int imm) {
            assert verify(asm, size, dst, null);
            assert isSSEInstruction();
            asm.interceptMemorySrcOperands(src);
            asm.simdPrefix(dst, preferredNDS.getNds(dst, src), src, size, prefix1, prefix2, w);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src, immediateSize(size));
            emitImmediate(asm, size, imm);
        }
    }

    /**
     * Opcode with operand order of MR.
     */
    public static class SSEMROp extends AMD64MROp {
        // @formatter:off
        // MOVD and MOVQ are the same opcode, just with different operand size prefix
        // Note that as MR opcodes, they have reverse operand order, so the IntToFloatingAssertion must be used.
        public static final SSEMROp MOVD   = new SSEMROp("MOVD",   0x66, P_0F, 0x7E, PreferredNDS.NONE, OpAssertion.DwordToFloatAssertion);
        public static final SSEMROp MOVQ   = new SSEMROp("MOVQ",   0x66, P_0F, 0x7E, PreferredNDS.NONE, OpAssertion.QwordToFloatAssertion);
        // MOVSS and MOVSD are the same opcode, just with different operand size prefix
        public static final SSEMROp MOVSS  = new SSEMROp("MOVSS",               P_0F, 0x11, PreferredNDS.SRC,  OpAssertion.SingleAssertion);
        public static final SSEMROp MOVSD  = new SSEMROp("MOVSD",               P_0F, 0x11, PreferredNDS.SRC,  OpAssertion.DoubleAssertion);

        public static final SSEMROp MOVDQU = new SSEMROp("MOVDQU",              P_0F, 0x7F, PreferredNDS.NONE, OpAssertion.SingleAssertion);
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

    public static class SSEMRIOp extends AMD64ImmOp {
        // @formatter:off
        public static final SSEMRIOp PEXTRB = new SSEMRIOp("PEXTRB", true, P_0F3A, false, 0x14, PreferredNDS.NONE,  OpAssertion.PackedDoubleFloatToIntAssertion, CPUFeature.SSE4_1);
        public static final SSEMRIOp PEXTRW = new SSEMRIOp("PEXTRW", true, P_0F3A, false, 0x15, PreferredNDS.NONE,  OpAssertion.PackedDoubleFloatToIntAssertion, CPUFeature.SSE4_1);
        public static final SSEMRIOp PEXTRD = new SSEMRIOp("PEXTRD", true, P_0F3A, false, 0x16, PreferredNDS.NONE,  OpAssertion.PackedDoubleFloatToIntAssertion, CPUFeature.SSE4_1);
        public static final SSEMRIOp PEXTRQ = new SSEMRIOp("PEXTRQ", true, P_0F3A, true,  0x16, PreferredNDS.NONE,  OpAssertion.PackedDoubleFloatToIntAssertion, CPUFeature.SSE4_1);
        // @formatter:on

        private final PreferredNDS preferredNDS;
        private final boolean w;

        protected SSEMRIOp(String opcode, boolean immIsByte, int prefix, boolean w, int op, PreferredNDS preferredNDS, OpAssertion assertion, CPUFeature feature) {
            super(opcode, immIsByte, prefix, op, assertion, feature);
            this.w = w;
            this.preferredNDS = preferredNDS;
        }

        public final void emit(AMD64Assembler asm, OperandSize size, Register dst, Register src, int imm) {
            assert verify(asm, size, dst, src);
            assert isSSEInstruction();
            asm.simdPrefix(src, preferredNDS.getNds(src, dst), dst, size, prefix1, prefix2, w);
            asm.emitByte(op);
            asm.emitModRM(src, dst);
            emitImmediate(asm, size, imm);
        }

        public final void emit(AMD64Assembler asm, OperandSize size, AMD64Address dst, Register src, int imm) {
            assert verify(asm, size, null, src);
            assert isSSEInstruction();
            asm.simdPrefix(src, preferredNDS.getNds(src, dst), dst, size, prefix1, prefix2, w);
            asm.emitByte(op);
            asm.emitOperandHelper(src, dst, immediateSize(size));
            emitImmediate(asm, size, imm);
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

            byteImmOp = new AMD64MIOp(opcode, true, 0, 0x80, code, false, OpAssertion.ByteAssertion);
            byteMrOp = new AMD64MROp(opcode, 0, baseOp, OpAssertion.ByteAssertion);
            byteRmOp = new AMD64RMOp(opcode, 0, baseOp | 0x02, OpAssertion.ByteAssertion);

            immOp = new AMD64MIOp(opcode, false, 0, 0x81, code, false, OpAssertion.WordOrLargerAssertion);
            immSxOp = new AMD64MIOp(opcode, true, 0, 0x83, code, false, OpAssertion.WordOrLargerAssertion);
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
            miOp = new AMD64MIOp(opcode, true, 0, 0xC1, code, true, OpAssertion.WordOrLargerAssertion);
        }
    }

    /**
     * This is used to verify the availability of a vex-encoded instruction during code emission.
     */
    private enum VEXFeatureAssertion {
        AVX1(EnumSet.of(CPUFeature.AVX), EnumSet.of(CPUFeature.AVX)),
        AVX2(EnumSet.of(CPUFeature.AVX2), EnumSet.of(CPUFeature.AVX2)),
        AVX1_AVX2(EnumSet.of(CPUFeature.AVX), EnumSet.of(CPUFeature.AVX2)),
        AVX1_128(EnumSet.of(CPUFeature.AVX), null),
        AVX2_128(EnumSet.of(CPUFeature.AVX2), null),
        AVX1_256(null, EnumSet.of(CPUFeature.AVX)),
        AVX2_256(null, EnumSet.of(CPUFeature.AVX2)),
        BMI1(EnumSet.of(CPUFeature.BMI1), null),
        BMI2(EnumSet.of(CPUFeature.BMI2), null),
        FMA(EnumSet.of(CPUFeature.FMA), EnumSet.of(CPUFeature.FMA)),
        FMA_128(EnumSet.of(CPUFeature.FMA), null),
        AVX1_AES_128(EnumSet.of(CPUFeature.AVX, CPUFeature.AES), null),
        F16C(EnumSet.of(CPUFeature.F16C), EnumSet.of(CPUFeature.F16C)),
        CLMUL_AVX1(EnumSet.of(CPUFeature.AVX, CPUFeature.CLMUL), EnumSet.of(CPUFeature.AVX, CPUFeature.CLMUL)),
        GFNI_AVX1(EnumSet.of(CPUFeature.AVX, CPUFeature.GFNI), EnumSet.of(CPUFeature.AVX, CPUFeature.GFNI)),
        AVX512F_L0(EnumSet.of(CPUFeature.AVX512F), null),
        AVX512BW_L0(EnumSet.of(CPUFeature.AVX512BW), null),
        AVX512DQ_L0(EnumSet.of(CPUFeature.AVX512DQ), null),
        AVX512F_L1(null, EnumSet.of(CPUFeature.AVX512F)),
        AVX512BW_L1(null, EnumSet.of(CPUFeature.AVX512BW)),
        AVX512DQ_L1(null, EnumSet.of(CPUFeature.AVX512DQ));

        private final EnumSet<CPUFeature> l128Features;
        private final EnumSet<CPUFeature> l256Features;

        VEXFeatureAssertion(EnumSet<CPUFeature> l128Features, EnumSet<CPUFeature> l256Features) {
            this.l128Features = l128Features;
            this.l256Features = l256Features;
        }

        public boolean isValid(EnumSet<CPUFeature> features, AVXSize size) {
            return switch (size) {
                case XMM -> l128Features != null && features.containsAll(l128Features);
                case YMM -> l256Features != null && features.containsAll(l256Features);
                case ZMM -> false;
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(size);
            };
        }
    }

    /**
     * This is used to verify the availability of an evex-encoded instruction during code emission.
     */
    private enum EVEXFeatureAssertion {
        /*
         * With very few exceptions (namely, KMOV{B,D,Q}), all AVX512 instructions require AVX512F.
         * For simplicity, include AVX512F in all AVX512 assertions, and mention it in the name of
         * the assertion even though it is implied.
         */
        AVX512F_128(EnumSet.of(AVX512F), null, null),
        AVX512BW_128(EnumSet.of(AVX512BW), null, null),
        AVX512DQ_128(EnumSet.of(AVX512DQ), null, null),
        AVX512F_VL(EnumSet.of(AVX512F, AVX512VL), EnumSet.of(AVX512F, AVX512VL), EnumSet.of(AVX512F)),
        AVX512F_CD_VL(EnumSet.of(AVX512F, AVX512CD, AVX512VL), EnumSet.of(AVX512F, AVX512CD, AVX512VL), EnumSet.of(AVX512F, AVX512CD)),
        AVX512F_DQ_VL(EnumSet.of(AVX512F, AVX512DQ, AVX512VL), EnumSet.of(AVX512F, AVX512DQ, AVX512VL), EnumSet.of(AVX512F, AVX512DQ)),
        AVX512F_BW_VL(EnumSet.of(AVX512F, AVX512BW, AVX512VL), EnumSet.of(AVX512F, AVX512BW, AVX512VL), EnumSet.of(AVX512F, AVX512BW)),
        AVX512F_VL_256_512(null, EnumSet.of(AVX512F, AVX512VL), EnumSet.of(AVX512F)),
        AVX512F_DQ_VL_256_512(null, EnumSet.of(AVX512F, AVX512DQ, AVX512VL), EnumSet.of(AVX512F, AVX512DQ)),
        AVX512F_DQ_512(null, null, EnumSet.of(AVX512F, AVX512DQ)),
        AVX512F_512(null, null, EnumSet.of(AVX512F)),
        AVX512_VBMI_VL(EnumSet.of(CPUFeature.AVX512_VBMI, CPUFeature.AVX512VL), EnumSet.of(CPUFeature.AVX512_VBMI, CPUFeature.AVX512VL), EnumSet.of(CPUFeature.AVX512_VBMI)),
        AVX512_VBMI2_VL(EnumSet.of(CPUFeature.AVX512_VBMI2, CPUFeature.AVX512VL), EnumSet.of(CPUFeature.AVX512_VBMI2, CPUFeature.AVX512VL), EnumSet.of(CPUFeature.AVX512_VBMI2)),
        CLMUL_AVX512F_VL(EnumSet.of(CPUFeature.AVX512VL, CPUFeature.CLMUL), EnumSet.of(CPUFeature.AVX512VL, CPUFeature.CLMUL), EnumSet.of(CPUFeature.AVX512F, CPUFeature.CLMUL)),
        GFNI_AVX512F_VL(EnumSet.of(CPUFeature.AVX512VL, CPUFeature.GFNI), EnumSet.of(CPUFeature.AVX512VL, CPUFeature.GFNI), EnumSet.of(CPUFeature.AVX512F, CPUFeature.GFNI));

        private final EnumSet<CPUFeature> l128features;
        private final EnumSet<CPUFeature> l256features;
        private final EnumSet<CPUFeature> l512features;

        EVEXFeatureAssertion(EnumSet<CPUFeature> l128features, EnumSet<CPUFeature> l256features, EnumSet<CPUFeature> l512features) {
            this.l128features = l128features;
            this.l256features = l256features;
            this.l512features = l512features;
        }

        public boolean isValid(EnumSet<CPUFeature> features, AVXSize size) {
            return switch (size) {
                case XMM -> l128features != null && features.containsAll(l128features);
                case YMM -> l256features != null && features.containsAll(l256features);
                case ZMM -> l512features != null && features.containsAll(l512features);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(size);
            };
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
    private static final UnionRegisterCategory CPU_OR_XMM = new UnionRegisterCategory("CPU_OR_XMM", CPU, XMM);

    private static boolean categoryContains(RegisterCategory category, Register register) {
        return register.getRegisterCategory().equals(category) || (category instanceof UnionRegisterCategory && ((UnionRegisterCategory) category).contains(register));
    }

    /**
     * This is used to verify the availability of an instruction during code emission.
     */
    private enum VEXOpAssertion {
        AVX1(VEXFeatureAssertion.AVX1, null, XMM, XMM, XMM),
        AVX1_2(VEXFeatureAssertion.AVX1_AVX2, null, XMM, XMM, XMM),
        AVX2(VEXFeatureAssertion.AVX2, null, XMM, XMM, XMM),
        AVX1_128ONLY(VEXFeatureAssertion.AVX1_128, null, XMM, XMM, XMM),
        AVX1_256ONLY(VEXFeatureAssertion.AVX1_256, null, XMM, XMM, XMM),
        AVX2_256ONLY(VEXFeatureAssertion.AVX2_256, null, XMM, XMM, XMM),
        XMM_CPU(VEXFeatureAssertion.AVX1_128, null, XMM, null, CPU),
        XMM_XMM_CPU(VEXFeatureAssertion.AVX1_128, null, XMM, XMM, CPU),
        CPU_XMM(VEXFeatureAssertion.AVX1_128, null, CPU, null, XMM),
        AVX1_CPU_XMM(VEXFeatureAssertion.AVX1, null, CPU, null, XMM),
        AVX1_2_CPU_XMM(VEXFeatureAssertion.AVX1_AVX2, null, CPU, null, XMM),
        BMI1(VEXFeatureAssertion.BMI1, null, CPU, CPU, CPU),
        BMI2(VEXFeatureAssertion.BMI2, null, CPU, CPU, CPU),
        FMA(VEXFeatureAssertion.FMA, EVEXFeatureAssertion.AVX512F_VL, XMM, XMM, XMM),
        FMA_AVX512F_128ONLY(VEXFeatureAssertion.FMA_128, EVEXFeatureAssertion.AVX512F_128, XMM, XMM, XMM),

        AVX1_AVX512F_CPU_OR_XMM(VEXFeatureAssertion.AVX1_128, EVEXFeatureAssertion.AVX512F_128, CPU_OR_XMM, null, CPU_OR_XMM),
        XMM_CPU_AVX1_AVX512BW_128ONLY(VEXFeatureAssertion.AVX1_128, EVEXFeatureAssertion.AVX512BW_128, XMM, null, CPU),
        XMM_CPU_AVX1_AVX512DQ_128ONLY(VEXFeatureAssertion.AVX1_128, EVEXFeatureAssertion.AVX512DQ_128, XMM, null, CPU),
        CPU_XMM_AVX1_AVX512F_128ONLY(VEXFeatureAssertion.AVX1_128, EVEXFeatureAssertion.AVX512F_128, CPU, null, XMM),
        CPU_XMM_AVX512F_128ONLY(null, EVEXFeatureAssertion.AVX512F_128, CPU, null, XMM),
        XMM_XMM_CPU_AVX1_AVX512F_128ONLY(VEXFeatureAssertion.AVX1_128, EVEXFeatureAssertion.AVX512F_128, XMM, XMM, CPU),
        XMM_XMM_CPU_AVX1_AVX512BW_128ONLY(VEXFeatureAssertion.AVX1_128, EVEXFeatureAssertion.AVX512BW_128, XMM, XMM, CPU),
        XMM_XMM_CPU_AVX1_AVX512DQ_128ONLY(VEXFeatureAssertion.AVX1_128, EVEXFeatureAssertion.AVX512DQ_128, XMM, XMM, CPU),
        XMM_XMM_CPU_AVX512F_128ONLY(null, EVEXFeatureAssertion.AVX512F_128, XMM, XMM, CPU),
        XMM_CPU_AVX512BW_VL(null, EVEXFeatureAssertion.AVX512F_BW_VL, XMM, null, CPU),
        XMM_CPU_AVX512F_VL(null, EVEXFeatureAssertion.AVX512F_VL, XMM, null, CPU),
        AVX1_AVX512F_VL(VEXFeatureAssertion.AVX1, EVEXFeatureAssertion.AVX512F_VL, XMM, XMM, XMM),
        AVX1_AVX512DQ_VL(VEXFeatureAssertion.AVX1, EVEXFeatureAssertion.AVX512F_DQ_VL, XMM, XMM, XMM),
        AVX1_AVX512F_128(VEXFeatureAssertion.AVX1_128, EVEXFeatureAssertion.AVX512F_128, XMM, XMM, XMM),
        AVX1_AVX512F_VL_256_512(VEXFeatureAssertion.AVX1_256, EVEXFeatureAssertion.AVX512F_VL_256_512, XMM, XMM, XMM),
        AVX1_AVX2_AVX512F_VL(VEXFeatureAssertion.AVX1_AVX2, EVEXFeatureAssertion.AVX512F_VL, XMM, XMM, XMM),
        AVX1_AVX2_AVX512BW_VL(VEXFeatureAssertion.AVX1_AVX2, EVEXFeatureAssertion.AVX512F_BW_VL, XMM, XMM, XMM),
        AVX1_AVX2_AVX512DQ_VL(VEXFeatureAssertion.AVX1_AVX2, EVEXFeatureAssertion.AVX512F_DQ_VL, XMM, XMM, XMM),
        AVX2_AVX512F_VL(VEXFeatureAssertion.AVX2, EVEXFeatureAssertion.AVX512F_VL, XMM, XMM, XMM),
        AVX2_AVX512BW_VL(VEXFeatureAssertion.AVX2, EVEXFeatureAssertion.AVX512F_BW_VL, XMM, XMM, XMM),
        AVX2_AVX512F_VL_256_512(VEXFeatureAssertion.AVX2_256, EVEXFeatureAssertion.AVX512F_VL_256_512, XMM, XMM, XMM),
        AVX512F_VL(null, EVEXFeatureAssertion.AVX512F_VL, XMM, XMM, XMM),
        AVX512BW_VL(null, EVEXFeatureAssertion.AVX512F_BW_VL, XMM, XMM, XMM),
        AVX512DQ_VL(null, EVEXFeatureAssertion.AVX512F_DQ_VL, XMM, XMM, XMM),
        AVX512F_VL_256_512(null, EVEXFeatureAssertion.AVX512F_VL_256_512, XMM, XMM, XMM),
        AVX512DQ_VL_256_512(null, EVEXFeatureAssertion.AVX512F_DQ_VL_256_512, XMM, XMM, XMM),
        AVX512F_512ONLY(null, EVEXFeatureAssertion.AVX512F_512, XMM, XMM, XMM),
        AVX512DQ_512ONLY(null, EVEXFeatureAssertion.AVX512F_DQ_512, XMM, XMM, XMM),
        AVX512_VBMI_VL(null, EVEXFeatureAssertion.AVX512_VBMI_VL, XMM, XMM, XMM),
        AVX512_VBMI2_VL(null, EVEXFeatureAssertion.AVX512_VBMI2_VL, XMM, XMM, XMM),

        AVX512F_CPU_OR_MASK(VEXFeatureAssertion.AVX512F_L0, null, CPU_OR_MASK, null, CPU_OR_MASK),
        AVX512BW_CPU_OR_MASK(VEXFeatureAssertion.AVX512BW_L0, null, CPU_OR_MASK, null, CPU_OR_MASK),
        AVX512DQ_CPU_OR_MASK(VEXFeatureAssertion.AVX512DQ_L0, null, CPU_OR_MASK, null, CPU_OR_MASK),
        AVX512F_MASK_L0(VEXFeatureAssertion.AVX512F_L0, null, MASK, MASK, MASK),
        AVX512DQ_MASK_L0(VEXFeatureAssertion.AVX512DQ_L0, null, MASK, MASK, MASK),
        AVX512BW_MASK_L0(VEXFeatureAssertion.AVX512BW_L0, null, MASK, MASK, MASK),
        AVX512F_MASK_L1(VEXFeatureAssertion.AVX512F_L1, null, MASK, MASK, MASK),
        AVX512DQ_MASK_L1(VEXFeatureAssertion.AVX512DQ_L1, null, MASK, MASK, MASK),
        AVX512BW_MASK_L1(VEXFeatureAssertion.AVX512BW_L1, null, MASK, MASK, MASK),
        MASK_XMM_XMM_AVX512F_VL(null, EVEXFeatureAssertion.AVX512F_VL, MASK, XMM, XMM),
        MASK_XMM_XMM_AVX512BW_VL(null, EVEXFeatureAssertion.AVX512F_BW_VL, MASK, XMM, XMM),
        MASK_XMM_XMM_AVX512F_128(null, EVEXFeatureAssertion.AVX512F_128, MASK, XMM, XMM),
        MASK_XMM_AVX512BW_VL(null, EVEXFeatureAssertion.AVX512F_BW_VL, MASK, null, XMM),
        MASK_XMM_AVX512DQ_VL(null, EVEXFeatureAssertion.AVX512F_DQ_VL, MASK, null, XMM),
        MASK_XMM_AVX512DQ_128(null, EVEXFeatureAssertion.AVX512DQ_128, MASK, null, XMM),

        CLMUL_AVX1_AVX512F_VL(VEXFeatureAssertion.CLMUL_AVX1, EVEXFeatureAssertion.CLMUL_AVX512F_VL, XMM, XMM, XMM),
        AES_AVX1_128ONLY(VEXFeatureAssertion.AVX1_AES_128, null, XMM, XMM, XMM),
        GFNI_AVX1_AVX512F_VL(VEXFeatureAssertion.GFNI_AVX1, EVEXFeatureAssertion.GFNI_AVX512F_VL, XMM, XMM, XMM),
        F16C_AVX512F_VL(VEXFeatureAssertion.F16C, EVEXFeatureAssertion.AVX512F_VL, XMM, XMM, XMM);

        private final VEXFeatureAssertion vexFeatures;
        private final EVEXFeatureAssertion evexFeatures;

        private final RegisterCategory rCategory;
        private final RegisterCategory vCategory;
        private final RegisterCategory mCategory;

        VEXOpAssertion(VEXFeatureAssertion vexFeatures, EVEXFeatureAssertion evexFeatures, RegisterCategory rCategory, RegisterCategory vCategory, RegisterCategory mCategory) {
            this.vexFeatures = vexFeatures;
            this.evexFeatures = evexFeatures;
            this.rCategory = rCategory;
            this.vCategory = vCategory;
            this.mCategory = mCategory;
        }

        private boolean isValid(EnumSet<CPUFeature> features, AVXSize size) {
            return vexFeatures != null && vexFeatures.isValid(features, size) || evexFeatures != null && evexFeatures.isValid(features, size);
        }
    }

    /**
     * This is used to query the availability of operations, in contrast to {@link VEXOpAssertion}
     * where the {@link AVXSize} parameter is used purely as an encoding property, this class used
     * {@code AVXSize} to denote the size of the operations. As a result, it is generally expected
     * that if an operation is available with a large {@code AVXSize}, it is also available with
     * smaller ones.
     */
    public enum VectorFeatureAssertion {
        AVX1_AVX512F_VL(VEXOpAssertion.AVX1_AVX512F_VL),
        AVX1_AVX512DQ_VL(VEXOpAssertion.AVX1_AVX512DQ_VL),
        AVX1_AVX2_AVX512F_VL(VEXOpAssertion.AVX1_AVX2_AVX512F_VL),
        AVX1_AVX2_AVX512BW_VL(VEXOpAssertion.AVX1_AVX2_AVX512BW_VL),
        AVX1_AVX2_AVX512DQ_VL(VEXOpAssertion.AVX1_AVX2_AVX512DQ_VL),
        AVX2_AVX512F_VL(VEXOpAssertion.AVX2_AVX512F_VL),
        AVX2_AVX512BW_VL(VEXOpAssertion.AVX2_AVX512BW_VL),
        AVX512F_VL(VEXOpAssertion.AVX512F_VL),
        AVX512BW_VL(VEXOpAssertion.AVX512BW_VL),
        AVX512DQ_VL(VEXOpAssertion.AVX512DQ_VL),
        AVX512_VBMI2_VL(VEXOpAssertion.AVX512_VBMI2_VL),
        FMA(VEXOpAssertion.FMA);

        private final VEXOpAssertion opAssertion;

        VectorFeatureAssertion(VEXOpAssertion opAssertion) {
            this.opAssertion = opAssertion;
        }

        public boolean supports(EnumSet<CPUFeature> features, AVXSize size) {
            return opAssertion.isValid(features, size);
        }
    }

    public enum AMD64SIMDInstructionEncoding {
        VEX,
        EVEX;

        public static AMD64SIMDInstructionEncoding forFeatures(EnumSet<CPUFeature> features) {
            return AMD64BaseAssembler.supportsFullAVX512(features) ? EVEX : VEX;
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
        protected final boolean isEvex;

        /**
         * This field is used to link VEX and EVEX encoded instructions to allow for easy selection
         * between them via the {@link VexOp#encoding} method.
         */
        protected VexOp variant;

        protected VexOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex, boolean isEvex) {
            this.pp = pp;
            this.mmmmm = mmmmm;
            this.w = w;
            this.op = op;
            this.opcode = opcode;
            this.assertion = assertion;
            this.evexTuple = evexTuple;
            this.wEvex = wEvex;
            this.isEvex = isEvex;
            assert isEvex == opcode.startsWith("E") : "EVEX instructions should start with the letter 'E'! (" + opcode + ")";
            variant = null;
        }

        protected VexOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            this(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex, false);
        }

        protected VexOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            this(opcode, pp, mmmmm, w, op, assertion, EVEXTuple.INVALID, VEXPrefixConfig.WIG);
        }

        public boolean isSupported(AMD64Assembler asm, AVXSize size) {
            return assertion.isValid(asm.getFeatures(), size);
        }

        /**
         * This method provides the logic to be implemented by {@link VexOp#encoding}. The
         * 'encoding' method in each subclass simply needs to call this method and cast the result
         * to its own type.
         */
        protected final VexOp encodingLogic(AMD64SIMDInstructionEncoding encoding) {
            GraalError.guarantee(variant != null || isEvex == (encoding == AMD64SIMDInstructionEncoding.EVEX), "%s has no %s variant!", this, encoding);
            GraalError.guarantee(variant == null || this.isEvex != variant.isEvex, "Only pairs of VEX and EVEX instructions are allowed. (%s, %s)", this, variant);
            return switch (encoding) {
                case VEX -> isEvex ? variant : this;
                case EVEX -> isEvex ? this : variant;
            };
        }

        /**
         * Returns the VEX or EVEX variant of this operation, selected by the encoding parameter.
         * May be called on either variant, and returns that op itself or its opposite version.
         */
        public VexOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return encodingLogic(encoding);
        }

        @Override
        public String toString() {
            return opcode;
        }

        protected final int getDisp8Scale(boolean useEvex, AVXSize size) {
            return useEvex ? evexTuple.getDisp8ScalingFactor(size) : DEFAULT_DISP8_SCALE;
        }

        protected final void emitVexOrEvex(AMD64Assembler asm, Register dst, Register nds, Register src, AVXSize size, int actualPP, int actualMMMMM, int actualW, int actualWEvex) {
            emitVexOrEvex(asm, dst, nds, src, Register.None, size, actualPP, actualMMMMM, actualW, actualWEvex, Z0, B0);
        }

        protected final void emitVexOrEvex(AMD64Assembler asm, Register dst, Register nds, Register src, Register opmask, AVXSize size, int actualPP, int actualMMMMM, int actualW,
                        int actualWEvex, int z, int b) {
            AVXSize avxSize = size;
            if (avxSize == AVXSize.DWORD || avxSize == AVXSize.QWORD) {
                avxSize = AVXSize.XMM;
            }
            if (isEvex) {
                checkEvex(asm, avxSize, dst, opmask, z, nds, src, b);
                asm.evexPrefix(dst, opmask, nds, src, avxSize, actualPP, actualMMMMM, actualWEvex, z, b);
            } else {
                checkVex(asm, avxSize, dst, opmask, z, nds, src, b);
                asm.emitVEX(getLFlag(avxSize), actualPP, actualMMMMM, actualW, getRXB(dst, src), nds.isValid() ? nds.encoding() : 0);
            }
        }

        protected final void emitVexOrEvex(AMD64Assembler asm, Register dst, Register nds, AMD64Address src, AVXSize size, int actualPP, int actualMMMMM, int actualW, int actualWEvex) {
            emitVexOrEvex(asm, dst, nds, src, Register.None, size, actualPP, actualMMMMM, actualW, actualWEvex, Z0, B0);
        }

        protected final void emitVexOrEvex(AMD64Assembler asm, Register dst, Register nds, AMD64Address src, Register opmask, AVXSize size, int actualPP, int actualMMMMM, int actualW,
                        int actualWEvex, int z, int b) {
            asm.interceptMemorySrcOperands(src);
            emitVexOrEvexImpl(asm, dst, nds, src, opmask, size, actualPP, actualMMMMM, actualW, actualWEvex, z, b);
        }

        protected final void emitVexOrEvex(AMD64Assembler asm, AMD64Address dst, Register nds, Register src, AVXSize size, int actualPP, int actualMMMMM, int actualW, int actualWEvex) {
            emitVexOrEvex(asm, dst, nds, src, Register.None, size, actualPP, actualMMMMM, actualW, actualWEvex, Z0, B0);
        }

        protected final void emitVexOrEvex(AMD64Assembler asm, AMD64Address dst, Register nds, Register src, Register opmask, AVXSize size, int actualPP, int actualMMMMM, int actualW,
                        int actualWEvex, int z, int b) {
            emitVexOrEvexImpl(asm, src, nds, dst, opmask, size, actualPP, actualMMMMM, actualW, actualWEvex, z, b);
        }

        private void emitVexOrEvexImpl(AMD64Assembler asm, Register reg1, Register reg2, AMD64Address addr, Register opmask, AVXSize size, int actualPP, int actualMMMMM, int actualW,
                        int actualWEvex, int z, int b) {
            if (isEvex) {
                checkEvex(asm, size, reg1, opmask, z, reg2, null, b);
                asm.evexPrefix(reg1, opmask, reg2, addr, size, actualPP, actualMMMMM, actualWEvex, z, b);
            } else {
                checkVex(asm, size, reg1, opmask, z, reg2, null, b);
                asm.emitVEX(getLFlag(size), actualPP, actualMMMMM, actualW, getRXB(reg1, addr), reg2.isValid() ? reg2.encoding() : 0);
            }
        }

        private void checkVex(AMD64Assembler asm, AVXSize size, Register dst, Register mask, int z, Register nds, Register src, int b) {
            GraalError.guarantee(mask.equals(Register.None) && z == Z0, "{%s}%s", mask.equals(Register.None) ? mask.name : "K0", z == Z0 ? "" : " {z}");
            GraalError.guarantee(b == B0, "illegal EVEX.b");
            GraalError.guarantee(dst == null || (!isAVX512Register(dst) && categoryContains(assertion.rCategory, dst)), "instruction %s illegal operand %s", opcode, dst);
            GraalError.guarantee(!nds.isValid() || (!isAVX512Register(nds) && categoryContains(assertion.vCategory, nds)), "instruction %s illegal operand %s", opcode, nds);
            GraalError.guarantee(src == null || (!isAVX512Register(src) && categoryContains(assertion.mCategory, src)), "instruction %s illegal operand %s", opcode, src);
            GraalError.guarantee(assertion.vexFeatures.isValid(asm.getFeatures(), size), "instruction %s not supported for size %s", opcode, size);
        }

        private void checkEvex(AMD64Assembler asm, AVXSize size, Register dst, Register mask, int z, Register nds, Register src, int b) {
            GraalError.guarantee(mask.isValid() || z == Z0, "illegal EVEX.z for no mask");
            GraalError.guarantee(src == null || b == B0, "illegal EVEX.b for register operand %s", src);
            GraalError.guarantee(dst == null || categoryContains(assertion.rCategory, dst), "instruction %s illegal operand %s", opcode, dst);
            GraalError.guarantee(!nds.isValid() || categoryContains(assertion.vCategory, nds), "instruction %s illegal operand %s", opcode, nds);
            GraalError.guarantee(src == null || categoryContains(assertion.mCategory, src), "instruction %s illegal operand %s", opcode, src);
            GraalError.guarantee(assertion.evexFeatures.isValid(asm.getFeatures(), size), "instruction %s not supported for size %s", opcode, size);
        }
    }

    /**
     * VEX-encoded instructions with an operand order of RM, but the M operand must be a register.
     */
    public static class VexRROp extends VexOp {
        // @formatter:off
        public static final VexRROp VMASKMOVDQU       = new VexRROp("VMASKMOVDQU",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xF7, VEXOpAssertion.AVX1_128ONLY);

        public static final VexRROp EVPBROADCASTB_GPR = new VexRROp("EVPBROADCASTB_GPR", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x7A, VEXOpAssertion.XMM_CPU_AVX512BW_VL, EVEXTuple.T1S_8BIT,  VEXPrefixConfig.W0, true);
        public static final VexRROp EVPBROADCASTW_GPR = new VexRROp("EVPBROADCASTW_GPR", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x7B, VEXOpAssertion.XMM_CPU_AVX512BW_VL, EVEXTuple.T1S_16BIT, VEXPrefixConfig.W0, true);
        public static final VexRROp EVPBROADCASTD_GPR = new VexRROp("EVPBROADCASTD_GPR", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x7C, VEXOpAssertion.XMM_CPU_AVX512F_VL,  EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0, true);
        public static final VexRROp EVPBROADCASTQ_GPR = new VexRROp("EVPBROADCASTQ_GPR", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x7C, VEXOpAssertion.XMM_CPU_AVX512F_VL,  EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1, true);

        public static final VexRROp KTESTB            = new VexRROp("KTESTB",            VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0x99, VEXOpAssertion.AVX512DQ_MASK_L0);
        public static final VexRROp KTESTW            = new VexRROp("KTESTW",            VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0x99, VEXOpAssertion.AVX512DQ_MASK_L0);
        public static final VexRROp KTESTD            = new VexRROp("KTESTD",            VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W1,  0x99, VEXOpAssertion.AVX512BW_MASK_L0);
        public static final VexRROp KTESTQ            = new VexRROp("KTESTQ",            VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.W1,  0x99, VEXOpAssertion.AVX512BW_MASK_L0);

        public static final VexRROp KORTESTB          = new VexRROp("KORTESTB",          VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0x98, VEXOpAssertion.AVX512DQ_MASK_L0);
        public static final VexRROp KORTESTW          = new VexRROp("KORTESTW",          VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0x98, VEXOpAssertion.AVX512F_MASK_L0);
        public static final VexRROp KORTESTD          = new VexRROp("KORTESTD",          VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W1,  0x98, VEXOpAssertion.AVX512BW_MASK_L0);
        public static final VexRROp KORTESTQ          = new VexRROp("KORTESTQ",          VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.W1,  0x98, VEXOpAssertion.AVX512BW_MASK_L0);

        public static final VexRROp KNOTB             = new VexRROp("KNOTB",             VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0x44, VEXOpAssertion.AVX512DQ_MASK_L0);
        public static final VexRROp KNOTW             = new VexRROp("KNOTW",             VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0x44, VEXOpAssertion.AVX512F_MASK_L0);
        public static final VexRROp KNOTD             = new VexRROp("KNOTD",             VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W1,  0x44, VEXOpAssertion.AVX512BW_MASK_L0);
        public static final VexRROp KNOTQ             = new VexRROp("KNOTQ",             VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.W1,  0x44, VEXOpAssertion.AVX512BW_MASK_L0);
        // @formatter:on

        protected VexRROp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex, boolean isEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex, isEvex);
        }

        protected VexRROp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            this(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex, false);
        }

        protected VexRROp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            this(opcode, pp, mmmmm, w, op, assertion, EVEXTuple.INVALID, w);
        }

        @Override
        public VexRROp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexRROp) encodingLogic(encoding);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src) {
            emit(asm, size, dst, src, Register.None, Z0, B0);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src, Register mask, int z, int b) {
            emitVexOrEvex(asm, dst, Register.None, src, mask, size, pp, mmmmm, w, wEvex, z, b);
            asm.emitByte(op);
            asm.emitModRM(dst, src);
        }
    }

    /**
     * VEX-encoded instructions with an operand order of RM.
     */
    public static class VexRMOp extends VexRROp {
        // @formatter:off
        public static final VexRMOp VAESIMC         = new VexRMOp("VAESIMC",         VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0xDB, VEXOpAssertion.AES_AVX1_128ONLY);
        public static final VexRMOp VCVTTSS2SI      = new VexRMOp("VCVTTSS2SI",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0x2C, VEXOpAssertion.CPU_XMM_AVX1_AVX512F_128ONLY, EVEXTuple.T1F_32BIT, VEXPrefixConfig.W0);
        public static final VexRMOp VCVTTSS2SQ      = new VexRMOp("VCVTTSS2SQ",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W1,  0x2C, VEXOpAssertion.CPU_XMM_AVX1_AVX512F_128ONLY, EVEXTuple.T1F_32BIT, VEXPrefixConfig.W1);
        public static final VexRMOp VCVTTSD2SI      = new VexRMOp("VCVTTSD2SI",      VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0x2C, VEXOpAssertion.CPU_XMM_AVX1_AVX512F_128ONLY, EVEXTuple.T1F_64BIT, VEXPrefixConfig.W0);
        public static final VexRMOp VCVTTSD2SQ      = new VexRMOp("VCVTTSD2SQ",      VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W1,  0x2C, VEXOpAssertion.CPU_XMM_AVX1_AVX512F_128ONLY, EVEXTuple.T1F_64BIT, VEXPrefixConfig.W1);
        public static final VexRMOp VCVTPS2PD       = new VexRMOp("VCVTPS2PD",       VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5A, VEXOpAssertion.AVX1_AVX512F_VL,           EVEXTuple.HVM,       VEXPrefixConfig.W0);
        public static final VexRMOp VCVTPD2PS       = new VexRMOp("VCVTPD2PS",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5A, VEXOpAssertion.AVX1_AVX512F_VL,           EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRMOp VCVTDQ2PS       = new VexRMOp("VCVTDQ2PS",       VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5B, VEXOpAssertion.AVX1_AVX512F_VL,           EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRMOp VCVTTPS2DQ      = new VexRMOp("VCVTTPS2DQ",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5B, VEXOpAssertion.AVX1_AVX512F_VL,           EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRMOp VCVTTPD2DQ      = new VexRMOp("VCVTTPD2DQ",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xE6, VEXOpAssertion.AVX1_AVX512F_VL,           EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRMOp VCVTDQ2PD       = new VexRMOp("VCVTDQ2PD",       VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xE6, VEXOpAssertion.AVX1_AVX512F_VL,           EVEXTuple.HVM,       VEXPrefixConfig.W0);
        public static final VexRMOp VMOVDDUP        = new VexRMOp("VMOVDDUP",        VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x12, VEXOpAssertion.AVX1_AVX512F_VL,           EVEXTuple.DUP,       VEXPrefixConfig.W1);
        public static final VexRMOp VBROADCASTSS    = new VexRMOp("VBROADCASTSS",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x18, VEXOpAssertion.AVX1_AVX512F_VL,           EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRMOp VBROADCASTSD    = new VexRMOp("VBROADCASTSD",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x19, VEXOpAssertion.AVX1_AVX512F_VL_256_512,   EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRMOp VBROADCASTF128  = new VexRMOp("VBROADCASTF128",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x1A, VEXOpAssertion.AVX1_AVX512F_VL_256_512,   EVEXTuple.T4_32BIT,  VEXPrefixConfig.W0);
        public static final VexRMOp VPBROADCASTI128 = new VexRMOp("VPBROADCASTI128", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x5A, VEXOpAssertion.AVX2_AVX512F_VL_256_512,   EVEXTuple.T4_32BIT,  VEXPrefixConfig.W0);
        public static final VexRMOp VPBROADCASTB    = new VexRMOp("VPBROADCASTB",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x78, VEXOpAssertion.AVX2_AVX512BW_VL,          EVEXTuple.T1S_8BIT,  VEXPrefixConfig.W0);
        public static final VexRMOp VPBROADCASTW    = new VexRMOp("VPBROADCASTW",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x79, VEXOpAssertion.AVX2_AVX512BW_VL,          EVEXTuple.T1S_16BIT, VEXPrefixConfig.W0);
        public static final VexRMOp VPBROADCASTD    = new VexRMOp("VPBROADCASTD",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x58, VEXOpAssertion.AVX2_AVX512F_VL,           EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRMOp VPBROADCASTQ    = new VexRMOp("VPBROADCASTQ",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x59, VEXOpAssertion.AVX2_AVX512F_VL,           EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRMOp VPMOVMSKB       = new VexRMOp("VPMOVMSKB",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xD7, VEXOpAssertion.AVX1_2_CPU_XMM);
        public static final VexRMOp VMOVMSKPD       = new VexRMOp("VMOVMSKPD",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x50, VEXOpAssertion.AVX1_CPU_XMM);
        public static final VexRMOp VMOVMSKPS       = new VexRMOp("VMOVMSKPS",       VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x50, VEXOpAssertion.AVX1_CPU_XMM);

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
        public static final VexRMOp VPTEST          = new VexRMOp("VPTEST",          VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x17, VEXOpAssertion.AVX1);
        public static final VexRMOp VSQRTPD         = new VexRMOp("VSQRTPD",         VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x51, VEXOpAssertion.AVX1_AVX512F_VL,           EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRMOp VSQRTPS         = new VexRMOp("VSQRTPS",         VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x51, VEXOpAssertion.AVX1_AVX512F_VL,           EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRMOp VUCOMISS        = new VexRMOp("VUCOMISS",        VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x2E, VEXOpAssertion.AVX1_AVX512F_128,          EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRMOp VUCOMISD        = new VexRMOp("VUCOMISD",        VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x2E, VEXOpAssertion.AVX1_AVX512F_128,          EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRMOp VPABSB          = new VexRMOp("VPABSB",          VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x1C, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,     EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRMOp VPABSW          = new VexRMOp("VPABSW",          VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x1D, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,     EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRMOp VPABSD          = new VexRMOp("VPABSD",          VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x1E, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,      EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRMOp VCVTPH2PS       = new VexRMOp("VCVTPH2PS",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x13, VEXOpAssertion.F16C_AVX512F_VL,           EVEXTuple.HVM,       VEXPrefixConfig.W0);

        // EVEX encoded instructions
        public static final VexRMOp EVCVTTSS2SI     = new VexRMOp("EVCVTTSS2SI",     VCVTTSS2SI);
        public static final VexRMOp EVCVTTSS2SQ     = new VexRMOp("EVCVTTSS2SQ",     VCVTTSS2SQ);
        public static final VexRMOp EVCVTTSS2USI    = new VexRMOp("EVCVTTSS2USI",    VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0x78, VEXOpAssertion.CPU_XMM_AVX512F_128ONLY,   EVEXTuple.T1F_32BIT, VEXPrefixConfig.W0, true);
        public static final VexRMOp EVCVTTSS2USQ    = new VexRMOp("EVCVTTSS2USQ",    VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W1,  0x78, VEXOpAssertion.CPU_XMM_AVX512F_128ONLY,   EVEXTuple.T1F_32BIT, VEXPrefixConfig.W1, true);
        public static final VexRMOp EVCVTTSD2SI     = new VexRMOp("EVCVTTSD2SI",     VCVTTSD2SI);
        public static final VexRMOp EVCVTTSD2SQ     = new VexRMOp("EVCVTTSD2SQ",     VCVTTSD2SQ);
        public static final VexRMOp EVCVTTSD2USI    = new VexRMOp("EVCVTTSD2USI",    VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0x78, VEXOpAssertion.CPU_XMM_AVX512F_128ONLY,   EVEXTuple.T1F_64BIT, VEXPrefixConfig.W0, true);
        public static final VexRMOp EVCVTTSD2USQ    = new VexRMOp("EVCVTTSD2USQ",    VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W1,  0x78, VEXOpAssertion.CPU_XMM_AVX512F_128ONLY,   EVEXTuple.T1F_64BIT, VEXPrefixConfig.W1, true);
        public static final VexRMOp EVCVTPS2PD      = new VexRMOp("EVCVTPS2PD",      VCVTPS2PD);
        public static final VexRMOp EVCVTPD2PS      = new VexRMOp("EVCVTPD2PS",      VCVTPD2PS);
        public static final VexRMOp EVCVTDQ2PS      = new VexRMOp("EVCVTDQ2PS",      VCVTDQ2PS);
        public static final VexRMOp EVCVTQQ2PS      = new VexRMOp("EVCVTQQ2PS",      VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.W1,  0x5B, VEXOpAssertion.AVX512DQ_VL,               EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRMOp EVCVTQQ2PD      = new VexRMOp("EVCVTQQ2PD",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W1,  0xE6, VEXOpAssertion.AVX512DQ_VL,               EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRMOp EVCVTTPS2DQ     = new VexRMOp("EVCVTTPS2DQ",     VCVTTPS2DQ);
        public static final VexRMOp EVCVTTPS2QQ     = new VexRMOp("EVCVTTPS2QQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0x7A, VEXOpAssertion.AVX512DQ_VL,               EVEXTuple.HVM,       VEXPrefixConfig.W0, true);
        public static final VexRMOp EVCVTTPD2DQ     = new VexRMOp("EVCVTTPD2DQ",     VCVTTPD2DQ);
        public static final VexRMOp EVCVTTPD2QQ     = new VexRMOp("EVCVTTPD2QQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W1,  0x7A, VEXOpAssertion.AVX512DQ_VL,               EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRMOp EVCVTDQ2PD      = new VexRMOp("EVCVTDQ2PD",      VCVTDQ2PD);
        public static final VexRMOp EVMOVDDUP       = new VexRMOp("EVMOVDDUP",       VMOVDDUP);
        public static final VexRMOp EVBROADCASTSS   = new VexRMOp("EVBROADCASTSS",   VBROADCASTSS);
        public static final VexRMOp EVBROADCASTSD   = new VexRMOp("EVBROADCASTSD",   VBROADCASTSD);
        public static final VexRMOp EVPBROADCASTB   = new VexRMOp("EVPBROADCASTB",   VPBROADCASTB);
        public static final VexRMOp EVPBROADCASTW   = new VexRMOp("EVPBROADCASTW",   VPBROADCASTW);
        public static final VexRMOp EVPBROADCASTD   = new VexRMOp("EVPBROADCASTD",   VPBROADCASTD);
        public static final VexRMOp EVPBROADCASTQ   = new VexRMOp("EVPBROADCASTQ",   VPBROADCASTQ);
        public static final VexRMOp EVPMOVB2M       = new VexRMOp("EVPMOVB2M",       VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x29, VEXOpAssertion.MASK_XMM_AVX512BW_VL,      EVEXTuple.FVM,       VEXPrefixConfig.W0, true);
        public static final VexRMOp EVPMOVW2M       = new VexRMOp("EVPMOVW2M",       VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x29, VEXOpAssertion.MASK_XMM_AVX512BW_VL,      EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRMOp EVPMOVD2M       = new VexRMOp("EVPMOVD2M",       VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x39, VEXOpAssertion.MASK_XMM_AVX512DQ_VL,      EVEXTuple.FVM,       VEXPrefixConfig.W0, true);
        public static final VexRMOp EVPMOVQ2M       = new VexRMOp("EVPMOVQ2M",       VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x39, VEXOpAssertion.MASK_XMM_AVX512DQ_VL,      EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRMOp EVPMOVSXBW      = new VexRMOp("EVPMOVSXBW",      VPMOVSXBW);
        public static final VexRMOp EVPMOVSXBD      = new VexRMOp("EVPMOVSXBD",      VPMOVSXBD);
        public static final VexRMOp EVPMOVSXBQ      = new VexRMOp("EVPMOVSXBQ",      VPMOVSXBQ);
        public static final VexRMOp EVPMOVSXWD      = new VexRMOp("EVPMOVSXWD",      VPMOVSXWD);
        public static final VexRMOp EVPMOVSXWQ      = new VexRMOp("EVPMOVSXWQ",      VPMOVSXWQ);
        public static final VexRMOp EVPMOVSXDQ      = new VexRMOp("EVPMOVSXDQ",      VPMOVSXDQ);
        public static final VexRMOp EVPMOVZXBW      = new VexRMOp("EVPMOVZXBW",      VPMOVZXBW);
        public static final VexRMOp EVPMOVZXBD      = new VexRMOp("EVPMOVZXBD",      VPMOVZXBD);
        public static final VexRMOp EVPMOVZXBQ      = new VexRMOp("EVPMOVZXBQ",      VPMOVZXBQ);
        public static final VexRMOp EVPMOVZXWD      = new VexRMOp("EVPMOVZXWD",      VPMOVZXWD);
        public static final VexRMOp EVPMOVZXWQ      = new VexRMOp("EVPMOVZXWQ",      VPMOVZXWQ);
        public static final VexRMOp EVPMOVZXDQ      = new VexRMOp("EVPMOVZXDQ",      VPMOVZXDQ);
        public static final VexRMOp EVSQRTPD        = new VexRMOp("EVSQRTPD",        VSQRTPD);
        public static final VexRMOp EVSQRTPS        = new VexRMOp("EVSQRTPS",        VSQRTPS);
        public static final VexRMOp EVUCOMISS       = new VexRMOp("EVUCOMISS",       VUCOMISS);
        public static final VexRMOp EVUCOMISD       = new VexRMOp("EVUCOMISD",       VUCOMISD);
        public static final VexRMOp EVPABSB         = new VexRMOp("EVPABSB",         VPABSB);
        public static final VexRMOp EVPABSW         = new VexRMOp("EVPABSW",         VPABSW);
        public static final VexRMOp EVPABSD         = new VexRMOp("EVPABSD",         VPABSD);
        public static final VexRMOp EVPABSQ         = new VexRMOp("EVPABSQ",         VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x1F, VEXOpAssertion.AVX512F_VL,                EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRMOp EVCVTPH2PS      = new VexRMOp("EVCVTPH2PS",      VCVTPH2PS);

        public static final VexRMOp EVPEXPANDB      = new VexRMOp("EVPEXPANDB",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x62, VEXOpAssertion.AVX512_VBMI2_VL,           EVEXTuple.T1S_8BIT,  VEXPrefixConfig.W0, true);
        public static final VexRMOp EVPEXPANDW      = new VexRMOp("EVPEXPANDW",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x62, VEXOpAssertion.AVX512_VBMI2_VL,           EVEXTuple.T1S_16BIT, VEXPrefixConfig.W1, true);
        public static final VexRMOp EVPEXPANDD      = new VexRMOp("EVPEXPANDD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x89, VEXOpAssertion.AVX512F_VL,                EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0, true);
        public static final VexRMOp EVPEXPANDQ      = new VexRMOp("EVPEXPANDQ",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x89, VEXOpAssertion.AVX512F_VL,                EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1, true);
        // @formatter:on

        protected VexRMOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            this(opcode, pp, mmmmm, w, op, assertion, EVEXTuple.INVALID, VEXPrefixConfig.WIG);
        }

        protected VexRMOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
        }

        protected VexRMOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex, boolean isEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex, isEvex);
        }

        /**
         * Build the EVEX variant of a given vexOp.
         */
        protected VexRMOp(String opcode, VexRMOp vexOp) {
            super(opcode, vexOp.pp, vexOp.mmmmm, vexOp.w, vexOp.op, vexOp.assertion, vexOp.evexTuple, vexOp.wEvex, true);
            variant = vexOp;
            assert vexOp.variant == null : "found 2 EVEX variants for VEX instruction " + vexOp;
            vexOp.variant = this;
        }

        protected VexRMOp(String opcode, VexRMOp vexOp, EVEXTuple evexTuple, int wEvex) {
            super(opcode, vexOp.pp, vexOp.mmmmm, vexOp.w, vexOp.op, vexOp.assertion, evexTuple, wEvex);
        }

        @Override
        public VexRMOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexRMOp) encodingLogic(encoding);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, AMD64Address src) {
            emit(asm, size, dst, src, Register.None, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src, Register mask, int z, int b) {
            emitVexOrEvex(asm, dst, Register.None, src, mask, size, pp, mmmmm, w, wEvex, z, b);
            asm.emitByte(op);
            asm.emitModRM(dst, src);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, AMD64Address src, Register mask, int z, int b) {
            emitVexOrEvex(asm, dst, Register.None, src, mask, size, pp, mmmmm, w, wEvex, z, b);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src, 0, getDisp8Scale(isEvex, size));
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

        protected VexGeneralMoveOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex, boolean isEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex, isEvex);
        }

        public abstract void emit(AMD64Assembler asm, AVXSize size, AMD64Address dst, Register src);
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
        // VexRVMOp.VMOVSS/VMOVSD utilize the merge semantics, while these simply move the whole
        // XMM register from src to dst
        public static final VexMoveOp VMOVSS    = new VexMoveOp("VMOVSS",    VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0x10, 0x11, VEXOpAssertion.AVX1_AVX512F_128,        EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexMoveOp VMOVSD    = new VexMoveOp("VMOVSD",    VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0x10, 0x11, VEXOpAssertion.AVX1_AVX512F_128,        EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexMoveOp VMOVD     = new VexMoveOp("VMOVD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0,  0x6E, 0x7E, VEXOpAssertion.AVX1_AVX512F_CPU_OR_XMM, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexMoveOp VMOVQ     = new VexMoveOp("VMOVQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1,  0x6E, 0x7E, VEXOpAssertion.AVX1_AVX512F_CPU_OR_XMM, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);

        // EVEX encoded instructions
        public static final VexMoveOp EVMOVDQA32 = new VexMoveOp("EVMOVDQA32", VMOVDQA32);
        public static final VexMoveOp EVMOVDQA64 = new VexMoveOp("EVMOVDQA64", VMOVDQA64);
        public static final VexMoveOp EVMOVDQU8  = new VexMoveOp("EVMOVDQU8",  VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x6F, 0x7F, VEXOpAssertion.AVX512BW_VL, EVEXTuple.FVM, VEXPrefixConfig.W0, true);
        public static final VexMoveOp EVMOVDQU16 = new VexMoveOp("EVMOVDQU16", VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0x6F, 0x7F, VEXOpAssertion.AVX512BW_VL, EVEXTuple.FVM, VEXPrefixConfig.W1, true);
        public static final VexMoveOp EVMOVDQU32 = new VexMoveOp("EVMOVDQU32", VMOVDQU32);
        public static final VexMoveOp EVMOVDQU64 = new VexMoveOp("EVMOVDQU64", VMOVDQU64);
        public static final VexMoveOp EVMOVAPS   = new VexMoveOp("EVMOVAPS",   VMOVAPS);
        public static final VexMoveOp EVMOVAPD   = new VexMoveOp("EVMOVAPD",   VMOVAPD);
        public static final VexMoveOp EVMOVUPS   = new VexMoveOp("EVMOVUPS",   VMOVUPS);
        public static final VexMoveOp EVMOVUPD   = new VexMoveOp("EVMOVUPD",   VMOVUPD);
        public static final VexMoveOp EVMOVSS    = new VexMoveOp("EVMOVSS",    VMOVSS);
        public static final VexMoveOp EVMOVSD    = new VexMoveOp("EVMOVSD",    VMOVSD);
        public static final VexMoveOp EVMOVD     = new VexMoveOp("EVMOVD",     VMOVD);
        public static final VexMoveOp EVMOVQ     = new VexMoveOp("EVMOVQ",     VMOVQ);
        // @formatter:on

        private final int opReverse;

        private VexMoveOp(String opcode, int pp, int mmmmm, int w, int op, int opReverse, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
            this.opReverse = opReverse;
        }

        private VexMoveOp(String opcode, int pp, int mmmmm, int w, int op, int opReverse, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex, boolean isEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex, isEvex);
            this.opReverse = opReverse;
        }

        /**
         * Build the EVEX variant of a given vexOp.
         */
        private VexMoveOp(String opcode, VexMoveOp vexOp) {
            super(opcode, vexOp.pp, vexOp.mmmmm, vexOp.w, vexOp.op, vexOp.assertion, vexOp.evexTuple, vexOp.wEvex, true);
            this.opReverse = vexOp.opReverse;
            variant = vexOp;
            assert vexOp.variant == null : "found 2 EVEX variants for VEX instruction " + vexOp;
            vexOp.variant = this;
        }

        @Override
        public VexMoveOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexMoveOp) encodingLogic(encoding);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, AMD64Address dst, Register src) {
            emitVexOrEvex(asm, dst, Register.None, src, size, pp, mmmmm, w, wEvex);
            asm.emitByte(opReverse);
            asm.emitOperandHelper(src, dst, 0, getDisp8Scale(isEvex, size));
        }

        public void emit(AMD64Assembler asm, AVXKind.AVXSize size, AMD64Address dst, Register src, Register mask) {
            emitVexOrEvex(asm, dst, Register.None, src, mask, size, pp, mmmmm, w, wEvex, Z0, B0);
            asm.emitByte(opReverse);
            asm.emitOperandHelper(src, dst, 0, getDisp8Scale(isEvex, size));
        }

        public void emitReverse(AMD64Assembler asm, AVXSize size, Register dst, Register src) {
            Register nds = (this == VMOVSS || this == VMOVSD || this == EVMOVSS || this == EVMOVSD) ? src : Register.None;
            emitVexOrEvex(asm, src, nds, dst, size, pp, mmmmm, w, wEvex);
            asm.emitByte(opReverse);
            asm.emitModRM(src, dst);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src) {
            emit(asm, size, dst, src, Register.None, Z0, B0);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src, Register mask, int z, int b) {
            // MOVSS/SD are RVM instruction when both operands are registers
            Register nds = (this == VMOVSS || this == VMOVSD || this == EVMOVSS || this == EVMOVSD) ? src : Register.None;
            emitVexOrEvex(asm, dst, nds, src, mask, size, pp, mmmmm, w, wEvex, z, b);
            asm.emitByte(op);
            asm.emitModRM(dst, src);
        }

        public void emit(AMD64Assembler asm, AVXSize size, AMD64Address dst, Register src, Register mask, int z, int b) {
            emitVexOrEvex(asm, dst, Register.None, src, mask, size, pp, mmmmm, w, wEvex, z, b);
            asm.emitByte(opReverse);
            asm.emitOperandHelper(src, dst, 0, getDisp8Scale(isEvex, size));
        }
    }

    public interface VexRRIOp {
        void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src, int imm8);
    }

    public static class VexMaskRRIOp extends VexOp implements VexRRIOp {
        // @formatter:off
        public static final VexMaskRRIOp KSHIFTRB = new VexMaskRRIOp("KSHIFTRB", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x30, VEXOpAssertion.AVX512DQ_MASK_L0);
        public static final VexMaskRRIOp KSHIFTRW = new VexMaskRRIOp("KSHIFTRW", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0x30, VEXOpAssertion.AVX512F_MASK_L0);
        public static final VexMaskRRIOp KSHIFTRD = new VexMaskRRIOp("KSHIFTRD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x31, VEXOpAssertion.AVX512BW_MASK_L0);
        public static final VexMaskRRIOp KSHIFTRQ = new VexMaskRRIOp("KSHIFTRQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0x31, VEXOpAssertion.AVX512BW_MASK_L0);
        // @formatter:on

        protected VexMaskRRIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion, EVEXTuple.INVALID, VEXPrefixConfig.WIG);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src, int imm8) {
            emitVexOrEvex(asm, dst, Register.None, src, Register.None, size, pp, mmmmm, w, wEvex, Z0, B0);
            asm.emitByte(op);
            asm.emitModRM(dst, src);
            asm.emitByte(imm8);
        }
    }

    /**
     * VEX-encoded instructions with an operand order of RMI.
     */
    public static class VexRMIOp extends VexOp implements VexRRIOp {
        // @formatter:off
        public static final VexRMIOp VAESKEYGENASSIST = new VexRMIOp("VAESKEYGENASSIST", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.WIG, 0xDF, VEXOpAssertion.AES_AVX1_128ONLY);
        public static final VexRMIOp VPERMQ           = new VexRMIOp("VPERMQ",           VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x00, VEXOpAssertion.AVX2_AVX512F_VL_256_512, EVEXTuple.FVM, VEXPrefixConfig.W1);
        public static final VexRMIOp VPSHUFLW         = new VexRMIOp("VPSHUFLW",         VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x70, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,   EVEXTuple.FVM, VEXPrefixConfig.WIG);
        public static final VexRMIOp VPSHUFHW         = new VexRMIOp("VPSHUFHW",         VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x70, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,   EVEXTuple.FVM, VEXPrefixConfig.WIG);
        public static final VexRMIOp VPSHUFD          = new VexRMIOp("VPSHUFD",          VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x70, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,    EVEXTuple.FVM, VEXPrefixConfig.W0);
        public static final VexRMIOp VPERMILPD        = new VexRMIOp("VPERMILPD",        VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x05, VEXOpAssertion.AVX1_AVX512F_VL,         EVEXTuple.FVM, VEXPrefixConfig.W1);
        public static final VexRMIOp VPERMILPS        = new VexRMIOp("VPERMILPS",        VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x04, VEXOpAssertion.AVX1_AVX512F_VL,         EVEXTuple.FVM, VEXPrefixConfig.W0);
        public static final VexRMIOp VPERMPD          = new VexRMIOp("VPERMPD",          VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x01, VEXOpAssertion.AVX2_AVX512F_VL_256_512, EVEXTuple.FVM, VEXPrefixConfig.W1);
        public static final VexRMIOp RORXL            = new VexRMIOp("RORXL",            VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0xF0, VEXOpAssertion.BMI2);
        public static final VexRMIOp RORXQ            = new VexRMIOp("RORXQ",            VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0xF0, VEXOpAssertion.BMI2);

        // EVEX encoded instructions
        public static final VexRMIOp EVPERMILPD       = new VexRMIOp("EVPERMILPD",       VPERMILPD);
        public static final VexRMIOp EVPERMILPS       = new VexRMIOp("EVPERMILPS",       VPERMILPS);
        public static final VexRMIOp EVPERMPD         = new VexRMIOp("EVPERMPD",         VPERMPD);
        public static final VexRMIOp EVPERMQ          = new VexRMIOp("EVPERMQ",          VPERMQ);
        public static final VexRMIOp EVPSHUFLW        = new VexRMIOp("EVPSHUFLW",        VPSHUFLW);
        public static final VexRMIOp EVPSHUFHW        = new VexRMIOp("EVPSHUFHW",        VPSHUFHW);
        public static final VexRMIOp EVPSHUFD         = new VexRMIOp("EVPSHUFD",         VPSHUFD);
        // @formatter:on

        protected VexRMIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion, EVEXTuple.INVALID, VEXPrefixConfig.WIG);
        }

        protected VexRMIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
        }

        protected VexRMIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex, boolean isEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex, isEvex);
        }

        /**
         * Build the EVEX variant of a given vexOp.
         */
        protected VexRMIOp(String opcode, VexRMIOp vexOp) {
            this(opcode, vexOp.pp, vexOp.mmmmm, vexOp.w, vexOp.op, vexOp.assertion, vexOp.evexTuple, vexOp.wEvex, true);
            variant = vexOp;
            assert vexOp.variant == null : "found 2 EVEX variants for VEX instruction " + vexOp;
            vexOp.variant = this;
        }

        @Override
        public VexRMIOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexRMIOp) encodingLogic(encoding);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src, int imm8) {
            emit(asm, size, dst, src, imm8, Register.None, Z0, B0);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, AMD64Address src, int imm8) {
            emit(asm, size, dst, src, imm8, Register.None, Z0, B0);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src, int imm8, Register mask, int z, int b) {
            emitVexOrEvex(asm, dst, Register.None, src, mask, size, pp, mmmmm, w, wEvex, z, b);
            asm.emitByte(op);
            asm.emitModRM(dst, src);
            asm.emitByte(imm8);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, AMD64Address src, int imm8, Register mask, int z, int b) {
            emitVexOrEvex(asm, dst, Register.None, src, mask, size, pp, mmmmm, w, wEvex, z, b);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src, 1, getDisp8Scale(isEvex, size));
            asm.emitByte(imm8);
        }
    }

    /**
     * EVEX-encoded instructions with an operand order of RMI.
     */
    public static final class EvexRMIOp extends VexRMIOp {
        // @formatter:off
        public static final EvexRMIOp EVFPCLASSSS = new EvexRMIOp("EVFPCLASS", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x67, VEXOpAssertion.MASK_XMM_AVX512DQ_128, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final EvexRMIOp EVFPCLASSSD = new EvexRMIOp("EVFPCLASD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0x67, VEXOpAssertion.MASK_XMM_AVX512DQ_128, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        // @formatter:on

        private EvexRMIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex, true);
        }

        @Override
        public EvexRMIOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (EvexRMIOp) encodingLogic(encoding);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src, int imm8) {
            emit(asm, size, dst, src, imm8, Register.None, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, AMD64Address src, int imm8) {
            emit(asm, size, dst, src, imm8, Register.None, EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        }
    }

    public static final class EvexRMIExtendOp extends VexRMIOp {
        // @formatter:off
        public static final EvexRMIExtendOp EVPROLQ = new EvexRMIExtendOp("EVPROLQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W1, 0x72, 1, VEXOpAssertion.AVX512F_VL, EVEXTuple.FVM, VEXPrefixConfig.W1);
        // @formatter:on
        private final int ext;

        private EvexRMIExtendOp(String opcode, int pp, int mmmmm, int w, int op, int ext, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex, true);
            this.ext = ext;
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src, int imm8, Register mask, int z, int b) {
            emitVexOrEvex(asm, dst, dst, src, mask, size, pp, mmmmm, w, wEvex, z, b);
            asm.emitByte(op);
            asm.emitModRM(ext, src);
            asm.emitByte(imm8);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, AMD64Address src, int imm8, Register mask, int z, int b) {
            emitVexOrEvex(asm, dst, dst, src, mask, size, pp, mmmmm, w, wEvex, z, b);
            asm.emitByte(op);
            asm.emitOperandHelper(ext, src, 1, getDisp8Scale(isEvex, size));
            asm.emitByte(imm8);
        }
    }

    /**
     * VEX-encoded instructions with an operand order of MR.
     */
    public static final class VexMROp extends VexRROp {
        // @formatter:off
        public static final VexMROp EVPCOMPRESSB = new VexMROp("EVPCOMPRESSB", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, 0x63, VEXOpAssertion.AVX512_VBMI2_VL,           EVEXTuple.T1S_8BIT,  VEXPrefixConfig.W0, true);
        public static final VexMROp EVPCOMPRESSW = new VexMROp("EVPCOMPRESSW", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1, 0x63, VEXOpAssertion.AVX512_VBMI2_VL,           EVEXTuple.T1S_16BIT, VEXPrefixConfig.W1, true);
        public static final VexMROp EVPCOMPRESSD = new VexMROp("EVPCOMPRESSD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, 0x8B, VEXOpAssertion.AVX512F_VL,                EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0, true);
        public static final VexMROp EVPCOMPRESSQ = new VexMROp("EVPCOMPRESSQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1, 0x8B, VEXOpAssertion.AVX512F_VL,                EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1, true);

        public static final VexMROp EVPMOVWB     = new VexMROp("EVPMOVWB",     VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, 0x30, VEXOpAssertion.AVX512BW_VL,               EVEXTuple.HVM,       VEXPrefixConfig.W0, true);
        public static final VexMROp EVPMOVDB     = new VexMROp("EVPMOVDB",     VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, 0x31, VEXOpAssertion.AVX512F_VL,                EVEXTuple.QVM,       VEXPrefixConfig.W0, true);
        public static final VexMROp EVPMOVQB     = new VexMROp("EVPMOVQB",     VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, 0x32, VEXOpAssertion.AVX512F_VL,                EVEXTuple.OVM,       VEXPrefixConfig.W0, true);
        public static final VexMROp EVPMOVDW     = new VexMROp("EVPMOVDW",     VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, 0x33, VEXOpAssertion.AVX512F_VL,                EVEXTuple.HVM,       VEXPrefixConfig.W0, true);
        public static final VexMROp EVPMOVQW     = new VexMROp("EVPMOVQW",     VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, 0x34, VEXOpAssertion.AVX512F_VL,                EVEXTuple.QVM,       VEXPrefixConfig.W0, true);
        public static final VexMROp EVPMOVQD     = new VexMROp("EVPMOVQD",     VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, 0x35, VEXOpAssertion.AVX512F_VL,                EVEXTuple.HVM,       VEXPrefixConfig.W0, true);
        // @formatter:on

        private VexMROp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex, boolean isEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex, isEvex);
        }

        @Override
        public VexMROp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexMROp) encodingLogic(encoding);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src) {
            emit(asm, size, dst, src, Register.None, Z0, B0);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src, Register mask, int z, int b) {
            emitVexOrEvex(asm, src, Register.None, dst, mask, size, pp, mmmmm, w, wEvex, z, b);
            asm.emitByte(op);
            asm.emitModRM(src, dst);
        }

        public void emit(AMD64Assembler asm, AVXSize size, AMD64Address dst, Register src) {
            emit(asm, size, dst, src, Register.None, Z0, B0);
        }

        public void emit(AMD64Assembler asm, AVXSize size, AMD64Address dst, Register src, Register mask, int z, int b) {
            emitVexOrEvex(asm, src, Register.None, dst, mask, size, pp, mmmmm, w, wEvex, z, b);
            asm.emitByte(op);
            asm.emitOperandHelper(src, dst, 1, getDisp8Scale(isEvex, size));
        }
    }

    /**
     * VEX-encoded instructions with an operand order of MRI.
     */
    public static final class VexMRIOp extends VexOp implements VexRRIOp {
        // @formatter:off
        public static final VexMRIOp VPEXTRB       = new VexMRIOp("VPEXTRB",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x14, VEXOpAssertion.XMM_CPU_AVX1_AVX512BW_128ONLY, EVEXTuple.T1S_8BIT,  VEXPrefixConfig.W0);
        public static final VexMRIOp VPEXTRW       = new VexMRIOp("VPEXTRW",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x15, VEXOpAssertion.XMM_CPU_AVX1_AVX512BW_128ONLY, EVEXTuple.T1S_16BIT, VEXPrefixConfig.W0);
        public static final VexMRIOp VPEXTRD       = new VexMRIOp("VPEXTRD",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x16, VEXOpAssertion.XMM_CPU_AVX1_AVX512DQ_128ONLY, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexMRIOp VPEXTRQ       = new VexMRIOp("VPEXTRQ",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0x16, VEXOpAssertion.XMM_CPU_AVX1_AVX512DQ_128ONLY, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);

        // AVX/AVX2 128-bit extract
        public static final VexMRIOp VEXTRACTF128  = new VexMRIOp("VEXTRACTF128",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x19, VEXOpAssertion.AVX1_AVX512F_VL_256_512,       EVEXTuple.T4_32BIT,  VEXPrefixConfig.W0);
        public static final VexMRIOp VEXTRACTI128  = new VexMRIOp("VEXTRACTI128",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x39, VEXOpAssertion.AVX2_AVX512F_VL_256_512,       EVEXTuple.T4_32BIT,  VEXPrefixConfig.W0);

        // AVX-512 extract
        public static final VexMRIOp EVPEXTRB       = new VexMRIOp("EVPEXTRB",       VPEXTRB);
        public static final VexMRIOp EVPEXTRW       = new VexMRIOp("EVPEXTRW",       VPEXTRW);
        public static final VexMRIOp EVPEXTRD       = new VexMRIOp("EVPEXTRD",       VPEXTRD);
        public static final VexMRIOp EVPEXTRQ       = new VexMRIOp("EVPEXTRQ",       VPEXTRQ);
        public static final VexMRIOp EVEXTRACTF32X4 = new VexMRIOp("EVEXTRACTF32X4", VEXTRACTF128);
        public static final VexMRIOp EVEXTRACTI32X4 = new VexMRIOp("EVEXTRACTI32X4", VEXTRACTI128);
        public static final VexMRIOp EVEXTRACTF64X2 = new VexMRIOp("EVEXTRACTF64X2", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0x19, VEXOpAssertion.AVX512DQ_VL_256_512,      EVEXTuple.T2_64BIT,  VEXPrefixConfig.W1, true);
        public static final VexMRIOp EVEXTRACTI64X2 = new VexMRIOp("EVEXTRACTI64X2", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0x39, VEXOpAssertion.AVX512DQ_VL_256_512,      EVEXTuple.T2_64BIT,  VEXPrefixConfig.W1, true);

        public static final VexMRIOp EVEXTRACTF32X8 = new VexMRIOp("EVEXTRACTF32X8", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x1B, VEXOpAssertion.AVX512DQ_512ONLY,         EVEXTuple.T8_32BIT,  VEXPrefixConfig.W0, true);
        public static final VexMRIOp EVEXTRACTI32X8 = new VexMRIOp("EVEXTRACTI32X8", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x3B, VEXOpAssertion.AVX512DQ_512ONLY,         EVEXTuple.T8_32BIT,  VEXPrefixConfig.W0, true);
        public static final VexMRIOp EVEXTRACTF64X4 = new VexMRIOp("EVEXTRACTF64X2", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0x1B, VEXOpAssertion.AVX512F_512ONLY,          EVEXTuple.T4_64BIT,  VEXPrefixConfig.W1, true);
        public static final VexMRIOp EVEXTRACTI64X4 = new VexMRIOp("EVEXTRACTI64X2", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0x3B, VEXOpAssertion.AVX512F_512ONLY,          EVEXTuple.T4_64BIT,  VEXPrefixConfig.W1, true);

        // Half precision floating-point values conversion
        public static final VexMRIOp VCVTPS2PH      = new VexMRIOp("VCVTPS2PH",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x1D, VEXOpAssertion.F16C_AVX512F_VL,          EVEXTuple.HVM,       VEXPrefixConfig.W0);
        public static final VexMRIOp EVCVTPS2PH     = new VexMRIOp("EVCVTPS2PH",     VCVTPS2PH);
        // @formatter:on

        private VexMRIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        /**
         * Build the EVEX variant of a given vexOp.
         */
        private VexMRIOp(String opcode, VexMRIOp vexOp) {
            this(opcode, vexOp.pp, vexOp.mmmmm, vexOp.w, vexOp.op, vexOp.assertion, vexOp.evexTuple, vexOp.wEvex, true);
            variant = vexOp;
            assert vexOp.variant == null : "found 2 EVEX variants for VEX instruction " + vexOp;
            vexOp.variant = this;
        }

        private VexMRIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            this(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex, false);
        }

        private VexMRIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex, boolean isEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex, isEvex);
        }

        @Override
        public VexMRIOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexMRIOp) encodingLogic(encoding);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src, int imm8) {
            emit(asm, size, dst, src, imm8, Register.None, Z0, B0);
        }

        public void emit(AMD64Assembler asm, AVXSize size, AMD64Address dst, Register src, int imm8) {
            emit(asm, size, dst, src, imm8, Register.None, Z0, B0);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src, int imm8, Register mask, int z, int b) {
            emitVexOrEvex(asm, src, Register.None, dst, mask, size, pp, mmmmm, w, wEvex, z, b);
            asm.emitByte(op);
            asm.emitModRM(src, dst);
            asm.emitByte(imm8);
        }

        public void emit(AMD64Assembler asm, AVXSize size, AMD64Address dst, Register src, int imm8, Register mask, int z, int b) {
            emitVexOrEvex(asm, src, Register.None, dst, mask, size, pp, mmmmm, w, wEvex, z, b);
            asm.emitByte(op);
            asm.emitOperandHelper(src, dst, 1, getDisp8Scale(isEvex, size));
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

        @Override
        public VexRVMROp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexRVMROp) encodingLogic(encoding);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register mask, Register src1, Register src2) {
            GraalError.guarantee(mask.getRegisterCategory().equals(XMM), "%s", mask);
            emitVexOrEvex(asm, dst, src1, src2, size, pp, mmmmm, w, wEvex);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
            asm.emitByte(mask.encoding() << 4);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register mask, Register src1, AMD64Address src2) {
            GraalError.guarantee(mask.getRegisterCategory().equals(XMM), "%s", mask);
            emitVexOrEvex(asm, dst, src1, src2, size, pp, mmmmm, w, wEvex);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src2, 0, getDisp8Scale(isEvex, size));
            asm.emitByte(mask.encoding() << 4);
        }
    }

    public static class VexRVROp extends VexOp {
        // @formatter:off
        public static final VexRVROp KANDW  = new VexRVROp("KANDW",  VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x41, VEXOpAssertion.AVX512F_MASK_L1);
        public static final VexRVROp KANDB  = new VexRVROp("KANDB",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x41, VEXOpAssertion.AVX512DQ_MASK_L1);
        public static final VexRVROp KANDQ  = new VexRVROp("KANDQ",  VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0x41, VEXOpAssertion.AVX512BW_MASK_L1);
        public static final VexRVROp KANDD  = new VexRVROp("KANDD",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0x41, VEXOpAssertion.AVX512BW_MASK_L1);

        public static final VexRVROp KANDNW = new VexRVROp("KANDNW", VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x42, VEXOpAssertion.AVX512F_MASK_L1);
        public static final VexRVROp KANDNB = new VexRVROp("KANDNB", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x42, VEXOpAssertion.AVX512DQ_MASK_L1);
        public static final VexRVROp KANDNQ = new VexRVROp("KANDNQ", VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0x42, VEXOpAssertion.AVX512BW_MASK_L1);
        public static final VexRVROp KANDND = new VexRVROp("KANDND", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0x42, VEXOpAssertion.AVX512BW_MASK_L1);

        public static final VexRVROp KORW   = new VexRVROp("KORW",   VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x45, VEXOpAssertion.AVX512F_MASK_L1);
        public static final VexRVROp KORB   = new VexRVROp("KORB",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x45, VEXOpAssertion.AVX512DQ_MASK_L1);
        public static final VexRVROp KORQ   = new VexRVROp("KORQ",   VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0x45, VEXOpAssertion.AVX512BW_MASK_L1);
        public static final VexRVROp KORD   = new VexRVROp("KORD",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0x45, VEXOpAssertion.AVX512BW_MASK_L1);

        public static final VexRVROp KXORW  = new VexRVROp("KXORW",  VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x47, VEXOpAssertion.AVX512F_MASK_L1);
        public static final VexRVROp KXORB  = new VexRVROp("KXORB",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x47, VEXOpAssertion.AVX512DQ_MASK_L1);
        public static final VexRVROp KXORQ  = new VexRVROp("KXORQ",  VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0x47, VEXOpAssertion.AVX512BW_MASK_L1);
        public static final VexRVROp KXORD  = new VexRVROp("KXORD",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0x47, VEXOpAssertion.AVX512BW_MASK_L1);

        public static final VexRVROp KXNORW = new VexRVROp("KXNORW", VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x46, VEXOpAssertion.AVX512F_MASK_L1);
        public static final VexRVROp KXNORB = new VexRVROp("KXNORB", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x46, VEXOpAssertion.AVX512DQ_MASK_L1);
        public static final VexRVROp KXNORQ = new VexRVROp("KXNORQ", VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0x46, VEXOpAssertion.AVX512BW_MASK_L1);
        public static final VexRVROp KXNORD = new VexRVROp("KXNORD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0x46, VEXOpAssertion.AVX512BW_MASK_L1);
        // @formatter:on

        protected VexRVROp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        @Override
        public VexRVROp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexRVROp) encodingLogic(encoding);
        }

        public void emit(AMD64Assembler asm, Register dst, Register src1, Register src2) {
            // format VEX.L1 ... kdest, ksrc1, ksrc2
            emitVexOrEvex(asm, dst, src1, src2, AVXSize.YMM, pp, mmmmm, w, wEvex);
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
        public static final VexRVMOp VADDSS          = new VexRVMOp("VADDSS",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x58, VEXOpAssertion.AVX1_AVX512F_128,             EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMOp VADDSD          = new VexRVMOp("VADDSD",      VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x58, VEXOpAssertion.AVX1_AVX512F_128,             EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMOp VMULPS          = new VexRVMOp("VMULPS",      VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x59, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VMULPD          = new VexRVMOp("VMULPD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x59, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VMULSS          = new VexRVMOp("VMULSS",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x59, VEXOpAssertion.AVX1_AVX512F_128,             EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMOp VMULSD          = new VexRVMOp("VMULSD",      VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x59, VEXOpAssertion.AVX1_AVX512F_128,             EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMOp VSUBPS          = new VexRVMOp("VSUBPS",      VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5C, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VSUBPD          = new VexRVMOp("VSUBPD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5C, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VSUBSS          = new VexRVMOp("VSUBSS",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5C, VEXOpAssertion.AVX1_AVX512F_128,             EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMOp VSUBSD          = new VexRVMOp("VSUBSD",      VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5C, VEXOpAssertion.AVX1_AVX512F_128,             EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMOp VMINPS          = new VexRVMOp("VMINPS",      VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5D, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VMINPD          = new VexRVMOp("VMINPD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5D, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VMINSS          = new VexRVMOp("VMINSS",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5D, VEXOpAssertion.AVX1_AVX512F_128,             EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMOp VMINSD          = new VexRVMOp("VMINSD",      VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5D, VEXOpAssertion.AVX1_AVX512F_128,             EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMOp VDIVPS          = new VexRVMOp("VDIVPS",      VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5E, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VDIVPD          = new VexRVMOp("VDIVPD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5E, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VDIVSS          = new VexRVMOp("VDIVSS",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5E, VEXOpAssertion.AVX1_AVX512F_128,             EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMOp VDIVSD          = new VexRVMOp("VDIVSD",      VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5E, VEXOpAssertion.AVX1_AVX512F_128,             EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMOp VMAXPS          = new VexRVMOp("VMAXPS",      VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5F, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VMAXPD          = new VexRVMOp("VMAXPD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5F, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VMAXSS          = new VexRVMOp("VMAXSS",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5F, VEXOpAssertion.AVX1_AVX512F_128,             EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMOp VMAXSD          = new VexRVMOp("VMAXSD",      VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x5F, VEXOpAssertion.AVX1_AVX512F_128,             EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMOp VPACKUSDW       = new VexRVMOp("VPACKUSDW",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x2B, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPACKUSWB       = new VexRVMOp("VPACKUSWB",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x67, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPACKSSWB       = new VexRVMOp("VPACKSSWB",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x63, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VADDSUBPS       = new VexRVMOp("VADDSUBPS",   VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xD0, VEXOpAssertion.AVX1);
        public static final VexRVMOp VADDSUBPD       = new VexRVMOp("VADDSUBPD",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xD0, VEXOpAssertion.AVX1);
        public static final VexRVMOp VPAND           = new VexRVMOp("VPAND",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xDB, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPANDN          = new VexRVMOp("VPANDN",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xDF, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPOR            = new VexRVMOp("VPOR",        VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xEB, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPXOR           = new VexRVMOp("VPXOR",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xEF, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPADDB          = new VexRVMOp("VPADDB",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xFC, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPADDW          = new VexRVMOp("VPADDW",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xFD, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPADDD          = new VexRVMOp("VPADDD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xFE, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPADDQ          = new VexRVMOp("VPADDQ",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xD4, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VPMAXSB         = new VexRVMOp("VPMAXSB",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x3C, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPMAXSW         = new VexRVMOp("VPMAXSW",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xEE, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPMAXSD         = new VexRVMOp("VPMAXSD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x3D, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPMAXUB         = new VexRVMOp("VPMAXUB",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0xDE, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPMAXUW         = new VexRVMOp("VPMAXUW",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x3E, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPMAXUD         = new VexRVMOp("VPMAXUD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x3F, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPMINSB         = new VexRVMOp("VPMINSB",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x38, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPMINSW         = new VexRVMOp("VPMINSW",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xEA, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPMINSD         = new VexRVMOp("VPMINSD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x39, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPMINUB         = new VexRVMOp("VPMINUB",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0xDA, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPMINUW         = new VexRVMOp("VPMINUW",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x3A, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPMINUD         = new VexRVMOp("VPMINUD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x3B, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPMULHUW        = new VexRVMOp("VPMULHUW",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xE4, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPMULHW         = new VexRVMOp("VPMULHW",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xE5, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPMULLW         = new VexRVMOp("VPMULLW",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xD5, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPMULLD         = new VexRVMOp("VPMULLD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x40, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPSUBUSB        = new VexRVMOp("VPSUBUSB",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xD8, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPSUBUSW        = new VexRVMOp("VPSUBUSW",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xD9, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPSUBB          = new VexRVMOp("VPSUBB",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xF8, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPSUBW          = new VexRVMOp("VPSUBW",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xF9, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPSUBD          = new VexRVMOp("VPSUBD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xFA, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPSUBQ          = new VexRVMOp("VPSUBQ",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xFB, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,         EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VPSLLVD         = new VexRVMOp("VPSLLVD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x47, VEXOpAssertion.AVX2_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPSLLVQ         = new VexRVMOp("VPSLLVQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x47, VEXOpAssertion.AVX2_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VPSRLVD         = new VexRVMOp("VPSRLVD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x45, VEXOpAssertion.AVX2_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPSRLVQ         = new VexRVMOp("VPSRLVQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x45, VEXOpAssertion.AVX2_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VPSRAVD         = new VexRVMOp("VPSRAVD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x46, VEXOpAssertion.AVX2_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPSHUFB         = new VexRVMOp("VPSHUFB",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x00, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,        EVEXTuple.FVM,       VEXPrefixConfig.WIG);
        public static final VexRVMOp VPCMPEQB        = new VexRVMOp("VPCMPEQB",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x74, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPEQW        = new VexRVMOp("VPCMPEQW",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x75, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPEQD        = new VexRVMOp("VPCMPEQD",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x76, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPEQQ        = new VexRVMOp("VPCMPEQQ",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x29, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPGTB        = new VexRVMOp("VPCMPGTB",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x64, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPGTW        = new VexRVMOp("VPCMPGTW",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x65, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPGTD        = new VexRVMOp("VPCMPGTD",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x66, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPCMPGTQ        = new VexRVMOp("VPCMPGTQ",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x37, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VFMADD231SS     = new VexRVMOp("VFMADD231SS", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0xB9, VEXOpAssertion.FMA_AVX512F_128ONLY,          EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMOp VFMADD231SD     = new VexRVMOp("VFMADD231SD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0xB9, VEXOpAssertion.FMA_AVX512F_128ONLY,          EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMOp VFMADD231PS     = new VexRVMOp("VFMADD231PS", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0xB8, VEXOpAssertion.FMA,                          EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VFMADD231PD     = new VexRVMOp("VFMADD231PD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0xB8, VEXOpAssertion.FMA,                          EVEXTuple.FVM,       VEXPrefixConfig.W1);
        public static final VexRVMOp VSQRTSD         = new VexRVMOp("VSQRTSD",     VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x51, VEXOpAssertion.AVX1_AVX512F_128,             EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMOp VSQRTSS         = new VexRVMOp("VSQRTSS",     VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x51, VEXOpAssertion.AVX1_AVX512F_128,             EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);

        public static final VexRVMOp VPERMILPS       = new VexRVMOp("VPERMILPS",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x0C, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPERMD          = new VexRVMOp("VPERMD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x36, VEXOpAssertion.AVX2_AVX512F_VL_256_512,      EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPERMPS         = new VexRVMOp("VPERMPS",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x16, VEXOpAssertion.AVX2_AVX512F_VL_256_512,      EVEXTuple.FVM,       VEXPrefixConfig.W0);
        public static final VexRVMOp VPERMILPD       = new VexRVMOp("VPERMILPD",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x0D, VEXOpAssertion.AVX1_AVX512F_VL,              EVEXTuple.FVM,       VEXPrefixConfig.W1);

        public static final VexRVMOp VMOVSS          = new VexRVMOp("VMOVSS",      VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x10, VEXOpAssertion.AVX1_AVX512F_128,             EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMOp VMOVSD          = new VexRVMOp("VMOVSD",      VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x10, VEXOpAssertion.AVX1_AVX512F_128,             EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMOp VMOVHPD         = new VexRVMOp("VMOVHPD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x16, VEXOpAssertion.AVX1_AVX512F_128,             EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMOp VMOVLPD         = new VexRVMOp("VMOVLPD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x12, VEXOpAssertion.AVX1_AVX512F_128,             EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMOp VMOVLHPS        = new VexRVMOp("VMOVLHPS",    VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x16, VEXOpAssertion.AVX1_AVX512F_128,             EVEXTuple.FVM,       VEXPrefixConfig.W0);

        public static final VexRVMOp VPHADDW         = new VexRVMOp("VPHADDW",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x01, VEXOpAssertion.AVX1_2);
        public static final VexRVMOp VPHADDD         = new VexRVMOp("VPHADDD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x02, VEXOpAssertion.AVX1_2);
        // EVEX encoded instructions
        public static final VexRVMOp EVANDPS         = new VexRVMOp("EVANDPS",      VANDPS);
        public static final VexRVMOp EVANDPD         = new VexRVMOp("EVANDPD",      VANDPD);
        public static final VexRVMOp EVANDNPS        = new VexRVMOp("EVANDNPS",     VANDNPS);
        public static final VexRVMOp EVANDNPD        = new VexRVMOp("EVANDNPD",     VANDNPD);
        public static final VexRVMOp EVORPS          = new VexRVMOp("EVORPS",       VORPS);
        public static final VexRVMOp EVORPD          = new VexRVMOp("EVORPD",       VORPD);
        public static final VexRVMOp EVXORPS         = new VexRVMOp("EVXORPS",      VXORPS);
        public static final VexRVMOp EVXORPD         = new VexRVMOp("EVXORPD",      VXORPD);
        public static final VexRVMOp EVADDPS         = new VexRVMOp("EVADDPS",      VADDPS);
        public static final VexRVMOp EVADDPD         = new VexRVMOp("EVADDPD",      VADDPD);
        public static final VexRVMOp EVADDSS         = new VexRVMOp("EVADDSS",      VADDSS);
        public static final VexRVMOp EVADDSD         = new VexRVMOp("EVADDSD",      VADDSD);
        public static final VexRVMOp EVMULPS         = new VexRVMOp("EVMULPS",      VMULPS);
        public static final VexRVMOp EVMULPD         = new VexRVMOp("EVMULPD",      VMULPD);
        public static final VexRVMOp EVMULSS         = new VexRVMOp("EVMULSS",      VMULSS);
        public static final VexRVMOp EVMULSD         = new VexRVMOp("EVMULSD",      VMULSD);
        public static final VexRVMOp EVSUBPS         = new VexRVMOp("EVSUBPS",      VSUBPS);
        public static final VexRVMOp EVSUBPD         = new VexRVMOp("EVSUBPD",      VSUBPD);
        public static final VexRVMOp EVSUBSS         = new VexRVMOp("EVSUBSS",      VSUBSS);
        public static final VexRVMOp EVSUBSD         = new VexRVMOp("EVSUBSD",      VSUBSD);
        public static final VexRVMOp EVMINPS         = new VexRVMOp("EVMINPS",      VMINPS);
        public static final VexRVMOp EVMINPD         = new VexRVMOp("EVMINPD",      VMINPD);
        public static final VexRVMOp EVMINSS         = new VexRVMOp("EVMINSS",      VMINSS);
        public static final VexRVMOp EVMINSD         = new VexRVMOp("EVMINSD",      VMINSD);
        public static final VexRVMOp EVDIVPS         = new VexRVMOp("EVDIVPS",      VDIVPS);
        public static final VexRVMOp EVDIVPD         = new VexRVMOp("EVDIVPD",      VDIVPD);
        public static final VexRVMOp EVDIVSS         = new VexRVMOp("EVDIVSS",      VDIVSS);
        public static final VexRVMOp EVDIVSD         = new VexRVMOp("EVDIVSD",      VDIVSD);
        public static final VexRVMOp EVMAXPS         = new VexRVMOp("EVMAXPS",      VMAXPS);
        public static final VexRVMOp EVMAXPD         = new VexRVMOp("EVMAXPD",      VMAXPD);
        public static final VexRVMOp EVMAXSS         = new VexRVMOp("EVMAXSS",      VMAXSS);
        public static final VexRVMOp EVMAXSD         = new VexRVMOp("EVMAXSD",      VMAXSD);
        public static final VexRVMOp EVPACKUSDW      = new VexRVMOp("EVPACKUSDW",   VPACKUSDW);
        public static final VexRVMOp EVPACKUSWB      = new VexRVMOp("EVPACKUSWB",   VPACKUSWB);
        public static final VexRVMOp EVPANDD         = new VexRVMOp("EVPANDD",      VPAND);
        public static final VexRVMOp EVPANDQ         = new VexRVMOp("EVPANDQ",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xDB, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVPANDND        = new VexRVMOp("EVPANDND",     VPANDN);
        public static final VexRVMOp EVPANDNQ        = new VexRVMOp("EVPANDNQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xDF, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVPORD          = new VexRVMOp("EVPORD",       VPOR);
        public static final VexRVMOp EVPORQ          = new VexRVMOp("EVPORQ",       VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xEB, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVPXORD         = new VexRVMOp("EVPXORD",      VPXOR);
        public static final VexRVMOp EVPXORQ         = new VexRVMOp("EVPXORQ",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xEF, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVPADDB         = new VexRVMOp("EVPADDB",      VPADDB);
        public static final VexRVMOp EVPADDW         = new VexRVMOp("EVPADDW",      VPADDW);
        public static final VexRVMOp EVPADDD         = new VexRVMOp("EVPADDD",      VPADDD);
        public static final VexRVMOp EVPADDQ         = new VexRVMOp("EVPADDQ",      VPADDQ);
        public static final VexRVMOp EVPMAXSB        = new VexRVMOp("EVPMAXSB",     VPMAXSB);
        public static final VexRVMOp EVPMAXSW        = new VexRVMOp("EVPMAXSW",     VPMAXSW);
        public static final VexRVMOp EVPMAXSD        = new VexRVMOp("EVPMAXSD",     VPMAXSD);
        public static final VexRVMOp EVPMAXSQ        = new VexRVMOp("EVPMAXSQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x3D, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVPMAXUB        = new VexRVMOp("EVPMAXUB",     VPMAXUB);
        public static final VexRVMOp EVPMAXUW        = new VexRVMOp("EVPMAXUW",     VPMAXUW);
        public static final VexRVMOp EVPMAXUD        = new VexRVMOp("EVPMAXUD",     VPMAXUD);
        public static final VexRVMOp EVPMAXUQ        = new VexRVMOp("EVPMAXUQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x3F, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVPMINSB        = new VexRVMOp("EVPMINSB",     VPMINSB);
        public static final VexRVMOp EVPMINSW        = new VexRVMOp("EVPMINSW",     VPMINSW);
        public static final VexRVMOp EVPMINSD        = new VexRVMOp("EVPMINSD",     VPMINSD);
        public static final VexRVMOp EVPMINSQ        = new VexRVMOp("EVPMINSQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x39, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVPMINUB        = new VexRVMOp("EVPMINUB",     VPMINUB);
        public static final VexRVMOp EVPMINUW        = new VexRVMOp("EVPMINUW",     VPMINUW);
        public static final VexRVMOp EVPMINUD        = new VexRVMOp("EVPMINUD",     VPMINUD);
        public static final VexRVMOp EVPMINUQ        = new VexRVMOp("EVPMINUQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x3B, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVPMULHUW       = new VexRVMOp("EVPMULHUW",    VPMULHUW);
        public static final VexRVMOp EVPMULHW        = new VexRVMOp("EVPMULHW",     VPMULHW);
        public static final VexRVMOp EVPMULLW        = new VexRVMOp("EVPMULLW",     VPMULLW);
        public static final VexRVMOp EVPMULLD        = new VexRVMOp("EVPMULLD",     VPMULLD);
        public static final VexRVMOp EVPMULLQ        = new VexRVMOp("EVPMULLQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x40, VEXOpAssertion.AVX512DQ_VL,                  EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVPSUBUSB       = new VexRVMOp("EVPSUBUSB",    VPSUBUSB);
        public static final VexRVMOp EVPSUBUSW       = new VexRVMOp("EVPSUBUSW",    VPSUBUSW);
        public static final VexRVMOp EVPSUBB         = new VexRVMOp("EVPSUBB",      VPSUBB);
        public static final VexRVMOp EVPSUBW         = new VexRVMOp("EVPSUBW",      VPSUBW);
        public static final VexRVMOp EVPSUBD         = new VexRVMOp("EVPSUBD",      VPSUBD);
        public static final VexRVMOp EVPSUBQ         = new VexRVMOp("EVPSUBQ",      VPSUBQ);
        public static final VexRVMOp EVPSLLVW        = new VexRVMOp("EVPSLLVW",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x12, VEXOpAssertion.AVX512BW_VL,                  EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVPSLLVD        = new VexRVMOp("EVPSLLVD",     VPSLLVD);
        public static final VexRVMOp EVPSLLVQ        = new VexRVMOp("EVPSLLVQ",     VPSLLVQ);
        public static final VexRVMOp EVPSRLVW        = new VexRVMOp("EVPSRLVW",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x10, VEXOpAssertion.AVX512BW_VL,                  EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVPSRLVD        = new VexRVMOp("EVPSRLVD",     VPSRLVD);
        public static final VexRVMOp EVPSRLVQ        = new VexRVMOp("EVPSRLVQ",     VPSRLVQ);
        public static final VexRVMOp EVPSRAVW        = new VexRVMOp("EVPSRAVW",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x11, VEXOpAssertion.AVX512BW_VL,                  EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVPSRAVD        = new VexRVMOp("EVPSRAVD",     VPSRAVD);
        public static final VexRVMOp EVPSRAVQ        = new VexRVMOp("EVPSRAVQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x46, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVPSHUFB        = new VexRVMOp("EVPSHUFB",     VPSHUFB);
        public static final VexRVMOp EVPCMPEQB       = new VexRVMOp("EVPCMPEQB",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x74, VEXOpAssertion.MASK_XMM_XMM_AVX512BW_VL,     EVEXTuple.FVM,       VEXPrefixConfig.WIG, true);
        public static final VexRVMOp EVPCMPEQW       = new VexRVMOp("EVPCMPEQW",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x75, VEXOpAssertion.MASK_XMM_XMM_AVX512BW_VL,     EVEXTuple.FVM,       VEXPrefixConfig.WIG, true);
        public static final VexRVMOp EVPCMPEQD       = new VexRVMOp("EVPCMPEQD",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x76, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL,      EVEXTuple.FVM,       VEXPrefixConfig.W0, true);
        public static final VexRVMOp EVPCMPEQQ       = new VexRVMOp("EVPCMPEQQ",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x29, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL,      EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVPCMPGTB       = new VexRVMOp("EVPCMPGTB",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x64, VEXOpAssertion.MASK_XMM_XMM_AVX512BW_VL,     EVEXTuple.FVM,       VEXPrefixConfig.WIG, true);
        public static final VexRVMOp EVPCMPGTW       = new VexRVMOp("EVPCMPGTW",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x65, VEXOpAssertion.MASK_XMM_XMM_AVX512BW_VL,     EVEXTuple.FVM,       VEXPrefixConfig.WIG, true);
        public static final VexRVMOp EVPCMPGTD       = new VexRVMOp("EVPCMPGTD",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0x66, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL,      EVEXTuple.FVM,       VEXPrefixConfig.W0, true);
        public static final VexRVMOp EVPCMPGTQ       = new VexRVMOp("EVPCMPGTQ",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, 0x37, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL,      EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVFMADD231SS    = new VexRVMOp("EVFMADD231SS", VFMADD231SS);
        public static final VexRVMOp EVFMADD231SD    = new VexRVMOp("EVFMADD231SD", VFMADD231SD);
        public static final VexRVMOp EVFMADD231PS    = new VexRVMOp("EVFMADD231PS", VFMADD231PS);
        public static final VexRVMOp EVFMADD231PD    = new VexRVMOp("EVFMADD231PD", VFMADD231PD);
        public static final VexRVMOp EVSQRTSD        = new VexRVMOp("EVSQRTSD",     VSQRTSD);
        public static final VexRVMOp EVSQRTSS        = new VexRVMOp("EVSQRTSS",     VSQRTSS);

        public static final VexRVMOp EVPERMB         = new VexRVMOp("EVPERMB",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x8D, VEXOpAssertion.AVX512_VBMI_VL,               EVEXTuple.FVM,       VEXPrefixConfig.W0, true);
        public static final VexRVMOp EVPERMW         = new VexRVMOp("EVPERMW",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x8D, VEXOpAssertion.AVX512BW_VL,                  EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVPERMILPS      = new VexRVMOp("EVPERMILPS",   VPERMILPS);
        public static final VexRVMOp EVPERMD         = new VexRVMOp("EVPERMD",      VPERMD);
        public static final VexRVMOp EVPERMPS        = new VexRVMOp("EVPERMPS",     VPERMPS);
        public static final VexRVMOp EVPERMILPD      = new VexRVMOp("EVPERMILPD",   VPERMILPD);
        public static final VexRVMOp EVPERMQ         = new VexRVMOp("EVPERMQ",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x36, VEXOpAssertion.AVX512F_VL_256_512,           EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVPERMPD        = new VexRVMOp("EVPERMPD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x16, VEXOpAssertion.AVX512F_VL_256_512,           EVEXTuple.FVM,       VEXPrefixConfig.W1, true);

        public static final VexRVMOp EVPBLENDMB      = new VexRVMOp("EVPBLENDMB",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x66, VEXOpAssertion.AVX512BW_VL,                  EVEXTuple.FVM,       VEXPrefixConfig.W0, true);
        public static final VexRVMOp EVPBLENDMW      = new VexRVMOp("EVPBLENDMW",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x66, VEXOpAssertion.AVX512BW_VL,                  EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVPBLENDMD      = new VexRVMOp("EVPBLENDMD",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x64, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W0, true);
        public static final VexRVMOp EVPBLENDMQ      = new VexRVMOp("EVPBLENDMQ",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x64, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVBLENDMPS      = new VexRVMOp("EVBLENDMPS",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x65, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W0, true);
        public static final VexRVMOp EVBLENDMPD      = new VexRVMOp("EVBLENDMPD",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x65, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVPERMT2B       = new VexRVMOp("EVPERMT2B",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0,  0x7D, VEXOpAssertion.AVX512_VBMI_VL,               EVEXTuple.FVM,       VEXPrefixConfig.W0, true);
        public static final VexRVMOp EVPERMT2Q       = new VexRVMOp("EVPERMT2Q",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x7E, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W1, true);
        public static final VexRVMOp EVPROLVQ        = new VexRVMOp("EVPROLVQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1,  0x15, VEXOpAssertion.AVX512F_VL,                   EVEXTuple.FVM,       VEXPrefixConfig.W1, true);

        public static final VexRVMOp EVMOVSS         = new VexRVMOp("EVMOVSS",      VMOVSS);
        public static final VexRVMOp EVMOVSD         = new VexRVMOp("EVMOVSD",      VMOVSD);
        public static final VexRVMOp EVMOVHPD        = new VexRVMOp("EVMOVHPD",     VMOVHPD);
        public static final VexRVMOp EVMOVLPD        = new VexRVMOp("EVMOVLPD",     VMOVLPD);
        public static final VexRVMOp EVMOVLHPS       = new VexRVMOp("EVMOVLHPS",    VMOVLHPS);
        // @formatter:on

        protected VexRVMOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        private VexRVMOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
        }

        /**
         * Build the EVEX variant of a given vexOp.
         */
        private VexRVMOp(String opcode, VexRVMOp vexOp) {
            this(opcode, vexOp.pp, vexOp.mmmmm, vexOp.w, vexOp.op, vexOp.assertion, vexOp.evexTuple, vexOp.wEvex, true);
            variant = vexOp;
            assert vexOp.variant == null : "found 2 EVEX variants for VEX instruction " + vexOp;
            vexOp.variant = this;
        }

        private VexRVMOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex, boolean isEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex, isEvex);
        }

        @Override
        public VexRVMOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexRVMOp) encodingLogic(encoding);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, Register src2) {
            emit(asm, size, dst, src1, src2, Register.None, Z0, B0);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, AMD64Address src2) {
            emit(asm, size, dst, src1, src2, Register.None, Z0, B0);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, Register src2, Register mask) {
            emit(asm, size, dst, src1, src2, mask, Z0, B0);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, AMD64Address src2, Register mask) {
            emit(asm, size, dst, src1, src2, mask, Z0, B0);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, Register src2, Register mask, int z, int b) {
            emitVexOrEvex(asm, dst, src1, src2, mask, size, pp, mmmmm, w, wEvex, z, b);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, AMD64Address src2, Register mask, int z, int b) {
            emitVexOrEvex(asm, dst, src1, src2, mask, size, pp, mmmmm, w, wEvex, z, b);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src2, 0, getDisp8Scale(isEvex, size));
        }

        public boolean isPacked() {
            return pp == VEXPrefixConfig.P_ || pp == VEXPrefixConfig.P_66;
        }
    }

    public static final class VexRVMConvertOp extends VexRVMOp {
        // @formatter:off
        public static final VexRVMConvertOp VCVTSD2SS = new VexRVMConvertOp("VCVTSD2SS", VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0x5A, VEXOpAssertion.AVX1_AVX512F_128,                 EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMConvertOp VCVTSS2SD = new VexRVMConvertOp("VCVTSS2SD", VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0x5A, VEXOpAssertion.AVX1_AVX512F_128,                 EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMConvertOp VCVTSI2SD = new VexRVMConvertOp("VCVTSI2SD", VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0,  0x2A, VEXOpAssertion.XMM_XMM_CPU_AVX1_AVX512F_128ONLY, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMConvertOp VCVTSQ2SD = new VexRVMConvertOp("VCVTSQ2SD", VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1,  0x2A, VEXOpAssertion.XMM_XMM_CPU_AVX1_AVX512F_128ONLY, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMConvertOp VCVTSI2SS = new VexRVMConvertOp("VCVTSI2SS", VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0,  0x2A, VEXOpAssertion.XMM_XMM_CPU_AVX1_AVX512F_128ONLY, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMConvertOp VCVTSQ2SS = new VexRVMConvertOp("VCVTSQ2SS", VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1,  0x2A, VEXOpAssertion.XMM_XMM_CPU_AVX1_AVX512F_128ONLY, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);

        // EVEX encoded instruction
        public static final VexRVMConvertOp EVCVTSD2SS = new VexRVMConvertOp("EVCVTSD2SS", VCVTSD2SS);
        public static final VexRVMConvertOp EVCVTSS2SD = new VexRVMConvertOp("EVCVTSS2SD", VCVTSS2SD);
        public static final VexRVMConvertOp EVCVTSI2SD = new VexRVMConvertOp("EVCVTSI2SD", VCVTSI2SD);
        public static final VexRVMConvertOp EVCVTSQ2SD = new VexRVMConvertOp("EVCVTSQ2SD", VCVTSQ2SD);
        public static final VexRVMConvertOp EVCVTSI2SS = new VexRVMConvertOp("EVCVTSI2SS", VCVTSI2SS);
        public static final VexRVMConvertOp EVCVTSQ2SS = new VexRVMConvertOp("EVCVTSQ2SS", VCVTSQ2SS);

        public static final VexRVMConvertOp EVCVTUSI2SD = new VexRVMConvertOp("EVCVTUSI2SD", VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x7B, VEXOpAssertion.XMM_XMM_CPU_AVX512F_128ONLY, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0, true);
        public static final VexRVMConvertOp EVCVTUSQ2SD = new VexRVMConvertOp("EVCVTUSQ2SD", VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x7B, VEXOpAssertion.XMM_XMM_CPU_AVX512F_128ONLY, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1, true);
        public static final VexRVMConvertOp EVCVTUSI2SS = new VexRVMConvertOp("EVCVTUSI2SS", VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x7B, VEXOpAssertion.XMM_XMM_CPU_AVX512F_128ONLY, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0, true);
        public static final VexRVMConvertOp EVCVTUSQ2SS = new VexRVMConvertOp("EVCVTUSQ2SS", VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0x7B, VEXOpAssertion.XMM_XMM_CPU_AVX512F_128ONLY, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1, true);
        // @formatter:on

        private VexRVMConvertOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
        }

        private VexRVMConvertOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex, boolean isEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex, isEvex);
        }

        /**
         * Build the EVEX variant of a given vexOp.
         */
        private VexRVMConvertOp(String opcode, VexRVMConvertOp vexOp) {
            super(opcode, vexOp.pp, vexOp.mmmmm, vexOp.w, vexOp.op, vexOp.assertion, vexOp.evexTuple, vexOp.wEvex, true);
            variant = vexOp;
            assert vexOp.variant == null : "found 2 EVEX variants for VEX instruction " + vexOp;
            vexOp.variant = this;
        }

        @Override
        public VexRVMConvertOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexRVMConvertOp) encodingLogic(encoding);
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
        public VexGeneralPurposeRVMOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexGeneralPurposeRVMOp) encodingLogic(encoding);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, Register src2) {
            emit(asm, size, dst, src1, src2, Register.None, Z0, B0);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, Register src2, Register mask, int z, int b) {
            assert size == AVXSize.DWORD || size == AVXSize.QWORD : size;
            emitVexOrEvex(asm, dst, src1, src2, mask, AVXSize.XMM, pp, mmmmm, size == AVXSize.DWORD ? VEXPrefixConfig.W0 : VEXPrefixConfig.W1, wEvex, z, b);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, AMD64Address src2) {
            emit(asm, size, dst, src1, src2, Register.None, Z0, B0);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, AMD64Address src2, Register mask, int z, int b) {
            assert size == AVXSize.DWORD || size == AVXSize.QWORD : size;
            emitVexOrEvex(asm, dst, src1, src2, mask, AVXSize.XMM, pp, mmmmm, size == AVXSize.DWORD ? VEXPrefixConfig.W0 : VEXPrefixConfig.W1, wEvex, z, b);
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

        @Override
        public VexGeneralPurposeRMVOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexGeneralPurposeRMVOp) encodingLogic(encoding);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, Register src2) {
            assert size == AVXSize.DWORD || size == AVXSize.QWORD : size;
            emitVexOrEvex(asm, dst, src2, src1, AVXSize.XMM, pp, mmmmm, size == AVXSize.DWORD ? VEXPrefixConfig.W0 : VEXPrefixConfig.W1, wEvex);
            asm.emitByte(op);
            asm.emitModRM(dst, src1);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, AMD64Address src1, Register src2) {
            assert size == AVXSize.DWORD || size == AVXSize.QWORD : size;
            emitVexOrEvex(asm, dst, src2, src1, AVXSize.XMM, pp, mmmmm, size == AVXSize.DWORD ? VEXPrefixConfig.W0 : VEXPrefixConfig.W1, wEvex);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src1, 0);
        }
    }

    public static final class VexAESOp extends VexRVMOp {
        // @formatter:off
        public static final VexAESOp VAESENC     = new VexAESOp("VAESENC",     0xDC, VEXOpAssertion.AES_AVX1_128ONLY);
        public static final VexAESOp VAESENCLAST = new VexAESOp("VAESENCLAST", 0xDD, VEXOpAssertion.AES_AVX1_128ONLY);
        public static final VexAESOp VAESDEC     = new VexAESOp("VAESDEC",     0xDE, VEXOpAssertion.AES_AVX1_128ONLY);
        public static final VexAESOp VAESDECLAST = new VexAESOp("VAESDECLAST", 0xDF, VEXOpAssertion.AES_AVX1_128ONLY);
        // @formatter:on

        private VexAESOp(String opcode, int op, VEXOpAssertion assertion) {
            // VEX.NDS.128.66.0F38.WIG - w not specified, so ignored.
            super(opcode, VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.WIG, op, assertion);
        }

        @Override
        public VexAESOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexAESOp) encodingLogic(encoding);
        }

        public void emit(AMD64Assembler asm, Register result, Register state, Register key) {
            emit(asm, AVXSize.XMM, result, state, key);
        }

        public void emit(AMD64Assembler asm, Register result, Register state, AMD64Address keyLocation) {
            emit(asm, AVXSize.XMM, result, state, keyLocation);
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

        @Override
        public VexGatherOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexGatherOp) encodingLogic(encoding);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, AMD64Address address, Register mask) {
            emitVexOrEvex(asm, dst, mask, address, size, pp, mmmmm, w, wEvex);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, address, 0);
        }
    }

    /**
     * EVEX-encoded vector gather instructions with an operand order of RM.
     */
    public static final class EvexGatherOp extends VexOp {
        // @formatter:off
        public static final EvexGatherOp EVPGATHERDD = new EvexGatherOp("EVPGATHERDD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, 0x90, VEXOpAssertion.AVX512F_VL, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0, true);
        public static final EvexGatherOp EVPGATHERQD = new EvexGatherOp("EVPGATHERQD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, 0x91, VEXOpAssertion.AVX512F_VL, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0, true);
        public static final EvexGatherOp EVPGATHERDQ = new EvexGatherOp("EVPGATHERDQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, 0x90, VEXOpAssertion.AVX512F_VL, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1, true);
        public static final EvexGatherOp EVPGATHERQQ = new EvexGatherOp("EVPGATHERQQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, 0x91, VEXOpAssertion.AVX512F_VL, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1, true);
        public static final EvexGatherOp EVGATHERDPD = new EvexGatherOp("EVGATHERDPD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, 0x92, VEXOpAssertion.AVX512F_VL, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1, true);
        public static final EvexGatherOp EVGATHERQPD = new EvexGatherOp("EVGATHERQPD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, 0x93, VEXOpAssertion.AVX512F_VL, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1, true);
        public static final EvexGatherOp EVGATHERDPS = new EvexGatherOp("EVGATHERDPS", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, 0x92, VEXOpAssertion.AVX512F_VL, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0, true);
        public static final EvexGatherOp EVGATHERQPS = new EvexGatherOp("EVGATHERQPS", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, 0x93, VEXOpAssertion.AVX512F_VL, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0, true);
        // @formatter:on

        protected EvexGatherOp(String opcode, int pp, int mmmmm, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex, boolean isEvex) {
            super(opcode, pp, mmmmm, wEvex, op, assertion, evexTuple, wEvex, isEvex);
        }

        @Override
        public EvexGatherOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (EvexGatherOp) encodingLogic(encoding);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, AMD64Address address, Register mask) {
            emit(asm, size, dst, address, mask, Z0, B0);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, AMD64Address address, Register mask, int z, int b) {
            emitVexOrEvex(asm, dst, Register.None, address, mask, size, pp, mmmmm, w, wEvex, z, b);
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
        public VexGeneralPurposeRMOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexGeneralPurposeRMOp) encodingLogic(encoding);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src) {
            emit(asm, size, dst, src, Register.None, Z0, B0);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src, Register mask, int z, int b) {
            emitVexOrEvex(asm, (Register) null, dst, src, mask, AVXSize.XMM, pp, mmmmm, size == AVXSize.DWORD ? VEXPrefixConfig.W0 : VEXPrefixConfig.W1, wEvex, z, b);
            asm.emitByte(op);
            asm.emitModRM(ext, src);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, AMD64Address src) {
            emit(asm, size, dst, src, Register.None, Z0, B0);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, AMD64Address src, Register mask, int z, int b) {
            emitVexOrEvex(asm, null, dst, src, mask, AVXSize.XMM, pp, mmmmm, size == AVXSize.DWORD ? VEXPrefixConfig.W0 : VEXPrefixConfig.W1, wEvex, z, b);
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
        public static final VexShiftOp VPSLLW = new VexShiftOp("VPSLLW", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xF1, 0x71, 6, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL, EVEXTuple.M128, VEXPrefixConfig.WIG);
        public static final VexShiftOp VPSLLD = new VexShiftOp("VPSLLD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xF2, 0x72, 6, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,  EVEXTuple.M128, VEXPrefixConfig.W0);
        public static final VexShiftOp VPSLLQ = new VexShiftOp("VPSLLQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xF3, 0x73, 6, VEXOpAssertion.AVX1_AVX2_AVX512F_VL,  EVEXTuple.M128, VEXPrefixConfig.W1);

        // EVEX encoded instructions
        public static final VexShiftOp EVPSRLW = new VexShiftOp("EVPSRLW", VPSRLW);
        public static final VexShiftOp EVPSRLD = new VexShiftOp("EVPSRLD", VPSRLD);
        public static final VexShiftOp EVPSRLQ = new VexShiftOp("EVPSRLQ", VPSRLQ);
        public static final VexShiftOp EVPSRAW = new VexShiftOp("EVPSRAW", VPSRAW);
        public static final VexShiftOp EVPSRAD = new VexShiftOp("EVPSRAD", VPSRAD);
        public static final VexShiftOp EVPSRAQ = new VexShiftOp("EVPSRAQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1, 0xE2, 0x72, 4, VEXOpAssertion.AVX512F_VL,           EVEXTuple.M128, VEXPrefixConfig.W1, true);
        public static final VexShiftOp EVPSLLW = new VexShiftOp("EVPSLLW", VPSLLW);
        public static final VexShiftOp EVPSLLD = new VexShiftOp("EVPSLLD", VPSLLD);
        public static final VexShiftOp EVPSLLQ = new VexShiftOp("EVPSLLQ", VPSLLQ);
        // @formatter:on

        private final int immOp;
        private final int r;

        private VexShiftOp(String opcode, int pp, int mmmmm, int w, int op, int immOp, int r, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
            this.immOp = immOp;
            this.r = r;
        }

        private VexShiftOp(String opcode, int pp, int mmmmm, int w, int op, int immOp, int r, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex, boolean isEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex, isEvex);
            this.immOp = immOp;
            this.r = r;
        }

        /**
         * Build the EVEX variant of a given vexOp.
         */
        private VexShiftOp(String opcode, VexShiftOp vexOp) {
            super(opcode, vexOp.pp, vexOp.mmmmm, vexOp.w, vexOp.op, vexOp.assertion, vexOp.evexTuple, vexOp.wEvex, true);
            this.immOp = vexOp.immOp;
            this.r = vexOp.r;
            variant = vexOp;
            assert vexOp.variant == null : "found 2 EVEX variants for VEX instruction " + vexOp;
            vexOp.variant = this;
        }

        @Override
        public VexShiftOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexShiftOp) encodingLogic(encoding);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src, int imm8) {
            emitVexOrEvex(asm, (Register) null, dst, src, size, pp, mmmmm, w, wEvex);
            asm.emitByte(immOp);
            asm.emitModRM(r, src);
            asm.emitByte(imm8);
        }
    }

    public static final class VexShiftImmOp extends VexOp implements VexRRIOp {
        // @formatter:off
        public static final VexShiftImmOp VPSLLDQ = new VexShiftImmOp("VPSLLDQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG,  0x73, 7, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL, EVEXTuple.FVM, VEXPrefixConfig.WIG);
        public static final VexShiftImmOp VPSRLDQ = new VexShiftImmOp("VPSRLDQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG,  0x73, 3, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL, EVEXTuple.FVM, VEXPrefixConfig.WIG);

        // EVEX encoded instructions
        public static final VexShiftImmOp EVPSLLDQ = new VexShiftImmOp("EVPSLLDQ", VPSLLDQ);
        public static final VexShiftImmOp EVPSRLDQ = new VexShiftImmOp("EVPSRLDQ", VPSRLDQ);
        // @formatter:on

        private final int r;

        private VexShiftImmOp(String opcode, int pp, int mmmmm, int w, int op, int r, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
            this.r = r;
        }

        /**
         * Build the EVEX variant of a given vexOp.
         */
        private VexShiftImmOp(String opcode, VexShiftImmOp vexOp) {
            super(opcode, vexOp.pp, vexOp.mmmmm, vexOp.w, vexOp.op, vexOp.assertion, vexOp.evexTuple, vexOp.wEvex, true);
            this.r = vexOp.r;
            variant = vexOp;
            assert vexOp.variant == null : "found 2 EVEX variants for VEX instruction " + vexOp;
            vexOp.variant = this;
        }

        @Override
        public VexShiftImmOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexShiftImmOp) encodingLogic(encoding);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src, int imm8) {
            emitVexOrEvex(asm, (Register) null, dst, src, size, pp, mmmmm, w, wEvex);
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
        public static final VexMaskedMoveOp VMASKMOVPS = new VexMaskedMoveOp("VMASKMOVPS", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, 0x2C, 0x2E, VEXOpAssertion.AVX1);
        public static final VexMaskedMoveOp VMASKMOVPD = new VexMaskedMoveOp("VMASKMOVPD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, 0x2D, 0x2F, VEXOpAssertion.AVX1);
        public static final VexMaskedMoveOp VPMASKMOVD = new VexMaskedMoveOp("VPMASKMOVD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W0, 0x8C, 0x8E, VEXOpAssertion.AVX2);
        public static final VexMaskedMoveOp VPMASKMOVQ = new VexMaskedMoveOp("VPMASKMOVQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F38, VEXPrefixConfig.W1, 0x8C, 0x8E, VEXOpAssertion.AVX2);
        // @formatter:on

        private final int opReverse;

        private VexMaskedMoveOp(String opcode, int pp, int mmmmm, int w, int op, int opReverse, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
            this.opReverse = opReverse;
        }

        @Override
        public VexMaskedMoveOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexMaskedMoveOp) encodingLogic(encoding);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register mask, AMD64Address src) {
            emitVexOrEvex(asm, dst, mask, src, size, pp, mmmmm, w, wEvex);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src, 0);
        }

        public void emit(AMD64Assembler asm, AVXSize size, AMD64Address dst, Register mask, Register src) {
            emitVexOrEvex(asm, dst, mask, src, size, pp, mmmmm, w, wEvex);
            asm.emitByte(opReverse);
            asm.emitOperandHelper(src, dst, 0, getDisp8Scale(isEvex, size));
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

        /**
         * The value of the pp field if one of the operands is a general purpose register.
         */
        private final int ppCPU;

        /**
         * The value of the w field if one of the operands is a general purpose register.
         */
        private final int wCPU;

        private VexMoveMaskOp(String opcode, int pp, int ppCPU, int mmmmm, int w, int wCPU, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, OP_K_FROM_K_MEM, assertion, EVEXTuple.INVALID, w);
            this.ppCPU = ppCPU;
            this.wCPU = wCPU;
        }

        @Override
        public VexMoveMaskOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexMoveMaskOp) encodingLogic(encoding);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src) {
            GraalError.guarantee(!(inRC(CPU, dst) && inRC(CPU, src)), "source and destination can't both be CPU registers");
            int actualOp = op(dst, src);
            int actualPP = pp(dst, src);
            int actualW = w(dst, src);
            emitVexOrEvex(asm, dst, Register.None, src, size, actualPP, mmmmm, actualW, wEvex);
            asm.emitByte(actualOp);
            asm.emitModRM(dst, src);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, AMD64Address dst, Register src) {
            GraalError.guarantee(inRC(MASK, src), "source must be a mask register");
            emitVexOrEvex(asm, src, Register.None, dst, size, pp, mmmmm, w, wEvex);
            asm.emitByte(OP_MEM_FROM_K);
            asm.emitOperandHelper(src, dst, 0);
        }

        @Override
        public void emit(AMD64Assembler asm, AVXSize size, Register dst, AMD64Address src) {
            GraalError.guarantee(inRC(MASK, dst), "destination must be a mask register");
            emitVexOrEvex(asm, dst, Register.None, src, size, pp, mmmmm, w, wEvex);
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
        public static final VexRVMIOp VPINSRB      = new VexRVMIOp("VPINSRB",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x20, VEXOpAssertion.XMM_XMM_CPU_AVX1_AVX512BW_128ONLY, EVEXTuple.T1S_8BIT,  VEXPrefixConfig.WIG);
        public static final VexRVMIOp VPINSRW      = new VexRVMIOp("VPINSRW",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.W0,  0xC4, VEXOpAssertion.XMM_XMM_CPU_AVX1_AVX512BW_128ONLY, EVEXTuple.T1S_16BIT, VEXPrefixConfig.WIG);
        public static final VexRVMIOp VPINSRD      = new VexRVMIOp("VPINSRD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x22, VEXOpAssertion.XMM_XMM_CPU_AVX1_AVX512DQ_128ONLY, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMIOp VPINSRQ      = new VexRVMIOp("VPINSRQ",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x22, VEXOpAssertion.XMM_XMM_CPU_AVX1_AVX512DQ_128ONLY, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1);
        public static final VexRVMIOp VINSERTPS    = new VexRVMIOp("VINSERTPS",    VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.WIG, 0x21, VEXOpAssertion.AVX1_AVX512F_128,         EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0);

        public static final VexRVMIOp VSHUFPS      = new VexRVMIOp("VSHUFPS",      VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xC6, VEXOpAssertion.AVX1_AVX512F_VL,          EVEXTuple.FVM,      VEXPrefixConfig.W0);
        public static final VexRVMIOp VSHUFPD      = new VexRVMIOp("VSHUFPD",      VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F,   VEXPrefixConfig.WIG, 0xC6, VEXOpAssertion.AVX1_AVX512F_VL,          EVEXTuple.FVM,      VEXPrefixConfig.W1);

        // AVX/AVX2 insert
        public static final VexRVMIOp VINSERTF128  = new VexRVMIOp("VINSERTF128",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x18, VEXOpAssertion.AVX1_AVX512F_VL_256_512,  EVEXTuple.T4_32BIT, VEXPrefixConfig.W0);
        public static final VexRVMIOp VINSERTI128  = new VexRVMIOp("VINSERTI128",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x38, VEXOpAssertion.AVX2_AVX512F_VL_256_512,  EVEXTuple.T4_32BIT, VEXPrefixConfig.W0);

        // AVX2 128-bit permutation
        public static final VexRVMIOp VPERM2I128   = new VexRVMIOp("VPERM2I128",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x46, VEXOpAssertion.AVX2_256ONLY);
        public static final VexRVMIOp VPERM2F128   = new VexRVMIOp("VPERM2F128",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x06, VEXOpAssertion.AVX1_256ONLY);
        // Carry-Less Multiplication Quadword
        public static final VexRVMIOp VPCLMULQDQ   = new VexRVMIOp("VPCLMULQDQ",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.WIG, 0x44, VEXOpAssertion.CLMUL_AVX1_AVX512F_VL,    EVEXTuple.FVM,      VEXPrefixConfig.WIG);
        // Packed Align Right
        public static final VexRVMIOp VPALIGNR     = new VexRVMIOp("VPALIGNR",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.WIG, 0x0F, VEXOpAssertion.AVX1_AVX2_AVX512BW_VL,    EVEXTuple.FVM,      VEXPrefixConfig.WIG);
        // Blend Packed Dwords
        public static final VexRVMIOp VPBLENDD     = new VexRVMIOp("VPBLENDD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x02, VEXOpAssertion.AVX2);

        // Galois Field New Instructions
        public static final VexRVMIOp VGF2P8AFFINEQB  = new VexRVMIOp("VGF2P8AFFINEQB",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0xCE, VEXOpAssertion.GFNI_AVX1_AVX512F_VL, EVEXTuple.FVM,     VEXPrefixConfig.W1);

        // EVEX encoded instructions
        public static final VexRVMIOp EVPINSRB        = new VexRVMIOp("EVPINSRB",        VPINSRB);
        public static final VexRVMIOp EVPINSRW        = new VexRVMIOp("EVPINSRW",        VPINSRW);
        public static final VexRVMIOp EVPINSRD        = new VexRVMIOp("EVPINSRD",        VPINSRD);
        public static final VexRVMIOp EVPINSRQ        = new VexRVMIOp("EVPINSRQ",        VPINSRQ);
        public static final VexRVMIOp EVINSERTPS      = new VexRVMIOp("EVINSERTPS",      VINSERTPS);
        public static final VexRVMIOp EVSHUFPS        = new VexRVMIOp("EVSHUFPS",        VSHUFPS);
        public static final VexRVMIOp EVSHUFPD        = new VexRVMIOp("EVSHUFPD",        VSHUFPD);
        public static final VexRVMIOp EVPTERNLOGD     = new VexRVMIOp("EVPTERNLOGD",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x25, VEXOpAssertion.AVX512F_VL,               EVEXTuple.FVM,      VEXPrefixConfig.W0, true);
        public static final VexRVMIOp EVPTERNLOGQ     = new VexRVMIOp("EVPTERNLOGQ",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x25, VEXOpAssertion.AVX512F_VL,               EVEXTuple.FVM,      VEXPrefixConfig.W1, true);

        // Packed Align Right
        public static final VexRVMIOp EVPALIGNR       = new VexRVMIOp("EVPALIGNR",       VPALIGNR);

        // AVX-512 insert
        public static final VexRVMIOp EVINSERTF32X4   = new VexRVMIOp("EVINSERTF32X4",   VINSERTF128);
        public static final VexRVMIOp EVINSERTI32X4   = new VexRVMIOp("EVINSERTI32X4",   VINSERTI128);
        public static final VexRVMIOp EVINSERTF64X2   = new VexRVMIOp("EVINSERTF64X2",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x18, VEXOpAssertion.AVX512DQ_VL_256_512,      EVEXTuple.T2_64BIT, VEXPrefixConfig.W1, true);
        public static final VexRVMIOp EVINSERTI64X2   = new VexRVMIOp("EVINSERTI64X2",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x38, VEXOpAssertion.AVX512DQ_VL_256_512,      EVEXTuple.T2_64BIT, VEXPrefixConfig.W1, true);
        public static final VexRVMIOp EVINSERTF32X8   = new VexRVMIOp("EVINSERTF32X8",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x1A, VEXOpAssertion.AVX512DQ_512ONLY,         EVEXTuple.T8_32BIT, VEXPrefixConfig.W0, true);
        public static final VexRVMIOp EVINSERTI32X8   = new VexRVMIOp("EVINSERTI32X8",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x3A, VEXOpAssertion.AVX512DQ_512ONLY,         EVEXTuple.T8_32BIT, VEXPrefixConfig.W0, true);
        public static final VexRVMIOp EVINSERTF64X4   = new VexRVMIOp("EVINSERTF64X4",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x1A, VEXOpAssertion.AVX512F_512ONLY,          EVEXTuple.T4_64BIT, VEXPrefixConfig.W1, true);
        public static final VexRVMIOp EVINSERTI64X4   = new VexRVMIOp("EVINSERTI64X4",   VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x3A, VEXOpAssertion.AVX512F_512ONLY,          EVEXTuple.T4_64BIT, VEXPrefixConfig.W1, true);

        public static final VexRVMIOp EVALIGND        = new VexRVMIOp("EVALIGND",        VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x03, VEXOpAssertion.AVX512F_VL,               EVEXTuple.FVM,      VEXPrefixConfig.W0, true);
        public static final VexRVMIOp EVALIGNQ        = new VexRVMIOp("EVALIGNQ",        VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x03, VEXOpAssertion.AVX512F_VL,               EVEXTuple.FVM,      VEXPrefixConfig.W1, true);

        // AVX-512 unsigned comparisons
        public static final VexRVMIOp EVPCMPUB        = new VexRVMIOp("EVPCMPUB",        VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x3E, VEXOpAssertion.MASK_XMM_XMM_AVX512BW_VL, EVEXTuple.FVM,      VEXPrefixConfig.W0, true);
        public static final VexRVMIOp EVPCMPUW        = new VexRVMIOp("EVPCMPUW",        VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x3E, VEXOpAssertion.MASK_XMM_XMM_AVX512BW_VL, EVEXTuple.FVM,      VEXPrefixConfig.W1, true);
        public static final VexRVMIOp EVPCMPUD        = new VexRVMIOp("EVPCMPUD",        VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0,  0x1E, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL,  EVEXTuple.FVM,      VEXPrefixConfig.W0, true);
        public static final VexRVMIOp EVPCMPUQ        = new VexRVMIOp("EVPCMPUQ",        VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x1E, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL,  EVEXTuple.FVM,      VEXPrefixConfig.W1, true);

        // Lane shuffles
        public static final VexRVMIOp EVSHUFI64X2     = new VexRVMIOp("EVSHUFI64X2",     VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1,  0x43, VEXOpAssertion.AVX512F_VL_256_512,       EVEXTuple.FVM,      VEXPrefixConfig.W1, true);

        // Galois Field New Instructions
        public static final VexRVMIOp EVGF2P8AFFINEQB = new VexRVMIOp("EVGF2P8AFFINEQB", VGF2P8AFFINEQB);
        // @formatter:on

        private VexRVMIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        private VexRVMIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex);
        }

        private VexRVMIOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex, boolean isEvex) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex, isEvex);
        }

        /**
         * Build the EVEX variant of a given vexOp.
         */
        private VexRVMIOp(String opcode, VexRVMIOp vexOp) {
            super(opcode, vexOp.pp, vexOp.mmmmm, vexOp.w, vexOp.op, vexOp.assertion, vexOp.evexTuple, vexOp.wEvex, true);
            variant = vexOp;
            assert vexOp.variant == null : "found 2 EVEX variants for VEX instruction " + vexOp;
            vexOp.variant = this;
        }

        @Override
        public VexRVMIOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexRVMIOp) encodingLogic(encoding);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, Register src2, int imm8) {
            assert (imm8 & 0xFF) == imm8 : imm8;
            emitVexOrEvex(asm, dst, src1, src2, size, pp, mmmmm, w, wEvex);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
            asm.emitByte(imm8);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, AMD64Address src2, int imm8) {
            assert (imm8 & 0xFF) == imm8 : imm8;
            emitVexOrEvex(asm, dst, src1, src2, size, pp, mmmmm, w, wEvex);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src2, 1, getDisp8Scale(isEvex, size));
            asm.emitByte(imm8);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, Register src2, Register mask, int imm8) {
            emit(asm, size, dst, src1, src2, mask, imm8, Z0, B0);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, Register src2, Register mask, int imm8, int z, int b) {
            assert (imm8 & 0xFF) == imm8 : imm8;
            emitVexOrEvex(asm, dst, src1, src2, mask, size, pp, mmmmm, w, wEvex, z, b);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
            asm.emitByte(imm8);
        }
    }

    /**
     * VEX-encoded comparison operation with an operand order of RVMI. The immediate operand is a
     * comparison operator.
     */
    public static final class VexIntegerCompareOp extends VexOp {
        // @formatter:off
        public static final VexIntegerCompareOp EVPCMPB  = new VexIntegerCompareOp("EVPCMPB",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x3F, VEXOpAssertion.MASK_XMM_XMM_AVX512BW_VL, EVEXTuple.FVM);
        public static final VexIntegerCompareOp EVPCMPW  = new VexIntegerCompareOp("EVPCMPW",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0x3F, VEXOpAssertion.MASK_XMM_XMM_AVX512BW_VL, EVEXTuple.FVM);
        public static final VexIntegerCompareOp EVPCMPD  = new VexIntegerCompareOp("EVPCMPD",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x1F, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL,  EVEXTuple.FVM);
        public static final VexIntegerCompareOp EVPCMPQ  = new VexIntegerCompareOp("EVPCMPQ",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0x1F, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL,  EVEXTuple.FVM);

        public static final VexIntegerCompareOp EVPCMPUB = new VexIntegerCompareOp("EVPCMPUB", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x3E, VEXOpAssertion.MASK_XMM_XMM_AVX512BW_VL, EVEXTuple.FVM);
        public static final VexIntegerCompareOp EVPCMPUW = new VexIntegerCompareOp("EVPCMPUW", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0x3E, VEXOpAssertion.MASK_XMM_XMM_AVX512BW_VL, EVEXTuple.FVM);
        public static final VexIntegerCompareOp EVPCMPUD = new VexIntegerCompareOp("EVPCMPUD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W0, 0x1E, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL,  EVEXTuple.FVM);
        public static final VexIntegerCompareOp EVPCMPUQ = new VexIntegerCompareOp("EVPCMPUQ", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F3A, VEXPrefixConfig.W1, 0x1E, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL,  EVEXTuple.FVM);
        // @formatter:on

        public enum Predicate {
            EQ(0),
            LT(1),
            LE(2),
            FALSE(3),
            NEQ(4),
            NLT(5),
            NLE(6),
            TRUE(7);

            private int imm8;

            Predicate(int imm8) {
                this.imm8 = imm8;
            }

            public static Predicate getPredicate(Condition condition) {
                return switch (condition) {
                    case EQ -> EQ;
                    case NE -> NEQ;
                    case LT, BT -> LT;
                    case LE, BE -> LE;
                    case GT, AT -> NLE;
                    case GE, AE -> NLT;
                    default -> throw GraalError.shouldNotReachHereUnexpectedValue(condition);
                };
            }
        }

        private VexIntegerCompareOp(String opcode, int pp, int mmmmm, int wEvex, int op, VEXOpAssertion assertion, EVEXTuple evexTuple) {
            super(opcode, pp, mmmmm, wEvex, op, assertion, evexTuple, wEvex, true);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, Register src2, Register mask, Predicate p) {
            emitVexOrEvex(asm, dst, src1, src2, mask, size, pp, mmmmm, w, wEvex, Z0, B0);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
            asm.emitByte(p.imm8);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, AMD64Address src2, Register mask, Predicate p, int b) {
            emitVexOrEvex(asm, dst, src1, src2, mask, size, pp, mmmmm, w, wEvex, Z0, b);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src2, 1, getDisp8Scale(isEvex, size));
            asm.emitByte(p.imm8);
        }
    }

    /**
     * VEX-encoded comparison operation with an operand order of RVMI. The immediate operand is a
     * comparison operator.
     */
    public static final class VexFloatCompareOp extends VexOp {
        // @formatter:off
        public static final VexFloatCompareOp VCMPPS  = new VexFloatCompareOp("VCMPPS",  VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xC2, VEXOpAssertion.AVX1);
        public static final VexFloatCompareOp VCMPPD  = new VexFloatCompareOp("VCMPPD",  VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xC2, VEXOpAssertion.AVX1);
        public static final VexFloatCompareOp VCMPSS  = new VexFloatCompareOp("VCMPSS",  VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xC2, VEXOpAssertion.AVX1_128ONLY);
        public static final VexFloatCompareOp VCMPSD  = new VexFloatCompareOp("VCMPSD",  VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.WIG, 0xC2, VEXOpAssertion.AVX1_128ONLY);

        // EVEX encoded instructions
        public static final VexFloatCompareOp EVCMPPS = new VexFloatCompareOp("EVCMPPS", VEXPrefixConfig.P_,   VEXPrefixConfig.M_0F, VEXPrefixConfig.W0,  0xC2, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL,  EVEXTuple.FVM,       VEXPrefixConfig.W0, VCMPPS);
        public static final VexFloatCompareOp EVCMPPD = new VexFloatCompareOp("EVCMPPD", VEXPrefixConfig.P_66, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1,  0xC2, VEXOpAssertion.MASK_XMM_XMM_AVX512F_VL,  EVEXTuple.FVM,       VEXPrefixConfig.W1, VCMPPD);
        public static final VexFloatCompareOp EVCMPSS = new VexFloatCompareOp("EVCMPSS", VEXPrefixConfig.P_F3, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0,  0xC2, VEXOpAssertion.MASK_XMM_XMM_AVX512F_128, EVEXTuple.T1S_32BIT, VEXPrefixConfig.W0, VCMPSS);
        public static final VexFloatCompareOp EVCMPSD = new VexFloatCompareOp("EVCMPSD", VEXPrefixConfig.P_F2, VEXPrefixConfig.M_0F, VEXPrefixConfig.W1,  0xC2, VEXOpAssertion.MASK_XMM_XMM_AVX512F_128, EVEXTuple.T1S_64BIT, VEXPrefixConfig.W1, VCMPSD);
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

        private VexFloatCompareOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion) {
            super(opcode, pp, mmmmm, w, op, assertion);
        }

        /**
         * Build the EVEX variant of a given vexOp.
         */
        private VexFloatCompareOp(String opcode, int pp, int mmmmm, int w, int op, VEXOpAssertion assertion, EVEXTuple evexTuple, int wEvex, VexFloatCompareOp vexCounterPart) {
            super(opcode, pp, mmmmm, w, op, assertion, evexTuple, wEvex, true);
            variant = vexCounterPart;
            assert vexCounterPart.variant == null : "found 2 EVEX variants for VEX instruction " + vexCounterPart;
            vexCounterPart.variant = this;
        }

        @Override
        public VexFloatCompareOp encoding(AMD64SIMDInstructionEncoding encoding) {
            return (VexFloatCompareOp) encodingLogic(encoding);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, Register src2, Predicate p) {
            emit(asm, size, dst, src1, src2, Register.None, p);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, AMD64Address src2, Predicate p) {
            emit(asm, size, dst, src1, src2, Register.None, p, B0);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, Register src2, Register mask, Predicate p) {
            emitVexOrEvex(asm, dst, src1, src2, mask, size, pp, mmmmm, w, wEvex, Z0, B0);
            asm.emitByte(op);
            asm.emitModRM(dst, src2);
            asm.emitByte(p.imm8);
        }

        public void emit(AMD64Assembler asm, AVXSize size, Register dst, Register src1, AMD64Address src2, Register mask, Predicate p, int b) {
            emitVexOrEvex(asm, dst, src1, src2, mask, size, pp, mmmmm, w, wEvex, Z0, b);
            asm.emitByte(op);
            asm.emitOperandHelper(dst, src2, 1, getDisp8Scale(isEvex, size));
            asm.emitByte(p.imm8);
        }
    }

    public final void emit(VexRMOp op, Register dst, Register src, AVXSize size) {
        op.emit(this, size, dst, src);
    }

    public final void emit(VexRMOp op, Register dst, AMD64Address src, AVXSize size) {
        op.emit(this, size, dst, src);
    }

    public final void emit(VexRMIOp op, Register dst, Register src, int imm8, AVXSize size) {
        op.emit(this, size, dst, src, imm8);
    }

    public final void emit(VexMRIOp op, Register dst, Register src, int imm8, AVXSize size) {
        op.emit(this, size, dst, src, imm8);
    }

    public final void emit(VexRVMOp op, Register dst, Register src1, Register src2, AVXSize size) {
        op.emit(this, size, dst, src1, src2);
    }

    public final void emit(VexGeneralPurposeRMVOp op, Register dst, Register src1, Register src2, AVXSize size) {
        op.emit(this, size, dst, src1, src2);
    }

    // Instructions, optimizations and erratum related to jumps

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
         * <li>it has an actual jump distance &lt; (127 -
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

    // Instructions with direct byte emission

    @Override
    public void align(int modulus) {
        align(modulus, position());
    }

    @Override
    public final void ensureUniquePC() {
        nop();
    }

    @Override
    public void halt() {
        hlt();
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

    private void addrNop8() {
        // 8 bytes: NOP DWORD PTR [EAX+EAX*0+0] 32-bits offset
        emitByte(0x0F);
        emitByte(0x1F);
        emitByte(0x84); // emitRm(cbuf, 0x2, EAXEnc, 0x4);
        emitByte(0x00); // emitRm(cbuf, 0x0, EAXEnc, EAXEnc);
        emitInt(0); // 32-bits offset (4 bytes)
    }

    private void addrNop7() {
        // 7 bytes: NOP DWORD PTR [EAX+0] 32-bits offset
        emitByte(0x0F);
        emitByte(0x1F);
        emitByte(0x80); // emitRm(cbuf, 0x2, EAXEnc, EAXEnc);
        emitInt(0); // 32-bits offset (4 bytes)
    }

    private void addrNop5() {
        // 5 bytes: NOP DWORD PTR [EAX+EAX*0+0] 8-bits offset
        emitByte(0x0F);
        emitByte(0x1F);
        emitByte(0x44); // emitRm(cbuf, 0x1, EAXEnc, 0x4);
        emitByte(0x00); // emitRm(cbuf, 0x0, EAXEnc, EAXEnc);
        emitByte(0); // 8-bits offset (1 byte)
    }

    private void addrNop4() {
        // 4 bytes: NOP DWORD PTR [EAX+0]
        emitByte(0x0F);
        emitByte(0x1F);
        emitByte(0x40); // emitRm(cbuf, 0x1, EAXEnc, EAXEnc);
        emitByte(0); // 8-bits offset (1 byte)
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

    public final void bswapl(Register reg) {
        prefix(reg);
        emitByte(0x0F);
        emitModRM(1, reg);
    }

    public final void bswapq(Register reg) {
        prefixq(reg);
        emitByte(0x0F);
        emitByte(0xC8 + encode(reg));
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

    public final void cdql() {
        emitByte(0x99);
    }

    public final void cdqq() {
        rexw();
        emitByte(0x99);
    }

    public final void clflush(AMD64Address adr) {
        prefix(adr);
        // opcode family is 0x0F 0xAE
        emitByte(0x0f);
        emitByte(0xae);
        // extended opcode byte is 7
        emitOperandHelper(7, adr, 0);
    }

    public final void cmovl(ConditionFlag cc, Register dst, Register src) {
        prefix(dst, src);
        emitByte(0x0F);
        emitByte(0x40 | cc.getValue());
        emitModRM(dst, src);
    }

    public final void cmovl(ConditionFlag cc, Register dst, AMD64Address src) {
        interceptMemorySrcOperands(src);
        prefix(src, dst);
        emitByte(0x0F);
        emitByte(0x40 | cc.getValue());
        emitOperandHelper(dst, src, 0);
    }

    public final void cmovq(ConditionFlag cc, Register dst, Register src) {
        prefixq(dst, src);
        emitByte(0x0F);
        emitByte(0x40 | cc.getValue());
        emitModRM(dst, src);
    }

    public final void cmovq(ConditionFlag cc, Register dst, AMD64Address src) {
        interceptMemorySrcOperands(src);
        prefixq(src, dst);
        emitByte(0x0F);
        emitByte(0x40 | cc.getValue());
        emitOperandHelper(dst, src, 0);
    }

    public final void endbranch() {
        emitByte(0xf3);
        emitByte(0x0f);
        emitByte(0x1e);
        emitByte(0xfa);
    }

    public final void fcos() {
        emitByte(0xD9);
        emitByte(0xFF);
    }

    public final void ffree(int i) {
        emitFPUArith(0xDD, 0xC0, i);
    }

    public final void fincstp() {
        emitByte(0xD9);
        emitByte(0xF7);
    }

    public final void fldd(AMD64Address src) {
        interceptMemorySrcOperands(src);
        emitByte(0xDD);
        emitOperandHelper(0, src, 0);
    }

    public final void fldlg2() {
        emitByte(0xD9);
        emitByte(0xEC);
    }

    public final void fldln2() {
        emitByte(0xD9);
        emitByte(0xED);
    }

    public final void flds(AMD64Address src) {
        interceptMemorySrcOperands(src);
        emitByte(0xD9);
        emitOperandHelper(0, src, 0);
    }

    public final void fnstswAX() {
        emitByte(0xDF);
        emitByte(0xE0);
    }

    public final void fprem() {
        emitByte(0xD9);
        emitByte(0xF8);
    }

    public final void fptan() {
        emitByte(0xD9);
        emitByte(0xF2);
    }

    public final void fsin() {
        emitByte(0xD9);
        emitByte(0xFE);
    }

    public final void fstp(int i) {
        assert 0 <= i && i < 8 : "illegal stack offset";
        emitByte(0xDD);
        emitByte(0xD8 + i);
    }

    public final void fstpd(AMD64Address src) {
        interceptMemorySrcOperands(src);
        emitByte(0xDD);
        emitOperandHelper(3, src, 0);
    }

    public final void fstps(AMD64Address src) {
        interceptMemorySrcOperands(src);
        emitByte(0xD9);
        emitOperandHelper(3, src, 0);
    }

    public final void fwait() {
        emitByte(0x9B);
    }

    public final void fxch(int i) {
        emitFPUArith(0xD9, 0xC8, i);
    }

    private void emitFPUArith(int b1, int b2, int i) {
        assert 0 <= i && i < 8 : "illegal FPU register: " + i;
        emitByte(b1);
        emitByte(b2 + i);
    }

    public final void fyl2x() {
        emitByte(0xD9);
        emitByte(0xF1);
    }

    public final void hlt() {
        emitByte(0xF4);
    }

    /**
     * Emits an instruction which is considered to be illegal. This is used if we deliberately want
     * to crash the program (debugging etc.).
     */
    public final void illegal() {
        emitByte(0x0f);
        emitByte(0x0b);
    }

    public final void int3() {
        emitByte(0xCC);
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

    public void lfence() {
        emitByte(0x0f);
        emitByte(0xae);
        emitByte(0xe8);
    }

    public void lock() {
        emitByte(0xF0);
    }

    public final void membar(int barriers) {
        if (isTargetMP()) {
            // We only have to handle StoreLoad
            if ((barriers & STORE_LOAD) != 0) {
                lock();
                // Assert the lock# signal here
                addl(new AMD64Address(AMD64.rsp, membarOffset()), 0);
            }
        }
    }

    protected int membarOffset() {
        return 0;
    }

    // AMD64MIOp.MOV uses an extra prefix
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

    public final void movlhps(Register dst, Register src) {
        assert inRC(XMM, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, dst, src, OperandSize.PS, P_0F, false);
        emitByte(0x16);
        emitModRM(dst, src);
    }

    /**
     * New CPUs require use of movsd and movss to avoid partial register stall when loading from
     * memory. But for old Opteron use movlpd instead of movsd. The selection is done in
     * {@link AMD64MacroAssembler#movdbl(Register, AMD64Address)} and
     * {@link AMD64MacroAssembler#movflt(Register, Register)}.
     */
    public final void movlpd(Register dst, AMD64Address src) {
        assert inRC(XMM, dst);
        interceptMemorySrcOperands(src);
        simdPrefix(dst, dst, src, OperandSize.PD, P_0F, false);
        emitByte(0x12);
        emitOperandHelper(dst, src, 0);
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
            interceptMemorySrcOperands(src);
            simdPrefix(dst, Register.None, src, OperandSize.SS, P_0F, false);
            emitByte(0x7E);
            emitOperandHelper(dst, src, force4BytesDisplacement, 0);
        } else {
            // gpr version of movq
            AMD64RMOp.MOV.emit(this, OperandSize.QWORD, dst, src, force4BytesDisplacement);
        }
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

    public final void movq(Register dst, Register src) {
        if (inRC(XMM, dst) && inRC(XMM, src)) {
            // Insn: MOVQ xmm1, xmm2
            // Code: F3 0F 7E /r
            simdPrefix(dst, Register.None, src, OperandSize.SS, P_0F, false);
            emitByte(0x7E);
            emitModRM(dst, src);
        } else {
            AMD64RMOp.MOV.emit(this, OperandSize.QWORD, dst, src);
        }
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
            AMD64MROp.MOV.emit(this, OperandSize.QWORD, dst, src);
        }
    }

    public final void nop() {
        nop(1);
    }

    public final void nop(int count) {
        intelNops(count);
    }

    public final void pause() {
        emitByte(0xF3);
        emitByte(0x90);
    }

    public final void pextrw(Register dst, Register src, int imm8) {
        assert inRC(CPU, dst) && inRC(XMM, src) : dst + " " + src;
        simdPrefix(dst, Register.None, src, OperandSize.PD, P_0F, false);
        emitByte(0xC5);
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

    public final void pop(Register dst) {
        prefix(dst);
        emitByte(0x58 + encode(dst));
    }

    public final void prefetchnta(AMD64Address src) {
        prefetchPrefix(src);
        emitByte(0x18);
        emitOperandHelper(0, src, 0);
    }

    private void prefetchPrefix(AMD64Address src) {
        prefix(src);
        emitByte(0x0F);
    }

    public final void prefetcht0(AMD64Address src) {
        assert supports(CPUFeature.SSE);
        prefetchPrefix(src);
        emitByte(0x18);
        emitOperandHelper(1, src, 0);
    }

    public final void prefetcht1(AMD64Address src) {
        assert supports(CPUFeature.SSE);
        prefetchPrefix(src);
        emitByte(0x18);
        emitOperandHelper(2, src, 0);
    }

    public final void prefetcht2(AMD64Address src) {
        assert supports(CPUFeature.SSE);
        prefix(src);
        emitByte(0x0f);
        emitByte(0x18);
        emitOperandHelper(3, src, 0);
    }

    public final void prefetchw(AMD64Address src) {
        assert supports(CPUFeature.AMD_3DNOW_PREFETCH);
        prefix(src);
        emitByte(0x0f);
        emitByte(0x0D);
        emitOperandHelper(1, src, 0);
    }

    public final void push(int imm32) {
        emitByte(0x68);
        emitInt(imm32);
    }

    public final void push(Register src) {
        assert inRC(CPU, src);
        prefix(src);
        emitByte(0x50 + encode(src));
    }

    public final void pushfq() {
        emitByte(0x9c);
    }

    public final void rdpid(Register dst) {
        assert supports(CPUFeature.RDPID);
        emitByte(0xF3);
        prefix(dst);
        emitByte(0x0F);
        emitByte(0xC7);
        emitModRM(7, dst);
    }

    public final void rdpkru() {
        emitByte(0x0F);
        emitByte(0x01);
        emitByte(0xEE);
    }

    public final void rdtsc() {
        emitByte(0x0F);
        emitByte(0x31);
    }

    public final void rdtscp() {
        emitByte(0x0F);
        emitByte(0x01);
        emitByte(0xF9);
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

    public final void setb(ConditionFlag cc, Register dst) {
        prefix(dst, true);
        emitByte(0x0F);
        emitByte(0x90 | cc.getValue());
        emitModRM(0, dst);
    }

    public final void sfence() {
        assert supports(CPUFeature.SSE2);
        emitByte(0x0f);
        emitByte(0xae);
        emitByte(0xf8);
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

    /**
     * Emit a UD2 instruction, this signals the processor to stop decoding instructions further in
     * the fallthrough path (Intel Optimization Reference Manual Volume 1, section 3.4.1.5, Branch
     * Type Selection, Assembly/Compiler coding rule 13).
     * <p>
     * This also helps when we want to emit data in the code section as it prevents mismatched
     * instructions when decoding from different paths. E.g. consider this piece of hex code:
     * <p>
     * {@code 01 48 01 c8}
     * <p>
     * With {@code 01} being the data and {@code 48 01 c8} being {@code add rax, rcx}. However, if
     * the decoder starts with {@code 01} it will see the next instruction being {@code 01 48 01}
     * which is {@code add [rax + 1], ecx}. This mismatch invalidates the uop cache as the CPU
     * cannot know which instruction sequence is the correct one.
     */
    public void ud2() {
        emitByte(0x0F);
        emitByte(0x0B);
    }

    public final void vzeroupper() {
        emitVEX(VEXPrefixConfig.L128, VEXPrefixConfig.P_, VEXPrefixConfig.M_0F, VEXPrefixConfig.W0, 0, 0);
        emitByte(0x77);
    }

    public final void wrpkru() {
        emitByte(0x0F);
        emitByte(0x01);
        emitByte(0xEF);
    }

    // Helper methods delegating to an AMD64Op

    public final void nullCheck(AMD64Address address) {
        testl(AMD64.rax, address);
    }

    public final void adcl(Register dst, int imm32) {
        AMD64BinaryArithmetic.ADC.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void adcq(Register dst, int imm32) {
        AMD64BinaryArithmetic.ADC.getMIOpcode(OperandSize.QWORD, isByte(imm32)).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void adcxq(Register dst, Register src) {
        AMD64RMOp.ADCX.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void addl(AMD64Address dst, int imm32) {
        AMD64BinaryArithmetic.ADD.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void addl(Register dst, int imm32) {
        AMD64BinaryArithmetic.ADD.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void addl(Register dst, Register src) {
        AMD64BinaryArithmetic.ADD.rmOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void addl(Register dst, AMD64Address src) {
        AMD64BinaryArithmetic.ADD.rmOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void addpd(Register dst, Register src) {
        SSEOp.ADD.emit(this, OperandSize.PD, dst, src);
    }

    public final void addpd(Register dst, AMD64Address src) {
        SSEOp.ADD.emit(this, OperandSize.PD, dst, src);
    }

    public final void addq(Register dst, int imm32) {
        AMD64BinaryArithmetic.ADD.getMIOpcode(OperandSize.QWORD, isByte(imm32)).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void addq(AMD64Address dst, int imm32) {
        AMD64BinaryArithmetic.ADD.getMIOpcode(OperandSize.QWORD, isByte(imm32)).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void addq(Register dst, Register src) {
        AMD64BinaryArithmetic.ADD.rmOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void addq(Register dst, AMD64Address src) {
        AMD64BinaryArithmetic.ADD.rmOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void addq(AMD64Address dst, Register src) {
        AMD64BinaryArithmetic.ADD.mrOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void addsd(Register dst, Register src) {
        SSEOp.ADD.emit(this, OperandSize.SD, dst, src);
    }

    public final void addsd(Register dst, AMD64Address src) {
        SSEOp.ADD.emit(this, OperandSize.SD, dst, src);
    }

    public final void adoxq(Register dst, Register src) {
        AMD64RMOp.ADOX.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void aesdec(Register dst, Register src) {
        SSEOp.AESDEC.emit(this, OperandSize.PD, dst, src);
    }

    public final void aesdeclast(Register dst, Register src) {
        SSEOp.AESDECLAST.emit(this, OperandSize.PD, dst, src);
    }

    public final void aesenc(Register dst, Register src) {
        SSEOp.AESENC.emit(this, OperandSize.PD, dst, src);
    }

    public final void aesenclast(Register dst, Register src) {
        SSEOp.AESENCLAST.emit(this, OperandSize.PD, dst, src);
    }

    public final void andl(Register dst, int imm32) {
        AMD64BinaryArithmetic.AND.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void andl(Register dst, Register src) {
        AMD64BinaryArithmetic.AND.rmOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void andnpd(Register dst, Register src) {
        SSEOp.ANDN.emit(this, OperandSize.PD, dst, src);
    }

    public final void andpd(Register dst, Register src) {
        SSEOp.AND.emit(this, OperandSize.PD, dst, src);
    }

    public final void andpd(Register dst, AMD64Address src) {
        SSEOp.AND.emit(this, OperandSize.PD, dst, src);
    }

    public final void andq(Register dst, int imm32) {
        AMD64BinaryArithmetic.AND.getMIOpcode(OperandSize.QWORD, isByte(imm32)).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void andq(Register dst, AMD64Address src) {
        AMD64BinaryArithmetic.AND.getRMOpcode(OperandSize.QWORD).emit(this, OperandSize.QWORD, dst, src);
    }

    public final void andq(Register dst, Register src) {
        AMD64BinaryArithmetic.AND.getRMOpcode(OperandSize.QWORD).emit(this, OperandSize.QWORD, dst, src);
    }

    public final void bsfl(Register dst, Register src) {
        AMD64RMOp.BSF.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void bsfq(Register dst, Register src) {
        AMD64RMOp.BSF.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void bsrl(Register dst, Register src) {
        AMD64RMOp.BSR.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void bsrq(Register dst, Register src) {
        AMD64RMOp.BSR.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void btq(Register src, int imm8) {
        AMD64MIOp.BT.emit(this, OperandSize.QWORD, src, imm8);
    }

    public final void btrq(Register src, int imm8) {
        AMD64MIOp.BTR.emit(this, OperandSize.QWORD, src, imm8);
    }

    public final void btsq(Register src, int imm8) {
        AMD64MIOp.BTS.emit(this, OperandSize.QWORD, src, imm8);
    }

    public final void cmpb(Register dst, Register src) {
        AMD64BinaryArithmetic.CMP.byteRmOp.emit(this, OperandSize.BYTE, dst, src);
    }

    public final void cmpb(AMD64Address dst, int imm) {
        AMD64BinaryArithmetic.CMP.byteImmOp.emit(this, OperandSize.BYTE, dst, imm);
    }

    public final void cmpl(Register dst, int imm32) {
        AMD64BinaryArithmetic.CMP.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void cmpl(Register dst, Register src) {
        AMD64BinaryArithmetic.CMP.rmOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void cmpl(Register dst, AMD64Address src) {
        AMD64BinaryArithmetic.CMP.rmOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void cmpl(AMD64Address dst, int imm32) {
        AMD64BinaryArithmetic.CMP.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void cmpq(Register dst, int imm32) {
        AMD64BinaryArithmetic.CMP.getMIOpcode(OperandSize.QWORD, isByte(imm32)).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void cmpq(AMD64Address dst, int imm32) {
        AMD64BinaryArithmetic.CMP.getMIOpcode(OperandSize.QWORD, isByte(imm32)).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void cmpq(Register dst, Register src) {
        AMD64BinaryArithmetic.CMP.rmOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void cmpq(Register dst, AMD64Address src) {
        AMD64BinaryArithmetic.CMP.rmOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void cmpw(AMD64Address dst, int imm16) {
        AMD64BinaryArithmetic.CMP.getMIOpcode(OperandSize.WORD, isByte(imm16)).emit(this, OperandSize.WORD, dst, imm16);
    }

    public final void cmpw(Register dst, Register src) {
        AMD64BinaryArithmetic.CMP.rmOp.emit(this, OperandSize.WORD, dst, src);
    }

    /**
     * Emit a cmpw with an imm16 operand regardless of the input value.
     */
    public final void cmpwImm16(AMD64Address dst, int imm16) {
        AMD64BinaryArithmetic.CMP.getMIOpcode(OperandSize.WORD, false).emit(this, OperandSize.WORD, dst, imm16);
    }

    /**
     * The 8-bit cmpxchg compares the value at adr with the contents of X86.rax, and stores reg into
     * adr if so; otherwise, the value at adr is loaded into X86.rax,. The ZF is set if the compared
     * values were equal, and cleared otherwise.
     */
    public final void cmpxchgb(AMD64Address adr, Register reg) { // cmpxchg
        AMD64MROp.CMPXCHGB.emit(this, OperandSize.BYTE, adr, reg);
    }

    /**
     * The 32-bit cmpxchg compares the value at adr with the contents of X86.rax, and stores reg
     * into adr if so; otherwise, the value at adr is loaded into X86.rax,. The ZF is set if the
     * compared values were equal, and cleared otherwise.
     */
    public final void cmpxchgl(AMD64Address adr, Register reg) { // cmpxchg
        AMD64MROp.CMPXCHG.emit(this, OperandSize.DWORD, adr, reg);
    }

    public final void cmpxchgq(Register reg, AMD64Address adr) {
        AMD64RMOp.CMPXCHG.emit(this, OperandSize.QWORD, reg, adr);
    }

    /**
     * The 16-bit cmpxchg compares the value at adr with the contents of X86.rax, and stores reg
     * into adr if so; otherwise, the value at adr is loaded into X86.rax,. The ZF is set if the
     * compared values were equal, and cleared otherwise.
     */
    public final void cmpxchgw(AMD64Address adr, Register reg) { // cmpxchg
        AMD64MROp.CMPXCHG.emit(this, OperandSize.WORD, adr, reg);
    }

    public final void cvtdq2pd(Register dst, Register src) {
        SSEOp.CVTDQ2PD.emit(this, OperandSize.SS, dst, src);
    }

    public final void cvtsd2siq(Register dst, Register src) {
        SSEOp.CVTSD2SI.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void cvtsi2sdl(Register dst, Register src) {
        SSEOp.CVTSI2SD.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void cvtsi2sdq(Register dst, Register src) {
        SSEOp.CVTSI2SD.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void cvttpd2dq(Register dst, Register src) {
        SSEOp.CVTTPD2DQ.emit(this, OperandSize.PD, dst, src);
    }

    public final void cvttsd2sil(Register dst, Register src) {
        SSEOp.CVTTSD2SI.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void cvttsd2siq(Register dst, Register src) {
        SSEOp.CVTTSD2SI.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void cvttss2sil(Register dst, Register src) {
        SSEOp.CVTTSS2SI.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void decl(AMD64Address dst) {
        AMD64MOp.DEC.emit(this, OperandSize.DWORD, dst);
    }

    public final void decl(Register dst) {
        // Use two-byte form (one-byte form is a REX prefix in 64-bit mode)
        AMD64MOp.DEC.emit(this, OperandSize.DWORD, dst);
    }

    public final void decq(Register dst) {
        // Use two-byte form (one-byte form is a REX prefix in 64-bit mode)
        AMD64MOp.DEC.emit(this, OperandSize.QWORD, dst);
    }

    public final void decq(AMD64Address dst) {
        AMD64MOp.DEC.emit(this, OperandSize.QWORD, dst);
    }

    public final void divsd(Register dst, Register src) {
        SSEOp.DIV.emit(this, OperandSize.SD, dst, src);
    }

    public final void gf2p8affineqb(Register dst, Register src, int imm8) {
        if (supports(CPUFeature.AVX)) {
            VexRVMIOp.VGF2P8AFFINEQB.emit(this, AVXSize.XMM, dst, dst, src, imm8);
        } else {
            SSERMIOp.GF2P8AFFINEQB.emit(this, OperandSize.PD, dst, src, imm8);
        }
    }

    public final void imull(Register dst, Register src, int value) {
        if (isByte(value)) {
            AMD64RMIOp.IMUL_SX.emit(this, OperandSize.DWORD, dst, src, value);
        } else {
            AMD64RMIOp.IMUL.emit(this, OperandSize.DWORD, dst, src, value);
        }
    }

    public final void imull(Register dst, Register src) {
        AMD64RMOp.IMUL.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void imulq(Register dst, Register src) {
        AMD64RMOp.IMUL.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void incl(AMD64Address dst) {
        AMD64MOp.INC.emit(this, OperandSize.DWORD, dst);
    }

    public final void incl(Register dst) {
        // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
        AMD64MOp.INC.emit(this, OperandSize.DWORD, dst);
    }

    public final void incq(Register dst) {
        // Don't use it directly. Use Macroincrementq() instead.
        // Use two-byte form (one-byte from is a REX prefix in 64-bit mode)
        AMD64MOp.INC.emit(this, OperandSize.QWORD, dst);
    }

    public final void incq(AMD64Address dst) {
        AMD64MOp.INC.emit(this, OperandSize.QWORD, dst);
    }

    public final void movapd(Register dst, AMD64Address src) {
        SSEOp.MOVAPD.emit(this, OperandSize.PD, dst, src);
    }

    public final void movapd(Register dst, Register src) {
        SSEOp.MOVAPD.emit(this, OperandSize.PD, dst, src);
    }

    public final void movaps(Register dst, Register src) {
        SSEOp.MOVAPS.emit(this, OperandSize.PS, dst, src);
    }

    public final void movb(Register dst, AMD64Address src) {
        AMD64RMOp.MOVB.emit(this, OperandSize.BYTE, dst, src);
    }

    public final void movb(AMD64Address dst, int imm8) {
        AMD64MIOp.MOVB.emit(this, OperandSize.BYTE, dst, imm8);
    }

    public final void movb(AMD64Address dst, Register src) {
        AMD64MROp.MOVB.emit(this, OperandSize.BYTE, dst, src);
    }

    public final void movddup(Register dst, Register src) {
        SSEOp.MOVDDUP.emit(this, OperandSize.SD, dst, src);
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

    public final void movdqa(Register dst, Register src) {
        SSEOp.MOVDQA.emit(this, OperandSize.PD, dst, src);
    }

    public final void movdqu(Register dst, AMD64Address src) {
        SSEOp.MOVDQU.emit(this, OperandSize.SS, dst, src);
    }

    public final void movdqu(Register dst, Register src) {
        SSEOp.MOVDQU.emit(this, OperandSize.SS, dst, src);
    }

    public final void movdqu(AMD64Address dst, Register src) {
        SSEMROp.MOVDQU.emit(this, OperandSize.SS, dst, src);
    }

    public final void movl(Register dst, Register src) {
        AMD64RMOp.MOV.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void movl(Register dst, AMD64Address src) {
        AMD64RMOp.MOV.emit(this, OperandSize.DWORD, dst, src);
    }

    /**
     * @param wide use 4 byte encoding for displacements that would normally fit in a byte
     */
    public final void movl(Register dst, AMD64Address src, boolean wide) {
        AMD64RMOp.MOV.emit(this, OperandSize.DWORD, dst, src, wide);
    }

    public final void movl(AMD64Address dst, int imm32) {
        AMD64MIOp.MOV.emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void movl(AMD64Address dst, Register src) {
        AMD64MROp.MOV.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void movsbl(Register dst, AMD64Address src) {
        AMD64RMOp.MOVSXB.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void movsbl(Register dst, Register src) {
        AMD64RMOp.MOVSXB.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void movsbq(Register dst, AMD64Address src) {
        AMD64RMOp.MOVSXB.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void movsbq(Register dst, Register src) {
        AMD64RMOp.MOVSXB.emit(this, OperandSize.QWORD, dst, src);
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

    public final void movslq(Register dst, int imm32) {
        AMD64MIOp.MOV.emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void movslq(AMD64Address dst, int imm32) {
        AMD64MIOp.MOV.emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void movslq(Register dst, AMD64Address src) {
        AMD64RMOp.MOVSXD.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void movslq(Register dst, Register src) {
        AMD64RMOp.MOVSXD.emit(this, OperandSize.QWORD, dst, src);
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
        AMD64MIOp.MOV.emit(this, OperandSize.WORD, dst, imm16);
    }

    public final void movw(AMD64Address dst, Register src) {
        AMD64MROp.MOV.emit(this, OperandSize.WORD, dst, src);
    }

    public final void movw(Register dst, AMD64Address src) {
        AMD64RMOp.MOV.emit(this, OperandSize.WORD, dst, src);
    }

    public final void movzbl(Register dst, AMD64Address src) {
        AMD64RMOp.MOVZXB.emit(this, OperandSize.DWORD, dst, src);
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

    public final void mull(Register src) {
        AMD64MOp.MUL.emit(this, OperandSize.DWORD, src);
    }

    public final void mulpd(Register dst, Register src) {
        SSEOp.MUL.emit(this, OperandSize.PD, dst, src);
    }

    public final void mulpd(Register dst, AMD64Address src) {
        SSEOp.MUL.emit(this, OperandSize.PD, dst, src);
    }

    public final void mulq(Register src) {
        AMD64MOp.MUL.emit(this, OperandSize.QWORD, src);
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

    public final void mulxq(Register dst1, Register dst2, Register src) {
        VexGeneralPurposeRVMOp.MULX.emit(this, AVXSize.QWORD, dst1, dst2, src);
    }

    public final void negl(Register dst) {
        AMD64MOp.NEG.emit(this, OperandSize.DWORD, dst);
    }

    public final void negq(Register dst) {
        AMD64MOp.NEG.emit(this, OperandSize.QWORD, dst);
    }

    public final void notl(Register dst) {
        AMD64MOp.NOT.emit(this, OperandSize.DWORD, dst);
    }

    public final void notq(Register dst) {
        AMD64MOp.NOT.emit(this, OperandSize.QWORD, dst);
    }

    public final void orl(Register dst, Register src) {
        AMD64BinaryArithmetic.OR.rmOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void orl(Register dst, AMD64Address src) {
        AMD64BinaryArithmetic.OR.rmOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void orl(AMD64Address dst, Register src) {
        AMD64BinaryArithmetic.OR.mrOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void orl(Register dst, int imm32) {
        AMD64BinaryArithmetic.OR.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void orpd(Register dst, Register src) {
        SSEOp.OR.emit(this, OperandSize.PD, dst, src);
    }

    public final void orq(Register dst, Register src) {
        AMD64BinaryArithmetic.OR.rmOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void orq(Register dst, AMD64Address src) {
        AMD64BinaryArithmetic.OR.rmOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void orq(Register dst, int imm32) {
        AMD64BinaryArithmetic.OR.getMIOpcode(OperandSize.QWORD, isByte(imm32)).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void orqImm32(Register dst, int imm32) {
        AMD64BinaryArithmetic.OR.getMIOpcode(OperandSize.QWORD, false).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void packusdw(Register dst, Register src) {
        SSEOp.PACKUSDW.emit(this, OperandSize.PD, dst, src);
    }

    public final void packuswb(Register dst, Register src) {
        SSEOp.PACKUSWB.emit(this, OperandSize.PD, dst, src);
    }

    public final void paddd(Register dst, Register src) {
        SSEOp.PADDD.emit(this, OperandSize.PD, dst, src);
    }

    public final void paddd(Register dst, AMD64Address src) {
        SSEOp.PADDD.emit(this, OperandSize.PD, dst, src);
    }

    public final void paddq(Register dst, Register src) {
        SSEOp.PADDQ.emit(this, OperandSize.PD, dst, src);
    }

    public final void palignr(Register dst, Register src, int imm8) {
        SSERMIOp.PALIGNR.emit(this, OperandSize.PD, dst, src, imm8);
    }

    public final void pand(Register dst, Register src) {
        SSEOp.PAND.emit(this, OperandSize.PD, dst, src);
    }

    public final void pand(Register dst, AMD64Address src) {
        SSEOp.PAND.emit(this, OperandSize.PD, dst, src);
    }

    public final void pandn(Register dst, Register src) {
        SSEOp.PANDN.emit(this, OperandSize.PD, dst, src);
    }

    public final void pblendw(Register dst, Register src, int imm8) {
        SSERMIOp.PBLENDW.emit(this, OperandSize.PD, dst, src, imm8);
    }

    public final void pclmulqdq(Register dst, Register src, int imm8) {
        SSERMIOp.PCLMULQDQ.emit(this, OperandSize.PD, dst, src, imm8);
    }

    public final void pcmpeqb(Register dst, Register src) {
        SSEOp.PCMPEQB.emit(this, OperandSize.PD, dst, src);
    }

    public final void pcmpeqb(Register dst, AMD64Address src) {
        SSEOp.PCMPEQB.emit(this, OperandSize.PD, dst, src);
    }

    public final void pcmpeqd(Register dst, Register src) {
        SSEOp.PCMPEQD.emit(this, OperandSize.PD, dst, src);
    }

    public final void pcmpeqd(Register dst, AMD64Address src) {
        SSEOp.PCMPEQD.emit(this, OperandSize.PD, dst, src);
    }

    public final void pcmpeqw(Register dst, Register src) {
        SSEOp.PCMPEQW.emit(this, OperandSize.PD, dst, src);
    }

    public final void pcmpeqw(Register dst, AMD64Address src) {
        SSEOp.PCMPEQW.emit(this, OperandSize.PD, dst, src);
    }

    public final void pcmpestri(Register dst, AMD64Address src, int imm8) {
        SSERMIOp.PCMPESTRI.emit(this, OperandSize.PD, dst, src, imm8);
    }

    public final void pcmpestri(Register dst, Register src, int imm8) {
        SSERMIOp.PCMPESTRI.emit(this, OperandSize.PD, dst, src, imm8);
    }

    public final void pcmpgtb(Register dst, Register src) {
        SSEOp.PCMPGTB.emit(this, OperandSize.PD, dst, src);
    }

    public final void pcmpgtd(Register dst, Register src) {
        SSEOp.PCMPGTD.emit(this, OperandSize.PD, dst, src);
    }

    public final void pextrb(AMD64Address dst, Register src, int imm8) {
        SSEMRIOp.PEXTRB.emit(this, OperandSize.PD, dst, src, imm8);
    }

    public final void pextrd(AMD64Address dst, Register src, int imm8) {
        SSEMRIOp.PEXTRD.emit(this, OperandSize.PD, dst, src, imm8);
    }

    public final void pextrq(Register dst, Register src, int imm8) {
        SSEMRIOp.PEXTRQ.emit(this, OperandSize.PD, dst, src, imm8);
    }

    public final void pextrq(AMD64Address dst, Register src, int imm8) {
        SSEMRIOp.PEXTRQ.emit(this, OperandSize.PD, dst, src, imm8);
    }

    public final void pextrw(AMD64Address dst, Register src, int imm8) {
        SSEMRIOp.PEXTRW.emit(this, OperandSize.PD, dst, src, imm8);
    }

    public final void pinsrb(Register dst, AMD64Address src, int imm8) {
        SSERMIOp.PINSRB.emit(this, OperandSize.PD, dst, src, imm8);
    }

    public final void pinsrd(Register dst, AMD64Address src, int imm8) {
        SSERMIOp.PINSRD.emit(this, OperandSize.PD, dst, src, imm8);
    }

    public final void pinsrq(Register dst, Register src, int imm8) {
        SSERMIOp.PINSRQ.emit(this, OperandSize.PD, dst, src, imm8);
    }

    public final void pinsrq(Register dst, AMD64Address src, int imm8) {
        SSERMIOp.PINSRQ.emit(this, OperandSize.PD, dst, src, imm8);
    }

    public final void pinsrw(Register dst, Register src, int imm8) {
        SSERMIOp.PINSRW.emit(this, OperandSize.PD, dst, src, imm8);
    }

    public final void pinsrw(Register dst, AMD64Address src, int imm8) {
        SSERMIOp.PINSRW.emit(this, OperandSize.PD, dst, src, imm8);
    }

    public final void pminub(Register dst, Register src) {
        SSEOp.PMINUB.emit(this, OperandSize.PD, dst, src);
    }

    public final void pminud(Register dst, Register src) {
        SSEOp.PMINUD.emit(this, OperandSize.PD, dst, src);
    }

    public final void pminuw(Register dst, Register src) {
        SSEOp.PMINUW.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovsxbd(Register dst, AMD64Address src) {
        SSEOp.PMOVSXBD.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovsxbd(Register dst, Register src) {
        SSEOp.PMOVSXBD.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovsxbq(Register dst, AMD64Address src) {
        SSEOp.PMOVSXBQ.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovsxbq(Register dst, Register src) {
        SSEOp.PMOVSXBQ.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovsxbw(Register dst, AMD64Address src) {
        SSEOp.PMOVSXBW.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovsxbw(Register dst, Register src) {
        SSEOp.PMOVSXBW.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovsxdq(Register dst, AMD64Address src) {
        SSEOp.PMOVSXDQ.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovsxdq(Register dst, Register src) {
        SSEOp.PMOVSXDQ.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovsxwd(Register dst, AMD64Address src) {
        SSEOp.PMOVSXWD.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovsxwd(Register dst, Register src) {
        SSEOp.PMOVSXWD.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovsxwq(Register dst, AMD64Address src) {
        SSEOp.PMOVSXWQ.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovsxwq(Register dst, Register src) {
        SSEOp.PMOVSXWQ.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovzxbd(Register dst, AMD64Address src) {
        SSEOp.PMOVZXBD.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovzxbd(Register dst, Register src) {
        SSEOp.PMOVZXBD.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovzxbq(Register dst, AMD64Address src) {
        SSEOp.PMOVZXBQ.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovzxbq(Register dst, Register src) {
        SSEOp.PMOVZXBQ.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovzxbw(Register dst, AMD64Address src) {
        SSEOp.PMOVZXBW.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovzxbw(Register dst, Register src) {
        SSEOp.PMOVZXBW.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovzxdq(Register dst, AMD64Address src) {
        SSEOp.PMOVZXDQ.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovzxdq(Register dst, Register src) {
        SSEOp.PMOVZXDQ.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovzxwd(Register dst, AMD64Address src) {
        SSEOp.PMOVZXWD.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovzxwd(Register dst, Register src) {
        SSEOp.PMOVZXWD.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovzxwq(Register dst, AMD64Address src) {
        SSEOp.PMOVZXWQ.emit(this, OperandSize.PD, dst, src);
    }

    public final void pmovzxwq(Register dst, Register src) {
        SSEOp.PMOVZXWQ.emit(this, OperandSize.PD, dst, src);
    }

    public final void popcntl(Register dst, Register src) {
        AMD64RMOp.POPCNT.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void por(Register dst, Register src) {
        SSEOp.POR.emit(this, OperandSize.PD, dst, src);
    }

    public final void pshufb(Register dst, Register src) {
        SSEOp.PSHUFB.emit(this, OperandSize.PD, dst, src);
    }

    public final void pshufb(Register dst, AMD64Address src) {
        SSEOp.PSHUFB.emit(this, OperandSize.PD, dst, src);
    }

    public final void pshufd(Register dst, Register src, int imm8) {
        GraalError.guarantee(isUByte(imm8), "invalid value");
        SSERMIOp.PSHUFD.emit(this, OperandSize.PD, dst, src, imm8);
    }

    public final void pshuflw(Register dst, Register src, int imm8) {
        GraalError.guarantee(isUByte(imm8), "invalid value");
        SSERMIOp.PSHUFLW.emit(this, OperandSize.SD, dst, src, imm8);
    }

    public final void pslld(Register dst, int imm8) {
        GraalError.guarantee(isUByte(imm8), "invalid value");
        SSERIOp.PSLLD.emit(this, OperandSize.PD, dst, imm8);
    }

    public final void pslldq(Register dst, int imm8) {
        GraalError.guarantee(isUByte(imm8), "invalid value");
        SSERIOp.PSLLDQ.emit(this, OperandSize.PD, dst, imm8);
    }

    public final void psllq(Register dst, Register shift) {
        SSEOp.PSLLQ.emit(this, OperandSize.PD, dst, shift);
    }

    public final void psllq(Register dst, int imm8) {
        GraalError.guarantee(isUByte(imm8), "invalid value");
        SSERIOp.PSLLQ.emit(this, OperandSize.PD, dst, imm8);
    }

    public final void psllw(Register dst, int imm8) {
        GraalError.guarantee(isUByte(imm8), "invalid value");
        SSERIOp.PSLLW.emit(this, OperandSize.PD, dst, imm8);
    }

    public final void psrad(Register dst, int imm8) {
        GraalError.guarantee(isUByte(imm8), "invalid value");
        SSERIOp.PSRAD.emit(this, OperandSize.PD, dst, imm8);
    }

    public final void psrld(Register dst, int imm8) {
        GraalError.guarantee(isUByte(imm8), "invalid value");
        SSERIOp.PSRLD.emit(this, OperandSize.PD, dst, imm8);
    }

    public final void psrldq(Register dst, int imm8) {
        GraalError.guarantee(isUByte(imm8), "invalid value");
        SSERIOp.PSRLDQ.emit(this, OperandSize.PD, dst, imm8);
    }

    public final void psrlq(Register dst, int imm8) {
        GraalError.guarantee(isUByte(imm8), "invalid value");
        SSERIOp.PSRLQ.emit(this, OperandSize.PD, dst, imm8);
    }

    public final void psrlw(Register dst, int imm8) {
        GraalError.guarantee(isUByte(imm8), "invalid value");
        SSERIOp.PSRLW.emit(this, OperandSize.PD, dst, imm8);
    }

    public final void psubd(Register dst, Register src) {
        SSEOp.PSUBD.emit(this, OperandSize.PD, dst, src);
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

    public final void psubw(Register dst, Register src) {
        SSEOp.PSUBW.emit(this, OperandSize.PD, dst, src);
    }

    public final void ptest(Register dst, Register src) {
        SSEOp.PTEST.emit(this, OperandSize.PD, dst, src);
    }

    public final void ptest(Register dst, AMD64Address src) {
        SSEOp.PTEST.emit(this, OperandSize.PD, dst, src);
    }

    public final void punpcklbw(Register dst, Register src) {
        SSEOp.PUNPCKLBW.emit(this, OperandSize.PD, dst, src);
    }

    public final void pxor(Register dst, Register src) {
        SSEOp.PXOR.emit(this, OperandSize.PD, dst, src);
    }

    public final void rclq(Register dst, int imm8) {
        GraalError.guarantee(isByte(imm8), "only byte immediate is supported");
        AMD64Shift.RCL.miOp.emit(this, OperandSize.QWORD, dst, (byte) imm8);
    }

    public final void rcpps(Register dst, Register src) {
        SSEOp.RCPPS.emit(this, OperandSize.PS, dst, src);
    }

    public final void rcrq(Register dst, int imm8) {
        GraalError.guarantee(isByte(imm8), "only byte immediate is supported");
        AMD64Shift.RCR.miOp.emit(this, OperandSize.QWORD, dst, (byte) imm8);
    }

    public final void roll(Register dst, int imm8) {
        GraalError.guarantee(isByte(imm8), "only byte immediate is supported");
        AMD64Shift.ROL.miOp.emit(this, OperandSize.DWORD, dst, (byte) imm8);
    }

    public final void rorl(Register dst, int imm8) {
        GraalError.guarantee(isByte(imm8), "only byte immediate is supported");
        AMD64Shift.ROR.miOp.emit(this, OperandSize.DWORD, dst, (byte) imm8);
    }

    public final void rorq(Register dst, int imm8) {
        GraalError.guarantee(isByte(imm8), "only byte immediate is supported");
        AMD64Shift.ROR.miOp.emit(this, OperandSize.QWORD, dst, (byte) imm8);
    }

    public final void rorxl(Register dst, Register src, int imm8) {
        VexRMIOp.RORXL.emit(this, AVXSize.XMM, dst, src, (byte) imm8);
    }

    public final void rorxq(Register dst, Register src, int imm8) {
        VexRMIOp.RORXQ.emit(this, AVXSize.XMM, dst, src, (byte) imm8);
    }

    public final void sarl(Register dst) {
        // Signed divide dst by 2, CL times.
        AMD64MOp.SAR.emit(this, OperandSize.DWORD, dst);
    }

    public final void sarl(Register dst, int imm8) {
        GraalError.guarantee(isShiftCount(imm8 >> 1), "illegal shift count");
        if (imm8 == 1) {
            AMD64MOp.SAR1.emit(this, OperandSize.DWORD, dst);
        } else {
            AMD64MIOp.SAR.emit(this, OperandSize.DWORD, dst, imm8);
        }
    }

    public final void sarq(Register dst) {
        // signed divide dst by 2, CL times.
        AMD64MOp.SAR.emit(this, OperandSize.QWORD, dst);
    }

    public final void sarq(Register dst, int imm8) {
        GraalError.guarantee(isShiftCount(imm8 >> 1), "illegal shift count");
        if (imm8 == 1) {
            AMD64MOp.SAR1.emit(this, OperandSize.QWORD, dst);
        } else {
            AMD64MIOp.SAR.emit(this, OperandSize.QWORD, dst, imm8);
        }
    }

    public final void sbbq(Register dst, Register src) {
        AMD64BinaryArithmetic.SBB.rmOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void sha1msg1(Register dst, Register src) {
        AMD64RMOp.SHA1MSG1.emit(this, OperandSize.PS, dst, src);
    }

    public final void sha1msg2(Register dst, Register src) {
        AMD64RMOp.SHA1MSG2.emit(this, OperandSize.PS, dst, src);
    }

    public final void sha1nexte(Register dst, Register src) {
        AMD64RMOp.SHA1NEXTE.emit(this, OperandSize.PS, dst, src);
    }

    public final void sha1rnds4(Register dst, Register src, int imm8) {
        AMD64RMIOp.SHA1RNDS4.emit(this, OperandSize.PS, dst, src, imm8);
    }

    public final void sha256msg1(Register dst, Register src) {
        AMD64RMOp.SHA256MSG1.emit(this, OperandSize.PS, dst, src);
    }

    public final void sha256msg2(Register dst, Register src) {
        AMD64RMOp.SHA256MSG2.emit(this, OperandSize.PS, dst, src);
    }

    public final void sha256rnds2(Register dst, Register src) {
        AMD64RMOp.SHA256RNDS2.emit(this, OperandSize.PS, dst, src);
    }

    public final void shll(Register dst, int imm8) {
        GraalError.guarantee(isShiftCount(imm8 >> 1), "illegal shift count");
        if (imm8 == 1) {
            AMD64MOp.SHL1.emit(this, OperandSize.DWORD, dst);
        } else {
            AMD64MIOp.SHL.emit(this, OperandSize.DWORD, dst, imm8);
        }
    }

    public final void shll(Register dst) {
        // Multiply dst by 2, CL times.
        AMD64MOp.SHL.emit(this, OperandSize.DWORD, dst);
    }

    public final void shlq(Register dst, int imm8) {
        GraalError.guarantee(isShiftCount(imm8 >> 1), "illegal shift count");
        if (imm8 == 1) {
            AMD64MOp.SHL1.emit(this, OperandSize.QWORD, dst);
        } else {
            AMD64MIOp.SHL.emit(this, OperandSize.QWORD, dst, imm8);
        }
    }

    public final void shlq(Register dst) {
        // Multiply dst by 2, CL times.
        AMD64MOp.SHL.emit(this, OperandSize.QWORD, dst);
    }

    // Insn: SHLX r32a, r/m32, r32b
    public final void shlxl(Register dst, Register src1, Register src2) {
        VexGeneralPurposeRMVOp.SHLX.emit(this, AVXSize.DWORD, dst, src1, src2);
    }

    public final void shrl(Register dst) {
        // Unsigned divide dst by 2, CL times.
        AMD64MOp.SHR.emit(this, OperandSize.DWORD, dst);
    }

    public final void shrl(Register dst, int imm8) {
        GraalError.guarantee(isShiftCount(imm8 >> 1), "illegal shift count");
        AMD64MIOp.SHR.emit(this, OperandSize.DWORD, dst, imm8);
    }

    public final void shrq(Register dst, int imm8) {
        GraalError.guarantee(isShiftCount(imm8 >> 1), "illegal shift count");
        if (imm8 == 1) {
            AMD64MOp.SHR1.emit(this, OperandSize.QWORD, dst);
        } else {
            AMD64MIOp.SHR.emit(this, OperandSize.QWORD, dst, imm8);
        }
    }

    public final void shrq(Register dst) {
        // Unsigned divide dst by 2, CL times.
        AMD64MOp.SHR.emit(this, OperandSize.QWORD, dst);
    }

    public final void shufpd(Register dst, Register src, int imm8) {
        SSERMIOp.SHUFPD.emit(this, OperandSize.PD, dst, src, imm8);
    }

    public final void sqrtsd(Register dst, Register src) {
        SSEOp.SQRT.emit(this, OperandSize.SD, dst, src);
    }

    public final void subl(AMD64Address dst, int imm32) {
        AMD64BinaryArithmetic.SUB.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void subl(Register dst, int imm32) {
        AMD64BinaryArithmetic.SUB.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void subl(Register dst, Register src) {
        AMD64BinaryArithmetic.SUB.rmOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void subpd(Register dst, Register src) {
        SSEOp.SUB.emit(this, OperandSize.PD, dst, src);
    }

    public final void subq(Register dst, int imm32) {
        AMD64BinaryArithmetic.SUB.getMIOpcode(OperandSize.QWORD, isByte(imm32)).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void subq(AMD64Address dst, int imm32) {
        AMD64BinaryArithmetic.SUB.getMIOpcode(OperandSize.QWORD, isByte(imm32)).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void subq(Register dst, Register src) {
        AMD64BinaryArithmetic.SUB.rmOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void subqWide(Register dst, int imm32) {
        // don't use the sign-extending version, forcing a 32-bit immediate
        AMD64BinaryArithmetic.SUB.getMIOpcode(OperandSize.QWORD, false).emit(this, OperandSize.QWORD, dst, imm32);
    }

    public final void subsd(Register dst, Register src) {
        SSEOp.SUB.emit(this, OperandSize.SD, dst, src);
    }

    public final void subsd(Register dst, AMD64Address src) {
        SSEOp.SUB.emit(this, OperandSize.SD, dst, src);
    }

    public final void subss(Register dst, Register src) {
        SSEOp.SUB.emit(this, OperandSize.SS, dst, src);
    }

    public final void subss(Register dst, AMD64Address src) {
        SSEOp.SUB.emit(this, OperandSize.SS, dst, src);
    }

    public final void testl(Register dst, Register src) {
        AMD64RMOp.TEST.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void testl(Register dst, AMD64Address src) {
        AMD64RMOp.TEST.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void testl(AMD64Address dst, int imm32) {
        AMD64MIOp.TEST.emit(this, OperandSize.DWORD, dst, imm32);
    }

    public final void testq(Register dst, Register src) {
        AMD64RMOp.TEST.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void testq(Register dst, AMD64Address src) {
        AMD64RMOp.TEST.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void ucomisd(Register dst, Register src) {
        SSEOp.UCOMIS.emit(this, OperandSize.PD, dst, src);
    }

    public final void ucomisd(Register dst, AMD64Address src) {
        SSEOp.UCOMIS.emit(this, OperandSize.PD, dst, src);
    }

    public final void ucomiss(Register dst, Register src) {
        SSEOp.UCOMIS.emit(this, OperandSize.PS, dst, src);
    }

    public final void unpckhpd(Register dst, Register src) {
        SSEOp.UNPCKHPD.emit(this, OperandSize.PD, dst, src);
    }

    public final void unpcklpd(Register dst, Register src) {
        SSEOp.UNPCKLPD.emit(this, OperandSize.PD, dst, src);
    }

    public final void vcvtph2ps(Register dst, Register src) {
        VexRMOp.VCVTPH2PS.emit(this, AVXSize.XMM, dst, src);
    }

    public final void vcvtps2ph(Register dst, Register src, int imm8) {
        VexMRIOp.VCVTPS2PH.emit(this, AVXSize.XMM, dst, src, imm8);
    }

    public final void vmovdqu(Register dst, AMD64Address src) {
        VexMoveOp.VMOVDQU32.emit(this, AVXSize.YMM, dst, src);
    }

    public final void vmovdqu(Register dst, Register src) {
        VexMoveOp.VMOVDQU32.emit(this, AVXSize.YMM, dst, src);
    }

    public final void vmovdqu(AMD64Address dst, Register src) {
        VexMoveOp.VMOVDQU32.emit(this, AVXSize.YMM, dst, src);
    }

    public final void vmovdqu64(Register dst, AMD64Address src) {
        VexMoveOp.VMOVDQU64.emit(this, AVXSize.ZMM, dst, src);
    }

    public final void vmovdqu64(AMD64Address dst, Register src) {
        VexMoveOp.VMOVDQU64.emit(this, AVXSize.ZMM, dst, src);
    }

    public final void vpaddd(Register dst, Register nds, Register src, AVXSize size) {
        VexRVMOp.VPADDD.emit(this, size, dst, nds, src);
    }

    public final void vpaddd(Register dst, Register nds, AMD64Address src, AVXSize size) {
        VexRVMOp.VPADDD.emit(this, size, dst, nds, src);
    }

    public final void vpaddq(Register dst, Register nds, Register src, AVXSize size) {
        VexRVMOp.VPADDQ.emit(this, size, dst, nds, src);
    }

    public final void vpaddq(Register dst, Register nds, AMD64Address src, AVXSize size) {
        VexRVMOp.VPADDQ.emit(this, size, dst, nds, src);
    }

    public final void vpalignr(Register dst, Register nds, Register src, int imm8, AVXSize size) {
        VexRVMIOp.VPALIGNR.emit(this, size, dst, nds, src, imm8);
    }

    public final void vpand(Register dst, Register nds, Register src, AVXSize size) {
        VexRVMOp.VPAND.emit(this, size, dst, nds, src);
    }

    public final void vpandn(Register dst, Register nds, Register src) {
        VexRVMOp.VPANDN.emit(this, AVXSize.YMM, dst, nds, src);
    }

    public final void vpblendd(Register dst, Register nds, Register src, int imm8, AVXSize size) {
        VexRVMIOp.VPBLENDD.emit(this, size, dst, nds, src, imm8);
    }

    public final void vpclmulhqhqdq(Register dst, Register nds, Register src) {
        VexRVMIOp.VPCLMULQDQ.emit(this, AVXSize.XMM, dst, nds, src, 0x11);
    }

    public final void vpclmulhqlqdq(Register dst, Register nds, Register src) {
        VexRVMIOp.VPCLMULQDQ.emit(this, AVXSize.XMM, dst, nds, src, 0x01);
    }

    public final void vpclmullqhqdq(Register dst, Register nds, Register src) {
        VexRVMIOp.VPCLMULQDQ.emit(this, AVXSize.XMM, dst, nds, src, 0x10);
    }

    public final void vpclmullqlqdq(Register dst, Register nds, Register src) {
        VexRVMIOp.VPCLMULQDQ.emit(this, AVXSize.XMM, dst, nds, src, 0x00);
    }

    public final void vpclmulqdq(Register dst, Register nds, Register src, int imm8) {
        VexRVMIOp.VPCLMULQDQ.emit(this, AVXSize.XMM, dst, nds, src, imm8);
    }

    public final void vpcmpeqb(Register dst, Register src1, Register src2) {
        VexRVMOp.VPCMPEQB.emit(this, AVXSize.YMM, dst, src1, src2);
    }

    public final void vpcmpeqd(Register dst, Register src1, Register src2) {
        VexRVMOp.VPCMPEQD.emit(this, AVXSize.YMM, dst, src1, src2);
    }

    public final void vpcmpeqw(Register dst, Register src1, Register src2) {
        VexRVMOp.VPCMPEQW.emit(this, AVXSize.YMM, dst, src1, src2);
    }

    public final void vperm2f128(Register dst, Register nds, Register src, int imm8) {
        VexRVMIOp.VPERM2F128.emit(this, AVXSize.YMM, dst, nds, src, imm8);
    }

    public final void vperm2i128(Register dst, Register nds, Register src, int imm8) {
        VexRVMIOp.VPERM2I128.emit(this, AVXSize.YMM, dst, nds, src, imm8);
    }

    public final void vpmovmskb(Register dst, Register src) {
        VexRMOp.VPMOVMSKB.emit(this, AVXSize.YMM, dst, src);
    }

    public final void vpmovzxbw(Register dst, AMD64Address src) {
        VexRMOp.VPMOVZXBW.emit(this, AVXSize.YMM, dst, src);
    }

    public final void vpor(Register dst, Register nds, Register src, AVXSize size) {
        VexRVMOp.VPOR.emit(this, size, dst, nds, src);
    }

    public final void vpshufb(Register dst, Register src1, Register src2, AVXSize size) {
        VexRVMOp.VPSHUFB.emit(this, size, dst, src1, src2);
    }

    public final void vpshufd(Register dst, Register src, int imm8, AVXSize size) {
        VexRMIOp.VPSHUFD.emit(this, size, dst, src, imm8);
    }

    public final void vpslld(Register dst, Register src, int imm8, AVXSize size) {
        VexShiftOp.VPSLLD.emit(this, size, dst, src, imm8);
    }

    public final void vpslldq(Register dst, Register src, int imm8, AVXSize size) {
        VexShiftImmOp.VPSLLDQ.emit(this, size, dst, src, imm8);
    }

    public final void vpsllq(Register dst, Register src, int imm8, AVXSize size) {
        VexShiftOp.VPSLLQ.emit(this, size, dst, src, imm8);
    }

    public final void vpsllw(Register dst, Register src, int imm8) {
        VexShiftOp.VPSLLW.emit(this, AVXSize.YMM, dst, src, imm8);
    }

    public final void vpsrld(Register dst, Register src, int imm8, AVXSize size) {
        VexShiftOp.VPSRLD.emit(this, size, dst, src, imm8);
    }

    public final void vpsrldq(Register dst, Register src, int imm8, AVXSize size) {
        VexShiftImmOp.VPSRLDQ.emit(this, size, dst, src, imm8);
    }

    public final void vpsrlq(Register dst, Register src, int imm8, AVXSize size) {
        VexShiftOp.VPSRLQ.emit(this, size, dst, src, imm8);
    }

    public final void vpsrlw(Register dst, Register src, int imm8) {
        VexShiftOp.VPSRLW.emit(this, AVXSize.YMM, dst, src, imm8);
    }

    public final void vptest(Register dst, Register src, AVXSize size) {
        VexRMOp.VPTEST.emit(this, size, dst, src);
    }

    public final void vpxor(Register dst, Register nds, Register src, AVXSize size) {
        VexRVMOp.VPXOR.emit(this, size, dst, nds, src);
    }

    public final void vpxor(Register dst, Register nds, AMD64Address src, AVXSize size) {
        VexRVMOp.VPXOR.emit(this, size, dst, nds, src);
    }

    public final void xaddb(AMD64Address dst, Register src) {
        AMD64MROp.XADDB.emit(this, OperandSize.BYTE, dst, src);
    }

    public final void xaddl(AMD64Address dst, Register src) {
        AMD64MROp.XADD.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void xaddq(AMD64Address dst, Register src) {
        AMD64MROp.XADD.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void xaddw(AMD64Address dst, Register src) {
        AMD64MROp.XADD.emit(this, OperandSize.WORD, dst, src);
    }

    public final void xchgb(Register dst, AMD64Address src) {
        AMD64RMOp.XCHGB.emit(this, OperandSize.BYTE, dst, src);
    }

    public final void xchgl(Register dst, AMD64Address src) {
        AMD64RMOp.XCHG.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void xchgq(Register dst, AMD64Address src) {
        AMD64RMOp.XCHG.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void xchgw(Register dst, AMD64Address src) {
        AMD64RMOp.XCHG.emit(this, OperandSize.WORD, dst, src);
    }

    public final void xorb(Register dst, AMD64Address src) {
        AMD64BinaryArithmetic.XOR.byteRmOp.emit(this, OperandSize.BYTE, dst, src);
    }

    public final void xorl(Register dst, Register src) {
        AMD64BinaryArithmetic.XOR.rmOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void xorl(Register dst, AMD64Address src) {
        AMD64BinaryArithmetic.XOR.rmOp.emit(this, OperandSize.DWORD, dst, src);
    }

    public final void xorl(Register dst, int imm32) {
        AMD64BinaryArithmetic.XOR.getMIOpcode(OperandSize.DWORD, isByte(imm32)).emit(this, OperandSize.DWORD, dst, imm32);
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

    public final void xorq(Register dst, Register src) {
        AMD64BinaryArithmetic.XOR.rmOp.emit(this, OperandSize.QWORD, dst, src);
    }

    public final void xorq(Register dst, AMD64Address src) {
        AMD64BinaryArithmetic.XOR.rmOp.emit(this, OperandSize.QWORD, dst, src);
    }

    // Mask register related instructions

    public final void kmovb(Register dst, Register src) {
        VexMoveMaskOp.KMOVB.emit(this, AVXSize.XMM, dst, src);
    }

    public final void kmovb(AMD64Address dst, Register src) {
        VexMoveMaskOp.KMOVB.emit(this, AVXSize.XMM, dst, src);
    }

    public final void kmovb(Register dst, AMD64Address src) {
        VexMoveMaskOp.KMOVB.emit(this, AVXSize.XMM, dst, src);
    }

    public final void kmovd(Register dst, Register src) {
        VexMoveMaskOp.KMOVD.emit(this, AVXSize.XMM, dst, src);
    }

    public final void kmovd(AMD64Address dst, Register src) {
        VexMoveMaskOp.KMOVD.emit(this, AVXSize.XMM, dst, src);
    }

    public final void kmovd(Register dst, AMD64Address src) {
        VexMoveMaskOp.KMOVD.emit(this, AVXSize.XMM, dst, src);
    }

    public final void kmovq(Register dst, Register src) {
        VexMoveMaskOp.KMOVQ.emit(this, AVXSize.XMM, dst, src);
    }

    public final void kmovq(AMD64Address dst, Register src) {
        VexMoveMaskOp.KMOVQ.emit(this, AVXSize.XMM, dst, src);
    }

    public final void kmovq(Register dst, AMD64Address src) {
        VexMoveMaskOp.KMOVQ.emit(this, AVXSize.XMM, dst, src);
    }

    public final void kmovw(Register dst, Register src) {
        VexMoveMaskOp.KMOVW.emit(this, AVXSize.XMM, dst, src);
    }

    public final void kmovw(AMD64Address dst, Register src) {
        VexMoveMaskOp.KMOVW.emit(this, AVXSize.XMM, dst, src);
    }

    public final void kmovw(Register dst, AMD64Address src) {
        VexMoveMaskOp.KMOVW.emit(this, AVXSize.XMM, dst, src);
    }

    // This instruction produces ZF or CF flags
    public final void kortestd(Register src1, Register src2) {
        VexRROp.KORTESTD.emit(this, AVXSize.XMM, src1, src2);
    }

    // This instruction produces ZF or CF flags
    public final void kortestq(Register src1, Register src2) {
        VexRROp.KORTESTQ.emit(this, AVXSize.XMM, src1, src2);
    }

    public final void kshiftrw(Register dst, Register src, int imm8) {
        VexMaskRRIOp.KSHIFTRW.emit(this, AVXSize.XMM, dst, src, imm8);
    }

    public final void ktestd(Register src1, Register src2) {
        VexRROp.KTESTD.emit(this, AVXSize.XMM, src1, src2);
    }

    public final void ktestq(Register src1, Register src2) {
        VexRROp.KTESTQ.emit(this, AVXSize.XMM, src1, src2);
    }

    // AVX512 instructions

    // Insn: VMOVDQU16 zmm1 {k1}{z}, zmm2/m512
    // -----
    // Insn: VMOVDQU16 zmm1, m512
    public final void evmovdqu16(Register dst, AMD64Address src) {
        VexMoveOp.EVMOVDQU16.emit(this, AVXSize.ZMM, dst, src);
    }

    public final void evmovdqu16(Register dst, Register src) {
        VexMoveOp.EVMOVDQU16.emit(this, AVXSize.ZMM, dst, src);
    }

    // Insn: VMOVDQU16 zmm1, k1:z, m512
    public final void evmovdqu16(Register dst, Register mask, AMD64Address src) {
        VexMoveOp.EVMOVDQU16.emit(this, AVXSize.ZMM, dst, src, mask, Z1, B0);
    }

    // Insn: VMOVDQU16 zmm2/m512 {k1}{z}, zmm1
    // -----
    // Insn: VMOVDQU16 m512, zmm1
    public final void evmovdqu16(AMD64Address dst, Register src) {
        VexMoveOp.EVMOVDQU16.emit(this, AVXSize.ZMM, dst, src);
    }

    // Insn: VMOVDQU16 m512, k1, zmm1
    public final void evmovdqu16(AMD64Address dst, Register mask, Register src) {
        VexMoveOp.EVMOVDQU16.emit(this, AVXSize.ZMM, dst, src, mask);
    }

    public final void evmovdqu64(Register dst, AMD64Address src) {
        VexMoveOp.EVMOVDQU64.emit(this, AVXSize.ZMM, dst, src);
    }

    public final void evmovdqu64(Register dst, Register mask, AMD64Address src) {
        VexMoveOp.EVMOVDQU64.emit(this, AVXSize.ZMM, dst, src, mask, Z1, B0);
    }

    public final void evmovdqu64(AMD64Address dst, Register src) {
        VexMoveOp.EVMOVDQU64.emit(this, AVXSize.ZMM, dst, src);
    }

    public final void evmovdqu64(AMD64Address dst, Register mask, Register src) {
        VexMoveOp.EVMOVDQU64.emit(this, AVXSize.ZMM, dst, src, mask);
    }

    // Insn: VPBROADCASTW zmm1 {k1}{z}, reg
    // -----
    // Insn: VPBROADCASTW zmm1, reg
    public final void evpbroadcastw(Register dst, Register src) {
        VexRROp.EVPBROADCASTW_GPR.emit(this, AVXSize.ZMM, dst, src);
    }

    public final void evpcmpeqb(Register kdst, Register nds, AMD64Address src) {
        VexRVMOp.EVPCMPEQB.emit(this, AVXSize.ZMM, kdst, nds, src);
    }

    // Insn: VPCMPQTB k1 {k2}, zmm2, zmm3/m512
    // -----
    // Insn: VPCMPQTB k1, zmm2, m512
    public final void evpcmpgtb(Register kdst, Register nds, AMD64Address src) {
        VexRVMOp.EVPCMPGTB.emit(this, AVXSize.ZMM, kdst, nds, src);
    }

    // Insn: VPCMPQTB k1 {k2}, zmm2, zmm3/m512
    // -----
    // Insn: VPCMPQTB k1, k2, zmm2, m512
    public final void evpcmpgtb(Register kdst, Register mask, Register nds, AMD64Address src) {
        VexRVMOp.EVPCMPGTB.emit(this, AVXSize.ZMM, kdst, nds, src, mask);
    }

    // Insn: VPCMPUW k1 {k2}, zmm2, zmm3/m512, imm8
    // -----
    // Insn: VPCMPUW k1, zmm2, zmm3, imm8
    public final void evpcmpuw(Register kdst, Register nds, Register src, int vcc) {
        VexRVMIOp.EVPCMPUW.emit(this, AVXSize.ZMM, kdst, nds, src, vcc);
    }

    // Insn: VPCMPUW k1 {k2}, zmm2, zmm3/m512, imm8
    // -----
    // Insn: VPCMPUW k1, k2, zmm2, zmm3, imm8
    public final void evpcmpuw(Register kdst, Register mask, Register nds, Register src, int vcc) {
        VexRVMIOp.EVPCMPUW.emit(this, AVXSize.ZMM, kdst, nds, src, mask, vcc);
    }

    public final void evpermt2q(Register dst, Register src1, Register src2) {
        VexRVMOp.EVPERMT2Q.emit(this, AVXSize.ZMM, dst, src1, src2);
    }

    // Insn: VPMOVWB ymm1/m256 {k1}{z}, zmm2
    // -----
    // Insn: VPMOVWB m256, zmm2
    public final void evpmovwb(AMD64Address dst, Register src) {
        VexMROp.EVPMOVWB.emit(this, AVXSize.ZMM, dst, src);
    }

    // Insn: VPMOVWB m256, k1, zmm2
    public final void evpmovwb(AMD64Address dst, Register mask, Register src) {
        VexMROp.EVPMOVWB.emit(this, AVXSize.ZMM, dst, src, mask, Z0, B0);
    }

    public final void evpmovzxbw(Register dst, AMD64Address src) {
        VexMoveOp.EVPMOVZXBW.emit(this, AVXSize.ZMM, dst, src);
    }

    // Insn: VPMOVZXBW zmm1 {k1}{z}, ymm2/m256
    // -----
    // Insn: VPMOVZXBW zmm1, k1, m256
    public final void evpmovzxbw(Register dst, Register mask, AMD64Address src) {
        VexMoveOp.EVPMOVZXBW.emit(this, AVXSize.ZMM, dst, src, mask, Z0, B0);
    }

    public final void evprolq(Register dst, Register src, int imm8) {
        EvexRMIExtendOp.EVPROLQ.emit(this, AVXSize.ZMM, dst, src, imm8);
    }

    public final void evprolvq(Register dst, Register src1, Register src2) {
        VexRVMOp.EVPROLVQ.emit(this, AVXSize.ZMM, dst, src1, src2);
    }

    public final void evpternlogq(Register dst, int imm8, Register src1, Register src2) {
        VexRVMIOp.EVPTERNLOGQ.emit(this, AVXSize.ZMM, dst, src1, src2, imm8);
    }

    public final void evpxorq(Register dst, Register mask, Register nds, AMD64Address src) {
        VexRVMOp.EVPXORQ.emit(this, AVXSize.ZMM, dst, nds, src, mask);
    }

}
