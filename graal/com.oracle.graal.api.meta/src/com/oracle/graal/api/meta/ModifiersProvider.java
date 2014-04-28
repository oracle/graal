/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.meta;

import static java.lang.reflect.Modifier.*;

import java.lang.reflect.*;

/**
 * A Java element (i.e., a class, interface, field or method) that is described by a set of Java
 * language {@linkplain #getModifiers() modifiers}.
 */
public interface ModifiersProvider {

    /**
     * Returns the Java language modifiers for this element.
     */
    int getModifiers();

    /**
     * see {@link Modifier#isInterface(int)}
     */
    default boolean isInterface() {
        return Modifier.isInterface(getModifiers());
    }

    /**
     * see {@link Modifier#isSynchronized(int)}
     */
    default boolean isSynchronized() {
        return Modifier.isSynchronized(getModifiers());
    }

    /**
     * see {@link Modifier#isStatic(int)}
     */
    default boolean isStatic() {
        return Modifier.isStatic(getModifiers());
    }

    /**
     * see {@link Modifier#isFinal(int)}
     */
    default boolean isFinal() {
        return Modifier.isFinal(getModifiers());
    }

    /**
     * see {@link Modifier#isPublic(int)}
     */
    default boolean isPublic() {
        return Modifier.isPublic(getModifiers());
    }

    /**
     * Determines if this element is neither {@linkplain #isPublic() public},
     * {@linkplain #isProtected() protected} nor {@linkplain #isPrivate() private}.
     */
    default boolean isPackagePrivate() {
        return ((PUBLIC | PROTECTED | PRIVATE) & getModifiers()) == 0;
    }

    /**
     * see {@link Modifier#isPrivate(int)}
     */
    default boolean isPrivate() {
        return Modifier.isPrivate(getModifiers());
    }

    /**
     * see {@link Modifier#isProtected(int)}
     */
    default boolean isProtected() {
        return Modifier.isProtected(getModifiers());
    }

    /**
     * see {@link Modifier#isTransient(int)}
     */
    default boolean isTransient() {
        return Modifier.isTransient(getModifiers());
    }

    /**
     * see {@link Modifier#isStrict(int)}
     */
    default boolean isStrict() {
        return Modifier.isStrict(getModifiers());
    }

    /**
     * see {@link Modifier#isVolatile(int)}
     */
    default boolean isVolatile() {
        return Modifier.isVolatile(getModifiers());
    }

    /**
     * see {@link Modifier#isNative(int)}
     */
    default boolean isNative() {
        return Modifier.isNative(getModifiers());
    }

    /**
     * see {@link Modifier#isAbstract(int)}
     */
    default boolean isAbstract() {
        return Modifier.isAbstract(getModifiers());
    }
}
