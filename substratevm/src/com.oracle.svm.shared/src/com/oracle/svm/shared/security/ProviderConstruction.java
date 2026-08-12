/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.shared.security;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.Provider;

import com.oracle.svm.shared.util.BasedOnJDKFile;

// §FS-002-security-providers.2.2
/**
 * The rule that decides how a JCA security provider can be constructed, stated in the JDK's terms.
 *
 * A provider is constructed through the path the JDK would use. The JDK
 * reaches a configured provider through {@code sun.security.jca.ProviderConfig}, which resolves the
 * entry through {@link java.util.ServiceLoader} and otherwise falls back to a legacy class-name load
 * that calls {@code Class.newInstance()}. The constructor path requires a public, concrete
 * {@link Provider} subtype and a public nullary constructor. The provider-method path requires a
 * public service provider class in an explicit module, but that class may be an interface, abstract,
 * or unrelated to {@link Provider}.
 *
 * The build-time constructibility predicate, the build-time instantiation that reads a provider's
 * service catalog, and the run-time provider-list loader all decide with these predicates, so a
 * construction path the build accepts is the path the run time takes. Each caller performs its own
 * member lookup, because the hosted and run-time sides reach members through different mechanisms.
 */
public final class ProviderConstruction {

    /** The name of the {@link java.util.ServiceLoader} provider factory method. */
    public static final String PROVIDER_METHOD_NAME = "provider";

    private ProviderConstruction() {
    }

    /**
     * Returns whether {@code providerClass} is in an explicit module, which is the only case in
     * which {@link java.util.ServiceLoader} consults a {@code provider()} method.
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+21/src/java.base/share/classes/java/util/ServiceLoader.java#L571-L574")
    public static boolean isInExplicitModule(Class<?> providerClass) {
        Module module = providerClass.getModule();
        return module.isNamed() && !module.getDescriptor().isAutomatic();
    }

    /**
     * Returns whether {@code providerClass} can be the class of a provider instance. A provider
     * method can return an instance of a non-public implementation class.
     */
    public static boolean isProviderImplementationClass(Class<?> providerClass) {
        if (providerClass == null || providerClass.isArray() || providerClass.isPrimitive() || providerClass.isInterface()) {
            return false;
        }
        int modifiers = providerClass.getModifiers();
        return !Modifier.isAbstract(modifiers) && Provider.class.isAssignableFrom(providerClass);
    }

    /**
     * Returns whether {@code providerClass} can use the provider-constructor path.
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+21/src/java.base/share/classes/java/util/ServiceLoader.java#L763-L785")
    public static boolean isProviderConstructorClass(Class<?> providerClass) {
        return isProviderImplementationClass(providerClass) && Modifier.isPublic(providerClass.getModifiers());
    }

    /**
     * Returns whether {@code constructor} is the nullary constructor that both JDK paths use.
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+21/src/java.base/share/classes/java/util/ServiceLoader.java#L620-L631")
    public static boolean isQualifyingConstructor(Constructor<?> constructor) {
        return constructor != null && Modifier.isPublic(constructor.getModifiers()) && constructor.getParameterCount() == 0 &&
                        isProviderConstructorClass(constructor.getDeclaringClass());
    }

    /**
     * Returns whether {@code method} is a {@code provider()} factory method that
     * {@link java.util.ServiceLoader} would call: public, static, nullary, returning a
     * {@link Provider} subtype, and declared by a public class in an explicit module.
     *
     * The declaring class itself need not be a {@link Provider} subtype, because a factory method
     * supplies the instance in place of the constructor.
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+21/src/java.base/share/classes/java/util/ServiceLoader.java#L583-L612")
    public static boolean isQualifyingProviderMethod(Method method) {
        if (method == null || !PROVIDER_METHOD_NAME.equals(method.getName())) {
            return false;
        }
        int modifiers = method.getModifiers();
        Class<?> declaringClass = method.getDeclaringClass();
        return Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && method.getParameterCount() == 0 &&
                        Provider.class.isAssignableFrom(method.getReturnType()) &&
                        Modifier.isPublic(declaringClass.getModifiers()) && isInExplicitModule(declaringClass);
    }
}
