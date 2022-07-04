/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.meta;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;

public final class KnownOffsets {
    private int vtableBaseOffset;
    private int vtableEntrySize;
    private int typeIDSlotsOffset;
    private int componentHubOffset;
    private int javaFrameAnchorLastSPOffset;
    private int javaFrameAnchorLastIPOffset;
    private int vmThreadStatusOffset;
    private int imageCodeInfoCodeStartOffset;

    @Fold
    public static KnownOffsets singleton() {
        return ImageSingletons.lookup(KnownOffsets.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setLazyState(int vtableBaseOffset, int vtableEntrySize, int typeIDSlotsOffset, int componentHubOffset,
                    int javaFrameAnchorLastSPOffset, int javaFrameAnchorLastIPOffset, int vmThreadStatusOffset, int imageCodeInfoCodeStartOffset) {
        assert !isFullyInitialized();

        this.vtableBaseOffset = vtableBaseOffset;
        this.vtableEntrySize = vtableEntrySize;
        this.typeIDSlotsOffset = typeIDSlotsOffset;
        this.componentHubOffset = componentHubOffset;
        this.javaFrameAnchorLastSPOffset = javaFrameAnchorLastSPOffset;
        this.javaFrameAnchorLastIPOffset = javaFrameAnchorLastIPOffset;
        this.vmThreadStatusOffset = vmThreadStatusOffset;
        this.imageCodeInfoCodeStartOffset = imageCodeInfoCodeStartOffset;

        assert isFullyInitialized();
    }

    private boolean isFullyInitialized() {
        return vtableEntrySize > 0;
    }

    public int getVTableOffset(int vTableIndex) {
        assert isFullyInitialized();
        return vtableBaseOffset + vTableIndex * vtableEntrySize;
    }

    public int getTypeIDSlotsOffset() {
        assert isFullyInitialized();
        return typeIDSlotsOffset;
    }

    public int getComponentHubOffset() {
        assert isFullyInitialized();
        return componentHubOffset;
    }

    public int getJavaFrameAnchorLastSPOffset() {
        assert isFullyInitialized();
        return javaFrameAnchorLastSPOffset;
    }

    public int getJavaFrameAnchorLastIPOffset() {
        assert isFullyInitialized();
        return javaFrameAnchorLastIPOffset;
    }

    public int getVMThreadStatusOffset() {
        assert isFullyInitialized();
        assert SubstrateOptions.MultiThreaded.getValue() && vmThreadStatusOffset != -1;
        return vmThreadStatusOffset;
    }

    public int getImageCodeInfoCodeStartOffset() {
        assert isFullyInitialized();
        return imageCodeInfoCodeStartOffset;
    }
}
