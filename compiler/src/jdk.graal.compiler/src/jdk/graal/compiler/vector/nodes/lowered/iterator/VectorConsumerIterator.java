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

import jdk.graal.compiler.vector.nodes.consumer.VectorConsumer;
import jdk.graal.compiler.vector.nodes.lowered.FinishVectorConsumerNode;

import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * A VectorConsumerIterator is used to iterate over the work that a vector consumer must do in the
 * lowered vector code.
 */
public interface VectorConsumerIterator {

    /**
     * Create lowered nodes for doing the next chunk of work.
     *
     * @param consumer The high-level vector consumer node.
     * @param stepLength The amount of elements that should be consumer.
     * @param position The position where the nodes should be inserted.
     * @return A new iterator representing the rest of the work.
     */
    VectorConsumerIterator next(VectorConsumer consumer, int stepLength, FixedNode position, ConstantReflectionProvider constantReflection);

    /**
     * Determine if at least stepLength elements are left to be consumed.
     */
    LogicNode hasNext(VectorConsumer consumer, int stepLength, ValueNode limit, ConstantReflectionProvider constantReflection);

    /**
     * Compute the number of elements that need to be processed in the pre-loop in order for the
     * access to vector elements to be aligned.
     */
    ValueNode getAlignCount(VectorConsumer consumer, int align);

    /**
     * Add a new input to this iterator. The iterator must be derived from a phi node.
     */
    void addPhiInput(VectorConsumer consumer, VectorConsumerIterator input, AbstractEndNode branch);

    /**
     * Create lowered nodes to finish the work of a vector consumer.
     */
    void finishConsumer(FinishVectorConsumerNode finish);
}
