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

abstract class SingleMethodDesc implements JavaMethodDesc {
    public abstract Executable getReflectionMethod();

    public abstract boolean isVarArgs();

    public abstract Class<?>[] getParameterTypes();

    public abstract Class<?> getReturnType();

    public Type[] getGenericParameterTypes() {
        return getReflectionMethod().getGenericParameterTypes();
    }

    public int getParameterCount() {
        return getReflectionMethod().getParameterCount();
    }

    @Override
    public String getName() {
        return getReflectionMethod().getName();
    }

    public JavaMethodDesc[] getOverloads() {
        return new JavaMethodDesc[]{this};
    }

    public abstract Object invoke(Object receiver, Object[] arguments) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException;

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
            this.reflectionMethod = reflectionMethod;
        }

        @Override
        public Method getReflectionMethod() {
            return reflectionMethod;
        }

        @Override
        public Object invoke(Object receiver, Object[] arguments) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            return reflectionMethod.invoke(receiver, arguments);
        }

        @Override
        public boolean isVarArgs() {
            return reflectionMethod.isVarArgs();
        }

        @Override
        public Class<?>[] getParameterTypes() {
            return reflectionMethod.getParameterTypes();
        }

        @Override
        public Class<?> getReturnType() {
            return reflectionMethod.getReturnType();
        }

        @Override
        public boolean isInternal() {
            return reflectionMethod.getDeclaringClass() == Object.class;
        }
    }

    static class ConcreteConstructor extends SingleMethodDesc {
        private final Constructor<?> reflectionConstructor;

        ConcreteConstructor(Constructor<?> reflectionConstructor) {
            this.reflectionConstructor = reflectionConstructor;
        }

        @Override
        public Constructor<?> getReflectionMethod() {
            return reflectionConstructor;
        }

        @Override
        public Object invoke(Object receiver, Object[] arguments) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
            return reflectionConstructor.newInstance(arguments);
        }

        @Override
        public Class<?>[] getParameterTypes() {
            return reflectionConstructor.getParameterTypes();
        }

        @Override
        public Class<?> getReturnType() {
            return reflectionConstructor.getDeclaringClass();
        }

        @Override
        public boolean isVarArgs() {
            return reflectionConstructor.isVarArgs();
        }
    }
}
