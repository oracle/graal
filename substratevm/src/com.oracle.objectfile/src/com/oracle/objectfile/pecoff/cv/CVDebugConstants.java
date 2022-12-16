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

public abstract class CVDebugConstants {

    static final int DEBUG_S_SYMBOLS = 0xf1;
    static final int DEBUG_S_LINES = 0xf2;
    static final int DEBUG_S_STRINGTABLE = 0xf3;
    static final int DEBUG_S_FILECHKSMS = 0xf4;

    /* Subcommands in DEBUG_S_SYMBOLS section. */
    static final short S_END = 0x0006;
    static final short S_FRAMEPROC = 0x1012;
    static final short S_OBJNAME = 0x1101;
    static final short S_BLOCK32 = 0x1103;
    static final short S_REGISTER = 0x1106; /* Register variable. */
    static final short S_CONSTANT = 0x1107; /* Constant. */
    static final short S_UDT = 0x1108; /* User defined type. */
    static final short S_LDATA32 = 0x110c; /* Local static. */
    static final short S_GDATA32 = 0x110d; /* Global static. */
    static final short S_GPROC32 = 0x1110; /* Global procedure. */
    static final short S_REGREL32 = 0x1111;
    static final short S_COMPILE3 = 0x113c;
    static final short S_ENVBLOCK = 0x113d;
    static final short S_LOCAL = 0x113e;
    static final short S_DEFRANGE = 0x113f;
    // static final short S_DEFRANGE_SUBFIELD = 0x1140;
    static final short S_DEFRANGE_REGISTER = 0x1141;
    static final short S_DEFRANGE_FRAMEPOINTER_REL = 0x1142;
    // static final short S_DEFRANGE_SUBFIELD_REGISTER = 0x1143;
    static final short S_DEFRANGE_FRAMEPOINTER_REL_FULL_SCOPE = 0x1144;
    static final short S_DEFRANGE_REGISTER_REL = 0x1145;
}
