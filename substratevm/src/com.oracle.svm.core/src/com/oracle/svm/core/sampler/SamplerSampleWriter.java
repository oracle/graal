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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.jfr.JfrThreadState;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;

public final class SamplerSampleWriter {

    public static final long END_MARKER = -1;
    public static final int IP_SIZE = Long.BYTES;
    public static final int END_MARKER_SIZE = Long.BYTES;

    private SamplerSampleWriter() {
    }

    @Fold
    public static int getHeaderSize() {
        /* sample hash + is truncated + sample size + tick + thread state. */
        return Integer.BYTES + Integer.BYTES + Integer.BYTES + Long.BYTES + Long.BYTES;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void begin(SamplerSampleWriterData data) {
        assert isValid(data);
        assert getUncommittedSize(data).equal(0);

        /* Sample hash. (will be patched later) */
        SamplerSampleWriter.putInt(data, 0);
        /* Is truncated? (will be patched later) */
        SamplerSampleWriter.putInt(data, 0);
        /* Sample size. (will be patched later) */
        SamplerSampleWriter.putInt(data, 0);
        /* Tick. */
        SamplerSampleWriter.putLong(data, JfrTicks.elapsedTicks());
        /* Thread state. */
        SamplerSampleWriter.putLong(data, JfrThreadState.getId(Thread.State.RUNNABLE));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void end(SamplerSampleWriterData data) {
        assert isValid(data);

        UnsignedWord sampleSize = getSampleSize(data);
        /* Put end marker. */
        putUncheckedLong(data, END_MARKER);

        Pointer currentPos = data.getCurrentPos();
        data.setCurrentPos(data.getStartPos());
        /* Patch sample hash. */
        putUncheckedInt(data, data.getHashCode());
        /* Patch is truncated. */
        putUncheckedInt(data, data.getTruncated() ? 1 : 0);
        /* Patch sample size. */
        putUncheckedInt(data, (int) sampleSize.rawValue());
        data.setCurrentPos(currentPos);

        commit(data);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean putLong(SamplerSampleWriterData data, long value) {
        if (ensureSize(data, Long.BYTES)) {
            putUncheckedLong(data, value);
            return true;
        } else {
            return false;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void putUncheckedLong(SamplerSampleWriterData data, long value) {
        /* This method is only called if ensureSize() succeeded earlier. */
        assert getAvailableSize(data).aboveOrEqual(Long.BYTES);
        data.getCurrentPos().writeLong(0, value);
        increaseCurrentPos(data, WordFactory.unsigned(Long.BYTES));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean putInt(SamplerSampleWriterData data, int value) {
        if (ensureSize(data, Integer.BYTES)) {
            putUncheckedInt(data, value);
            return true;
        } else {
            return false;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void putUncheckedInt(SamplerSampleWriterData data, int value) {
        /* This method is only called if ensureSize() succeeded earlier. */
        assert getAvailableSize(data).aboveOrEqual(Integer.BYTES);
        data.getCurrentPos().writeInt(0, value);
        increaseCurrentPos(data, WordFactory.unsigned(Integer.BYTES));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void commit(SamplerSampleWriterData data) {
        SamplerBuffer buffer = data.getSamplerBuffer();
        assert isValid(data);
        assert buffer.getPos().equal(data.getStartPos());
        assert SamplerBufferAccess.getDataEnd(data.getSamplerBuffer()).equal(data.getEndPos());

        buffer.setPos(data.getCurrentPos());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean ensureSize(SamplerSampleWriterData data, int requested) {
        assert requested > 0;
        if (!isValid(data)) {
            return false;
        }

        int totalRequested = requested + END_MARKER_SIZE;
        if (getAvailableSize(data).belowThan(totalRequested)) {
            if (!accommodate(data, getUncommittedSize(data))) {
                assert !isValid(data);
                return false;
            }
        }
        assert getAvailableSize(data).aboveOrEqual(totalRequested);
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean accommodate(SamplerSampleWriterData data, UnsignedWord uncommitted) {
        if (SamplerBufferAccess.isEmpty(data.getSamplerBuffer())) {
            /*
             * Sample is too big to fit into the size of one buffer i.e. we want to do
             * accommodations while nothing is committed into buffer.
             */
            SamplerThreadLocal.increaseMissedSamples();
            return false;
        }

        /* Pop first free buffer from the pool. */
        SamplerBuffer newBuffer = SubstrateSigprofHandler.singleton().availableBuffers().popBuffer();
        if (newBuffer.isNull()) {
            /* No available buffers on the pool. Fallback! */
            SamplerThreadLocal.increaseMissedSamples();
            return false;
        }
        SamplerThreadLocal.setThreadLocalBuffer(newBuffer);

        /* Copy the uncommitted content of old buffer into new one. */
        UnmanagedMemoryUtil.copy(data.getStartPos(), SamplerBufferAccess.getDataStart(newBuffer), uncommitted);

        /* Put in the stack with other unprocessed buffers and send a signal to the JFR recorder. */
        SamplerBuffer oldBuffer = data.getSamplerBuffer();
        SubstrateSigprofHandler.singleton().fullBuffers().pushBuffer(oldBuffer);
        SubstrateJVM.getRecorderThread().signal();

        /* Reinitialize data structure. */
        data.setSamplerBuffer(newBuffer);
        reset(data);
        increaseCurrentPos(data, uncommitted);
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void reset(SamplerSampleWriterData data) {
        SamplerBuffer buffer = data.getSamplerBuffer();
        data.setStartPos(buffer.getPos());
        data.setCurrentPos(buffer.getPos());
        data.setEndPos(SamplerBufferAccess.getDataEnd(buffer));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isValid(SamplerSampleWriterData data) {
        return data.getEndPos().isNonNull();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord getAvailableSize(SamplerSampleWriterData data) {
        return data.getEndPos().subtract(data.getCurrentPos());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord getUncommittedSize(SamplerSampleWriterData data) {
        return data.getCurrentPos().subtract(data.getStartPos());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord getSampleSize(SamplerSampleWriterData data) {
        return getUncommittedSize(data).subtract(getHeaderSize());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void increaseCurrentPos(SamplerSampleWriterData data, UnsignedWord delta) {
        data.setCurrentPos(data.getCurrentPos().add(delta));
    }
}
