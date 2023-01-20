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
package org.graalvm.compiler.replacements.nodes;

import java.util.Arrays;
import java.util.stream.Stream;

import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.GraalError;

public class ArrayIndexOfForeignCalls {

    private static ForeignCallDescriptor foreignCallDescriptor(String name, int nValues) {
        Class<?>[] argTypes = new Class<?>[4 + nValues];
        argTypes[0] = Object.class;
        argTypes[1] = long.class;
        for (int i = 2; i < argTypes.length; i++) {
            argTypes[i] = int.class;
        }
        return ForeignCalls.pureFunctionForeignCallDescriptor(name, int.class, argTypes);
    }

    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_S1 = foreignCallDescriptor("indexOfTwoConsecutiveS1", 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_S2 = foreignCallDescriptor("indexOfTwoConsecutiveS2", 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_S4 = foreignCallDescriptor("indexOfTwoConsecutiveS4", 2);

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

    public static final ForeignCallDescriptor STUB_INDEX_OF_WITH_MASK_S1 = foreignCallDescriptor("indexOfWithMaskS1", 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_WITH_MASK_S2 = foreignCallDescriptor("indexOfWithMaskS2", 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_WITH_MASK_S4 = foreignCallDescriptor("indexOfWithMaskS4", 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S1 = foreignCallDescriptor("indexOfTwoConsecutiveWithMaskS1", 4);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S2 = foreignCallDescriptor("indexOfTwoConsecutiveWithMaskS2", 4);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S4 = foreignCallDescriptor("indexOfTwoConsecutiveWithMaskS4", 4);

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

    public static final ForeignCallDescriptor[] STUBS = Stream.concat(Stream.of(
                    STUB_INDEX_OF_TWO_CONSECUTIVE_S1,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_S2,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_S4,
                    STUB_INDEX_OF_WITH_MASK_S1,
                    STUB_INDEX_OF_WITH_MASK_S2,
                    STUB_INDEX_OF_WITH_MASK_S4,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S1,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S2,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S4),
                    Arrays.stream(STUBS_INDEX_OF_ANY)).toArray(ForeignCallDescriptor[]::new);

    public static ForeignCallDescriptor getStub(ArrayIndexOfNode indexOfNode) {
        Stride stride = indexOfNode.getStride();
        int valueCount = indexOfNode.getNumberOfValues();
        boolean findTwoConsecutive = indexOfNode.isFindTwoConsecutive();
        GraalError.guarantee(stride == Stride.S1 || stride == Stride.S2 || stride == Stride.S4, "unsupported stride");
        GraalError.guarantee(valueCount >= 1 && valueCount <= 4, "unsupported valueCount");
        if (indexOfNode.isWithMask()) {
            if (findTwoConsecutive) {
                GraalError.guarantee(valueCount == 4, "findTwoConsecutive with mask requires 4 values");
                switch (stride) {
                    case S1:
                        return STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S1;
                    case S2:
                        return STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S2;
                    case S4:
                        return STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S4;
                    default:
                        throw GraalError.shouldNotReachHere();
                }
            } else {
                GraalError.guarantee(valueCount == 2, "indexOf with mask requires 2 values");
                switch (stride) {
                    case S1:
                        return STUB_INDEX_OF_WITH_MASK_S1;
                    case S2:
                        return STUB_INDEX_OF_WITH_MASK_S2;
                    case S4:
                        return STUB_INDEX_OF_WITH_MASK_S4;
                    default:
                        throw GraalError.shouldNotReachHere();
                }
            }
        } else if (findTwoConsecutive) {
            GraalError.guarantee(valueCount == 2, "findTwoConsecutive without mask requires 2 values");
            switch (stride) {
                case S1:
                    return STUB_INDEX_OF_TWO_CONSECUTIVE_S1;
                case S2:
                    return STUB_INDEX_OF_TWO_CONSECUTIVE_S2;
                case S4:
                    return STUB_INDEX_OF_TWO_CONSECUTIVE_S4;
                default:
                    throw GraalError.shouldNotReachHere();
            }
        } else {
            int index = (4 * stride.log2) + (valueCount - 1);
            return STUBS_INDEX_OF_ANY[index];
        }
    }
}
