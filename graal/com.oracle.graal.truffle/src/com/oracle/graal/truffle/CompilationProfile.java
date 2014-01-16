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

public class CompilationProfile {

    private int invokeCounter;
    private int originalInvokeCounter;
    private int loopAndInvokeCounter;
    /**
     * Number of times an installed code for this tree was invalidated.
     */
    private int invalidationCount;

    /**
     * Number of times a node was replaced in this tree.
     */
    private int nodeReplaceCount;

    private long previousTimestamp;

    private final int compilationThreshold;
    private final String name;

    public CompilationProfile(final int compilationThreshold, final int initialInvokeCounter, final String name) {
        this.invokeCounter = initialInvokeCounter;
        this.loopAndInvokeCounter = compilationThreshold;
        this.originalInvokeCounter = compilationThreshold;
        this.previousTimestamp = System.nanoTime();

        this.compilationThreshold = compilationThreshold;
        this.name = name;
    }

    public long getPreviousTimestamp() {
        return previousTimestamp;
    }

    public String getName() {
        return this.name;
    }

    public int getInvalidationCount() {
        return invalidationCount;
    }

    public int getNodeReplaceCount() {
        return nodeReplaceCount;
    }

    public int getInvokeCounter() {
        return invokeCounter;
    }

    public int getOriginalInvokeCounter() {
        return originalInvokeCounter;
    }

    public int getLoopAndInvokeCounter() {
        return loopAndInvokeCounter;
    }

    void reportTiminingFailed(long timestamp) {
        this.loopAndInvokeCounter = compilationThreshold;
        this.originalInvokeCounter = compilationThreshold;
        this.previousTimestamp = timestamp;
    }

    void reportInvalidated() {
        invalidationCount++;
        int invalidationReprofileCount = TruffleInvalidationReprofileCount.getValue();
        invokeCounter = invalidationReprofileCount;
        originalInvokeCounter += invalidationReprofileCount;
    }

    void reportInterpreterCall() {
        invokeCounter--;
        loopAndInvokeCounter--;
    }

    void reportInliningPerformed(TruffleInlining inlining) {
        invokeCounter = inlining.getInvocationReprofileCount();
        int inliningReprofileCount = inlining.getReprofileCount();
        loopAndInvokeCounter = inliningReprofileCount;
        originalInvokeCounter = inliningReprofileCount;
    }

    void reportLoopCount(int count) {
        loopAndInvokeCounter = Math.max(0, loopAndInvokeCounter - count);
    }

    void reportNodeReplaced() {
        nodeReplaceCount++;
        // delay compilation until tree is deemed stable enough
        int replaceBackoff = TruffleReplaceReprofileCount.getValue();
        if (loopAndInvokeCounter < replaceBackoff) {
            loopAndInvokeCounter = replaceBackoff;
        }
    }

}
