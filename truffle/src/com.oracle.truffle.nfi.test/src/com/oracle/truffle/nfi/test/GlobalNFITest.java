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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.nfi.test.interop.TestCallback;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.DoubleFunction;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public class GlobalNFITest extends NFITest {

    static TruffleObject registerGlobalCallback;
    static TruffleObject testGlobalCallback;

    @BeforeClass
    public static void initContext() {
        registerGlobalCallback = lookupAndBind("registerGlobalCallback", "((double):double):object");
        testGlobalCallback = lookupAndBind("testGlobalCallback", "(double):double");
        TruffleObject initializeGlobalContext = lookupAndBind("initializeGlobalContext", "(env):void");
        try {
            UNCACHED_INTEROP.execute(initializeGlobalContext);
        } catch (InteropException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void register(ArrayList<Object[]> ret, String name, DoubleFunction<?> op) {
        ret.add(new Object[]{name, op, new TestCallback(1, x -> op.apply((double) x[0]))});
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        register(ret, "sqrt", Math::sqrt);
        register(ret, "timesTwo", x -> 2.0 * x);
        register(ret, "half", x -> x / 2.0);
        return ret;
    }

    @Parameter(0) public String name;
    @Parameter(1) public DoubleFunction<?> op;
    @Parameter(2) public TestCallback callback;

    public class TestGlobalMethod extends NFITestRootNode {

        private final TruffleObject register = registerGlobalCallback;
        private final TruffleObject test = testGlobalCallback;

        @Child InteropLibrary registerInterop = getInterop(register);
        @Child InteropLibrary testInterop = getInterop(test);

        Object handle; // to keep the native callback alive

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            handle = registerInterop.execute(register, callback);
            return testInterop.execute(test, frame.getArguments()[0]);
        }
    }

    @Test
    public void test(@Inject(TestGlobalMethod.class) CallTarget callTarget) {
        Object ret = callTarget.call(42.0);
        Assert.assertThat("return value", ret, is(instanceOf(Double.class)));
        Assert.assertEquals("return value", op.apply(42.0), ret);
    }
}
