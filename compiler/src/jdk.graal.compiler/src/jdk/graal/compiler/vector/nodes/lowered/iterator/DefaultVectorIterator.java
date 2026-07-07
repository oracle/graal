/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.lowered.iterator;

import jdk.graal.compiler.vector.nodes.AbstractVectorNode;
import jdk.graal.compiler.vector.nodes.VectorNode;

import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.ConstantReflectionProvider;

public class DefaultVectorIterator implements VectorIterator {

    @Override
    public void addPhiInput(VectorIterator input) {
    }

    @Override
    public VectorNode getVector(VectorNode source, VectorIterationState state, FixedNode position, ConstantReflectionProvider constantReflection, int stepLength) {
        return AbstractVectorNode.shift(source, state.getIndex(), AbstractBeginNode.prevBegin(position), constantReflection);
    }

    @Override
    public LogicNode hasNext(VectorNode vector, VectorIterationState state, int stepLength, ValueNode limit) {
        return null;
    }

    @Override
    public VectorIterator next(VectorNode vector, ValueNode stepLength) {
        return this;
    }
}
