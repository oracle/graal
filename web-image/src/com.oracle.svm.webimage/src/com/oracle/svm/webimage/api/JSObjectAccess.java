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
package com.oracle.svm.webimage.api;

import org.graalvm.webimage.api.JSObject;

import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;

/**
 * Methods for field accesses on subtypes of {@link JSObject}.
 *
 * The following methods are intrinsified in the compiler. The intrinsification is implemented in
 * WebImageJSNodeLowerer during the lowering of foreign calls.
 *
 * <h2>Why intrinsification?</h2>
 *
 * The main reason is to make generated code Closure-friendly. Previously, the code uses a['prop']
 * to access object fields, which Closure cannot reason about and will result in errors without
 * extern files. It's unreasonable to have extern files for code included with @JS.Code
 * or @JS.Code.Include. That means users cannot generate a non-Closure image and use their own
 * Closure-workflow to compress it even if the image contains all the JS code. The intrinsification
 * solves the problem.
 */
public class JSObjectAccess {
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static native boolean getBoolean(JSObject self, String field);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static native byte getByte(JSObject self, String field);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static native short getShort(JSObject self, String field);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static native char getChar(JSObject self, String field);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static native int getInt(JSObject self, String field);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static native float getFloat(JSObject self, String field);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static native long getLong(JSObject self, String field);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static native double getDouble(JSObject self, String field);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static native Object getObject(JSObject self, String field);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static native void putBoolean(JSObject self, String field, boolean value);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static native void putByte(JSObject self, String field, byte value);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static native void putShort(JSObject self, String field, short value);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static native void putChar(JSObject self, String field, char value);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static native void putInt(JSObject self, String field, int value);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static native void putFloat(JSObject self, String field, float value);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static native void putLong(JSObject self, String field, long value);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static native void putDouble(JSObject self, String field, double value);

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static native void putObject(JSObject self, String field, Object value);
}
