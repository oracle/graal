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

    private static boolean legacySplitting;
    private static boolean splitting;
    private static boolean splittingAllowForcedSplits;
    private static boolean splittingDumpDecisions;
    private static boolean splittingTraceEvents;
    private static boolean traceSplittingSummary;
    private static int splittingMaxCalleeSize;
    private static int splittingMaxPropagationDepth;

    static void reinitialize() {
        legacySplitting = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleLegacySplitting);
        splitting = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleSplitting);
        splittingAllowForcedSplits = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleSplittingAllowForcedSplits);
        splittingDumpDecisions = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleSplittingDumpDecisions);
        splittingMaxCalleeSize = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleSplittingMaxCalleeSize);
        splittingMaxPropagationDepth = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleSplittingMaxPropagationDepth);
        splittingTraceEvents = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleSplittingTraceEvents);
        traceSplittingSummary = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleTraceSplittingSummary);
    }

    static boolean isSplittingDumpDecisions() {
        return splittingDumpDecisions;
    }

    static boolean isLegacySplitting() {
        return legacySplitting;
    }

    static boolean isSplittingAllowForcedSplits() {
        return splittingAllowForcedSplits;
    }

    static boolean isSplitting() {
        return splitting;
    }

    static boolean isSplittingTraceEvents() {
        return splittingTraceEvents;
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
