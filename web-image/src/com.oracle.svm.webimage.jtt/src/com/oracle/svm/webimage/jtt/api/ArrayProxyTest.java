/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.jtt.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSBigInt;
import org.graalvm.webimage.api.JSBoolean;
import org.graalvm.webimage.api.JSNumber;
import org.graalvm.webimage.api.JSObject;
import org.graalvm.webimage.api.JSString;
import org.graalvm.webimage.api.JSUndefined;
import org.graalvm.webimage.api.JSValue;
import org.graalvm.webimage.api.ThrownFromJavaScript;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;

public class ArrayProxyTest {
    public static void main(String[] args) {
        checkNoCoercion();
        System.out.println("checkNoCoercion DONE");
        checkArrayLoad();
        System.out.println("checkArrayLoad DONE");
        checkArrayStore();
        System.out.println("checkArrayStore DONE");
        checkArrayStoreMismatchedType();
        System.out.println("checkArrayStoreMismatchedType DONE");
        checkProxyIterator();
        System.out.println("checkProxyIterator DONE");
        checkProxyJSIteration();
        System.out.println("checkProxyJSIteration DONE");
    }

    /// Checks that Java arrays don't undergo coercion to JS array types.
    public static void checkNoCoercion() {
        assertNotCoercedToArrayOrTyped(new Object[1]);
        assertNotCoercedToArrayOrTyped(new boolean[2]);
        assertNotCoercedToArrayOrTyped(new short[3]);
        assertNotCoercedToArrayOrTyped(new char[4]);
        assertNotCoercedToArrayOrTyped(new int[5]);
        assertNotCoercedToArrayOrTyped(new long[6]);
        assertNotCoercedToArrayOrTyped(new float[7]);
        assertNotCoercedToArrayOrTyped(new double[8]);
    }

    /// Checks proper behavior of loads from Java primitive arrays in JS code.
    ///
    /// Loads from primitive arrays should undergo coercion and return a JS value.
    public static void checkArrayLoad() {
        boolean[] boolArray = new boolean[]{true, false, true};
        assertOutOfRangeLoadsAreUndefined(boolArray);
        for (int i = 0; i < boolArray.length; i++) {
            JSBoolean bool = assertInstanceOf(JSBoolean.class, arrayGet(boolArray, i), "Wrong type at index " + i + " for boolean array");
            assertEquals(boolArray[i], bool.asBoolean(), "at index " + i + " for boolean array");
        }

        short[] shortArray = new short[]{-1, 12, 0};
        assertOutOfRangeLoadsAreUndefined(shortArray);
        for (int i = 0; i < shortArray.length; i++) {
            JSNumber n = assertInstanceOf(JSNumber.class, arrayGet(shortArray, i), "Wrong type at index " + i + " for short array");
            assertEquals(shortArray[i], n.asShort(), "at index " + i + " for short array");
        }

        char[] charArray = new char[]{'a', '\n', 0};
        assertOutOfRangeLoadsAreUndefined(charArray);
        for (int i = 0; i < charArray.length; i++) {
            JSNumber n = assertInstanceOf(JSNumber.class, arrayGet(charArray, i), "Wrong type at index " + i + " for char array");
            assertEquals(charArray[i], n.asChar(), "at index " + i + " for char array");
        }

        int[] intArray = new int[]{1, 2, 3, Integer.MAX_VALUE};
        assertOutOfRangeLoadsAreUndefined(intArray);
        for (int i = 0; i < intArray.length; i++) {
            JSNumber num = assertInstanceOf(JSNumber.class, arrayGet(intArray, i), "Wrong type at index " + i + " for int array");
            assertEquals(intArray[i], num.asInt(), "at index " + i + " for int array");
        }

        long[] longArray = new long[]{-1, 12, Long.MIN_VALUE, Long.MAX_VALUE};
        assertOutOfRangeLoadsAreUndefined(longArray);
        for (int i = 0; i < longArray.length; i++) {
            JSBigInt bool = assertInstanceOf(JSBigInt.class, arrayGet(longArray, i), "Wrong type at index " + i + " for long array");
            assertEquals(longArray[i], bool.asLong(), "at index " + i + " for long array");
        }

        float[] floatArray = new float[]{Float.NaN, 1.2233450123572345f, -1, Float.NEGATIVE_INFINITY, Float.MIN_NORMAL, Float.MAX_VALUE, Float.MIN_VALUE};
        assertOutOfRangeLoadsAreUndefined(floatArray);
        for (int i = 0; i < floatArray.length; i++) {
            JSNumber n = assertInstanceOf(JSNumber.class, arrayGet(floatArray, i), "Wrong type at index " + i + " for float array");
            assertEquals(floatArray[i], n.asFloat(), "at index " + i + " for float array");
        }

        double[] doubleArray = new double[]{Double.NaN, 1.2233450123572345, -1, Double.NEGATIVE_INFINITY, Double.MIN_NORMAL, Double.MAX_VALUE, Double.MIN_VALUE};
        assertOutOfRangeLoadsAreUndefined(doubleArray);
        for (int i = 0; i < doubleArray.length; i++) {
            JSNumber n = assertInstanceOf(JSNumber.class, arrayGet(doubleArray, i), "Wrong type at index " + i + " for double array");
            assertEquals(doubleArray[i], n.asDouble(), "at index " + i + " for double array");
        }
    }

    /// Checks proper behavior when storing into Java primitive array in JS code.
    ///
    /// Any number of BigInt value that's in range for the target type should be allowed.
    public static void checkArrayStore() {
        boolean[] boolArray = new boolean[]{true, false, true};
        arraySet(boolArray, 0, JSBoolean.of(false));
        arraySet(boolArray, 1, JSBoolean.of(true));
        arraySet(boolArray, 2, JSBoolean.of(false));
        assertArrayEquals(new boolean[]{false, true, false}, boolArray, "Stores did not take effect in boolean array");

        short[] shortArray = new short[]{1, 2, 3, 4};
        short[] originalShortArray = shortArray.clone();
        arraySet(shortArray, -1, JSNumber.of(12));
        arraySet(shortArray, shortArray.length, JSNumber.of(33));
        assertArrayEquals(originalShortArray, shortArray, "short array was changed by out of bounds store");
        arraySet(shortArray, 0, JSNumber.of(5));
        // Arrays should also accept in-range BigInt value
        arraySet(shortArray, 1, JSBigInt.of(12));
        arraySet(shortArray, 2, JSBigInt.of(Short.MAX_VALUE));
        assertArrayEquals(new short[]{5, 12, Short.MAX_VALUE, 4}, shortArray, "Stores did not take effect in short array");

        char[] charArray = new char[]{0, 12, 99};
        arraySet(charArray, 0, JSNumber.of(Character.MAX_VALUE));
        arraySet(charArray, 1, JSNumber.of(0));
        arraySet(charArray, 2, JSBigInt.of(128));
        assertArrayEquals(new char[]{Character.MAX_VALUE, 0, 128}, charArray, "Stores did not take effect in char array");

        int[] intArray = new int[]{0, 12, 99};
        arraySet(intArray, 0, JSNumber.of(Integer.MAX_VALUE));
        arraySet(intArray, 1, JSNumber.of(Integer.MIN_VALUE));
        arraySet(intArray, 2, JSBigInt.of(128));
        assertArrayEquals(new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE, 128}, intArray, "Stores did not take effect in int array");

        long[] longArray = new long[]{1, 2, 3, 4};
        arraySet(longArray, 0, JSBigInt.of(12));
        // Long arrays should also accept integer values
        arraySet(longArray, 1, JSNumber.of(-100));
        arraySet(longArray, 2, JSNumber.of(12));
        assertArrayEquals(new long[]{12, -100, 12, 4}, longArray, "Stores did not take effect in long array");

        float[] floatArray = new float[]{1, 2, 3, 4};
        arraySet(floatArray, 0, JSNumber.of(1.2f));
        // Floating point arrays should accept all BigInt values with potential loss of precision
        arraySet(floatArray, 1, JSBigInt.of(-100));
        // This is the first integer that a 32-bit float can't accurately represent, should lose
        // precision when stored
        BigInteger largeBigIntFloat = BigInteger.valueOf(16_777_217);
        arraySet(floatArray, 2, JSBigInt.of(largeBigIntFloat));
        assertArrayEquals(new float[]{1.2f, -100, largeBigIntFloat.floatValue(), 4}, floatArray, "Stores did not take effect in float array");

        double[] doubleArray = new double[]{1, 2, 3, 4};
        arraySet(doubleArray, 0, JSNumber.of(1.2));
        // doubleing point arrays should accept all BigInt values with potential loss of precision
        arraySet(doubleArray, 1, JSBigInt.of(-100));
        // This is the first integer that a 64-bit double can't accurately represent, should lose
        // precision when stored
        BigInteger largeBigIntDouble = BigInteger.valueOf(JSNumber.maxSafeInteger() + 1);
        arraySet(doubleArray, 2, JSBigInt.of(largeBigIntDouble));
        assertArrayEquals(new double[]{1.2d, -100, largeBigIntDouble.doubleValue(), 4}, doubleArray, "Stores did not take effect in double array");
    }

    /// Checks that the proper errors are thrown when inserting incompatible JS values into Java
    /// primitive arrays.
    ///
    /// The store should throw a JS `RangeError` whenever the inserted value is not inside the range
    /// of valid types of the target type. For integer types that are out of range or non-integer
    /// floating point values. `float` and `double` arrays accept any number value, though
    /// possibly with a loss of precision.
    public static void checkArrayStoreMismatchedType() {
        boolean[] boolArray = new boolean[]{true};
        int[] intArray = new int[]{12};
        long[] longArray = new long[]{Long.MAX_VALUE};

        assertArraySetThrowsError("TypeError", "Invalid type number for insertion into boolean array", boolArray, 0, JSNumber.of(0), "number");
        assertArraySetThrowsError("TypeError", "Invalid type bigint for insertion into boolean array", boolArray, 0, JSBigInt.of(0), "bigint");
        assertArraySetThrowsError("TypeError", "Invalid type undefined for insertion into boolean array", boolArray, 0, JSValue.undefined(), "undefined");

        assertArraySetThrowsError("TypeError", "Invalid type string for insertion into int array", intArray, 0, JSString.of("foo"), "string");
        assertArraySetThrowsError("RangeError", "Non-integer number 0.5 for insertion into int array", intArray, 0, JSNumber.of(0.5), "non-integer");
        assertArraySetThrowsError("RangeError", "Out of range value %s for insertion into int array", intArray, 0, JSNumber.of(JSNumber.maxSafeInteger()), "too big");
        assertArraySetThrowsError("RangeError", "Out of range value %s for insertion into int array", intArray, 0, JSNumber.of(JSNumber.minSafeInteger()), "too small");
        assertArraySetThrowsError("RangeError", "Out of range value %s for insertion into int array", intArray, 0, JSNumber.of(Integer.MIN_VALUE - 1.0), "too small");
        assertArraySetThrowsError("RangeError", "Out of range value %s for insertion into int array", intArray, 0, JSNumber.of(Integer.MAX_VALUE + 1.0), "too big");
        assertArraySetThrowsError("RangeError", "Out of range value %s for insertion into int array", intArray, 0, JSBigInt.of(JSNumber.maxSafeInteger()), "too big");
        assertArraySetThrowsError("RangeError", "Out of range value %s for insertion into int array", intArray, 0, JSBigInt.of(JSNumber.minSafeInteger()), "too small");
        assertArraySetThrowsError("RangeError", "Out of range value %s for insertion into int array", intArray, 0, JSBigInt.of(Integer.MIN_VALUE - 1L), "too small");
        assertArraySetThrowsError("RangeError", "Out of range value %s for insertion into int array", intArray, 0, JSBigInt.of(Integer.MAX_VALUE + 1L), "too big");

        assertArraySetThrowsError("RangeError", "Out of range value %s for insertion into long array", longArray, 0, JSBigInt.of(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)), "too big");
        assertArraySetThrowsError("RangeError", "Out of range value %s for insertion into long array", longArray, 0, JSBigInt.of(BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE)),
                        "too small");
        assertArraySetThrowsError("RangeError", "Non-integer number NaN for insertion into long array", longArray, 0, JSNumber.of(Double.NaN), "non-integer");
        assertArraySetThrowsError("RangeError", "Non-integer number -Infinity for insertion into long array", longArray, 0, JSNumber.of(Double.NEGATIVE_INFINITY), "non-integer");
    }

    /// Checks that the proxy for Java arrays conforms to the [iterable
    /// protocol](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Iteration_protocols#the_iterable_protocol).
    ///
    /// The proxy has to have a `[Symbol.iterator]()` method, returning an iterator on which
    /// `next()` can be called to get the next iteration result.
    public static void checkProxyIterator() {
        byte[] byteArray = new byte[]{1, 2, 3, 4};
        JSObject iterator = assertInstanceOf(JSObject.class, getProxyIterator(byteArray));
        JSObject nextFunction = iterator.get("next", JSObject.class);

        for (int i = 0; i < byteArray.length; i++) {
            Object iteratorResult = nextFunction.call(iterator);
            // Put into final variable to be used in lambda
            final int index = i;
            assertFullIteratorResult(i, iteratorResult, value -> {
                JSNumber num = assertInstanceOf(JSNumber.class, value);
                byte byteValue = num.asByte();
                assertEquals(byteArray[index], byteValue, "Wrong value at index " + index);
            });
        }

        assertIterationDone(nextFunction.call(iterator));
        assertIterationDone(nextFunction.call(iterator));
        assertIterationDone(nextFunction.call(iterator));
    }

    /**
     * Checks that the proxy for Java arrays can be used with JS iteration (`for` loop, spread
     * iterator).
     */
    public static void checkProxyJSIteration() {
        int[] intArray = new int[]{1, 2, 99};

        int sum = IntStream.of(intArray).sum();
        int product = IntStream.of(intArray).reduce(0, (a, b) -> a * b);

        assertEquals(sum, sumInLoop(intArray), "Sum mismatch");
        assertEquals(product, productUsingReduce(intArray), "Product mismatch");
    }

    private static void assertFullIteratorResult(int index, Object iterationResult, Consumer<Object> valueMatcher) {
        JSObject result = assertInstanceOf(JSObject.class, iterationResult, "Iterator result for index " + index + " is not a JSObject");
        assertTrue(hasKey(result, "done"), "Iterator result for index " + index + " does not have 'done' property");
        assertTrue(hasKey(result, "value"), "Iterator result for index " + index + " does not have 'value' property");
        boolean done = result.get("done", Boolean.class);
        assertFalse(done, "Iterator was done at index " + index);
        valueMatcher.accept(result.get("value"));
    }

    private static void assertIterationDone(Object iterationResult) {
        JSObject result = assertInstanceOf(JSObject.class, iterationResult, "Iterator result is not a JSObject");
        assertTrue(hasKey(result, "done"), "Iterator result does not have 'done' property");
        boolean done = result.get("done", Boolean.class);
        assertTrue(done, "Iterator was not done");
    }

    /// Inserts the given JS value into the given Java array at the given index and asserts it
    /// throws a JS exception (e.g. `RangeError`).
    ///
    /// @param expectedType Expected value of the Error.name property
    /// @param expectedMessagePattern Expected Error.message property. Can contain a %s conversion
    /// that is replaced using [String#formatted(Object...) ].
    /// @param message Context message that describes what's supposed to go wrong. Added to every
    /// assertion
    ///
    private static void assertArraySetThrowsError(String expectedType, String expectedMessagePattern, Object arr, int idx, JSValue value, String message) {
        String fullMessage = message + " in " + arr.getClass().getTypeName();
        String errorMessage = assertThrowsJSError(expectedType, () -> arraySet(arr, idx, value), fullMessage);
        String expectedMessage = expectedMessagePattern.formatted(value.stringValue());
        assertEquals(expectedMessage, errorMessage, fullMessage);
    }

    /// @return The `Error.message` property
    private static String assertThrowsJSError(String expectedType, Executable e, String message) {
        ThrownFromJavaScript jsExc = assertThrows(ThrownFromJavaScript.class, e, message);
        JSObject thrownObject = assertInstanceOf(JSObject.class, jsExc.getThrownObject(), message);
        assertTrue(isError(thrownObject), "Thrown object " + thrownObject + " is not an error: " + message);
        String name = thrownObject.get("name", String.class);
        assertEquals(expectedType, name, message);
        return thrownObject.get("message", String.class);
    }

    private static void assertNotCoercedToArrayOrTyped(Object o) {
        Assertions.assertNotNull(o, "Object to check is null");
        assertFalse(isCoercedToArray(o), "Object of type " + o.getClass() + " is coerced to JS Array");
        assertFalse(isCoercedToTypedArray(o), "Object of type " + o.getClass() + " is coerced to JS Typed Array");
    }

    private static void assertOutOfRangeLoadsAreUndefined(Object arr) {
        int length = Array.getLength(arr);

        assertInstanceOf(JSUndefined.class, arrayGet(arr, -1), "Array of type " + arr + " does not return undefined at index -1");
        assertInstanceOf(JSUndefined.class, arrayGet(arr, length), "Array of type " + arr + " does not return undefined at index " + length);
    }

    @JS.Coerce
    @JS("return o instanceof Error;")
    private static native boolean isError(Object o);

    @JS.Coerce
    @JS("return Array.isArray(o);")
    private static native boolean isCoercedToArray(Object o);

    @JS.Coerce
    @JS("return ArrayBuffer.isView(o) && !(o instanceof DataView);")
    private static native boolean isCoercedToTypedArray(Object o);

    @JS.Coerce
    @JS("return arr[idx];")
    private static native Object arrayGet(Object arr, int idx);

    @JS.Coerce
    @JS("arr[idx] = value;")
    private static native Object arraySet(Object arr, int idx, JSValue value);

    @JS("return arr[Symbol.iterator]();")
    private static native Object getProxyIterator(Object arr);

    @JS.Coerce
    @JS("return Reflect.has(obj, key);")
    private static native boolean hasKey(JSObject obj, String key);

    @JS.Coerce
    @JS("""
                    let sum = 0;
                    for (var x of arr) {
                        sum += x;
                    }

                    return sum;
                    """)
    private static native int sumInLoop(Object arr);

    @JS.Coerce
    @JS("return [...arr].reduce((a, c) => a * c, 0);")
    private static native int productUsingReduce(Object arr);
}
