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
package com.oracle.max.graal.compiler.ir;

import static com.oracle.max.graal.compiler.GraalCompilation.*;

import com.oracle.max.graal.compiler.nodes.spi.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code Constant} instruction represents a constant such as an integer value,
 * long, float, object reference, address, etc.
 */
public final class Constant extends BooleanNode {

    @Data public final CiConstant value;

    /**
     * Constructs a new instruction representing the specified constant.
     * @param value the constant
     * @param graph
     */
    public Constant(CiConstant value, Graph graph) {
        super(value.kind.stackKind(), graph);
        this.value = value;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitConstant(this);
    }

    /**
     * Creates an instruction for a double constant.
     * @param d the double value for which to create the instruction
     * @param graph
     * @return an instruction representing the double
     */
    public static Constant forDouble(double d, Graph graph) {
        return new Constant(CiConstant.forDouble(d), graph);
    }

    /**
     * Creates an instruction for a float constant.
     * @param f the float value for which to create the instruction
     * @return an instruction representing the float
     */
    public static Constant forFloat(float f, Graph graph) {
        return new Constant(CiConstant.forFloat(f), graph);
    }

    /**
     * Creates an instruction for an long constant.
     * @param i the long value for which to create the instruction
     * @return an instruction representing the long
     */
    public static Constant forLong(long i, Graph graph) {
        return new Constant(CiConstant.forLong(i), graph);
    }

    /**
     * Creates an instruction for an integer constant.
     * @param i the integer value for which to create the instruction
     * @return an instruction representing the integer
     */
    public static Constant forInt(int i, Graph graph) {
        return new Constant(CiConstant.forInt(i), graph);
    }

    /**
     * Creates an instruction for a boolean constant.
     * @param i the boolean value for which to create the instruction
     * @return an instruction representing the boolean
     */
    public static Constant forBoolean(boolean i, Graph graph) {
        return new Constant(CiConstant.forBoolean(i), graph);
    }

    /**
     * Creates an instruction for an address (jsr/ret address) constant.
     * @param i the address value for which to create the instruction
     * @return an instruction representing the address
     */
    public static Constant forJsr(int i, Graph graph) {
        return new Constant(CiConstant.forJsr(i), graph);
    }

    /**
     * Creates an instruction for an object constant.
     * @param o the object value for which to create the instruction
     * @return an instruction representing the object
     */
    public static Constant forObject(Object o, Graph graph) {
        return new Constant(CiConstant.forObject(o), graph);
    }

    /**
     * Creates an instruction for a word constant.
     * @param val the word value for which to create the instruction
     * @return an instruction representing the word
     */
    public static Constant forWord(long val, Graph graph) {
        return new Constant(CiConstant.forWord(val), graph);
    }

    public static Constant defaultForKind(CiKind kind, Graph graph) {
        switch(kind) {
            case Boolean:
                return Constant.forBoolean(false, graph);
            case Byte:
            case Char:
            case Short:
            case Int:
                return Constant.forInt(0, graph);
            case Double:
                return Constant.forDouble(0.0, graph);
            case Float:
                return Constant.forFloat(0.0f, graph);
            case Long:
                return Constant.forLong(0L, graph);
            case Object:
                return Constant.forObject(null, graph);
            case Word:
                return Constant.forWord(0L, graph);
            default:
                return null;
        }
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
    public RiType declaredType() {
        RiRuntime runtime = compilation().runtime;
        if (kind.isPrimitive()) {
            return runtime.asRiType(kind);
        }
        return runtime.getTypeOf(asConstant());
    }

    @Override
    public RiType exactType() {
        return declaredType();
    }

    @Override
    public String shortName() {
        return value.name();
    }
}
