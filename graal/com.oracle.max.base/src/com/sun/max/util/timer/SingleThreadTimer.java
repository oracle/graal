/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.util.timer;

import com.sun.max.annotate.*;
import com.sun.max.profile.*;

/**
 * This class implements a timer that requires no synchronization but maintains an internal stack
 * for handling nested tasks.
 */
public class SingleThreadTimer implements Timer {
    private static final int MAXIMUM_NESTING_DEPTH = 20;

    private final Clock clock;
    private final long[] start = new long[MAXIMUM_NESTING_DEPTH];
    private final long[] nested = new long[MAXIMUM_NESTING_DEPTH];
    @RESET
    private int depth;
    @RESET
    private long last;

    public SingleThreadTimer(Clock clock) {
        this.clock = clock;
    }

    public void start() {
        final int d = this.depth;
        nested[d] = 0;
        this.depth = d + 1;
        start[d] = clock.getTicks();
    }

    public void stop() {
        final long time = clock.getTicks();
        final int d = this.depth - 1;
        last = time - start[d];
        if (d > 0) {
            nested[d - 1] += last;
        }
        this.depth = d;
    }

    public Clock getClock() {
        return clock;
    }

    public long getLastElapsedTime() {
        return last;
    }

    public long getLastNestedTime() {
        return nested[depth];
    }
}
