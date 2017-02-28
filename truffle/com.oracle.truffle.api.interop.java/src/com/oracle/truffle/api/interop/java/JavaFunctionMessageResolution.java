/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = JavaFunctionObject.class, language = JavaInteropLanguage.class)
class JavaFunctionMessageResolution {

    @Resolve(message = "EXECUTE")
    abstract static class ExecuteNode extends Node {

        @Child private DoExecuteNode doExecute;

        public Object access(JavaFunctionObject function, Object[] args) {
            if (doExecute == null || args.length != doExecute.numberOfArguments()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                doExecute = insert(new DoExecuteNode(args.length));
            }
            return doExecute.execute(function.method, function.obj, args);
        }

        static final class DoExecuteNode extends Node {

            @Children private final ToJavaNode[] toJava;
            @Child private ToPrimitiveNode primitive = new ToPrimitiveNode();

            DoExecuteNode(int argsLength) {
                this.toJava = new ToJavaNode[argsLength];
                for (int i = 0; i < argsLength; i++) {
                    this.toJava[i] = ToJavaNodeGen.create();
                }
            }

            int numberOfArguments() {
                return toJava.length;
            }

            @ExplodeLoop
            Object execute(Method method, Object obj, Object[] args) {
                Object[] convertedArguments = new Object[toJava.length];
                TypeAndClass<?>[] types = getTypes(method, toJava.length);
                for (int i = 0; i < toJava.length; i++) {
                    convertedArguments[i] = toJava[i].execute(args[i], types[i]);
                }
                return doInvoke(method, obj, convertedArguments);
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
                            final GenericArrayType arrayType = (GenericArrayType) argumentTypes[argumentTypes.length - 1];
                            final Class<?> arrayClazz = argumentClasses[argumentClasses.length - 1];
                            types[i] = new TypeAndClass<>(arrayType.getGenericComponentType(), arrayClazz.getComponentType());
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
            private Object doInvoke(Method method, Object obj, Object[] args) {
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
                    return reflectiveInvoke(method, obj, arguments);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    throw new IllegalStateException(ex);
                }
            }

            @TruffleBoundary
            private Object reflectiveInvoke(Method method, Object obj, Object[] arguments) throws IllegalAccessException, InvocationTargetException {
                method.setAccessible(true);
                Object ret = method.invoke(obj, arguments);
                if (primitive.isPrimitive(ret)) {
                    return ret;
                }
                return JavaInterop.asTruffleObject(ret);
            }
        }

    }

    @Resolve(message = "IS_EXECUTABLE")
    abstract static class IsExecutableNode extends Node {

        public Object access(@SuppressWarnings("unused") JavaFunctionObject receiver) {
            return Boolean.TRUE;
        }

    }

}
