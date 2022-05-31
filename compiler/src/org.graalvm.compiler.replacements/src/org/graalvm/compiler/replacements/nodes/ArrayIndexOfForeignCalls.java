/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.StrideUtil.NONE;
import static org.graalvm.compiler.core.common.StrideUtil.S1;
import static org.graalvm.compiler.core.common.StrideUtil.S2;
import static org.graalvm.compiler.core.common.StrideUtil.S4;

import java.util.Arrays;
import java.util.stream.Stream;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.meta.JavaKind;

public class ArrayIndexOfForeignCalls {

    private static ForeignCallDescriptor foreignCallDescriptor(String name, Class<?> arrayArgType, int nValues) {
        Class<?>[] argTypes = new Class<?>[4 + nValues];
        argTypes[0] = arrayArgType;
        argTypes[1] = long.class;
        for (int i = 2; i < argTypes.length; i++) {
            argTypes[i] = int.class;
        }
        return ForeignCalls.pureFunctionForeignCallDescriptor(name, int.class, argTypes);
    }

    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_B_S1 = foreignCallDescriptor("indexOfTwoConsecutiveBS1", byte[].class, 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_B_S2 = foreignCallDescriptor("indexOfTwoConsecutiveBS2", byte[].class, 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_C_S2 = foreignCallDescriptor("indexOfTwoConsecutiveCS2", char[].class, 2);

    public static final ForeignCallDescriptor STUB_INDEX_OF_B_1_S1 = foreignCallDescriptor("indexOfB1S1", byte[].class, 1);
    public static final ForeignCallDescriptor STUB_INDEX_OF_B_2_S1 = foreignCallDescriptor("indexOfB2S1", byte[].class, 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_B_3_S1 = foreignCallDescriptor("indexOfB3S1", byte[].class, 3);
    public static final ForeignCallDescriptor STUB_INDEX_OF_B_4_S1 = foreignCallDescriptor("indexOfB4S1", byte[].class, 4);
    public static final ForeignCallDescriptor STUB_INDEX_OF_B_1_S2 = foreignCallDescriptor("indexOfB1S2", byte[].class, 1);
    public static final ForeignCallDescriptor STUB_INDEX_OF_B_2_S2 = foreignCallDescriptor("indexOfB2S2", byte[].class, 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_B_3_S2 = foreignCallDescriptor("indexOfB3S2", byte[].class, 3);
    public static final ForeignCallDescriptor STUB_INDEX_OF_B_4_S2 = foreignCallDescriptor("indexOfB4S2", byte[].class, 4);

    public static final ForeignCallDescriptor STUB_INDEX_OF_C_1_S2 = foreignCallDescriptor("indexOfC1S2", char[].class, 1);
    public static final ForeignCallDescriptor STUB_INDEX_OF_C_2_S2 = foreignCallDescriptor("indexOfC2S2", char[].class, 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_C_3_S2 = foreignCallDescriptor("indexOfC3S2", char[].class, 3);
    public static final ForeignCallDescriptor STUB_INDEX_OF_C_4_S2 = foreignCallDescriptor("indexOfC4S2", char[].class, 4);

    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_S1 = foreignCallDescriptor("indexOfTwoConsecutiveS1", Object.class, 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_S2 = foreignCallDescriptor("indexOfTwoConsecutiveS2", Object.class, 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_S4 = foreignCallDescriptor("indexOfTwoConsecutiveS4", Object.class, 2);

    public static final ForeignCallDescriptor STUB_INDEX_OF_1_S1 = foreignCallDescriptor("indexOf1S1", Object.class, 1);
    public static final ForeignCallDescriptor STUB_INDEX_OF_2_S1 = foreignCallDescriptor("indexOf2S1", Object.class, 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_3_S1 = foreignCallDescriptor("indexOf3S1", Object.class, 3);
    public static final ForeignCallDescriptor STUB_INDEX_OF_4_S1 = foreignCallDescriptor("indexOf4S1", Object.class, 4);
    public static final ForeignCallDescriptor STUB_INDEX_OF_1_S2 = foreignCallDescriptor("indexOf1S2", Object.class, 1);
    public static final ForeignCallDescriptor STUB_INDEX_OF_2_S2 = foreignCallDescriptor("indexOf2S2", Object.class, 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_3_S2 = foreignCallDescriptor("indexOf3S2", Object.class, 3);
    public static final ForeignCallDescriptor STUB_INDEX_OF_4_S2 = foreignCallDescriptor("indexOf4S2", Object.class, 4);
    public static final ForeignCallDescriptor STUB_INDEX_OF_1_S4 = foreignCallDescriptor("indexOf1S4", Object.class, 1);
    public static final ForeignCallDescriptor STUB_INDEX_OF_2_S4 = foreignCallDescriptor("indexOf2S4", Object.class, 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_3_S4 = foreignCallDescriptor("indexOf3S4", Object.class, 3);
    public static final ForeignCallDescriptor STUB_INDEX_OF_4_S4 = foreignCallDescriptor("indexOf4S4", Object.class, 4);

    public static final ForeignCallDescriptor STUB_INDEX_OF_WITH_MASK_B_S1 = foreignCallDescriptor("indexOfWithMaskBS1", byte[].class, 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_WITH_MASK_B_S2 = foreignCallDescriptor("indexOfWithMaskBS2", byte[].class, 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_WITH_MASK_C_S2 = foreignCallDescriptor("indexOfWithMaskCS2", char[].class, 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_B_S1 = foreignCallDescriptor("indexOfTwoConsecutiveWithMaskBS1", byte[].class, 4);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_B_S2 = foreignCallDescriptor("indexOfTwoConsecutiveWithMaskBS2", byte[].class, 4);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_C_S2 = foreignCallDescriptor("indexOfTwoConsecutiveWithMaskCS2", char[].class, 4);
    public static final ForeignCallDescriptor STUB_INDEX_OF_WITH_MASK_S1 = foreignCallDescriptor("indexOfWithMaskS1", Object.class, 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_WITH_MASK_S2 = foreignCallDescriptor("indexOfWithMaskS2", Object.class, 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_WITH_MASK_S4 = foreignCallDescriptor("indexOfWithMaskS4", Object.class, 2);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S1 = foreignCallDescriptor("indexOfTwoConsecutiveWithMaskS1", Object.class, 4);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S2 = foreignCallDescriptor("indexOfTwoConsecutiveWithMaskS2", Object.class, 4);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S4 = foreignCallDescriptor("indexOfTwoConsecutiveWithMaskS4", Object.class, 4);

    private static final ForeignCallDescriptor[] STUBS_INDEX_OF_ANY_B = new ForeignCallDescriptor[]{
                    STUB_INDEX_OF_B_1_S1,
                    STUB_INDEX_OF_B_2_S1,
                    STUB_INDEX_OF_B_3_S1,
                    STUB_INDEX_OF_B_4_S1,
                    STUB_INDEX_OF_B_1_S2,
                    STUB_INDEX_OF_B_2_S2,
                    STUB_INDEX_OF_B_3_S2,
                    STUB_INDEX_OF_B_4_S2,
    };

    private static final ForeignCallDescriptor[] STUBS_INDEX_OF_ANY_C = new ForeignCallDescriptor[]{
                    STUB_INDEX_OF_C_1_S2,
                    STUB_INDEX_OF_C_2_S2,
                    STUB_INDEX_OF_C_3_S2,
                    STUB_INDEX_OF_C_4_S2,
    };

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

    public static final ForeignCallDescriptor[] STUBS_AMD64 = Stream.concat(Stream.concat(Stream.concat(Stream.of(
                    STUB_INDEX_OF_TWO_CONSECUTIVE_B_S1,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_B_S2,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_C_S2,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_S1,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_S2,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_S4,
                    STUB_INDEX_OF_WITH_MASK_B_S1,
                    STUB_INDEX_OF_WITH_MASK_B_S2,
                    STUB_INDEX_OF_WITH_MASK_C_S2,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_B_S1,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_B_S2,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_C_S2,
                    STUB_INDEX_OF_WITH_MASK_S1,
                    STUB_INDEX_OF_WITH_MASK_S2,
                    STUB_INDEX_OF_WITH_MASK_S4,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S1,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S2,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S4),
                    Arrays.stream(STUBS_INDEX_OF_ANY_B)),
                    Arrays.stream(STUBS_INDEX_OF_ANY_C)),
                    Arrays.stream(STUBS_INDEX_OF_ANY)).toArray(ForeignCallDescriptor[]::new);

    public static final ForeignCallDescriptor[] STUBS_AARCH64 = {
                    STUB_INDEX_OF_B_1_S1,
                    STUB_INDEX_OF_B_1_S2,
                    STUB_INDEX_OF_C_1_S2,
                    STUB_INDEX_OF_1_S1,
                    STUB_INDEX_OF_1_S2,
                    STUB_INDEX_OF_1_S4,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_B_S1,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_B_S2,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_C_S2,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_S1,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_S2,
                    STUB_INDEX_OF_TWO_CONSECUTIVE_S4,
    };

    public static ForeignCallDescriptor getStub(ArrayIndexOfNode indexOfNode) {
        return getStub(indexOfNode.getArrayKind(), indexOfNode.getStride(), indexOfNode.getNumberOfValues(), indexOfNode.isFindTwoConsecutive(), indexOfNode.isWithMask());
    }

    public static ForeignCallDescriptor getStub(JavaKind arrayKind, JavaKind stride, int valueCount, boolean findTwoConsecutive, boolean withMask) {
        GraalError.guarantee(arrayKind == S1 || arrayKind == S2 || arrayKind == NONE, "unsupported arrayKind");
        GraalError.guarantee(stride == S1 || stride == S2 || stride == S4, "unsupported stride");
        GraalError.guarantee(valueCount >= 1 && valueCount <= 4, "unsupported valueCount");
        if (withMask) {
            if (findTwoConsecutive) {
                GraalError.guarantee(valueCount == 4, "findTwoConsecutive with mask requires 4 values");
                switch (arrayKind) {
                    case Byte:
                        switch (stride) {
                            case Byte:
                                return STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_B_S1;
                            case Char:
                                return STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_B_S2;
                            default:
                                throw GraalError.shouldNotReachHere();
                        }
                    case Char:
                        GraalError.guarantee(stride == JavaKind.Char, "unsupported stride for char array");
                        return STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_C_S2;
                    case Void:
                        switch (stride) {
                            case Byte:
                                return STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S1;
                            case Char:
                                return STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S2;
                            case Int:
                                return STUB_INDEX_OF_TWO_CONSECUTIVE_WITH_MASK_S4;
                            default:
                                throw GraalError.shouldNotReachHere();
                        }
                    default:
                        throw GraalError.shouldNotReachHere();
                }
            } else {
                GraalError.guarantee(valueCount == 2, "indexOf with mask requires 2 values");
                switch (arrayKind) {
                    case Byte:
                        assert stride == S1 || stride == S2;
                        switch (stride) {
                            case Byte:
                                return STUB_INDEX_OF_WITH_MASK_B_S1;
                            case Char:
                                return STUB_INDEX_OF_WITH_MASK_B_S2;
                            default:
                                throw GraalError.shouldNotReachHere();
                        }
                    case Char:
                        GraalError.guarantee(stride == JavaKind.Char, "unsupported stride for char array");
                        return STUB_INDEX_OF_WITH_MASK_C_S2;
                    case Void:
                        switch (stride) {
                            case Byte:
                                return STUB_INDEX_OF_WITH_MASK_S1;
                            case Char:
                                return STUB_INDEX_OF_WITH_MASK_S2;
                            case Int:
                                return STUB_INDEX_OF_WITH_MASK_S4;
                            default:
                                throw GraalError.shouldNotReachHere();
                        }
                    default:
                        throw GraalError.shouldNotReachHere();
                }
            }
        } else if (findTwoConsecutive) {
            GraalError.guarantee(valueCount == 2, "findTwoConsecutive without mask requires 2 values");
            switch (arrayKind) {
                case Byte:
                    switch (stride) {
                        case Byte:
                            return STUB_INDEX_OF_TWO_CONSECUTIVE_B_S1;
                        case Char:
                            return STUB_INDEX_OF_TWO_CONSECUTIVE_B_S2;
                        default:
                            throw GraalError.shouldNotReachHere();
                    }
                case Char:
                    GraalError.guarantee(stride == JavaKind.Char, "unsupported stride for char array");
                    return STUB_INDEX_OF_TWO_CONSECUTIVE_C_S2;
                case Void:
                    switch (stride) {
                        case Byte:
                            return STUB_INDEX_OF_TWO_CONSECUTIVE_S1;
                        case Char:
                            return STUB_INDEX_OF_TWO_CONSECUTIVE_S2;
                        case Int:
                            return STUB_INDEX_OF_TWO_CONSECUTIVE_S4;
                        default:
                            throw GraalError.shouldNotReachHere();
                    }
                default:
                    throw GraalError.shouldNotReachHere();
            }
        } else {
            int index = (4 * ForeignCalls.strideAsPowerOf2(stride)) + (valueCount - 1);
            switch (arrayKind) {
                case Byte:
                    return STUBS_INDEX_OF_ANY_B[index];
                case Char:
                    return STUBS_INDEX_OF_ANY_C[valueCount - 1];
                case Void:
                    return STUBS_INDEX_OF_ANY[index];
                default:
                    throw GraalError.shouldNotReachHere();
            }
        }
    }
}
