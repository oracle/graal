/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.pointsto.reports.StatisticsPrinter;
import com.oracle.svm.util.ImageBuildStatistics;
import org.graalvm.nativeimage.ImageSingletons;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TimerCollection implements ImageBuildStatistics.TimerCollectionPrinter {

    public static TimerCollection singleton() {
        return ImageSingletons.lookup(TimerCollection.class);
    }

    private final List<Timer> timers = Collections.synchronizedList(new ArrayList<>());

    public Timer createTimer(String name) {
        return createTimer(null, name, true);
    }

    public Timer createTimer(String prefix, String name) {
        return createTimer(prefix, name, true);
    }

    public Timer createTimer(String name, boolean autoPrint) {
        return createTimer(null, name, autoPrint);
    }

    public Timer createTimer(String prefix, String name, boolean autoPrint) {
        Timer timer = new Timer(prefix, name, autoPrint);
        timers.add(timer);
        return timer;
    }

    @Override
    public void printTimerStats(PrintWriter out) {
        for (int i = 0; i < timers.size(); i++) {
            Timer timer = timers.get(i);
            StatisticsPrinter.print(out, timer.getName() + "_time", ((int) timer.getTotalTime()));
            if (i != timers.size() - 1) {
                StatisticsPrinter.print(out, timer.getName() + "_memory", ((int) timer.getTotalMemory()));
            } else {
                StatisticsPrinter.printLast(out, timer.getName() + "_memory", ((int) timer.getTotalMemory()));
            }
        }
    }
}
