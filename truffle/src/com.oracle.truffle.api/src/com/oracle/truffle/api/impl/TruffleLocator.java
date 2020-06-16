/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.TruffleRuntime;

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
    public static List<ClassLoader> loaders() {
        TruffleLocator locator = Truffle.getRuntime().getCapability(TruffleLocator.class);
        if (locator != null) {
            List<ClassLoader> found = new ArrayList<>();
            Response response = new Response(found);
            locator.locate(response);
            if (found.isEmpty()) {
                // use default
                return null;
            }
            return found;
        } else {
            return null;
        }
    }

    static void initializeNativeImageTruffleLocator() {
        assert TruffleOptions.AOT : "Only supported in AOT mode.";
        if (nativeImageLocator != null) {
            if (ImageInfo.inImageBuildtimeCode() || NATIVE_IMAGE_LOCATOR_INITIALIZED.compareAndSet(false, true)) {
                nativeImageLocator.locate(new Response(new ArrayList<>()));
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
        private final List<ClassLoader> loaders;

        Response(List<ClassLoader> loaders) {
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
