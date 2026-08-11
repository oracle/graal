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
package com.oracle.svm.core.hub;

/**
 * Compiler intrinsic declarations that depend on the core-owned {@link DynamicHub} type.
 *
 * This declaration remains in core because {@link DynamicHub} is not yet guest-visible. GR-78110
 * tracks moving {@link DynamicHub} to guest staging; once that is complete, {@link #readHub(Object)}
 * can move to the guest-staging {@code KnownIntrinsics} declaration and this class can be removed.
 */
public final class DynamicHubIntrinsics {
    /** Prevents instantiation. */
    private DynamicHubIntrinsics() {
    }

    /**
     * Returns the hub of the given object.
     */
    public static native DynamicHub readHub(Object obj);
}
