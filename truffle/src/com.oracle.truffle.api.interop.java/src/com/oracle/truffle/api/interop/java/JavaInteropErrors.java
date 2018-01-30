/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java;

import java.lang.reflect.Type;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.interop.TruffleObject;

class JavaInteropErrors {

    @TruffleBoundary
    static RuntimeException nullCoercion(Object languageContext, TruffleObject nullValue, Type targetType) {
        return newNullPointerException(String.format("Cannot convert null value %s to Java type '%s'.",
                        getValueInfo(languageContext, nullValue),
                        targetType.getTypeName()));
    }

    @TruffleBoundary
    static RuntimeException cannotConvert(Object languageContext, Object value, Type targetType, String reason) {
        return newClassCastException(String.format("Cannot convert %s to Java type '%s': %s",
                        getValueInfo(languageContext, value),
                        targetType.getTypeName(),
                        reason));
    }

    @TruffleBoundary
    static RuntimeException invalidListIndex(Object context, Object receiver, Type componentType, int index) {
        String message = String.format("Invalid index %s for List<%s> %s.", index, formatComponentType(componentType), getValueInfo(context, receiver));
        throw newArrayIndexOutOfBounds(message);
    }

    private static Object formatComponentType(Type componentType) {
        return (componentType == null || componentType == Object.class) ? "Object" : componentType.getTypeName();
    }

    @TruffleBoundary
    static RuntimeException listUnsupported(Object context, Object receiver, Type componentType, String operation) {
        String message = String.format("Unsupported operation %s for List<%s> %s.", operation, formatComponentType(componentType), getValueInfo(context, receiver));
        throw newUnsupportedOperationException(message);
    }

    @TruffleBoundary
    static RuntimeException mapUnsupported(Object context, Object receiver, Type keyType, Type valueType, String operation) {
        String message = String.format("Unsupported operation %s for Map<%s, %s> %s.", operation, formatComponentType(keyType), formatComponentType(valueType), getValueInfo(context, receiver));
        throw newUnsupportedOperationException(message);
    }

    @TruffleBoundary
    static RuntimeException invalidMapValue(Object context, Object receiver, Type keyType, Type valueType, Object identifier, Object value) {
        throw newClassCastException(
                        String.format("Invalid value %s for Map<%s, %s> %s and identifier '%s'.",
                                        getValueInfo(context, value), formatComponentType(keyType), formatComponentType(valueType), getValueInfo(context, receiver), identifier));
    }

    @TruffleBoundary
    static RuntimeException invalidMapIdentifier(Object context, Object receiver, Type keyType, Type valueType, Object identifier) {
        throw newIllegalArgumentException(
                        String.format("Invalid or unmodifiable value for identifier '%s' for Map<%s, %s> %s.", identifier, formatComponentType(keyType),
                                        formatComponentType(valueType), getValueInfo(context, receiver)));
    }

    @TruffleBoundary
    static RuntimeException invalidListValue(Object context, Object receiver, Type componentType, int identifier, Object value) {
        throw newClassCastException(
                        String.format("Invalid value %s for List<%s> %s and index %s.",
                                        getValueInfo(context, value), formatComponentType(componentType), getValueInfo(context, receiver), identifier));
    }

    private static String getValueInfo(Object languageContext, Object value) {
        EngineSupport engine = JavaInterop.ACCESSOR.engine();
        if (engine == null) {
            return value.toString();
        } else {
            return engine.getValueInfo(languageContext, value);
        }
    }

    private static RuntimeException newNullPointerException(String message) {
        CompilerDirectives.transferToInterpreter();
        EngineSupport engine = JavaInterop.ACCESSOR.engine();
        return engine != null ? engine.newNullPointerException(message, null) : new NullPointerException(message);
    }

    private static RuntimeException newUnsupportedOperationException(String message) {
        CompilerDirectives.transferToInterpreter();
        EngineSupport engine = JavaInterop.ACCESSOR.engine();
        return engine != null ? engine.newUnsupportedOperationException(message, null) : new UnsupportedOperationException(message);
    }

    private static RuntimeException newClassCastException(String message) {
        CompilerDirectives.transferToInterpreter();
        EngineSupport engine = JavaInterop.ACCESSOR.engine();
        return engine != null ? engine.newClassCastException(message, null) : new ClassCastException(message);
    }

    static final RuntimeException newIllegalArgumentException(String message) {
        CompilerDirectives.transferToInterpreter();
        EngineSupport engine = JavaInterop.ACCESSOR.engine();
        return engine != null ? engine.newIllegalArgumentException(message, null) : new IllegalArgumentException(message);
    }

    private static RuntimeException newArrayIndexOutOfBounds(String message) {
        CompilerDirectives.transferToInterpreter();
        EngineSupport engine = JavaInterop.ACCESSOR.engine();
        return engine != null ? engine.newArrayIndexOutOfBounds(message, null) : new ArrayIndexOutOfBoundsException(message);
    }

}
