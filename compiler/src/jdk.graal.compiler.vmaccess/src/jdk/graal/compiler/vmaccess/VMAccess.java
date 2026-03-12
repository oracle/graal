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
     * @return true if the VM access implementation enforces full heap isolation (e.g. espresso),
     *         false if not (e.g. host). This method should be used only where it's absolutely
     *         needed, e.g., for guarding code that can be executed in both builder and guest, such
     *         as when registering the image singleton registry, to avoid a double registration when
     *         there is no full heap isolation.
     */
    boolean isFullyIsolated();

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
     * <p>
     * If the method throws a {@code CallbackException} originating from a
     * {@linkplain #createCallback callback}, the {@code CallbackException} wrapper will be removed
     * and a {@link InvocationException} whose {@linkplain Throwable#getCause cause} is the original
     * host exception will be thrown.
     * <p>
     * Note that if the implementation is backed by an {@link Executable} object, this call will
     * ensure it is {@linkplain Executable#setAccessible(boolean) accessible} before attempting the
     * invocation.
     *
     * @param method the method to invoke.
     * @param receiver for non-static, non-constructor methods, the receiver of the invocation
     *            passed a {@link JavaConstant}. This must be {@code null} for static or constructor
     *            methods.
     * @param args Arguments of types matching the {@linkplain ResolvedJavaMethod#getSignature()
     *            signature} passed as {@link JavaConstant} objects. The arguments are subject to
     *            conversions as described in the Java Language Specifications' strict invocation
     *            context (JLS 5.3).
     * @return the result as a {@link JavaConstant} or null if the method has a void return type.
     * @throws InvocationException if the invoked method throws an exception, it is wrapped in an
     *             {@link InvocationException}.
     */
    JavaConstant invoke(ResolvedJavaMethod method, JavaConstant receiver, JavaConstant... args);

    /**
     * Writes a value to a {@link ResolvedJavaField}.
     *
     * Note that if the implementation is backed by a {@link Field} object, this call will ensure it
     * is {@linkplain Field#setAccessible(boolean) accessible} before attempting writing the field.
     *
     * @param field the field to write.
     * @param receiver the receiver object for an instance field, passed as a {@link JavaConstant}.
     *            This must be {@code null} for a {@linkplain ResolvedJavaField#isStatic() static}
     *            field.
     * @param value the value to be written, passed as a {@link JavaConstant}. Primitive values must
     *            exactly match the type of the {@code field}. For example, an {@code int} value
     *            cannot be written to a field of type {@code long}). Implementations must not
     *            perform widening primitive conversion (JLS 5.1.2). This is in contrast to
     *            {@link #invoke}.
     * @throws IllegalArgumentException if {@code receiver} is non-null for a static field, if
     *             {@code receiver} is null for a non-static field, or if {@code value} cannot be
     *             assigned to the field type.
     */
    void writeField(ResolvedJavaField field, JavaConstant receiver, JavaConstant value);

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
     * Writes {@code element} into {@code array} at {@code index}.
     *
     * @param array the array in which the element will be written
     * @param index the position in the array where the element will be written
     * @param element the element to write into the array. Primitive values must exactly match the
     *            type of the {@code array}. For example, an {@code int} value cannot be written to
     *            a field of type {@code long}). Implementations must not perform widening primitive
     *            conversion (JLS 5.1.2). This is in contrast to {@link #invoke}.
     * @throws IllegalArgumentException if {@code array} does not represent an array or if the
     *             element cannot be assigned to the type of the array
     */
    void writeArrayElement(JavaConstant array, int index, JavaConstant element);

    /**
     * Gets a {@link ResolvedJavaMethod} for an {@link Executable} object encapsulated in
     * {@code constant}. Returns {@code null} if {@code constant} does not encapsulate an
     * {@link Executable}.
     */
    ResolvedJavaMethod asResolvedJavaMethod(Constant constant);

    /**
     * Gets a {@link ResolvedJavaField} for a {@link Field} object encapsulated in {@code constant}.
     * Returns {@code null} if {@code constant} does not encapsulate a {@link Field}.
     */
    ResolvedJavaField asResolvedJavaField(Constant constant);

    /**
     * Gets the runtime representation of an {@link Executable} object for {@code method}. This is
     * the inverse of {@link #asResolvedJavaMethod(Constant)}. Not all VM methods (such as
     * {@linkplain ResolvedJavaMethod#isClassInitializer()} <clint>) have a reflection object, in
     * which case {@code null} is returned.
     * <p>
     * Multiple calls to this method for the same {@code ResolvedJavaMethod} instance can return the
     * same {@link Executable} object. This is worth keeping in mind since {@link Executable}
     * objects are {@linkplain Executable#setAccessible(boolean) mutable}.
     */
    JavaConstant asExecutableConstant(ResolvedJavaMethod method);

    /**
     * Gets the runtime representation of a {@link Field} object for {@code field}. This is the
     * inverse of {@link #asResolvedJavaField}. Not all VM fields (such as
     * {@linkplain ResolvedJavaField#isInternal() injected} fields) have a reflection object, in
     * which case {@code null} is returned.
     * <p>
     * Multiple calls to this method for the same {@code ResolvedJavaField} instance can return the
     * same {@link Field} object. This is worth keeping in mind since {@link Field} objects are
     * {@linkplain Field#setAccessible(boolean) mutable}.
     */
    JavaConstant asFieldConstant(ResolvedJavaField field);

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
     * <p>
     * If {@code type.isArray()}, this method returns the {@link ResolvedJavaModule} for
     * {@code type.getElementalType()}. for the element type. If this class represents a primitive
     * type or void, then the {@link ResolvedJavaModule} object for the {@code java.base} module is
     * returned.
     * <p>
     * If this class is in an unnamed module then the {@linkplain ClassLoader#getUnnamedModule()
     * unnamed module} of the class loader for {@code type} is returned.
     * <p>
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
     * Copies memory from {@code src} in the guest to a destination {@code dst} in the host. The
     * semantics (e.g., regarding atomicity guarantees) are the same as for
     * {@code Unsafe#copyMemory}.
     *
     * @param src the source {@link JavaConstant} representing a primitive array to copy from
     * @param srcFrom the starting offset in the source array (inclusive)
     * @param srcTo the ending offset in the source array (exclusive)
     * @param dst the destination {@code byte[]} in the host to copy the memory to
     * @param dstFrom the starting offset in the destination array (inclusive)
     * @throws IllegalArgumentException if {@code src} does not represent a primitive array or the
     *             offsets are invalid
     */
    void copyMemory(JavaConstant src, int srcFrom, int srcTo, byte[] dst, int dstFrom);

    /**
     * Returns a value that implements the {@code guestType} interface by calling back to
     * {@code hostTarget} through its methods.
     * <p>
     * The {@code hostTarget} and {@code guestType} interfaces must "match" in the following way:
     * for each method in {@code guestType} (and its super-interfaces), there must exist a
     * "compatible" method in {@code hostTarget}'s class (or its super-class or super-interfaces).
     * <p>
     * A host method is "compatible" with a guest method if they have the same name, same number of
     * arguments, argument types are "compatible", and return types are "compatible". Type
     * "compatibility" is defined by the following table:
     * <table>
     * <tr>
     * <th>Host type</th>
     * <th>Guest type</th>
     * <th>Notes</th>
     * </tr>
     * <tr>
     * <td>primitive type T</td>
     * <td>primitive type T</td>
     * <td>The {@linkplain Class#isPrimitive() primitive types} must match exactly.</td>
     * </tr>
     * <tr>
     * <td>{@link String}</td>
     * <td>{@link String}</td>
     * <td>The identity of the string might not be preserved through a round-trip.</td>
     * </tr>
     * <tr>
     * <td>{@link JavaConstant}</td>
     * <td>any type</td>
     * <td></td>
     * </tr>
     * <tr>
     * <td>{@code void}</td>
     * <td>any type</td>
     * <td>This is only relevant for return types.</td>
     * </tr>
     * <tr>
     * <td>{@link ResolvedJavaType}</td>
     * <td>{@link Class}</td>
     * <td>The host type must be {@link ResolvedJavaType} exactly (e.g.,
     * {@link jdk.vm.ci.meta.JavaType} will not work).</td>
     * </tr>
     * <tr>
     * <td>{@link ResolvedJavaField}</td>
     * <td>{@link Field}</td>
     * <td>The host type must be {@link ResolvedJavaField} exactly (e.g.,
     * {@link jdk.vm.ci.meta.JavaField} will not work).</td>
     * </tr>
     * <tr>
     * <td>{@link ResolvedJavaMethod}</td>
     * <td>{@link Executable}, {@link java.lang.reflect.Method}, or
     * {@link java.lang.reflect.Constructor}</td>
     * <td>The host type must be {@link ResolvedJavaMethod} exactly (e.g.,
     * {@link jdk.vm.ci.meta.JavaMethod} will not work).</td>
     * </tr>
     * </table>
     * <p>
     * If a host method throws an {@link InvocationException} with an attached
     * {@linkplain InvocationException#getExceptionObject guest exception object}, the
     * {@link InvocationException} wrapper will be discarded and the original guest exception will
     * be thrown in the guest. Otherwise, if a host method throws an {@link InvocationException}
     * with no guest exception object, the {@linkplain Throwable#getCause() cause} of the
     * {@link InvocationException} will be wrapped in a
     * {@code jdk.graal.compiler.vmaccess.guest.CallbackException} and thrown in the guest. Finally,
     * if a host method throws any other type of exception, it will be wrapped in
     * {@code jdk.graal.compiler.vmaccess.guest.CallbackException} and thrown in the guest.
     * <p>
     * Note: generic type information is not considered, so for example if {@code hostTarget} has a
     * {@code void accept(T t)} method with {@code T} an unbounded class type parameter, the host
     * signature that will be checked is {@code void accept(Object t)} which can't be compatible
     * with any guest types according to the table above.
     *
     * @param hostTarget the object that will be used as receiver when calling methods.
     * @param guestType the interface that should be implemented by the returned value.
     */
    JavaConstant createCallback(Object hostTarget, ResolvedJavaType guestType);

    /**
     * Gets the host exception wrapped the
     * {@code jdk.graal.compiler.vmaccess.guest.CallbackException} encapsulated by {@code constant}.
     * Returns {@code null} if the constant doesn't encapsulate a
     * {@code jdk.graal.compiler.vmaccess.guest.CallbackException}.
     */
    Throwable unwrapCallbackException(JavaConstant constant);

    /**
     * A builder can be used to set a JVM context up and observe it through a {@link VMAccess}.
     * <p>
     * The {@link java.util.ServiceLoader} API can be used to locate such a builder. Implementations
     * can be distinguished by their {@linkplain #getVMAccessName() name}.
     */
    interface Builder {
        String getVMAccessName();

        /**
         * The class path to use. This has the semantics of the {@code --class-path} java launcher
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
