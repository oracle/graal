/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.deopt;

import static com.oracle.svm.core.snippets.KnownIntrinsics.convertUnknownValue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.oracle.svm.core.meta.SubstrateObjectConstant;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

// Checkstyle: allow synchronization

public class SubstrateSpeculationLog implements SpeculationLog {

    public static final class SubstrateSpeculation extends Speculation {
        public SubstrateSpeculation(SpeculationReason reason) {
            super(reason);
        }
    }

    private static final class LogEntry {
        private final SpeculationReason reason;
        private final LogEntry next;

        private LogEntry(SpeculationReason reason, LogEntry next) {
            this.reason = reason;
            this.next = next;
        }
    }

    /** The collected set of speculations, for quick access during compilation. */
    private Map<SpeculationReason, Boolean> failedSpeculations;

    /**
     * Newly added speculation failures. Atomic linked list to allow lock free append during
     * deoptimization.
     */
    private volatile LogEntry addedFailedSpeculationsHead;

    private static final AtomicReferenceFieldUpdater<SubstrateSpeculationLog, LogEntry> HEAD_UPDATER = AtomicReferenceFieldUpdater.newUpdater(SubstrateSpeculationLog.class,
                    LogEntry.class, "addedFailedSpeculationsHead");

    public void addFailedSpeculation(SpeculationReason speculation) {
        /*
         * This method is called from inside the VMOperation that performs deoptimization, and
         * thefore must not be synchronization free. Note that this even precludes using a
         * ConcurrentHashMap, because it also has some code paths that require synchronization.
         *
         * Therefore we use our own very simple atomic linked list.
         */
        while (true) {
            LogEntry oldHead = addedFailedSpeculationsHead;
            LogEntry newHead = new LogEntry(speculation, oldHead);
            if (HEAD_UPDATER.compareAndSet(this, oldHead, newHead)) {
                break;
            }
        }
    }

    @Override
    public synchronized void collectFailedSpeculations() {
        LogEntry cur = HEAD_UPDATER.getAndSet(this, null);
        while (cur != null) {
            if (failedSpeculations == null) {
                failedSpeculations = new HashMap<>();
            }
            failedSpeculations.put(cur.reason, Boolean.TRUE);
            cur = cur.next;
        }
    }

    @Override
    public synchronized boolean maySpeculate(SpeculationReason reason) {
        return failedSpeculations == null || !failedSpeculations.containsKey(reason);
    }

    @Override
    public Speculation speculate(SpeculationReason reason) {
        if (!maySpeculate(reason)) {
            throw new IllegalArgumentException("Cannot make speculation with reason " + reason + " as it is known to fail");
        }
        return new SubstrateSpeculation(reason);
    }

    @Override
    public boolean hasSpeculations() {
        return true;
    }

    @Override
    public Speculation lookupSpeculation(JavaConstant constant) {
        return new SubstrateSpeculation((SpeculationReason) convertUnknownValue(SubstrateObjectConstant.asObject(constant), Object.class));
    }
}
