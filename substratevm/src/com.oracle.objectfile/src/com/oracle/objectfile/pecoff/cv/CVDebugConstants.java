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

public interface CVDebugConstants {

    //int DEBUG_S_IGNORE      = 0x00;
    int DEBUG_S_SYMBOLS     = 0xf1;
    int DEBUG_S_LINES       = 0xf2;
    int DEBUG_S_STRINGTABLE = 0xf3;
    int DEBUG_S_FILECHKSMS  = 0xf4;

    /* subcommands in DEBUG_S_SYMBOLS section */
    //short S_COMPILE   = 0x0001;
    short S_SSEARCH   = 0x0005;
    short S_END       = 0x0006;
    short S_OBJNAME   = 0x1101;
    short S_LDATA32_ST = 0x1007;
    short S_FRAMEPROC = 0x1012;
    short S_CONSTANT  = 0x1107;
    short S_UDT       = 0x1108;
    short S_LDATA32   = 0x110c;
    short S_GDATA32   = 0x110d;
    short S_GPROC32   = 0x1110;
    short S_REGREL32  = 0x1111;
    short S_COMPILE3  = 0x113c;
    short S_ENVBLOCK  = 0x113d;
    short S_GPROC32_ID  = 0x1147;
    short S_PROC_ID_END = 0x114f;
    //short S_BUILDINFO   = 0x114c;

    /* enums are more typesafe but the IDE no longer knows which enum constant is unused
    enum CV_RECORD {
        CV_SIGNATURE_C13(4),
        S_COMPILE(0x0001),
        S_SSEARCH(0x0005),
        S_END(0x0006),
        S_OBJNAME(0x1101),
        S_LDATA32_ST(0x1007),
        S_FRAMEPROC(0x1012),
        S_CONSTANT(0x1107),
        S_UDT(0x1108),
        S_LDATA32(0x110c),
        S_GDATA32(0x110d),
        S_GPROC32(0x1110),
        S_REGREL32(0x1111),
        S_COMPILE3(0x113c),
        S_ENVBLOCK(0x113d);

        final int cmd;

        CV_RECORD(int cmd) {
            this.cmd = cmd;
        }

        public int command() {
            return cmd;
        }
    }

    enum DEBUG_S {
        DEBUG_S_IGNORE(0x00),
        DEBUG_S_SYMBOLS(0xf1),
        DEBUG_S_LINES(0xf2),
        DEBUG_S_STRINGTABLE(0xf3),
        DEBUG_S_FILECHKSMS(0xf4);

        final short cmd;

        DEBUG_S(int cmd) {
            this.cmd = (short)cmd;
        }

        public short command() {
            return cmd;
        }
    } */
}
