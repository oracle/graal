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

import java.util.function.Function;

import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.util.VMError;

public interface MultiLayeredImageSingleton extends LayeredImageSingleton {

    /**
     * Returns an array containing the image singletons installed for {@code key} within all layers.
     * See {@link LayeredImageSingleton} for full explanation.
     */
    @SuppressWarnings("unused")
    static <T extends MultiLayeredImageSingleton> T[] getAllLayers(Class<T> key) {
        throw VMError.shouldNotReachHere("This can only be called during runtime");
    }

    default <T extends MultiLayeredImageSingleton, U> U getSingletonData(T singleton, T[] singletons, Function<T, U> getSingletonDataFunction) {
        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            for (var layerSingleton : singletons) {
                U result = getSingletonDataFunction.apply(layerSingleton);
                if (result != null) {
                    return result;
                }
            }
            return null;
        } else {
            return getSingletonDataFunction.apply(singleton);
        }
    }
}
