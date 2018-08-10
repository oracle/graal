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

public class TraceFinderDFAStateNode extends BackwardDFAStateNode {

    public static final byte NO_PRE_CALC_RESULT = (byte) 0xff;

    private final byte preCalculatedUnAnchoredResult;
    private final byte preCalculatedAnchoredResult;

    public TraceFinderDFAStateNode(short id, byte flags, LoopOptimizationNode loopOptimizationNode, short[] successors, CharMatcher[] matchers,
                    byte preCalculatedUnAnchoredResult, byte preCalculatedAnchoredResult) {
        super(id, flags, loopOptimizationNode, successors, matchers);
        this.preCalculatedUnAnchoredResult = preCalculatedUnAnchoredResult;
        this.preCalculatedAnchoredResult = preCalculatedAnchoredResult;
    }

    private TraceFinderDFAStateNode(TraceFinderDFAStateNode copy, short copyID) {
        super(copy, copyID);
        this.preCalculatedUnAnchoredResult = copy.preCalculatedUnAnchoredResult;
        this.preCalculatedAnchoredResult = copy.preCalculatedAnchoredResult;
    }

    @Override
    public DFAStateNode createNodeSplitCopy(short copyID) {
        return new TraceFinderDFAStateNode(this, copyID);
    }

    private boolean hasPreCalculatedUnAnchoredResult() {
        return preCalculatedUnAnchoredResult != NO_PRE_CALC_RESULT;
    }

    private int getPreCalculatedUnAnchoredResult() {
        return Byte.toUnsignedInt(preCalculatedUnAnchoredResult);
    }

    private boolean hasPreCalculatedAnchoredResult() {
        return preCalculatedAnchoredResult != NO_PRE_CALC_RESULT;
    }

    private int getPreCalculatedAnchoredResult() {
        return Byte.toUnsignedInt(preCalculatedAnchoredResult);
    }

    @Override
    protected void storeResult(VirtualFrame frame, TRegexDFAExecutorNode executor, int index, boolean anchored) {
        if (anchored) {
            assert hasPreCalculatedAnchoredResult();
            if (hasPreCalculatedUnAnchoredResult() && getPreCalculatedUnAnchoredResult() < getPreCalculatedAnchoredResult()) {
                executor.setResultInt(frame, getPreCalculatedUnAnchoredResult());
            } else {
                executor.setResultInt(frame, getPreCalculatedAnchoredResult());
            }
        } else {
            assert hasPreCalculatedUnAnchoredResult();
            executor.setResultInt(frame, getPreCalculatedUnAnchoredResult());
        }
    }
}
