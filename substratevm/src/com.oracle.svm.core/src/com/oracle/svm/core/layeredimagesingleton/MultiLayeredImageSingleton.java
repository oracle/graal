/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.layeredimagesingleton;

import com.oracle.svm.core.util.VMError;

public interface MultiLayeredImageSingleton extends LayeredImageSingleton {

    int LAYER_NUM_UNINSTALLED = -3;
    /**
     * Marker indicating this field is an instance field, not a static field.
     */
    int NONSTATIC_FIELD_LAYER_NUMBER = -2;
    /**
     * Marker used when a having a layer number is not applicable (such as when not building layered
     * images).
     */
    int UNUSED_LAYER_NUMBER = -1;
    int UNKNOWN_LAYER_NUMBER = 0;
    int INITIAL_LAYER_NUMBER = 0;

    /**
     * Returns an array containing the image singletons installed for {@code key} within all layers.
     * See {@link LayeredImageSingleton} for full explanation.
     */
    @SuppressWarnings("unused")
    static <T extends MultiLayeredImageSingleton> T[] getAllLayers(Class<T> key) {
        throw VMError.shouldNotReachHere("This can only be called during runtime");
    }

    /**
     * Retrieve a specific layer's singleton from a MultiLayeredImageSingleton. The index represents
     * which layer number's singleton to retrieve. If a singleton was not installed in that layer
     * (and this is allowed), then null is returned.
     */
    @SuppressWarnings("unused")
    static <T extends MultiLayeredImageSingleton> T getForLayer(Class<T> key, int index) {
        throw VMError.shouldNotReachHere("This can only be called during runtime");
    }
}
