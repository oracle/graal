/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.target.sparc;

import static com.sun.cri.bytecode.Bytecodes.MemoryBarriers.*;
import static com.sun.cri.ci.CiRegister.RegisterFlag.*;

import com.sun.cri.ci.*;

/**
 * Represents the SPARC architecture.
 *
 * @author Thomas Wuerthinger
 */
public class SPARC extends CiArchitecture {

    // General purpose CPU registers
    public static final CiRegister g0 = new CiRegister(0,  0,  8, "g0", CPU);
    public static final CiRegister g1 = new CiRegister(1,  1,  8, "g1", CPU);
    public static final CiRegister g2 = new CiRegister(2,  2,  8, "g2", CPU);
    public static final CiRegister g3 = new CiRegister(3,  3,  8, "g3", CPU);
    public static final CiRegister g4 = new CiRegister(4,  4,  8, "g4", CPU);
    public static final CiRegister g5 = new CiRegister(5,  5,  8, "g5", CPU);
    public static final CiRegister g6 = new CiRegister(6,  6,  8, "g6", CPU);
    public static final CiRegister g7 = new CiRegister(7,  7,  8, "g7", CPU);

    public static final CiRegister o0 = new CiRegister(8,  8,  8, "o0", CPU);
    public static final CiRegister o1 = new CiRegister(9,  9,  8, "o1", CPU);
    public static final CiRegister o2 = new CiRegister(10, 10, 8, "o2", CPU);
    public static final CiRegister o3 = new CiRegister(11, 11, 8, "o3", CPU);
    public static final CiRegister o4 = new CiRegister(12, 12, 8, "o4", CPU);
    public static final CiRegister o5 = new CiRegister(13, 13, 8, "o5", CPU);
    public static final CiRegister o6 = new CiRegister(14, 14, 8, "o6", CPU);
    public static final CiRegister o7 = new CiRegister(15, 15, 8, "o7", CPU);

    public static final CiRegister l0 = new CiRegister(16, 16, 8, "l0", CPU);
    public static final CiRegister l1 = new CiRegister(17, 17, 8, "l1", CPU);
    public static final CiRegister l2 = new CiRegister(18, 18, 8, "l2", CPU);
    public static final CiRegister l3 = new CiRegister(19, 19, 8, "l3", CPU);
    public static final CiRegister l4 = new CiRegister(20, 20, 8, "l4", CPU);
    public static final CiRegister l5 = new CiRegister(21, 21, 8, "l5", CPU);
    public static final CiRegister l6 = new CiRegister(22, 22, 8, "l6", CPU);
    public static final CiRegister l7 = new CiRegister(23, 23, 8, "l7", CPU);

    public static final CiRegister i0 = new CiRegister(24, 24, 8, "i0", CPU);
    public static final CiRegister i1 = new CiRegister(25, 25, 8, "i1", CPU);
    public static final CiRegister i2 = new CiRegister(26, 26, 8, "i2", CPU);
    public static final CiRegister i3 = new CiRegister(27, 27, 8, "i3", CPU);
    public static final CiRegister i4 = new CiRegister(28, 28, 8, "i4", CPU);
    public static final CiRegister i5 = new CiRegister(29, 29, 8, "i5", CPU);
    public static final CiRegister i6 = new CiRegister(30, 30, 8, "i6", CPU);
    public static final CiRegister i7 = new CiRegister(31, 31, 8, "i7", CPU);

    public static final CiRegister[] cpuRegisters = {
        g0, g1, g2, g3, g4, g5, g6, g7,
        o0, o1, o2, o3, o4, o5, o6, o7,
        l0, l1, l2, l3, l4, l5, l6, l7,
        i0, i1, i2, i3, i4, i5, i6, i7
    };

    protected SPARC(int wordSize, CiRegister[] registers) {
        super("SPARC",
              wordSize,
              ByteOrder.BigEndian,
              registers,
              LOAD_LOAD | LOAD_STORE | STORE_STORE,
              0,
              i7.encoding + 1,
              0);
    }

    @Override
    public boolean isSPARC() {
        return true;
    }

}
