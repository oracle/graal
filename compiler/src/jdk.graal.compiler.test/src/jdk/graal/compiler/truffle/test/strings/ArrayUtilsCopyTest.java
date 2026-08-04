/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.ArrayUtils;

import jdk.graal.compiler.replacements.nodes.ArrayCopyWithConversionsSingleKillNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ArrayUtilsCopyTest extends TStringOpsTest<ArrayCopyWithConversionsSingleKillNode> {

    protected static final int ARRAY_LENGTH = 128;

    protected int length;

    public ArrayUtilsCopyTest() {
        super(ArrayCopyWithConversionsSingleKillNode.class);
    }

    protected void setTestCase(Object[] args) {
        this.length = (int) args[3];
    }

    public static List<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        int[] source = initializedSource();
        for (int sourceIndex : new int[]{0, 1, 7, 15, 64}) {
            for (int destinationIndex : new int[]{0, 3, 9, 64}) {
                for (int length : new int[]{0, 1, 2, 3, 4, 5, 7, 8, 9, 15, 16, 17, 31, 32, 33, 63, 64}) {
                    if (sourceIndex + length <= source.length && destinationIndex + length <= ARRAY_LENGTH) {
                        ret.add(new Object[]{source, sourceIndex, destinationIndex, length});
                    }
                }
            }
        }
        return ret;
    }

    protected static int[] initializedSource() {
        int[] source = new int[ARRAY_LENGTH];
        for (int i = 0; i < source.length; i++) {
            source[i] = 0x12340000 + i;
        }
        return source;
    }

    protected ResolvedJavaMethod getArrayCopyS4() {
        return getResolvedJavaMethod(ArrayUtils.class, "arraycopyS4", int[].class, int.class, int[].class, int.class, int.class);
    }

    @Test
    public void testCopy() {
        testParameterized(data(), this::testCopyCase);
    }

    private void testCopyCase(Object[] args) {
        setTestCase(args);
        ArgSupplier destination = ArrayUtilsCopyTest::initializedDestination;
        test(getArrayCopyS4(), null, args[0], args[1], destination, args[2], args[3]);
    }

    protected static int[] initializedDestination() {
        int[] destination = new int[ARRAY_LENGTH];
        for (int i = 0; i < destination.length; i++) {
            destination[i] = 0x56780000 + i;
        }
        return destination;
    }
}
