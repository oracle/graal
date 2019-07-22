/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * This state node is responsible for selecting a DFA's initial state based on the index the search
 * starts from. Successors are entry points in case we start matching at the beginning of the input
 * string, followed by entry points in case we do not start matching at the beginning of the input
 * string. If possible matches must start at the beginning of the input string, entry points may be
 * -1.
 */
public class DFAInitialStateNode extends DFAAbstractStateNode {

    private final boolean searching;
    private final boolean trackCaptureGroups;

    public DFAInitialStateNode(short[] successors, boolean searching, boolean trackCaptureGroups) {
        super(successors);
        this.searching = searching;
        this.trackCaptureGroups = trackCaptureGroups;
    }

    private DFAInitialStateNode(DFAInitialStateNode copy) {
        this(Arrays.copyOf(copy.successors, copy.successors.length), copy.searching, copy.trackCaptureGroups);
    }

    public int getPrefixLength() {
        return (successors.length / 2) - 1;
    }

    public boolean hasUnAnchoredEntry() {
        return successors[successors.length / 2] != -1;
    }

    /**
     * Creates a node split copy of this initial state as described in {@link DFAAbstractStateNode},
     * but ignores copyID, since having two initial states in a DFA is not supported. Therefore,
     * this method should be used for replacing the original initial state with the copy.
     *
     * @param copyID new ID for the copy.
     * @return a node split copy of this initial state as described in {@link DFAAbstractStateNode},
     *         ignoring copyID.
     */
    @Override
    public DFAAbstractStateNode createNodeSplitCopy(short copyID) {
        return new DFAInitialStateNode(this);
    }

    @Override
    public short getId() {
        return 0;
    }

    @Override
    public void executeFindSuccessor(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean compactString) {
        if (searching) {
            locals.setSuccessorIndex(executor.rewindUpTo(locals, getPrefixLength()));
        } else {
            locals.setSuccessorIndex(Math.max(0, Math.min(getPrefixLength(), locals.getFromIndex() - locals.getIndex())));
        }
        if (!executor.atBegin(locals)) {
            locals.setSuccessorIndex(locals.getSuccessorIndex() + (successors.length / 2));
        }
        if (trackCaptureGroups) {
            locals.setLastTransition((short) 0);
        }
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj();
    }
}
