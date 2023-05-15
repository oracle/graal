/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;

public final class OldObjectEventEmitter {
    private final OldObjectList list;
    private final OldObjectEffects effects;

    public OldObjectEventEmitter(OldObjectList list, OldObjectEffects effects) {
        this.list = list;
        this.effects = effects;
    }

    @Uninterruptible(reason = "Access protected by lock.")
    public void emit(long cutoff) {
        if (cutoff <= 0) {
            // No reference chains
            emitUnchained();
        }

        // todo support cutoff > 0 (path-to-gc-roots)
    }

    @Uninterruptible(reason = "Access protected by lock.")
    private void emitUnchained() {
        final long timestamp = effects.elapsedTicks();

        OldObject current = list.head();
        while (current != null) {
            if (current.reference != null) {
                final Object obj = effects.getWeakReferent(current.reference);
                final long allocationTime = current.allocationTime;
                /*
                 * Should delegate checking that the reference is alive, even if the weak referent
                 * has already been retrieved.
                 */
                if (effects.isAlive(current.reference)) {
                    final long objectSize = current.objectSize;
                    final long threadId = current.threadId;
                    final long stackTraceId = current.stackTraceId;
                    final long heapUsedAtLastGC = current.heapUsedAtLastGC;
                    final int arrayLength = current.arrayLength;
                    effects.emit(obj, timestamp, objectSize, allocationTime, threadId, stackTraceId, heapUsedAtLastGC, arrayLength);
                }
            }

            current = current.previous;
        }
    }
}
