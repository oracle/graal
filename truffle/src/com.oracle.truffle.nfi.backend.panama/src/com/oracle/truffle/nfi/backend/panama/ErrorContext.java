/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.backend.panama;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InternalResource.OS;

public class ErrorContext {
    private static final String ERRNO_LOCATION;

    static {
        ERRNO_LOCATION = switch (OS.getCurrent()) {
            case DARWIN -> "__error";
            case LINUX -> "__errno_location";
            case WINDOWS -> "_errno";
        };
    }

    @SuppressWarnings("preview") private MemorySegment errnoLocation;
    private Integer nativeErrno = null;
    final PanamaNFIContext ctx;

    public boolean nativeErrnoSet() {
        return (nativeErrno != null);
    }

    public int getNativeErrno() {
        return nativeErrno;
    }

    public void setNativeErrno(int nativeErrno) {
        this.nativeErrno = nativeErrno;
    }

    @SuppressWarnings({"preview"})
    MemorySegment lookupErrnoLocation() {
        FunctionDescriptor desc = FunctionDescriptor.of(ValueLayout.JAVA_LONG);
        try {
            MethodHandle handle = NFIPanamaAccessor.FOREIGN.downcallHandle(ERRNO_LOCATION, desc);
            MemorySegment errnoAddress;
            try {
                errnoAddress = MemorySegment.ofAddress((long) handle.invokeExact());
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            return (MemorySegment) NFIPanamaAccessor.FOREIGN.reinterpret(errnoAddress, 4);
        } catch (IllegalCallerException ic) {
            throw NFIError.illegalNativeAccess(null);
        }
    }

    void initialize() {
        if (this.errnoLocation == null) {
            errnoLocation = lookupErrnoLocation();
        }
    }

    private MemorySegment getErrnoLocation() {
        if (errnoLocation == null) {
            // FIXME: GR-30264
            /*
             * This thread was initialized externally, and we were called before the first truffle
             * safepoint after thread initialization. Unfortunately there is not much we can do here
             * except deopt and lazy initialize. This should be very rare.
             *
             * The actual fix for this is that Truffle should take care of doing the thread
             * initialization on the correct thread. Truffle has enough control over safepoints that
             * it can make sure this is guaranteed to happen before any guest code runs.
             */
            CompilerDirectives.transferToInterpreterAndInvalidate();
            errnoLocation = lookupErrnoLocation();
        }
        return errnoLocation;
    }

    @SuppressWarnings("preview")
    int getErrno() {
        return getErrnoLocation().get(ValueLayout.JAVA_INT, 0);
    }

    @SuppressWarnings("preview")
    void setErrno(int newErrno) {
        getErrnoLocation().set(ValueLayout.JAVA_INT, 0, newErrno);
    }

    ErrorContext(PanamaNFIContext ctx, Thread thread) {
        this.ctx = ctx;
    }
}
