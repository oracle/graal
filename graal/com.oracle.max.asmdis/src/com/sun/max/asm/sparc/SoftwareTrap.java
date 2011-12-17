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
package com.sun.max.asm.sparc;

/**
 * Software traps for SPARC's trap instruction.
 * See /usr/include/sys/trap.h on Solaris.
 */
public enum SoftwareTrap {
    ST_OSYSCALL(0),
    ST_BREAKPOINT(0x1),
    ST_DIV0(0x2),
    ST_FLUSH_WINDOWS(0x3),
    ST_CLEAN_WINDOWS(0x4),
    ST_RANGE_CHECK(0x5),
    ST_FIX_ALIGN(0x6),
    ST_INT_OVERFLOW(0x7),
    ST_SYSCALL(0x8),

    ST_DTRACE_PID(0x38),
    ST_DTRACE_PROBE(0x39),
    ST_DTRACE_RETURN(0x3a);

    private final int trapNumber;
    private SoftwareTrap(int trapNumber) {
        this.trapNumber = trapNumber;
    }

    public int trapNumber() {
        return trapNumber;
    }
}
