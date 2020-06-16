/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.nativeimage.hosted;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

//Checkstyle: allow reflection

/**
 * This class provides methods that can be called during native image generation to register
 * classes, methods, and fields for reflection at run time.
 *
 * @since 19.0
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class RuntimeReflection {

    /**
     * Makes the provided classes available for reflection at run time. A call to
     * {@link Class#forName} for the names of the classes will return the classes at run time.
     *
     * @since 19.0
     */
    public static void register(Class<?>... classes) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).register(classes);
    }

    /**
     * Makes the provided methods available for reflection at run time. The methods will be returned
     * by {@link Class#getMethod}, {@link Class#getMethods},and all the other methods on
     * {@link Class} that return a single or a list of methods.
     *
     * @since 19.0
     */
    public static void register(Executable... methods) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).register(methods);
    }

    /**
     * Makes the provided fields available for reflection at run time. The fields will be returned
     * by {@link Class#getField}, {@link Class#getFields},and all the other methods on {@link Class}
     * that return a single or a list of fields.
     *
     * @since 19.0
     */
    public static void register(Field... fields) {
        register(false, fields);
    }

    /**
     * Makes the provided fields available for reflection at run time. The fields will be returned
     * by {@link Class#getField}, {@link Class#getFields},and all the other methods on {@link Class}
     * that return a single or a list of fields.
     *
     * @param finalIsWritable for all of the passed fields which are marked {@code final}, indicates
     *            whether it should be possible to change their value using reflection.
     *
     * @since 19.0
     */
    public static void register(boolean finalIsWritable, Field... fields) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).register(finalIsWritable, false, fields);
    }

    /**
     * Makes the provided classes available for reflective instantiation by
     * {@link Class#newInstance}. This is equivalent to registering the nullary constructors of the
     * classes.
     *
     * @since 19.0
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
