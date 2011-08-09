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

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.collections.*;
import com.oracle.max.graal.nodes.base.*;

/**
 * The {@code PhiSimplifier} class is a helper class that can reduce phi instructions.
 */
public final class PhiSimplifier {

    private NodeBitMap visited;
    private NodeBitMap cannotSimplify;

    public PhiSimplifier(Graph graph) {
        visited = graph.createNodeBitMap();
        cannotSimplify = graph.createNodeBitMap();

        for (Node n : graph.getNodes()) {
            if (n instanceof PhiNode) {
                simplify((PhiNode) n);
            }
        }
    }

    private ValueNode simplify(ValueNode x) {
        if (x == null || !(x instanceof PhiNode)) {
            return x;
        }
        PhiNode phi = (PhiNode) x;

        if (phi.valueCount() == 1 && !cannotSimplify.isMarked(phi)) {
            ValueNode result = phi.valueAt(0);
            phi.replaceAndDelete(result);
            return result;
        }

        if (cannotSimplify.isMarked(phi)) {
            // already tried, cannot simplify this phi
            return phi;
        } else if (visited.isMarked(phi)) {
            // break cycles in phis
            return phi;
        } else {
            // attempt to simplify the phi by recursively simplifying its operands
            visited.mark(phi);
            ValueNode phiSubst = null;
            int max = phi.valueCount();
            boolean cannotSimplify = false;
            for (int i = 0; i < max; i++) {
                ValueNode oldInstr = phi.valueAt(i);

                if (oldInstr == null) {
                    // if one operand is illegal, make the entire phi illegal
                    visited.clear(phi);
                    phi.replaceAndDelete(null);
                    return null;
                }

                ValueNode newInstr = simplify(oldInstr);

                if (newInstr == null) {
                    // if the subst instruction is illegal, make the entire phi illegal
                    visited.clear(phi);
                    phi.replaceAndDelete(null);
                    return null;
                }

                // attempt to simplify this operand
                if (!cannotSimplify) {

                    if (newInstr != phi && newInstr != phiSubst) {
                        if (phiSubst == null) {
                            phiSubst = newInstr;
                            continue;
                        }
                        // this phi cannot be simplified
                        cannotSimplify = true;
                    }
                }
            }
            if (cannotSimplify) {
                this.cannotSimplify.mark(phi);
                visited.clear(phi);
                return phi;
            }

            // successfully simplified the phi
            assert phiSubst != null : "illegal phi function";
            visited.clear(phi);

            phi.replaceAndDelete(phiSubst);

            return phiSubst;
        }
    }
}
