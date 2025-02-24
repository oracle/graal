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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import jdk.graal.compiler.replacements.nodes.CalcStringAttributesNode;

@RunWith(Parameterized.class)
public class TStringOpsCalcStringAttributesLatin1Test extends TStringOpsTest<CalcStringAttributesNode> {

    @Parameters(name = "{index}: args: {1}, {2}")
    public static List<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        int offset = 20;
        int padding = 20;
        for (int length : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 47, 48, 49, 63, 64, 65, 127, 128, 129}) {
            byte[] arr = new byte[offset + length + padding];
            for (int i = 0; i < offset; i++) {
                arr[i] = (byte) 0xff;
            }
            for (int i = offset; i < offset + length; i++) {
                arr[i] = (byte) (i % 0x7f);
            }
            for (int i = arr.length - padding; i < arr.length; i++) {
                arr[i] = (byte) 0xff;
            }
            byte[] arr2 = Arrays.copyOf(arr, arr.length);
            arr2[offset] = (byte) 0x80;
            byte[] arr3 = Arrays.copyOf(arr, arr.length);
            arr3[offset + length - 1] = (byte) 0x80;
            ret.add(new Object[]{arr, offset, length});
            ret.add(new Object[]{arr2, offset, length});
            ret.add(new Object[]{arr3, offset, length});
        }
        return ret;
    }

    final byte[] array;
    final long offset;
    final int length;

    public TStringOpsCalcStringAttributesLatin1Test(byte[] array, int offset, int length) {
        super(CalcStringAttributesNode.class);
        this.array = array;
        this.offset = offset + byteArrayBaseOffset();
        this.length = length;
    }

    @Test
    public void testLatin1() {
        testWithNative(getTStringOpsMethod("calcStringAttributesLatin1", byte[].class, long.class, int.class), null, DUMMY_LOCATION, array, offset, length);
    }
}
