/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.internal.jvmci.code.ValueUtil.*;

import java.util.*;

import com.oracle.graal.lir.*;
import com.oracle.graal.lir.framemap.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.common.*;
import jdk.internal.jvmci.hotspot.*;
import jdk.internal.jvmci.meta.*;

public final class HotSpotReferenceMapBuilder extends ReferenceMapBuilder {

    private int maxRegisterSize;

    private final ArrayList<Value> objectValues;
    private int objectCount;

    private final TargetDescription target;
    private final int totalFrameSize;

    public HotSpotReferenceMapBuilder(TargetDescription target, int totalFrameSize) {
        this.objectValues = new ArrayList<>();
        this.objectCount = 0;

        this.target = target;
        this.totalFrameSize = totalFrameSize;
    }

    @Override
    public void addLiveValue(Value v) {
        if (isConstant(v)) {
            return;
        }
        LIRKind lirKind = v.getLIRKind();
        if (!lirKind.isValue()) {
            objectValues.add(v);
            if (lirKind.isUnknownReference()) {
                objectCount++;
            } else {
                objectCount += lirKind.getReferenceCount();
            }
        }
        if (isRegister(v)) {
            int size = target.getSizeInBytes(lirKind.getPlatformKind());
            if (size > maxRegisterSize) {
                maxRegisterSize = size;
            }
        }
    }

    @Override
    public ReferenceMap finish(LIRFrameState state) {
        Location[] objects = new Location[objectCount];
        Location[] derivedBase = new Location[objectCount];
        int[] sizeInBytes = new int[objectCount];

        int idx = 0;
        for (Value obj : objectValues) {
            LIRKind kind = obj.getLIRKind();
            int bytes = bytesPerElement(kind);
            if (kind.isUnknownReference()) {
                throw JVMCIError.unimplemented("derived references not yet implemented");
            } else {
                for (int i = 0; i < kind.getPlatformKind().getVectorLength(); i++) {
                    if (kind.isReference(i)) {
                        objects[idx] = toLocation(obj, i * bytes);
                        derivedBase[idx] = null;
                        sizeInBytes[idx] = bytes;
                        idx++;
                    }
                }
            }
        }

        return new HotSpotReferenceMap(objects, derivedBase, sizeInBytes, maxRegisterSize);
    }

    private int bytesPerElement(LIRKind kind) {
        PlatformKind platformKind = kind.getPlatformKind();
        return target.getSizeInBytes(platformKind) / platformKind.getVectorLength();
    }

    private Location toLocation(Value v, int offset) {
        if (isRegister(v)) {
            return Location.subregister(asRegister(v), offset);
        } else {
            StackSlot s = asStackSlot(v);
            return Location.stack(s.getOffset(totalFrameSize) + offset);
        }
    }
}
