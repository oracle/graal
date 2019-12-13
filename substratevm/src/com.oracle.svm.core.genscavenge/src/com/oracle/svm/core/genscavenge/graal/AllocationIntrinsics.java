/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.graal;

import org.graalvm.word.Pointer;

/**
 * Functions that are implemented as compiler intrinsics. For the implementation see
 * {@link HeapFeature}.
 */
public class AllocationIntrinsics {
    /**
     * Installs the object header and zeros the rest of the object.
     */
    public static native Object formatObject(Pointer memory, Class<?> hub, boolean rememberedSet, boolean fillContents, boolean emitMemoryBarrier);

    /**
     * Installs the array header and zeros the rest of the object.
     */
    public static native Object formatArray(Pointer memory, Class<?> hub, int length, boolean rememberedSet, boolean unaligned, boolean fillContents, boolean emitMemoryBarrier);
}
