/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSBigInt;
import org.graalvm.webimage.api.JSBoolean;
import org.graalvm.webimage.api.JSNumber;
import org.graalvm.webimage.api.JSObject;
import org.graalvm.webimage.api.JSString;
import org.graalvm.webimage.api.JSValue;
import org.junit.Assert;

public class JavaProxyConversionTest {
    public static final String[] OUTPUT = {
                    "Cell(10)",
                    "Cell(10)",
                    "Cell(10)",
                    "'Cell(10)' cannot be coerced to 'String'.",
                    "true",
                    "true",
                    "false",
                    "true",
                    "false",
                    "'123' cannot be coerced to 'Boolean'.",
                    "121",
                    "121",
                    "121",
                    "121",
                    "a",
                    "a",
                    "b",
                    "1121",
                    "1121",
                    "1001",
                    "1.5",
                    "1.5",
                    "0.125",
                    "9876543210",
                    "9876543210",
                    "10222333444",
                    "1.125",
                    "1.125",
                    "1.0625",
                    "Guess who.",
                    "JavaScript<boolean; true>",
                    "true",
                    "'true' cannot be coerced to a JavaScript 'function'.",
                    "JavaScript<number; 121.0>",
                    "121",
                    "'4141.0' cannot be coerced to a JavaScript 'string'.",
                    "JavaScript<bigint; 123456123456123456>",
                    "123456123456123456",
                    "JavaScript<bigint; 9876543210>",
                    "JavaScript<string; Go proxy!>",
                    "Go proxy!",
                    "'Proxy power!' cannot be coerced to a JavaScript 'object'.",
                    "true",
                    "JavaScript<object; 3,0,0> cannot be coerced to 'double[]'.",
                    "Lambda call! Can't touch this.",
                    "It's raining proxies.",
                    "true",
                    "true",
                    "true",
                    "true",
                    "true",
                    "true",
                    "true",
                    "true",
                    "cannot be coerced to a JavaScript 'Float32Array'.",
                    "Rock'n'roll",
                    "3",
                    "Rock'n'roll",
                    "15",
                    "2",
                    "Johnny be good.",
                    "[717: Integer]",
                    "[717: Integer]",
                    "717",
                    "[]",
                    "[Great success!, For the win!]",
                    "JavaScript<string; cell>",
                    "JavaScript<string; subcell(subcontent)>",
    };

    public static void main(String[] args) {
        passingProxies();
        proxyToBasicTypeConversion();
        proxyCalls();
        proxyNewOperator();
        passingLambdaToVirtual();
        indexedAccessProxiedArray();
    }

    private static void passingProxies() {
        Cell cell = new Cell(10);
        logJavaProxy(cell);
        System.out.println(id(cell));
        System.out.println(idCell(cell));
        try {
            System.out.println(idString(cell));
        } catch (ClassCastException e) {
            System.out.println(e.getMessage());
        }

        boolean b0 = true;
        logJavaProxy(b0);
        Boolean boxed0 = (Boolean) id(b0);
        System.out.println(boxed0);
        System.out.println(id(false));
        System.out.println(id(Boolean.valueOf(true)));
        boolean b1 = idBoolean(Boolean.valueOf(false));
        System.out.println(b1);
        try {
            idBoolean(123);
        } catch (ClassCastException e) {
            System.out.println(e.getMessage());
        }

        short s0 = 121;
        logJavaProxy(s0);
        System.out.println(id(s0));
        short s1 = (short) id(s0);
        System.out.println(s1);
        short s2 = (short) idShort(s0);
        System.out.println(s2);

        char c = 'a';
        logJavaProxy(c);
        System.out.println(id(c));
        System.out.println(idChar('b'));

        int i = 1121;
        logJavaProxy(i);
        System.out.println(id(i));
        System.out.println(idInt(1001));

        float f = 1.5f;
        logJavaProxy(f);
        System.out.println(id(f));
        System.out.println(idFloat(0.125f));

        long l = 9876543210L;
        logJavaProxy(l);
        System.out.println(id(l));
        System.out.println(idLong(10222333444L));

        double d = 1.125;
        logJavaProxy(d);
        System.out.println(id(d));
        System.out.println(idDouble(1.0625));

        Object x = id("Guess who.");
        logJavaProxy(x);
    }

    @JS("console.log(x.toString());")
    private static native void logJavaProxy(Object x);

    @JS("return x;")
    private static native Object id(Object x);

    @JS("return c;")
    private static native Cell idCell(Cell c);

    @JS("return s;")
    private static native String idString(Object s);

    @JS("return b;")
    private static native boolean idBoolean(Object b);

    @JS("return s;")
    private static native Object idShort(short s);

    @JS("return c;")
    private static native Object idChar(char c);

    @JS("return i;")
    private static native int idInt(int i);

    @JS("return f;")
    private static native float idFloat(Float f);

    @JS("return l;")
    private static native Long idLong(long l);

    @JS("return d;")
    private static native double idDouble(double d);

    private static void proxyToBasicTypeConversion() {
        JSBoolean trueValue = coerceToBoolean(true);
        System.out.println(trueValue);
        log(trueValue);
        try {
            failedCoerceToBoolean(Boolean.valueOf(true));
        } catch (ClassCastException e) {
            System.out.println(e.getMessage());
        }

        JSNumber number = coerceToNumber(121);
        System.out.println(number);
        log(number);
        try {
            failedCoerceToNumber(4141.0);
        } catch (ClassCastException e) {
            System.out.println(e.getMessage());
        }

        JSBigInt bigint = coerceToBigInt(new BigInteger("123456123456123456"));
        System.out.println(bigint);
        log(bigint);
        System.out.println(coerceToBigInt(9876543210L));

        JSString string = coerceToString("Go proxy!");
        System.out.println(string);
        log(string);
        try {
            failedCoerceToString("Proxy power!");
        } catch (ClassCastException e) {
            System.out.println(e.getMessage());
        }

        byte[] bytes = new byte[3];
        bytes[0] = 3;
        byte[] bytes0 = coerceToObject(bytes).asByteArray();
        System.out.println(bytes == bytes0);
        try {
            coerceToObject(bytes).asDoubleArray();
        } catch (ClassCastException e) {
            System.out.println(e.getMessage());
        }

        JSObject fun = coerceRunnableToFunction(() -> System.out.println("Lambda call! Can't touch this."));
        fun.invoke();

        JSObject fun1 = coerceConsumerToFunction(x -> System.out.println("It's raining " + x + "."));
        fun1.invoke("proxies");

        boolean[] booleans = new boolean[3];
        booleans[0] = true;
        boolean[] booleans1 = coerceToTypedArray(booleans).asBooleanArray();
        System.out.println(booleans == booleans1);

        byte[] bytes1 = coerceToTypedArray(bytes).asByteArray();
        System.out.println(bytes == bytes1);

        short[] shorts = new short[3];
        short[] shorts1 = coerceToTypedArray(shorts).asShortArray();
        System.out.println(shorts == shorts1);

        char[] chars = new char[3];
        char[] chars1 = coerceToTypedArray(chars).asCharArray();
        System.out.println(chars == chars1);

        int[] ints = new int[3];
        int[] ints1 = coerceToTypedArray(ints).asIntArray();
        System.out.println(ints == ints1);

        float[] floats = new float[3];
        float[] floats1 = coerceToTypedArray(floats).asFloatArray();
        System.out.println(floats == floats1);

        long[] longs = new long[3];
        long[] longs1 = coerceToTypedArray(longs).asLongArray();
        System.out.println(longs == longs1);

        double[] doubles = new double[3];
        double[] doubles1 = coerceToTypedArray(doubles).asDoubleArray();
        System.out.println(doubles == doubles1);
        try {
            failedCoerceToTypedFloatArray(doubles);
        } catch (ClassCastException e) {
            System.out.println(e.getMessage().substring(e.getMessage().indexOf("cannot")));
        }
    }

    @JS("console.log(x.toString());")
    private static native void log(Object x);

    @JS("return b.$as('boolean');")
    private static native JSBoolean coerceToBoolean(boolean b);

    @JS("return b.$as('function');")
    private static native JSObject failedCoerceToBoolean(Boolean b);

    @JS("return n.$as('number');")
    private static native JSNumber coerceToNumber(int n);

    @JS("return n.$as('string');")
    private static native JSString failedCoerceToNumber(Double n);

    @JS("return n.$as('bigint');")
    private static native JSBigInt coerceToBigInt(Number n);

    @JS("return s.$as('string');")
    private static native JSString coerceToString(String s);

    @JS("return s.$as('object');")
    private static native JSObject failedCoerceToString(String s);

    @JS("return bytes.$as('object');")
    private static native JSObject coerceToObject(byte[] bytes);

    @JS("return r.$as('function');")
    private static native JSObject coerceRunnableToFunction(Runnable r);

    @JS("return r.$as('function');")
    private static native JSObject coerceConsumerToFunction(Consumer<String> r);

    @JS("return booleans.$as(Uint8Array);")
    private static native JSObject coerceToTypedArray(boolean[] booleans);

    @JS("return bytes.$as(Int8Array);")
    private static native JSObject coerceToTypedArray(byte[] bytes);

    @JS("return shorts.$as(Int16Array);")
    private static native JSObject coerceToTypedArray(short[] shorts);

    @JS("return chars.$as(Uint16Array);")
    private static native JSObject coerceToTypedArray(char[] chars);

    @JS("return ints.$as(Int32Array);")
    private static native JSObject coerceToTypedArray(int[] ints);

    @JS("return floats.$as(Float32Array);")
    private static native JSObject coerceToTypedArray(float[] floats);

    @JS("return longs.$as(BigInt64Array);")
    private static native JSObject coerceToTypedArray(long[] longs);

    @JS("return doubles.$as(Float64Array);")
    private static native JSObject coerceToTypedArray(double[] doubles);

    @JS("return doubles.$as(Float32Array);")
    private static native JSObject failedCoerceToTypedFloatArray(double[] doubles);

    private static void proxyCalls() {
        String text = "  Rock'n'roll  ";
        System.out.println(text.trim());
        System.out.println(text.indexOf("o", 0));
        printTrimmed(text);
        printLength(text);
        printIndexOf(text, 0);

        StringBuilder sb = new StringBuilder();
        sb.append("Johnny ");
        append(sb, "be good.");
        System.out.println(sb);

        SubCell subCell = new SubCell(717);
        System.out.println(subCell.getString());
        System.out.println(getString(subCell));
        System.out.println(get(subCell));
    }

    @JS("console.log(s.trim().$as('string'));")
    private static native void printTrimmed(String s);

    @JS("console.log(s.length().$as('number'));")
    private static native void printLength(String s);

    @JS("const vm = s.$vm; console.log(s.indexOf(vm.as('R', 'java.lang.String'), from).$as('number'));")
    private static native void printIndexOf(String s, int from);

    @JS("sb.append(text);")
    private static native void append(StringBuilder sb, String text);

    @JS("return cell.getString();")
    private static native String getString(SubCell cell);

    @JS("return cell.get();")
    private static native Object get(SubCell cell);

    private static void proxyNewOperator() {
        System.out.println(new ArrayList<>());
        ArrayList<String> list = createArrayList(ArrayList.class);
        list.add("Great success!");
        list.add("For the win!");
        System.out.println(list);
    }

    @JS("return new arrayListType();")
    private static native ArrayList<String> createArrayList(Class<?> arrayListType);

    private static Cell[] cells = new Cell[]{new Cell("content"), new SubCell("subcontent")};

    private static void passingLambdaToVirtual() {
        System.out.println(cells[0].jsName());
        System.out.println(cells[1].jsName());
    }

    private static void indexedAccessProxiedArray() {
        Object[] arr = new Object[]{"abc", 5};
        for (int i = 0; i < arr.length; i++) {
            Object result = getIndexedProxyArray(arr, JSNumber.of(i));
            Assert.assertSame("Get in proxied array returned different object", arr[i], result);
        }
        Object[] newArr = new Object[arr.length];
        for (int i = 0; i < arr.length; i++) {
            setIndexedProxyArray(newArr, JSNumber.of(i), arr[i]);
            Assert.assertSame("Set in proxied array did not succeed", arr[i], newArr[i]);
        }
        Assert.assertEquals("Array length of proxied array is incorrect", arr.length, (int) getProxiedArrayLength(newArr).asInt());
    }

    @JS("return arr[i];")
    private static native Object getIndexedProxyArray(Object[] arr, JSNumber i);

    @JS("arr[i] = value;")
    private static native void setIndexedProxyArray(Object[] arr, JSNumber i, Object value);

    @JS("return arr.length;")
    private static native JSNumber getProxiedArrayLength(Object[] arr);
}

class Cell {
    private Object value;

    Cell(Object value) {
        this.value = value;
    }

    public Object get() {
        return value;
    }

    public final JSValue jsName() {
        // This is a virtual call that may be targetting a JS-annotated method, which means that the
        // supplier's SAM should be preserved.
        return buildJsName(() -> value);
    }

    @SuppressWarnings("unused")
    protected JSValue buildJsName(Supplier<Object> f) {
        return JSString.of("cell");
    }

    @Override
    public String toString() {
        return "Cell(" + value + ")";
    }
}

class SubCell extends Cell {

    SubCell(Object value) {
        super(value);
    }

    @Override
    @JS("return 'subcell(' + f().toString() + ')';")
    protected native JSValue buildJsName(Supplier<Object> f);

    public String getString() {
        return "[" + get().toString() + ": " + get().getClass().getSimpleName() + "]";
    }
}
