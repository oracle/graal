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

public interface CVTypeConstants {

    /* type table */
    short T_NOTYPE       = 0x0000;
    short T_VOID         = 0x0003;
    //short T_CHAR         = 0x0010; /* 8 bit signed (java type) */
    //short T_WCHAR        = 0x0071;
    //short T_CHAR16       = 0x007a; /* 16 bit unicode (Java type) */
    //short T_SHORT        = 0x0011; /* 16 bit signed short (Java type) */
    //short T_LONG         = 0x0014; /* 32 bit signed (java type? maybe T_short4?) */
    short T_QUAD         = 0x0013;   /* 64 bit signed long long (Java type) */
    //short T_REAL32       = 0x0040; /* 32 bit float (Java type) */
    //short T_REAL64       = 0x0041; /* 64 but double (Java type) */
    //short T_RCHAR        = 0x0070; /* ?? "really a char" */

    //short T_INT4 = T_LONG;    /* ?? is tis right */
    short T_UQUAD = T_QUAD;     /* ?? */

    //short T_POINTER_BITS  = 0x0700;
    //short T_POINTER32     = 0x0400; /* 32 bit pointer */
    //short T_POINTER64     = 0x0600; /* 64 bit pointer */

    short LF_MODIFIER    = 0x1001;
    short LF_POINTER     = 0x1002;
    short LF_PROCEDURE   = 0x1008;
    short LF_ARGLIST     = 0x1201;
    //short LF_FIELDLIST   = 0x1203;
    short LF_BITFIELD    = 0x1205;
    short LF_BCLASS      = 0x1400;
    short LF_ARRAY       = 0x1503;
    short LF_CLASS       = 0x1504;
    short LF_STRUCTURE   = 0x1505;
    //short LF_UNION       = 0x1506;
    //short LF_ENUM        = 0x1507;
    short LF_MEMBER      = 0x150d;
    short LF_TYPESERVER2 = 0x1515;
    short LF_INTERFACE   = 0x1519;
    short LF_BINTERFACE  = 0x151a;

/*
    short LF_CHAR        = (short) 0x8000;
    short LF_SHORT       = (short) 0x8001;
    short LF_USHORT      = (short) 0x8002;
    short LF_LONG        = (short) 0x8003;
    short LF_ULONG       = (short) 0x8004;
    short LF_REAL32      = (short) 0x8005;
    short LF_REAL64      = (short) 0x8006;
    short LF_QUADWORD    = (short) 0x8009;
    short LF_UQUADWORD   = (short) 0x800a;
    short LF_OCTWORD     = (short) 0x8017;
    short LF_UOCTWORD    = (short) 0x8018;
*/

    //byte LF_PAD0  = (byte) 0xf0;
    byte LF_PAD1  = (byte) 0xf1;
    byte LF_PAD2  = (byte) 0xf2;
    byte LF_PAD3  = (byte) 0xf3;
    /*byte LF_PAD4  = (byte) 0xf4;
    byte LF_PAD5  = (byte) 0xf5;
    byte LF_PAD6  = (byte) 0xf6;
    byte LF_PAD7  = (byte) 0xf7;
    byte LF_PAD8  = (byte) 0xf8;
    byte LF_PAD9  = (byte) 0xf9;
    byte LF_PAD10 = (byte) 0xfa;
    byte LF_PAD11 = (byte) 0xfb;
    byte LF_PAD12 = (byte) 0xfc;
    byte LF_PAD13 = (byte) 0xfd;
    byte LF_PAD14 = (byte) 0xfe;
    byte LF_PAD15 = (byte) 0xff;*/
}
