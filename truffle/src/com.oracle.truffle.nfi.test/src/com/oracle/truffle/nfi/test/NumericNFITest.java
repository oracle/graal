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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;
import com.oracle.truffle.nfi.test.interop.BoxedPrimitive;
import com.oracle.truffle.nfi.test.interop.TestCallback;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

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

    final class NumberMatcher extends BaseMatcher<Object> {

        private final long expected;

        private NumberMatcher(long expected) {
            this.expected = expected;
        }

        private boolean matchesType(Object item) {
            try {
                if (UNCACHED_INTEROP.isNumber(item)) {
                    switch (type) {
                        case SINT8:
                            return UNCACHED_INTEROP.fitsInByte(item);
                        case SINT16:
                            return UNCACHED_INTEROP.fitsInShort(item);
                        case SINT32:
                            return UNCACHED_INTEROP.fitsInInt(item);
                        case SINT64:
                        case UINT64:
                            return UNCACHED_INTEROP.fitsInLong(item);
                        case UINT8:
                            return Long.compareUnsigned(UNCACHED_INTEROP.asLong(item), 1L << Byte.SIZE) < 0;
                        case UINT16:
                            return Long.compareUnsigned(UNCACHED_INTEROP.asLong(item), 1L << Short.SIZE) < 0;
                        case UINT32:
                            return Long.compareUnsigned(UNCACHED_INTEROP.asLong(item), 1L << Integer.SIZE) < 0;
                        case FLOAT:
                            return UNCACHED_INTEROP.fitsInFloat(item);
                        case DOUBLE:
                            return UNCACHED_INTEROP.fitsInDouble(item);
                    }
                }
            } catch (UnsupportedMessageException ex) {
            }
            return false;
        }

        @Override
        public boolean matches(Object item) {
            try {
                return matchesType(item) && UNCACHED_INTEROP.asLong(item) == expected;
            } catch (UnsupportedMessageException ex) {
                return false;
            }
        }

        @Override
        public void describeMismatch(Object item, Description description) {
            super.describeMismatch(item, description);
            if (!matchesType(item)) {
                description.appendText(" (wrong type)");
            }
        }

        @Override
        public void describeTo(Description description) {
            description.appendText(Long.toString(expected)).appendText(" (type ").appendText(type.name()).appendText(")");
        }
    }

    private Matcher<Object> number(long expected) {
        return new NumberMatcher(expected);
    }

    static long unboxNumber(Object arg) {
        Assert.assertTrue("isNumber", UNCACHED_INTEROP.isNumber(arg));
        Assert.assertTrue("fitsInLong", UNCACHED_INTEROP.fitsInLong(arg));
        try {
            return UNCACHED_INTEROP.asLong(arg);
        } catch (UnsupportedMessageException ex) {
            throw new AssertionError(ex);
        }
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
        Assert.assertThat("return", ret, is(number(43)));
    }

    private long fixSign(long nr) {
        switch (type) {
            case UINT8:
                return nr & 0xFFL;
            case UINT16:
                return nr & 0xFFFFL;
            case UINT32:
                return nr & 0xFFFF_FFFFL;
            default:
                return nr;
        }
    }

    @Test
    public void testIncrementNeg(@Inject(TestIncrementNode.class) CallTarget callTarget) {
        Object ret = callTarget.call(fixSign(-5));
        Assert.assertThat("return", ret, is(number(fixSign(-4))));
    }

    /**
     * Test boxed primitive types as argument to native functions.
     *
     * @param callTarget
     */
    @Test
    public void testBoxed(@Inject(TestIncrementNode.class) CallTarget callTarget) {
        Object ret = callTarget.call(new BoxedPrimitive(42));
        Assert.assertThat("return", ret, is(number(43)));
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

    private final TruffleObject callback = new TestCallback(1, (args) -> {
        Assert.assertThat("argument", args[0], is(number(42 + 1)));
        return unboxNumber(args[0]) + 5;
    });

    @Test
    public void testCallback(@Inject(TestCallbackNode.class) CallTarget callTarget) {
        Object ret = callTarget.call(callback, 42);
        Assert.assertThat("return", ret, is(number((42 + 6) * 2)));
    }

    private final TruffleObject negCallback = new TestCallback(1, (args) -> {
        Assert.assertThat("argument", args[0], is(number(fixSign(-42 + 1))));
        return unboxNumber(args[0]) + 5;
    });

    @Test
    public void testCallbackNeg(@Inject(TestCallbackNode.class) CallTarget callTarget) {
        Object ret = callTarget.call(negCallback, fixSign(-42));
        Assert.assertThat("return", ret, is(number(fixSign((-42 + 6) * 2))));
    }

    /**
     * Test callback function as return type of native function.
     */
    public class TestCallbackRetNode extends NFITestRootNode {

        final TruffleObject getIncrement = lookupAndBind("callback_ret_" + type, String.format("() : (%s):%s", type, type));

        @Child InteropLibrary getIncrementInterop = getInterop(getIncrement);
        @Child InteropLibrary closureInterop = getInterop();

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            Object functionPtr = getIncrementInterop.execute(getIncrement);
            checkIsClosure(functionPtr);
            return closureInterop.execute(functionPtr, 42);
        }

        @TruffleBoundary
        private void checkIsClosure(Object value) {
            Assert.assertTrue("closure", UNCACHED_INTEROP.isExecutable(value));
        }
    }

    @Test
    public void testCallbackRet(@Inject(TestCallbackRetNode.class) CallTarget callTarget) {
        Object ret = callTarget.call();
        Assert.assertThat("return", ret, is(number(43)));
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

    private final TruffleObject wrap = new TestCallback(1, (args) -> {
        Assert.assertThat("argument", args[0], is(instanceOf(TruffleObject.class)));
        TruffleObject fn = (TruffleObject) args[0];
        TruffleObject wrapped = new TestCallback(1, (innerArgs) -> {
            Assert.assertThat("argument", innerArgs[0], is(number(6)));
            try {
                return UNCACHED_INTEROP.execute(fn, unboxNumber(innerArgs[0]) * 3);
            } catch (InteropException ex) {
                throw new AssertionError(ex);
            }
        });
        return wrapped;
    });

    @Test
    public void testPingPong(@Inject(TestPingPongNode.class) CallTarget callTarget) {
        Object ret = callTarget.call(wrap, 5);
        Assert.assertThat("return", ret, is(number(38)));
    }
}
