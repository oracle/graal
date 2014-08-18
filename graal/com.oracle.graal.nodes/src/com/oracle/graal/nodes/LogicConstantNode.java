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
package com.oracle.graal.nodes;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.spi.*;

/**
 * The {@code LogicConstantNode} represents a boolean constant.
 */
@NodeInfo(nameTemplate = "{p#value}")
public class LogicConstantNode extends LogicNode implements LIRLowerable {

    public final boolean value;

    public static LogicConstantNode create(boolean value) {
        return new LogicConstantNodeGen(value);
    }

    protected LogicConstantNode(boolean value) {
        super();
        this.value = value;
    }

    /**
     * Returns a node for a boolean constant.
     *
     * @param v the boolean value for which to create the instruction
     * @param graph
     * @return a node representing the boolean
     */
    public static LogicConstantNode forBoolean(boolean v, Graph graph) {
        return graph.unique(LogicConstantNode.create(v));
    }

    /**
     * Returns a node for a boolean constant.
     *
     * @param v the boolean value for which to create the instruction
     * @return a node representing the boolean
     */
    public static LogicConstantNode forBoolean(boolean v) {
        return LogicConstantNode.create(v);
    }

    /**
     * Gets a constant for {@code true}.
     */
    public static LogicConstantNode tautology(Graph graph) {
        return forBoolean(true, graph);
    }

    /**
     * Gets a constant for {@code false}.
     */
    public static LogicConstantNode contradiction(Graph graph) {
        return forBoolean(false, graph);
    }

    /**
     * Gets a constant for {@code true}.
     */
    public static LogicConstantNode tautology() {
        return forBoolean(true);
    }

    /**
     * Gets a constant for {@code false}.
     */
    public static LogicConstantNode contradiction() {
        return forBoolean(false);
    }

    public boolean getValue() {
        return value;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        // nothing to do
    }
}
