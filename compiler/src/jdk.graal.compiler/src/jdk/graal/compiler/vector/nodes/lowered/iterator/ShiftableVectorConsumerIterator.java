/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.nodes.ValueNode;

/**
 * A {@link VectorConsumerIterator} whose work can be offset by an arbitrary index without
 * performing a corresponding chunk of work. For example:
 * <p>
 * {@link VectorWriteIterator}s are shiftable, since it is possible to write an arbitrary chunk of a
 * vector independently of other chunks. As long as all elements of the vector are written, the
 * writes can be performed in any order.
 * <p>
 * {@link FoldVectorIterator}s, on the other hand, are not shiftable in the general case. Skipping a
 * chunk of work, even if we later go back and perform it after the loop is done, could produce a
 * different result than doing the work in iteration order.
 */
public interface ShiftableVectorConsumerIterator extends VectorConsumerIterator {
    VectorConsumerIterator shift(VectorConsumer consumer, ValueNode shiftAmount);
}
