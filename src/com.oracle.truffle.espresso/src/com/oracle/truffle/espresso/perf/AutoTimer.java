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

public abstract class AutoTimer implements AutoCloseable {
    private static final AutoTimer NO_TIMER = new NoTimer();

    private AutoTimer() {
    }

    @Override
    public abstract void close();

    private static final class Default extends AutoTimer {
        private final DebugTimer timer;
        private final long tick;

        private Default(DebugTimer timer) {
            this.timer = timer;
            this.timer.start();
            this.tick = System.nanoTime();
        }

        @Override
        public void close() {
            timer.end(System.nanoTime() - tick);
        }
    }

    public static AutoTimer time(DebugTimer timer) {
        if (DebugTimer.DEBUG_TIMER_ENABLED) {
            return new Default(timer);
        } else {
            return NO_TIMER;
        }
    }

    private static final class NoTimer extends AutoTimer {
        @Override
        public void close() {

        }
    }
}
