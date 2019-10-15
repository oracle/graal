/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common.vectorization;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ValueNode;

import java.util.Set;

public interface AutovectorizationPolicies {

    /**
     * Estimate the savings of executing the pack rather than two separate instructions.
     *
     * @param context AutovectorizationContext to query information about the context.
     * @param packSet packset, for membership checks.
     * @param s1 Candidate left element of Pack.
     * @param s2 Candidate right element of Pack.
     * @return Savings in an arbitrary unit, can be negative.
     *         Candidate pack is created if this method returns a non-negative integer.
     */
    int estSavings(AutovectorizationContext context, Set<Pair<ValueNode, ValueNode>> packSet, Node s1, Node s2);

    /**
     * Filter out packs according to a heuristic.
     *
     * This method serves to be a final point at which to evaluate whether a Pack is beneficial within the context
     * of all other Packs. If a pack is to be removed, this method should mutate the set.
     *
     * @param context AutovectorizationContext to query information about the context.
     * @param combinedPackSet Set of packs that are as large as possible, just before being scheduled.
     */
    void filterPacks(AutovectorizationContext context, Set<Pack> combinedPackSet);
}
