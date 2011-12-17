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
 * The single-precision (32-bit) floating-point registers.
 */
public interface SFPR extends SymbolicArgument, StaticFieldName {
    SFPR F0 = FPR.F0;
    SFPR F1 = FPR.F1;
    SFPR F2 = FPR.F2;
    SFPR F3 = FPR.F3;
    SFPR F4 = FPR.F4;
    SFPR F5 = FPR.F5;
    SFPR F6 = FPR.F6;
    SFPR F7 = FPR.F7;
    SFPR F8 = FPR.F8;
    SFPR F9 = FPR.F9;
    SFPR F10 = FPR.F10;
    SFPR F11 = FPR.F11;
    SFPR F12 = FPR.F12;
    SFPR F13 = FPR.F13;
    SFPR F14 = FPR.F14;
    SFPR F15 = FPR.F15;
    SFPR F16 = FPR.F16;
    SFPR F17 = FPR.F17;
    SFPR F18 = FPR.F18;
    SFPR F19 = FPR.F19;
    SFPR F20 = FPR.F20;
    SFPR F21 = FPR.F21;
    SFPR F22 = FPR.F22;
    SFPR F23 = FPR.F23;
    SFPR F24 = FPR.F24;
    SFPR F25 = FPR.F25;
    SFPR F26 = FPR.F26;
    SFPR F27 = FPR.F27;
    SFPR F28 = FPR.F28;
    SFPR F29 = FPR.F29;
    SFPR F30 = FPR.F30;
    SFPR F31 = FPR.F31;

    Symbolizer<SFPR> SYMBOLIZER = Symbolizer.Static.initialize(SFPR.class);
}
