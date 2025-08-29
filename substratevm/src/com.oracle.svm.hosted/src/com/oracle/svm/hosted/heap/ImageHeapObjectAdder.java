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
package com.oracle.svm.hosted.heap;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.UnsavedSingleton;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.meta.HostedUniverse;

/**
 * Object adders are executed late, after the analysis has completed and after the shadow heap has
 * been sealed. The code using this feature must ensure that the object has been seen by the static
 * analysis, and it has been added to the shadow heap, e.g., by triggering a shadow heap re-scan.
 */
@AutomaticallyRegisteredImageSingleton
public class ImageHeapObjectAdder implements UnsavedSingleton {
    private final Set<BiConsumer<NativeImageHeap, HostedUniverse>> objectAdders = new HashSet<>();
    private boolean sealed = false;

    public static ImageHeapObjectAdder singleton() {
        return ImageSingletons.lookup(ImageHeapObjectAdder.class);
    }

    public void registerObjectAdder(BiConsumer<NativeImageHeap, HostedUniverse> adder) {
        VMError.guarantee(!sealed, "Object adder is registered too late");
        objectAdders.add(adder);
    }

    public void addInitialObjects(NativeImageHeap heap, HostedUniverse hUniverse) {
        sealed = true;
        objectAdders.forEach(adder -> adder.accept(heap, hUniverse));
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
    }
}
