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

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSBigInt;
import org.graalvm.webimage.api.JSBoolean;
import org.graalvm.webimage.api.JSNumber;
import org.graalvm.webimage.api.JSObject;
import org.graalvm.webimage.api.JSString;
import org.graalvm.webimage.api.JSSymbol;
import org.graalvm.webimage.api.JSUndefined;
import org.graalvm.webimage.api.JSValue;

public class CoercionConversionTest {
    public static final String[] OUTPUT = {
                    // Argument coercion tests.
                    "null", "object",
                    "undefined", "undefined",
                    "true", "boolean",
                    "1.1", "number",
                    "9876543210", "bigint",
                    "JavaScript", "string",
                    "Symbol(one and only)", "symbol",
                    "[object Object]", "object",
                    "true", "boolean",
                    "16", "number",
                    "1.125", "number",
                    "65", "number",
                    "102030405060", "bigint",
                    "9876543210", "bigint",
                    "freestyla", "string",
                    "1,2,3,4,5", "object",
                    "Tuple(x=3, y=4)", "function",
                    // Return-value coercion tests.
                    "true",
                    "10",
                    "B",
                    "9876543210",
                    "MC",
                    "100,101,102",
                    "Tuple(x=1, y=2)",
                    "ghost in the virtual machine",
                    "undefined",
                    "null",
    };

    public static void main(String[] args) {
        argumentCoercion();
        returnValueCoercion();
    }

    private static void argumentCoercion() {
        // JSValue instances undergo no coercion.
        log(null);
        typeof(null);

        log(JSUndefined.undefined());
        typeof(JSUndefined.undefined());

        log(JSBoolean.of(true));
        typeof(JSBoolean.of(true));

        log(JSNumber.of(1.1));
        typeof(JSNumber.of(1.1));

        log(JSBigInt.of(new BigInteger("9876543210")));
        typeof(JSBigInt.of(new BigInteger("9876543210")));

        log(JSString.of("JavaScript"));
        typeof(JSString.of("JavaScript"));

        log(JSSymbol.of("one and only"));
        typeof(JSSymbol.of("one and only"));

        log(JSObject.create());
        typeof(JSObject.create());

        // Coercive types undergo coercion.
        log(true);
        typeof(true);

        log(16);
        typeof(16);

        log(1.125);
        typeof(1.125);

        log('A');
        typeof('A');

        log(102030405060L);
        typeof(102030405060L);

        log(new BigInteger("9876543210"));
        typeof(new BigInteger("9876543210"));

        log("freestyla");
        typeof("rock da microphone");

        int[] ints = new int[]{1, 2, 3, 4, 5};
        logToString(ints);
        typeof(ints);

        // All other types are not coerced, and are exposed as Java Proxies.
        log(new Tuple(3, 4));
        typeof(new Tuple(3, 4));
    }

    @JS.Coerce
    @JS("console.log(x !== null && x !== undefined ? x.toString() : x);")
    private static native void log(Object x);

    @JS.Coerce
    @JS("console.log(x.toString());")
    private static native void logToString(Object x);

    @JS.Coerce
    @JS("console.log(typeof x);")
    private static native void typeof(Object x);

    private static void returnValueCoercion() {
        System.out.println(typeOfCheck(1.25f, "number"));
        int len = stringLength("foxy proxy");
        System.out.println(len);
        System.out.println(b());
        System.out.println(bigDaddy("9876543210"));
        System.out.println(bomfunk());
        byte[] bytes = primitivo();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                System.out.print(',');
            }
            System.out.print(bytes[i]);
        }
        System.out.println();
        System.out.println(returnSame(new Tuple(1, 2)));
        System.out.println(returnCharSequence(new StringBuilder("ghost in the virtual machine")));
        System.out.println(returnUndefined().typeof());
        System.out.println(returnNull());
    }

    @JS.Coerce
    @JS("return (typeof x) === expectedType;")
    private static native Boolean typeOfCheck(Object x, String expectedType);

    @JS.Coerce
    @JS("return s.length;")
    private static native int stringLength(String s);

    @JS.Coerce
    @JS("return 66;")
    private static native Character b();

    @JS.Coerce
    @JS("return BigInt(num);")
    private static native BigInteger bigDaddy(String num);

    @JS.Coerce
    @JS("return 'MC';")
    private static native String bomfunk();

    @JS.Coerce
    @JS("let xs = new Int8Array(3); xs[0] = 100; xs[1] = 101; xs[2] = 102; return xs;")
    private static native byte[] primitivo();

    @JS.Coerce
    @JS("return tuple;")
    private static native Object returnSame(Tuple tuple);

    @JS.Coerce
    @JS("return chars;")
    private static native CharSequence returnCharSequence(StringBuilder chars);

    @JS.Coerce
    @JS("return undefined;")
    private static native JSValue returnUndefined();

    @JS.Coerce
    @JS("return null;")
    private static native String returnNull();
}

class Tuple {
    private final int x;
    private final int y;

    Tuple(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    @Override
    public String toString() {
        return "Tuple(x=" + x + ", y=" + y + ')';
    }
}
