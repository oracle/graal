/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

public class ReferenceMapIndex {
    /**
     * Marker value returned by
     * {@link com.oracle.svm.core.code.CodeInfoQueryResult#getReferenceMapIndex()} when the
     * reference map is empty for the {@link com.oracle.svm.core.code.CodeInfoQueryResult#getIP()
     * IP}.
     */
    public static final int EMPTY_REFERENCE_MAP = 0;

    /**
     * Marker value returned by
     * {@link com.oracle.svm.core.code.CodeInfoQueryResult#getReferenceMapIndex()} when no reference
     * map is registered for the {@link com.oracle.svm.core.code.CodeInfoQueryResult#getIP() IP}.
     */
    public static final int NO_REFERENCE_MAP = -1;

    /**
     * Reference map index value for {@link StoredContinuation} to indicate this instance needs
     * special treatment during allocation and GC.
     */
    public static final int STORED_CONTINUATION = -2;

    public static boolean denotesEmptyReferenceMap(long referenceMapIndex) {
        return referenceMapIndex == EMPTY_REFERENCE_MAP || referenceMapIndex == NO_REFERENCE_MAP;
    }

    public static boolean denotesValidReferenceMap(long referenceMapIndex) {
        return referenceMapIndex != NO_REFERENCE_MAP;
    }
}
