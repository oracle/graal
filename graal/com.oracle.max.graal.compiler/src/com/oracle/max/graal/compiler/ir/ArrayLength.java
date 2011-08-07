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
package com.oracle.max.graal.compiler.ir;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.graph.*;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.NotifyReProcess;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code ArrayLength} instruction gets the length of an array.
 */
public final class ArrayLength extends FloatingNode {
    private static final ArrayLengthCanonicalizerOp CANONICALIZER = new ArrayLengthCanonicalizerOp();

    @Input    private Value array;

    public Value array() {
        return array;
    }

    public void setArray(Value x) {
        updateUsages(array, x);
        array = x;
    }

    /**
     * Constructs a new ArrayLength instruction.
     * @param array the instruction producing the array
     * @param newFrameState the state after executing this instruction
     */
    public ArrayLength(Value array, Graph graph) {
        super(CiKind.Int, graph);
        setArray(array);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitArrayLength(this);
    }

    @Override
    public int valueNumber() {
        return Util.hash1(Bytecodes.ARRAYLENGTH, array());
    }

    @Override
    public boolean valueEqual(Node i) {
        return i instanceof ArrayLength;
    }

    @Override
    public void print(LogStream out) {
        out.print(array()).print(".length");
    }

    @Override
    public Node copy(Graph into) {
        return new ArrayLength(null, into);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == CanonicalizerOp.class) {
            return (T) CANONICALIZER;
        }
        return super.lookup(clazz);
    }

    private static class ArrayLengthCanonicalizerOp implements CanonicalizerOp {
        @Override
        public Node canonical(Node node, NotifyReProcess reProcess) {
            ArrayLength arrayLength = (ArrayLength) node;
            Value array = arrayLength.array();
            if (array instanceof NewArray) {
                Value length = ((NewArray) array).length();
                if (array instanceof NewMultiArray) {
                    length = ((NewMultiArray) array).dimension(0);
                }
                assert length != null;
                return length;
            }
            CiConstant constantValue = null;
            if (array.isConstant()) {
                constantValue = array.asConstant();
            }
            if (constantValue != null && constantValue.isNonNull()) {
                Graph graph = node.graph();
                if (graph instanceof CompilerGraph) {
                    RiRuntime runtime = ((CompilerGraph) graph).runtime();
                    return Constant.forInt(runtime.getArrayLength(constantValue), graph);
                }
            }
            return arrayLength;
        }
    }
}
