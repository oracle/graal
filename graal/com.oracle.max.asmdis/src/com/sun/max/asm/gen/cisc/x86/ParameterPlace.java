/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.gen.cisc.x86;

/**
 * The place/field into which the argument value passed to a parameter is to be assembled.
 */
public enum ParameterPlace {

    MOD_REG("reg field of the modR/M byte"),
    MOD_REG_REXR("mod field extension by REX.R bit"),
    MOD_RM("rm field of the modR/M byte"),
    MOD_RM_REXB("rm field extension by REX.B bit"),
    SIB_SCALE("scale field of the SIB byte"),
    SIB_INDEX("index field of the SIB byte"),
    SIB_INDEX_REXX("SIB index field extension by REX.X bit"),
    SIB_BASE("base field of the SIB byte"),
    SIB_BASE_REXB("SIB base field extension by REX.B bit"),
    APPEND("appended to the instruction"),
    OPCODE1("added to the first opcode"),
    OPCODE1_REXB("opcode1 extension by REX.B bit"),
    OPCODE2("added to the second opcode"),
    OPCODE2_REXB("opcode2 extension by REX.B bit");

    private final String comment;

    private ParameterPlace(String comment) {
        this.comment = comment;
    }

    public String comment() {
        return comment;
    }
}
