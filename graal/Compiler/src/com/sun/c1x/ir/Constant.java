/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.ir;

import static com.sun.c1x.C1XCompilation.*;

import com.sun.c1x.debug.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code Constant} instruction represents a constant such as an integer value,
 * long, float, object reference, address, etc.
 *
 * @author Ben L. Titzer
 */
public final class Constant extends Instruction {

    public final CiConstant value;

    /**
     * Constructs a new instruction representing the specified constant.
     * @param value the constant
     */
    public Constant(CiConstant value) {
        super(value.kind.stackKind());
        this.value = value;
        initFlag(Value.Flag.NonNull, value.isNonNull());
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitConstant(this);
    }

    /**
     * Creates an instruction for a double constant.
     * @param d the double value for which to create the instruction
     * @return an instruction representing the double
     */
    public static Constant forDouble(double d) {
        return new Constant(CiConstant.forDouble(d));
    }

    /**
     * Creates an instruction for a float constant.
     * @param f the float value for which to create the instruction
     * @return an instruction representing the float
     */
    public static Constant forFloat(float f) {
        return new Constant(CiConstant.forFloat(f));
    }

    /**
     * Creates an instruction for an long constant.
     * @param i the long value for which to create the instruction
     * @return an instruction representing the long
     */
    public static Constant forLong(long i) {
        return new Constant(CiConstant.forLong(i));
    }

    /**
     * Creates an instruction for an integer constant.
     * @param i the integer value for which to create the instruction
     * @return an instruction representing the integer
     */
    public static Constant forInt(int i) {
        return new Constant(CiConstant.forInt(i));
    }

    /**
     * Creates an instruction for a boolean constant.
     * @param i the boolean value for which to create the instruction
     * @return an instruction representing the boolean
     */
    public static Constant forBoolean(boolean i) {
        return new Constant(CiConstant.forBoolean(i));
    }

    /**
     * Creates an instruction for an address (jsr/ret address) constant.
     * @param i the address value for which to create the instruction
     * @return an instruction representing the address
     */
    public static Constant forJsr(int i) {
        return new Constant(CiConstant.forJsr(i));
    }

    /**
     * Creates an instruction for an object constant.
     * @param o the object value for which to create the instruction
     * @return an instruction representing the object
     */
    public static Constant forObject(Object o) {
        return new Constant(CiConstant.forObject(o));
    }

    /**
     * Creates an instruction for a word constant.
     * @param val the word value for which to create the instruction
     * @return an instruction representing the word
     */
    public static Constant forWord(long val) {
        return new Constant(CiConstant.forWord(val));
    }

    @Override
    public String toString() {
        return super.toString() + "(" + value + ")";
    }

    @Override
    public int valueNumber() {
        return 0x50000000 | value.hashCode();
    }

    @Override
    public boolean valueEqual(Instruction i) {
        return i instanceof Constant && ((Constant) i).value.equivalent(this.value);
    }

    @Override
    public RiType declaredType() {
        RiRuntime runtime = compilation().runtime;
        if (kind.isPrimitive()) {
            runtime.asRiType(kind);
        }
        return runtime.getTypeOf(asConstant());
    }

    @Override
    public RiType exactType() {
        return declaredType();
    }

    @Override
    public void print(LogStream out) {
        out.print(value.valueString());
    }
}
