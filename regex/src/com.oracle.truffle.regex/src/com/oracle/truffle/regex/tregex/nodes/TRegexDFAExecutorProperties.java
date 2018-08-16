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
package com.oracle.truffle.regex.tregex.nodes;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;

public final class TRegexDFAExecutorProperties {

    private final FrameDescriptor frameDescriptor;
    private final FrameSlot inputFS;
    private final FrameSlot fromIndexFS;
    private final FrameSlot indexFS;
    private final FrameSlot maxIndexFS;
    private final FrameSlot curMaxIndexFS;
    private final FrameSlot successorIndexFS;
    private final FrameSlot resultFS;
    private final FrameSlot captureGroupResultFS;
    private final FrameSlot lastTransitionFS;
    private final FrameSlot cgDataFS;
    private final FrameSlot inputIsCompactStringFS;
    private final boolean forward;
    private final boolean searching;
    private final boolean trackCaptureGroups;
    private final boolean regressionTestMode;
    private final int numberOfCaptureGroups;
    private final int minResultLength;

    public TRegexDFAExecutorProperties(FrameDescriptor frameDescriptor,
                    FrameSlot inputFS,
                    FrameSlot fromIndexFS,
                    FrameSlot indexFS,
                    FrameSlot maxIndexFS,
                    FrameSlot curMaxIndexFS,
                    FrameSlot successorIndexFS,
                    FrameSlot resultFS,
                    FrameSlot captureGroupResultFS,
                    FrameSlot lastTransitionFS,
                    FrameSlot cgDataFS,
                    FrameSlot inputIsCompactStringFS,
                    boolean forward,
                    boolean searching,
                    boolean trackCaptureGroups,
                    boolean regressionTestMode,
                    int numberOfCaptureGroups,
                    int minResultLength) {
        this.frameDescriptor = frameDescriptor;
        this.inputFS = inputFS;
        this.fromIndexFS = fromIndexFS;
        this.indexFS = indexFS;
        this.maxIndexFS = maxIndexFS;
        this.curMaxIndexFS = curMaxIndexFS;
        this.lastTransitionFS = lastTransitionFS;
        this.successorIndexFS = successorIndexFS;
        this.resultFS = resultFS;
        this.captureGroupResultFS = captureGroupResultFS;
        this.cgDataFS = cgDataFS;
        this.inputIsCompactStringFS = inputIsCompactStringFS;
        this.forward = forward;
        this.searching = searching;
        this.trackCaptureGroups = trackCaptureGroups;
        this.regressionTestMode = regressionTestMode;
        this.numberOfCaptureGroups = numberOfCaptureGroups;
        this.minResultLength = minResultLength;
    }

    public FrameDescriptor getFrameDescriptor() {
        return frameDescriptor;
    }

    public FrameSlot getInputFS() {
        return inputFS;
    }

    public FrameSlot getFromIndexFS() {
        return fromIndexFS;
    }

    public FrameSlot getIndexFS() {
        return indexFS;
    }

    public FrameSlot getMaxIndexFS() {
        return maxIndexFS;
    }

    public FrameSlot getCurMaxIndexFS() {
        return curMaxIndexFS;
    }

    public FrameSlot getSuccessorIndexFS() {
        return successorIndexFS;
    }

    public FrameSlot getResultFS() {
        return resultFS;
    }

    public FrameSlot getCaptureGroupResultFS() {
        return captureGroupResultFS;
    }

    public FrameSlot getLastTransitionFS() {
        return lastTransitionFS;
    }

    public FrameSlot getCgDataFS() {
        return cgDataFS;
    }

    public FrameSlot getInputIsCompactStringFS() {
        return inputIsCompactStringFS;
    }

    public boolean isForward() {
        return forward;
    }

    public boolean isBackward() {
        return !forward;
    }

    public boolean isSearching() {
        return searching;
    }

    public boolean isTrackCaptureGroups() {
        return trackCaptureGroups;
    }

    public boolean isRegressionTestMode() {
        return regressionTestMode;
    }

    public int getNumberOfCaptureGroups() {
        return numberOfCaptureGroups;
    }

    public int getMinResultLength() {
        return minResultLength;
    }
}
