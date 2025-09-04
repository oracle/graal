/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.impl;

import org.graalvm.nativeimage.ImageInfo;

/**
 * Experimental API used to support runtime class loading in the context of native images.
 */
public final class ClassLoading {
    private ClassLoading() {
    }

    /**
     * Checks if runtime class loading is enabled.
     */
    public static boolean isSupported() {
        if (!ImageInfo.inImageRuntimeCode()) {
            return true;
        }
        return ClassLoadingSupport.singleton().isSupported();
    }

    /**
     * Opens a scope in which class loading is not constrained by the reflection configuration. Once
     * the returned {@link AutoCloseable} is closed, the previous state is restored.
     *
     * @throws IllegalStateException if class loading is not {@linkplain #isSupported() supported}.
     */
    public static ArbitraryClassLoadingScope allowArbitraryClassLoading() {
        if (!isSupported()) {
            throw new IllegalStateException("Runtime class loading is not supported. It must be enabled at build-time with -H:+RuntimeClassLoading.");
        }
        return new ArbitraryClassLoadingScope();
    }

    public static final class ArbitraryClassLoadingScope implements AutoCloseable {
        private boolean closed;

        ArbitraryClassLoadingScope() {
            if (!ImageInfo.inImageRuntimeCode()) {
                return;
            }
            ClassLoadingSupport.singleton().startIgnoreReflectionConfigurationScope();
        }

        @Override
        public void close() {
            if (closed) {
                throw new IllegalStateException("ArbitraryClassLoadingScope is already closed");
            }
            closed = true;
            if (!ImageInfo.inImageRuntimeCode()) {
                return;
            }
            ClassLoadingSupport.singleton().endIgnoreReflectionConfigurationScope();
        }
    }
}
