/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import org.graalvm.nativeimage.c.struct.RawStructure;

import com.oracle.svm.core.util.DuplicatedInNativeCode;

/**
 * A tethered {@link CodeInfo} object that can be accessed using the static methods on the class
 * {@link CodeInfoAccess}. As long as the tether object is reachable, it is guaranteed that the GC
 * won't free the {@link CodeInfo} object. For more details, refer to the documentation of
 * {@link CodeInfoAccess}.
 */
@RawStructure
public interface CodeInfo extends UntetheredCodeInfo {
    /**
     * Initial state, probably not fully initialized yet. The GC must visit all heap references
     * (except for the code constants as those are not installed yet).
     */
    @DuplicatedInNativeCode //
    int STATE_CREATED = 0;

    /**
     * Indicates that the code is fully installed from the GC point of view. The GC must visit all
     * heap references, including code constants that are directly embedded into the machine code.
     *
     * @see CodeInfoAccess#isAliveState
     */
    @DuplicatedInNativeCode //
    int STATE_CODE_CONSTANTS_LIVE = STATE_CREATED + 1;

    /**
     * Indicates that the code can no longer be newly invoked. Once there are no activations
     * remaining, this {@link CodeInfo} object will be freed by the GC. Until then, the GC must
     * visit all heap references, including code constants that are directly embedded into the
     * machine code.
     *
     * @see CodeInfoAccess#isAliveState
     */
    @DuplicatedInNativeCode //
    int STATE_NON_ENTRANT = STATE_CODE_CONSTANTS_LIVE + 1;

    /**
     * This state is only a temporary state when the VM is at a safepoint. It indicates that no
     * activations are remaining and that the code is no longer needed (code is non-entrant) or no
     * longer wanted (code has references to otherwise unreachable objects). The GC will invalidate
     * and free this {@link CodeInfo} object during the current safepoint. It is crucial that the GC
     * still visits all heap references that may be accessed while invalidating and freeing the
     * {@link CodeInfo} object (i.e., all object fields).
     */
    @DuplicatedInNativeCode //
    int STATE_READY_FOR_INVALIDATION = STATE_NON_ENTRANT + 1;

    /**
     * Indicates that this {@link CodeInfo} object was invalidated and parts of its data (including
     * the code memory) were freed. The remaining data will be freed by the GC once the tether
     * object becomes unreachable. Until then, the GC must continue visiting all heap references
     * (except for the code constants as the code is no longer installed).
     */
    @DuplicatedInNativeCode //
    int STATE_PARTIALLY_FREED = STATE_READY_FOR_INVALIDATION + 1;

    /**
     * This state is only a temporary state when the VM is at a safepoint. It indicates that a
     * previously already partially freed {@link CodeInfo} object is no longer reachable from the GC
     * point of view. The GC will free the {@link CodeInfo} object during the current safepoint. It
     * is crucial that the GC still visits all heap references that may be accessed while freeing
     * the {@link CodeInfo} object (i.e., all object fields).
     */
    @DuplicatedInNativeCode //
    int STATE_UNREACHABLE = STATE_PARTIALLY_FREED + 1;

    /**
     * Indicates that the {@link CodeInfo} object was already freed. This state should never be
     * seen.
     */
    @DuplicatedInNativeCode //
    int STATE_FREED = STATE_UNREACHABLE + 1;
}
