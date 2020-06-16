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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.nfi.test.interop.NullObject;
import com.oracle.truffle.nfi.test.interop.TestCallback;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public class WrappedPrimitiveNFITest extends NFITest {

    private static class TestObject implements TruffleObject {
    }

    private static final Object[] ARGUMENTS = {
                    false, (byte) 42, (short) 42, (char) 42, 42, (long) 42, (float) 42, (double) 42, //
                    8472, Integer.MAX_VALUE, Integer.MIN_VALUE, //
                    "Hello, World!", new TestObject(), new NullObject()
    };

    @Parameters(name = "{1}")
    public static List<Object[]> getParameters() {
        ArrayList<Object[]> ret = new ArrayList<>(ARGUMENTS.length);
        for (Object arg : ARGUMENTS) {
            ret.add(new Object[]{arg, arg.getClass().getSimpleName()});
        }
        return ret;
    }

    @Parameter(0) public Object argument;
    @Parameter(1) public String argumentType;

    private final TestCallback getObject = new TestCallback(0, (args) -> {
        return argument;
    });

    private final TestCallback verifyObject = new TestCallback(2, (args) -> {
        Assert.assertSame("arg 1", argument, args[0]);
        Assert.assertSame("arg 2", argument, args[1]);
        return argument;
    });

    public static class PassObjectNode extends SendExecuteNode {

        public PassObjectNode() {
            super("pass_object", "(object, ():object, (object, object):object) : object");
        }
    }

    @Test
    public void passObjectTest(@Inject(PassObjectNode.class) CallTarget target) {
        Object ret = target.call(argument, getObject, verifyObject);
        if (argument instanceof TruffleObject) {
            Assert.assertSame("return value", argument, ret);
        } else {
            // everything else is considered a value type by Truffle, so identity can be lost
            Assert.assertEquals("return value", argument, ret);
        }
    }
}
