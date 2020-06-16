/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage;

import org.graalvm.nativeimage.hosted.Feature;
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
 *
 * @since 19.0
 */
public final class ImageSingletons {

    /**
     * Add a singleton to the registry. The key must be unique, i.e., no value must have been
     * registered with the given class before.
     *
     * @since 19.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static <T> void add(Class<T> key, T value) {
        ImageSingletonsSupport.get().add(key, value);
    }

    /**
     * Lookup a singleton in the registry. The key must be a compile time constant, so that the call
     * to this method can be replaced with the constant configuration objects.
     * <p>
     * The key must have been {@link #add added} before, i.e., the result is guaranteed to be non-
     * {@code null}.
     *
     * @since 19.0
     */
    public static <T> T lookup(Class<T> key) {
        return ImageSingletonsSupport.get().lookup(key);
    }

    /**
     * Checks if a singleton is in the registry. The key must be a compile time constant, so that
     * the call to this method can be replaced with the constant {@code true} or {@code false}.
     * <p>
     * The method returns {@code false} since 19.3.0 when not used in the context of a native image.
     * As such it is safe to write:
     *
     * {@codesnippet org.graalvm.nativeimage.ImageSingletonsTest}
     *
     * and let such code run fine in the context of {@link ImageInfo#inImageCode() native image} as
     * well as outside of it.
     *
     * @since 19.0
     */
    public static boolean contains(Class<?> key) {
        if (!ImageInfo.inImageCode()) {
            return false;
        }
        return ImageSingletonsSupport.get().contains(key);
    }

    private ImageSingletons() {
    }
}
