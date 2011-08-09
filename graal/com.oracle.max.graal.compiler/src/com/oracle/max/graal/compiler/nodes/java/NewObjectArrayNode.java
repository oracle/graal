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
package com.oracle.max.graal.compiler.nodes.java;

import com.oracle.max.graal.compiler.nodes.base.*;
import com.oracle.max.graal.compiler.nodes.spi.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code NewObjectArray} instruction represents an allocation of an object array.
 */
public final class NewObjectArrayNode extends NewArrayNode {

    final RiType elementClass;

    /**
     * Constructs a new NewObjectArray instruction.
     * @param elementClass the class of elements in this array
     * @param length the instruction producing the length of the array
     * @param graph
     */
    public NewObjectArrayNode(RiType elementClass, ValueNode length, Graph graph) {
        super(length, graph);
        this.elementClass = elementClass;
    }

    /**
     * Gets the type of the elements of the array.
     * @return the element type of the array
     */
    public RiType elementType() {
        return elementClass;
    }

    @Override
    public CiKind elementKind() {
        return elementClass.kind();
    }

    @Override
    public RiType exactType() {
        return elementClass.arrayOf();
    }

    @Override
    public RiType declaredType() {
        return exactType();
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitNewObjectArray(this);
    }
}
