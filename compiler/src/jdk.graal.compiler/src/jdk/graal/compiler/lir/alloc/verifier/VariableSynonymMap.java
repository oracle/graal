/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.util.EconomicHashMap;

import java.util.Map;

/**
 * Simplified disjoint union set implementation. We could on the fact that the destination variable
 * is only defined once in variable to variable moves.
 *
 * <p>
 * The representative of every union is the original source variable not created by a coalesced
 * move.
 * </p>
 */
public class VariableSynonymMap {
    protected final Map<RAVariable, RAVariable> parent;

    protected VariableSynonymMap() {
        parent = new EconomicHashMap<>();
    }

    /**
     * Add a synonym to the disjoint union set.
     *
     * <p>
     * The destination variable is only defined once and is always linked to the root of the source
     * variable.
     * </p>
     *
     * <p>
     * This method counts on the fact that the source variable was already defined by either a
     * different coalesced move or by an existing instruction <code>v2 = MOVE v1</code> and then
     * followed by <code>v1 = MOVE v3</code> will cause a failure.
     * </p>
     *
     * @param src Source variable
     * @param dst Destination variable
     */
    protected void addSynonym(RAVariable src, RAVariable dst) {
        assert !parent.containsKey(dst) : "Cannot redefine variable again.";

        RAVariable rootSrc = find(src);
        parent.put(dst, rootSrc);
    }

    /**
     * Find the root of the variable. If none is found, then the variable itself is returned.
     *
     * @param x Variable to find the root of
     * @return Root of the variable
     */
    public RAVariable find(RAVariable x) {
        parent.putIfAbsent(x, x);
        return parent.get(x);
    }

    /**
     * Check if a variable is being aliased by some other variable.
     *
     * <p>
     * The parent map has to have an entry, and it has to be different from the variable itself.
     * </p>
     *
     * @param variable Input variable
     * @return Is aliased?
     */
    protected boolean isAliased(RAVariable variable) {
        return parent.containsKey(variable) && !parent.get(variable).equals(variable);
    }
}
