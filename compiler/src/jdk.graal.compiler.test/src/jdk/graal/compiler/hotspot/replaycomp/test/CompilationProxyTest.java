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
package jdk.graal.compiler.hotspot.replaycomp.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.hotspot.replaycomp.CompilerInterfaceDeclarations;
import jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase;
import jdk.graal.compiler.hotspot.replaycomp.proxy.HotSpotConstantProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.HotSpotResolvedJavaTypeProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.HotSpotVMConfigAccessProxy;
import jdk.graal.compiler.util.CollectionsUtil;

/**
 * Checks that the implementations of {@link CompilationProxy} follow the expected schema. The check
 * is performed by reflectively invoking non-private instance methods of every proxy and verifying
 * that the implementation forwards the calls to the handler with the expected arguments.
 */
public class CompilationProxyTest {
    /**
     * Methods whose implementation is not checked.
     */
    private static final Set<Method> skippedMethods;

    static {
        try {
            skippedMethods = CollectionsUtil.setOf(
                            HotSpotVMConfigAccessProxy.class.getDeclaredMethod("getStore"),
                            CompilationProxyBase.class.getDeclaredMethod("handle", CompilationProxy.SymbolicMethod.class, CompilationProxy.InvokableMethod.class, Object[].class),
                            HotSpotResolvedJavaTypeProxy.class.getDeclaredMethod("handle", CompilationProxy.SymbolicMethod.class, CompilationProxy.InvokableMethod.class, Object[].class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void checkProxyMethods() throws IllegalAccessException, InvocationTargetException {
        CompilerInterfaceDeclarations declarations = CompilerInterfaceDeclarations.build();
        for (CompilerInterfaceDeclarations.Registration registration : declarations.getRegistrations()) {
            Class<?> clazz = registration.clazz();
            CompilationProxy sampleProxy = CompilationProxy.newProxyInstance(clazz, (_, _, _, _) -> null);
            for (Method method : getProxyMethods(sampleProxy)) {
                if (method.isSynthetic() || Modifier.isStatic(method.getModifiers()) || Modifier.isPrivate(method.getModifiers()) || skippedMethods.contains(method)) {
                    continue;
                }

                Object[] args = createValues(method.getParameterTypes());

                if (Modifier.isProtected(method.getModifiers())) {
                    method.setAccessible(true);
                    try {
                        method.invoke(sampleProxy, args);
                    } catch (InvocationTargetException e) {
                        assertTrue("protected method should throw UnsupportedOperationException", e.getTargetException() instanceof UnsupportedOperationException);
                        continue;
                    }
                    Assert.fail("expected the protected method to throw");
                }

                Object returnValue = createValue(method.getReturnType());

                CompilationProxy[] instance = new CompilationProxy[2];
                boolean[] invoked = new boolean[2];

                // We will directly invoke a method on the first instance.
                instance[0] = CompilationProxy.newProxyInstance(clazz, (proxy, symbolicMethod, invokableMethod, actualArgs) -> {
                    checkHandlerInvocation(method, proxy, symbolicMethod, actualArgs, instance[0], args);
                    // Invoke the second handler using the invokable method.
                    invokableMethod.invoke(instance[1], actualArgs);
                    invoked[0] = true;
                    return returnValue;
                });

                /*
                 * The second proxy instance is used to test the InvokableMethod passed to the first
                 * invocation handler.
                 */
                instance[1] = CompilationProxy.newProxyInstance(clazz, (proxy, symbolicMethod, _, actualArgs) -> {
                    checkHandlerInvocation(method, proxy, symbolicMethod, actualArgs, instance[1], args);
                    invoked[1] = true;
                    return returnValue;
                });

                // Invoke the first handler, which should invoke the second handler.
                Object actualReturnValue = method.invoke(instance[0], args);

                assertEquals("the proxy implementation should forward the return value", returnValue, actualReturnValue);
                assertTrue("the first invocation handler should have been invoked", invoked[0]);
                assertTrue("the second invocation handler should have been invoked", invoked[1]);
            }
        }
    }

    /**
     * Gets the proxy methods implemented by the given proxy instance.
     */
    private static List<Method> getProxyMethods(CompilationProxy proxyInstance) {
        List<Method> results = new ArrayList<>(Arrays.asList(proxyInstance.getClass().getDeclaredMethods()));
        if (proxyInstance instanceof CompilationProxyBase.CompilationProxyAnnotatedBase) {
            results.addAll(Arrays.asList(CompilationProxyBase.CompilationProxyAnnotatedBase.class.getDeclaredMethods()));
        }
        if (proxyInstance instanceof CompilationProxyBase) {
            results.addAll(Arrays.asList(CompilationProxyBase.class.getDeclaredMethods()));
        }
        if (proxyInstance instanceof HotSpotConstantProxy) {
            results.addAll(Arrays.asList(HotSpotConstantProxy.class.getDeclaredMethods()));
        }
        return results;
    }

    /**
     * Creates an array of values of the given types.
     */
    private static Object[] createValues(Class<?>[] types) {
        if (types.length == 0) {
            return null;
        }
        Object[] values = new Object[types.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = createValue(types[i]);
        }
        return values;
    }

    /**
     * Creates a value of the given type.
     */
    private static Object createValue(Class<?> type) {
        if (type == int.class) {
            return 189349;
        } else if (type == boolean.class) {
            return true;
        } else if (type == byte.class) {
            return (byte) 177;
        } else if (type == char.class) {
            return 'a';
        } else if (type == short.class) {
            return (short) -1235;
        } else if (type == long.class) {
            return 118239233L;
        } else if (type == float.class) {
            return -234.55923f;
        } else if (type == double.class) {
            return 1.28349932d;
        } else if (type == String.class) {
            return "test";
        } else {
            return null;
        }
    }

    /**
     * Checks that the arguments passed to an invocation handler match the expected arguments.
     */
    private static void checkHandlerInvocation(Method method, Object proxy, CompilationProxy.SymbolicMethod symbolicMethod, Object[] actualArgs, CompilationProxy instance, Object[] args) {
        assertSame("the proxy implementation should pass this object as the proxy", instance, proxy);
        assertEquals("the symbolic method should match the invoked method", asSymbolicMethod(method), symbolicMethod);
        assertArrayEquals("the arguments passed to the handler should match the invocation arguments", args, actualArgs);
    }

    /**
     * Returns a symbolic method for the given method.
     */
    private static CompilationProxy.SymbolicMethod asSymbolicMethod(Method method) {
        return new CompilationProxy.SymbolicMethod(method.getName(), method.getParameterTypes());
    }
}
