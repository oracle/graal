/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp.proxy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.function.Predicate;

import jdk.graal.compiler.core.common.CompilerProfiler;
import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotMemoryAccessProvider;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotProfilingInfo;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;

//JaCoCo Exclude

/**
 * A proxy object for a compiler-interface class.
 * <p>
 * The implementations of this interface are static proxies for JVMCI objects. We use static proxies
 * instead of {@link Proxy dynamic proxies} to minimize the amount of reachable methods in a
 * libgraal image and for a finer control over which interface methods need to be overridden.
 * <ul>
 * <li>{@link SymbolicMethod} and {@link InvokableMethod} are replacements for {@link Method}. Using
 * an {@link InvokableMethod} instead of an invokable {@link Method} avoids marking the methods as
 * compilation roots, allowing the points-to analysis to keep the reachable methods only.</li>
 * <li>The {@link SymbolicMethod} and {@link InvokableMethod} instances are stored in static final
 * fields and initialized at image build time. This is preferred over allocation at run time.</li>
 * <li>Interface methods with a default implementation (implemented by calling other interface
 * methods) do not require an overriding implementation. Omitting the implementation can make
 * recording and replay more efficient. For example, many methods of {@link ResolvedJavaMethod} are
 * implemented by delegating to {@link ResolvedJavaMethod#getModifiers()}, so we override
 * {@link ResolvedJavaMethod#getModifiers()} but do not override
 * {@link ResolvedJavaMethod#isInterface()}.</li>
 * </ul>
 *
 * @see jdk.graal.compiler.hotspot.replaycomp.ReplayCompilationSupport
 * @see jdk.graal.compiler.hotspot.replaycomp.CompilerInterfaceDeclarations
 */
public interface CompilationProxy {
    /**
     * Returns the underlying object being proxied or {@code null} if such an object was not
     * identified in this VM.
     *
     * @return the underlying object
     */
    Object unproxify();

    /**
     * Represents a compiler-interface method that can be invoked.
     */
    @FunctionalInterface
    interface InvokableMethod {
        /**
         * Invokes the method on the given receiver object with the provided arguments.
         *
         * @param receiver the object on which to invoke the method
         * @param args the method arguments
         * @return the result of the method invocation
         * @throws InvocationTargetException if the method invocation fails
         * @throws IllegalAccessException if access to the method is denied
         */
        Object invoke(Object receiver, Object[] args) throws InvocationTargetException, IllegalAccessException;
    }

    /**
     * A symbolic non-invokable representation of a compiler-interface method.
     *
     * @param methodAndParamNames the method name and parameter class names
     */
    record SymbolicMethod(String[] methodAndParamNames) {
        public SymbolicMethod(String methodName, Class<?>... params) {
            this(toArray(methodName, params));
        }

        /**
         * Returns {@code true} if the method has parameters.
         */
        public boolean hasParams() {
            return methodAndParamNames.length > 1;
        }

        /**
         * Returns the number of method parameters.
         */
        public int paramCount() {
            return methodAndParamNames.length - 1;
        }

        private static String[] toArray(String methodName, Class<?>[] params) {
            String[] result = new String[params.length + 1];
            result[0] = methodName;
            for (int i = 0; i < params.length; i++) {
                result[i + 1] = params[i].getSimpleName();
            }
            return result;
        }

        /**
         * Creates a new symbolic method instance from a list receiver classes, method name, and
         * parameter types. At least one of the receiver classes must declare a method with the
         * given signature.
         *
         * @param receiverClasses the receiver classes
         * @param methodName the method name
         * @param params the parameter types
         */
        public SymbolicMethod(Class<?>[] receiverClasses, String methodName, Class<?>... params) {
            this(methodName, params);
            if (!LibGraalSupport.inLibGraalRuntime()) {
                // Omit the check in the image to avoid increasing image size.
                for (Class<?> receiverClass : receiverClasses) {
                    try {
                        receiverClass.getDeclaredMethod(methodName, params);
                        return;
                    } catch (NoSuchMethodException ignored) {
                    }
                    try {
                        receiverClass.getMethod(methodName, params);
                        return;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                throw new GraalError("Method " + methodName + " not found");
            }
        }

        /**
         * Creates a new symbolic method from a receiver class, method name, and parameter types.
         * The receiver class must declare a method with the given signature.
         *
         * @param receiverClass the receiver class
         * @param methodName the method name
         * @param params the parameter types
         */
        public SymbolicMethod(Class<?> receiverClass, String methodName, Class<?>... params) {
            this(new Class<?>[]{receiverClass}, methodName, params);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof SymbolicMethod that) {
                return Arrays.equals(methodAndParamNames, that.methodAndParamNames);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(methodAndParamNames);
        }

        @Override
        public String toString() {
            return Arrays.toString(methodAndParamNames);
        }
    }

    /**
     * An invocation handler for a proxy object.
     */
    @FunctionalInterface
    interface InvocationHandler {
        /**
         * Handles an invocation on the proxy object.
         *
         * @param proxy the proxy object
         * @param method the symbolic method being invoked
         * @param invokableMethod the underlying compiler-interface method
         * @param args the invocation arguments
         * @return the result of the invocation
         * @throws Throwable thrown by the handler
         */
        Object handle(Object proxy, SymbolicMethod method, InvokableMethod invokableMethod, Object[] args) throws Throwable;
    }

    /**
     * Handles an invocation on a proxy object using the provided invocation handler. This method
     * should be invoked by the proxy implementations.
     *
     * @param handler the invocation handler
     * @param proxy the proxy object
     * @param method the symbolic method being invoked
     * @param invokable the underlying compiler-interface method
     * @param args the invocation arguments
     * @return the result of the invocation
     * @throws UndeclaredThrowableException if the handler throws a checked exception
     */
    static Object handle(InvocationHandler handler, Object proxy, SymbolicMethod method, InvokableMethod invokable, Object... args) {
        try {
            return handler.handle(proxy, method, wrapInvocationExceptions(invokable), (args.length == 0) ? null : args);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new UndeclaredThrowableException(e);
        }
    }

    /**
     * Returns a new invokable method that wraps the exceptions thrown by the provided invokable
     * method.
     *
     * @param invokable an invokable method that can throw an unwrapped exception
     * @return an invokable method that can throw an {@link InvocationTargetException}
     */
    static InvokableMethod wrapInvocationExceptions(InvokableMethod invokable) {
        return (Object receiver, Object[] actualArgs) -> {
            try {
                return invokable.invoke(receiver, actualArgs);
            } catch (Throwable e) {
                throw new InvocationTargetException(e);
            }
        };
    }

    /**
     * Creates a new compilation proxy instance for the specified class and invocation handler.
     *
     * @param clazz the class (interface) that the proxy must implement
     * @param handler the invocation handler for the proxy
     * @return a new compilation proxy instance
     * @throws GraalError if no proxy class exists for the specified interfaces
     */
    static CompilationProxy newProxyInstance(Class<?> clazz, InvocationHandler handler) {
        if (clazz == HotSpotResolvedObjectType.class) {
            return new HotSpotResolvedObjectTypeProxy(handler);
        } else if (clazz == HotSpotResolvedJavaType.class) {
            return new HotSpotResolvedJavaTypeProxy(handler);
        } else if (clazz == HotSpotResolvedJavaMethod.class) {
            return new HotSpotResolvedJavaMethodProxy(handler);
        } else if (clazz == HotSpotResolvedJavaField.class) {
            return new HotSpotResolvedJavaFieldProxy(handler);
        } else if (clazz == HotSpotVMConfigAccess.class) {
            return new HotSpotVMConfigAccessProxy(handler);
        } else if (clazz == MetaAccessProvider.class) {
            return new MetaAccessProviderProxy(handler);
        } else if (clazz == HotSpotConstantReflectionProvider.class) {
            return new HotSpotConstantReflectionProviderProxy(handler);
        } else if (clazz == MethodHandleAccessProvider.class) {
            return new MethodHandleAccessProviderProxy(handler);
        } else if (clazz == HotSpotMemoryAccessProvider.class) {
            return new HotSpotMemoryAccessProviderProxy(handler);
        } else if (clazz == HotSpotCodeCacheProvider.class) {
            return new HotSpotCodeCacheProviderProxy(handler);
        } else if (clazz == CompilerProfiler.class) {
            return new CompilerProfilerProxy(handler);
        } else if (clazz == ConstantPool.class) {
            return new ConstantPoolProxy(handler);
        } else if (clazz == Signature.class) {
            return new SignatureProxy(handler);
        } else if (clazz == HotSpotObjectConstant.class) {
            return new HotSpotObjectConstantProxy(handler);
        } else if (clazz == HotSpotMetaspaceConstant.class) {
            return new HotSpotMetaspaceConstantProxy(handler);
        } else if (clazz == HotSpotProfilingInfo.class) {
            return new HotSpotProfilingInfoProxy(handler);
        } else if (clazz == ProfilingInfo.class) {
            return new ProfilingInfoProxy(handler);
        } else if (clazz == SpeculationLog.class) {
            return new SpeculationLogProxy(handler);
        } else if (clazz == Predicate.class) {
            return new PredicateProxy(handler);
        } else {
            throw new GraalError("No proxy class for " + clazz);
        }
    }
}
