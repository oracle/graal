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
package com.oracle.truffle.regex.tregex.dfa;

import com.oracle.truffle.regex.tregex.automaton.StateTransitionCanonicalizer;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;

import java.util.Iterator;

public class DFATransitionCanonicalizer extends StateTransitionCanonicalizer<NFATransitionSet, DFAStateTransitionBuilder> {

    private final boolean trackCaptureGroups;

    public DFATransitionCanonicalizer(boolean trackCaptureGroups) {
        this.trackCaptureGroups = trackCaptureGroups;
    }

    @Override
    protected boolean isSameTargetMergeAllowed(DFAStateTransitionBuilder a, DFAStateTransitionBuilder b) {
        if (!trackCaptureGroups) {
            return true;
        }
        assert a.getTransitionSet().isForward() && b.getTransitionSet().isForward();
        assert a.getTransitionSet().equals(b.getTransitionSet());
        Iterator<NFAStateTransition> ia = a.getTransitionSet().iterator();
        Iterator<NFAStateTransition> ib = b.getTransitionSet().iterator();
        while (ia.hasNext()) {
            final NFAStateTransition lastA = ia.next();
            final NFAStateTransition lastB = ib.next();
            // implied by a.getTransitionSet().equals(b.getTransitionSet())
            assert lastA.getTarget().equals(lastB.getTarget());
            if (!(lastA.getSource().equals(lastB.getSource()) && lastA.getGroupBoundaries().equals(lastB.getGroupBoundaries()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected DFAStateTransitionBuilder[] createResultArray(int size) {
        return new DFAStateTransitionBuilder[size];
    }
}
