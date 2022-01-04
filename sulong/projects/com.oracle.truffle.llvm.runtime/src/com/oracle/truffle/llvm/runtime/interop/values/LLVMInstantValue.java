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

package com.oracle.truffle.llvm.runtime.interop.values;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.DynamicDispatchLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotAsDateTimeNode.LLVMPolyglotAsTimeZoneNode;

@ValueType
@ExportLibrary(value = DynamicDispatchLibrary.class, useForAOT = true)
public final class LLVMInstantValue implements TruffleObject {
    private final Instant instant;

    public LLVMInstantValue(Instant instant) {
        this.instant = instant;
    }

    public static LLVMInstantValue ofEpochSecond(long epochSecond) throws DateTimeException {
        return new LLVMInstantValue(Instant.ofEpochSecond(epochSecond));
    }

    @TruffleBoundary
    public LocalDate asDate() throws DateTimeException {
        return LocalDate.ofInstant(instant, asTimeZone());
    }

    @TruffleBoundary
    public LocalTime asTime() {
        return LocalTime.ofInstant(instant, asTimeZone());
    }

    public static ZoneId asTimeZone() {
        return LLVMPolyglotAsTimeZoneNode.UTC;
    }

    public Instant asInstant() {
        return instant;
    }

    @ExportMessage
    static Class<?> dispatch(@SuppressWarnings("unused") LLVMInstantValue receiver) {
        return LLVMInstantValueLibrary.class;
    }

    @ExportLibrary(value = InteropLibrary.class, receiverType = LLVMInstantValue.class)
    public abstract static class LLVMInstantValueLibrary {
        @ExportMessage
        public static LocalDate asDate(LLVMInstantValue receiver) throws DateTimeException {
            return receiver.asDate();
        }

        @ExportMessage
        public static boolean isDate(@SuppressWarnings("unused") LLVMInstantValue receiver) {
            return true;
        }

        @ExportMessage
        public static LocalTime asTime(LLVMInstantValue receiver) {
            return receiver.asTime();
        }

        @ExportMessage
        public static boolean isTime(@SuppressWarnings("unused") LLVMInstantValue receiver) {
            return true;
        }

        @ExportMessage
        public static ZoneId asTimeZone(@SuppressWarnings("unused") LLVMInstantValue receiver) {
            return LLVMInstantValue.asTimeZone();
        }

        @ExportMessage
        public static boolean isTimeZone(@SuppressWarnings("unused") LLVMInstantValue receiver) {
            return true;
        }

        @ExportMessage
        public static Instant asInstant(LLVMInstantValue receiver) {
            return receiver.asInstant();
        }
    }
}
