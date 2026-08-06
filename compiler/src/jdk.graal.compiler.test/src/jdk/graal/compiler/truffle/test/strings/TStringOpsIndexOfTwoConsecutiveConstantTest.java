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

import jdk.graal.compiler.replacements.nodes.ArrayIndexOfNode;

public class TStringOpsIndexOfTwoConsecutiveConstantTest extends TStringOpsIndexOfConstantTest<ArrayIndexOfNode> {

    public TStringOpsIndexOfTwoConsecutiveConstantTest() {
        super(ArrayIndexOfNode.class);
    }

    public static List<Object[]> data() {
        return reduceTestData(reduceTestData(TStringOpsIndexOfTwoConsecutiveTest.data(), 2, 1, 7, 16), 4, 0, 1);
    }

    @Test
    public void testIndexOfTwoConsecutive() {
        testParameterized(data(), this::testIndexOfTwoConsecutiveCase);
    }

    private void testIndexOfTwoConsecutiveCase(Object[] args) {
        setTestCase(new Object[]{args[0], args[1], args[2], args[3], args[4], new int[]{(int) args[5], (int) args[6], (int) args[7], (int) args[8]}});
        setConstantArgs(DUMMY_LOCATION, arrayA, offsetA, lengthA, stride, fromIndex, values[0], values[1]);
        test(getIndexOf2ConsecutiveWithStrideIntl(), null, DUMMY_LOCATION, arrayA, offsetA, lengthA, stride, fromIndex, values[0], values[1]);
    }
}
