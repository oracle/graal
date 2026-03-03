/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.vmaccess.guest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Invocation handler for dynamic proxies that implement a "guest" interface and forward calls to a
 * "host" object, adapting argument/return types and translating exceptions as specified by
 * {@code VMAccess.createCallback()}.
 */
final class GuestCallbackHandler implements InvocationHandler {
    private final Object target;
    private final Map<Method, Object> methodMap;

    private GuestCallbackHandler(Object target, Map<Method, Object> methodMap) {
        this.target = target;
        this.methodMap = methodMap;
    }

    // Called directly by the host in EspressoExternalVMAccess
    static Object createProxy(Object target, Map<Method, Object> methodMap, Class<?> guestClass) {
        return Proxy.newProxyInstance(guestClass.getClassLoader(), new Class<?>[]{guestClass}, new GuestCallbackHandler(target, methodMap));
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
                    if (!(Proxy.getInvocationHandler(other) instanceof GuestCallbackHandler otherCallbackHandler)) {
                        return false;
                    }
                    return identical(target, otherCallbackHandler.target);
                case "hashCode":
                    return identityHashCode(target);
                case "toString":
                    return "Proxy[" + getClassName(target) + "]@" + identityHashCode(target);
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        }
        assert method.getDeclaringClass().isInterface();
        Object invocable = methodMap.get(method);
        if (invocable != null) {
            return execute(invocable, target, args);
        }
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }
        throw new UnsupportedOperationException(method.getName());
    }

    // Called directly by the host in EspressoExternalVMAccess
    static Map<Method, Object> computeMethodMap(Class<?> guestClass, Object invocables) {
        Map<Method, Object> map = new HashMap<>();
        Set<Class<?>> seen = new HashSet<>();
        addMethods(guestClass, map, invocables, seen);
        return Map.copyOf(map);
    }

    private static void addMethods(Class<?> guestClass, Map<Method, Object> map, Object invocables, Set<Class<?>> seen) {
        assert guestClass.isInterface();
        if (!seen.add(guestClass)) {
            return;
        }
        for (Method method : guestClass.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            String methodSymbol = getMethodSymbol(method);
            Object invocable = readMember(invocables, methodSymbol);
            if (invocable == null) {
                if (method.isDefault()) {
                    continue;
                }
                throw new IllegalArgumentException("Bad invocables: missing " + methodSymbol);
            }
            map.put(method, invocable);
        }
        for (Class<?> superInterface : guestClass.getInterfaces()) {
            addMethods(superInterface, map, invocables, seen);
        }
    }

    private static String getMethodSymbol(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getDeclaringClass().descriptorString()).append("#").append(method.getName()).append('(');
        for (Class<?> parameterType : method.getParameterTypes()) {
            sb.append(parameterType.descriptorString());
        }
        sb.append(')').append(method.getReturnType().descriptorString());
        return sb.toString();
    }

    private static native int identityHashCode(Object o);

    private static native int identical(Object o1, Object o2);

    static native String getClassName(Object o);

    private static native Object readMember(Object receiver, String member);

    private static native Object execute(Object invocable, Object target, Object[] args);
}
