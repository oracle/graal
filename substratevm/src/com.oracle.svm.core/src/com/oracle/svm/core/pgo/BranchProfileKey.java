/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.pgo;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.guest.staging.core.heap.UnknownObjectField;
import com.oracle.svm.guest.staging.core.heap.UnknownPrimitiveField;
import com.oracle.svm.shared.BuildPhaseProvider.AfterCompilation;

/**
 * The key that the counts of one branch belong to: the identity of an {@code IfNode} as the
 * instrumentation finds it, before inlining, and the index of its true and false counts. The
 * branch's two {@code BranchProfileCounterNode}s, and every copy that later transformations make of
 * them, count under this one key.
 */
public final class BranchProfileKey {

    /** Index of a key that no compiled code uses (yet). */
    static final int NO_INDEX = -1;

    /** Calling context of the branch, innermost frame first. */
    @UnknownObjectField(availability = AfterCompilation.class) private final ProfileFrame[] context;
    /** BCIs of the true and the false successor. */
    @UnknownPrimitiveField(availability = AfterCompilation.class) private final int trueSuccessorBci;
    @UnknownPrimitiveField(availability = AfterCompilation.class) private final int falseSuccessorBci;
    /**
     * Name of the class of the tested condition node, for example
     * {@code jdk.graal.compiler.nodes.calc.IntegerLessThanNode}.
     */
    @UnknownObjectField(availability = AfterCompilation.class) private final String conditionKind;
    /**
     * A 64-bit hash of the tested condition's shape: the classes and source positions of the
     * branch, of its condition node and of the condition's inputs, three levels deep (see
     * {@code ProfilingUtilities.conditionFingerprint}). The writer sums keys that share a calling
     * context and successor bytecode indexes into one entry. When the successors have no bytecode
     * position (a branch inside an inlined intrinsic or substitution, or on an exception edge),
     * keys of different branches look alike that way, so the writer merges them only when their
     * kind and fingerprint also match, that is, when they test the same condition (see
     * {@code IprofWriter}).
     */
    @UnknownPrimitiveField(availability = AfterCompilation.class) private final long conditionFingerprint;
    /**
     * Index of the key's true and false counts in the per-thread counter blocks and the shared
     * totals of {@link BranchProfileRecorder}. {@link #NO_INDEX} until a counter node of this key
     * reaches machine code (see {@link #counterIndexForCode()}).
     */
    @UnknownPrimitiveField(availability = AfterCompilation.class) private int counterIndex;

    BranchProfileKey(ProfileFrame[] context, int trueSuccessorBci, int falseSuccessorBci, String conditionKind, long conditionFingerprint, int counterIndex) {
        this.context = context;
        this.trueSuccessorBci = trueSuccessorBci;
        this.falseSuccessorBci = falseSuccessorBci;
        this.conditionKind = conditionKind;
        this.conditionFingerprint = conditionFingerprint;
        this.counterIndex = counterIndex;
    }

    /** The calling context, innermost frame first. Callers must not modify the array. */
    public ProfileFrame[] getContext() {
        return context;
    }

    public int getTrueSuccessorBci() {
        return trueSuccessorBci;
    }

    public int getFalseSuccessorBci() {
        return falseSuccessorBci;
    }

    public String getConditionKind() {
        return conditionKind;
    }

    public long getConditionFingerprint() {
        return conditionFingerprint;
    }

    public int getCounterIndex() {
        return counterIndex;
    }

    /**
     * Returns the counter index for compiled code, numbering the key when the first of its counter
     * nodes is lowered. Keys in graphs that never reach machine code, such as callees the priority
     * inliner expands and then drops, get no counter memory and no registry entry.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized int counterIndexForCode() {
        if (counterIndex == NO_INDEX) {
            counterIndex = BranchProfileRecorder.register(this);
        }
        return counterIndex;
    }

    public long getTrueCount() {
        return BranchProfileRecorder.getTrueCount(counterIndex);
    }

    public long getFalseCount() {
        return BranchProfileRecorder.getFalseCount(counterIndex);
    }
}
