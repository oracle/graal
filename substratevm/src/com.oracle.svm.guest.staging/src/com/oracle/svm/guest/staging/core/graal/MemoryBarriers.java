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
package com.oracle.svm.guest.staging.core.graal;

/** Guest-visible declarations for compiler-recognized memory barriers. */
public final class MemoryBarriers {

    /**
     * Barrier kinds supported by the guest runtime. Names must match their
     * {@code MembarNode.FenceKind} counterparts; the guest enum may expose a subset of the builder
     * enum.
     */
    public enum BarrierKind {
        /** Prevents compiler memory operations from moving across this point without emitting a target fence. */
        NONE,
        /** Prevents preceding stores from being reordered with subsequent stores. */
        STORE_STORE
    }

    /** Not instantiable. */
    private MemoryBarriers() {
    }

    /**
     * Intrinsified as {@code MembarNode} by {@code SubstrateGraphBuilderPlugins}. The {@code kind}
     * must be a non-null compile-time constant. Compiler-owned location semantics remain implicit.
     */
    public static native void memoryBarrier(BarrierKind kind);
}
