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
 * The key that the count of one call edge belongs to, counted at the callee's entry under the
 * caller's context.
 */
public final class CallCountProfileKey implements CallCountProfileSlot {
    /** Index of a counter that no compiled code uses (yet). */
    static final int NO_INDEX = -1;

    /** {@link #NO_INDEX} until a counter node of this edge reaches machine code. */
    @UnknownPrimitiveField(availability = AfterCompilation.class) private int counterIndex;
    /** Calling context of the callee entry, innermost frame first. */
    @UnknownObjectField(availability = AfterCompilation.class) private final ProfileFrame[] context;

    CallCountProfileKey(int counterIndex, ProfileFrame[] context) {
        this.counterIndex = counterIndex;
        this.context = context;
    }

    public int counterIndex() {
        return counterIndex;
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized int counterIndexForCode() {
        if (counterIndex == NO_INDEX) {
            counterIndex = CallCountProfileRecorder.register(this);
        }
        return counterIndex;
    }

    /** The calling context, innermost frame first. Callers must not modify the array. */
    public ProfileFrame[] context() {
        return context;
    }

    public long count() {
        return CallCountProfileRecorder.getCount(counterIndex);
    }
}
