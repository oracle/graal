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
 * The class defining the symbolic identifiers for the general purpose registers.
 */
public class GPR extends AbstractSymbolicArgument {

    protected GPR(String name, int value) {
        super(name, value);
    }

    public enum GPRSet {
        GLOBALS, OUT, LOCAL, IN;
    }

    private int registerSetOrdinal() {
        return value() >> 3;
    }

    public GPRSet registerSet() {
        return GPRSet.values()[registerSetOrdinal()];
    }

    public boolean isGlobal() {
        return registerSetOrdinal() == GPRSet.GLOBALS.ordinal();
    }

    public boolean isLocal() {
        return registerSetOrdinal() == GPRSet.LOCAL.ordinal();
    }

    public boolean isOut() {
        return registerSetOrdinal() == GPRSet.OUT.ordinal();
    }

    public boolean isIn() {
        return registerSetOrdinal() == GPRSet.IN.ordinal();
    }

    public static final class Even extends GPR {
        private Even(String name, int value) {
            super(name, value);
        }
    }

    public static final Even G0 = new Even("G0", 0);
    public static final  GPR G1 = new  GPR("G1", 1);
    public static final Even G2 = new Even("G2", 2);
    public static final  GPR G3 = new  GPR("G3", 3);
    public static final Even G4 = new Even("G4", 4);
    public static final  GPR G5 = new  GPR("G5", 5);
    public static final Even G6 = new Even("G6", 6);
    public static final  GPR G7 = new  GPR("G7", 7);
    public static final Even O0 = new Even("O0", 8);
    public static final  GPR O1 = new  GPR("O1", 9);
    public static final Even O2 = new Even("O2", 10);
    public static final  GPR O3 = new  GPR("O3", 11);
    public static final Even O4 = new Even("O4", 12);
    public static final  GPR O5 = new  GPR("O5", 13);
    public static final Even O6 = new Even("O6", 14);
    public static final  GPR O7 = new  GPR("O7", 15);
    public static final Even L0 = new Even("L0", 16);
    public static final  GPR L1 = new  GPR("L1", 17);
    public static final Even L2 = new Even("L2", 18);
    public static final  GPR L3 = new  GPR("L3", 19);
    public static final Even L4 = new Even("L4", 20);
    public static final  GPR L5 = new  GPR("L5", 21);
    public static final Even L6 = new Even("L6", 22);
    public static final  GPR L7 = new  GPR("L7", 23);
    public static final Even I0 = new Even("I0", 24);
    public static final  GPR I1 = new  GPR("I1", 25);
    public static final Even I2 = new Even("I2", 26);
    public static final  GPR I3 = new  GPR("I3", 27);
    public static final Even I4 = new Even("I4", 28);
    public static final  GPR I5 = new  GPR("I5", 29);
    public static final Even I6 = new Even("I6", 30);
    public static final  GPR I7 = new  GPR("I7", 31);

    public static final Symbolizer<GPR> SYMBOLIZER = Symbolizer.Static.initialize(GPR.class);

    public static final Symbolizer<Even> EVEN_SYMBOLIZER = Symbolizer.Static.initialize(GPR.class, Even.class);

    public static final Symbolizer<GPR> GLOBAL_SYMBOLIZER = Symbolizer.Static.fromSymbolizer(SYMBOLIZER, new com.sun.max.util.Predicate<GPR>() {
        public boolean evaluate(GPR register) {
            return register.isGlobal();
        }
    });

    public static final Symbolizer<GPR> IN_SYMBOLIZER = Symbolizer.Static.fromSymbolizer(SYMBOLIZER, new com.sun.max.util.Predicate<GPR>() {
        public boolean evaluate(GPR register) {
            return register.isIn();
        }
    });
}
