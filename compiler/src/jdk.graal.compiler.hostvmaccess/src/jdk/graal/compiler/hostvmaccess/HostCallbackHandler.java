/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hostvmaccess;

import static jdk.graal.compiler.hostvmaccess.HostVMAccess.makeAccessible;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.vmaccess.InvocationException;
import jdk.graal.compiler.vmaccess.VMAccess;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Invocation handler for dynamic proxies that implement a "guest" interface and forward calls to a
 * "host" object, adapting argument/return types and translating exceptions as specified by
 * {@link VMAccess#createCallback}.
 */
final class HostCallbackHandler implements InvocationHandler {
    private final Object target;
    private final Map<Method, MethodHandle> methodMap;

    <T> HostCallbackHandler(T hostTarget, Map<Method, MethodHandle> methodMap) {
        this.target = hostTarget;
        this.methodMap = methodMap;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            switch (method.getName()) {
                case "equals":
                    Object other = args[0];
                    if (other == null || !Proxy.isProxyClass(other.getClass())) {
                        return false;
                    }
                    if (!(Proxy.getInvocationHandler(other) instanceof HostCallbackHandler otherCallbackHandler)) {
                        return false;
                    }
                    return target == otherCallbackHandler.target;
                case "hashCode":
                    return System.identityHashCode(target);
                case "toString":
                    return target.toString();
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        }
        assert method.getDeclaringClass().isInterface();
        MethodHandle targetMethod = methodMap.get(method);
        if (targetMethod != null) {
            return targetMethod.invoke(target, args);
        }
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }
        throw new UnsupportedOperationException(method.getName());
    }

    /**
     * Gathers, for every public, non-static method in the {@code guestClass} interface, a
     * {@link MethodHandle} that invokes a corresponding method in {@code hostClass} after making
     * applicable argument conversions.
     *
     * @param methodHandles collection of handles to methods for performing argument/return value
     *            conversions
     * @return an immutable map for dispatching {@link Method} invocations to {@link MethodHandle}s
     */
    static Map<Method, MethodHandle> computeMethodMap(Class<?> hostClass, Class<?> guestClass, CallbackHandlerMethodHandles methodHandles) {
        Map<Method, MethodHandle> map = new LinkedHashMap<>();
        Set<Class<?>> seen = new LinkedHashSet<>();
        addMethods(hostClass, guestClass, methodHandles, map, seen);
        return Collections.unmodifiableMap(map);
    }

    private static void addMethods(Class<?> hostClass, Class<?> guestClass, CallbackHandlerMethodHandles methodHandles, Map<Method, MethodHandle> map, Set<Class<?>> seen) {
        assert guestClass.isInterface();
        if (!seen.add(guestClass)) {
            return;
        }
        for (Method method : guestClass.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            Method hostMethod = findHostMethod(hostClass, method);
            if (hostMethod == null) {
                continue;
            }
            // e.g., (MyHostType, JavaConstant, int) -> ResolvedJavaMethod
            MethodHandle mh = unreflect(hostMethod);
            MethodHandle[] filters = null;
            for (int i = 0; i < method.getParameterCount(); i++) {
                // e.g., (long) -> JavaConstant
                MethodHandle filter = getArgumentFilter(method.getParameterTypes()[i], hostMethod.getParameterTypes()[i], methodHandles);
                if (filter != null) {
                    if (filters == null) {
                        filters = new MethodHandle[method.getParameterCount()];
                    }
                    filters[i] = filter;
                }
            }
            if (filters != null) {
                // e.g., (MyHostType, long, int) -> ResolvedJavaMethod
                mh = MethodHandles.filterArguments(mh, 1, filters);
            }
            // e.g., (ResolvedJavaMethod) -> Method
            MethodHandle filter = getReturnFilter(method.getReturnType(), hostMethod.getReturnType(), methodHandles);
            if (filter != null) {
                // e.g., (MyHostType, long, int) -> Method
                mh = MethodHandles.filterReturnValue(mh, filter);
            }
            // e.g., (Throwable) -> Method
            MethodHandle wrapAndRethrow = methodHandles.filterException.asType(MethodType.methodType(mh.type().returnType(), Throwable.class));
            // converts thrown exceptions, see filterException(SnippetReflectionProvider, Throwable)
            mh = MethodHandles.catchException(mh, Throwable.class, wrapAndRethrow);
            // e.g., (MyHostType, Object[]) -> Method
            mh = mh.asSpreader(Object[].class, mh.type().parameterCount() - 1);
            map.put(method, mh);
        }
        for (Class<?> superInterface : guestClass.getInterfaces()) {
            addMethods(hostClass, superInterface, methodHandles, map, seen);
        }
    }

    @SuppressWarnings("unused")
    static Object filterException(SnippetReflectionProvider snippetReflectionProvider, Throwable t) {
        if (t instanceof InvocationException e) {
            throw sneakyThrow(snippetReflectionProvider.asObject(Throwable.class, e.getExceptionObject()));
        }
        throw new HostCallbackException(t);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable ex) throws T {
        throw (T) ex;
    }

    private static MethodHandle unreflect(Method hostMethod) {
        makeAccessible(hostMethod);
        try {
            return MethodHandles.lookup().unreflect(hostMethod);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static Method findHostMethod(Class<?> hostClass, Method method) {
        Method hostMethod = null;
        for (Method hostMethodCandidate : hostClass.getMethods()) {
            if (!hostMethodCandidate.getName().equals(method.getName())) {
                continue;
            }
            if (!argumentsCompatible(method.getParameterTypes(), hostMethodCandidate.getParameterTypes())) {
                continue;
            }
            if (!typesCompatible(method.getReturnType(), hostMethodCandidate.getReturnType())) {
                continue;
            }
            if (hostMethod == null) {
                hostMethod = hostMethodCandidate;
            } else {
                throw new RuntimeException("Unimplemented: most specific mapping: " + hostMethod + " vs. " + hostMethodCandidate);
            }
        }
        if (hostMethod == null && !method.isDefault()) {
            throw new IllegalArgumentException("Method compatible with " + method + " not found in class " + hostClass.getName());
        }
        return hostMethod;
    }

    private static boolean argumentsCompatible(Class<?>[] guestParameterTypes, Class<?>[] hostParameterTypes) {
        int parameterCount = guestParameterTypes.length;
        if (hostParameterTypes.length != parameterCount) {
            return false;
        }
        for (int i = 0; i < parameterCount; i++) {
            Class<?> guestParameterType = guestParameterTypes[i];
            Class<?> hostParameterType = hostParameterTypes[i];
            if (!typesCompatible(guestParameterType, hostParameterType)) {
                return false;
            }
        }
        return true;
    }

    private static boolean typesCompatible(Class<?> guestType, Class<?> hostType) {
        if (hostType == JavaConstant.class) {
            return true;
        }
        if (guestType.isPrimitive() || guestType == String.class) {
            return guestType.equals(hostType);
        }
        if (guestType == Class.class) {
            return hostType == ResolvedJavaType.class;
        }
        if (guestType == Field.class) {
            return hostType == ResolvedJavaField.class;
        }
        if (Executable.class.isAssignableFrom(guestType)) {
            return hostType == ResolvedJavaMethod.class;
        }
        return false;
    }

    record CallbackHandlerMethodHandles(
                    MethodHandle forObject, // bound SnippetReflectionProvider.forObject
                    // providers.getSnippetReflectionProvider().asObject(Object.class, x)
                    MethodHandle asObject,
                    // Host<->Guest conversions for JavaConstant/primitive
                    MethodHandle javaConstantForBoolean,
                    MethodHandle javaConstantForByte,
                    MethodHandle javaConstantForShort,
                    MethodHandle javaConstantForChar,
                    MethodHandle javaConstantForInt,
                    MethodHandle javaConstantForLong,
                    MethodHandle javaConstantForFloat,
                    MethodHandle javaConstantForDouble,
                    MethodHandle javaConstantAsBoolean,
                    MethodHandle javaConstantAsByte,
                    MethodHandle javaConstantAsShort,
                    MethodHandle javaConstantAsChar,
                    MethodHandle javaConstantAsInt,
                    MethodHandle javaConstantAsLong,
                    MethodHandle javaConstantAsFloat,
                    MethodHandle javaConstantAsDouble,
                    // Field/Executable/Class mapping
                    MethodHandle lookupJavaField, // bound MetaAccessProvider.lookupJavaField
                    MethodHandle lookupJavaMethod, // bound MetaAccessProvider.lookupJavaMethod
                    MethodHandle lookupJavaType, // bound MetaAccessProvider.lookupJavaType
                    MethodHandle originalField, // bound SnippetReflectionProvider.originalField
                    MethodHandle originalMethod, // bound SnippetReflectionProvider.originalMethod
                    MethodHandle originalClass, // bound SnippetReflectionProvider.originalClass
                    // exception mapping
                    MethodHandle filterException) {
    }

    // Host -> Guest
    private static MethodHandle getReturnFilter(Class<?> guestType, Class<?> hostType, CallbackHandlerMethodHandles methodHandles) {
        if (hostType == JavaConstant.class) {
            if (guestType.isPrimitive()) {
                if (guestType == boolean.class) {
                    return methodHandles.javaConstantAsBoolean;
                }
                if (guestType == byte.class) {
                    return methodHandles.javaConstantAsByte;
                }
                if (guestType == short.class) {
                    return methodHandles.javaConstantAsShort;
                }
                if (guestType == char.class) {
                    return methodHandles.javaConstantAsChar;
                }
                if (guestType == int.class) {
                    return methodHandles.javaConstantAsInt;
                }
                if (guestType == long.class) {
                    return methodHandles.javaConstantAsLong;
                }
                if (guestType == float.class) {
                    return methodHandles.javaConstantAsFloat;
                }
                if (guestType == double.class) {
                    return methodHandles.javaConstantAsDouble;
                }
                throw new RuntimeException("Should not reach here: " + guestType);
            } else {
                return methodHandles.asObject;
            }
        }
        if (guestType.isPrimitive()) {
            return null;
        }
        if (guestType == String.class) {
            return null;
        }
        if (guestType == Class.class) {
            return methodHandles.originalClass;
        }
        if (guestType == Field.class) {
            return methodHandles.originalField;
        }
        if (Executable.class.isAssignableFrom(guestType)) {
            return methodHandles.originalMethod;
        }
        throw new RuntimeException("Should not reach here: " + hostType + " -> " + guestType);
    }

    // Guest -> Host
    private static MethodHandle getArgumentFilter(Class<?> guestType, Class<?> hostType, CallbackHandlerMethodHandles methodHandles) {
        if (hostType == JavaConstant.class) {
            if (guestType.isPrimitive()) {
                if (guestType == boolean.class) {
                    return methodHandles.javaConstantForBoolean;
                }
                if (guestType == byte.class) {
                    return methodHandles.javaConstantForByte;
                }
                if (guestType == short.class) {
                    return methodHandles.javaConstantForShort;
                }
                if (guestType == char.class) {
                    return methodHandles.javaConstantForChar;
                }
                if (guestType == int.class) {
                    return methodHandles.javaConstantForInt;
                }
                if (guestType == long.class) {
                    return methodHandles.javaConstantForLong;
                }
                if (guestType == float.class) {
                    return methodHandles.javaConstantForFloat;
                }
                if (guestType == double.class) {
                    return methodHandles.javaConstantForDouble;
                }
                throw new RuntimeException("Should not reach here: " + guestType);
            } else {
                // Box non-primitive objects into JavaConstant via forObject
                return methodHandles.forObject;
            }
        }
        if (guestType.isPrimitive()) {
            return null;
        }
        if (guestType == String.class) {
            return null;
        }
        if (guestType == Class.class) {
            return methodHandles.lookupJavaType;
        }
        if (guestType == Field.class) {
            return methodHandles.lookupJavaField;
        }
        if (Executable.class.isAssignableFrom(guestType)) {
            return methodHandles.lookupJavaMethod;
        }
        throw new RuntimeException("Should not reach here: " + guestType + " -> " + hostType);
    }
}
