/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.pecoff.cv;

abstract class CVTypeConstants {

    /* type table */
    static final short T_NOTYPE = 0x0000;
    static final short T_VOID = 0x0003;
    // static final short T_CHAR = 0x0010; /* 8 bit signed (java type) */
    // static final short T_WCHAR = 0x0071;
    // static final short T_CHAR16 = 0x007a; /* 16 bit unicode (Java type) */
    // static final short T_SHORT = 0x0011; /* 16 bit signed short (Java type) */
    // static final short T_LONG = 0x0014; /* 32 bit signed (java type? maybe T_short4?) */
    static final short T_QUAD = 0x0013; /* 64 bit signed long long (Java type) */
    // static final short T_REAL32 = 0x0040; /* 32 bit float (Java type) */
    // static final short T_REAL64 = 0x0041; /* 64 but double (Java type) */
    // static final short T_RCHAR = 0x0070; /* ?? "really a char" */

    // static final short T_INT4 = T_LONG; /* ?? is tis right */
    static final short T_UQUAD = T_QUAD; /* ?? */

    // static final short T_POINTER_BITS = 0x0700;
    // static final short T_POINTER32 = 0x0400; /* 32 bit pointer */
    // static final short T_POINTER64 = 0x0600; /* 64 bit pointer */

    static final short LF_MODIFIER = 0x1001;
    static final short LF_POINTER = 0x1002;
    static final short LF_PROCEDURE = 0x1008;
    static final short LF_ARGLIST = 0x1201;
    // static final short LF_FIELDLIST = 0x1203;
    static final short LF_BITFIELD = 0x1205;
    static final short LF_BCLASS = 0x1400;
    static final short LF_ARRAY = 0x1503;
    static final short LF_CLASS = 0x1504;
    static final short LF_STRUCTURE = 0x1505;
    // static final short LF_UNION = 0x1506;
    // static final short LF_ENUM = 0x1507;
    static final short LF_MEMBER = 0x150d;
    static final short LF_TYPESERVER2 = 0x1515;
    static final short LF_INTERFACE = 0x1519;
    static final short LF_BINTERFACE = 0x151a;

    /*-
    static final short LF_CHAR        = (short) 0x8000;
    static final short LF_SHORT       = (short) 0x8001;
    static final short LF_USHORT      = (short) 0x8002;
    static final short LF_LONG        = (short) 0x8003;
    static final short LF_ULONG       = (short) 0x8004;
    static final short LF_REAL32      = (short) 0x8005;
    static final short LF_REAL64      = (short) 0x8006;
    static final short LF_QUADWORD    = (short) 0x8009;
    static final short LF_UQUADWORD   = (short) 0x800a;
    static final short LF_OCTWORD     = (short) 0x8017;
    static final short LF_UOCTWORD    = (short) 0x8018;
    */

    // static final byte LF_PAD0 = (byte) 0xf0;
    static final byte LF_PAD1 = (byte) 0xf1;
    static final byte LF_PAD2 = (byte) 0xf2;
    static final byte LF_PAD3 = (byte) 0xf3;
    /*-
    static final byte LF_PAD4  = (byte) 0xf4;
    static final byte LF_PAD5  = (byte) 0xf5;
    static final byte LF_PAD6  = (byte) 0xf6;
    static final byte LF_PAD7  = (byte) 0xf7;
    static final byte LF_PAD8  = (byte) 0xf8;
    static final byte LF_PAD9  = (byte) 0xf9;
    static final byte LF_PAD10 = (byte) 0xfa;
    static final byte LF_PAD11 = (byte) 0xfb;
    static final byte LF_PAD12 = (byte) 0xfc;
    static final byte LF_PAD13 = (byte) 0xfd;
    static final byte LF_PAD14 = (byte) 0xfe;
    static final byte LF_PAD15 = (byte) 0xff;
    */
}
