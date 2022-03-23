/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Struct;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.TimeInfo;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignGetMemberPointerNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode.LLVMI32OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNode.LLVMI64OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMPolyglotAsDateTimeNode extends LLVMNode {

    protected static boolean isInstantPointer(LLVMPointer receiver) {
        return receiver.getExportType() instanceof LLVMInteropType.Instant;
    }

    protected static boolean isTimeInfoPointer(LLVMPointer receiver) {
        return receiver.getExportType() instanceof LLVMInteropType.TimeInfo;
    }

    @GenerateUncached
    public abstract static class LLVMPolyglotAsDateNode extends LLVMPolyglotAsDateTimeNode {

        public abstract LocalDate execute(LLVMPointer receiver) throws UnsupportedMessageException;

        @TruffleBoundary
        private LocalDate ofInstant(Instant instant, ZoneId zoneId) {
            try {
                return LocalDate.ofInstant(instant, zoneId);
            } catch (DateTimeException ex) {
                throw new LLVMPolyglotException(this, "Failed to convert instant to local date value: %s", ex.toString());
            }
        }

        @Specialization(guards = "isInstantPointer(receiver)")
        protected LocalDate instantAsDate(LLVMPointer receiver,
                        @Cached LLVMPolyglotAsInstantNode instant,
                        @Cached LLVMPolyglotAsTimeZoneNode timeZone) throws UnsupportedMessageException {
            return ofInstant(instant.execute(receiver), timeZone.execute(receiver));
        }

        @TruffleBoundary
        private LocalDate ofYearMonthDay(int year, int month, int day) {
            try {
                return LocalDate.of(year, month, day);
            } catch (DateTimeException ex) {
                throw new LLVMPolyglotException(this, "Failed to construct local date value: %s", ex.toString());
            }
        }

        @Specialization(guards = "isTimeInfoPointer(receiver)")
        protected LocalDate timeInfoAsDate(LLVMPointer receiver,
                        @Cached LLVMForeignGetMemberPointerNode getElementPointerYear,
                        @Cached LLVMForeignGetMemberPointerNode getElementPointerMon,
                        @Cached LLVMForeignGetMemberPointerNode getElementPointerMday,
                        @Cached LLVMI32OffsetLoadNode loadInt,
                        @Cached BranchProfile exception) throws UnsupportedMessageException {
            try {
                TimeInfo ti = (TimeInfo) receiver.getExportType();
                Struct struct = ti.getStruct();
                LLVMPointer yearPtr = getElementPointerYear.execute(struct, receiver, "tm_year");
                LLVMPointer monPtr = getElementPointerMon.execute(struct, receiver, "tm_mon");
                LLVMPointer mdayPtr = getElementPointerMday.execute(struct, receiver, "tm_mday");
                return ofYearMonthDay(
                                loadInt.executeWithTarget(yearPtr, 0) + 1900, // year is from 1900
                                loadInt.executeWithTarget(monPtr, 0) + 1, // month is from 0-11,
                                loadInt.executeWithTarget(mdayPtr, 0)); // mday is from 1-31
            } catch (UnknownIdentifierException ex) {
                exception.enter();
                throw UnsupportedMessageException.create(ex);
            }
        }

        @Fallback
        public LocalDate unsupported(@SuppressWarnings("unused") LLVMPointer receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @GenerateUncached
    public abstract static class LLVMPolyglotAsTimeNode extends LLVMPolyglotAsDateTimeNode {
        public abstract LocalTime execute(LLVMPointer receiver) throws UnsupportedMessageException;

        @TruffleBoundary
        private LocalTime ofInstant(Instant instant, ZoneId zoneId) {
            try {
                return LocalTime.ofInstant(instant, zoneId);
            } catch (DateTimeException ex) {
                throw new LLVMPolyglotException(this, "Failed to convert instant to local time: %s", ex.toString());
            }
        }

        @Specialization(guards = "isInstantPointer(receiver)")
        protected LocalTime instantAsTime(LLVMPointer receiver,
                        @Cached LLVMPolyglotAsInstantNode instant,
                        @Cached LLVMPolyglotAsTimeZoneNode timeZone) throws UnsupportedMessageException {
            return ofInstant(instant.execute(receiver), timeZone.execute(receiver));
        }

        @TruffleBoundary
        private LocalTime ofHourMinSec(int hour, int min, int sec) {
            try {
                return LocalTime.of(hour, min, sec);
            } catch (DateTimeException ex) {
                throw new LLVMPolyglotException(this, "Failed to construct local time value: %s", ex.toString());
            }
        }

        @Specialization(guards = "isTimeInfoPointer(receiver)")
        protected LocalTime timeInfoAsTime(LLVMPointer receiver,
                        @Cached LLVMForeignGetMemberPointerNode getElementPointerHour,
                        @Cached LLVMForeignGetMemberPointerNode getElementPointerMin,
                        @Cached LLVMForeignGetMemberPointerNode getElementPointerSec,
                        @Cached LLVMI32OffsetLoadNode loadInt,
                        @Cached BranchProfile exception) throws UnsupportedMessageException {
            try {
                TimeInfo ti = (TimeInfo) receiver.getExportType();
                Struct struct = ti.getStruct();
                LLVMPointer hourPtr = getElementPointerHour.execute(struct, receiver, "tm_hour");
                LLVMPointer minPtr = getElementPointerMin.execute(struct, receiver, "tm_min");
                LLVMPointer secPtr = getElementPointerSec.execute(struct, receiver, "tm_sec");
                return ofHourMinSec(loadInt.executeWithTarget(hourPtr, 0),
                                loadInt.executeWithTarget(minPtr, 0),
                                loadInt.executeWithTarget(secPtr, 0));
            } catch (UnknownIdentifierException ex) {
                exception.enter();
                throw UnsupportedMessageException.create(ex);
            }
        }

        @Fallback
        public LocalTime unsupported(@SuppressWarnings("unused") LLVMPointer receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @GenerateUncached
    public abstract static class LLVMPolyglotAsTimeZoneNode extends LLVMPolyglotAsDateTimeNode {
        public static final ZoneId UTC = ZoneId.of("UTC");

        public abstract ZoneId execute(LLVMPointer receiver) throws UnsupportedMessageException;

        @Specialization(guards = "isInstantPointer(receiver)")
        protected ZoneId instantAsZoneId(@SuppressWarnings("unused") LLVMPointer receiver) {
            return UTC;
        }

        @Fallback
        public ZoneId unsupported(@SuppressWarnings("unused") LLVMPointer receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @GenerateUncached
    public abstract static class LLVMPolyglotAsInstantNode extends LLVMPolyglotAsDateTimeNode {

        public abstract Instant execute(LLVMPointer receiver) throws UnsupportedMessageException;

        @TruffleBoundary
        private Instant ofEpochSecond(long epochSecond) {
            try {
                return Instant.ofEpochSecond(epochSecond);
            } catch (DateTimeException ex) {
                throw new LLVMPolyglotException(this, "Failed to construct instant value from epoch second: %s", ex.toString());
            }
        }

        @Specialization(guards = "isInstantPointer(receiver)")
        protected Instant asInstant(LLVMPointer receiver,
                        @Cached LLVMI64OffsetLoadNode load,
                        @Cached BranchProfile exception) throws UnsupportedMessageException {
            try {
                long v = load.executeWithTarget(receiver, 0);
                return ofEpochSecond(v);
            } catch (UnexpectedResultException ex) {
                exception.enter();
                throw UnsupportedMessageException.create(ex);
            }
        }

        @Fallback
        public Instant unsupported(@SuppressWarnings("unused") LLVMPointer receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }
}
