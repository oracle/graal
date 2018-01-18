/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
