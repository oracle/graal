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
package com.oracle.truffle.polyglot;

import java.lang.reflect.Type;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.TruffleObject;

class HostInteropErrors {

    @TruffleBoundary
    static RuntimeException nullCoercion(PolyglotLanguageContext languageContext, TruffleObject nullValue, Type targetType) {
        return newNullPointerException(String.format("Cannot convert null value %s to Java type '%s'.",
                        getValueInfo(languageContext, nullValue),
                        targetType.getTypeName()));
    }

    @TruffleBoundary
    static RuntimeException cannotConvert(PolyglotLanguageContext languageContext, Object value, Type targetType, String reason) {
        return newClassCastException(String.format("Cannot convert %s to Java type '%s': %s",
                        getValueInfo(languageContext, value),
                        targetType.getTypeName(),
                        reason));
    }

    @TruffleBoundary
    static RuntimeException invalidListIndex(PolyglotLanguageContext context, Object receiver, Type componentType, int index) {
        String message = String.format("Invalid index %s for List<%s> %s.", index, formatComponentType(componentType), getValueInfo(context, receiver));
        throw newArrayIndexOutOfBounds(message);
    }

    private static Object formatComponentType(Type componentType) {
        return (componentType == null || componentType == Object.class) ? "Object" : componentType.getTypeName();
    }

    @TruffleBoundary
    static RuntimeException listUnsupported(PolyglotLanguageContext context, Object receiver, Type componentType, String operation) {
        String message = String.format("Unsupported operation %s for List<%s> %s.", operation, formatComponentType(componentType), getValueInfo(context, receiver));
        throw newUnsupportedOperationException(message);
    }

    @TruffleBoundary
    static RuntimeException mapUnsupported(PolyglotLanguageContext context, Object receiver, Type keyType, Type valueType, String operation) {
        String message = String.format("Unsupported operation %s for Map<%s, %s> %s.", operation, formatComponentType(keyType), formatComponentType(valueType), getValueInfo(context, receiver));
        throw newUnsupportedOperationException(message);
    }

    @TruffleBoundary
    static RuntimeException invalidMapValue(PolyglotLanguageContext context, Object receiver, Type keyType, Type valueType, Object identifier, Object value) {
        throw newClassCastException(
                        String.format("Invalid value %s for Map<%s, %s> %s and identifier '%s'.",
                                        getValueInfo(context, value), formatComponentType(keyType), formatComponentType(valueType), getValueInfo(context, receiver), identifier));
    }

    @TruffleBoundary
    static RuntimeException invalidMapIdentifier(PolyglotLanguageContext context, Object receiver, Type keyType, Type valueType, Object identifier) {
        if (identifier instanceof Number || identifier instanceof String) {
            throw newIllegalArgumentException(
                            String.format("Invalid or unmodifiable value for identifier '%s' for Map<%s, %s> %s.", identifier, formatComponentType(keyType),
                                            formatComponentType(valueType), getValueInfo(context, receiver)));
        } else {
            throw newIllegalArgumentException(
                            String.format("Illegal identifier type '%s' for Map<%s, %s> %s.", identifier == null ? "null" : identifier.getClass().getTypeName(), formatComponentType(keyType),
                                            formatComponentType(valueType), getValueInfo(context, receiver)));
        }
    }

    @TruffleBoundary
    static RuntimeException invalidListValue(PolyglotLanguageContext context, Object receiver, Type componentType, int identifier, Object value) {
        throw newClassCastException(
                        String.format("Invalid value %s for List<%s> %s and index %s.",
                                        getValueInfo(context, value), formatComponentType(componentType), getValueInfo(context, receiver), identifier));
    }

    static RuntimeException invalidExecuteArgumentType(PolyglotLanguageContext context, Object receiver, Object[] arguments) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument when executing %s with arguments %s.", getValueInfo(context, receiver), Arrays.asList(formattedArgs));
        throw newIllegalArgumentException(message);
    }

    static RuntimeException invalidInstantiateArgumentType(PolyglotLanguageContext context, Object receiver, Object[] arguments) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument when instantiating %s with arguments %s.", getValueInfo(context, receiver), Arrays.asList(formattedArgs));
        throw newIllegalArgumentException(message);
    }

    static RuntimeException invalidInstantiateArity(PolyglotLanguageContext context, Object receiver, Object[] arguments, int expected, int actual) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument count when instantiating %s with arguments %s. Expected %s argument(s) but got %s.",
                        getValueInfo(context, receiver), Arrays.asList(formattedArgs), expected, actual);
        throw newIllegalArgumentException(message);
    }

    static RuntimeException invalidExecuteArity(PolyglotLanguageContext context, Object receiver, Object[] arguments, int expected, int actual) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument count when executing %s with arguments %s. Expected %s argument(s) but got %s.",
                        getValueInfo(context, receiver), Arrays.asList(formattedArgs), expected, actual);
        throw newIllegalArgumentException(message);
    }

    @TruffleBoundary
    static RuntimeException invokeUnsupported(PolyglotLanguageContext context, Object receiver, String identifier) {
        String message = String.format("Unsupported operation identifier '%s' and  object %s. Identifier is not executable or instantiable.", identifier, getValueInfo(context, receiver));
        throw newUnsupportedOperationException(message);
    }

    @TruffleBoundary
    static RuntimeException executeUnsupported(PolyglotLanguageContext context, Object receiver) {
        String message = String.format("Unsupported operation for object %s. Object is not executable or instantiable.", getValueInfo(context, receiver));
        throw newUnsupportedOperationException(message);
    }

    private static String[] formatArgs(PolyglotLanguageContext context, Object[] arguments) {
        String[] formattedArgs = new String[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            formattedArgs[i] = getValueInfo(context, arguments[i]);
        }
        return formattedArgs;
    }

    static String getValueInfo(PolyglotLanguageContext languageContext, Object value) {
        return PolyglotValue.getValueInfo(languageContext, value);
    }

    private static RuntimeException newNullPointerException(String message) {
        CompilerDirectives.transferToInterpreter();
        throw new PolyglotNullPointerException(message);
    }

    private static RuntimeException newUnsupportedOperationException(String message) {
        CompilerDirectives.transferToInterpreter();
        throw new PolyglotUnsupportedException(message);
    }

    private static RuntimeException newClassCastException(String message) {
        CompilerDirectives.transferToInterpreter();
        throw new PolyglotClassCastException(message);
    }

    static final RuntimeException newIllegalArgumentException(String message) {
        CompilerDirectives.transferToInterpreter();
        throw new PolyglotIllegalArgumentException(message);
    }

    private static RuntimeException newArrayIndexOutOfBounds(String message) {
        CompilerDirectives.transferToInterpreter();
        throw new PolyglotArrayIndexOutOfBoundsException(message);
    }

}
