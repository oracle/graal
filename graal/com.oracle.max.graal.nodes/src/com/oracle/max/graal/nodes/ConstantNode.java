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
package com.oracle.max.graal.nodes;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code ConstantNode} represents a constant such as an integer value,
 * long, float, object reference, address, etc.
 */
@NodeInfo(shortName = "Const")
public final class ConstantNode extends BooleanNode implements LIRLowerable {

    @Data public final CiConstant value;
    private RiRuntime runtime;

    private ConstantNode(CiConstant value) {
        this(value, null);
    }

    /**
     * Constructs a new ConstantNode representing the specified constant.
     * @param value the constant
     */
    private ConstantNode(CiConstant value, RiRuntime runtime) {
        super(StampFactory.forConstant(value, runtime));
        this.value = value;
        this.runtime = runtime;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        if (gen.canInlineConstant(value)) {
            gen.setResult(this, value);
        } else {
            gen.setResult(this, gen.emitMove(value));
        }
    }

    public static ConstantNode forCiConstant(CiConstant constant, RiRuntime runtime, Graph graph) {
        return graph.unique(new ConstantNode(constant, runtime));
    }

    /**
     * Returns a node for a double constant.
     * @param d the double value for which to create the instruction
     * @param graph
     * @return a node for a double constant
     */
    public static ConstantNode forDouble(double d, Graph graph) {
        return graph.unique(new ConstantNode(CiConstant.forDouble(d)));
    }

    /**
     * Returns a node for a float constant.
     * @param f the float value for which to create the instruction
     * @param graph
     * @return a node for a float constant
     */
    public static ConstantNode forFloat(float f, Graph graph) {
        return graph.unique(new ConstantNode(CiConstant.forFloat(f)));
    }

    /**
     * Returns a node for an long constant.
     * @param i the long value for which to create the instruction
     * @param graph
     * @return a node for an long constant
     */
    public static ConstantNode forLong(long i, Graph graph) {
        return graph.unique(new ConstantNode(CiConstant.forLong(i)));
    }

    /**
     * Returns a node for an integer constant.
     * @param i the integer value for which to create the instruction
     * @param graph
     * @return a node for an integer constant
     */
    public static ConstantNode forInt(int i, Graph graph) {
        return graph.unique(new ConstantNode(CiConstant.forInt(i)));
    }

    /**
     * Returns a node for a boolean constant.
     * @param i the boolean value for which to create the instruction
     * @param graph
     * @return a node representing the boolean
     */
    public static ConstantNode forBoolean(boolean i, Graph graph) {
        return graph.unique(new ConstantNode(CiConstant.forBoolean(i)));
    }

    /**
     * Returns a node for a byte constant.
     * @param i the byte value for which to create the instruction
     * @param graph
     * @return a node representing the byte
     */
    public static ConstantNode forByte(byte i, Graph graph) {
        return graph.unique(new ConstantNode(CiConstant.forByte(i)));
    }

    /**
     * Returns a node for a char constant.
     * @param i the char value for which to create the instruction
     * @param graph
     * @return a node representing the char
     */
    public static ConstantNode forChar(char i, Graph graph) {
        return graph.unique(new ConstantNode(CiConstant.forChar(i)));
    }

    /**
     * Returns a node for a short constant.
     * @param i the short value for which to create the instruction
     * @param graph
     * @return a node representing the short
     */
    public static ConstantNode forShort(short i, Graph graph) {
        return graph.unique(new ConstantNode(CiConstant.forShort(i)));
    }

    /**
     * Returns a node for an address (jsr/ret address) constant.
     * @param i the address value for which to create the instruction
     * @param graph
     * @return a node representing the address
     */
    public static ConstantNode forJsr(int i, Graph graph) {
        return graph.unique(new ConstantNode(CiConstant.forJsr(i)));
    }

    /**
     * Returns a node for an object constant.
     * @param o the object value for which to create the instruction
     * @param graph
     * @return a node representing the object
     */
    public static ConstantNode forObject(Object o, RiRuntime runtime, Graph graph) {
        return graph.unique(new ConstantNode(CiConstant.forObject(o), runtime));
    }

    public static ConstantNode forIntegerKind(CiKind kind, long value, Graph graph) {
        switch (kind) {
            case Byte:
            case Short:
            case Int:
                return ConstantNode.forInt((int) value, graph);
            case Long:
                return ConstantNode.forLong(value, graph);
            default:
                throw new InternalError("Should not reach here");
        }
    }

    public static ConstantNode forFloatingKind(CiKind kind, double value, Graph graph) {
        switch (kind) {
            case Float:
                return ConstantNode.forFloat((float) value, graph);
            case Double:
                return ConstantNode.forDouble(value, graph);
            default:
                throw new InternalError("Should not reach here");
        }
    }

    public static ConstantNode defaultForKind(CiKind kind, Graph graph) {
        switch(kind) {
            case Boolean:
                return ConstantNode.forBoolean(false, graph);
            case Byte:
            case Char:
            case Short:
            case Int:
                return ConstantNode.forInt(0, graph);
            case Double:
                return ConstantNode.forDouble(0.0, graph);
            case Float:
                return ConstantNode.forFloat(0.0f, graph);
            case Long:
                return ConstantNode.forLong(0L, graph);
            case Object:
                return ConstantNode.forObject(null, null, graph);
            default:
                return null;
        }
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(Verbosity.Name) + "(" + value.kind.format(value.boxedValue()) + ")";
        } else {
            return super.toString(verbosity);
        }
    }

    @Override
    public BooleanNode negate() {
        return ConstantNode.forBoolean(!value.asBoolean(), graph());
    }
}
