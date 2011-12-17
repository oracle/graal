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
package com.oracle.max.graal.nodes.java;

import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code ArrayLength} instruction gets the length of an array.
 */
public final class ArrayLengthNode extends FixedWithNextNode implements Canonicalizable, Lowerable, LIRLowerable {

    @Input private ValueNode array;

    public ValueNode array() {
        return array;
    }

    public ArrayLengthNode(ValueNode array) {
        super(StampFactory.intValue());
        this.array = array;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.visitArrayLength(this);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (array() instanceof NewArrayNode) {
            ValueNode length = ((NewArrayNode) array()).dimension(0);
            assert length != null;
            return length;
        }
        CiConstant constantValue = null;
        if (array().isConstant()) {
            constantValue = array().asConstant();
            if (constantValue != null && constantValue.isNonNull()) {
                RiRuntime runtime = tool.runtime();
                return ConstantNode.forInt(runtime.getArrayLength(constantValue), graph());
            }
        }
        return this;
    }

    @Override
    public void lower(CiLoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }
}
