/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.util;

public class Timer implements AutoCloseable {
    private String name;
    private String analysisName;
    /**
     * Timer start time in nanoseconds.
     */
    private long startTime;
    /**
     * Timer total time in nanoseconds.
     */
    private long totalTime;

    public Timer(String name, String analysisName) {
        this.name = name;
        this.analysisName = analysisName;
        startTime = System.nanoTime();
    }

    @Override
    public void close() {
        totalTime = System.nanoTime() - startTime;
        long totalMemory = Runtime.getRuntime().totalMemory();
        double totalMemoryGB = totalMemory / 1024.0 / 1024.0 / 1024.0;
        System.out.format("[%s]%25s: %,10.2f ms, %,5.2f GB%n", analysisName, name, totalTime / 1000000d, totalMemoryGB);
    }
}
