/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import org.hamcrest.Matcher;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;
import com.oracle.truffle.nfi.test.NumericNFITest.NumberMatcher;
import com.oracle.truffle.nfi.test.interop.TestCallback;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public class IntegerLimitsNFITest extends NFITest {

    private static void addTests(ArrayList<Object[]> tests, NativeSimpleType type, boolean signed, long minSigned, long maxSigned, long maxUnsigned) {
        tests.add(new Object[]{type, signed, minSigned, maxSigned, maxUnsigned});
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();

        addTests(ret, NativeSimpleType.SINT8, true, Byte.MIN_VALUE, Byte.MAX_VALUE, (1L << Byte.SIZE) - 1);
        addTests(ret, NativeSimpleType.SINT16, true, Short.MIN_VALUE, Short.MAX_VALUE, (1L << Short.SIZE) - 1);
        addTests(ret, NativeSimpleType.SINT32, true, Integer.MIN_VALUE, Integer.MAX_VALUE, (1L << Integer.SIZE) - 1);

        addTests(ret, NativeSimpleType.UINT8, false, Byte.MIN_VALUE, Byte.MAX_VALUE, (1L << Byte.SIZE) - 1);
        addTests(ret, NativeSimpleType.UINT16, false, Short.MIN_VALUE, Short.MAX_VALUE, (1L << Short.SIZE) - 1);
        addTests(ret, NativeSimpleType.UINT32, false, Integer.MIN_VALUE, Integer.MAX_VALUE, (1L << Integer.SIZE) - 1);

        // TODO(GR-15358): INT64 tests, but first we have to fix UINT64 interop

        return ret;
    }

    @Parameter(0) public NativeSimpleType type;
    @Parameter(1) public boolean signed;
    @Parameter(2) public long minSigned;
    @Parameter(3) public long maxSigned;
    @Parameter(4) public long maxUnsigned;

    private Matcher<Object> number(long expected) {
        return new NumberMatcher(type, expected);
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

    public class IncrementNode extends SendExecuteNode {

        public IncrementNode() {
            super("increment_" + type, String.format("(%s):%s", type, type));
        }
    }

    public class DecrementNode extends SendExecuteNode {

        public DecrementNode() {
            super("decrement_" + type, String.format("(%s):%s", type, type));
        }
    }

    public class CallClosureNode extends SendExecuteNode {

        public CallClosureNode() {
            super("call_closure_" + type, String.format("((%s):%s, %s) : %s", type, type, type, type));
        }
    }

    // increment

    @Test
    public void testIncZero(@Inject(IncrementNode.class) CallTarget increment) {
        Object ret = increment.call(0);
        // no overflow
        Assert.assertThat("return value", ret, is(number(1)));
    }

    @Test
    public void testIncMinSigned(@Inject(IncrementNode.class) CallTarget increment) {
        Object ret = increment.call(minSigned);
        if (signed) {
            // no overflow
            Assert.assertThat("return value", ret, is(number(minSigned + 1)));
        } else {
            // unsigned: minSigned overflows to maxSigned + 1
            Assert.assertThat("return value", ret, is(number(maxSigned + 2)));
        }
    }

    @Test
    public void testIncMaxSigned(@Inject(IncrementNode.class) CallTarget increment) {
        Object ret = increment.call(maxSigned);
        if (signed) {
            // signed: maxSigned overflows to minSigned - 1
            Assert.assertThat("return value", ret, is(number(minSigned)));
        } else {
            // no overflow
            Assert.assertThat("return value", ret, is(number(maxSigned + 1)));
        }
    }

    @Test
    public void testIncMaxUnsigned(@Inject(IncrementNode.class) CallTarget increment) {
        Object ret = increment.call(maxUnsigned);
        // regardless of sign, always overflows to zero
        Assert.assertThat("return value", ret, is(number(0)));
    }

    // decrement

    @Test
    public void testDecZero(@Inject(DecrementNode.class) CallTarget decrement) {
        Object ret = decrement.call(0);
        if (signed) {
            // no overflow
            Assert.assertThat("return value", ret, is(number(-1)));
        } else {
            // overflow to maxUnsigned
            Assert.assertThat("return value", ret, is(number(maxUnsigned)));
        }
    }

    @Test
    public void testDecMinSigned(@Inject(DecrementNode.class) CallTarget decrement) {
        Object ret = decrement.call(minSigned);
        // signed: minSigned-1 overflows to maxSigned
        // unsigned: minSigned overflows to maxSigned + 1
        Assert.assertThat("return value", ret, is(number(maxSigned)));
    }

    @Test
    public void testDecMaxSigned(@Inject(DecrementNode.class) CallTarget decrement) {
        Object ret = decrement.call(maxSigned);
        // no overflow
        Assert.assertThat("return value", ret, is(number(maxSigned - 1)));
    }

    @Test
    public void testDecMaxUnsigned(@Inject(DecrementNode.class) CallTarget decrement) {
        Object ret = decrement.call(maxUnsigned);
        if (signed) {
            // maxUnsigned overflows to -1
            Assert.assertThat("return value", ret, is(number(-2)));
        } else {
            // no overflow
            Assert.assertThat("return value", ret, is(number(maxUnsigned - 1)));
        }
    }

    // closure arguments

    @Test
    public void testClosureArgMinSigned(@Inject(CallClosureNode.class) CallTarget callClosure) {
        TestCallback c = new TestCallback(1, (args) -> {
            if (signed) {
                // no overflow
                Assert.assertThat("closure arg", args[0], is(number(minSigned)));
            } else {
                // unsigned: minSigned overflows to maxSigned+1
                Assert.assertThat("closure arg", args[0], is(number(maxSigned + 1)));
            }
            return 0;
        });
        callClosure.call(c, minSigned);
    }

    @Test
    public void testClosureArgMaxUnsigned(@Inject(CallClosureNode.class) CallTarget callClosure) {
        TestCallback c = new TestCallback(1, (args) -> {
            if (signed) {
                // signed: maxUnsigned overflows to -1
                Assert.assertThat("closure arg", args[0], is(number(-1)));
            } else {
                // no overflow
                Assert.assertThat("closure arg", args[0], is(number(maxUnsigned)));
            }
            return 0;
        });
        callClosure.call(c, maxUnsigned);
    }

    // errors

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testLowerBound(@Inject(IncrementNode.class) CallTarget increment) {
        expectedException.expectCause(instanceOf(UnsupportedTypeException.class));
        increment.call(minSigned - 1);
    }

    @Test
    public void testLowerBoundClosureRet(@Inject(CallClosureNode.class) CallTarget callClosure) {
        expectedException.expectCause(instanceOf(UnsupportedTypeException.class));
        TestCallback c = new TestCallback(1, (args) -> minSigned - 1);
        callClosure.call(c, 0);
    }

    @Test
    public void testUpperBound(@Inject(IncrementNode.class) CallTarget increment) {
        expectedException.expectCause(instanceOf(UnsupportedTypeException.class));
        increment.call(maxUnsigned + 1);
    }

    @Test
    public void testUpperBoundClosureRet(@Inject(CallClosureNode.class) CallTarget callClosure) {
        expectedException.expectCause(instanceOf(UnsupportedTypeException.class));
        TestCallback c = new TestCallback(1, (args) -> maxUnsigned + 1);
        callClosure.call(c, 0);
    }
}
