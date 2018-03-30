/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage;

import java.lang.reflect.Constructor;

//Checkstyle: allow reflection

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

/**
 * This class provides methods that can be called during native image generation to register
 * classes, methods, and fields for reflection at run time.
 *
 * @since 1.0
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class RuntimeReflection {

    /**
     * Makes the provided classes available for reflection at run time. A call to
     * {@link Class#forName} for the names of the classes will return the classes at run time.
     *
     * @since 1.0
     */
    public static void register(Class<?>... classes) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).register(classes);
    }

    /**
     * Makes the provided methods available for reflection at run time. The methods will be returned
     * by {@link Class#getMethod}, {@link Class#getMethods},and all the other methods on
     * {@link Class} that return a single or a list of methods.
     *
     * @since 1.0
     */
    public static void register(Executable... methods) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).register(methods);
    }

    /**
     * Makes the provided fields available for reflection at run time. The fields will be returned
     * by {@link Class#getField}, {@link Class#getFields},and all the other methods on {@link Class}
     * that return a single or a list of fields.
     *
     * @since 1.0
     */
    public static void register(Field... fields) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).register(fields);
    }

    /**
     * Makes the provided classes available for reflective instantiation by
     * {@link Class#newInstance}. This is equivalent to registering the nullary constructors of the
     * classes.
     *
     * @since 1.0
     */
    public static void registerForReflectiveInstantiation(Class<?>... classes) {
        for (Class<?> clazz : classes) {
            if (clazz.isArray() || clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                throw new IllegalArgumentException("Class " + clazz.getTypeName() + " cannot be instantiated reflectively. It must be a non-abstract instance type.");
            }

            Constructor<?> nullaryConstructor;
            try {
                nullaryConstructor = clazz.getDeclaredConstructor();
            } catch (NoSuchMethodException ex) {
                throw new IllegalArgumentException("Class " + clazz.getTypeName() + " cannot be instantiated reflectively . It does not have a nullary constructor.");
            }

            register(nullaryConstructor);
        }
    }

    private RuntimeReflection() {
    }
}
