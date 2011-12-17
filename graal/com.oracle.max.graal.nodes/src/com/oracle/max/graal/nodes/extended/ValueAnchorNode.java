/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.nodes.extended;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;
import com.sun.cri.ci.*;

/**
 * The ValueAnchor instruction keeps non-CFG (floating) nodes above a certain point in the graph.
 */

public final class ValueAnchorNode extends FixedWithNextNode implements Canonicalizable, LIRLowerable {

    @Input private ValueNode object;

    public ValueNode object() {
        return object;
    }

    public ValueAnchorNode(ValueNode object) {
        super(StampFactory.illegal());
        this.object = object;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        // Nothing to emit, since this is node is used for structural purposes only.
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (object == null) {
            return next();
        }
        if (object instanceof ConstantNode) {
            return next();
        }
        if (object instanceof IntegerDivNode || object instanceof IntegerRemNode) {
            if (((ArithmeticNode) object).y().isConstant()) {
                CiConstant  constant = ((ArithmeticNode) object).y().asConstant();
                assert constant.kind == object.kind() : constant.kind + " != " + object.kind();
                if (constant.asLong() != 0) {
                    return next();
                }
            }
        }
        return this;
    }
}
