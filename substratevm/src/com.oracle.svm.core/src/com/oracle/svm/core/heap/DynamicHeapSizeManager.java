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
package com.oracle.svm.core.heap;

import static com.oracle.svm.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;

import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateGCOptions;

/**
 * This interface defines the functions needed to implement a dynamic heap size manager, where the
 * Java heap size can be changed at runtime.
 */
public interface DynamicHeapSizeManager {
    /**
     * Retrieves the current max heap size, overrides {@link SubstrateGCOptions#MaxHeapSize}.
     *
     * @return max heap size
     */
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Called from allocation-free code paths.")
    UnsignedWord maxHeapSize();

    /**
     * In case of OOM, try to request additional memory.
     *
     * @param bytes memory required
     * @return true if out of memory, false if max heap size was increased
     */
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Called from allocation-free code paths.")
    boolean outOfMemory(UnsignedWord bytes);
}
