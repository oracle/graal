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

import com.oracle.max.graal.debug.internal.DebugScope;
import com.oracle.max.graal.debug.internal.MetricImpl;
import com.oracle.max.graal.debug.internal.TimerImpl;
import java.util.*;
import java.util.concurrent.*;


public class Debug {
    private static boolean ENABLED = false;

    public static void enable() {
        ENABLED = true;
        DebugScope.initialize();
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static void sandbox(String name, Runnable runnable) {
        if (ENABLED) {
            DebugScope.getInstance().scope(name, runnable, null, true, new Object[0]);
        } else {
            runnable.run();
        }
    }

    public static void scope(String name, Runnable runnable) {
        scope(name, null, runnable);
    }

    public static <T> T scope(String name, Callable<T> callable) {
        return scope(name, null, callable);
    }

    public static void scope(String name, Object context, Runnable runnable) {
        if (ENABLED) {
            DebugScope.getInstance().scope(name, runnable, null, false, new Object[]{context});
        } else {
            runnable.run();
        }
    }

    public static String currentScope() {
        if (ENABLED) {
            return DebugScope.getInstance().getQualifiedName();
        } else {
            return "";
        }
    }

    public static <T> T scope(String name, Object context, Callable<T> callable) {
        if (ENABLED) {
            return DebugScope.getInstance().scope(name, null, callable, false, new Object[]{context});
        } else {
            return DebugScope.call(callable);
        }
    }

    public static void log(String msg, Object... args) {
        if (ENABLED && DebugScope.getInstance().isLogEnabled()) {
            DebugScope.getInstance().log(msg, args);
        }
    }

    public static void dump(Object object, String msg, Object... args) {
        if (ENABLED && DebugScope.getInstance().isDumpEnabled()) {
            DebugScope.getInstance().dump(object, msg, args);
        }
    }

    public static Iterable<Object> context() {
        if (ENABLED) {
            return DebugScope.getInstance().getCurrentContext();
        } else {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> contextSnapshot(Class<T> clazz) {
        if (ENABLED) {
            List<T> result = new ArrayList<>();
            for (Object o : context()) {
                if (clazz.isInstance(o)) {
                    result.add((T) o);
                }
            }
            return result;
        } else {
            return Collections.emptyList();
        }
    }

    public static DebugMetric metric(String name) {
        if (ENABLED && DebugScope.getInstance().isMeterEnabled()) {
            return new MetricImpl(name);
        } else {
            return VOID_METRIC;
        }
    }

    public static void setConfig(DebugConfig config) {
        if (ENABLED) {
            DebugScope.getInstance().setConfig(config);
        }
    }

    public static DebugConfig fixedConfig(final boolean isLogEnabled, final boolean isDumpEnabled, final boolean isMeterEnabled, final boolean isTimerEnabled) {
        return new DebugConfig() {

            @Override
            public boolean isLogEnabled() {
                return isLogEnabled;
            }

            @Override
            public boolean isMeterEnabled() {
                return isMeterEnabled;
            }

            @Override
            public boolean isDumpEnabled() {
                return isDumpEnabled;
            }

            @Override
            public boolean isTimerEnabled() {
                return isTimerEnabled;
            }

            @Override
            public RuntimeException interceptException(RuntimeException e) {
                return e;
            }

            @Override
            public Collection< ? extends DebugDumpHandler> dumpHandlers() {
                return Collections.emptyList();
            }
        };
    }

    private static final DebugMetric VOID_METRIC = new DebugMetric() {
        public void increment() { }
        public void add(int value) { }
    };

    public static DebugTimer timer(String name) {
        if (ENABLED && DebugScope.getInstance().isTimerEnabled()) {
            return new TimerImpl(name);
        } else {
            return VOID_TIMER;
        }
    }

    private static final DebugTimer VOID_TIMER = new DebugTimer() {
        public void start() { }
        public void stop() { }
    };
}
