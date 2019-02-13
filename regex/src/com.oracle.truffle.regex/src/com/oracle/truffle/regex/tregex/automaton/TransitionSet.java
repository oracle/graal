/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.automaton;

import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;

import java.util.Collection;

/**
 * Represents a set of NFA transitions to be used in {@link TransitionBuilder}.
 *
 * The set of NFA transitions implicitly also represents a set of NFA <em>target states</em>, which
 * should be used to determine equality between instances of this class.
 */
public interface TransitionSet extends JsonConvertible {

    /**
     * Create a merged set of {@code this} and {@code other} by copying {@code this} and adding the
     * contents of {@code other} to the copy.
     * 
     * @param other the {@link TransitionSet} to be merged with the copy. Implementing classes may
     *            accept objects of their own type only.
     * @return the merged set. <strong>MUST</strong> be of the same type as {@code this}!
     */
    TransitionSet createMerged(TransitionSet other);

    /**
     * Add the contents of {@code other} to {@code this}, analogous to
     * {@link java.util.Set#addAll(Collection)}.
     * 
     * @param other the {@link TransitionSet} to be merged with {@code this}. Implementing classes
     *            may accept objects of their own type only.
     */
    void addAll(TransitionSet other);

    /**
     * Returns the hash code value for this object.
     *
     * The hash should be calculated from the <strong>set of target states</strong> represented by
     * this transition set!
     */
    @Override
    int hashCode();

    /**
     * Checks if this transition set is equal to another.
     *
     * Two transition sets should be treated as equal if and only if their <strong>set of target
     * states</strong> is equal!
     */
    @Override
    boolean equals(Object obj);
}
