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
package com.oracle.truffle.polyglot;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.StringJoiner;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.interop.UnsupportedTypeException;

abstract class HostMethodDesc {

    abstract String getName();

    abstract HostMethodDesc[] getOverloads();

    boolean isInternal() {
        return false;
    }

    abstract boolean isMethod();

    abstract boolean isConstructor();

    abstract static class SingleMethod extends HostMethodDesc {

        private final boolean varArgs;
        @CompilationFinal(dimensions = 1) private final Class<?>[] parameterTypes;
        @CompilationFinal(dimensions = 1) private final Type[] genericParameterTypes;

        protected SingleMethod(Executable executable) {
            this.varArgs = executable.isVarArgs();
            this.parameterTypes = executable.getParameterTypes();
            this.genericParameterTypes = executable.getGenericParameterTypes();
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
            return genericParameterTypes;
        }

        @Override
        public String getName() {
            return getReflectionMethod().getName();
        }

        @Override
        public HostMethodDesc[] getOverloads() {
            return new HostMethodDesc[]{this};
        }

        public abstract Object invoke(Object receiver, Object[] arguments) throws Throwable;

        @Override
        public boolean isMethod() {
            return getReflectionMethod() instanceof Method;
        }

        @Override
        public boolean isConstructor() {
            return getReflectionMethod() instanceof Constructor<?>;
        }

        static SingleMethod unreflect(Method reflectionMethod) {
            assert isAccessible(reflectionMethod);
            if (TruffleOptions.AOT || isCallerSensitive(reflectionMethod)) {
                return new MethodReflectImpl(reflectionMethod);
            } else {
                return new MethodMHImpl(reflectionMethod);
            }
        }

        static SingleMethod unreflect(Constructor<?> reflectionConstructor) {
            assert isAccessible(reflectionConstructor);
            if (TruffleOptions.AOT || isCallerSensitive(reflectionConstructor)) {
                return new ConstructorReflectImpl(reflectionConstructor);
            } else {
                return new ConstructorMHImpl(reflectionConstructor);
            }
        }

        static boolean isAccessible(Executable method) {
            return Modifier.isPublic(method.getModifiers()) && Modifier.isPublic(method.getDeclaringClass().getModifiers());
        }

        static boolean isCallerSensitive(Executable method) {
            Annotation[] annotations = method.getAnnotations();
            for (Annotation annotation : annotations) {
                switch (annotation.annotationType().getName()) {
                    case "sun.reflect.CallerSensitive":
                    case "jdk.internal.reflect.CallerSensitive":
                        return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return "Method[" + getReflectionMethod().toString() + "]";
        }

        private static final class MethodReflectImpl extends SingleMethod {
            private final Method reflectionMethod;

            MethodReflectImpl(Method reflectionMethod) {
                super(reflectionMethod);
                this.reflectionMethod = reflectionMethod;
            }

            @Override
            public Method getReflectionMethod() {
                CompilerAsserts.neverPartOfCompilation();
                return reflectionMethod;
            }

            @Override
            public Object invoke(Object receiver, Object[] arguments) throws Throwable {
                try {
                    return reflectInvoke(reflectionMethod, receiver, arguments);
                } catch (IllegalArgumentException | IllegalAccessException ex) {
                    throw UnsupportedTypeException.raise(ex, arguments);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }

            @TruffleBoundary
            private static Object reflectInvoke(Method reflectionMethod, Object receiver, Object[] arguments)
                            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
                return reflectionMethod.invoke(receiver, arguments);
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

        private static final class ConstructorReflectImpl extends SingleMethod {
            private final Constructor<?> reflectionConstructor;

            ConstructorReflectImpl(Constructor<?> reflectionConstructor) {
                super(reflectionConstructor);
                this.reflectionConstructor = reflectionConstructor;
            }

            @Override
            public Constructor<?> getReflectionMethod() {
                CompilerAsserts.neverPartOfCompilation();
                return reflectionConstructor;
            }

            @Override
            public Object invoke(Object receiver, Object[] arguments) throws Throwable {
                try {
                    return reflectNewInstance(reflectionConstructor, arguments);
                } catch (IllegalArgumentException | IllegalAccessException | InstantiationException ex) {
                    throw UnsupportedTypeException.raise(ex, arguments);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }

            @TruffleBoundary
            private static Object reflectNewInstance(Constructor<?> reflectionConstructor, Object[] arguments)
                            throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
                return reflectionConstructor.newInstance(arguments);
            }

            @Override
            public Class<?> getReturnType() {
                return getReflectionMethod().getDeclaringClass();
            }
        }

        private abstract static class MHBase extends SingleMethod {
            @CompilationFinal private MethodHandle methodHandle;

            MHBase(Executable executable) {
                super(executable);
            }

            @Override
            public final Object invoke(Object receiver, Object[] arguments) throws Throwable {
                if (methodHandle == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    methodHandle = makeMethodHandle();
                }
                try {
                    return invokeHandle(methodHandle, receiver, arguments);
                } catch (ClassCastException ex) {
                    throw UnsupportedTypeException.raise(ex, arguments);
                }
            }

            @TruffleBoundary(allowInlining = true)
            private static Object invokeHandle(MethodHandle invokeHandle, Object receiver, Object[] arguments) throws Throwable {
                return invokeHandle.invokeExact(receiver, arguments);
            }

            protected abstract MethodHandle makeMethodHandle();

            protected static MethodHandle adaptSignature(MethodHandle originalHandle, boolean isStatic, int parameterCount) {
                MethodHandle adaptedHandle = originalHandle;
                adaptedHandle = adaptedHandle.asType(adaptedHandle.type().changeReturnType(Object.class));
                if (isStatic) {
                    adaptedHandle = MethodHandles.dropArguments(adaptedHandle, 0, Object.class);
                } else {
                    adaptedHandle = adaptedHandle.asType(adaptedHandle.type().changeParameterType(0, Object.class));
                }
                adaptedHandle = adaptedHandle.asSpreader(Object[].class, parameterCount);
                return adaptedHandle;
            }
        }

        private static final class MethodMHImpl extends MHBase {
            private final Method reflectionMethod;

            MethodMHImpl(Method reflectionMethod) {
                super(reflectionMethod);
                this.reflectionMethod = reflectionMethod;
            }

            @Override
            public Method getReflectionMethod() {
                CompilerAsserts.neverPartOfCompilation();
                return reflectionMethod;
            }

            @Override
            public Class<?> getReturnType() {
                return getReflectionMethod().getReturnType();
            }

            @Override
            public boolean isInternal() {
                return getReflectionMethod().getDeclaringClass() == Object.class;
            }

            @Override
            protected MethodHandle makeMethodHandle() {
                CompilerAsserts.neverPartOfCompilation();
                try {
                    final MethodHandle methodHandle = MethodHandles.publicLookup().unreflect(reflectionMethod);
                    return adaptSignature(methodHandle, Modifier.isStatic(reflectionMethod.getModifiers()), reflectionMethod.getParameterCount());
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        private static final class ConstructorMHImpl extends MHBase {
            private final Constructor<?> reflectionConstructor;

            ConstructorMHImpl(Constructor<?> reflectionConstructor) {
                super(reflectionConstructor);
                this.reflectionConstructor = reflectionConstructor;
            }

            @Override
            public Constructor<?> getReflectionMethod() {
                CompilerAsserts.neverPartOfCompilation();
                return reflectionConstructor;
            }

            @Override
            public Class<?> getReturnType() {
                return getReflectionMethod().getDeclaringClass();
            }

            @Override
            protected MethodHandle makeMethodHandle() {
                CompilerAsserts.neverPartOfCompilation();
                try {
                    final MethodHandle methodHandle = MethodHandles.publicLookup().unreflectConstructor(reflectionConstructor);
                    return adaptSignature(methodHandle, true, getParameterCount());
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    static final class OverloadedMethod extends HostMethodDesc {
        private final SingleMethod[] overloads;

        OverloadedMethod(SingleMethod[] overloads) {
            this.overloads = overloads;
            assert overloads.length >= 2;
        }

        @Override
        public SingleMethod[] getOverloads() {
            return overloads;
        }

        @Override
        public String getName() {
            return getOverloads()[0].getName();
        }

        @Override
        public boolean isMethod() {
            return getOverloads()[0].isMethod();
        }

        @Override
        public boolean isConstructor() {
            return getOverloads()[0].isConstructor();
        }

        @Override
        public String toString() {
            StringJoiner sj = new StringJoiner(", ", "Method[", "]");
            for (SingleMethod overload : getOverloads()) {
                sj.add(overload.getReflectionMethod().toString());
            }
            return sj.toString();
        }

        @Override
        public boolean isInternal() {
            for (SingleMethod overload : overloads) {
                if (!overload.isInternal()) {
                    return false;
                }
            }
            return true;
        }
    }

}
