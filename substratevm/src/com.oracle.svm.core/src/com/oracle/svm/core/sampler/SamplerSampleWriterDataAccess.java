/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.sampler;

import org.graalvm.word.Pointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrThreadLocal;
import com.oracle.svm.core.jfr.SubstrateJVM;

/**
 * Helper class that holds methods related to {@link SamplerSampleWriterData}.
 */
public final class SamplerSampleWriterDataAccess {

    private SamplerSampleWriterDataAccess() {
    }

    /**
     * Initialize the {@link SamplerSampleWriterData data} so that it uses the current thread's
     * native buffer.
     */
    @Uninterruptible(reason = "Accesses a sampler buffer", callerMustBe = true)
    public static boolean initialize(SamplerSampleWriterData data, int skipCount, boolean allowBufferAllocation) {
        SamplerBuffer buffer = JfrThreadLocal.getSamplerBuffer();
        if (buffer.isNull()) {
            buffer = SubstrateJVM.getSamplerBufferPool().acquireBuffer(allowBufferAllocation);
            if (buffer.isNull()) {
                /* No buffer available. */
                JfrThreadLocal.increaseMissedSamples();
                return false;
            }
            JfrThreadLocal.setSamplerBuffer(buffer);
        }
        initialize0(data, buffer, skipCount, SubstrateJVM.getStackTraceRepo().getStackTraceDepth(), allowBufferAllocation);
        return true;
    }

    /**
     * Initialize the {@link SamplerSampleWriterData data} so that it uses the given buffer.
     */
    @Uninterruptible(reason = "Accesses a sampler buffer", callerMustBe = true)
    public static void initialize0(SamplerSampleWriterData data, SamplerBuffer buffer, int skipCount, int maxDepth, boolean allowBufferAllocation) {
        assert SamplerBufferAccess.verify(buffer);

        data.setSamplerBuffer(buffer);
        data.setStartPos(buffer.getPos());
        data.setCurrentPos(buffer.getPos());
        data.setEndPos(SamplerBufferAccess.getDataEnd(buffer));
        data.setHashCode(1);
        data.setMaxDepth(maxDepth);
        data.setTruncated(false);
        data.setSkipCount(skipCount);
        data.setSeenFrames(0);
        data.setAllowBufferAllocation(allowBufferAllocation);
    }

    @Uninterruptible(reason = "Accesses a native JFR buffer.", callerMustBe = true)
    public static boolean verify(SamplerSampleWriterData data) {
        if (data.isNull() || !SamplerBufferAccess.verify(data.getSamplerBuffer())) {
            return false;
        }

        SamplerBuffer buffer = data.getSamplerBuffer();
        Pointer dataStart = SamplerBufferAccess.getDataStart(buffer);
        Pointer dataEnd = SamplerBufferAccess.getDataEnd(buffer);

        return data.getStartPos() == buffer.getPos() &&
                        (data.getEndPos() == dataEnd || data.getEndPos().isNull()) &&
                        data.getCurrentPos().aboveOrEqual(dataStart) && data.getCurrentPos().belowOrEqual(dataEnd) && data.getCurrentPos().aboveOrEqual(data.getStartPos());
    }
}
