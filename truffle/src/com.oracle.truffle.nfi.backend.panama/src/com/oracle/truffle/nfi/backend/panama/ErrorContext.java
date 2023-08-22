/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

public class ErrorContext {
    private Throwable throwable = null;
    @SuppressWarnings("preview") private MemorySegment errnoLocation;
    private Integer nativeErrno = null;
    final PanamaNFIContext ctx;

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    void handleThrowables() {
        if (throwable != null) {
            Throwable temp = throwable;
            throwable = null;
            throw silenceThrowable(RuntimeException.class, temp);
        }
    }

    public boolean nativeErrnoSet() {
        return (nativeErrno != null);
    }

    public int getNativeErrno() {
        return nativeErrno;
    }

    public void setNativeErrno(int nativeErrno) {
        this.nativeErrno = nativeErrno;
    }

    @SuppressWarnings("preview")
    MemorySegment getErrnoLocation() {
        Linker linker = Linker.nativeLinker();
        FunctionDescriptor desc = FunctionDescriptor.of(ValueLayout.JAVA_LONG);

        MemorySegment t = linker.defaultLookup().find("__errno_location").get();
        MethodHandle handle = linker.downcallHandle(desc);
        try {
            return MemorySegment.ofAddress((long) handle.invokeExact(t)).reinterpret(4);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    void initialize() {
        errnoLocation = getErrnoLocation();
    }

    @SuppressWarnings("preview")
    int getErrno() {
        return errnoLocation.get(ValueLayout.JAVA_INT, 0);
    }

    @SuppressWarnings("preview")
    void setErrno(int newErrno) {
        errnoLocation.set(ValueLayout.JAVA_INT, 0, newErrno);
    }

    ErrorContext(PanamaNFIContext ctx, Thread thread) {
        this.ctx = ctx;
    }

    @SuppressWarnings("unchecked")
    static <E extends Throwable> RuntimeException silenceThrowable(Class<E> type, Throwable t) throws E {
        throw (E) t;
    }
}
