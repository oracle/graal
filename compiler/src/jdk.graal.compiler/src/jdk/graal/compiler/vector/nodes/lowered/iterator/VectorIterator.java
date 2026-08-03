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

import jdk.graal.compiler.vector.nodes.VectorNode;

import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * A VectorIterator is used to iterate over a high-level vector node in the lowered code.
 */
public interface VectorIterator {

    /**
     * Add a new input to this iterator. The iterator must be derived from a phi node.
     */
    void addPhiInput(VectorIterator input);

    /**
     * Get the lowered vector node that represents the current part of the high-level vector.
     *
     * @param vector The high-level vector.
     * @param state The current {@link VectorIterationState}.
     * @param position The position where fixed nodes produced by the lowering should be inserted.
     * @param stepLength The number of accessed elements.
     */
    VectorNode getVector(VectorNode vector, VectorIterationState state, FixedNode position, ConstantReflectionProvider constantReflection, int stepLength);

    /**
     * Get a {@linkplain LogicNode} that determines if at least stepLength more values can be read
     * from the vector. The node returned by
     * {@linkplain #getVector(VectorNode, VectorIterationState, FixedNode, ConstantReflectionProvider, int)}
     * is valid and has at least stepLength elements if the node returned by
     * {@linkplain #hasNext(VectorNode, VectorIterationState, int, ValueNode)} is true.
     * <p>
     * The {@code limit} parameter specifies an upper bound on the number of elements that can be
     * read from the vector. If {@code null}, that limit defaults to the full length of the vector.
     * The value will be clamped to the vector's length during lowering, so there is no need for an
     * explicit bounds check.
     * <p>
     * This method may return null if the next value is always valid (i.e. the high-level vector has
     * infinite length).
     */
    LogicNode hasNext(VectorNode vector, VectorIterationState state, int stepLength, ValueNode limit);

    /**
     * Get the next iterator, skipping stepLength elements.
     */
    VectorIterator next(VectorNode vector, ValueNode stepLength);
}
