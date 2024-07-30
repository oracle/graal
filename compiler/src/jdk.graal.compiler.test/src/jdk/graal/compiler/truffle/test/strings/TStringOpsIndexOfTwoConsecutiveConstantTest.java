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

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import jdk.graal.compiler.replacements.nodes.ArrayIndexOfNode;

@RunWith(Parameterized.class)
public class TStringOpsIndexOfTwoConsecutiveConstantTest extends TStringOpsIndexOfConstantTest<ArrayIndexOfNode> {

    public TStringOpsIndexOfTwoConsecutiveConstantTest(byte[] arrayA, int offsetA, int lengthA, int strideA, int fromIndexA, int v0, int v1, int mask0, int mask1) {
        super(ArrayIndexOfNode.class, arrayA, offsetA, lengthA, strideA, fromIndexA, new int[]{v0, v1, mask0, mask1});
    }

    @Parameters(name = "{index}: offset: {1}, length: {2}, stride: {3}, fromIndex: {4}, toIndex: {5}")
    public static List<Object[]> data() {
        return reduceTestData(reduceTestData(TStringOpsIndexOfTwoConsecutiveTest.data(), 2, 1, 7, 16), 4, 0, 1);
    }

    @Test
    public void testIndexOfTwoConsecutive() {
        setConstantArgs(DUMMY_LOCATION, arrayA, offsetA, lengthA, stride, fromIndex, values[0], values[1]);
        test(getIndexOf2ConsecutiveWithStrideIntl(), null, DUMMY_LOCATION, arrayA, offsetA, lengthA, stride, fromIndex, values[0], values[1]);
    }
}
