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
package jdk.graal.compiler.options;

import jdk.graal.compiler.core.common.LibGraalSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Extra metadata about a class containing one or more static fields annotated by {@link Option}.
 * Such a class can implement this interface to {@linkplain #getNamePrefix() customize the name} of
 * the contained options or to {@link #optionsAreServiceLoadable prevent} them from being loaded by
 * {@link #load(ClassLoader)}.
 */
public interface OptionsContainer {

    /**
     * Determines if this container's options are available via {@link #load(ClassLoader)}.
     */
    default boolean optionsAreServiceLoadable() {
        return true;
    }

    /**
     * Gets the prefix to be added to {@link Option#name()}} by {@link OptionDescriptor#getName()}.
     *
     * @return null if no prefix is to be added
     */
    default String getNamePrefix() {
        return null;
    }

    /**
     * Gets {@code name} prefixed by {@link #getNamePrefix()} if the latter is non-null otherwise
     * {@code name}.
     */
    default String prefixed(String name) {
        String prefix = getNamePrefix();
        return prefix != null ? prefix + name : name;
    }

    /**
     * Gets {@code name} without {@link #getNamePrefix()} if the latter is non-null otherwise
     * {@code name}. At most one copy of the prefix is stripped.
     */
    default String unprefixed(String name) {
        String prefix = getNamePrefix();
        return prefix != null && name.startsWith(prefix) ? name.substring(prefix.length()) : name;
    }

    /**
     * Gets the class declaring the options.
     */
    default Class<?> getDeclaringClass() {
        return getClass();
    }

    /**
     * Finds all {@linkplain #optionsAreServiceLoadable() loadable} options available via service
     * loading.
     *
     * @param loader the class loader used for {@link ServiceLoader#load(Class, ClassLoader)}
     */
    @LibGraalSupport.HostedOnly
    static Iterable<OptionDescriptors> load(ClassLoader loader) {
        List<OptionDescriptors> res = new ArrayList<>();
        for (OptionDescriptors d : ServiceLoader.load(OptionDescriptors.class, loader)) {
            if (d.getContainer().optionsAreServiceLoadable()) {
                res.add(d);
            }
        }
        return res;
    }

    static OptionsContainer asContainer(Object containerOrDeclaringClass) {
        if (containerOrDeclaringClass instanceof OptionsContainer oc) {
            return oc;
        }
        if (containerOrDeclaringClass instanceof Class<?> c) {
            return new Default(c);
        }
        throw new IllegalArgumentException("Not a class or container: " + containerOrDeclaringClass.getClass());
    }

    /**
     * The default metadata for an options declaring class.
     */
    class Default implements OptionsContainer {
        private final Class<?> declaringClass;

        public Default(Class<?> declaringClass) {
            this.declaringClass = declaringClass;
        }

        @Override
        public Class<?> getDeclaringClass() {
            return declaringClass;
        }
    }
}
