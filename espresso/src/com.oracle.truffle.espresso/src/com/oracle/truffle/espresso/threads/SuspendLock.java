/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.threads;

import com.oracle.truffle.espresso.runtime.StaticObject;

public final class SuspendLock {
    private final Object handshakeLock = new Object() {
    };
    private final ThreadsAccess access;
    private final StaticObject thread;

    private volatile boolean shouldSuspend;
    private volatile boolean threadSuspended;

    public SuspendLock(ThreadsAccess access, StaticObject thread) {
        this.access = access;
        this.thread = thread;
    }

    public void suspend() {
        if (access.getHost(thread) == Thread.currentThread()) {
            // No need for handshake
            shouldSuspend = true;
            selfSuspend();
        } else {
            suspendHandshake();
        }
    }

    public synchronized void selfSuspend() {
        while (shouldSuspend()) {
            notifySuspended();
            try {
                wait();
            } catch (InterruptedException e) {
                /* spin back */
            }
        }
        threadSuspended = false;
    }

    public synchronized void resume() {
        if (shouldSuspend()) {
            shouldSuspend = false;
            notifyAll();
        }
    }

    private boolean shouldSuspend() {
        return shouldSuspend;
    }

    private boolean isSuspended() {
        return threadSuspended;
    }

    private void suspendHandshake() {
        boolean wasInterrupted = false;
        while (!isSuspended()) {
            shouldSuspend = true;
            try {
                synchronized (handshakeLock) {
                    if (!access.isAlive(thread)) {
                        // If thread terminates, we don't want to wait forever
                        handshakeLock.wait(100);
                    } else {
                        break;
                    }
                }
            } catch (InterruptedException e) {
                /* Thread.suspend() is not supposed to be interrupted */
                wasInterrupted = true;
            }
        }
        if (wasInterrupted) {
            // Re-interrupt ourselves
            Thread.currentThread().interrupt();
        }
    }

    private void notifySuspended() {
        synchronized (handshakeLock) {
            threadSuspended = true;
            handshakeLock.notifyAll();
        }
    }
}
