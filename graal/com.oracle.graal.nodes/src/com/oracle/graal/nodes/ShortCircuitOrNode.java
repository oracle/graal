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

import com.oracle.graal.nodes.spi.*;

/**
 * The short-circuit <b>OR</b> (i.e. {@code ||} in Java) operator.
 */
public class ShortCircuitOrNode extends ShortCircuitBooleanNode implements Canonicalizable {

    public ShortCircuitOrNode(LogicNode x, boolean xNegated, LogicNode y, boolean yNegated, double shortCircuitProbability) {
        super(x, xNegated, y, yNegated, shortCircuitProbability);
    }

    @Override
    public LogicNode canonical(CanonicalizerTool tool) {
        LogicNode x = getX();
        LogicNode y = getY();
        if (x == y) {
            // @formatter:off
            //  a ||  a = a
            //  a || !a = true
            // !a ||  a = true
            // !a || !a = !a
            // @formatter:on
            if (isXNegated()) {
                if (isYNegated()) {
                    // !a || !a = !a
                    negateUsages();
                    return x;
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
                    return x;
                }
            }
        }
        if (x instanceof LogicConstantNode) {
            if (((LogicConstantNode) x).getValue() ^ isXNegated()) {
                return LogicConstantNode.tautology(graph());
            } else {
                if (isYNegated()) {
                    negateUsages();
                }
                return y;
            }
        }
        if (y instanceof LogicConstantNode) {
            if (((LogicConstantNode) y).getValue() ^ isYNegated()) {
                return LogicConstantNode.tautology(graph());
            } else {
                if (isXNegated()) {
                    negateUsages();
                }
                return x;
            }
        }
        return this;
    }

}
