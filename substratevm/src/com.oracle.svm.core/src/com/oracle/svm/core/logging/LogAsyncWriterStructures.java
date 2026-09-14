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
package com.oracle.svm.core.logging;

import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

/// Holds the raw structures used by `LogAsyncWriter`. Keeping them in a class without static state
/// allows layered C interface discovery to resolve their enclosing type without initializing the
/// asynchronous writer before the structures have been registered.
final class LogAsyncWriterStructures {

    private LogAsyncWriterStructures() {
    }

    // @formatter:off
    /// A variable-sized queue entry whose raw header is followed immediately by inline prefix and
    /// message bytes. The complete allocation is contiguous and never wraps around the end of the
    /// [queue][QueueState] chunk.
    @RawStructure
    interface Record extends PointerBase {
        @RawField int  getAllocationSize();
        @RawField void setAllocationSize(int value);

        @RawField int  getMessageLength();
        @RawField void setMessageLength(int value);

        @RawField int  getPrefixLength();
        @RawField void setPrefixLength(int value);

        @RawField int  getOutputSlot();
        @RawField void setOutputSlot(int value);

        @RawField int  getLevelOrdinal();
        @RawField void setLevelOrdinal(int value);

        @RawField int  getTagSetOrdinal();
        @RawField void setTagSetOrdinal(int value);

        @RawField long getSystemMillis();
        @RawField void setSystemMillis(long value);

        @RawField long getSystemNanos();
        @RawField void setSystemNanos(long value);

        @RawField long getUptimeNanos();
        @RawField void setUptimeNanos(long value);

        @RawField long getThreadId();
        @RawField void setThreadId(long value);
    }

    /// Native ownership and byte-ring state accessed by both producers and the native consumer.
    /// The diagrams linearize the chunk; its left and right ends are adjacent in the ring. `head`
    /// identifies the next record for the consumer, while `tail` identifies the next producer
    /// insertion point.
    ///
    /// The logical queue can cross the chunk boundary, but an individual `Record` never does.
    /// Keeping each header and its inline bytes contiguous lets the consumer access the complete
    /// entry as one native memory range without temporary storage. When a record does not fit
    /// between `tail` and the end of the chunk, the producer stores the old `tail` in `wrapOffset`
    /// and resumes allocation at offset zero. The skipped suffix from `wrapOffset` to `capacity` is
    /// the wrap pad. It contains no record, but remains part of `usedBytes` so producers cannot
    /// overcommit the queue. After the consumer finishes the last record before `wrapOffset`, it
    /// reclaims the wrap pad, moves `head` to zero, and resets `wrapOffset` to `-1`.
    ///
    /// ```text
    /// tail > head (queued records are contiguous)
    ///
    /// 0              head                              tail capacity
    /// +----------------+===================================+--------+
    /// |      free      |          queued records           |  free  |
    /// +----------------+===================================+--------+
    ///                 ^ head                              ^ tail
    /// <--------------  capacity ------------------------------------>
    ///
    /// tail < head (queued records wrap around the end)
    ///
    /// 0              tail         head              wrapOffset capacity
    /// +===============+------------+===================+...........+
    /// | queued records|    free    |  queued records   | wrap pad  |
    /// +===============+------------+===================+...........+
    ///                 ^ tail       ^ head
    /// ```
    @RawStructure
    public interface QueueState extends PointerBase {
        @RawField Pointer getBuffer();
        @RawField void    setBuffer(Pointer value);

        @RawField int     getCapacity();
        @RawField void    setCapacity(int value);

        @RawField int     getHead();
        @RawField void    setHead(int value);

        @RawField int     getTail();
        @RawField void    setTail(int value);

        @RawField int     getUsedBytes();
        @RawField void    setUsedBytes(int value);

        @RawField int     getWrapOffset();
        @RawField void    setWrapOffset(int value);

        @RawField int     getQueuedRecords();
        @RawField void    setQueuedRecords(int value);

        @RawField boolean getInFlight();
        @RawField void    setInFlight(boolean value);

        @RawField boolean getShutdownRequested();
        @RawField void    setShutdownRequested(boolean value);
    }
    // @formatter:on
}
