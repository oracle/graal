/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.host;

import java.lang.reflect.Type;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.UnsupportedTypeException;

final class HostInteropErrors {

    private HostInteropErrors() {
    }

    @TruffleBoundary
    static RuntimeException nullCoercion(HostContext context, Object nullValue, Type targetType) {
        throw HostEngineException.nullPointer(context.access, String.format("Cannot convert null value %s to Java type '%s'.",
                        getValueInfo(context, nullValue),
                        targetType.getTypeName()));
    }

    @TruffleBoundary
    static RuntimeException cannotConvertPrimitive(HostContext context, Object value, Class<?> targetType) {
        String reason;
        if (HostToTypeNode.isPrimitiveTarget(targetType)) {
            reason = "Invalid or lossy primitive coercion.";
        } else {
            reason = "Unsupported target type.";
        }
        return HostEngineException.classCast(context.access, String.format("Cannot convert %s to Java type '%s': %s",
                        getValueInfo(context, value),
                        targetType.getTypeName(),
                        reason));
    }

    @TruffleBoundary
    static RuntimeException cannotConvert(HostContext context, Object value, Type targetType, String reason) {
        return HostEngineException.classCast(context.access, String.format("Cannot convert %s to Java type '%s': %s",
                        getValueInfo(context, value),
                        targetType.getTypeName(),
                        reason));
    }

    @TruffleBoundary
    static RuntimeException invalidArrayIndex(HostContext context, Object receiver, Type componentType, int index) {
        String message = String.format("Invalid array index %s for %s[] %s.", index, formatComponentType(componentType), getValueInfo(context, receiver));
        throw HostEngineException.arrayIndexOutOfBounds(context.access, message);
    }

    private static Object formatComponentType(Type componentType) {
        return (componentType == null || componentType == Object.class) ? "Object" : componentType.getTypeName();
    }

    @TruffleBoundary
    static RuntimeException arrayReadUnsupported(HostContext context, Object receiver, Type componentType) {
        String message = String.format("Unsupported array read operation for %s[] %s.", formatComponentType(componentType), getValueInfo(context, receiver));
        throw HostEngineException.unsupported(context.access, message);
    }

    @TruffleBoundary
    static RuntimeException invalidExecuteArgumentType(HostContext context, Object receiver, Object[] arguments) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument when executing %s with arguments %s.", getValueInfo(context, receiver), Arrays.asList(formattedArgs));
        throw HostEngineException.illegalArgument(context.access, message);
    }

    @TruffleBoundary
    static RuntimeException invalidExecuteArity(HostContext context, Object receiver, Object[] arguments, int minArity, int maxArity, int actual) {
        String[] formattedArgs = formatArgs(context, arguments);
        String message = String.format("Invalid argument count when executing %s with arguments %s. %s",
                        getValueInfo(context, receiver), Arrays.asList(formattedArgs), formatExpectedArguments(minArity, maxArity, actual));
        throw HostEngineException.illegalArgument(context.access, message);
    }

    private static String[] formatArgs(HostContext context, Object[] arguments) {
        String[] formattedArgs = new String[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            formattedArgs[i] = getValueInfo(context, arguments[i]);
        }
        return formattedArgs;
    }

    static String getValueInfo(HostContext context, Object value) {
        return context.language.access.getValueInfo(context.internalContext, value);
    }

    @TruffleBoundary
    static UnsupportedTypeException unsupportedTypeException(Object[] args, Throwable e) {
        return UnsupportedTypeException.create(args, e.getMessage());
    }

    @TruffleBoundary
    static UnsupportedTypeException unsupportedTypeException(Object arg, Throwable e) {
        return UnsupportedTypeException.create(new Object[]{arg}, e.getMessage());
    }

    static String formatExpectedArguments(int expectedMinArity, int expectedMaxArity, int actualArity) {
        String actual;
        if (actualArity < 0) {
            actual = "unknown";
        } else {
            actual = String.valueOf(actualArity);
        }
        String expected;
        if (expectedMinArity == expectedMaxArity) {
            expected = String.valueOf(expectedMinArity);
        } else {
            if (expectedMaxArity < 0) {
                expected = expectedMinArity + "+";
            } else {
                expected = expectedMinArity + "-" + expectedMaxArity;
            }
        }
        return String.format("Expected %s argument(s) but got %s.", expected, actual);
    }
}
