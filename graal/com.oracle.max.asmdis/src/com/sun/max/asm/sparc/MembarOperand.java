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

import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.util.*;

/**
 * The components of the argument to the Memory Barrier (i.e. {@code membar}) instruction.
 */
public final class MembarOperand extends AbstractSymbolicArgument {

    private String externalName;

    private MembarOperand(String name, String externalName, int value) {
        super(name, value);
        this.externalName = externalName;
    }

    private MembarOperand(MembarOperand addend1, MembarOperand addend2) {
        super(addend1.name() + "_" + addend2.name(), addend1.value() | addend2.value());
        externalName = addend1.externalName + " | " + addend2.externalName;
    }

    @Override
    public String externalValue() {
        return externalName;
    }

    @Override
    public String toString() {
        return externalValue();
    }

    public MembarOperand or(MembarOperand other) {
        return new MembarOperand(this, other);
    }

    public static final MembarOperand NO_MEMBAR = new MembarOperand("None", "0", 0);
    public static final MembarOperand LOAD_LOAD = new MembarOperand("LoadLoad", "#LoadLoad", 1);
    public static final MembarOperand STORE_LOAD = new MembarOperand("StoreLoad", "#StoreLoad", 2);
    public static final MembarOperand LOAD_STORE = new MembarOperand("LoadStore", "#LoadStore", 4);
    public static final MembarOperand STORE_STORE = new MembarOperand("StoreStore", "#StoreStore", 8);
    public static final MembarOperand LOOKASIDE = new MembarOperand("Lookaside", "#Lookaside", 16);
    public static final MembarOperand MEM_ISSUE = new MembarOperand("MemIssue", "#MemIssue", 32);
    public static final MembarOperand SYNC = new MembarOperand("Sync", "#Sync", 64);

    public static final Symbolizer<MembarOperand> SYMBOLIZER = new Symbolizer<MembarOperand>() {

        private final List<MembarOperand> values = Arrays.asList(new MembarOperand[]{NO_MEMBAR, LOAD_LOAD, STORE_LOAD, LOAD_STORE, STORE_STORE, LOOKASIDE, MEM_ISSUE, SYNC});

        public Class<MembarOperand> type() {
            return MembarOperand.class;
        }

        public int numberOfValues() {
            return values.size();
        }

        public MembarOperand fromValue(int value) {
            MembarOperand result = NO_MEMBAR;
            for (MembarOperand operand : values) {
                if ((value & operand.value()) != 0) {
                    if (result == NO_MEMBAR) {
                        result = operand;
                    } else {
                        result = new MembarOperand(result, operand);
                    }
                }
            }
            return result;
        }

        public Iterator<MembarOperand> iterator() {
            return values.iterator();
        }
    };

}
