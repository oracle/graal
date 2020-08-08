/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.nodes.Node;

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

        public abstract Object invokeGuestToHost(Object receiver, Object[] arguments, PolyglotEngineImpl engine, PolyglotLanguageContext context, Node node);

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

        abstract static class ReflectBase extends SingleMethod {

            @CompilationFinal private CallTarget doInvokeTarget;

            ReflectBase(Executable executable) {
                super(executable);
            }

            @Override
            public Object invokeGuestToHost(Object receiver, Object[] arguments, PolyglotEngineImpl engine, PolyglotLanguageContext languageContext, Node node) {
                CallTarget target = this.doInvokeTarget;
                if (target == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    doInvokeTarget = target = languageContext.context.engine.getHostToGuestCodeCache().reflectionHostInvoke;
                }
                assert target == languageContext.context.engine.getHostToGuestCodeCache().reflectionHostInvoke;

                return GuestToHostRootNode.guestToHostCall(node, target, languageContext, receiver, this, arguments);
            }

        }

        private static final class MethodReflectImpl extends ReflectBase {
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
                    throw HostInteropErrors.unsupportedTypeException(arguments, ex);
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

        private static final class ConstructorReflectImpl extends ReflectBase {
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
                    throw HostInteropErrors.unsupportedTypeException(arguments, ex);
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

        abstract static class MHBase extends SingleMethod {
            @CompilationFinal private MethodHandle methodHandle;

            MHBase(Executable executable) {
                super(executable);
            }

            @Override
            public final Object invoke(Object receiver, Object[] arguments) throws Throwable {
                MethodHandle handle = methodHandle;
                if (handle == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    handle = makeMethodHandle();
                    methodHandle = handle;
                }
                return invokeHandle(handle, receiver, arguments);
            }

            @TruffleBoundary(allowInlining = true)
            static Object invokeHandle(MethodHandle invokeHandle, Object receiver, Object[] arguments) throws Throwable {
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

            @Override
            public Object invokeGuestToHost(Object receiver, Object[] arguments, PolyglotEngineImpl engine, PolyglotLanguageContext languageContext, Node node) {
                MethodHandle handle = methodHandle;
                if (handle == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    handle = makeMethodHandle();
                    methodHandle = handle;
                }
                CallTarget target = engine.getHostToGuestCodeCache().methodHandleHostInvoke;
                CompilerAsserts.partialEvaluationConstant(target);
                return GuestToHostRootNode.guestToHostCall(node, target, languageContext, receiver, handle, arguments);
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
                    Method m = reflectionMethod;
                    final MethodHandle methodHandle = MethodHandles.publicLookup().unreflect(m);
                    return adaptSignature(methodHandle, Modifier.isStatic(m.getModifiers()), m.getParameterCount());
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
