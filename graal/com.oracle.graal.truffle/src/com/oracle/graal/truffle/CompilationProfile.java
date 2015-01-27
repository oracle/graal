/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle;

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.util.*;

public class CompilationProfile {

    /**
     * Number of times an installed code for this tree was invalidated.
     */
    private int invalidationCount;
    private int deferedCount;

    private int interpreterCallCount;
    private int interpreterCallAndLoopCount;
    private int compilationCallThreshold;
    private int compilationCallAndLoopThreshold;

    private long timestamp;

    public CompilationProfile() {
        compilationCallThreshold = TruffleMinInvokeThreshold.getValue();
        compilationCallAndLoopThreshold = TruffleCompilationThreshold.getValue();
    }

    @Override
    public String toString() {
        return String.format("CompilationProfile(callCount=%d/%d, callAndLoopCount=%d/%d)", interpreterCallCount, compilationCallThreshold, interpreterCallAndLoopCount,
                        compilationCallAndLoopThreshold);
    }

    public Map<String, Object> getDebugProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        String callsThreshold = String.format("%7d/%5d", getInterpreterCallCount(), getCompilationCallThreshold());
        String loopsThreshold = String.format("%7d/%5d", getInterpreterCallAndLoopCount(), getCompilationCallAndLoopThreshold());
        String invalidations = String.format("%5d", invalidationCount);
        properties.put("Calls/Thres", callsThreshold);
        properties.put("CallsAndLoop/Thres", loopsThreshold);
        properties.put("Inval#", invalidations);
        return properties;
    }

    public int getInvalidationCount() {
        return invalidationCount;
    }

    public int getInterpreterCallAndLoopCount() {
        return interpreterCallAndLoopCount;
    }

    public int getInterpreterCallCount() {
        return interpreterCallCount;
    }

    public int getDeferedCount() {
        return deferedCount;
    }

    public int getCompilationCallAndLoopThreshold() {
        return compilationCallAndLoopThreshold;
    }

    public int getCompilationCallThreshold() {
        return compilationCallThreshold;
    }

    void ensureProfiling(int calls, int callsAndLoop) {
        int increaseCallAndLoopThreshold = callsAndLoop - (this.compilationCallAndLoopThreshold - this.interpreterCallAndLoopCount);
        if (increaseCallAndLoopThreshold > 0) {
            this.compilationCallAndLoopThreshold += increaseCallAndLoopThreshold;
        }

        int increaseCallsThreshold = calls - (this.compilationCallThreshold - this.interpreterCallCount);
        if (increaseCallsThreshold > 0) {
            this.compilationCallThreshold += increaseCallsThreshold;
        }
    }

    public void reportInvalidated() {
        invalidationCount++;
        int reprofile = TruffleInvalidationReprofileCount.getValue();
        ensureProfiling(reprofile, reprofile);
    }

    public void reportInterpreterCall() {
        interpreterCallCount++;
        interpreterCallAndLoopCount++;

        int callsMissing = compilationCallAndLoopThreshold - interpreterCallAndLoopCount;
        if (callsMissing == getTimestampThreshold()) {
            timestamp = System.nanoTime();
        }
    }

    public void reportDirectCall() {

    }

    public void reportIndirectCall() {

    }

    public void reportInlinedCall() {

    }

    public void deferCompilation() {
        ensureProfiling(0, getTimestampThreshold() + 1);
        timestamp = 0;
        deferedCount++;
    }

    void reportLoopCount(int count) {
        interpreterCallAndLoopCount += count;

        int callsMissing = compilationCallAndLoopThreshold - interpreterCallAndLoopCount;
        if (callsMissing <= getTimestampThreshold() && callsMissing + count > getTimestampThreshold()) {
            timestamp = System.nanoTime();
        }
    }

    void reportNodeReplaced() {
        // delay compilation until tree is deemed stable enough
        int replaceBackoff = TruffleReplaceReprofileCount.getValue();
        ensureProfiling(1, replaceBackoff);
    }

    public long getTimestamp() {
        return timestamp;
    }

    private static int getTimestampThreshold() {
        return Math.max(TruffleCompilationThreshold.getValue() / 2, 1);
    }
}
