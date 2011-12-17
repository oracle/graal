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
 * The argument to a Branch on Floating-Point Condition Code instruction specifying
 * the conditional test to be performed.
 */
public final class FBfcc extends NameSuffixSymbolicArgument implements Predicate<FCCOperand, FBfcc> {
    private FBfcc negation;

    private FBfcc(int value) {
        super(value);
    }
    private FBfcc(int value, FBfcc negation) {
        this(value);
        this.negation = negation;
        negation.negation = this;
    }

    public static final FBfcc A = new FBfcc(8);
    public static final FBfcc N = new FBfcc(0, A);
    public static final FBfcc U = new FBfcc(7);
    public static final FBfcc G = new FBfcc(6);
    public static final FBfcc UG = new FBfcc(5);
    public static final FBfcc L = new FBfcc(4);
    public static final FBfcc UL = new FBfcc(3);
    public static final FBfcc LG = new FBfcc(2);
    public static final FBfcc NE = new FBfcc(1);
    public static final FBfcc E = new FBfcc(9, NE);
    public static final FBfcc UE = new FBfcc(10, LG);
    public static final FBfcc GE = new FBfcc(11, UL);
    public static final FBfcc UGE = new FBfcc(12, L);
    public static final FBfcc LE = new FBfcc(13, UG);
    public static final FBfcc ULE = new FBfcc(14, G);
    public static final FBfcc O = new FBfcc(15, U);

    public static final Symbolizer<FBfcc> SYMBOLIZER = Symbolizer.Static.initialize(FBfcc.class);
    public FBfcc negate() { return negation; }
}
