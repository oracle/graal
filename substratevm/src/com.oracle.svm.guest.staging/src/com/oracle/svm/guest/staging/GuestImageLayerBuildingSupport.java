/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.shared.ImageLayerBuildingSupportProvider;
import com.oracle.svm.shared.meta.GuestFold;

/**
 * Guest-context counterpart of {@code ImageLayerBuildingSupport}.
 */
public final class GuestImageLayerBuildingSupport {

    @GuestFold
    private static ImageLayerBuildingSupportProvider singleton() {
        return ImageSingletons.lookup(ImageLayerBuildingSupportProvider.class);
    }

    @GuestFold
    private static boolean installed() {
        return ImageSingletons.contains(ImageLayerBuildingSupportProvider.class);
    }

    @GuestFold
    public static boolean firstImageBuild() {
        return installed() ? singleton().isFirstImageBuild() : true;
    }

    @GuestFold
    public static boolean lastImageBuild() {
        return installed() ? singleton().isLastImageBuild() : true;
    }

    @GuestFold
    public static boolean buildingImageLayer() {
        return installed() && singleton().isBuildingImageLayer();
    }

    @GuestFold
    public static boolean buildingInitialLayer() {
        return installed() && singleton().isBuildingInitialLayer();
    }

    @GuestFold
    public static boolean buildingApplicationLayer() {
        return installed() && singleton().isBuildingApplicationLayer();
    }

    @GuestFold
    public static boolean buildingExtensionLayer() {
        return installed() && singleton().isBuildingExtensionLayer();
    }

    @GuestFold
    public static boolean buildingSharedLayer() {
        return installed() && singleton().isBuildingSharedLayer();
    }

    private GuestImageLayerBuildingSupport() {
    }
}
