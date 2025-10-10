/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.gc.shenandoah;

import static com.oracle.svm.core.gc.shenandoah.ShenandoahOptions.ShenandoahRegionSize;

import com.oracle.svm.core.gc.shenandoah.ShenandoahRegionType;
import com.oracle.svm.core.image.ImageHeapObject;

import jdk.graal.compiler.core.common.NumUtil;

/**
 * Holds metadata about a heap region during the image build.
 */
public class ShenandoahImageHeapRegion {
    private final ShenandoahRegionType type;
    private final long startOffset;
    private int used;

    public ShenandoahImageHeapRegion(ShenandoahRegionType type, long startOffset) {
        this.type = type;
        this.startOffset = startOffset;
        this.used = 0;
    }

    public void allocate(ImageHeapObject info) {
        /* We use absolute offsets, all partitions have an offset of 0. */
        info.setOffsetInPartition(startOffset + used);

        int regionSize = ShenandoahRegionSize.getValue();
        assert info.getSize() <= regionSize || type.isHumongous() : info;
        int size = info.getSize() > regionSize ? regionSize : NumUtil.safeToInt(info.getSize());
        increaseUsed(size);
    }

    public ShenandoahRegionType getType() {
        return type;
    }

    public void increaseUsed(int size) {
        assert size > 0 && used + size <= ShenandoahRegionSize.getValue() : size;
        used += size;
    }

    public int getUsed() {
        return used;
    }

    public int getRemainingSpace() {
        return ShenandoahRegionSize.getValue() - used;
    }
}
