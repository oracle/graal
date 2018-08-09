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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.test.interop.BoxedPrimitive;
import com.oracle.truffle.nfi.test.interop.TestCallback;
import com.oracle.truffle.nfi.types.NativeSimpleType;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public class NumericNFITest extends NFITest {

    public static final NativeSimpleType[] NUMERIC_TYPES = {
                    NativeSimpleType.UINT8, NativeSimpleType.SINT8,
                    NativeSimpleType.UINT16, NativeSimpleType.SINT16,
                    NativeSimpleType.UINT32, NativeSimpleType.SINT32,
                    NativeSimpleType.UINT64, NativeSimpleType.SINT64,
                    NativeSimpleType.FLOAT, NativeSimpleType.DOUBLE
    };

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        for (NativeSimpleType type : NUMERIC_TYPES) {
            ret.add(new Object[]{type});
        }
        return ret;
    }

    @Parameter(0) public NativeSimpleType type;

    private void checkExpectedRet(long expected, Object arg) {
        checkExpected("return", expected, arg);
    }

    private void checkExpectedArg(long expected, Object arg) {
        checkExpected("argument", expected, arg);
    }

    static long unboxNumber(Object arg) {
        Object value = arg;
        while (value instanceof TruffleObject) {
            TruffleObject obj = (TruffleObject) value;
            Assert.assertTrue("isBoxed", isBoxed(obj));
            value = unbox(obj);
        }
        Assert.assertThat(value, is(instanceOf(Number.class)));
        return ((Number) value).longValue();
    }

    private void checkExpected(String thing, long expected, Object arg) {
        Object value = arg;
        switch (type) {
            case UINT8:
            case SINT8:
                Assert.assertThat(thing + " type", value, is(instanceOf(Byte.class)));
                break;
            case UINT16:
            case SINT16:
                Assert.assertThat(thing + " type", value, is(instanceOf(Short.class)));
                break;
            case UINT32:
            case SINT32:
                Assert.assertThat(thing + " type", value, is(instanceOf(Integer.class)));
                break;
            case UINT64:
            case SINT64:
                Assert.assertThat(thing + " type", value, is(instanceOf(Long.class)));
                break;
            case FLOAT:
                Assert.assertThat(thing + " type", value, is(instanceOf(Float.class)));
                break;
            case DOUBLE:
                Assert.assertThat(thing + " type", value, is(instanceOf(Double.class)));
                break;
            case POINTER:
                Assert.assertThat(thing + " type", value, is(instanceOf(TruffleObject.class)));
                TruffleObject obj = (TruffleObject) value;
                Assert.assertTrue(thing + " is boxed", isBoxed(obj));
                value = unbox(obj);
                Assert.assertThat("unboxed " + thing, value, is(instanceOf(Long.class)));
                break;
            default:
                Assert.fail();
        }
        Assert.assertEquals(expected, ((Number) value).longValue());
    }

    /**
     * Test all primitive types as argument and return type of native functions.
     */
    public class TestIncrementNode extends SendExecuteNode {

        public TestIncrementNode() {
            super("increment_" + type, String.format("(%s):%s", type, type));
        }
    }

    @Test
    public void testIncrement(@Inject(TestIncrementNode.class) CallTarget callTarget) {
        Object ret = callTarget.call(42);
        checkExpectedRet(43, ret);
    }

    /**
     * Test boxed primitive types as argument to native functions.
     *
     * @param callTarget
     */
    @Test
    public void testBoxed(@Inject(TestIncrementNode.class) CallTarget callTarget) {
        Object ret = callTarget.call(new BoxedPrimitive(42));
        checkExpectedRet(43, ret);
    }

    /**
     * Test callback function as argument to native functions, and all primitive types as argument
     * and return type of callback functions.
     */
    public class TestCallbackNode extends SendExecuteNode {

        public TestCallbackNode() {
            super("callback_" + type, String.format("((%s):%s, %s) : %s", type, type, type, type));
        }
    }

    @Test
    public void testCallback(@Inject(TestCallbackNode.class) CallTarget callTarget) {
        TruffleObject callback = new TestCallback(1, (args) -> {
            checkExpectedArg(42 + 1, args[0]);
            return unboxNumber(args[0]) + 5;
        });
        Object ret = callTarget.call(callback, 42);
        checkExpectedRet((42 + 6) * 2, ret);
    }

    /**
     * Test callback function as return type of native function.
     */
    public class TestCallbackRetNode extends NFITestRootNode {

        final TruffleObject getIncrement = lookupAndBind("callback_ret_" + type, String.format("() : (%s):%s", type, type));

        @Child Node executeGetIncrement = Message.EXECUTE.createNode();
        @Child Node executeClosure = Message.EXECUTE.createNode();

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            Object functionPtr = ForeignAccess.sendExecute(executeGetIncrement, getIncrement);
            checkIsClosure(functionPtr);
            return ForeignAccess.sendExecute(executeClosure, (TruffleObject) functionPtr, 42);
        }

        @TruffleBoundary
        private void checkIsClosure(Object value) {
            Assert.assertThat("closure", value, is(instanceOf(TruffleObject.class)));
        }
    }

    @Test
    public void testCallbackRet(@Inject(TestCallbackRetNode.class) CallTarget callTarget) {
        Object ret = callTarget.call();
        checkExpectedRet(43, ret);
    }

    private String getPingPongSignature() {
        String fnPointer = String.format("(%s):%s", type, type);
        String wrapPointer = String.format("(env, %s):%s", fnPointer, fnPointer);
        return String.format("(env, %s, %s) : %s", wrapPointer, type, type);
    }

    /**
     * Test callback functions as argument and return type of other callback functions.
     */
    public class TestPingPongNode extends SendExecuteNode {

        public TestPingPongNode() {
            super("pingpong_" + type, getPingPongSignature());
        }
    }

    @Test
    public void testPingPong(@Inject(TestPingPongNode.class) CallTarget callTarget) {

        TruffleObject wrap = new TestCallback(1, (args) -> {
            Assert.assertThat("argument", args[0], is(instanceOf(TruffleObject.class)));
            TruffleObject fn = (TruffleObject) args[0];
            TruffleObject wrapped = new TestCallback(1, (innerArgs) -> {
                checkExpectedArg(6, innerArgs[0]);
                try {
                    return ForeignAccess.sendExecute(Message.EXECUTE.createNode(), fn, unboxNumber(innerArgs[0]) * 3);
                } catch (InteropException ex) {
                    throw new AssertionError(ex);
                }
            });
            return wrapped;
        });

        Object ret = callTarget.call(wrap, 5);
        checkExpectedRet(38, ret);
    }
}
