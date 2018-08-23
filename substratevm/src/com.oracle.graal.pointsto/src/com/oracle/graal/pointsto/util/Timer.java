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

import org.graalvm.compiler.serviceprovider.GraalServices;

public class Timer {

    private String prefix;

    private final String name;
    private final boolean autoPrint;
    private long startTime;
    private long totalTime;

    public Timer(String name) {
        this(null, name, true);
    }

    public Timer(String prefix, String name) {
        this(prefix, name, true);
    }

    public Timer(String name, boolean autoPrint) {
        this(null, name, autoPrint);
    }

    public Timer(String prefix, String name, boolean autoPrint) {
        this.prefix = prefix;
        this.name = name;
        this.autoPrint = autoPrint;
    }

    /**
     * Registers the prefix to be used when {@linkplain Timer#print(long) printing} a timer. This
     * allows the output of interlaced native image executions to be disambiguated.
     */
    public void setPrefix(String value) {
        this.prefix = value;
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
        if (prefix != null) {
            // Add the PID to further disambiguate concurrent builds of images with the same name
            String pid = GraalServices.getExecutionID();
            System.out.format("[%s:%s] %12s: %,10.2f ms\n", prefix, pid, name, time / 1000000d);
        } else {
            System.out.format("%12s: %,10.2f ms\n", name, time / 1000000d);
        }
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
