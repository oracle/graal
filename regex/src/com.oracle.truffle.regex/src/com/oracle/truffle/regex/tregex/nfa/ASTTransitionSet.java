/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nfa;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.tregex.automaton.TransitionBuilder;
import com.oracle.truffle.regex.tregex.util.DebugUtil;

import java.util.ArrayList;
import java.util.Iterator;

public class ASTTransitionSet implements Iterable<ASTTransition> {

    private final ArrayList<ASTTransition> transitions;

    public ASTTransitionSet(ASTTransition transition) {
        this.transitions = new ArrayList<>();
        transitions.add(transition);
    }

    public ASTTransitionSet(ArrayList<ASTTransition> transitions) {
        this.transitions = transitions;
    }

    public ASTTransitionSet createMerged(ASTTransitionSet other) {
        ArrayList<ASTTransition> merged = new ArrayList<>(transitions);
        ASTTransitionSet ret = new ASTTransitionSet(merged);
        ret.merge(other);
        return ret;
    }

    public void mergeInPlace(TransitionBuilder<ASTTransitionSet> other) {
        merge(other.getTargetState());
    }

    private void merge(ASTTransitionSet other) {
        for (ASTTransition t : other) {
            if (!transitions.contains(t)) {
                transitions.add(t);
            }
        }
    }

    @Override
    public Iterator<ASTTransition> iterator() {
        return transitions.iterator();
    }

    @Override
    public int hashCode() {
        return transitions.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof ASTTransitionSet && transitions.equals(((ASTTransitionSet) obj).transitions));
    }

    @CompilerDirectives.TruffleBoundary
    public DebugUtil.Table toTable() {
        DebugUtil.Table table = new DebugUtil.Table("ASTTransitionSet");
        for (ASTTransition t : transitions) {
            table.append(t.toTable());
        }
        return table;
    }
}
