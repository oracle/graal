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
package com.oracle.truffle.regex.tregex.nodes;

import com.oracle.truffle.regex.tregex.matchers.CharMatcher;

public class BackwardDFAStateNode extends DFAStateNode {

    public BackwardDFAStateNode(short id, byte flags, LoopOptimizationNode loopOptimizationNode, short[] successors, CharMatcher[] matchers,
                    AllTransitionsInOneTreeMatcher allTransitionsInOneTreeMatcher) {
        super(id, flags, loopOptimizationNode, successors, matchers, allTransitionsInOneTreeMatcher);
    }

    protected BackwardDFAStateNode(BackwardDFAStateNode copy, short copyID) {
        super(copy, copyID);
    }

    @Override
    public DFAStateNode createNodeSplitCopy(short copyID) {
        return new BackwardDFAStateNode(this, copyID);
    }

    private int getBackwardPrefixStateIndex() {
        assert hasBackwardPrefixState();
        return getSuccessors().length - 1;
    }

    @Override
    int prevIndex(TRegexDFAExecutorLocals locals) {
        return locals.getIndex() + 1;
    }

    @Override
    int atEnd(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        super.atEnd(locals, executor);
        if (hasBackwardPrefixState() && locals.getIndex() == locals.getFromIndex() - 1 && locals.getFromIndex() - 1 > locals.getMaxIndex()) {
            locals.setCurMaxIndex(locals.getMaxIndex());
            return getBackwardPrefixStateIndex();
        }
        return FS_RESULT_NO_SUCCESSOR;
    }
}
