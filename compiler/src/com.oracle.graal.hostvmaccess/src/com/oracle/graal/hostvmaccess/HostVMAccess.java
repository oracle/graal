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
package com.oracle.graal.hostvmaccess;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import com.oracle.graal.vmaccess.InvocationException;
import com.oracle.graal.vmaccess.VMAccess;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.api.runtime.GraalJVMCICompiler;
import jdk.graal.compiler.api.runtime.GraalRuntime;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.runtime.RuntimeProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.runtime.JVMCI;

/**
 * An implementation of {@link VMAccess} that reflects on the JVM it's currently running inside.
 * There is no isolation between the current JVM and the JVM being accessed through this
 * implementation, it is the same JVM.
 * <p>
 * Note that each instance of this VM access creates a dedicated class loader and module layer that
 * it uses to implement {@link VMAccess#lookupAppClassLoaderType} instead of using the host JVM's
 * {@linkplain ClassLoader#getSystemClassLoader system/app classloader}.
 */
final class HostVMAccess implements VMAccess {
    private final ClassLoader appClassLoader;
    private final Providers providers;

    HostVMAccess(ClassLoader appClassLoader) {
        this.appClassLoader = appClassLoader;
        GraalRuntime graalRuntime = ((GraalJVMCICompiler) JVMCI.getRuntime().getCompiler()).getGraalRuntime();
        Backend hostBackend = graalRuntime.getCapability(RuntimeProvider.class).getHostBackend();
        providers = hostBackend.getProviders();
    }

    @Override
    public Providers getProviders() {
        return providers;
    }

    @Override
    public JavaConstant invoke(ResolvedJavaMethod method, JavaConstant receiver, JavaConstant... arguments) {
        SnippetReflectionProvider snippetReflection = providers.getSnippetReflection();
        Executable executable = snippetReflection.originalMethod(method);
        executable.setAccessible(true);
        boolean isConstructor = executable instanceof Constructor;
        Class<?>[] parameterTypes = executable.getParameterTypes();
        if (Modifier.isStatic(executable.getModifiers()) || isConstructor) {
            if (receiver != null) {
                throw new IllegalArgumentException("For static methods or constructor, the receiver argument must be null");
            }
        } else if (receiver == null) {
            throw new NullPointerException("For instance methods, the receiver argument must not be null");
        } else if (receiver.isNull()) {
            throw new IllegalArgumentException("For instance methods, the receiver argument must not represent a null constant");
        }
        if (parameterTypes.length != arguments.length) {
            throw new IllegalArgumentException("Wrong number of arguments: expected " + parameterTypes.length + " but got " + arguments.length);
        }
        Signature signature = method.getSignature();
        Object[] unboxedArguments = new Object[parameterTypes.length];
        for (int i = 0; i < unboxedArguments.length; i++) {
            JavaKind parameterKind = signature.getParameterKind(i);
            JavaConstant argument = arguments[i];
            if (parameterKind.isObject()) {
                unboxedArguments[i] = snippetReflection.asObject(parameterTypes[i], argument);
            } else {
                assert parameterKind.isPrimitive();
                unboxedArguments[i] = argument.asBoxedPrimitive();
            }
        }
        try {
            if (isConstructor) {
                Constructor<?> constructor = (Constructor<?>) executable;
                return snippetReflection.forObject(constructor.newInstance(unboxedArguments));
            } else {
                Method reflectionMethod = (Method) executable;
                Object unboxedReceiver;
                if (Modifier.isStatic(reflectionMethod.getModifiers())) {
                    unboxedReceiver = null;
                } else {
                    unboxedReceiver = snippetReflection.asObject(reflectionMethod.getDeclaringClass(), receiver);
                }
                JavaKind returnKind = method.getSignature().getReturnKind();
                Object result = reflectionMethod.invoke(unboxedReceiver, unboxedArguments);
                if (returnKind == JavaKind.Void) {
                    return null;
                }
                if (returnKind.isObject()) {
                    return snippetReflection.forObject(result);
                } else {
                    return snippetReflection.forBoxed(returnKind, result);
                }
            }
        } catch (InstantiationException e) {
            throw new IllegalArgumentException(e);
        } catch (InvocationTargetException e) {
            throw new InvocationException(snippetReflection.forObject(e.getCause()), e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public JavaConstant asArrayConstant(ResolvedJavaType componentType, JavaConstant... elements) {
        SnippetReflectionProvider snippetReflection = providers.getSnippetReflection();
        Class<?> componentClass = snippetReflection.originalClass(componentType);
        Object array = Array.newInstance(componentClass, elements.length);
        for (int i = 0; i < elements.length; i++) {
            JavaConstant argument = elements[i];
            if (argument.getJavaKind().isObject()) {
                Array.set(array, i, snippetReflection.asObject(Object.class, argument));
            } else {
                Array.set(array, i, argument.asBoxedPrimitive());
            }
        }
        return snippetReflection.forObject(array);
    }

    @Override
    public ResolvedJavaType lookupBootClassLoaderType(String name) {
        return lookupType(name, null);
    }

    @Override
    public ResolvedJavaType lookupPlatformClassLoaderType(String name) {
        return lookupType(name, ClassLoader.getPlatformClassLoader());
    }

    @Override
    public ResolvedJavaType lookupAppClassLoaderType(String name) {
        return lookupType(name, appClassLoader);
    }

    private ResolvedJavaType lookupType(String name, ClassLoader loader) {
        try {
            Class<?> cls = Class.forName(name, false, loader);
            return providers.getMetaAccess().lookupJavaType(cls);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
