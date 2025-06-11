/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.vector.replacements.vectorapi;

import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicMapUtil;

import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPIConvertNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPIMaskReductionCoercedNode;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class allows mapping numeric operation codes defined in
 * {@code jdk.internal.vm.vector.VectorSupport} to {@link ArithmeticOpTable} operations.
 *
 * Some TODOs:
 * <ul>
 * <li>LROTATE, RROTATE, REVERSE, REVERSE_BYTES: we have nodes for these, but they are not
 * arithmetic ops</li>
 * <li>COMPRESS_BITS, EXPAND_BITS: need to check which of these map to our existing compress and
 * expand ops, and what the other pair does</li>
 * </ul>
 */
public class VectorAPIOperations {

    public static Tables TABLES = new Tables(Constants.CONSTANT_MAP);

    public static ArithmeticOpTable.UnaryOp<?> lookupIntegerUnaryOp(int opcode) {
        return TABLES.integerUnaryOperations.get(opcode);
    }

    public static ArithmeticOpTable.BinaryOp<?> lookupIntegerBinaryOp(int opcode) {
        return TABLES.integerBinaryOperations.get(opcode);
    }

    public static ArithmeticOpTable.ShiftOp<?> lookupIntegerShiftOp(int opcode) {
        return TABLES.integerShiftOperations.get(opcode);
    }

    public static ArithmeticOpTable.UnaryOp<?> lookupFloatingPointUnaryOp(int opcode) {
        return TABLES.floatingPointUnaryOperations.get(opcode);
    }

    public static ArithmeticOpTable.BinaryOp<?> lookupFloatingPointBinaryOp(int opcode) {
        return TABLES.floatingPointBinaryOperations.get(opcode);
    }

    public static ArithmeticOpTable.TernaryOp<?> lookupFloatingPointTernaryOp(int opcode) {
        return TABLES.floatingPointTernaryOperations.get(opcode);
    }

    public static ArithmeticOpTable.UnaryOp<?> lookupOpMaskUnaryOp(int opcode) {
        return TABLES.opMaskUnaryOperations.get(opcode);
    }

    public static ArithmeticOpTable.BinaryOp<?> lookupOpMaskBinaryOp(int opcode) {
        return TABLES.opMaskBinaryOperations.get(opcode);
    }

    public static Condition lookupCondition(int conditionCode) {
        return TABLES.conditions.get(conditionCode);
    }

    public static VectorAPIConvertNode.ConversionOp lookupConversion(int conversionCode) {
        return TABLES.conversions.get(conversionCode);
    }

    public static VectorAPIMaskReductionCoercedNode.Op lookupMaskReduction(int maskReductionCode) {
        return TABLES.maskReductions.get(maskReductionCode);
    }

    public static class Constants {

        public static final EconomicMap<String, Integer> CONSTANT_MAP;

        // Unsigned comparisons apply to BT_le, BT_ge, BT_lt, BT_gt for integral types
        private static final int BT_unsigned_compare = 0b10000;

        static {
            CONSTANT_MAP = EconomicMap.create();
            /*
             * The following values are copied from VectorSupport.java. This avoids having to look
             * them up reflectively at compile time. The disadvantage is that JDK updates could
             * theoretically change these constants. A unit test ensures that the values we use in
             * our opcode tables are always in sync with the ones in the underlying JDK's
             * VectorSupport class.
             */
            // Unary
            CONSTANT_MAP.put("VECTOR_OP_ABS", 0);
            CONSTANT_MAP.put("VECTOR_OP_NEG", 1);
            CONSTANT_MAP.put("VECTOR_OP_SQRT", 2);
            CONSTANT_MAP.put("VECTOR_OP_BIT_COUNT", 3);

            // Binary
            CONSTANT_MAP.put("VECTOR_OP_ADD", 4);
            CONSTANT_MAP.put("VECTOR_OP_SUB", 5);
            CONSTANT_MAP.put("VECTOR_OP_MUL", 6);
            CONSTANT_MAP.put("VECTOR_OP_DIV", 7);
            CONSTANT_MAP.put("VECTOR_OP_MIN", 8);
            CONSTANT_MAP.put("VECTOR_OP_MAX", 9);

            CONSTANT_MAP.put("VECTOR_OP_AND", 10);
            CONSTANT_MAP.put("VECTOR_OP_OR", 11);
            CONSTANT_MAP.put("VECTOR_OP_XOR", 12);

            // Ternary
            CONSTANT_MAP.put("VECTOR_OP_FMA", 13);

            // Broadcast int
            CONSTANT_MAP.put("VECTOR_OP_LSHIFT", 14);
            CONSTANT_MAP.put("VECTOR_OP_RSHIFT", 15);
            CONSTANT_MAP.put("VECTOR_OP_URSHIFT", 16);

            CONSTANT_MAP.put("VECTOR_OP_CAST", 17);
            CONSTANT_MAP.put("VECTOR_OP_UCAST", 18);
            CONSTANT_MAP.put("VECTOR_OP_REINTERPRET", 19);

            // Mask manipulation operations
            CONSTANT_MAP.put("VECTOR_OP_MASK_TRUECOUNT", 20);
            CONSTANT_MAP.put("VECTOR_OP_MASK_FIRSTTRUE", 21);
            CONSTANT_MAP.put("VECTOR_OP_MASK_LASTTRUE", 22);
            CONSTANT_MAP.put("VECTOR_OP_MASK_TOLONG", 23);

            // Rotate operations
            CONSTANT_MAP.put("VECTOR_OP_LROTATE", 24);
            CONSTANT_MAP.put("VECTOR_OP_RROTATE", 25);

            // Compression expansion operations
            CONSTANT_MAP.put("VECTOR_OP_COMPRESS", 26);
            CONSTANT_MAP.put("VECTOR_OP_EXPAND", 27);
            CONSTANT_MAP.put("VECTOR_OP_MASK_COMPRESS", 28);

            // Leading/Trailing zeros count operations
            CONSTANT_MAP.put("VECTOR_OP_TZ_COUNT", 29);
            CONSTANT_MAP.put("VECTOR_OP_LZ_COUNT", 30);

            // Reverse operation
            CONSTANT_MAP.put("VECTOR_OP_REVERSE", 31);
            CONSTANT_MAP.put("VECTOR_OP_REVERSE_BYTES", 32);

            // Compress and Expand Bits operation
            CONSTANT_MAP.put("VECTOR_OP_COMPRESS_BITS", 33);
            CONSTANT_MAP.put("VECTOR_OP_EXPAND_BITS", 34);

            // See src/hotspot/share/opto/subnode.hpp
            // struct BoolTest, and enclosed enum mask
            CONSTANT_MAP.put("BT_eq", 0);  // 0000
            CONSTANT_MAP.put("BT_ne", 4);  // 0100
            CONSTANT_MAP.put("BT_le", 5);  // 0101
            CONSTANT_MAP.put("BT_ge", 7);  // 0111
            CONSTANT_MAP.put("BT_lt", 3);  // 0011
            CONSTANT_MAP.put("BT_gt", 1);  // 0001
            CONSTANT_MAP.put("BT_overflow", 2);     // 0010
            CONSTANT_MAP.put("BT_no_overflow", 6);  // 0110
            // never = 8 1000
            // illegal = 9 1001
            CONSTANT_MAP.put("BT_ule", 5 /* BT_le */ | BT_unsigned_compare);
            CONSTANT_MAP.put("BT_uge", 7 /* BT_ge */ | BT_unsigned_compare);
            CONSTANT_MAP.put("BT_ult", 3 /* BT_lt */ | BT_unsigned_compare);
            CONSTANT_MAP.put("BT_ugt", 1 /* BT_gt */ | BT_unsigned_compare);
        }
    }

    public static final class Tables {
        private EconomicMap<Integer, ArithmeticOpTable.UnaryOp<?>> integerUnaryOperations;
        private EconomicMap<Integer, ArithmeticOpTable.BinaryOp<?>> integerBinaryOperations;
        private EconomicMap<Integer, ArithmeticOpTable.ShiftOp<?>> integerShiftOperations;
        private EconomicMap<Integer, ArithmeticOpTable.UnaryOp<?>> floatingPointUnaryOperations;
        private EconomicMap<Integer, ArithmeticOpTable.BinaryOp<?>> floatingPointBinaryOperations;
        private EconomicMap<Integer, ArithmeticOpTable.TernaryOp<?>> floatingPointTernaryOperations;
        private EconomicMap<Integer, ArithmeticOpTable.UnaryOp<?>> opMaskUnaryOperations;
        private EconomicMap<Integer, ArithmeticOpTable.BinaryOp<?>> opMaskBinaryOperations;
        private EconomicMap<Integer, Condition> conditions;
        private EconomicMap<Integer, VectorAPIConvertNode.ConversionOp> conversions;
        private EconomicMap<Integer, VectorAPIMaskReductionCoercedNode.Op> maskReductions;

        public Tables(EconomicMap<String, Integer> constantMap) {
            this.integerUnaryOperations = resolveUnaryOperations(constantMap, IntegerStamp.OPS);
            this.integerBinaryOperations = resolveBinaryOperations(constantMap, IntegerStamp.OPS);
            this.integerShiftOperations = resolveShiftOperations(constantMap, IntegerStamp.OPS);
            this.floatingPointUnaryOperations = resolveUnaryOperations(constantMap, FloatStamp.OPS);
            this.floatingPointBinaryOperations = resolveBinaryOperations(constantMap, FloatStamp.OPS);
            this.floatingPointTernaryOperations = resolveTernaryOperations(constantMap, FloatStamp.OPS);
            this.opMaskUnaryOperations = resolveUnaryOperations(constantMap, SimdStamp.OPMASK_OPS);
            this.opMaskBinaryOperations = resolveBinaryOperations(constantMap, SimdStamp.OPMASK_OPS);
            this.conditions = resolveCompareOperations(constantMap);
            this.conversions = resolveConversionOperations(constantMap);
            this.maskReductions = resolveMaskReductionOperations(constantMap);
        }

        public Tables(ResolvedJavaType constantHolderType, CoreProviders providers) {
            this(constantMapFromType(constantHolderType, providers));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Tables other = (Tables) o;
            return EconomicMapUtil.equals(integerUnaryOperations, other.integerUnaryOperations) &&
                            EconomicMapUtil.equals(integerBinaryOperations, other.integerBinaryOperations) &&
                            EconomicMapUtil.equals(integerShiftOperations, other.integerShiftOperations) &&
                            EconomicMapUtil.equals(floatingPointUnaryOperations, other.floatingPointUnaryOperations) &&
                            EconomicMapUtil.equals(floatingPointBinaryOperations, other.floatingPointBinaryOperations) &&
                            EconomicMapUtil.equals(floatingPointTernaryOperations, other.floatingPointTernaryOperations) &&
                            EconomicMapUtil.equals(conditions, other.conditions) &&
                            EconomicMapUtil.equals(conversions, other.conversions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(integerUnaryOperations, integerBinaryOperations, integerShiftOperations, floatingPointUnaryOperations, floatingPointBinaryOperations, floatingPointTernaryOperations,
                            conditions, conversions);
        }

        private static EconomicMap<String, Integer> constantMapFromType(ResolvedJavaType constantHolderType, CoreProviders providers) {
            GraalError.guarantee(constantHolderType.isInitialized(), "class %s must be initialized for static field lookups", constantHolderType);
            /*
             * All the operations codes we are interested in are static final int fields in
             * VectorSupport. Read all such fields into a map, then look them up by name below.
             */
            ConstantReflectionProvider constantReflection = providers.getConstantReflection();
            EconomicMap<String, Integer> constantMap = EconomicMap.create();
            for (ResolvedJavaField field : constantHolderType.getStaticFields()) {
                if (field.isFinal() && field.getJavaKind() == JavaKind.Int) {
                    constantMap.put(field.getName(), constantReflection.readFieldValue(field, null).asInt());
                }
            }
            return constantMap;
        }

        private static EconomicMap<Integer, ArithmeticOpTable.UnaryOp<?>> resolveUnaryOperations(EconomicMap<String, Integer> vectorSupportConstants, ArithmeticOpTable arithmeticOpTable) {
            EconomicMap<Integer, ArithmeticOpTable.UnaryOp<?>> ops = EconomicMap.create();
            putOpCode(ops, vectorSupportConstants, "ABS", arithmeticOpTable.getAbs());
            putOpCode(ops, vectorSupportConstants, "NEG", arithmeticOpTable.getNeg());
            putOpCode(ops, vectorSupportConstants, "SQRT", arithmeticOpTable.getSqrt());
            /*
             * VectorSupport also has a BIT_COUNT operation for which we do not have an arithmetic
             * op. The Vector API also supports a not() operation, but it does not expand to a unary
             * NOT opcode but rather to XOR with -1.
             */
            return ops;
        }

        private static EconomicMap<Integer, ArithmeticOpTable.BinaryOp<?>> resolveBinaryOperations(EconomicMap<String, Integer> vectorSupportConstants, ArithmeticOpTable arithmeticOpTable) {
            EconomicMap<Integer, ArithmeticOpTable.BinaryOp<?>> ops = EconomicMap.create();
            putOpCode(ops, vectorSupportConstants, "ADD", arithmeticOpTable.getAdd());
            putOpCode(ops, vectorSupportConstants, "SUB", arithmeticOpTable.getSub());
            putOpCode(ops, vectorSupportConstants, "MUL", arithmeticOpTable.getMul());
            putOpCode(ops, vectorSupportConstants, "DIV", arithmeticOpTable.getDiv());
            putOpCode(ops, vectorSupportConstants, "MIN", arithmeticOpTable.getMin());
            putOpCode(ops, vectorSupportConstants, "MAX", arithmeticOpTable.getMax());
            putOpCode(ops, vectorSupportConstants, "AND", arithmeticOpTable.getAnd());
            putOpCode(ops, vectorSupportConstants, "OR", arithmeticOpTable.getOr());
            putOpCode(ops, vectorSupportConstants, "XOR", arithmeticOpTable.getXor());
            putOpCode(ops, vectorSupportConstants, "LSHIFT", arithmeticOpTable.getShl());
            putOpCode(ops, vectorSupportConstants, "URSHIFT", arithmeticOpTable.getUShr());
            putOpCode(ops, vectorSupportConstants, "RSHIFT", arithmeticOpTable.getShr());
            /*
             * We also have Rem, (U)MulHigh, and UMin/UMax operations which do not exist in
             * VectorSupport.
             */
            return ops;
        }

        private static EconomicMap<Integer, ArithmeticOpTable.TernaryOp<?>> resolveTernaryOperations(EconomicMap<String, Integer> vectorSupportConstants, ArithmeticOpTable arithmeticOpTable) {
            EconomicMap<Integer, ArithmeticOpTable.TernaryOp<?>> ops = EconomicMap.create();
            putOpCode(ops, vectorSupportConstants, "FMA", arithmeticOpTable.getFMA());
            return ops;
        }

        private static EconomicMap<Integer, ArithmeticOpTable.ShiftOp<?>> resolveShiftOperations(EconomicMap<String, Integer> vectorSupportConstants, ArithmeticOpTable arithmeticOpTable) {
            EconomicMap<Integer, ArithmeticOpTable.ShiftOp<?>> ops = EconomicMap.create();
            putOpCode(ops, vectorSupportConstants, "LSHIFT", arithmeticOpTable.getShl());
            putOpCode(ops, vectorSupportConstants, "RSHIFT", arithmeticOpTable.getShr());
            putOpCode(ops, vectorSupportConstants, "URSHIFT", arithmeticOpTable.getUShr());
            return ops;
        }

        /**
         * Looks up the {@code int} constant opcode corresponding to {@code opName} in the
         * {@code vectorSupport} class, then stores the given {@code op} in the {@code ops} table
         * under the numeric opcode. Does nothing if {@code op} is {@code null}.
         */
        private static <T> void putOpCode(EconomicMap<Integer, T> ops, EconomicMap<String, Integer> vectorSupportConstants, String opName, T op) {
            if (op != null) {
                Integer opcode = vectorSupportConstants.get("VECTOR_OP_" + opName);
                GraalError.guarantee(opcode != null, "did not find constant for operation %s in VectorSupport", opName);
                ops.put(opcode, op);
            }
        }

        private static EconomicMap<Integer, Condition> resolveCompareOperations(EconomicMap<String, Integer> vectorSupportConstants) {
            EconomicMap<Integer, Condition> conditions = EconomicMap.create();
            putCondition(conditions, vectorSupportConstants, "eq", Condition.EQ);
            putCondition(conditions, vectorSupportConstants, "ne", Condition.NE);
            putCondition(conditions, vectorSupportConstants, "le", Condition.LE);
            putCondition(conditions, vectorSupportConstants, "ge", Condition.GE);
            putCondition(conditions, vectorSupportConstants, "lt", Condition.LT);
            putCondition(conditions, vectorSupportConstants, "gt", Condition.GT);
            putCondition(conditions, vectorSupportConstants, "ule", Condition.BE);
            putCondition(conditions, vectorSupportConstants, "uge", Condition.AE);
            putCondition(conditions, vectorSupportConstants, "ult", Condition.BT);
            putCondition(conditions, vectorSupportConstants, "ugt", Condition.AT);
            return conditions;
        }

        private static void putCondition(EconomicMap<Integer, Condition> conditions, EconomicMap<String, Integer> vectorSupportConstants, String conditionName, Condition condition) {
            Integer conditionCode = vectorSupportConstants.get("BT_" + conditionName);
            GraalError.guarantee(conditionCode != null, "did not find constant for condition %s in VectorSupport", condition);
            conditions.put(conditionCode, condition);
        }

        private static EconomicMap<Integer, VectorAPIConvertNode.ConversionOp> resolveConversionOperations(EconomicMap<String, Integer> vectorSupportConstants) {
            EconomicMap<Integer, VectorAPIConvertNode.ConversionOp> conversions = EconomicMap.create();
            putConversion(conversions, vectorSupportConstants, "CAST", VectorAPIConvertNode.ConversionOp.CAST);
            putConversion(conversions, vectorSupportConstants, "UCAST", VectorAPIConvertNode.ConversionOp.UCAST);
            putConversion(conversions, vectorSupportConstants, "REINTERPRET", VectorAPIConvertNode.ConversionOp.REINTERPRET);
            return conversions;
        }

        private static void putConversion(EconomicMap<Integer, VectorAPIConvertNode.ConversionOp> conversions, EconomicMap<String, Integer> vectorSupportConstants, String opName,
                        VectorAPIConvertNode.ConversionOp op) {
            Integer conversionCode = vectorSupportConstants.get("VECTOR_OP_" + opName);
            GraalError.guarantee(conversionCode != null, "did not find constant for conversion %s in VectorSupport", op);
            conversions.put(conversionCode, op);
        }

        private static EconomicMap<Integer, VectorAPIMaskReductionCoercedNode.Op> resolveMaskReductionOperations(EconomicMap<String, Integer> vectorSupportConstants) {
            EconomicMap<Integer, VectorAPIMaskReductionCoercedNode.Op> maskReductions = EconomicMap.create();
            putMaskReduction(maskReductions, vectorSupportConstants, "TOLONG", VectorAPIMaskReductionCoercedNode.Op.TO_LONG);
            putMaskReduction(maskReductions, vectorSupportConstants, "TRUECOUNT", VectorAPIMaskReductionCoercedNode.Op.TRUE_COUNT);
            putMaskReduction(maskReductions, vectorSupportConstants, "FIRSTTRUE", VectorAPIMaskReductionCoercedNode.Op.FIRST_TRUE);
            putMaskReduction(maskReductions, vectorSupportConstants, "LASTTRUE", VectorAPIMaskReductionCoercedNode.Op.LAST_TRUE);
            return maskReductions;
        }

        private static void putMaskReduction(EconomicMap<Integer, VectorAPIMaskReductionCoercedNode.Op> maskReductions, EconomicMap<String, Integer> vectorSupportConstants, String opName,
                        VectorAPIMaskReductionCoercedNode.Op op) {
            Integer code = vectorSupportConstants.get("VECTOR_OP_MASK_" + opName);
            GraalError.guarantee(code != null, "did not find constant for mask reduction %s in VectorSupport", op);
            maskReductions.put(code, op);
        }
    }
}
