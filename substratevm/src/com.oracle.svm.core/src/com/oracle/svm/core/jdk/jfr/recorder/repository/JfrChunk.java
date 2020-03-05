/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr.recorder.repository;

import static com.oracle.svm.core.jdk.jfr.utilities.JfrTime.invalidTime;

import java.nio.file.Path;

import com.oracle.svm.core.jdk.jfr.JfrOptions;
import com.oracle.svm.core.jdk.jfr.utilities.JfrTicks;
import com.oracle.svm.core.jdk.jfr.utilities.JfrTime;
import com.oracle.svm.core.jdk.jfr.utilities.JfrTimeConverter;

public class JfrChunk {
    private Path path;
    private long startTicks;
    private long previousStartTicks;
    private long startNanos;
    private long previousStartNanos;
    private long lastUpdateNanos;
    private long lastCheckpointOffset;
    private long lastMetadataOffset;
    private int generation;

    boolean isFinal;

    private static final String MAGIC = "FLR\0";
    private static final short JFR_VERSION_MAJOR = 2;
    private static final short JFR_VERSION_MINOR = 1;

    static final byte COMPLETE = 0;
    static final int GUARD = 0xff;
    static final byte PAD = 0;

    // strictly monotone
    static long last = 0;
    static long getNanosNow() {
        // We use javaTimeMillis so this can be correlated with
        // external timestamps.
        long now = System.currentTimeMillis() * JfrTimeConverter.NANOS_PER_MILLISEC;
        if (now > last) {
            last = now;
        } else {
            ++last;
        }
        return last;
    }

    static long getTicksNow() {
        // TODO JFR Figure out what to use here
        return JfrTicks.now();
    }

    public JfrChunk() {
        previousStartTicks = invalidTime;
        previousStartNanos = invalidTime;
        generation = 1;

        startTicks = startNanos = lastUpdateNanos = lastCheckpointOffset = lastMetadataOffset = 0;
        isFinal = false;
    }

    void reset() {
        path = null;
        lastCheckpointOffset = 0;
        lastMetadataOffset = 0;
        generation = 1;
    }

    String getMagic() {
        return MAGIC;
    }

    short getMajorVersion() {
        return JFR_VERSION_MAJOR;
    }

    short getMinorVersion() {
        return JFR_VERSION_MINOR;
    }

    void markFinal() {
        isFinal = true;
    }

    short getFlags() {
        // chunk capabilities, CompressedIntegers etc
        short flags = 0;
       if (JfrOptions.compressedIntegers()) {
           flags |= 1;
       }
        if (isFinal) {
            flags |= 1 << 1;
        }
        return flags;
    }

    private static final long frequency =  JfrTime.getFrequency();
    long getCpuFrequency() {
        return frequency;
    }

    void setLastCheckpointOffset(long offset) {
        lastCheckpointOffset = offset;
    }

    long getLastCheckpointOffset() {
        return lastCheckpointOffset;
    }

    long getStartTicks() {
        assert startTicks != 0;
        return startTicks;
    }

    long getStartNanos() {
        assert startNanos != 0;
        return startNanos;
    }

    long getPreviousStartTicks() {
        assert previousStartTicks != invalidTime;
        return previousStartTicks;
    }

    long getPreviousStartNanos() {
        assert previousStartNanos != invalidTime;
        return previousStartNanos;
    }

    void updateStartTicks() {
        startTicks = getTicksNow();
    }

    void updateStartNanos() {
        long now = getNanosNow();
        assert now > startNanos;
        assert now > lastUpdateNanos;
        startNanos = lastUpdateNanos = now;
    }

    void updateCurrentNanos() {
        long now = getNanosNow();
        assert now > lastUpdateNanos;
        lastUpdateNanos = now;
    }

    void saveCurrentAndUpdateStartTicks() {
        previousStartTicks = startTicks;
        updateStartTicks();
    }

    void saveCurrentAndUpdateStartNanos() {
        previousStartNanos = startNanos;
        updateStartNanos();
    }

    void setTimeStamp() {
        saveCurrentAndUpdateStartNanos();
        saveCurrentAndUpdateStartTicks();
    }

    long getLastChunkDuration() {
        assert previousStartNanos != invalidTime;
        return startNanos - previousStartNanos;
    }

    Path getPath() {
        return path;
    }

    void setPath(Path path) {
        this.path = path;
    }

    boolean isStarted() {
        return startNanos != 0;
    }

    boolean isFinished() {
        return 0 == generation;
    }

    long getDuration() {
        assert lastUpdateNanos >= startNanos;
        return lastUpdateNanos - startNanos;
    }

    long getLastMetadataOffset() {
        return lastMetadataOffset;
    }

    void setLastMetadataOffset(long offset) {
        assert offset > lastMetadataOffset;
        lastMetadataOffset = offset;
    }

    boolean hasMetadata() {
        return 0 != lastMetadataOffset;
    }

    int getGeneration() {
        assert generation > 0;
        int thisGeneration = generation++;
        if (GUARD == generation) {
            generation = 1;
        }
        return thisGeneration;
    }

    int getNextGeneration() {
        assert generation > 0;
        int nextGen = generation;
        return GUARD == nextGen ? 1 : nextGen;
    }
}
