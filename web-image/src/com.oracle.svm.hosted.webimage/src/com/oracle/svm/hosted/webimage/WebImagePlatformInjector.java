/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage;

import static com.oracle.svm.hosted.webimage.options.WebImageOptions.CompilerBackend;
import static com.oracle.svm.hosted.webimage.options.WebImageOptions.isNativeImageBackend;

import org.graalvm.nativeimage.Platform;

import com.oracle.svm.hosted.NativeImageClassLoaderPostProcessing;
import com.oracle.svm.hosted.NativeImageClassLoaderSupport;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;

/**
 * Sets the {@link Platform#PLATFORM_PROPERTY_NAME} property based on which code-generation backend
 * is selected.
 * <p>
 * In the {@code native-image} launcher this is always the WasmGC backend. Under {@code web-image},
 * it is determined by the {@link WebImageOptions#Backend} option.
 */
public class WebImagePlatformInjector implements NativeImageClassLoaderPostProcessing {
    @Override
    public void apply(NativeImageClassLoaderSupport support) {
        CompilerBackend backend;
        if (isNativeImageBackend()) {
            // Under native-image, selection of the Web Image backend is not allowed
            backend = CompilerBackend.WASMGC;
        } else {
            backend = WebImageOptions.Backend.getValue(support.getParsedHostedOptions());
        }

        /*
         * installNativeImageClassLoader uses this property at the end to create the platform
         * instance. This method is called before that.
         */
        System.setProperty(Platform.PLATFORM_PROPERTY_NAME, backend.platform.getName());
    }
}
