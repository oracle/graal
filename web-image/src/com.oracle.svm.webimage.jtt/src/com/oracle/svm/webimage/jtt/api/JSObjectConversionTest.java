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

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSNumber;
import org.graalvm.webimage.api.JSObject;

public class JSObjectConversionTest {
    public static final String[] OUTPUT = {
                    "JavaScript<object; [object Object]>", "[object Object]", "JavaScript<object; [object Object]>", "true", "true", "false",
                    "JavaScript<object; 1,0,0,0>", "1,0,0,0", "true",
                    "JavaScript<object; 1,0,0,0> cannot be coerced to 'double[]'.",
                    "JavaScript<object; 11,0,0,0,0,0,0,0,0,0>", "11,0,0,0,0,0,0,0,0,0", "11", "78",
                    "JavaScript<object; 78,0,0,0,0,0,0,0,0,0> cannot be coerced to 'short[]'.",
                    "JavaScript<object; 14,0,0,0>", "14,0,0,0", "14",
                    "JavaScript<object; 14,0,0,0> cannot be coerced to 'int[]'.",
                    "JavaScript<object; 65,0,0,0>", "65,0,0,0", "A",
                    "JavaScript<object; 65,0,0,0> cannot be coerced to 'boolean[]'.",
                    "JavaScript<object; 123456,0,0,0>", "123456,0,0,0", "123456",
                    "JavaScript<object; 123456,0,0,0> cannot be coerced to 'long[]'.",
                    "JavaScript<object; -22.5,0,0>", "-22.5,0,0", "-22.5",
                    "JavaScript<object; -22.5,0,0> cannot be coerced to 'byte[]'.",
                    "JavaScript<object; 1000123456,0,0>", "1000123456,0,0", "1000123456",
                    "JavaScript<object; 1000123456,0,0> cannot be coerced to 'char[]'.",
                    "JavaScript<object; 0.0625,0,0>", "0.0625,0,0", "0.0625",
                    "JavaScript<object; 0.0625,0,0> cannot be coerced to 'float[]'.",
                    "JavaScript<function;", "JavaScript<function;", "5", "JavaScript<number; 5.0>", "JavaScript<number; 12.0>",
                    "12",
                    "js value",
                    "Field type modified!",
                    "",
                    "java value",
                    "java value",
    };

    public static void main(String[] args) {
        passingAnonymousObject();
        objectToArrayConversion();
        functionPassingAndInvoking();
        jsObjectInvalidFieldAccess();
    }

    private static void passingAnonymousObject() {
        // Anonymous object creation and passing through boundaries.
        JSObject obj = JSObject.create();
        System.out.println(obj);
        log(obj);
        System.out.println(id(obj));
        System.out.println(obj == id(obj));
        System.out.println(obj.equals(id(obj)));
        System.out.println(obj.equals(JSObject.create()));
    }

    @JS("console.log(x.toString());")
    private static native void log(Object x);

    @JS("return x;")
    private static native Object id(Object x);

    private static void objectToArrayConversion() {
        // Object-to-array conversion.
        JSObject booleanArrayObject = createBooleanArray(JSNumber.of(4));
        System.out.println(booleanArrayObject);
        log(booleanArrayObject);
        boolean[] booleans = booleanArrayObject.asBooleanArray();
        System.out.println(booleans[0]);
        try {
            booleanArrayObject.asDoubleArray();
        } catch (ClassCastException e) {
            System.out.println(e.getMessage());
        }

        JSObject byteArrayObject = createByteArray(JSNumber.of(10));
        System.out.println(byteArrayObject);
        log(byteArrayObject);
        byte[] bytes = byteArrayObject.asByteArray();
        System.out.println(bytes[0]);
        bytes[0] = 78;
        System.out.println(getByte0(byteArrayObject).asByte());
        try {
            byteArrayObject.asShortArray();
        } catch (ClassCastException e) {
            System.out.println(e.getMessage());
        }

        JSObject shortArrayObject = createShortArray(JSNumber.of(4));
        System.out.println(shortArrayObject);
        log(shortArrayObject);
        short[] shorts = shortArrayObject.asShortArray();
        System.out.println(shorts[0]);
        try {
            shortArrayObject.asIntArray();
        } catch (ClassCastException e) {
            System.out.println(e.getMessage());
        }

        JSObject charArrayObject = createCharArray(JSNumber.of(4));
        System.out.println(charArrayObject);
        log(charArrayObject);
        char[] chars = charArrayObject.asCharArray();
        System.out.println(chars[0]);
        try {
            charArrayObject.asBooleanArray();
        } catch (ClassCastException e) {
            System.out.println(e.getMessage());
        }

        JSObject intArrayObject = createIntArray(JSNumber.of(4));
        System.out.println(intArrayObject);
        log(intArrayObject);
        int[] ints = intArrayObject.asIntArray();
        System.out.println(ints[0]);
        try {
            intArrayObject.asLongArray();
        } catch (ClassCastException e) {
            System.out.println(e.getMessage());
        }

        JSObject floatArrayObject = createFloatArray(JSNumber.of(3));
        System.out.println(floatArrayObject);
        log(floatArrayObject);
        float[] floats = floatArrayObject.asFloatArray();
        System.out.println(floats[0]);
        try {
            floatArrayObject.asByteArray();
        } catch (ClassCastException e) {
            System.out.println(e.getMessage());
        }

        JSObject longArrayObject = createLongArray(JSNumber.of(3));
        System.out.println(longArrayObject);
        log(longArrayObject);
        long[] longs = longArrayObject.asLongArray();
        System.out.println(longs[0]);
        try {
            longArrayObject.asCharArray();
        } catch (ClassCastException e) {
            System.out.println(e.getMessage());
        }

        JSObject doubleArrayObject = createDoubleArray(JSNumber.of(3));
        System.out.println(doubleArrayObject);
        log(doubleArrayObject);
        double[] doubles = doubleArrayObject.asDoubleArray();
        System.out.println(doubles[0]);
        try {
            doubleArrayObject.asFloatArray();
        } catch (ClassCastException e) {
            System.out.println(e.getMessage());
        }
    }

    @JS("const arr = new Uint8Array(length); arr[0] = 1; return arr;")
    private static native JSObject createBooleanArray(JSNumber length);

    @JS("const arr = new Int8Array(length); arr[0] = 11; return arr;")
    private static native JSObject createByteArray(JSNumber length);

    @JS("return byteArray[0];")
    private static native JSNumber getByte0(JSObject byteArray);

    @JS("const arr = new Int16Array(length); arr[0] = 14; return arr;")
    private static native JSObject createShortArray(JSNumber length);

    @JS("const arr = new Uint16Array(length); arr[0] = 65; return arr;")
    private static native JSObject createCharArray(JSNumber length);

    @JS("const arr = new Int32Array(length); arr[0] = 123456; return arr;")
    private static native JSObject createIntArray(JSNumber length);

    @JS("const arr = new Float32Array(length); arr[0] = -22.5; return arr;")
    private static native JSObject createFloatArray(JSNumber length);

    @JS("const arr = new BigInt64Array(length); arr[0] = BigInt('1000123456'); return arr;")
    private static native JSObject createLongArray(JSNumber length);

    @JS("const arr = new Float64Array(length); arr[0] = 0.0625; return arr;")
    private static native JSObject createDoubleArray(JSNumber length);

    private static void functionPassingAndInvoking() {
        // Function creation, passing through boundaries and invoking.
        JSObject add = addFunction();
        System.out.println(add.toString().substring(0, 20));
        System.out.println(id(add).toString().substring(0, 20));
        JSNumber sum = (JSNumber) add.invoke(JSNumber.of(2), JSNumber.of(3));
        log(sum);
        System.out.println(sum);

        // Function call with this binding
        JSObject thisObj = dummyJSObject(JSNumber.of(10));
        JSObject fun = functionWithThis();
        JSNumber res = (JSNumber) fun.call(thisObj, JSNumber.of(2));
        System.out.println(res);
    }

    @JS("return (a, b) => a + b;")
    private static native JSObject addFunction();

    @JS("return function (a) { return a + this.size; }")
    private static native JSObject functionWithThis();

    @JS("return { size: num };")
    private static native JSObject dummyJSObject(JSNumber num);

    private static void jsObjectInvalidFieldAccess() {
        ObjectWithFields obj = new ObjectWithFields();
        String s = "";
        obj.intValue = 11;
        obj.stringValue = "value";
        modifyFields(obj, 12, "js value");
        System.out.println(obj.intValue);
        System.out.println(obj.stringValue);
        incorrectlyModifyFields(obj, 3.0);
        try {
            s = obj.stringValue;
        } catch (ClassCastException e) {
            System.out.println("Field type modified!");
        }
        System.out.println(s);
        fixFields(obj, "java value");
        System.out.println(obj.stringValue);
        System.out.println(obj.get("stringValue"));
    }

    @JS("obj.intValue = intValue; obj.stringValue = stringValue;")
    private static native void modifyFields(ObjectWithFields obj, Integer intValue, String stringValue);

    @JS("obj.stringValue = doubleValue;")
    private static native void incorrectlyModifyFields(ObjectWithFields obj, Double doubleValue);

    @JS("obj.stringValue = stringValue;")
    private static native void fixFields(ObjectWithFields obj, String stringValue);
}

class ObjectWithFields extends JSObject {
    public Integer intValue;
    public String stringValue;
}
