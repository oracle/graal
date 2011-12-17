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

import com.sun.max.asm.*;
import com.sun.max.util.*;

/**
 * The class defining the symbolic identifiers for the privileged registers
 * accessed by the Read Privileged Register and Write Privileged Register
 * instructions.
 */
public class PrivilegedRegister extends AbstractSymbolicArgument {

    PrivilegedRegister(int value) {
        super(value);
    }

    public static class Writable extends PrivilegedRegister {
        Writable(int value) {
            super(value);
        }
    }

    public static final Writable TPC = new Writable(0);
    public static final Writable TNPC = new Writable(1);
    public static final Writable TSTATE = new Writable(2);
    public static final Writable TT = new Writable(3);
    public static final Writable TTICK = new Writable(4) {
        @Override
        public String externalValue() {
            return "%tick";
        }
    };
    public static final Writable TBA = new Writable(5);
    public static final Writable PSTATE = new Writable(6);
    public static final Writable TL = new Writable(7);
    public static final Writable PIL = new Writable(8);
    public static final Writable CWP = new Writable(9);
    public static final Writable CANSAVE = new Writable(10);
    public static final Writable CANRESTORE = new Writable(11);
    public static final Writable CLEANWIN = new Writable(12);
    public static final Writable OTHERWIN = new Writable(13);
    public static final Writable WSTATE = new Writable(14);
    public static final PrivilegedRegister FQ = new PrivilegedRegister(15);
    public static final PrivilegedRegister VER = new PrivilegedRegister(31);

    public static final Symbolizer<PrivilegedRegister> SYMBOLIZER = Symbolizer.Static.initialize(PrivilegedRegister.class);
    public static final Symbolizer<Writable> WRITE_ONLY_SYMBOLIZER = Symbolizer.Static.initialize(PrivilegedRegister.class, Writable.class);
}
