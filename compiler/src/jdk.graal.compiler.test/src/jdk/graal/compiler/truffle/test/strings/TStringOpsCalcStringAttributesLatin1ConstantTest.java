/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.replacements.nodes.CalcStringAttributesNode;

@RunWith(Parameterized.class)
public class TStringOpsCalcStringAttributesLatin1ConstantTest extends TStringOpsConstantTest<CalcStringAttributesNode> {

    @Parameters(name = "{index}: args: {1}, {2}")
    public static List<Object[]> data() {
        return reduceTestData(TStringOpsCalcStringAttributesLatin1Test.data(), 2, 1, 7, 16);
    }

    public TStringOpsCalcStringAttributesLatin1ConstantTest(byte[] array, int offset, int length) {
        super(CalcStringAttributesNode.class, array, offset, length);
    }

    @Test
    public void testLatin1() {
        setConstantArgs(DUMMY_LOCATION, arrayA, offsetA, lengthA);
        test(getTStringOpsMethod("calcStringAttributesLatin1", byte[].class, long.class, int.class), null, DUMMY_LOCATION, arrayA, offsetA, lengthA);
    }
}
