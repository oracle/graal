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

package com.oracle.svm.webimage.thirdparty;

import java.math.BigInteger;

import org.graalvm.webimage.api.JS;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.webimage.JSNameGenerator;
import com.oracle.svm.webimage.annotation.JSRawCall;
import com.oracle.svm.webimage.functionintrinsics.JSFunctionIntrinsics;

/**
 * Contains static methods that are responsible for converting values between the JS and Java
 * representation.
 */
public class JavaScriptBodyConversion {

    static {
        JSNameGenerator.registerReservedSymbols(
                        "$$$x", "$$$z", "$$$b", "$$$c", "$$$s", "$$$l", "$$$a", "$$$d", "$$$f", "$$$i", "$$$j",
                        "$$$V", "$$$Z", "$$$B", "$$$C", "$$$S", "$$$L", "$$$A", "$$$D", "$$$F", "$$$I", "$$$J");
    }

    /**
     * Converts the given object to its JS counterpart.
     *
     * The value returned is not necessarily a Java object. For example, this method could return a
     * JS string, array, or even numbers (for boxed types).
     */
    @AlwaysInline("Most checks can be optimized away given the context")
    public static Object convertObjectToJS(Object o) {
        if (o == null) {
            return null;
        }

        if (o instanceof Boolean) {
            return toJSBoolObject((Boolean) o);
        }

        if (o instanceof Character) {
            return JSFunctionIntrinsics.getRaw((Character) o);
        }

        if (o instanceof Number) {
            if (o instanceof Byte) {
                return JSFunctionIntrinsics.getRaw((Byte) o);
            }

            if (o instanceof Short) {
                return JSFunctionIntrinsics.getRaw((Short) o);
            }

            if (o instanceof Integer) {
                return JSFunctionIntrinsics.getRaw((Integer) o);
            }

            if (o instanceof Float) {
                return JSFunctionIntrinsics.getRaw((Float) o);
            }

            if (o instanceof Long) {
                return longToJSNumber((Long) o);
            }

            if (o instanceof Double) {
                return JSFunctionIntrinsics.getRaw((Double) o);
            }

            if (o instanceof BigInteger) {
                return toJSBigint(o.toString());
            }
        }

        Class<?> clazz = o.getClass();

        if (o instanceof JavaScriptBodyObject) {
            return ((JavaScriptBodyObject) o).wrapped;
        }

        if (o instanceof String) {
            return toJSString((String) o);
        } else if (clazz.isArray()) {
            Object[] arr;

            if (o instanceof boolean[]) {
                boolean[] parr = (boolean[]) o;
                arr = new Object[parr.length];
                for (int i = 0; i < parr.length; i++) {
                    arr[i] = parr[i];
                }
            } else if (o instanceof char[]) {
                char[] parr = (char[]) o;
                arr = new Object[parr.length];
                for (int i = 0; i < parr.length; i++) {
                    arr[i] = parr[i];
                }
            } else if (o instanceof byte[]) {
                byte[] parr = (byte[]) o;
                arr = new Object[parr.length];
                for (int i = 0; i < parr.length; i++) {
                    arr[i] = parr[i];
                }
            } else if (o instanceof short[]) {
                short[] parr = (short[]) o;
                arr = new Object[parr.length];
                for (int i = 0; i < parr.length; i++) {
                    arr[i] = parr[i];
                }
            } else if (o instanceof int[]) {
                int[] parr = (int[]) o;
                arr = new Object[parr.length];
                for (int i = 0; i < parr.length; i++) {
                    arr[i] = parr[i];
                }
            } else if (o instanceof long[]) {
                long[] parr = (long[]) o;
                arr = new Object[parr.length];
                for (int i = 0; i < parr.length; i++) {
                    arr[i] = parr[i];
                }
            } else if (o instanceof float[]) {
                float[] parr = (float[]) o;
                arr = new Object[parr.length];
                for (int i = 0; i < parr.length; i++) {
                    arr[i] = parr[i];
                }
            } else if (o instanceof double[]) {
                double[] parr = (double[]) o;
                arr = new Object[parr.length];
                for (int i = 0; i < parr.length; i++) {
                    arr[i] = parr[i];
                }
            } else {
                arr = (Object[]) o;
            }

            return toJSArray(arr);
        }

        return o;
    }

    @JSRawCall
    @JS("return BigInt(o);")
    private static native Object toJSBigint(String o);

    /**
     * Longs are represented differently and have to be converted manually.
     *
     * This function loses precision if the the input number cannot be represented as a JS number.
     */
    @JSRawCall
    @JS("return Long64.toNumber(l);")
    public static native Object longToJSNumber(long l);

    /**
     * In Web Image, booleans are stored as numbers, this converts this representation to JS native
     * boolean.
     */
    @JSRawCall
    @JS("return !!b;")
    public static native boolean toJSBool(boolean b);

    @JSRawCall
    @JS("return !!b;")
    public static native Object toJSBoolObject(boolean b);

    @JSRawCall
    @JS("return toJSArray(a);")
    public static native Object toJSArray(Object[] a);

    @JSRawCall
    @JS("return s.toJSString();")
    public static native Object toJSString(String s);

    public static Object[] createJavaArray(int length) {
        return new Object[length];
    }

    /**
     * Helper method to box a double from JS.
     */
    public static Double toDouble(double d) {
        return d;
    }

    /**
     * Helper method to create a BigInteger from JS.
     */
    public static BigInteger toBigInt(String s) {
        return new BigInteger(s);
    }

    /**
     * Helper method to box a boolean from JS.
     */
    public static Boolean toBoolean(boolean b) {
        return b;
    }

    /**
     * Helper method that creates a JavaScriptBodyObject, i.e. a wrapper around a native JavaScript
     * object.
     */
    public static JavaScriptBodyObject createJavaScriptBodyObject(Object wrapped) {
        return new JavaScriptBodyObject(wrapped);
    }

    /**
     * Takes an object returned from a JavaScript function and creates a suitable Java object from
     * it according to the rules of {@link net.java.html.js.JavaScriptBody}.
     */
    @JSRawCall
    @JS("return convertObjectToJavaForJavaScriptBody(o);") // see jsbody_compat.js
    public static native Object convertObjectToJavaForJavaScriptBody(Object o);
}
