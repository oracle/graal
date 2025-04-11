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

package com.oracle.svm.hosted.webimage;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;

import java.util.EnumMap;
import java.util.Map;

import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.webimage.api.JSObjectAccess;
import com.oracle.svm.webimage.platform.WebImageJSPlatform;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;

import jdk.vm.ci.meta.JavaKind;

@AutomaticallyRegisteredFeature
@Platforms(WebImageJSPlatform.class)
public class JSObjectAccessFeature implements InternalFeature {

    public static final SubstrateForeignCallDescriptor GET_BOOLEAN = SnippetRuntime.findForeignCall(JSObjectAccess.class, "getBoolean", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor GET_BYTE = SnippetRuntime.findForeignCall(JSObjectAccess.class, "getByte", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor GET_SHORT = SnippetRuntime.findForeignCall(JSObjectAccess.class, "getShort", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor GET_CHAR = SnippetRuntime.findForeignCall(JSObjectAccess.class, "getChar", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor GET_INT = SnippetRuntime.findForeignCall(JSObjectAccess.class, "getInt", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor GET_FLOAT = SnippetRuntime.findForeignCall(JSObjectAccess.class, "getFloat", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor GET_LONG = SnippetRuntime.findForeignCall(JSObjectAccess.class, "getLong", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor GET_DOUBLE = SnippetRuntime.findForeignCall(JSObjectAccess.class, "getDouble", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor GET_OBJECT = SnippetRuntime.findForeignCall(JSObjectAccess.class, "getObject", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor PUT_BOOLEAN = SnippetRuntime.findForeignCall(JSObjectAccess.class, "putBoolean", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor PUT_BYTE = SnippetRuntime.findForeignCall(JSObjectAccess.class, "putByte", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor PUT_SHORT = SnippetRuntime.findForeignCall(JSObjectAccess.class, "putShort", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor PUT_CHAR = SnippetRuntime.findForeignCall(JSObjectAccess.class, "putChar", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor PUT_INT = SnippetRuntime.findForeignCall(JSObjectAccess.class, "putInt", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor PUT_FLOAT = SnippetRuntime.findForeignCall(JSObjectAccess.class, "putFloat", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor PUT_LONG = SnippetRuntime.findForeignCall(JSObjectAccess.class, "putLong", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor PUT_DOUBLE = SnippetRuntime.findForeignCall(JSObjectAccess.class, "putDouble", NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor PUT_OBJECT = SnippetRuntime.findForeignCall(JSObjectAccess.class, "putObject", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = {
                    GET_BOOLEAN,
                    GET_BYTE,
                    GET_SHORT,
                    GET_CHAR,
                    GET_INT,
                    GET_FLOAT,
                    GET_LONG,
                    GET_DOUBLE,
                    GET_OBJECT,

                    PUT_BOOLEAN,
                    PUT_BYTE,
                    PUT_SHORT,
                    PUT_CHAR,
                    PUT_INT,
                    PUT_FLOAT,
                    PUT_LONG,
                    PUT_DOUBLE,
                    PUT_OBJECT,
    };
    public static final Map<JavaKind, SubstrateForeignCallDescriptor> GETTERS = new EnumMap<>(JavaKind.class);
    public static final Map<JavaKind, SubstrateForeignCallDescriptor> SETTERS = new EnumMap<>(JavaKind.class);

    static {
        GETTERS.put(JavaKind.Boolean, GET_BOOLEAN);
        GETTERS.put(JavaKind.Byte, GET_BYTE);
        GETTERS.put(JavaKind.Short, GET_SHORT);
        GETTERS.put(JavaKind.Char, GET_CHAR);
        GETTERS.put(JavaKind.Int, GET_INT);
        GETTERS.put(JavaKind.Float, GET_FLOAT);
        GETTERS.put(JavaKind.Long, GET_LONG);
        GETTERS.put(JavaKind.Double, GET_DOUBLE);
        GETTERS.put(JavaKind.Object, GET_OBJECT);

        SETTERS.put(JavaKind.Boolean, PUT_BOOLEAN);
        SETTERS.put(JavaKind.Byte, PUT_BYTE);
        SETTERS.put(JavaKind.Short, PUT_SHORT);
        SETTERS.put(JavaKind.Char, PUT_CHAR);
        SETTERS.put(JavaKind.Int, PUT_INT);
        SETTERS.put(JavaKind.Float, PUT_FLOAT);
        SETTERS.put(JavaKind.Long, PUT_LONG);
        SETTERS.put(JavaKind.Double, PUT_DOUBLE);
        SETTERS.put(JavaKind.Object, PUT_OBJECT);
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(FOREIGN_CALLS);
    }
}
