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

import static com.oracle.svm.hosted.image.NativeImage.localSymbolNameForMethod;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.imagelayer.BuildingExtensionLayerPredicate;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.UnsavedSingleton;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedMethod;

/**
 * Tracks references to symbols defined in prior layers so that we can define them as undefined
 * symbols in the layer.
 */
@AutomaticallyRegisteredImageSingleton(onlyWith = BuildingExtensionLayerPredicate.class)
public class PriorLayerSymbolTracker implements UnsavedSingleton {

    private final Set<HostedMethod> referencedMethods = ConcurrentHashMap.newKeySet();
    boolean sealed = false;

    public static PriorLayerSymbolTracker singletonOrNull() {
        if (ImageSingletons.contains(PriorLayerSymbolTracker.class)) {
            return ImageSingletons.lookup(PriorLayerSymbolTracker.class);
        }

        return null;
    }

    public void registerPriorLayerReference(HostedMethod method) {
        VMError.guarantee(method.getWrapped().isInBaseLayer(), "Can only register methods in a prior layer");
        VMError.guarantee(!sealed, "Too late to register methods");
        referencedMethods.add(method);
    }

    public void defineSymbols(ObjectFile objectFile) {
        sealed = true;
        referencedMethods.forEach(m -> objectFile.createUndefinedSymbol(localSymbolNameForMethod(m), 0, true));
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
    }
}
