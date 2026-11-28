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

import java.util.Arrays;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.guest.staging.core.heap.UnknownObjectField;
import com.oracle.svm.shared.BuildPhaseProvider.AfterCompilation;

/**
 * The key that the counts of one switch belong to: its identity and the call-count counter index of
 * each of its successors. Switch successors are counted in the {@link CallCountProfileRecorder}
 * tables, like call edges, but their key is this object, so their indexes have no
 * {@link CallCountProfileKey}.
 */
public final class SwitchProfileKey {
    /** Calling context of the switch, innermost frame first. */
    @UnknownObjectField(availability = AfterCompilation.class) private final ProfileFrame[] context;
    @UnknownObjectField(availability = AfterCompilation.class) private final int[] successorBcis;
    /**
     * Call-count counter of every successor; {@link CallCountProfileKey#NO_INDEX} until the
     * successor's counter node reaches machine code.
     */
    @UnknownObjectField(availability = AfterCompilation.class) private final int[] counterIndexes;

    SwitchProfileKey(ProfileFrame[] context, int[] successorBcis) {
        this.context = context;
        this.successorBcis = successorBcis.clone();
        this.counterIndexes = new int[successorBcis.length];
        Arrays.fill(counterIndexes, CallCountProfileKey.NO_INDEX);
    }

    /** The calling context, innermost frame first. Callers must not modify the array. */
    public ProfileFrame[] context() {
        return context;
    }

    public int successorCount() {
        return successorBcis.length;
    }

    public int successorBci(int index) {
        return successorBcis[index];
    }

    public int counterIndex(int index) {
        return counterIndexes[index];
    }

    public long count(int index) {
        int counterIndex = counterIndexes[index];
        return counterIndex == CallCountProfileKey.NO_INDEX ? 0 : CallCountProfileRecorder.getCount(counterIndex);
    }

    /** The call-count counter of successor {@code index}, for the successor's counter node. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public CallCountProfileSlot successorSlot(int index) {
        return new SuccessorSlot(this, index);
    }

    /**
     * Numbers successor {@code index} when its counter node is first lowered. The switch joins the
     * registry with its first numbered successor; successors whose counter nodes never reach
     * machine code keep no counter and count zero.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    synchronized int successorIndexForCode(int index) {
        if (counterIndexes[index] == CallCountProfileKey.NO_INDEX) {
            boolean firstNumbered = Arrays.stream(counterIndexes).allMatch(i -> i == CallCountProfileKey.NO_INDEX);
            counterIndexes[index] = CallCountProfileRecorder.allocateIndex();
            if (firstNumbered) {
                SwitchProfileRecorder.register(this);
            }
        }
        return counterIndexes[index];
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private record SuccessorSlot(SwitchProfileKey key, int index) implements CallCountProfileSlot {
        @Override
        public int counterIndexForCode() {
            return key.successorIndexForCode(index);
        }
    }
}
