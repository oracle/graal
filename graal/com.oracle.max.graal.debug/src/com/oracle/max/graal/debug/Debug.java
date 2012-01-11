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
    public static boolean ENABLED = false;

    public static void log(String msg, Object... args) {
        if (ENABLED) {
            DebugContext.getInstance().log(msg, args);
        }
    }

    public static void scope(String name, Runnable runnable, Object... context) {
        if (ENABLED) {
            DebugContext.getInstance().scope(name, runnable, context);
        } else {
            runnable.run();
        }
    }

    private static final Scope VOID_SCOPE = new Scope(null);


    public static final class Scope implements AutoCloseable {
        private String name;
        private Object[] context;

        private Scope(String name, Object... context) {
            this.name = name;
            this.context = context;
        }

        @Override
        public void close() {
        }
    }

    private static class ScopeChild {
        private String name;

        protected ScopeChild(String name) {
            this.name = name;
        }
    }

    public static final class Metric extends ScopeChild {
        private Metric(String name) {
            super(name);
        }

        public void increment() {
            // TODO Auto-generated method stub

        }

        public void add(int targetCodeSize) {
            // TODO Auto-generated method stub

        }

    }

    public static final class Timer extends ScopeChild {
        private Timer(String name) {
            super(name);
        }
    }

    public static Metric metric(String string) {
        return new Metric(string);
    }
}
