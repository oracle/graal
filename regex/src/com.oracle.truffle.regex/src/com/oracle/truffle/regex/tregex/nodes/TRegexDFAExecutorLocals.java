/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Container for all local variables used in
 * {@link TRegexDFAExecutorNode#execute(TRegexDFAExecutorLocals, boolean)}.
 */
public final class TRegexDFAExecutorLocals {

    private final Object input;
    private final int fromIndex;
    private final int maxIndex;
    private int index;
    private int curMaxIndex;
    private int successorIndex;
    private int result;
    private int[] captureGroupResult;
    private short lastTransition;
    private final DFACaptureGroupTrackingData cgData;

    public TRegexDFAExecutorLocals(Object input, int fromIndex, int index, int maxIndex, DFACaptureGroupTrackingData cgData) {
        this.input = input;
        this.fromIndex = fromIndex;
        this.index = index;
        this.maxIndex = maxIndex;
        this.cgData = cgData;
    }

    /**
     * The {@code input} argument given to {@link TRegexExecRootNode#execute(Object, int)}.
     *
     * @return the {@code input} argument given to {@link TRegexExecRootNode#execute(Object, int)}.
     */
    public Object getInput() {
        return input;
    }

    /**
     * The {@code fromIndex} argument given to {@link TRegexExecRootNode#execute(Object, int)}.
     *
     * @return the {@code fromIndex} argument given to
     *         {@link TRegexExecRootNode#execute(Object, int)}.
     */
    public int getFromIndex() {
        return fromIndex;
    }

    /**
     * The maximum index as given by the parent {@link TRegexExecRootNode}.
     *
     * @return the maximum index as given by the parent {@link TRegexExecRootNode}.
     */
    public int getMaxIndex() {
        return maxIndex;
    }

    /**
     * The index pointing into {@link #getInput()}.
     *
     * @return the current index of {@link #getInput()} that is being processed.
     */
    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    /**
     * The maximum index as checked by
     * {@link TRegexDFAExecutorNode#hasNext(TRegexDFAExecutorLocals)}. In most cases this value is
     * equal to {@link #getMaxIndex()}, but backward matching nodes change this value while
     * matching.
     *
     * @return the maximum index as checked by
     *         {@link TRegexDFAExecutorNode#hasNext(TRegexDFAExecutorLocals)}.
     *
     * @see BackwardDFAStateNode
     */
    public int getCurMaxIndex() {
        return curMaxIndex;
    }

    public void setCurMaxIndex(int curMaxIndex) {
        this.curMaxIndex = curMaxIndex;
    }

    public short getLastTransition() {
        return lastTransition;
    }

    public void setLastTransition(short lastTransition) {
        this.lastTransition = lastTransition;
    }

    public int getSuccessorIndex() {
        return successorIndex;
    }

    public void setSuccessorIndex(int successorIndex) {
        this.successorIndex = successorIndex;
    }

    public int getResultInt() {
        return result;
    }

    public void setResultInt(int result) {
        this.result = result;
    }

    public int[] getResultCaptureGroups() {
        return captureGroupResult;
    }

    public void setResultObject(int[] captureGroupsResult) {
        this.captureGroupResult = captureGroupsResult;
    }

    public DFACaptureGroupTrackingData getCGData() {
        return cgData;
    }
}
