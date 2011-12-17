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
package com.sun.max.asm.arm;

import com.sun.max.asm.*;
import com.sun.max.util.*;

/**
 * The condition codes that can be used for the 4-bit condition field in every ARM instruction.
 */
public final class ConditionCode extends OptionSuffixSymbolicArgument {

    private ConditionCode(int value, String externalMnemonicSuffix) {
        super(value, externalMnemonicSuffix);
    }

    public static final ConditionCode EQ = new ConditionCode(0, "eq");
    public static final ConditionCode NE = new ConditionCode(1, "ne");
    public static final ConditionCode CS = new ConditionCode(2, "cs");
    public static final ConditionCode HS = new ConditionCode(2, "hs");
    public static final ConditionCode CC = new ConditionCode(3, "cc");
    public static final ConditionCode LO = new ConditionCode(3, "lo");
    public static final ConditionCode MI = new ConditionCode(4, "mi");
    public static final ConditionCode PL = new ConditionCode(5, "pl");
    public static final ConditionCode VS = new ConditionCode(6, "vs");
    public static final ConditionCode VC = new ConditionCode(7, "vc");
    public static final ConditionCode HI = new ConditionCode(8, "hi");
    public static final ConditionCode LS = new ConditionCode(9, "ls");
    public static final ConditionCode GE = new ConditionCode(10, "ge");
    public static final ConditionCode LT = new ConditionCode(11, "lt");
    public static final ConditionCode GT = new ConditionCode(12, "gt");
    public static final ConditionCode LE = new ConditionCode(13, "le");
    public static final ConditionCode AL = new ConditionCode(14, "al");
    public static final ConditionCode NO_COND = new ConditionCode(14, "");
    public static final ConditionCode NV = new ConditionCode(15, "nv");

    public static final Symbolizer<ConditionCode> SYMBOLIZER = Symbolizer.Static.initialize(ConditionCode.class);
}
