/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.warmup.impl;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;

class WarmupEstimatorNode extends ExecutionEventNode {

    private final List<Long> times;
    @CompilerDirectives.CompilationFinal private volatile FrameSlot startSlot;

    WarmupEstimatorNode(List<Long> times) {
        this.times = times;
    }

    @Override
    protected void onEnter(VirtualFrame frame) {
        if (this.startSlot == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Lock lock = getLock();
            lock.lock();
            try {
                startSlot = frame.getFrameDescriptor().findOrAddFrameSlot(this, FrameSlotKind.Long);
            } finally {
                lock.unlock();
            }
        }
        frame.setLong(startSlot, System.nanoTime());
    }

    @Override
    protected void onReturnValue(VirtualFrame frame, Object result) {
        if (startSlot != null) {
            final long end = System.nanoTime();
            record(end - FrameUtil.getLongSafe(frame, startSlot));
        }
    }

    @CompilerDirectives.TruffleBoundary
    private synchronized void record(long duration) {
        times.add(duration);
    }

    synchronized List<Long> getTimes() {
        return Collections.unmodifiableList(times);
    }
}
