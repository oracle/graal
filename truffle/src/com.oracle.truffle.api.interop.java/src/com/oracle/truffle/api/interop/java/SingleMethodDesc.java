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

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

abstract class SingleMethodDesc implements JavaMethodDesc {
    private final boolean varArgs;
    @CompilationFinal(dimensions = 1) private final Class<?>[] parameterTypes;

    protected SingleMethodDesc(Executable executable) {
        this.varArgs = executable.isVarArgs();
        this.parameterTypes = executable.getParameterTypes();
    }

    public abstract Executable getReflectionMethod();

    public final boolean isVarArgs() {
        return varArgs;
    }

    public abstract Class<?> getReturnType();

    public final Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public final int getParameterCount() {
        return parameterTypes.length;
    }

    public Type[] getGenericParameterTypes() {
        return getReflectionMethod().getGenericParameterTypes();
    }

    @Override
    public String getName() {
        return getReflectionMethod().getName();
    }

    public JavaMethodDesc[] getOverloads() {
        return new JavaMethodDesc[]{this};
    }

    public abstract Object invoke(Object receiver, Object[] arguments) throws Throwable;

    static SingleMethodDesc unreflect(Method reflectionMethod) {
        assert isAccessible(reflectionMethod);
        return new SingleMethodDesc.ConcreteMethod(reflectionMethod);
    }

    static SingleMethodDesc unreflect(Constructor<?> reflectionConstructor) {
        assert isAccessible(reflectionConstructor);
        return new SingleMethodDesc.ConcreteConstructor(reflectionConstructor);
    }

    static boolean isAccessible(Executable method) {
        return Modifier.isPublic(method.getModifiers()) && Modifier.isPublic(method.getDeclaringClass().getModifiers());
    }

    @Override
    public String toString() {
        return "Method[" + getReflectionMethod().toString() + "]";
    }

    static class ConcreteMethod extends SingleMethodDesc {
        private final Method reflectionMethod;

        ConcreteMethod(Method reflectionMethod) {
            super(reflectionMethod);
            this.reflectionMethod = reflectionMethod;
        }

        @Override
        public Method getReflectionMethod() {
            CompilerAsserts.neverPartOfCompilation();
            return reflectionMethod;
        }

        @TruffleBoundary
        @Override
        public Object invoke(Object receiver, Object[] arguments) throws Throwable {
            try {
                return getReflectionMethod().invoke(receiver, arguments);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        @Override
        public Class<?> getReturnType() {
            return getReflectionMethod().getReturnType();
        }

        @Override
        public boolean isInternal() {
            return getReflectionMethod().getDeclaringClass() == Object.class;
        }
    }

    static class ConcreteConstructor extends SingleMethodDesc {
        private final Constructor<?> reflectionConstructor;

        ConcreteConstructor(Constructor<?> reflectionConstructor) {
            super(reflectionConstructor);
            this.reflectionConstructor = reflectionConstructor;
        }

        @Override
        public Constructor<?> getReflectionMethod() {
            CompilerAsserts.neverPartOfCompilation();
            return reflectionConstructor;
        }

        @TruffleBoundary
        @Override
        public Object invoke(Object receiver, Object[] arguments) throws Throwable {
            try {
                return getReflectionMethod().newInstance(arguments);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        @Override
        public Class<?> getReturnType() {
            return getReflectionMethod().getDeclaringClass();
        }
    }
}
