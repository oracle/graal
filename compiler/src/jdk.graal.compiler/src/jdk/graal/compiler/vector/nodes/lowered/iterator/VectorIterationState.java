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

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.vector.nodes.consumer.VectorGuardNode;
import jdk.graal.compiler.vector.nodes.producer.VectorReadNode;

import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;

/**
 * Represents the current state during the iteration over a vector operation.
 *
 * @see VectorIterator
 */
public interface VectorIterationState {

    /**
     * The current index into the vector.
     */
    ValueNode getIndex();

    /**
     * Gets the last access to a memory location during the vector iteration, or {@code null} if the
     * vector iteration does not access this location.
     */
    MemoryKill getLastLocationAccess(LocationIdentity location);

    /**
     * Returns the last vector read generated for this read at this position, or {@code null} if
     * there was no such previous read. Used for preserving sharing of reads with multiple users.
     */
    VectorReadNode getCachedVectorRead(VectorReadNode originalRead, FixedNode position);

    /**
     * Puts the new vector read into the cache of nodes generated for the given original read and
     * position.
     */
    void cacheVectorRead(VectorReadNode originalRead, FixedNode position, VectorReadNode newRead);

    /**
     * Sets the given map as the cache to be used for cached lookups. This enables sharing a cache
     * between different iterators in a group.
     */
    void setVectorReadCache(EconomicMap<Pair<VectorReadNode, FixedNode>, VectorReadNode> readCache);

    /**
     * Returns a version of the last vector guard generated for this guard at this position, or the
     * {@code originalGuard} if it is not a vector guard. Used for preserving the association of
     * vector guards and the nodes guarded by them.
     * <p/>
     *
     * If the vector guard is used through a MultiGuard, implementations must duplicate the
     * MultiGuard. For example, a common graph shape is:
     *
     * <pre>
     *     Guard(NullCheck)        VectorGuard(BoundsCheck)
     *                     \      /
     *                    MultiGuard
     *                        |
     *                   VectorGather
     * </pre>
     *
     * When asked for the cached version of the MultiGuard, implementations must produce a copy of
     * the MultiGuard with the null check guard unchanged but the vectorized bounds check guard
     * replaced by its cached version.
     */
    GuardingNode getCachedVectorGuard(GuardingNode originalGuard, FixedNode position);

    /**
     * Puts the new vector guard into the cache of nodes generated for the given original guard and
     * position.
     */
    void cacheVectorGuard(VectorGuardNode originalGuard, FixedNode position, VectorGuardNode newGuard);

    /**
     * Sets the given map as the cache to be used for cached lookups. This enables sharing a cache
     * between different iterators in a group.
     */
    void setVectorGuardCache(EconomicMap<Pair<VectorGuardNode, FixedNode>, VectorGuardNode> guardCache);
}
