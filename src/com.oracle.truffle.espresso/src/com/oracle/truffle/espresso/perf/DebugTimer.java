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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public abstract class DebugTimer {
    static final boolean DEBUG_TIMER_ENABLED = true;

    private static final List<DebugTimer> timers = new ArrayList<>();

    protected final String name;

    private DebugTimer(String name) {
        this.name = name;
    }

    public static void report() {
        for (DebugTimer timer : timers) {
            timer.doReport();
        }
    }

    abstract void start();

    abstract void end(long tick);

    protected abstract void doReport();

    private static final class Default extends DebugTimer {
        private final AtomicLong clock = new AtomicLong();
        private final AtomicLong counter = new AtomicLong();

        public Default(String name) {
            super(name);
        }

        @Override
        void start() {
            counter.getAndIncrement();
        }

        @Override
        void end(long tick) {
            clock.getAndAdd(tick);
        }

        @Override
        protected void doReport() {
            System.err.println(name + " avg: " + (clock.get() / counter.get()));
        }
    }

    private static final class NoTimer extends DebugTimer {
        private static final NoTimer INSTANCE = new NoTimer();

        public NoTimer() {
            super(null);
        }

        @Override
        void start() {
        }

        @Override
        void end(long tick) {
        }

        @Override
        protected void doReport() {
        }
    }

    public static DebugTimer create(String name) {
        if (DEBUG_TIMER_ENABLED) {
            DebugTimer result = new Default(name);
            timers.add(result);
            return result;
        } else {
            return NoTimer.INSTANCE;
        }
    }
}
