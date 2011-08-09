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

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code LoadIndexed} instruction represents a read from an element of an array.
 */
public final class LoadIndexedNode extends AccessIndexedNode {

    /**
     * Creates a new LoadIndexed instruction.
     * @param array the instruction producing the array
     * @param index the instruction producing the index
     * @param length the instruction producing the length
     * @param elementKind the element type
     * @param graph
     */
    public LoadIndexedNode(ValueNode array, ValueNode index, ValueNode length, CiKind elementKind, Graph graph) {
        super(elementKind.stackKind(), array, index, length, elementKind, graph);
    }

    /**
     * Gets the declared type of this instruction's result.
     * @return the declared type
     */
    @Override
    public RiType declaredType() {
        RiType arrayType = array().declaredType();
        if (arrayType == null) {
            return null;
        }
        return arrayType.componentType();
    }

    /**
     * Gets the exact type of this instruction's result.
     * @return the exact type
     */
    @Override
    public RiType exactType() {
        RiType declared = declaredType();
        return declared != null && declared.isResolved() ? declared.exactType() : null;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitLoadIndexed(this);
    }

    @Override
    public boolean needsStateAfter() {
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == LoweringOp.class) {
            return (T) DELEGATE_TO_RUNTIME;
        }
        return super.lookup(clazz);
    }
}
