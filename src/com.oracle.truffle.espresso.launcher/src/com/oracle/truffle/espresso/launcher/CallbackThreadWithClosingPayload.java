/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.launcher;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.polyglot.PolyglotException;

/**
 * The espresso guest main thread. Implements two functional interfaces to avoid reflection in
 * Espresso's code.
 */
public final class CallbackThreadWithClosingPayload extends Thread implements
                Consumer<Supplier<Thread>>,
                Supplier<Runnable> {
    public CallbackThreadWithClosingPayload(Runnable target, Runnable payload) {
        super(target);
        this.payload = payload;
    }

    /**
     * The closing payload that needs to be executed by the closing thread. Should just be
     * context.close(). Used by the VM
     */
    private final Runnable payload;

    /**
     * A callback set by the VM. Simply starts and returns the closing thread.
     */
    private volatile Supplier<Thread> callback;

    /**
     * Reference to the thread that is closing the context.
     */
    private volatile Thread closer;

    private int rc = 1;

    public int getExitStatus() {
        return rc;
    }

    private final Object closerWaiter = new Object() {
    };

    @Override
    public final void run() {
        try {
            // Initialize Context and run main().
            super.run();
        } catch (PolyglotException e) {
            if (!e.isExit()) {
                e.printStackTrace();
            } else {
                rc = e.getExitStatus();
            }
        } finally {
            assert callback != null;
            // Start the Context closing thread
            closer = callback.get();
            synchronized (closerWaiter) {
                closerWaiter.notifyAll();
            }
        }
    }

    @Override
    public void accept(Supplier<Thread> r) {
        setCallback(r);
    }

    @Override
    public Runnable get() {
        return getPayload();
    }

    private void setCallback(Supplier<Thread> r) {
        assert callback == null;
        callback = r;
    }

    private Runnable getPayload() {
        return payload;
    }

    // Waits for the closing thread to close the context.
    public void waitForCloser() {
        synchronized (closerWaiter) {
            while (closer == null) {
                try {
                    closerWaiter.wait();
                } catch (InterruptedException e) {
                    /* Spin */
                }
            }
        }

        while (closer.isAlive()) {
            try {
                closer.join();
            } catch (InterruptedException e) {
                /* Spin */
            }
        }
    }

}
