/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = BuiltinTraits.DisallowLayered.class)
public final class InterpreterExecutionOffsets {
    private final int dynamicHubCompanionInterpreterTypeOffset;
    private final int interpreterResolvedObjectTypeVtableHolderOffset;
    private final int vtableHolderVtableOffset;
    private final int interpreterResolvedJavaMethodRistrettoMethodOffset;
    private final int ristrettoMethodInstalledCodeOffset;
    private final int installedCodeEntryPointOffset;

    public InterpreterExecutionOffsets(int dynamicHubCompanionInterpreterTypeOffset, int interpreterResolvedObjectTypeVtableHolderOffset, int vtableHolderVtableOffset,
                    int interpreterResolvedJavaMethodRistrettoMethodOffset, int ristrettoMethodInstalledCodeOffset, int installedCodeEntryPointOffset) {
        this.dynamicHubCompanionInterpreterTypeOffset = dynamicHubCompanionInterpreterTypeOffset;
        this.interpreterResolvedObjectTypeVtableHolderOffset = interpreterResolvedObjectTypeVtableHolderOffset;
        this.vtableHolderVtableOffset = vtableHolderVtableOffset;
        this.interpreterResolvedJavaMethodRistrettoMethodOffset = interpreterResolvedJavaMethodRistrettoMethodOffset;
        this.ristrettoMethodInstalledCodeOffset = ristrettoMethodInstalledCodeOffset;
        this.installedCodeEntryPointOffset = installedCodeEntryPointOffset;
    }

    public static InterpreterExecutionOffsets singleton() {
        return ImageSingletons.lookup(InterpreterExecutionOffsets.class);
    }

    public int getDynamicHubCompanionInterpreterTypeOffset() {
        return dynamicHubCompanionInterpreterTypeOffset;
    }

    public int getInterpreterResolvedObjectTypeVtableHolderOffset() {
        return interpreterResolvedObjectTypeVtableHolderOffset;
    }

    public int getVtableHolderVtableOffset() {
        return vtableHolderVtableOffset;
    }

    public int getInterpreterResolvedJavaMethodRistrettoMethodOffset() {
        return interpreterResolvedJavaMethodRistrettoMethodOffset;
    }

    public int getRistrettoMethodInstalledCodeOffset() {
        return ristrettoMethodInstalledCodeOffset;
    }

    public int getInstalledCodeEntryPointOffset() {
        return installedCodeEntryPointOffset;
    }
}
