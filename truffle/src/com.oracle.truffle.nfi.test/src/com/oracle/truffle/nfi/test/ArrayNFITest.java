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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.types.NativeSimpleType;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
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

    @Parameter(0) public NativeSimpleType nativeType;
    @Parameter(1) public Class<?> javaType;

    public class CreateAndSumArray extends NFITestRootNode {

        private final Class<?> finalJavaType;

        private final TruffleObject store;
        private final TruffleObject sum;

        @Child Node executeStore = Message.EXECUTE.createNode();
        @Child Node executeSum = Message.EXECUTE.createNode();

        public CreateAndSumArray() {
            this.finalJavaType = javaType;
            this.store = lookupAndBind("store_" + nativeType, String.format("([%s], uint32, %s) : void", nativeType, nativeType));
            this.sum = lookupAndBind("sum_" + nativeType, String.format("([%s], uint32) : %s", nativeType, nativeType));
        }

        @TruffleBoundary
        private void verifyArray(Object array) {
            int length = Array.getLength(array);
            for (int i = 0; i < length; i++) {
                Object elem = Array.get(array, i);
                Assert.assertThat("array element", elem, is(instanceOf(finalJavaType)));
                long actual = 0;
                long expected = i + 1;
                if (elem instanceof Number) {
                    actual = ((Number) elem).longValue();
                } else if (elem instanceof Character) {
                    actual = (Character) elem;
                } else if (elem instanceof Boolean) {
                    /*
                     * The conversion from native byte to Java boolean is undefined and may be
                     * different on different VM versions.
                     */
                    return;
                }
                Assert.assertEquals("array element", expected, actual);
            }
        }

        @Override
        public Object executeTest(VirtualFrame frame) {
            int arrayLength = (Integer) frame.getArguments()[0];

            Object array = Array.newInstance(finalJavaType, arrayLength);
            Object wrappedArray = runWithPolyglot.getTruffleTestEnv().asGuestValue(array);

            try {
                for (int i = 0; i < arrayLength; i++) {
                    ForeignAccess.sendExecute(executeStore, store, wrappedArray, i, i + 1);
                }
                verifyArray(array);
                return ForeignAccess.sendExecute(executeSum, sum, wrappedArray, arrayLength);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(ex);
            }
        }
    }

    @Test
    public void testSumArray(@Inject(CreateAndSumArray.class) CallTarget callTarget) {
        int arrayLength = 5;
        Object ret = callTarget.call(arrayLength);
        Assert.assertThat("return value", ret, is(instanceOf(Number.class)));
        Assert.assertEquals("return value", arrayLength * (arrayLength + 1) / 2, ((Number) ret).intValue());
    }
}
