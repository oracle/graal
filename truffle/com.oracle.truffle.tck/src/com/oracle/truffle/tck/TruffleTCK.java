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

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Language;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A collection of tests that can certify language implementation to be compliant with most recent
 * requirements of the Truffle infrastructure and tooling. Subclass, implement abstract methods and
 * include in your test suite.
 */
public abstract class TruffleTCK {
    private static final Random RANDOM = new Random();
    private PolyglotEngine tckVM;

    protected TruffleTCK() {
    }

    /**
     * This methods is called before first test is executed. It's purpose is to set a
     * {@link PolyglotEngine} with your language up, so it is ready for testing.
     * {@link PolyglotEngine#eval(com.oracle.truffle.api.source.Source) Execute} any scripts you
     * need, and prepare global symbols with proper names. The symbols will then be looked up by the
     * infrastructure (using the names provided by you from methods like {@link #plusInt()}) and
     * used for internal testing.
     *
     * @return initialized Truffle virtual machine
     * @throws java.lang.Exception thrown when the VM preparation fails
     */
    protected abstract PolyglotEngine prepareVM() throws Exception;

    /**
     * MIME type associated with your language. The MIME type will be passed to
     * {@link PolyglotEngine#eval(com.oracle.truffle.api.source.Source)} method of the
     * {@link #prepareVM() created engine}.
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
     * {@link PolyglotEngine#eval}, it needs to be converted to real Java <code>null</code> by
     * sending a foreign access <em>isNull</em> message. There is a test to verify it is really
     * true.
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
     * Name of identity function. The identity function accepts one argument and returns it. The
     * argument should go through without any modification, e.g. the input should result in
     * identical output.
     *
     * @return name of globally exported symbol
     */
    protected String identity() {
        throw new UnsupportedOperationException("identity() method not implemented");
    }

    /**
     * Name of a function that adds up two complex numbers. The function accepts two arguments and
     * provides no return value. The arguments are complex numbers with members called real and
     * imaginary. The first argument contains the result of the addition.
     *
     * @return name of globally exported symbol
     */
    protected String complexAdd() {
        throw new UnsupportedOperationException("complexAdd() method not implemented");
    }

    /**
     * Name of a function to return global object. The function can be executed without providing
     * any arguments and should return global object of the language, if the language supports it.
     * Global object is the one accessible via
     * {@link TruffleLanguage#getLanguageGlobal(java.lang.Object)}.
     *
     * @return name of globally exported symbol, return <code>null</code> if the language doesn't
     *         support the concept of global object
     */
    protected String globalObject() {
        throw new UnsupportedOperationException("globalObject() method not implemented");
    }

    /**
     * Name of a function to parse source written in some other language. When the function is
     * executed, it expects two arguments. First one is MIME type identifying
     * {@link TruffleLanguage} and the second one is the source code to parse in that language and
     * execute it. The result of the execution is then returned back to the caller.
     *
     * @return name of globally exported symbol to invoke when one wants to execute some code
     */
    protected String evaluateSource() {
        throw new UnsupportedOperationException("evaluateSource() method not implemented");
    }

    /**
     * Code snippet to multiplyCode two two variables. The test uses the snippet as a parameter to
     * your language's
     * {@link TruffleLanguage#parse(com.oracle.truffle.api.source.Source, com.oracle.truffle.api.nodes.Node, java.lang.String...)}
     * method.
     *
     * @param firstName name of the first variable to multiplyCode
     * @param secondName name of the second variable to multiplyCode
     * @return code snippet that multiplies the two variables in your language
     */
    protected String multiplyCode(String firstName, String secondName) {
        throw new UnsupportedOperationException("multiply(String,String) method not implemeted!");
    }

    /**
     * Name of a function that counts number of its invocations in current {@link PolyglotEngine}
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
     * {@link PolyglotEngine#eval(com.oracle.truffle.api.source.Source) evaluation} should fail and
     * yield an exception.
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
        throw new UnsupportedOperationException("compoundObject() method not implemented");
    }

    private PolyglotEngine vm() throws Exception {
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
        PolyglotEngine.Value fourtyTwo = findGlobalSymbol(fourtyTwo());

        Object res = fourtyTwo.execute().get();

        assert res instanceof Number : "should yield a number, but was: " + res;

        Number n = (Number) res;

        assert 42 == n.intValue() : "The value is 42 =  " + n.intValue();
    }

    @Test
    public void testFortyTwoWithCompoundObject() throws Exception {
        CompoundObject obj = findCompoundSymbol();
        if (obj == null) {
            return;
        }
        Number res = obj.fourtyTwo();
        assertEquals("Should be 42", 42, res.intValue());
    }

    @Test
    public void testNull() throws Exception {
        PolyglotEngine.Value retNull = findGlobalSymbol(returnsNull());

        Object res = retNull.execute().get();

        assertNull("Should yield real Java null", res);
    }

    @Test
    public void testNullCanBeCastToAnything() throws Exception {
        PolyglotEngine.Value retNull = findGlobalSymbol(returnsNull());

        Object res = retNull.execute().as(CompoundObject.class);

        assertNull("Should yield real Java null", res);
    }

    @Test
    public void testNullInCompoundObject() throws Exception {
        CompoundObject obj = findCompoundSymbol();
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

        PolyglotEngine.Value plus = findGlobalSymbol(plus(int.class, int.class));

        Number n = plus.execute(a, b).as(Number.class);
        assert a + b == n.intValue() : "The value is correct: (" + a + " + " + b + ") =  " + n.intValue();
    }

    @Test
    public void testPlusWithBytes() throws Exception {
        int a = RANDOM.nextInt(100);
        int b = RANDOM.nextInt(100);

        PolyglotEngine.Value plus = findGlobalSymbol(plus(byte.class, byte.class));

        Number n = plus.execute((byte) a, (byte) b).as(Number.class);
        assert a + b == n.intValue() : "The value is correct: (" + a + " + " + b + ") =  " + n.intValue();
    }

    @Test
    public void testPlusWithShort() throws Exception {
        int a = RANDOM.nextInt(100);
        int b = RANDOM.nextInt(100);

        PolyglotEngine.Value plus = findGlobalSymbol(plus(short.class, short.class));

        Number n = plus.execute((short) a, (short) b).as(Number.class);
        assert a + b == n.intValue() : "The value is correct: (" + a + " + " + b + ") =  " + n.intValue();
    }

    @Test
    public void testPlusWithLong() throws Exception {
        long a = RANDOM.nextInt(100);
        long b = RANDOM.nextInt(100);

        PolyglotEngine.Value plus = findGlobalSymbol(plus(long.class, long.class));

        Number n = plus.execute(a, b).as(Number.class);
        assert a + b == n.longValue() : "The value is correct: (" + a + " + " + b + ") =  " + n.longValue();
    }

    @Test
    public void testPlusWithFloat() throws Exception {
        float a = RANDOM.nextFloat();
        float b = RANDOM.nextFloat();

        PolyglotEngine.Value plus = findGlobalSymbol(plus(float.class, float.class));

        Number n = plus.execute(a, b).as(Number.class);
        assertEquals("Correct value computed", a + b, n.floatValue(), 0.01f);
    }

    @Test
    public void testPlusWithDouble() throws Exception {
        double a = RANDOM.nextDouble();
        double b = RANDOM.nextDouble();

        PolyglotEngine.Value plus = findGlobalSymbol(plus(float.class, float.class));

        Number n = plus.execute(a, b).as(Number.class);
        assertEquals("Correct value computed", a + b, n.doubleValue(), 0.01);
    }

    @Test
    public void testPlusWithIntsOnCompoundObject() throws Exception {
        int a = RANDOM.nextInt(100);
        int b = RANDOM.nextInt(100);

        CompoundObject obj = findCompoundSymbol();
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
        PolyglotEngine.Value apply = findGlobalSymbol(applyNumbers());

        TruffleObject fn = JavaInterop.asTruffleFunction(LongBinaryOperation.class, new MaxMinObject(true));
        Object res = apply.execute(fn).get();

        assert res instanceof Number : "result should be a number: " + res;

        Number n = (Number) res;

        assert 42 == n.intValue() : "32 > 18 and plus 10";
    }

    @Test
    public void testMaxOrMinValue2() throws Exception {
        PolyglotEngine.Value apply = findGlobalSymbol(applyNumbers());

        TruffleObject fn = JavaInterop.asTruffleFunction(LongBinaryOperation.class, new MaxMinObject(false));
        final PolyglotEngine.Value result = apply.execute(fn);

        try {
            Boolean res = result.as(Boolean.class);
            fail("Cannot be converted to Boolean: " + res);
        } catch (ClassCastException ex) {
            // correct
        }

        Number n = result.as(Number.class);
        assert 28 == n.intValue() : "18 < 32 and plus 10";
    }

    @Test
    public void testPrimitiveReturnTypeByte() throws Exception {
        PolyglotEngine.Value apply = findGlobalSymbol(applyNumbers());

        byte value = (byte) RANDOM.nextInt(100);

        TruffleObject fn = JavaInterop.asTruffleFunction(ObjectBinaryOperation.class, new ConstantFunction(value));
        Number n = apply.execute(fn).as(Number.class);
        assertEquals("The same value returned", value + 10, n.byteValue());
    }

    @Test
    public void testPrimitiveReturnTypeShort() throws Exception {
        PolyglotEngine.Value apply = findGlobalSymbol(applyNumbers());

        short value = (short) RANDOM.nextInt(100);

        TruffleObject fn = JavaInterop.asTruffleFunction(ObjectBinaryOperation.class, new ConstantFunction(value));
        Number n = apply.execute(fn).as(Number.class);
        assertEquals("The same value returned", value + 10, n.shortValue());
    }

    @Test
    public void testPrimitiveReturnTypeInt() throws Exception {
        PolyglotEngine.Value apply = findGlobalSymbol(applyNumbers());

        int value = RANDOM.nextInt(100);

        TruffleObject fn = JavaInterop.asTruffleFunction(ObjectBinaryOperation.class, new ConstantFunction(value));
        Number n = apply.execute(fn).as(Number.class);
        assertEquals("The same value returned", value + 10, n.intValue());
    }

    @Test
    public void testPrimitiveReturnTypeLong() throws Exception {
        PolyglotEngine.Value apply = findGlobalSymbol(applyNumbers());

        long value = RANDOM.nextInt(1000);

        TruffleObject fn = JavaInterop.asTruffleFunction(ObjectBinaryOperation.class, new ConstantFunction(value));
        Number n = apply.execute(fn).as(Number.class);
        assertEquals("The same value returned", value + 10, n.longValue());
    }

    @Test
    public void testPrimitiveReturnTypeFloat() throws Exception {
        PolyglotEngine.Value apply = findGlobalSymbol(applyNumbers());

        float value = RANDOM.nextInt(1000) + RANDOM.nextFloat();

        TruffleObject fn = JavaInterop.asTruffleFunction(ObjectBinaryOperation.class, new ConstantFunction(value));
        Number n = apply.execute(fn).as(Number.class);
        assertEquals("The same value returned", value + 10, n.floatValue(), 0.01);
    }

    @Test
    public void testPrimitiveReturnTypeDouble() throws Exception {
        PolyglotEngine.Value apply = findGlobalSymbol(applyNumbers());

        double value = RANDOM.nextInt(1000) + RANDOM.nextDouble();

        TruffleObject fn = JavaInterop.asTruffleFunction(ObjectBinaryOperation.class, new ConstantFunction(value));
        Number n = apply.execute(fn).as(Number.class);
        assertEquals("The same value returned", value + 10, n.doubleValue(), 0.01);
    }

    @Test
    public void testPrimitiveidentityByte() throws Exception {
        String id = identity();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        byte value = (byte) RANDOM.nextInt(100);

        Number n = (Number) apply.execute(value).get();
        assertEquals("The same value returned", value, n.byteValue());
    }

    @Test
    public void testPrimitiveidentityBoxedByte() throws Exception {
        String id = identity();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        byte value = (byte) RANDOM.nextInt(100);
        BoxedValue boxed = new BoxedValue(value);

        Number n = (Number) apply.execute(boxed).get();
        assertEquals("The same value returned", value, n.byteValue());
    }

    @Test
    public void testPrimitiveidentityShort() throws Exception {
        String id = identity();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        short value = (short) RANDOM.nextInt(100);
        Number n = (Number) apply.execute(value).get();
        assertEquals("The same value returned", value, n.shortValue());
    }

    @Test
    public void testPrimitiveidentityBoxedShort() throws Exception {
        String id = identity();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        short value = (short) RANDOM.nextInt(100);
        BoxedValue boxed = new BoxedValue(value);

        Number n = (Number) apply.execute(boxed).get();
        assertEquals("The same value returned", value, n.shortValue());
    }

    @Test
    public void testPrimitiveidentityInt() throws Exception {
        String id = identity();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        int value = RANDOM.nextInt(100);

        Number n = (Number) apply.execute(value).get();
        assertEquals("The same value returned", value, n.intValue());
    }

    @Test
    public void testPrimitiveidentityBoxedInt() throws Exception {
        String id = identity();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        int value = RANDOM.nextInt(100);
        BoxedValue boxed = new BoxedValue(value);

        Number n = (Number) apply.execute(boxed).get();
        assertEquals("The same value returned", value, n.intValue());
    }

    @Test
    public void testPrimitiveidentityLong() throws Exception {
        String id = identity();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        long value = RANDOM.nextInt(1000);

        Number n = (Number) apply.execute(value).get();
        assertEquals("The same value returned", value, n.longValue());
    }

    @Test
    public void testPrimitiveidentityBoxedLong() throws Exception {
        String id = identity();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        long value = RANDOM.nextInt(1000);
        BoxedValue boxed = new BoxedValue(value);

        Number n = (Number) apply.execute(boxed).get();
        assertEquals("The same value returned", value, n.longValue());
    }

    @Test
    public void testPrimitiveidentityFloat() throws Exception {
        String id = identity();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        float value = RANDOM.nextInt(1000) + RANDOM.nextFloat();

        Number n = (Number) apply.execute(value).get();
        assertEquals("The same value returned", value, n.floatValue(), 0.01);
    }

    @Test
    public void testPrimitiveidentityBoxedFloat() throws Exception {
        String id = identity();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        float value = RANDOM.nextInt(1000) + RANDOM.nextFloat();
        BoxedValue boxed = new BoxedValue(value);

        Number n = (Number) apply.execute(boxed).get();
        assertEquals("The same value returned", value, n.floatValue(), 0.01);
    }

    @Test
    public void testPrimitiveidentityDouble() throws Exception {
        String id = identity();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        double value = RANDOM.nextInt(1000) + RANDOM.nextDouble();

        Number n = (Number) apply.execute(value).get();
        assertEquals("The same value returned", value, n.doubleValue(), 0.01);
    }

    @Test
    public void testPrimitiveidentityBoxedDouble() throws Exception {
        String id = identity();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        double value = RANDOM.nextInt(1000) + RANDOM.nextDouble();
        BoxedValue boxed = new BoxedValue(value);

        Number n = (Number) apply.execute(boxed).get();
        assertEquals("The same value returned", value, n.doubleValue(), 0.01);
    }

    @Test
    public void testPrimitiveidentityString() throws Exception {
        String id = identity();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        String value = "Value" + RANDOM.nextInt(1000) + RANDOM.nextDouble();

        String ret = (String) apply.execute(value).get();
        assertEquals("The same value returned", value, ret);
    }

    @Test
    public void testPrimitiveidentityBoxedString() throws Exception {
        String id = identity();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        String value = "Value" + RANDOM.nextInt(1000) + RANDOM.nextDouble();
        BoxedValue boxed = new BoxedValue(value);

        String ret = (String) apply.execute(boxed).get();
        assertEquals("The same value returned", value, ret);
    }

    @Test
    public void testPrimitiveIdentityForeignObject() throws Exception {
        String id = identity();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        TruffleObject fn = JavaInterop.asTruffleFunction(LongBinaryOperation.class, new MaxMinObject(true));

        Object ret = apply.execute(fn).get();
        assertSameTruffleObject("The same value returned", fn, ret);
    }

    @Test
    public void testCoExistanceOfMultipleLanguageInstances() throws Exception {
        final String countMethod = countInvocations();
        PolyglotEngine.Value count1 = findGlobalSymbol(countMethod);
        PolyglotEngine vm1 = tckVM;
        tckVM = null; // clean-up
        PolyglotEngine.Value count2 = findGlobalSymbol(countMethod);
        PolyglotEngine vm2 = tckVM;

        assertNotSame("Two virtual machines allocated", vm1, vm2);

        int prev1 = 0;
        int prev2 = 0;
        for (int i = 0; i < 10; i++) {
            int quantum = RANDOM.nextInt(10);
            for (int j = 0; j < quantum; j++) {
                Object res = count1.execute().get();
                assert res instanceof Number : "expecting number: " + res;
                ++prev1;
                assert ((Number) res).intValue() == prev1 : "expecting " + prev1 + " but was " + res;
            }
            for (int j = 0; j < quantum; j++) {
                Object res = count2.execute().get();
                assert res instanceof Number : "expecting number: " + res;
                ++prev2;
                assert ((Number) res).intValue() == prev2 : "expecting " + prev2 + " but was " + res;
            }
            assert prev1 == prev2 : "At round " + i + " the same number of invocations " + prev1 + " vs. " + prev2;
        }
    }

    @Test
    public void testGlobalObjectIsAccessible() throws Exception {
        String globalObjectFunction = globalObject();
        if (globalObjectFunction == null) {
            return;
        }

        Language language = vm().getLanguages().get(mimeType());
        assertNotNull("Langugage for " + mimeType() + " found", language);

        PolyglotEngine.Value function = vm().findGlobalSymbol(globalObjectFunction);
        Object global = function.execute().get();
        assertEquals("Global from the language same with Java obtained one", language.getGlobalObject().get(), global);
    }

    @Test
    public void testEvaluateSource() throws Exception {
        Language language = vm().getLanguages().get(mimeType());
        assertNotNull("Langugage for " + mimeType() + " found", language);

        PolyglotEngine.Value function = vm().findGlobalSymbol(evaluateSource());
        assertNotNull(evaluateSource() + " found", function);

        double expect = Math.floor(RANDOM.nextDouble() * 100000.0) / 10.0;
        Object parsed = function.execute("application/x-tck", "" + expect).get();
        assertTrue("Expecting numeric result, was:" + expect, parsed instanceof Number);
        double value = ((Number) parsed).doubleValue();
        assertEquals("Gets the double", expect, value, 0.01);
    }

    @Test
    public void multiplyTwoVariables() throws Exception {
        final String firstVar = "var" + (char) ('A' + RANDOM.nextInt(24));
        final String secondVar = "var" + (char) ('0' + RANDOM.nextInt(10));
        String mulCode = multiplyCode(firstVar, secondVar);
        Source source = Source.fromText("TCK42:" + mimeType() + ":" + mulCode, "evaluate " + firstVar + " * " + secondVar).withMimeType("application/x-tck");
        final PolyglotEngine.Value evalSource = vm().eval(source);
        final PolyglotEngine.Value invokeMul = evalSource.execute(firstVar, secondVar);
        Object result = invokeMul.get();
        assertTrue("Expecting numeric result, was:" + result, result instanceof Number);
        assertEquals("Right value", 42, ((Number) result).intValue());
    }

    @Test
    public void testAddComplexNumbers() throws Exception {
        String id = complexAdd();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        ComplexNumber a = new ComplexNumber(32, 10);
        ComplexNumber b = new ComplexNumber(10, 32);

        apply.execute(a, b);

        assertEquals(42.0, a.get(ComplexNumber.REAL_IDENTIFIER), 0.1);
        assertEquals(42.0, a.get(ComplexNumber.IMAGINARY_IDENTIFIER), 0.1);
    }

    private PolyglotEngine.Value findGlobalSymbol(String name) throws Exception {
        PolyglotEngine.Value s = vm().findGlobalSymbol(name);
        assert s != null : "Symbol " + name + " is not found!";
        return s;
    }

    private CompoundObject findCompoundSymbol() throws Exception {
        final String compoundObjectName = compoundObject();
        PolyglotEngine.Value s = vm().findGlobalSymbol(compoundObjectName);
        assert s != null : "Symbol " + compoundObjectName + " is not found!";
        final PolyglotEngine.Value value = s.execute();
        CompoundObject obj = value.as(CompoundObject.class);
        assertNotNull("Compound object for " + value + " found", obj);
        int traverse = RANDOM.nextInt(10);
        for (int i = 1; i <= traverse; i++) {
            obj = obj.returnsThis();
            assertNotNull("Remains non-null even after " + i + " iteration", obj);
        }
        return obj;
    }

    private static void assertSameTruffleObject(String msg, Object expected, Object actual) {
        Object unExpected = unwrapTruffleObject(expected);
        Object unAction = unwrapTruffleObject(actual);
        assertSame(msg, unExpected, unAction);
    }

    private static Object unwrapTruffleObject(Object obj) {
        try {
            if (obj instanceof TruffleObject) {
                Class<?> eto = Class.forName("com.oracle.truffle.api.vm.EngineTruffleObject");
                if (eto.isInstance(obj)) {
                    final Field field = eto.getDeclaredField("delegate");
                    field.setAccessible(true);
                    return field.get(obj);
                }
            }
            return obj;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    interface CompoundObject {
        Number fourtyTwo();

        Number plus(int x, int y);

        Object returnsNull();

        CompoundObject returnsThis();
    }
}
