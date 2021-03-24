/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.remset;

import java.util.List;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.GreyToBlackObjectVisitor;
import com.oracle.svm.core.genscavenge.Space;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.util.HostedByteBufferPointer;

import jdk.vm.ci.meta.MetaAccessProvider;

public interface RememberedSet {
    @Fold
    static RememberedSet get() {
        return ImageSingletons.lookup(RememberedSet.class);
    }

    BarrierSet createBarrierSet(MetaAccessProvider metaAccess);

    UnsignedWord getHeaderSizeOfAlignedChunk();

    UnsignedWord getHeaderSizeOfUnalignedChunk();

    @Platforms(Platform.HOSTED_ONLY.class)
    void enableRememberedSetForAlignedChunk(HostedByteBufferPointer chunk, int chunkPosition, List<ImageHeapObject> objects);

    @Platforms(Platform.HOSTED_ONLY.class)
    void enableRememberedSetForUnalignedChunk(HostedByteBufferPointer chunk);

    void enableRememberedSetForChunk(AlignedHeader chunk);

    void enableRememberedSetForChunk(UnalignedHeader chunk);

    void enableRememberedSetForObject(AlignedHeader chunk, Object obj);

    void clearRememberedSet(AlignedHeader chunk);

    void clearRememberedSet(UnalignedHeader chunk);

    @AlwaysInline("GC performance")
    boolean hasRememberedSet(UnsignedWord header);

    @AlwaysInline("GC performance")
    void dirtyCardForAlignedObject(Object object, boolean verifyOnly);

    @AlwaysInline("GC performance")
    void dirtyCardForUnalignedObject(Object object, boolean verifyOnly);

    @AlwaysInline("GC performance")
    void dirtyCardIfNecessary(Object holderObject, Object object);

    void walkDirtyObjects(AlignedHeader chunk, GreyToBlackObjectVisitor visitor);

    void walkDirtyObjects(UnalignedHeader chunk, GreyToBlackObjectVisitor visitor);

    void walkDirtyObjects(Space space, GreyToBlackObjectVisitor visitor);

    boolean verify(AlignedHeader firstAlignedHeapChunk);

    boolean verify(UnalignedHeader firstUnalignedHeapChunk);
}
