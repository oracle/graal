/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

/**
 * A cache that enables the Runtime options to be read without the lookup. This is intended to only
 * be used on performance critical paths.
 */
class RuntimeOptionsCache {

    private static boolean experimentalSplitting;
    private static boolean experimentalSplittingAllowForcedSplits;
    private static boolean experimentalSplittingDumpDecisions;
    private static boolean experimentalSplittingTraceEvents;
    private static boolean splitting;
    private static boolean traceSplittingSummary;
    private static int splittingMaxCalleeSize;
    private static int splittingMaxPropagationDepth;

    static void reinitialize() {
        experimentalSplitting = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleExperimentalSplitting);
        experimentalSplittingAllowForcedSplits = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleExperimentalSplittingAllowForcedSplits);
        experimentalSplittingDumpDecisions = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleExperimentalSplittingDumpDecisions);
        experimentalSplittingTraceEvents = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleExperimentalSplittingTraceEvents);
        splitting = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleSplitting);
        splittingMaxCalleeSize = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleSplittingMaxCalleeSize);
        splittingMaxPropagationDepth = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleExperimentalSplittingMaxPropagationDepth);
        traceSplittingSummary = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleTraceSplittingSummary);
    }

    static boolean isExperimentalSplittingDumpDecisions() {
        return experimentalSplittingDumpDecisions;
    }

    static boolean isExperimentalSplitting() {
        return experimentalSplitting;
    }

    static boolean isExperimentalSplittingAllowForcedSplits() {
        return experimentalSplittingAllowForcedSplits;
    }

    static boolean isSplitting() {
        return splitting;
    }

    static boolean isExperimentalSplittingTraceEvents() {
        return experimentalSplittingTraceEvents;
    }

    static boolean isTraceSplittingSummary() {
        return traceSplittingSummary;
    }

    static int getSplittingMaxCalleeSize() {
        return splittingMaxCalleeSize;
    }

    static int getSplittingMaxPropagationDepth() {
        return splittingMaxPropagationDepth;
    }
}
