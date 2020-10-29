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

package com.oracle.truffle.espresso.perf;

import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.TruffleLogger;

public final class DebugTimer {
    static final boolean DEBUG_TIMER_ENABLED = true;

    final String name;

    private DebugTimer(String name) {
        this.name = name;
    }

    public static DebugTimer create(String name) {
        return new DebugTimer(name);
    }

    public DebugCloseable scope(TimerCollection timers) {
        return timers.scope(this);
    }

    static DebugTimerImpl spawn() {
        return new Default();
    }

    static abstract class DebugTimerImpl {
        abstract void tick(long tick);

        abstract void report(TruffleLogger logger, String name);
    }

    private static final class Default extends DebugTimerImpl {
        private final AtomicLong clock = new AtomicLong();
        private final AtomicLong counter = new AtomicLong();

        @Override
        void tick(long tick) {
            counter.getAndIncrement();
            clock.getAndAdd(tick);
        }

        @Override
        void report(TruffleLogger logger, String name) {
            if (counter.get() == 0) {
                logger.info(name + ": " + 0);
            } else {
                logger.info(name + ": " + (clock.get() / counter.get()));
            }
        }
    }

    abstract static class AutoTimer implements DebugCloseable {
        static final AutoTimer NO_TIMER = new NoTimer();

        private AutoTimer() {
        }

        static DebugCloseable scope(DebugTimerImpl impl) {
            return new Default(impl);
        }

        @Override
        public abstract void close();

        private static final class Default extends AutoTimer {
            private final DebugTimerImpl timer;
            private final long tick;

            private Default(DebugTimerImpl timer) {
                this.timer = timer;
                this.tick = System.nanoTime();
            }

            @Override
            public void close() {
                timer.tick(System.nanoTime() - tick);
            }
        }

        private static final class NoTimer extends AutoTimer {
            @Override
            public void close() {

            }
        }
    }
}
