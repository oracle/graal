/*
 * Copyright (c) 2013, 2013, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.graph.spi.*;

public class ShortCircuitOrNode extends LogicNode implements IterableNodeType, Canonicalizable {

    @Input private LogicNode x;
    @Input private LogicNode y;
    private boolean xNegated;
    private boolean yNegated;
    private double shortCircuitProbability;

    public ShortCircuitOrNode(LogicNode x, boolean xNegated, LogicNode y, boolean yNegated, double shortCircuitProbability) {
        this.x = x;
        this.xNegated = xNegated;
        this.y = y;
        this.yNegated = yNegated;
        this.shortCircuitProbability = shortCircuitProbability;
    }

    public LogicNode getX() {
        return x;
    }

    public LogicNode getY() {
        return y;
    }

    public boolean isXNegated() {
        return xNegated;
    }

    public boolean isYNegated() {
        return yNegated;
    }

    /**
     * Gets the probability that the {@link #getY() y} part of this binary node is <b>not</b>
     * evaluated. This is the probability that this operator will short-circuit its execution.
     */
    public double getShortCircuitProbability() {
        return shortCircuitProbability;
    }

    protected ShortCircuitOrNode canonicalizeNegation() {
        LogicNode xCond = x;
        boolean xNeg = xNegated;
        while (xCond instanceof LogicNegationNode) {
            xCond = ((LogicNegationNode) xCond).getInput();
            xNeg = !xNeg;
        }

        LogicNode yCond = y;
        boolean yNeg = yNegated;
        while (yCond instanceof LogicNegationNode) {
            yCond = ((LogicNegationNode) yCond).getInput();
            yNeg = !yNeg;
        }

        if (xCond != x || yCond != y) {
            return graph().unique(new ShortCircuitOrNode(xCond, xNeg, yCond, yNeg, shortCircuitProbability));
        } else {
            return null;
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ShortCircuitOrNode ret = canonicalizeNegation();
        if (ret != null) {
            return ret;
        }

        if (getX() == getY()) {
            // @formatter:off
            //  a ||  a = a
            //  a || !a = true
            // !a ||  a = true
            // !a || !a = !a
            // @formatter:on
            if (isXNegated()) {
                if (isYNegated()) {
                    // !a || !a = !a
                    return graph().unique(new LogicNegationNode(getX()));
                } else {
                    // !a || a = true
                    return LogicConstantNode.tautology(graph());
                }
            } else {
                if (isYNegated()) {
                    // a || !a = true
                    return LogicConstantNode.tautology(graph());
                } else {
                    // a || a = a
                    return getX();
                }
            }
        }
        if (getX() instanceof LogicConstantNode) {
            if (((LogicConstantNode) getX()).getValue() ^ isXNegated()) {
                return LogicConstantNode.tautology(graph());
            } else {
                if (isYNegated()) {
                    return graph().unique(new LogicNegationNode(getY()));
                } else {
                    return getY();
                }
            }
        }
        if (getY() instanceof LogicConstantNode) {
            if (((LogicConstantNode) getY()).getValue() ^ isYNegated()) {
                return LogicConstantNode.tautology(graph());
            } else {
                if (isXNegated()) {
                    return graph().unique(new LogicNegationNode(getX()));
                } else {
                    return getX();
                }
            }
        }
        return this;
    }
}
