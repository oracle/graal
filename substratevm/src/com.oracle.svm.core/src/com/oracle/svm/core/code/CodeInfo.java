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

/**
 * A tethered {@link CodeInfo} object that can be accessed using the static methods on the class
 * {@link CodeInfoAccess}. As long as the tether object is reachable, it is guaranteed that the GC
 * won't free the {@link CodeInfo} object. For more details, refer to the documentation of
 * {@link CodeInfoAccess}.
 */
@RawStructure
public interface CodeInfo extends UntetheredCodeInfo {
    /** Initial state. */
    int STATE_CREATED = 0;
    /**
     * Indicates that the code is fully installed from the GC point of view, i.e., the GC must visit
     * the heap references that are directly embedded in the machine code.
     */
    int STATE_CODE_CONSTANTS_LIVE = STATE_CREATED + 1;
    /**
     * Indicates that the GC will invalidate and free this {@link CodeInfo} object as part of the
     * current garbage collection.
     */
    int STATE_READY_FOR_INVALIDATION = STATE_CODE_CONSTANTS_LIVE + 1;
    /**
     * Indicates that this {@link CodeInfo} object was invalidated and parts of its data were freed.
     */
    int STATE_PARTIALLY_FREED = STATE_READY_FOR_INVALIDATION + 1;
    /**
     * Indicates that a partially freed {@link CodeInfo} object is not reachable from the GC point
     * of view.
     */
    int STATE_UNREACHABLE = STATE_PARTIALLY_FREED + 1;
}
