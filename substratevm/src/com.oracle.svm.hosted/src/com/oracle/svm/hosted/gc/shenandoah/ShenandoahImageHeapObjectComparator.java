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

import java.util.Comparator;

import com.oracle.svm.core.image.ImageHeapObject;

public class ShenandoahImageHeapObjectComparator implements Comparator<ImageHeapObject> {
    private final int regionSize;
    private final boolean humongousObjectsFirst;

    public ShenandoahImageHeapObjectComparator(int regionSize, boolean humongousObjectsFirst) {
        this.regionSize = regionSize;
        this.humongousObjectsFirst = humongousObjectsFirst;
    }

    @Override
    public int compare(ImageHeapObject a, ImageHeapObject b) {
        boolean aIsHumongous = a.getSize() > regionSize;
        boolean bIsHumongous = b.getSize() > regionSize;
        if (aIsHumongous != bIsHumongous) {
            /* Place humongous objects at the start or at the end. */
            return aIsHumongous == humongousObjectsFirst ? -1 : 1;
        }

        boolean aIsDynamicHub = a.getObjectClass() == Class.class;
        boolean bIsDynamicHub = b.getObjectClass() == Class.class;
        if (aIsDynamicHub != bIsDynamicHub) {
            /* Place DynamicHubs before other objects, regardless of size. */
            return aIsDynamicHub ? -1 : 1;
        }

        return Long.signum(b.getSize() - a.getSize());
    }
}
