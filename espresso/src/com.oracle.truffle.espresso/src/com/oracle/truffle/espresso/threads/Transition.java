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

import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * Represents thread state transition in Espresso. Instances of this class are intended to be used
 * in a try-with-resources block.
 */
public interface Transition extends AutoCloseable {
    /**
     * Implements the transition of a thread from runnable to other {@link State states}.
     * <p>
     * Note that this class does not handle transition from native code to guest world. Use
     * {@link #fromNative(EspressoContext)} instead.
     */
    static Transition transition(EspressoContext context, State state) {
        return new ThreadStateTransitionImpl(context, state);
    }

    /**
     * Implements the transition of a thread from native to guest code. The returned object is
     * intended to be used in a try-with-resources block.
     */
    static Transition fromNative(EspressoContext context) {
        return new NativeToGuestTransition(context);
    }

    @Override
    void close();

}

final class NativeToGuestTransition implements Transition {
    private final ThreadsAccess access;
    private final int old;
    private final StaticObject thread;

    NativeToGuestTransition(EspressoContext context) {
        this.access = context.getThreadAccess();
        this.thread = context.getCurrentPlatformThread();
        this.old = access.getState(thread);
        assert (old & State.IN_NATIVE.value) != 0;
        access.setState(thread, State.RUNNABLE.value);
    }

    @Override
    public void close() {
        access.restoreState(thread, old);
    }
}

final class ThreadStateTransitionImpl implements Transition {
    private final ThreadsAccess access;
    private final int old;
    private final StaticObject thread;

    ThreadStateTransitionImpl(EspressoContext context, State state) {
        this.access = context.getThreadAccess();
        this.thread = context.getCurrentPlatformThread();
        this.old = access.fromRunnable(thread, state);
    }

    @Override
    public void close() {
        access.restoreState(thread, old);
    }
}
