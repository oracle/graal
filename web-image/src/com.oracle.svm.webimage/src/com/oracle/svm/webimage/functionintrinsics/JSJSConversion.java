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

package com.oracle.svm.webimage.functionintrinsics;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSValue;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.webimage.annotation.JSRawCall;
import com.oracle.svm.webimage.platform.WebImageJSPlatform;

/**
 * Helper code for the JS backend to deal with conversions between the Java and JS world.
 */
@AutomaticallyRegisteredImageSingleton(JSConversion.class)
@Platforms(WebImageJSPlatform.class)
public class JSJSConversion extends JSConversion {
    /**
     * Associates the given Java object with the given JS value.
     * <p>
     * The JS value is stored in the Java object under {@code javaScriptNative} property.
     */
    @Override
    @JSRawCall
    @JS("self[runtime.symbol.javaScriptNative] = jsNative;")
    public native void setJavaScriptNativeImpl(JSValue self, Object jsNative);

    /**
     * Extracts the JS-native value, which represents the JavaScript counterpart of the specified
     * Java object, and can be passed to JavaScript code.
     */
    @Override
    @JSRawCall
    @JS("""
                    const javaScriptNative = self[runtime.symbol.javaScriptNative];
                    return javaScriptNative === undefined ? null : javaScriptNative;""")
    public native Object extractJavaScriptNativeImpl(JSValue self);
}
