/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;
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

        @Child InteropLibrary storeInterop;
        @Child InteropLibrary sumInterop;

        public CreateAndSumArray() {
            this.finalJavaType = javaType;
            this.store = lookupAndBind("store_" + nativeType, String.format("([%s], uint32, %s) : void", nativeType, nativeType));
            this.sum = lookupAndBind("sum_" + nativeType, String.format("([%s], uint32) : %s", nativeType, nativeType));

            this.storeInterop = getInterop(this.store);
            this.sumInterop = getInterop(this.sum);
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
                    storeInterop.execute(store, wrappedArray, i, i + 1);
                }
                verifyArray(array);
                return sumInterop.execute(sum, wrappedArray, arrayLength);
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
