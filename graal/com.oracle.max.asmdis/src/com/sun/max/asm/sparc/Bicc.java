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
 * The argument to a Branch on Integer Condition Code instruction specifying
 * the conditional test to be performed.
 */
public final class Bicc extends NameSuffixSymbolicArgument implements Predicate<ICCOperand, Bicc> {
    private Bicc negation;

    private Bicc(int value) {
        super(value);
    }

    private Bicc(int value, Bicc negation) {
        this(value);
        this.negation = negation;
        negation.negation = this;
    }

    public static final Bicc A = new Bicc(8);
    public static final Bicc N = new Bicc(0, A);
    public static final Bicc NE = new Bicc(9);
    public static final Bicc E = new Bicc(1, NE);
    public static final Bicc G = new Bicc(10);
    public static final Bicc LE = new Bicc(2, G);
    public static final Bicc GE = new Bicc(11);
    public static final Bicc L = new Bicc(3, GE);
    public static final Bicc GU = new Bicc(12);
    public static final Bicc LEU = new Bicc(4, GU);
    public static final Bicc CC = new Bicc(13);
    public static final Bicc CS = new Bicc(5, CC);
    public static final Bicc POS = new Bicc(14);
    public static final Bicc NEG = new Bicc(6, POS);
    public static final Bicc VC = new Bicc(15);
    public static final Bicc VS = new Bicc(7, VC);

    public static final Symbolizer<Bicc> SYMBOLIZER = Symbolizer.Static.initialize(Bicc.class);

    public Bicc negate() {
        return negation;
    }

}
