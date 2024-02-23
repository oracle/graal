/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.reflect.proxy;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A substitution processor that renames Proxy classes using a stable name. The name is created
 * using the name of the interfaces composing the Proxy type combined with the name of the class
 * loader of the type.
 * <p>
 * Needs to be created before
 * {@link org.graalvm.nativeimage.hosted.Feature#duringSetup(Feature.DuringSetupAccess)} as Proxy
 * types can be created in the serialization feature.
 */
public class ProxyRenamingSubstitutionProcessor extends SubstitutionProcessor {
    public static final String DYNAMIC_MODULE_REGEX = "jdk[.]proxy[0-9]+";
    public static final String NULL_CLASS_LOADER_NAME = "native-image-null-class-loader";
    public static final String NULL_MODULE_NAME = "native-image-null-module";
    public static final String UNNAMED_MODULE_NAME = "native-image-unnamed";
    private static final String STABLE_NAME_TEMPLATE = "/$Proxy.s";

    private final ConcurrentMap<ResolvedJavaType, ProxySubstitutionType> typeSubstitutions = new ConcurrentHashMap<>();
    private final Set<String> uniqueTypeNames = new HashSet<>();

    public static boolean isProxyType(ResolvedJavaType type) {
        Class<?> clazz = OriginalClassProvider.getJavaClass(type);
        return Proxy.isProxyClass(clazz);
    }

    /**
     * The code creating the name of dynamic modules can be found in
     * Proxy$ProxyBuilder.getDynamicModule.
     */
    private static boolean isModuleDynamic(Module module) {
        return module != null && module.getName() != null && module.getName().matches(DYNAMIC_MODULE_REGEX);
    }

    @Override
    public ResolvedJavaType lookup(ResolvedJavaType type) {
        if (!shouldReplace(type)) {
            return type;
        }
        return getSubstitution(type);
    }

    private static boolean shouldReplace(ResolvedJavaType type) {
        return !(type instanceof ProxySubstitutionType) && isProxyType(type);
    }

    private ProxySubstitutionType getSubstitution(ResolvedJavaType original) {
        Class<?> clazz = OriginalClassProvider.getJavaClass(original);
        return typeSubstitutions.computeIfAbsent(original, key -> new ProxySubstitutionType(key, getUniqueProxyName(clazz)));
    }

    public String getUniqueProxyName(Class<?> clazz) {
        StringBuilder sb = new StringBuilder();

        /*
         * According to the Proxy documentation: "A proxy class implements exactly the interfaces
         * specified at its creation, in the same order. Invoking {@link Class#getInterfaces()
         * getInterfaces} on its {@code Class} object will return an array containing the same list
         * of interfaces (in the order specified at its creation)."
         *
         * This means that this order matches the order of the classes in our Proxy configuration
         * JSON. The order is important as changing it creates a different Proxy type.
         */
        Class<?>[] interfaces = clazz.getInterfaces();
        Arrays.stream(interfaces).forEach(i -> sb.append(i.getName()));

        /*
         * Two proxy classes with the same interfaces and two different class loaders with the same
         * name or without a name but from the same class will produce the same stable name.
         *
         * The module of proxy classes without a package private interface contains a unique id that
         * depends on the class loader. This id is assigned to the class loaders based on the order
         * in which they are used to create a proxy class. In some rare cases, this order can be
         * different in two build processes, meaning the module will contain a different id and the
         * name will not be stable.
         */
        ClassLoader classLoader = clazz.getClassLoader();
        String classLoaderName = classLoader == null ? NULL_CLASS_LOADER_NAME : classLoader.getName();
        sb.append(classLoaderName != null ? classLoaderName : classLoader.getClass().getName());

        Module module = clazz.getModule();
        if (!isModuleDynamic(module)) {
            String moduleName = module == null ? NULL_MODULE_NAME : module.getName();
            sb.append(moduleName != null ? moduleName : UNNAMED_MODULE_NAME);
        }

        return findUniqueName(clazz, sb.toString().hashCode());
    }

    private String findUniqueName(Class<?> clazz, int hashCode) {
        CharSequence baseName = "L" + clazz.getPackageName().replace('.', '/') + STABLE_NAME_TEMPLATE + Integer.toHexString(hashCode);
        String name = baseName + ";";
        synchronized (uniqueTypeNames) {
            int suffix = 1;
            while (uniqueTypeNames.contains(name)) {
                name = baseName + "_" + suffix + ";";
                suffix++;
            }
            uniqueTypeNames.add(name);
            return name;
        }
    }
}
