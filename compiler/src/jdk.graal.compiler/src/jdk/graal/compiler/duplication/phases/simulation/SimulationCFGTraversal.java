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

import jdk.graal.compiler.core.common.cfg.BasicBlockSet;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph.RecursiveVisitor;

public class SimulationCFGTraversal {

    @SuppressWarnings({"unchecked"})
    public static <V> void visitDominatorTreeBounded(ControlFlowGraph cfg, RecursiveVisitor<V> visitor, HIRBlock start, int startDepth, int maxDepth) {
        HIRBlock[] stack = new HIRBlock[cfg.getBlocks().length];
        int tos = 0;
        BasicBlockSet visited = cfg.createBasicBlockSet();
        Object[] values = null;
        int valuesTOS = 0;
        stack[0] = start;

        while (tos >= 0) {
            HIRBlock cur = stack[tos];
            if (visited.get(cur)) {
                V value = null;
                if (values != null && valuesTOS > 0) {
                    value = (V) values[--valuesTOS];
                }
                visitor.exit(cur, value);
                --tos;
            } else {
                visited.set(cur);
                V value = visitor.enter(cur);
                if (value != null || values != null) {
                    if (values == null) {
                        values = new Object[Math.max(cfg.getMaxDominatorDepth() + 1, maxDepth + 1)];
                    }
                    values[valuesTOS++] = value;
                }

                if ((cur.getDominatorDepth() - startDepth) <= maxDepth) {
                    HIRBlock alwaysReached = cur.getPostdominator();
                    if (alwaysReached != null) {
                        if (alwaysReached.getDominator() != cur) {
                            alwaysReached = null;
                        } else {
                            stack[++tos] = alwaysReached;
                        }
                    }

                    HIRBlock b = cur.getFirstDominated();
                    while (b != null) {
                        if (b != alwaysReached) {
                            stack[++tos] = b;
                        }
                        b = b.getDominatedSibling();
                    }
                }

            }
        }
    }

}
