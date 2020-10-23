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
    static final short S_OBJNAME = 0x1101;
    static final short S_FRAMEPROC = 0x1012;
    static final short S_GPROC32 = 0x1110;
    static final short S_COMPILE3 = 0x113c;
    static final short S_ENVBLOCK = 0x113d;
}
