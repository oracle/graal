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
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

import com.oracle.truffle.api.InternalResource.OS;

public class ErrorContext extends AbstractErrorContext {

    private static final String ERRNO_LOCATION = switch (OS.getCurrent()) {
        case DARWIN -> "__error";
        case LINUX -> "__errno_location";
        case WINDOWS -> "_errno";
        case UNSUPPORTED -> throw new IllegalStateException("NFI is not supported on unsupported platforms.");
    };

    public static final VarHandle INT_VAR_HANDLE = ValueLayout.JAVA_INT.varHandle();

    private MemorySegment errnoLocation;

    @SuppressWarnings("restricted")
    private static MemorySegment lookupErrnoLocation() {
        try {
            Linker linker = Linker.nativeLinker();
            FunctionDescriptor desc = FunctionDescriptor.of(ValueLayout.JAVA_LONG);
            MemorySegment sym = linker.defaultLookup().find(ERRNO_LOCATION).orElseThrow();
            MethodHandle handle = linker.downcallHandle(desc);
            try {
                return MemorySegment.ofAddress((long) handle.invokeExact(sym)).reinterpret(4);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } catch (IllegalCallerException ic) {
            throw NFIError.illegalNativeAccess(null);
        }
    }

    @Override
    void initialize() {
        if (errnoLocation == null) {
            errnoLocation = lookupErrnoLocation();
        }
    }

    private MemorySegment getErrnoLocation() {
        assert errnoLocation != null;
        return errnoLocation;
    }

    @Override
    int getNativeErrno() {
        return (int) INT_VAR_HANDLE.get(getErrnoLocation(), 0);
    }

    @Override
    void setNativeErrno(int newErrno) {
        INT_VAR_HANDLE.set(getErrnoLocation(), 0, newErrno);
    }

}
