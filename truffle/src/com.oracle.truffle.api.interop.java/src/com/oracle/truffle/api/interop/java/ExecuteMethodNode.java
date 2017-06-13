/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

final class ExecuteMethodNode extends Node {

    @Children private final ToJavaNode[] toJava;
    @Child private ToPrimitiveNode primitive = ToPrimitiveNode.create();

    ExecuteMethodNode(int argsLength) {
        this.toJava = new ToJavaNode[argsLength];
        for (int i = 0; i < argsLength; i++) {
            this.toJava[i] = ToJavaNodeGen.create();
        }
    }

    int numberOfArguments() {
        return toJava.length;
    }

    @ExplodeLoop
    Object execute(Method method, Object obj, Object[] args, Object languageContext) {
        Object[] convertedArguments = new Object[toJava.length];
        TypeAndClass<?>[] types = getTypes(method, toJava.length);
        for (int i = 0; i < toJava.length; i++) {
            convertedArguments[i] = toJava[i].execute(args[i], types[i], languageContext);
        }
        return doInvoke(method, obj, convertedArguments, languageContext);
    }

    @TruffleBoundary
    private static TypeAndClass<?>[] getTypes(Method method, int expectedTypeCount) {
        Type[] argumentTypes = method.getGenericParameterTypes();
        Class<?>[] argumentClasses = method.getParameterTypes();
        if (method.isVarArgs()) {
            TypeAndClass<?>[] types = new TypeAndClass<?>[expectedTypeCount];
            for (int i = 0; i < expectedTypeCount; i++) {
                if (i < argumentTypes.length - 1) {
                    types[i] = new TypeAndClass<>(argumentTypes[i], argumentClasses[i]);
                } else {
                    final Type lastArgumentType = argumentTypes[argumentTypes.length - 1];
                    final Class<?> arrayClazz = argumentClasses[argumentClasses.length - 1];
                    if (lastArgumentType instanceof GenericArrayType) {
                        final GenericArrayType arrayType = (GenericArrayType) lastArgumentType;
                        types[i] = new TypeAndClass<>(arrayType.getGenericComponentType(), arrayClazz.getComponentType());
                    } else {
                        types[i] = new TypeAndClass<>(arrayClazz.getComponentType(), arrayClazz.getComponentType());
                    }
                }
            }
            return types;
        } else {
            assert expectedTypeCount == argumentTypes.length;
            TypeAndClass<?>[] types = new TypeAndClass<?>[expectedTypeCount];
            for (int i = 0; i < expectedTypeCount; i++) {
                types[i] = new TypeAndClass<>(argumentTypes[i], argumentClasses[i]);
            }
            return types;
        }
    }

    @TruffleBoundary
    private Object doInvoke(Method method, Object obj, Object[] args, Object languageContext) {
        try {
            int numberOfArguments = method.getParameterTypes().length;
            Class<?>[] argumentTypes = method.getParameterTypes();
            Object[] arguments = new Object[numberOfArguments];
            if (method.isVarArgs()) {
                for (int i = 0; i < numberOfArguments - 1; i++) {
                    arguments[i] = args[i];
                }
                Class<?> varArgsType = argumentTypes[numberOfArguments - 1].getComponentType();
                Object varArgs = Array.newInstance(varArgsType, args.length - numberOfArguments + 1);
                for (int i = numberOfArguments - 1, j = 0; i < args.length; i++, j++) {
                    Array.set(varArgs, j, args[i]);
                }
                arguments[numberOfArguments - 1] = varArgs;
            } else {
                for (int i = 0; i < args.length; i++) {
                    arguments[i] = args[i];
                }
            }
            return reflectiveInvoke(method, obj, arguments, languageContext);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @TruffleBoundary
    private Object reflectiveInvoke(Method method, Object obj, Object[] arguments, Object languageContext) throws IllegalAccessException, InvocationTargetException {
        method.setAccessible(true);
        Object ret = method.invoke(obj, arguments);
        if (primitive.isPrimitive(ret)) {
            return ret;
        }
        return JavaInterop.toGuestValue(ret, languageContext);
    }
}
