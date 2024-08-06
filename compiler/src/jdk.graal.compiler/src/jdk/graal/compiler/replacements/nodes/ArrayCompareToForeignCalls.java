/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.debug.GraalError;
import org.graalvm.word.Pointer;

public final class ArrayCompareToForeignCalls {
    private static final ForeignCallDescriptor STUB_BYTE_ARRAY_COMPARE_TO_BYTE_ARRAY = foreignCallDescriptor("byteArrayCompareToByteArray");
    private static final ForeignCallDescriptor STUB_BYTE_ARRAY_COMPARE_TO_CHAR_ARRAY = foreignCallDescriptor("byteArrayCompareToCharArray");
    private static final ForeignCallDescriptor STUB_CHAR_ARRAY_COMPARE_TO_BYTE_ARRAY = foreignCallDescriptor("charArrayCompareToByteArray");
    private static final ForeignCallDescriptor STUB_CHAR_ARRAY_COMPARE_TO_CHAR_ARRAY = foreignCallDescriptor("charArrayCompareToCharArray");

    public static final ForeignCallDescriptor[] STUBS = {
                    STUB_BYTE_ARRAY_COMPARE_TO_BYTE_ARRAY,
                    STUB_BYTE_ARRAY_COMPARE_TO_CHAR_ARRAY,
                    STUB_CHAR_ARRAY_COMPARE_TO_BYTE_ARRAY,
                    STUB_CHAR_ARRAY_COMPARE_TO_CHAR_ARRAY};

    private static ForeignCallDescriptor foreignCallDescriptor(String name) {
        return ForeignCalls.pureFunctionForeignCallDescriptor(name, int.class, Pointer.class, int.class, Pointer.class, int.class);
    }

    public static ForeignCallDescriptor getStub(ArrayCompareToNode arrayCompareToNode) {
        Stride strideA = arrayCompareToNode.getStrideA();
        Stride strideB = arrayCompareToNode.getStrideB();
        if (strideA == Stride.S1) {
            if (strideB == Stride.S1) {
                return STUB_BYTE_ARRAY_COMPARE_TO_BYTE_ARRAY;
            } else if (strideB == Stride.S2) {
                return STUB_BYTE_ARRAY_COMPARE_TO_CHAR_ARRAY;
            }
        } else if (strideA == Stride.S2) {
            if (strideB == Stride.S1) {
                return STUB_CHAR_ARRAY_COMPARE_TO_BYTE_ARRAY;
            } else if (strideB == Stride.S2) {
                return STUB_CHAR_ARRAY_COMPARE_TO_CHAR_ARRAY;
            }
        }
        throw GraalError.shouldNotReachHere(strideA + " " + strideB); // ExcludeFromJacocoGeneratedReport
    }
}
