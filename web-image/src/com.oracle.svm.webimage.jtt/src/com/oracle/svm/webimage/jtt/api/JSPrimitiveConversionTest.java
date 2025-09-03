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
import org.graalvm.webimage.api.JSString;
import org.graalvm.webimage.api.JSSymbol;
import org.graalvm.webimage.api.JSUndefined;
import org.graalvm.webimage.api.JSValue;

public class JSPrimitiveConversionTest {
    public static final String[] OUTPUT = {
                    "null", "null",
                    "JavaScript<undefined; undefined>", "undefined", "JavaScript<undefined; undefined>", "true",
                    "JavaScript<boolean; true>", "true", "JavaScript<boolean; true>", "true",
                    "JavaScript<number; 1.5>", "1.5", "JavaScript<number; 1.5>", "true",
                    "JavaScript<bigint; 9876543210>", "9876543210n", "JavaScript<bigint; 9876543210>", "true",
                    "JavaScript<bigint; 98765432101234567890>", "98765432101234567890n", "JavaScript<bigint; 98765432101234567890>", "false",
                    "JavaScript<string; JavaScript>", "JavaScript", "JavaScript<string; JavaScript>", "true", "false",
                    "JavaScript<symbol; Symbol(Unique)>", "Symbol(Unique)", "JavaScript<symbol; Symbol(Unique)>",
                    "JavaScript<symbol; Symbol(NonUnique)>", "Symbol(NonUnique)", "JavaScript<symbol; Symbol(NonUnique)>", "false", "true", "true"
    };

    public static void main(String[] args) {
        // JSValue classes.
        log(null);
        System.out.println(id(null));
        JSUndefined u = JSValue.undefined();
        System.out.println(u);
        log(u);
        System.out.println(id(u));
        System.out.println(u.equals(JSValue.undefined()));
        final JSBoolean b = JSBoolean.of(true);
        System.out.println(b);
        log(b);
        System.out.println(id(b));
        System.out.println(b.equals(JSBoolean.of(true)));
        final JSNumber n = JSNumber.of(1.5);
        System.out.println(n);
        log(n);
        System.out.println(id(n));
        System.out.println(n.equals(JSNumber.of(1.5)));
        final JSBigInt bi0 = JSBigInt.of(9876543210L);
        System.out.println(bi0);
        log(bi0);
        System.out.println(id(bi0));
        System.out.println(bi0.equals(JSBigInt.of(9876543210L)));
        final JSBigInt bi1 = JSBigInt.of(new BigInteger("98765432101234567890"));
        System.out.println(bi1);
        log(bi1);
        System.out.println(id(bi1));
        System.out.println(bi1.equals(JSBigInt.of(123456789)));
        final JSString s = JSString.of("JavaScript");
        System.out.println(s);
        log(s);
        System.out.println(id(s));
        System.out.println(s.equals(JSString.of("JavaScript")));
        System.out.println(s.equals(JSString.of("Java")));
        final JSSymbol sym0 = JSSymbol.of("Unique");
        System.out.println(sym0);
        log(sym0);
        System.out.println(id(sym0));
        final JSSymbol sym1 = JSSymbol.forString("NonUnique");
        System.out.println(sym1);
        log(sym1);
        System.out.println(id(sym1));
        System.out.println(sym0.equals(sym1));
        System.out.println(sym0.equals(sym0));
        System.out.println(sym1.equals(JSSymbol.forString("NonUnique")));
    }

    @JS("console.log(x);")
    private static native void log(Object x);

    @JS("return x;")
    private static native Object id(Object x);
}
