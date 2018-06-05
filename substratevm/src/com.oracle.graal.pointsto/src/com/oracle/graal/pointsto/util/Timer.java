/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.util;

public class Timer {

    private final String name;
    private final boolean autoPrint;
    private long startTime;
    private long totalTime;

    public Timer(String name) {
        this(name, true);
    }

    public Timer(String name, boolean autoPrint) {
        this.name = name;
        this.autoPrint = autoPrint;
    }

    public StopTimer start() {
        startTime = System.nanoTime();
        return new StopTimer();
    }

    public void stop() {
        long addTime = System.nanoTime() - startTime;
        totalTime += addTime;
        if (autoPrint) {
            print(addTime);
        }
    }

    private void print(long time) {
        System.out.format("%12s: %,10.2f ms\n", name, time / 1000000d);
    }

    public void print() {
        print(totalTime);
    }

    public class StopTimer implements AutoCloseable {

        @Override
        public void close() {
            stop();
        }
    }
}
