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

import com.sun.max.lang.*;
import com.sun.max.util.*;

/**
 * The quad-precision (128-bit) floating-point registers.
 */
public interface QFPR extends DFPR, StaticFieldName {
    QFPR F0 = FPR.F0;
    QFPR F4 = FPR.F4;
    QFPR F8 = FPR.F8;
    QFPR F12 = FPR.F12;
    QFPR F16 = FPR.F16;
    QFPR F20 = FPR.F20;
    QFPR F24 = FPR.F24;
    QFPR F28 = FPR.F28;
    QFPR F32 = FPR.F32;
    QFPR F36 = FPR.F36;
    QFPR F40 = FPR.F40;
    QFPR F44 = FPR.F44;
    QFPR F48 = FPR.F48;
    QFPR F52 = FPR.F52;
    QFPR F56 = FPR.F56;
    QFPR F60 = FPR.F60;

    Symbolizer<QFPR> SYMBOLIZER = Symbolizer.Static.initialize(QFPR.class);
}
