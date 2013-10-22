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

public class CompilationPolicy {

    private int invokeCounter;
    private int originalInvokeCounter;
    private int loopAndInvokeCounter;
    private long prevTimestamp;

    private final int compilationThreshold;
    private final String name;

    public CompilationPolicy(final int compilationThreshold, final int initialInvokeCounter, final String name) {
        this.invokeCounter = initialInvokeCounter;
        this.loopAndInvokeCounter = compilationThreshold;
        this.originalInvokeCounter = compilationThreshold;
        this.prevTimestamp = System.nanoTime();

        this.compilationThreshold = compilationThreshold;
        this.name = name;
    }

    public String getName() {
        return this.name;
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

    public void compilationInvalidated() {
        int invalidationReprofileCount = TruffleInvalidationReprofileCount.getValue();
        invokeCounter = invalidationReprofileCount;
        if (TruffleFunctionInlining.getValue()) {
            originalInvokeCounter += invalidationReprofileCount;
        }
    }

    public void countInterpreterCall() {
        invokeCounter--;
        loopAndInvokeCounter--;
    }

    public void inlined(int minInvokesAfterInlining) {
        invokeCounter = minInvokesAfterInlining;
        int inliningReprofileCount = TruffleInliningReprofileCount.getValue();
        loopAndInvokeCounter = inliningReprofileCount;
        originalInvokeCounter = inliningReprofileCount;
    }

    public void reportLoopCount(int count) {
        loopAndInvokeCounter = Math.max(0, loopAndInvokeCounter - count);
    }

    public void nodeReplaced() {
        // delay compilation until tree is deemed stable enough
        int replaceBackoff = TruffleReplaceReprofileCount.getValue();
        if (loopAndInvokeCounter < replaceBackoff) {
            loopAndInvokeCounter = replaceBackoff;
        }
    }

    public boolean compileOrInline() {
        if (invokeCounter <= 0 && loopAndInvokeCounter <= 0) {
            if (TruffleUseTimeForCompilationDecision.getValue()) {
                long timestamp = System.nanoTime();
                long timespan = (timestamp - prevTimestamp);
                if (timespan < (TruffleCompilationDecisionTime.getValue())) {
                    return true;
                }
                this.loopAndInvokeCounter = compilationThreshold;
                this.originalInvokeCounter = compilationThreshold;
                this.prevTimestamp = timestamp;
                if (TruffleCompilationDecisionTimePrintFail.getValue()) {
                    // Checkstyle: stop
                    System.out.println(name + ": timespan  " + (timespan / 1000000) + " ms  larger than threshold");
                    // Checkstyle: resume
                }
            } else {
                return true;
            }
        }
        return false;
    }
}
