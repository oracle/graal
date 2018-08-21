/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.TruffleRuntime;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import org.graalvm.nativeimage.ImageInfo;

/**
 * Locator that allows the users of the Truffle API to find implementations of languages to be
 * available in {@link com.oracle.truffle.api.vm.PolyglotEngine}. A {@link TruffleRuntime} can
 * provide the locator via its {@link TruffleRuntime#getCapability(java.lang.Class)} method.
 *
 * @since 0.18
 */
public abstract class TruffleLocator {

    private static TruffleLocator nativeImageLocator;   // effectively final after native image
                                                        // compilation
    private static final AtomicBoolean NATIVE_IMAGE_LOCATOR_INITIALIZED = new AtomicBoolean();

    /**
     * Creates the set of classloaders to be used by the system.
     *
     * @return set of classloaders to search registrations in
     */
    public static Set<ClassLoader> loaders() {
        Iterable<TruffleLocator> allLocators;
        TruffleLocator locator = Truffle.getRuntime().getCapability(TruffleLocator.class);
        if (locator != null) {
            allLocators = Collections.singleton(locator);
        } else {
            allLocators = Collections.emptyList();
        }
        Set<ClassLoader> found = new LinkedHashSet<>();
        Response response = new Response(found);
        for (TruffleLocator test : allLocators) {
            test.locate(response);
        }
        found.add(ClassLoader.getSystemClassLoader());
        found.add(TruffleLocator.class.getClassLoader());
        return found;
    }

    /**
     * Utility method to load a class from one of the located classloaders. Please note that this
     * method is used in tests using reflection. Do not remove.
     *
     * @param name class to search for
     * @return the class or <code>null</code> if none of the loaders knows the class
     */
    static Class<?> loadClass(String name) {
        for (ClassLoader loader : loaders()) {
            if (loader == null) {
                continue;
            }
            try {
                return loader.loadClass(name);
            } catch (ClassNotFoundException ex) {
                continue;
            }
        }
        return null;
    }

    static void initializeNativeImageTruffleLocator() {
        assert TruffleOptions.AOT : "Only supported in AOT mode.";
        if (nativeImageLocator != null) {
            if (ImageInfo.inImageBuildtimeCode() || NATIVE_IMAGE_LOCATOR_INITIALIZED.compareAndSet(false, true)) {
                nativeImageLocator.locate(new Response(new HashSet<>()));
            }
        }
    }

    /**
     * Called to locate languages and other parts of the system.
     *
     * @param response the response to fill in with found languages
     * @since 0.18
     */
    protected abstract void locate(Response response);

    /**
     * Callback to register languages.
     *
     * @since 0.18
     */
    public static final class Response {
        private final Set<ClassLoader> loaders;

        Response(Set<ClassLoader> loaders) {
            this.loaders = loaders;
        }

        /**
         * Register a classloader to be used to find implementations of the languages. Can be called
         * multiple times to register as many classloaders as necessary.
         *
         * @param languageLoader the classloader to use
         * @since 0.18
         */
        public void registerClassLoader(ClassLoader languageLoader) {
            loaders.add(languageLoader);
        }

    }

    @SuppressWarnings("unused")
    private static void initializeNativeImageState() {
        assert TruffleOptions.AOT : "Only supported during image generation";
        nativeImageLocator = Truffle.getRuntime().getCapability(TruffleLocator.class);
    }

    @SuppressWarnings("unused")
    private static void resetNativeImageState() {
        assert TruffleOptions.AOT : "Only supported during image generation";
        nativeImageLocator = null;
    }
}
