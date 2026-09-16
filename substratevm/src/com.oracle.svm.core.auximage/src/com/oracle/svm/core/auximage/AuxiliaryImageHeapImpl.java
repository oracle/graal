/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.auximage;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.genscavenge.AuxiliaryImageHeap;
import com.oracle.svm.core.genscavenge.HeapChunkVisitor;
import com.oracle.svm.core.genscavenge.ImageHeapInfo;
import com.oracle.svm.core.genscavenge.ImageHeapWalker;
import com.oracle.svm.core.heap.ExcludeFromReferenceMap;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import jdk.graal.compiler.api.replacements.Fold;

@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
final class AuxiliaryImageHeapImpl implements AuxiliaryImageHeap {
    @Fold
    static AuxiliaryImageHeapImpl singleton() {
        return (AuxiliaryImageHeapImpl) ImageSingletons.lookup(AuxiliaryImageHeap.class);
    }

    @ExcludeFromReferenceMap(reason = "Do not keep live reference into auxiliary image heap during unloading.") //
    ImageHeapInfo heapInfo;

    boolean isWalkable;

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean containsObject(Pointer p) {
        return heapInfo != null && heapInfo.isInImageHeap(p);
    }

    @Override
    public void walkObjects(ObjectVisitor visitor) {
        if (isWalkable && heapInfo != null) {
            ImageHeapWalker.walkImageHeapObjects(heapInfo, visitor);
        }
    }

    @Override
    public void walkHeapChunks(HeapChunkVisitor visitor) {
        if (isWalkable && heapInfo != null) {
            ImageHeapWalker.walkImageHeapChunks(heapInfo, visitor);
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void walkRegions(MemoryWalker.ImageHeapRegionVisitor visitor) {
        if (isWalkable && heapInfo != null) {
            ImageHeapWalker.walkRegions(heapInfo, visitor);
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public ImageHeapInfo getImageHeapInfo() {
        return isWalkable ? heapInfo : null;
    }
}
