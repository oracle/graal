/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vmaccess;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.List;
import java.util.stream.Stream;

import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
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
     * Determines if the concrete type of {@code value} is owned by this access. That is, it is the
     * canonical reference to a Java class in the JVM accessed by this object.
     */
    boolean owns(ResolvedJavaType value);

    /**
     * Determines if the concrete type of {@code value} is owned by this access.
     *
     * @see #owns(ResolvedJavaType)
     */
    boolean owns(ResolvedJavaMethod value);

    /**
     * Determines if the concrete type of {@code value} is owned by this access.
     *
     * @see #owns(ResolvedJavaType)
     */
    boolean owns(ResolvedJavaField value);

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
     * @param method the method to invoke.
     * @param receiver for non-static, non-constructor methods, the receiver of the invocation
     *            passed a {@link JavaConstant}. This must be {@code null} for static or constructor
     *            methods.
     * @param args Arguments of types matching the {@linkplain ResolvedJavaMethod#getSignature()
     *            signature} passed as {@link JavaConstant} objects. The arguments are subject to
     *            conversions as described in the Java Language Specifications' strict invocation
     *            context (5.3).
     * @return the result as a {@link JavaConstant} or null if the method has a void return type.
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
     * Returns the {@link ResolvedJavaMethod} for an {@link Executable} object encapsulated in
     * {@code constant}. Returns {@code null} if the constant does not encapsulate an
     * {@link Executable}.
     */
    ResolvedJavaMethod asResolvedJavaMethod(Constant constant);

    /**
     * Returns the {@link ResolvedJavaField} for a {@link Field} object encapsulated in
     * {@code constant}. Returns {@code null} if the constant does not encapsulate a {@link Field}.
     */
    ResolvedJavaField asResolvedJavaField(Constant constant);

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
     * Gets the {@link ResolvedJavaModule} of the given {@link ResolvedJavaType}.
     *
     * If {@code type.isArray()}, this method returns the {@link ResolvedJavaModule} for
     * {@code type.getElementalType()}. for the element type. If this class represents a primitive
     * type or void, then the {@link ResolvedJavaModule} object for the {@code java.base} module is
     * returned.
     *
     * If this class is in an unnamed module then the {@linkplain ClassLoader#getUnnamedModule()
     * unnamed module} of the class loader for {@code type} is returned.
     *
     * This method never returns {@code null}.
     */
    ResolvedJavaModule getModule(ResolvedJavaType type);

    /**
     * Gets the package enclosing {@code type} or null if {@code type} represents an array type, a
     * primitive type or void.
     */
    ResolvedJavaPackage getPackage(ResolvedJavaType type);

    /**
     * Returns a stream of the packages defined to the boot loader. See
     * {@code jdk.internal.loader.BootLoader#packages()}.
     */
    Stream<ResolvedJavaPackage> bootLoaderPackages();

    /**
     * Returns the boot layer. See {@link java.lang.ModuleLayer#boot()}.
     */
    ResolvedJavaModuleLayer bootModuleLayer();

    /**
     * Returns the location of the code source associated with this {@link ResolvedJavaType}.
     *
     * @return the location (URL), or {@code null} if no URL was supplied during construction.
     */
    URL getCodeSourceLocation(ResolvedJavaType type);

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
         * <p>
         * This appends to the module(s) previously added by this method or {@link #addModule}. No
         * checking for duplicates is performed.
         */
        Builder addModules(List<String> modules);

        /**
         * Sets the list of modules to resolve in addition to the initial module. This has the
         * semantics of the {@code --add-modules} java launcher option.
         * <p>
         * This appends to the module(s) previously added by this method or {@link #addModules}. No
         * checking for duplicates is performed.
         */
        Builder addModule(String module);

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
