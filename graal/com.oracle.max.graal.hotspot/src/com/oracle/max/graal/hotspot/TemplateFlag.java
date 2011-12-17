/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.max.graal.hotspot;

enum TemplateFlag {
    NULL_CHECK, READ_BARRIER, WRITE_BARRIER, STORE_CHECK, BOUNDS_CHECK, GIVEN_LENGTH, INPUTS_DIFFERENT, INPUTS_SAME, STATIC_METHOD, SYNCHRONIZED;

    private static final long FIRST_FLAG = 0x0000000100000000L;
    public static final long FLAGS_MASK = 0x0000FFFF00000000L;
    public static final long INDEX_MASK = 0x00000000FFFFFFFFL;

    public long bits() {
        assert ((FIRST_FLAG << ordinal()) & FLAGS_MASK) != 0;
        return FIRST_FLAG << ordinal();
    }
}
