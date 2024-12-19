/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.debug;

import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class SteppingControl {

    /**
     * Step into any newly pushed frames.
     */
    public static final int STEP_INTO = 1;
    /**
     * Step over any newly pushed frames.
     */
    public static final int STEP_OVER = 2;
    /**
     * Step out of the current frame.
     */
    public static final int STEP_OUT = 3;

    /**
     * Step to the next available location.
     */
    public static final int STEP_MIN = -1;
    /**
     * Step to the next location on a different line.
     */
    public static final int STEP_LINE = -2;

    private final Thread thread;
    private final int depth;
    private final int size;

    private int currentFrameDepth;

    // Location where the stepping request was set, required only to check line numbers when
    // STEP_LINE is specified.
    private Location startingLocation;

    public boolean isActiveAtCurrentFrameDepth() {
        switch (getDepth()) {
            case STEP_OUT:
                return currentFrameDepth < 0; // fire events only on outer frames
            case STEP_OVER:
                return currentFrameDepth <= 0; // fire events in the starting and outer frames
            case STEP_INTO:
                return true; // fire events in all frames
            default:
                throw VMError.shouldNotReachHere("unknown stepping depth");
        }
    }

    public SteppingControl(Thread thread, int depth, int size) {
        VMError.guarantee(size == STEP_MIN || size == STEP_LINE);
        VMError.guarantee(depth == STEP_INTO || depth == STEP_OVER || depth == STEP_OUT);
        this.thread = thread;
        this.depth = depth;
        this.size = size;
        this.currentFrameDepth = 0;
    }

    public int getSize() {
        return size;
    }

    public int getDepth() {
        return depth;
    }

    public Thread getThread() {
        return thread;
    }

    public void setStartingLocation(Location location) {
        this.startingLocation = location;
    }

    public boolean withinSameLine(ResolvedJavaMethod method, int bci) {
        if (startingLocation == null) {
            // No starting location set, consider it new line hit.
            return false;
        }
        if (!method.equals(startingLocation.method())) {
            return false;
        }
        // Might need to handle case when no line information is available
        int startingLine = startingLocation.lineNumber();
        int currentLine = Location.getLineNumber(method, bci);
        return (currentLine == startingLine);
    }

    public void popFrame() {
        --currentFrameDepth;
    }

    public void pushFrame() {
        ++currentFrameDepth;
    }

}
