/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.debug;


public class Debug {
    public static boolean SCOPE = false;
    public static boolean LOG = false;
    public static boolean METER = false;
    public static boolean TIME = false;

    public static void scope(String name, Runnable runnable, Object... context) {
        if (SCOPE) {
            DebugContext.getInstance().scope(name, runnable, context);
        } else {
            runnable.run();
        }
    }

    public static void log(String msg, Object... args) {
        if (LOG) {
            DebugContext.getInstance().log(msg, args);
        }
    }

    public static Metric metric(String name) {
        if (METER) {
            return new MetricImpl(name);
        } else {
            return VOID_METRIC;
        }
    }

    public interface Metric {
        void increment();
        void add(int value);
    }

    private static final Metric VOID_METRIC = new Metric() {
        public void increment() { }
        public void add(int value) { }
    };

    public static Timer timer(String name) {
        if (TIME) {
            return new TimerImpl(name);
        } else {
            return VOID_TIMER;
        }
    }

    public interface Timer {
        void start();
        void stop();
    }

    private static final Timer VOID_TIMER = new Timer() {
        public void start() { };
        public void stop() { };
    };

    private static final class MetricImpl extends ScopeChild implements Metric {
        private MetricImpl(String name) {
            super(name);
        }

        public void increment() {
        }

        public void add(int value) {
        }

    }

    public static final class TimerImpl extends ScopeChild implements Timer {
        private TimerImpl(String name) {
            super(name);
        }

        @Override
        public void start() {
            // TODO Auto-generated method stub

        }

        @Override
        public void stop() {
            // TODO Auto-generated method stub

        }
    }
}
