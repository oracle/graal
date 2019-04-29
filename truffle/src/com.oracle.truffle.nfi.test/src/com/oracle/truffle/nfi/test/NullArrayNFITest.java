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

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;
import com.oracle.truffle.nfi.test.interop.NullObject;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public class NullArrayNFITest extends NFITest {

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        ret.add(new Object[]{NativeSimpleType.UINT8});
        ret.add(new Object[]{NativeSimpleType.SINT8});
        ret.add(new Object[]{NativeSimpleType.UINT16});
        ret.add(new Object[]{NativeSimpleType.SINT16});
        ret.add(new Object[]{NativeSimpleType.UINT32});
        ret.add(new Object[]{NativeSimpleType.SINT32});
        ret.add(new Object[]{NativeSimpleType.UINT64});
        ret.add(new Object[]{NativeSimpleType.SINT64});
        ret.add(new Object[]{NativeSimpleType.FLOAT});
        ret.add(new Object[]{NativeSimpleType.DOUBLE});
        return ret;
    }

    @Parameter public NativeSimpleType nativeType;

    public class NullArrayNode extends SendExecuteNode {

        public NullArrayNode() {
            super("null_array_" + nativeType, String.format("([%s]):string", nativeType));
        }
    }

    @Test
    public void testNullArray(@Inject(NullArrayNode.class) CallTarget target) throws UnsupportedMessageException {
        Object ret = target.call(new NullObject());

        Assert.assertTrue("isBoxed", UNCACHED_INTEROP.isString(ret));
        Assert.assertEquals("return value", "null", UNCACHED_INTEROP.asString(ret));
    }

    @Test
    public void testHostNullArray(@Inject(NullArrayNode.class) CallTarget target) throws UnsupportedMessageException {
        Object hostNull = runWithPolyglot.getTruffleTestEnv().asGuestValue(null);
        Object ret = target.call(hostNull);

        Assert.assertTrue("isBoxed", UNCACHED_INTEROP.isString(ret));
        Assert.assertEquals("return value", "null", UNCACHED_INTEROP.asString(ret));
    }
}
