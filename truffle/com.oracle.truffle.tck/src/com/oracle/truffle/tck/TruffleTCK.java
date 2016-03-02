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

import com.oracle.truffle.tck.impl.LongBinaryOperation;
import com.oracle.truffle.tck.impl.ObjectBinaryOperation;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.ForeignAccess.Factory10;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.interop.java.MethodMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Language;
import com.oracle.truffle.tck.Schema.Type;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test compatibility kit (the <em>TCK</em>) is a collection of tests to certify your
 * {@link TruffleLanguage language implementation} compliance. If you want your language to be
 * compliant with most recent requirements of the Truffle infrastructure and tooling, subclass,
 * implement <b>protected</b> methods and include in your test suite:
 *
 * <pre>
 * <b>public class</b> MyLanguageTCKTest <b>extends</b> {@link TruffleTCK} {
 *   {@link Override @Override}
 *   <b>protected</b> {@link PolyglotEngine} {@link #prepareVM() prepareVM}() {
 *     <em>// create the engine</em>
 *     <em>// execute necessary scripts</em>
 *   }
 * 
 *   {@link Override @Override}
 *   <b>protected</b> {@link String} fourtyTwo() {
 *     <b>return</b> <em>// name of function that returns 42</em>
 *   }
 * 
 *   <em>// and so on...</em>
 * }
 * </pre>
 *
 * The <em>TCK</em> is carefully designed to accommodate differences between languages. The
 * <em>TCK</em> doesn't dictate what object your language is using to represent {@link Number
 * numbers} or {@link String strings} internally. The <em>TCK</em> doesn't prescribe the precise
 * type of values returned from your
 * {@link PolyglotEngine#eval(com.oracle.truffle.api.source.Source) language evaluations}. The tests
 * just assume that if the result is supposed to be a number, the returned value will be an instance
 * of {@link Number} or be {@link Message#UNBOX convertible} to a {@link Number} and keeps
 * sufficient level of precision. Similarly for {@link String strings}. As such the tests in the
 * <em>TCK</em> should be applicable to wide range of languages. Should there be a test that cannot
 * be implemented in your language, it can be suppressed by overriding its test method and doing
 * nothing:
 *
 * <pre>
 *   {@link Override @Override}
 *   <b>public void</b> {@link #testFortyTwo() testFortyTwo}() {
 *     <em>// do nothing</em>
 *   }
 * </pre>
 * <p>
 * The primary goal of the <em>TCK</em> is to ensure your {@link TruffleLanguage language
 * implementation} plays well with other languages in the {@link PolyglotEngine} - e.g. that data
 * can be exchanged between your and other languages. The {@link com.oracle.truffle.api.interop
 * interop package} defines what types of data can be interchanged between the languages and the
 * <em>TCK</em> does its best to make sure all these data are really accepted as an input on a
 * boundary of your {@link TruffleLanguage language implementation}. That doesn't mean such data
 * need to be used internally, many languages do conversions in their {@link Factory10 foreign
 * access} {@link RootNode nodes} to more suitable internal representation. Such conversion is fully
 * acceptable as nobody prescribes what is the actual type of output after executing a function/code
 * snippet in your language.
 * <p>
 * Should the <em>TCK</em> be found unsuitable for your {@link TruffleLanguage language
 * implementation} please speak-up (at <em>Truffle/Graal</em> mailing list for example) and we do
 * our best to analyze your case and adjust the <em>TCK</em> to suite everyone's needs.
 * 
 * @since 0.8 or earlier
 */
public abstract class TruffleTCK {
    private static final Random RANDOM = new Random();
    private PolyglotEngine tckVM;

    /** @since 0.8 or earlier */
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
     * @since 0.8 or earlier
     */
    protected abstract PolyglotEngine prepareVM() throws Exception;

    /**
     * MIME type associated with your language. The MIME type will be passed to
     * {@link PolyglotEngine#eval(com.oracle.truffle.api.source.Source)} method of the
     * {@link #prepareVM() created engine}.
     *
     * @return mime type of the tested language
     * @since 0.8 or earlier
     */
    protected abstract String mimeType();

    /**
     * Name of function which will return value 42 as a number. The return value of the method
     * should be instance of {@link Number} and its {@link Number#intValue()} should return
     * <code>42</code>.
     *
     * @return name of globally exported symbol
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
     */
    protected abstract String returnsNull();

    /**
     * Name of function to add two integer values together. The symbol will be invoked with two
     * parameters of type {@link Integer} and expects result of type {@link Number} which's
     * {@link Number#intValue()} is equivalent of <code>param1 + param2</code>.
     *
     * @return name of globally exported symbol
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
     */
    protected abstract String applyNumbers();

    /**
     * Name of identity function. The identity function accepts one argument and returns it. The
     * argument should go through without any modification, e.g. the input should result in
     * identical output.
     *
     * @return name of globally exported symbol
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
     */
    protected String complexAdd() {
        throw new UnsupportedOperationException("complexAdd() method not implemented");
    }

    /**
     * Name of a function that adds up two complex numbers using an add method of the first complex
     * number. The function accepts two arguments and provides no return value. The arguments are
     * complex numbers with members called real and imaginary. The first argument contains the
     * result of the addition.
     *
     * @return name of globally exported symbol
     * @since 0.8 or earlier
     */
    protected String complexAddWithMethod() {
        throw new UnsupportedOperationException("complexAddWithMethod() method not implemented");
    }

    /**
     * Name of a function that adds up the real part of complex numbers. The function accepts one
     * argument and provides the sum of all real parts. The argument is an array/buffer of complex
     * numbers.
     *
     * @return name of globally exported symbol, <code>null</code> if the test should be skipped
     * @since 0.8 or earlier
     */
    protected String complexSumReal() {
        throw new UnsupportedOperationException("complexSumReal() method not implemented");
    }

    /**
     * Name of a function that copies a list of complex numbers. The function accepts two arguments
     * and provides no return value. The arguments are two lists of complex numbers with members
     * called real and imaginary. The first argument is the destination, the second argument is the
     * source.
     *
     * @return name of globally exported symbol, <code>null</code> if the test should be skipped
     * @since 0.8 or earlier
     */
    protected String complexCopy() {
        throw new UnsupportedOperationException("complexCopy() method not implemented");
    }

    /**
     * Name of a function to return global object. The function can be executed without providing
     * any arguments and should return global object of the language, if the language supports it.
     * Global object is the one accessible via
     * {@link TruffleLanguage#getLanguageGlobal(java.lang.Object)}.
     *
     * @return name of globally exported symbol, return <code>null</code> if the language doesn't
     *         support the concept of global object
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
     */
    protected String evaluateSource() {
        throw new UnsupportedOperationException("evaluateSource() method not implemented");
    }

    /**
     * Code snippet to multiplyCode two two variables. The test uses the snippet as a parameter to
     * your language' s
     * {@link TruffleLanguage#parse(com.oracle.truffle.api.source.Source, com.oracle.truffle.api.nodes.Node, java.lang.String...)}
     * method.
     *
     * @param firstName name of the first variable to multiplyCode
     * @param secondName name of the second variable to multiplyCode
     * @return code snippet that multiplies the two variables in your language
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
     */
    protected abstract String countInvocations();

    /**
     * Return a code snippet that is invalid in your language. Its
     * {@link PolyglotEngine#eval(com.oracle.truffle.api.source.Source) evaluation} should fail and
     * yield an exception.
     *
     * @return code snippet invalid in the tested language
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
     */
    protected String compoundObject() {
        throw new UnsupportedOperationException("compoundObject() method not implemented");
    }

    /**
     * Name of a function that returns a compound object with members representing certain primitive
     * types. In the JavaScript the object should look like:
     *
     * <pre>
     * <b>var</b> obj = {
     *   'byteValue': 0,
     *   'shortValue': 0,
     *   'intValue': 0,
     *   'longValue': 0,
     *   'floatValue': 0.0,
     *   'doubleValue': 0.0,
     *   'charValue': '0',
     *   'stringValue': '',
     *   'booleanVlaue': false
     * };
     * <b>return</b> obj;
     * </pre>
     *
     * The returned object shall have slots for these values that can be read and written to.
     * Various test methods try to read and modify the values. Each invocation of the function
     * should yield new object.
     *
     * @return name of a function that returns such values object
     * @since 0.8 or earlier
     */
    protected String valuesObject() {
        throw new UnsupportedOperationException("valuesObject() method not implemented");
    }

    /**
     * Assert two double values are the same. Various languages may have different semantics with
     * respect to double numbers. Some of the language may not support <b>double</b> or <b>float</b>
     * values at all. Those languages may override this method and compare the values with as much
     * precision as they like.
     * <p>
     * Default implementation of this method calls
     * {@link Assert#assertEquals(java.lang.String, double, double, double)} with delta
     * <code>0.1</code>.
     *
     * @param msg assertion message to display in case of error
     * @param expectedValue the value expected by the test
     * @param actualValue the real value produced by the language
     * @throws AssertionError if the values are different according to the language semantics
     * @since 0.8 or earlier
     */
    protected void assertDouble(String msg, double expectedValue, double actualValue) {
        assertEquals(msg, expectedValue, actualValue, 0.1);
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
    /** @since 0.8 or earlier */
    @Test
    public void testFortyTwo() throws Exception {
        PolyglotEngine.Value fourtyTwo = findGlobalSymbol(fourtyTwo());

        Object res = fourtyTwo.execute().get();

        assert res instanceof Number : "should yield a number, but was: " + res;

        Number n = (Number) res;

        assert 42 == n.intValue() : "The value is 42 =  " + n.intValue();
    }

    /** @since 0.8 or earlier */
    @Test
    public void testFortyTwoWithCompoundObject() throws Exception {
        CompoundObject obj = findCompoundSymbol();
        if (obj == null) {
            return;
        }
        Number res = obj.fourtyTwo();
        assertEquals("Should be 42", 42, res.intValue());
    }

    /** @since 0.8 or earlier */
    @Test
    public void testNull() throws Exception {
        PolyglotEngine.Value retNull = findGlobalSymbol(returnsNull());

        Object res = retNull.execute().get();

        assertNull("Should yield real Java null", res);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testNullCanBeCastToAnything() throws Exception {
        PolyglotEngine.Value retNull = findGlobalSymbol(returnsNull());

        Object res = retNull.execute().as(CompoundObject.class);

        assertNull("Should yield real Java null", res);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testNullInCompoundObject() throws Exception {
        CompoundObject obj = findCompoundSymbol();
        if (obj == null) {
            return;
        }
        Object res = obj.returnsNull();
        assertNull("Should yield real Java null", res);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithInts() throws Exception {
        int a = RANDOM.nextInt(100);
        int b = RANDOM.nextInt(100);
        doPlusWithInts(a, b);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithOneNegativeInt() throws Exception {
        int a = -RANDOM.nextInt(100);
        int b = RANDOM.nextInt(100);
        doPlusWithInts(a, b);
    }

    private void doPlusWithInts(int a, int b) throws Exception {
        PolyglotEngine.Value plus = findGlobalSymbol(plus(int.class, int.class));

        Number n = plus.execute(a, b).as(Number.class);
        assert a + b == n.intValue() : "The value is correct: (" + a + " + " + b + ") =  " + n.intValue();
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithBytes() throws Exception {
        int a = RANDOM.nextInt(100);
        int b = RANDOM.nextInt(100);
        doPlusWithBytes(a, b);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithOneNegativeByte() throws Exception {
        int a = -RANDOM.nextInt(100);
        int b = RANDOM.nextInt(100);
        doPlusWithBytes(a, b);
    }

    private void doPlusWithBytes(int a, int b) throws Exception {
        PolyglotEngine.Value plus = findGlobalSymbol(plus(byte.class, byte.class));

        Number n = plus.execute((byte) a, (byte) b).as(Number.class);
        assert a + b == n.intValue() : "The value is correct: (" + a + " + " + b + ") =  " + n.intValue();
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithShort() throws Exception {
        int a = RANDOM.nextInt(100);
        int b = RANDOM.nextInt(100);
        doPlusWithShorts(a, b);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithOneNegativeShort() throws Exception {
        int a = RANDOM.nextInt(100);
        int b = -RANDOM.nextInt(100);
        doPlusWithShorts(a, b);
    }

    private void doPlusWithShorts(int a, int b) throws Exception {
        PolyglotEngine.Value plus = findGlobalSymbol(plus(short.class, short.class));

        Number n = plus.execute((short) a, (short) b).as(Number.class);
        assert a + b == n.intValue() : "The value is correct: (" + a + " + " + b + ") =  " + n.intValue();
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithLong() throws Exception {
        long a = RANDOM.nextInt(100);
        long b = RANDOM.nextInt(100);
        doPlusWithLong(a, b);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithLongMaxIntMinInt() throws Exception {
        doPlusWithLong(Integer.MAX_VALUE, Integer.MIN_VALUE);
    }

    private void doPlusWithLong(long a, long b) throws Exception {
        PolyglotEngine.Value plus = findGlobalSymbol(plus(long.class, long.class));

        Number n = plus.execute(a, b).as(Number.class);
        assert a + b == n.longValue() : "The value is correct: (" + a + " + " + b + ") =  " + n.longValue();
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithDoubleFloatSameAsInt() throws Exception {
        int x = RANDOM.nextInt(100);
        int y = RANDOM.nextInt(100);
        float a = x;
        float b = y;
        double u = a;
        double v = b;

        PolyglotEngine.Value floatPlus = findGlobalSymbol(plus(float.class, float.class));
        PolyglotEngine.Value doublePlus = findGlobalSymbol(plus(double.class, double.class));
        PolyglotEngine.Value intPlus = findGlobalSymbol(plus(int.class, int.class));

        Number floatResult = floatPlus.execute(a, b).as(Number.class);
        Number doubleResult = doublePlus.execute(u, v).as(Number.class);
        Number intResult = intPlus.execute(x, y).as(Number.class);

        assertEquals("Correct value computed via int: (" + a + " + " + b + ")", x + y, intResult.intValue());
        assertEquals("Correct value computed via float: (" + a + " + " + b + ")", intResult.intValue(), floatResult.intValue());
        assertEquals("Correct value computed via double: (" + a + " + " + b + ")", intResult.intValue(), doubleResult.intValue());
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithFloat() throws Exception {
        float a = RANDOM.nextFloat() * 100.0f;
        float b = RANDOM.nextFloat() * 100.0f;

        doPlusWithFloat(a, b);
    }

    private void doPlusWithFloat(float a, float b) throws Exception {
        PolyglotEngine.Value plus = findGlobalSymbol(plus(float.class, float.class));

        Number n = plus.execute(a, b).as(Number.class);
        assertDouble("Correct value computed: (" + a + " + " + b + ")", a + b, n.floatValue());
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithDouble() throws Exception {
        double a = RANDOM.nextDouble() * 100.0;
        double b = RANDOM.nextDouble() * 100.0;
        doPlusWithDouble(a, b);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithDoubleRound() throws Exception {
        double a = RANDOM.nextInt(1000);
        double b = RANDOM.nextInt(1000);

        doPlusWithDouble(a, b);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithDoubleMaxInt() throws Exception {
        double a = Integer.MAX_VALUE;
        double b = 1;

        doPlusWithDouble(a, b);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithDoubleMaxMinInt() throws Exception {
        double a = Integer.MAX_VALUE;
        double b = Integer.MIN_VALUE;

        doPlusWithDouble(a, b);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithDoubleMinIntMinusOne() throws Exception {
        double a = -1;
        double b = Integer.MIN_VALUE;

        doPlusWithDouble(a, b);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithDoubleMaxIntPlusOne() throws Exception {
        double a = 1;
        double b = Integer.MAX_VALUE;

        doPlusWithDouble(a, b);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithDoubleNaNPlusNegInf() throws Exception {
        double a = Double.NaN;
        double b = Double.NEGATIVE_INFINITY;

        doPlusWithDouble(a, b);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithDoubleNaNPlusPosInf() throws Exception {
        double a = Double.NaN;
        double b = Double.POSITIVE_INFINITY;

        doPlusWithDouble(a, b);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithDoubleMaxIntPlusPosInf() throws Exception {
        double a = Integer.MAX_VALUE;
        double b = Double.POSITIVE_INFINITY;

        doPlusWithDouble(a, b);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPlusWithDoubleMaxIntPlusNegInf() throws Exception {
        double a = Integer.MAX_VALUE;
        double b = Double.NEGATIVE_INFINITY;

        doPlusWithDouble(a, b);
    }

    private void doPlusWithDouble(double a, double b) throws Exception {
        PolyglotEngine.Value plus = findGlobalSymbol(plus(double.class, double.class));

        Number n = plus.execute(a, b).as(Number.class);
        assertDouble("Correct value computed: (" + a + " + " + b + ")", a + b, n.doubleValue());
    }

    /** @since 0.8 or earlier */
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

    /** @since 0.8 or earlier */
    @Test(expected = IOException.class)
    public void testInvalidTestMethod() throws Exception {
        String mime = mimeType();
        String code = invalidCode();
        Object ret = vm().eval(Source.fromText(code, "Invalid code").withMimeType(mime)).get();
        fail("Should yield IOException, but returned " + ret);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testMaxOrMinValue() throws Exception {
        PolyglotEngine.Value apply = findGlobalSymbol(applyNumbers());

        TruffleObject fn = JavaInterop.asTruffleFunction(LongBinaryOperation.class, new MaxMinObject(true));
        Object res = apply.execute(fn).get();

        assert res instanceof Number : "result should be a number: " + res;

        Number n = (Number) res;

        assert 42 == n.intValue() : "32 > 18 and plus 10";
    }

    /** @since 0.8 or earlier */
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

    /** @since 0.8 or earlier */
    @Test
    public void testPrimitiveReturnTypeByte() throws Exception {
        PolyglotEngine.Value apply = findGlobalSymbol(applyNumbers());

        byte value = (byte) RANDOM.nextInt(100);

        TruffleObject fn = JavaInterop.asTruffleFunction(ObjectBinaryOperation.class, new ConstantFunction(value));
        Number n = apply.execute(fn).as(Number.class);
        assertEquals("The same value returned (" + value + " + 10): ", value + 10, n.byteValue());
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPrimitiveReturnTypeShort() throws Exception {
        PolyglotEngine.Value apply = findGlobalSymbol(applyNumbers());

        short value = (short) RANDOM.nextInt(100);

        TruffleObject fn = JavaInterop.asTruffleFunction(ObjectBinaryOperation.class, new ConstantFunction(value));
        Number n = apply.execute(fn).as(Number.class);
        assertEquals("The same value returned (" + value + " + 10): ", value + 10, n.shortValue());
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPrimitiveReturnTypeInt() throws Exception {
        PolyglotEngine.Value apply = findGlobalSymbol(applyNumbers());

        int value = RANDOM.nextInt(100);

        TruffleObject fn = JavaInterop.asTruffleFunction(ObjectBinaryOperation.class, new ConstantFunction(value));
        Number n = apply.execute(fn).as(Number.class);
        assertEquals("The same value returned (" + value + " + 10): ", value + 10, n.intValue());
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPrimitiveReturnTypeLong() throws Exception {
        PolyglotEngine.Value apply = findGlobalSymbol(applyNumbers());

        long value = RANDOM.nextInt(1000);

        TruffleObject fn = JavaInterop.asTruffleFunction(ObjectBinaryOperation.class, new ConstantFunction(value));
        Number n = apply.execute(fn).as(Number.class);
        assertEquals("The same value returned (" + value + " + 10): ", value + 10, n.longValue());
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPrimitiveReturnTypeFloat() throws Exception {
        PolyglotEngine.Value apply = findGlobalSymbol(applyNumbers());

        float value = RANDOM.nextInt(1000) + RANDOM.nextFloat();

        TruffleObject fn = JavaInterop.asTruffleFunction(ObjectBinaryOperation.class, new ConstantFunction(value));
        Number n = apply.execute(fn).as(Number.class);
        assertDouble("The same value returned (" + value + " + 10): ", value + 10, n.floatValue());
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPrimitiveReturnTypeDouble() throws Exception {
        PolyglotEngine.Value apply = findGlobalSymbol(applyNumbers());

        double value = RANDOM.nextInt(1000) + RANDOM.nextDouble();

        TruffleObject fn = JavaInterop.asTruffleFunction(ObjectBinaryOperation.class, new ConstantFunction(value));
        Number n = apply.execute(fn).as(Number.class);
        assertDouble("The same value returned (" + value + " + 10): ", value + 10, n.doubleValue());
    }

    /** @since 0.8 or earlier */
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

    /** @since 0.8 or earlier */
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

    /** @since 0.8 or earlier */
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

    /** @since 0.8 or earlier */
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

    /** @since 0.8 or earlier */
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

    /** @since 0.8 or earlier */
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

    /** @since 0.8 or earlier */
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

    /** @since 0.8 or earlier */
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

    /** @since 0.8 or earlier */
    @Test
    public void testPrimitiveidentityFloat() throws Exception {
        String id = identity();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        float value = RANDOM.nextInt(1000) + RANDOM.nextFloat();

        Number n = (Number) apply.execute(value).get();
        assertDouble("The same value returned", value, n.floatValue());
    }

    /** @since 0.8 or earlier */
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
        assertDouble("The same value returned", value, n.floatValue());
    }

    /** @since 0.8 or earlier */
    @Test
    public void testPrimitiveidentityDouble() throws Exception {
        String id = identity();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        double value = RANDOM.nextInt(1000) + RANDOM.nextDouble();

        Number n = (Number) apply.execute(value).get();
        assertDouble("The same value returned", value, n.doubleValue());
    }

    /** @since 0.8 or earlier */
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
        assertDouble("The same value returned", value, n.doubleValue());
    }

    /** @since 0.8 or earlier */
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

    /** @since 0.8 or earlier */
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

    /** @since 0.8 or earlier */
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

    /** @since 0.8 or earlier */
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
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            int quantum = RANDOM.nextInt(10);
            log.append("quantum" + i + " is " + quantum + "\n");
            for (int j = 0; j < quantum; j++) {
                Object res = count1.execute().get();
                assert res instanceof Number : "expecting number: " + res + "\n" + log;
                ++prev1;
                assert ((Number) res).intValue() == prev1 : "expecting " + prev1 + " but was " + res + "\n" + log;
            }
            for (int j = 0; j < quantum; j++) {
                Object res = count2.execute().get();
                assert res instanceof Number : "expecting number: " + res + "\n" + log;
                ++prev2;
                assert ((Number) res).intValue() == prev2 : "expecting " + prev2 + " but was " + res + "\n" + log;
            }
            assert prev1 == prev2 : "At round " + i + " the same number of invocations " + prev1 + " vs. " + prev2 + "\n" + log;
        }
    }

    /** @since 0.8 or earlier */
    @Test
    public void testGlobalObjectIsAccessible() throws Exception {
        String globalObjectFunction = globalObject();
        if (globalObjectFunction == null) {
            return;
        }

        Language language = vm().getLanguages().get(mimeType());
        assertNotNull("Language for " + mimeType() + " found", language);

        PolyglotEngine.Value function = vm().findGlobalSymbol(globalObjectFunction);
        Object global = function.execute().get();
        assertEquals("Global from the language same with Java obtained one", language.getGlobalObject().get(), global);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testEvaluateSource() throws Exception {
        Language language = vm().getLanguages().get(mimeType());
        assertNotNull("Language for " + mimeType() + " found", language);

        PolyglotEngine.Value function = vm().findGlobalSymbol(evaluateSource());
        assertNotNull(evaluateSource() + " found", function);

        double expect = Math.floor(RANDOM.nextDouble() * 100000.0) / 10.0;
        Object parsed = function.execute("application/x-tck", "" + expect).get();
        assertTrue("Expecting numeric result, was:" + expect, parsed instanceof Number);
        double value = ((Number) parsed).doubleValue();
        assertDouble("Gets the double", expect, value);
    }

    /** @since 0.8 or earlier */
    @Test
    public void multiplyTwoVariables() throws Exception {
        final String firstVar = "var" + (char) ('A' + RANDOM.nextInt(24));
        final String secondVar = "var" + (char) ('0' + RANDOM.nextInt(10));
        String mulCode = multiplyCode(firstVar, secondVar);
        Source source = Source.fromText("TCK42:" + mimeType() + ":" + mulCode, "evaluate " + firstVar + " * " + secondVar).withMimeType("application/x-tck");
        final PolyglotEngine.Value evalSource = vm().eval(source);
        final PolyglotEngine.Value invokeMul = evalSource.execute(firstVar, secondVar);
        Object result = invokeMul.get();
        assertTrue("Expecting numeric result, was:" + result + " for " + firstVar + " and " + secondVar, result instanceof Number);
        assertEquals("Right value for " + firstVar + " and " + secondVar, 42, ((Number) result).intValue());
    }

    /** @since 0.8 or earlier */
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

    /** @since 0.8 or earlier */
    @Test
    public void testAddComplexNumbersWithMethod() throws Exception {
        String id = complexAddWithMethod();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        ComplexNumber a = new ComplexNumber(32, 10);
        ComplexNumber b = new ComplexNumber(10, 32);

        apply.execute(a, b);

        assertDouble("The same value returned", 42.0, a.get(ComplexNumber.REAL_IDENTIFIER));
        assertDouble("The same value returned", 42.0, a.get(ComplexNumber.IMAGINARY_IDENTIFIER));
    }

    /** @since 0.8 or earlier */
    @Test
    public void testSumRealOfComplexNumbersA() throws Exception {
        String id = complexSumReal();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        ComplexNumbersA numbers = new ComplexNumbersA(new double[]{2, -1, 30, -1, 10, -1});

        Number n = (Number) apply.execute(numbers).get();
        assertDouble("The same value returned", 42.0, n.doubleValue());
    }

    /** @since 0.8 or earlier */
    @Test
    public void testSumRealOfComplexNumbersB() throws Exception {
        String id = complexSumReal();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        ComplexNumbersB numbers = new ComplexNumbersB(new double[]{2, 30, 10}, new double[]{-1, -1, -1});

        Number n = (Number) apply.execute(numbers).get();
        assertDouble("The same value returned", 42.0, n.doubleValue());
    }

    /** @since 0.8 or earlier */
    @Test
    public void testSumRealOfComplexNumbersAsStructuredDataRowBased() throws Exception {
        String id = complexSumReal();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        Schema schema = new Schema(3, true, Arrays.asList(ComplexNumber.REAL_IDENTIFIER, ComplexNumber.IMAGINARY_IDENTIFIER), Arrays.asList(Type.DOUBLE, Type.DOUBLE));
        byte[] buffer = new byte[(6 * Double.SIZE / Byte.SIZE)];
        putDoubles(buffer, new double[]{2, -1, 30, -1, 10, -1});
        StructuredData numbers = new StructuredData(buffer, schema);

        Number n = (Number) apply.execute(numbers).get();
        assertDouble("The same value returned", 42.0, n.doubleValue());
    }

    /** @since 0.8 or earlier */
    @Test
    public void testSumRealOfComplexNumbersAsStructuredDataColumnBased() throws Exception {
        String id = complexSumReal();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        Schema schema = new Schema(3, false, Arrays.asList(ComplexNumber.REAL_IDENTIFIER, ComplexNumber.IMAGINARY_IDENTIFIER), Arrays.asList(Type.DOUBLE, Type.DOUBLE));
        byte[] buffer = new byte[6 * Double.SIZE / Byte.SIZE];
        putDoubles(buffer, new double[]{2, 30, 10, -1, -1, -1});

        StructuredData numbers = new StructuredData(buffer, schema);

        Number n = (Number) apply.execute(numbers).get();
        assertDouble("The same value returned", 42.0, n.doubleValue());
    }

    /** @since 0.8 or earlier */
    @Test
    public void testCopyComplexNumbersA() throws Exception {
        String id = complexCopy();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        ComplexNumbersA a = new ComplexNumbersA(new double[]{-1, -1, -1, -1, -1, -1});
        ComplexNumbersA b = new ComplexNumbersA(new double[]{41, 42, 43, 44, 45, 46});

        apply.execute(a, b);

        Assert.assertArrayEquals(new double[]{41, 42, 43, 44, 45, 46}, a.getData(), 0.1);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testCopyComplexNumbersB() throws Exception {
        String id = complexCopy();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        ComplexNumbersB a = new ComplexNumbersB(new double[]{-1, -1, -1}, new double[]{-1, -1, -1});
        ComplexNumbersB b = new ComplexNumbersB(new double[]{41, 43, 45}, new double[]{42, 44, 46});

        apply.execute(a, b);

        Assert.assertArrayEquals(new double[]{41, 42, 43, 44, 45, 46}, a.getData(), 0.1);
    }

    /** @since 0.8 or earlier */
    @Test
    public void testCopyStructuredComplexToComplexNumbersA() throws Exception {
        String id = complexCopy();
        if (id == null) {
            return;
        }
        PolyglotEngine.Value apply = findGlobalSymbol(id);

        ComplexNumbersA a = new ComplexNumbersA(new double[]{-1, -1, -1, -1, -1, -1});

        Schema schema = new Schema(3, true, Arrays.asList(ComplexNumber.REAL_IDENTIFIER, ComplexNumber.IMAGINARY_IDENTIFIER), Arrays.asList(Type.DOUBLE, Type.DOUBLE));
        byte[] buffer = new byte[6 * Double.SIZE / Byte.SIZE];
        putDoubles(buffer, new double[]{41, 42, 43, 44, 45, 46});

        StructuredData b = new StructuredData(buffer, schema);

        apply.execute(a, b);

        Assert.assertArrayEquals(new double[]{41, 42, 43, 44, 45, 46}, a.getData(), 0.1);
    }

    /** @since 0.8 or earlier */
    @Test
    public void readWriteByteValue() throws Exception {
        String id = valuesObject();
        ValuesObject values = findGlobalSymbol(id).execute().as(ValuesObject.class);
        assertEquals("Zero", 0, values.byteValue());
        final byte value = (byte) RANDOM.nextInt(128);
        values.byteValue(value);
        assertEquals("Correct value", value, values.byteValue());
    }

    /** @since 0.8 or earlier */
    @Test
    public void readWriteShortValue() throws Exception {
        String id = valuesObject();
        ValuesObject values = findGlobalSymbol(id).execute().as(ValuesObject.class);
        assertEquals("Zero", 0, values.shortValue());
        final short value = (short) RANDOM.nextInt(32768);
        values.shortValue(value);
        assertEquals("Correct value", value, values.shortValue());
    }

    /** @since 0.8 or earlier */
    @Test
    public void readWriteIntValue() throws Exception {
        String id = valuesObject();
        ValuesObject values = findGlobalSymbol(id).execute().as(ValuesObject.class);
        assertEquals("Zero", 0, values.intValue());
        final int value = RANDOM.nextInt();
        values.intValue(value);
        assertEquals("Correct value", value, values.intValue());
    }

    /** @since 0.8 or earlier */
    @Test
    public void readWriteFloatValue() throws Exception {
        String id = valuesObject();
        ValuesObject values = findGlobalSymbol(id).execute().as(ValuesObject.class);
        assertDouble("Zero", 0, values.floatValue());
        final float value = RANDOM.nextFloat() * 1000.0f;
        values.floatValue(value);
        assertDouble("Correct value", value, values.floatValue());
    }

    /** @since 0.8 or earlier */
    @Test
    public void readWriteDoubleValue() throws Exception {
        String id = valuesObject();
        ValuesObject values = findGlobalSymbol(id).execute().as(ValuesObject.class);
        assertDouble("Zero", 0, values.doubleValue());
        final double value = RANDOM.nextDouble() * 1000.0;
        values.doubleValue(value);
        assertDouble("Correct value", value, values.doubleValue());
    }

    /** @since 0.8 or earlier */
    @Test
    public void readWriteCharValue() throws Exception {
        String id = valuesObject();
        ValuesObject values = findGlobalSymbol(id).execute().as(ValuesObject.class);
        assertEquals("Zero", '0', values.charValue());
        String letters = "P\u0159\u00EDli\u0161 \u017Elu\u0165ou\u010Dk\u00FD k\u016F\u0148 \u00FAp\u011Bl \u010F\u00E1belsk\u00E9 \u00F3dy";
        final char value = letters.charAt(RANDOM.nextInt(letters.length()));
        values.charValue(value);
        assertEquals("Correct value", value, values.charValue());
    }

    /** @since 0.8 or earlier */
    @Test
    public void readWriteBooleanValue() throws Exception {
        String id = valuesObject();
        ValuesObject values = findGlobalSymbol(id).execute().as(ValuesObject.class);
        assertEquals("False", false, values.booleanValue());
        values.booleanValue(true);
        assertEquals("Correct value", true, values.booleanValue());
        values.booleanValue(false);
        assertEquals("Correct value2", false, values.booleanValue());
    }

    private static void putDoubles(byte[] buffer, double[] values) {
        for (int index = 0; index < values.length; index++) {
            int doubleSize = Double.SIZE / Byte.SIZE;
            byte[] bytes = new byte[doubleSize];
            ByteBuffer.wrap(bytes).putDouble(values[index]);
            for (int i = 0; i < doubleSize; i++) {
                buffer[index * doubleSize + i] = bytes[i];
            }
        }
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

    interface ValuesObject {
        byte byteValue();

        @MethodMessage(message = "WRITE")
        void byteValue(byte v);

        short shortValue();

        @MethodMessage(message = "WRITE")
        void shortValue(short v);

        int intValue();

        @MethodMessage(message = "WRITE")
        void intValue(int v);

        long longValue();

        @MethodMessage(message = "WRITE")
        void longValue(long v);

        float floatValue();

        @MethodMessage(message = "WRITE")
        void floatValue(float v);

        double doubleValue();

        @MethodMessage(message = "WRITE")
        void doubleValue(double v);

        char charValue();

        @MethodMessage(message = "WRITE")
        void charValue(char v);

        boolean booleanValue();

        @MethodMessage(message = "WRITE")
        void booleanValue(boolean v);
    }
}
