/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import java.util.concurrent.atomic.AtomicReference;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Exception that abruptly breaks execution of a node and unwinds it off the execution stack. It
 * acts in connection with <code>onUnwind</code> execution handlers. An instance of this exception
 * is created by {@link EventBinding#createUnwind(java.lang.Object)}.
 */
final class UnwindException extends ThreadDeath {

    private static final long serialVersionUID = -8034021436021506591L;

    private final Object info;
    private final boolean hasPreferredBindingSet;
    private EventBinding<?> binding;
    private UnwindException next;
    private final AtomicReference<Thread> thrownThread;
    private boolean thrownFromBindingCalled;

    boolean notifiedOnReturnValue; // True if onReturnValue() was called when this was thrown.

    UnwindException(Object info, EventBinding<?> preferredBinding) {
        this.info = info;
        this.hasPreferredBindingSet = (preferredBinding != null);
        this.binding = preferredBinding;
        boolean assertions = false;
        assert (assertions = true) == true;
        thrownThread = assertions ? new AtomicReference<>() : null;
    }

    void thrownFromBinding(EventBinding<?> unwindBinding) {
        thrownFromBindingCalled = true;
        assert unwindBinding != null;
        // Either we have a preferred binding set, or the binding that thrown it must be the same:
        assert this.hasPreferredBindingSet || (this.binding == null || this.binding == unwindBinding);
        if (this.binding == null) {
            this.binding = unwindBinding;
        }
        assert thrownThread == null || checkThrownConsistency();
    }

    // Checks that the exception is thrown in one thread only.
    @TruffleBoundary
    private boolean checkThrownConsistency() {
        Thread currentThread = Thread.currentThread();
        Thread oldThread = thrownThread.getAndSet(currentThread);
        if (oldThread != null && oldThread != currentThread) {
            throw new IllegalStateException("A single instance of UnwindException thrown in two threads: '" + oldThread + "' and '" + currentThread + "'");
        }
        return true;
    }

    boolean hasPreferredBinding() {
        return hasPreferredBindingSet;
    }

    boolean isThrownFromBinding() {
        return thrownFromBindingCalled;
    }

    EventBinding<?> getBinding() {
        return binding;
    }

    /**
     * For performance reasons, this exception does not record any stack trace information.
     */
    @SuppressWarnings("sync-override")
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    @TruffleBoundary
    void addNext(UnwindException ex) { // not common - when more instruments throw unwind
        if (next == null) {
            next = ex;
        } else {
            next.addNext(ex);
        }
    }

    UnwindException getNext() {
        return next;
    }

    Object getInfo() {
        return info;
    }

    @TruffleBoundary
    void resetBoundary(EventBinding<?> unwindBinding) {
        if (this.binding == unwindBinding) {
            resetThread();
        } else if (next != null) {
            next.resetBoundary(unwindBinding);
            if (next.binding == unwindBinding) {
                next = next.next;
            }
        }
    }

    void resetThread() {
        assert thrownThread == null || resetThreadBoundary();
    }

    @TruffleBoundary
    private boolean resetThreadBoundary() {
        thrownThread.set(null);
        return true;
    }

}
