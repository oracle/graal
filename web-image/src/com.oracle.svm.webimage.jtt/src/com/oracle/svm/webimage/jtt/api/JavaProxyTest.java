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

import java.util.function.Function;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSError;
import org.graalvm.webimage.api.JSValue;

import com.oracle.svm.core.NeverInline;

/**
 * Tests the implementation of the JavaScript {@code Proxy} handler and its traps that is used for
 * proxying access to Java objects.
 * <p>
 * Anything related to object properties relies on whether the method with that name was actually
 * generated in the image. Because of this, we restrict ourselves to the {@code equals} property
 * which seems to be always generated for {@code java.lang.Object}.
 */
public class JavaProxyTest {
    public static final String[] OUTPUT = {
                    // apply
                    "Hello from Runnable",
                    "Hello from Runnable",
                    "40 + 2 = 42",
                    "-1 + 2 = 1",
                    // construct
                    "java.lang.Object",
// Blocked Operations
                    "Caught JS error for defineProperty",
                    "Caught JS error for deleteProperty",
                    "Caught JS error for preventExtensions",
                    "Caught JS error for set",
                    "Caught JS error for setPrototypeOf to null",
                    "Caught JS error for setPrototypeOf to non-null",
                    // get
                    "function",
                    // getOwnPropertyDescriptor
                    // TODO GR-63218 This does not work yet in any backend
                    // getPrototypeOf
                    "null",
                    // has
                    "true",
                    "false",
                    // ownKeys
                    // TODO GR-63218 This does not work yet in any backend
    };

    public static void main(String[] args) {
        trapApply();
        trapConstruct();
        blockedOperations();
        trapGet();
        trapGetOwnPropertyDescriptor();
        trapGetPrototypeOf();
        trapHas();
        trapOwnKeys();
    }

    private static void trapApply() {
        Runnable runnable = () -> {
            System.out.println("Hello from Runnable");
        };
        callRunnable(runnable);

        Function<Integer, String> function = i -> i + " + 2 = " + (i + 2);
        System.out.println(callFunction(function, 40));
        System.out.println(callFunction(function, -1));
    }

    @JS("r();")
    private static native void callRunnableImpl(Runnable r);

    @NeverInline("Make sure single-abstract-method is created and not inlined")
    private static void callRunnable(Runnable r) {
        // Force SVM to generate the run method
        r.run();
        callRunnableImpl(r);
    }

    @JS("return f(value);")
    private static native String callFunctionImpl(Function<Integer, String> f, int value);

    @NeverInline("Make sure single-abstract-method is created and not inlined")
    private static String callFunction(Function<Integer, String> f, int value) {
        // Force SVM to generate the apply method
        f.apply(0);
        return callFunctionImpl(f, value);
    }

    private static void trapConstruct() {
        System.out.println(construct(Object.class).getClass().getName());
    }

    @JS("return new clazz();")
    private static native Object construct(Class<?> clazz);

    private static void expectJSError(Runnable r, String name) {
        try {
            r.run();
            System.out.println("ERROR: Expected JS error for " + name);
        } catch (JSError jsError) {
            System.out.println("Caught JS error for " + name);
        }
    }

    private static void blockedOperations() {
        Object o = new Object();
        expectJSError(() -> defineProperty(o), "defineProperty");
        expectJSError(() -> deleteProperty(o), "deleteProperty");
        expectJSError(() -> preventExtensions(o), "preventExtensions");
        expectJSError(() -> setProperty(o, "Some string"), "set");
        expectJSError(() -> setPrototypeOf(o, null), "setPrototypeOf to null");
        expectJSError(() -> setPrototypeOf(o, new Object()), "setPrototypeOf to non-null");
    }

    @JS("Object.defineProperty(o, 'prop1', {value: 42, writable: false,});")
    private static native void defineProperty(Object o);

    @JS("delete o['equals']")
    private static native void deleteProperty(Object o);

    @JS("Object.preventExtensions(o)")
    private static native void preventExtensions(Object o);

    @JS("o['equals'] = value;")
    private static native void setProperty(Object o, Object value);

    @JS("Object.setPrototypeOf(o.prototype, proto)")
    private static native void setPrototypeOf(Object o, Object proto);

    private static void trapGet() {
        Object o = new Object();
        System.out.println(get(o, "equals").typeof());
    }

    @JS("return o[property.$as('string')]")
    private static native JSValue get(Object o, String property);

    private static void trapGetOwnPropertyDescriptor() {
        // TODO GR-63218 This does not work yet in any backend
    }

    private static void trapGetPrototypeOf() {
        System.out.println(getPrototypeOf(new Object()));
    }

    @JS("return Object.getPrototypeOf(o);")
    private static native JSValue getPrototypeOf(Object o);

    private static void trapHas() {
        Object o = new Object();
        System.out.println(has(o, "equals").asBoolean());
        System.out.println(has(o, "I do not exist").asBoolean());
    }

    @JS("return property.$as('string') in o")
    private static native JSValue has(Object o, String property);

    private static void trapOwnKeys() {
        // TODO GR-63218 This does not work yet in any backend
    }
}
