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
package com.oracle.svm.hosted.imagelayer;

import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.hosted.heap.SVMImageLayerLoader;
import com.oracle.svm.hosted.heap.SVMImageLayerWriter;

public final class HostedImageLayerBuildingSupport extends ImageLayerBuildingSupport {
    private final SVMImageLayerLoader loader;
    private final SVMImageLayerWriter writer;

    private HostedImageLayerBuildingSupport(SVMImageLayerLoader loader, SVMImageLayerWriter writer, boolean buildingImageLayer, boolean buildingInitialLayer, boolean buildingApplicationLayer) {
        super(buildingImageLayer, buildingInitialLayer, buildingApplicationLayer);
        this.loader = loader;
        this.writer = writer;
    }

    public static HostedImageLayerBuildingSupport singleton() {
        return (HostedImageLayerBuildingSupport) ImageSingletons.lookup(ImageLayerBuildingSupport.class);
    }

    public SVMImageLayerLoader getLoader() {
        return loader;
    }

    public SVMImageLayerWriter getWriter() {
        return writer;
    }

    public static HostedImageLayerBuildingSupport initialize(HostedOptionValues values) {
        SVMImageLayerWriter writer = null;
        if (SubstrateOptions.ImageLayer.getValue(values)) {
            writer = new SVMImageLayerWriter();
        }
        SVMImageLayerLoader loader = null;
        if (SubstrateOptions.LoadImageLayer.hasBeenSet(values)) {
            loader = new SVMImageLayerLoader(SubstrateOptions.LoadImageLayer.getValue(values).values());
        }

        boolean buildingImageLayer = loader != null || writer != null;
        boolean buildingInitialLayer = buildingImageLayer && loader == null;
        boolean buildingFinalLayer = buildingImageLayer && writer == null;
        return new HostedImageLayerBuildingSupport(loader, writer, buildingImageLayer, buildingInitialLayer, buildingFinalLayer);
    }
}
