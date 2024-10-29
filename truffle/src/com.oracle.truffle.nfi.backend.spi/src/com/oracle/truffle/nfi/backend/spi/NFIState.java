/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.backend.spi;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * Thread-local state of the NFI that is shared between different backends. All methods should only
 * be called from the corresponding thread to which this state belongs (for thread-safety reasons
 * and because the SVM NFI backend relies on it by using a FastThreadLocalBytes for the errno
 * pointer, which means the errno pointer points inside the IsolateThread), except the constructor
 * and {@link #dispose()} which may be called from another thread, as Truffle hooks which call those
 * are not guaranteed to be called from the corresponding thread.
 */
public final class NFIState {

    private static final Unsafe UNSAFE;

    static {
        Field unsafeField;
        try {
            unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

        try {
            UNSAFE = (Unsafe) unsafeField.get(null);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    // We cannot just store the errno as an int here, because it must be accessible as an int* in
    // native code for Sulong
    private final long nfiErrnoAddress;

    private Throwable pendingException;

    // for faster query from JNI
    boolean hasPendingException;

    public NFIState(Thread thread) {
        this.nfiErrnoAddress = initNfiErrnoAddress(thread);
    }

    public int getNFIErrno() {
        return UNSAFE.getInt(nfiErrnoAddress);
    }

    public void setNFIErrno(int nfiErrno) {
        UNSAFE.putInt(nfiErrnoAddress, nfiErrno);
    }

    public Throwable getPendingException() {
        return pendingException;
    }

    public void setPendingException(Throwable t) {
        assert t != null;
        pendingException = t;
        hasPendingException = true;
    }

    public void clearPendingException() {
        pendingException = null;
        hasPendingException = false;
    }

    private static long initNfiErrnoAddress(@SuppressWarnings("unused") Thread thread) {
        long address = UNSAFE.allocateMemory(Integer.BYTES);
        UNSAFE.putInt(address, 0);
        return address;
    }

    private void freeNfiErrnoAddress() {
        UNSAFE.freeMemory(this.nfiErrnoAddress);
    }

    public void dispose() {
        freeNfiErrnoAddress();
    }
}
