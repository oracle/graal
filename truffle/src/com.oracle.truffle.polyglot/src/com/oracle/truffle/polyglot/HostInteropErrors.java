/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot;

import java.lang.reflect.Type;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.UnsupportedTypeException;

final class HostInteropErrors {

    private HostInteropErrors() {
    }

    @TruffleBoundary
    static RuntimeException nullCoercion(PolyglotLanguageContext languageContext, Object nullValue, Type targetType) {
        throw PolyglotEngineException.nullPointer(String.format("Cannot convert null value %s to Java type '%s'.",
                        getValueInfo(languageContext, nullValue),
                        targetType.getTypeName()));
    }

    @TruffleBoundary
    static RuntimeException cannotConvertPrimitive(PolyglotLanguageContext languageContext, Object value, Class<?> targetType) {
        String reason;
        if (ToHostNode.isPrimitiveTarget(targetType)) {
            reason = "Invalid or lossy primitive coercion.";
        } else {
            reason = "Unsupported target type.";
        }
        return PolyglotEngineException.classCast(String.format("Cannot convert %s to Java type '%s': %s",
                        getValueInfo(languageContext, value),
                        targetType.getTypeName(),
                        reason));
    }

    @TruffleBoundary
    static RuntimeException cannotConvert(PolyglotLanguageContext languageContext, Object value, Type targetType, String reason) {
        return PolyglotEngineException.classCast(String.format("Cannot convert %s to Java type '%s': %s",
                        getValueInfo(languageContext, value),
                        targetType.getTypeName(),
                        reason));
    }

    @TruffleBoundary
    static RuntimeException invalidListIndex(PolyglotLanguageContext context, Object receiver, Type componentType, int index) {
        String message = String.format("Invalid index %s for List<%s> %s.", index, formatComponentType(componentType), getValueInfo(context, receiver));
        throw PolyglotEngineException.arrayIndexOutOfBounds(message);
    }

    @TruffleBoundary
    static RuntimeException invalidArrayIndex(PolyglotLanguageContext context, Object receiver, Type componentType, int index) {
        String message = String.format("Invalid array index %s for %s[] %s.", index, formatComponentType(componentType), getValueInfo(context, receiver));
        throw PolyglotEngineException.arrayIndexOutOfBounds(message);
    }

    private static Object formatComponentType(Type componentType) {
        return (componentType == null || componentType == Object.class) ? "Object" : componentType.getTypeName();
    }

    @TruffleBoundary
    static RuntimeException arrayReadUnsupported(PolyglotLanguageContext context, Object receiver, Type componentType) {
        String message = String.format("Unsupported array read operation for %s[] %s.", formatComponentType(componentType), getValueInfo(context, receiver));
        throw PolyglotEngineException.unsupported(message);
    }

    @TruffleBoundary
    static RuntimeException listUnsupported(PolyglotLanguageContext context, Object receiver, Type componentType, String operation) {
        String message = String.format("Unsupported operation %s for List<%s> %s.", operation, formatComponentType(componentType), getValueInfo(context, receiver));
        throw PolyglotEngineException.unsupported(message);
    }

    @TruffleBoundary
    static RuntimeException mapUnsupported(PolyglotLanguageContext context, Object receiver, Type keyType, Type valueType, String operation) {
        String message = String.format("Unsupported operation %s for Map<%s, %s> %s.", operation, formatComponentType(keyType), formatComponentType(valueType), getValueInfo(context, receiver));
        throw PolyglotEngineException.unsupported(message);
    }

    @TruffleBoundary
    static RuntimeException invalidMapValue(PolyglotLanguageContext context, Object receiver, Type keyType, Type valueType, Object identifier, Object value) {
        throw PolyglotEngineException.classCast(
                        String.format("Invalid value %s for Map<%s, %s> %s and identifier '%s'.",
                                        getValueInfo(context, value), formatComponentType(keyType), formatComponentType(valueType), getValueInfo(context, receiver), identifier));
    }

    @TruffleBoundary
    static RuntimeException invalidMapIdentifier(PolyglotLanguageContext context, Object receiver, Type keyType, Type valueType, Object identifier) {
        if (identifier instanceof Number || identifier instanceof String) {
            throw PolyglotEngineException.illegalArgument(
                            String.format("Invalid or unmodifiable value for identifier '%s' for Map<%s, %s> %s.", identifier, formatComponentType(keyType),
                                            formatComponentType(valueType), getValueInfo(context, receiver)));
        } else {
            throw PolyglotEngineException.illegalArgument(
                            String.format("Illegal identifier type '%s' for Map<%s, %s> %s.", identifier == null ? "null" : identifier.getClass().getTypeName(), formatComponentType(keyType),
                                            formatComponentType(valueType), getValueInfo(context, receiver)));
        }
    }

    @TruffleBoundary
    static RuntimeException invalidListValue(PolyglotLanguageContext context, Object receiver, Type componentType, int identifier, Object value) {
        throw PolyglotEngineException.classCast(
                        String.format("Invalid value %s for List<%s> %s and index %s.",
                                        getValueInfo(context, value), formatComponentType(componentType), getValueInfo(context, receiver), identifier));
    }

    @TruffleBoundary
    static RuntimeException invalidExecuteArgumentType(PolyglotLanguageContext context, Object receiver, Object[] arguments) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument when executing %s with arguments %s.", getValueInfo(context, receiver), Arrays.asList(formattedArgs));
        throw PolyglotEngineException.illegalArgument(message);
    }

    @TruffleBoundary
    static RuntimeException invalidInstantiateArgumentType(PolyglotLanguageContext context, Object receiver, Object[] arguments) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument when instantiating %s with arguments %s.", getValueInfo(context, receiver), Arrays.asList(formattedArgs));
        throw PolyglotEngineException.illegalArgument(message);
    }

    @TruffleBoundary
    static RuntimeException invalidInstantiateArity(PolyglotLanguageContext context, Object receiver, Object[] arguments, int expected, int actual) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument count when instantiating %s with arguments %s. Expected %s argument(s) but got %s.",
                        getValueInfo(context, receiver), Arrays.asList(formattedArgs), expected, actual);
        throw PolyglotEngineException.illegalArgument(message);
    }

    @TruffleBoundary
    static RuntimeException invalidExecuteArity(PolyglotLanguageContext context, Object receiver, Object[] arguments, int expected, int actual) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument count when executing %s with arguments %s. Expected %s argument(s) but got %s.",
                        getValueInfo(context, receiver), Arrays.asList(formattedArgs), expected, actual);
        throw PolyglotEngineException.illegalArgument(message);
    }

    @TruffleBoundary
    static RuntimeException invokeUnsupported(PolyglotLanguageContext context, Object receiver, String identifier) {
        String message = String.format("Unsupported operation identifier '%s' and  object %s. Identifier is not executable or instantiable.", identifier, getValueInfo(context, receiver));
        throw PolyglotEngineException.unsupported(message);
    }

    @TruffleBoundary
    static RuntimeException executeUnsupported(PolyglotLanguageContext context, Object receiver) {
        String message = String.format("Unsupported operation for object %s. Object is not executable or instantiable.", getValueInfo(context, receiver));
        throw PolyglotEngineException.unsupported(message);
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

    @TruffleBoundary
    static UnsupportedTypeException unsupportedTypeException(Object[] args, Throwable e) {
        return UnsupportedTypeException.create(args, e.getMessage());
    }

    @TruffleBoundary
    static UnsupportedTypeException unsupportedTypeException(Object arg, Throwable e) {
        return UnsupportedTypeException.create(new Object[]{arg}, e.getMessage());
    }
}
