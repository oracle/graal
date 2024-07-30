/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.graal.compiler.lir.gen.WriteBarrierSetLIRGeneratorTool;

/**
 * Shared definitions to the architecture specific ZGC load and store barrier emission.
 * <p>
 * ZGC relies on coloring pointers stored in an object to track the reference state. Every read from
 * an oop field must check the coloring bits and slow path if some GC work is required. Then the
 * coloring bits, {@code ZPointerLoadShift}, are stripped away with a shift and the oop is used
 * normally. A fixed number of low order bits are used for the coloring information and the upper
 * bits are a valid pointer after shifting away the coloring bits. In ZGC terminology, a
 * {@code zpointer} is a colored oop and a {@code zaddress} is an uncolored pointer that it valid
 * for dereferencing.
 * <p>
 * A more full explanation of Z pointers is at
 * https://github.com/openjdk/jdk/blob/d07e530d33360dae687552a6dfbe26408f3fb58e/src/hotspot/share/gc/z/zAddress.hpp#L42
 */
public interface ZWriteBarrierSetLIRGeneratorTool extends WriteBarrierSetLIRGeneratorTool {

    ForeignCallsProvider getForeignCalls();

    /**
     * This is a placeholder value used when emitting instructions that are marked with the ZGC
     * specific relocations that are part of {@link HotSpotMarkId}.
     */
    int UNPATCHED = 0;

    /**
     * Information about the store that's used to emit the medium path fixup in the store barrier.
     */
    enum StoreKind {
        /**
         * A normal memory write.
         */
        Normal,

        /**
         * Stores as part of compare and swap or atomic read and write.
         */
        Atomic,

        /**
         * Stores to native memory like an
         * {@link jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.OopHandleLocationIdentity}.
         */
        Native
    }

    default ForeignCallLinkage getReadBarrierStub(BarrierType barrierType) {
        ForeignCallLinkage callTarget;
        switch (barrierType) {
            case READ:
                callTarget = getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.Z_LOAD_BARRIER);
                break;
            case REFERENCE_GET:
                callTarget = getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.Z_REFERENCE_GET_BARRIER);
                break;
            case WEAK_REFERS_TO:
                callTarget = getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.Z_WEAK_REFERS_TO_BARRIER);
                break;
            case PHANTOM_REFERS_TO:
                callTarget = getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.Z_PHANTOM_REFERS_TO_BARRIER);
                break;
            default:
                throw GraalError.shouldNotReachHere("Unexpected barrier type: " + barrierType);
        }
        return callTarget;
    }

    default ForeignCallLinkage getWriteBarrierStub(BarrierType barrierType, StoreKind storeKind) {
        ForeignCallLinkage callTarget;
        switch (barrierType) {
            case FIELD:
            case ARRAY:
            case POST_INIT_WRITE:
                if (storeKind == StoreKind.Atomic) {
                    callTarget = getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.Z_STORE_BARRIER_WITH_HEALING);
                } else if (storeKind == StoreKind.Native) {
                    callTarget = getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.Z_STORE_BARRIER_NATIVE);
                } else {
                    callTarget = getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.Z_STORE_BARRIER_WITHOUT_HEALING);
                }
                break;
            default:
                throw GraalError.shouldNotReachHere("Unexpected barrier type: " + barrierType);
        }
        return callTarget;
    }
}
