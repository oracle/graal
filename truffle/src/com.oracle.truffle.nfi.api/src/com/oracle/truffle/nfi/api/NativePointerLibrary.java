/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.nfi.api;

import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;

/**
 * This library contains a subset of {@link com.oracle.truffle.api.interop.InteropLibrary} messages
 * exported by some NFI objects. Those objects already implement the corresponding
 * {@link com.oracle.truffle.api.interop.InteropLibrary} messages, but they cannot be used in the
 * AOT mode, as {@link com.oracle.truffle.api.interop.InteropLibrary} is not an AOT-enabled library.
 */
@GenerateLibrary
@GenerateAOT
@GenerateLibrary.DefaultExport(NativePointerLibrary.LongNativePointerLibrary.class)
public abstract class NativePointerLibrary extends Library {

    @GenerateLibrary.Abstract(ifExported = {"asPointer"})
    public boolean isPointer(@SuppressWarnings("unused") Object receiver) {
        return false;
    }

    @GenerateLibrary.Abstract(ifExported = {"isPointer"})
    public long asPointer(@SuppressWarnings("unused") Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportLibrary(value = NativePointerLibrary.class, receiverType = Long.class, useForAOT = true, useForAOTPriority = 1)
    static final class LongNativePointerLibrary {

        @ExportMessage
        public static boolean isPointer(Long receiver) {
            assert receiver != null;
            return true;
        }

        @ExportMessage
        public static long asPointer(Long receiver) {
            assert receiver != null;
            return receiver;
        }
    }
}
