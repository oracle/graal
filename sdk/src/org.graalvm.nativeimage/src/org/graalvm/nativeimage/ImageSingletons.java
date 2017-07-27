/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.impl.ImageSingletonsSupport;

/**
 * A key-value store of singleton objects. The registry is populated during native image generation
 * (no changes are allowed at run time); it is queried both during native image generation and at
 * run time. The key is a {@link java.lang.Class class or interface}, the value is an instance that
 * extends that class.
 * <p>
 * When accessing the registry at run time, the key must be a compile time constant. The
 * {@link #lookup} is performed during native image generation, and the resulting value is put into
 * the compiled code as a literal constant. Therefore, the actual map that stores the key-value data
 * is not necessary at run time, and there is no lookup overhead at run time.
 * <p>
 * {@link ImageSingletons} avoids static fields: instead of filling a static field during native
 * image generation and accessing it at run time, the value is put into the {@link ImageSingletons}.
 * Usually, that happens in one of the early initialization methods of a {@link Feature}.
 */
public final class ImageSingletons {

    /**
     * Add a singleton to the registry. The key must be unique, i.e., no value must have been
     * registered with the given class before.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static <T> void add(Class<T> key, T value) {
        SUPPORT.add(key, value);
    }

    /**
     * Lookup a singleton in the registry. The key must be a compile time constant, so that the call
     * to this method can be replaced with the constant configuration objects.
     *
     * The key must have been {@link #add added} before, i.e., the result is guaranteed to be non-
     * {@code null}.
     */
    public static <T> T lookup(Class<T> key) {
        return SUPPORT.lookup(key);
    }

    /**
     * Checks if a singleton is in the registry. The key must be a compile time constant, so that
     * the call to this method can be replaced with the constant {@code true} of {@code false}.
     */
    public static boolean contains(Class<?> key) {
        return SUPPORT.contains(key);
    }

    private ImageSingletons() {
    }

    /** Implementation-specific singleton that stores the registration data. */
    private static final ImageSingletonsSupport SUPPORT;

    static {
        try {
            SUPPORT = (ImageSingletonsSupport) Class.forName("com.oracle.svm.hosted.ImageSingletonsSupportImpl", true, Thread.currentThread().getContextClassLoader()).newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | SecurityException | ClassNotFoundException ex) {
            throw new Error("Class path of native image generator is not set up correctly");
        }
    }
}
