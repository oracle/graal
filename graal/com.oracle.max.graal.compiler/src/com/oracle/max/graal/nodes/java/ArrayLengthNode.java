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

import com.oracle.max.graal.compiler.graph.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.base.*;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code ArrayLength} instruction gets the length of an array.
 */
public final class ArrayLengthNode extends FloatingNode implements Canonicalizable {

    @Input private ValueNode array;

    public ValueNode array() {
        return array;
    }

    public void setArray(ValueNode x) {
        updateUsages(array, x);
        array = x;
    }

    /**
     * Constructs a new ArrayLength instruction.
     *
     * @param array the instruction producing the array
     * @param newFrameState the state after executing this instruction
     */
    public ArrayLengthNode(ValueNode array, Graph graph) {
        super(CiKind.Int, graph);
        setArray(array);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitArrayLength(this);
    }

    @Override
    public Node canonical(NotifyReProcess reProcess) {
        if (array() instanceof NewArrayNode) {
            ValueNode length = ((NewArrayNode) array()).dimension(0);
            assert length != null;
            return length;
        }
        CiConstant constantValue = null;
        if (array().isConstant()) {
            constantValue = array().asConstant();
            if (constantValue != null && constantValue.isNonNull()) {
                if (graph() instanceof CompilerGraph) {
                    RiRuntime runtime = ((CompilerGraph) graph()).runtime();
                    return ConstantNode.forInt(runtime.getArrayLength(constantValue), graph());
                }
            }
        }
        return this;
    }
}
