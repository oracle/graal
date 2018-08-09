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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
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
            ForeignAccess.sendExecute(Message.EXECUTE.createNode(), initializeGlobalContext);
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

        @Child Node executeRegister = Message.EXECUTE.createNode();
        @Child Node executeTest = Message.EXECUTE.createNode();

        TruffleObject handle; // to keep the native callback alive

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            handle = (TruffleObject) ForeignAccess.sendExecute(executeRegister, register, callback);
            return ForeignAccess.sendExecute(executeTest, test, frame.getArguments()[0]);
        }
    }

    @Test
    public void test(@Inject(TestGlobalMethod.class) CallTarget callTarget) {
        Object ret = callTarget.call(42.0);
        Assert.assertThat("return value", ret, is(instanceOf(Double.class)));
        Assert.assertEquals("return value", op.apply(42.0), ret);
    }
}
