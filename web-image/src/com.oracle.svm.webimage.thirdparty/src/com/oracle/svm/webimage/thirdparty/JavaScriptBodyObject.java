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

import net.java.html.js.JavaScriptBody;

/**
 * <p>
 * A <code>JavaScriptBodyObject</code> is a Java-side wrapper for JavaScript objects. The purpose of
 * this class is that a JavaScript object is not normally an instance of any Java class, and it
 * therefore cannot be represented as a data-type in Java programs. When the JavaScript code
 * (invoked by the method annotated with the {@link JavaScriptBody} annotation) returns a JavaScript
 * object, that object gets wrapped into <code>JavaScriptBodyObject</code> instance. The
 * <code>JavaScriptBodyObject</code> allows the Java code to access the fields of the underlying
 * JavaScript object using the <code>get</code> and <code>set</code> methods.
 * </p>
 *
 * Here are a few examples:
 *
 * <pre>
 * &#64;JavaScriptBody(body = "return {x: x, y: y};", args = {"x", "y"})
 * public static native JavaScriptBodyObject createPair(double x, double y);
 *
 * JavaScriptBodyObject pair = createPair(3.0, 4.0);
 *
 * System.out.println(pair.get("x"));
 * System.out.println(pair.get("y"));
 * </pre>
 *
 * In this example, with the help of the JavaScriptBodyObject, the user can access the
 * <code>x</code> and <code>y</code> fields.
 *
 * <pre>
 * pair.set("x", 5.0);
 * System.out.println(pair.get("x"));
 * </pre>
 *
 * The code above sets a new value for the <code>x</code> field, and prints the new one after that.
 */
public class JavaScriptBodyObject {

    public final Object wrapped;

    public JavaScriptBodyObject(Object wrapped) {
        this.wrapped = wrapped;
    }

    /**
     * Returns the value of the key passed as the argument in the JavaScript object.
     *
     * @param key the object under which the returned value is placed in the JavaScript object
     * @return the value of the key passed as the argument in the JavaScript object
     */
    @JavaScriptBody(args = {"key"}, body = "return this[key];")
    public native Object get(Object key);

    /**
     * Sets the value of the key passed as the argument in the JavaScript object.
     *
     * @param key the object under which the value should be placed in the JavaScript object
     * @param newValue the value that should be placed under the given key in the JavaScript object
     */
    @JavaScriptBody(args = {"key", "newValue"}, body = "this[key] = newValue;")
    public native void set(Object key, Object newValue);

    /**
     * Calls the object stored at the given key as a function with the given arguments.
     *
     * @param key the object under which the function is placed in the JavaScript object
     * @param args Arguments passed to the function
     * @return Result of invoked function. Is <code>null</code>, if the function does not return
     *         anything
     */
    @JavaScriptBody(args = {"key", "args"}, body = "return this[key](...args);")
    public native Object invokeMember(Object key, Object... args);

    /**
     * Creates an empty JavaScript object.
     *
     * @return an empty JavaScript object
     */
    @JavaScriptBody(args = {}, body = "return {};")
    public static native JavaScriptBodyObject create();

    @Override
    public String toString() {
        return "JavaScriptBodyObject";
    }
}
