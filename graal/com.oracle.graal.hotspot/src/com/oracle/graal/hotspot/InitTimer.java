/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import com.oracle.graal.debug.*;

import edu.umd.cs.findbugs.annotations.*;

/**
 * A facility for timing a step in the runtime or compilation queue initialization sequence. This
 * exists separate from {@link DebugTimer} as it must be independent from all other Graal code so as
 * to not perturb the initialization sequence.
 */
public class InitTimer implements AutoCloseable {
    final String name;
    final long start;

    private InitTimer(String name) {
        this.name = name;
        this.start = System.currentTimeMillis();
        System.out.println("START: " + SPACES.substring(0, timerDepth * 2) + name);
        assert Thread.currentThread() == initializingThread : Thread.currentThread() + " != " + initializingThread;
        timerDepth++;
    }

    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", justification = "only the initializing thread accesses this field")
    public void close() {
        final long end = System.currentTimeMillis();
        timerDepth--;
        System.out.println(" DONE: " + SPACES.substring(0, timerDepth * 2) + name + " [" + (end - start) + " ms]");
    }

    public static InitTimer timer(String name) {
        return ENABLED ? new InitTimer(name) : null;
    }

    public static InitTimer timer(String name, Object suffix) {
        return ENABLED ? new InitTimer(name + suffix) : null;
    }

    /**
     * Specifies if initialization timing is enabled. This can only be set via a system property as
     * the timing facility is used to time initialization of {@link HotSpotOptions}.
     */
    private static final boolean ENABLED = Boolean.getBoolean("graal.runtime.TimeInit");

    public static int timerDepth = 0;
    public static final String SPACES = "                                            ";

    /**
     * Used to assert the invariant that all initialization happens on the same thread.
     */
    public static final Thread initializingThread;
    static {
        if (ENABLED) {
            initializingThread = Thread.currentThread();
            System.out.println("INITIALIZING THREAD: " + initializingThread);
        } else {
            initializingThread = null;
        }
    }
}
