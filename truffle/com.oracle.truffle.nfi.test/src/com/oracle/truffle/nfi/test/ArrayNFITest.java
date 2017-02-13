/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.types.NativeSimpleType;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArrayNFITest extends NFITest {

    @Parameters(name = "{0}, {1}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        ret.add(new Object[]{NativeSimpleType.UINT8, boolean.class});
        ret.add(new Object[]{NativeSimpleType.UINT8, byte.class});
        ret.add(new Object[]{NativeSimpleType.SINT8, boolean.class});
        ret.add(new Object[]{NativeSimpleType.SINT8, byte.class});
        ret.add(new Object[]{NativeSimpleType.UINT16, short.class});
        ret.add(new Object[]{NativeSimpleType.UINT16, char.class});
        ret.add(new Object[]{NativeSimpleType.SINT16, short.class});
        ret.add(new Object[]{NativeSimpleType.SINT16, char.class});
        ret.add(new Object[]{NativeSimpleType.UINT32, int.class});
        ret.add(new Object[]{NativeSimpleType.SINT32, int.class});
        ret.add(new Object[]{NativeSimpleType.UINT64, long.class});
        ret.add(new Object[]{NativeSimpleType.SINT64, long.class});
        ret.add(new Object[]{NativeSimpleType.FLOAT, float.class});
        ret.add(new Object[]{NativeSimpleType.DOUBLE, double.class});
        return ret;
    }

    private static class CreateAndSumArray extends TestRootNode {

        private final Class<?> javaType;

        private final TruffleObject store;
        private final TruffleObject sum;

        @Child Node executeStore = Message.createExecute(3).createNode();
        @Child Node executeSum = Message.createExecute(2).createNode();

        CreateAndSumArray(NativeSimpleType nativeType, Class<?> javaType) {
            this.javaType = javaType;
            this.store = lookupAndBind("store_" + nativeType, String.format("([%s], uint32, %s) : void", nativeType, nativeType));
            this.sum = lookupAndBind("sum_" + nativeType, String.format("([%s], uint32) : %s", nativeType, nativeType));
        }

        @TruffleBoundary
        private void verifyArray(Object array) {
            int length = Array.getLength(array);
            for (int i = 0; i < length; i++) {
                Object elem = Array.get(array, i);
                Assert.assertThat("array element", elem, is(instanceOf(javaType)));
                long actual = 0;
                long expected = i + 1;
                if (elem instanceof Number) {
                    actual = ((Number) elem).longValue();
                } else if (elem instanceof Character) {
                    actual = (Character) elem;
                } else if (elem instanceof Boolean) {
                    actual = (Boolean) elem ? 1 : 0;
                    expected &= 1; // only one bit available
                }
                Assert.assertEquals("array element", expected, actual);
            }
        }

        @Override
        public Object executeTest(VirtualFrame frame) {
            int arrayLength = (Integer) frame.getArguments()[0];

            Object array = Array.newInstance(javaType, arrayLength);
            TruffleObject wrappedArray = JavaInterop.asTruffleObject(array);

            try {
                for (int i = 0; i < arrayLength; i++) {
                    ForeignAccess.sendExecute(executeStore, store, wrappedArray, i, i + 1);
                }
                verifyArray(array);
                return ForeignAccess.sendExecute(executeSum, sum, wrappedArray, arrayLength);
            } catch (InteropException ex) {
                throw new AssertionError(ex);
            }
        }
    }

    private final CreateAndSumArray createAndSum;

    public ArrayNFITest(NativeSimpleType nativeType, Class<?> javaType) {
        createAndSum = new CreateAndSumArray(nativeType, javaType);
    }

    @Test
    public void testSumArray() {
        int arrayLength = 5;
        Object ret = run(createAndSum, arrayLength);
        Assert.assertThat("return value", ret, is(instanceOf(Number.class)));
        Assert.assertEquals("return value", arrayLength * (arrayLength + 1) / 2, ((Number) ret).intValue());
    }
}
