/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.perf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.oracle.truffle.espresso.classfile.descriptors.ErrorUtil;

public final class DebugTimer {
    final String name;
    private List<DebugTimer> children = null;
    private final DebugTimer parent;

    private DebugTimer(String name, DebugTimer parent) {
        this.name = name;
        this.parent = parent;
    }

    public static DebugTimer create(String name) {
        return create(name, null);
    }

    public static DebugTimer create(String name, DebugTimer parent) {
        DebugTimer timer = new DebugTimer(name, parent);
        if (parent != null) {
            parent.registerChild(timer);
        }
        return timer;
    }

    public DebugCloseable scope(TimerCollection timers) {
        return timers.scope(this);
    }

    static DebugTimerImpl spawn() {
        return new Default();
    }

    private void registerChild(DebugTimer child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
    }

    List<DebugTimer> children() {
        return children;
    }

    DebugTimer parent() {
        return parent;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DebugTimer && ((DebugTimer) obj).name.equals(name);
    }

    abstract static class DebugTimerImpl {
        abstract void tick(long tick);

        abstract void report(Consumer<String> logger, String name);

        abstract void enter();
    }

    private static final class Default extends DebugTimerImpl {
        private final AtomicLong clock = new AtomicLong();
        private final AtomicLong counter = new AtomicLong();
        private final ThreadLocal<Boolean> entered = ThreadLocal.withInitial(new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return false;
            }
        });

        @Override
        void tick(long tick) {
            ErrorUtil.guarantee(entered.get(), "Not entered scope.");
            counter.getAndIncrement();
            clock.getAndAdd(tick);
            entered.set(false);
        }

        @Override
        void report(Consumer<String> logger, String name) {
            if (counter.get() == 0) {
                logger.accept(name + ": " + 0);
            } else {
                logger.accept(name + " total : " + getAsMillis(total()) + " | avg : " + getAsMillis(avg()));
            }
        }

        @Override
        void enter() {
            // Ensure we are not counting twice.
            ErrorUtil.guarantee(!entered.get(), "Counting twice for timer.");
            entered.set(true);
        }

        private long total() {
            return clock.get();
        }

        private long avg() {
            return (counter.get() == 0) ? 0L : (total() / counter.get());
        }

        private static double getAsMillis(long value) {
            return (value / 1_000L) / 1_000d;
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
