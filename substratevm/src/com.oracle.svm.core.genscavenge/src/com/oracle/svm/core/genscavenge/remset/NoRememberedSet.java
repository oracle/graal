/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.GreyToBlackObjectVisitor;
import com.oracle.svm.core.genscavenge.Space;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.genscavenge.graal.SubstrateNoBarrierSet;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.util.HostedByteBufferPointer;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * This implementation is only used if remembered sets are disabled, so most operations are no-ops.
 * Without a remembered set, the GC won't be able to collect the young and the old generation
 * independently.
 */
public final class NoRememberedSet implements RememberedSet {
    @Override
    public BarrierSet createBarrierSet(MetaAccessProvider metaAccess) {
        return new SubstrateNoBarrierSet();
    }

    @Override
    public UnsignedWord getHeaderSizeOfAlignedChunk() {
        UnsignedWord headerSize = WordFactory.unsigned(SizeOf.get(AlignedHeader.class));
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(headerSize, alignment);
    }

    @Override
    public UnsignedWord getHeaderSizeOfUnalignedChunk() {
        UnsignedWord headerSize = WordFactory.unsigned(SizeOf.get(UnalignedHeader.class));
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(headerSize, alignment);
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public void enableRememberedSetForAlignedChunk(HostedByteBufferPointer chunk, int chunkPosition, List<ImageHeapObject> objects) {
        // Nothing to do.
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public void enableRememberedSetForUnalignedChunk(HostedByteBufferPointer chunk) {
        // Nothing to do.
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void enableRememberedSetForChunk(AlignedHeader chunk) {
        // Nothing to do.
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void enableRememberedSetForChunk(UnalignedHeader chunk) {
        // Nothing to do.
    }

    @Override
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void enableRememberedSetForObject(AlignedHeader chunk, Object obj, UnsignedWord objSize) {
        // Nothing to do.
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void clearRememberedSet(AlignedHeader chunk) {
        // Nothing to do.
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void clearRememberedSet(UnalignedHeader chunk) {
        // Nothing to do.
    }

    @Override
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean hasRememberedSet(UnsignedWord header) {
        return false;
    }

    @Override
    public void dirtyCardForAlignedObject(Object object, boolean verifyOnly) {
        throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public void dirtyCardForUnalignedObject(Object object, boolean verifyOnly) {
        throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void dirtyCardIfNecessary(Object holderObject, Object object) {
        // Nothing to do.
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void walkDirtyObjects(AlignedHeader chunk, GreyToBlackObjectVisitor visitor, boolean clean) {
        throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void walkDirtyObjects(UnalignedHeader chunk, GreyToBlackObjectVisitor visitor, boolean clean) {
        throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void walkDirtyObjects(Space space, GreyToBlackObjectVisitor visitor, boolean clean) {
        throw VMError.shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public boolean verify(AlignedHeader firstAlignedHeapChunk) {
        return true;
    }

    @Override
    public boolean verify(UnalignedHeader firstUnalignedHeapChunk) {
        return true;
    }
}
