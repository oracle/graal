/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.bridge;

public enum IdTag {
    ARBITRARY_REFERENCE,
    INITIAL_IMAGE_HEAP_REFERENCE,
    THREAD,
    TYPE,
    METHOD,
    FIELD;

    // No valid ID should be equal to this e.g. type tags cannot be == TAG_MASK.
    // Tag and index of this id are invalid.
    public static final long INVALID_ID = -1L;

    private static final int TAG_BITS;
    private static final int TAG_MASK;
    private static final IdTag[] VALUES = IdTag.values();

    static {
        int size = VALUES.length;
        int bits = 0;
        while (size > 0) {
            size >>= 1;
            bits++;
        }
        TAG_BITS = bits;
        TAG_MASK = (1 << TAG_BITS) - 1;
    }

    public long toTaggedId(long index) {
        assert index >= 0;
        assert index == ((index << TAG_BITS) >>> TAG_BITS) : "index information lost";
        return index << TAG_BITS | this.ordinal();
    }

    public static long fromTaggedId(long id) {
        long index = id >> TAG_BITS;
        assert index >= 0;
        return index;
    }

    public static IdTag getTag(long id) {
        return VALUES[(int) (id & TAG_MASK)];
    }

}
