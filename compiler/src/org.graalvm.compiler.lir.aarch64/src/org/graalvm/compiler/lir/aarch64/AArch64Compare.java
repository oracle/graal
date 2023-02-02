/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.aarch64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;

import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerator;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;

public class AArch64Compare {

    public static class CompareOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<CompareOp> TYPE = LIRInstructionClass.create(CompareOp.class);

        @Use protected Value x;
        @Use({REG, CONST}) protected Value y;

        public CompareOp(Value x, Value y) {
            super(TYPE);
            assert x.getPlatformKind() == y.getPlatformKind() : x.getPlatformKind() + " " + y.getPlatformKind();
            assert !isJavaConstant(y) || isCompareConstant(y);
            this.x = x;
            this.y = y;
        }

        /**
         * Checks if value can be used as a constant or not.
         */
        public static boolean isCompareConstant(Value value) {
            if (isJavaConstant(value)) {
                JavaConstant constant = asJavaConstant(value);
                if (constant instanceof PrimitiveConstant) {
                    return AArch64MacroAssembler.isComparisonImmediate(constant.asLong());
                } else {
                    return constant.isDefaultForKind();
                }
            }
            return false;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            final int size = x.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            if (isRegister(y)) {
                masm.cmp(size, asRegister(x), asRegister(y));
            } else {
                JavaConstant constant = asJavaConstant(y);
                if (constant.isDefaultForKind()) {
                    masm.compare(size, asRegister(x), 0);
                } else {
                    JavaKind javaKind = constant.getJavaKind();
                    GraalError.guarantee(javaKind == JavaKind.Int || javaKind == JavaKind.Long, "Unexpected constant size.");
                    masm.compare(size, asRegister(x), NumUtil.safeToInt(constant.asLong()));
                }
            }
        }
    }

    public static class FloatCompareOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<FloatCompareOp> TYPE = LIRInstructionClass.create(FloatCompareOp.class);

        @Use protected Value x;
        @Use({REG, CONST}) protected Value y;
        private final Condition condition;
        private final boolean unorderedIsTrue;

        public FloatCompareOp(Value x, Value y, Condition condition, boolean unorderedIsTrue) {
            super(TYPE);
            assert !isJavaConstant(y) || isCompareConstant(y, condition, unorderedIsTrue);
            this.x = x;
            this.y = y;
            this.condition = condition;
            this.unorderedIsTrue = unorderedIsTrue;
        }

        /**
         * There is no condition code for "EQ || unordered" or "NE && !unordered", so we have to fix
         * them up ourselves.
         */
        private static boolean needsUnorderedFixup(Condition condition, boolean unorderedIsTrue) {
            return (condition == Condition.EQ && unorderedIsTrue) || (condition == Condition.NE && !unorderedIsTrue);
        }

        /**
         * Checks if value can be used as a constant or not.
         */
        public static boolean isCompareConstant(Value value, Condition condition, boolean unorderedIsTrue) {
            /*
             * If fixup is needed then all values must be in registers.
             */
            if (needsUnorderedFixup(condition, unorderedIsTrue)) {
                return false;
            }
            return isJavaConstant(value) && asJavaConstant(value).isDefaultForKind();
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            assert isRegister(x);
            Register left = asRegister(x);
            int size = x.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            boolean fixupUnorderedState = needsUnorderedFixup(condition, unorderedIsTrue);
            if (isRegister(y)) {
                masm.fcmp(size, left, asRegister(y));
                /*
                 * There is no condition code for "condition == EQ && unordered" or
                 * "condition == NE && !unordered", so we have to fix them up ourselves.
                 *
                 * We do this by conditionally changing the condition bits (via fccmp) to be set to
                 * EQ (Z==1) when the operands are unordered. In code, the operation is:
                 *
                 * result = ordered(f1, f2) ? cmp(f1, f2) : EQ
                 *
                 * When a value is unordered, setting the result to EQ satisfies
                 * "condition == EQ && unordered" since EQ == EQ, and also
                 * "condition == NE && !unordered", since EQ != NE
                 */
                if (fixupUnorderedState) {
                    int nzcv = 0b0100;   // EQUAL -> Z = 1
                    masm.fccmp(size, left, asRegister(y), nzcv, AArch64Assembler.ConditionFlag.VC);
                }
            } else {
                assert !fixupUnorderedState;
                // cmp against +0.0
                masm.fcmpZero(size, left);
            }
        }

        @Override
        public void verify() {
            assert x.getPlatformKind().equals(y.getPlatformKind()) : "a: " + x + " b: " + y;
        }
    }

    public static class ASIMDCompareOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<ASIMDCompareOp> TYPE = LIRInstructionClass.create(ASIMDCompareOp.class);

        @Opcode private final Condition condition;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        @Use({REG}) protected AllocatableValue y;

        public ASIMDCompareOp(Condition condition, AllocatableValue result, AllocatableValue x, AllocatableValue y) {
            super(TYPE);
            assert x.getPlatformKind() == y.getPlatformKind() : x.getPlatformKind() + " " + y.getPlatformKind();
            this.condition = condition;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            ASIMDSize size = ASIMDSize.fromVectorKind(result.getPlatformKind());
            ElementSize eSize = ElementSize.fromKind(result.getPlatformKind());

            Register dst = asRegister(result);
            Register left = asRegister(x);
            Register right = asRegister(y);
            switch (condition) {
                case EQ:
                    masm.neon.cmeqVVV(size, eSize, dst, left, right);
                    break;
                case NE:
                    masm.neon.cmeqVVV(size, eSize, dst, left, right);
                    masm.neon.mvnVV(size, dst, dst);
                    break;
                case LT:
                    masm.neon.cmgtVVV(size, eSize, dst, right, left);
                    break;
                case LE:
                    masm.neon.cmgeVVV(size, eSize, dst, right, left);
                    break;
                case GT:
                    masm.neon.cmgtVVV(size, eSize, dst, left, right);
                    break;
                case GE:
                    masm.neon.cmgeVVV(size, eSize, dst, left, right);
                    break;
                case AE:
                    masm.neon.cmhsVVV(size, eSize, dst, left, right);
                    break;
                case BE:
                    masm.neon.cmhsVVV(size, eSize, dst, right, left);
                    break;
                case AT:
                    masm.neon.cmhiVVV(size, eSize, dst, left, right);
                    break;
                case BT:
                    masm.neon.cmhiVVV(size, eSize, dst, right, left);
                    break;
                default:
                    throw GraalError.unimplemented();
            }
        }
    }

    public static class ASIMDCompareZeroOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<ASIMDCompareZeroOp> TYPE = LIRInstructionClass.create(ASIMDCompareZeroOp.class);

        @Opcode private final Condition condition;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;

        public ASIMDCompareZeroOp(Condition condition, AllocatableValue result, AllocatableValue x) {
            super(TYPE);
            this.condition = condition;
            this.result = result;
            this.x = x;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            ASIMDSize size = ASIMDSize.fromVectorKind(result.getPlatformKind());
            ElementSize eSize = ElementSize.fromKind(result.getPlatformKind());

            Register dst = asRegister(result);
            Register src = asRegister(x);
            switch (condition) {
                case EQ:
                    masm.neon.cmeqZeroVV(size, eSize, dst, src);
                    break;
                case NE:
                    masm.neon.cmeqZeroVV(size, eSize, dst, src);
                    masm.neon.mvnVV(size, dst, dst);
                    break;
                case LT:
                    masm.neon.cmltZeroVV(size, eSize, dst, src);
                    break;
                case LE:
                    masm.neon.cmleZeroVV(size, eSize, dst, src);
                    break;
                case GT:
                    masm.neon.cmgtZeroVV(size, eSize, dst, src);
                    break;
                case GE:
                    masm.neon.cmgeZeroVV(size, eSize, dst, src);
                    break;
                default:
                    throw GraalError.unimplemented();
            }
        }
    }

    /**
     * By default, AArch64 outputs false whenever it encounters an unordered operation. To set
     * unordered to be true the negated condition must be tested and then the result should be
     * negated.
     */
    private static Condition getASIMDFloatCompareCondition(Condition condition, boolean unorderedIsTrue) {
        return unorderedIsTrue ? condition.negate() : condition;
    }

    public static AArch64LIRInstruction generateASIMDFloatCompare(LIRGenerator gen, Condition condition, AllocatableValue result, AllocatableValue x, AllocatableValue y, boolean unorderedIsTrue) {
        Condition testCondition = getASIMDFloatCompareCondition(condition, unorderedIsTrue);
        if (testCondition == Condition.NE) {
            return new ASIMDFloatCompareNEOp(gen, condition, result, x, y, unorderedIsTrue);
        } else {
            return new ASIMDFloatCompareOp(condition, result, x, y, unorderedIsTrue);
        }
    }

    /**
     * AArch64 does not have a vector floating point not equal (!=) operation. The simplest way to
     * perform this would be negating the equals operation. However, this negation would also affect
     * the comparison result for unordered (i.e. NaN) operands. As a workaround, instead we perform
     * != as (> || <). This has the same result and also preserves the value for NaN comparisons.
     */
    private static class ASIMDFloatCompareNEOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<ASIMDFloatCompareNEOp> TYPE = LIRInstructionClass.create(ASIMDFloatCompareNEOp.class);

        @Opcode protected final Condition condition;
        @Def({REG}) protected AllocatableValue result;
        @Alive({REG}) protected AllocatableValue x;
        @Alive({REG}) protected AllocatableValue y;
        @Temp({REG}) protected AllocatableValue[] temps;
        private final boolean unorderedIsTrue;

        ASIMDFloatCompareNEOp(LIRGenerator gen, Condition condition, AllocatableValue result, AllocatableValue x, AllocatableValue y, boolean unorderedIsTrue) {
            super(TYPE);
            assert x.getPlatformKind() == y.getPlatformKind() : x.getPlatformKind() + " " + y.getPlatformKind();
            /* Confirming test cast is NE. */
            Condition testCondition = getASIMDFloatCompareCondition(condition, unorderedIsTrue);
            assert testCondition == Condition.NE;
            this.condition = condition;
            this.result = result;
            this.x = x;
            this.y = y;
            this.temps = new AllocatableValue[2];
            temps[0] = gen.newVariable(result.getValueKind());
            temps[1] = gen.newVariable(result.getValueKind());
            this.unorderedIsTrue = unorderedIsTrue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            ASIMDSize size = ASIMDSize.fromVectorKind(result.getPlatformKind());
            ElementSize eSize = ElementSize.fromKind(result.getPlatformKind());

            Register dst = asRegister(result);
            Register left = asRegister(x);
            Register right = asRegister(y);
            Register cmp1 = asRegister(temps[0]);
            Register cmp2 = asRegister(temps[1]);

            /* left != right is equivalent to (left > right || right > left). */
            masm.neon.fcmgtVVV(size, eSize, cmp1, left, right);
            masm.neon.fcmgtVVV(size, eSize, cmp2, right, left);
            masm.neon.orrVVV(size, dst, cmp1, cmp2);
            if (unorderedIsTrue) {
                masm.neon.mvnVV(size, dst, dst);
            }
        }

    }

    private static class ASIMDFloatCompareOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<ASIMDFloatCompareOp> TYPE = LIRInstructionClass.create(ASIMDFloatCompareOp.class);

        @Opcode private final Condition condition;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        @Use({REG}) protected AllocatableValue y;
        private final boolean unorderedIsTrue;

        ASIMDFloatCompareOp(Condition condition, AllocatableValue result, AllocatableValue x, AllocatableValue y, boolean unorderedIsTrue) {
            super(TYPE);
            assert x.getPlatformKind() == y.getPlatformKind() : x.getPlatformKind() + " " + y.getPlatformKind();
            this.condition = condition;
            this.result = result;
            this.x = x;
            this.y = y;
            this.unorderedIsTrue = unorderedIsTrue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            ASIMDSize size = ASIMDSize.fromVectorKind(result.getPlatformKind());
            ElementSize eSize = ElementSize.fromKind(result.getPlatformKind());

            Register dst = asRegister(result);
            Register left = asRegister(x);
            Register right = asRegister(y);
            Condition testCondition = getASIMDFloatCompareCondition(condition, unorderedIsTrue);
            switch (testCondition) {
                case EQ:
                    masm.neon.fcmeqVVV(size, eSize, dst, left, right);
                    break;
                case LT:
                    masm.neon.fcmgtVVV(size, eSize, dst, right, left);
                    break;
                case LE:
                    masm.neon.fcmgeVVV(size, eSize, dst, right, left);
                    break;
                case GT:
                    masm.neon.fcmgtVVV(size, eSize, dst, left, right);
                    break;
                case GE:
                    masm.neon.fcmgeVVV(size, eSize, dst, left, right);
                    break;
                case AE:
                    masm.neon.facgeVVV(size, eSize, dst, left, right);
                    break;
                case BE:
                    masm.neon.facgeVVV(size, eSize, dst, right, left);
                    break;
                case AT:
                    masm.neon.facgtVVV(size, eSize, dst, left, right);
                    break;
                case BT:
                    masm.neon.facgtVVV(size, eSize, dst, right, left);
                    break;
                default:
                    throw GraalError.unimplemented();
            }
            if (unorderedIsTrue) {
                /* Negate result if the negated condition was performed. */
                masm.neon.mvnVV(size, dst, dst);
            }
        }
    }

    public static class ASIMDFloatCompareZeroOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<ASIMDFloatCompareZeroOp> TYPE = LIRInstructionClass.create(ASIMDFloatCompareZeroOp.class);

        @Opcode private final Condition condition;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        private final boolean unorderedIsTrue;

        public ASIMDFloatCompareZeroOp(Condition condition, AllocatableValue result, AllocatableValue x, boolean unorderedIsTrue) {
            super(TYPE);
            this.condition = condition;
            this.result = result;
            this.x = x;
            this.unorderedIsTrue = unorderedIsTrue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            ASIMDSize size = ASIMDSize.fromVectorKind(result.getPlatformKind());
            ElementSize eSize = ElementSize.fromKind(result.getPlatformKind());

            Register dst = asRegister(result);
            Register src = asRegister(x);
            Condition testCondition = getASIMDFloatCompareCondition(condition, unorderedIsTrue);
            switch (testCondition) {
                case EQ:
                    masm.neon.fcmeqZeroVV(size, eSize, dst, src);
                    break;
                case NE:
                    /* x != 0 is equivalent to |x| > 0 */
                    masm.neon.fabsVV(size, eSize, dst, src);
                    masm.neon.fcmgtZeroVV(size, eSize, dst, dst);
                    break;
                case LT:
                    masm.neon.fcmltZeroVV(size, eSize, dst, src);
                    break;
                case LE:
                    masm.neon.fcmleZeroVV(size, eSize, dst, src);
                    break;
                case GT:
                    masm.neon.fcmgtZeroVV(size, eSize, dst, src);
                    break;
                case GE:
                    masm.neon.fcmgeZeroVV(size, eSize, dst, src);
                    break;
                default:
                    throw GraalError.unimplemented();
            }
            if (unorderedIsTrue) {
                /* Negate result if the negated condition was performed. */
                masm.neon.mvnVV(size, dst, dst);
            }
        }
    }
}
