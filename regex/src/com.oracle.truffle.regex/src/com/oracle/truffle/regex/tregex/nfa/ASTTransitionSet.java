/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.automaton.TransitionSet;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.ArrayList;
import java.util.Iterator;

public class ASTTransitionSet implements TransitionSet, Iterable<ASTTransition> {

    private final ArrayList<ASTTransition> transitions;

    public ASTTransitionSet(ASTTransition transition) {
        this.transitions = new ArrayList<>();
        transitions.add(transition);
    }

    public ASTTransitionSet(ArrayList<ASTTransition> transitions) {
        this.transitions = transitions;
    }

    @Override
    public ASTTransitionSet createMerged(TransitionSet other) {
        ArrayList<ASTTransition> merged = new ArrayList<>(transitions);
        ASTTransitionSet ret = new ASTTransitionSet(merged);
        ret.addAll(other);
        return ret;
    }

    @Override
    public void addAll(TransitionSet other) {
        for (ASTTransition t : (ASTTransitionSet) other) {
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

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.array(transitions);
    }
}
