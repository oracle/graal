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
package com.oracle.svm.core.imagelayer;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.meta.SharedMethod;

public abstract class DynamicImageLayerInfo {
    public final int layerNumber;
    public final int nextLayerNumber;
    public final int numLayers;

    protected DynamicImageLayerInfo(int layerNumber) {
        this.layerNumber = layerNumber;
        this.nextLayerNumber = layerNumber + 1;
        this.numLayers = nextLayerNumber;
    }

    public static DynamicImageLayerInfo singleton() {
        return ImageSingletons.lookup(DynamicImageLayerInfo.class);
    }

    public record PriorLayerMethodLocation(CGlobalDataInfo base, int offset) {
    }

    /**
     * Returns a (Base, Offset) pair which can be used to call a method defined in a prior layer.
     */
    public abstract PriorLayerMethodLocation getPriorLayerMethodLocation(SharedMethod method);
}
