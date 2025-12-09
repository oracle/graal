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
package com.oracle.graal.vmaccess;

import java.util.List;

import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This provides access to a JVM that can be reflected upon and manipulated using the JVMCI and
 * Graal compiler APIs.
 */
public interface VMAccess {
    /**
     * Returns the Graal compiler {@link Providers} which can be used to reflect upon and manipulate
     * the observed JVM.
     */
    Providers getProviders();

    /**
     * Invokes the provided method.
     * <ul>
     * <li>For instance methods (not {@linkplain ResolvedJavaMethod#isStatic() static} and not
     * {@linkplain ResolvedJavaMethod#isConstructor() constructor}), a receiver of a type compatible
     * with this method's {@linkplain ResolvedJavaMethod#getDeclaringClass() declaring class} must
     * be passed as the first argument.</li>
     * <li>For {@linkplain ResolvedJavaMethod#isStatic() static} methods, only the plain arguments
     * need to be passed, no null or class argument needs to be prepended.</li>
     * <li>For {@linkplain ResolvedJavaMethod#isConstructor() constructors}, only the plain,
     * language-level arguments need to be passed. An instance of the
     * {@linkplain ResolvedJavaMethod#getDeclaringClass() declaring class} will be created and
     * doesn't need to be prepended.</li>
     * </ul>
     *
     * @return the result as a {@link JavaConstant} or null if the method has a void return type.
     * @param method the method to invoke.
     * @param receiver for non-static, non-constructor methods, the receiver of the invocation
     *            passed a {@link JavaConstant}. This must be {@code null} for static or constructor
     *            methods.
     * @param args Arguments of types matching the {@linkplain ResolvedJavaMethod#getSignature()
     *            signature} passed as {@link JavaConstant} objects. The arguments are subject to
     *            conversions as described in the Java Language Specifications' strict invocation
     *            context (5.3).
     * @throws InvocationException if the invoked method throws an exception, it is wrapped in an
     *             {@link InvocationException}.
     */
    JavaConstant invoke(ResolvedJavaMethod method, JavaConstant receiver, JavaConstant... args);

    /**
     * Creates an array from an array of values.
     *
     * @param componentType the component type for the array to be created
     * @param elements the values with which to initialize the array
     * @return the array as a {@link JavaConstant}
     * @throws IllegalArgumentException if any value in {@code elements} is not assignable to
     *             {@code componentType}
     */
    JavaConstant asArrayConstant(ResolvedJavaType componentType, JavaConstant... elements);

    /**
     * Lookup a type by name in the {@linkplain ClassLoader#getSystemClassLoader() system/app} class
     * loader. This performs the usual class loader delegation and behaves as if the following was
     * called: {@code Class.forName(name, false, ClassLoader.getSystemClassLoader())}.
     * <p>
     * Note: this could in theory all be done by using JVMCI and {@link #invoke} to call
     * {@code Class.forName}. The reason why this method is part of this interface is to allow for
     * the degenerate case of a "host" VM access where this method doesn't actually load from the
     * system class loader but from a specially prepared class loader.
     */
    ResolvedJavaType lookupAppClassLoaderType(String name);

    /**
     * Lookup a type by name in the {@linkplain ClassLoader#getPlatformClassLoader() platform} class
     * loader. This performs the usual class loader delegation and behaves as if the following was
     * called: {@code Class.forName(name, false, ClassLoader.getPlatformClassLoader())}.
     */
    ResolvedJavaType lookupPlatformClassLoaderType(String name);

    /**
     * Lookup a type by name in the boot ({@code null}) class loader. This performs the usual class
     * loader delegation and behaves as if the following was called:
     * {@code Class.forName(name, false, null)}.
     */
    ResolvedJavaType lookupBootClassLoaderType(String name);

    /**
     * A builder can be used to set a JVM context up and observe it through a {@link VMAccess}.
     * <p>
     * The {@link java.util.ServiceLoader} API can be used to locate such a builder. Implementations
     * can be distinguished by their {@linkplain #getVMAccessName() name}.
     */
    interface Builder {
        String getVMAccessName();

        /**
         * The module path to use. This has the semantics of the {@code --class-path} java launcher
         * option.
         */
        Builder classPath(List<String> paths);

        /**
         * The module path to use. This has the semantics of the {@code --module-path} java launcher
         * option.
         */
        Builder modulePath(List<String> paths);

        /**
         * Sets the list of modules to resolve in addition to the initial module. This has the
         * semantics of the {@code --add-modules} java launcher option.
         */
        Builder addModules(List<String> modules);

        /**
         * Sets the assertion status for application classes.
         */
        Builder enableAssertions(boolean assertionStatus);

        /**
         * Sets the assertion status for system classes.
         */
        Builder enableSystemAssertions(boolean assertionStatus);

        /**
         * Sets a system property value.
         */
        Builder systemProperty(String name, String value);

        /**
         * Adds a VM option. This has the same semantics as a {@code JavaVMOption} in the JNI
         * Invocation API.
         *
         * @see "https://docs.oracle.com/en/java/javase/25/docs/specs/jni/invocation.html#jni_createjavavm"
         */
        Builder vmOption(String option);

        VMAccess build();
    }
}
