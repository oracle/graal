/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.nodes;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Stream;

import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64;

public class ArrayIndexOfForeignCalls {

    private static ForeignCallDescriptor foreignCallDescriptor(String name, int nValues) {
        return foreignCallDescriptor(name, nValues, int.class);
    }

    private static ForeignCallDescriptor foreignCallDescriptor(String name, int nValues, Class<?> valueClass) {
        Class<?>[] argTypes = new Class<?>[4 + nValues];
        argTypes[0] = Object.class;
        argTypes[1] = long.class;
        argTypes[2] = int.class;
        argTypes[3] = int.class;
        for (int i = 4; i < argTypes.length; i++) {
            argTypes[i] = valueClass;
        }
        return ForeignCalls.pureFunctionForeignCallDescriptor(name, int.class, argTypes);
    }

    public static final ForeignCallDescriptor STUB_INDEX_OF_1_S1 = foreignCallDescriptor("indexOf1S1", 1);
    public static final ForeignCallDescriptor STUB_INDEX_OF_2_S1 = foreignCallDescriptor("indexOf2S1", 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_3_S1 = foreignCallDescriptor("indexOf3S1", 3);
    public static final ForeignCallDescriptor STUB_INDEX_OF_4_S1 = foreignCallDescriptor("indexOf4S1", 4);
    public static final ForeignCallDescriptor STUB_INDEX_OF_1_S2 = foreignCallDescriptor("indexOf1S2", 1);
    public static final ForeignCallDescriptor STUB_INDEX_OF_2_S2 = foreignCallDescriptor("indexOf2S2", 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_3_S2 = foreignCallDescriptor("indexOf3S2", 3);
    public static final ForeignCallDescriptor STUB_INDEX_OF_4_S2 = foreignCallDescriptor("indexOf4S2", 4);
    public static final ForeignCallDescriptor STUB_INDEX_OF_1_S4 = foreignCallDescriptor("indexOf1S4", 1);
    public static final ForeignCallDescriptor STUB_INDEX_OF_2_S4 = foreignCallDescriptor("indexOf2S4", 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_3_S4 = foreignCallDescriptor("indexOf3S4", 3);
    public static final ForeignCallDescriptor STUB_INDEX_OF_4_S4 = foreignCallDescriptor("indexOf4S4", 4);
    public static final ForeignCallDescriptor STUB_INDEX_OF_RANGE_1_S1 = foreignCallDescriptor("indexOfRange1S1", 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_RANGE_1_S2 = foreignCallDescriptor("indexOfRange1S2", 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_RANGE_1_S4 = foreignCallDescriptor("indexOfRange1S4", 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_RANGE_2_S1 = foreignCallDescriptor("indexOfRange2S1", 4);
    public static final ForeignCallDescriptor STUB_INDEX_OF_RANGE_2_S2 = foreignCallDescriptor("indexOfRange2S2", 4);
    public static final ForeignCallDescriptor STUB_INDEX_OF_RANGE_2_S4 = foreignCallDescriptor("indexOfRange2S4", 4);

    public static final ForeignCallDescriptor STUB_INDEX_OF_WITH_MASK_S1 = foreignCallDescriptor("indexOfWithMaskS1", 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_WITH_MASK_S2 = foreignCallDescriptor("indexOfWithMaskS2", 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_WITH_MASK_S4 = foreignCallDescriptor("indexOfWithMaskS4", 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_S1 = foreignCallDescriptor("indexOfTwoConsecutiveS1", 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_S2 = foreignCallDescriptor("indexOfTwoConsecutiveS2", 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_S4 = foreignCallDescriptor("indexOfTwoConsecutiveS4", 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S1 = foreignCallDescriptor("indexOfTwoConsecutiveWithMaskS1", 4);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S2 = foreignCallDescriptor("indexOfTwoConsecutiveWithMaskS2", 4);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S4 = foreignCallDescriptor("indexOfTwoConsecutiveWithMaskS4", 4);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TABLE_S1 = foreignCallDescriptor("indexOfTableS1", 1, byte[].class);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TABLE_S2 = foreignCallDescriptor("indexOfTableS2", 1, byte[].class);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TABLE_S4 = foreignCallDescriptor("indexOfTableS4", 1, byte[].class);

    /**
     * CAUTION: the ordering here is important: ever entry's index must be 4 * log2(stride) +
     * [number of values] - 1.
     *
     * @see #getStub(ArrayIndexOfNode)
     */
    private static final ForeignCallDescriptor[] STUBS_INDEX_OF_ANY = new ForeignCallDescriptor[]{
                    STUB_INDEX_OF_1_S1,
                    STUB_INDEX_OF_2_S1,
                    STUB_INDEX_OF_3_S1,
                    STUB_INDEX_OF_4_S1,
                    STUB_INDEX_OF_1_S2,
                    STUB_INDEX_OF_2_S2,
                    STUB_INDEX_OF_3_S2,
                    STUB_INDEX_OF_4_S2,
                    STUB_INDEX_OF_1_S4,
                    STUB_INDEX_OF_2_S4,
                    STUB_INDEX_OF_3_S4,
                    STUB_INDEX_OF_4_S4,
    };

    public static final ForeignCallDescriptor[] STUBS = Stream.concat(Arrays.stream(STUBS_INDEX_OF_ANY), Stream.of(
                    STUB_INDEX_OF_RANGE_1_S1,
                    STUB_INDEX_OF_RANGE_1_S2,
                    STUB_INDEX_OF_RANGE_1_S4,
                    STUB_INDEX_OF_RANGE_2_S1,
                    STUB_INDEX_OF_RANGE_2_S2,
                    STUB_INDEX_OF_RANGE_2_S4,
                    STUB_INDEX_OF_WITH_MASK_S1,
                    STUB_INDEX_OF_WITH_MASK_S2,
                    STUB_INDEX_OF_WITH_MASK_S4,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_S1,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_S2,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_S4,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S1,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S2,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S4,
                    STUB_INDEX_OF_TABLE_S1,
                    STUB_INDEX_OF_TABLE_S2,
                    STUB_INDEX_OF_TABLE_S4)).toArray(ForeignCallDescriptor[]::new);

    public static EnumSet<AMD64.CPUFeature> getMinimumFeaturesAMD64(ForeignCallDescriptor foreignCallDescriptor) {
        return ArrayIndexOfNode.minFeaturesAMD64(getStride(foreignCallDescriptor), getVariant(foreignCallDescriptor));
    }

    private static Stride getStride(ForeignCallDescriptor foreignCallDescriptor) {
        return Stride.valueOf(foreignCallDescriptor.getName().substring(foreignCallDescriptor.getName().length() - 2));
    }

    private static LIRGeneratorTool.ArrayIndexOfVariant getVariant(ForeignCallDescriptor foreignCallDescriptor) {
        String name = foreignCallDescriptor.getName();
        if (name.startsWith("indexOfRange")) {
            return LIRGeneratorTool.ArrayIndexOfVariant.MatchRange;
        }
        if (name.startsWith("indexOfTable")) {
            return LIRGeneratorTool.ArrayIndexOfVariant.Table;
        }
        if (name.startsWith("indexOfTwoConsecutiveWithMask")) {
            return LIRGeneratorTool.ArrayIndexOfVariant.FindTwoConsecutiveWithMask;
        }
        if (name.startsWith("indexOfTwoConsecutive")) {
            return LIRGeneratorTool.ArrayIndexOfVariant.FindTwoConsecutive;
        }
        if (name.startsWith("indexOfWithMask")) {
            return LIRGeneratorTool.ArrayIndexOfVariant.WithMask;
        }
        char n = name.charAt("indexOf".length());
        GraalError.guarantee(name.startsWith("indexOf") && '1' <= n && n <= '4', "unexpected foreign call descriptor name");
        return LIRGeneratorTool.ArrayIndexOfVariant.MatchAny;
    }

    public static ForeignCallDescriptor getStub(ArrayIndexOfNode indexOfNode) {
        Stride stride = indexOfNode.getStride();
        int valueCount = indexOfNode.getNumberOfValues();
        // Checkstyle: stop FallThrough
        switch (indexOfNode.getVariant()) {
            case MatchAny:
                int index = (4 * stride.log2) + (valueCount - 1);
                return STUBS_INDEX_OF_ANY[index];
            case MatchRange:
                switch (stride) {
                    case S1:
                        return valueCount == 2 ? STUB_INDEX_OF_RANGE_1_S1 : STUB_INDEX_OF_RANGE_2_S1;
                    case S2:
                        return valueCount == 2 ? STUB_INDEX_OF_RANGE_1_S2 : STUB_INDEX_OF_RANGE_2_S2;
                    case S4:
                        return valueCount == 2 ? STUB_INDEX_OF_RANGE_1_S4 : STUB_INDEX_OF_RANGE_2_S4;
                    default:
                        throw GraalError.shouldNotReachHereUnexpectedValue(stride); // ExcludeFromJacocoGeneratedReport
                }
            case WithMask:
                switch (stride) {
                    case S1:
                        return STUB_INDEX_OF_WITH_MASK_S1;
                    case S2:
                        return STUB_INDEX_OF_WITH_MASK_S2;
                    case S4:
                        return STUB_INDEX_OF_WITH_MASK_S4;
                    default:
                        throw GraalError.shouldNotReachHereUnexpectedValue(stride); // ExcludeFromJacocoGeneratedReport
                }
            case FindTwoConsecutive:
                switch (stride) {
                    case S1:
                        return STUB_INDEX_OF_TWO_CONSECUTIVE_S1;
                    case S2:
                        return STUB_INDEX_OF_TWO_CONSECUTIVE_S2;
                    case S4:
                        return STUB_INDEX_OF_TWO_CONSECUTIVE_S4;
                    default:
                        throw GraalError.shouldNotReachHereUnexpectedValue(stride); // ExcludeFromJacocoGeneratedReport
                }
            case FindTwoConsecutiveWithMask:
                switch (stride) {
                    case S1:
                        return STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S1;
                    case S2:
                        return STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S2;
                    case S4:
                        return STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S4;
                    default:
                        throw GraalError.shouldNotReachHereUnexpectedValue(stride); // ExcludeFromJacocoGeneratedReport
                }
            case Table:
                switch (stride) {
                    case S1:
                        return STUB_INDEX_OF_TABLE_S1;
                    case S2:
                        return STUB_INDEX_OF_TABLE_S2;
                    case S4:
                        return STUB_INDEX_OF_TABLE_S4;
                    default:
                        throw GraalError.shouldNotReachHereUnexpectedValue(stride); // ExcludeFromJacocoGeneratedReport
                }
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(indexOfNode.getVariant()); // ExcludeFromJacocoGeneratedReport
        }
        // Checkstyle: resume FallThrough
    }
}
