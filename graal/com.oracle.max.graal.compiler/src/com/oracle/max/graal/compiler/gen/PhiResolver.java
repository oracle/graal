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
package com.oracle.max.graal.compiler.gen;

import static com.sun.cri.ci.CiValue.*;

import java.util.*;

import com.oracle.max.graal.nodes.*;
import com.sun.cri.ci.*;

/**
 * Converts {@link PhiNode} instructions into moves.
 *
 * Resolves cycles:
 * <pre>
 *
 *  r1 := r2  becomes  temp := r1
 *  r2 := r1           r1 := r2
 *                     r2 := temp
 * </pre>
 *
 * and orders moves:
 *
 * <pre>
 *  r2 := r3  becomes  r1 := r2
 *  r1 := r2           r2 := r3
 * </pre>
 */
public class PhiResolver {

    /**
     * Tracks a data flow dependency between a source operand and any number of the destination operands.
     */
    static class PhiResolverNode {

        /**
         * A source operand whose value flows into the {@linkplain #destinations destination} operands.
         */
        final CiValue operand;

        /**
         * The operands whose values are defined by the {@linkplain #operand source} operand.
         */
        final ArrayList<PhiResolverNode> destinations;

        /**
         * Denotes if a move instruction has already been emitted to initialize the value of {@link #operand}.
         */
        boolean assigned;

        /**
         * Specifies if this operand been visited for the purpose of emitting a move instruction.
         */
        boolean visited;

        /**
         * Specifies if this is the initial definition in data flow path for a given value.
         */
        boolean startNode;

        PhiResolverNode(CiValue operand) {
            this.operand = operand;
            destinations = new ArrayList<PhiResolverNode>(4);
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder(operand.toString());
            if (!destinations.isEmpty()) {
                buf.append(" ->");
                for (PhiResolverNode node : destinations) {
                    buf.append(' ').append(node.operand);
                }
            }
            return buf.toString();
        }
    }

    private final LIRGenerator gen;

    /**
     * The operand loop header phi for the operand currently being process in {@link #dispose()}.
     */
    private PhiResolverNode loop;

    private CiValue temp;

    private final ArrayList<PhiResolverNode> variableOperands = new ArrayList<PhiResolverNode>(3);
    private final ArrayList<PhiResolverNode> otherOperands = new ArrayList<PhiResolverNode>(3);

    /**
     * Maps operands to nodes.
     */
    private final HashMap<CiValue, PhiResolverNode> operandToNodeMap = new HashMap<CiValue, PhiResolverNode>();

    public PhiResolver(LIRGenerator gen) {
        this.gen = gen;
        temp = IllegalValue;
    }

    public void dispose() {
        // resolve any cycles in moves from and to variables
        for (int i = variableOperands.size() - 1; i >= 0; i--) {
            PhiResolverNode node = variableOperands.get(i);
            if (!node.visited) {
                loop = null;
                move(null, node);
                node.startNode = true;
                assert temp.isIllegal() : "moveTempTo() call missing";
            }
        }

        // generate move for move from non variable to arbitrary destination
        for (int i = otherOperands.size() - 1; i >= 0; i--) {
            PhiResolverNode node = otherOperands.get(i);
            for (int j = node.destinations.size() - 1; j >= 0; j--) {
                emitMove(node.operand, node.destinations.get(j).operand);
            }
        }
    }

    public void move(CiValue src, CiValue dest) {
        assert dest.isVariable() : "destination must be virtual";
        // tty.print("move "); src.print(); tty.print(" to "); dest.print(); tty.cr();
        assert src.isLegal() : "source for phi move is illegal";
        assert dest.isLegal() : "destination for phi move is illegal";
        PhiResolverNode srcNode = sourceNode(src);
        PhiResolverNode destNode = destinationNode(dest);
        srcNode.destinations.add(destNode);
      }

    private PhiResolverNode createNode(CiValue operand, boolean source) {
        PhiResolverNode node;
        if (operand.isVariable()) {
            node = operandToNodeMap.get(operand);
            assert node == null || node.operand.equals(operand);
            if (node == null) {
                node = new PhiResolverNode(operand);
                operandToNodeMap.put(operand, node);
            }
            // Make sure that all variables show up in the list when
            // they are used as the source of a move.
            if (source) {
                if (!variableOperands.contains(node)) {
                    variableOperands.add(node);
                }
            }
        } else {
            assert source;
            node = new PhiResolverNode(operand);
            otherOperands.add(node);
        }
        return node;
    }

    private PhiResolverNode destinationNode(CiValue opr) {
        return createNode(opr, false);
    }

    private void emitMove(CiValue src, CiValue dest) {
        assert src.isLegal();
        assert dest.isLegal();
        gen.emitMove(src, dest);
    }

    // Traverse assignment graph in depth first order and generate moves in post order
    // ie. two assignments: b := c, a := b start with node c:
    // Call graph: move(NULL, c) -> move(c, b) -> move(b, a)
    // Generates moves in this order: move b to a and move c to b
    // ie. cycle a := b, b := a start with node a
    // Call graph: move(NULL, a) -> move(a, b) -> move(b, a)
    // Generates moves in this order: move b to temp, move a to b, move temp to a
    private void move(PhiResolverNode src, PhiResolverNode dest) {
        if (!dest.visited) {
            dest.visited = true;
            for (int i = dest.destinations.size() - 1; i >= 0; i--) {
                move(dest, dest.destinations.get(i));
            }
        } else if (!dest.startNode) {
            // cycle in graph detected
            assert loop == null : "only one loop valid!";
            loop = dest;
            moveToTemp(src.operand);
            return;
        } // else dest is a start node

        if (!dest.assigned) {
            if (loop == dest) {
                moveTempTo(dest.operand);
                dest.assigned = true;
            } else if (src != null) {
                emitMove(src.operand, dest.operand);
                dest.assigned = true;
            }
        }
    }

    private void moveTempTo(CiValue dest) {
        assert temp.isLegal();
        emitMove(temp, dest);
        temp = IllegalValue;
    }

    private void moveToTemp(CiValue src) {
        assert temp.isIllegal();
        temp = gen.newVariable(src.kind);
        emitMove(src, temp);
    }

    private PhiResolverNode sourceNode(CiValue opr) {
        return createNode(opr, true);
    }
}
