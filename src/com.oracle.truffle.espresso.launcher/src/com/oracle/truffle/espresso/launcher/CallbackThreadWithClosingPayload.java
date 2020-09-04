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

public final class CallbackThreadWithClosingPayload extends Thread implements
                Consumer<Supplier<Thread>>,
                Supplier<Runnable> {
    public CallbackThreadWithClosingPayload(Runnable target, Runnable payload) {
        super(target);
        this.payload = payload;
    }

    private final Runnable payload;
    private volatile Supplier<Thread> callback;
    private volatile Thread closer;

    @Override
    public final void run() {
        super.run();
        assert callback != null;
        closer = callback.get();
    }

    @Override
    public void accept(Supplier<Thread> r) {
        setCallback(r);
    }

    @Override
    public Runnable get() {
        return getPayload();
    }

    public void setCallback(Supplier<Thread> r) {
        assert callback == null;
        callback = r;
    }

    public Runnable getPayload() {
        return payload;
    }

    public void waitForCloser() {
        while (closer == null) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                /* Spin */
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
