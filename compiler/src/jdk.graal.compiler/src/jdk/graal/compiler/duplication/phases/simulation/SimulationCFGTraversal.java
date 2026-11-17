/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.duplication.phases.simulation;

import java.util.Arrays;

import jdk.graal.compiler.nodes.cfg.ControlFlowGraph.RecursiveVisitor;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

/**
 * Reusable traversal storage for a dominator tree. Each invocation visits only its bounded subtree;
 * it does not allocate or clear storage proportional to the size of the entire CFG.
 */
public final class SimulationCFGTraversal<V> {
    private HIRBlock[] stack = new HIRBlock[16];
    private Object[] values = new Object[16];
    private boolean[] entered = new boolean[16];
    private int size;

    private void push(HIRBlock block) {
        if (size == stack.length) {
            int capacity = stack.length * 2;
            stack = Arrays.copyOf(stack, capacity);
            values = Arrays.copyOf(values, capacity);
            entered = Arrays.copyOf(entered, capacity);
        }
        stack[size] = block;
        entered[size] = false;
        size++;
    }

    @SuppressWarnings("unchecked")
    public void visitDominatorTreeBounded(RecursiveVisitor<V> visitor, HIRBlock start, int startDepth, int maxDepth) {
        assert size == 0 : "Traversal must not be reentered";
        push(start);
        try {
            while (size > 0) {
                int index = size - 1;
                HIRBlock cur = stack[index];
                if (entered[index]) {
                    V value = (V) values[index];
                    stack[index] = null;
                    values[index] = null;
                    size--;
                    visitor.exit(cur, value);
                } else {
                    entered[index] = true;
                    values[index] = visitor.enter(cur);
                    if ((cur.getDominatorDepth() - startDepth) <= maxDepth) {
                        HIRBlock alwaysReached = cur.getPostdominator();
                        if (alwaysReached != null && alwaysReached.getDominator() == cur) {
                            push(alwaysReached);
                        } else {
                            alwaysReached = null;
                        }
                        // Preserve the original traversal order: the postdominator is visited last.
                        for (HIRBlock b = cur.getFirstDominated(); b != null; b = b.getDominatedSibling()) {
                            if (b != alwaysReached) {
                                push(b);
                            }
                        }
                    }
                }
            }
        } finally {
            // A failed visitor must not retain blocks or scope values in reusable storage.
            while (size > 0) {
                stack[--size] = null;
                values[size] = null;
            }
        }
    }
}
