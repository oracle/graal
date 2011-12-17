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
 * The argument to the Write State Register and Read State Register instructions.
 */
public class StateRegister extends AbstractSymbolicArgument {

    StateRegister(int value) {
        super(value);
    }

    public static class Writable extends StateRegister {
        Writable(int value) {
            super(value);
        }
    }

    public static final class ASR extends Writable {
        private ASR(int value) {
            super(value);
        }
    }

    /**
     * @return true if this is the Y register or an Ancillary State register
     */
    public boolean isYorASR() {
        return this == Y || value() >= 16 && value() <= 31;
    }

    public static final Writable Y = new Writable(0);
    public static final Writable CCR = new Writable(2);
    public static final Writable ASI = new Writable(3);
    public static final StateRegister TICK = new StateRegister(4);
    public static final StateRegister PC = new StateRegister(5);
    public static final Writable FPRS = new Writable(6);
    public static final ASR ASR16 = new ASR(16);
    public static final ASR ASR17 = new ASR(17);
    public static final ASR ASR18 = new ASR(18);
    public static final ASR ASR19 = new ASR(19);
    public static final ASR ASR20 = new ASR(20);
    public static final ASR ASR21 = new ASR(21);
    public static final ASR ASR22 = new ASR(22);
    public static final ASR ASR23 = new ASR(23);
    public static final ASR ASR24 = new ASR(24);
    public static final ASR ASR25 = new ASR(25);
    public static final ASR ASR26 = new ASR(26);
    public static final ASR ASR27 = new ASR(27);
    public static final ASR ASR28 = new ASR(28);
    public static final ASR ASR29 = new ASR(29);
    public static final ASR ASR30 = new ASR(30);
    public static final ASR ASR31 = new ASR(31);

    public static final Symbolizer<StateRegister> SYMBOLIZER = Symbolizer.Static.initialize(StateRegister.class);
    public static final Symbolizer<Writable> WRITE_ONLY_SYMBOLIZER = Symbolizer.Static.initialize(StateRegister.class, Writable.class);
}
