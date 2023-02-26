/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

@SuppressWarnings("unused")
abstract class CVTypeConstants {

    static final int ADDRESS_BITS = 64;

    static final String JAVA_LANG_OBJECT_NAME = "java.lang.Object";
    static final String OBJ_HEADER_NAME = "_objhdr";

    static final int FUNC_IS_CONSTRUCTOR = 2;

    /*
     * Type table. Constants below 0x1000 are 'hardcoded', above are new type entries in the type
     * section.
     */
    static final short T_NOTYPE = 0x0000;
    static final short T_VOID = 0x0003;

    static final short T_BOOL08 = 0x0030; /* 8 bit boolean */
    static final short T_64PBOOL08 = 0x0630; /* 64 bit pointer to 8 bit bool */

    static final short T_WCHAR = 0x0071; /* 16 bite wide character (java char type) */
    static final short T_REAL32 = 0x0040; /* 32 bit float (Java float type) */
    static final short T_REAL64 = 0x0041; /* 64 bit double (Java double type) */

    static final short T_INT1 = 0x0068; /* 8 bit int (java byte type) */
    static final short T_INT2 = 0x0072; /* 16 bit int (java short type) */
    static final short T_INT4 = 0x0074; /* 32 bit int (java int type) */
    static final short T_INT8 = 0x0076; /* 64 bit int (java long type) */

    static final short T_64PINT1 = 0x0668; /* 64 bit pointer to 8 bit int (java byte type) */
    static final short T_64PINT2 = 0x0672; /* 64 bit pointer to 16 bit int (java short type) */
    static final short T_64PINT4 = 0x0674; /* 64 bit pointer to 32 bit int (java int type) */
    static final short T_64PINT8 = 0x0676; /* 64 bit pointer to 64 bit int (java long type) */

    static final short T_UINT1 = 0x0069; /* 8 bit unsigned int */
    static final short T_UINT2 = 0x0073; /* 16 bit unsigned int */
    static final short T_UINT4 = 0x0075; /* 32 bit unsigned int */
    static final short T_UINT8 = 0x0077; /* 64 bit unsigned int */

    static final short T_64PVOID = 0x0603;
    static final short T_64PCHAR = 0x0610;
    static final short T_64PUCHAR = 0x0620;
    static final short T_64PWCHAR = 0x0671;
    static final short T_64PUINT1 = 0x0669;
    static final short T_64PREAL32 = 0x0640;
    static final short T_64PREAL64 = 0x0641;

    static final short T_POINTER_BITS = 0x0700;
    static final short T_POINTER32 = 0x0400; /* 32 bit pointer */
    static final short T_POINTER64 = 0x0600; /* 64 bit pointer */

    static final short MAX_PRIMITIVE = 0x0fff;

    static final short LF_MODIFIER = 0x1001;
    static final short LF_POINTER = 0x1002;
    static final short LF_PROCEDURE = 0x1008;
    static final short LF_MFUNCTION = 0x1009;
    static final short LF_ARGLIST = 0x1201;
    static final short LF_FIELDLIST = 0x1203;
    static final short LF_BITFIELD = 0x1205;
    static final short LF_METHODLIST = 0x1206;
    static final short LF_BCLASS = 0x1400;
    static final short LF_INDEX = 0x1404;
    static final short LF_ENUMERATE = 0x1502;
    static final short LF_ARRAY = 0x1503;
    static final short LF_CLASS = 0x1504;
    static final short LF_STRUCTURE = 0x1505;
    static final short LF_UNION = 0x1506;
    static final short LF_ENUM = 0x1507;
    static final short LF_MEMBER = 0x150d;
    static final short LF_STMEMBER = 0x150e;
    static final short LF_METHOD = 0x150f;
    static final short LF_NESTTYPE = 0x1510;
    static final short LF_ONEMETHOD = 0x1511;
    static final short LF_TYPESERVER2 = 0x1515;
    static final short LF_INTERFACE = 0x1519;
    static final short LF_BINTERFACE = 0x151a;

    static final short LF_FUNC_ID = 0x1601;
    static final short LF_MFUNC_ID = 0x1602;
    static final short LF_BUILDINFO = 0x1603;
    static final short LF_STRING_ID = 0x1605;
    static final short LF_UDT_SRC_LINE = 0x1606;
    static final short LF_UDT_MOD_SRC_LINE = 0x1607;
    static final short LF_ID_LAST = 0x1608;

    /* LF_NUMERIC constanta */
    static final short LF_NUMERIC = (short) 0x8000;
    static final short LF_CHAR = (short) 0x8000;
    static final short LF_SHORT = (short) 0x8001;
    static final short LF_USHORT = (short) 0x8002;
    static final short LF_LONG = (short) 0x8003;
    static final short LF_ULONG = (short) 0x8004;
    static final short LF_REAL32 = (short) 0x8005;
    static final short LF_REAL64 = (short) 0x8006;
    static final short LF_REAL80 = (short) 0x8007;
    static final short LF_REAL128 = (short) 0x8008;
    static final short LF_QUADWORD = (short) 0x8009;
    static final short LF_UQUADWORD = (short) 0x800a;
    static final short LF_REAL48 = (short) 0x800b;
    static final short LF_COMPLEX32 = (short) 0x800c;
    static final short LF_COMPLEX64 = (short) 0x800d;
    static final short LF_COMPLEX80 = (short) 0x800e;
    static final short LF_COMPLEX128 = (short) 0x800f;
    static final short LF_VARSTRING = (short) 0x8010;
    static final short LF_OCTWORD = (short) 0x8017;
    static final short LF_UOCTWORD = (short) 0x8018;

    static final short LF_DECIMAL = (short) 0x8019;
    static final short LF_DATE = (short) 0x801a;
    static final short LF_UTF8STRING = (short) 0x801b;

    static final short LF_REAL16 = (short) 0x801c;

    /* Padding. */
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

    static final short MPROP_VANILLA = 0;
    static final short MPROP_PRIVATE = 1;
    static final short MPROP_PROTECTED = 2;
    static final short MPROP_PUBLIC = 3;
    static final short MPROP_PPP_MASK = 0x03;

    static final short MPROP_VIRTUAL = (1 << 2);  // redefinition
    static final short MPROP_STATIC = (2 << 2);
    static final short MPROP_FRIEND = (3 << 2);
    static final short MPROP_IVIRTUAL = (4 << 2);
    static final short MPROP_PURE_VIRTUAL = (5 << 2);
    static final short MPROP_PURE_IVIRTUAL = (6 << 2);
    static final short MPROP_VSF_MASK = 0x1c;

    static final short MPROP_PSEUDO = 0x20;
    static final short MPROP_FINAL_CLASS = 0x40;
    static final short MPROP_ABSTRACT = 0x80;
    static final short MPROP_COMPGENX = 0x100;
    static final short MPROP_FINAL_METHOD = 0x200;

    /* For x86_64, calling type is always CV_CALL_NEAR_C. */
    static final int CV_CALL_NEAR_C = 0;
}
