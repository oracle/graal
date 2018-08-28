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
import static org.hamcrest.core.Is.is;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.nfi.test.interop.TestCallback;
import com.oracle.truffle.nfi.types.NativeSimpleType;
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

    private Object callback(Object... args) {
        Assert.assertEquals("callback argument", numericValue + 1, NumericNFITest.unboxNumber(args[0]));
        return value;
    }

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
        TruffleObject callback = new TestCallback(1, this::callback);
        Object ret = callTarget.call(callback, value);

        if (type == NativeSimpleType.POINTER) {
            Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));
            TruffleObject obj = (TruffleObject) ret;
            Assert.assertTrue("isBoxed", isBoxed(obj));
            ret = unbox(obj);
            Assert.assertThat("unboxed return value", ret, is(instanceOf(Long.class)));
        } else {
            Assert.assertThat("return value", ret, is(instanceOf(Number.class)));
        }

        long retValue = ((Number) ret).longValue();
        Assert.assertEquals("callback return", numericValue * 2, retValue);
    }
}
