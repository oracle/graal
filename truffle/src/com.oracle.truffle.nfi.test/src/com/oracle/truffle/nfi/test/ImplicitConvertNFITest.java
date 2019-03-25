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
import static org.hamcrest.core.Is.is;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;
import com.oracle.truffle.nfi.test.interop.TestCallback;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public class ImplicitConvertNFITest extends NFITest {

    private static final Object[] NUMERIC_VALUES = {
                    false, true, (byte) 42, (short) 42, (char) 42, 42, (long) 42, (float) 42, (double) 42
    };

    @Parameters(name = "{0}, ({3}) {1}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        for (NativeSimpleType type : NumericNFITest.NUMERIC_TYPES) {
            for (Object value : NUMERIC_VALUES) {
                long numericValue = 0;
                if (value instanceof Number) {
                    numericValue = ((Number) value).longValue();
                } else if (value instanceof Character) {
                    numericValue = (Character) value;
                } else if (value instanceof Boolean) {
                    numericValue = (Boolean) value ? 1 : 0;
                }
                ret.add(new Object[]{type, value, numericValue, value.getClass()});
            }
        }
        return ret;
    }

    @Parameter(0) public NativeSimpleType type;
    @Parameter(1) public Object value;
    @Parameter(2) public long numericValue;
    @Parameter(3) public Class<?> valueClass;

    private final TruffleObject callback = new TestCallback(1, (args) -> {
        Assert.assertEquals("callback argument", numericValue + 1, NumericNFITest.unboxNumber(args[0]));
        return value;
    });

    /**
     * Test implicit conversion between different numeric types when used as argument to native
     * functions or return type from callback.
     */
    public class TestConvertNode extends SendExecuteNode {

        public TestConvertNode() {
            super("callback_" + type, String.format("((%s):%s, %s) : %s", type, type, type, type));
        }
    }

    @Test
    public void testConvert(@Inject(TestConvertNode.class) CallTarget callTarget) {
        Assume.assumeFalse(isCompileImmediately());
        Object ret = callTarget.call(callback, value);

        if (type == NativeSimpleType.POINTER) {
            Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));
            TruffleObject obj = (TruffleObject) ret;
            Assert.assertTrue("isNumber", UNCACHED_INTEROP.isNumber(obj));
        } else {
            Assert.assertThat("return value", ret, is(instanceOf(Number.class)));
        }

        long retValue = NumericNFITest.unboxNumber(ret);
        Assert.assertEquals("callback return", numericValue * 2, retValue);
    }

    private static boolean isCompileImmediately() {
        CallTarget target = Truffle.getRuntime().createCallTarget(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return CompilerDirectives.inCompiledCode();
            }
        });
        return (boolean) target.call();
    }

}
