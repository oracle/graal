/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.TruffleVM;
import java.io.IOException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * A collection of tests that can certify language implementation to be compliant with most recent
 * requirements of the Truffle infrastructure and tooling. Subclass, implement abstract methods and
 * include in your test suite.
 */
public abstract class TruffleTCK {
    private static final Logger LOG = Logger.getLogger(TruffleTCK.class.getName());
    private static final Random RANDOM = new Random();
    private TruffleVM tckVM;

    protected TruffleTCK() {
    }

    /**
     * This methods is called before first test is executed. It's purpose is to set a TruffleVM with
     * your language up, so it is ready for testing.
     * {@link TruffleVM#eval(com.oracle.truffle.api.source.Source) Execute} any scripts you need,
     * and prepare global symbols with proper names. The symbols will then be looked up by the
     * infrastructure (using the names provided by you from methods like {@link #plusInt()}) and
     * used for internal testing.
     *
     * @return initialized Truffle virtual machine
     * @throws java.lang.Exception thrown when the VM preparation fails
     */
    protected abstract TruffleVM prepareVM() throws Exception;

    /**
     * MIME type associated with your language. The MIME type will be passed to
     * {@link TruffleVM#eval(com.oracle.truffle.api.source.Source)} method of the
     * {@link #prepareVM() created TruffleVM}.
     *
     * @return mime type of the tested language
     */
    protected abstract String mimeType();

    /**
     * Name of function which will return value 42 as a number. The return value of the method
     * should be instance of {@link Number} and its {@link Number#intValue()} should return
     * <code>42</code>.
     *
     * @return name of globally exported symbol
     */
    protected abstract String fourtyTwo();

    /**
     * Name of a function that returns <code>null</code>. Truffle languages are encouraged to have
     * their own type representing <code>null</code>, but when such value is returned from
     * {@link TruffleVM#eval}, it needs to be converted to real Java <code>null</code> by sending a
     * foreign access <em>isNull</em> message. There is a test to verify it is really true.
     *
     * @return name of globally exported symbol
     */
    protected abstract String returnsNull();

    /**
     * Name of function to add two integer values together. The symbol will be invoked with two
     * parameters of type {@link Integer} and expects result of type {@link Number} which's
     * {@link Number#intValue()} is equivalent of <code>param1 + param2</code>.
     *
     * @return name of globally exported symbol
     */
    protected String plusInt() {
        throw new UnsupportedOperationException("Override plus(Class,Class) method!");
    }

    /**
     * Name of function to add two numbers together. The symbol will be invoked with two parameters
     * of <code>type1</code> and <code>type2</code> and expects result of type {@link Number} 
     * which's {@link Number#intValue()} is equivalent of <code>param1 + param2</code>. As some
     * languages may have different operations for different types of numbers, the actual types are
     * passed to the method and the implementation can decide to return different symbol based on
     * the parameters.
     *
     * @param type1 one of byte, short, int, long, float, double class
     * @param type2 one of byte, short, int, long, float, double class
     * @return name of globally exported symbol
     */
    protected String plus(Class<?> type1, Class<?> type2) {
        return plusInt();
    }

    /**
     * Name of a function in your language to perform a callback to foreign function. Your function
     * should prepare two numbers (18 and 32) and apply them to the function passed in as an
     * argument of your function. It should then add 10 to the returned value and return the result
     * back to its caller.
     *
     * @return name of globally exported symbol
     */
    protected abstract String applyNumbers();

    /**
     * Name of a function that counts number of its invocations in current {@link TruffleVM}
     * context. Your function should somehow keep a counter to remember number of its invocations
     * and always increment it. The first invocation should return <code>1</code>, the second
     * <code>2</code> and so on. The returned values are expected to be instances of {@link Number}.
     * <p>
     * The function will be used to test that two instances of your language can co-exist next to
     * each other. Without being mutually influenced.
     *
     * @return name of globally expected symbol
     */
    protected abstract String countInvocations();

    /**
     * Return a code snippet that is invalid in your language. Its
     * {@link TruffleVM#eval(com.oracle.truffle.api.source.Source) evaluation} should fail and yield
     * an exception.
     *
     * @return code snippet invalid in the tested language
     */
    protected abstract String invalidCode();

    /**
     * Name of a function that returns a compound object with members representing certain
     * operations. In the JavaScript the object should look like:
     * 
     * <pre>
     * <b>var</b> obj = {
     *   'fourtyTwo': function {@link #fourtyTwo()},
     *   'plus': function {@link #plusInt()},
     *   'returnsNull': function {@link #returnsNull()},
     *   'returnsThis': function() { return obj; }
     * };
     * <b>return</b> obj;
     * </pre>
     * 
     * The returned object shall have three functions that will be obtained and used exactly as
     * described in their Javadoc - e.g. {@link #fourtyTwo()}, {@link #plusInt()} and
     * {@link #returnsNull()}. In addition to that there should be one more function
     * <b>returnsThis</b> that will return the object itself again.
     *
     * @return name of a function that returns such compound object
     */
    protected String compoundObject() {
        return null;
    }

    private TruffleVM vm() throws Exception {
        if (tckVM == null) {
            tckVM = prepareVM();
        }
        return tckVM;
    }

    //
    // The tests
    //

    @Test
    public void testFortyTwo() throws Exception {
        TruffleVM.Symbol fourtyTwo = findGlobalSymbol(fourtyTwo());

        Object res = fourtyTwo.invoke(null).get();

        assert res instanceof Number : "should yield a number, but was: " + res;

        Number n = (Number) res;

        assert 42 == n.intValue() : "The value is 42 =  " + n.intValue();
    }

    @Test
    public void testFortyTwoWithCompoundObject() throws Exception {
        CompoundObject obj = findCompoundSymbol("testFortyTwoWithCompoundObject");
        if (obj == null) {
            return;
        }
        Number res = obj.fourtyTwo();
        assertEquals("Should be 42", 42, res.intValue());
    }

    @Test
    public void testNull() throws Exception {
        TruffleVM.Symbol retNull = findGlobalSymbol(returnsNull());

        Object res = retNull.invoke(null).get();

        assertNull("Should yield real Java null", res);
    }

    @Test
    public void testNullInCompoundObject() throws Exception {
        CompoundObject obj = findCompoundSymbol("testNullInCompoundObject");
        if (obj == null) {
            return;
        }
        Object res = obj.returnsNull();
        assertNull("Should yield real Java null", res);
    }

    @Test
    public void testPlusWithInts() throws Exception {
        int a = RANDOM.nextInt(100);
        int b = RANDOM.nextInt(100);

        TruffleVM.Symbol plus = findGlobalSymbol(plus(int.class, int.class));

        Number n = plus.invoke(null, a, b).as(Number.class);
        assert a + b == n.intValue() : "The value is correct: (" + a + " + " + b + ") =  " + n.intValue();
    }

    @Test
    public void testPlusWithBytes() throws Exception {
        int a = RANDOM.nextInt(100);
        int b = RANDOM.nextInt(100);

        TruffleVM.Symbol plus = findGlobalSymbol(plus(byte.class, byte.class));

        Number n = plus.invoke(null, (byte) a, (byte) b).as(Number.class);
        assert a + b == n.intValue() : "The value is correct: (" + a + " + " + b + ") =  " + n.intValue();
    }

    @Test
    public void testPlusWithShort() throws Exception {
        int a = RANDOM.nextInt(100);
        int b = RANDOM.nextInt(100);

        TruffleVM.Symbol plus = findGlobalSymbol(plus(short.class, short.class));

        Number n = plus.invoke(null, (short) a, (short) b).as(Number.class);
        assert a + b == n.intValue() : "The value is correct: (" + a + " + " + b + ") =  " + n.intValue();
    }

    @Test
    public void testPlusWithLong() throws Exception {
        long a = RANDOM.nextInt(100);
        long b = RANDOM.nextInt(100);

        TruffleVM.Symbol plus = findGlobalSymbol(plus(long.class, long.class));

        Number n = plus.invoke(null, a, b).as(Number.class);
        assert a + b == n.longValue() : "The value is correct: (" + a + " + " + b + ") =  " + n.longValue();
    }

    @Test
    public void testPlusWithFloat() throws Exception {
        float a = RANDOM.nextFloat();
        float b = RANDOM.nextFloat();

        TruffleVM.Symbol plus = findGlobalSymbol(plus(float.class, float.class));

        Number n = plus.invoke(null, a, b).as(Number.class);
        assertEquals("Correct value computed", a + b, n.floatValue(), 0.01f);
    }

    @Test
    public void testPlusWithDouble() throws Exception {
        double a = RANDOM.nextDouble();
        double b = RANDOM.nextDouble();

        TruffleVM.Symbol plus = findGlobalSymbol(plus(float.class, float.class));

        Number n = plus.invoke(null, a, b).as(Number.class);
        assertEquals("Correct value computed", a + b, n.doubleValue(), 0.01);
    }

    @Test
    public void testPlusWithIntsOnCompoundObject() throws Exception {
        int a = RANDOM.nextInt(100);
        int b = RANDOM.nextInt(100);

        CompoundObject obj = findCompoundSymbol("testPlusWithIntsOnCompoundObject");
        if (obj == null) {
            return;
        }

        Number n = obj.plus(a, b);
        assert a + b == n.intValue() : "The value is correct: (" + a + " + " + b + ") =  " + n.intValue();
    }

    @Test(expected = IOException.class)
    public void testInvalidTestMethod() throws Exception {
        String mime = mimeType();
        String code = invalidCode();
        Object ret = vm().eval(Source.fromText(code, "Invalid code").withMimeType(mime)).get();
        fail("Should yield IOException, but returned " + ret);
    }

    @Test
    public void testMaxOrMinValue() throws Exception {
        TruffleVM.Symbol apply = findGlobalSymbol(applyNumbers());

        TruffleObject fn = JavaInterop.asTruffleFunction(LongBinaryOperation.class, new MaxMinObject(true));
        Object res = apply.invoke(null, fn).get();

        assert res instanceof Number : "result should be a number: " + res;

        Number n = (Number) res;

        assert 42 == n.intValue() : "32 > 18 and plus 10";
    }

    @Test
    public void testMaxOrMinValue2() throws Exception {
        TruffleVM.Symbol apply = findGlobalSymbol(applyNumbers());

        TruffleObject fn = JavaInterop.asTruffleFunction(LongBinaryOperation.class, new MaxMinObject(false));
        final TruffleVM.Symbol result = apply.invoke(null, fn);

        try {
            String res = result.as(String.class);
            fail("Cannot be converted to String: " + res);
        } catch (ClassCastException ex) {
            // correct
        }

        Number n = result.as(Number.class);
        assert 28 == n.intValue() : "18 < 32 and plus 10";
    }

    @Test
    public void testCoExistanceOfMultipleLanguageInstances() throws Exception {
        final String countMethod = countInvocations();
        TruffleVM.Symbol count1 = findGlobalSymbol(countMethod);
        TruffleVM vm1 = tckVM;
        tckVM = null; // clean-up
        TruffleVM.Symbol count2 = findGlobalSymbol(countMethod);
        TruffleVM vm2 = tckVM;

        assertNotSame("Two virtual machines allocated", vm1, vm2);

        int prev1 = 0;
        int prev2 = 0;
        for (int i = 0; i < 10; i++) {
            int quantum = RANDOM.nextInt(10);
            for (int j = 0; j < quantum; j++) {
                Object res = count1.invoke(null).get();
                assert res instanceof Number : "expecting number: " + res;
                ++prev1;
                assert ((Number) res).intValue() == prev1 : "expecting " + prev1 + " but was " + res;
            }
            for (int j = 0; j < quantum; j++) {
                Object res = count2.invoke(null).get();
                assert res instanceof Number : "expecting number: " + res;
                ++prev2;
                assert ((Number) res).intValue() == prev2 : "expecting " + prev2 + " but was " + res;
            }
            assert prev1 == prev2 : "At round " + i + " the same number of invocations " + prev1 + " vs. " + prev2;
        }

    }

    private TruffleVM.Symbol findGlobalSymbol(String name) throws Exception {
        TruffleVM.Symbol s = vm().findGlobalSymbol(name);
        assert s != null : "Symbol " + name + " is not found!";
        return s;
    }

    private CompoundObject findCompoundSymbol(String name) throws Exception {
        final String compoundObjectName = compoundObject();
        if (compoundObjectName == null) {
            final long introduced = 1441616302340L;
            long wait = (System.currentTimeMillis() - introduced) / 3600;
            if (wait < 100) {
                wait = 100;
            }
            LOG.log(Level.SEVERE, "compoundObject() method not overriden! Skipping {1} test for now. But sleeping for {0} ms.", new Object[]{wait, name});
            Thread.sleep(wait);
            return null;
        }
        TruffleVM.Symbol s = vm().findGlobalSymbol(compoundObjectName);
        assert s != null : "Symbol " + compoundObjectName + " is not found!";
        CompoundObject obj = s.invoke(null).as(CompoundObject.class);
        int traverse = RANDOM.nextInt(10);
        while (traverse-- >= 0) {
            obj = obj.returnsThis();
        }
        return obj;
    }

    interface CompoundObject {
        Number fourtyTwo();

        Number plus(int x, int y);

        Object returnsNull();

        CompoundObject returnsThis();
    }
}
