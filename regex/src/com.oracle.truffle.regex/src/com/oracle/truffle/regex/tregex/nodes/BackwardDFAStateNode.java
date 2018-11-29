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
package com.oracle.truffle.regex.tregex.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;

public class BackwardDFAStateNode extends DFAStateNode {

    public BackwardDFAStateNode(short id, byte flags, LoopOptimizationNode loopOptimizationNode, short[] successors, CharMatcher[] matchers) {
        super(id, flags, loopOptimizationNode, successors, matchers);
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
    protected int prevIndex(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        return executor.getIndex(frame) + 1;
    }

    @Override
    protected int atEnd1(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        super.atEnd1(frame, executor);
        return switchToPrefixState(executor, frame);
    }

    @Override
    protected int atEnd2(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        super.atEnd2(frame, executor);
        return switchToPrefixState(executor, frame);
    }

    @Override
    protected int atEnd3(VirtualFrame frame, TRegexDFAExecutorNode executor, int preLoopIndex) {
        super.atEnd3(frame, executor, preLoopIndex);
        return switchToPrefixState(executor, frame);
    }

    private int switchToPrefixState(TRegexDFAExecutorNode executor, VirtualFrame frame) {
        if (executor.getIndex(frame) == executor.getFromIndex(frame) - 1 && executor.getFromIndex(frame) - 1 > executor.getMaxIndex(frame) && hasBackwardPrefixState()) {
            executor.setCurMaxIndex(frame, executor.getMaxIndex(frame));
            return getBackwardPrefixStateIndex();
        }
        return FS_RESULT_NO_SUCCESSOR;
    }
}
