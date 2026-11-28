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
 * The key under which {@link TypeProfileRecorder} counts object types: one per physical indirect
 * call, whose receiver types are counted, and one that every monitor enter shares
 * ({@link TypeProfileRecorder#monitorKey()}).
 */
public final class TypeProfileKey {
    /** Index of a key that no compiled code uses (yet). */
    static final int NO_INDEX = -1;

    /** {@link #NO_INDEX} until a counter node of this key reaches machine code. */
    @UnknownPrimitiveField(availability = AfterCompilation.class) private int siteIndex;
    /** Calling context of the call, innermost frame first; empty for the monitor key. */
    @UnknownObjectField(availability = AfterCompilation.class) private final ProfileFrame[] context;
    /** Receiver types that the static analysis expects at the call, with their names; empty for the monitor key. */
    @UnknownObjectField(availability = AfterCompilation.class) private final int[] receiverTypeIds;
    @UnknownObjectField(availability = AfterCompilation.class) private final String[] receiverTypeDescriptors;
    @UnknownPrimitiveField(availability = AfterCompilation.class) private final boolean monitorKey;

    TypeProfileKey(int siteIndex, ProfileFrame[] context, int[] receiverTypeIds, String[] receiverTypeDescriptors, boolean monitorKey) {
        this.siteIndex = siteIndex;
        this.context = context;
        this.receiverTypeIds = receiverTypeIds.clone();
        this.receiverTypeDescriptors = receiverTypeDescriptors.clone();
        this.monitorKey = monitorKey;
    }

    public int siteIndex() {
        return siteIndex;
    }

    /**
     * Returns the site index for compiled code, numbering the key when the first of its counter
     * nodes is lowered. Keys in graphs that never reach machine code get no index.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized int siteIndexForCode() {
        if (siteIndex == NO_INDEX) {
            siteIndex = TypeProfileRecorder.register(this);
        }
        return siteIndex;
    }

    /** The calling context, innermost frame first. Callers must not modify the array. */
    public ProfileFrame[] context() {
        return context;
    }

    public boolean isMonitorKey() {
        return monitorKey;
    }

    String receiverTypeDescriptor(int typeId) {
        for (int i = 0; i < receiverTypeIds.length; i++) {
            if (receiverTypeIds[i] == typeId) {
                return receiverTypeDescriptors[i];
            }
        }
        return null;
    }
}
