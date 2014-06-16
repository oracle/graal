/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.nodes.type.*;

public class HotSpotTargetDescription extends TargetDescription {

    private final PlatformKind rawNarrowOopKind;

    public HotSpotTargetDescription(Architecture arch, boolean isMP, int stackAlignment, int implicitNullCheckLimit, boolean inlineObjects, PlatformKind rawNarrowOopKind) {
        super(arch, isMP, stackAlignment, implicitNullCheckLimit, inlineObjects);
        this.rawNarrowOopKind = rawNarrowOopKind;
    }

    @Override
    public int getSizeInBytes(PlatformKind kind) {
        if (kind == NarrowOopStamp.NarrowOop) {
            return super.getSizeInBytes(rawNarrowOopKind);
        } else {
            return super.getSizeInBytes(kind);
        }
    }

    @Override
    public ReferenceMap createReferenceMap(boolean hasRegisters, int stackSlotCount) {
        return new HotSpotReferenceMap(hasRegisters ? arch.getRegisterReferenceMapBitCount() : 0, stackSlotCount, this);
    }
}
