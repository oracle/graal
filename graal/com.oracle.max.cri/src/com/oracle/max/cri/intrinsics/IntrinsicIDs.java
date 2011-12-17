/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.cri.intrinsics;

/**
 * Definition of ID strings for general-purpose intrinsics.
 */
public class IntrinsicIDs {
    /**
     * Prefix of ID strings defined in this class to make them unique.
     */
    private static final String p = "com.oracle.max.cri.intrinsics:";

    /**
     * Unsigned comparison aboveThan for two numbers.
     * The method definition must have the following form:
     * <pre>
     * static boolean m(T a, T b)
     * where T is one of {int, long}
     * </pre>
     */
    public static final String UCMP_AT = p + "UCMP_AT";

    /**
     * Unsigned comparison aboveOrEqual for two numbers.
     * The method definition must have the following form:
     * <pre>
     * static boolean m(T a, T b)
     * where T is one of {int, long}
     * </pre>
     */
    public static final String UCMP_AE = p + "UCMP_AE";

    /**
     * Unsigned comparison belowThan for two numbers.
     * The method definition must have the following form:
     * <pre>
     * static boolean m(T a, T b)
     * where T is one of {int, long}
     * </pre>
     */
    public static final String UCMP_BT = p + "UCMP_BT";

    /**
     * Unsigned comparison belowOrEqual for two numbers.
     * The method definition must have the following form:
     * <pre>
     * static boolean m(T a, T b)
     * where T is one of {int, long}
     * </pre>
     */
    public static final String UCMP_BE = p + "UCMP_BE";

    /**
     * Unsigned division for two numbers.
     * The method definition must have the following form:
     * <pre>
     * static T m(T a, T b)
     * where T is one of {int, long}
     * </pre>
     */
    public static final String UDIV = p + "UDIV";

    /**
     * Unsigned remainder for two numbers.
     * The method definition must have the following form:
     * <pre>
     * static T m(T a, T b)
     * where T is one of {int, long}
     * </pre>
     */
    public static final String UREM = p + "UREM";

    /**
     * Intrinsic that emits the specified memory barriers.
     * The method definition must have the following form:
     * <pre>
     * static void barrier(@INTRINSIC.Constant int barrierSpec);
     * barrierSpec: A combination of the flags {@link MemoryBarriers#LOAD_LOAD}, {@link MemoryBarriers#LOAD_STORE},
     *     {@link MemoryBarriers#STORE_LOAD}, {@link MemoryBarriers#STORE_STORE}.
     *     This parameter must be a compile-time constant.
     * </pre>
     */
    public static final String MEMBAR = p + "MEMBAR";
}
