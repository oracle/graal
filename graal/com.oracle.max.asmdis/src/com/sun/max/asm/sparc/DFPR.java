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
import com.sun.max.lang.*;
import com.sun.max.util.*;

/**
 * The double-precision (64-bit) floating-point registers.
 */
public interface DFPR extends SymbolicArgument, StaticFieldName {

    DFPR F0 = FPR.F0;
    DFPR F2 = FPR.F2;
    DFPR F4 = FPR.F4;
    DFPR F6 = FPR.F6;
    DFPR F8 = FPR.F8;
    DFPR F10 = FPR.F10;
    DFPR F12 = FPR.F12;
    DFPR F14 = FPR.F14;
    DFPR F16 = FPR.F16;
    DFPR F18 = FPR.F18;
    DFPR F20 = FPR.F20;
    DFPR F22 = FPR.F22;
    DFPR F24 = FPR.F24;
    DFPR F26 = FPR.F26;
    DFPR F28 = FPR.F28;
    DFPR F30 = FPR.F30;
    DFPR F32 = FPR.F32;
    DFPR F34 = FPR.F34;
    DFPR F36 = FPR.F36;
    DFPR F38 = FPR.F38;
    DFPR F40 = FPR.F40;
    DFPR F42 = FPR.F42;
    DFPR F44 = FPR.F44;
    DFPR F46 = FPR.F46;
    DFPR F48 = FPR.F48;
    DFPR F50 = FPR.F50;
    DFPR F52 = FPR.F52;
    DFPR F54 = FPR.F54;
    DFPR F56 = FPR.F56;
    DFPR F58 = FPR.F58;
    DFPR F60 = FPR.F60;
    DFPR F62 = FPR.F62;

    Symbolizer<DFPR> SYMBOLIZER = Symbolizer.Static.initialize(DFPR.class);
}
