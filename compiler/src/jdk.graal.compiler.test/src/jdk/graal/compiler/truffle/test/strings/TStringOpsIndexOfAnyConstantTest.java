/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test.strings;

import static org.junit.runners.Parameterized.Parameters;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import jdk.graal.compiler.replacements.nodes.ArrayIndexOfNode;

@RunWith(Parameterized.class)
public class TStringOpsIndexOfAnyConstantTest extends TStringOpsIndexOfConstantTest<ArrayIndexOfNode> {

    public TStringOpsIndexOfAnyConstantTest(byte[] array, int offset, int length, int stride, int fromIndex, int[] values) {
        super(ArrayIndexOfNode.class, array, offset, length, stride, fromIndex, values);
    }

    @Parameters(name = "{index}: offset: {1}, length: {2}, stride: {3}, fromIndex: {4}, toIndex: {5}")
    public static List<Object[]> data() {
        return reduceTestData(reduceTestData(TStringOpsIndexOfAnyTest.data(), 2, 0, 1, 7, 16), 4, 0, 1);
    }

    @Test
    public void testIndexOfAny() {
        constantArgs = new Object[7];
        constantArgs[0] = DUMMY_LOCATION;
        constantArgs[1] = arrayA;
        constantArgs[2] = offsetA;
        constantArgs[3] = lengthA;
        if (stride == 0) {
            byte[] valuesB = new byte[values.length];
            for (int i = 0; i < values.length; i++) {
                valuesB[i] = (byte) values[i];
            }
            constantArgs[4] = fromIndex;
            constantArgs[5] = valuesB;
            test(getIndexOfAnyByteIntl(), null, DUMMY_LOCATION, arrayA, offsetA, lengthA, fromIndex, valuesB);
        }
        if (stride < 2) {
            char[] valuesC = new char[values.length];
            for (int i = 0; i < values.length; i++) {
                valuesC[i] = (char) (stride == 0 ? values[i] & 0xff : values[i]);
            }
            constantArgs[4] = stride;
            constantArgs[5] = fromIndex;
            constantArgs[6] = valuesC;
            test(getIndexOfAnyCharIntl(), null, DUMMY_LOCATION, arrayA, offsetA, lengthA, stride, fromIndex, valuesC);
        }
        int[] valuesI = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            valuesI[i] = stride == 0 ? values[i] & 0xff : stride == 1 ? values[i] & 0xffff : values[i];
        }
        constantArgs[4] = stride;
        constantArgs[5] = fromIndex;
        constantArgs[6] = valuesI;
        test(getIndexOfAnyIntIntl(), null, DUMMY_LOCATION, arrayA, offsetA, lengthA, stride, fromIndex, valuesI);
    }
}
