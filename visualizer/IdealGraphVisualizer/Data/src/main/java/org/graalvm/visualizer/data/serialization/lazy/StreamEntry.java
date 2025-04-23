/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package org.graalvm.visualizer.data.serialization.lazy;

import java.util.Objects;

import jdk.graal.compiler.graphio.parsing.ConstantPool;

/**
 * Describes an entry in the data stream. For graphs, holds GraphMetadata
 */
class StreamEntry {
    static final int LARGE_ENTRY_THRESHOLD = Integer.getInteger("visualizer.data.serialization.largeEntryLimit", // NOI18N
            1024 * 1024 * 2); // 2Mbyte of serialized data

    /**
     * Offset in file/stream where the object starts.
     */
    private final long start;

    /**
     * End of the object.
     */
    private long end = -1;

    /**
     * Constant pool to be used when the object should be read. Must be cloned.
     */
    private final ConstantPool initialPool;

    /**
     * Constant pool to be used when this object is <b>skipped</b>. Must be cloned.
     */
    private ConstantPool skipPool;
    private GraphMetadata graphMeta;
    private final int majorVersion;
    private final int minorVersion;
    private final String contentId;

    public StreamEntry(String contentId, int majorVersion, int minorVersion, long start, ConstantPool initialPool) {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.start = start;
        this.initialPool = initialPool;
        this.contentId = contentId;
    }

    String id() {
        return contentId;
    }

    synchronized StreamEntry end(long end, ConstantPool skipPool) {
        this.end = end;
        this.skipPool = skipPool;
        notifyAll();
        return this;
    }

    synchronized boolean isFinished() {
        return end != -1;
    }

    /**
     * Overridable by tests. Called just before {@link #wait} on this object,
     * tests will add special processing.
     */
    void beforeWait() {
    }

    StreamEntry setMetadata(GraphMetadata meta) {
        this.graphMeta = meta;
        return this;
    }

    public long getStart() {
        return start;
    }

    public synchronized long getEnd() {
        return end;
    }

    public long unfinishedSize() {
        if (isFinished()) {
            return end - start;
        } else {
            return -1;
        }
    }

    public synchronized long size() {
        if (!isFinished()) {
            return Long.MAX_VALUE;
        }
        return end - start;
    }

    public ConstantPool getInitialPool() {
        return initialPool;
    }

    public synchronized ConstantPool getSkipPool() {
        return skipPool;
    }

    public GraphMetadata getGraphMeta() {
        return graphMeta;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    @Override
    public String toString() {
        return "StreamEntry[" + contentId + ":" + getStart() + "(" + Integer.toHexString(System.identityHashCode(getInitialPool())) + ")-" +
                getEnd() + "(" + Integer.toHexString(System.identityHashCode(getInitialPool())) + ")]";
    }

    @Override
    public int hashCode() {
        int hash = 7;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final StreamEntry other = (StreamEntry) obj;
        if (this.start != other.start) {
            return false;
        }
        return Objects.equals(this.contentId, other.contentId);
    }
}
