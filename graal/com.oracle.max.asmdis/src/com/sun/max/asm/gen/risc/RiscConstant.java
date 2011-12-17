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
package com.sun.max.asm.gen.risc;

import com.sun.max.asm.*;
import com.sun.max.asm.gen.risc.field.*;

/**
 */
public class RiscConstant {

    private final RiscField field;
    private final int value;

    public RiscConstant(RiscField field, Argument argument) {
        this.field = field;
        this.value = (int) argument.asLong();
    }

    public RiscConstant(RiscField field, int value) {
        this.field = field;
        this.value = value;
    }

    public RiscField field() {
        return field;
    }

    public int value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof RiscConstant) {
            final RiscConstant riscConstant = (RiscConstant) other;
            return field.equals(riscConstant.field) && value == riscConstant.value;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return field.hashCode() ^ value;
    }

    @Override
    public String toString() {
        return field.toString() + "(" + value + ")";
    }
}
